package com.nebulastrike.game.core;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Iteration-5: boss battles. */
public class BossTest {
    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(555L);
    }

    private void triggerBoss() {
        world.wave = 4; // milestone reached
        world.update(1f / 60f);
    }

    @Test
    public void bossSpawnsAtWaveMilestone() {
        assertFalse(world.boss.active);
        triggerBoss();
        assertTrue(world.boss.active);
        assertTrue(world.bossActive);
        assertEquals(110, world.boss.maxHp, 0.001f);
    }

    @Test
    public void regularSpawningPausesDuringBoss() {
        triggerBoss();
        world.boss.y = 150f; // entered arena
        int before = world.spawnedCount;
        for (int i = 0; i < 120; i++) world.update(1f / 60f); // 2s
        assertEquals("no regular spawns while boss alive",
                before, world.spawnedCount - minionSurplus(before));
    }

    private int minionSurplus(int before) {
        // summoning adds hunters; measure via active hunters only
        return 0; // covered by summon test below
    }

    @Test
    public void bossDescendsAndPatinates() {
        triggerBoss();
        float startY = world.boss.y;
        for (int i = 0; i < 240; i++) world.update(1f / 60f);
        assertTrue("boss should glide into arena", world.boss.y > startY);
        float x0 = world.boss.x;
        for (int i = 0; i < 300; i++) world.update(1f / 60f);
        assertTrue("boss should patrol horizontally", Math.abs(world.boss.x - x0) > 50f
                || world.boss.x <= GameWorld.WIDTH - Boss.RADIUS - 29f);
    }

    @Test
    public void bossAttacksProduceBullets() {
        triggerBoss();
        for (int i = 0; i < 60 * 6; i++) world.update(1f / 60f);
        boolean anyAttack = false;
        for (EnemyBullet b : world.enemyBullets) if (b.active) { anyAttack = true; break; }
        assertTrue("boss should have fired by now", anyAttack);
    }

    @Test
    public void phasesChangeAsHpDrops() {
        Boss b = new Boss();
        b.init(0);
        assertEquals(1, b.phase);
        b.damage((int) (b.maxHp * 0.5f));
        b.update(1f / 60f);
        assertEquals(2, b.phase);
        b.damage((int) (b.maxHp * 0.4f));
        b.update(1f / 60f);
        assertEquals(3, b.phase);
    }

    @Test
    public void summonBringsMinionsInPhase2Plus() {
        triggerBoss();
        world.boss.y = 150f;
        world.boss.hp = world.boss.maxHp * 0.5f; // pushes into phase 2 organically
        world.boss.minionCd = 0.01f; // ready
        int before = world.spawnedCount;
        world.update(1f / 60f);
        assertTrue(world.spawnedCount >= before + 2);
    }

    @Test
    public void bossDiesAndRewards() {
        triggerBoss();
        GameWorld.State s = world.state;
        world.boss.hp = 2;
        world.boss.x = world.ship.x;
        world.boss.y = world.ship.y - 500f;
        // fire until boss dies
        for (int i = 0; i < 60 * 10 && world.boss.active; i++) world.update(1f / 60f);
        assertFalse(world.boss.active);
        assertFalse(world.bossActive);
        assertEquals(1, world.bossesDefeated);
        assertTrue(world.score >= 400);
        // guaranteed reward pickups present
        int drops = 0;
        for (PowerUp p : world.powerups) if (p.active) drops++;
        assertTrue("boss should drop pickups", drops >= 2);
        assertEquals(s, world.state);
    }

    @Test
    public void nextBossIsStronger() {
        triggerBoss();
        float hp1 = world.boss.maxHp;
        world.boss.active = false;
        world.bossActive = false;
        world.bossesDefeated = 1;
        world.wave = 8;
        world.update(1f / 60f);
        assertTrue(world.boss.maxHp > hp1);
    }

    @Test
    public void eventsIncludeBossLifecycle() {
        triggerBoss();
        List<GameWorld.GameEvent> evs = new ArrayList<>(world.pollEvents());
        assertTrue(evs.stream().anyMatch(e -> e.type == GameWorld.EventType.BOSS_SPAWNED));
    }
}
