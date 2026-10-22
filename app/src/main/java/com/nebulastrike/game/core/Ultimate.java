package com.nebulastrike.game.core;

/**
 * 终极技能（ULT）的纯逻辑状态：战斗行为积累槽，攒满后由玩家选择时机释放。
 *
 * <p>设计约束（与 v1.15 重力井同一条约定）：<b>任何积累都不许白攒</b>。槽满之后继续战斗
 * 不会浪费——溢出部分直接折算成分数，所以"没空按大招"的代价只是延后收益，而不是损失。
 *
 * <p>三档威力由释放瞬间的槽量决定（34 / 67 / 100），所以提前放是战术、憋满放也是战术，
 * 两条路都不亏：档位只影响光矛数量与伤害、时停与无敌时长，不会改变"能释放"这件事本身。
 */
public final class Ultimate {

    /** 满槽。 */
    public static final float CAP = 100f;
    /** 最低可释放槽量（第一档）。 */
    public static final float MIN_CAST = 34f;
    /** 第二档门槛。 */
    public static final float TIER_TWO = 67f;
    /** 溢出 1 点槽折算的分数：槽满还在战斗说明玩家没空按，别让他亏。 */
    public static final int OVERFLOW_SCORE = 6;

    /** 当前槽量（0..{@link #CAP}）。 */
    public float charge;
    /** 本局释放过的终极技次数（结算数据卡）。 */
    public int casts;
    /** 本局折算掉的溢出槽（结算与测试用）。 */
    public int overflowSpent;
    /** 本局释放过的最高档位（v1.18 成就"憋到最满"用它判定；0 = 还没放过）。 */
    public int bestTier;
    /** 时停剩余秒数（>0 期间敌机/敌弹/Boss 近乎凝滞）。 */
    public float stasisTimer;

    /**
     * 积累槽量。
     *
     * @return 因超过满槽而被丢弃的槽量（调用方据此折算分数）
     */
    public float add(float amount) {
        if (amount <= 0f) return 0f;
        float next = charge + amount;
        if (next <= CAP) {
            charge = next;
            return 0f;
        }
        float overflow = next - CAP;
        charge = CAP;
        return overflow;
    }

    public boolean ready() {
        return charge >= MIN_CAST;
    }

    /** 是否已经攒满（HUD 用它做"可放大招"的脉冲提示）。 */
    public boolean full() {
        return charge >= CAP;
    }

    /** 释放瞬间的档位：1 / 2 / 3，槽量不足时为 0。 */
    public int tier() {
        if (charge >= CAP) return 3;
        if (charge >= TIER_TWO) return 2;
        if (charge >= MIN_CAST) return 1;
        return 0;
    }

    /** 消耗整槽并返回档位；不可释放时返回 0 且不扣槽。 */
    public int spend() {
        int t = tier();
        if (t == 0) return 0;
        charge = 0f;
        casts++;
        if (t > bestTier) bestTier = t;
        return t;
    }

    /** 本档的光矛目标数上限。 */
    public static int lancesForTier(int tier) {
        switch (tier) {
            case 3: return 24;
            case 2: return 14;
            case 1: return 7;
            default: return 0;
        }
    }

    /** 本档每发光矛的伤害。 */
    public static int damageForTier(int tier) {
        switch (tier) {
            case 3: return 8;
            case 2: return 6;
            case 1: return 4;
            default: return 0;
        }
    }

    /** 本档的时停秒数（同时决定无敌帧长度，见 {@link #invulnForTier}）。 */
    public static float stasisForTier(int tier) {
        switch (tier) {
            case 3: return 1.15f;
            case 2: return 0.8f;
            case 1: return 0.5f;
            default: return 0f;
        }
    }

    /** 本档给玩家的从容输出窗口——放完不会被立刻撞死。 */
    public static float invulnForTier(int tier) {
        switch (tier) {
            case 3: return 2.4f;
            case 2: return 1.6f;
            case 1: return 1.0f;
            default: return 0f;
        }
    }

    /** 时停期间敌我时间比例：不是完全静止，留一点余量避免"卡屏"错觉。 */
    public static final float STASIS_SCALE = 0.1f;

    /** @return false 表示时停已结束（调用方可以收掉演出） */
    public boolean updateStasis(float dt) {
        if (stasisTimer <= 0f) return false;
        stasisTimer = Math.max(0f, stasisTimer - dt);
        return stasisTimer > 0f;
    }

    /** @return 时停仍在生效返回缩放后的时间比例，否则返回 1（不干扰正常节奏） */
    public float timeScale() {
        return stasisTimer > 0f ? STASIS_SCALE : 1f;
    }

    public void reset() {
        charge = 0f;
        casts = 0;
        bestTier = 0;
        overflowSpent = 0;
        stasisTimer = 0f;
    }
}
