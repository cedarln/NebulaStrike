package com.nebulastrike.game.core;

import org.junit.Test;

import static org.junit.Assert.*;

public class ShipTest {
    @Test
    public void followsTargetSmoothly() {
        Ship s = new Ship();
        s.reset(100f, 800f);
        s.setTarget(300f, 800f);
        s.update(1f / 60f, GameWorld.WIDTH, GameWorld.HEIGHT);
        assertTrue("should move toward target but not teleport",
                s.x > 100f && s.x < 300f);
    }

    @Test
    public void staysInsideWorldBounds() {
        Ship s = new Ship();
        s.reset(50f, 50f);
        s.setTarget(-5000f, -5000f);
        for (int i = 0; i < 120; i++) s.update(1f / 60f, GameWorld.WIDTH, GameWorld.HEIGHT);
        assertTrue(s.x >= Ship.RADIUS - 0.01f);
        assertTrue(s.y >= Ship.RADIUS - 0.01f);
    }

    @Test
    public void invulnerabilityExpires() {
        Ship s = new Ship();
        s.reset(800f, 700f);
        s.makeInvulnerable();
        assertTrue(s.invulnerable);
        float t = 0f;
        while (s.invulnerable && t < 5f) {
            s.update(1f / 60f, GameWorld.WIDTH, GameWorld.HEIGHT);
            t += 1f / 60f;
        }
        assertFalse("invulnerability should expire", s.invulnerable);
        assertTrue("expired within ~2s, took " + t, t <= 2.2f);
    }
}
