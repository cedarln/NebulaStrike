package com.nebulastrike.game.core;

import org.junit.Test;

import static org.junit.Assert.*;

public class EnemyTest {
    @Test
    public void weaverOscillatesAroundOrigin() {
        GameWorld.RNG.setSeed(1L);
        Enemy e = new Enemy();
        e.init(EnemyType.WEAVER, 800f, 100f, 1f);
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int i = 0; i < 240; i++) { // 4s: covers multiple sine periods
            e.update(1f / 60f, GameWorld.WIDTH);
            min = Math.min(min, e.x);
            max = Math.max(max, e.x);
        }
        assertTrue("weaver should oscillate", max - min > 200f);
        assertTrue("oscillation should be centered near origin",
                Math.abs((min + max) / 2f - 800f) < 40f);
    }

    @Test
    public void hunterBouncesOffWalls() {
        GameWorld.RNG.setSeed(2L);
        Enemy e = new Enemy();
        e.init(EnemyType.HUNTER, 100f, 100f, 1f);
        float before = e.x;
        for (int i = 0; i < 600; i++) {
            e.update(1f / 60f, GameWorld.WIDTH);
            assertTrue(e.x >= EnemyType.HUNTER.radius - 0.5f);
            assertTrue(e.x <= GameWorld.WIDTH - EnemyType.HUNTER.radius + 0.5f);
        }
        assertNotEquals("hunter should move horizontally", before, e.x, 0.001f);
    }

    @Test
    public void damageReducesHpAndFlashes() {
        Enemy e = new Enemy();
        e.init(EnemyType.TANK, 800f, 100f, 1f);
        assertEquals((float) EnemyType.TANK.hp, e.hp, 0.001f);
        e.damage(1);
        assertEquals((float) EnemyType.TANK.hp - 1f, e.hp, 0.001f);
        assertTrue(e.flash > 0f);
    }

    @Test
    public void offscreenDetection() {
        Enemy e = new Enemy();
        e.init(EnemyType.DRONE, 800f, GameWorld.HEIGHT + 100f, 1f);
        assertTrue(e.offscreen(GameWorld.HEIGHT));
        e.init(EnemyType.DRONE, 800f, GameWorld.HEIGHT - 100f, 1f);
        assertFalse(e.offscreen(GameWorld.HEIGHT));
    }
}
