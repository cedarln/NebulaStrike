package com.nebulastrike.game.core;

/**
 * Player ship. Pure simulation object: no Android imports so it is unit-testable.
 * Coordinates are in virtual units: the game runs in a fixed 900x1600 portrait space.
 * 移动为"相对拖动"模式：app 层把手指相对按下锚点的位移换算成目标点，
 * 飞船以限速平滑跟随（MOBA/射击常见的浮动摇杆手感）。
 */
public class Ship {
    public static final float RADIUS = 34f;
    /** 拖动跟随的最高速度：老版本 950 跟不上快速滑动，手感发飘。 */
    public static final float MAX_DRAG_SPEED = 2400f;
    public static final float FIRE_COOLDOWN = 0.16f;
    public static final float RESPAWN_INVULN = 2.0f;

    public float x, y;
    /** 上一帧位置（扫掠碰撞用，避免高速拖动穿过敌弹）。 */
    public float prevX, prevY;
    private float targetX, targetY;
    private float cooldown;
    public boolean invulnerable;
    private float invulnTimer;

    // loadout state
    /** 当前主武器（最近拾取的特殊武器；没有特殊武器时为 VULCAN）。 */
    public WeaponType weapon = WeaponType.VULCAN;
    /** 主武器剩余时间（镜像 weaponTimers[weapon]；兼容旧代码/渲染）。 */
    public float weaponTimer;
    public static final float WEAPON_DURATION = 12f;
    /** 同类武器重复拾取可叠加的层数上限（每层增加弹道密度）。 */
    public static final int MAX_WEAPON_STACK = 3;
    /** 每种特殊武器的剩余时间与叠加层数；VULCAN 槽位恒为 0。 */
    public final float[] weaponTimers = new float[WeaponType.values().length];
    public final int[] weaponStacks = new int[WeaponType.values().length];
    /**
     * 火力等级 1..3（雷电式）：扩展弹道数量，永久生效，坠机降 1 级。
     * POWER 道具 +1（满级 300 分折算），见 GameWorld.collect/fireWeapon。
     */
    public int powerLevel = 1;
    public static final int MAX_POWER = 3;
    public boolean hasShield;
    /** 护盾层数（最多 2 层，每层挡一次伤害）。 */
    public int shieldLayers;
    public static final int MAX_SHIELD_LAYERS = 2;
    public int bombs = 1;
    public static final int MAX_BOMBS = 3;
    /** 速射道具剩余时间（>0 时射击冷却减半） */
    public float rapidTimer;
    public static final float RAPID_DURATION = 8f;
    /** 僚机道具剩余时间（>0 时两翼无人机自动开火） */
    public float wingmanTimer;
    public static final float WINGMAN_DURATION = 14f;
    /** 擦弹充满后的过载时间（更快射速 + 火力临时 +1） */
    public float overdriveTimer;
    public static final float OVERDRIVE_DURATION = 5f;
    public static final float OVERDRIVE_FIRE_MULT = 0.55f;
    /** 限时道具重复拾取时最多延长到基础时长的 2 倍。 */
    public static final float MAX_TIMER_MULT = 2f;

    public void reset(float x, float y) {
        this.x = x;
        this.y = y;
        prevX = x;
        prevY = y;
        targetX = x;
        targetY = y;
        cooldown = 0f;
        invulnerable = false;
        invulnTimer = 0f;
        weapon = WeaponType.VULCAN;
        weaponTimer = 0f;
        for (int i = 0; i < weaponTimers.length; i++) {
            weaponTimers[i] = 0f;
            weaponStacks[i] = 0;
        }
        powerLevel = 1;
        hasShield = false;
        shieldLayers = 0;
        bombs = 1;
        rapidTimer = 0f;
        wingmanTimer = 0f;
        overdriveTimer = 0f;
    }

    /** 拾取特殊武器：不替换已有武器，同类则叠层并刷新持续时间。 */
    public void addWeapon(WeaponType type) {
        if (type == WeaponType.VULCAN) return;
        int i = type.ordinal();
        weaponStacks[i] = Math.min(MAX_WEAPON_STACK, weaponStacks[i] + 1);
        weaponTimers[i] = WEAPON_DURATION;
        weapon = type;
        weaponTimer = WEAPON_DURATION;
    }

    /** 当前是否存在任意特殊武器（含手动指定、尚未计时的状态）。 */
    public boolean hasSpecialWeapon() {
        if (weapon != WeaponType.VULCAN) return true;
        for (int i = 1; i < weaponStacks.length; i++) {
            if (weaponStacks[i] > 0) return true;
        }
        return false;
    }

    /** 该武器本帧是否参与射击（主武器或已叠层的副武器）。 */
    public boolean isWeaponEquipped(WeaponType type) {
        if (type == WeaponType.VULCAN) return !hasSpecialWeapon();
        return weapon == type || weaponStacks[type.ordinal()] > 0;
    }

    /** 已叠层数；主武器至少按 1 层处理（兼容测试/外部直接指定）。 */
    public int stackOf(WeaponType type) {
        if (type == WeaponType.VULCAN) return 0;
        int s = weaponStacks[type.ordinal()];
        return weapon == type ? Math.max(1, s) : s;
    }

    public float timerOf(WeaponType type) {
        if (type == WeaponType.VULCAN) return 0f;
        int i = type.ordinal();
        if (weapon == type && weaponStacks[i] <= 0) return weaponTimer;
        return weaponTimers[i];
    }

    public void addShield() {
        if (!hasShield) {
            hasShield = true;
            shieldLayers = 1;
        } else if (shieldLayers < MAX_SHIELD_LAYERS) {
            shieldLayers++;
        }
    }

    /** @return true if a layer absorbed the hit (hasShield 同步更新) */
    public boolean consumeShield() {
        if (!hasShield) return false;
        shieldLayers = Math.max(0, shieldLayers - 1);
        if (shieldLayers == 0) hasShield = false;
        return true;
    }

    /** 拖拽模式：设置期望位置（限速跟随）。 */
    public void setTarget(float tx, float ty) {
        targetX = tx;
        targetY = ty;
    }

    /** 暂停/死亡时冻结目标，避免恢复后继续飞向旧目标点。 */
    public void snapTarget() {
        targetX = x;
        targetY = y;
    }

    /** 清零射击冷却，下一帧立即开火（测试与重生流程用）。 */
    public void resetCooldown() {
        cooldown = 0f;
    }

    /** Smooth-follows the target with a bounded speed. Returns true if the ship fired. */
    public boolean update(float dt, float worldW, float worldH) {
        boolean fired = false;
        cooldown -= dt;
        if (cooldown <= 0f) {
            cooldown = weapon.cooldown
                    * (rapidTimer > 0f ? 0.5f : 1f)
                    * (overdriveTimer > 0f ? OVERDRIVE_FIRE_MULT : 1f);
            fired = true;
        }
        prevX = x;
        prevY = y;
        float dx = targetX - x, dy = targetY - y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < 1.5f) {
            x = targetX;
            y = targetY;
        } else {
            // 指数逼近给出"贴手"的小位移跟随，速度上限保证快速甩动不瞬移
            float k = 1f - (float) Math.exp(-24f * dt);
            float step = Math.min(MAX_DRAG_SPEED * dt, dist * k);
            x += dx / dist * step;
            y += dy / dist * step;
        }
        float margin = RADIUS;
        x = clamp(x, margin, worldW - margin);
        y = clamp(y, margin, worldH - margin);
        if (invulnerable) {
            invulnTimer -= dt;
            if (invulnTimer <= 0f) invulnerable = false;
        }
        // 所有特殊武器独立倒计时；主武器耗尽时自动把仍在生效的副武器提升为主武器
        boolean primaryExpired = false;
        for (int i = 1; i < weaponTimers.length; i++) {
            if (weaponTimers[i] <= 0f) continue;
            weaponTimers[i] -= dt;
            if (weaponTimers[i] <= 0f) {
                weaponTimers[i] = 0f;
                weaponStacks[i] = 0;
                if (weapon.ordinal() == i) primaryExpired = true;
            }
        }
        if (weapon != WeaponType.VULCAN) {
            int wi = weapon.ordinal();
            if (weaponStacks[wi] > 0) {
                weaponTimer = weaponTimers[wi];
            } else if (weaponTimer > 0f) {
                // 兼容旧逻辑/外部直接设置的计时器
                weaponTimer -= dt;
                if (weaponTimer <= 0f) {
                    weaponTimer = 0f;
                    primaryExpired = true;
                }
            }
        }
        if (primaryExpired) {
            WeaponType next = WeaponType.VULCAN;
            float best = 0f;
            for (int i = 1; i < weaponTimers.length; i++) {
                if (weaponStacks[i] > 0 && weaponTimers[i] > best) {
                    best = weaponTimers[i];
                    next = WeaponType.values()[i];
                }
            }
            weapon = next;
            weaponTimer = next == WeaponType.VULCAN ? 0f : weaponTimers[next.ordinal()];
        }
        if (rapidTimer > 0f) {
            rapidTimer -= dt;
            if (rapidTimer < 0f) rapidTimer = 0f;
        }
        if (wingmanTimer > 0f) {
            wingmanTimer -= dt;
            if (wingmanTimer < 0f) wingmanTimer = 0f;
        }
        if (overdriveTimer > 0f) {
            overdriveTimer -= dt;
            if (overdriveTimer < 0f) overdriveTimer = 0f;
        }
        return fired;
    }

    public void makeInvulnerable() {
        invulnerable = true;
        invulnTimer = RESPAWN_INVULN;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
