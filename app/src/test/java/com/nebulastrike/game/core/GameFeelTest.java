package com.nebulastrike.game.core;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** Tests for iteration-2 features: enemy fire, combo scoring, formations, tanks. */
public class GameFeelTest {
    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(2024L);
    }

    @Test
    public void tankNeedsFourHitsAndAwardsBonus() {
        world.spawningEnabled = false;
        stepToFire();
        Bullet b = firstActiveBullet();
        Enemy tank = firstFreeEnemy();
        tank.init(EnemyType.TANK, b.x, b.y - 220f, 1f);
        for (int i = 0; i < 400 && tank.active; i++) world.update(1f / 60f);
        assertFalse("4 hits should kill the tank", tank.active);
        assertEquals("first kill scores base value", 30, world.score);
    }

    @Test
    public void comboChainAwardsMultiplier() {
        // six drones stacked in the bullet lane; kill them fast
        world.spawningEnabled = false;
        stepToFire();
        Enemy[] drones = new Enemy[6];
        float y = world.ship.y - 80f;
        for (int i = 0; i < 6; i++) {
            drones[i] = world.enemies[i];
            drones[i].init(EnemyType.DRONE, world.ship.x, y - i * 50f, 0f);
        }
        for (int i = 0; i < 600 && world.kills < 6; i++) world.update(1f / 60f);
        assertEquals(6, world.kills);
        // kills 1-4 x1 (40), kill 5 x2 (20), kill 6 x2 (20)
        // 成就解锁会在某个判定帧白送一笔庆祝分，它不属于连击倍率，先扣掉再比
        assertEquals(80, world.score - world.achieveScore);
        assertEquals(6, world.comboKills);
        assertEquals(2, world.comboMultiplier());
    }

    @Test
    public void comboResetsOnPlayerHit() {
        world.comboKills = 4;
        world.comboTimer = GameWorld.COMBO_WINDOW;
        world.enemyBullets[0].init(world.ship.x, world.ship.y, 0f, 0f);
        world.update(1f / 60f);
        assertEquals(0, world.comboKills);
        assertTrue(world.lives < 3);
    }

    @Test
    public void enemyBulletCanHitShip() {
        world.spawningEnabled = false;
        int before = world.lives;
        world.ship.makeInvulnerable();
        float t = 0f;
        while (world.ship.invulnerable && t < 3f) { // wait out invulnerability
            world.update(1f / 60f);
            t += 1f / 60f;
        }
        world.enemyBullets[0].init(world.ship.x, world.ship.y - 5f, 0f, 0f);
        world.update(1f / 60f);
        assertEquals(before - 1, world.lives);
    }

    @Test
    public void formationsSpawnFiveAtOnce() {
        int prev = 0;
        boolean sawBurst = false;
        for (int i = 0; i < 60 * 10; i++) { // up to 10s
            world.update(1f / 60f);
            int now = world.spawnedCount;
            if (now - prev == 5) { sawBurst = true; break; }
            prev = now;
        }
        assertTrue("expected a 5-ship formation burst", sawBurst);
    }

    @Test
    public void screenShakeDecays() {
        world.addShake(0.2f, 10f);
        assertTrue(world.shakeTime > 0f);
        float t = 0f;
        while (world.shakeTime > 0f && t < 1f) {
            world.update(1f / 60f);
            t += 1f / 60f;
        }
        assertEquals(0f, world.shakeMag, 0.001f);
    }

    @Test
    public void gameOverSettlesShake() {
        world.lives = 1;
        world.ship.hasShield = false;
        Enemy e = firstFreeEnemy();
        e.init(EnemyType.DRONE, world.ship.x, world.ship.y, 1f);
        world.update(1f / 60f);
        assertEquals(GameWorld.State.GAME_OVER, world.state);
        assertTrue(world.shakeTime > 0f);

        // 结算界面仍需推进特效，否则整块屏幕会永久偏移
        for (int i = 0; i < 120; i++) world.update(1f / 60f);
        assertEquals(0f, world.shakeMag, 0.001f);
    }

    // ---- helpers ----

    private void stepToFire() {
        world.update(1f / 60f); // ship fires on its first frame (cooldown starts at 0)
    }

    private Bullet firstActiveBullet() {
        for (Bullet b : world.bullets) if (b.active) return b;
        throw new AssertionError("no bullet");
    }

    private Enemy firstFreeEnemy() {
        for (Enemy e : world.enemies) if (!e.active) return e;
        throw new AssertionError("no free enemy");
    }
}
