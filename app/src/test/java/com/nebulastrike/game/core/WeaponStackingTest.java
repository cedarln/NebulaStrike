package com.nebulastrike.game.core;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** 武器叠加、护盾叠层与限时道具延长的回归测试。 */
public class WeaponStackingTest {
    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(4242L);
        world.spawningEnabled = false;
    }

    @Test
    public void newWeaponDoesNotReplacePreviousWeapon() {
        world.spawnPowerUp(PowerUp.Kind.SPREAD, world.ship.x, world.ship.y);
        world.spawnPowerUp(PowerUp.Kind.LASER, world.ship.x, world.ship.y);
        world.update(1f / 60f); // collect both

        assertEquals("最新拾取的是主武器", WeaponType.LASER, world.ship.weapon);
        assertTrue("之前的 SPREAD 仍在生效", world.ship.isWeaponEquipped(WeaponType.SPREAD));
        assertTrue(world.ship.isWeaponEquipped(WeaponType.LASER));
        assertTrue(world.ship.timerOf(WeaponType.SPREAD) > 10f);
        assertTrue(world.ship.timerOf(WeaponType.LASER) > 10f);

        clearBullets();
        world.update(1f / 60f); // 两种武器同时开火
        int lasers = 0, spreads = 0;
        for (Bullet b : world.bullets) {
            if (!b.active) continue;
            if (b.piercing) lasers++;
            else spreads++;
        }
        assertEquals("LASER 主武器射出一束", 1, lasers);
        assertEquals("SPREAD 副武器同时射出扇形弹", 3, spreads);
    }

    @Test
    public void threeDifferentWeaponsAllFireTogether() {
        world.spawnPowerUp(PowerUp.Kind.SPREAD, world.ship.x, world.ship.y);
        world.spawnPowerUp(PowerUp.Kind.LASER, world.ship.x, world.ship.y);
        world.spawnPowerUp(PowerUp.Kind.MISSILE, world.ship.x, world.ship.y);
        world.update(1f / 60f);

        assertTrue(world.ship.isWeaponEquipped(WeaponType.SPREAD));
        assertTrue(world.ship.isWeaponEquipped(WeaponType.LASER));
        assertTrue(world.ship.isWeaponEquipped(WeaponType.MISSILE));
        assertTrue("VULCAN 在特殊武器耗尽前保持静默", !world.ship.isWeaponEquipped(WeaponType.VULCAN));

        clearBullets();
        world.update(1f / 60f);
        int lasers = 0, spreads = 0, missiles = 0;
        for (Bullet b : world.bullets) {
            if (!b.active) continue;
            if (b.piercing) lasers++;
            else if (b.homing) missiles++;
            else spreads++;
        }
        assertEquals(1, lasers);
        assertEquals(3, spreads);
        assertEquals(2, missiles);
    }

    @Test
    public void sameWeaponStacksUpToThreeAndThickensVolley() {
        // 第一次拾取：1 层
        world.spawnPowerUp(PowerUp.Kind.SPREAD, world.ship.x, world.ship.y);
        world.update(1f / 60f);
        clearBullets();
        world.update(1f / 60f);
        assertEquals(3, activeBullets());

        // 连续再吃三个：封顶 3 层
        for (int i = 0; i < 3; i++) {
            world.spawnPowerUp(PowerUp.Kind.SPREAD, world.ship.x, world.ship.y);
            world.update(1f / 60f);
        }
        assertEquals(Ship.MAX_WEAPON_STACK, world.ship.stackOf(WeaponType.SPREAD));
        assertEquals(Ship.WEAPON_DURATION, world.ship.timerOf(WeaponType.SPREAD), 0.05f);

        clearBullets();
        world.update(1f / 60f);
        assertEquals("3 层火焰弹应为 7 向", 7, activeBullets());
    }

    @Test
    public void expiringWeaponPromotesRemainingStack() {
        Ship ship = world.ship;
        ship.addWeapon(WeaponType.SPREAD);
        ship.addWeapon(WeaponType.LASER);
        // 让 SPREAD 成为即将到期的主武器
        ship.weapon = WeaponType.SPREAD;
        ship.weaponStacks[WeaponType.SPREAD.ordinal()] = 1;
        ship.weaponTimers[WeaponType.SPREAD.ordinal()] = 0.05f;
        ship.weaponTimers[WeaponType.LASER.ordinal()] = 9f;

        ship.update(0.1f, GameWorld.WIDTH, GameWorld.HEIGHT);

        assertFalse(ship.isWeaponEquipped(WeaponType.SPREAD));
        assertTrue(ship.isWeaponEquipped(WeaponType.LASER));
        assertEquals("主武器自动切到剩余武器", WeaponType.LASER, ship.weapon);
    }

    @Test
    public void weaponPowerupNeverOverwritesOtherTimers() {
        Ship ship = world.ship;
        ship.addWeapon(WeaponType.MISSILE);
        float missileTimer = ship.timerOf(WeaponType.MISSILE);
        ship.addWeapon(WeaponType.SPREAD);
        assertEquals("新武器不会重置旧武器计时",
                missileTimer, ship.timerOf(WeaponType.MISSILE), 0.001f);
        assertTrue(ship.timerOf(WeaponType.MISSILE) > 0f);
    }

    @Test
    public void shieldStacksTwoLayers() {
        world.spawnPowerUp(PowerUp.Kind.SHIELD, world.ship.x, world.ship.y);
        world.spawnPowerUp(PowerUp.Kind.SHIELD, world.ship.x, world.ship.y);
        world.update(1f / 60f);
        assertEquals(2, world.ship.shieldLayers);

        // 第一次命中：破一层
        world.enemyBullets[0].init(world.ship.x, world.ship.y, 0, 0);
        world.update(1f / 60f);
        assertEquals(1, world.ship.shieldLayers);
        assertTrue(world.ship.hasShield);
        assertEquals(3, world.lives);

        // 第二次命中：破第二层
        world.ship.invulnerable = false;
        world.enemyBullets[0].init(world.ship.x, world.ship.y, 0, 0);
        world.update(1f / 60f);
        assertEquals(0, world.ship.shieldLayers);
        assertFalse(world.ship.hasShield);
        assertEquals(3, world.lives);

        // 第三次命中：真正掉命
        world.ship.invulnerable = false;
        world.enemyBullets[0].init(world.ship.x, world.ship.y, 0, 0);
        world.update(1f / 60f);
        assertEquals(2, world.lives);
    }

    @Test
    public void timedPickupExtendsButCapsAtDouble() {
        for (int i = 0; i < 3; i++) {
            world.spawnPowerUp(PowerUp.Kind.RAPID, world.ship.x, world.ship.y);
            world.update(1f / 60f);
        }
        float cap = Ship.RAPID_DURATION * Ship.MAX_TIMER_MULT;
        assertTrue("重复拾取应延长", world.ship.rapidTimer > Ship.RAPID_DURATION);
        assertTrue("不能超过基础上限", world.ship.rapidTimer <= cap + 0.001f);
    }

    private void clearBullets() {
        for (Bullet b : world.bullets) b.active = false;
    }

    private int activeBullets() {
        int n = 0;
        for (Bullet b : world.bullets) if (b.active) n++;
        return n;
    }
}
