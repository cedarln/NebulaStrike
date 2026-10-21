package com.nebulastrike.game.core;

/**
 * 玩家部署的自动炮台：拾取 TURRET 道具后停在原地（随屏幕缓慢下移），
 * 朝正上方三向开火，替玩家守住一条通道。被敌机撞上或吃到敌弹会掉耐久，
 * 耐久耗尽或超时后自行解体。
 */
public class Turret {
    public static final float RADIUS = 26f;
    public static final float MAX_HP = 8f;
    public static final float LIFE = 16f;
    /** 缓慢下移速度（与战场一起"滚"出屏幕，避免永久站桩）。 */
    public static final float DRIFT = 42f;
    public static final float FIRE_PERIOD = 0.5f;

    public float x, y;
    public float hp;
    public float life;
    public float fireCd;
    public boolean active;

    public void init(float x, float y) {
        this.x = x;
        this.y = y;
        this.hp = MAX_HP;
        this.life = LIFE;
        this.fireCd = 0.15f;
        this.active = true;
    }

    /** @return false 表示该炮台已退役（时间到或离场） */
    public boolean update(float dt) {
        life -= dt;
        y += DRIFT * dt;
        if (fireCd > 0f) fireCd = Math.max(0f, fireCd - dt);
        return life > 0f && y < GameWorld.HEIGHT + 40f;
    }

    public void damage(float amount) {
        hp -= amount;
    }

    public boolean destroyed() {
        return hp <= 0f;
    }
}
