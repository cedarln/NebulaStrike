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
    /** v1.17：本发弹是否已被磁暴艇偏折过（结算卡"偏弹"按发计，不按帧计）。 */
    public boolean bent;
    /** v1.23：由反射护罩弹回来的敌弹（渲染成相位紫，与普通火力区分）。 */
    public boolean reflected;
    /** v1.24：电弧炮弹体，命中后向附近目标连锁放电。 */
    public boolean arc;
    /** 电弧剩余跳跃次数（0 = 命中后不再连锁）。 */
    public int arcJumps;
    /** v1.43：回旋镖弹体（穿透、折返、回程吸附发射线）。 */
    public boolean boomerang;
    /** 回旋镖是否已进入回程（true 后不再计时，改由 homeX 弹性收束）。 */
    public boolean boomerangReturning;
    /** 去程剩余时间；归零即折返。 */
    public float boomerangTimer;
    /** 回程瞄准的横坐标（发射时的船位置），让回旋镖"回到手里"。 */
    public float homeX;
    /** 渲染自旋角。 */
    public float spin;

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
        this.bent = false;
        this.reflected = false;
        this.arc = false;
        this.arcJumps = 0;
        this.boomerang = false;
        this.boomerangReturning = false;
        this.boomerangTimer = 0f;
        this.homeX = 0f;
        this.spin = 0f;
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

    /** 电弧炮：命中后从弹着点继续向附近目标跳跃 jumps 次。 */
    public void initArc(float x, float y, float vx, float vy, int damage, int jumps) {
        init(x, y, vx, vy, damage, false);
        this.arc = true;
        this.arcJumps = jumps;
        this.radius = 10f;
    }

    /**
     * 回旋镖：穿透圆盘，去程 outTime 秒，随后折返并收回 homeX。
     * @param homeX 回程希望回到的横坐标（通常是发射时飞船所在列）
     */
    public void initBoomerang(float x, float y, float vx, float vy, int damage,
                              float outTime, float homeX) {
        init(x, y, vx, vy, damage, false);
        this.boomerang = true;
        this.boomerangReturning = false;
        this.boomerangTimer = outTime;
        this.homeX = homeX;
        this.radius = 13f;
        this.spin = 0f;
        // 穿透：去程与回程各结算一次，这正是回旋镖的价值
        this.piercing = true;
    }

    public void update(float dt) {
        if (boomerang) updateBoomerang(dt);
        x += vx * dt;
        y += vy * dt;
        if (hitCooldown > 0f) hitCooldown -= dt;
    }

    /**
     * 回旋镖弹道：去程匀速上抛，outTime 到点后折返——纵向反向并加速，
     * 横向用阻尼弹簧收回发射时的 homeX，于是"回到手里"而不是斜着飘走。
     */
    private void updateBoomerang(float dt) {
        spin += dt * 17f;
        if (!boomerangReturning) {
            boomerangTimer -= dt;
            if (boomerangTimer <= 0f) {
                boomerangReturning = true;
                vy = Math.max(Math.abs(vy) * 1.28f, 980f);
                vx *= 0.3f;
            }
            return;
        }
        float dx = homeX - x;
        vx += (dx * 5.2f - vx * 2.6f) * dt;
        vy = Math.min(vy + 240f * dt, 1280f);
    }
}
