package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.42 迭代 32：旋翼者 GYRE 与旋转光刃。
 *
 * <p>切割者把"一整列不能站"做成了一道静态的竖线；旋翼者把同一条线转起来：
 * 起手线固定指向飞船，光刃绕轮毂扫过 158°。玩家不能靠"跑出射程"躲——
 * 光刃够得着整个屏幕——只能判断扫向、绕到它还没扫到的那一侧。
 * 测试钉住起手瞄准、扫向与角速度、一次旋转只结算一次伤害、
 * 离开整条线即安全、以及充能期击毁可以取消光刃。
 */
public class GyreV142Test {

    private GameWorld world;
    private final List<GameWorld.GameEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261027L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.bountyEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.lives = 999_999;
        world.ship.x = 450f;
        world.ship.prevX = 450f;
        world.ship.y = 1460f;
        world.ship.prevY = 1460f;
        events.clear();
    }

    private Enemy spawnGyre(float x) {
        for (Enemy e : world.enemies) {
            if (e.active) continue;
            e.init(EnemyType.GYRE, x, Enemy.GYRE_HOVER_Y, 1f);
            // 自动火力会在几秒内把它点掉；这些用例只关心光刃本身
            e.hp = e.maxHp = 9999f;
            return e;
        }
        throw new IllegalStateException("敌机池已满");
    }

    private void step(int frames) {
        for (int i = 0; i < frames; i++) world.update(1f / 60f);
    }

    private void stepUntilCharging(Enemy e) {
        int guard = 0;
        while (!e.isGyreCharging() && guard++ < 240) world.update(1f / 60f);
        assertTrue("蓄能结束后应当进入预警 guard=" + guard, e.isGyreCharging());
    }

    private void stepUntilSpinning(Enemy e) {
        stepUntilCharging(e);
        int guard = 0;
        while (!e.isGyreSpinning() && guard++ < 120) world.update(1f / 60f);
        assertTrue("预警结束后应当起转 guard=" + guard, e.isGyreSpinning());
    }

    private int activeEnemyBullets() {
        int n = 0;
        for (EnemyBullet b : world.enemyBullets) if (b.active) n++;
        return n;
    }

    private int spinEvents() {
        int n = 0;
        for (GameWorld.GameEvent e : events) {
            if (e.type == GameWorld.EventType.GYRE_SPIN) n++;
        }
        return n;
    }

    /** 把飞船停到"这次扫不到"的扇区里：取下方半圈里离扫过区间最远的角度。 */
    private void parkInGyreBlindSpot(Enemy e) {
        float sweep = Enemy.GYRE_SPIN_RATE * Enemy.GYRE_SPIN_TIME;
        float start = e.gyreAngle;
        float end = start + e.gyreSpinDir * sweep;
        float lo = Math.min(start, end);
        float hi = Math.max(start, end);
        float best = (float) (Math.PI / 2);
        float bestGap = -1f;
        for (int deg = 20; deg <= 160; deg += 10) {
            float a = (float) Math.toRadians(deg);
            float gap = a < lo ? lo - a : a > hi ? a - hi : -1f;
            if (gap > bestGap) {
                bestGap = gap;
                best = a;
            }
        }
        assertTrue("盲区角度应当远离扫过的扇区 gap=" + bestGap, bestGap > 0.4f);
        float px = e.x + (float) Math.cos(best) * 380f;
        float py = e.y + (float) Math.sin(best) * 380f;
        world.ship.x = px;
        world.ship.prevX = px;
        world.ship.y = py;
        world.ship.prevY = py;
        world.ship.setTarget(px, py);
    }

    // ---- 蓄能与起转 ----

    @Test
    public void gyreChargesThenSpinsWithoutFiringBullets() {
        Enemy e = spawnGyre(450f);
        assertEquals("旋翼者不开普通炮", -1f, e.fireCd, 0.001f);
        step(3);
        assertFalse("刚到位还在蓄能巡游", e.isGyreCharging());

        stepUntilCharging(e);
        assertEquals("刚起手时预警进度接近 0", 0f, e.gyreCharge(), 0.1f);
        assertEquals("旋翼者没有普通弹", 0, activeEnemyBullets());

        stepUntilSpinning(e);
        events.addAll(world.pollEvents());
        assertEquals("一次起转只播一次事件", 1, spinEvents());
        int code = 0;
        for (GameWorld.GameEvent ev : events) {
            if (ev.type == GameWorld.EventType.GYRE_SPIN) code = ev.code;
        }
        assertEquals("事件带上扫向", e.gyreSpinDir > 0f ? 1 : -1, code);
        assertEquals("旋转全程也没有普通弹", 0, activeEnemyBullets());
    }

    @Test
    public void telegraphAimsAtTheShipAndSweepsInOneDirection() {
        Enemy e = spawnGyre(300f);
        world.ship.x = 700f;
        world.ship.prevX = 700f;
        world.ship.setTarget(700f, 1460f);
        stepUntilCharging(e);
        float expected = (float) Math.atan2(world.ship.y - e.y, world.ship.x - e.x);
        assertEquals("起手线指向飞船", expected, e.gyreAngle, 0.06f);

        float start = e.gyreAngle;
        parkInGyreBlindSpot(e);
        stepUntilSpinning(e);
        step(30);   // 半秒
        float delta = e.gyreAngle - start;
        if (e.gyreSpinDir > 0f) {
            assertTrue("顺时针扫过应当角度递增 delta=" + delta, delta > 0.3f);
        } else {
            assertTrue("逆时针扫过应当角度递减 delta=" + delta, delta < -0.3f);
        }
        assertEquals("半秒约转 0.575 弧度", 0.5f * Enemy.GYRE_SPIN_RATE, Math.abs(delta), 0.08f);
    }

    // ---- 伤害判定 ----

    @Test
    public void beamHitsAtMostOncePerSpin() {
        Enemy e = spawnGyre(450f);   // 正下方就是飞船：起手线直落命中
        stepUntilCharging(e);
        int livesBefore = world.lives;
        stepUntilSpinning(e);
        step(2);
        assertTrue("站在光刃线里会挨打", world.lives < livesBefore);
        int livesAfter = world.lives;
        step(60);   // 光刃还在转，但同一次旋转不会二次结算
        assertEquals("一次旋转只算一次伤害", livesAfter, world.lives);
        assertEquals("只记一次受击", 1, world.hitsTaken);
        assertTrue("命中后置位", e.gyreHit);
    }

    @Test
    public void steppingOutOfTheLineDuringTheTelegraphAvoidsTheBeam() {
        Enemy e = spawnGyre(450f);
        stepUntilCharging(e);
        // 起手线已经指向飞船；预警期挪到这次扫不到的扇区，这就是设计给玩家的解法
        parkInGyreBlindSpot(e);
        int livesBefore = world.lives;
        stepUntilSpinning(e);
        step(60);
        assertEquals("离开光刃线就安全", livesBefore, world.lives);
        assertFalse("没有命中时不该置位", e.gyreHit);
    }

    @Test
    public void spinEndsAndReturnsToCruiseBeforeTheNextTelegraph() {
        Enemy e = spawnGyre(450f);
        stepUntilCharging(e);
        parkInGyreBlindSpot(e);
        stepUntilSpinning(e);
        step((int) Math.ceil(Enemy.GYRE_SPIN_TIME * 60f) + 8);
        assertFalse("扫完回到巡航", e.isGyreSpinning());
        assertEquals("阶段清零", 0, e.beamPhase);
        assertEquals(0f, e.gyreTimer, 0.001f);

        step(45);   // 0.75 秒：仍在新一轮蓄能里
        assertFalse("蓄能期间不应重新起手", e.isGyreCharging());
        assertTrue("蓄能计时还在走", e.gyreRest > 0f);
    }

    // ---- 反制与生命周期 ----

    @Test
    public void killingItDuringTheChargeCancelsTheSpin() {
        Enemy e = spawnGyre(450f);
        stepUntilCharging(e);
        events.addAll(world.pollEvents());
        int livesBefore = world.lives;

        e.active = false;   // 充能期被打掉
        step(120);
        events.addAll(world.pollEvents());
        assertEquals("没有起转", 0, spinEvents());
        assertEquals("光刃不会伤到飞船", livesBefore, world.lives);
    }

    @Test
    public void reinitialisingThePooledEnemyClearsGyreState() {
        Enemy e = spawnGyre(300f);
        stepUntilCharging(e);
        e.gyreHit = true;
        e.gyreJustStarted = true;

        e.init(EnemyType.GYRE, 300f, Enemy.GYRE_HOVER_Y, 1f);
        assertEquals("重新取用后阶段归零", 0, e.beamPhase);
        assertEquals("预警计时归零", 0f, e.gyreTimer, 0.001f);
        assertEquals("起手线回到正下方", (float) (Math.PI / 2), e.gyreAngle, 0.001f);
        assertEquals("第一轮蓄能是短档", 1.2f, e.gyreRest, 0.001f);
        assertFalse(e.gyreHit);
        assertFalse(e.gyreJustStarted);
        assertFalse(e.isGyreSpinning());
    }

    @Test
    public void gyreJoinsTheCodexAsALateGameType() {
        assertTrue("旋翼者属于后期兵种", EnemyType.GYRE.isAdvanced());
        assertTrue("旋翼者的血量与分值应当是非零",
                EnemyType.GYRE.hp > 0 && EnemyType.GYRE.score > 0);
        assertTrue("旋翼者应排在图鉴靠后的位置",
                EnemyType.GYRE.ordinal() >= Ui.CODEX_MAX_ROWS);
        int types = EnemyType.values().length;
        assertEquals("全部敌机仍应在两页内", 2, Ui.codexPageCount(types));
        assertTrue(Ui.codexPageCount(types) * Ui.CODEX_MAX_ROWS >= types);
        // 图鉴位图是 int：新增舰型必须仍落在 31 位以内，老存档的 sanitize 才不会吞掉它
        assertTrue("旋翼者的图鉴位必须可用", EnemyType.GYRE.ordinal() < 31);
        assertFalse(world.settings.enemySeen(EnemyType.GYRE));
    }
}
