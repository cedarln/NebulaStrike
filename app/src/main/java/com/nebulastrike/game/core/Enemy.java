package com.nebulastrike.game.core;

/** Enemy ship. Movement pattern depends on {@link EnemyType}. */
public class Enemy {
    public EnemyType type;
    public float x, y;
    public float hp;
    public boolean active;
    /** age in seconds, used for sine/zig-zag patterns */
    public float age;
    /** hit flash timer for renderer */
    public float flash;
    /** cooldown until next shot; <=0 means this archetype does not fire */
    public float fireCd;
    private float originX;
    private float dir = 1f;

    public void init(EnemyType type, float x, float y, float speedScale) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.originX = x;
        this.hp = type.hp;
        this.active = true;
        this.age = 0f;
        this.flash = 0f;
        this.dir = GameWorld.RNG.get().nextBoolean() ? 1f : -1f;
        this.speedScale = speedScale;
        switch (type) {
            case TANK: fireCd = 1.4f + GameWorld.RNG.get().nextFloat() * 0.6f; break;
            case HUNTER: fireCd = 2.0f + GameWorld.RNG.get().nextFloat() * 0.8f; break;
            default: fireCd = -1f; // does not fire
        }
    }

    private float speedScale;

    public float speed() {
        return type.baseSpeed * speedScale;
    }

    public void update(float dt, float worldW) {
        age += dt;
        if (flash > 0f) flash -= dt;
        switch (type) {
            case DRONE:
                y += speed() * dt;
                break;
            case WEAVER:
                y += speed() * dt;
                x = originX + (float) Math.sin(age * 3.2f) * 130f;
                break;
            case HUNTER:
                y += speed() * dt;
                x += dir * 260f * dt;
                if (x < type.radius || x > worldW - type.radius) {
                    dir = -dir;
                    x = clamp(x, type.radius, worldW - type.radius);
                }
                break;
            case TANK:
                y += speed() * dt;
                break;
        }
    }

    public void damage(int amount) {
        hp -= amount;
        flash = 0.08f;
    }

    public boolean offscreen(float worldH) {
        return y - type.radius > worldH;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
