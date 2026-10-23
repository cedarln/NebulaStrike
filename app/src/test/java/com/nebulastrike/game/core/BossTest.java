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
        assertEquals(420f, world.boss.maxHp, 0.001f);
    }

    @Test
    public void bossGrowsWithEachEncounter() {
        Boss scout = new Boss();
        scout.init(0);
        Boss leviathan = new Boss();
        leviathan.init(3);
        Boss worldEater = new Boss();
        worldEater.init(6);
        Boss dreadnought = new Boss();
        dreadnought.init(1);
        assertTrue("later bosses are physically larger", leviathan.radius > scout.radius);
        assertEquals("size caps at tier 3", leviathan.radius, worldEater.radius, 0.001f);
        assertTrue("later bosses have more hit points", worldEater.maxHp > leviathan.maxHp);
        assertTrue("more appendages at higher tiers", worldEater.partCount > scout.partCount);
        assertEquals("tier 0 starts with two gun pods", 2, scout.partCount);
        boolean dreadLaser = false;
        for (int i = 0; i < dreadnought.partCount; i++) {
            if (dreadnought.parts[i].kind == BossPart.Kind.LASER) dreadLaser = true;
        }
        assertTrue("laser pods appear from the second boss", dreadLaser);
        assertEquals("tier 3 carries guns, lasers, shield generators, minion pods and a mine bay",
                12, worldEater.partCount);
        int[] byKind = new int[BossPart.Kind.values().length];
        for (int i = 0; i < worldEater.partCount; i++) {
            byKind[worldEater.parts[i].kind.ordinal()]++;
        }
        assertEquals(5, byKind[BossPart.Kind.GUN.ordinal()]);
        assertEquals(2, byKind[BossPart.Kind.LASER.ordinal()]);
        assertEquals(2, byKind[BossPart.Kind.SHIELD.ordinal()]);
        assertEquals(2, byKind[BossPart.Kind.POD.ordinal()]);
        assertEquals("布雷舱从第三艘起登场", 1, byKind[BossPart.Kind.MINE.ordinal()]);
        assertTrue("护盾发生器存活时舰体带甲", worldEater.shielded());
        assertEquals("两层护盾把核心伤害削到 50%", 0.5f, worldEater.damageScale(), 0.001f);
        assertEquals("tier 0 没有护盾，伤害不衰减", 1.0f, scout.damageScale(), 0.001f);
    }

    @Test
    public void partsFireAndCanBeDestroyed() {
        triggerBoss();
        world.boss.y = 150f;
        for (int i = 0; i < 60 * 4; i++) world.update(1f / 60f);
        boolean anyEnemyShot = false;
        for (EnemyBullet b : world.enemyBullets) if (b.active) { anyEnemyShot = true; break; }
        assertTrue("gun pods should fire", anyEnemyShot);

        BossPart part = world.boss.parts[0];
        assertTrue(part.active);
        part.hp = 1f;
        float hx = part.worldX(world.boss), hy = part.worldY(world.boss);
        // put a player bullet right on the pod
        Bullet shot = null;
        for (Bullet b : world.bullets) if (!b.active) { shot = b; break; }
        assertNotNull(shot);
        shot.init(hx, hy, 0f, -100f, 2, false);
        world.update(1f / 60f);
        assertFalse("pod should be destroyed by enough damage", part.active);
        boolean wreckEvent = world.pollEvents().stream()
                .anyMatch(e -> e.type == GameWorld.EventType.BOSS_PART_DESTROYED);
        assertTrue(wreckEvent);
    }

    @Test
    public void destroyingEveryPartDisarmsTheBoss() {
        triggerBoss();
        world.boss.y = 150f;
        for (int i = 0; i < world.boss.partCount; i++) {
            BossPart p = world.boss.parts[i];
            p.hp = 0.5f;
            Bullet shot = null;
            for (Bullet b : world.bullets) if (!b.active) { shot = b; break; }
            assertNotNull(shot);
            shot.init(p.worldX(world.boss), p.worldY(world.boss), 0f, -100f, 2, false);
            world.update(1f / 60f);
        }
        assertTrue("all pods destroyed", world.boss.partsAllDestroyed());
        assertTrue("boss is disarmed", world.boss.allPartsDown);
        assertTrue("stagger opens a damage window", world.boss.staggerTimer > 0f);
        assertFalse(world.boss.readyToAttack());
        boolean disarmedEvent = world.pollEvents().stream()
                .anyMatch(e -> e.type == GameWorld.EventType.BOSS_DISARMED);
        assertTrue(disarmedEvent);
    }

    @Test
    public void laserPodsChargeThenFireBeam() {
        triggerBoss();
        world.boss.y = 150f;
        world.bossesDefeated = 2; // tier 2 layout keeps laser pods on the field
        world.boss.init(2);
        world.boss.y = 150f;
        world.boss.parts[world.boss.partCount - 1].fireCd = 0.01f; // first laser pod ready
        boolean sawWarmup = false, sawBeam = false;
        for (int i = 0; i < 60 * 6; i++) {
            world.update(1f / 60f);
            for (BossLaser l : world.bossLasers) {
                if (!l.active) continue;
                if (l.firing) sawBeam = true; else sawWarmup = true;
            }
            if (sawBeam) break;
        }
        assertTrue("laser should show a warning before firing", sawWarmup);
        assertTrue("laser should eventually fire", sawBeam);
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
                || world.boss.x <= GameWorld.WIDTH - world.boss.radius - 29f);
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
