package com.nebulastrike.game.core;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** Iteration-8: local top-5 leaderboard persistence + retuned difficulty curve. */
public class LeaderboardTest {
    private GameWorld world;
    private UiStateTest.FakeStore store;

    @Before
    public void setUp() {
        store = new UiStateTest.FakeStore();
        world = new GameWorld(5L, true);
        world.attachStore(store);
    }

    @Test
    public void scoresFillBoardInDescendingOrder() {
        assertEquals(0, world.insertScore(100));
        assertEquals(0, world.insertScore(300));
        assertEquals(1, world.insertScore(200));
        assertArrayEquals(new int[]{300, 200, 100, 0, 0}, world.leaderboard);
        assertEquals(3, store.lbWrites);
    }

    @Test
    public void boardOverflowShiftsEntriesDown() {
        world.insertScore(500);
        world.insertScore(400);
        world.insertScore(300);
        world.insertScore(200);
        world.insertScore(100);
        assertEquals(-1, world.insertScore(50));   // too small
        assertEquals(2, world.insertScore(350));   // displaces 300
        assertArrayEquals(new int[]{500, 400, 350, 300, 200}, world.leaderboard);
    }

    @Test
    public void zeroScoreRunsAreNotRecorded() {
        assertEquals(-1, world.insertScore(0));
        assertEquals(-1, world.insertScore(-5));
        assertArrayEquals(new int[]{0, 0, 0, 0, 0}, world.leaderboard);
        assertEquals(0, store.lbWrites);
    }

    @Test
    public void gameEndRecordsRankOnBoard() {
        world.handleUiAction(Ui.Action.START);
        world.spawningEnabled = false;
        world.score = 250;
        world.lives = 1;
        world.enemyBullets[0].init(world.ship.x, world.ship.y, 0f, 0f);
        // wait out spawn protection, then take the hit
        float t = 0f;
        while (world.ship.invulnerable && t < 3f) { world.update(1f / 60f); t += 1f / 60f; }
        world.enemyBullets[0].init(world.ship.x, world.ship.y, 0f, 0f);
        world.update(1f / 60f);
        assertEquals(GameWorld.State.GAME_OVER, world.state);
        assertEquals(0, world.scoreRank);
        assertArrayEquals(new int[]{250, 0, 0, 0, 0}, world.leaderboard);
    }

    @Test
    public void leaderboardSurvivesStoreRoundTrip() {
        world.insertScore(420);
        world.insertScore(90);
        GameWorld fresh = new GameWorld(7L, true);
        fresh.attachStore(store);
        assertArrayEquals(new int[]{420, 90, 0, 0, 0}, fresh.leaderboard);
    }

    @Test
    public void restartClearsRankButKeepsBoard() {
        world.handleUiAction(Ui.Action.START);
        world.scoreRank = world.insertScore(123);
        assertEquals(0, world.scoreRank);
        world.restart();
        assertEquals(-1, world.scoreRank);
        assertEquals(123, world.leaderboard[0]);
    }

    // ---- difficulty curve ----

    @Test
    public void spawnIntervalTightensOver150Seconds() {
        world.handleUiAction(Ui.Action.START); // difficulty only advances while PLAYING
        world.spawningEnabled = false;
        assertEquals(1.1f, world.spawnInterval, 0.0001f);
        float t = 0f;
        while (t < 150f) { world.update(1f / 60f); t += 1f / 60f; }
        assertTrue("spawn interval should reach ~0.28s after the ramp",
                world.spawnInterval < 0.30f && world.spawnInterval > 0.25f);
        assertTrue("enemy speed should reach ~x1.75", world.enemySpeedScale > 1.7f);
    }

    @Test
    public void lateGameCreepsBeyondTheRamp() {
        world.handleUiAction(Ui.Action.START);
        world.spawningEnabled = false;
        float t = 0f;
        while (t < 300f) { world.update(1f / 60f); t += 1f / 60f; }
        assertTrue("speed should exceed the 150s cap during the creep phase",
                world.enemySpeedScale > 1.85f);
        assertTrue("spawns should keep tightening during the creep phase",
                world.spawnInterval < 0.28f);
    }

    @Test
    public void earlyGameStaysGentle() {
        world.handleUiAction(Ui.Action.START);
        world.spawningEnabled = false;
        for (int i = 0; i < 60; i++) world.update(1f / 60f);
        assertTrue("first second should keep the default pacing",
                world.spawnInterval > 1.0f);
        assertTrue("early speed scale should be near 1x",
                world.enemySpeedScale < 1.05f);
    }
}
