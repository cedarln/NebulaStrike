package com.nebulastrike.game.core;

/**
 * v1.54 局内强化 PERK：每击破一场旗舰，暂停战场并让玩家从三张卡里挑一张。
 *
 * <p>和圣物（{@link Relic}）的区别：圣物是"掉什么拿什么"，带点运气；
 * PERK 是玩家主动做构筑决策——同一类可以反复叠到
 * {@link #MAX_STACKS} 层，不同类之间是相加/相乘的永久增益，
 * 一局结束时全部归零。它不替换任何已有道具或武器，只在上面再加一层。
 *
 * <p>枚举顺序即存档/事件里的 ordinal，新条目一律追加到末尾。
 */
public enum Perk {
    /** 超频：所有武器的射击冷却一起缩短。 */
    OVERCLOCK,
    /** 推进器：拖动跟随的最高速度提升。 */
    THRUSTER,
    /** 装甲：护盾上限 +1，并立刻补一层。 */
    ARMOR,
    /** 军械：炸弹上限 +1，并立刻补一颗。 */
    ARSENAL,
    /** 幸运：敌机掉落道具的概率提升。 */
    LUCK,
    /** 精准：所有命中额外获得暴击率。 */
    CRIT;

    public static final int COUNT = 6;
    /** 每次三选一展示几张卡。 */
    public static final int CHOICES = 3;
    /** 同类强化的叠加上限。 */
    public static final int MAX_STACKS = 3;

    /** 卡片标题。 */
    public String label() {
        switch (this) {
            case OVERCLOCK: return "超频";
            case THRUSTER: return "推进器";
            case ARMOR: return "装甲";
            case ARSENAL: return "军械";
            case LUCK: return "幸运";
            default: return "精准";
        }
    }

    /** 一句话说清每层给什么（卡片正文用）。 */
    public String blurb() {
        switch (this) {
            case OVERCLOCK: return "全部武器射击冷却 -8%";
            case THRUSTER: return "飞船移速 +8%";
            case ARMOR: return "护盾上限 +1，并立刻补一层";
            case ARSENAL: return "炸弹上限 +1，并立刻补一颗";
            case LUCK: return "敌机掉落道具的概率 +22%";
            default: return "所有命中暴击率 +5%";
        }
    }

    /** 角标（HUD 小芯片与卡片徽记共用）。 */
    public String badge() {
        switch (this) {
            case OVERCLOCK: return "频";
            case THRUSTER: return "推";
            case ARMOR: return "甲";
            case ARSENAL: return "弹";
            case LUCK: return "运";
            default: return "准";
        }
    }

    /** 卡片与芯片配色。 */
    public int colorRgb() {
        switch (this) {
            case OVERCLOCK: return 0x6BFFC8;
            case THRUSTER: return 0x7DF9FF;
            case ARMOR: return 0x9FE86B;
            case ARSENAL: return 0xFFB03A;
            case LUCK: return 0xFFE066;
            default: return 0xFF6B8A;
        }
    }

    public static Perk byOrdinal(int ordinal) {
        Perk[] all = values();
        if (ordinal < 0 || ordinal >= all.length) return null;
        return all[ordinal];
    }
}
