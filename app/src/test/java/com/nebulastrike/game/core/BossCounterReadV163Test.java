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
 * v1.63 迭代 53：破招 · COUNTER READING。
 *
 * <p>v1.61 让八艘旗舰各丢一手招牌弹幕，v1.62 让它们各说各话；这一版教你<b>怎么读</b>：
 * 招牌出手时舰体下方跟一句破招提示，这一轮弹幕整批散尽而没打中你，就算识破——
 * 一笔赏金、一声确认、一条读数。判定只看这一轮的弹：常规扩散环、弹幕网、扇面都不参与，
 * 掉血立刻作废（护盾挡下的那一下不算掉血，与残响的规矩一致）。
 */
public class BossCounterReadV163Test {

    private GameWorld world;
    private final List<GameWorld.GameEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261119L);
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

    /** 叫出这一段的旗舰并跳过入场演出。 */
    private void triggerBoss(int level) {
        if (world.boss.active) {
            world.boss.hp = 0f;   // 上一艘还挂着就先沉掉，否则"新旗舰"是同一艘
            step(1);
        }
        world.bossGapTimer = 0f;
        world.stageClearTimer = 0f;
        world.bossesDefeated = level;
        world.wave = world.nextBossWave;
        step(1);
        assertTrue("旗舰该入场", world.boss.active);
        world.bossWarningTimer = 0f;
        world.bossPhaseBreakTimer = 0f;
        world.boss.y = world.boss.stationY;
        drain();
    }

    /** 场上这一轮招牌的轮次号（没有招牌弹时为 0）。 */
    private int signatureSerialInPlay() {
        int serial = 0;
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active && b.sigSerial > serial) serial = b.sigSerial;
        }
        return serial;
    }

    /** 只把指定轮次的招牌弹收干净。 */
    private void clearVolley(int serial) {
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active && b.sigSerial == serial) b.active = false;
        }
    }

    /** 场上还活着的"这一轮招牌"弹数。 */
    private int liveSignatureBullets() {
        int n = 0;
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active && b.sigSerial != 0) n++;
        }
        return n;
    }

    /** 把某一轮招牌的弹全部收掉（等效于玩家把这轮躲完 / 点干净）。 */
    private void clearSignatureBullets() {
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active && b.sigSerial != 0) b.active = false;
        }
    }

    private void survive() {
        world.ship.makeInvulnerable(9999f);
    }

    // ---- 破招提示本身 ----

    @Test
    public void everyFormExplainsHowToReadItsSignature() {
        Set<String> tips = new HashSet<>();
        for (Boss.Form f : Boss.Form.values()) {
            assertTrue(f.name() + " 缺破招提示", f.counter != null && f.counter.length() > 4);
            assertTrue(f.name() + " 提示太长: " + f.counter, f.counter.length() <= 24);
            assertTrue("提示要分成两拍才好读: " + f.counter, f.counter.contains(" · "));
            assertTrue("破招提示不许撞车: " + f.counter, tips.add(f.counter));
            assertFalse(f.name() + " 的提示不该就是台词",
                    f.counter.equals(f.taunt) || f.counter.equals(f.rage));
        }
        assertEquals("八型旗舰八句破招", Boss.Form.values().length, tips.size());
    }

    // ---- 轮次标记 ----

    @Test
    public void aSignatureVolleySharesOneSerialAndOrdinaryBulletsHaveNone() {
        triggerBoss(3);
        int made = world.fireSignature(world.boss.form, world.boss.tier);
        assertTrue("招牌要真的丢出弹", made > 0);
        int serial = 0;
        int tagged = 0;
        for (EnemyBullet b : world.enemyBullets) {
            if (!b.active || b.sigSerial == 0) continue;
            if (serial == 0) serial = b.sigSerial;
            assertEquals("同一轮招牌共用一个轮次号", serial, b.sigSerial);
            tagged++;
        }
        assertEquals("标记数就是这轮的弹数", made, tagged);

        world.fireNet(6, 1, 180f);
        int untagged = 0;
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active && b.sigSerial == 0) untagged++;
        }
        assertTrue("扩散网也得出弹（中间留缝，列数会少两三发）", untagged > 0);
        assertEquals("扩散网这类常规弹幕不参与识破判定", made, liveSignatureBullets());
    }

    @Test
    public void aVolleyThatVanishesWithoutTouchingYouPays() {
        triggerBoss(2);
        Boss.Form form = world.boss.form;
        int scoreBefore = world.score;
        int made = world.fireSignature(form, world.boss.tier);
        assertTrue(made > 0);
        assertEquals("刚出手还不算读懂", 0, world.countersRead);

        clearSignatureBullets();
        step(1);

        assertEquals("散尽即识破", 1, world.countersRead);
        assertEquals("赏金 180 + 20 × 舰级",
                GameWorld.COUNTER_SCORE_BASE + 20 * world.boss.level,
                world.score - scoreBefore);
        assertEquals(1, countEvents(GameWorld.EventType.BOSS_COUNTER));
        assertEquals("读的是刚才那一型", form, world.counterForm);
        assertTrue("HUD 有余辉", world.counterFlash > 0f);
        assertTrue("分数也飘出来了", world.activeFloatTexts() > 0);
    }

    @Test
    public void biggerHullsPayMoreForTheSameRead() {
        triggerBoss(0);
        int first = world.score;
        world.fireSignature(Boss.Form.SPECTER, 0);
        clearSignatureBullets();
        step(1);
        int paid = world.score - first;
        assertEquals("第一段航程的识破赏金", GameWorld.COUNTER_SCORE_BASE, paid);

        triggerBoss(4);
        int second = world.score;
        world.fireSignature(Boss.Form.SPECTER, 3);
        clearSignatureBullets();
        step(1);
        assertTrue("舰级越高这一手越值钱", world.score - second > paid);
        assertEquals(2, world.countersRead);
    }

    @Test
    public void onlyTheNewestVolleyIsJudged() {
        triggerBoss(1);
        world.fireSignature(Boss.Form.SPECTER, 1);
        int oldest = signatureSerialInPlay();
        assertTrue("第一轮要有弹", oldest > 0);
        world.fireSignature(Boss.Form.CINDER, 1);
        int newest = signatureSerialInPlay();
        assertTrue("第二轮的轮次号要更新", newest > oldest);
        assertEquals("两轮同时在场上", 2, volleyCount());

        // 只把旧的那轮散尽：判定跟的是最新一轮，旧轮散尽不该白送
        clearVolley(oldest);
        step(1);
        assertEquals("旧轮散尽不算识破", 0, world.countersRead);

        clearVolley(newest);
        step(1);
        assertEquals("最新那轮散尽才算", 1, world.countersRead);
    }

    /** 场上有几轮不同的招牌弹。 */
    private int volleyCount() {
        Set<Integer> serials = new HashSet<>();
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active && b.sigSerial != 0) serials.add(b.sigSerial);
        }
        return serials.size();
    }

    @Test
    public void lettingTheVolleyHangTooLongIsNotARead() {
        triggerBoss(1);
        world.fireSignature(Boss.Form.SPECTER, 1);
        int made = liveSignatureBullets();
        assertTrue(made > 0);

        // 把这轮弹一直摁在屏幕中间：判定窗口走完就该作废
        for (int i = 0; i < (int) (GameWorld.COUNTER_WINDOW * 60f) + 30; i++) {
            for (EnemyBullet b : world.enemyBullets) {
                if (b.active && b.sigSerial != 0) {
                    b.x = 450f;
                    b.y = 700f;
                }
            }
            step(1);
        }
        assertEquals("窗口到点就没资格了", 0, world.countersRead);

        clearSignatureBullets();
        step(1);
        assertEquals("散尽也不补发迟到的识破", 0, world.countersRead);
    }

    // ---- 失败与例外 ----

    @Test
    public void takingAHitSpoilsTheRead() {
        triggerBoss(1);
        world.lives = 99;
        world.ship.hasShield = false;
        world.ship.shieldLayers = 0;
        world.ship.makeInvulnerable(0f);
        assertTrue("招牌要真的丢出弹", world.fireSignature(Boss.Form.SPECTER, 1) > 0);
        // 只留一发并摁在飞船身上：挨这一下，这一轮就作废
        int serial = signatureSerialInPlay();
        clearVolleyExcept(serial, 1);
        step(2);
        assertEquals("被打中的那一轮不算识破", 0, world.countersRead);
        assertTrue(world.hitsTaken > 0);
    }

    @Test
    public void aShieldBlockIsStillACleanRead() {
        triggerBoss(1);
        world.lives = 99;
        world.ship.addShield();
        world.ship.makeInvulnerable(0f);
        int scoreBefore = world.score;
        world.fireSignature(Boss.Form.SPECTER, 1);
        int serial = signatureSerialInPlay();
        clearVolleyExcept(serial, 1);
        step(2);
        assertEquals("护盾挡下的那一下不算掉血", 1, world.countersRead);
        assertTrue("识破赏金照给", world.score > scoreBefore);
    }

    /** 只留 count 枚这一轮的招牌弹，并把它们叠到飞船身上（判定用的最小场景）。 */
    private void clearVolleyExcept(int serial, int count) {
        int left = count;
        for (EnemyBullet b : world.enemyBullets) {
            if (!b.active || b.sigSerial != serial) continue;
            if (left > 0) {
                b.x = world.ship.x;
                b.y = world.ship.y;
                b.vx = 0f;
                b.vy = 0f;
                left--;
            } else {
                b.active = false;
            }
        }
        assertEquals("招牌弹足够", 0, left);
    }

    @Test
    public void aReadChangesNothingButTheScore() {
        triggerBoss(1);
        int lives = world.lives;
        int power = world.ship.powerLevel;
        int drops = 0;
        for (PowerUp p : world.powerups) if (p.active) drops++;
        world.fireSignature(Boss.Form.MIRROR, 1);
        clearSignatureBullets();
        step(30);
        assertEquals("识破不掉补给", drops, countDrops());
        assertEquals("识破不扣命", lives, world.lives);
        assertEquals("识破不动火力", power, world.ship.powerLevel);
        assertEquals(1, world.countersRead);
    }

    private int countDrops() {
        int n = 0;
        for (PowerUp p : world.powerups) if (p.active) n++;
        return n;
    }

    // ---- 模式边界 ----

    @Test
    public void theRealFightPaysReadsOnItsOwn() {
        triggerBoss(5);
        survive();
        int reads = 0;
        for (int i = 0; i < 60 * 45; i++) {
            world.update(1f / 60f);
            events.addAll(world.pollEvents());
            if (world.countersRead > reads) reads = world.countersRead;
        }
        assertTrue("实战里招牌一轮接一轮，读得出来就该有进账：" + reads, reads >= 2);
        assertEquals(reads, world.countersRead);
    }

    @Test
    public void everyModeKeepsTheSameReading() {
        for (GameMode m : new GameMode[]{GameMode.PRACTICE, GameMode.RUSH, GameMode.ECHO,
                GameMode.TIME}) {
            world.restart(m);
            world.spawningEnabled = false;
            world.perksEnabled = false;
            survive();
            drain();
            int reads = 0;
            for (int i = 0; i < 60 * 60 && reads < 1; i++) {
                if (!world.boss.active) {
                    // 没有小怪推进航程的模式（练功 / 连战）自己会开，其余手动把这一段的旗舰叫来
                    world.bossGapTimer = 0f;
                    world.stageClearTimer = 0f;
                    world.wave = world.nextBossWave;
                }
                world.update(1f / 60f);
                world.pollEvents();
                reads = world.countersRead;
            }
            assertTrue(m + " 也该能读招", reads >= 1);
        }
    }

    @Test
    public void aNewBossDoesNotInheritThePendingRead() {
        triggerBoss(1);
        world.fireSignature(Boss.Form.SPECTER, 1);
        int oldVolley = signatureSerialInPlay();
        assertTrue(oldVolley > 0);
        world.boss.hp = 0f;
        step(1);
        assertFalse(world.boss.active);
        triggerBoss(2);
        int reads = world.countersRead;

        clearVolley(oldVolley);
        step(2);
        assertEquals("上一艘船那轮散尽不再补分", reads, world.countersRead);
    }

    @Test
    public void restartClearsEveryReadTrace() {
        triggerBoss(1);
        world.fireSignature(Boss.Form.HIVE, 1);
        clearSignatureBullets();
        step(1);
        assertTrue(world.countersRead > 0);
        assertTrue(world.counterFlash > 0f);

        world.restart(GameMode.WAVES);
        assertEquals(0, world.countersRead);
        assertEquals(0f, world.counterFlash, 0.001f);
        assertEquals(0, world.counterScore);
        clearSignatureBullets();
        step(2);
        assertEquals("重开不补发上一局的识破", 0, world.countersRead);
    }

    @Test
    public void theEchoIsReadJustLikeTheRealHull() {
        triggerBoss(0);
        survive();
        world.bossFightHits = 1;          // 这一场不干净：残响不成立，换个方式直读
        world.boss.hp = 0f;
        step(1);
        assertFalse(world.boss.active);
        assertEquals("脏仗不留残响", -1, world.echoPendingTier);

        world.bossGapTimer = 0f;
        world.stageClearTimer = 0f;
        world.wave = world.nextBossWave;
        step(1);
        assertTrue(world.boss.active);
        Boss.Form form = world.boss.form;
        world.fireSignature(form, world.boss.tier);
        clearSignatureBullets();
        step(1);
        assertEquals("残影之外照常判读", 1, world.countersRead);
        assertEquals(form, world.counterForm);
    }
}
