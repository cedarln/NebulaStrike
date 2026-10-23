package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.24 迭代 14：电弧炮 ARC。
 *
 * <p>它是一把"点 → 面"的武器：弹体本身只有 2 点伤害，但命中后从弹着点向 260 半径内
 * 最近的敌机连续跳跃，每跳 1 点伤害。作为掉落武器，它和现有五把特殊武器一样叠层不替换；
 * 叠层同时增加弹数与跳跃次数。
 */
public class ArcWeaponTest {

    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(20261026L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.lives = 999_999;
        // 飞船靠左：自动机炮的弹道避开测试用的敌机列
        world.ship.x = 120f;
        world.ship.prevX = 120f;
        world.ship.y = 1460f;
        world.ship.prevY = 1460f;
    }

    // ---- 辅助 ----

    private Enemy enemyAt(EnemyType type, float x, float y, float hp) {
        for (Enemy e : world.enemies) {
            if (e.active) continue;
            e.init(type, x, y, 1f);
            if (hp > 0f) {
                e.maxHp = hp;
                e.hp = hp;
            }
            return e;
        }
        throw new IllegalStateException("敌机池已满");
    }

    private Bullet arcBulletAt(float x, float y, int jumps) {
        for (Bullet b : world.bullets) {
            if (b.active) continue;
            b.initArc(x, y, 0f, -900f, 2, jumps);
            return b;
        }
        throw new IllegalStateException("玩家弹池已满");
    }

    private int activeArcBullets() {
        int n = 0;
        for (Bullet b : world.bullets) if (b.active && b.arc) n++;
        return n;
    }

    private List<GameWorld.GameEvent> step(int frames) {
        List<GameWorld.GameEvent> all = new ArrayList<>();
        for (int i = 0; i < frames; i++) {
            world.update(1f / 60f);
            all.addAll(world.pollEvents());
        }
        return all;
    }

    private static int lastArcCode(List<GameWorld.GameEvent> events) {
        int code = -1;
        for (GameWorld.GameEvent e : events) {
            if (e.type == GameWorld.EventType.ARC_CHAIN) code = e.code;
        }
        return code;
    }

    // ---- 叠加 / 掉落 ----

    @Test
    public void arcStacksWithoutReplacingOtherWeapons() {
        world.spawnPowerUp(PowerUp.Kind.LASER, world.ship.x, world.ship.y);
        world.update(1f / 60f);
        world.spawnPowerUp(PowerUp.Kind.ARC, world.ship.x, world.ship.y);
        world.update(1f / 60f);

        assertTrue("电弧炮应与激光并存", world.ship.isWeaponEquipped(WeaponType.ARC));
        assertTrue("原有激光不能被替换掉", world.ship.isWeaponEquipped(WeaponType.LASER));
        assertEquals(1, world.ship.stackOf(WeaponType.ARC));

        world.spawnPowerUp(PowerUp.Kind.ARC, world.ship.x, world.ship.y);
        world.update(1f / 60f);
        assertEquals("同类电弧炮叠层", 2, world.ship.stackOf(WeaponType.ARC));
        assertFalse("电弧炮是掉落武器，不是融合武器", WeaponType.ARC.isFused());
    }

    @Test
    public void arcVolleyLaunchesArcBullets() {
        world.ship.addWeapon(WeaponType.ARC);
        world.resetWeaponCooldowns();
        world.update(1f / 60f);

        Bullet arc = null;
        for (Bullet b : world.bullets) {
            if (b.active && b.arc) {
                arc = b;
                break;
            }
        }
        assertNotNull("电弧炮应该发射带电弧标记的弹体", arc);
        assertEquals(2, arc.damage);
        assertTrue("电弧弹朝上飞", arc.vy < 0f);
        assertTrue("至少能跳一次", arc.arcJumps >= 1);
    }

    /** 两层叠起来同时加弹数与跳数：这是"叠加不替换"在数值上的兑现。 */
    @Test
    public void stackingRaisesOrbCountAndJumps() {
        world.ship.addWeapon(WeaponType.ARC);
        world.ship.addWeapon(WeaponType.ARC);
        world.resetWeaponCooldowns();
        world.update(1f / 60f);

        int orbs = activeArcBullets();
        assertEquals("两层电弧炮一次两发", 2, orbs);
        for (Bullet b : world.bullets) {
            if (b.active && b.arc) assertEquals("两层时跳 3 次", 3, b.arcJumps);
        }
    }

    @Test
    public void dropTableCarvesOutArcWeight() {
        assertEquals(PowerUp.Kind.GRAVITY, GameWorld.dropKind(0.94f));
        assertEquals(PowerUp.Kind.ARC, GameWorld.dropKind(0.96f));
        assertEquals(PowerUp.Kind.REFLECT, GameWorld.dropKind(0.98f));
        // 原有核心武器权重不动
        assertEquals(PowerUp.Kind.MISSILE, GameWorld.dropKind(0.30f));
        assertEquals(PowerUp.Kind.SPREAD, GameWorld.dropKind(0.05f));
    }

    // ---- 连锁行为 ----

    @Test
    public void hitChainsToNearbyEnemies() {
        Enemy primary = enemyAt(EnemyType.TANK, 450f, 1100f, 200f);
        Enemy second = enemyAt(EnemyType.DRONE, 560f, 1060f, 1f);
        Enemy third = enemyAt(EnemyType.DRONE, 360f, 1040f, 1f);
        arcBulletAt(450f, 1200f, 3);
        List<GameWorld.GameEvent> events = step(30);

        assertTrue("主目标受到弹体伤害", primary.hp < 200f);
        assertFalse("连锁应清掉第一个近邻", second.active);
        assertFalse("连锁应清掉第二个近邻", third.active);
        assertEquals("一次命中连锁两个目标", 2, lastArcCode(events));
    }

    /** 相位中的敌机不参与连锁：把唯一的近邻让给后面那只实体机。 */
    @Test
    public void chainSkipsPhasedEnemy() {
        Enemy primary = enemyAt(EnemyType.TANK, 450f, 1100f, 200f);
        Enemy ghost = enemyAt(EnemyType.PHASER, 450f, 1020f, 6f);
        ghost.phasePhase = 2; // 强制相位化
        ghost.phaseTimer = 5f; // 足够覆盖整段测试，别在连锁前重新实体化
        Enemy behind = enemyAt(EnemyType.DRONE, 450f, 940f, 1f);
        arcBulletAt(450f, 1200f, 1);
        step(30);

        assertTrue("实体主目标照常受伤", primary.hp < 200f);
        assertEquals("相位者不该被连锁扣血", 6f, ghost.hp, 0.001f);
        assertFalse("连锁应越过相位者打到后方的实体机", behind.active);
    }

    /** 每跳只有 1 点伤害，主目标仍是完整的 2 点。 */
    @Test
    public void chainJumpsDealOneDamageEach() {
        Enemy primary = enemyAt(EnemyType.TANK, 450f, 1100f, 200f);
        Enemy second = enemyAt(EnemyType.CARRIER, 560f, 1060f, 12f);
        arcBulletAt(450f, 1200f, 2);
        step(30);

        assertTrue("主目标吃满弹体的 2 点（暴击 4 点也算）",
                primary.hp == 198f || primary.hp == 196f);
        assertEquals("连锁每跳 1 点", 11f, second.hp, 0.001f);
    }

    @Test
    public void chainStopsAtJumpsLimit() {
        Enemy primary = enemyAt(EnemyType.TANK, 450f, 1100f, 200f);
        Enemy a = enemyAt(EnemyType.CARRIER, 560f, 1060f, 12f);
        Enemy b = enemyAt(EnemyType.CARRIER, 360f, 1040f, 12f);
        Enemy untouched = enemyAt(EnemyType.CARRIER, 450f, 980f, 12f);
        arcBulletAt(450f, 1200f, 1);
        step(30);

        assertEquals("只跳一次", 11f, a.hp + b.hp - 12f, 0.001f);
        assertEquals("未参与连锁的目标满血", 12f, untouched.hp, 0.001f);
    }

    @Test
    public void arcBulletWithoutTargetsFiresNothingExtra() {
        arcBulletAt(450f, 1200f, 2);
        List<GameWorld.GameEvent> events = step(30);
        assertEquals("空场没有连锁事件", -1, lastArcCode(events));
    }
}
