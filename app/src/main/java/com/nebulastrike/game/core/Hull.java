package com.nebulastrike.game.core;

/**
 * 机体（v1.51）：开局前在主菜单底部选择的三种舰体。
 *
 * <p>它们不改变任何"拾取上限"或武器叠加规则，只改基础手感与起手补给——
 * 玩家捡到的每一件道具仍然照常叠加。数值刻意做得"各有短长"：
 * 游隼跑得快但火力稀，壁垒火力足但转向慢，星火两者都不极端。
 */
public enum Hull {

    SPARK("星火", "均衡", 0xFF7DF9FF,
            1f, 1f, 1, 0, 0f, 1f,
            "标准舰体：移速与射速均衡，开局 1 枚炸弹"),

    FALCON("游隼", "高速", 0xFF6FE38B,
            1.18f, 1.06f, 1, 0, 0f, 1.35f,
            "移速 +18% · 冲刺充能 +35% · 射速 -6%，靠走位换输出"),

    BULWARK("壁垒", "重装", 0xFFFFB03A,
            0.88f, 0.90f, 2, 1, 0.8f, 0.9f,
            "移速 -12% · 射速 +11% · 开局 2 炸弹 + 1 层护盾 · 重生保护 +0.8 秒");

    public static final int COUNT = values().length;

    public final String label;
    public final String tag;
    public final int accent;
    /** 拖动跟随速度倍率（1 = 标准）。 */
    public final float speedMul;
    /** 开火冷却倍率：>1 更慢，<1 更快。 */
    public final float fireCdMul;
    /** 开局炸弹数（含第 1 枚）。 */
    public final int startBombs;
    /** 开局护盾层数。 */
    public final int startShield;
    /** 重生保护额外秒数（护盾挡伤同样享受）。 */
    public final float respawnBonus;
    /** 冲刺充能速率倍率（与圣物相乘）。 */
    public final float dashRechargeMul;
    /** 菜单里的一行完整说明。 */
    public final String blurb;

    Hull(String label, String tag, int accent, float speedMul, float fireCdMul,
         int startBombs, int startShield, float respawnBonus, float dashRechargeMul,
         String blurb) {
        this.label = label;
        this.tag = tag;
        this.accent = accent;
        this.speedMul = speedMul;
        this.fireCdMul = fireCdMul;
        this.startBombs = startBombs;
        this.startShield = startShield;
        this.respawnBonus = respawnBonus;
        this.dashRechargeMul = dashRechargeMul;
        this.blurb = blurb;
    }

    /** 越界回落到范围内（存档清洗与旧版本升级都会用到）。 */
    public static Hull of(int index) {
        int i = Math.max(0, Math.min(COUNT - 1, index));
        return values()[i];
    }
}
