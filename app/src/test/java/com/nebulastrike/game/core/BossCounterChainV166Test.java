package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.66 迭代 56：破招连读 · READ CHAIN。
 *
 * <p>破招这条线到这一版走满四层：v1.63 识破给分、v1.64 反击给伤害、v1.65 精通给"看习惯这艘船"，
 * v1.66 给<b>手感</b>——连着读懂的招数是一串，串越长，单招赏金越高：3 招 ×2、6 招 ×3、9 招起 ×4。
 * 唯一的断法是<b>真正掉血</b>：护盾挡下的那一下不算，换船、开新局旗舰、判定窗口超时都不算。
 *
 * <p>四条口径：① 连读只乘识破赏金，不动反击窗口、伤害加成、弹速与掉落；② 串是玩家的账，
 * 不是某一艘船的账，所以旗舰沉了它还在；③ 只抬档那一瞬放一次确认音，第 4、5 招不再重复响；
 * ④ 断串时把"断在第几招"当场写出来，规则要看得见，不然玩家只看见数字掉回 1。
 */
public class BossCounterChainV166Test {

    private GameWorld world;
    private final List<GameWorld.GameEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261122L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.bountyEnabled = false;
        world.perksEnabled = false;
        world.settings.reducedFx = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
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

    private GameWorld.GameEvent last(GameWorld.EventType type) {
        GameWorld.GameEvent hit = null;
        for (GameWorld.GameEvent e : events) if (e.type == type) hit = e;
        return hit;
    }

    /** 叫出这一段的旗舰、跳过演出、压掉词缀，并保证场上没有上一艘船。 */
    private void triggerBoss(int level) {
        if (world.boss.active) {
            world.boss.hp = 0f;
            step(1);
        }
        world.bossGapTimer = 0f;
        world.stageClearTimer = 0f;
        world.bossesDefeated = level;
        world.wave = world.nextBossWave;
        step(1);
        assertTrue("旗舰该入场", world.boss.active);
        world.applyBossAffix(BossAffix.NONE);
        world.bossWarningTimer = 0f;
        world.bossPhaseBreakTimer = 0f;
        world.boss.y = world.boss.stationY;
        shieldFromHarm();
        drain();
    }

    /** 测试期间不让玩家莫名其妙掉血——想挨打时另有专门的方法。 */
    private void shieldFromHarm() {
        world.ship.makeInvulnerable(9999f);
        world.ship.hasShield = false;
    }

    /** 丢出本型招牌并让它当场散尽：识破、连读、反击窗口都在这一刻结算。 */
    private void readOneSignature() {
        assertTrue("招牌要真的丢出弹",
                world.fireSignature(world.boss.form, world.boss.tier) > 0);
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active && b.sigSerial != 0) b.active = false;
        }
        step(2);
    }

    private void read(int n) {
        for (int i = 0; i < n; i++) readOneSignature();
    }

    /** 真的掉血那一下：不打护盾、解除无敌、塞一发贴身的敌弹。 */
    private void takeRealHit() {
        world.ship.hasShield = false;
        world.ship.shieldLayers = 0;
        world.lives = 5;
        world.ship.invulnerable = false;
        world.ship.dashTimer = 0f;
        world.enemyBullets[0].init(world.ship.x, world.ship.y, 0f, 0f);
        step(1);
        shieldFromHarm();
    }

    /** 护盾挡下那一下：只掉一层盾，不算掉血。 */
    private void takeBlockedHit() {
        world.ship.addShield();
        assertTrue("盾要挂上", world.ship.hasShield);
        int layers = world.ship.shieldLayers;
        world.ship.invulnerable = false;
        world.ship.dashTimer = 0f;
        world.enemyBullets[0].init(world.ship.x, world.ship.y, 0f, 0f);
        step(1);
        assertEquals("盾掉一层", Math.max(0, layers - 1), world.ship.shieldLayers);
        shieldFromHarm();
    }

    // ---- 档位本身 ----

    @Test
    public void theLadderIsTheSameForEveryone() {
        assertEquals(1, GameWorld.chainMultFor(0));
        assertEquals(1, GameWorld.chainMultFor(1));
        assertEquals(1, GameWorld.chainMultFor(2));
        assertEquals(2, GameWorld.chainMultFor(GameWorld.CHAIN_STEP_READS));
        assertEquals(2, GameWorld.chainMultFor(5));
        assertEquals(3, GameWorld.chainMultFor(6));
        assertEquals(4, GameWorld.chainMultFor(9));
        assertEquals("九招以后不再往上抬", 4, GameWorld.chainMultFor(40));
    }

    @Test
    public void theLadderNeverGoesBackwardsOrBelowOne() {
        assertEquals(1, GameWorld.chainMultFor(-7));
        int prev = 1;
        for (int s = 0; s <= 40; s++) {
            int m = GameWorld.chainMultFor(s);
            assertTrue("倍数只涨不跌：" + s, m >= prev && m <= GameWorld.CHAIN_MAX_MULT);
            prev = m;
        }
    }

    @Test
    public void onlyThreeStepsFireTheConfirmation() {
        assertTrue(GameWorld.chainStepUp(3));
        assertTrue(GameWorld.chainStepUp(6));
        assertTrue(GameWorld.chainStepUp(9));
        assertFalse("第 4、5 招还在 ×2 这一档，不该再响", GameWorld.chainStepUp(4));
        assertFalse(GameWorld.chainStepUp(5));
        assertFalse("十招已经在封顶，不用再庆祝", GameWorld.chainStepUp(10));
        assertFalse("第 1 招没有串可庆祝", GameWorld.chainStepUp(1));
    }

    @Test
    public void freshRunStartsWithNoChain() {
        assertEquals(0, world.counterStreak);
        assertEquals("一上来就是原价", 1, world.counterScoreMult);
        assertEquals(0, world.counterStreakBest);
    }

    // ---- 串是一招一招数出来的 ----

    @Test
    public void theFirstReadsAreStillPlainPrice() {
        triggerBoss(0);
        int base = GameWorld.COUNTER_SCORE_BASE + 20 * world.boss.level;
        read(1);
        assertEquals(1, world.counterStreak);
        assertEquals(base, world.counterScore);
        readOneSignature();
        assertEquals(2, world.counterStreak);
        assertEquals("两招之内不打折也不翻倍", base, world.counterScore);
        assertEquals(0, countEvents(GameWorld.EventType.BOSS_COUNTER_CHAIN));
    }

    @Test
    public void theThirdReadDoublesTheBounty() {
        triggerBoss(0);
        int base = GameWorld.COUNTER_SCORE_BASE + 20 * world.boss.level;
        read(3);
        assertEquals(3, world.counterStreak);
        assertEquals(2, world.counterScoreMult);
        assertEquals(2 * base, world.counterScore);
        assertEquals("抬档只在那一瞬报一次", 1, countEvents(GameWorld.EventType.BOSS_COUNTER_CHAIN));
        assertEquals(2, last(GameWorld.EventType.BOSS_COUNTER_CHAIN).code);
        assertTrue(world.counterChainBanner > 0f);
        assertEquals(2, world.counterChainMult);
    }

    @Test
    public void theChainKeepsClimbingAcrossShips() {
        triggerBoss(0);
        read(3);
        assertEquals(2, world.counterScoreMult);
        read(3);
        assertEquals("六招就是 ×3", 3, world.counterScoreMult);
        read(3);
        assertEquals("九招封顶 ×4", 4, world.counterScoreMult);
        assertEquals(9, world.counterStreak);
        assertEquals(9, world.counterStreakBest);
        assertEquals(3, countEvents(GameWorld.EventType.BOSS_COUNTER_CHAIN));
    }

    @Test
    public void aSinkingFlagshipDoesNotBreakTheChain() {
        triggerBoss(0);
        read(3);
        int streak = world.counterStreak;
        world.boss.hp = 0f;
        step(4);
        assertFalse("旗舰沉了", world.boss.active);
        assertEquals("串是玩家的账，不是它的账", streak, world.counterStreak);
        assertEquals(0, countEvents(GameWorld.EventType.BOSS_COUNTER_BREAK));
    }

    @Test
    public void theBountyFollowsHullSizeAndChainTogether() {
        triggerBoss(0);
        read(3);
        int smallShip = world.counterScore;
        assertEquals(2, world.counterScoreMult);
        triggerBoss(4);
        readOneSignature();
        int level = world.boss.level;
        assertEquals("大船按自己的舰级再乘连读档位（4 招 → ×2）",
                (GameWorld.COUNTER_SCORE_BASE + 20 * level) * GameWorld.chainMultFor(4),
                world.counterScore);
        assertTrue("同一档位下，大舰的识破更值钱", world.counterScore > smallShip);
    }

    // ---- 断串 ----

    @Test
    public void realDamageBreaksTheChain() {
        triggerBoss(0);
        read(4);
        assertEquals(4, world.counterStreak);
        takeRealHit();
        assertEquals(0, world.counterStreak);
        assertEquals("读数立刻回到原价", 1, world.counterScoreMult);
        assertEquals("断在第几招要记下来", 4, world.counterChainBreakAt);
        assertTrue(world.counterChainBreak > 0f);
        assertEquals(1, countEvents(GameWorld.EventType.BOSS_COUNTER_BREAK));
        assertEquals(4, last(GameWorld.EventType.BOSS_COUNTER_BREAK).code);
        assertEquals("最长连读已经进账，不会被抹掉", 4, world.counterStreakBest);
    }

    @Test
    public void shieldBlockKeepsTheChainAlive() {
        triggerBoss(0);
        read(4);
        takeBlockedHit();
        assertEquals("护盾挡下的那一下不算掉血", 4, world.counterStreak);
        assertEquals(2, world.counterScoreMult);
        assertEquals(0, countEvents(GameWorld.EventType.BOSS_COUNTER_BREAK));
    }

    @Test
    public void theChainRebuildsFromScratchAfterABreak() {
        triggerBoss(0);
        read(5);
        takeRealHit();
        read(2);
        assertEquals("断完再数，两招还是原价", 1, world.counterScoreMult);
        int base = GameWorld.COUNTER_SCORE_BASE + 20 * world.boss.level;
        readOneSignature();
        assertEquals(3, world.counterStreak);
        assertEquals("攒满三招才重新抬档", 2 * base, world.counterScore);
    }

    @Test
    public void gettingHitWithoutAChainSaysNothing() {
        triggerBoss(0);
        takeRealHit();
        assertEquals("本来就没有串，不该假装有", 0, countEvents(GameWorld.EventType.BOSS_COUNTER_BREAK));
        assertEquals(0, world.counterChainBreakAt);
    }

    @Test
    public void timeAloneNeverDecaysTheChain() {
        triggerBoss(0);
        read(3);
        assertEquals(2, world.counterScoreMult);
        world.boss.hp = 0f;
        step(4);
        assertFalse("旗舰收场", world.boss.active);
        step(700);                                   // 十多秒什么都不读
        assertEquals("串不会因为放着不用就掉", 3, world.counterStreak);
        assertEquals("识破的账也没自己涨", 3, world.countersRead);
        assertEquals(3, world.counterStreakBest);
    }

    // ---- 只乘赏金，别的不掺和 ----

    @Test
    public void theChainTouchesNothingElse() {
        triggerBoss(0);
        read(3);                                   // 串到 ×2，这一型同时升到"熟悉"
        float window3 = world.counterStrikeWindowTotal;
        assertEquals("窗口长度还是精通说了算",
                GameWorld.counterStrikeWindowFor(1), window3, 0.001f);
        assertTrue("该开的窗一格没少", world.counterStrikeTimer > 0f);
        int strikes = world.counterStrikes;
        read(3);                                   // 六招：串到 ×3，这一型到"读透"
        assertEquals("每一招照旧开一扇窗", strikes + 3, world.counterStrikes);
        assertEquals("窗口变长只是因为那一型读透了，跟串无关",
                GameWorld.counterStrikeWindowFor(2), world.counterStrikeWindowTotal, 0.001f);
        assertTrue(window3 < world.counterStrikeWindowTotal);
    }

    @Test
    public void theBoostIsStillPureTierMath() {
        triggerBoss(0);
        read(9);
        world.counterStrikeTimer = world.counterStrikeWindowTotal;
        int dealt = 5;
        int boosted = world.counterStrikeBoost(dealt);
        assertEquals("窗口里抬的还是精通那一档",
                dealt + Math.max(1, dealt * GameWorld.counterStrikeBonusPctFor(
                        world.counterStrikeTier) / 100), boosted);
    }

    @Test
    public void signatureSpeedIgnoresTheChain() {
        triggerBoss(0);
        Boss.Form form = world.boss.form;
        world.formReads[form.ordinal()] = 0;
        float raw = topSignatureSpeed();
        // 精通摁到"读透"固定住，这样两次之间唯一变化的就只有串的长度
        world.formReads[form.ordinal()] = GameWorld.MASTERY_MASTERED_READS;
        float bare = topSignatureSpeed();
        world.counterStreak = 9;
        world.counterScoreMult = GameWorld.chainMultFor(9);
        float chained = topSignatureSpeed();
        assertTrue("读透那一型确实慢一档", bare < raw);
        assertEquals("串再长也不额外减速，那是精通的账", bare, chained, 0.001f);
    }

    private float topSignatureSpeed() {
        for (EnemyBullet b : world.enemyBullets) b.active = false;
        assertTrue(world.fireSignature(world.boss.form, world.boss.tier) > 0);
        float top = 0f;
        for (EnemyBullet b : world.enemyBullets) {
            if (!b.active || b.sigSerial == 0) continue;
            top = Math.max(top, (float) Math.hypot(b.vx, b.vy));
        }
        for (EnemyBullet b : world.enemyBullets) b.active = false;
        return top;
    }

    // ---- 事件与清零 ----

    @Test
    public void everyReadStillFiresItsOwnCounterEvent() {
        triggerBoss(0);
        read(4);
        assertEquals(4, countEvents(GameWorld.EventType.BOSS_COUNTER));
        assertEquals(4, world.countersRead);
        assertEquals("只有三招里的一次是抬档", 1, countEvents(GameWorld.EventType.BOSS_COUNTER_CHAIN));
    }

    @Test
    public void restartClearsEveryTraceOfTheChain() {
        triggerBoss(0);
        read(6);
        takeRealHit();
        world.restart(GameMode.WAVES);
        assertEquals(0, world.counterStreak);
        assertEquals(0, world.counterStreakBest);
        assertEquals(1, world.counterScoreMult);
        assertEquals(0f, world.counterChainBanner, 0f);
        assertEquals(0f, world.counterChainBreak, 0f);
        assertEquals(0, world.counterChainBreakAt);
        assertEquals(0, world.countersRead);
    }

    @Test
    public void theBannerAndTheBreakFadeOnTheirOwn() {
        triggerBoss(0);
        read(3);
        assertTrue(world.counterChainBanner > 0f);
        step((int) (GameWorld.CHAIN_BANNER_TIME * 60) + 6);
        assertEquals(0f, world.counterChainBanner, 0f);
        takeRealHit();
        assertTrue(world.counterChainBreak > 0f);
        step((int) (GameWorld.CHAIN_BREAK_TIME * 60) + 6);
        assertEquals(0f, world.counterChainBreak, 0f);
    }

    // ---- 真实一场打下来 ----

    @Test
    public void aRealFightBuildsTheChainOnItsOwn() {
        triggerBoss(5);
        int frames = 0;
        while (frames < 60 * 150 && world.counterStreak < GameWorld.CHAIN_STEP_READS) {
            world.update(1f / 60f);
            events.addAll(world.pollEvents());
            shieldFromHarm();            // 这一条只验"能不能自己接上串"，不验挨打
            frames++;
        }
        assertTrue("两分半还读不出三连，说明招与招之间根本不接上: 连读 "
                + world.counterStreak + " / 识破 " + world.countersRead,
                world.counterStreak >= GameWorld.CHAIN_STEP_READS);
        assertEquals(world.counterStreak, world.counterStreakBest);
        assertTrue("抬档那一下真的按翻倍计入总分",
                world.score >= world.countersRead * GameWorld.COUNTER_SCORE_BASE);
        assertEquals("三分半里最多也就 ×2，不该一步到顶",
                2, GameWorld.chainMultFor(world.counterStreak));
    }
}
