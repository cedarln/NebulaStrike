package com.nebulastrike.game.core;

/** Projectile fired by enemies. Slow enough to dodge. */
public class EnemyBullet {
    public float x, y, vx, vy;
    public float radius = 10f;
    public boolean active;
    /** set once the bullet has been close enough to graze, so it only charges once. */
    public boolean grazed;
    /** 水雷：布雷艇投放的静止爆炸物，渲染成带齿的环（碰撞行为与普通敌弹一致）。 */
    public boolean mine;
    /** 弹幕网节点：Boss 的矩阵弹幕，渲染成方框网格，与普通圆弹一眼可分。 */
    public boolean net;

    public void init(float x, float y, float vx, float vy) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.radius = 10f;
        this.active = true;
        this.grazed = false;
        this.mine = false;
        this.net = false;
    }

    /** Custom-radius variant (boss shells are bigger). */
    public void initRadius(float x, float y, float vx, float vy, float radius) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.radius = radius;
        this.active = true;
        this.grazed = false;
        this.mine = false;
        this.net = false;
    }

    /** 弹幕网节点：缓慢直落，视觉上连成一张网格。 */
    public void initNet(float x, float y, float vy) {
        initRadius(x, y, 0f, vy, 12f);
        this.net = true;
    }

    /** 水雷：几乎静止，只在原地缓慢下坠，玩家碰到即受伤。 */
    public void initMine(float x, float y) {
        initRadius(x, y, 0f, 26f, 15f);
        this.mine = true;
    }

    /** Boss 布雷舱投下的漂流水雷：带横向速度，把下方的通道切成两半。 */
    public void initDriftingMine(float x, float y, float vx, float vy) {
        initRadius(x, y, vx, vy, 15f);
        this.mine = true;
    }

    public void update(float dt) {
        x += vx * dt;
        y += vy * dt;
    }

    public boolean offscreen(float w, float h) {
        return x < -40f || x > w + 40f || y < -40f || y > h + 40f;
    }
}
