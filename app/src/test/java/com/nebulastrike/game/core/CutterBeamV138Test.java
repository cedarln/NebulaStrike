package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.38 迭代 28：切割者 CUTTER 与贯穿光柱。
 *
 * <p>以前所有敌机攻击都是"弹"——能被点掉、能被弹反、能被反射护罩送回去。
 * 切割者换了一种考法：先锁定一列，再切下一道从舰体直落屏幕底的竖光柱；
 * 光柱挡不住也弹不回去，唯一解法是在预警期横向离开那一列。
 * 测试钉住瞄准跟踪、锁死后不再跟随、伤害每发只算一次、离列即安全，
 * 以及"充能期击毁就不会落光束"的反制窗口。
 */
public class CutterBeamV138Test {

    private GameWorld world;
    private final List<GameWorld.GameEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261025L);
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

    private Enemy spawnCutter(float x) {
        for (Enemy e : world.enemies) {
            if (e.active) continue;
            e.init(EnemyType.CUTTER, x, Enemy.CUTTER_HOVER_Y, 1f);
            // 自动火力会在几秒内把它点掉；这些用例只关心光束本身
            e.hp = e.maxHp = 9999f;
            e.fireCd = 0.02f;
            return e;
        }
        throw new IllegalStateException("敌机池已满");
    }

    private void step(int frames) {
        for (int i = 0; i < frames; i++) world.update(1f / 60f);
    }

    private void stepUntilFiring(Enemy e) {
        int guard = 0;
        while (!e.isBeamFiring() && guard++ < 180) world.update(1f / 60f);
        assertTrue("充能结束后应当开始切割 guard=" + guard, e.isBeamFiring());
    }

    private int activeEnemyBullets() {
        int n = 0;
        for (EnemyBullet b : world.enemyBullets) if (b.active) n++;
        return n;
    }

    private int beamEvents() {
        int n = 0;
        for (GameWorld.GameEvent e : events) {
            if (e.type == GameWorld.EventType.ENEMY_BEAM) n++;
        }
        return n;
    }

    // ---- 蓄力与开火 ----

    @Test
    public void cutterChargesThenCutsWithoutFiringBullets() {
        Enemy e = spawnCutter(450f);
        step(3);
        assertTrue("冷却到点后进入瞄准", e.isBeamCharging());
        assertTrue(e.beamTimer <= Enemy.CUTTER_TELL);
        assertEquals("刚起手时充能进度接近 0", 0f, e.beamCharge(), 0.1f);
        assertEquals("切割者不发射普通弹", 0, activeEnemyBullets());

        stepUntilFiring(e);
        events.addAll(world.pollEvents());
        assertEquals("一发光束应当只播一次事件", 1, beamEvents());
        int code = -1;
        for (GameWorld.GameEvent ev : events) {
            if (ev.type == GameWorld.EventType.ENEMY_BEAM) code = ev.code;
        }
        assertEquals("事件带上判定半宽", (int) Enemy.CUTTER_HALF_WIDTH, code);
        assertEquals("切割全程也没有普通弹", 0, activeEnemyBullets());
    }

    @Test
    public void aimTracksTheShipThenLocksForTheFinalBeat() {
        Enemy e = spawnCutter(200f);
        world.ship.x = 700f;
        world.ship.prevX = 700f;
        step(3);
        assertTrue(e.isBeamCharging());
        float startX = e.x;
        step(20);   // 约 1/3 秒
        assertTrue("充能前段应当朝飞船横向压过去 start=" + startX + " now=" + e.x,
                e.x > startX + 20f);

        // 进最后 0.28 秒：落点锁死，飞船换了位置也不再跟
        int guard = 0;
        while (e.isBeamCharging() && e.beamTimer > Enemy.CUTTER_LOCK && guard++ < 180) {
            world.update(1f / 60f);
        }
        assertTrue("应当进入锁定段", e.isBeamCharging());
        float lockedX = e.x;
        world.ship.x = 60f;
        step(6);
        assertTrue("锁死后不再跟随飞船 locked=" + lockedX + " now=" + e.x,
                Math.abs(e.x - lockedX) < 0.01f);
    }

    // ---- 伤害判定 ----

    @Test
    public void beamHitsAtMostOncePerShot() {
        Enemy e = spawnCutter(450f);
        step(3);
        int livesBefore = world.lives;
        stepUntilFiring(e);
        step(1);
        assertTrue("站在光束列里会挨打", world.lives < livesBefore);
        int livesAfter = world.lives;
        step(40);   // 光束还在切，但同一发不会二次结算
        assertEquals("一发光束只算一次伤害", livesAfter, world.lives);
        assertEquals("只记一次受击", 1, world.hitsTaken);
    }

    @Test
    public void steppingOutOfTheColumnAfterLockAvoidsTheBeam() {
        Enemy e = spawnCutter(450f);
        step(3);
        assertTrue(e.isBeamCharging());
        // 等光束落点锁死，再横移出这一列：这就是设计给玩家的解法
        int guard = 0;
        while (e.isBeamCharging() && e.beamTimer > Enemy.CUTTER_LOCK && guard++ < 180) {
            world.update(1f / 60f);
        }
        world.ship.x = 140f;
        world.ship.prevX = 140f;
        world.ship.setTarget(140f, 1460f);
        int livesBefore = world.lives;
        stepUntilFiring(e);
        step(40);
        assertEquals("离开光束列就安全", livesBefore, world.lives);
        assertFalse("没有命中时不该置位", e.beamHit);
    }

    @Test
    public void beamEndsAndCooldownHoldsBeforeTheNextAim() {
        Enemy e = spawnCutter(450f);
        step(3);
        // 先让飞船离开光束列：命中会触发停顿，测时长时要把这一层排除
        world.ship.x = 140f;
        world.ship.prevX = 140f;
        world.ship.setTarget(140f, 1460f);
        stepUntilFiring(e);
        step((int) Math.ceil(Enemy.CUTTER_FIRE * 60f) + 2);
        assertFalse("切割结束回到巡航", e.isBeamFiring());
        assertEquals("阶段清零", 0, e.beamPhase);
        assertEquals(0f, e.beamTimer, 0.001f);

        step(45);   // 0.75 秒：仍在冷却里
        assertFalse("冷却期间不应重新瞄准", e.isBeamCharging());
        assertTrue("冷却还没走完", e.fireCd > 0f);
    }

    // ---- 反制与生命周期 ----

    @Test
    public void killingItDuringTheChargeCancelsTheBeam() {
        Enemy e = spawnCutter(450f);
        step(3);
        assertTrue(e.isBeamCharging());
        events.addAll(world.pollEvents());
        int livesBefore = world.lives;

        e.active = false;   // 充能期被打掉
        step(120);
        events.addAll(world.pollEvents());
        assertEquals("没有落下的光束", 0, beamEvents());
        assertEquals("光束不会伤到飞船", livesBefore, world.lives);
    }

    @Test
    public void reinitialisingThePooledEnemyClearsBeamState() {
        Enemy e = spawnCutter(450f);
        step(3);
        assertTrue(e.isBeamCharging());
        e.beamHit = true;
        e.beamJustFired = true;

        e.init(EnemyType.CUTTER, 300f, Enemy.CUTTER_HOVER_Y, 1f);
        assertEquals("重新取用后阶段归零", 0, e.beamPhase);
        assertEquals(0f, e.beamTimer, 0.001f);
        assertFalse(e.beamHit);
        assertFalse(e.beamJustFired);
        assertFalse(e.isBeamFiring());
    }

    @Test
    public void cutterJoinsTheCodexAsALateGameType() {
        assertTrue("切割者属于后期兵种", EnemyType.CUTTER.isAdvanced());
        assertTrue("切割者的血量与分值应当是非零",
                EnemyType.CUTTER.hp > 0 && EnemyType.CUTTER.score > 0);
        assertTrue("切割者应排在图鉴靠后的位置",
                EnemyType.CUTTER.ordinal() >= Ui.CODEX_MAX_ROWS);
        int types = EnemyType.values().length;
        assertEquals("全部敌机仍应在两页内", 2, Ui.codexPageCount(types));
        assertTrue(Ui.codexPageCount(types) * Ui.CODEX_MAX_ROWS >= types);
    }
}
