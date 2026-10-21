package com.nebulastrike.game.core;

/**
 * 难度档位：一次性调节"掉落率 / 掉命惩罚 / 重生保护 / 敌机速度 / 精英率 / 得分倍率"六项。
 * 标准档所有系数都是 1.0，与历史版本行为一致（老玩家的分数榜不会被稀释）。
 * LAB 金手指仍然可以在此基础上继续单独微调掉率与敌速。
 */
public enum Difficulty {
    /** 休闲：道具多、掉命不掉装备、重生保护最长，得分打 75 折。 */
    CASUAL(0.75f, 1.45f, 0.85f, 4.0f, 330f, 0.4f, Penalty.KEEP_ALL),
    /** 标准：与 v1.9 行为一致。 */
    STANDARD(1.0f, 1.0f, 1.0f, 2.0f, 240f, 1.0f, Penalty.KEEP_WEAPONS),
    /** 硬核：道具稀、敌机快、精英多，坠机清空特殊武器与圣物，得分 1.5 倍。 */
    HARDCORE(1.5f, 0.8f, 1.15f, 1.4f, 150f, 2.2f, Penalty.STRIP);

    /** 坠机时对装备的处置方式。 */
    public enum Penalty {
        /** 什么都不掉（火力等级也不降）。 */
        KEEP_ALL,
        /** 保留武器与圣物，火力等级降 1（v1.9 的默认惩罚）。 */
        KEEP_WEAPONS,
        /** 清空特殊武器、圣物与护盾，火力回到 1 级。 */
        STRIP
    }

    public final float scoreScale;
    /** 道具掉率系数（与 LAB 的 dropRatePct 相乘）。 */
    public final float dropScale;
    /** 敌机速度系数（与 LAB 的 enemySpeedPct 相乘）。 */
    public final float speedScale;
    /** 重生无敌时长（秒）。 */
    public final float respawnInvuln;
    /** 重生瞬间清除周边敌弹的半径（虚拟单位）。 */
    public final float respawnClearRadius;
    /** 精英敌机出现率系数。 */
    public final float eliteScale;
    public final Penalty penalty;

    Difficulty(float scoreScale, float dropScale, float speedScale, float respawnInvuln,
               float respawnClearRadius, float eliteScale, Penalty penalty) {
        this.scoreScale = scoreScale;
        this.dropScale = dropScale;
        this.speedScale = speedScale;
        this.respawnInvuln = respawnInvuln;
        this.respawnClearRadius = respawnClearRadius;
        this.eliteScale = eliteScale;
        this.penalty = penalty;
    }

    /** 展示名（设置面板与 HUD 用）。 */
    public String label() {
        switch (this) {
            case CASUAL: return "休闲";
            case HARDCORE: return "硬核";
            default: return "标准";
        }
    }

    /** 一句话说明，给设置面板副标题用。 */
    public String blurb() {
        switch (this) {
            case CASUAL: return "道具多·掉命不掉装备·得分×0.75";
            case HARDCORE: return "敌快弹稀·掉命清空强化·得分×1.5";
            default: return "v1.9 的原始曲线·掉命降 1 级火力";
        }
    }

    public static Difficulty byOrdinal(int ordinal) {
        Difficulty[] all = values();
        if (ordinal < 0 || ordinal >= all.length) return STANDARD;
        return all[ordinal];
    }
}
