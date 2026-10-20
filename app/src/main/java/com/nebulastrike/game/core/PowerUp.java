package com.nebulastrike.game.core;

/** Falling pickup. Drifts gently and is attracted when the ship is close. */
public class PowerUp {
    public enum Kind { SPREAD, MISSILE, SHIELD, BOMB, RAPID, X2, LIFE }

    public Kind kind;
    public float x, y;
    public float age;
    public boolean active;

    public void init(Kind kind, float x, float y) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.age = 0f;
        this.active = true;
    }

    public void update(float dt, float shipX, float shipY) {
        age += dt;
        // gentle fall + sway
        y += 110f * dt;
        x += (float) Math.sin(age * 2.6f) * 46f * dt;
        // magnet: accelerate toward ship when close
        float dx = shipX - x, dy = shipY - y;
        float d2 = dx * dx + dy * dy;
        if (d2 < 260f * 260f) {
            float d = (float) Math.sqrt(d2);
            if (d > 1f) {
                x += dx / d * 520f * dt;
                y += dy / d * 520f * dt;
            }
        }
    }

    public boolean offscreen(float h) {
        return y > h + 40f;
    }
}
