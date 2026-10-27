package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

/**
 * v1.47 迭代 37：Boss 词缀 AFFIX。
 *
 * <p>第三艘 Boss 起每场随机挂一条词缀，写在血条下方：疾火（攻击更快、核心暴露更勤）、
 * 磐垒（舰体减伤 16%）、蜂群（换段增援 +2）。三条都有额外赏金，磐垒与蜂群多掉一件道具；
 * 词缀用独立 LCG 抽取，不扰动对局主随机源。
 */
public class BossAffixV147Test {

    private GameWorld newGame(long seed) {
        GameWorld w = new GameWorld(seed);
        w.spawningEnabled = false;
        w.mutatorsEnabled = false;
        w.settings.setDifficulty(Difficulty.STANDARD);
        w.ship.makeInvulnerable(1_000f);
        w.ship.y = 1460f;
        w.ship.prevY = 1460f;
        return w;
    }

    // ---- 辅助 ----

    /** 把第 n 艘 Boss 直接叫进场（n = bossesDefeated，0 基）。 */
    private void triggerBoss(GameWorld w, int bossesDefeated) {
        w.bossesDefeated = bossesDefeated;
        w.wave = 4;
        w.update(1f / 60f);
        w.bossWarningTimer = 0f;
        w.bossPhaseBreakTimer = 0f;
        w.boss.y = w.boss.arenaY;
        assertTrue("Boss 应当已入场", w.boss.active);
    }

    private void disableParts(GameWorld w) {
        for (int i = 0; i < w.boss.partCount; i++) w.boss.parts[i].active = false;
    }

    private void clearEnemies(GameWorld w) {
        for (Enemy e : w.enemies) e.active = false;
    }

    private int countEnemies(GameWorld w) {
        int n = 0;
        for (Enemy e : w.enemies) if (e.active) n++;
        return n;
    }

    private Bullet playerBulletAt(GameWorld w, float x, float y, int damage) {
        for (Bullet b : w.bullets) {
            if (b.active) continue;
            b.init(x, y, 0f, 0f, damage, false);
            return b;
        }
        throw new IllegalStateException("玩家弹池已满");
    }

    private List<GameWorld.GameEvent> drain(GameWorld w) {
        List<GameWorld.GameEvent> out = new ArrayList<>();
        for (GameWorld.GameEvent e : w.pollEvents()) out.add(e);
        return out;
    }

    // ---- 抽取 ----

    @Test
    public void firstTwoBossesStayPlain() {
        GameWorld first = newGame(777L);
        triggerBoss(first, 0);
        assertEquals("第一艘不带词缀", BossAffix.NONE, first.boss.affix);

        GameWorld second = newGame(777L);
        triggerBoss(second, 1);
        assertEquals("第二艘也不带词缀", BossAffix.NONE, second.boss.affix);
    }

    @Test
    public void thirdBossAlwaysGetsAnAffix() {
        for (long seed = 1L; seed <= 6L; seed++) {
            GameWorld w = newGame(seed);
            triggerBoss(w, 2);
            assertNotEquals("第三艘起必须有词缀: seed=" + seed,
                    BossAffix.NONE, w.boss.affix);
        }
    }

    @Test
    public void affixIsDeterministicForTheSameSeed() {
        GameWorld a = newGame(2026L);
        GameWorld b = newGame(2026L);
        triggerBoss(a, 2);
        triggerBoss(b, 2);
        assertEquals("同一种子同一条词缀", a.boss.affix, b.boss.affix);
    }

    @Test
    public void affixVariesAcrossSeeds() {
        Set<BossAffix> seen = new HashSet<>();
        for (long seed = 1L; seed <= 9L; seed++) {
            GameWorld w = newGame(seed);
            triggerBoss(w, 2);
            seen.add(w.boss.affix);
        }
        assertTrue("跨种子应当能见到不止一种词缀: " + seen, seen.size() >= 2);
    }

    @Test
    public void affixEventCarriesTheSelectedIndex() {
        GameWorld w = newGame(2026L);
        w.bossesDefeated = 2;
        w.wave = 4;
        w.update(1f / 60f);
        int code = -1;
        for (GameWorld.GameEvent e : drain(w)) {
            if (e.type == GameWorld.EventType.BOSS_AFFIX) code = e.code;
        }
        assertEquals("事件带上词缀序号", w.boss.affix.ordinal(), code);

        GameWorld plain = newGame(2026L);
        triggerBoss(plain, 0);
        for (GameWorld.GameEvent e : drain(plain)) {
            assertNotEquals("没词缀就不发事件",
                    GameWorld.EventType.BOSS_AFFIX, e.type);
        }
    }

    @Test
    public void ofWrapsTheRollInsideTheAffixRange() {
        assertEquals(BossAffix.BLAZE, BossAffix.of(0));
        assertEquals(BossAffix.BULWARK, BossAffix.of(1));
        assertEquals(BossAffix.BROOD, BossAffix.of(2));
        assertEquals(BossAffix.BLAZE, BossAffix.of(3));
        assertEquals(BossAffix.BROOD, BossAffix.of(-1));
    }

    // ---- 疾火 BLAZE ----

    @Test
    public void blazeSpeedsUpTheBossClockAndShortensCoreInterval() {
        GameWorld w = newGame(777L);
        triggerBoss(w, 2);
        w.applyBossAffix(BossAffix.BLAZE);

        assertEquals("疾火的攻击节奏倍率", 1.22f, w.boss.tempo, 0.001f);
        // 开核再合拢，下一次的间隔应当比常规短 18%
        w.bossCoreTimer = 0.01f;
        w.bossCoreCooldown = 0f;
        w.update(1f / 60f);
        assertEquals(0f, w.bossCoreTimer, 0.001f);
        assertEquals(GameWorld.CORE_INTERVAL * 0.82f,
                w.bossCoreCooldown, 0.05f);
    }

    // ---- 磐垒 BULWARK ----

    @Test
    public void bulwarkSoaksSixteenPercentOfEveryHit() {
        GameWorld w = newGame(777L);
        triggerBoss(w, 0);   // 第一艘没有护盾发生器，先把词缀的减伤单独量出来
        w.applyBossAffix(BossAffix.BULWARK);
        float before = w.boss.hp;

        w.boss.damage(10);

        assertEquals("舰体受击 ×0.84", before - 8.4f, w.boss.hp, 0.001f);
    }

    @Test
    public void bulwarkStacksWithShieldGenerators() {
        GameWorld w = newGame(777L);
        triggerBoss(w, 2);   // LEVIATHAN 带一座护盾发生器（核心伤害 ×0.68）
        w.applyBossAffix(BossAffix.BULWARK);
        float before = w.boss.hp;

        w.boss.damage(10);

        assertEquals("两条减伤叠乘：10 × 0.68 × 0.84", before - 5.712f,
                w.boss.hp, 0.001f);
    }

    @Test
    public void bulwarkPaysExtraScore() {
        GameWorld w = newGame(777L);
        triggerBoss(w, 2);
        w.applyBossAffix(BossAffix.BULWARK);
        assertEquals(GameWorld.AFFIX_SCORE_BASE + 200, w.bossAffixBonus());
    }

    // ---- 蜂群 BROOD ----

    @Test
    public void broodSendsTwoExtraReinforcementsAtPhaseBreak() {
        GameWorld plain = newGame(777L);
        triggerBoss(plain, 0);
        clearEnemies(plain);
        plain.boss.hp = plain.boss.maxHp * 0.6f;   // 跨过 66% → 第二阶段
        plain.update(1f / 60f);
        int normal = countEnemies(plain);

        GameWorld brood = newGame(777L);
        triggerBoss(brood, 0);
        brood.applyBossAffix(BossAffix.BROOD);
        clearEnemies(brood);
        brood.boss.hp = brood.boss.maxHp * 0.6f;
        brood.update(1f / 60f);
        int extra = countEnemies(brood);

        assertEquals("常规换段放两架", 2, normal);
        assertEquals("蜂群多放两架", 4, extra);
    }

    // ---- 赏金与清理 ----

    @Test
    public void plainBossPaysNoAffixBonus() {
        GameWorld w = newGame(777L);
        triggerBoss(w, 0);
        assertEquals(0, w.bossAffixBonus());
    }

    @Test
    public void killingAnAffixedBossAddsThePayout() {
        int plainScore = killBossAndReadScore(BossAffix.NONE);
        int bulwarkScore = killBossAndReadScore(BossAffix.BULWARK);
        assertEquals("磐垒赏金 300", GameWorld.AFFIX_SCORE_BASE,
                bulwarkScore - plainScore);
    }

    private int killBossAndReadScore(BossAffix affix) {
        GameWorld w = killBoss(affix);
        return w.score;
    }

    @Test
    public void bulwarkAndBroodDropOneMorePickup() {
        int plain = countPowerUps(killBoss(BossAffix.NONE));
        assertEquals("磐垒多掉一件", plain + 1,
                countPowerUps(killBoss(BossAffix.BULWARK)));
        assertEquals("蜂群多掉一件", plain + 1,
                countPowerUps(killBoss(BossAffix.BROOD)));
        assertEquals("疾火不多掉", plain,
                countPowerUps(killBoss(BossAffix.BLAZE)));
    }

    private int countPowerUps(GameWorld w) {
        int n = 0;
        for (PowerUp p : w.powerups) if (p.active) n++;
        return n;
    }

    private GameWorld killBoss(BossAffix affix) {
        GameWorld w = newGame(777L);
        triggerBoss(w, 0);
        disableParts(w);
        w.applyBossAffix(affix);
        for (EnemyBullet eb : w.enemyBullets) eb.active = false;
        w.boss.hp = 1f;
        playerBulletAt(w, w.boss.x, w.boss.y, 5);
        w.update(1f / 60f);
        w.update(1f / 60f);
        assertFalse("Boss 应当已被击毁", w.boss.active);
        return w;
    }

    @Test
    public void restartReplaysTheSameAffixSequence() {
        GameWorld w = newGame(1234L);
        triggerBoss(w, 2);
        BossAffix first = w.boss.affix;
        assertNotNull(first);

        w.restart(GameMode.WAVES);
        triggerBoss(w, 2);

        assertEquals("重开后的第一艘词缀与开局一致", first, w.boss.affix);
    }
}
