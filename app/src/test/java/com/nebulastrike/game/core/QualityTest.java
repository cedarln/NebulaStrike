package com.nebulastrike.game.core;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** Tests for iteration-7: adaptive particle quality and ring-buffer recycling. */
public class QualityTest {
    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(77L);
    }

    @Test
    public void sustainedSlowFramesHalveEmission() {
        for (int i = 0; i < 90; i++) world.noteFrameCost(0.030f); // ~33ms for 1.5s
        assertEquals(0.5f, world.emissionScale, 0.001f);
    }

    @Test
    public void sustainedFastFramesRestoreFullEmission() {
        for (int i = 0; i < 90; i++) world.noteFrameCost(0.030f);
        for (int i = 0; i < 90; i++) world.noteFrameCost(0.012f);
        assertEquals(1f, world.emissionScale, 0.001f);
    }

    @Test
    public void movingAverageDoesNotFlap() {
        world.noteFrameCost(0.030f); // one bad frame must not trigger downshift
        assertEquals(1f, world.emissionScale, 0.001f);
    }

    @Test
    public void outlierFrameCostsAreIgnored() {
        world.noteFrameCost(10f);   // GC / background pause
        world.noteFrameCost(-1f);   // bogus
        assertEquals(1f, world.emissionScale, 0.001f);
        world.noteFrameCost(0.030f); // normal frame still averages in cleanly
        assertTrue(world.emissionScale == 1f);
    }

    @Test
    public void lowQualityModeEmitsFewerParticles() {
        int high = killOneDroneCountingParticles(1f);
        world.restart(GameMode.WAVES);
        int low = killOneDroneCountingParticles(0.5f);
        assertTrue("low quality should emit fewer particles", low < high);
        assertTrue("explosion burst at low quality should be capped",
                low <= 10);
    }

    @Test
    public void fullPoolRecyclesOldestSlotInsteadOfDropping() {
        for (Particle p : world.particles) {
            p.init(0f, 0f, 0f, 0f, 60f, 4f, 0); // long-lived, saturates the pool
        }
        assertEquals(world.particles.length, world.activeParticles());
        world.spawningEnabled = false;
        killOneDrone(0f); // explosion must still work by recycling
        assertEquals(world.particles.length, world.activeParticles());
    }

    @Test
    public void reducedFxSettingForcesLowEmission() {
        world.settings.reducedFx = true;
        for (int i = 0; i < 90; i++) world.noteFrameCost(0.005f); // even with fast frames
        assertEquals(0.5f, world.emissionScale, 0.001f);
    }

    @Test
    public void primedGovernorSkipsWarmup() {
        world.primeQuality(1f); // device prior seeds the sample count
        for (int i = 0; i < 5; i++) world.noteFrameCost(0.030f);
        assertEquals(0.5f, world.emissionScale, 0.001f);
    }

    @Test
    public void particlesDieOutAfterTheirLifetime() {
        world.spawningEnabled = false;
        killOneDrone(0f);
        int peak = world.activeParticles();
        assertTrue(peak > 0);
        float t = 0f;
        while (world.activeParticles() > 0 && t < 3f) {
            world.update(1f / 60f);
            t += 1f / 60f;
        }
        assertEquals(0, world.activeParticles());
    }

    @Test
    public void particleToggleStopsEmission() {
        world.settings.particles = false;
        world.spawningEnabled = false;
        killOneDrone(1f);
        assertEquals("关闭粒子后不应再产生爆炸/尾焰粒子", 0, world.activeParticles());
    }

    // ---- helpers ----

    private int killOneDroneCountingParticles(float scale) {
        world.emissionScale = scale;
        killOneDrone(scale);
        return world.activeParticles();
    }

    private void killOneDrone(float scale) {
        world.emissionScale = scale;
        world.spawningEnabled = false;
        world.update(1f / 60f); // fires a bullet
        Bullet b = firstActiveBullet();
        Enemy drone = firstFreeEnemy();
        drone.init(EnemyType.DRONE, b.x, b.y - 60f, 0f);
        for (int i = 0; i < 240 && drone.active; i++) world.update(1f / 60f);
        assertFalse("drone should be dead", drone.active);
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
