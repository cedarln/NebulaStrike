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
    /** 布雷艇下一次投雷的倒计时（<=0 时由 GameWorld 结算）。 */
    public float mineCd;
    /** 母舰下一次放机的倒计时。 */
    public float droneCd;
    /** 盾卫正面护盾耐久：>0 时挡下所有非穿透弹（激光与弹跳弹照样能穿）。 */
    public float shieldHp;
    public static final float SHIELDER_SHIELD = 6f;
    /** 精英词缀：厚血、高分、必掉道具，渲染层加一圈光环。 */
    public boolean elite;
    /** 精英倍率（生命、分值）。 */
    public static final float ELITE_HP_MULT = 2.6f;
    public static final int ELITE_SCORE_MULT = 3;
    private float originX;
    private float dir = 1f;
    private float speedScale;

    /** 狙击塔悬停高度（虚拟坐标，越小说明越靠屏幕顶部）。 */
    public static final float SNIPER_HOVER_Y = 300f;
    /** 母舰巡航高度：到位后先悬停放机，再俯冲离场。 */
    public static final float CARRIER_CRUISE_Y = 420f;

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
        this.hoverCd = type == EnemyType.SNIPER ? 8.5f : type == EnemyType.CARRIER ? 12f : 0f;
        this.splitDepth = 0;
        this.mineCd = type == EnemyType.MINER ? 1.2f : 0f;
        this.droneCd = type == EnemyType.CARRIER ? 1.6f : 0f;
        this.shieldHp = type == EnemyType.SHIELDER ? SHIELDER_SHIELD : 0f;
        this.elite = false;
        this.diveSpeed = type.baseSpeed * speedScale;
        switch (type) {
            case TANK: fireCd = 1.4f + GameWorld.RNG.get().nextFloat() * 0.6f; break;
            case HUNTER: fireCd = 2.0f + GameWorld.RNG.get().nextFloat() * 0.8f; break;
            case SPLITTER: fireCd = 2.0f + GameWorld.RNG.get().nextFloat() * 0.8f; break;
            case SNIPER: fireCd = 1.5f + GameWorld.RNG.get().nextFloat() * 0.5f; break;
            case SHIELDER: fireCd = 2.4f + GameWorld.RNG.get().nextFloat() * 0.8f; break;
            default: fireCd = -1f; // does not fire
        }
    }

    /** 打上精英词缀：厚血、减速一点点方便玩家优先集火，掉落保底由 GameWorld 处理。 */
    public void makeElite() {
        elite = true;
        hp = maxHp = Math.round(type.hp * ELITE_HP_MULT);
        speedScale *= 0.9f;
        diveSpeed = type.baseSpeed * speedScale;
    }

    /** 正面护盾是否还在（挡弹判定与渲染都用它）。 */
    public boolean hasFrontShield() {
        return type == EnemyType.SHIELDER && shieldHp > 0f;
    }

    /** 受击时先扣护盾耐久，护盾崩溃后本体才 vulnerable（返回值仅用于表现）。 */
    public void chipShield(float amount) {
        if (shieldHp > 0f) shieldHp = Math.max(0f, shieldHp - amount);
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
            case MINER:
                // 布雷艇：慢速推进并左右小幅摆动，投雷倒计时由 GameWorld 结算
                y += speed() * dt;
                x = originX + (float) Math.sin(age * 0.9f) * 60f;
                x = clamp(x, type.radius, worldW - type.radius);
                if (mineCd > 0f) mineCd = Math.max(0f, mineCd - dt);
                break;
            case CARRIER: {
                // 母舰：降到巡航高度后悬停放机，超时再俯冲离场
                if (y < CARRIER_CRUISE_Y) {
                    y += speed() * dt;
                    break;
                }
                hoverCd -= dt;
                if (hoverCd <= 0f) {
                    y += speed() * 1.4f * dt;
                    break;
                }
                x += dir * 60f * dt;
                if (x < type.radius + 30f || x > worldW - type.radius - 30f) {
                    dir = -dir;
                    x = clamp(x, type.radius + 30f, worldW - type.radius - 30f);
                }
                if (droneCd > 0f) droneCd = Math.max(0f, droneCd - dt);
                break;
            }
            case SHIELDER:
                // 盾卫：正面朝下缓慢压上，偶尔射一发扇形弹逼玩家走位
                y += speed() * dt;
                x = originX + (float) Math.sin(age * 0.7f) * 40f;
                x = clamp(x, type.radius, worldW - type.radius);
                break;
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
