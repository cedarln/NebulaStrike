package com.nebulastrike.game.core;

/**
 * Player ship. Pure simulation object: no Android imports so it is unit-testable.
 * Coordinates are in virtual units: the game runs in a fixed 1600x900 space.
 */
public class Ship {
    public static final float RADIUS = 34f;
    public static final float MAX_SPEED = 950f;
    public static final float FIRE_COOLDOWN = 0.16f;
    public static final float RESPAWN_INVULN = 2.0f;

    public float x, y;
    private float targetX, targetY;
    private float cooldown;
    public boolean invulnerable;
    private float invulnTimer;

    public void reset(float x, float y) {
        this.x = x;
        this.y = y;
        targetX = x;
        targetY = y;
        cooldown = 0f;
        invulnerable = false;
        invulnTimer = 0f;
    }

    /** Called on touch drag: sets the desired position (virtual coords). */
    public void setTarget(float tx, float ty) {
        targetX = tx;
        targetY = ty;
    }

    /**
     * Moves toward the target with a bounded speed so control feels smooth.
     * Returns true if the ship fired this frame.
     */
    public boolean update(float dt, float worldW, float worldH) {
        boolean fired = false;
        cooldown -= dt;
        if (cooldown <= 0f) {
            cooldown = FIRE_COOLDOWN;
            fired = true;
        }
        moveToward(targetX, targetY, dt, worldW, worldH);
        if (invulnerable) {
            invulnTimer -= dt;
            if (invulnTimer <= 0f) invulnerable = false;
        }
        return fired;
    }

    private void moveToward(float tx, float ty, float dt, float worldW, float worldH) {
        float dx = tx - x, dy = ty - y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist > 1e-3f) {
            float step = Math.min(MAX_SPEED * dt, dist);
            x += dx / dist * step;
            y += dy / dist * step;
        }
        float margin = RADIUS;
        x = clamp(x, margin, worldW - margin);
        y = clamp(y, margin, worldH - margin);
    }

    public void makeInvulnerable() {
        invulnerable = true;
        invulnTimer = RESPAWN_INVULN;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
