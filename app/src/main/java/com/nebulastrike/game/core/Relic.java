package com.nebulastrike.game.core;

/**
 * Boss 圣物：击败 Boss 后随机获得的一种"Boss 能力残片"，永久生效到本局结束。
 * 硬核难度下坠机会全部失去（见 {@link Difficulty.Penalty#STRIP}）。
 * 新条目一律追加在末尾，存档与图鉴按 ordinal 记录。
 */
public enum Relic {
    /** 光束核心：每隔一秒自动射出一束宽穿透光束，无视护盾。 */
    BEAM_CORE,
    /** 永恒僚机：僚机不再到期，且双翼各多一发弹。 */
    ETERNAL_WING,
    /** 镜像枪：基础炮额外向两侧 25° 射出镜像弹道。 */
    MIRROR_LANCE,
    /** 圣盾外壳：护盾上限 +1，并每 18 秒自动补一层。 */
    AEGIS_FIELD,
    /** 虹吸矩阵：每次擦弹额外充能冲刺能量。 */
    GRAZE_LEECH,
    /** 过载核心：过载时长翻倍，且过载期间所有武器多算一层。 */
    OVERDRIVE_CORE;

    public static final int COUNT = 6;

    /** 简短展示名（HUD 角标与横幅用）。 */
    public String label() {
        switch (this) {
            case BEAM_CORE: return "光束核心";
            case ETERNAL_WING: return "永恒僚机";
            case MIRROR_LANCE: return "镜像枪";
            case AEGIS_FIELD: return "圣盾外壳";
            case GRAZE_LEECH: return "虹吸矩阵";
            default: return "过载核心";
        }
    }

    /** 效果说明（横幅与结算页用）。 */
    public String blurb() {
        switch (this) {
            case BEAM_CORE: return "每 1.1 秒自动射出一束穿透光束";
            case ETERNAL_WING: return "僚机不再消失，双翼各多一发";
            case MIRROR_LANCE: return "基础炮追加两路 25° 镜像弹";
            case AEGIS_FIELD: return "护盾上限 +1，每 18 秒自动补一层";
            case GRAZE_LEECH: return "擦弹额外充能冲刺能量";
            default: return "过载时长翻倍，过载时火力再 +1 层";
        }
    }

    /** HUD 角标字母（画在小芯片上）。 */
    public String badge() {
        switch (this) {
            case BEAM_CORE: return "B";
            case ETERNAL_WING: return "W";
            case MIRROR_LANCE: return "M";
            case AEGIS_FIELD: return "S";
            case GRAZE_LEECH: return "G";
            default: return "O";
        }
    }

    /** 配色（与武器/道具色区分开，走"遗物金"系）。 */
    public int colorRgb() {
        switch (this) {
            case BEAM_CORE: return 0x7DF9FF;
            case ETERNAL_WING: return 0xFF8FD1;
            case MIRROR_LANCE: return 0xFFD166;
            case AEGIS_FIELD: return 0x7CFF6B;
            case GRAZE_LEECH: return 0xB08FFF;
            default: return 0xFFB03A;
        }
    }

    public static Relic byOrdinal(int ordinal) {
        Relic[] all = values();
        if (ordinal < 0 || ordinal >= all.length) return null;
        return all[ordinal];
    }
}
