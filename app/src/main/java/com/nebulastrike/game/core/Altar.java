package com.nebulastrike.game.core;

/**
 * 局内祭坛（v1.26）：随机出现在场上的符文法阵。
 *
 * 两种充能方式，都要求玩家主动分神：
 * 1. 朝核心开火——子弹会被法阵吃掉并转化为充能；
 * 2. 站进光环里引导——范围内的敌弹会被拽向核心并吞掉，站得越久越安全，但人也越危。
 *
 * 充能满立刻爆发赐福（由创建时显示的符文决定），法阵随之消失；
 * 寿命到期则自然熄灭。祭坛不阻挡飞船，只吃子弹和敌弹。
 */
public class Altar {
    public enum Blessing {
        /** 军械：随机一路武器 + 火力等级。 */
        ARSENAL,
        /** 生机：一条命，满命折算炸弹。 */
        VITAL,
        /** 守护：护盾层，满层转为纳米修复。 */
        AEGIS,
        /** 财宝：大量分数 + 双倍 + 清空全屏敌弹。 */
        FORTUNE
    }

    /** 核心半径（子弹命中的判定半径）。 */
    public static final float RADIUS = 54f;
    /** 光环半径：站进来才算引导，敌弹也只有这个范围内的才会被吸。 */
    public static final float AURA = 168f;
    /** 敌弹被拽进这个距离就被吞掉。 */
    public static final float ABSORB = 46f;
    /** 引导时敌弹被拉向核心的加速度。 */
    public static final float PULL_ACCEL = 2600f;
    /** 法阵寿命：到期未充能满就熄灭，不惩罚，只是错过了。 */
    public static final float LIFE = 14f;
    /** 充满所需充能。 */
    public static final float MAX_CHARGE = 100f;
    /** 每点子弹伤害转化多少充能：基础机炮也要打上几秒，重火力更快。 */
    public static final float CHARGE_PER_DAMAGE = 5f;
    /** 吞掉一颗敌弹给多少充能。 */
    public static final float CHARGE_PER_BULLET = 4f;

    public float x, y;
    public float life;
    public float age;
    public float charge;
    /** 闪光余量：被子弹命中的即时反馈。 */
    public float flash;
    public Blessing blessing = Blessing.ARSENAL;
    /** 本帧玩家是否站在光环里（世界每帧刷新，渲染层据此加亮）。 */
    public boolean channeled;
    public boolean active;

    public void init(float x, float y, Blessing blessing) {
        this.x = x;
        this.y = y;
        this.life = LIFE;
        this.age = 0f;
        this.charge = 0f;
        this.flash = 0f;
        this.blessing = blessing != null ? blessing : Blessing.ARSENAL;
        this.channeled = false;
        this.active = true;
    }

    public void update(float dt) {
        life -= dt;
        age += dt;
        if (flash > 0f) flash = Math.max(0f, flash - dt * 4f);
    }

    public float fraction() {
        return Math.min(1f, charge / MAX_CHARGE);
    }

    public boolean full() {
        return charge >= MAX_CHARGE;
    }

    /** @return true 表示这一下刚好把法阵充满。 */
    public boolean addCharge(float amount) {
        if (!active || charge >= MAX_CHARGE) return false;
        charge += amount;
        return charge >= MAX_CHARGE;
    }
}
