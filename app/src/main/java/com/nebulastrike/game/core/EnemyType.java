package com.nebulastrike.game.core;

/** Enemy archetypes. Values are tuned for the 900x1600 virtual space. */
public enum EnemyType {
    /** Falls straight down, 1 HP, small. */
    DRONE(26f, 1, 230f, 10, 1),
    /** Sine-wave descent, 1 HP, dodges more. */
    WEAVER(30f, 1, 190f, 15, 2),
    /** Slow brick, 4 HP, worth more. Unlocked in iteration 2. */
    TANK(48f, 4, 120f, 30, 0),
    /** Zig-zag hunter, 2 HP. Unlocked in iteration 2. */
    HUNTER(30f, 2, 330f, 25, 3),
    /** 自杀机：朝飞船俯冲加速，靠近即撞（1 HP，脆弱但压迫力强）。 */
    SUICIDER(24f, 1, 300f, 20, 4),
    /** 分裂舱：慢速重甲，被击毁后裂成两架无人机。 */
    SPLITTER(42f, 5, 140f, 35, 5),
    /** 医疗艇：为附近友舰回血，优先击杀目标。 */
    HEALER(32f, 3, 130f, 40, 6),
    /** 狙击塔：下降到上空悬停，预警后射出高速瞄准弹。 */
    SNIPER(34f, 3, 210f, 45, 7),
    /** 布雷艇（v1.10）：缓慢推进，沿途留下接触即炸的水雷。 */
    MINER(34f, 3, 130f, 35, 8),
    /** 母舰（v1.10）：厚血慢速，持续放出无人机，优先击杀可断源。 */
    CARRIER(56f, 12, 88f, 90, 9),
    /** 盾卫（v1.10）：正面护盾挡下所有非穿透弹，要用激光或打穿护盾才能伤舰体。 */
    SHIELDER(38f, 6, 120f, 60, 10),
    /** 伪装者（v1.17）：伪装成掉落道具下压，靠近或超时才现形；识破（现形前击毁）必掉真道具。 */
    MIRAGE(28f, 1, 150f, 45, 11),
    /** 磁暴艇（v1.17）：周身磁场弯曲玩家的直射弹，追踪弹/穿透光束/光矛不受影响。 */
    MAGNETON(44f, 7, 110f, 75, 12),
    /** 蜂后（v1.17）：在上空游走产卵，与你保持距离；击毁时她的幼机一并崩溃。 */
    QUEEN(52f, 16, 100f, 140, 13),
    /** 冲撞者（v1.21）：悬停瞄准后沿预警线俯冲，撞停后硬直，硬直期受击双倍。 */
    RAMMER(40f, 7, 150f, 55, 14),
    /** 相位者（v1.22）：周期性相位化——无敌穿弹 0.7 秒，重新实体化的瞬间受击双倍。 */
    PHASER(36f, 6, 155f, 70, 15),
    /** 追猎者（v1.33）：悬停跟踪玩家横向位置，发射双发追踪弹；转向有限，可以甩掉。 */
    SEEKER(38f, 6, 140f, 75, 16),
    /** 缚网者（v1.34）：在中场横向摆渡，投下减速网弹；命中不扣命，但会减速并封锁冲刺。 */
    SNARER(40f, 7, 145f, 80, 17),
    /** 螺旋炮台（v1.35）：悬停高位，以三臂螺旋连续吐出慢速弹，缺口随旋转移动。 */
    HELIX(36f, 8, 130f, 95, 18),
    /** 切割者（v1.38）：悬停高位瞄准一列，预警后落下贯穿全场的竖切光束；只能靠位移躲。 */
    CUTTER(40f, 9, 135f, 110, 19);

    public final float radius;
    public final int hp;
    public final float baseSpeed;
    public final int score;
    /** Palette index used by the renderer (0 cyan, 1 green, 2 orange, 3 magenta, 4 yellow,
     *  5 lime, 6 rose, 7 violet, 8 mint, 9 crimson, 10 steel,
     *  11 phantom rose, 12 storm blue, 13 brood amber, 14 impact ember, 15 shift violet,
     *  16 tracker periwinkle, 17 snare violet, 18 helix cyan, 19 cutter mint). */
    public final int colorHint;

    EnemyType(float radius, int hp, float baseSpeed, int score, int colorHint) {
        this.radius = radius;
        this.hp = hp;
        this.baseSpeed = baseSpeed;
        this.score = score;
        this.colorHint = colorHint;
    }

    /** 是否属于"后期解锁"兵种（图鉴/生成权重按登场时间排序用）。 */
    public boolean isAdvanced() {
        switch (this) {
            case SUICIDER: case SPLITTER: case HEALER: case SNIPER:
            case MINER: case CARRIER: case SHIELDER:
            case MIRAGE: case MAGNETON: case QUEEN: case RAMMER: case PHASER: case SEEKER:
            case SNARER: case HELIX: case CUTTER:
                return true;
            default:
                return false;
        }
    }
}
