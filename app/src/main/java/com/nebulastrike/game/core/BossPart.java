package com.nebulastrike.game.core;

/**
 * Destructible boss appendage. Parts are positioned relative to the boss hull,
 * inherit its movement, and keep firing until their own HP is depleted.
 */
public class BossPart {
    /**
     * GUN 瞄准炮塔 / LASER 激光吊舱 / SHIELD 护盾发生器（存活时削弱核心伤害）/
     * POD 分裂舱（周期性投放僚机）/ PRISM 棱镜（后期召唤，环绕舰体并放出十字光束）。
     */
    public enum Kind { GUN, LASER, SHIELD, POD, PRISM }

    public Kind kind;
    /** offset from the boss center, virtual units */
    public float ox, oy;
    public float radius;
    public float hp, maxHp;
    public boolean active;
    public float flash;
    /** seconds until this part may fire again (counted down by Boss.update) */
    public float fireCd;
    /** how long this part has been a wreck (drives smoke/spark rendering) */
    public float destroyedAge;
    /** rendering: current barrel angle toward the player */
    public float aim;
    /** PRISM 环绕舰体的当前角度与角速度（正负方向各一个）。 */
    public float orbit;
    public float spin;

    public void init(Kind kind, float ox, float oy, float radius, float hp, float fireCd) {
        this.kind = kind;
        this.ox = ox;
        this.oy = oy;
        this.radius = radius;
        this.hp = hp;
        this.maxHp = hp;
        this.active = true;
        this.flash = 0f;
        this.fireCd = fireCd;
        this.destroyedAge = 0f;
        this.aim = (float) (Math.PI / 2);
        this.orbit = 0f;
        this.spin = 0f;
    }

    public void update(float dt) {
        if (flash > 0f) flash = Math.max(0f, flash - dt);
        if (active) {
            if (fireCd > 0f) fireCd = Math.max(0f, fireCd - dt);
        } else {
            destroyedAge += dt;
        }
    }

    public void damage(int amount) {
        hp -= amount;
        flash = 0.08f;
    }

    public float worldX(Boss b) { return b.x + ox; }
    public float worldY(Boss b) { return b.y + oy; }
}
