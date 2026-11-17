package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.68 迭代 58：形态终式 FORM FINALE · 那句狠话是要兑现的。
 *
 * <p>v1.61 给了每型旗舰一张自己的脸和一招收场，v1.62 让它说一句"我认真了"的狠话。可三阶段真的到了，
 * 场上多出来的只有更密的环和更密的网——那句话没换成任何具体招式。这一版把它兑现：<b>三阶段起，
 * 招牌与终式交替出手</b>，终式是这一型独有的第二手，弹道更贪、读法也不一样。
 *
 * <p>四条口径：① 终式只在三阶段之后出，一、二阶段与上一版一模一样；② 读终式与读招牌记同一型的账，
 * 精通、连读、反击窗口、双份解药全部照旧接管，终式不多发一分赏金；③ 精通的降速只作用于打了轮次号的
 * 招牌/终式弹，常规环网不受影响——这条边界由本测试守着；④ 掉落契约、模拟战与残响规则一概不改。
 */
public class BossFormFinaleV168Test {

    private GameWorld world;
    private final List<GameWorld.GameEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261124L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.bountyEnabled = false;
        world.perksEnabled = false;
        world.settings.reducedFx = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.lives = 999_999;
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

    /** 叫出这一段的旗舰、跳过演出、压掉词缀。 */
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
        world.ship.makeInvulnerable(9999f);
        drain();
    }

    /**
     * 把旗舰钉在第几阶段：血量拉到 100 万，只按 frac 定阶段——
     * 打不沉，也不会因为玩家子弹磨血而偷偷换段。
     */
    private void pinPhase(int phase) {
        world.boss.maxHp = 1_000_000f;
        world.boss.hp = phase == 1 ? 900_000f : phase == 2 ? 500_000f : 200_000f;
        for (int i = 0; i < world.boss.partCount; i++) {
            BossPart p = world.boss.parts[i];
            p.maxHp = 1_000_000f;
            p.hp = 1_000_000f;
        }
        step(1);
        assertEquals("阶段该钉住", phase, world.boss.phase);
    }

    private void clearBullets() {
        for (Bullet b : world.bullets) b.active = false;
        for (EnemyBullet b : world.enemyBullets) b.active = false;
    }

    /** 把旗舰一击收掉，让掉落当场结算。 */
    private void sinkBoss() {
        world.boss.maxHp = 1f;
        world.boss.hp = 0f;
        step(2);
        assertFalse("旗舰该沉了", world.boss.active);
    }

    private void clearPowerUps() {
        for (PowerUp p : world.powerups) p.active = false;
    }

    private int activePowerUps() {
        int n = 0;
        for (PowerUp p : world.powerups) if (p.active) n++;
        return n;
    }

    /** 场上还活着、且带着招牌/终式轮次号的敌弹。 */
    private int taggedBullets() {
        int n = 0;
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active && b.sigSerial != 0) n++;
        }
        return n;
    }

    private void killTaggedBullets() {
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active && b.sigSerial != 0) b.active = false;
        }
    }

    /** 让本型旗舰当场丢一手终式。 */
    private int fireFinale() {
        return world.fireSignature(world.boss.form, world.boss.tier, true);
    }

    /** 丢一手终式并让它当场散尽：识破、连读、反击窗口都在这一刻结算。 */
    private void readOneFinale() {
        assertTrue("终式要真的丢出弹", fireFinale() > 0);
        killTaggedBullets();
        step(2);
    }

    /** 丢一手招牌（非终式），同样当场散尽。 */
    private void readOneSignature() {
        assertTrue("招牌要真的丢出弹",
                world.fireSignature(world.boss.form, world.boss.tier) > 0);
        killTaggedBullets();
        step(2);
    }

    /** 活着的招牌/终式弹的平均速率——精通降速量的是这个。 */
    private float meanTaggedSpeed() {
        float sum = 0f;
        int n = 0;
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active && b.sigSerial != 0) {
                sum += (float) Math.sqrt(b.vx * b.vx + b.vy * b.vy);
                n++;
            }
        }
        return n == 0 ? 0f : sum / n;
    }

    // ---- 形态数据 ----

    @Test
    public void everyFormHasItsOwnFinale() {
        Set<String> names = new HashSet<>();
        for (Boss.Form f : Boss.Form.values()) {
            assertTrue(f.name() + " 要有终式名", f.finale != null && f.finale.length() > 0);
            assertTrue(f.name() + " 要有终式读法",
                    f.finaleCounter != null && f.finaleCounter.length() > 0);
            assertTrue(f.name() + " 的终式不能就是招牌", !f.finale.equals(
                    f.signature.substring(f.signature.indexOf(" · ") + 3)));
            assertTrue(f.name() + " 的终式读法要区别于招牌读法",
                    !f.finaleCounter.equals(f.counter));
            assertTrue("终式名不能重复", names.add(f.finale));
        }
        assertEquals("八型各有一手", Boss.Form.values().length, names.size());
    }

    @Test
    public void finaleNamesStayOnePhrase() {
        // 渲染层把"招式名 · "那层前缀切掉再打，终式名得是一句完整的话
        for (Boss.Form f : Boss.Form.values()) {
            assertFalse(f.name() + " 的终式名里不该再有分隔点", f.finale.contains("·"));
            assertTrue(f.name() + " 的终式名要短", f.finale.length() <= 6);
        }
    }

    // ---- 出手门槛：只有三阶段 ----

    @Test
    public void theFinaleOnlyOpensAtPhaseThree() {
        triggerBoss(3);
        pinPhase(2);
        step(1_800);
        assertTrue("二阶段照样该出招牌", countEvents(GameWorld.EventType.BOSS_SIGNATURE) > 0);
        assertEquals("二阶段一发终式都不该出",
                0, countEvents(GameWorld.EventType.BOSS_FINALE));
        assertFalse("读数也该停在招牌上", world.signatureFinale);
        assertEquals(0, world.finaleFired);

        pinPhase(3);
        drain();
        step(1_800);
        assertTrue("三阶段该出终式了", countEvents(GameWorld.EventType.BOSS_FINALE) > 0);
        assertTrue(world.finaleFired > 0);
    }

    @Test
    public void signatureAndFinaleTakeTurns() {
        triggerBoss(4);
        pinPhase(3);
        drain();
        step(3_600);
        int sig = countEvents(GameWorld.EventType.BOSS_SIGNATURE);
        int fin = countEvents(GameWorld.EventType.BOSS_FINALE);
        assertTrue("三阶段仍然照常出招牌", sig > 0);
        assertTrue("终式也得出场", fin > 0);
        assertTrue("两者该是交替的，不该一边倒：" + sig + "/" + fin,
                Math.abs(sig - fin) <= 2);
        assertTrue("终式统计跟着事件走", world.finaleFired >= fin);
    }

    @Test
    public void theFinaleEventCarriesItsForm() {
        triggerBoss(2);
        pinPhase(3);
        int before = world.finaleFired;
        fireFinale();
        step(1);
        GameWorld.GameEvent e = null;
        for (GameWorld.GameEvent ev : events) {
            if (ev.type == GameWorld.EventType.BOSS_FINALE) e = ev;
        }
        assertTrue("该广播一次终式", e != null);
        assertEquals("code 就是形态序号", world.boss.form.ordinal(), e.code);
        assertEquals("招牌事件不该跟着一起响",
                0, countEvents(GameWorld.EventType.BOSS_SIGNATURE));
        assertTrue(world.signatureFinale);
        assertEquals(before + 1, world.finaleFired);
    }

    // ---- 每一型的终式都得真的打出弹 ----

    @Test
    public void everyFormActuallyPutsFinaleBulletsOnTheField() {
        triggerBoss(3);
        for (Boss.Form f : Boss.Form.values()) {
            world.boss.applyForm(f);
            clearBullets();
            int made = fireFinale();
            assertTrue(f.name() + " 的终式要打出弹", made > 0);
            assertEquals(f.name() + " 打出的弹都该带上本轮轮次号", made, taggedBullets());
            assertTrue(f.name() + " 的终式别把弹池榨干：" + made, made <= 40);
        }
    }

    @Test
    public void aFullBulletPoolJustMakesFewerBullets() {
        triggerBoss(3);
        pinPhase(3);
        for (EnemyBullet b : world.enemyBullets) {
            b.init(10f, 10f, 0f, -1f);
            b.sigSerial = 0;
        }
        int made = fireFinale();
        assertEquals("池子满了就是一发都加不上", 0, made);
        step(30);
        assertTrue("满了也不该把战斗卡住", world.boss.active);
    }

    // ---- 读招账本：终式与招牌记同一格 ----

    @Test
    public void readingTheFinaleGoesOnTheSameLedger() {
        triggerBoss(2);
        pinPhase(3);
        Boss.Form f = world.boss.form;
        int readsBefore = world.formReads[f.ordinal()];
        int countersBefore = world.countersRead;
        readOneFinale();
        assertEquals("终式读懂了照旧记本型账", readsBefore + 1, world.formReads[f.ordinal()]);
        assertEquals(countersBefore + 1, world.countersRead);
        assertEquals("另外单独统计它是终式", 1, world.finaleReads);
        assertTrue("识破那行该标出这是终式", world.counterFromFinale);
    }

    @Test
    public void readingTheSignatureIsNotAFinaleRead() {
        triggerBoss(2);
        pinPhase(3);
        readOneSignature();
        assertEquals(1, world.countersRead);
        assertEquals("招牌不该混进终式统计", 0, world.finaleReads);
        assertFalse("招牌那一读不该标终式", world.counterFromFinale);
    }

    @Test
    public void signatureAndFinaleReadsStackIntoMastery() {
        triggerBoss(2);
        pinPhase(3);
        Boss.Form f = world.boss.form;
        readOneSignature();
        readOneFinale();
        readOneSignature();
        readOneFinale();
        assertEquals("四手全记同一型", 4, world.formReads[f.ordinal()]);
        assertEquals(GameWorld.MASTERY_MASTERED_READS, world.formReads[f.ordinal()]);
        assertTrue("该升到读透了", world.masteryTier(f) >= 2);
        assertEquals(2, world.finaleReads);
    }

    @Test
    public void theFinalePaysExactlyWhatTheSignaturePays() {
        triggerBoss(3);
        pinPhase(3);
        world.counterStreak = 0;
        world.counterScoreMult = 1;
        readOneSignature();
        int signaturePrice = world.counterScore;
        assertEquals("单价还是那套老公式", GameWorld.COUNTER_SCORE_BASE + 20 * world.boss.level,
                signaturePrice);

        world.counterStreak = 0;
        world.counterScoreMult = 1;
        readOneFinale();
        assertEquals("终式不另开价目表", signaturePrice, world.counterScore);
    }

    @Test
    public void theFinaleOpensTheCounterStrikeWindow() {
        triggerBoss(3);
        pinPhase(3);
        readOneFinale();
        assertTrue("读懂终式一样开反击窗口", world.counterStrikeTimer > 0f);
        assertEquals("窗口记的是刚读懂的那一型", world.boss.form, world.counterStrikeForm);
    }

    @Test
    public void theChainKeepsClimbingThroughFinales() {
        triggerBoss(3);
        pinPhase(3);
        readOneFinale();
        readOneSignature();
        readOneFinale();
        assertEquals("三手连读", 3, world.counterStreak);
        assertEquals(2, world.counterScoreMult);
        assertEquals(2, world.finaleReads);
    }

    // ---- 精通的降速照样只咬轮次号 ----

    @Test
    public void masterySlowsTheFinaleToo() {
        triggerBoss(3);
        pinPhase(3);
        Boss.Form f = world.boss.form;
        world.formReads[f.ordinal()] = 0;
        clearBullets();
        assertTrue(fireFinale() > 0);
        float raw = meanTaggedSpeed();
        assertTrue(raw > 0f);
        killTaggedBullets();

        world.formReads[f.ordinal()] = GameWorld.MASTERY_MASTERED_READS;
        clearBullets();
        assertTrue(fireFinale() > 0);
        float slowed = meanTaggedSpeed();
        float expected = GameWorld.signatureSlowFactor(2);
        assertEquals("终式吃的就是精通那一档降速", raw * expected, slowed, raw * 0.02f + 1f);
        assertTrue("读透之后确实慢了", slowed < raw);
    }

    @Test
    public void theFinaleNeverTouchesOrdinaryDanmakuSpeed() {
        triggerBoss(3);
        pinPhase(3);
        Boss.Form f = world.boss.form;
        world.formReads[f.ordinal()] = GameWorld.MASTERY_MASTERED_READS;
        clearBullets();
        // 先塞一发不带轮次号的"常规弹"，再丢终式：它一滴速度都不该掉
        EnemyBullet plain = world.enemyBullets[0];
        plain.init(300f, 300f, 120f, 240f);
        plain.sigSerial = 0;
        float speedBefore = (float) Math.sqrt(plain.vx * plain.vx + plain.vy * plain.vy);
        assertTrue(fireFinale() > 0);
        float speedAfter = (float) Math.sqrt(plain.vx * plain.vx + plain.vy * plain.vy);
        assertEquals("没打轮次号的弹不归终式管", speedBefore, speedAfter, 0.01f);
    }

    // ---- 边界：掉落、词缀、重开 ----

    @Test
    public void theFinaleDoesNotChangeTheDrops() {
        triggerBoss(3);
        pinPhase(3);
        clearPowerUps();
        sinkBoss();
        int plain = activePowerUps();
        assertTrue("掉落照旧要有", plain > 0);

        triggerBoss(3);
        pinPhase(3);
        Boss.Form f = world.boss.form;
        readOneFinale();
        assertEquals("读过一手终式离读透还差三步，不该触发双份解药", 0, world.supplyDoubles);
        clearPowerUps();
        sinkBoss();
        assertEquals("读过终式也不会多掉一件", plain, activePowerUps());
        int supply = 0;
        for (PowerUp p : world.powerups) if (p.active && p.kind == f.supply) supply++;
        assertEquals("解药照旧一件", 1, supply);
    }

    @Test
    public void affixesDoNotChangeTheFinale() {
        triggerBoss(3);
        pinPhase(3);
        clearBullets();
        int bare = fireFinale();
        killTaggedBullets();
        world.applyBossAffix(BossAffix.BULWARK);
        clearBullets();
        int armored = fireFinale();
        assertEquals("词缀不改终式的弹数", bare, armored);
        world.applyBossAffix(BossAffix.NONE);
    }

    @Test
    public void restartClearsEveryTraceOfTheFinale() {
        triggerBoss(3);
        pinPhase(3);
        readOneFinale();
        assertTrue(world.finaleFired > 0);
        assertTrue(world.finaleReads > 0);
        assertTrue(world.signatureFinale);
        assertTrue(world.counterFromFinale);
        world.restart(GameMode.WAVES);
        assertFalse(world.counterFromFinale);
        assertEquals(0, world.finaleFired);
        assertEquals(0, world.finaleReads);
        assertFalse(world.signatureFinale);
        for (Boss.Form f : Boss.Form.values()) {
            assertEquals(0, world.formReads[f.ordinal()]);
        }
    }

    @Test
    public void aRealPhaseThreeFightReadsFinalesOnItsOwn() {
        triggerBoss(5);
        pinPhase(3);
        drain();
        // 无敌的飞船 + 六分钟：让真实节拍自己把终式丢出来、自己散尽、自己记账
        for (int i = 0; i < 60 * 240 && world.finaleReads == 0; i++) {
            world.boss.hp = Math.max(world.boss.hp, 200_000f);
            step(1);
        }
        assertTrue("实战里终式该被读懂过", world.finaleReads > 0);
        assertTrue("连读账也照旧在走", world.counterStreak > 0 || world.counterStreakBest > 0);
    }

    @Test
    public void theEchoShadeFiresTheSameFinale() {
        triggerBoss(3);
        pinPhase(3);
        world.echoBossFight = true;
        Boss.Form f = world.boss.form;
        readOneFinale();
        assertEquals("残影的终式记在同一型账上", 1, world.formReads[f.ordinal()]);
        assertEquals(1, world.finaleReads);
        world.echoBossFight = false;
    }
}
