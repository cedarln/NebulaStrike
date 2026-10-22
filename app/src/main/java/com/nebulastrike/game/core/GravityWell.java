package com.nebulastrike.game.core;

/**
 * 重力井（v1.15 道具 GRAVITY）：一个临时的微型黑洞。
 * 影响范围内的敌弹会被拖向核心并被吞掉（吞掉的弹不会再打到玩家），
 * 吞满 {@link #CAP} 发或寿命耗尽时把库存沿上方扇形"喷还"成玩家子弹，
 * 伤害为 {@link GameWorld#GRAVITY_BURST_DAMAGE}（足够点掉敌弹，形成连锁）。
 *
 * 与炮台的区别：炮台是"多一路火力"，重力井是"把对方的火力变成自己的火力"，
 * 弹幕越密的场景（铁雨异变 / Boss 阶段）收益越高。
 */
public class GravityWell {
    /** 核心可视半径。 */
    public static final float RADIUS = 28f;
    /** 引力范围：只有这个半径内的敌弹会被拉扯。 */
    public static final float PULL_RADIUS = 330f;
    /** 进入这个距离即被吞掉（在核心之上再加敌弹自身半径）。 */
    public static final float ABSORB_RADIUS = 30f;
    /** 心口处的引力加速度，边缘按线性衰减到 0。 */
    public static final float PULL_ACCEL = 4200f;
    /** 寿命：到期后即使没吸满也会喷一次。 */
    public static final float LIFE = 9f;
    /** 与战场一起缓慢下移，避免永久占位。 */
    public static final float DRIFT = 14f;
    /** 库存上限：吸满立刻喷还。 */
    public static final int CAP = 12;

    public float x, y;
    public float life;
    public float age;
    /** 已吞下、尚未喷还的敌弹数。 */
    public int stored;
    public boolean active;

    public void init(float x, float y) {
        this.x = x;
        this.y = y;
        this.life = LIFE;
        this.age = 0f;
        this.stored = 0;
        this.active = true;
    }

    /** @return false 表示该重力井已消散（时间到或被挤出场外） */
    public boolean update(float dt) {
        life -= dt;
        age += dt;
        y += DRIFT * dt;
        return life > 0f && y < GameWorld.HEIGHT + 60f;
    }

    public boolean full() {
        return stored >= CAP;
    }

    /** 引力随距离线性衰减：边缘几乎拉不动，越近越猛。 */
    public float pullAt(float d) {
        if (d >= PULL_RADIUS) return 0f;
        return PULL_ACCEL * (1f - d / PULL_RADIUS);
    }
}
