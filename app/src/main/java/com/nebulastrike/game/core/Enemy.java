package com.nebulastrike.game.core;

/**
 * Enemy ship. Movement pattern depends on {@link EnemyType}.
 * 需要瞄准/追踪飞船的兵种走 4 参 {@link #update(float, float, float, float)}；
 * 2 参重载保持旧调用点与测试兼容（等价于飞船在屏幕下方中央）。
 */
public class Enemy {
    public EnemyType type;
    public float x, y;
    public float hp;
    /** 满血值：医疗艇回血不能超过它，狙击塔悬停也需要知道自己是否受损。 */
    public float maxHp;
    public boolean active;
    /** age in seconds, used for sine/zig-zag patterns */
    public float age;
    /** hit flash timer for renderer */
    public float flash;
    /** cooldown until next shot; <=0 means this archetype does not fire */
    public float fireCd;
    /** 医疗艇的治疗冷却（<=0 表示本帧不治疗，由 GameWorld 统一结算）。 */
    public float healCd;
    /** 狙击塔悬停剩余时间，耗尽后继续俯冲离场。 */
    public float hoverCd;
    /** 分裂舱被击毁时生成的子机层数（防止无限分裂）。 */
    public int splitDepth;
    /** 自杀机锁定强度：越接近飞船加速越猛。 */
    public float diveSpeed;
    /** 治疗脉冲余量（1 = 刚释放，0 = 无）；渲染层据此画扩散环。 */
    public float healFx;
    private float originX;
    private float dir = 1f;
    private float speedScale;

    /** 狙击塔悬停高度（虚拟坐标，越小说明越靠屏幕顶部）。 */
    public static final float SNIPER_HOVER_Y = 300f;

    public void init(EnemyType type, float x, float y, float speedScale) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.originX = x;
        this.hp = type.hp;
        this.maxHp = type.hp;
        this.active = true;
        this.age = 0f;
        this.flash = 0f;
        this.dir = GameWorld.RNG.get().nextBoolean() ? 1f : -1f;
        this.speedScale = speedScale;
        this.healCd = 1.1f;
        this.healFx = 0f;
        this.hoverCd = type == EnemyType.SNIPER ? 8.5f : 0f;
        this.splitDepth = 0;
        this.diveSpeed = type.baseSpeed * speedScale;
        switch (type) {
            case TANK: fireCd = 1.4f + GameWorld.RNG.get().nextFloat() * 0.6f; break;
            case HUNTER: fireCd = 2.0f + GameWorld.RNG.get().nextFloat() * 0.8f; break;
            case SPLITTER: fireCd = 2.0f + GameWorld.RNG.get().nextFloat() * 0.8f; break;
            case SNIPER: fireCd = 1.5f + GameWorld.RNG.get().nextFloat() * 0.5f; break;
            default: fireCd = -1f; // does not fire
        }
    }

    public float speed() {
        return type.baseSpeed * speedScale;
    }

    /** 兼容旧调用：不需要飞船位置的兵种行为。 */
    public void update(float dt, float worldW) {
        update(dt, worldW, Float.NaN, Float.NaN);
    }

    public void update(float dt, float worldW, float shipX, float shipY) {
        age += dt;
        if (flash > 0f) flash -= dt;
        if (healFx > 0f) healFx = Math.max(0f, healFx - dt / 1.1f);
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
            case SUICIDER: {
                // 锁定飞船俯冲：初速慢、越近越快，给玩家反应窗口
                float cap = speed() * 2.6f;
                diveSpeed = Math.min(cap, diveSpeed + speed() * 2.2f * dt);
                float tx = shipX, ty = shipY;
                if (Float.isNaN(tx)) { tx = originX; ty = GameWorld.HEIGHT; }
                float dx = tx - x, dy = ty - y;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < 1f) { dx = 0f; dy = 1f; d = 1f; }
                x += dx / d * diveSpeed * dt;
                y += dy / d * diveSpeed * dt;
                x = clamp(x, type.radius, worldW - type.radius);
                break;
            }
            case SPLITTER:
                y += speed() * dt;
                x = originX + (float) Math.sin(age * 1.4f) * 46f;
                break;
            case HEALER:
                y += speed() * dt;
                x = originX + (float) Math.sin(age * 1.1f) * 90f;
                if (healCd > 0f) healCd = Math.max(0f, healCd - dt);
                break;
            case SNIPER: {
                if (y < SNIPER_HOVER_Y) {
                    y += speed() * dt;
                    x = originX + (float) Math.sin(age * 0.8f) * 70f;
                    break;
                }
                hoverCd -= dt;
                if (hoverCd <= 0f) {          // 悬停结束，离场俯冲
                    y += speed() * 1.25f * dt;
                    break;
                }
                x += dir * 70f * dt;
                if (x < type.radius + 20f || x > worldW - type.radius - 20f) {
                    dir = -dir;
                    x = clamp(x, type.radius + 20f, worldW - type.radius - 20f);
                }
                break;
            }
        }
    }

    /** 狙击塔正在充能（渲染层据此画预警线）。 */
    public boolean isCharging() {
        return type == EnemyType.SNIPER && fireCd > 0f && fireCd < 0.7f;
    }

    public void heal(float amount) {
        if (amount <= 0f || !active) return;
        hp = Math.min(maxHp, hp + amount);
        flash = Math.max(flash, 0.06f);
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
