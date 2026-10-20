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
    public WeaponType weapon = WeaponType.VULCAN;
    public float weaponTimer;      // >0 while a powerup weapon is active
    public static final float WEAPON_DURATION = 12f;
    /**
     * 火力等级 1..3（雷电式）：扩展弹道数量，永久生效，坠机降 1 级。
     * POWER 道具 +1（满级 300 分折算），见 GameWorld.collect/fireBullet。
     */
    public int powerLevel = 1;
    public static final int MAX_POWER = 3;
    public boolean hasShield;
    public int bombs = 1;
    public static final int MAX_BOMBS = 3;
    /** 速射道具剩余时间（>0 时射击冷却减半） */
    public float rapidTimer;
    public static final float RAPID_DURATION = 8f;
    /** 僚机道具剩余时间（>0 时两翼无人机自动开火） */
    public float wingmanTimer;
    public static final float WINGMAN_DURATION = 14f;

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
        powerLevel = 1;
        hasShield = false;
        bombs = 1;
        rapidTimer = 0f;
        wingmanTimer = 0f;
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
            cooldown = weapon.cooldown * (rapidTimer > 0f ? 0.5f : 1f);
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
        if (weaponTimer > 0f) {
            weaponTimer -= dt;
            if (weaponTimer <= 0f) weapon = WeaponType.VULCAN;
        }
        if (rapidTimer > 0f) {
            rapidTimer -= dt;
            if (rapidTimer < 0f) rapidTimer = 0f;
        }
        if (wingmanTimer > 0f) {
            wingmanTimer -= dt;
            if (wingmanTimer < 0f) wingmanTimer = 0f;
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
