package com.nebulastrike.game.core;

/** Simple expanding/sparking particle for explosions and engine exhaust. */
public class Particle {
    public float x, y, vx, vy;
    public float life, maxLife;
    public float size;
    public int color; // palette index
    public boolean active;

    public void init(float x, float y, float vx, float vy, float life, float size, int color) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.life = life;
        this.maxLife = life;
        this.size = size;
        this.color = color;
        this.active = true;
    }

    public void update(float dt) {
        x += vx * dt;
        y += vy * dt;
        vx *= (1f - 2.2f * dt);
        vy *= (1f - 2.2f * dt);
        life -= dt;
        if (life <= 0f) active = false;
    }
}
