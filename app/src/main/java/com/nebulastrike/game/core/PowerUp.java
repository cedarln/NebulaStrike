package com.nebulastrike.game.core;

/**
 * Falling pickup. Drifts gently and is attracted when the ship is close.
 * attracted 标志供渲染层区分"磁吸中"状态（光环加速闪烁 = 可收集反馈）。
 */
public class PowerUp {
    /**
     * ORBIT/CHAIN/BONUS 为 v1.9 新增：轨道球、连锁闪电、突袭奖励。
     * BOUNCE/NOVA/NANITE/TURRET 为 v1.10 新增：弹跳弹、新星冲击、纳米修复、部署炮台。
     * GRAVITY 为 v1.15 新增：部署重力井（吸掉敌弹再喷还成自己的子弹）。
     * 新类型一律追加在末尾，避免改变既有 ordinal（掉落表、音效、存档都按 ordinal 映射）。
     */
    public enum Kind { SPREAD, MISSILE, LASER, POWER, SHIELD, BOMB, RAPID, X2, LIFE, WINGMAN, TIME,
        ORBIT, CHAIN, BONUS, BOUNCE, NOVA, NANITE, TURRET, GRAVITY,
        /** v1.23：反射护罩（叠加，最多 2 层；每层 6 秒充能一次，把命中你的敌弹弹回去）。 */
        REFLECT,
        /** v1.24：电弧炮（武器类，叠加不替换；命中后向附近目标连锁放电）。 */
        ARC,
        /** v1.43：回旋镖（武器类，叠加不替换；去程折返、回程沿发射线扫回来）。 */
        BOOMERANG,
        /** v1.44：诱饵浮标（9 秒内把敌人的瞄准骗到浮标上，吃满或到期时爆散清弹）。 */
        DECOY,
        /** v1.53：锁定激光（武器类，叠加不替换；自动点名最近目标，从顶端落下贯穿光柱）。 */
        LOCKON }

    public Kind kind;
    public float x, y;
    public float age;
    public boolean active;
    public boolean attracted;

    public void init(Kind kind, float x, float y) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.age = 0f;
        this.active = true;
        this.attracted = false;
    }

    public void update(float dt, float shipX, float shipY) {
        age += dt;
        // gentle fall + sway
        y += 110f * dt;
        x += (float) Math.sin(age * 2.6f) * 46f * dt;
        // magnet: accelerate toward ship when close
        attracted = false;
        float dx = shipX - x, dy = shipY - y;
        float d2 = dx * dx + dy * dy;
        if (d2 < 260f * 260f) {
            attracted = true;
            float d = (float) Math.sqrt(d2);
            if (d > 1f) {
                x += dx / d * 520f * dt;
                y += dy / d * 520f * dt;
            }
        }
    }

    public boolean offscreen(float h) {
        return y > h + 40f;
    }
}
