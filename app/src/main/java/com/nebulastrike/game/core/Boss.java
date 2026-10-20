package com.nebulastrike.game.core;

/**
 * Capital-ship boss. Three HP-driven phases plus destructible appendages.
 * Later encounters grow physically larger and gain more guns / laser pods,
 * so fights escalate from a small scout carrier to a screen-filling leviathan.
 */
public class Boss {
    public static final int MAX_PARTS = 8;

    public final BossPart[] parts = new BossPart[MAX_PARTS];
    public int partCount;
    /** visual hull radius; grows with boss level */
    public float radius = 92f;
    /** slightly smaller hitbox so grazing the hull edge is forgiving */
    public float hitRadius = 76f;
    public float arenaY = 150f;

    public float x, y;
    public float hp, maxHp;
    public int level;      // 0-based boss index, scales stats
    public int phase = 1;  // 1..3
    public float age;
    public float flash;
    public boolean active;
    /** set once every appendage has been destroyed */
    public boolean allPartsDown;
    /** >0 while the disarmed boss is staggered and cannot attack */
    public float staggerTimer;
    private float dir = 1f;
    private float attackCd = 2f;
    /** until next minion summon; package-private so tests can schedule one */
    float minionCd = 6f;

    public Boss() {
        for (int i = 0; i < parts.length; i++) parts[i] = new BossPart();
    }

    public void init(int level) {
        this.level = level;
        this.tier = Math.min(level, 3);
        this.maxHp = 420f + 200f * level;
        this.hp = maxHp;
        this.radius = 60f + 14f * tier;
        this.hitRadius = radius * 0.84f;
        this.arenaY = 120f + radius * 0.35f;
        this.x = GameWorld.WIDTH / 2f;
        this.y = -radius - 20f;
        this.age = 0f;
        this.flash = 0f;
        this.phase = 1;
        this.active = true;
        this.dir = GameWorld.RNG.get().nextBoolean() ? 1f : -1f;
        this.attackCd = 2.2f;
        this.minionCd = 6f;
        this.allPartsDown = false;
        this.staggerTimer = 0f;
        layoutParts();
    }

    /** 0..3: shared by art, attack density and component count. */
    public int tier;

    public String title() {
        switch (tier) {
            case 0: return "SCOUT CARRIER";
            case 1: return "DREADNOUGHT";
            case 2: return "LEVIATHAN";
            default: return "WORLD EATER";
        }
    }

    private void layoutParts() {
        for (BossPart p : parts) p.active = false;
        partCount = 0;
        float r = radius;
        float pr = clamp(radius * 0.24f, 15f, 26f);
        int guns = tier == 0 ? 2 : tier == 1 ? 4 : tier == 2 ? 4 : 6;
        // 第二个 Boss 起出现激光吊舱，数量随舰体等级增长
        int lasers = tier >= 2 ? 2 : tier == 1 ? 1 : 0;
        float[][] gunOffsets = {
                {-0.92f, 0.06f}, {0.92f, 0.06f},
                {-0.52f, 0.72f}, {0.52f, 0.72f},
                {-0.98f, -0.38f}, {0.98f, -0.38f}
        };
        float gunHp = 26f + 9f * level;
        for (int i = 0; i < guns && i < gunOffsets.length; i++) {
            parts[partCount].init(BossPart.Kind.GUN,
                    gunOffsets[i][0] * r, gunOffsets[i][1] * r,
                    pr, gunHp, 1.3f + i * 0.3f);
            partCount++;
        }
        float[][] laserOffsets = lasers == 1
                ? new float[][]{{0f, -0.72f}}
                : new float[][]{{-0.56f, -0.68f}, {0.56f, -0.68f}};
        float laserHp = 34f + 12f * level;
        for (int i = 0; i < lasers; i++) {
            parts[partCount].init(BossPart.Kind.LASER,
                    laserOffsets[i][0] * r, laserOffsets[i][1] * r,
                    pr * 1.08f, laserHp, 3.4f + i * 0.9f);
            partCount++;
        }
    }

    public void update(float dt) {
        age += dt;
        if (flash > 0f) flash = Math.max(0f, flash - dt);
        if (staggerTimer > 0f) staggerTimer = Math.max(0f, staggerTimer - dt);
        for (int i = 0; i < partCount; i++) parts[i].update(dt);

        float hpFrac = hp / maxHp;
        int newPhase = hpFrac > 0.66f ? 1 : (hpFrac > 0.33f ? 2 : 3);
        if (newPhase != phase) {
            phase = newPhase;
            flash = 0.3f;
        }

        // entry glide then sine patrol; larger / later bosses move a bit quicker
        float speed = 55f + phase * 24f + tier * 7f;
        if (y < arenaY) {
            y += 90f * dt;
        } else {
            x += dir * speed * dt;
            if (x < radius + 30f) { x = radius + 30f; dir = 1f; }
            if (x > GameWorld.WIDTH - radius - 30f) {
                x = GameWorld.WIDTH - radius - 30f;
                dir = -1f;
            }
            y = arenaY + (float) Math.sin(age * 0.9f) * 26f;
        }

        if (staggerTimer <= 0f) {
            attackCd -= dt * (0.8f + 0.25f * phase);
            minionCd -= dt;
        }
    }

    public boolean readyToAttack() {
        return staggerTimer <= 0f && y > 80f && attackCd <= 0f;
    }

    public void resetAttack() {
        attackCd = phase == 3 ? 1.1f : phase == 2 ? 1.5f : 1.9f;
    }

    public boolean readyToSummon() {
        return staggerTimer <= 0f && phase >= 2 && y > 120f && minionCd <= 0f;
    }

    public void resetSummon() {
        minionCd = 6.5f;
    }

    public void damage(int amount) {
        hp -= amount;
        flash = 0.06f;
    }

    /** Called when the last appendage is destroyed: brief damage window. */
    public void disarm() {
        allPartsDown = true;
        staggerTimer = 3f;
        attackCd = Math.max(attackCd, 2.2f);
    }

    public int partsAlive() {
        int n = 0;
        for (int i = 0; i < partCount; i++) if (parts[i].active) n++;
        return n;
    }

    public boolean partsAllDestroyed() {
        for (int i = 0; i < partCount; i++) if (parts[i].active) return false;
        return partCount > 0;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
