package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.56 迭代 46：枪骑 LANCER 与横向枪线冲锋。
 *
 * <p>旋翼者把"一条线"转起来，枪骑则把同一条线<b>横过来</b>：它压到上半屏架枪，
 * 枪线先跟着玩家高度走 0.9 秒，然后锁死、连着机身横穿整屏。它一颗子弹都不打，
 * 压力全在"这一整行不能站"——解法是看到方向箭头后纵向让开，而不是横向逃跑
 * （横向跑永远跑不过 1150 px/s 的冲锋）。测试钉住架枪跟踪与钳制、锁线事件、
 * 冲锋横越与安静退场、以及冲锋机体的加宽判定。
 */
public class LancerV156Test {

    private GameWorld world;
    private final List<GameWorld.GameEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261028L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.bountyEnabled = false;
        world.perksEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.lives = 999_999;
        world.ship.x = 450f;
        world.ship.prevX = 450f;
        world.ship.y = 1460f;
        world.ship.prevY = 1460f;
        world.ship.setTarget(450f, 1460f);
        events.clear();
    }

    private Enemy spawnLancer(float x) {
        for (Enemy e : world.enemies) {
            if (e.active) continue;
            e.init(EnemyType.LANCER, x, 0f, 1f);
            // 自动火力十几秒就能把它点掉；这些用例只关心枪线本身
            e.hp = e.maxHp = 9999f;
            return e;
        }
        throw new IllegalStateException("敌机池已满");
    }

    private void step(int frames) {
        for (int i = 0; i < frames; i++) world.update(1f / 60f);
    }

    private void parkShip(float x, float y) {
        world.ship.x = x;
        world.ship.prevX = x;
        world.ship.y = y;
        world.ship.prevY = y;
        world.ship.setTarget(x, y);
    }

    private void stepUntilAiming(Enemy e) {
        int guard = 0;
        while (!e.isLanceAiming() && guard++ < 400) world.update(1f / 60f);
        assertTrue("压到高位后应当进入架枪 guard=" + guard, e.isLanceAiming());
    }

    private void stepUntilCharging(Enemy e) {
        stepUntilAiming(e);
        int guard = 0;
        while (!e.isLanceCharging() && guard++ < 120) world.update(1f / 60f);
        assertTrue("预警结束应当冲锋 guard=" + guard, e.isLanceCharging());
    }

    private int countEvents(GameWorld.EventType type) {
        int n = 0;
        for (GameWorld.GameEvent e : events) if (e.type == type) n++;
        return n;
    }

    private int lastCode(GameWorld.EventType type) {
        int code = Integer.MIN_VALUE;
        for (GameWorld.GameEvent e : events) if (e.type == type) code = e.code;
        return code;
    }

    private int activeEnemyBullets() {
        int n = 0;
        for (EnemyBullet b : world.enemyBullets) if (b.active) n++;
        return n;
    }

    // ---- 架枪 ----

    @Test
    public void lancerDropsToHoverAndNeverFires() {
        Enemy e = spawnLancer(450f);
        assertEquals("枪骑不开普通炮", -1f, e.fireCd, 0.001f);
        stepUntilAiming(e);
        assertEquals("悬停在设计高度", Enemy.LANCER_HOVER_Y, e.y, 0.6f);
        step(30);
        assertEquals("架枪期同样不发射任何敌弹", 0, activeEnemyBullets());
        assertTrue("确实在瞄准", e.isLanceAiming());
    }

    @Test
    public void telegraphLaneFollowsTheShipButStaysInThePlayableBand() {
        Enemy e = spawnLancer(450f);
        parkShip(450f, 900f);
        stepUntilCharging(e);
        assertEquals("枪线跟到飞船高度", 900f, e.lanceLaneY, 2f);

        // 飞船跑到下沿：枪线仍被钳在 1280，不会一路跟到屏幕底
        Enemy low = spawnLancer(450f);
        parkShip(450f, 1580f);
        e.active = false;
        stepUntilAiming(low);
        stepUntilCharging(low);
        assertEquals("枪线钳在上限", Enemy.LANCER_LANE_MAX, low.lanceLaneY, 1f);

        // 反过来跑到上沿：钳到 520，避免枪骑贴脸导致无解
        Enemy high = spawnLancer(450f);
        parkShip(450f, 80f);
        low.active = false;
        stepUntilAiming(high);
        stepUntilCharging(high);
        assertEquals("枪线钳在下限", Enemy.LANCER_LANE_MIN, high.lanceLaneY, 1f);
    }

    @Test
    public void laneLocksAtTheEndOfTheTelegraph() {
        Enemy e = spawnLancer(450f);
        parkShip(450f, 700f);
        stepUntilAiming(e);
        step(45);   // 0.75 秒：还在预警里，枪线已经扑到 700
        stepUntilCharging(e);
        float lane = e.lanceLaneY;
        assertEquals("锁线高度就是玩家当时的高度", 700f, lane, 2f);
        parkShip(450f, 1200f);   // 锁定后玩家再挪高度，枪线不该跟着走
        step(30);
        assertEquals("锁线后枪线固定", lane, e.lanceLaneY, 0.001f);
        assertEquals("冲锋高度等于锁线高度", lane, e.y, 0.001f);
    }

    // ---- 锁线与冲锋 ----

    @Test
    public void lockEmitsChargeEventWithDirectionTowardThePlayer() {
        Enemy right = spawnLancer(650f);
        parkShip(200f, 800f);
        stepUntilCharging(right);
        events.addAll(world.pollEvents());
        assertEquals("玩家在左边就往左冲", -1, lastCode(GameWorld.EventType.LANCER_CHARGE));
        assertEquals("一次锁线只播一次事件", 1,
                countEvents(GameWorld.EventType.LANCER_CHARGE));

        Enemy left = spawnLancer(200f);
        parkShip(800f, 800f);
        right.active = false;   // 上一杆枪已验完，别让它的冲锋重置飞船位置
        stepUntilCharging(left);
        events.addAll(world.pollEvents());
        assertEquals("玩家在右边就往右冲", 1, lastCode(GameWorld.EventType.LANCER_CHARGE));
    }

    @Test
    public void chargeCrossesTheWholeScreenAndIsReapedQuietly() {
        Enemy e = spawnLancer(300f);
        parkShip(450f, 1460f);   // 站在下半屏，枪线跟不到这么低（钳 1280），冲锋打不到
        stepUntilCharging(e);
        float startX = e.x;
        step(20);
        assertTrue("冲锋确实在横向推进", Math.abs(e.x - startX) > 200f);
        assertEquals("冲锋途中高度不变", e.lanceLaneY, e.y, 1f);

        step(120);
        events.addAll(world.pollEvents());
        assertFalse("冲出画面后回收", e.active);
        assertEquals("枪骑退场不算逃逸", 0, countEvents(GameWorld.EventType.ENEMY_ESCAPED));
        assertTrue("玩家没站那行就不该掉命", world.hitsTaken == 0);
    }

    @Test
    public void chargingLancerHurtsThePlayerInItsLane() {
        Enemy e = spawnLancer(300f);
        parkShip(450f, 650f);
        stepUntilAiming(e);
        step(45);   // 枪线已经扑到 650，锁线就锁在这一行
        int livesBefore = world.lives;
        stepUntilCharging(e);
        step(40);
        assertTrue("站在枪线上会挨打", world.lives < livesBefore);
        assertEquals("一次冲锋只结算一次", 1, world.hitsTaken);
    }

    @Test
    public void steppingOutOfTheLaneAfterTheLockAvoidsTheCharge() {
        Enemy e = spawnLancer(300f);
        parkShip(450f, 650f);
        stepUntilAiming(e);
        step(45);
        stepUntilCharging(e);
        // 锁定之后往纵向挪开：这就是设计给玩家的解法
        parkShip(450f, 1050f);
        int livesBefore = world.lives;
        step(40);
        assertEquals("纵向让开就安全", livesBefore, world.lives);
        assertEquals(0, world.hitsTaken);
    }

    @Test
    public void chargingHullHasAWiderHitboxThanItsBody() {
        // 34（飞船）+ 42（机身）+ 18（冲锋加宽）= 94，85 在判定内、110 在判定外
        Enemy e = spawnLancer(300f);
        parkShip(450f, 650f);
        stepUntilAiming(e);
        step(45);
        stepUntilCharging(e);
        int livesBefore = world.lives;
        parkShip(450f, e.lanceLaneY + 85f);
        step(40);
        assertTrue("冲锋机体的判定被加宽", world.lives < livesBefore);

        Enemy e2 = spawnLancer(300f);
        parkShip(450f, 650f);
        stepUntilAiming(e2);
        step(45);
        stepUntilCharging(e2);
        int livesAfter = world.lives;
        int hitsAfter = world.hitsTaken;
        parkShip(450f, e2.lanceLaneY + 110f);
        step(40);
        assertEquals("加宽也只到 94，110 之外安全", livesAfter, world.lives);
        assertEquals(hitsAfter, world.hitsTaken);
    }

    // ---- 生命周期与出场 ----

    @Test
    public void killingItMidChargeEndsTheThreat() {
        Enemy e = spawnLancer(300f);
        parkShip(450f, 800f);
        stepUntilCharging(e);
        e.active = false;   // 冲锋途中被打爆
        int livesBefore = world.lives;
        step(60);
        assertEquals("枪骑没了，枪线也跟着消失", livesBefore, world.lives);
    }

    @Test
    public void lancerEntersTheGuaranteedEncounterRosterAt195Seconds() {
        boolean found = false;
        int idx = -1;
        for (int i = 0; i < GameWorld.V117_TYPES.length; i++) {
            if (GameWorld.V117_TYPES[i] == EnemyType.LANCER) {
                found = true;
                idx = i;
            }
        }
        assertTrue("枪骑应当在保底遭遇名单里", found);
        assertEquals("解锁窗口与常规刷新一致", 195f, GameWorld.V117_WINDOWS[idx], 0.01f);
        assertTrue("中后期舰型", EnemyType.LANCER.isAdvanced());
    }

    @Test
    public void ordinaryEnemiesAreNotReapedForLeavingTheSides() {
        Enemy e = spawnLancer(450f);
        Enemy drone = spawnLancer(750f);
        drone.init(EnemyType.DRONE, 850f, 300f, 1f);
        stepUntilCharging(e);
        step(120);
        events.addAll(world.pollEvents());
        assertFalse("枪骑已回收", e.active);
        assertTrue("普通敌机不受侧向回收规则影响", drone.active);
        assertEquals("只有枪骑会安静离场", 0,
                countEvents(GameWorld.EventType.ENEMY_ESCAPED));
    }
}
