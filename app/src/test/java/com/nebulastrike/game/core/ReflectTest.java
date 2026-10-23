package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.23 迭代 13：反射护罩 REFLECT。
 *
 * <p>它跟护盾的区别在"方向"：护盾把命中吞掉，反射把这一发直接翻面成友军火力，
 * 而且层数不消耗，只进入 6 秒充能。两层各自独立充能，捡到第二片是纯增益，
 * 符合"道具叠加而不是切换"的既有约定。
 */
public class ReflectTest {

    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(20261025L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.lives = 999_999;
        world.ship.x = 450f;
        world.ship.prevX = 450f;
        world.ship.y = 1460f;
        world.ship.prevY = 1460f;
    }

    // ---- 辅助 ----

    private EnemyBullet enemyBulletAt(float x, float y, float vx, float vy) {
        for (EnemyBullet eb : world.enemyBullets) {
            if (eb.active) continue;
            eb.init(x, y, vx, vy);
            return eb;
        }
        throw new IllegalStateException("敌弹池已满");
    }

    private Enemy enemyAt(EnemyType type, float x, float y) {
        for (Enemy e : world.enemies) {
            if (e.active) continue;
            e.init(type, x, y, 1f);
            return e;
        }
        throw new IllegalStateException("敌机池已满");
    }

    private void collectReflect() {
        world.spawnPowerUp(PowerUp.Kind.REFLECT, world.ship.x, world.ship.y);
        world.update(1f / 60f);
    }

    private Bullet firstReflected() {
        for (Bullet b : world.bullets) {
            if (b.active && b.reflected) return b;
        }
        return null;
    }

    private List<GameWorld.GameEvent> step(int frames) {
        List<GameWorld.GameEvent> all = new ArrayList<>();
        for (int i = 0; i < frames; i++) {
            world.update(1f / 60f);
            all.addAll(world.pollEvents());
        }
        return all;
    }

    private static boolean has(List<GameWorld.GameEvent> events, GameWorld.EventType type) {
        for (GameWorld.GameEvent e : events) if (e.type == type) return true;
        return false;
    }

    // ---- 叠层 ----

    @Test
    public void reflectLayersStackToTwoThenPaySalvageScore() {
        collectReflect();
        collectReflect();
        assertEquals(2, world.ship.reflectLayers);
        int scoreBefore = world.score;
        collectReflect();
        assertEquals("满层后不再叠加", 2, world.ship.reflectLayers);
        assertTrue("第三片折算奖励分", world.score > scoreBefore);
    }

    /** 消耗的是充能而不是层数——这是"叠加"约定的核心，不能退化成一次性护罩。 */
    @Test
    public void consumingOneReflectKeepsTheLayerAndStartsRecharge() {
        world.ship.addReflect();
        assertTrue(world.ship.consumeReflect());
        assertEquals(1, world.ship.reflectLayers);
        assertEquals(-1, world.ship.readyReflectLayer());
        assertEquals(Ship.REFLECT_RECHARGE, world.ship.reflectCd[0], 0.001f);
        assertFalse("充能期间不能再弹", world.ship.consumeReflect());
    }

    @Test
    public void chargeRefillsAfterSixSeconds() {
        world.ship.addReflect();
        assertTrue(world.ship.consumeReflect());
        step(370);
        assertEquals(1, world.ship.reflectLayers);
        assertEquals(0, world.ship.readyReflectLayer());
        assertTrue("充满后回到可用状态", world.ship.reflectCd[0] <= 0f);
    }

    /** 两层各自计时：用掉第一层时第二层仍然就绪，不会一起进入充能。 */
    @Test
    public void secondLayerStaysReadyWhenFirstIsSpent() {
        world.ship.addReflect();
        world.ship.addReflect();
        assertTrue(world.ship.consumeReflect());
        assertEquals(1, world.ship.readyReflectLayer());
        assertEquals(0f, world.ship.reflectCd[1], 0.001f);
        assertTrue(world.ship.consumeReflect());
        assertEquals(-1, world.ship.readyReflectLayer());
    }

    // ---- 战斗行为 ----

    @Test
    public void enemyBulletBecomesFriendlyFireInsteadOfHitting() {
        world.ship.addReflect();
        enemyBulletAt(world.ship.x, world.ship.y, 0f, 420f);
        List<GameWorld.GameEvent> events = step(2);

        assertFalse("反射成功时不该算受击", has(events, GameWorld.EventType.PLAYER_HIT));
        assertTrue("要发出反射事件", has(events, GameWorld.EventType.REFLECT));
        Bullet out = firstReflected();
        assertNotNull("敌弹应翻面成友军子弹", out);
        assertEquals(2, out.damage);
        assertTrue("必须朝上飞回去", out.vy < -300f);
        assertEquals("层数不消耗", 1, world.ship.reflectLayers);
    }

    /** 反射弹真的能伤到敌机：用斜向弹把友军火力送到炮口之外的目标。 */
    @Test
    public void reflectedBulletDamagesEnemy() {
        world.ship.addReflect();
        Enemy tank = enemyAt(EnemyType.TANK, 660f, 1300f);
        float hpBefore = tank.hp;
        // 敌弹从左侧斜着撞船，反射后带正横向速度飞向右上方
        enemyBulletAt(world.ship.x, world.ship.y, -800f, 400f);
        step(30);

        assertTrue("反射弹应命中目标", tank.hp < hpBefore || !tank.active);
    }

    @Test
    public void reflectedBulletCannotHurtShipAgain() {
        world.ship.addReflect();
        enemyBulletAt(world.ship.x, world.ship.y, 0f, 420f);
        List<GameWorld.GameEvent> events = step(60);
        assertFalse(has(events, GameWorld.EventType.PLAYER_HIT));
        assertEquals(999_999, world.lives);
    }

    /** 反射优先于护盾：挡下同一发时护盾不掉层，玩家两份资源都保住。 */
    @Test
    public void reflectTakesPriorityWithoutBurningShield() {
        world.ship.addShield();
        world.ship.addReflect();
        int layersBefore = world.ship.shieldLayers;
        enemyBulletAt(world.ship.x, world.ship.y, 0f, 420f);
        List<GameWorld.GameEvent> events = step(2);

        assertTrue(has(events, GameWorld.EventType.REFLECT));
        assertFalse(has(events, GameWorld.EventType.SHIELD_BLOCK));
        assertEquals(layersBefore, world.ship.shieldLayers);
    }

    @Test
    public void chargingLayerLetsBulletThrough() {
        world.ship.addReflect();
        assertTrue(world.ship.consumeReflect());
        int livesBefore = world.lives;
        enemyBulletAt(world.ship.x, world.ship.y, 0f, 420f);
        List<GameWorld.GameEvent> events = step(2);

        assertTrue("充能中应按普通受击处理", has(events, GameWorld.EventType.PLAYER_HIT));
        assertTrue(world.lives < livesBefore);
        assertNull(firstReflected());
    }

    // ---- 事件 / 掉落表 ----

    @Test
    public void eventCodeReportsWhetherAnotherLayerIsStillReady() {
        world.ship.addReflect();
        enemyBulletAt(world.ship.x, world.ship.y, 0f, 420f);
        int lastOnlyLayer = -1;
        for (GameWorld.GameEvent e : step(2)) {
            if (e.type == GameWorld.EventType.REFLECT) lastOnlyLayer = e.code;
        }
        assertEquals("最后一层用掉后 code 为 0", 0, lastOnlyLayer);

        // 补满两层并让第一层重新就绪：这次消耗后仍有一层可用，code 应为 1
        world.ship.reflectCd[0] = 0f;
        world.ship.addReflect();
        enemyBulletAt(world.ship.x + 40f, world.ship.y, 0f, 420f);
        int withSpare = -1;
        for (GameWorld.GameEvent e : step(2)) {
            if (e.type == GameWorld.EventType.REFLECT) withSpare = e.code;
        }
        assertEquals("还有就绪层时 code 为 1", 1, withSpare);
    }

    @Test
    public void dropTableServesReflectBeforeTurret() {
        assertEquals(PowerUp.Kind.GRAVITY, GameWorld.dropKind(0.96f));
        assertEquals(PowerUp.Kind.REFLECT, GameWorld.dropKind(0.985f));
        assertEquals(PowerUp.Kind.TURRET, GameWorld.dropKind(0.999f));
    }
}
