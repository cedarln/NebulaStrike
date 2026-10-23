package com.nebulastrike.game.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Central simulation of the game. Runs in a fixed virtual space (900x1600 portrait) that the
 * renderer scales to the actual display, keeping gameplay identical on every device.
 * Pure Java: fully unit-testable on the JVM.
 */
public class GameWorld {
    /** 竖屏虚拟坐标系（v1.3 起从 1600x900 横屏改为 900x1600 竖屏）。 */
    public static final float WIDTH = 900f;
    public static final float HEIGHT = 1600f;

    public enum State { MENU, PLAYING, PAUSED, GAME_OVER }

    /** Game events consumed by the presentation layer (sfx, haptics, fx). */
    public enum EventType {
        ENEMY_KILLED, PLAYER_HIT, GAME_OVER, ENEMY_ESCAPED, FORMATION,
        POWERUP_COLLECTED, BOMB, BOSS_SPAWNED, BOSS_DEFEATED, BOSS_PHASE,
        BOSS_PART_DESTROYED, BOSS_DISARMED, BOSS_LASER,
        OVERDRIVE, GRAZE, SHIELD_BROKEN, COMBO,
        /** v1.9：相位冲刺 / 连锁闪电 / 武器融合 / Boss 横冲 / 突袭波 / 治疗脉冲 / 轨道球命中 */
        DASH, CHAIN, FUSION, BOSS_RAM, RUSH, HEAL_PULSE, ORBIT_HIT,
        /** v1.10：新星冲击 / 部署炮台 / 获得圣物 / 重生保护 / 布雷艇投雷 / 盾卫挡弹 */
        NOVA, TURRET_DEPLOY, RELIC_GAINED, RESPAWN, MINE, SHIELD_BLOCK,
        /** v1.11：暴击命中（code = 实际伤害） */
        CRIT,
        /** v1.12：点爆敌弹（code 1 = 水雷） */
        BULLET_BREAK,
        /** v1.13：冲刺弹反（code = 本次冲刺内的连击序号，从 1 开始） */
        PARRY,
        /** v1.14：异变波开始（code = {@link Mutator#ordinal()}） */
        MUTATOR_START,
        /** v1.14：异变波平息（code = 奖励分数） */
        MUTATOR_CLEAR,
        /** v1.15：部署重力井 */
        GRAVITY_DEPLOY,
        /** v1.15：重力井吞掉一颗敌弹（code = 该井当前库存） */
        GRAVITY_ABSORB,
        /** v1.15：重力井喷还（code = 喷出的弹数） */
        GRAVITY_BURST,
        /** v1.16：终极槽充满，可以放大招（HUD 提示一次） */
        ULT_READY,
        /** v1.16：释放终极技（code = 档位 1/2/3） */
        ULT_CAST,
        /** v1.16：终极技光矛齐射（code = 实际放出的矛数） */
        ULT_LANCE,
        /** v1.17：伪装者现形（玩家识破或超时暴露） */
        MIRAGE_REVEAL,
        /** v1.17：磁暴艇 EMP 环形脉冲（code = 弹数） */
        MAG_BURST,
        /** v1.17：蜂后产卵（code = 幼机数） */
        BROOD_SPAWN,
        /** v1.17：特化遭遇保底登场（code = EnemyType.ordinal()） */
        NEW_SHIP_DEBUT,
        /** v1.18：解锁成就（code = {@link Achievement#ordinal()}） */
        ACHIEVEMENT_UNLOCKED,
        /** v1.19：每日挑战开局（code = 日期键对今日规则的序号） */
        DAILY_START,
        /** v1.20：BOSS RUSH 开局（code = 本局固定的命数） */
        BOSS_RUSH_START,
        /** v1.21：冲撞者撞停（硬直窗口开始，此时受击双倍） */
        RAM_IMPACT,
        /** v1.22：相位者切换相位（code 1 = 进入相位，0 = 重新实体化） */
        PHASE_SHIFT,
        /** v1.23：反射护罩把一发敌弹弹回去（code = 剩余就绪层数） */
        REFLECT,
        /** v1.24：电弧炮命中后的连锁放电（code = 连锁到的目标数） */
        ARC_CHAIN,
        /** v1.25：Boss 弹幕网（code = 本次网格弹数） */
        BOSS_NET,
        /** v1.25：Boss 召唤棱镜（code = 召唤数量） */
        BOSS_PRISM
    }
    public static final class GameEvent {
        public final EventType type;
        public final float x, y;
        /** Optional payload: enemy/kind ordinal, boss phase or combo count. */
        public final int code;
        public GameEvent(EventType type, float x, float y) {
            this(type, x, y, 0);
        }
        public GameEvent(EventType type, float x, float y, int code) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.code = code;
        }
    }

    /** Seedable RNG shared by simulation code; tests can pin it via {@link #setSeed}. */
    public static final class RNG {
        private static final ThreadLocal<Random> TL = new ThreadLocal<>();
        public static Random get() {
            Random r = TL.get();
            if (r == null) { r = new Random(42); TL.set(r); }
            return r;
        }
        public static void setSeed(long seed) { TL.set(new Random(seed)); }
    }

    public final Ship ship = new Ship();
    /** 多武器叠加后同屏弹丸更多，池子相应扩充（v1.10 基础炮常驻后再加一档）。 */
    public final Bullet[] bullets = new Bullet[168];
    public final EnemyBullet[] enemyBullets = new EnemyBullet[72];
    public final Enemy[] enemies = new Enemy[48];
    public final Particle[] particles = new Particle[256];
    public final PowerUp[] powerups = new PowerUp[24];
    /** 玩家部署的炮台（TURRET 道具），最多同时 3 座。 */
    public final Turret[] turrets = new Turret[3];
    /** v1.15：重力井（GRAVITY 道具）同时最多 2 口，避免整屏敌弹被吸干净。 */
    public final GravityWell[] gravityWells = new GravityWell[2];
    public final Star[] stars = new Star[90];
    /** v1.11 伤害/得分飘字池；满槽时覆盖最旧的一条（与粒子同样的 ring cursor）。 */
    public final FloatText[] floatTexts = new FloatText[40];
    private int floatCursor;
    /** 每帧最多新起几条飘字，防止穿透激光刷屏。 */
    private int floatBudget;
    public static final int FLOAT_BUDGET_PER_FRAME = 6;

    /** 飘字语义色（0xRRGGBB，Renderer 直接取用）。 */
    public static final int FLOAT_DAMAGE = 0xE8F1FF;
    public static final int FLOAT_CRIT = 0xFFB03A;
    public static final int FLOAT_SCORE = 0xFFD34A;
    public static final int FLOAT_BREAK = 0x7DF9FF;
    public static final int FLOAT_PARRY = 0xB0FFF6;
    /** 异变平息奖励飘字（Renderer 直接取用）。 */
    public static final int FLOAT_MUTATOR = 0xFFD166;
    /** 重力井吞弹飘字（Renderer 直接取用）。 */
    public static final int FLOAT_GRAVITY = 0xC9A0FF;
    /** 终极技充能折算 / 折弹得分的飘字色（金色）。 */
    public static final int FLOAT_ULT = 0xFFE08A;

    /** 暴击：命中有小概率加成伤害，过载期间概率更高。 */
    public static final float CRIT_CHANCE = 0.08f;
    public static final float CRIT_CHANCE_OVERDRIVE = 0.18f;
    public static final float CRIT_MULT = 1.7f;
    /** 本局暴击次数（结算数据卡）。 */
    public int crits;
    /** 本局被玩家子弹打爆的敌弹数（点弹幕成就与数据卡用）。 */
    public int bulletsShotDown;
    /** 刚点掉敌弹的 HUD 脉冲（Renderer 用来放大"截弹"计数）。 */
    public float breakPulse;
    /** 打爆一枚敌弹的分数：低到不会鼓励"只点弹不打机"，但连击会放大它。 */
    public static final int BULLET_BREAK_SCORE = 2;
    /** 点弹幕的粗筛半径：远超这个距离的直接跳过，避免 168×56 全量配对。 */
    private static final float BREAK_CULL = 96f;
    /**
     * 能点掉敌弹的最低单发伤害。基础机炮伤害 1，不参与拦截——
     * 否则重弹幕里机炮流会被敌弹全部吃掉，Boss 战输出直接崩盘。
     * 想点弹就得换导弹 / 集束 / 长枪 / 弹跳这类重弹，"清出一条路"和"攒一发伤害"从此二选一。
     */
    public static final int BREAK_MIN_DAMAGE = 2;

    /**
     * 弹反 PARRY（v1.13 迭代 3）：正在相位冲刺时贴身撞上的敌弹会被折返成友军火力。
     * 冲刺本来只是无敌位移（躲掉=零收益，还拿不到擦弹），弹反把"躲"变成"打"：
     * 同一次冲刺里连续弹反按连击倍率计分，越贴脸越值钱。
     */
    public static final int PARRY_SCORE = 15;
    /** 折返弹的单发伤害：够格参与点弹幕，于是弹反还能连锁清出一片路。 */
    public static final int PARRY_DAMAGE = 3;
    /** 弹反判定在船体之外再放宽这么多单位，给一点点宽容度。 */
    public static final float PARRY_BAND = 18f;
    /** 每次弹反补的擦弹槽（过载充能），躲弹幕顺带回血输出循环。 */
    public static final float PARRY_GRAZE_CHARGE = 0.05f;
    /** 折反弹的保底弹速：慢敌弹原样反弹会变成飘半屏的"气球"，手感差还占弹池。 */
    public static final float PARRY_MIN_SPEED = 900f;
    /** 本局弹反次数与同一次冲刺内的连击数。 */
    public int parries;
    public float parryFlash;
    private int parryStreak;
    /** 单次冲刺里最高的连反数（结算数据卡）。 */
    public int maxParryStreak;
    /** 本局击坠的精英机数量（结算数据卡）。 */
    public int elitesKilled;

    // ---- v1.14：随机异变波（迭代 4）----
    /** 当前异变；NONE 表示正常时空。 */
    public Mutator mutator = Mutator.NONE;
    /** 异变剩余秒数（0 时下一次投骰冷却用 {@link #mutatorNextAt}）。 */
    public float mutatorTimer;
    /** 下一次异变投骰的时刻（timeAlive 秒）。 */
    public float mutatorNextAt = MUTATOR_FIRST_AT;
    /** 异变横幅余量（HUD 用，与 relicBanner 同族）。 */
    public float mutatorBanner;
    /** 本局经历过的异变波次数。 */
    public int mutatorsSeen;
    /** 本局在异变窗口里拿到的额外分数（结算与测试用）。 */
    public int mutatorBonus;
    /** 当前窗口内击坠数：窗口结束时按它给"异变平息"奖励。 */
    private int mutatorKills;
    /** 异变波开关：需要确定性的模式（每日挑战 / Boss Rush）可以关掉。 */
    public boolean mutatorsEnabled = true;

    // ---- v1.15：重力井道具 GRAVITY（迭代 5）----
    /** 喷还弹的伤害：2 点足够越过 {@link #BREAK_MIN_DAMAGE}，能连锁点掉敌弹。 */
    public static final int GRAVITY_BURST_DAMAGE = 2;
    /** 喷还弹速：与弹反一致保底拉到正常弹速，避免"飘半天才出屏"。 */
    public static final float GRAVITY_BURST_SPEED = 1050f;
    /** 喷还扇形的张角（弧度），以正上方为中心。 */
    public static final float GRAVITY_BURST_SPREAD = 2.6f;
    /** 每吞一颗敌弹的分数（吞弹本身是保命，也给小额正反馈）。 */
    public static final int GRAVITY_ABSORB_SCORE = 3;
    /** 吞弹也补擦弹槽，但只按擦弹的三成计，避免吸弹比贴弹走位更划算。 */
    public static final float ABSORB_GRAZE_FACTOR = 0.3f;
    /** 部署重力井时的屏幕余晖（渲染层用，与 novaFlash 同族）。 */
    public float gravityFlash;
    /** 本局被重力井吞掉的敌弹总数（结算数据卡）。 */
    public int gravityAbsorbed;
    /** 本局重力井喷还次数。 */
    public int gravityBursts;
    /** 本局部署过的重力井口数。 */
    public int gravityWellsDeployed;
    /** 本局被终极技折成友军弹的敌弹数（结算数据卡）。 */
    public int ultConverted;

    // ---- v1.16：终极技能 ULT（迭代 6）----
    /** 终极槽状态（纯逻辑，见 {@link Ultimate}）：战斗行为积累，玩家选时机释放。 */
    public final Ultimate ult = new Ultimate();
    /** 释放终极技的屏幕余晖（渲染层用，与 novaFlash 同族）。 */
    public float ultFlash;
    /** 光矛飞行速度：比普通导弹快一档，大招要"唰"地穿过去。 */
    public static final float ULT_LANCE_SPEED = 1250f;
    /** 光矛半径：渲染与命中都用，比喷还弹更粗。 */
    public static final float ULT_LANCE_RADIUS = 15f;
    /** 终极技把敌弹折成友军弹时的伤害（与弹反同级，仍够点弹幕门槛）。 */
    public static final int ULT_CONVERT_DAMAGE = 3;
    /** 每折一颗敌弹的分数。 */
    public static final int ULT_CONVERT_SCORE = 2;
    /** 终极槽充能倍率：休闲档更容易攒满，硬核档更考验节奏。 */
    public static final float ULT_CHARGE_CASUAL = 1.3f;
    public static final float ULT_CHARGE_HARDCORE = 0.8f;
    /** 各类战斗行为的充能量（击坠 / 精英 / 擦弹 / 点弹 / 弹反 / 吞弹 / 部件 / Boss）。 */
    public static final float ULT_GAIN_KILL = 3f;
    public static final float ULT_GAIN_ELITE = 10f;
    public static final float ULT_GAIN_GRAZE = 0.6f;
    public static final float ULT_GAIN_BREAK = 1f;
    public static final float ULT_GAIN_PARRY = 2f;
    public static final float ULT_GAIN_ABSORB = 1.5f;
    public static final float ULT_GAIN_BOSS_PART = 8f;
    public static final float ULT_GAIN_BOSS = 30f;

    // ---- v1.17：伪装者 / 磁暴艇 / 蜂后（迭代 7）----
    /** 磁暴艇 EMP 脉冲的弹数与弹速（慢环，靠"穿环"而不是"躲不完"）。 */
    public static final int MAG_BULLET_COUNT = 8;
    public static final float MAG_BULLET_SPEED = 250f;
    public static final float MAG_BULLET_RADIUS = 12f;
    /** 磁暴艇被击毁时坍缩场清敌弹的半径——打死它等于白捡一次小新星。 */
    public static final float MAG_COLLAPSE_RADIUS = 260f;
    /** 伪装者现形后的环形警告弹数与弹速。 */
    public static final int MIRAGE_BURST_COUNT = 6;
    public static final float MIRAGE_BURST_SPEED = 300f;
    /** 识破伪装（现形前击毁）的额外分数：奖励"看得仔细"而不是"手快"。 */
    public static final int MIRAGE_SPOT_BONUS = 25;
    /** 蜂后单次产卵的幼机数上限（受伤后从 2 涨到 3，节奏更紧）。 */
    public static final int QUEEN_BROOD_MIN = 2;
    public static final int QUEEN_BROOD_MAX = 3;
    /** 本局现形过的伪装者数量（结算数据卡与图鉴统计）。 */
    public int miragesRevealed;
    /** 本局识破（现形前击毁）的伪装者数量。 */
    public int miragesSpotted;
    /** 本局磁暴脉冲次数。 */
    public int magnetBursts;
    /** 本局蜂后产下的幼机总数。 */
    public int broodSpawned;
    /** 本局被磁场偏折过的玩家子弹数（用于测试与后续统计）。 */
    public int bulletsBent;
    /** 磁场源坐标缓存：每帧收集一次，弹道偏折线性扫这几项，零分配。 */
    private final float[] magnetXs = new float[4];
    private final float[] magnetYs = new float[4];
    private int magnetCount;

    /** 首个异变在 45 秒后出现：开局教学段保持纯净，玩家先学会基本循环再遇变量。 */
    public static final float MUTATOR_FIRST_AT = 45f;
    /** 单个窗口时长。 */
    public static final float MUTATOR_DURATION = 16f;
    /** 两次异变之间的间隔（窗口结束后重新计时），保证三分之二时间是正常节奏。 */
    public static final float MUTATOR_GAP = 34f;
    /** 扛过一个窗口的收尾奖励：基础分 + 窗口内每次击坠的加分。 */
    public static final int MUTATOR_CLEAR_BONUS = 150;
    public static final int MUTATOR_CLEAR_KILL_BONUS = 4;

    /** 输入/升级状态在 UI 线程写入、游戏线程读取，必须 volatile 保证可见性。 */
    public volatile State state = State.PLAYING;
    /** seconds spent in the current non-PLAYING state (UI guards use it) */
    public float stateTimer;
    /** active play mode; set by restart(mode) when a run launches from the menu */
    public volatile GameMode mode = GameMode.WAVES;
    public int score;
    public int lives = 3;
    public int kills;
    public int spawnedCount;
    public int shotsFired;
    /** 本帧实际开火的武器位掩码（bit = WeaponType.ordinal()），供音效层区分多武器齐射。 */
    public int weaponVolleyMask;
    /** wingman volleys (separate counter so the audio layer can use a softer blip). */
    public int wingmanShots;
    /** projectiles actually created (accuracy denominator; volleys are not bullets). */
    public int bulletsFired;
    /** projectiles that hit at least once (piercing counted once). */
    public int shotsHit;
    public int maxCombo;
    public int hitsTaken;
    public int grazes;
    public float timeAlive;
    public int wave = 1;

    // combo system: consecutive kills without taking a hit
    public int comboKills;
    public float comboTimer;
    public static final float COMBO_WINDOW = 3f;

    // screen shake
    public float shakeTime;
    public float shakeMag;
    /** white flash overlay after a smart bomb, decays to 0 */
    public float bombFlash;

    // timed powerup effects
    /** 双倍分数剩余时间（>0 时得分翻倍） */
    public float x2Timer;
    public static final float X2_DURATION = 8f;
    public static final int MAX_LIVES = 4;
    /** 缓时道具剩余时间；>0 时敌人/敌弹/Boss 以 45% 速度运行 */
    public float slowTimer;
    public static final float SLOW_DURATION = 6f;
    public static final float SLOW_TIME_SCALE = 0.45f;

    // 擦弹充能：敌弹擦身而过积攒，满槽自动进入 OVERDRIVE
    public static final float GRAZE_BAND = 26f;
    public static final float GRAZE_CHARGE_PER_BULLET = 0.08f;
    /** 每次擦弹额外充的冲刺能量（基础值，虹吸圣物会加倍）。 */
    public static final float GRAZE_DASH_PER_BULLET = 0.05f;
    public float grazeCharge;

    // ---- v1.10：Boss 圣物 / 重生保护 / 新星冲击 ----
    /** 已持有圣物的位掩码（bit = {@link Relic#ordinal()}），永久到本局结束。 */
    public int relicMask;
    /** 最近一次获得的圣物与横幅余量（HUD 用）。 */
    public Relic lastRelic;
    public float relicBanner;
    /** 圣物累计获得次数（结算页与测试用）。 */
    public int relicsGained;
    /** 重生保护剩余时间（渲染层画倒计时气泡）。 */
    public float respawnGrace;
    /** 光束核心（圣物）与圣盾自动补盾的倒计时。 */
    private float relicBeamCd;
    private float relicAegisCd;
    /** 纳米修复补盾倒计时。 */
    private float nanoCd;
    /** 新星冲击的屏幕余晖（渲染层用）。 */
    public float novaFlash;
    public static final float NOVA_DAMAGE = 3f;
    /** 弹跳弹每发可用的反弹次数。 */
    public static final int MAX_BOUNCES = 3;
    /** RELIC_GAINED 事件的 code 上限，方便测试断言。 */
    public static final int RELIC_COUNT = Relic.COUNT;

    // persistence-backed state
    public int bestScore;
    public boolean newBest;
    /** local top-5 scores, descending; 0 = empty slot. Three boards: WAVES / ENDLESS / RUSH */
    public final int[] leaderboard = new int[5];
    public final int[] leaderboardEndless = new int[5];
    public final int[] leaderboardRush = new int[5];
    /** 0-based rank of the last finished run on the board for the active mode; -1 = not on board */
    public int scoreRank = -1;
    public final Settings settings = new Settings();
    public volatile boolean settingsOpen;
    public volatile boolean cheatOpen;
    /** 图鉴面板（菜单内）。 */
    public volatile boolean codexOpen;
    /** 图鉴当前页签：0 = 敌机，1 = Boss。 */
    public volatile int codexTab;
    /** 成就面板（菜单内，v1.18）。 */
    public volatile boolean achieveOpen;
    /** 成就页签：0 = 单局最佳，1 = 生涯累计。 */
    public volatile int achieveTab;
    private SettingsStore store;

    // ---- 应用内升级检测（状态机；网络 IO 由 app 层异步执行，这里只存状态） ----
    public enum UpdateState { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, ERROR }
    public volatile UpdateState updateState = UpdateState.IDLE;
    public volatile String updateVersion;          // 检测到的新版本 tag，如 "v1.2.0"
    public volatile String updateUrl;              // 新 APK 下载地址
    public volatile String updateError;            // 失败提示
    public volatile int updateProgress;            // 下载进度 0-100
    public volatile float updateMsgTimer;          // UP_TO_DATE / ERROR 提示的自动消隐
    public String currentVersion = "";    // 当前 versionName，由 app 层填入

    // boss battle
    public final Boss boss = new Boss();
    /** 独立瞄准的 Boss 激光（预警 -> 持续光束）；满足棱镜十字光束同时开火。 */
    public final BossLaser[] bossLasers = new BossLaser[12];
    public int bossesDefeated;
    int nextBossWave = 4;
    /**
     * 两场 Boss 之间的最短喘息（秒）。wave 完全由分数驱动，后期分数涨得飞快，
     * 只按 wave 节拍会让 Boss 一场接一场、普通刷新几乎没有位置。
     */
    static final float BOSS_MIN_GAP = 12f;
    /** Boss 刚打完时的冷却倒计时（包级可见，测试可直接清零来强制触发下一场）。 */
    float bossGapTimer;
    /** boss dead-flash timer for the renderer */
    public float bossFlash;
    /** 每种特殊武器独立的开火冷却，实现真正的同时叠加。 */
    private final float[] weaponCd = new float[WeaponType.values().length];

    // ---- 连锁闪电 / 轨道球的瞬时表现数据（渲染层读取） ----
    public static final int MAX_ARCS = 14;
    public final float[] arcX1 = new float[MAX_ARCS];
    public final float[] arcY1 = new float[MAX_ARCS];
    public final float[] arcX2 = new float[MAX_ARCS];
    public final float[] arcY2 = new float[MAX_ARCS];
    /** >0 表示该电弧仍在显示（每帧递减）。 */
    public final float[] arcAge = new float[MAX_ARCS];
    /** 轨道球坐标缓冲（避免每帧分配） */
    private final float[] orbitScratch = new float[2];
    /** 0 = 连锁放电，1 = 医疗脉冲 */
    public final int[] arcKind = new int[MAX_ARCS];
    /** 轨道球各自的命中冷却，避免一帧内反复结算同一目标。 */
    private final float[] orbitCd = new float[Ship.ORBIT_SPHERES];

    // ---- hit-stop：击杀/破坏大事件时短暂放慢全局模拟，冲击感更强 ----
    public float hitStopTimer;
    public static final float HIT_STOP_SCALE = 0.22f;

    /** 突袭奖励波倒计时（首次触发前的等待时间）。 */
    float rushTimer = 26f;
    /** 突袭波进行中标记与剩余时间，供 HUD 横幅显示。 */
    public boolean rushActive;
    public float rushBanner;
    public int rushesTriggered;

    // ---- v1.17 特化遭遇保底 ----
    /**
     * 常规刷新要先抢空敌机池，中后期三种新舰型经常一局只露面一两次，
     * 玩法再好也等于没做。这里按解锁窗口保底投放：到点就确保场上至少有一艘。
     */
    static final EnemyType[] V117_TYPES = {
            EnemyType.MIRAGE, EnemyType.MAGNETON, EnemyType.QUEEN,
            EnemyType.RAMMER, EnemyType.PHASER};
    /** 与 V117_TYPES 对应的解锁 sim-time 窗口，和 pickEnemyType 里的出场时间一致。 */
    static final float[] V117_WINDOWS = {115f, 150f, 180f, 125f, 145f};
    /** 保底遭遇倒计时（做成包级字段，测试可以直接拨快）。 */
    float v117EncounterTimer = 8f;
    /** 每种新舰型的保底遭遇次数，用于轮换——优先补最少出现的那一型。 */
    final int[] v117EncounterCounts = new int[V117_TYPES.length];
    public int v117Encounters;
    /**
     * 首次遭遇新舰型时的介绍横幅。每种舰只在存档里第一次登场时弹一次，
     * 之后就交给图鉴——否则每次都弹同一句提示，玩家只会把它当噪声忽略掉。
     */
    public float debutBanner;
    public EnemyType debutBannerType;

    // ---- v1.18 成就系统 ----
    /** 解锁一条成就立刻给的分（纯增益奖励：没有任何东西会因为拿成就而变少）。 */
    public static final int ACHIEVEMENT_SCORE = 300;
    /** 每种敌机的本局击毁数（生涯成就按击毁对象分类）。 */
    public final int[] typeKills = new int[EnemyType.values().length];
    /** 本局完成的武器融合次数。 */
    public int fusions;
    /** 本局因解锁成就获得的分数（结算页展示）。 */
    public int achieveScore;
    /** 最近一次解锁实际到手的分数（含难度、双倍、异变加成；横幅展示）。 */
    public int achieveLastGain;
    /**
     * 不参与关卡节拍的分数：成就奖励是"庆祝"，不是"表现"。
     * wave 由总分驱动（每 500 分一档），若把白送的奖励分也算进节拍，
     * 解锁越多的玩家 Boss 来得越早、普通敌机越稀——新系统反而把对局改得不一样了。
     * 所以这里记下已发放的奖励额度，节拍判定时扣掉。
     */
    public int pacingExemptScore;
    /**
     * 演出随机流：与对局随机源完全隔离，专门给"庆祝类"特效取粒子角度。
     * 种子固定，所以同一台设备上的演出可复现，但又不会挤占对局的随机数序列。
     */
    private final Random fxRandom = new Random(0x5A1EEDL);
    /** 本局解锁的成就条数（结算页展示）。 */
    public int achievesThisRun;
    /** 成就解锁横幅（渲染层与音效消费）。 */
    public float achieveBanner;
    public Achievement achieveBannerType;
    /** 判定器：把本局读数写进 Settings 的两本账并播报解锁。 */
    private final Achievements achievements = new Achievements(this::onAchievementUnlocked);
    /** 每种统计口径的本局读数，交给判定器（复用同一个数组，不每帧分配）。 */
    private final int[] runMetrics = new int[Achievement.Metric.COUNT];

    // ---- v1.19 每日挑战 ----
    /** 今日日期键（yyyyMMdd）；非日常模式为 0。 */
    public int dailyKey;
    /** 今日常驻规则（一种异变倍率）；非日常模式恒为 {@link Mutator#NONE}。 */
    public Mutator dailyRule = Mutator.NONE;
    /** 今日命数（1~3），由日期决定，与 LAB 无关。 */
    public int dailyLives = 3;
    /** 本局是否刷新了今日最佳（结算页提示）。 */
    public boolean dailyImproved;
    /** 开局横幅剩余秒数。 */
    public float dailyBanner;
    /**
     * 测试可以把"今天"钉死（传入指定毫秒）；0 = 取系统时间。
     * 日常挑战的一切规则都由日期推导，所以这条缝只用于复现，不影响真实存档。
     */
    public volatile long dailyNowMillis;
    /**
     * 待装的日常种子。{@link RNG} 是 ThreadLocal，而"点开始"发生在 UI 线程、
     * 跑模拟在渲染线程——只在开局的调用里 setSeed 会种错线程。
     * 所以种子先存这里，{@link #update} 第一行在真正的模拟线程上装一次。
     */
    private volatile long pendingRngSeed;
    /** {@link #todayKey()} 的一秒缓存：渲染层每帧要读日期，但不能每帧建 Calendar。 */
    private int cachedDailyKey;
    private long cachedDailyCheck;

    // ---- v1.20 BOSS RUSH ----
    /** 连战局固定命数（不吃 LAB）。 */
    public static final int RUSH_LIVES = 3;
    /** 两场 Boss 之间的喘息：刚好够捡完掉落，不给刷小怪的时间。 */
    static final float BOSS_RUSH_GAP = 1.8f;
    /** 开局到第一个 Boss 入场的准备时间。 */
    static final float BOSS_RUSH_OPENING = 2.6f;
    /** 本局已连续击败的 Boss 数（结算页与 HUD 用）。 */
    public int bossRushKills;
    /** 开局横幅剩余秒数。 */
    public float bossRushBanner;
    /** 本局是否刷新了"深入纪录"（结算页提示）。 */
    public boolean bossRushImproved;

    // ---- 得分构成（结算图用） ----
    public static final int SCORE_ENEMY = 0;
    public static final int SCORE_BOSS = 1;
    public static final int SCORE_PART = 2;
    public static final int SCORE_PICKUP = 3;
    public static final int SCORE_OTHER = 4;
    public static final int SCORE_SOURCES = 5;
    /** 实际计入的分数（已含双倍道具），按来源累计。 */
    public final int[] scoreBySource = new int[SCORE_SOURCES];

    // adaptive quality: frame-cost feedback scales particle emission
    /** 1 = full juice, 0.5 = reduced emission on slow devices */
    public float emissionScale = 1f;
    private float frameCostAvg;
    private static final float FRAME_BUDGET = 0.017f;   // ~60fps
    private static final float FRAME_DROP = 0.023f;    // sustained miss -> halve fx
    private static final float FRAME_RECOVER = 0.015f; // sustained headroom -> restore

    private float spawnTimer;
    /** when false, no enemies spawn (used by tests and future pause menu) */
    public boolean spawningEnabled = true;
    private float formationTimer = 8f;
    private List<GameEvent> events = new ArrayList<>();
    private List<GameEvent> eventsOut = new ArrayList<>();

    public GameWorld(long seed) {
        this(seed, false);
    }

    /** @param startInMenu true for the real app flow; tests use false to start playing */
    public GameWorld(long seed, boolean startInMenu) {
        RNG.setSeed(seed);
        state = startInMenu ? State.MENU : State.PLAYING;
        for (int i = 0; i < bullets.length; i++) bullets[i] = new Bullet();
        for (int i = 0; i < bossLasers.length; i++) bossLasers[i] = new BossLaser();
        for (int i = 0; i < enemyBullets.length; i++) enemyBullets[i] = new EnemyBullet();
        for (int i = 0; i < enemies.length; i++) enemies[i] = new Enemy();
        for (int i = 0; i < powerups.length; i++) powerups[i] = new PowerUp();
        for (int i = 0; i < turrets.length; i++) turrets[i] = new Turret();
        for (int i = 0; i < gravityWells.length; i++) gravityWells[i] = new GravityWell();
        for (int i = 0; i < particles.length; i++) particles[i] = new Particle();
        for (int i = 0; i < stars.length; i++) {
            stars[i] = new Star();
            int layer = i % 3;
            float speed = 30f + layer * 55f;
            stars[i].init(RNG.get().nextFloat() * WIDTH, RNG.get().nextFloat() * HEIGHT,
                    1.5f + layer * 1.3f, speed, layer);
        }
        for (int i = 0; i < floatTexts.length; i++) floatTexts[i] = new FloatText();
        ship.reset(WIDTH / 2f, HEIGHT - 140f);
    }

    /** Drags the ship via touch input (relative slide, virtual coords). */
    public void moveShipTo(float x, float y) {
        if (state == State.PLAYING) ship.setTarget(x, y);
    }

    // 浮动摇杆呈现状态：由 app 层写入，Renderer 绘制反馈（不影响模拟）
    public volatile boolean joyActive;
    public volatile float joyBaseX, joyBaseY;   // 按下锚点（基座）
    public volatile float joyX, joyY;           // 当前手指位置（摇杆头朝向）
    /** 摇杆出现/消失动画进度 0..1（按下渐入、松手渐出），由 update 推进 */
    public float joyAnim;

    /** 暂停/结算时冻结飞船目标，避免恢复后继续漂移。 */
    public void clearShipInput() {
        ship.snapTarget();
        joyActive = false;
        joyAnim = 0f;
    }

    /** Steps the simulation. dt is clamped so backgrounding cannot explode physics. */
    public void update(float dt) {
        dt = Math.min(dt, 1f / 20f);
        applyPendingRngSeed();
        if (state != State.PLAYING) {
            stateTimer += dt;
            for (Star s : stars) s.update(dt * (state == State.PAUSED ? 0.1f : 0.3f), HEIGHT);
            // 结算/菜单界面让死亡爆炸与屏幕震动自然收尾；暂停则完全冻结
            if (state != State.PAUSED) {
                for (Particle p : particles) if (p.active) p.update(dt);
                if (shakeTime > 0f) {
                    shakeTime -= dt;
                    if (shakeTime <= 0f) shakeMag = 0f;
                }
                if (bombFlash > 0f) bombFlash = Math.max(0f, bombFlash - dt * 2.5f);
                if (bossFlash > 0f) bossFlash = Math.max(0f, bossFlash - dt * 2f);
            }
            // 升级提示（已是最新 / 失败）停留 4 秒后回到可点击状态
            if (updateState == UpdateState.UP_TO_DATE || updateState == UpdateState.ERROR) {
                updateMsgTimer += dt;
                if (updateMsgTimer > 4f) updateState = UpdateState.IDLE;
            } else if (updateState == UpdateState.READY) {
                // 安装器弹出后用户取消返回：6 秒后允许重新下载触发
                updateMsgTimer += dt;
                if (updateMsgTimer > 6f) {
                    updateState = UpdateState.AVAILABLE;
                    updateMsgTimer = 0f;
                }
            }
            return;
        }
        timeAlive += dt;
        float realDt = dt;   // 时停与余晖按真实时间流逝，不受 hit-stop 缩放影响
        // hit-stop：大事件后短暂慢放（残影、爆炸与电弧仍按缩放后的时间推进，视觉上就是"凝滞一瞬"）
        if (hitStopTimer > 0f) {
            hitStopTimer = Math.max(0f, hitStopTimer - dt);
            dt *= HIT_STOP_SCALE;
        }
        if (slowTimer > 0f) slowTimer = Math.max(0f, slowTimer - dt);
        ult.updateStasis(realDt);
        // 终极技时停与缓时道具叠乘：两者同时生效时取更慢的一档，不会出现"敌机比子弹快"
        float timeScale = (slowTimer > 0f ? SLOW_TIME_SCALE : 1f) * ult.timeScale();
        updateMutators(dt);
        updateDifficulty();
        // 保底投放排在常规刷新之前：池子只空出一个位置时，谁先跑谁拿到，
        // 否则新舰型会被普通刷新永久挤掉（实测后期池子常年是满的）
        updateV117Encounters(dt);
        updateSpawning(dt);
        updateRushWave(dt);
        updateGrace(dt);
        updateBoss(dt * timeScale);

        // 摇杆出现/消失动画（约 0.12 秒完成，8 倍速率逼近目标值）
        float joyTarget = joyActive ? 1f : 0f;
        if (joyAnim < joyTarget) joyAnim = Math.min(joyTarget, joyAnim + dt * 8f);
        else if (joyAnim > joyTarget) joyAnim = Math.max(joyTarget, joyAnim - dt * 8f);

        ship.update(dt, WIDTH, HEIGHT);
        // 每路武器（含常驻基础炮）都走自己的冷却，互不影响
        for (int i = 0; i < weaponCd.length; i++) {
            if (weaponCd[i] > 0f) weaponCd[i] = Math.max(0f, weaponCd[i] - dt);
        }
        int bulletsBefore = activeBulletCount();
        weaponVolleyMask = 0;
        // v1.10：基础炮常驻——拾取任何武器都是"再多一路火力"，绝不会替换掉现有弹道
        volley(WeaponType.VULCAN, 1);
        for (int i = 1; i < WeaponType.values().length; i++) {
            WeaponType type = WeaponType.values()[i];
            if (!ship.isWeaponEquipped(type)) continue;
            volley(type, ship.stackOf(type));
        }
        bulletsFired += activeBulletCount() - bulletsBefore;
        updateWingman(dt);
        updateShipFx(dt);

        collectMagnetFields();
        for (Bullet b : bullets) {
            if (!b.active) continue;
            if (b.homing) steerMissile(b, dt);
            bendPlayerBullet(b, dt);
            b.update(dt);
            if (b.bounces > 0 && ricochet(b)) continue;
            if (b.y < -b.radius || b.y > HEIGHT + 60f
                    || b.x < -40f || b.x > WIDTH + 40f) b.active = false;
        }
        for (PowerUp p : powerups) {
            if (!p.active) continue;
            p.update(dt, ship.x, ship.y);
            if (p.offscreen(HEIGHT)) p.active = false;
        }
        for (Enemy e : enemies) {
            if (!e.active) continue;
            e.update(dt * timeScale, WIDTH, ship.x, ship.y);
            if (e.offscreen(HEIGHT)) {
                e.active = false;
                events.add(new GameEvent(EventType.ENEMY_ESCAPED, e.x, HEIGHT));
                continue;
            }
            tryEnemyFire(e, dt * timeScale);
        }
        updateV117Behaviours(dt * timeScale);
        updateOrbitSpheres(dt);
        updateHealers(dt);
        updateArcs(dt);
        for (EnemyBullet b : enemyBullets) {
            if (!b.active) continue;
            b.update(dt * timeScale);
            if (b.offscreen(WIDTH, HEIGHT)) b.active = false;
        }
        for (Particle p : particles) if (p.active) p.update(dt);
        floatBudget = FLOAT_BUDGET_PER_FRAME;   // 每帧重新放行少量飘字
        for (FloatText f : floatTexts) if (f.active) f.update(dt);
        for (Star s : stars) s.update(dt, HEIGHT);

        if (comboTimer > 0f) {
            comboTimer -= dt;
            if (comboTimer <= 0f) comboKills = 0;
        }
        if (x2Timer > 0f) {
            x2Timer -= dt;
            if (x2Timer < 0f) x2Timer = 0f;
        }
        if (fusionFlash > 0f) fusionFlash = Math.max(0f, fusionFlash - dt);
        if (shakeTime > 0f) {
            shakeTime -= dt;
            if (shakeTime <= 0f) shakeMag = 0f;
        }
        if (bombFlash > 0f) bombFlash = Math.max(0f, bombFlash - dt * 2.5f);
        if (bossFlash > 0f) bossFlash = Math.max(0f, bossFlash - dt * 2f);

        resolveCollisions();
        if (state == State.PLAYING && grazeCharge >= 1f && ship.overdriveTimer <= 0f) {
            activateOverdrive();
        }
        // 成就判定排在碰撞之后：本帧的击坠/擦弹/点弹都已经入账，读数是完整的
        if (state == State.PLAYING) updateAchievements(dt);
        if (score - pacingExemptScore > wave * 500) wave++;
    }

    // ---- boss battle ----

    private void updateBoss(float dt) {
        if (!boss.active) {
            for (BossLaser l : bossLasers) l.active = false;
            if (bossGapTimer > 0f) bossGapTimer = Math.max(0f, bossGapTimer - dt);
            // trigger at wave milestones when the screen is calm-ish
            // BOSS RUSH：不看 wave，喘息一结束下一位立刻入场（连战没有普通节拍）
            boolean due = bossRush() ? bossGapTimer <= 0f
                    : wave >= nextBossWave && bossGapTimer <= 0f;
            if (due) {
                // 从"当前节拍"跳档，而不是在旧档位上加固定步长：wave 由分数驱动，
                // 高分玩家一场 Boss 拖几十秒就能攒够下一场的额度，只加步长会让下一场
                // 在上一场尸体凉透前立刻接上（实测后期 endless 会退化成无间断 Boss 链，
                // 普通刷新、突袭波和新舰型全被挤掉）。BOSS_MIN_GAP 再兜一层，留出喘息。
                if (!bossRush()) nextBossWave = wave + (mode == GameMode.ENDLESS ? 3 : 4);
                boss.init(bossesDefeated);
                bossActive = true;
                events.add(new GameEvent(EventType.BOSS_SPAWNED, boss.x, boss.y));
            }
            return;
        }
        int before = boss.phase;
        boss.update(dt);
        if (boss.phase != before) {
            events.add(new GameEvent(EventType.BOSS_PHASE, boss.x, boss.y, boss.phase));
            addShake(0.3f, 8f);
        }
        updateBossParts(dt);
        updateBossLasers(dt);
        updateBossRam(dt);
        if (boss.readyToAttack()) {
            bossAttack();
            boss.resetAttack();
        }
        if (boss.readyToSummon()) {
            summonMinions();
            boss.resetSummon();
        }
        if (boss.readyToRam()) {
            boss.startRam();
            addShake(0.4f, 14f);
            events.add(new GameEvent(EventType.BOSS_RAM, boss.x, boss.y));
        }
        if (boss.readyToSummonPrism()) {
            int made = boss.summonPrisms();
            if (made > 0) {
                addShake(0.5f, 16f);
                events.add(new GameEvent(EventType.BOSS_PRISM, boss.x, boss.y, made));
            }
        }
        if (boss.hp <= 0) {
            boss.active = false;
            bossActive = false;
            // 打完立刻上表：下一场至少等这段喘息，别让高分把 Boss 战连成一片
            bossGapTimer = bossRush() ? BOSS_RUSH_GAP
                    : mode == GameMode.ENDLESS ? BOSS_MIN_GAP : BOSS_MIN_GAP * 1.5f;
            bossesDefeated++;
            if (bossRush()) {
                bossRushKills++;
                // 越深入赏金越高：第 n 个 Boss 额外 150×n，刷分靠稳而不是靠苟
                addScore(150 * bossRushKills, SCORE_BOSS);
            }
            ultCharge(ULT_GAIN_BOSS, boss.x, boss.y);
            for (BossLaser l : bossLasers) l.active = false;
            for (int i = 0; i < boss.partCount; i++) {
                BossPart p = boss.parts[i];
                if (p.active) {
                    p.active = false;
                    explode(p.worldX(boss), p.worldY(boss), 2);
                }
            }
            addScore(500 + boss.level * 250, SCORE_BOSS);
            explode(boss.x, boss.y, 2);
            explode(boss.x - boss.radius * 0.7f, boss.y + 30f, 3);
            explode(boss.x + boss.radius * 0.7f, boss.y - 20f, 0);
            addShake(0.8f, 26f);
            addHitStop(0.13f);
            bossFlash = 1f;
            recordBossInCodex(boss.tier);
            // breathing room before regular spawns resume
            spawnTimer = 2.5f;
            formationTimer = Math.max(formationTimer, 5f);
            // reward: guaranteed pickups（武器叠加后多给一发武器）
            spawnPowerUp(PowerUp.Kind.SHIELD, boss.x - 80f, boss.y);
            spawnPowerUp(PowerUp.Kind.BOMB, boss.x + 80f, boss.y);
            spawnPowerUp(PowerUp.Kind.POWER, boss.x, boss.y + 40f);
            spawnPowerUp(dropWeaponKind(), boss.x, boss.y - 10f);
            // 后期 Boss 额外掉落新机制道具，鼓励玩家尝试轨道球/连锁闪电
            if (boss.tier >= 1) {
                spawnPowerUp(RNG.get().nextBoolean()
                        ? PowerUp.Kind.ORBIT : PowerUp.Kind.CHAIN, boss.x, boss.y + 80f);
            }
            events.add(new GameEvent(EventType.BOSS_DEFEATED, boss.x, boss.y));
            // v1.10：击败 Boss 随机继承一种 Boss 能力（圣物）
            grantBossRelic();
        }
    }

    public boolean bossActive;

    /** Boss 部件开火：炮塔打瞄准弹，激光部件进入预警->光束流程。 */
    private void updateBossParts(float dt) {
        if (boss.y < 60f || boss.staggerTimer > 0f) return;
        for (int i = 0; i < boss.partCount; i++) {
            BossPart p = boss.parts[i];
            if (!p.active || p.fireCd > 0f) continue;
            float px = p.worldX(boss), py = p.worldY(boss);
            if (p.kind == BossPart.Kind.SHIELD) continue;   // 只减伤，不开火
            if (p.kind == BossPart.Kind.POD) {              // 分裂舱：投放僚机
                spawnPodMinions(p);
                p.fireCd = 5.4f - 0.35f * boss.level + RNG.get().nextFloat() * 0.8f;
                continue;
            }
            if (p.kind == BossPart.Kind.PRISM) {            // 棱镜：四向十字光束
                float base = boss.age * 0.8f;
                int made = 0;
                for (int k = 0; k < 4; k++) {
                    BossLaser l = freeBossLaser();
                    if (l == null) break;
                    l.init(px, py, base + k * (float) (Math.PI / 2), 0.7f, 0.28f);
                    l.fixedAim = true;
                    made++;
                }
                p.fireCd = made > 0 ? 4.4f - boss.phase * 0.25f : 0.5f;
                if (made > 0) spark(px, py, 2, 4);
                continue;
            }
            float dx = ship.x - px, dy = ship.y - py;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1f) len = 1f;
            p.aim = (float) Math.atan2(dy, dx);
            if (p.kind == BossPart.Kind.GUN) {
                int shots = boss.phase >= 2 ? 2 : 1;
                for (int s = 0; s < shots; s++) {
                    EnemyBullet b = freeEnemyBullet();
                    if (b == null) break;
                    float ang = p.aim + (s - (shots - 1) / 2f) * 0.10f;
                    b.initRadius(px, py,
                            (float) Math.cos(ang) * (250f + boss.level * 10f),
                            (float) Math.sin(ang) * (250f + boss.level * 10f),
                            10f);
                }
                p.fireCd = 2.6f - boss.level * 0.08f - boss.phase * 0.12f
                        + RNG.get().nextFloat() * 0.4f;
            } else {
                BossLaser l = freeBossLaser();
                if (l == null) {
                    p.fireCd = 0.5f;
                    continue;
                }
                l.init(px, py, p.aim, 0.85f, 0.38f);
                p.fireCd = 4.6f - boss.phase * 0.35f + RNG.get().nextFloat() * 0.4f;
            }
        }
    }

    private void updateBossLasers(float dt) {
        for (BossLaser l : bossLasers) {
            if (!l.active) continue;
            if (!l.firing) {
                // 预警期间缓慢跟踪，给玩家留出横向位移的窗口；棱镜十字光束保持固定方向
                if (!l.fixedAim) {
                    float px = ship.x - l.x, py = ship.y - l.y;
                    float desired = (float) Math.atan2(py, px);
                    l.angle = lerpAngle(l.angle, desired, Math.min(1f, dt * 3.2f));
                }
            }
            boolean wasFiring = l.firing;
            l.update(dt);
            if (!wasFiring && l.firing) {
                addShake(0.16f, 6f);
                events.add(new GameEvent(EventType.BOSS_LASER, l.x, l.y));
            }
        }
    }

    private BossLaser freeBossLaser() {
        for (BossLaser l : bossLasers) if (!l.active) return l;
        return null;
    }

    /** 分裂舱被击毁前投放的僚机：自杀机为主，后期混入分裂舱。 */
    private void spawnPodMinions(BossPart pod) {
        recordEnemyInCodex(boss.tier >= 3 ? EnemyType.SPLITTER : EnemyType.SUICIDER);
        float px = pod.worldX(boss), py = pod.worldY(boss);
        int n = boss.phase >= 3 ? 3 : 2;
        EnemyType kind = boss.tier >= 3 && RNG.get().nextFloat() < 0.34f
                ? EnemyType.SPLITTER : EnemyType.SUICIDER;
        for (int i = 0; i < n; i++) {
            Enemy e = freeEnemy();
            if (e == null) return;
            e.init(kind, px + (i - (n - 1) / 2f) * 70f, py + 20f, enemySpeedScale);
            e.splitDepth = 0;
            spawnedCount++;
            spark(px, py, 3, 5);
        }
        events.add(new GameEvent(EventType.FORMATION, px, py));
    }

    /** 横冲过程中沿舰体尾部洒下弹丸，形成一条"撞过来就得躲开"的弹幕走廊。 */
    private float bossRamTrailCd;
    private void updateBossRam(float dt) {
        if (!boss.isRamming()) {
            bossRamTrailCd = 0f;
            return;
        }
        bossRamTrailCd -= dt;
        if (bossRamTrailCd > 0f) return;
        bossRamTrailCd = 0.07f;
        EnemyBullet b = freeEnemyBullet();
        if (b == null) return;
        float dirSign = boss.ramTargetX >= boss.x ? -1f : 1f;
        b.initRadius(boss.x + dirSign * boss.radius * 0.5f, boss.y + boss.hitRadius * 0.4f,
                dirSign * 90f, 240f + boss.level * 12f, 12f);
        spark(boss.x, boss.y + boss.radius * 0.4f, 2, 2);
    }

    /** 击毁 Boss 记入图鉴（解锁对应舰级）。 */
    private void recordBossInCodex(int tier) {
        if (tier > settings.codexMaxTierSlain) {
            settings.codexMaxTierSlain = tier;
            codexDirty = true;
        }
        if (tier >= 0 && tier < settings.codexBossKills.length) {
            settings.codexBossKills[tier]++;
            codexDirty = true;
        }
    }

    public boolean codexDirty;

    private int attackSeq;

    private void bossAttack() {
        int tier = boss.tier;
        attackSeq++;
        boolean ring = tier >= 1 && boss.phase >= 2 && attackSeq % 3 == 0;
        boolean net = tier >= 1 && boss.phase >= 2 && attackSeq % 4 == 0;
        switch (boss.phase) {
            case 1: // 简单扇形：随着 Boss 级别变宽
                fireFan(5 + tier * 2, 290f + tier * 18f, 0.19f);
                if (tier >= 2) fireAimed(2, 380f, 0.09f);
                break;
            case 2: // 扇形 + 双侧瞄准弹，每三次交替扩散脉冲环、每四次铺一张弹幕网
                if (net) {
                    fireNet(9 + tier, 2, 180f + tier * 8f);
                    fireAimed(2, 390f, 0.10f);
                } else if (ring) {
                    fireRing(10 + tier * 3, 235f + tier * 12f);
                    fireAimed(2, 400f, 0.12f);
                } else {
                    fireFan(5 + tier, 315f + tier * 15f, 0.17f);
                    fireAimed(3, 410f, 0.12f);
                }
                break;
            default:
                if (net) {
                    fireNet(10 + tier, tier >= 2 ? 3 : 2, 190f + tier * 8f);
                    fireAimed(3, 430f, 0.12f);
                } else if (ring && tier >= 2) {
                    fireHelix(8 + tier * 2, 310f + tier * 12f);
                    fireRing(12 + tier * 3, 270f);
                } else if (tier <= 1) {
                    fireFan(7, 330f, 0.15f);
                    fireAimed(3, 440f, 0.10f);
                } else {
                    fireHelix(8 + tier * 2, 310f + tier * 12f);
                    fireAimed(3, 450f, 0.12f);
                }
                break;
        }
    }

    /**
     * 弹幕网：横贯全屏的慢速矩阵弹，中间留一条两列宽的缺口，
     * 缺口位置每次攻击横移，逼玩家先找缝再穿过去。返回本次弹数。
     */
    int fireNet(int columns, int rows, float speed) {
        if (columns < 4) columns = 4;
        int gap = Math.floorMod(attackSeq, columns - 2) + 1;
        float margin = 54f;
        float step = (WIDTH - margin * 2f) / (columns - 1);
        float y0 = boss.y + boss.hitRadius * 0.55f;
        int made = 0;
        for (int row = 0; row < rows; row++) {
            float y = y0 - row * 96f;
            for (int col = 0; col < columns; col++) {
                if (col == gap || col == gap + 1) continue;
                EnemyBullet b = freeEnemyBullet();
                if (b == null) break;
                b.initNet(margin + col * step, y, speed);
                made++;
            }
        }
        addShake(0.22f, 7f);
        events.add(new GameEvent(EventType.BOSS_NET, boss.x, boss.y, made));
        return made;
    }

    /** 扩散脉冲环：360° 均匀弹幕，需要玩家从缝隙中穿过去。 */
    private void fireRing(int count, float speed) {
        float spin = boss.age * 1.7f;
        for (int i = 0; i < count; i++) {
            EnemyBullet b = freeEnemyBullet();
            if (b == null) return;
            float ang = spin + i * (float) (Math.PI * 2 / count);
            b.initRadius(boss.x, boss.y + boss.hitRadius * 0.2f,
                    (float) Math.cos(ang) * speed, (float) Math.sin(ang) * speed, 11f);
        }
        addShake(0.18f, 6f);
    }

    private void fireFan(int count, float speed, float step) {
        float y0 = boss.y + boss.hitRadius * 0.72f;
        for (int i = 0; i < count; i++) {
            EnemyBullet b = freeEnemyBullet();
            if (b == null) return;
            float ang = (float) (Math.PI / 2 + (i - (count - 1) / 2f) * step);
            b.initRadius(boss.x, y0,
                    (float) Math.cos(ang) * speed, (float) Math.sin(ang) * speed, 13f);
        }
    }

    private void fireAimed(int count, float speed, float spread) {
        for (int i = 0; i < count; i++) {
            EnemyBullet b = freeEnemyBullet();
            if (b == null) return;
            float side = (i % 2 == 0) ? -1f : 1f;
            float px = boss.x + side * boss.radius * 0.42f;
            float py = boss.y + boss.hitRadius * 0.45f;
            float dx = ship.x - px, dy = ship.y - py;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1f) len = 1f;
            float ang = (float) Math.atan2(dy, dx) + (i - (count - 1) / 2f) * spread;
            b.initRadius(px, py,
                    (float) Math.cos(ang) * speed, (float) Math.sin(ang) * speed, 11f);
        }
    }

    private void fireHelix(int count, float speed) {
        float spin = boss.age * 2.4f;
        float y0 = boss.y + boss.hitRadius * 0.6f;
        for (int i = 0; i < count; i++) {
            EnemyBullet b = freeEnemyBullet();
            if (b == null) return;
            float ang = (float) (Math.PI / 2 + spin + i * Math.PI * 2f / count);
            b.initRadius(boss.x, y0,
                    (float) Math.cos(ang) * speed, (float) Math.sin(ang) * speed, 12f);
        }
    }

    private static float lerpAngle(float from, float to, float t) {
        float diff = to - from;
        while (diff > Math.PI) diff -= 2f * Math.PI;
        while (diff < -Math.PI) diff += 2f * Math.PI;
        return from + diff * t;
    }

    /** Boss 击杀奖励的随机武器（只从三种特殊武器里挑）。 */
    private static PowerUp.Kind dropWeaponKind() {
        float r = RNG.get().nextFloat();
        if (r < 0.4f) return PowerUp.Kind.SPREAD;
        if (r < 0.7f) return PowerUp.Kind.LASER;
        return PowerUp.Kind.MISSILE;
    }

    /** 直接给予武器（不经过掉落物）时使用，例如突袭奖励。 */
    private static WeaponType dropWeaponType() {
        PowerUp.Kind k = dropWeaponKind();
        if (k == PowerUp.Kind.SPREAD) return WeaponType.SPREAD;
        if (k == PowerUp.Kind.LASER) return WeaponType.LASER;
        return WeaponType.MISSILE;
    }

    /** 部件受伤/摧毁：爆点、计分、掉落，全部武装被毁后 Boss 短暂失能。 */
    private void damageBossPart(BossPart p, int damage) {
        p.damage(damage);
        boss.flash = Math.max(boss.flash, 0.05f);
        if (p.hp > 0f || !p.active) return;
        p.active = false;
        p.hp = 0f;
        float px = p.worldX(boss), py = p.worldY(boss);
        int color = p.kind == BossPart.Kind.SHIELD ? 6 : p.kind == BossPart.Kind.POD ? 5
                : p.kind == BossPart.Kind.LASER ? 2 : 3;
        explode(px, py, p.kind == BossPart.Kind.LASER ? 2 : 1);
        explode(px, py, color);
        spark(px, py, 8, color);
        addShake(0.16f, 7f);
        addScore(80 + boss.level * 20 + (p.kind == BossPart.Kind.SHIELD ? 150 : 0), SCORE_PART);
        ultCharge(ULT_GAIN_BOSS_PART, px, py);
        events.add(new GameEvent(EventType.BOSS_PART_DESTROYED, px, py, p.kind.ordinal()));
        if (p.kind == BossPart.Kind.SHIELD) {
            // 护盾发生器下线：减伤消失，给一次明显的视觉与手感反馈
            bombFlash = Math.max(bombFlash, 0.45f);
            addShake(0.3f, 12f);
            addHitStop(0.07f);
        }
        if (p.kind == BossPart.Kind.POD) {
            spawnPowerUp(RNG.get().nextFloat() < 0.5f
                    ? PowerUp.Kind.ORBIT : PowerUp.Kind.CHAIN, px, py);
        }
        if (boss.partsAllDestroyed() && !boss.allPartsDown) {
            boss.disarm();
            boss.cancelRam();
            addScore(300, SCORE_PART);
            addShake(0.45f, 16f);
            addHitStop(0.1f);
            spawnPowerUp(PowerUp.Kind.POWER, boss.x, boss.y + 30f);
            events.add(new GameEvent(EventType.BOSS_DISARMED, boss.x, boss.y));
        } else if (RNG.get().nextFloat() < 0.22f) {
            spawnPowerUp(dropKind(RNG.get().nextFloat()), px, py);
        }
    }

    /** 僚机：道具激活期间从两翼交替/齐射小弹丸。 */
    private void updateWingman(float dt) {
        if (ship.wingmanTimer <= 0f) return;
        wingmanCd -= dt;
        if (wingmanCd > 0f) return;
        wingmanCd = 0.24f;
        wingmanShots++;
        for (int i = -1; i <= 1; i += 2) {
            Bullet b = freeBullet();
            if (b == null) return;
            b.initDrone(ship.x + i * 52f, ship.y + 2f, i * 30f, -1080f);
            bulletsFired++;
            if (hasRelic(Relic.ETERNAL_WING)) {
                // 永恒僚机圣物：双翼各多一发斜射
                Bullet b2 = freeBullet();
                if (b2 == null) return;
                b2.initDrone(ship.x + i * 52f, ship.y + 2f, i * 170f, -1000f);
                bulletsFired++;
            }
        }
    }

    private float wingmanCd;

    // ---- v1.9：相位冲刺 / 轨道球 / 连锁闪电 / 医疗艇 / 突袭奖励波 ----

    /** 相位冲刺：消耗一格能量，朝拖动方向瞬时位移并短暂无敌。 */
    public boolean tryDash() {
        if (state != State.PLAYING || !ship.tryDash()) return false;
        parryStreak = 0;
        for (int i = 0; i < 8; i++) {
            Particle p = freeParticle();
            float ang = RNG.get().nextFloat() * (float) Math.PI * 2f;
            p.init(ship.x, ship.y, (float) Math.cos(ang) * 150f, (float) Math.sin(ang) * 150f,
                    0.26f, 3.2f, 3);
        }
        addShake(0.1f, 4f);
        events.add(new GameEvent(EventType.DASH, ship.x, ship.y));
        return true;
    }

    /** 轨道球：环绕飞船的能量球，擦掉敌弹并撞伤敌机。 */
    private void updateOrbitSpheres(float dt) {
        if (ship.orbitTimer <= 0f) return;
        float[] pos = orbitScratch;
        for (int i = 0; i < Ship.ORBIT_SPHERES; i++) {
            if (orbitCd[i] > 0f) orbitCd[i] = Math.max(0f, orbitCd[i] - dt);
            if (!ship.orbitSphere(i, pos)) continue;
            float sx = pos[0], sy = pos[1];
            float rr = 30f;
            for (EnemyBullet b : enemyBullets) {
                if (!b.active) continue;
                float dx = b.x - sx, dy = b.y - sy;
                float rad = rr + b.radius;
                if (dx * dx + dy * dy <= rad * rad) {
                    b.active = false;
                    spark(sx, sy, 3, 3);
                }
            }
            if (orbitCd[i] > 0f) continue;
            for (Enemy e : enemies) {
                if (!e.active) continue;
                float dx = e.x - sx, dy = e.y - sy;
                float rad = rr + e.type.radius;
                if (dx * dx + dy * dy <= rad * rad) {
                    orbitCd[i] = 0.14f;
                    e.damage(2);
                    e.flash = 0.1f;
                    spark(sx, sy, 4, 3);
                    events.add(new GameEvent(EventType.ORBIT_HIT, sx, sy));
                    if (e.hp <= 0f) killEnemy(e);
                    break;
                }
            }
            if (boss.active && orbitCd[i] <= 0f) {
                for (int pi = 0; pi < boss.partCount; pi++) {
                    BossPart p = boss.parts[pi];
                    if (!p.active) continue;
                    float dx = p.worldX(boss) - sx, dy = p.worldY(boss) - sy;
                    float rad = rr + p.radius;
                    if (dx * dx + dy * dy <= rad * rad) {
                        orbitCd[i] = 0.2f;
                        spark(sx, sy, 4, 3);
                        damageBossPart(p, 2);
                        break;
                    }
                }
                float dx = boss.x - sx, dy = boss.y - sy;
                float rad = rr + boss.hitRadius;
                if (orbitCd[i] <= 0f && dx * dx + dy * dy <= rad * rad) {
                    orbitCd[i] = 0.2f;
                    boss.damage(1);
                    spark(sx, sy, 3, 3);
                }
            }
        }
    }

    /** 医疗艇：周期性为半径内的友舰回血，逼玩家优先点杀。 */
    private void updateHealers(float dt) {
        for (Enemy h : enemies) {
            if (!h.active || h.type != EnemyType.HEALER || h.healCd > 0f) continue;
            h.healCd = 1.5f;
            int healed = 0;
            for (Enemy e : enemies) {
                if (!e.active || e == h || e.hp >= e.maxHp) continue;
                float dx = e.x - h.x, dy = e.y - h.y;
                if (dx * dx + dy * dy > 260f * 260f) continue;
                e.heal(1);
                healed++;
                arc(h.x, h.y, e.x, e.y, 0.34f, 1);
            }
            if (boss.active) {
                float dx = boss.x - h.x, dy = boss.y - h.y;
                if (dx * dx + dy * dy <= 260f * 260f && boss.hp < boss.maxHp) {
                    boss.hp = Math.min(boss.maxHp, boss.hp + 6f);
                    arc(h.x, h.y, boss.x, boss.y, 0.34f, 1);
                    healed++;
                }
            }
            if (healed > 0) {
                h.healFx = 1f;
                spark(h.x, h.y, 6, 6);
                events.add(new GameEvent(EventType.HEAL_PULSE, h.x, h.y, healed));
            }
        }
    }

    /** 连锁闪电：从飞船向上最近的目标起，依次跳跃放电。 */
    private void castChainLightning() {
        float fromX = ship.x, fromY = ship.y;
        int hits = 0;
        float damage = 22f + 6f * ship.powerLevel;
        Enemy[] picked = new Enemy[6];
        for (int step = 0; step < 6; step++) {
            Enemy best = null;
            float bestD = Float.MAX_VALUE;
            for (Enemy e : enemies) {
                if (!e.active || contains(picked, e)) continue;
                float dx = e.x - fromX, dy = e.y - fromY;
                float d = dx * dx + dy * dy;
                if (d < bestD) { bestD = d; best = e; }
            }
            if (best == null) break;
            picked[hits] = best;
            arc(fromX, fromY, best.x, best.y, 0.42f, 0);
            fromX = best.x; fromY = best.y;
            best.damage(Math.round(damage));
            best.flash = 0.16f;
            spark(best.x, best.y, 5, 3);
            hits++;
            if (best.hp <= 0f) {
                killEnemy(best);
                damage *= 0.75f; // 每次跳跃后的余电衰减
            }
        }
        if (boss.active && hits < 6) {
            float tx = boss.x, ty = boss.y;
            BossPart nearest = null;
            float bestD = Float.MAX_VALUE;
            for (int i = 0; i < boss.partCount; i++) {
                BossPart p = boss.parts[i];
                if (!p.active) continue;
                float dx = p.worldX(boss) - fromX, dy = p.worldY(boss) - fromY;
                float d = dx * dx + dy * dy;
                if (d < bestD) { bestD = d; nearest = p; }
            }
            if (nearest != null) {
                tx = nearest.worldX(boss); ty = nearest.worldY(boss);
            }
            arc(fromX, fromY, tx, ty, 0.42f, 0);
            if (nearest != null) damageBossPart(nearest, Math.round(damage));
            else boss.damage(Math.round(damage * 0.5f));
            hits++;
        }
        if (hits > 0) {
            addShake(0.22f, 9f);
            bombFlash = Math.max(bombFlash, 0.35f);
            events.add(new GameEvent(EventType.CHAIN, ship.x, ship.y, hits));
        }
    }

    private static boolean contains(Enemy[] arr, Enemy e) {
        for (Enemy a : arr) if (a == e) return true;
        return false;
    }

    /** 记录一条放电/治疗弧线（渲染层按 arcAge 淡出）。kind: 0 放电，1 治疗。 */
    private void arc(float x1, float y1, float x2, float y2, float life, int kind) {
        for (int i = 0; i < MAX_ARCS; i++) {
            if (arcAge[i] > 0f) continue;
            arcX1[i] = x1; arcY1[i] = y1;
            arcX2[i] = x2; arcY2[i] = y2;
            arcAge[i] = life;
            arcKind[i] = kind;
            return;
        }
    }

    private void updateArcs(float dt) {
        for (int i = 0; i < MAX_ARCS; i++) {
            if (arcAge[i] > 0f) arcAge[i] = Math.max(0f, arcAge[i] - dt);
        }
        if (rushBanner > 0f) {
            rushBanner = Math.max(0f, rushBanner - dt);
            if (rushBanner <= 0f) rushActive = false;
        }
        if (debutBanner > 0f) debutBanner = Math.max(0f, debutBanner - dt);
    }

    /** hit-stop：只在比当前剩余更长时接管，避免小事件截断大事件。 */
    public void addHitStop(float seconds) {
        hitStopTimer = Math.max(hitStopTimer, seconds);
    }

    /**
     * 突袭奖励波：周期性从屏幕上方压进一小队敌机，中央掉一枚金色奖励。
     * 打光这波敌机才有额外分数，纯粹是"敢上去捡"的奖励。
     */
    private void updateRushWave(float dt) {
        if (!spawningEnabled || bossActive) return;
        if (bossRush()) return;   // 连战局没有小怪，只剩 Boss 与它召来的僚机
        rushTimer -= dt;
        if (rushTimer > 0f) return;
        rushTimer = mode == GameMode.ENDLESS ? 24f : 32f;
        rushesTriggered++;
        rushActive = true;
        rushBanner = 2.4f;
        float cx = 200f + RNG.get().nextFloat() * (WIDTH - 400f);
        EnemyType escort = timeAlive > 70f && RNG.get().nextFloat() < 0.5f
                ? EnemyType.SUICIDER : EnemyType.WEAVER;
        for (int i = 0; i < 6; i++) {
            Enemy e = freeEnemy();
            if (e == null) break;
            int slot = (i + 1) / 2 * (i % 2 == 0 ? 1 : -1); // 0,1,-1,2,-2
            recordEnemyInCodex(escort);
            e.init(escort, cx + slot * 120f, -60f - Math.abs(slot) * 70f, enemySpeedScale);
            spawnedCount++;
        }
        spawnPowerUp(PowerUp.Kind.BONUS, cx, -20f);
        events.add(new GameEvent(EventType.RUSH, cx, 0f));
    }

    /** 擦弹槽满：清空敌弹并进入短时火力爆发。 */
    private void activateOverdrive() {
        grazeCharge = 0f;
        ship.overdriveTimer = Ship.OVERDRIVE_DURATION
                * (hasRelic(Relic.OVERDRIVE_CORE) ? 2f : 1f);
        for (EnemyBullet b : enemyBullets) {
            if (!b.active) continue;
            b.active = false;
            spark(b.x, b.y, 3, 2);
        }
        addShake(0.35f, 14f);
        events.add(new GameEvent(EventType.OVERDRIVE, ship.x, ship.y));
    }

    /** 命中统计：每颗子弹只记一次（穿透激光不重复）。 */
    private void registerHit(Bullet b) {
        if (b.landed) return;
        b.landed = true;
        shotsHit++;
    }

    public int accuracyPct() {
        return bulletsFired <= 0 ? 0 : Math.round(100f * shotsHit / bulletsFired);
    }

    private int activeBulletCount() {
        int n = 0;
        for (Bullet b : bullets) if (b.active) n++;
        return n;
    }

    private void summonMinions() {
        int n = boss.phase >= 3 ? 3 : 2;
        for (int i = 0; i < n; i++) {
            Enemy e = freeEnemy();
            if (e == null) return;
            e.init(EnemyType.HUNTER,
                    boss.x + (i - (n - 1) / 2f) * 130f, boss.y + 60f, enemySpeedScale);
            applyMutatorToEnemy(e);
            spawnedCount++;
        }
    }

    /** Engine exhaust trail + subtle muzzle flash; cheap juice, one particle per interval. */
    private float fxTimer;
    private void updateShipFx(float dt) {
        if (!settings.particles) return;
        fxTimer -= dt;
        if (fxTimer > 0f) return;
        fxTimer = 0.045f / emissionScale; // halved trail rate in low-quality mode
        Particle p = freeParticle();
        p.init(ship.x + (RNG.get().nextFloat() - 0.5f) * 8f,
                ship.y + Ship.RADIUS * 0.9f,
                (RNG.get().nextFloat() - 0.5f) * 30f,
                140f + RNG.get().nextFloat() * 80f,
                0.28f, 4.5f, 0);
    }

    private void tryEnemyFire(Enemy e, float dt) {
        if (e.fireCd <= 0f) return;
        if (e.isPhased()) return;   // 相位期间不开火：这 0.7 秒是玩家的喘息窗口
        if (e.y < 60f || e.y > HEIGHT * 0.72f) return; // only fire while on-screen, not too close
        // 异变"铁雨"：冷却时间流逝更快，同样的开火表会变成双倍弹幕密度
        e.fireCd -= dt * fireScaleOf();
        if (e.fireCd > 0f) return;
        switch (e.type) {
            case TANK: // slow triple spread straight down
                for (int i = -1; i <= 1; i++) {
                    EnemyBullet b = freeEnemyBullet();
                    if (b == null) break;
                    b.init(e.x, e.y + e.type.radius, i * 90f, 300f);
                }
                e.fireCd = 1.6f + RNG.get().nextFloat() * 0.5f;
                break;
            case HUNTER: // aimed shot
                float dx = ship.x - e.x, dy = ship.y - e.y;
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len < 1f) len = 1f;
                EnemyBullet b = freeEnemyBullet();
                if (b != null) b.init(e.x, e.y + e.type.radius, dx / len * 380f, dy / len * 380f);
                e.fireCd = 2.2f + RNG.get().nextFloat() * 0.8f;
                break;
            case SNIPER: // 预警 0.7 秒后的高速瞄准弹
                float sx = ship.x - e.x, sy = ship.y - e.y;
                float sl = (float) Math.sqrt(sx * sx + sy * sy);
                if (sl < 1f) sl = 1f;
                float snipeSpeed = 560f + 40f * Math.min(4f, timeAlive / 60f);
                EnemyBullet sb = freeEnemyBullet();
                if (sb != null) {
                    sb.initRadius(e.x, e.y + e.type.radius,
                            sx / sl * snipeSpeed, sy / sl * snipeSpeed, 9f);
                }
                spark(e.x, e.y + e.type.radius, 4, 7);
                e.fireCd = 2.1f + RNG.get().nextFloat() * 0.9f;
                break;
            case SPLITTER: // 慢速分裂舱只丢下一颗直落弹骚扰
                EnemyBullet cb = freeEnemyBullet();
                if (cb != null) cb.initRadius(e.x, e.y + e.type.radius, 0f, 330f, 11f);
                e.fireCd = 2.6f + RNG.get().nextFloat() * 0.9f;
                break;
            case SHIELDER: // 盾卫：正面三向扇形，逼玩家绕后或用弹跳弹
                for (int i = -1; i <= 1; i++) {
                    EnemyBullet sb2 = freeEnemyBullet();
                    if (sb2 == null) break;
                    sb2.init(e.x, e.y + e.type.radius, i * 140f, 310f);
                }
                e.fireCd = 2.0f + RNG.get().nextFloat() * 0.8f;
                break;
            case MIRAGE: {
                // 伪装者现形后的警告环：六发低速均分一圈，靠走位穿出去。
                // tryEnemyFire 的上下界守卫保证它不会在贴脸时开火——
                // "捡到假道具"不该直接等于"被撞死"。
                for (int i = 0; i < MIRAGE_BURST_COUNT; i++) {
                    EnemyBullet mb = freeEnemyBullet();
                    if (mb == null) break;
                    float ang = (float) (Math.PI * 2 * i / MIRAGE_BURST_COUNT + Math.PI / 6f);
                    mb.initRadius(e.x, e.y, (float) Math.cos(ang) * MIRAGE_BURST_SPEED,
                            (float) Math.sin(ang) * MIRAGE_BURST_SPEED, 9f);
                }
                spark(e.x, e.y, 6, 11);
                e.fireCd = 3.4f + RNG.get().nextFloat() * 1.4f;
                break;
            }
            case PHASER: {
                // 相位者只在实体期开火：一对窄角瞄准弹，逼玩家在"能打"的窗口里走位；
                // 相位期完全沉默，那 0.7 秒是玩家重新占位的时间
                float px = ship.x - e.x, py = ship.y - e.y;
                float pl = (float) Math.sqrt(px * px + py * py);
                if (pl < 1f) { px = 0f; py = 1f; pl = 1f; }
                for (int i = -1; i <= 1; i += 2) {
                    EnemyBullet pb = freeEnemyBullet();
                    if (pb == null) break;
                    float ang = i * 0.13f;
                    float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
                    pb.init(e.x, e.y + e.type.radius,
                            (px / pl * ca - py / pl * sa) * 340f,
                            (px / pl * sa + py / pl * ca) * 340f);
                }
                e.fireCd = 2.6f + RNG.get().nextFloat() * 0.8f;
                break;
            }
            default:
                e.fireCd = -1f;
        }
    }

    /**
     * Difficulty ramp, tuned in three bands:
     *  0-150s  linear ramp — spawn interval 1.1s -> 0.28s, speed x1.0 -> x1.75
     *  150s+   slow creep — up to ~x2 speed and 0.24s spawns by the 5-minute mark
     * Formation cadence tightens alongside (x1.0 -> x0.8).
     *
     * ENDLESS ramps to full pressure in 90s and then creeps without a cap,
     * but never below a 0.16s spawn floor (roughly 6 spawns/sec).
     */
    private void updateDifficulty() {
        if (mode == GameMode.ENDLESS) {
            float t = Math.min(timeAlive / 90f, 1f);
            float over = Math.max(0f, timeAlive - 90f) / 240f; // uncapped creep
            spawnInterval = Math.max(0.16f, (1.0f - 0.68f * t) / (1f + 0.35f * over));
            enemySpeedScale = 1f + 0.85f * t + 0.5f * over;
            formationPeriod = Math.max(0.55f, 1.1f - 0.45f * t - 0.15f * Math.min(over, 1f));
        } else {
            float t = Math.min(timeAlive / 150f, 1f);
            float over = Math.min((timeAlive - 150f) / 300f, 1f);
            if (over < 0f) over = 0f;
            spawnInterval = Math.max(0.16f, (1.1f - 0.82f * t) / (1f + 0.15f * over));
            enemySpeedScale = 1f + 0.75f * t + 0.25f * over;
            formationPeriod = 1.15f - 0.35f * t;
        }
        enemySpeedScale *= enemySpeedPctOf() / 100f * difficultyOf().speedScale;
        // 异变波与今日规则：出怪节奏与敌机速度都是纯倍率，窗口一结束立刻回到今天的基础值
        float spawn = spawnScaleOf();
        if (spawn != 1f) {
            spawnInterval = Math.max(0.12f, spawnInterval / spawn);
        }
        float spd = speedScaleOf();
        if (spd != 1f) enemySpeedScale *= spd;
    }

    /**
     * 异变波排程：到点就掷一种异变开启窗口，窗口结束再排下一次。
     * Boss 战里不掷"铁雨"——两层弹幕密度叠在一起，屏幕就变成猜谜了。
     */
    private void updateMutators(float dt) {
        if (mutator.isActive()) {
            mutatorTimer -= dt;
            if (mutatorTimer <= 0f) clearMutator(true);
            return;
        }
        if (!mutatorsEnabled || !spawningEnabled) return;
        // 连战局每局规则一致：不掷异变，比的是走位和节奏，不是这一把摇到了什么
        if (bossRush()) return;
        if (timeAlive < mutatorNextAt) return;
        Mutator[] all = Mutator.values();
        Mutator picked = all[1 + RNG.get().nextInt(Mutator.COUNT)];
        if (bossActive && picked == Mutator.IRON_RAIN) picked = Mutator.SWARM;
        startMutator(picked);
    }

    /** 测试缝：立刻开启指定异变窗口（不受投骰时刻与 Boss 状态限制）。 */
    public void forceMutator(Mutator m) {
        startMutator(m == null ? Mutator.NONE : m);
    }

    private void startMutator(Mutator m) {
        if (!m.isActive()) {
            clearMutator(false);
            return;
        }
        mutator = m;
        mutatorTimer = MUTATOR_DURATION;
        mutatorBanner = 3.2f;
        mutatorKills = 0;
        mutatorsSeen++;
        events.add(new GameEvent(EventType.MUTATOR_START, ship.x, 300f, m.ordinal()));
    }

    /** 窗口结束：按本窗口击坠数发一笔"异变平息"奖励，再排下一次投骰。 */
    private void clearMutator(boolean reward) {
        Mutator done = mutator;
        mutator = Mutator.NONE;
        mutatorTimer = 0f;
        mutatorNextAt = timeAlive + MUTATOR_GAP;
        if (!reward || !done.isActive()) return;
        int gained = addScore(MUTATOR_CLEAR_BONUS + mutatorKills * MUTATOR_CLEAR_KILL_BONUS,
                SCORE_OTHER);
        mutatorBonus += gained;
        mutatorKills = 0;
        spawnFloatText(ship.x, ship.y - 150f, gained, FLOAT_MUTATOR, true);
        spark(ship.x, ship.y - 60f, 12, 3);
        addShake(0.18f, 6f);
        events.add(new GameEvent(EventType.MUTATOR_CLEAR, ship.x, ship.y - 150f, gained));
    }

    /** 异变波与今日规则对新登场敌机的属性修正：只动血量，速度已经走 {@link #updateDifficulty}。 */
    private void applyMutatorToEnemy(Enemy e) {
        float hp = hpScaleOf();
        if (hp == 1f) return;
        e.hp = Math.max(0.5f, e.hp * hp);
        e.maxHp = e.hp;
    }

    float spawnInterval = 1.1f;
    float enemySpeedScale = 1f;
    private float formationPeriod = 1f;

    // ---- v1.10：重生保护 / Boss 圣物 / 炮台 / 布雷与母舰 ----

    /** 每帧推进的"轻量"子系统：保护窗口、圣物被动、纳米修复、炮台、重力井、布雷与放机。 */
    private void updateGrace(float dt) {
        if (respawnGrace > 0f) respawnGrace = Math.max(0f, respawnGrace - dt);
        if (relicBanner > 0f) relicBanner = Math.max(0f, relicBanner - dt);
        if (achieveBanner > 0f) achieveBanner = Math.max(0f, achieveBanner - dt);
        if (dailyBanner > 0f) dailyBanner = Math.max(0f, dailyBanner - dt);
        if (bossRushBanner > 0f) bossRushBanner = Math.max(0f, bossRushBanner - dt);
        if (novaFlash > 0f) novaFlash = Math.max(0f, novaFlash - dt * 2.2f);
        if (breakPulse > 0f) breakPulse = Math.max(0f, breakPulse - dt * 3.2f);
        if (mutatorBanner > 0f) mutatorBanner = Math.max(0f, mutatorBanner - dt);
        if (parryFlash > 0f) parryFlash = Math.max(0f, parryFlash - dt * 4f);
        if (gravityFlash > 0f) gravityFlash = Math.max(0f, gravityFlash - dt * 2.2f);
        if (ultFlash > 0f) ultFlash = Math.max(0f, ultFlash - dt * 1.6f);
        updateRelics(dt);
        updateNano(dt);
        updateTurrets(dt);
        updateGravityWells(dt);
        updateMinersAndCarriers();
    }

    /** 圣物被动效果统一入口（光束自动开火 / 僚机续期 / 圣盾补层）。 */
    private void updateRelics(float dt) {
        if (relicMask == 0) return;
        if (hasRelic(Relic.BEAM_CORE)) {
            relicBeamCd -= dt;
            if (relicBeamCd <= 0f) {
                relicBeamCd = 1.1f;
                fireRelicBeam();
            }
        }
        if (hasRelic(Relic.ETERNAL_WING) && ship.wingmanTimer < 2f) {
            ship.wingmanTimer = Ship.WINGMAN_DURATION;   // 永恒僚机：不再到期
        }
        if (hasRelic(Relic.AEGIS_FIELD)) {
            relicAegisCd -= dt;
            if (relicAegisCd <= 0f) {
                relicAegisCd = 18f;
                if (ship.shieldLayers < ship.shieldCap) {
                    ship.addShield();
                    spark(ship.x, ship.y, 6, 5);
                }
            }
        }
    }

    /** 光束核心：一束宽穿透光束，无视盾卫的正面护盾。 */
    private void fireRelicBeam() {
        Bullet b = freeBullet();
        if (b == null) return;
        b.initBeam(ship.x, ship.y - Ship.RADIUS - 16f, 0f, -1450f, 4, 22f, 150f);
        spark(ship.x, ship.y - Ship.RADIUS - 10f, 3, 0);
    }

    /** 纳米修复：周期性自动补一层护盾（不超过当前上限）。 */
    private void updateNano(float dt) {
        if (ship.nanoTimer <= 0f) {
            nanoCd = 0f;
            return;
        }
        nanoCd -= dt;
        if (nanoCd > 0f) return;
        nanoCd = Ship.NANITE_PERIOD;
        if (ship.shieldLayers < ship.shieldCap) {
            ship.addShield();
            spark(ship.x, ship.y, 5, 5);
            events.add(new GameEvent(EventType.HEAL_PULSE, ship.x, ship.y));
        }
    }

    /** 炮台：原地三向开火、缓慢下移，被敌弹与敌机消耗耐久。 */
    private void updateTurrets(float dt) {
        for (Turret t : turrets) {
            if (!t.active) continue;
            if (!t.update(dt)) {
                retireTurret(t);
                continue;
            }
            // 敌弹命中炮台：炮台是可牺牲的掩体，替玩家吃下这条通道
            for (EnemyBullet b : enemyBullets) {
                if (!b.active) continue;
                float dx = b.x - t.x, dy = b.y - t.y;
                float rr = Turret.RADIUS + b.radius;
                if (dx * dx + dy * dy <= rr * rr) {
                    b.active = false;
                    t.damage(1f);
                    spark(b.x, b.y, 3, 2);
                }
            }
            // 敌机撞上炮台：同归于尽式消耗
            for (Enemy e : enemies) {
                if (!e.active) continue;
                float dx = e.x - t.x, dy = e.y - t.y;
                float rr = Turret.RADIUS + e.type.radius;
                if (dx * dx + dy * dy <= rr * rr) {
                    e.active = false;
                    explode(e.x, e.y, e.type.colorHint);
                    t.damage(3f);
                }
            }
            if (t.destroyed()) {
                explode(t.x, t.y, 4);
                retireTurret(t);
                addShake(0.16f, 6f);
                continue;
            }
            if (t.fireCd > 0f) continue;
            t.fireCd = Turret.FIRE_PERIOD;
            for (int i = -1; i <= 1; i++) {
                Bullet b = freeBullet();
                if (b == null) break;
                float ang = (float) (-Math.PI / 2 + i * 0.22f);
                b.initDrone(t.x, t.y - Turret.RADIUS,
                        (float) Math.cos(ang) * 920f, (float) Math.sin(ang) * 920f);
                bulletsFired++;
            }
        }
    }

    private void deployTurret(float x, float y) {
        for (Turret t : turrets) {
            if (t.active) continue;
            t.init(clamp(x, Turret.RADIUS + 20f, WIDTH - Turret.RADIUS - 20f),
                    clamp(y, Ship.RADIUS * 2f, HEIGHT - 120f));
            spark(t.x, t.y, 10, 4);
            events.add(new GameEvent(EventType.TURRET_DEPLOY, t.x, t.y));
            return;
        }
        // 已满：折算奖励分，不浪费这次拾取
        addScore(250, SCORE_PICKUP);
    }

    private void retireTurret(Turret t) {
        t.active = false;
        spark(t.x, t.y, 6, 4);
    }

    // ---- v1.15：重力井 ----

    /**
     * 重力井：拉扯影响范围内的敌弹、吞掉吸到心口的弹，吸满或到期时向上扇形喷还。
     * 在 {@link #updateGrace} 里调用，早于本帧敌弹位移与命中判定，
     * 所以被吞掉的弹不可能再打到玩家（同帧也不会出现"既被吞又命中"）。
     */
    private void updateGravityWells(float dt) {
        for (GravityWell g : gravityWells) {
            if (!g.active) continue;
            if (!g.update(dt)) {
                burstGravityWell(g);
                retireGravityWell(g);
                continue;
            }
            for (EnemyBullet b : enemyBullets) {
                if (!b.active) continue;
                float dx = g.x - b.x, dy = g.y - b.y;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d >= GravityWell.PULL_RADIUS) continue;
                // 越近拉得越猛；保留原有速度分量，弹道会弯成螺旋而不是直直掉下来
                float pull = g.pullAt(d) * dt;
                if (d > 1e-3f) {
                    b.vx += dx / d * pull;
                    b.vy += dy / d * pull;
                }
                if (d > GravityWell.ABSORB_RADIUS + b.radius) continue;
                absorbIntoWell(g, b);
                if (g.full()) {
                    burstGravityWell(g);
                    break;   // 库存清零，剩下的敌弹下一帧再吸
                }
            }
        }
    }

    /** 吞掉一颗敌弹：小额分数 + 紫色飘字，同时给一点擦弹充能（贴脸吸弹也是走位）。 */
    private void absorbIntoWell(GravityWell g, EnemyBullet b) {
        b.active = false;
        g.stored++;
        gravityAbsorbed++;
        ultCharge(ULT_GAIN_ABSORB, b.x, b.y);
        int gained = addScore(GRAVITY_ABSORB_SCORE, SCORE_OTHER);
        spark(b.x, b.y, 4, 7);
        spawnFloatText(g.x, g.y - GravityWell.RADIUS - 14f, gained, FLOAT_GRAVITY, false);
        if (ship.overdriveTimer <= 0f) {
            grazeCharge = Math.min(1f,
                    grazeCharge + GRAZE_CHARGE_PER_BULLET * ABSORB_GRAZE_FACTOR);
        }
        events.add(new GameEvent(EventType.GRAVITY_ABSORB, b.x, b.y, g.stored));
    }

    /**
     * 喷还：把库存敌弹换成玩家子弹，沿正上方 {@link #GRAVITY_BURST_SPREAD} 张角扇形散开。
     * 伤害 {@link #GRAVITY_BURST_DAMAGE} 越过点弹幕门槛，喷出去的这一波还能继续引爆敌弹。
     */
    private void burstGravityWell(GravityWell g) {
        int n = g.stored;
        if (n <= 0) return;
        g.stored = 0;
        int fired = 0;
        for (int i = 0; i < n; i++) {
            Bullet b = freeBullet();
            if (b == null) break;
            float k = n == 1 ? 0f : i / (float) (n - 1) - 0.5f;
            float ang = (float) (-Math.PI / 2 + k * GRAVITY_BURST_SPREAD);
            b.init(g.x, g.y,
                    (float) Math.cos(ang) * GRAVITY_BURST_SPEED,
                    (float) Math.sin(ang) * GRAVITY_BURST_SPEED,
                    GRAVITY_BURST_DAMAGE, false);
            b.radius = 11f;
            fired++;
        }
        bulletsFired += fired;
        if (fired == 0) return;
        gravityBursts++;
        spark(g.x, g.y, 10, 7);
        addShake(0.14f, 7f);
        events.add(new GameEvent(EventType.GRAVITY_BURST, g.x, g.y, fired));
    }

    private void deployGravityWell(float x, float y) {
        for (GravityWell g : gravityWells) {
            if (g.active) continue;
            g.init(clamp(x, GravityWell.PULL_RADIUS * 0.4f, WIDTH - GravityWell.PULL_RADIUS * 0.4f),
                    clamp(y, Ship.RADIUS * 1.6f, HEIGHT - 150f));
            gravityWellsDeployed++;
            gravityFlash = 1f;
            spark(g.x, g.y, 12, 7);
            addShake(0.12f, 5f);
            events.add(new GameEvent(EventType.GRAVITY_DEPLOY, g.x, g.y));
            return;
        }
        // 已满：折算奖励分，不浪费这次拾取（与炮台同一处理）
        addScore(250, SCORE_PICKUP);
    }

    private void retireGravityWell(GravityWell g) {
        g.active = false;
        spark(g.x, g.y, 5, 7);
    }

    // ---- v1.16：终极技能 ULT（迭代 6）----

    /**
     * 终极槽充能：击坠、擦弹、点弹、弹反、吞弹、拆部件、杀 Boss 全部往一个槽里汇。
     *
     * <p>槽满之后还在战斗不会浪费——溢出的槽量按 {@link Ultimate#OVERFLOW_SCORE}
     * 折算成分数。玩家"没空按大招"只该延后收益，不该变成损失。
     */
    private void ultCharge(float amount, float x, float y) {
        if (amount <= 0f || state != State.PLAYING) return;
        boolean wasFull = ult.full();
        float overflow = ult.add(amount * ultChargeScale());
        if (overflow > 0f) {
            ult.overflowSpent += (int) Math.ceil(overflow);
            int gained = addScore(Math.round(overflow * Ultimate.OVERFLOW_SCORE), SCORE_OTHER);
            spawnFloatText(x, y, gained, FLOAT_ULT, false);
        }
        if (!wasFull && ult.full()) {
            events.add(new GameEvent(EventType.ULT_READY, ship.x, ship.y));
        }
    }

    /** 难度档影响攒槽速度：休闲更容易体验大招，硬核更考验节奏。 */
    private float ultChargeScale() {
        Difficulty d = difficultyOf();
        if (d == Difficulty.CASUAL) return ULT_CHARGE_CASUAL;
        if (d == Difficulty.HARDCORE) return ULT_CHARGE_HARDCORE;
        return 1f;
    }

    /**
     * 释放终极技：时停 + 全屏光矛点名 + 把满屏敌弹折成自己的弹幕。
     * 档位由释放瞬间的槽量决定（34 / 67 / 100），提前放、憋满放都是合法选择。
     *
     * @return false 表示槽量不足，本次没有消耗
     */
    public boolean tryUltimate() {
        if (state != State.PLAYING) return false;
        int tier = ult.spend();
        if (tier == 0) return false;
        ult.stasisTimer = Ultimate.stasisForTier(tier);
        ultFlash = 1f;
        ship.makeInvulnerable(Ultimate.invulnForTier(tier));
        int lances = fireUltimateLances(Ultimate.lancesForTier(tier),
                Ultimate.damageForTier(tier));
        int converted = convertEnemyBullets();
        ultConverted += converted;
        addShake(0.35f + 0.12f * tier, 16f + 6f * tier);
        addHitStop(0.06f + 0.03f * tier);
        spark(ship.x, ship.y - 40f, 14, 9);
        spawnFloatText(ship.x, ship.y - 170f,
                lances + converted > 0 ? ULT_CONVERT_SCORE * converted : 0, FLOAT_ULT, true);
        events.add(new GameEvent(EventType.ULT_CAST, ship.x, ship.y, tier));
        events.add(new GameEvent(EventType.ULT_LANCE, ship.x, ship.y, lances));
        return true;
    }

    /**
     * 光矛齐射：把所有可打目标按离船距离升序排好，最近的先点名；
     * 矛数多于目标时轮转补弹，空场则朝天放空矛——保证"按下一定有画面"。
     */
    private int fireUltimateLances(int count, int damage) {
        int n = collectUltimateTargets();
        int fired = 0;
        for (int i = 0; i < count; i++) {
            Bullet b = freeBullet();
            if (b == null) break;
            float tx = n == 0 ? ship.x : ultXs[i % n];
            float ty = n == 0 ? ship.y - 600f : ultYs[i % n];
            float dx = tx - ship.x;
            float dy = ty - (ship.y - 30f);
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < 1e-3f) {
                dx = 0f;
                dy = -1f;
                d = 1f;
            }
            // 起点左右错开，24 发光矛不会叠成一根柱子
            b.init(ship.x + ((i % 5) - 2) * 26f, ship.y - 34f,
                    dx / d * ULT_LANCE_SPEED, dy / d * ULT_LANCE_SPEED, damage, true);
            b.ult = true;
            b.piercing = true;
            b.hitCooldown = 0.16f;
            b.radius = ULT_LANCE_RADIUS;
            b.len = 34f;
            bulletsFired++;
            fired++;
        }
        return fired;
    }

    /** 目标点缓冲（一次释放最多 24 发光矛，64 个槽足够覆盖全屏敌机 + Boss 部件）。 */
    private final float[] ultXs = new float[64];
    private final float[] ultYs = new float[64];

    /** 收集敌机 / Boss 部件 / Boss 核心为光矛目标，按离船距离升序排好。 */
    private int collectUltimateTargets() {
        int n = 0;
        for (Enemy e : enemies) {
            if (!e.active) continue;
            if (n == ultXs.length) break;
            ultXs[n] = e.x;
            ultYs[n] = e.y;
            n++;
        }
        if (boss.active) {
            for (int i = 0; i < boss.partCount && n < ultXs.length; i++) {
                BossPart p = boss.parts[i];
                if (!p.active) continue;
                ultXs[n] = p.worldX(boss);
                ultYs[n] = p.worldY(boss);
                n++;
            }
            if (n < ultXs.length) {
                ultXs[n] = boss.x;
                ultYs[n] = boss.y;
                n++;
            }
        }
        // 目标数不超过 64，插入排序足够而且零分配
        for (int i = 1; i < n; i++) {
            float x = ultXs[i], y = ultYs[i];
            float sx = x - ship.x, sy = y - ship.y;
            float d = sx * sx + sy * sy;
            int j = i - 1;
            while (j >= 0) {
                float jx = ultXs[j] - ship.x, jy = ultYs[j] - ship.y;
                if (jx * jx + jy * jy <= d) break;
                ultXs[j + 1] = ultXs[j];
                ultYs[j + 1] = ultYs[j];
                j--;
            }
            ultXs[j + 1] = x;
            ultYs[j + 1] = y;
        }
        return n;
    }

    /**
     * 把满屏敌弹折成友军子弹（与冲刺弹反同一条规则：折返后保证朝上、拉到正常弹速）。
     * 弹池剩余空间不够就停止转换，避免大招把子弹池占满导致后续武器哑火。
     */
    private int convertEnemyBullets() {
        int converted = 0;
        for (EnemyBullet b : enemyBullets) {
            if (!b.active) continue;
            if (freeBullet() == null) break;
            b.active = false;
            float vx = -b.vx * 1.1f;
            float vy = -b.vy * 1.1f;
            if (vy > -300f) vy = -900f;
            float sp = (float) Math.sqrt(vx * vx + vy * vy);
            if (sp < PARRY_MIN_SPEED && sp > 1e-4f) {
                float k = PARRY_MIN_SPEED / sp;
                vx *= k;
                vy *= k;
            }
            Bullet shot = freeBullet();
            if (shot == null) break;
            shot.init(b.x, b.y, vx, vy, ULT_CONVERT_DAMAGE, false);
            bulletsFired++;
            converted++;
            addScore(ULT_CONVERT_SCORE, SCORE_OTHER);
            spark(b.x, b.y, 3, 9);
        }
        return converted;
    }

    /** 布雷艇投雷、母舰放机（两者的倒计时都在 Enemy.update 里推进）。 */
    private void updateMinersAndCarriers() {        for (Enemy e : enemies) {
            if (!e.active) continue;
            if (e.type == EnemyType.MINER && e.mineCd <= 0f
                    && e.y > 120f && e.y < HEIGHT * 0.72f) {
                e.mineCd = 1.9f + RNG.get().nextFloat() * 0.8f;
                EnemyBullet m = freeEnemyBullet();
                if (m != null) {
                    m.initMine(e.x, e.y + e.type.radius * 0.7f);
                    events.add(new GameEvent(EventType.MINE, m.x, m.y));
                }
                spark(e.x, e.y + e.type.radius, 3, 8);
            }
            if (e.type == EnemyType.CARRIER && e.droneCd <= 0f && e.hoverCd > 0f) {
                e.droneCd = 2.4f + RNG.get().nextFloat() * 0.7f;
                spawnCarrierDrones(e);
            }
        }
    }

    private void spawnCarrierDrones(Enemy e) {
        int born = 0;
        for (int i = 0; i < 2; i++) {
            Enemy d = freeEnemy();
            if (d == null) break;
            d.init(EnemyType.DRONE, e.x + (i == 0 ? -54f : 54f), e.y + 34f, enemySpeedScale);
            applyMutatorToEnemy(d);
            d.splitDepth = 1;   // 子机不再触发分裂逻辑
            spawnedCount++;
            recordEnemyInCodex(EnemyType.DRONE);
            born++;
        }
        if (born == 0) return;
        spark(e.x, e.y + 30f, 5, 9);
        events.add(new GameEvent(EventType.FORMATION, e.x, e.y));
    }

    // ---- v1.17：伪装者 / 磁暴艇 / 蜂后的战场行为 ----

    /** 磁场源每帧收集一次：同屏只有两三艘，弹道偏折直接线性扫描就够，零分配。 */
    private void collectMagnetFields() {
        magnetCount = 0;
        for (Enemy e : enemies) {
            if (!e.active || !e.projectsMagnetField()) continue;
            if (magnetCount == magnetXs.length) break;
            magnetXs[magnetCount] = e.x;
            magnetYs[magnetCount] = e.y;
            magnetCount++;
        }
    }

    /**
     * 磁暴艇的弹道弯曲：把直射弹沿"远离舰体"的方向推，越靠近核心推得越狠。
     *
     * <p>追踪弹、穿透光束、融合长枪与终极光矛完全不参与——磁场该是"逼你换武器"的
     * 谜题，不该是"这局打不动"的墙。弹跳弹照旧被推，它的反弹本来就是绕后的解法。
     */
    private void bendPlayerBullet(Bullet b, float dt) {
        if (magnetCount == 0) return;
        if (b.homing || b.piercing || b.ult || b.drone) return;
        for (int i = 0; i < magnetCount; i++) {
            float dx = b.x - magnetXs[i], dy = b.y - magnetYs[i];
            float d2 = dx * dx + dy * dy;
            if (d2 > Enemy.MAGNET_RADIUS * Enemy.MAGNET_RADIUS || d2 < 1e-3f) continue;
            float d = (float) Math.sqrt(d2);
            float k = 1f - d / Enemy.MAGNET_RADIUS;
            float a = Enemy.MAGNET_BEND * k * dt / d;
            b.vx += dx * a;
            // 纵向只给一半：让弹"绕着走"而不是被按回地面
            b.vy += dy * a * 0.5f;
            if (!b.bent) { b.bent = true; bulletsBent++; }
        }
    }

    /** 伪装者现形、磁暴艇脉冲、蜂后产卵——都靠各自倒计时推进，这里统一结算。 */
    private void updateV117Behaviours(float dt) {
        for (int i = 0; i < enemies.length; i++) {
            Enemy e = enemies[i];
            if (!e.active) continue;
            if (e.type == EnemyType.MIRAGE) {
                if (!e.isCloaked()) continue;
                float dx = ship.x - e.x, dy = ship.y - e.y;
                if ((float) Math.sqrt(dx * dx + dy * dy) < Enemy.MIRAGE_TRIGGER
                        || e.age >= Enemy.MIRAGE_MAX_CLOAK) {
                    revealMirage(e);
                }
                continue;
            }
            if (e.type == EnemyType.MAGNETON) {
                if (e.empCd <= 0f && e.y > 120f && e.y < HEIGHT * 0.8f) fireMagnetBurst(e);
                continue;
            }
            if (e.type == EnemyType.QUEEN && e.broodCd <= 0f) {
                int born = queenLayBrood(i, e);
                e.broodCd = e.broodInterval();
                if (born > 0) {
                    broodSpawned += born;
                    events.add(new GameEvent(EventType.BROOD_SPAWN, e.x, e.y, born));
                }
                continue;
            }
            if (e.type == EnemyType.RAMMER && e.ramImpact) {
                // 撞停是一次性的表现事件：尘土、震屏、闷响，然后把标记交还敌机
                e.ramImpact = false;
                addShake(0.2f, 9f);
                spark(e.x, e.y, 9, 14);
                events.add(new GameEvent(EventType.RAM_IMPACT, e.x, e.y));
                continue;
            }
            if (e.type == EnemyType.PHASER && e.phaseShifted) {
                // 相位切换：音效按 code 区分"沉下去"和"浮上来"，粒子也换一层颜色
                e.phaseShifted = false;
                spark(e.x, e.y, e.phaseOut ? 8 : 6, 15);
                events.add(new GameEvent(EventType.PHASE_SHIFT, e.x, e.y,
                        e.phaseOut ? 1 : 0));
            }
        }
    }

    /**
     * v1.17 特化遭遇保底：到点检查三种新舰型谁还没上场，补一艘进来。
     * 只补"场上没有的那一型"，所以不会出现两艘蜂后互相铺幼机的场面。
     * 包级可见：测试直接拨这个计时器，绕开常规刷新的随机干扰。
     */
    void updateV117Encounters(float dt) {
        if (!spawningEnabled || bossActive) return;
        if (bossRush()) return;   // 连战局只打 Boss：保底投放会抢走 Boss 战的空间
        v117EncounterTimer -= dt;
        if (v117EncounterTimer > 0f) return;
        float t = mode == GameMode.ENDLESS ? timeAlive * 1.6f : timeAlive;
        EnemyType pick = null;
        int fewest = Integer.MAX_VALUE;
        for (int i = 0; i < V117_TYPES.length; i++) {
            if (t < V117_WINDOWS[i]) continue;
            if (typeAlive(V117_TYPES[i])) continue;
            if (v117EncounterCounts[i] < fewest) {
                fewest = v117EncounterCounts[i];
                pick = V117_TYPES[i];
            }
        }
        // 三型都已上场：这不是"投放失败"，但也没必要再等一整个周期，1.5 秒后复查
        if (pick == null) {
            v117EncounterTimer = 1.5f;
            return;
        }
        Enemy e = freeEnemy();
        // 敌机池被普通刷新占满时不能把保底推后一整轮——下一帧就再试，
        // 否则"新舰型露面"这件事会被池子挤没（实测长跑里就是这么丢的）
        if (e == null) {
            v117EncounterTimer = 0.25f;
            return;
        }
        // 计时只在"没有 Boss 的空档"里走（和突袭波同一套规矩），后期一局里这种空档
        // 只占一成时间，所以周期本身必须短——6 秒空档就该有一艘新舰露面
        v117EncounterTimer = mode == GameMode.ENDLESS ? 6f : 8f;
        boolean fresh = !settings.enemySeen(pick);
        recordEnemyInCodex(pick);
        float margin = pick.radius + 40f;
        float x = margin + RNG.get().nextFloat() * (WIDTH - margin * 2f);
        e.init(pick, x, -pick.radius - 10f, enemySpeedScale);
        applyMutatorToEnemy(e);
        spawnedCount++;
        v117Encounters++;
        for (int i = 0; i < V117_TYPES.length; i++) {
            if (V117_TYPES[i] == pick) v117EncounterCounts[i]++;
        }
        spark(x, 40f, 8, paletteOf(pick));
        // 伪装者的登场故意不播报：一旦有声音提示，玩家就能靠听识破伪装，玩法当场失效
        if (pick != EnemyType.MIRAGE) {
            events.add(new GameEvent(EventType.NEW_SHIP_DEBUT, x, 0f, pick.ordinal()));
            if (fresh) {
                debutBanner = 2.6f;
                debutBannerType = pick;
            }
        }
    }

    private boolean typeAlive(EnemyType type) {
        for (Enemy e : enemies) if (e.active && e.type == type) return true;
        return false;
    }

    /** 舰型对应的调色板索引（特效着色用）。 */
    private static int paletteOf(EnemyType type) {
        switch (type) {
            case MIRAGE: return 11;
            case MAGNETON: return 12;
            case QUEEN: return 13;
            case RAMMER: return 14;
            case PHASER: return 15;
            default: return 4;
        }
    }

    // ---- v1.18 成就 ----

    /** 把对局统计摊平成成就读数（顺序对齐 {@link Achievement.Metric}）。 */
    private void fillRunMetrics() {
        int[] m = runMetrics;
        m[Achievement.Metric.KILLS.ordinal()] = kills;
        m[Achievement.Metric.GRAZE.ordinal()] = grazes;
        m[Achievement.Metric.PARRY.ordinal()] = parries;
        m[Achievement.Metric.BREAK.ordinal()] = bulletsShotDown;
        m[Achievement.Metric.COMBO.ordinal()] = maxCombo;
        m[Achievement.Metric.TIME.ordinal()] = (int) timeAlive;
        m[Achievement.Metric.SCORE.ordinal()] = score;
        // 命中率要有样本量才算数：开局前几发全中不该白送"神射手"
        m[Achievement.Metric.ACC.ordinal()] = bulletsFired >= 200 ? accuracyPct() : 0;
        // 金身：挨过一次打就把本局读数归零，之后即使击坠更多也不会误记成"无损"
        m[Achievement.Metric.CLEAN_KILLS.ordinal()] = hitsTaken == 0 ? kills : 0;
        m[Achievement.Metric.GRAVITY.ordinal()] = gravityAbsorbed;
        m[Achievement.Metric.ULT_TIER.ordinal()] = ult.bestTier;
        m[Achievement.Metric.ULT_CONVERT.ordinal()] = ultConverted;
        m[Achievement.Metric.ULT_CASTS.ordinal()] = ult.casts;
        m[Achievement.Metric.BOSSES.ordinal()] = bossesDefeated;
        m[Achievement.Metric.RUSH.ordinal()] = rushesTriggered;
        m[Achievement.Metric.SPOT.ordinal()] = miragesSpotted;
        m[Achievement.Metric.MUTATORS.ordinal()] = mutatorsSeen;
        m[Achievement.Metric.DASH.ordinal()] = ship.dashesUsed;
        m[Achievement.Metric.RELIC.ordinal()] = relicsGained;
        m[Achievement.Metric.FUSION.ordinal()] = fusions;
        m[Achievement.Metric.MAGNET_KILLS.ordinal()] = typeKills[EnemyType.MAGNETON.ordinal()];
        m[Achievement.Metric.QUEEN_KILLS.ordinal()] = typeKills[EnemyType.QUEEN.ordinal()];
    }

    private void updateAchievements(float dt) {
        fillRunMetrics();
        achievements.update(settings, runMetrics, dt);
        // 成就账本和图鉴走同一条落盘通道：只在真的有变化时才写 SharedPreferences
        if (achievements.consumePendingSave()) codexDirty = true;
    }

    /**
     * 立刻入账。坠机、退出到菜单、暂停与开局前都会走这里，
     * 保证"打了一半就退"的那部分进度也不会丢（增量记账，重复调用无副作用）。
     */
    public void commitAchievements() {
        fillRunMetrics();
        achievements.commit(settings, runMetrics);
        if (achievements.consumePendingSave()) codexDirty = true;
    }

    /** 成就页读数：某条成就当前的判定值（单局取历史最佳，生涯取累计）。 */
    public int achievementProgress(Achievement a) {
        return Achievements.valueOf(settings, a);
    }

    public int achievementsUnlocked() {
        return Long.bitCount(settings.achieveMask);
    }

    /** 解锁播报：对局内额外发一笔奖励分，非对局状态（坠机结算）只记录不再改分。 */
    private void onAchievementUnlocked(Achievement a) {
        achievesThisRun++;
        achieveBannerType = a;
        achieveBanner = 3.0f;
        events.add(new GameEvent(EventType.ACHIEVEMENT_UNLOCKED, ship.x, ship.y, a.ordinal()));
        if (state != State.PLAYING) return;
        int gained = addScore(ACHIEVEMENT_SCORE, SCORE_OTHER);
        achieveScore += gained;
        achieveLastGain = gained;
        pacingExemptScore += gained;
        sparkFx(ship.x, ship.y - 60f, 14, 4);
        addShake(0.16f, 6f);
    }

    /**
     * 伪装者现形：脱掉道具外壳，留下 0.6 秒开火预警。
     * 现形距离（210）远大于撞击判定，玩家不会"捡个假道具就被撞死"。
     */
    private void revealMirage(Enemy e) {
        e.reveal();
        miragesRevealed++;
        spark(e.x, e.y, 9, 6);
        addShake(0.07f, 4f);
        events.add(new GameEvent(EventType.MIRAGE_REVEAL, e.x, e.y));
    }

    /** 磁暴艇的 EMP 慢环：八发低速弹均分一圈，考验"穿环"走位而不是反应速度。 */
    private void fireMagnetBurst(Enemy e) {
        e.empCd = 3.0f + RNG.get().nextFloat() * 1.0f;
        e.empFx = 1f;
        magnetBursts++;
        int fired = 0;
        float phase = RNG.get().nextFloat() * 0.4f;
        for (int i = 0; i < MAG_BULLET_COUNT; i++) {
            EnemyBullet b = freeEnemyBullet();
            if (b == null) break;
            float ang = (float) (Math.PI * 2 * (i + phase) / MAG_BULLET_COUNT);
            b.initRadius(e.x, e.y, (float) Math.cos(ang) * MAG_BULLET_SPEED,
                    (float) Math.sin(ang) * MAG_BULLET_SPEED, MAG_BULLET_RADIUS);
            fired++;
        }
        spark(e.x, e.y, 6, 12);
        events.add(new GameEvent(EventType.MAG_BURST, e.x, e.y, fired));
    }

    /**
     * 蜂后产卵：幼机在她身侧生成，归属记她的池下标，击毁她时一并崩溃。
     * 血量越低产得越快（{@link Enemy#broodInterval()}），拖久了对玩家更亏。
     */
    private int queenLayBrood(int queenIndex, Enemy queen) {
        int want = queen.hp < queen.maxHp * 0.5f ? QUEEN_BROOD_MAX : QUEEN_BROOD_MIN;
        EnemyType[] kit = {EnemyType.DRONE, EnemyType.DRONE, EnemyType.HUNTER,
                EnemyType.SUICIDER};
        int born = 0;
        for (int i = 0; i < enemies.length && born < want; i++) {
            if (enemies[i].active) continue;
            EnemyType t = kit[RNG.get().nextInt(kit.length)];
            Enemy child = enemies[i];
            child.init(t, clampX(queen.x + (born * 2 - 1) * 60f
                            + (RNG.get().nextFloat() - 0.5f) * 40f, t.radius),
                    queen.y + 40f + RNG.get().nextFloat() * 30f, enemySpeedScale);
            applyMutatorToEnemy(child);
            child.broodOf = queenIndex;
            child.splitDepth = 1;      // 幼机不再触发分裂逻辑
            spawnedCount++;
            recordEnemyInCodex(t);
            born++;
        }
        if (born > 0) spark(queen.x, queen.y + 30f, 5, 11);
        return born;
    }

    /** 击毁蜂后：她的幼机一并崩溃，玩家不必再收拾一屏幕残党。 */
    private void collapseQueenBrood(int queenIndex) {
        // 默认归属是 -1，绝不能拿它当有效下标，否则会把全场敌机一起清掉
        if (queenIndex < 0) return;
        for (Enemy e : enemies) {
            if (!e.active || e.broodOf != queenIndex) continue;
            e.broodOf = -1;
            killEnemy(e);
        }
    }

    /** 磁暴艇死亡=磁场坍缩：把周边的敌弹一起吞掉，给玩家一个正反馈。 */
    private void collapseMagnetField(Enemy e) {
        int eaten = 0;
        for (EnemyBullet b : enemyBullets) {
            if (!b.active) continue;
            float dx = b.x - e.x, dy = b.y - e.y;
            if (dx * dx + dy * dy > MAG_COLLAPSE_RADIUS * MAG_COLLAPSE_RADIUS) continue;
            b.active = false;
            spark(b.x, b.y, 2, 12);
            eaten++;
        }
        if (eaten > 0) {
            spawnFloatText(e.x, e.y - 30f, eaten * GRAVITY_ABSORB_SCORE, FLOAT_BREAK, false);
            events.add(new GameEvent(EventType.MAG_BURST, e.x, e.y, -eaten));
        }
    }

    private float clampX(float x, float pad) {
        return x < pad ? pad : (x > WIDTH - pad ? WIDTH - pad : x);
    }

    /** 敌机在池中的下标（蜂后的幼机按它归属）；找不到返回 -1。 */
    private int indexOf(Enemy e) {
        for (int i = 0; i < enemies.length; i++) {
            if (enemies[i] == e) return i;
        }
        return -1;
    }

    /** 弹跳弹撞墙反弹：镜像速度并贴回壁内，返回 true 表示本帧不因出界被回收。 */
    private boolean ricochet(Bullet b) {
        if (b.x <= 14f && b.vx < 0f) {
            b.x = 14f;
            b.vx = -b.vx;
        } else if (b.x >= WIDTH - 14f && b.vx > 0f) {
            b.x = WIDTH - 14f;
            b.vx = -b.vx;
        } else if (b.y <= 14f && b.vy < 0f) {
            b.y = 14f;
            b.vy = -b.vy;
        } else {
            return false;
        }
        b.speed = (float) Math.sqrt(b.vx * b.vx + b.vy * b.vy);
        b.bounces--;
        spark(b.x, b.y, 3, 4);
        return true;
    }

    /** 新星冲击：清光全场敌弹，并对所有敌机与 Boss 造成一次范围伤害。 */
    private void detonateNova() {
        for (EnemyBullet b : enemyBullets) {
            if (!b.active) continue;
            b.active = false;
            spark(b.x, b.y, 2, 3);
        }
        // 两段式结算：先算伤害再统一击杀，避免分裂子机被同一波新星连带击毁刷分
        int victims = 0;
        for (Enemy e : enemies) {
            if (!e.active) continue;
            e.damage((int) NOVA_DAMAGE);
            if (e.hp <= 0f && victims < novaVictims.length) novaVictims[victims++] = e;
        }
        for (int i = 0; i < victims; i++) {
            Enemy v = novaVictims[i];
            novaVictims[i] = null;
            if (v.active) killEnemy(v);
        }
        if (boss.active) {
            for (int i = 0; i < boss.partCount; i++) {
                BossPart p = boss.parts[i];
                if (p.active) damageBossPart(p, (int) NOVA_DAMAGE);
            }
            boss.damage((int) NOVA_DAMAGE);
        }
        novaFlash = 1f;
        bombFlash = Math.max(bombFlash, 0.7f);
        addShake(0.5f, 18f);
        addHitStop(0.1f);
        events.add(new GameEvent(EventType.NOVA, ship.x, ship.y));
    }

    /** 新星伤害名单的复用缓冲（避免每次拾取都分配）。 */
    private final Enemy[] novaVictims = new Enemy[enemies.length];

    /** 盾卫正面护盾是否挡下这颗子弹：只拦自下而上的直射实弹，光束/弹跳/破片可穿。 */
    private boolean blockedByShield(Enemy e, Bullet b) {
        if (!e.hasFrontShield()) return false;
        if (b.piercing || b.bounces > 0 || b.fragment) return false;
        return b.vy < 0f;
    }

    public boolean hasRelic(Relic r) {
        return r != null && (relicMask & (1 << r.ordinal())) != 0;
    }

    /** 六枚圣物是否全部集齐。 */
    public boolean relicsComplete() {
        return relicMask == (1 << Relic.COUNT) - 1;
    }

    /** 击败 Boss 掉落一枚尚未持有的圣物；集齐后折算成奖励分。 */
    private void grantBossRelic() {
        int missing = ((1 << Relic.COUNT) - 1) & ~relicMask;
        if (missing == 0) {
            addScore(800, SCORE_OTHER);
            return;
        }
        int skip = RNG.get().nextInt(Integer.bitCount(missing));
        Relic pick = null;
        for (int i = 0; i < Relic.COUNT; i++) {
            if ((missing & (1 << i)) == 0) continue;
            if (skip-- == 0) {
                pick = Relic.byOrdinal(i);
                break;
            }
        }
        applyRelic(pick);
    }

    /** 获得并激活一枚圣物（硬核难度坠机会全部失去）。 */
    private void applyRelic(Relic r) {
        if (r == null) return;
        relicMask |= 1 << r.ordinal();
        lastRelic = r;
        relicBanner = 3.4f;
        relicsGained++;
        refreshShieldCap();
        if (r == Relic.BEAM_CORE) relicBeamCd = 0.35f;
        if (r == Relic.AEGIS_FIELD) relicAegisCd = 18f;
        if (r == Relic.ETERNAL_WING) ship.wingmanTimer = Ship.WINGMAN_DURATION;
        spark(ship.x, ship.y, 14, 4);
        addShake(0.25f, 8f);
        events.add(new GameEvent(EventType.RELIC_GAINED, ship.x, ship.y, r.ordinal()));
    }

    /** 圣盾外壳会把护盾上限抬高一层，失去圣物时恢复。 */
    private void refreshShieldCap() {
        ship.shieldCap = hasRelic(Relic.AEGIS_FIELD)
                ? Ship.MAX_SHIELD_LAYERS + 1 : Ship.MAX_SHIELD_LAYERS;
        if (ship.shieldLayers > ship.shieldCap) ship.shieldLayers = ship.shieldCap;
    }

    /** 坠机惩罚：按难度决定掉什么。 */
    private void applyDeathPenalty() {
        Difficulty.Penalty p = difficultyOf().penalty;
        if (p == Difficulty.Penalty.KEEP_ALL) return;
        if (p == Difficulty.Penalty.KEEP_WEAPONS) {
            // 雷电式惩罚：坠机火力降 1 级，武器与圣物保留
            if (ship.powerLevel > 1) ship.powerLevel--;
            return;
        }
        // STRIP（硬核）：特殊武器、圣物、护盾、炮台全部清零
        for (int i = 1; i < ship.weaponTimers.length; i++) {
            ship.weaponTimers[i] = 0f;
            ship.weaponStacks[i] = 0;
        }
        ship.weapon = WeaponType.VULCAN;
        ship.weaponTimer = 0f;
        ship.powerLevel = 1;
        ship.hasShield = false;
        ship.shieldLayers = 0;
        ship.nanoTimer = 0f;
        nanoCd = 0f;
        relicMask = 0;
        relicBeamCd = 0f;
        relicAegisCd = 0f;
        refreshShieldCap();
        resetWeaponCooldowns();
        for (Turret t : turrets) if (t.active) retireTurret(t);
        for (GravityWell g : gravityWells) {
            if (!g.active) continue;
            burstGravityWell(g);
            retireGravityWell(g);
        }
        // 终极槽不清零，只保留一半：硬核的惩罚是"这一条命要更稳"，不是"前面白打了"
        ult.charge *= 0.5f;
    }

    /** 重生保护：短暂无敌 + 清掉身边敌弹，避免"落地成盒"。 */
    private void respawnSafely() {
        float secs = difficultyOf().respawnInvuln;
        ship.makeInvulnerable(secs);
        respawnGrace = secs;
        clearBulletsAround(ship.x, ship.y, difficultyOf().respawnClearRadius);
        resetWeaponCooldowns();
        events.add(new GameEvent(EventType.RESPAWN, ship.x, ship.y, (int) (secs * 10f)));
    }

    private void clearBulletsAround(float x, float y, float radius) {
        float r2 = radius * radius;
        for (EnemyBullet b : enemyBullets) {
            if (!b.active) continue;
            float dx = b.x - x, dy = b.y - y;
            if (dx * dx + dy * dy > r2) continue;
            b.active = false;
            spark(b.x, b.y, 2, 3);
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /**
     * 系统返回键的统一处理：Activity 与 View 共用这一条状态映射，
     * 避免"视图没拿到焦点 → 返回键落到系统默认行为 → 整个应用被切到后台"。
     * @return true 表示本次返回已被游戏消费，Activity 不应继续 finish()
     */
    public boolean consumeBack() {
        switch (state) {
            case PLAYING:
                pauseIfPlaying();
                return true;
            case PAUSED:
                handleUiAction(Ui.Action.RESUME);
                return true;
            case GAME_OVER:
                handleUiAction(Ui.Action.QUIT_TO_MENU);
                return true;
            default:
                if (settingsOpen || cheatOpen || codexOpen || achieveOpen) {
                    closePanels();
                    return true;
                }
                return false;   // 裸主菜单：放行，让 Activity 正常退出
        }
    }

    private void updateSpawning(float dt) {
        if (!spawningEnabled) return;
        if (bossActive) return; // the boss owns the sky
        if (bossRush()) return; // 连战局全程没有常规刷新：空档只有喘息，没有小怪
        spawnTimer -= dt;
        formationTimer -= dt;
        if (formationTimer <= 0f) {
            formationTimer = (11f + RNG.get().nextFloat() * 4f) * formationPeriod;
            spawnFormation();
        }
        if (spawnTimer <= 0f) {
            spawnTimer = spawnInterval;
            spawnEnemy();
            // late-game pressure: occasional paired spawns (sooner and more often in endless)
            double chance = mode == GameMode.ENDLESS ? 0.40f : 0.25f;
            float after = mode == GameMode.ENDLESS ? 40f : 60f;
            if (timeAlive > after && RNG.get().nextFloat() < chance) spawnEnemy();
        }
    }

    /** V-formation of five drones sweeping down the screen. */
    private void spawnFormation() {
        float cx = 240f + RNG.get().nextFloat() * (WIDTH - 480f);
        for (int i = 0; i < 5; i++) {
            Enemy e = freeEnemy();
            if (e == null) return;
            int slot = (i + 1) / 2 * (i % 2 == 0 ? 1 : -1); // 0,1,-1,2,-2
            e.init(EnemyType.DRONE, cx + slot * 110f, -60f - Math.abs(slot) * 90f, enemySpeedScale);
            applyMutatorToEnemy(e);
            spawnedCount++;
        }
        events.add(new GameEvent(EventType.FORMATION, cx, 0f));
    }

    private void spawnEnemy() {
        Enemy e = freeEnemy();
        if (e == null) return;
        spawnedCount++;
        EnemyType type = pickEnemyType();
        recordEnemyInCodex(type);
        float margin = type.radius + 30f;
        float x = margin + RNG.get().nextFloat() * (WIDTH - margin * 2);
        e.init(type, x, -type.radius - 10f, enemySpeedScale);
        applyMutatorToEnemy(e);
        maybeElite(e, type);
    }

    /**
     * 精英词缀：中后期低概率出现，厚血、3 倍分、必掉道具。
     * 炮灰无人机不当精英（一屏 5 架的编队会突然变成硬骨头，节奏会崩）。
     */
    private void maybeElite(Enemy e, EnemyType type) {
        float t = mode == GameMode.ENDLESS ? timeAlive * 1.6f : timeAlive;
        if (t < 45f || type == EnemyType.DRONE) return;
        float chance = ELITE_BASE_CHANCE * difficultyOf().eliteScale;
        if (RNG.get().nextFloat() >= chance) return;
        e.makeElite();
        spark(e.x, e.y, 5, 4);
    }

    private static final float ELITE_BASE_CHANCE = 0.055f;

    /** 敌机一旦登场就解锁图鉴条目（不要求击杀）。 */
    private void recordEnemyInCodex(EnemyType type) {
        int bit = 1 << type.ordinal();
        if ((settings.codexSeenEnemies & bit) == 0) {
            settings.codexSeenEnemies |= bit;
            codexDirty = true;
        }
    }

    /** Enemy-type mix drifts heavier as the run goes on (1.6x faster in endless). */
    private EnemyType pickEnemyType() {
        float t = mode == GameMode.ENDLESS ? timeAlive * 1.6f : timeAlive;
        float r = RNG.get().nextFloat();
        float wTank = t > 35f ? Math.min(0.20f, 0.12f + (t - 35f) / 600f) : 0f;
        float wHunter = t > 20f ? Math.min(0.32f, 0.25f + (t - 20f) / 800f) : 0f;
        float wWeaver = t > 10f ? Math.min(0.35f, 0.30f + (t - 10f) / 1000f) : 0f;
        float wSui = t > 25f ? Math.min(0.18f, 0.10f + (t - 25f) / 900f) : 0f;
        float wSplit = t > 45f ? Math.min(0.16f, 0.08f + (t - 45f) / 1000f) : 0f;
        float wHeal = t > 60f ? Math.min(0.11f, 0.06f + (t - 60f) / 1200f) : 0f;
        float wSnipe = t > 75f ? Math.min(0.13f, 0.07f + (t - 75f) / 1200f) : 0f;
        float wMiner = t > 95f ? Math.min(0.14f, 0.07f + (t - 95f) / 1400f) : 0f;
        float wShield = t > 110f ? Math.min(0.13f, 0.06f + (t - 110f) / 1500f) : 0f;
        float wCarrier = t > 135f ? Math.min(0.10f, 0.05f + (t - 135f) / 1600f) : 0f;
        // v1.17：伪装者 120s 出场（低频但常驻，靠"眼力"取胜）；磁暴艇 150s；
        // 蜂后 180s 且权重封顶 5%——她是优先目标，不该满屏都是
        float wMirage = t > 115f ? Math.min(0.055f, 0.025f + (t - 115f) / 2600f) : 0f;
        float wMagnet = t > 150f ? Math.min(0.10f, 0.05f + (t - 150f) / 1700f) : 0f;
        float wQueen = t > 180f ? 0.05f : 0f;
        // v1.21：冲撞者 125s 解锁——她不是"多一种靶子"，而是要求玩家在预警线亮起时
        // 主动让位；权重压低，避免满屏都是需要躲的俯冲
        float wRammer = t > 125f ? Math.min(0.08f, 0.04f + (t - 125f) / 1800f) : 0f;
        // v1.22：相位者 145s 解锁——她不会加速、不会追人，只要求玩家读懂"现在打不到"
        float wPhaser = t > 145f ? Math.min(0.07f, 0.035f + (t - 145f) / 2000f) : 0f;
        // 保留至少 15% 无人机当"炮灰"：非无人机权重整体归一
        float sum = wTank + wHunter + wWeaver + wSui + wSplit + wHeal + wSnipe
                + wMiner + wShield + wCarrier + wMirage + wMagnet + wQueen + wRammer
                + wPhaser;
        if (sum > 0.85f) {
            float k = 0.85f / sum;
            wTank *= k; wHunter *= k; wWeaver *= k;
            wSui *= k; wSplit *= k; wHeal *= k; wSnipe *= k;
            wMiner *= k; wShield *= k; wCarrier *= k;
            wMirage *= k; wMagnet *= k; wQueen *= k; wRammer *= k; wPhaser *= k;
        }
        float c = 0f;
        if (r < (c += wTank)) return EnemyType.TANK;
        if (r < (c += wHunter)) return EnemyType.HUNTER;
        if (r < (c += wWeaver)) return EnemyType.WEAVER;
        if (r < (c += wSui)) return EnemyType.SUICIDER;
        if (r < (c += wSplit)) return EnemyType.SPLITTER;
        if (r < (c += wHeal)) return EnemyType.HEALER;
        if (r < (c += wSnipe)) return EnemyType.SNIPER;
        if (r < (c += wMiner)) return EnemyType.MINER;
        if (r < (c += wShield)) return EnemyType.SHIELDER;
        if (r < (c += wCarrier)) return EnemyType.CARRIER;
        if (r < (c += wMirage)) return EnemyType.MIRAGE;
        if (r < (c += wMagnet)) return EnemyType.MAGNETON;
        if (r < (c += wQueen)) return EnemyType.QUEEN;
        if (r < (c += wRammer)) return EnemyType.RAMMER;
        if (r < (c += wPhaser)) return EnemyType.PHASER;
        return EnemyType.DRONE;
    }

    /** 清零所有武器冷却：重生后立刻能开火，测试也用它隔离上一轮弹道。 */
    public void resetWeaponCooldowns() {
        for (int i = 0; i < weaponCd.length; i++) weaponCd[i] = 0f;
    }

    /** 按各自冷却开一路武器；冷却未到则本帧静默（基础炮与所有特殊武器共用这条路径）。 */
    private void volley(WeaponType type, int stack) {
        int i = type.ordinal();
        if (weaponCd[i] > 0f) return;
        fireWeapon(type, stack);
        weaponCd[i] = type.cooldown
                * (ship.rapidTimer > 0f ? 0.5f : 1f)
                * (ship.overdriveTimer > 0f ? Ship.OVERDRIVE_FIRE_MULT : 1f);
    }

    /** 弹道生成：武器类型 × 火力等级 × 同类叠加层数共同决定数量与形状。 */
    private void fireWeapon(WeaponType type, int stack) {
        shotsFired++;
        weaponVolleyMask |= 1 << type.ordinal();
        // 火力等级（永久 POWER）+ OVERDRIVE 临时等级 + 同类叠层（每层约等于半级）
        int lvl = ship.powerLevel
                + (ship.overdriveTimer > 0f ? 1 + (hasRelic(Relic.OVERDRIVE_CORE) ? 1 : 0) : 0)
                + Math.max(0, stack - 1);
        switch (type) {
            case SPREAD: {
                // 叠层/升级会继续加宽扇形，最多 9 / 11 向
                int rays = Math.min(6, 1 + lvl);
                for (int i = -(rays - 1); i <= rays - 1; i++) {
                    Bullet b = freeBullet();
                    if (b == null) return;
                    float ang = (float) (-Math.PI / 2 + i * 0.20);
                    b.init(ship.x, ship.y - Ship.RADIUS - 6f,
                            (float) Math.cos(ang) * 1150f, (float) Math.sin(ang) * 1150f,
                            WeaponType.SPREAD.damage, false);
                }
                break;
            }
            case LASER: {
                // 束数随火力/叠层增长，最多 5 束（全部穿透）
                int beams = Math.min(5, lvl);
                for (int i = 0; i < beams; i++) {
                    Bullet b = freeBullet();
                    if (b == null) return;
                    float off = (i - (beams - 1) / 2f) * 24f;
                    b.initLaser(ship.x + off, ship.y - Ship.RADIUS - 10f,
                            0f, -1500f, WeaponType.LASER.damage);
                }
                break;
            }
            case MISSILE: {
                // 2..6 发追踪；叠层优先加两侧弹道，奇数时补一发中路
                int total = Math.min(6, lvl + 1);
                int pairs = total / 2;
                if (total % 2 == 1) {
                    Bullet b = freeBullet();
                    if (b == null) return;
                    b.init(ship.x, ship.y - Ship.RADIUS - 6f, 0f, -700f,
                            WeaponType.MISSILE.damage, true);
                }
                for (int p = 1; p <= pairs; p++) {
                    for (int side = -1; side <= 1; side += 2) {
                        Bullet b = freeBullet();
                        if (b == null) return;
                        b.init(ship.x + side * (18f + (p - 1) * 20f),
                                ship.y - Ship.RADIUS - 6f,
                                side * (220f + (p - 1) * 90f), -640f,
                                WeaponType.MISSILE.damage, true);
                    }
                }
                break;
            }
            case LANCE: {
                // 融合·贯穿长枪：一记宽光束，层级越高束数越多
                int beams = Math.min(3, Math.max(1, lvl - 1));
                for (int i = 0; i < beams; i++) {
                    Bullet b = freeBullet();
                    if (b == null) return;
                    float off = (i - (beams - 1) / 2f) * 42f;
                    b.initBeam(ship.x + off, ship.y - Ship.RADIUS - 12f,
                            0f, -1350f, WeaponType.LANCE.damage, 20f, 130f);
                }
                break;
            }
            case CLUSTER: {
                // 融合·集束巢：命中后炸开破片
                int pods = Math.min(4, 2 + Math.max(0, lvl - 2) + Math.max(0, stack - 1));
                for (int i = 0; i < pods; i++) {
                    Bullet b = freeBullet();
                    if (b == null) return;
                    float ang = (float) (-Math.PI / 2 + (i - (pods - 1) / 2f) * 0.26);
                    b.initCluster(ship.x, ship.y - Ship.RADIUS - 8f,
                            (float) Math.cos(ang) * 980f, (float) Math.sin(ang) * 980f,
                            WeaponType.CLUSTER.damage);
                }
                break;
            }
            case SWARM: {
                // 融合·蜂群：追踪 + 穿透的细束
                int darts = Math.min(4, 1 + lvl / 2 + Math.max(0, stack - 1) * 2);
                for (int i = 0; i < darts; i++) {
                    Bullet b = freeBullet();
                    if (b == null) return;
                    float ang = (float) (-Math.PI / 2 + (i - (darts - 1) / 2f) * 0.5);
                    b.initBeam(ship.x, ship.y - Ship.RADIUS - 6f,
                            (float) Math.cos(ang) * 900f, (float) Math.sin(ang) * 900f,
                            WeaponType.SWARM.damage, 6f, 30f);
                    b.homing = true;
                    b.speed = (float) Math.sqrt(b.vx * b.vx + b.vy * b.vy);
                }
                break;
            }
            case BOUNCE: {
                // 弹跳弹：撞墙反弹最多 3 次，专打盾卫这类正面怪物
                int balls = Math.min(5, 1 + lvl / 2 + Math.max(0, stack - 1));
                for (int i = 0; i < balls; i++) {
                    Bullet b = freeBullet();
                    if (b == null) return;
                    float ang = (float) (-Math.PI / 2 + (i - (balls - 1) / 2f) * 0.34f);
                    b.init(ship.x, ship.y - Ship.RADIUS - 8f,
                            (float) Math.cos(ang) * 1000f, (float) Math.sin(ang) * 1000f,
                            WeaponType.BOUNCE.damage, false);
                    b.bounces = MAX_BOUNCES;
                    b.radius = 10f;
                }
                break;
            }
            case ARC: {
                // 电弧炮：慢速电浆球，命中后向附近目标跳跃放电；叠层同时加弹数与跳数
                int orbs = Math.min(3, 1 + Math.max(0, stack - 1)
                        + Math.max(0, (lvl - 2) / 2));
                int jumps = Math.min(4, 1 + lvl / 2 + Math.max(0, stack - 1));
                for (int i = 0; i < orbs; i++) {
                    Bullet b = freeBullet();
                    if (b == null) return;
                    float off = (i - (orbs - 1) / 2f) * 26f;
                    b.initArc(ship.x + off, ship.y - Ship.RADIUS - 8f,
                            0f, -980f, WeaponType.ARC.damage, jumps);
                }
                break;
            }
            default: {
                // VULCAN：单发 / 双发平行 / 三向
                int vulcanLvl = Math.min(Ship.MAX_POWER, lvl);
                if (vulcanLvl == 1) {
                    Bullet b = freeBullet();
                    if (b == null) return;
                    b.init(ship.x, ship.y - Ship.RADIUS - 6f, -1250f);
                } else {
                    for (int i = -1; i <= 1; i += 2) {
                        Bullet b = freeBullet();
                        if (b == null) return;
                        b.init(ship.x + i * 16f, ship.y - Ship.RADIUS - 6f, -1250f);
                    }
                    if (vulcanLvl >= 3) {
                        for (int i = -1; i <= 1; i += 2) {
                            Bullet b = freeBullet();
                            if (b == null) return;
                            float ang = (float) (-Math.PI / 2 + i * 0.13);
                            b.init(ship.x, ship.y - Ship.RADIUS - 6f,
                                    (float) Math.cos(ang) * 1150f, (float) Math.sin(ang) * 1150f,
                                    1, false);
                        }
                    }
                }
                if (hasRelic(Relic.MIRROR_LANCE)) {
                    // 镜像枪圣物：基础炮追加两路 ±25° 的镜像弹
                    for (int i = -1; i <= 1; i += 2) {
                        Bullet b = freeBullet();
                        if (b == null) return;
                        float ang = (float) (-Math.PI / 2 + i * 0.44f);
                        b.init(ship.x, ship.y - Ship.RADIUS - 6f,
                                (float) Math.cos(ang) * 1150f, (float) Math.sin(ang) * 1150f,
                                1, false);
                    }
                }
            }
        }
    }

    /** 集束弹命中后的破片：5 枚小弹四散，造成范围压制但不连锁爆裂。 */
    private void burstFragments(float x, float y) {
        for (int i = 0; i < 5; i++) {
            Bullet f = freeBullet();
            if (f == null) return;
            float ang = (float) (Math.PI * 2 * i / 5f + RNG.get().nextFloat() * 0.3f);
            f.init(x, y, (float) Math.cos(ang) * 520f, (float) Math.sin(ang) * 520f, 1, false);
            f.fragment = true;
            f.radius = 6f;
        }
        spark(x, y, 5, 1);
    }

    /**
     * 电弧炮连锁：从弹着点向 260 半径内最近的敌机 / Boss 部件跳跃，
     * 每跳造成 1 点伤害并在视觉层留一道电弧。主目标由 exclude 排除，
     * 相位中的敌机不参与（打得中才叫连锁）。
     */
    private void chainArc(float fromX, float fromY, int jumps, Enemy exclude) {
        Enemy[] picked = new Enemy[8];
        BossPart[] pickedParts = new BossPart[8];
        int n = 0, np = 0, chained = 0;
        for (int step = 0; step < jumps && step < picked.length; step++) {
            Enemy best = null;
            float bestD = 260f * 260f;
            for (Enemy e : enemies) {
                if (!e.active || e == exclude || e.isPhased()
                        || contains(picked, e)) continue;
                float dx = e.x - fromX, dy = e.y - fromY;
                float d = dx * dx + dy * dy;
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
            BossPart bestPart = null;
            if (boss.active) {
                for (int i = 0; i < boss.partCount; i++) {
                    BossPart p = boss.parts[i];
                    if (!p.active || containsPart(pickedParts, p)) continue;
                    float dx = p.worldX(boss) - fromX, dy = p.worldY(boss) - fromY;
                    float d = dx * dx + dy * dy;
                    if (d < bestD) {
                        bestD = d;
                        bestPart = p;
                    }
                }
            }
            if (best == null && bestPart == null) break;
            if (best != null) {
                arc(fromX, fromY, best.x, best.y, 0.24f, 2);
                best.damage(1);
                best.flash = Math.max(best.flash, 0.10f);
                spark(best.x, best.y, 4, 3);
                picked[n++] = best;
                fromX = best.x;
                fromY = best.y;
                if (best.hp <= 0f) killEnemy(best);
            } else {
                float px = bestPart.worldX(boss), py = bestPart.worldY(boss);
                arc(fromX, fromY, px, py, 0.24f, 2);
                damageBossPart(bestPart, 1);
                spark(px, py, 4, 3);
                pickedParts[np++] = bestPart;
                fromX = px;
                fromY = py;
            }
            chained++;
        }
        if (chained > 0) {
            addShake(0.05f, 2f);
            events.add(new GameEvent(EventType.ARC_CHAIN, fromX, fromY, chained));
        }
    }

    private static boolean containsPart(BossPart[] arr, BossPart p) {
        for (BossPart a : arr) if (a == p) return true;
        return false;
    }

    /** Homing missiles curve toward the closest enemy. */
    private void steerMissile(Bullet b, float dt) {
        Enemy target = null;
        float best = Float.MAX_VALUE;
        for (Enemy e : enemies) {
            if (!e.active) continue;
            float dx = e.x - b.x, dy = e.y - b.y;
            float d = dx * dx + dy * dy;
            if (d < best) { best = d; target = e; }
        }
        if (target == null) return;
        float desired = (float) Math.atan2(target.y - b.y, target.x - b.x);
        float cur = (float) Math.atan2(b.vy, b.vx);
        float diff = desired - cur;
        while (diff > Math.PI) diff -= 2f * Math.PI;
        while (diff < -Math.PI) diff += 2f * Math.PI;
        float turn = 6.5f * dt; // 每秒 6.5 弧度，帧率无关
        cur += Math.max(-turn, Math.min(turn, diff));
        b.vx = (float) Math.cos(cur) * b.speed;
        b.vy = (float) Math.sin(cur) * b.speed;
    }

    private void resolveCollisions() {
        // boss: hull and laser beams vs ship
        if (boss.active && !ship.isSafe()) {
            float dx = ship.x - boss.x, dy = ship.y - boss.y;
            float rr = Ship.RADIUS + boss.hitRadius;
            if (dx * dx + dy * dy <= rr * rr) {
                onPlayerHit();
            }
        }
        if (!ship.isSafe()) {
            for (BossLaser l : bossLasers) {
                if (!l.active || !l.firing || l.hitThisShot) continue;
                float d = distToSegment(ship.x, ship.y,
                        l.x, l.y, l.endX(), l.endY());
                if (d <= Ship.RADIUS * 0.55f + 10f) {
                    l.hitThisShot = true;
                    spark(ship.x, ship.y, 6, 2);
                    onPlayerHit();
                    break;
                }
            }
        }
        // bullets vs destructible parts first, then the exposed core
        if (boss.active) {
            for (Bullet b : bullets) {
                if (!b.active || b.hitCooldown > 0f) continue;
                boolean hitPart = false;
                for (int i = 0; i < boss.partCount; i++) {
                    BossPart p = boss.parts[i];
                    if (!p.active) continue;
                    float dx = b.x - p.worldX(boss), dy = b.y - p.worldY(boss);
                    float rr = b.radius + p.radius;
                    if (dx * dx + dy * dy <= rr * rr) {
                        if (b.piercing) b.hitCooldown = 0.06f;
                        else b.active = false;
                        registerHit(b);
                        spark(b.x, b.y, 3, 2);
                        if (b.cluster) burstFragments(b.x, b.y);
                        if (b.arc && b.arcJumps > 0) chainArc(b.x, b.y, b.arcJumps, null);
                        int dealt = damageOrCrit(b.damage);
                        if (dealt != b.damage
                                || dealt >= 3) spawnFloatText(b.x, b.y, dealt,
                                dealt != b.damage ? FLOAT_CRIT : FLOAT_DAMAGE, dealt != b.damage);
                        damageBossPart(p, dealt);
                        hitPart = true;
                        break;
                    }
                }
                if (hitPart || !b.active) continue;
                float dx = b.x - boss.x, dy = b.y - boss.y;
                float rr = b.radius + boss.hitRadius;
                if (dx * dx + dy * dy <= rr * rr) {
                    if (b.piercing) b.hitCooldown = 0.06f; // 激光穿透，冷却防连击
                    else b.active = false;
                    registerHit(b);
                    if (b.cluster) burstFragments(b.x, b.y);
                    if (b.arc && b.arcJumps > 0) chainArc(b.x, b.y, b.arcJumps, null);
                    int dealt = damageOrCrit(b.damage);
                    boss.damage(dealt);
                    if (dealt != b.damage) {
                        spawnFloatText(b.x, b.y, dealt, FLOAT_CRIT, true);
                        events.add(new GameEvent(EventType.CRIT, b.x, b.y, dealt));
                    }
                    spark(b.x, b.y, 2, 2);
                    if (boss.hp <= 0) break;
                }
            }
        }
        for (Enemy e : enemies) {
            if (!e.active) continue;
            // 相位态：子弹原样穿过（不消耗、不结算），也不与飞船碰撞——
            // "打不到"不等于"白打"，火力会在实体化落点上继续生效
            if (e.isPhased()) continue;
            // bullets vs enemies（穿透激光命中后继续飞行）
            for (Bullet b : bullets) {
                if (!b.active || b.hitCooldown > 0f) continue;
                float dx = b.x - e.x, dy = b.y - e.y;
                float rr = b.radius + e.type.radius;
                if (dx * dx + dy * dy <= rr * rr) {
                    if (blockedByShield(e, b)) {
                        // 盾卫正面挡弹：只消耗护盾耐久，舰体无伤
                        b.active = false;
                        registerHit(b);
                        e.chipShield(b.damage);
                        spark(b.x, b.y, 4, 10);
                        events.add(new GameEvent(EventType.SHIELD_BLOCK, b.x, b.y,
                                e.shieldHp <= 0f ? 1 : 0));
                        if (e.shieldHp <= 0f) {
                            addShake(0.14f, 6f);
                            spark(e.x, e.y + e.type.radius, 10, 10);
                        }
                        continue;
                    }
                    if (b.piercing) b.hitCooldown = 0.06f;
                    else b.active = false;
                    registerHit(b);
                    int dealt = damageOrCrit(b.damage);
                    boolean crit = dealt != b.damage;
                    e.damage(dealt);
                    if (crit || dealt >= 3) {
                        spawnFloatText(b.x, b.y, dealt, crit ? FLOAT_CRIT : FLOAT_DAMAGE, crit);
                        if (crit) {
                            spark(b.x, b.y, 6, 0);
                            events.add(new GameEvent(EventType.CRIT, b.x, b.y, dealt));
                        }
                    }
                    spark(b.x, b.y, 3, 0);
                    if (b.cluster) burstFragments(b.x, b.y);
                    if (b.arc && b.arcJumps > 0) chainArc(b.x, b.y, b.arcJumps, e);
                    if (e.hp <= 0) {
                        killEnemy(e);
                        if (b.piercing) continue; // 激光穿过残骸继续命中后面的敌机
                        break;
                    }
                }
            }
            if (!e.active) continue;
            // enemies vs ship（扫掠：高速拖动时不会瞬移穿过敌机）
            if (!ship.isSafe()) {
                float rr = Ship.RADIUS + e.type.radius;
                if (distToSegment(e.x, e.y,
                        ship.prevX, ship.prevY, ship.x, ship.y) <= rr) {
                    e.active = false;
                    explode(e.x, e.y, e.type.colorHint);
                    explode(ship.x, ship.y, 3);
                    onPlayerHit();
                }
            }
        }
        // v1.13：冲刺中的贴身敌弹会被弹反成友军火力（冲刺本就无敌，躲掉零收益，这里把"躲"变成"打"）
        if (ship.dashTimer > 0f) parryEnemyBullets();
        // v1.12：重弹可以打爆敌弹（点弹幕），代价是这发子弹不再造成伤害
        shootDownEnemyBullets();
        // enemy bullets vs ship
        if (!ship.isSafe()) {
            for (EnemyBullet b : enemyBullets) {
                if (!b.active) continue;
                float rr = Ship.RADIUS + b.radius;
                if (distToSegment(b.x, b.y,
                        ship.prevX, ship.prevY, ship.x, ship.y) <= rr) {
                    // 反射护罩优先于护盾：把这一发直接翻面，层数不消耗（只进充能）
                    if (ship.consumeReflect()) {
                        reflectEnemyBullet(b);
                        continue;
                    }
                    b.active = false;
                    spark(b.x, b.y, 6, 2);
                    onPlayerHit();
                    break;
                }
            }
        }
        // 擦弹：同一颗敌弹只充能一次；命中判定优先，过近直接算命中
        boolean grazedThisFrame = false;
        for (EnemyBullet b : enemyBullets) {
            if (!b.active || b.grazed) continue;
            float rr = Ship.RADIUS + b.radius;
            float d = distToSegment(b.x, b.y, ship.prevX, ship.prevY, ship.x, ship.y);
            if (d > rr && d <= rr + GRAZE_BAND) {
                b.grazed = true;
                grazes++;
                ultCharge(ULT_GAIN_GRAZE, b.x, b.y);
                if (ship.overdriveTimer <= 0f) {
                    float gs = grazeScaleOf();
                    grazeCharge = Math.min(1f, grazeCharge + GRAZE_CHARGE_PER_BULLET * gs);
                }
                if (hasRelic(Relic.GRAZE_LEECH)) {
                    // 虹吸矩阵：擦弹同时给冲刺充能，鼓励贴弹走位
                    ship.dashCharge = Math.min(Ship.DASH_MAX_CHARGE,
                            ship.dashCharge + GRAZE_DASH_PER_BULLET);
                }
                if (!grazedThisFrame) {
                    grazedThisFrame = true;
                    events.add(new GameEvent(EventType.GRAZE, b.x, b.y));
                }
            }
        }
        // powerups vs ship
        for (PowerUp p : powerups) {
            if (!p.active) continue;
            float dx = ship.x - p.x, dy = ship.y - p.y;
            float rr = Ship.RADIUS + 26f;
            if (dx * dx + dy * dy <= rr * rr) {
                p.active = false;
                collect(p);
            }
        }
    }

    private void collect(PowerUp p) {
        switch (p.kind) {
            case SPREAD:
                ship.addWeapon(WeaponType.SPREAD);
                weaponCd[WeaponType.SPREAD.ordinal()] = 0f;
                break;
            case MISSILE:
                ship.addWeapon(WeaponType.MISSILE);
                weaponCd[WeaponType.MISSILE.ordinal()] = 0f;
                break;
            case LASER:
                ship.addWeapon(WeaponType.LASER);
                weaponCd[WeaponType.LASER.ordinal()] = 0f;
                break;
            case POWER:
                if (ship.powerLevel < Ship.MAX_POWER) ship.powerLevel++;
                else addScore(300, SCORE_PICKUP); // 满级折算奖励分（雷电式）
                break;
            case SHIELD:
                ship.addShield();
                break;
            case BOMB:
                if (ship.bombs < Ship.MAX_BOMBS) ship.bombs++;
                else addScore(50, SCORE_PICKUP); // overflow converts to salvage points
                break;
            case RAPID:
                ship.rapidTimer = extendTimer(ship.rapidTimer, Ship.RAPID_DURATION);
                break;
            case X2:
                x2Timer = extendTimer(x2Timer, X2_DURATION);
                break;
            case LIFE:
                if (lives < Math.max(MAX_LIVES, startLivesOf())) lives++;
                else addScore(200, SCORE_PICKUP); // 满命时折算奖励分
                break;
            case WINGMAN:
                ship.wingmanTimer = extendTimer(ship.wingmanTimer, Ship.WINGMAN_DURATION);
                break;
            case TIME:
                slowTimer = extendTimer(slowTimer, SLOW_DURATION);
                break;
            case ORBIT:
                ship.orbitTimer = extendTimer(ship.orbitTimer, Ship.ORBIT_DURATION);
                for (int i = 0; i < Ship.ORBIT_SPHERES; i++) orbitCd[i] = 0f;
                break;
            case CHAIN:
                castChainLightning();
                break;
            case BOUNCE:
                ship.addWeapon(WeaponType.BOUNCE);
                weaponCd[WeaponType.BOUNCE.ordinal()] = 0f;
                break;
            case ARC:
                ship.addWeapon(WeaponType.ARC);
                weaponCd[WeaponType.ARC.ordinal()] = 0f;
                break;
            case NOVA:
                detonateNova();
                break;
            case NANITE:
                ship.nanoTimer = extendTimer(ship.nanoTimer, Ship.NANITE_DURATION);
                nanoCd = 0.25f;   // 拾取瞬间就补一层，手感"立刻变强"
                break;
            case TURRET:
                deployTurret(p.x, p.y + 40f);
                break;
            case GRAVITY:
                // 摆在船上方：井口对着来袭弹幕才有"截住"的感觉，井会自己缓慢下移离场
                deployGravityWell(p.x, p.y - 170f);
                break;
            case REFLECT:
                // 叠加而不是替换：满 2 层后折算奖励分，已有层照常工作
                if (!ship.addReflect()) addScore(200, SCORE_PICKUP);
                break;
            case BONUS:
                // 突袭奖励：大量分数 + 双倍分数 + 随机武器 + 清空敌弹
                addScore(400 + 60 * wave, SCORE_PICKUP);
                x2Timer = extendTimer(x2Timer, X2_DURATION);
                ship.addWeapon(dropWeaponType());
                applyFusion();
                for (EnemyBullet b : enemyBullets) {
                    if (!b.active) continue;
                    b.active = false;
                    spark(b.x, b.y, 2, 3);
                }
                bombFlash = Math.max(bombFlash, 0.5f);
                addShake(0.3f, 12f);
                break;
        }
        // 武器叠加：同时持有两种基础武器时自动解锁对应融合武器
        applyFusion();
        events.add(new GameEvent(EventType.POWERUP_COLLECTED, p.x, p.y, p.kind.ordinal()));
    }

    /**
     * 融合检查：SPREAD+LASER=LANCE（贯穿长枪）、SPREAD+MISSILE=CLUSTER（集束爆裂）、
     * LASER+MISSILE=SWARM（追踪蜂群）。融合武器作为额外一路火力叠加，不替换任何已有武器。
     */
    private void applyFusion() {
        for (WeaponType fused : WeaponType.values()) {
            if (!fused.isFused()) continue;
            if (ship.isWeaponEquipped(fused)) continue;
            if (!ship.isWeaponEquipped(fused.fusionA()) || !ship.isWeaponEquipped(fused.fusionB())) {
                continue;
            }
            ship.addWeapon(fused);
            weaponCd[fused.ordinal()] = 0f;
            lastFusion = fused;
            fusions++;
            fusionFlash = Math.max(fusionFlash, 0.9f);
            addShake(0.18f, 6f);
            spark(ship.x, ship.y - 40f, 12, 2);
            events.add(new GameEvent(EventType.FUSION, ship.x, ship.y, fused.ordinal()));
        }
    }

    /** 融合提示的剩余显示时间（HUD 用）。 */
    public float fusionFlash;
    public WeaponType lastFusion;

    /** 重复拾取延长持续时间，上限为基础时长的 2 倍。 */
    private static float extendTimer(float current, float duration) {
        return Math.min(duration * Ship.MAX_TIMER_MULT, current + duration);
    }

    /** 统一得分入口：双倍分数道具激活时翻倍。@return 实际计入的分数 */
    int addScore(int base) {
        return addScore(base, SCORE_OTHER);
    }

    /** 带来源的得分入口，来源用于结算页"分数构成"图。@return 实际计入的分数 */
    int addScore(int base, int source) {
        int gained = base * (x2Timer > 0f ? 2 : 1);
        // 难度得分倍率：休闲打 75 折、硬核 1.5 倍，标准档保持原值
        float scale = difficultyOf().scoreScale;
        if (scale != 1f) gained = Math.round(gained * scale);
        // 异变窗口与日常规则：同一个口径取更强的一个，不叠乘，风险只兑现一次
        float ms = mutatorScoreScaleOf();
        if (ms != 1f) gained = Math.round(gained * ms);
        // 日常挑战命少分高：只有 DAILY 会 >1，其它模式恒等于 1
        float bonus = dailyScoreBonus();
        if (bonus != 1f) gained = Math.round(gained * bonus);
        score += gained;
        int idx = source >= 0 && source < SCORE_SOURCES ? source : SCORE_OTHER;
        scoreBySource[idx] += gained;
        return gained;
    }

    /** Second-finger tap: clears bullets, damages everything, huge shake. */
    public void detonateBomb() {
        if (state != State.PLAYING || ship.bombs <= 0) return;
        ship.bombs--;
        bombFlash = 1f;
        for (Enemy e : enemies) {
            if (!e.active) continue;
            e.damage(3);
            e.flash = 0.2f;
            if (e.hp <= 0) killEnemy(e);
        }
        // 清弹放在击杀之后：自杀机的解体碎片也会被这枚炸弹一起抹掉
        for (EnemyBullet b : enemyBullets) {
            if (b.active) {
                b.active = false;
                spark(b.x, b.y, 2, 0);
            }
        }
        if (boss.active) {
            boss.damage(3);
            for (int i = 0; i < boss.partCount; i++) {
                BossPart p = boss.parts[i];
                if (p.active) damageBossPart(p, 3);
            }
        }
        addShake(0.55f, 24f);
        addHitStop(0.08f);
        events.add(new GameEvent(EventType.BOMB, ship.x, ship.y));
    }

    /** Multiplier earned by chaining kills: +1 every 5 chained kills, capped at x5. */
    public int comboMultiplier() {
        return Math.min(1 + comboKills / 5, 5);
    }

    /** 测试用：直接击杀指定敌人（走完整计分/掉落路径）。 */
    void killEnemyForTest(Enemy e) { killEnemy(e); }

    /** 测试缝：单独掷一次暴击伤害判定（会推进 RNG 并累计 crits）。 */
    int damageOrCritForTest(int base) { return damageOrCrit(base); }

    private void killEnemy(Enemy e) {
        e.active = false;
        // 分裂/子机会复用同一个空闲槽位（很可能就是本机刚让出的槽），
        // 所以先把本机属性取成本地变量，避免后续结算读到被覆写的对象。
        final EnemyType type = e.type;
        final float ex = e.x, ey = e.y;
        final int depth = e.splitDepth;
        final boolean elite = e.elite;
        final boolean cloaked = e.isCloaked();
        final int broodOwner = e.broodOf;
        comboKills++;
        if (comboKills > maxCombo) maxCombo = comboKills;
        comboTimer = COMBO_WINDOW;
        int gained = addScore(type.score * (elite ? Enemy.ELITE_SCORE_MULT : 1)
                * comboMultiplier(), SCORE_ENEMY);
        // 击杀得分飘字：精英与重甲用大号，普通小怪只在分数可观时飘
        spawnFloatText(ex, ey - type.radius * 0.6f, gained, FLOAT_SCORE,
                elite || type.score >= 25 || comboMultiplier() >= 3);
        kills++;
        typeKills[type.ordinal()]++;
        if (elite) elitesKilled++;
        ultCharge(elite ? ULT_GAIN_ELITE : ULT_GAIN_KILL, ex, ey);
        if (mutator.isActive()) mutatorKills++;
        explode(ex, ey, type.colorHint);
        if (elite) {
            spark(ex, ey, 10, 4);
            addShake(0.12f, 5f);
        }
        if (type == EnemyType.SUICIDER) {
            // 自杀机解体时散出一圈碎片，逼玩家不要贴脸击杀
            for (int i = 0; i < 5; i++) {
                EnemyBullet b = freeEnemyBullet();
                if (b == null) break;
                float ang = (float) (Math.PI * 2 * i / 5f + RNG.get().nextFloat() * 0.4f);
                b.initRadius(ex, ey,
                        (float) Math.cos(ang) * 210f, (float) Math.sin(ang) * 210f, 8f);
            }
            addShake(0.14f, 6f);
        }
        if (type == EnemyType.SPLITTER && depth < 1) {
            for (int i = 0; i < 2; i++) {
                Enemy child = freeEnemyExcept(e);
                if (child == null) break;
                child.init(EnemyType.DRONE, ex + (i == 0 ? -34f : 34f), ey, enemySpeedScale);
                child.splitDepth = depth + 1;
                child.hp = 1f;
                child.maxHp = 1f;
                spawnedCount++;
            }
            explode(ex - 30f, ey + 10f, 5);
            explode(ex + 30f, ey - 10f, 5);
        }
        addShake(type == EnemyType.TANK ? 0.22f : 0.05f,
                 type == EnemyType.TANK ? 9f : 2.5f);
        if (type == EnemyType.TANK || type == EnemyType.SPLITTER) addHitStop(0.045f);
        if (type == EnemyType.MINER) {
            // 击毁布雷艇：它刚布下的水雷一并解除，否则越打越乱
            for (EnemyBullet b : enemyBullets) {
                if (b.active && b.mine && Math.abs(b.y - ey) < 520f) {
                    b.active = false;
                    spark(b.x, b.y, 3, 8);
                }
            }
        }
        if (type == EnemyType.MIRAGE && cloaked) {
            // 识破：在它还披着道具外壳时就打下来，奖励"看得仔细"
            miragesSpotted++;
            int spotted = addScore(MIRAGE_SPOT_BONUS, SCORE_OTHER);
            spawnFloatText(ex, ey - type.radius, spotted, FLOAT_SCORE, true);
            ultCharge(ULT_GAIN_ELITE * 0.6f, ex, ey);
            spark(ex, ey, 10, 11);
            events.add(new GameEvent(EventType.MIRAGE_REVEAL, ex, ey));
            // 必掉一枚真道具：识破不该只是加分，得当场把赚头还给玩家
            spawnPowerUp(dropKind(RNG.get().nextFloat()), ex, ey);
        }
        if (type == EnemyType.MAGNETON) collapseMagnetField(e);
        if (type == EnemyType.QUEEN) {
            collapseQueenBrood(indexOf(e));
            addShake(0.3f, 12f);
            addHitStop(0.055f);
        }
        if (broodOwner >= 0) e.broodOf = -1;   // 幼机槽位即将复用，先摘掉归属
        maybeDropPowerUp(type, ex, ey, elite);
        events.add(new GameEvent(EventType.ENEMY_KILLED, ex, ey, type.ordinal()));
        if (comboKills % 5 == 0 && comboKills <= 20) {
            events.add(new GameEvent(EventType.COMBO, ex, ey, comboKills));
        }
    }

    /** Tanks and support hulls drop often, drones rarely. 武器/火力类道具权重最高（雷电式成长曲线）。 */
    private void maybeDropPowerUp(EnemyType type, float x, float y, boolean elite) {
        float chance = type == EnemyType.TANK ? 0.45f
                : type == EnemyType.SPLITTER ? 0.32f
                : type == EnemyType.HEALER ? 0.36f
                : type == EnemyType.SNIPER ? 0.22f
                : type == EnemyType.CARRIER ? 0.55f
                : type == EnemyType.SHIELDER ? 0.30f
                : type == EnemyType.MINER ? 0.24f
                : type == EnemyType.MAGNETON ? 0.40f
                : type == EnemyType.QUEEN ? 0.75f
                : type == EnemyType.MIRAGE ? 0.20f
                : type == EnemyType.RAMMER ? 0.30f
                : type == EnemyType.PHASER ? 0.34f
                : type == EnemyType.HUNTER ? 0.12f
                : type == EnemyType.SUICIDER ? 0.04f : 0.05f;
        chance = Math.min(1f, chance * dropRatePctOf() / 100f
                    * difficultyOf().dropScale
                    * dropScaleOf());
        if (!elite && RNG.get().nextFloat() >= chance) return; // 精英必掉
        spawnPowerUp(dropKind(RNG.get().nextFloat()), x, y);
    }

    /**
     * 掉落分布（v1.15，v1.23 加入 REFLECT，v1.24 加入 ARC）。
     * BONUS 只由突袭奖励波投放，不在常规掉落表内。
     * 追踪弹 MISSILE 14%（用户反馈"想要更多追踪弹"），
     * 尾部"特殊道具"区（0.85~1.0，共 15%）：
     * BOUNCE 3%、NOVA 3%、NANITE 2.5%、GRAVITY 2%、ARC 2%、REFLECT 1.5%、TURRET 1%。
     * ARC 的 2% 由双倍分数与僚机各让出 1%（效果本身不变，只是出现频率略降）。
     * 独立成纯函数便于测试边界。
     */
    public static PowerUp.Kind dropKind(float roll) {
        if (roll < 0.12f) return PowerUp.Kind.SPREAD;
        if (roll < 0.24f) return PowerUp.Kind.POWER;
        if (roll < 0.38f) return PowerUp.Kind.MISSILE;
        if (roll < 0.46f) return PowerUp.Kind.LASER;
        if (roll < 0.54f) return PowerUp.Kind.SHIELD;
        if (roll < 0.60f) return PowerUp.Kind.BOMB;
        if (roll < 0.65f) return PowerUp.Kind.ORBIT;
        if (roll < 0.70f) return PowerUp.Kind.CHAIN;
        if (roll < 0.74f) return PowerUp.Kind.RAPID;
        if (roll < 0.77f) return PowerUp.Kind.X2;
        if (roll < 0.80f) return PowerUp.Kind.LIFE;
        if (roll < 0.82f) return PowerUp.Kind.WINGMAN;
        if (roll < 0.85f) return PowerUp.Kind.TIME;
        if (roll < 0.88f) return PowerUp.Kind.BOUNCE;
        if (roll < 0.91f) return PowerUp.Kind.NOVA;
        if (roll < 0.935f) return PowerUp.Kind.NANITE;
        if (roll < 0.955f) return PowerUp.Kind.GRAVITY;
        if (roll < 0.975f) return PowerUp.Kind.ARC;
        if (roll < 0.99f) return PowerUp.Kind.REFLECT;
        return PowerUp.Kind.TURRET;
    }

    /** Public for tests; spawns a pickup at the given spot. */
    public void spawnPowerUp(PowerUp.Kind kind, float x, float y) {
        for (PowerUp p : powerups) {
            if (!p.active) {
                p.init(kind, x, y);
                return;
            }
        }
    }

    /** Wires persistence; loads best score, both leaderboards and settings. Call once from the app layer. */
    public void attachStore(SettingsStore s) {
        this.store = s;
        if (s != null) {
            s.load(settings);
            bestScore = s.bestScore();
            int[] lb = s.leaderboard(GameMode.WAVES);
            for (int i = 0; i < leaderboard.length && i < lb.length; i++) leaderboard[i] = lb[i];
            int[] elb = s.leaderboard(GameMode.ENDLESS);
            for (int i = 0; i < leaderboardEndless.length && i < elb.length; i++) leaderboardEndless[i] = elb[i];
            int[] rlb = s.leaderboard(GameMode.RUSH);
            for (int i = 0; i < leaderboardRush.length && i < rlb.length; i++) leaderboardRush[i] = rlb[i];
        }
    }

    /** @return the board for the given mode. */
    public int[] board(GameMode m) {
        // 日常局不进分数榜：它的榜单是"最近 14 天日历"，见 {@link #dailyHistory()}
        if (m == GameMode.DAILY) return new int[5];
        if (m == GameMode.RUSH) return leaderboardRush;
        return m == GameMode.ENDLESS ? leaderboardEndless : leaderboard;
    }

    /**
     * Inserts a finished-run score into the mode's local top-5 and persists it.
     * @return 0-based rank, or -1 if the score didn't make the board
     */
    int insertScore(int s, GameMode m) {
        if (s <= 0) return -1;
        int[] board = board(m);
        int rank = -1;
        for (int i = 0; i < board.length; i++) {
            if (s > board[i]) {
                rank = i;
                break;
            }
        }
        if (rank < 0) return -1;
        for (int i = board.length - 1; i > rank; i--) board[i] = board[i - 1];
        board[rank] = s;
        if (store != null) store.saveLeaderboard(m, board);
        return rank;
    }

    /** Handles a UI tap action produced by {@link Ui#hit}. */
    public void handleUiAction(Ui.Action a) {
        switch (a) {
            case START:
                closePanels();
                restart(GameMode.WAVES);
                break;
            case START_ENDLESS:
                closePanels();
                restart(GameMode.ENDLESS);
                break;
            case START_DAILY:
                closePanels();
                restart(GameMode.DAILY);
                break;
            case START_RUSH:
                closePanels();
                restart(GameMode.RUSH);
                break;
            case CHECK_UPDATE:
                // IDLE/最新/失败 -> 重新检查；已发现新版本 -> 开始下载；忙碌中忽略
                if (updateState == UpdateState.IDLE || updateState == UpdateState.UP_TO_DATE
                        || updateState == UpdateState.ERROR) {
                    updateState = UpdateState.CHECKING;
                    updateMsgTimer = 0f;
                    updateError = null;
                } else if (updateState == UpdateState.AVAILABLE) {
                    updateState = UpdateState.DOWNLOADING;
                    updateProgress = 0;
                }
                break;
            case OPEN_SETTINGS:
                settingsOpen = true;
                cheatOpen = false;
                codexOpen = false;
                achieveOpen = false;
                break;
            case OPEN_CHEATS:
                cheatOpen = true;
                settingsOpen = false;
                codexOpen = false;
                achieveOpen = false;
                break;
            case OPEN_CODEX:
                codexOpen = true;
                settingsOpen = false;
                cheatOpen = false;
                achieveOpen = false;
                flushCodex();
                break;
            case CODEX_BACK:
                codexOpen = false;
                break;
            case CODEX_TAB:
                codexTab = codexTab == 0 ? 1 : 0;
                break;
            case OPEN_ACHIEVEMENTS:
                achieveOpen = true;
                settingsOpen = false;
                cheatOpen = false;
                codexOpen = false;
                // 打开成就页前先把账结算干净：页面显示的进度要和真实进度一致
                commitAchievements();
                flushCodex();
                break;
            case ACHIEVEMENTS_BACK:
                achieveOpen = false;
                break;
            case ACHIEVEMENTS_TAB:
                achieveTab = achieveTab == 0 ? 1 : 0;
                break;
            case CHEATS_BACK:
                cheatOpen = false;
                break;
            case TOGGLE_GOD:
                settings.godMode = !settings.godMode;
                saveSettings();
                break;
            case CHEAT_RESET:
                settings.resetCheats();
                saveSettings();
                break;
            case CHEAT_LIVES_DOWN:
                settings.adjust("lives", -1);
                saveSettings();
                break;
            case CHEAT_LIVES_UP:
                settings.adjust("lives", 1);
                saveSettings();
                break;
            case CHEAT_DROP_DOWN:
                settings.adjust("drop", -1);
                saveSettings();
                break;
            case CHEAT_DROP_UP:
                settings.adjust("drop", 1);
                saveSettings();
                break;
            case CHEAT_SPEED_DOWN:
                settings.adjust("speed", -1);
                saveSettings();
                break;
            case CHEAT_SPEED_UP:
                settings.adjust("speed", 1);
                saveSettings();
                break;
            case CHEAT_POWER_DOWN:
                settings.adjust("power", -1);
                saveSettings();
                break;
            case CHEAT_POWER_UP:
                settings.adjust("power", 1);
                saveSettings();
                break;
            case TOGGLE_SOUND:
                settings.toggle("sound");
                saveSettings();
                break;
            case TOGGLE_PARTICLES:
                settings.toggle("particles");
                saveSettings();
                break;
            case TOGGLE_HAPTICS:
                settings.toggle("haptics");
                saveSettings();
                break;
            case TOGGLE_REDUCED_FX:
                settings.toggle("reducedFx");
                saveSettings();
                break;
            case TOGGLE_LEFT_HAND:
                settings.toggle("leftHanded");
                saveSettings();
                break;
            case SETTINGS_BACK:
                settingsOpen = false;
                break;
            case SET_DIFF_CASUAL:
            case SET_DIFF_STANDARD:
            case SET_DIFF_HARDCORE:
                settings.setDifficulty(difficultyFor(a));
                saveSettings();
                break;
            case RESUME:
                state = State.PLAYING;
                break;
            case RESTART:
                restart(mode); // restart keeps the current mode
                break;
            case QUIT_TO_MENU:
                // 半局退出也要结算：成就吃的是增量，不结算就等于这一局白玩
                commitAchievements();
                flushCodex();
                closePanels();
                state = State.MENU;
                stateTimer = 0f;
                break;
            case PAUSE:
                if (state == State.PLAYING) {
                    state = State.PAUSED;
                    stateTimer = 0f;
                    clearShipInput(); // 否则摇杆残影会留在暂停遮罩下
                }
                break;
        }
    }

    /** 分段按钮 → 难度档位。 */
    private static Difficulty difficultyFor(Ui.Action a) {
        switch (a) {
            case SET_DIFF_CASUAL: return Difficulty.CASUAL;
            case SET_DIFF_HARDCORE: return Difficulty.HARDCORE;
            default: return Difficulty.STANDARD;
        }
    }

    private void saveSettings() {
        if (store != null) store.save(settings);
    }

    /** 菜单里的设置/LAB/图鉴/成就面板互斥；返回键统一关闭。 */
    public void closePanels() {
        settingsOpen = false;
        cheatOpen = false;
        codexOpen = false;
        achieveOpen = false;
    }

    /** 图鉴进度只在有变化时落盘，避免每帧写 SharedPreferences。 */
    public void flushCodex() {
        if (!codexDirty) return;
        codexDirty = false;
        saveSettings();
    }

    /** Pauses gameplay (used by lifecycle callbacks). */
    public void pauseIfPlaying() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
            stateTimer = 0f;
            clearShipInput();
            // 切后台/锁屏：先把这一段入账并落盘，玩家回来时进度不会凭空少一截
            commitAchievements();
            flushCodex();
        }
    }

    private void onPlayerHit() {
        if (godModeOf()) return;
        if (ship.consumeShield()) { // 每一层护盾各挡一次伤害
            ship.makeInvulnerable(difficultyOf().respawnInvuln);
            respawnGrace = difficultyOf().respawnInvuln;
            addShake(0.25f, 10f);
            addHitStop(0.06f);
            events.add(new GameEvent(EventType.SHIELD_BROKEN, ship.x, ship.y));
            return;
        }
        comboKills = 0;
        comboTimer = 0f;
        hitsTaken++;
        addShake(0.4f, 16f);
        addHitStop(0.09f);
        lives--;
        applyDeathPenalty();
        events.add(new GameEvent(EventType.PLAYER_HIT, ship.x, ship.y));
        if (lives <= 0) {
            lives = 0;
            state = State.GAME_OVER;
            stateTimer = 0f;
            clearShipInput();
            addShake(0.6f, 22f);
            if (daily()) {
                // 日常局只写日历：全球同一天同一把尺，历史最高分和两张分数榜留给自由局
                dailyImproved = recordDailyRun();
            } else if (bossRush()) {
                // 连战局只写自己的榜与"最深入纪录"：不吃 CAMPAIGN 的历史最高分
                if (bossRushKills > settings.rushBest) {
                    settings.rushBest = bossRushKills;
                    bossRushImproved = true;
                    if (store != null) store.save(settings);
                }
                scoreRank = insertScore(score, mode);
            } else {
                if (score > bestScore) {
                    bestScore = score;
                    newBest = true;
                    if (store != null) store.saveBestScore(bestScore);
                }
                scoreRank = insertScore(score, mode);
            }
            // 坠机也要把最后一程的增量入账（此时已是 GAME_OVER，只记成就不再改分）
            commitAchievements();
            flushCodex();
            events.add(new GameEvent(EventType.GAME_OVER, ship.x, ship.y));
        } else {
            respawnSafely();
        }
    }

    public void addShake(float time, float mag) {
        if (time > shakeTime || mag > shakeMag) {
            shakeTime = Math.max(shakeTime, time);
            shakeMag = Math.max(shakeMag, mag);
        }
    }

    // ---- 升级检测：app 层异步 IO 完成后调用的回调 ----

    /** 检查结果。upToDate=true 时 tag/url 忽略；失败时 err 非空。 */
    public void onCheckResult(boolean ok, boolean upToDate, String tag, String url, String err) {
        if (updateState != UpdateState.CHECKING) return; // 状态已被重置（如回菜单），忽略过期回调
        if (!ok) {
            updateState = UpdateState.ERROR;
            updateError = err != null ? err : "检查失败，点击重试";
            updateMsgTimer = 0f;
            return;
        }
        if (upToDate) {
            updateState = UpdateState.UP_TO_DATE;
            updateMsgTimer = 0f;
        } else {
            updateVersion = tag;
            updateUrl = url;
            updateState = UpdateState.AVAILABLE;
        }
    }

    public void onDownloadProgress(int percent) {
        if (updateState == UpdateState.DOWNLOADING && percent > updateProgress) {
            updateProgress = Math.min(percent, 99); // 100 留给 READY
        }
    }

    public void onDownloadReady() {
        if (updateState == UpdateState.DOWNLOADING) updateState = UpdateState.READY;
    }

    /** 下载失败：DOWNLOADING -> ERROR，否则界面会永久停在“下载中”。 */
    public void onDownloadError(String err) {
        if (updateState != UpdateState.DOWNLOADING) return;
        updateState = UpdateState.ERROR;
        updateError = err != null ? err : "下载失败，点击重试";
        updateMsgTimer = 0f;
    }

    /** READY 后若用户取消安装，允许回到可再次点击下载的状态。 */
    public void onUpdateDismissed() {
        if (updateState == UpdateState.READY) {
            updateState = UpdateState.AVAILABLE;
        }
    }

    /** @return true 当处于需要 app 层执行网络 IO 的状态。 */
    public boolean updateNeedsNetwork() {
        return updateState == UpdateState.CHECKING || updateState == UpdateState.DOWNLOADING;
    }

    /** Starts a fresh run in the given mode. */
    public void restart(GameMode m) {
        // 开新局前先把上一局的增量入账：中途退出、坠机后立刻重开都不会白打
        commitAchievements();
        achievements.beginRun();
        mode = m;
        applyDailyRules(m);
        // v1.20：连战局每场都从第一位 Boss 重来，深潜纪录只涨不跌
        bossRushKills = 0;
        bossRushImproved = false;
        bossRushBanner = bossRush() ? 3.2f : 0f;
        if (bossRush()) {
            events.add(new GameEvent(EventType.BOSS_RUSH_START,
                    WIDTH / 2f, HEIGHT - 140f, RUSH_LIVES));
        }
        score = 0;
        lives = startLivesOf();
        kills = 0;
        spawnedCount = 0;
        bulletsFired = 0;
        shotsHit = 0;
        maxCombo = 0;
        hitsTaken = 0;
        grazes = 0;
        grazeCharge = 0f;
        crits = 0;
        bulletsShotDown = 0;
        parries = 0;
        parryStreak = 0;
        parryFlash = 0f;
        elitesKilled = 0;
        mutator = Mutator.NONE;
        mutatorTimer = 0f;
        mutatorNextAt = MUTATOR_FIRST_AT;
        mutatorBanner = 0f;
        mutatorsSeen = 0;
        mutatorBonus = 0;
        mutatorKills = 0;
        maxParryStreak = 0;
        timeAlive = 0f;
        stateTimer = 0f;
        wave = 1;
        newBest = false;
        scoreRank = -1;
        comboKills = 0;
        comboTimer = 0f;
        shakeTime = 0f;
        shakeMag = 0f;
        bombFlash = 0f;
        x2Timer = 0f;
        slowTimer = 0f;
        wingmanCd = 0f;
        hitStopTimer = 0f;
        rushTimer = m == GameMode.ENDLESS ? 20f : 26f;
        rushActive = false;
        rushBanner = 0f;
        rushesTriggered = 0;
        // v1.17：特化遭遇保底倒计时与轮换计数归零
        v117EncounterTimer = m == GameMode.ENDLESS ? 6f : 9f;
        v117Encounters = 0;
        debutBanner = 0f;
        debutBannerType = null;
        for (int i = 0; i < v117EncounterCounts.length; i++) v117EncounterCounts[i] = 0;
        // v1.18：本局成就读数归零（Settings 里的历史最佳与累计账本不动）
        for (int i = 0; i < typeKills.length; i++) typeKills[i] = 0;
        fusions = 0;
        achieveScore = 0;
        achieveLastGain = 0;
        pacingExemptScore = 0;
        achievesThisRun = 0;
        achieveBanner = 0f;
        achieveBannerType = null;
        fusionFlash = 0f;
        lastFusion = null;
        attackSeq = 0;
        bossRamTrailCd = 0f;
        // v1.10：圣物 / 重生保护 / 新星 / 炮台 全部归零
        relicMask = 0;
        lastRelic = null;
        relicBanner = 0f;
        relicsGained = 0;
        respawnGrace = 0f;
        novaFlash = 0f;
        relicBeamCd = 0f;
        relicAegisCd = 0f;
        nanoCd = 0f;
        for (Turret t : turrets) t.active = false;
        for (GravityWell g : gravityWells) g.active = false;
        gravityFlash = 0f;
        gravityAbsorbed = 0;
        gravityBursts = 0;
        gravityWellsDeployed = 0;
        ultConverted = 0;
        ult.reset();
        ultFlash = 0f;
        miragesRevealed = 0;
        miragesSpotted = 0;
        magnetBursts = 0;
        broodSpawned = 0;
        bulletsBent = 0;
        magnetCount = 0;
        for (int i = 0; i < SCORE_SOURCES; i++) scoreBySource[i] = 0;
        for (int i = 0; i < MAX_ARCS; i++) arcAge[i] = 0f;
        for (int i = 0; i < orbitCd.length; i++) orbitCd[i] = 0f;
        joyActive = false;
        joyAnim = 0f;
        boss.active = false;
        bossActive = false;
        bossFlash = 0f;
        for (BossLaser l : bossLasers) l.active = false;
        for (int i = 0; i < weaponCd.length; i++) weaponCd[i] = 0f;
        bossesDefeated = 0;
        nextBossWave = m == GameMode.ENDLESS ? 3 : 4; // endless: bosses come faster
        bossGapTimer = bossRush() ? BOSS_RUSH_OPENING : 0f;   // 连战局开场 2.6 秒后第一位入场
        formationTimer = m == GameMode.ENDLESS ? 6f : 8f;
        spawnTimer = 0f;
        state = State.PLAYING;
        ship.reset(WIDTH / 2f, HEIGHT - 140f);
        ship.powerLevel = startPowerOf();
        for (Bullet b : bullets) b.active = false;
        for (EnemyBullet b : enemyBullets) b.active = false;
        for (Enemy e : enemies) e.active = false;
        for (PowerUp p : powerups) p.active = false;
        for (Particle p : particles) p.active = false;
        for (FloatText f : floatTexts) f.active = false;
        floatBudget = 0;
        floatCursor = 0;
    }

    // ---- v1.19 每日挑战：日期决定一切 ----

    /** 当前是否在每日挑战局里。 */
    public boolean daily() {
        return mode == GameMode.DAILY;
    }

    /** 当前是否在 BOSS RUSH 连战局里。 */
    public boolean bossRush() {
        return mode == GameMode.RUSH;
    }

    /** 下一场 Boss 的编号（0 起；HUD 展示用 1 起）。 */
    public int bossRushNext() {
        return bossesDefeated + 1;
    }

    /**
     * 把 {@link #pendingRngSeed} 装上当前线程的随机流。
     *
     * <p>为什么不能就在 {@link #restart} 里 setSeed：{@link RNG} 是 ThreadLocal，
     * 触屏"开始日常"在 UI 线程回调，而吃随机数的是渲染线程。种子先存着，
     * 下一帧第一行装上，保证同一天的每一次重开都从同一个随机序列起步。
     */
    private void applyPendingRngSeed() {
        long seed = pendingRngSeed;
        if (seed == 0L) return;
        pendingRngSeed = 0L;
        RNG.setSeed(seed);
    }

    /**
     * "今天"的日期键。真实设备读系统时钟；测试通过 {@link #dailyNowMillis} 钉死。
     * 结果按秒缓存，渲染层每帧调用也不会去 new Calendar。
     */
    public int todayKey() {
        long millis = dailyNowMillis != 0L ? dailyNowMillis : System.currentTimeMillis();
        if (cachedDailyKey == 0 || millis - cachedDailyCheck >= 1000L) {
            cachedDailyCheck = millis;
            cachedDailyKey = DailyChallenge.dateKey(millis);
        }
        return cachedDailyKey;
    }

    /** 开日常局时按日期锁定种子/规则/命数，普通局恢复玩家自己的设置。 */
    private void applyDailyRules(GameMode m) {
        dailyImproved = false;
        if (m == GameMode.DAILY) {
            dailyKey = todayKey();
            dailyRule = DailyChallenge.ruleFor(dailyKey);
            dailyLives = DailyChallenge.livesFor(dailyKey);
            dailyBanner = 3.2f;
            // 同一天重开 = 同一条时间线：刷分靠重复练习，不靠重新摇号。
            // 种子先存着，等 {@link #update} 在跑模拟的那条线程上真正装上（见 applyPendingRngSeed）。
            pendingRngSeed = DailyChallenge.seedFor(dailyKey);
            RNG.setSeed(pendingRngSeed);
            events.add(new GameEvent(EventType.DAILY_START,
                    WIDTH / 2f, HEIGHT - 140f, dailyRule.ordinal()));
        } else {
            dailyKey = 0;
            dailyRule = Mutator.NONE;
            dailyLives = settings.startLives;
            dailyBanner = 0f;
            // 普通局不改随机流：否则刚打完日常再开自由局，第一帧会去吃日常的种子
            pendingRngSeed = 0L;
        }
    }

    /** 今日得分加成（一条命的日子给 1.5 倍，风险与回报同时兑现）。 */
    private float dailyScoreBonus() {
        return daily() ? DailyChallenge.livesScoreBonus(dailyLives) : 1f;
    }

    // ---- 本局生效系数：日常挑战锁标准难度、屏蔽 LAB，保证全球同一把尺 ----
    // BOSS RUSH 同理：它有自己的榜，不能让 LAB 或难度档位改写别人的成绩。

    private boolean competitive() {
        return daily() || bossRush();
    }

    private Difficulty difficultyOf() {
        return competitive() ? Difficulty.STANDARD : settings.difficulty;
    }

    private int dropRatePctOf() {
        return competitive() ? 100 : settings.dropRatePct;
    }

    private int enemySpeedPctOf() {
        return competitive() ? 100 : settings.enemySpeedPct;
    }

    private int startPowerOf() {
        if (daily()) return 1;
        if (bossRush()) return 2;   // 开局就敢当面刚 Boss：两路火力，且第一场必掉装备
        return settings.startPower;
    }

    private int startLivesOf() {
        if (daily()) return dailyLives;
        if (bossRush()) return RUSH_LIVES;
        return settings.startLives;
    }

    private boolean godModeOf() {
        return !competitive() && settings.godMode;
    }

    // ---- 今日规则与随机异变波的叠加：同类倍率只认更极端的一档，不相乘 ----

    /**
     * 两层倍率相乘会造出"两倍铁雨"这种没人能读的时空，所以同一口径只生效一个：
     * 取<b>偏离 1 更远</b>的那一档。不能直接取 max——虫群把敌机血量压到 0.6、
     * 钢铁洪流抬到 2.2，max 会把"削弱"当成"没生效"（0.6 与 1 取大 = 1）。
     * 普通局 {@link #dailyRule} 是 NONE（全 1），本式恒等于 window，与旧行为逐位相同。
     */
    private static float harsher(float window, float daily) {
        if (daily == 1f) return window;
        if (window == 1f) return daily;
        return Math.abs(window - 1f) >= Math.abs(daily - 1f) ? window : daily;
    }

    private float fireScaleOf() {
        return harsher(mutator.fireScale, dailyRule.fireScale);
    }

    private float speedScaleOf() {
        return harsher(mutator.speedScale, dailyRule.speedScale);
    }

    private float spawnScaleOf() {
        return harsher(mutator.spawnScale, dailyRule.spawnScale);
    }

    private float hpScaleOf() {
        return harsher(mutator.hpScale, dailyRule.hpScale);
    }

    private float dropScaleOf() {
        return harsher(mutator.dropScale, dailyRule.dropScale);
    }

    private float critScaleOf() {
        return harsher(mutator.critScale, dailyRule.critScale);
    }

    private float grazeScaleOf() {
        return harsher(mutator.grazeScale, dailyRule.grazeScale);
    }

    private float mutatorScoreScaleOf() {
        return harsher(mutator.scoreScale, dailyRule.scoreScale);
    }

    /** 今日规则的展示串（菜单/HUD/结算共用）。 */
    public String dailySummary() {
        return DailyChallenge.summary(dailyKey != 0 ? dailyKey : todayKey());
    }

    /** 最近 14 天的日期键（[0] 是最近一天，0 = 空槽）。 */
    public int[] dailyKeysSeen() {
        return settings.dailyKeys;
    }

    /** 与 {@link #dailyKeysSeen()} 一一对应的当日最佳分。 */
    public int[] dailyScoresSeen() {
        return settings.dailyScores;
    }

    /** 连续打卡天数（从今天往回数，断一天归零）。 */
    public int dailyStreak() {
        return settings.dailyStreak(todayKey());
    }

    /** 今天是否已经打过（菜单上的"已完成/待打卡"标记）。 */
    public boolean dailyPlayedToday() {
        return settings.dailyBest(todayKey()) > 0;
    }

    /**
     * 结算一局日常挑战：写进"日期 → 当日最佳"的日历，不动历史最高分与两张分数榜。
     *
     * @return 是否刷新了今天的最佳
     */
    private boolean recordDailyRun() {
        boolean improved = settings.recordDaily(dailyKey, score);
        if (improved) codexDirty = true;   // 日历与图鉴共用同一条落盘通道
        return improved;
    }

    private void explode(float x, float y, int color) {
        if (!settings.particles) return;
        int n = (int) (14 * emissionScale);
        for (int i = 0; i < n; i++) {
            Particle p = freeParticle();
            float ang = RNG.get().nextFloat() * (float) Math.PI * 2f;
            float spd = 80f + RNG.get().nextFloat() * 320f;
            p.init(x, y, (float) Math.cos(ang) * spd, (float) Math.sin(ang) * spd,
                    0.35f + RNG.get().nextFloat() * 0.35f, 4f + RNG.get().nextFloat() * 5f, color);
        }
    }

    private void spark(float x, float y, int n, int color) {
        if (!settings.particles) return;
        n = (int) (n * emissionScale);
        sparkFrom(x, y, n, color, RNG.get());
    }

    /**
     * 庆祝特效专用的发射口：随机数取自独立的 {@link #fxRandom}。
     * 对局随机流（{@link #RNG}）决定敌机排布、掉落、导弹目标——每解锁一条成就就从里面
     * 多抽几个数，等于把这一局之后的进程整个改写。成就只该加东西、不该改动既有对局，
     * 所以粒子角度这类纯演出的取值走另一条流。
     */
    private void sparkFx(float x, float y, int n, int color) {
        if (!settings.particles) return;
        sparkFrom(x, y, (int) (n * emissionScale), color, fxRandom);
    }

    private void sparkFrom(float x, float y, int n, int color, Random random) {
        for (int i = 0; i < n; i++) {
            Particle p = freeParticle();
            float ang = random.nextFloat() * (float) Math.PI * 2f;
            float spd = 60f + random.nextFloat() * 160f;
            p.init(x, y, (float) Math.cos(ang) * spd, (float) Math.sin(ang) * spd, 0.22f, 3f, color);
        }
    }

    /** Shortest distance from point P to segment AB (ship sweep between frames). */
    private static float distToSegment(float px, float py, float ax, float ay, float bx, float by) {
        float abx = bx - ax, aby = by - ay;
        float apx = px - ax, apy = py - ay;
        float ab2 = abx * abx + aby * aby;
        float t = ab2 <= 1e-6f ? 0f : (apx * abx + apy * aby) / ab2;
        t = Math.max(0f, Math.min(1f, t));
        float dx = px - (ax + abx * t);
        float dy = py - (ay + aby * t);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // ---- pool helpers ----
    private Bullet freeBullet() {
        for (Bullet b : bullets) if (!b.active) return b;
        return null;
    }

    private EnemyBullet freeEnemyBullet() {
        for (EnemyBullet b : enemyBullets) if (!b.active) return b;
        return null;
    }

    private Enemy freeEnemy() {
        for (Enemy e : enemies) if (!e.active) return e;
        return null;
    }

    /**
     * 取一个空闲槽位，但避开指定对象。
     * 分裂舱在自己槽位刚释放时生成子机，若复用同一对象，本帧的碰撞循环会把刚出生的
     * 子机当成"还没结算的母机"处理（同位置立刻撞船），因此显式排除。
     */
    private Enemy freeEnemyExcept(Enemy skip) {
        for (Enemy e : enemies) if (e != skip && !e.active) return e;
        return null;
    }

    private Particle freeParticle() {
        // ring cursor: O(1) amortized scan, oldest slots recycled first when full
        for (int i = 0; i < particles.length; i++) {
            int idx = (particleCursor + i) % particles.length;
            Particle p = particles[idx];
            if (!p.active) {
                particleCursor = (idx + 1) % particles.length;
                return p;
            }
        }
        Particle p = particles[particleCursor];
        particleCursor = (particleCursor + 1) % particles.length;
        return p;
    }

    private int particleCursor;

    /** Live particle count; used by tests and the adaptive-quality governor. */
    public int activeParticles() {
        int n = 0;
        for (Particle p : particles) if (p.active) n++;
        return n;
    }

    /**
     * 起一条飘字。低特效开关下直接跳过；每帧条数受 {@link #FLOAT_BUDGET_PER_FRAME}
     * 限制，池满时覆盖最旧的一条，保证激光连射不会把屏幕刷成数字雨。
     */
    private void spawnFloatText(float x, float y, int value, int color, boolean bold) {
        if (!settings.reducedFx && floatBudget > 0 && value > 0) {
            floatBudget--;
            FloatText f = floatTexts[floatCursor];
            floatCursor = (floatCursor + 1) % floatTexts.length;
            float drift = (RNG.get().nextFloat() - 0.5f) * 70f;
            f.init(x, y, value, color, bold, drift);
        }
    }

    /** 活着的飘字条数（测试与自适应画质用）。 */
    public int activeFloatTexts() {
        int n = 0;
        for (FloatText f : floatTexts) if (f.active) n++;
        return n;
    }

    /**
     * 玩家子弹 vs 敌弹：命中即引爆敌弹，穿透光束引爆后继续飞行。
     * 只有 {@link #BREAK_MIN_DAMAGE} 以上的重弹参与拦截，僚机弹丸也不算；
     * 代价是这发实弹本轮不再对舰体造成伤害，于是"点弹幕保命"和"集火输出"变成取舍。
     * 用 96 单位粗筛避免 168×56 的全量配对。
     */
    private void shootDownEnemyBullets() {
        for (EnemyBullet eb : enemyBullets) {
            if (!eb.active) continue;
            for (Bullet b : bullets) {
                if (!b.active || b.hitCooldown > 0f) continue;
                if (b.damage < BREAK_MIN_DAMAGE || b.drone) continue;
                float dx = b.x - eb.x;
                if (dx > BREAK_CULL || dx < -BREAK_CULL) continue;
                float dy = b.y - eb.y;
                if (dy > BREAK_CULL || dy < -BREAK_CULL) continue;
                float rr = b.radius + eb.radius + 1f;
                if (dx * dx + dy * dy > rr * rr) continue;
                if (b.piercing) b.hitCooldown = 0.05f;
                else b.active = false;
                eb.active = false;
                bulletsShotDown++;
                ultCharge(ULT_GAIN_BREAK, eb.x, eb.y);
                breakPulse = 1f;
                addScore(BULLET_BREAK_SCORE, SCORE_OTHER);
                spark(eb.x, eb.y, 3, eb.mine ? 8 : 0);
                events.add(new GameEvent(EventType.BULLET_BREAK, eb.x, eb.y,
                        eb.mine ? 1 : 0));
                break;
            }
        }
    }

    /**
     * 反射护罩：把命中飞船的敌弹翻面成友军火力。
     * 与"吸收"不同，这一发不会消失：它带着 2 点伤害飞回敌阵，层数也不减——
     * 玩家拿到的是一条持续的正向回路，而不是一次性的免死。
     */
    private void reflectEnemyBullet(EnemyBullet src) {
        float vx = -src.vx * 1.25f;
        float vy = Math.min(-Math.abs(src.vy) * 1.25f - 220f, -320f);
        src.active = false;
        Bullet out = freeBullet();
        if (out != null) {
            out.init(src.x, src.y, vx, vy, 2, false);
            out.reflected = true;
            out.radius = 11f;
        }
        spark(src.x, src.y, 8, 15);
        addShake(0.08f, 3f);
        events.add(new GameEvent(EventType.REFLECT, src.x, src.y,
                ship.readyReflectLayer() >= 0 ? 1 : 0));
    }

    /**
     * 冲刺弹反：把贴身的敌弹折返成一发友军子弹。
     * 同一次冲刺里连续弹反按 streak 倍率计分，事件 code 带 streak 供音效升调；
     * 弹反同时补一点擦弹槽，让"莽进弹幕"成为一条正向回路。
     */
    private void parryEnemyBullets() {
        for (EnemyBullet b : enemyBullets) {
            if (!b.active) continue;
            float rr = Ship.RADIUS + b.radius + PARRY_BAND;
            if (distToSegment(b.x, b.y,
                    ship.prevX, ship.prevY, ship.x, ship.y) > rr) continue;
            b.active = false;
            parryStreak++;
            parries++;
            ultCharge(ULT_GAIN_PARRY, b.x, b.y);
            if (parryStreak > maxParryStreak) maxParryStreak = parryStreak;
            parryFlash = 1f;
            int gained = addScore(PARRY_SCORE * parryStreak, SCORE_OTHER);
            spawnParryBullet(b);
            if (ship.overdriveTimer <= 0f) {
                grazeCharge = Math.min(1f, grazeCharge + PARRY_GRAZE_CHARGE);
            }
            spark(b.x, b.y, 5, 6);
            spawnFloatText(b.x, b.y, gained, FLOAT_PARRY, parryStreak >= 2);
            events.add(new GameEvent(EventType.PARRY, b.x, b.y, parryStreak));
        }
    }

    /**
     * 生成弹反后的友军子弹：沿敌弹原方向折返，并保证至少朝屏幕上方飞，
     * 否则横着飞的敌弹反弹回来会立刻飞出屏幕，玩家拿不到任何收益。
     */
    private void spawnParryBullet(EnemyBullet src) {
        for (Bullet b : bullets) {
            if (b.active) continue;
            float vx = -src.vx * 1.2f;
            float vy = -src.vy * 1.2f;
            if (vy > -300f) vy = -900f;
            // 敌弹本身很慢，原样折返会变成一颗飘半天才出屏的"气球"，
            // 既没有射击手感又会堆积弹池，所以保底拉到正常弹速。
            float sp = (float) Math.sqrt(vx * vx + vy * vy);
            if (sp < PARRY_MIN_SPEED && sp > 1e-4f) {
                float k = PARRY_MIN_SPEED / sp;
                vx *= k;
                vy *= k;
            }
            b.init(src.x, src.y, vx, vy, PARRY_DAMAGE, false);
            bulletsFired++;   // 计入射出的弹，命中率不会被白送的弹拉爆
            return;
        }
    }

    /**
     * 结算一次命中的实际伤害：小概率暴击加成（过载期间概率翻倍）。
     * 暴击会累计 {@link #crits}，Renderer 用返回值是否为原值判断是否暴击色。
     */
    private int damageOrCrit(int base) {
        float chance = ship.overdriveTimer > 0f ? CRIT_CHANCE_OVERDRIVE : CRIT_CHANCE;
        // 异变"暴击临界"与今日规则：把已经存在的暴击判定放大，不新增判定
        chance *= critScaleOf();
        if (base > 1 && RNG.get().nextFloat() < Math.min(0.9f, chance)) {
            crits++;
            return Math.max(base + 1, Math.round(base * CRIT_MULT));
        }
        return base;
    }

    /**
     * Feeds the measured update+render cost of one frame into the adaptive
     * quality governor. Sustained over-budget frames halve particle emission;
     * sustained headroom restores full juice. Hysteresis on the moving
     * average prevents flapping between the two modes. A forced reduced-fx
     * setting (user toggle / low-RAM device) overrides the governor.
     */
    public void noteFrameCost(float seconds) {
        if (settings.reducedFx) {
            emissionScale = 0.5f;
            return;
        }
        if (seconds <= 0f || seconds > 0.5f) return; // ignore outliers (GC, background)
        frameCostAvg = frameCostAvg == 0f ? seconds : frameCostAvg * 0.93f + seconds * 0.07f;
        if (++frameSamples < 30) return; // warm-up: don't judge on a handful of frames
        if (frameCostAvg > FRAME_DROP) emissionScale = 0.5f;
        else if (frameCostAvg < FRAME_RECOVER) emissionScale = 1f;
    }

    private int frameSamples;

    /**
     * Seeds the governor with a device-capability prior (e.g. low-RAM
     * detection at startup) so slow devices get reduced fx from frame one
     * instead of after the 30-frame warm-up.
     */
    public void primeQuality(float scale) {
        emissionScale = scale;
        frameSamples = 30; // skip warm-up
    }

    /** Drains accumulated events so the presentation layer can react (sfx/haptics). */
    public List<GameEvent> pollEvents() {
        eventsOut.clear();
        List<GameEvent> tmp = events;
        events = eventsOut;
        eventsOut = tmp;
        return tmp;
    }
}
