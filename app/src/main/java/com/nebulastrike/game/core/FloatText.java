package com.nebulastrike.game.core;

/**
 * 伤害与得分飘字：命中瞬间从受击点向上浮起、末段淡出。
 * 与粒子一样走对象池，零分配；同屏条数由 GameWorld 每帧预算限制，避免激光刷屏。
 */
public final class FloatText {
    /** 初始上浮速度（虚拟单位/秒）。 */
    public static final float RISE = 150f;
    /** 普通数字存活时间。 */
    public static final float LIFE = 0.66f;
    /** 大号（暴击 / 击杀得分）存活时间略长，方便看清。 */
    public static final float LIFE_BOLD = 0.9f;
    /** 末段淡出时长。 */
    public static final float FADE = 0.22f;

    public float x, y;
    public float vy;
    public float life;
    public float maxLife;
    public int value;
    /** 0xRRGGBB，由 GameWorld 决定的语义色（普伤/暴击/得分）。 */
    public int color;
    /** 大号加粗：暴击与击杀得分。 */
    public boolean bold;
    /** 横向漂移，让连续命中的数字不会叠在同一列。 */
    public float drift;
    public boolean active;

    public void init(float x, float y, int value, int color, boolean bold, float drift) {
        this.x = x;
        this.y = y;
        this.value = value;
        this.color = color;
        this.bold = bold;
        this.drift = drift;
        this.vy = RISE * (bold ? 1.1f : 1f);
        this.maxLife = bold ? LIFE_BOLD : LIFE;
        this.life = maxLife;
        this.active = true;
    }

    /** @return false 表示已飘完，槽位可回收 */
    public boolean update(float dt) {
        life -= dt;
        y -= vy * dt;
        x += drift * dt;
        vy *= 1f - 1.7f * dt;   // 越升越慢，像被"顿住"
        if (life <= 0f) {
            active = false;
            return false;
        }
        return true;
    }

    /** 0..1 的可见度，末段线性淡出。 */
    public float alpha() {
        if (life >= FADE) return 1f;
        return Math.max(0f, life / FADE);
    }
}
