package com.nebulastrike.game.core;

/** Player bullet. */
public class Bullet {
    public float x, y;
    public float vy;
    public float radius = 9f;
    public boolean active;

    public void init(float x, float y, float vy) {
        this.x = x;
        this.y = y;
        this.vy = vy;
        active = true;
    }

    public void update(float dt) {
        y += vy * dt;
    }
}
