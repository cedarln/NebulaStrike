package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * v1.48 迭代 38：枢链者 LINKER · 电弧锁链。
 *
 * <p>枢链者从不发射子弹：一对从上方压到中场，用一条锁链把两人的位置连起来。
 * 悬停期锁链架在玩家头顶并缓慢横移贴住玩家；充能 0.9 秒（停止跟船、细线预警）后
 * 一起俯冲，通电的锁链扫过整片活动带。躲法是横移出走廊，或先点掉任意一端断链。
 */
public class LinkerV148Test {

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

    private Enemy[] spawnPair(GameWorld w, float centerX) {
        w.spawnLinkerPairForTest(centerX);
        Enemy left = null, right = null;
        for (Enemy e : w.enemies) {
            if (!e.active || e.type != EnemyType.LINKER) continue;
            if (e.linkSide < 0) left = e;
            else right = e;
        }
        assertTrue("一对枢链者应当都入场", left != null && right != null);
        return new Enemy[]{left, right};
    }

    private int activeLinkers(GameWorld w) {
        int n = 0;
        for (Enemy e : w.enemies) if (e.active && e.type == EnemyType.LINKER) n++;
        return n;
    }

    private void step(GameWorld w, int frames) {
        for (int i = 0; i < frames; i++) w.update(1f / 60f);
    }

    /** 把两只直接放到悬停高度，跳过 3.5 秒的入场下降。 */
    private Enemy[] atHover(GameWorld w, float centerX) {
        Enemy[] pair = spawnPair(w, centerX);
        pair[0].y = Enemy.LINK_HOVER_Y;
        pair[1].y = Enemy.LINK_HOVER_Y;
        return pair;
    }

    /** 立刻进入通电下扫（不经过充能），方便单测伤害判定。 */
    private Enemy[] forceDive(Enemy[] pair) {
        for (Enemy e : pair) {
            e.linkPhase = 2;
            e.linkTimer = Enemy.LINK_DIVE_TIME;
            e.linkHit = false;
        }
        return pair;
    }

    private void makeVulnerable(GameWorld w, float x, float y) {
        w.ship.invulnerable = false;
        w.ship.dashTimer = 0f;
        w.ship.x = x;
        w.ship.prevX = x;
        w.ship.y = y;
        w.ship.prevY = y;
        // Ship.update 会向 targetX/targetY 收敛：不设目标的话，位置会在下一帧被拉回去
        w.ship.setTarget(x, y);
    }

    private List<GameWorld.GameEvent> drain(GameWorld w) {
        List<GameWorld.GameEvent> out = new ArrayList<>();
        for (GameWorld.GameEvent e : w.pollEvents()) out.add(e);
        return out;
    }

    private boolean has(List<GameWorld.GameEvent> events, GameWorld.EventType type) {
        for (GameWorld.GameEvent e : events) if (e.type == type) return true;
        return false;
    }

    // ---- 成对登场 ----

    @Test
    public void pairSpawnsTogetherAndFlanksEvenly() {
        GameWorld w = newGame(2026L);
        Enemy[] pair = spawnPair(w, 450f);

        assertEquals("两只共用一条锁链编号", pair[0].linkId, pair[1].linkId);
        assertNotEquals("编号必须是有效值", -1, pair[0].linkId);
        assertEquals(-1, pair[0].linkSide);
        assertEquals(1, pair[1].linkSide);
        assertEquals("左右各半距", 300f, pair[1].x - pair[0].x, 0.01f);
        assertEquals("同一高度入场", pair[0].y, pair[1].y, 0.001f);
        assertEquals("枢链者不开炮", -1f, pair[0].fireCd, 0.001f);
        assertEquals(2, activeLinkers(w));
    }

    @Test
    public void pairDescendsTogetherAndStopsAtHoverHeight() {
        GameWorld w = newGame(2026L);
        Enemy[] pair = spawnPair(w, 450f);
        step(w, 60 * 4);   // 入场下降约 3.5 秒，4 秒时已经落位、还没开始充能

        assertEquals(Enemy.LINK_HOVER_Y, pair[0].y, 0.5f);
        assertEquals(pair[0].y, pair[1].y, 0.001f);
        assertEquals("锁链始终保持水平", pair[0].y, pair[1].y, 0.001f);
    }

    @Test
    public void pairSpawnIsClampedInsideTheScreen() {
        GameWorld w = newGame(2026L);
        Enemy[] pair = spawnPair(w, 40f);   // 靠左边缘：整对要被推回屏内
        float r = EnemyType.LINKER.radius;
        assertTrue(pair[0].x >= r);
        assertTrue(pair[1].x <= GameWorld.WIDTH - r);
        assertEquals(300f, pair[1].x - pair[0].x, 0.01f);
    }

    // ---- 锁链节拍 ----

    @Test
    public void tetherChargesBeforeItTurnsLive() {
        GameWorld w = newGame(2026L);
        Enemy[] pair = atHover(w, 450f);
        pair[0].linkTimer = 0.02f;
        pair[1].linkTimer = 0.02f;
        step(w, 2);

        assertTrue("先进入充能预警", pair[0].isLinkCharging());
        assertFalse("充能期不结算伤害", pair[0].isLinkLive());
        assertTrue("这时已经能看见锁链", pair[0].hasLinkTether());

        // 充能进度应该从 0 涨到 1，而不是瞬间通电
        float c0 = pair[0].linkCharge();
        step(w, 27);   // 0.45 秒
        float c1 = pair[0].linkCharge();
        assertTrue("充能进度应当推进: " + c0 + " -> " + c1, c1 > c0);
        assertFalse("0.45 秒还没到通电", pair[0].isLinkLive());

        step(w, 32);   // 再 0.53 秒，越过 0.9 秒预警
        assertTrue("预警结束才通电", pair[0].isLinkLive());
        assertEquals("两端同步通电", 2, pair[1].linkPhase);
    }

    @Test
    public void chargedTetherDoesNotHurtDuringTheWarning() {
        GameWorld w = newGame(2026L);
        Enemy[] pair = atHover(w, 450f);
        makeVulnerable(w, 450f, Enemy.LINK_HOVER_Y);
        pair[0].linkTimer = 0.02f;
        pair[1].linkTimer = 0.02f;
        step(w, 2);
        assertTrue(pair[0].isLinkCharging());

        step(w, 20);   // 仍然在 0.9 秒预警之内
        assertEquals("预警期的线只是提示，不扣命", 0, w.hitsTaken);
    }

    @Test
    public void pulseEventIsAnnouncedOncePerPair() {
        GameWorld w = newGame(2026L);
        Enemy[] pair = atHover(w, 450f);
        pair[0].linkTimer = 0.02f;
        pair[1].linkTimer = 0.02f;
        drain(w);
        step(w, 2);              // 进入充能
        pair[0].linkTimer = 0.01f;
        pair[1].linkTimer = 0.01f;
        step(w, 2);              // 通电

        int pulses = 0;
        for (GameWorld.GameEvent e : drain(w)) {
            if (e.type == GameWorld.EventType.LINK_PULSE) {
                pulses++;
                assertEquals("事件带上锁链全长", 300, e.code);
            }
        }
        assertEquals("一对只播一次报点", 1, pulses);
    }

    @Test
    public void trackingStopsWhenTheTetherCharges() {
        GameWorld w = newGame(2026L);
        Enemy[] pair = atHover(w, 450f);
        makeVulnerable(w, 450f, 1460f);

        // 蓄势期会跟着飞船走：把船挪到 700，锁链中心应当缓慢追过去
        float before = (pair[0].x + pair[1].x) * 0.5f;
        pair[0].linkTimer = 1.2f;
        pair[1].linkTimer = 1.2f;
        w.ship.x = 700f;
        w.ship.prevX = 700f;
        w.ship.setTarget(700f, 1460f);
        step(w, 36);
        float tracked = (pair[0].x + pair[1].x) * 0.5f;
        assertTrue("蓄势期应当向玩家横移: " + before + " -> " + tracked, tracked > before + 30f);

        // 进入充能后停止跟船：玩家往反方向跑，走廊不再追
        pair[0].linkTimer = 0.02f;
        pair[1].linkTimer = 0.02f;
        step(w, 2);
        assertTrue(pair[0].isLinkCharging());
        float locked = (pair[0].x + pair[1].x) * 0.5f;
        w.ship.x = 120f;
        w.ship.prevX = 120f;
        w.ship.setTarget(120f, 1460f);
        step(w, 30);
        assertEquals("充能期必须锁死落点", locked,
                (pair[0].x + pair[1].x) * 0.5f, 0.5f);
    }

    @Test
    public void tetherComesBackAfterTheFullCycle() {
        GameWorld w = newGame(2026L);
        Enemy[] pair = atHover(w, 450f);
        pair[0].linkPhase = 1;
        pair[0].linkTimer = 0.01f;
        pair[1].linkPhase = 1;
        pair[1].linkTimer = 0.01f;
        step(w, 2);
        assertTrue(pair[0].isLinkLive());

        // 下扫剩余 1.58s + 回收 1.0s + 蓄势 1.8s；这里停在蓄势中段
        step(w, (int) ((Enemy.LINK_RISE_TIME + 0.5f) * 60f)
                + (int) (Enemy.LINK_DIVE_TIME * 60f));
        assertEquals("回收之后回到蓄势", 0, pair[0].linkPhase);
        assertEquals(Enemy.LINK_HOVER_Y, pair[0].y, 0.5f);

        pair[0].linkTimer = 0.02f;
        pair[1].linkTimer = 0.02f;
        step(w, 2);
        assertTrue("第二轮的充能照样会来", pair[0].isLinkCharging());
    }

    // ---- 伤害判定 ----

    @Test
    public void liveTetherSweepingThroughTheShipCostsALife() {
        GameWorld w = newGame(2026L);
        Enemy[] pair = forceDive(atHover(w, 450f));
        int lives = w.lives;
        makeVulnerable(w, 450f, 900f);

        step(w, 60);   // 锁链从 620 扫到 1300，必然经过 900

        assertEquals("站在走廊正中会被扫到", lives - 1, w.lives);
        assertEquals(1, w.hitsTaken);
        assertTrue("两端都记下这次命中", pair[0].linkHit && pair[1].linkHit);
    }

    @Test
    public void oneDiveHitsAtMostOnce() {
        GameWorld w = newGame(2026L);
        Enemy[] pair = forceDive(atHover(w, 450f));
        makeVulnerable(w, 450f, 900f);

        for (int i = 0; i < 90; i++) {
            // 把飞船按在锁链上，模拟"没有离开走廊"的极端情况
            w.ship.invulnerable = false;
            w.ship.dashTimer = 0f;
            w.ship.x = 450f;
            w.ship.prevX = 450f;
            w.ship.y = 900f;
            w.ship.prevY = 900f;
            w.ship.setTarget(450f, 900f);
            w.update(1f / 60f);
        }

        assertEquals("一次下扫只结算一次", 1, w.hitsTaken);
        assertTrue(pair[0].linkHit);
    }

    @Test
    public void standingOutsideTheEndsIsSafe() {
        GameWorld w = newGame(2026L);
        forceDive(atHover(w, 450f));   // 两端在 300 / 600
        makeVulnerable(w, 100f, 900f);

        step(w, 60);

        assertEquals("走廊之外不吃锁链", 0, w.hitsTaken);
        assertEquals(3, w.lives);
    }

    // ---- 拆链 ----

    @Test
    public void killingOneEndBreaksTheTether() {
        GameWorld w = newGame(2026L);
        Enemy[] pair = atHover(w, 450f);
        pair[0].linkPhase = 1;
        pair[0].linkTimer = 0.5f;
        pair[1].linkPhase = 1;
        pair[1].linkTimer = 0.5f;
        step(w, 1);
        assertTrue(pair[1].hasLinkTether());

        w.killEnemyForTest(pair[0]);
        step(w, 2);

        assertFalse("搭档一死锁链立刻消失", pair[1].hasLinkTether());
        assertFalse(pair[1].isLinkLive());
        assertTrue("幸存者失稳", pair[1].linkLost);
    }

    @Test
    public void survivorFleesAfterTheLinkBreaks() {
        GameWorld w = newGame(2026L);
        Enemy[] pair = atHover(w, 450f);
        w.killEnemyForTest(pair[0]);
        step(w, 2);
        float y0 = pair[1].y;
        step(w, 60);

        assertTrue("断链的幸存者俯冲离场: " + y0 + " -> " + pair[1].y, pair[1].y > y0 + 80f);
    }

    @Test
    public void brokenTetherCannotDamageTheShip() {
        GameWorld w = newGame(2026L);
        Enemy[] pair = forceDive(atHover(w, 450f));
        w.killEnemyForTest(pair[0]);
        makeVulnerable(w, 450f, 900f);
        step(w, 60);

        assertEquals("链断了就不该再有伤害", 0, w.hitsTaken);
    }

    @Test
    public void loneLinkerCannotFormATether() {
        GameWorld w = newGame(2026L);
        // 把池子占满到只剩一个空位：第二只放不出来，第一只应当立刻失稳离场，
        // 而不是留在场上架着一条没有对端的"孤链"
        int filled = 0;
        for (Enemy e : w.enemies) {
            if (filled >= 47) break;
            e.init(EnemyType.DRONE, -400f, -400f, 1f);
            filled++;
        }
        w.spawnLinkerPairForTest(450f);

        assertEquals("只放出一只", 1, activeLinkers(w));
        Enemy lone = null;
        for (Enemy e : w.enemies) if (e.active && e.type == EnemyType.LINKER) lone = e;
        assertTrue(lone.linkLost);
        assertFalse(lone.hasLinkTether());

        step(w, 10);
        assertEquals(0, w.hitsTaken);
    }

    @Test
    public void restartClearsThePairBookkeeping() {
        GameWorld w = newGame(2026L);
        spawnPair(w, 450f);
        w.restart(GameMode.WAVES);

        assertEquals("重开不残留枢链者", 0, activeLinkers(w));
        Enemy[] fresh = spawnPair(w, 450f);
        assertTrue(fresh[0].linkId > 0);
        assertEquals(2, activeLinkers(w));
    }
}
