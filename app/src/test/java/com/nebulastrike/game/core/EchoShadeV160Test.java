package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.60 迭代 50：旗舰残响 BOSS ECHO。
 *
 * <p>把一场旗舰战打得干干净净（全程没掉血），被击沉的那艘船会在两段航程后
 * 以残影姿态回来：血量只有本体的 55%，出招更快，招式与本体完全一致。
 * 它不是新航段——不推进 bossesDefeated、不写图鉴、不解锁剧情、不发圣物与强化卡，
 * 只给赏金和两件补给。日常 / 连战 / 回声周目 / 限时 / 模拟战都不掺这条支线。
 */
public class EchoShadeV160Test {

    private GameWorld world;
    private final List<GameWorld.GameEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261029L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.bountyEnabled = false;
        world.perksEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.ship.makeInvulnerable();
        world.restart(GameMode.WAVES);
        drain();
    }

    // ---- 辅助 ----

    private void step(int frames) {
        for (int i = 0; i < frames; i++) {
            world.update(1f / 60f);
            events.addAll(world.pollEvents());
        }
    }

    private void drain() {
        events.clear();
        events.addAll(world.pollEvents());
    }

    private int countEvents(GameWorld.EventType type) {
        int n = 0;
        for (GameWorld.GameEvent e : events) if (e.type == type) n++;
        return n;
    }

    private void triggerBoss() {
        world.wave = 4;
        step(1);
        assertTrue("第 4 波应该压旗舰", world.boss.active);
        world.bossWarningTimer = 0f;
        world.bossPhaseBreakTimer = 0f;
        world.boss.y = world.boss.arenaY;
        drain();
    }

    private void cleanKill() {
        world.boss.hp = 0f;
        step(1);
        assertFalse("旗舰应该被击破", world.boss.active);
    }

    /** 跳过喘息并把航段推到残响预定的那一波，让残响立刻登场。 */
    private void awaitEcho() {
        assertTrue("应该有等待中的残响", world.echoPendingTier >= 0);
        world.wave = world.echoSpawnWave;
        world.bossGapTimer = 0f;
        step(1);
        assertTrue("残响应在到期后入场", world.boss.active);
        assertTrue("这一场应该标记为残响战", world.echoBossFight);
        world.bossWarningTimer = 0f;
        world.bossPhaseBreakTimer = 0f;
        world.boss.y = world.boss.arenaY;
        drain();
    }

    // ---- 留影 ----

    @Test
    public void aCleanKillLeavesAnEchoTwoWavesLater() {
        triggerBoss();
        int tier = world.boss.tier;
        int waveAtKill = world.wave;
        cleanKill();

        assertEquals("无伤击破的旗舰档位被记住", tier, world.echoPendingTier);
        assertEquals("两段航程后回来", waveAtKill + GameWorld.ECHO_DELAY_WAVES,
                world.echoSpawnWave);
        assertEquals("正常旗舰照旧推进航段", 1, world.bossesDefeated);
        assertFalse("此时只是留影，还没入场", world.echoBossFight);
    }

    @Test
    public void takingAHitSpoilsTheEcho() {
        triggerBoss();
        world.bossFightHits = 1;
        cleanKill();
        assertEquals("掉过血的旗舰不留残响", -1, world.echoPendingTier);
        assertEquals(-1, world.echoSpawnWave);
    }

    @Test
    public void theEchoWaitsForItsWave() {
        triggerBoss();
        cleanKill();
        world.bossGapTimer = 0f;
        world.wave = world.echoSpawnWave - 1;
        step(1);
        assertFalse("还差一段航程就不该提前来", world.boss.active);

        world.wave = world.echoSpawnWave;
        step(1);
        assertTrue(world.boss.active);
        assertTrue(world.echoBossFight);
    }

    // ---- 残响战本身 ----

    @Test
    public void theEchoIsThinnerAndQuickerThanTheRealHull() {
        triggerBoss();
        int tier = world.boss.tier;
        float bodyHp = world.boss.maxHp;
        cleanKill();
        awaitEcho();

        assertEquals("同一档位", tier, world.boss.tier);
        assertEquals("只有本体 55% 的血", bodyHp * GameWorld.ECHO_SHADE_HP_SCALE,
                world.boss.maxHp, 0.01f);
        assertTrue("出招更紧", world.boss.tempo > 1.1f);
        assertSame("残响不掷词缀", BossAffix.NONE, world.boss.affix);
    }

    @Test
    public void theEchoNeverAdvancesTheSector() {
        triggerBoss();
        cleanKill();
        drain();
        int sectorAfterBoss = world.stageNumber();
        int defeatsAfterBoss = world.bossesDefeated;
        int codexAfterBoss = world.settings.codexBossKills[0];
        awaitEcho();
        int tier = world.boss.tier;

        // 残响存活时也不该被当成"最终旗舰"或下一段
        assertFalse(world.finalBossFight);
        assertEquals(defeatsAfterBoss, world.bossesDefeated);

        cleanKill();
        assertEquals("击破数不动", defeatsAfterBoss, world.bossesDefeated);
        assertEquals("航段不动", sectorAfterBoss, world.stageNumber());
        assertEquals("没有肃清横幅", 0, countEvents(GameWorld.EventType.STAGE_CLEAR));
        assertEquals("不解锁剧情", 0, countEvents(GameWorld.EventType.STORY_UNLOCK));
        assertEquals("不写图鉴", codexAfterBoss, world.settings.codexBossKills[tier]);
        assertEquals("残响计数 +1", 1, world.echoDefeats);
        assertFalse(world.echoBossFight);
        assertTrue("HUD 有击破余辉", world.echoFlash > 0f);
        assertFalse("不弹强化卡", world.perkOpen);
    }

    @Test
    public void beatingTheEchoPaysAndDropsSupplies() {
        triggerBoss();
        cleanKill();
        awaitEcho();
        int tier = world.boss.tier;
        int scoreBefore = world.score;
        int dropsBefore = 0;
        for (PowerUp p : world.powerups) if (p.active) dropsBefore++;
        cleanKill();

        assertEquals("残响赏金", GameWorld.ECHO_SCORE_BASE + tier * 120,
                world.score - scoreBefore);
        int drops = 0;
        for (PowerUp p : world.powerups) if (p.active) drops++;
        assertEquals("只掉两件补给", dropsBefore + 2, drops);
        assertFalse("不会再留第二层残响", world.echoPendingTier >= 0);
    }

    @Test
    public void theEchoLeavesTheNormalRhythmIntact() {
        triggerBoss();
        cleanKill();
        int nextWave = world.nextBossWave;
        int gapBefore = Math.round(world.bossGapTimer);
        awaitEcho();
        // 残响入场不重排 nextBossWave：它只是一段插曲
        world.boss.hp = 0f;
        step(1);
        assertEquals("正常旗舰的节拍不变", nextWave, world.nextBossWave);
        assertEquals("残响后的喘息短于正式旗舰", GameWorld.ECHO_GAP,
                world.bossGapTimer, 0.001f);
        assertTrue("残响喘息确实更短", GameWorld.ECHO_GAP < gapBefore);
    }

    // ---- 模式边界 ----

    @Test
    public void otherModesNeverQueueAnEcho() {
        assertNoEchoIn(GameMode.DAILY, 4);
        assertNoEchoIn(GameMode.RUSH, -1);
        assertNoEchoIn(GameMode.ECHO, 4);
        assertNoEchoIn(GameMode.TIME, 3);
        assertNoEchoIn(GameMode.PRACTICE, -1);
    }

    private void assertNoEchoIn(GameMode mode, int waveAtBoss) {
        world.restart(mode);
        world.bossGapTimer = 0f;
        if (waveAtBoss > 0) world.wave = waveAtBoss;
        step(1);
        if (!world.boss.active) step(1);
        assertTrue(mode + " 应该能叫出旗舰", world.boss.active);
        world.bossWarningTimer = 0f;
        world.bossPhaseBreakTimer = 0f;
        world.boss.y = world.boss.arenaY;
        world.boss.hp = 0f;
        step(1);
        assertFalse(mode + " 不该被击破判定漏掉", world.boss.active);
        assertEquals(mode + " 不掺残响支线", -1, world.echoPendingTier);
        assertEquals(-1, world.echoSpawnWave);
    }

    @Test
    public void endlessStillLeavesEchoes() {
        world.restart(GameMode.ENDLESS);
        world.bossGapTimer = 0f;
        world.wave = 3;
        step(1);
        assertTrue(world.boss.active);
        world.bossWarningTimer = 0f;
        world.boss.y = world.boss.arenaY;
        int tier = world.boss.tier;
        world.boss.hp = 0f;
        step(1);
        assertEquals("无尽也吃这条支线", tier, world.echoPendingTier);
    }

    @Test
    public void restartClearsEveryEchoTrace() {
        triggerBoss();
        cleanKill();
        awaitEcho();
        cleanKill();
        assertTrue(world.echoDefeats > 0);

        world.restart(GameMode.WAVES);
        assertEquals(-1, world.echoPendingTier);
        assertEquals(-1, world.echoSpawnWave);
        assertEquals(0, world.echoDefeats);
        assertEquals(0f, world.echoFlash, 0.001f);
        assertFalse(world.echoBossFight);
        assertNotEquals("不是练功局", GameMode.PRACTICE, world.mode);
    }
}
