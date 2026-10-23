package com.nebulastrike.game.core;

/**
 * 成就定义（v1.18）。
 *
 * <p>每条成就只描述"达成条件"，具体数值由 {@link Metric} 从对局统计里取。两条硬约定：
 *
 * <ul>
 *   <li><b>只增不减</b>：单局指标存历史最佳、累计指标只累加，坠机/换难度/开 LAB 都不会让
 *       已达成 or 已推进的进度倒退；LAB 无敌、休闲档同样照常计入，成就不是惩罚而是记录。</li>
 *   <li><b>门槛必须可达</b>：单局类门槛都取"正常打法够得着"的量级，生涯类门槛按
 *       几十局的累计设计，宁可留长线目标也不放"永远差一点"的成就。</li>
 * </ul>
 *
 * <p>条数上限 64：解锁状态存成一个 long 位图持久化。
 */
public enum Achievement {

    // ---- 单局成就（取历史最佳）----
    FIRST_BLOOD(Scope.RUN, Metric.KILLS, 1, "初次击坠", "击落第一架敌机"),
    CENTURY(Scope.RUN, Metric.KILLS, 120, "百人斩", "单局击坠 120 架"),
    SLICK(Scope.RUN, Metric.GRAZE, 150, "毫厘之间", "单局擦弹 150 次"),
    BORROWED_FORCE(Scope.RUN, Metric.PARRY, 8, "借力打力", "单局弹反 8 发敌弹"),
    SUPPRESSOR(Scope.RUN, Metric.BREAK, 120, "点爆弹幕", "单局点掉 120 发敌弹"),
    OVERLOAD(Scope.RUN, Metric.COMBO, 40, "连击超载", "单局连击达到 40"),
    THREE_MINUTES(Scope.RUN, Metric.TIME, 180, "三分钟不死", "单局生存 180 秒"),
    TWENTY_K(Scope.RUN, Metric.SCORE, 20000, "两万星辰", "单局得分 20000"),
    MARKSMAN(Scope.RUN, Metric.ACC, 65, "神射手", "打出 200 发后命中率达到 65%"),
    IRON_BODY(Scope.RUN, Metric.CLEAN_KILLS, 150, "金身不灭", "一次没挨打就击坠 150 架"),
    APPETITE(Scope.RUN, Metric.GRAVITY, 40, "胃口惊人", "单局重力井吞掉 40 发敌弹"),
    FULL_CHARGE(Scope.RUN, Metric.ULT_TIER, 3, "憋到最满", "释放一次满档终极技"),
    BOOMERANG(Scope.RUN, Metric.ULT_CONVERT, 40, "折木为兵", "单局把 40 发敌弹折回去"),
    TRIPLE_KILL(Scope.RUN, Metric.BOSSES, 3, "一局三弑", "单局击破 3 尊 Boss"),
    RUSH_BREAKER(Scope.RUN, Metric.RUSH, 3, "突袭克星", "单局扛过 3 波突袭"),
    EAGLE_EYE(Scope.RUN, Metric.SPOT, 5, "火眼金睛", "单局识破 5 架伪装者"),
    VETERAN_EYE(Scope.RUN, Metric.MUTATORS, 4, "异变亲历", "单局经历 4 波异变"),

    // ---- 生涯成就（跨局累计）----
    SWEEPER(Scope.CUM, Metric.KILLS, 5000, "星海清扫者", "累计击坠 5000 架"),
    DANCER(Scope.CUM, Metric.GRAZE, 5000, "刀尖舞者", "累计擦弹 5000 次"),
    MIRROR(Scope.CUM, Metric.PARRY, 300, "反弹大师", "累计弹反 300 次"),
    OLD_GUARD(Scope.CUM, Metric.TIME, 7200, "老兵不死", "累计飞行 7200 秒"),
    GODBOUNT(Scope.CUM, Metric.BOSSES, 50, "弑神纪录", "累计击破 50 尊 Boss"),
    FEET_OF_CLAY(Scope.CUM, Metric.DASH, 800, "走位狂魔", "累计冲刺 800 次"),
    COLLECTOR(Scope.CUM, Metric.RELIC, 30, "圣物收藏家", "累计拾取 30 件圣物"),
    SMITH(Scope.CUM, Metric.FUSION, 12, "熔炉工匠", "累计完成 12 次武器融合"),
    BOMBER(Scope.CUM, Metric.ULT_CASTS, 40, "大招狂人", "累计释放 40 次终极技"),
    STORMBREAKER(Scope.CUM, Metric.MAGNET_KILLS, 40, "破磁者", "累计击毁 40 艘磁暴艇"),
    BROOD_HUNTER(Scope.CUM, Metric.QUEEN_KILLS, 20, "灭巢者", "累计击杀 20 只蜂后");

    /** 判定口径：单局取历史最佳，生涯取跨局累计。 */
    public enum Scope { RUN, CUM }

    /**
     * 成就读取的统计口径。
     *
     * <p>约定：RUN 口径每局归零，读数是"本局当前值"，存档时再取历史最佳；
     * CUM 口径要求"单局内只增不减"，累计器按增量入账，所以像命中率这种会回落的量不能设成 CUM。
     */
    public enum Metric {
        KILLS,
        GRAZE,
        PARRY,
        BREAK,
        COMBO,
        TIME,
        SCORE,
        ACC,
        CLEAN_KILLS,
        GRAVITY,
        ULT_TIER,
        ULT_CONVERT,
        ULT_CASTS,
        BOSSES,
        RUSH,
        SPOT,
        MUTATORS,
        DASH,
        RELIC,
        FUSION,
        MAGNET_KILLS,
        QUEEN_KILLS;

        public static final int COUNT = values().length;
    }

    public static final int COUNT = values().length;

    /** 解锁位图用 long 存，超过 64 条就装不下（测试会直接失败提醒）。 */
    public static final int MAX_FOR_BITMAP = 64;

    public final Scope scope;
    public final Metric metric;
    public final int target;
    private final String label;
    private final String desc;

    Achievement(Scope scope, Metric metric, int target, String label, String desc) {
        this.scope = scope;
        this.metric = metric;
        this.target = target;
        this.label = label;
        this.desc = desc;
    }

    public String label() {
        return label;
    }

    public String desc() {
        return desc;
    }

    /** 本条成就在解锁位图里的位。 */
    public long bit() {
        return 1L << ordinal();
    }

    public boolean unlockedIn(long mask) {
        return (mask & bit()) != 0L;
    }

    /** 达标判定：把当前读数（最佳值或累计值）和门槛比一次。 */
    public boolean reached(int value) {
        return value >= target;
    }

    /** 各军衔的解锁条数门槛，rank() 与生涯面板的"下一级还差几条"共用。 */
    private static final int[] RANK_AT = {5, 10, 15, 20, 25, COUNT};
    private static final String[] RANK_NAME = {
            "列兵", "飞行员", "王牌", "舰队长", "提督", "元帅"
    };

    /**
     * 按已解锁条数给出军衔称号（菜单与成就页都显示它，作为长线目标）。
     */
    public static String rank(int unlocked) {
        if (unlocked <= 0) return "未入册";
        for (int i = 0; i < RANK_AT.length; i++) {
            if (unlocked < RANK_AT[i]) return RANK_NAME[i];
        }
        return "星海传说";
    }

    /** 下一个军衔需要多少条；已到最高军衔返回 -1。 */
    public static int nextRankAt(int unlocked) {
        for (int at : RANK_AT) {
            if (unlocked < at) return at;
        }
        return -1;
    }
}
