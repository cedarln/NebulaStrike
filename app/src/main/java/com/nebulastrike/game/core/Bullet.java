package com.nebulastrike.game.core;

/**
 * Player projectile. Straight shot by default, or a homing missile.
 * piercing=true（激光）：命中后不消失，hitCooldown 内不对同一目标重复结算。
 */
public class Bullet {
    public float x, y;
    public float vx, vy;
    public float radius = 9f;
    public int damage = 1;
    public boolean homing;
    /** 僚机射出的弹丸（渲染为小型粉色弹） */
    public boolean drone;
    public float speed;
    public boolean active;
    // laser
    public boolean piercing;
    public float hitCooldown;   // >0 时跳过命中结算（防止单敌多扣血）
    public float len;           // 渲染光束长度（0 = 普通圆弹）

    public void init(float x, float y, float vy) {
        init(x, y, 0f, vy, 1, false);
    }

    public void init(float x, float y, float vx, float vy, int damage, boolean homing) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.damage = damage;
        this.homing = homing;
        this.drone = false;
        this.speed = (float) Math.sqrt(vx * vx + vy * vy);
        this.active = true;
        this.piercing = false;
        this.hitCooldown = 0f;
        this.len = 0f;
    }

    public void initDrone(float x, float y, float vx, float vy) {
        init(x, y, vx, vy, 1, false);
        this.drone = true;
        this.radius = 6f;
    }

    /** 激光束初始化。 */
    public void initLaser(float x, float y, float vx, float vy, int damage) {
        init(x, y, vx, vy, damage, false);
        this.piercing = true;
        this.radius = 7f;
        this.len = 46f;
    }

    public void update(float dt) {
        x += vx * dt;
        y += vy * dt;
        if (hitCooldown > 0f) hitCooldown -= dt;
    }
}
