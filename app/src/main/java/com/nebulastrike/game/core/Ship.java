package com.nebulastrike.game.core;

/**
 * Player ship. Pure simulation object: no Android imports so it is unit-testable.
 * Coordinates are in virtual units: the game runs in a fixed 900x1600 portrait space.
 * 两种移动模式：虚拟方向键（速度语义）与拖拽目标点（平滑跟随，供测试/兼容）。
 */
public class Ship {
    public static final float RADIUS = 34f;
    public static final float MAX_SPEED = 950f;
    public static final float FIRE_COOLDOWN = 0.16f;
    public static final float RESPAWN_INVULN = 2.0f;

    public float x, y;
    private float targetX, targetY;
    private float cooldown;
    public boolean invulnerable;
    private float invulnTimer;

    // 虚拟方向键：每个轴 -1/0/1，由 app 层根据按键集合计算
    public int moveDx, moveDy;

    // loadout state
    public WeaponType weapon = WeaponType.VULCAN;
    public float weaponTimer;      // >0 while a powerup weapon is active
    public static final float WEAPON_DURATION = 12f;
    public boolean hasShield;
    public int bombs = 1;
    public static final int MAX_BOMBS = 3;
    /** 速射道具剩余时间（>0 时射击冷却减半） */
    public float rapidTimer;
    public static final float RAPID_DURATION = 8f;

    public void reset(float x, float y) {
        this.x = x;
        this.y = y;
        targetX = x;
        targetY = y;
        cooldown = 0f;
        invulnerable = false;
        invulnTimer = 0f;
        weapon = WeaponType.VULCAN;
        weaponTimer = 0f;
        hasShield = false;
        bombs = 1;
        moveDx = 0;
        moveDy = 0;
        rapidTimer = 0f;
    }

    /** 虚拟方向键设置移动方向（app 层在按键集合变化时调用）。 */
    public void setMoveDir(int dx, int dy) {
        moveDx = dx;
        moveDy = dy;
    }

    /** 拖拽模式：设置期望位置。 */
    public void setTarget(float tx, float ty) {
        targetX = tx;
        targetY = ty;
    }

    /**
     * 方向键优先；无方向输入时平滑跟随目标点。
     * Returns true if the ship fired this frame.
     */
    public boolean update(float dt, float worldW, float worldH) {
        boolean fired = false;
        cooldown -= dt;
        if (cooldown <= 0f) {
            cooldown = weapon.cooldown * (rapidTimer > 0f ? 0.5f : 1f);
            fired = true;
        }
        if (moveDx != 0 || moveDy != 0) {
            float len = (float) Math.sqrt(moveDx * moveDx + moveDy * moveDy);
            float step = MAX_SPEED * dt;
            x += moveDx / len * step;
            y += moveDy / len * step;
        } else {
            moveToward(targetX, targetY, dt);
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
        return fired;
    }

    private void moveToward(float tx, float ty, float dt) {
        float dx = tx - x, dy = ty - y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist > 1e-3f) {
            float step = Math.min(MAX_SPEED * dt, dist);
            x += dx / dist * step;
            y += dy / dist * step;
        }
    }

    public void makeInvulnerable() {
        invulnerable = true;
        invulnTimer = RESPAWN_INVULN;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
