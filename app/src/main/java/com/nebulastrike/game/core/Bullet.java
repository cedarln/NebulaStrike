package com.nebulastrike.game.core;

/** Player projectile. Straight shot by default, or a homing missile. */
public class Bullet {
    public float x, y;
    public float vx, vy;
    public float radius = 9f;
    public int damage = 1;
    public boolean homing;
    public float speed;
    public boolean active;

    public void init(float x, float y, float vy) {
        init(x, y, 0f, vy, 1, false);
    }

    public void init(float x, float y, float vx, float vy, int damage, boolean homing) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.damage = damage;
        this.homing = homing;
        this.speed = (float) Math.sqrt(vx * vx + vy * vy);
        this.active = true;
    }

    public void update(float dt) {
        x += vx * dt;
        y += vy * dt;
    }
}
