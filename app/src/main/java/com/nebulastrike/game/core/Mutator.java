package com.nebulastrike.game.core;

/**
 * 随机异变波（v1.14 迭代 4）：对局进入中盘后，每隔一段时间随机开启一段十几秒的全局"异变"，
 * 把某一条规则推向极端，同时给这段时间内的所有得分加权——玩家不用记新操作，
 * 只要读懂横幅就知道"这十几秒该怎么打"。
 *
 * <p>所有效果都是纯倍率，落在已有钩子（开火冷却 / 登场血量 / 出怪节奏 / 掉落判定 /
 * 暴击判定 / 擦弹充能 / addScore）上，因此对 Boss 招式、武器叠加、点弹幕、弹反一并成立。
 *
 * <p>新条目一律追加在末尾：{@link GameWorld.EventType#MUTATOR_START} 的 code 与
 * 统计都按 ordinal 记录。{@link #NONE} 固定占 ordinal 0，代表正常时空。
 */
public enum Mutator {

    /** 正常时空：所有倍率为 1，用于"这一波没有异变"。 */
    NONE("正常时空", "", 0x8FA3C8, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f),

    /** 铁雨：敌机开火冷却流逝近两倍快，弹幕密度直接翻倍，换 1.6 倍分。 */
    IRON_RAIN("铁雨", "敌机开火频率 ×1.9 · 得分 ×1.6",
            0x5C64FF, 1.9f, 1f, 1f, 1f, 1f, 1f, 1f, 1.6f),

    /** 虫群：出怪节奏翻倍、敌机血量削到六成，用刷屏小怪换连击与分数。 */
    SWARM("虫群", "出怪节奏 ×1.9、敌机血量 ×0.6 · 得分 ×1.5",
            0x5CFFB8, 1f, 1f, 1.9f, 0.6f, 1f, 1f, 1f, 1.5f),

    /** 钢铁洪流：敌机厚血 2.2 倍但必掉更多道具，考验重弹与破甲窗口。 */
    FORTRESS("钢铁洪流", "敌机血量 ×2.2、道具掉落 ×1.8 · 得分 ×1.4",
            0xC0CCE8, 1f, 1f, 1f, 2.2f, 1.8f, 1f, 1f, 1.4f),

    /** 疾行：敌机俯冲更快、间距更大，靠走位而不是火力吃分。 */
    TURBO("疾行", "敌机速度 ×1.4 · 得分 ×1.45",
            0xFFD166, 1f, 1.4f, 1f, 1f, 1f, 1f, 1f, 1.45f),

    /** 军火空投：掉落率暴涨，把一段劣势窗口变成构筑窗口。 */
    ARSENAL("军火空投", "道具掉落 ×3 · 得分 ×1.25",
            0xFFE066, 1f, 1f, 1f, 1f, 3f, 1f, 1f, 1.25f),

    /** 暴击临界：暴击率三倍，重武器（长枪/导弹）在这段最吃香。 */
    CRIT_STORM("暴击临界", "暴击率 ×3 · 得分 ×1.3",
            0xFFB03A, 1f, 1f, 1f, 1f, 1f, 3f, 1f, 1.3f),

    /** 擦弹潮汐：擦弹充能三倍，几分钟内能反复进出过载。 */
    GRAZE_TIDE("擦弹潮汐", "擦弹充能 ×3 · 得分 ×1.35",
            0xB08FFF, 1f, 1f, 1f, 1f, 1f, 1f, 3f, 1.35f);

    public final String label;
    public final String blurb;
    /** 横幅 / HUD / 染色的主色（RGB，alpha 由渲染层决定）。 */
    public final int colorRgb;
    /** 敌机开火冷却流逝倍率（>1 = 开火更频繁）。 */
    public final float fireScale;
    /** 敌机移动速度倍率。 */
    public final float speedScale;
    /** 出怪节奏倍率（>1 = 间隔更短）。 */
    public final float spawnScale;
    /** 敌机登场血量倍率。 */
    public final float hpScale;
    /** 道具掉落率倍率。 */
    public final float dropScale;
    /** 暴击率倍率。 */
    public final float critScale;
    /** 擦弹充能倍率。 */
    public final float grazeScale;
    /** 窗口内所有得分倍率。 */
    public final float scoreScale;

    Mutator(String label, String blurb, int colorRgb, float fireScale, float speedScale,
            float spawnScale, float hpScale, float dropScale, float critScale,
            float grazeScale, float scoreScale) {
        this.label = label;
        this.blurb = blurb;
        this.colorRgb = colorRgb;
        this.fireScale = fireScale;
        this.speedScale = speedScale;
        this.spawnScale = spawnScale;
        this.hpScale = hpScale;
        this.dropScale = dropScale;
        this.critScale = critScale;
        this.grazeScale = grazeScale;
        this.scoreScale = scoreScale;
    }

    /** 可投出的异变种类数（不含 {@link #NONE}）。 */
    public static final int COUNT = 7;

    public boolean isActive() {
        return this != NONE;
    }

    public static Mutator byOrdinal(int ordinal) {
        Mutator[] all = values();
        if (ordinal < 0 || ordinal >= all.length) return NONE;
        return all[ordinal];
    }
}
