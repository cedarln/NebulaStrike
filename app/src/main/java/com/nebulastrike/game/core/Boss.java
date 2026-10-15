package com.nebulastrike.game.core;

/**
 * Capital-ship boss. Three phases unlocked as HP drops; each phase changes
 * movement speed and attack patterns. Pure simulation logic.
 */
public class Boss {
    public static final float RADIUS = 92f;

    public float x, y;
    public float hp, maxHp;
    public int level;      // 0-based boss index, scales stats
    public int phase = 1;  // 1..3
    public float age;
    public float flash;
    public boolean active;
    private float dir = 1f;
    private float attackCd = 2f;
    /** until next minion summon; package-private so tests can schedule one */
    float minionCd = 6f;

    public void init(int level) {
        this.level = level;
        this.maxHp = 110 + 55 * level;
        this.hp = maxHp;
        this.x = GameWorld.WIDTH / 2f;
        this.y = -RADIUS - 20f;
        this.age = 0f;
        this.flash = 0f;
        this.phase = 1;
        this.active = true;
        this.dir = GameWorld.RNG.get().nextBoolean() ? 1f : -1f;
        this.attackCd = 2.2f;
        this.minionCd = 6f;
    }

    public void update(float dt) {
        age += dt;
        if (flash > 0f) flash -= dt;

        float hpFrac = hp / maxHp;
        int newPhase = hpFrac > 0.66f ? 1 : (hpFrac > 0.33f ? 2 : 3);
        if (newPhase != phase) {
            phase = newPhase;
            flash = 0.3f;
            GameWorld.RNG.get(); // keep RNG stream alive; phase fx handled via event
        }

        // entry glide then sine patrol; speed grows with phase
        float speed = 60f + phase * 26f;
        if (y < 150f) {
            y += 90f * dt;
        } else {
            x += dir * speed * dt;
            if (x < RADIUS + 30f) { x = RADIUS + 30f; dir = 1f; }
            if (x > GameWorld.WIDTH - RADIUS - 30f) { x = GameWorld.WIDTH - RADIUS - 30f; dir = -1f; }
            y = 150f + (float) Math.sin(age * 0.9f) * 26f;
        }

        attackCd -= dt * (0.8f + 0.25f * phase);
        minionCd -= dt;
    }

    public boolean readyToAttack() {
        return y > 80f && attackCd <= 0f;
    }

    public void resetAttack() {
        attackCd = phase == 3 ? 1.1f : phase == 2 ? 1.5f : 1.9f;
    }

    public boolean readyToSummon() {
        return phase >= 2 && y > 120f && minionCd <= 0f;
    }

    public void resetSummon() {
        minionCd = 6.5f;
    }

    public void damage(int amount) {
        hp -= amount;
        flash = 0.06f;
    }
}
