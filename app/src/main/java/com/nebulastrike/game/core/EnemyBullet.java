package com.nebulastrike.game.core;

/** Projectile fired by enemies. Slow enough to dodge. */
public class EnemyBullet {
    public float x, y, vx, vy;
    public float radius = 10f;
    public boolean active;

    public void init(float x, float y, float vx, float vy) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.radius = 10f;
        this.active = true;
    }

    /** Custom-radius variant (boss shells are bigger). */
    public void initRadius(float x, float y, float vx, float vy, float radius) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.radius = radius;
        this.active = true;
    }

    public void update(float dt) {
        x += vx * dt;
        y += vy * dt;
    }

    public boolean offscreen(float w, float h) {
        return x < -40f || x > w + 40f || y < -40f || y > h + 40f;
    }
}
