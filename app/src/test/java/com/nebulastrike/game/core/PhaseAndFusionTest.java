package com.nebulastrike.game.core;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * v1.9 新增交互系统的回归测试：相位冲刺、轨道球、连锁闪电、武器融合与顿帧。
 */
public class PhaseAndFusionTest {
    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(4242L);
        world.restart(GameMode.WAVES);
        world.spawningEnabled = false;
    }

    private Enemy freeEnemy() {
        for (Enemy e : world.enemies) if (!e.active) return e;
        throw new AssertionError("no free enemy slot");
    }

    private EnemyBullet freeBulletSlot() {
        for (EnemyBullet b : world.enemyBullets) if (!b.active) return b;
        throw new AssertionError("no free enemy bullet slot");
    }

    private void clearPlayerBullets() {
        for (Bullet b : world.bullets) b.active = false;
    }

    private boolean hasEvent(GameWorld.EventType type) {
        List<GameWorld.GameEvent> events = world.pollEvents();
        for (GameWorld.GameEvent e : events) if (e.type == type) return true;
        return false;
    }

    // ---- 相位冲刺 ----

    @Test
    public void dashConsumesEnergyMovesAndRecharges() {
        assertTrue(world.ship.canDash());
        float startY = world.ship.y;
        assertTrue(world.tryDash());
        assertEquals(Ship.DASH_MAX_CHARGE - Ship.DASH_COST,
                world.ship.dashCharge, 0.001f);

        for (int i = 0; i < 20; i++) world.update(1f / 60f); // 0.33s > 冲刺时长
        assertTrue("冲刺把飞船沿方向推出去", world.ship.y < startY - 100f);
        assertEquals(1, world.ship.dashesUsed);
        assertTrue("满槽 2 格允许再来一次", world.ship.canDash());

        assertTrue(world.tryDash());
        assertEquals(2, world.ship.dashesUsed);
        assertTrue("能量耗尽后不够第三次冲刺", world.ship.dashCharge < Ship.DASH_COST);
        assertFalse(world.ship.canDash());

        int frames = (int) Math.ceil(Ship.DASH_RECHARGE_SECONDS * 60) + 5;
        for (int i = 0; i < frames; i++) world.update(1f / 60f);
        assertTrue("能量按时间回充", world.ship.canDash());
    }

    @Test
    public void dashCannotBeReTriggeredMidFlight() {
        assertTrue(world.tryDash());
        assertFalse("冲刺过程中再次点击无效", world.tryDash());
        assertEquals(1, world.ship.dashesUsed);
    }

    @Test
    public void dashGraceBlocksContactDamage() {
        Enemy e = freeEnemy();
        e.init(EnemyType.TANK, world.ship.x, world.ship.y, 1f);
        int lives = world.lives;
        assertTrue(world.tryDash());
        for (int i = 0; i < 10; i++) world.update(1f / 60f);
        assertEquals("冲刺无敌帧内不受碰撞伤害", lives, world.lives);
        assertTrue(hasEvent(GameWorld.EventType.DASH));
    }

    @Test
    public void contactWithoutDashStillHurts() {
        Enemy e = freeEnemy();
        e.init(EnemyType.TANK, world.ship.x, world.ship.y, 1f);
        int lives = world.lives;
        for (int i = 0; i < 10; i++) world.update(1f / 60f);
        assertTrue("未冲刺时同位置会受伤", world.lives < lives);
    }

    @Test
    public void dashIntoWallStopsAtTheEdge() {
        world.ship.y = Ship.RADIUS + 30f;
        world.ship.setTarget(world.ship.x, world.ship.y - 200f);
        assertTrue(world.tryDash());
        for (int i = 0; i < 30; i++) world.update(1f / 60f);
        assertTrue("冲刺不会穿出屏幕", world.ship.y >= Ship.RADIUS - 0.5f);
        assertTrue("贴着上边界停下", world.ship.y < Ship.RADIUS + 2f);
    }

    // ---- 轨道球 ----

    @Test
    public void orbitSpheresDamageEnemiesAndEraseBullets() {
        world.spawnPowerUp(PowerUp.Kind.ORBIT, world.ship.x, world.ship.y);
        world.update(1f / 60f);
        assertTrue(world.ship.orbitTimer > 0f);

        Enemy e = freeEnemy();
        e.init(EnemyType.TANK, world.ship.x + Ship.ORBIT_RADIUS, world.ship.y, 1f);
        e.maxHp = 12f;
        e.hp = 12f;
        float[] pos = new float[2];
        assertTrue(world.ship.orbitSphere(0, pos));
        EnemyBullet b = freeBulletSlot();
        b.init(pos[0], pos[1], 0f, 120f);

        int kills = world.kills;
        for (int i = 0; i < 90; i++) world.update(1f / 60f);

        assertTrue("轨道球应打到重装堡", e.hp < 12f || !e.active);
        assertFalse("轨道球擦掉敌弹", b.active);
        assertTrue(world.kills >= kills);
    }

    @Test
    public void orbitExpiresAndStacksOnRepickup() {
        world.spawnPowerUp(PowerUp.Kind.ORBIT, world.ship.x, world.ship.y);
        world.update(1f / 60f);
        float first = world.ship.orbitTimer;
        world.spawnPowerUp(PowerUp.Kind.ORBIT, world.ship.x, world.ship.y);
        world.update(1f / 60f);
        assertTrue("重复拾取延长但不翻倍", world.ship.orbitTimer > first);
        assertTrue(world.ship.orbitTimer <= Ship.ORBIT_DURATION * Ship.MAX_TIMER_MULT + 0.001f);

        int frames = (int) (world.ship.orbitTimer * 60) + 5;
        for (int i = 0; i < frames; i++) world.update(1f / 60f);
        assertEquals(0f, world.ship.orbitTimer, 0.0001f);
        float[] pos = new float[2];
        assertFalse("到期后不再提供轨道球", world.ship.orbitSphere(0, pos));
    }

    // ---- 连锁闪电 ----

    @Test
    public void chainLightningArcsAcrossEnemies() {
        Enemy a = freeEnemy();
        a.init(EnemyType.DRONE, world.ship.x - 60f, world.ship.y - 200f, 1f);
        Enemy b = freeEnemy();
        b.init(EnemyType.DRONE, world.ship.x + 60f, world.ship.y - 360f, 1f);
        Enemy c = freeEnemy();
        c.init(EnemyType.TANK, world.ship.x, world.ship.y - 520f, 1f);
        c.maxHp = 60f;
        c.hp = 60f;

        world.spawnPowerUp(PowerUp.Kind.CHAIN, world.ship.x, world.ship.y);
        world.update(1f / 60f);

        assertTrue(hasEvent(GameWorld.EventType.CHAIN));
        int arcs = 0;
        for (int i = 0; i < GameWorld.MAX_ARCS; i++) if (world.arcAge[i] > 0f) arcs++;
        assertTrue("至少留下数条放电路径", arcs >= 3);
        assertFalse("低血量敌机被放电直接带走", a.active);
        assertFalse(b.active);
        assertTrue("重装堡受创但存活", c.hp > 0f && c.hp < 60f);
    }

    @Test
    public void chainWithoutTargetsIsHarmlessButStillReads() {
        world.spawnPowerUp(PowerUp.Kind.CHAIN, world.ship.x, world.ship.y);
        world.update(1f / 60f);
        assertEquals(GameWorld.State.PLAYING, world.state);
        assertTrue(hasEvent(GameWorld.EventType.POWERUP_COLLECTED));
    }

    // ---- 武器融合 ----

    @Test
    public void fusionRequiresTwoBaseWeapons() {
        world.spawnPowerUp(PowerUp.Kind.SPREAD, world.ship.x, world.ship.y);
        world.update(1f / 60f);
        assertFalse("单一武器不会融合", world.ship.isWeaponEquipped(WeaponType.LANCE));

        world.spawnPowerUp(PowerUp.Kind.LASER, world.ship.x, world.ship.y);
        world.update(1f / 60f);
        assertTrue(world.ship.isWeaponEquipped(WeaponType.LANCE));
        assertEquals(WeaponType.LANCE, world.lastFusion);
        assertTrue(world.fusionFlash > 0f);
        assertTrue(hasEvent(GameWorld.EventType.FUSION));
        assertTrue("两种基础武器都保留",
                world.ship.isWeaponEquipped(WeaponType.SPREAD)
                        && world.ship.isWeaponEquipped(WeaponType.LASER));
    }

    @Test
    public void fusionRetiresWithItsIngredients() {
        world.spawnPowerUp(PowerUp.Kind.SPREAD, world.ship.x, world.ship.y);
        world.spawnPowerUp(PowerUp.Kind.LASER, world.ship.x, world.ship.y);
        world.update(1f / 60f);
        assertTrue(world.ship.isWeaponEquipped(WeaponType.LANCE));

        int frames = (int) (Ship.WEAPON_DURATION * 60) + 20;
        for (int i = 0; i < frames; i++) world.update(1f / 60f);
        assertFalse("基础武器耗尽后融合武器一并消失",
                world.ship.isWeaponEquipped(WeaponType.LANCE));
        assertFalse(world.ship.isWeaponEquipped(WeaponType.SPREAD));

        clearPlayerBullets();
        world.update(1f / 60f);
        for (Bullet b : world.bullets) {
            if (!b.active) continue;
            assertFalse("融合武器不再开火", b.piercing && b.radius > 12f);
        }
    }

    @Test
    public void fusedWeaponsNeverDropFromEnemies() {
        for (int i = 0; i <= 1000; i++) {
            PowerUp.Kind kind = GameWorld.dropKind(i / 1000f);
            WeaponType asWeapon = weaponOf(kind);
            if (asWeapon != null) {
                assertFalse(kind + " 不应掉落融合武器", asWeapon.isFused());
            }
        }
    }

    private static WeaponType weaponOf(PowerUp.Kind kind) {
        switch (kind) {
            case SPREAD: return WeaponType.SPREAD;
            case LASER: return WeaponType.LASER;
            case MISSILE: return WeaponType.MISSILE;
            case ARC: return WeaponType.ARC;
            default: return null;
        }
    }

    @Test
    public void clusterPodsBurstIntoFragmentsOnHit() {
        world.spawnPowerUp(PowerUp.Kind.SPREAD, world.ship.x, world.ship.y);
        world.spawnPowerUp(PowerUp.Kind.MISSILE, world.ship.x, world.ship.y);
        world.update(1f / 60f);
        assertTrue(world.ship.isWeaponEquipped(WeaponType.CLUSTER));

        Enemy e = freeEnemy();
        e.init(EnemyType.TANK, world.ship.x, world.ship.y - 160f, 1f);
        e.maxHp = 200f;
        e.hp = 200f;

        for (int i = 0; i < 40 && e.hp > 190f; i++) world.update(1f / 60f);
        boolean fragments = false;
        for (Bullet b : world.bullets) {
            if (b.active && b.fragment) fragments = true;
        }
        assertTrue("集束弹命中后应炸出破片", fragments);
    }

    // ---- 顿帧 ----

    @Test
    public void hitStopSlowsSimulationButNotTheClock() {
        Enemy e = freeEnemy();
        e.init(EnemyType.DRONE, GameWorld.WIDTH / 2f, 100f, 1f);
        float before = e.y;

        world.addHitStop(0.05f);
        world.update(1f / 60f);
        float stopped = e.y - before;

        int guard = 0;
        while (world.hitStopTimer > 0f && guard++ < 20) world.update(1f / 60f);
        assertEquals(0f, world.hitStopTimer, 0.0001f);

        e.init(EnemyType.DRONE, GameWorld.WIDTH / 2f, 100f, 1f);
        before = e.y;
        world.update(1f / 60f);
        float normal = e.y - before;

        assertTrue("顿帧帧位移应明显小于正常帧", stopped < normal * 0.5f);
        assertTrue("顿帧不冻结 timeAlive，难度曲线照常推进", world.timeAlive > 0f);
    }

    @Test
    public void hitStopKeepsTheLongestWindow() {
        world.addHitStop(0.08f);
        world.addHitStop(0.02f);
        assertEquals(0.08f, world.hitStopTimer, 0.0001f);
        world.addHitStop(0.12f);
        assertEquals(0.12f, world.hitStopTimer, 0.0001f);
    }
}
