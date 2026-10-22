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
    /** true once this projectile has hit something (accuracy stat; piercing counts once). */
    public boolean landed;
    // laser
    public boolean piercing;
    public float hitCooldown;   // >0 时跳过命中结算（防止单敌多扣血）
    public float len;           // 渲染光束长度（0 = 普通圆弹）
    /** 集束弹：命中后炸开一圈破片（见 GameWorld.burstFragments）。 */
    public boolean cluster;
    /** 破片本身不再二次爆裂，防止无限递归生成。 */
    public boolean fragment;
    /** 弹跳弹剩余反弹次数（>0 时撞到左右壁会镜像 vx，见 GameWorld.bulletBounds）。 */
    public int bounces;
    /** v1.16：终极技光矛——寻敌、贯穿、伤害随档位成长，渲染层单独画成金色长枪。 */
    public boolean ult;

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
        this.cluster = false;
        this.fragment = false;
        this.bounces = 0;
        this.ult = false;
        this.landed = false;
    }

    public void initDrone(float x, float y, float vx, float vy) {
        init(x, y, vx, vy, 1, false);
        this.drone = true;
        this.radius = 6f;
    }

    /** 激光束初始化。 */
    public void initLaser(float x, float y, float vx, float vy, int damage) {
        initBeam(x, y, vx, vy, damage, 7f, 46f);
    }

    /** 通用光束（融合长枪/蜂群用不同宽度与长度）。 */
    public void initBeam(float x, float y, float vx, float vy, int damage,
                         float radius, float len) {
        init(x, y, vx, vy, damage, false);
        this.piercing = true;
        this.radius = radius;
        this.len = len;
    }

    /** 集束弹：直射弹体，命中后分裂。 */
    public void initCluster(float x, float y, float vx, float vy, int damage) {
        init(x, y, vx, vy, damage, false);
        this.cluster = true;
        this.radius = 11f;
    }

    public void update(float dt) {
        x += vx * dt;
        y += vy * dt;
        if (hitCooldown > 0f) hitCooldown -= dt;
    }
}
