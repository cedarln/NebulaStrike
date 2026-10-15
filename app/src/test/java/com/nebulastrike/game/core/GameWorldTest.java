package com.nebulastrike.game.core;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** Simulation-level tests for the core game loop. */
public class GameWorldTest {
    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(1234L);
    }

    @Test
    public void startsInPlayingState() {
        assertEquals(GameWorld.State.PLAYING, world.state);
        assertEquals(0, world.score);
        assertEquals(3, world.lives);
        assertEquals(0, world.spawnedCount);
    }

    @Test
    public void spawnsEnemiesOverTime() {
        stepSeconds(2.5f);
        assertTrue(world.spawnedCount > 0);
    }

    @Test
    public void difficultyRampsUpSpawnRate() {
        GameWorld early = new GameWorld(7L);
        step(early, 3f);
        int earlyCount = early.spawnedCount;

        GameWorld late = new GameWorld(7L);
        late.timeAlive = 118f; // near max difficulty
        step(late, 3f);
        assertTrue("late game should spawn more: " + earlyCount + " vs " + late.spawnedCount,
                late.spawnedCount > earlyCount);
    }

    @Test
    public void shipFiresBullets() {
        stepSeconds(0.05f);
        assertTrue(anyActiveBullet());
    }

    @Test
    public void bulletCanKillEnemyAndScores() {
        // fire one bullet
        stepSeconds(0.05f);
        Bullet b = firstActiveBullet();
        assertNotNull(b);
        // place a drone right above the bullet's next position
        Enemy e = firstFreeEnemy();
        e.init(EnemyType.DRONE, b.x, b.y - 20f, 1f);
        world.enemies[0] = e;
        stepSeconds(0.05f);
        assertEquals("enemy should die to one bullet", 10, world.score);
        assertFalse(e.active);
        assertEquals(1, world.kills);
    }

    @Test
    public void playerHitReducesLifeAndGrantsInvulnerability() {
        int before = world.lives;
        hitShip();
        assertEquals(before - 1, world.lives);
        assertTrue(world.ship.invulnerable);
    }

    @Test
    public void threeHitsEndTheGame() {
        hitShip();
        clearInvuln();
        hitShip();
        clearInvuln();
        hitShip();
        assertEquals(0, world.lives);
        assertEquals(GameWorld.State.GAME_OVER, world.state);
    }

    @Test
    public void restartResetsEverything() {
        hitShip();
        world.restart(GameMode.WAVES);
        assertEquals(GameWorld.State.PLAYING, world.state);
        assertEquals(3, world.lives);
        assertEquals(0, world.score);
        assertEquals(0, world.spawnedCount);
        assertEquals(0f, world.stateTimer, 1e-6);
    }

    @Test
    public void eventsAreEmittedForPresentationLayer() {
        hitShip();
        boolean found = world.pollEvents().stream()
                .anyMatch(ev -> ev.type == GameWorld.EventType.PLAYER_HIT);
        assertTrue(found);
    }

    // ---- helpers ----

    private void stepSeconds(float seconds) {
        step(world, seconds);
    }

    private static void step(GameWorld w, float seconds) {
        float dt = 1f / 60f;
        int steps = Math.round(seconds / dt);
        for (int i = 0; i < steps; i++) w.update(dt);
    }

    private void hitShip() {
        Enemy e = firstFreeEnemy();
        e.init(EnemyType.DRONE, world.ship.x, world.ship.y, 1f);
        world.update(1f / 60f);
    }

    private void clearInvuln() {
        // invulnerability lasts 2s; stepping 2.5s of gameplay is enough
        stepSeconds(2.5f);
    }

    private boolean anyActiveBullet() {
        for (Bullet b : world.bullets) if (b.active) return true;
        return false;
    }

    private Bullet firstActiveBullet() {
        for (Bullet b : world.bullets) if (b.active) return b;
        return null;
    }

    private Enemy firstFreeEnemy() {
        for (Enemy e : world.enemies) if (!e.active) return e;
        throw new AssertionError("enemy pool exhausted");
    }
}
