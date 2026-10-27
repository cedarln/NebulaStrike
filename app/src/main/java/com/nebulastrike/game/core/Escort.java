package com.nebulastrike.game.core;

/**
 * v1.41 护航目标：友军补给舰「星鸦」。
 *
 * <p>它从屏幕上方进入、在上空悬停一段时间，再爬升离场。悬停期间它的信标会把附近的
 * 敌弹轻轻拉向自己——玩家想拿到补给，就得在它身边清出一条安全走廊。舰体被敌弹打空
 * 只会让这一趟护航失败（不扣命、不扣分），所以它是"可选的风险换收益"。
 */
public class Escort {

    public static final float RADIUS = 34f;
    public static final float MAX_HP = 100f;
    /** 进入后在战场上空悬停的纵坐标。 */
    public static final float HOLD_Y = 330f;
    /** 悬停时长（秒）：足够打出一轮走位，又不至于让 Boss 战被拖长。 */
    public static final float HOLD_TIME = 14f;
    public static final float ENTER_SPEED = 190f;
    public static final float LEAVE_SPEED = 300f;
    /** 信标半径：这个范围内的敌弹会被慢慢拉向舰体。 */
    public static final float TAUNT_RADIUS = 250f;
    /** 敌弹每秒被拉偏的最大角速度（弧度）。 */
    public static final float TAUNT_TURN = 2.4f;
    /** 每颗敌弹造成的舰体损伤（大弹更疼）。 */
    public static final float BULLET_DAMAGE = 7f;

    public boolean active;
    public float x, y, prevX, prevY;
    public float hp = MAX_HP;
    /** 悬停倒计时；进入阶段不递减。 */
    public float holdTimer;
    public float age;
    /** 受击闪白。 */
    public float flash;
    /** 悬停结束后进入爬升离场。 */
    public boolean leaving;
    /** 这一趟吃掉的敌弹数（结算/事件用）。 */
    public int absorbed;

    public void init(float x) {
        this.active = true;
        this.x = x;
        this.y = -RADIUS - 40f;
        this.prevX = x;
        this.prevY = y;
        this.hp = MAX_HP;
        this.holdTimer = HOLD_TIME;
        this.age = 0f;
        this.flash = 0f;
        this.leaving = false;
        this.absorbed = 0;
    }

    /** @return false 表示已经离场（由调用方结算奖励或失败）。 */
    public boolean update(float dt) {
        if (!active) return false;
        age += dt;
        prevX = x;
        prevY = y;
        if (flash > 0f) flash = Math.max(0f, flash - dt * 3f);
        if (!leaving) {
            if (y < HOLD_Y) {
                y = Math.min(HOLD_Y, y + ENTER_SPEED * dt);
            } else {
                holdTimer -= dt;
                if (holdTimer <= 0f) leaving = true;
            }
        } else {
            y -= LEAVE_SPEED * dt;
            if (y < -RADIUS - 60f) {
                active = false;
                return false;
            }
        }
        return true;
    }

    public boolean holding() {
        return active && !leaving && y >= HOLD_Y - 0.5f;
    }

    public float hpFrac() {
        return hp <= 0f ? 0f : Math.min(1f, hp / MAX_HP);
    }

    /** @return true 表示这一发把舰体打空。 */
    public boolean damage(float amount) {
        if (!active || hp <= 0f) return false;
        hp -= amount;
        flash = 1f;
        if (hp <= 0f) {
            hp = 0f;
            return true;
        }
        return false;
    }
}
