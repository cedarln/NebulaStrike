package com.nebulastrike.game.core;

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
    /** v1.33：追踪弹。转向速率有限、寿命有限，靠转向就能甩掉。 */
    public boolean homing;
    /** 追踪弹剩余寿命（秒）；非追踪弹为 0。 */
    public float life;
    /** 追踪弹每秒最大转向角（弧度）。 */
    public float turnRate;

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
        this.homing = false;
        this.life = 0f;
        this.turnRate = 0f;
    }

    /** Custom-radius variant (boss shells are bigger). */
    public void initRadius(float x, float y, float vx, float vy, float radius) {
        init(x, y, vx, vy);
        this.radius = radius;
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

    /** v1.33：追踪弹。初速朝目标，之后按 turnRate 慢慢修正，life 耗尽后自毁。 */
    public void initHoming(float x, float y, float vx, float vy, float turnRate, float life) {
        initRadius(x, y, vx, vy, 10f);
        this.homing = true;
        this.turnRate = turnRate;
        this.life = life;
    }

    public void update(float dt) {
        x += vx * dt;
        y += vy * dt;
    }

    /** 追踪更新：限速转向目标点；寿命耗尽返回 false（由调用方回收）。 */
    public boolean updateHoming(float dt, float targetX, float targetY) {
        life -= dt;
        if (life <= 0f) return false;
        float speed = (float) Math.sqrt(vx * vx + vy * vy);
        if (speed < 1f) return true;
        float cur = (float) Math.atan2(vy, vx);
        float want = (float) Math.atan2(targetY - y, targetX - x);
        float diff = want - cur;
        while (diff > Math.PI) diff -= (float) (Math.PI * 2);
        while (diff < -Math.PI) diff += (float) (Math.PI * 2);
        float maxTurn = turnRate * dt;
        if (diff > maxTurn) diff = maxTurn;
        if (diff < -maxTurn) diff = -maxTurn;
        float a = cur + diff;
        vx = (float) Math.cos(a) * speed;
        vy = (float) Math.sin(a) * speed;
        x += vx * dt;
        y += vy * dt;
        return true;
    }

    public boolean offscreen(float w, float h) {
        return x < -40f || x > w + 40f || y < -40f || y > h + 40f;
    }
}
