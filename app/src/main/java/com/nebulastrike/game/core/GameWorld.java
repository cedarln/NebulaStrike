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
        ULT_LANCE
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
    public final EnemyBullet[] enemyBullets = new EnemyBullet[56];
    public final Enemy[] enemies = new Enemy[48];
    public final Particle[] particles = new Particle[256];
    public final PowerUp[] powerups = new PowerUp[20];
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
    /** local top-5 scores, descending; 0 = empty slot. Two boards: WAVES / ENDLESS */
    public final int[] leaderboard = new int[5];
    public final int[] leaderboardEndless = new int[5];
    /** 0-based rank of the last finished run on the board for the active mode; -1 = not on board */
    public int scoreRank = -1;
    public final Settings settings = new Settings();
    public volatile boolean settingsOpen;
    public volatile boolean cheatOpen;
    /** 图鉴面板（菜单内）。 */
    public volatile boolean codexOpen;
    /** 图鉴当前页签：0 = 敌机，1 = Boss。 */
    public volatile int codexTab;
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
    /** 独立瞄准的 Boss 激光（预警 -> 持续光束）。 */
    public final BossLaser[] bossLasers = new BossLaser[4];
    public int bossesDefeated;
    int nextBossWave = 4;
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

        for (Bullet b : bullets) {
            if (!b.active) continue;
            if (b.homing) steerMissile(b, dt);
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
        if (score > wave * 500) wave++;
    }

    // ---- boss battle ----

    private void updateBoss(float dt) {
        if (!boss.active) {
            for (BossLaser l : bossLasers) l.active = false;
            // trigger at wave milestones when the screen is calm-ish
            if (wave >= nextBossWave) {
                nextBossWave += mode == GameMode.ENDLESS ? 3 : 4;
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
        if (boss.hp <= 0) {
            boss.active = false;
            bossActive = false;
            bossesDefeated++;
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
                // 预警期间缓慢跟踪，给玩家留出横向位移的窗口
                float px = ship.x - l.x, py = ship.y - l.y;
                float desired = (float) Math.atan2(py, px);
                l.angle = lerpAngle(l.angle, desired, Math.min(1f, dt * 3.2f));
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
        switch (boss.phase) {
            case 1: // 简单扇形：随着 Boss 级别变宽
                fireFan(5 + tier * 2, 290f + tier * 18f, 0.19f);
                if (tier >= 2) fireAimed(2, 380f, 0.09f);
                break;
            case 2: // 扇形 + 双侧瞄准弹，每三次交替一次扩散脉冲环
                if (ring) {
                    fireRing(10 + tier * 3, 235f + tier * 12f);
                    fireAimed(2, 400f, 0.12f);
                } else {
                    fireFan(5 + tier, 315f + tier * 15f, 0.17f);
                    fireAimed(3, 410f, 0.12f);
                }
                break;
            default:
                if (ring && tier >= 2) {
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
        if (e.y < 60f || e.y > HEIGHT * 0.72f) return; // only fire while on-screen, not too close
        // 异变"铁雨"：冷却时间流逝更快，同样的开火表会变成双倍弹幕密度
        e.fireCd -= dt * (mutator.isActive() ? mutator.fireScale : 1f);
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
        enemySpeedScale *= settings.enemySpeedPct / 100f * settings.difficulty.speedScale;
        // 异变波：出怪节奏与敌机速度都是纯倍率，窗口一结束立刻回到正常值
        if (mutator.isActive()) {
            if (mutator.spawnScale != 1f) {
                spawnInterval = Math.max(0.12f, spawnInterval / mutator.spawnScale);
            }
            if (mutator.speedScale != 1f) enemySpeedScale *= mutator.speedScale;
        }
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

    /** 异变对新登场敌机的属性修正：只动血量，速度已经走 {@link #updateDifficulty}。 */
    private void applyMutatorToEnemy(Enemy e) {
        if (!mutator.isActive() || mutator.hpScale == 1f) return;
        e.hp = Math.max(0.5f, e.hp * mutator.hpScale);
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
        if (settings.difficulty == Difficulty.CASUAL) return ULT_CHARGE_CASUAL;
        if (settings.difficulty == Difficulty.HARDCORE) return ULT_CHARGE_HARDCORE;
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
        Difficulty.Penalty p = settings.difficulty.penalty;
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
        float secs = settings.difficulty.respawnInvuln;
        ship.makeInvulnerable(secs);
        respawnGrace = secs;
        clearBulletsAround(ship.x, ship.y, settings.difficulty.respawnClearRadius);
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
                if (settingsOpen || cheatOpen || codexOpen) {
                    closePanels();
                    return true;
                }
                return false;   // 裸主菜单：放行，让 Activity 正常退出
        }
    }

    private void updateSpawning(float dt) {
        if (!spawningEnabled) return;
        if (bossActive) return; // the boss owns the sky
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
        float chance = ELITE_BASE_CHANCE * settings.difficulty.eliteScale;
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
        // 保留至少 15% 无人机当"炮灰"：非无人机权重整体归一
        float sum = wTank + wHunter + wWeaver + wSui + wSplit + wHeal + wSnipe
                + wMiner + wShield + wCarrier;
        if (sum > 0.85f) {
            float k = 0.85f / sum;
            wTank *= k; wHunter *= k; wWeaver *= k;
            wSui *= k; wSplit *= k; wHeal *= k; wSnipe *= k;
            wMiner *= k; wShield *= k; wCarrier *= k;
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
                    grazeCharge = Math.min(1f, grazeCharge
                            + GRAZE_CHARGE_PER_BULLET
                            * (mutator.isActive() ? mutator.grazeScale : 1f));
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
                if (lives < Math.max(MAX_LIVES, settings.startLives)) lives++;
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
        float scale = settings.difficulty.scoreScale;
        if (scale != 1f) gained = Math.round(gained * scale);
        // 异变窗口：所有入账分数都加权，风险与回报同时兑现
        if (mutator.isActive() && mutator.scoreScale != 1f) {
            gained = Math.round(gained * mutator.scoreScale);
        }
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
        comboKills++;
        if (comboKills > maxCombo) maxCombo = comboKills;
        comboTimer = COMBO_WINDOW;
        int gained = addScore(type.score * (elite ? Enemy.ELITE_SCORE_MULT : 1)
                * comboMultiplier(), SCORE_ENEMY);
        // 击杀得分飘字：精英与重甲用大号，普通小怪只在分数可观时飘
        spawnFloatText(ex, ey - type.radius * 0.6f, gained, FLOAT_SCORE,
                elite || type.score >= 25 || comboMultiplier() >= 3);
        kills++;
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
                : type == EnemyType.HUNTER ? 0.12f
                : type == EnemyType.SUICIDER ? 0.04f : 0.05f;
        chance = Math.min(1f, chance * settings.dropRatePct / 100f
                    * settings.difficulty.dropScale
                    * (mutator.isActive() ? mutator.dropScale : 1f));
        if (!elite && RNG.get().nextFloat() >= chance) return; // 精英必掉
        spawnPowerUp(dropKind(RNG.get().nextFloat()), x, y);
    }

    /**
     * 掉落分布（v1.15）。BONUS 只由突袭奖励波投放，不在常规掉落表内。
     * 追踪弹 MISSILE 提到 14%（用户反馈"想要更多追踪弹"），
     * 尾部"特殊道具"区（0.87~1.0，共 13%）本轮由四类扩为五类：
     * BOUNCE 3%、NOVA 3%、NANITE 2.5%、GRAVITY 2.5%、TURRET 2%。
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
        if (roll < 0.78f) return PowerUp.Kind.X2;
        if (roll < 0.81f) return PowerUp.Kind.LIFE;
        if (roll < 0.84f) return PowerUp.Kind.WINGMAN;
        if (roll < 0.87f) return PowerUp.Kind.TIME;
        if (roll < 0.90f) return PowerUp.Kind.BOUNCE;
        if (roll < 0.93f) return PowerUp.Kind.NOVA;
        if (roll < 0.955f) return PowerUp.Kind.NANITE;
        if (roll < 0.98f) return PowerUp.Kind.GRAVITY;
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
        }
    }

    /** @return the board for the given mode. */
    public int[] board(GameMode m) {
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
                break;
            case OPEN_CHEATS:
                cheatOpen = true;
                settingsOpen = false;
                codexOpen = false;
                break;
            case OPEN_CODEX:
                codexOpen = true;
                settingsOpen = false;
                cheatOpen = false;
                flushCodex();
                break;
            case CODEX_BACK:
                codexOpen = false;
                break;
            case CODEX_TAB:
                codexTab = codexTab == 0 ? 1 : 0;
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

    /** 菜单里的设置/LAB/图鉴面板互斥；返回键统一关闭。 */
    public void closePanels() {
        settingsOpen = false;
        cheatOpen = false;
        codexOpen = false;
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
        }
    }

    private void onPlayerHit() {
        if (settings.godMode) return;
        if (ship.consumeShield()) { // 每一层护盾各挡一次伤害
            ship.makeInvulnerable(settings.difficulty.respawnInvuln);
            respawnGrace = settings.difficulty.respawnInvuln;
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
            if (score > bestScore) {
                bestScore = score;
                newBest = true;
                if (store != null) store.saveBestScore(bestScore);
            }
            scoreRank = insertScore(score, mode);
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
        mode = m;
        score = 0;
        lives = settings.startLives;
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
        formationTimer = m == GameMode.ENDLESS ? 6f : 8f;
        spawnTimer = 0f;
        state = State.PLAYING;
        ship.reset(WIDTH / 2f, HEIGHT - 140f);
        ship.powerLevel = settings.startPower;
        for (Bullet b : bullets) b.active = false;
        for (EnemyBullet b : enemyBullets) b.active = false;
        for (Enemy e : enemies) e.active = false;
        for (PowerUp p : powerups) p.active = false;
        for (Particle p : particles) p.active = false;
        for (FloatText f : floatTexts) f.active = false;
        floatBudget = 0;
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
        for (int i = 0; i < n; i++) {
            Particle p = freeParticle();
            float ang = RNG.get().nextFloat() * (float) Math.PI * 2f;
            float spd = 60f + RNG.get().nextFloat() * 160f;
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
        // 异变"暴击临界"：把已经存在的暴击判定放大，不新增判定
        if (mutator.isActive() && mutator.critScale != 1f) chance *= mutator.critScale;
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
