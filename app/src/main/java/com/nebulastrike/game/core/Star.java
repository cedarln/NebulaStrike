package com.nebulastrike.game.core;

/** One background star with parallax depth. */
public class Star {
    public float x, y;
    public float size;
    public float speed;
    public int layer; // 0 far .. 2 near
    public float twinklePhase;

    public void init(float x, float y, float size, float speed, int layer) {
        this.x = x;
        this.y = y;
        this.size = size;
        this.speed = speed;
        this.layer = layer;
        this.twinklePhase = (float) (Math.random() * Math.PI * 2);
    }

    public void update(float dt, float worldH) {
        y += speed * dt;
        if (y > worldH + size) {
            y = -size;
            x = (float) Math.random() * GameWorld.WIDTH;
        }
    }
}
