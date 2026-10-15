package com.nebulastrike.game.core;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** Tests for iteration-3: weapons, powerups, shield, bombs. */
public class WeaponsPowerUpsTest {
    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(777L);
        world.spawningEnabled = false;
    }

    @Test
    public void spreadFiresThreeBullets() {
        world.ship.weapon = WeaponType.SPREAD;
        world.update(1f / 60f);
        assertEquals(3, countActive(world.bullets));
    }

    @Test
    public void missileFiresTwoHomingShots() {
        world.ship.weapon = WeaponType.MISSILE;
        world.update(1f / 60f);
        assertEquals(2, countActive(world.bullets));
        for (Bullet b : world.bullets) if (b.active) assertTrue(b.homing);
    }

    @Test
    public void missileSteersTowardNearestEnemy() {
        world.ship.weapon = WeaponType.MISSILE;
        world.update(1f / 60f); // fire
        Bullet m = null;
        for (Bullet b : world.bullets) if (b.active) { m = b; break; }
        assertNotNull(m);
        // enemy far to the right
        Enemy e = world.enemies[0];
        e.init(EnemyType.DRONE, m.x + 600f, m.y + 60f, 0f);
        float vxBefore = m.vx;
        for (int i = 0; i < 20; i++) world.update(1f / 60f);
        assertTrue("missile should curve right, vx " + vxBefore + " -> " + m.vx, m.vx > 0);
    }

    @Test
    public void weaponPowerupAppliesAndExpires() {
        world.spawnPowerUp(PowerUp.Kind.SPREAD, world.ship.x, world.ship.y);
        world.update(1f / 60f);
        assertEquals(WeaponType.SPREAD, world.ship.weapon);
        assertEquals(Ship.WEAPON_DURATION, world.ship.weaponTimer, 0.01f);
        // fast-forward past duration
        for (int i = 0; i < 60 * 13; i++) world.update(1f / 60f);
        assertEquals(WeaponType.VULCAN, world.ship.weapon);
    }

    @Test
    public void shieldAbsorbsExactlyOneHit() {
        world.ship.hasShield = true;
        world.enemyBullets[0].init(world.ship.x, world.ship.y, 0, 0);
        world.update(1f / 60f);
        assertFalse(world.ship.hasShield);
        assertEquals("shield should prevent life loss", 3, world.lives);
        assertTrue(world.ship.invulnerable);
        // after invulnerability, a second hit does cost a life
        for (int i = 0; i < 60 * 3; i++) world.update(1f / 60f);
        world.enemyBullets[0].init(world.ship.x, world.ship.y, 0, 0);
        world.update(1f / 60f);
        assertEquals(2, world.lives);
    }

    @Test
    public void bombClearsBulletsAndDamagesEnemies() {
        Enemy tank = world.enemies[0];
        tank.init(EnemyType.TANK, 400f, 300f, 0f);
        world.enemyBullets[0].init(200f, 200f, 0, 300);
        world.ship.bombs = 2;
        world.detonateBomb();
        assertEquals(1, world.ship.bombs);
        assertFalse(world.enemyBullets[0].active);
        assertEquals(EnemyType.TANK.hp - 3, tank.hp, 0.001f);
        assertTrue(world.bombFlash > 0f);
        // tank dies from bomb alone: 4 hp -> hit once more
        world.detonateBomb(); // second bomb, already damaged to 1 hp
        assertFalse(tank.active);
        assertEquals(0, world.ship.bombs);
    }

    @Test
    public void bombDoesNothingWithoutStock() {
        world.ship.bombs = 0;
        Enemy e = world.enemies[0];
        e.init(EnemyType.DRONE, 400f, 300f, 0f);
        world.detonateBomb();
        assertTrue(e.active);
        assertEquals(0f, world.bombFlash, 0.0001f);
    }

    @Test
    public void magnetPullsPowerUpWhenClose() {
        PowerUp p = world.powerups[0];
        p.init(PowerUp.Kind.SHIELD, world.ship.x + 150f, world.ship.y);
        float d0 = world.ship.x - p.x;
        world.update(1f / 60f);
        float d1 = Math.abs(world.ship.x - p.x);
        assertTrue("powerup should be attracted", d1 < Math.abs(d0) + 1f);
        assertTrue(d1 < 150f);
    }

    private static int countActive(Bullet[] bullets) {
        int n = 0;
        for (Bullet b : bullets) if (b.active) n++;
        return n;
    }
}
