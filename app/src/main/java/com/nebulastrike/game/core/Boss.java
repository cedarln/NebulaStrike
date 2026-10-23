package com.nebulastrike.game.core;

/**
 * Capital-ship boss. Three HP-driven phases plus destructible appendages.
 * Later encounters grow physically larger and gain more guns / laser pods,
 * so fights escalate from a small scout carrier to a screen-filling leviathan.
 */
public class Boss {
    public static final int MAX_PARTS = 16;
    /** 三阶段召唤的棱镜数量（正反方向各一枚）。 */
    public static final int PRISM_COUNT = 2;

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
    /** 护甲（伤害倍率）与"存活护盾发生器"绑定，见 {@link #damageScale()} */
    private float ramCd = 9f;
    /** >0 = 正在横冲（相位撞击），期间 x 由冲刺控制、不再走正弦巡逻 */
    public float ramTimer;
    public float ramTargetX;
    /** 横冲速度（虚拟单位/秒） */
    public static final float RAM_SPEED = 1150f;
    private static final float RAM_DURATION = 0.5f;
    /** 三阶段棱镜召唤：已召唤过就不再重复，队列由 GameWorld 消费。 */
    public boolean prismsSummoned;
    public boolean prismQueued;

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
        this.ramCd = 8f - 1.2f * tier;
        this.ramTimer = 0f;
        this.ramTargetX = this.x;
        this.prismsSummoned = false;
        this.prismQueued = false;
        layoutParts();
    }

    /** 0..3: shared by art, attack density and component count. */
    public int tier;

    public String title() {
        return titleFor(tier);
    }

    /** 图鉴等无实例场景按等级取名。 */
    public static String titleFor(int tier) {
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
        int guns = tier == 0 ? 2 : tier == 1 ? 4 : tier == 2 ? 4 : 5;
        // 第二个 Boss 起出现激光吊舱，数量随舰体等级增长
        int lasers = tier >= 2 ? 2 : tier == 1 ? 1 : 0;
        int pods = tier >= 3 ? 2 : tier >= 1 ? 1 : 0;      // 分裂舱：投放僚机
        int shields = tier >= 3 ? 2 : tier >= 2 ? 1 : 0;   // 护盾发生器：削弱核心伤害
        int mineBays = tier >= 2 ? 1 : 0;                  // 布雷舱：底部通道投放漂流水雷
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
        float[][] podOffsets = {{-0.74f, 0.44f}, {0.74f, 0.44f}};
        float podHp = 30f + 10f * level;
        for (int i = 0; i < pods; i++) {
            parts[partCount].init(BossPart.Kind.POD,
                    podOffsets[i][0] * r, podOffsets[i][1] * r,
                    pr * 1.15f, podHp, 4.2f + i * 1.6f);
            partCount++;
        }
        float[][] shieldOffsets = {{-0.34f, -0.62f}, {0.34f, -0.62f}};
        float shieldHp = 40f + 14f * level;
        for (int i = 0; i < shields; i++) {
            parts[partCount].init(BossPart.Kind.SHIELD,
                    shieldOffsets[i][0] * r, shieldOffsets[i][1] * r,
                    pr * 1.05f, shieldHp, -1f);
            partCount++;
        }
        for (int i = 0; i < mineBays; i++) {
            parts[partCount].init(BossPart.Kind.MINE,
                    0f, 0.86f * r, pr * 0.94f, 34f + 11f * level, 3.4f + i * 1.2f);
            partCount++;
        }
    }

    public void update(float dt) {
        age += dt;
        if (flash > 0f) flash = Math.max(0f, flash - dt);
        if (staggerTimer > 0f) staggerTimer = Math.max(0f, staggerTimer - dt);
        for (int i = 0; i < partCount; i++) {
            BossPart p = parts[i];
            p.update(dt);
            if (p.active && p.kind == BossPart.Kind.PRISM) {
                p.orbit += p.spin * dt;
                p.ox = (float) Math.cos(p.orbit) * radius * 1.18f;
                p.oy = (float) Math.sin(p.orbit) * radius * 0.6f - radius * 0.1f;
            }
        }

        float hpFrac = hp / maxHp;
        int newPhase = hpFrac > 0.66f ? 1 : (hpFrac > 0.33f ? 2 : 3);
        if (newPhase != phase) {
            phase = newPhase;
            flash = 0.3f;
            if (phase >= 3 && tier >= 1) prismQueued = true;
        }

        // entry glide then sine patrol; larger / later bosses move a bit quicker
        float speed = 55f + phase * 24f + tier * 7f;
        if (ramTimer > 0f) {
            // 横冲：向目标侧高速平移，撞到底部边缘再停下
            ramTimer -= dt;
            float to = ramTargetX - x;
            float step = RAM_SPEED * dt;
            if (Math.abs(to) <= step) x = ramTargetX;
            else x += (to > 0f ? step : -step);
            y = arenaY + (float) Math.sin(age * 6f) * 8f;
            if (ramTimer <= 0f) ramTimer = 0f;
            return;
        }
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
            ramCd -= dt;
        }
    }

    /** 横冲（撞击招式）是否可触发：第二艘 Boss 起、进入二阶段后。 */
    public boolean readyToRam() {
        return tier >= 1 && phase >= 2 && staggerTimer <= 0f && ramTimer <= 0f
                && ramCd <= 0f && y >= arenaY - 4f && !partsAllDestroyed();
    }

    /** 开始一次横冲：冲向远离当前的一侧，扫过全场。 */
    public void startRam() {
        ramTimer = RAM_DURATION;
        ramCd = 9.5f - 0.9f * tier - 0.5f * phase;
        ramTargetX = x < GameWorld.WIDTH / 2f
                ? GameWorld.WIDTH - radius - 30f
                : radius + 30f;
        flash = 0.24f;
    }

    /** 部件 destroyed 后不再阻挡巡逻，但横冲需要立刻停止。 */
    public void cancelRam() {
        ramTimer = 0f;
    }

    public boolean isRamming() {
        return ramTimer > 0f;
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

    /** 三阶段棱镜召唤：第二艘 Boss 起、入场就位、从硬直中恢复后才允许。 */
    public boolean readyToSummonPrism() {
        return tier >= 1 && phase >= 3 && prismQueued && !prismsSummoned
                && staggerTimer <= 0f && y >= arenaY - 4f;
    }

    /** 召唤一对反向环绕的棱镜，加到部件末尾；返回实际数量。 */
    public int summonPrisms() {
        if (tier < 1 || prismsSummoned) return 0;
        prismsSummoned = true;
        prismQueued = false;
        int made = 0;
        float pr = clamp(radius * 0.2f, 14f, 24f);
        float hp = 30f + 10f * level;
        for (int i = 0; i < PRISM_COUNT && partCount < MAX_PARTS; i++) {
            BossPart p = parts[partCount];
            float sign = i == 0 ? 1f : -1f;
            p.init(BossPart.Kind.PRISM, sign * radius * 1.18f, -radius * 0.1f,
                    pr, hp, 1.8f + i * 1.5f);
            p.orbit = i == 0 ? 0f : (float) Math.PI;
            p.spin = sign * (1.05f + 0.08f * tier);
            p.ox = (float) Math.cos(p.orbit) * radius * 1.18f;
            partCount++;
            made++;
        }
        flash = 0.35f;
        return made;
    }

    public int prismsAlive() {
        int n = 0;
        for (int i = 0; i < partCount; i++) {
            BossPart p = parts[i];
            if (p.active && p.kind == BossPart.Kind.PRISM) n++;
        }
        return n;
    }

    public void damage(int amount) {
        hp -= amount * damageScale();
        flash = 0.06f;
    }

    /** 存活的护盾发生器数量（决定减伤倍率）。 */
    public int shieldGeneratorsAlive() {
        int n = 0;
        for (int i = 0; i < partCount; i++) {
            BossPart p = parts[i];
            if (p.active && p.kind == BossPart.Kind.SHIELD) n++;
        }
        return n;
    }

    /** 核心伤害倍率：护盾发生器存活时打折（两个更硬）。 */
    public float damageScale() {
        int n = shieldGeneratorsAlive();
        if (n <= 0) return 1f;
        return n >= 2 ? 0.5f : 0.68f;
    }

    /** 护盾是否仍在线（渲染层画护盾壳、音效层用）。 */
    public boolean shielded() {
        return shieldGeneratorsAlive() > 0;
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
