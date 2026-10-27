package com.nebulastrike.game.core;

/**
 * v1.47 Boss 词缀：第三艘 Boss 起，每场随机挂一条。
 *
 * <p>词缀只改变这一场战斗的节奏与回报，不改 Boss 的血量曲线：玩家在预警横幅和
 * 血条下方就能读到是哪一条，进而决定先拆部件还是直接压核心。
 */
public enum BossAffix {
    /** 没有词缀（前两艘 Boss，或调试场景）。 */
    NONE("", "", ""),
    /** 疾火：攻击间隔更短，但核心暴露来得更勤——换输出窗口。 */
    BLAZE("疾火", "BLAZE", "攻击间隔 ×0.82 · 核心暴露更频繁"),
    /** 磐垒：舰体减伤 16%，打空后多给一件道具与一笔赏金。 */
    BULWARK("磐垒", "BULWARK", "舰体减伤 16% · 击破多掉一件"),
    /** 蜂群：每次换段多放两架增援，击破多给一件道具。 */
    BROOD("蜂群", "BROOD", "换段增援 +2 · 击破多掉一件");

    public final String label;
    public final String tag;
    public final String desc;

    BossAffix(String label, String tag, String desc) {
        this.label = label;
        this.tag = tag;
        this.desc = desc;
    }

    /** 可随机抽到的词缀条数（不含 NONE）。 */
    public static final int COUNT = 3;

    /** 把 0..COUNT-1 的掷点映射成词缀，永不超过 NONE。 */
    public static BossAffix of(int roll) {
        return values()[1 + Math.floorMod(roll, COUNT)];
    }
}
