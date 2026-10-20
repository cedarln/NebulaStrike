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
        this.twinklePhase = GameWorld.RNG.get().nextFloat() * (float) Math.PI * 2f;
    }

    public void update(float dt, float worldH) {
        y += speed * dt;
        if (y > worldH + size) {
            y = -size;
            x = GameWorld.RNG.get().nextFloat() * GameWorld.WIDTH;
        }
    }
}
