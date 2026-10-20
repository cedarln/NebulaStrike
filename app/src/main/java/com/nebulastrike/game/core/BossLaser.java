package com.nebulastrike.game.core;

/**
 * Boss laser: a short telegraphed warmup line followed by a damaging beam.
 * The beam is a fixed segment once firing starts, so the player can dodge out
 * of the locked line during the warning.
 */
public class BossLaser {
    public static final float LENGTH = 2000f;

    public float x, y, angle;
    public float warmup, beam;
    public float totalWarmup, totalBeam;
    public boolean active;
    public boolean firing;
    /** only one hit per beam, even if the player stays inside it */
    public boolean hitThisShot;

    public void init(float x, float y, float angle, float warmup, float beam) {
        this.x = x;
        this.y = y;
        this.angle = angle;
        this.warmup = warmup;
        this.beam = beam;
        this.totalWarmup = Math.max(0.01f, warmup);
        this.totalBeam = Math.max(0.01f, beam);
        this.active = true;
        this.firing = false;
        this.hitThisShot = false;
    }

    public void update(float dt) {
        if (!active) return;
        if (!firing) {
            warmup -= dt;
            if (warmup <= 0f) {
                warmup = 0f;
                firing = true;
            }
        } else {
            beam -= dt;
            if (beam <= 0f) {
                beam = 0f;
                active = false;
            }
        }
    }

    public float endX() { return x + (float) Math.cos(angle) * LENGTH; }
    public float endY() { return y + (float) Math.sin(angle) * LENGTH; }
    /** 0..1 charge-up progress for the warning line. */
    public float warmupProgress() {
        if (firing) return 1f;
        return 1f - Math.max(0f, warmup) / totalWarmup;
    }
}
