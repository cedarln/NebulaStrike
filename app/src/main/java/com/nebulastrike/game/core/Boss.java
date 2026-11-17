package com.nebulastrike.game.core;

/**
 * Capital-ship boss. Three HP-driven phases plus destructible appendages.
 * Later encounters grow physically larger and gain more guns / laser pods,
 * so fights escalate from a small scout carrier to a screen-filling leviathan.
 */
public class Boss {
    public static final int MAX_PARTS = 16;
    /** 三阶段召唤的棱镜数量（正反方向各一枚）。 */
    public static final int PRISM_COUNT = 2;

    public final BossPart[] parts = new BossPart[MAX_PARTS];
    public int partCount;
    /** visual hull radius; grows with boss level */
    public float radius = 92f;
    /** slightly smaller hitbox so grazing the hull edge is forgiving */
    public float hitRadius = 76f;
    public float arenaY = 150f;
    /**
     * 本场形态真正驻留的航道高度：{@code arenaY + form.lane}。
     * 贴顶的形态把舰艏压到屏幕上沿，压低的形态给自己留出纵向起伏的空间。
     */
    public float stationY = 150f;

    public float x, y;
    public float hp, maxHp;
    public int level;      // 0-based boss index, scales stats
    public int phase = 1;  // 1..3
    public float age;
    public float flash;
    public boolean active;
    /** set once every appendage has been destroyed */
    public boolean allPartsDown;
    /** >0 while the disarmed boss is staggered and cannot attack */
    public float staggerTimer;
    private float dir = 1f;
    private float attackCd = 2f;
    /** until next minion summon; package-private so tests can schedule one */
    float minionCd = 6f;
    /** 护甲（伤害倍率）与"存活护盾发生器"绑定，见 {@link #damageScale()} */
    private float ramCd = 9f;
    /** >0 = 正在横冲（相位撞击），期间 x 由冲刺控制、不再走正弦巡逻 */
    public float ramTimer;
    public float ramTargetX;
    /** 横冲速度（虚拟单位/秒） */
    public static final float RAM_SPEED = 1150f;
    private static final float RAM_DURATION = 0.5f;
    /** 三阶段棱镜召唤：已召唤过就不再重复，队列由 GameWorld 消费。 */
    public boolean prismsSummoned;
    public boolean prismQueued;
    /** v1.47 词缀与它带来的两项修正：攻击节奏倍率、舰体受击倍率。 */
    public BossAffix affix = BossAffix.NONE;
    public float tempo = 1f;
    public float armor = 1f;

    /** 走位模式：形态决定旗舰在场上怎么动。 */
    public static final int MOVE_PATROL = 0;
    public static final int MOVE_GLIDE = 1;
    public static final int MOVE_HOLD = 2;
    public static final int MOVE_FIGURE8 = 3;
    public static final int MOVE_DASH = 4;
    public static final int MOVE_BOB = 5;
    public static final int MOVE_SPIN = 6;

    /**
     * v1.61 旗舰形态 FORM：八段航程的旗舰各有各的剪影、走位和招牌招式。
     * 舰级（tier）只负责"多大、多硬"，形态负责"长什么样、怎么动、丢什么弹幕"。
     * lane 是它占的航道高度：负数贴顶、正数压低，配合起伏幅度决定吊舱能不能被够着。
     *
     * <p>v1.62 形态再开口、再交货：taunt / rage 是它在预警横幅与三阶段换段时说的两句话，
     * supply 是它被击沉时必定多掉的那件补给——主题上正好是"用它的办法还治它自己"。
     */
    public enum Form {
        /** 静默：慢滑 + 大起伏，二阶段起会闪现换位，招牌是高速针雨。 */
        SPECTER("静默", "闪现换位 · 高速针雨", MOVE_GLIDE, 0.2f, -6f,
                "你连我在哪儿都没记住。", "我在这儿。不在这儿。在这儿。",
                PowerUp.Kind.REFLECT, "针出厂就锁死了位置 · 出手那刻横向一让",
                "三重针幕", "三层针错开落点 · 躲过第一层还得往里收半步", "两翼拉开", "先拆翼尖"),
        /** 灰烬：压得最低的巡逻舰，招牌是把全场盖住的落灰。 */
        CINDER("灰烬", "贴顶巡逻 · 落灰覆盖", MOVE_PATROL, 0.3f, -14f,
                "灰烬会落满你所有的空隙。", "灰烬底下还是火。",
                PowerUp.Kind.NOVA, "落灰之间留着空巷 · 挑最宽那道走",
                "灰烬塌陷", "灰烬塌两层 · 第一层底下压着更快的第二层", "前压一字", "自右向左"),
        /** 船坞：几乎停在原地，靠一波波僚机和护航弹压人。 */
        DOCKYARD("船坞", "定点放机 · 护航弹幕", MOVE_HOLD, 0.15f, 26f,
                "我不需要亲自动手。", "全部出击。",
                PowerUp.Kind.ARC, "扇面慢得来得及绕 · 先清僚机再说",
                "全甲板出击", "快扇从慢扇背后穿出 · 让开慢的未必让得开快的", "双层甲板", "先拆下甲板"),
        /** 蜂巢：8 字环绕 + 自旋，招牌是切通道的水雷带。 */
        HIVE("蜂巢", "八字自旋 · 漂流水雷", MOVE_FIGURE8, 1.1f, 10f,
                "通道正在合上。", "巢破了，蜂还在。",
                PowerUp.Kind.BOOMERANG, "水雷往中间合 · 顺它让开的那条缝走",
                "巢门合拢", "两道水雷一先一后 · 等第一道让开再走同一条缝", "六格巢环", "顺环而拆"),
        /** 裂谷：短距高速横跳，招牌是贯穿全屏的交叉束。 */
        RIFT("裂谷", "高速横跳 · 交叉光束", MOVE_DASH, 0.4f, 0f,
                "别眨眼，我在你身后。", "追得上我吗？",
                PowerUp.Kind.TIME, "交叉束只从两侧射 · 守中线最稳",
                "三面裂束", "三束里中线那束最慢 · 交叉过了再回中线", "斜交叉架", "顺斜线拆"),
        /** 磁暴：上下浮动的极板舰，招牌是慢速追踪磁球。 */
        MAGNET("磁暴", "纵向浮动 · 追踪磁球", MOVE_BOB, 0.5f, 72f,
                "你会自己把弹送过来。", "你的弹现在听我的。",
                PowerUp.Kind.MAGNET, "磁球追得慢 · 绕大圈把它拖出屏幕",
                "磁极旋涡", "漩涡球绕大圈才收口 · 往边角拖，别在中央转身", "双极团簇", "先拆一极"),
        /** 镜面：中心悬停自旋，招牌是左右完全对称的镜像环。 */
        MIRROR("镜面", "悬停自旋 · 镜像双环", MOVE_SPIN, 0.75f, 18f,
                "你打的每一个我，都是真的。", "碎一片，照出两片。",
                PowerUp.Kind.BOUNCE, "镜像环左右同相 · 沿中线直穿就行",
                "千镜叠影", "四层环仍是同相 · 中线一次穿过去，别回头补躲", "内外双环", "先外后内"),
        /** 星核：最终旗舰，棱镜 + 星核爆裂，招式最杂。 */
        SENTINEL("星核", "棱镜护环 · 星核爆裂", MOVE_PATROL, 0.35f, -8f,
                "回溯到此为止，星火号。", "我是最后一道回声。",
                PowerUp.Kind.LIFE, "星核散得匀 · 侧移半个弹距就漏过去",
                "星核崩塌", "崩塌一圈套一圈 · 贴着舰影横移比后退快", "护心弧墙", "先破墙心");

        public final String label;
        /** 图鉴与横幅用的一句话：它怎么动 + 它的招牌招式。 */
        public final String signature;
        public final int moveMode;
        public final float spinRate;
        /** 航道偏移（虚拟单位）：贴顶的把舰艏压到屏幕上沿，压低的给自己留出起伏空间。 */
        public final float lane;
        /** 入场台词：预警横幅上它开口的那一句。 */
        public final String taunt;
        /** 三阶段台词：换段横幅上它认真起来的那一句。 */
        public final String rage;
        /**
         * 形态补给：击沉这艘旗舰额外必掉的一件道具。只加不减——
         * 原有的护盾 / 炸弹 / 火力 / 武器与后期机制道具照常掉落，道具一律叠加。
         */
        public final PowerUp.Kind supply;
        /**
         * v1.63 破招提示：这一型的招牌弹幕该怎么读。招牌出手时跟着招式名一起打在舰体下方，
         * 整轮弹幕散尽而没打中玩家就算"识破"，额外给一笔赏金（{@link GameWorld#countersRead}）。
         */
        public final String counter;
        /**
         * v1.68 终式招式名：三阶段（也就是说完那句狠话）之后，这一型在招牌之外多出来的一手。
         * 招牌与终式交替出手，读招账本仍然按形态算——终式读懂了一样往同一格里记。
         */
        public final String finale;
        /** v1.68 终式的破招提示，与 {@link #counter} 同一套口径，只是这一手更狠、读法也不一样。 */
        public final String finaleCounter;
        /**
         * v1.70 舰装排布：这一型的部件挂在船体的哪些位置。件数与种类仍然只看舰级，
         * 形态只改"这些件摆在哪儿"——八型八种攻坚顺序，先拆哪一件、哪件挡在核心前面各不一样。
         */
        public final String loadout;
        /**
         * v1.71 攻坚路线：这一型的舰装该按什么顺序拆。路线只是把 {@link #loadout}
         * 那句排布话翻成一句动作指令——真正好不好走由 {@link #breachOrder} 从站位坐标算出来，
         * 玩家顺着拆一路有加分，插队只会把连段清零，不会扣掉已经到手的东西。
         */
        public final String breach;

        Form(String label, String signature, int moveMode, float spinRate, float lane,
             String taunt, String rage, PowerUp.Kind supply, String counter,
             String finale, String finaleCounter, String loadout, String breach) {
            this.label = label;
            this.signature = signature;
            this.moveMode = moveMode;
            this.spinRate = spinRate;
            this.lane = lane;
            this.taunt = taunt;
            this.rage = rage;
            this.supply = supply;
            this.counter = counter;
            this.finale = finale;
            this.finaleCounter = finaleCounter;
            this.loadout = loadout;
            this.breach = breach;
        }
    }

    /** 常规旗舰的形态循环：七种形态对应前七段航程，第八段由 {@link Form#SENTINEL} 压阵。 */
    private static final Form[] FORM_CYCLE = {
            Form.SPECTER, Form.CINDER, Form.DOCKYARD, Form.HIVE,
            Form.RIFT, Form.MAGNET, Form.MIRROR
    };

    /** 第 level 艘常规旗舰的形态（无尽与回声周目按同一循环滚回来，只是舰级更高）。 */
    public static Form formFor(int level) {
        return FORM_CYCLE[Math.floorMod(level, FORM_CYCLE.length)];
    }

    /** 形态名（无实例场景用，例如图鉴按档位取第一艘的形态）。 */
    public static String formLabelFor(int level) {
        return formFor(level).label;
    }

    /** 本场的形态。 */
    public Form form = Form.SPECTER;
    /** 舰体自旋角（弧度），旋转系形态会真的转起来。 */
    public float spin;
    /** 闪现残影强度（>0 时渲染层画一层错位重影）。 */
    public float blink;
    /** 走位锚点 x：定点与环绕系形态绕着它移动。 */
    public float anchorX = GameWorld.WIDTH / 2f;
    /** 是否已经入场就位（到位后各形态才开始走自己的轨迹）。 */
    private boolean arrived;
    /** 下一次闪现 / 横跳的倒计时。 */
    private float specialCd = 2.4f;
    /** 横跳形态的目标 x。 */
    private float dashTargetX;

    public Boss() {
        for (int i = 0; i < parts.length; i++) parts[i] = new BossPart();
    }

    public void init(int level) {
        this.level = level;
        this.tier = Math.min(level, 3);
        this.maxHp = 420f + 200f * level;
        this.hp = maxHp;
        this.radius = 60f + 14f * tier;
        this.hitRadius = radius * 0.84f;
        this.arenaY = 120f + radius * 0.35f;
        this.x = GameWorld.WIDTH / 2f;
        this.y = -radius - 20f;
        this.age = 0f;
        this.flash = 0f;
        this.phase = 1;
        this.active = true;
        this.dir = GameWorld.RNG.get().nextBoolean() ? 1f : -1f;
        this.attackCd = 2.2f;
        this.minionCd = 6f;
        this.allPartsDown = false;
        this.staggerTimer = 0f;
        this.ramCd = 8f - 1.2f * tier;
        this.ramTimer = 0f;
        this.ramTargetX = this.x;
        this.prismsSummoned = false;
        this.prismQueued = false;
        this.affix = BossAffix.NONE;
        this.tempo = 1f;
        this.armor = 1f;
        // v1.61 形态：每段航程换一艘长得不一样、动得也不一样、招式更不一样的旗舰
        applyForm(formFor(level));
        this.spin = 0f;
        this.blink = 0f;
        this.anchorX = GameWorld.WIDTH / 2f;
        this.arrived = false;
        this.specialCd = 2.4f;
        this.dashTargetX = this.x;
        layoutParts();
    }

    /** 0..3: shared by art, attack density and component count. */
    public int tier;

    public String title() {
        return titleFor(tier);
    }

    /** 图鉴等无实例场景按等级取名。 */
    public static String titleFor(int tier) {
        switch (tier) {
            case 0: return "SCOUT CARRIER";
            case 1: return "DREADNOUGHT";
            case 2: return "LEVIATHAN";
            default: return "WORLD EATER";
        }
    }

    /**
     * 舰装排布（v1.70）：<b>件数、种类、半径、血量、开火节奏全都只看舰级</b>——v1.31 起那本平衡账
     * 一个字都没动。形态只决定一件事：这些件<b>摆在船体的哪儿</b>。静默把炮推到两翼尖、
     * 船坞摊成上下两层甲板、星核拿部件在核心前面垒一道弧墙。同一艘船换个形态，
     * 就是换一套攻坚顺序：先拆哪一件、哪件挡在核心前面，八型八种答案。
     */
    void layoutParts() {
        float r = radius;
        float pr = clamp(radius * 0.24f, 15f, 26f);
        int guns = tier == 0 ? 2 : tier == 1 ? 4 : tier == 2 ? 4 : 5;
        // 第二个 Boss 起出现激光吊舱，数量随舰体等级增长
        int lasers = tier >= 2 ? 2 : tier == 1 ? 1 : 0;
        int pods = tier >= 3 ? 2 : tier >= 1 ? 1 : 0;      // 分裂舱：投放僚机
        int shields = tier >= 3 ? 2 : tier >= 2 ? 1 : 0;   // 护盾发生器：削弱核心伤害
        int mineBays = tier >= 2 ? 1 : 0;                  // 布雷舱：底部通道投放漂流水雷
        int n = guns + lasers + pods + shields + mineBays;
        BossPart.Kind[] kind = new BossPart.Kind[parts.length];
        float[] rad = new float[parts.length];
        float[] hp = new float[parts.length];
        float[] cd = new float[parts.length];
        float gunHp = 26f + 9f * level;
        float laserHp = 34f + 12f * level;
        float podHp = 30f + 10f * level;
        float shieldHp = 40f + 14f * level;
        float mineHp = 34f + 11f * level;
        // 先按旧顺序排成一本清单（GUN → LASER → POD → SHIELD → MINE），再按形态把它们摆上站位
        int at = 0;
        for (int i = 0; i < guns; i++) {
            kind[at] = BossPart.Kind.GUN;
            rad[at] = pr;
            hp[at] = gunHp;
            cd[at] = 1.3f + i * 0.3f;
            at++;
        }
        for (int i = 0; i < lasers; i++) {
            kind[at] = BossPart.Kind.LASER;
            rad[at] = pr * 1.08f;
            hp[at] = laserHp;
            cd[at] = 3.4f + i * 0.9f;
            at++;
        }
        for (int i = 0; i < pods; i++) {
            kind[at] = BossPart.Kind.POD;
            rad[at] = pr * 1.15f;
            hp[at] = podHp;
            cd[at] = 4.2f + i * 1.6f;
            at++;
        }
        for (int i = 0; i < shields; i++) {
            kind[at] = BossPart.Kind.SHIELD;
            rad[at] = pr * 1.05f;
            hp[at] = shieldHp;
            cd[at] = -1f;
            at++;
        }
        for (int i = 0; i < mineBays; i++) {
            kind[at] = BossPart.Kind.MINE;
            rad[at] = pr * 0.94f;
            hp[at] = mineHp;
            cd[at] = 3.4f + i * 1.2f;
            at++;
        }
        float[][] station = formStations(form, at);
        int[] rank = breachOrder(form, at);
        for (int i = 0; i < parts.length; i++) parts[i].active = false;
        partCount = 0;
        for (int i = 0; i < at && i < parts.length; i++) {
            parts[i].init(kind[i], station[i][0] * r, station[i][1] * r,
                    rad[i], hp[i], cd[i]);
            // v1.71：站位定下来顺手把拆卸层号也发下去。棱镜舱（PRISM）是开打后追加的，
            // 不在这本舰装账上，层号留 -1——拆它既不算顺线也不算插队。
            parts[i].breachRank = rank[i];
            partCount = i + 1;
        }
    }

    /**
     * v1.71 攻坚路线：把 {@link #formStations} 摆好的站位排成一个拆卸顺序，返回每个部件的<b>层号</b>
     * （0 = 最先该拆的那一层）。
     *
     * <p>同一层的件不分先后：静默的镜像双翼、船坞的同一条甲板，玩家同时够得着，
     * 逼他二选一只是罚错人。所以这里发的是"层"而不是"名次"，同键（差在 1e-4 内）算一层。
     * 排布是确定的，层号自然也是——每一艘船的路线开打前就已经定了。
     */
    static int[] breachOrder(Form form, int n) {
        float[][] p = formStations(form, n);
        int m = p.length;
        float[] key = new float[m];
        for (int i = 0; i < m; i++) key[i] = breachKey(form, p[i][0], p[i][1]);
        int[] order = new int[m];
        for (int i = 0; i < m; i++) order[i] = i;
        // 件数封顶 16，直接选择排序：同键的按下标定序，不依赖任何排序实现
        for (int a = 0; a < m; a++) {
            int best = a;
            for (int b = a + 1; b < m; b++) {
                int x = order[b], y = order[best];
                boolean takes = key[x] < key[y] - 1e-4f
                        || (Math.abs(key[x] - key[y]) <= 1e-4f && x < y);
                if (takes) best = b;
            }
            int t = order[a];
            order[a] = order[best];
            order[best] = t;
        }
        int[] rank = new int[m];
        int level = -1;
        for (int a = 0; a < m; a++) {
            if (a == 0 || key[order[a]] > key[order[a - 1]] + 1e-4f) level++;
            rank[order[a]] = level;
        }
        return rank;
    }

    /** 各型的拆卸键，键小的先拆——这条键就是 {@link Form#breach} 那句话的数学写法。 */
    private static float breachKey(Form form, float x, float y) {
        switch (form) {
            case SPECTER:                                  // 两翼拉开 → 从翼尖往中线收
                return -Math.abs(x);
            case CINDER:                                   // 前压一字 → 自右向左一路刮过去
                return -x;
            case DOCKYARD:                                 // 双层甲板 → 贴近玩家的下甲板先刮平
                return -y;
            case HIVE:                                     // 六格巢环 → 顺着环挨件拆
                return (float) Math.atan2(y, x);
            case RIFT:                                     // 斜交叉架 → 沿斜线一件接一件
                return -(x + y);
            case MAGNET:                                   // 双极团簇 → 整极拆完再换另一极
                return -x;
            case MIRROR:                                   // 内外双环 → 外环剥完才轮得到内环
                return -(float) Math.hypot(x, y);
            case SENTINEL:                                 // 护心弧墙 → 从正对玩家的墙心凿开
                return Math.abs(x);
            default:
                return 0f;
        }
    }

    /** 站位之间的最小间距（归一化舰半径）：叠成一件的部件既看不出两种打法，也点不着第二件。 */
    private static final float STATION_MIN_GAP = 0.26f;
    /** 互相推开再收边的轮数：站位形状是给定的，几轮就收敛。 */
    private static final int STATION_SETTLE_ROUNDS = 24;
    /** 站位包络：横向不推出舰影，纵向既不贴屏幕上沿也不压到核心底下。 */
    private static final float STATION_MAX_X = 1.05f;
    private static final float STATION_MIN_Y = -0.7f;
    private static final float STATION_MAX_Y = 0.96f;

    /**
     * 按形态生成 n 个舰装站位（归一化到舰体半径）。形状左右成对，n 为奇数时中位落在轴线上；
     * 生成后收进包络并互相推开到最小间距。包内可见：舰装排布的全部规则都在这一个函数里，
     * 测试直接按形态与件数预演，不需要真开一局。
     */
    static float[][] formStations(Form form, int n) {
        float[][] p = new float[Math.max(0, n)][2];
        int pairs = n / 2;
        for (int k = 0; k < pairs; k++) {
            // u：从里侧那一翼走到外侧，左右两翼共用同一个层次，保证镜像成对
            float u = pairs == 1 ? 0.5f : k / (float) (pairs - 1);
            float[] side = stationFor(form, u);
            p[k][0] = -side[0];
            p[k][1] = side[1];
            p[n - 1 - k][0] = side[0];
            p[n - 1 - k][1] = side[1];
        }
        if (n % 2 == 1) {
            float[] mid = middleStationFor(form);
            p[pairs][0] = mid[0];
            p[pairs][1] = mid[1];
        }
        for (int round = 0; round < STATION_SETTLE_ROUNDS; round++) {
            for (float[] q : p) {
                q[0] = clamp(q[0], -STATION_MAX_X, STATION_MAX_X);
                q[1] = clamp(q[1], STATION_MIN_Y, STATION_MAX_Y);
            }
            for (int i = 0; i < p.length; i++) {
                for (int j = i + 1; j < p.length; j++) {
                    float dx = p[j][0] - p[i][0];
                    float dy = p[j][1] - p[i][1];
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    if (d >= STATION_MIN_GAP) continue;
                    if (d < 1e-4f) {
                        // 完全重合：按站位序左右岔开，不然推不动
                        dx = (i % 2 == 0 ? -1f : 1f) * STATION_MIN_GAP;
                        dy = 0f;
                        d = STATION_MIN_GAP;
                    }
                    float push = (STATION_MIN_GAP - d) * 0.5f / d;
                    p[i][0] -= dx * push;
                    p[i][1] -= dy * push;
                    p[j][0] += dx * push;
                    p[j][1] += dy * push;
                }
            }
        }
        for (float[] q : p) {
            q[0] = clamp(q[0], -STATION_MAX_X, STATION_MAX_X);
            q[1] = clamp(q[1], STATION_MIN_Y, STATION_MAX_Y);
        }
        return p;
    }

    /** 右半侧的舰装站位（左半边镜像出来）：u=0 是最靠中线的那一层，u=1 是最外那一层。 */
    private static float[] stationFor(Form form, float u) {
        switch (form) {
            case SPECTER:
                // 两翼拉开：炮位一路推到翼尖，越往外压得越低，逼玩家横着追
                return new float[]{0.42f + 0.6f * u, -0.48f + 0.62f * u};
            case CINDER:
                // 前压一字：所有件摊在舰体下缘一条弧上，正面能同时够着三件
                return new float[]{0.18f + 0.72f * u, 0.82f - 0.24f * u};
            case DOCKYARD:
                // 双层甲板：件按由内到外分给两条线——靠中线的那半排在下甲板（y=0.52，冲玩家这侧，
                // 也就是 v1.71「先拆下甲板」那条路线的第一层），往外那半退到上甲板（y=-0.06，
                // 贴着舰影上沿），舰级越高两条线各 6 件
                return u < 0.5f
                        ? new float[]{0.3f + 0.66f * (u * 2f), 0.52f}
                        : new float[]{0.3f + 0.66f * ((u - 0.5f) * 2f), -0.06f};
            case HIVE:
                // 六格巢环：件沿舰体围成一圈斜臂，环上处处都有东西要拆
                return new float[]{0.75f - 0.42f * u, -0.55f + 1.05f * u};
            case RIFT:
                // 斜交叉架：两对对角线拉开，跟它的交叉光束一个方向
                return new float[]{0.24f + 0.72f * u, -0.62f + 1.15f * u};
            case MAGNET:
                // 双极团簇：件挤在左右两个极点上，一簇一簇地拆
                return new float[]{0.62f + 0.28f * u, -0.2f + 0.42f * u};
            case MIRROR: {
                // 内外双环：内外两圈交替，外环比内环矮一点
                float rr = 0.5f + 0.48f * (u * 2f % 1f < 0.5f ? 0f : 1f);
                float ang = 0.2f + 1.15f * u;
                return new float[]{rr * (float) Math.sin(ang), -rr * (float) Math.cos(ang) + 0.18f};
            }
            default:
                // 护心弧墙：件在核心前面垒成一道朝玩家的弧，想打船体得先破墙
                return new float[]{0.93f - 0.8f * u, 0.32f + 0.6f * u};
        }
    }

    /** 奇数件时落在轴线上的那一件（布雷舱、单激光吊舱这些本来就该在正中）。 */
    private static float[] middleStationFor(Form form) {
        switch (form) {
            case SPECTER:
                return new float[]{0f, -0.6f};
            case CINDER:
                return new float[]{0f, 0.9f};
            case DOCKYARD:
                return new float[]{0f, 0.52f};
            case HIVE:
                return new float[]{0f, -0.66f};
            case RIFT:
                return new float[]{0f, 0.6f};
            case MAGNET:
                return new float[]{0f, -0.62f};
            case MIRROR:
                return new float[]{0f, -0.68f};
            default:
                return new float[]{0f, 0.94f};
        }
    }

    public void update(float dt) {
        age += dt;
        if (flash > 0f) flash = Math.max(0f, flash - dt);
        if (staggerTimer > 0f) staggerTimer = Math.max(0f, staggerTimer - dt);
        for (int i = 0; i < partCount; i++) {
            BossPart p = parts[i];
            p.update(dt);
            if (p.active && p.kind == BossPart.Kind.PRISM) {
                p.orbit += p.spin * dt;
                p.ox = (float) Math.cos(p.orbit) * radius * 1.18f;
                p.oy = (float) Math.sin(p.orbit) * radius * 0.6f - radius * 0.1f;
            }
        }

        float hpFrac = hp / maxHp;
        int newPhase = hpFrac > 0.66f ? 1 : (hpFrac > 0.33f ? 2 : 3);
        if (newPhase != phase) {
            phase = newPhase;
            flash = 0.3f;
            if (phase >= 3 && tier >= 1) prismQueued = true;
        }

        // entry glide then form-specific station-keeping; later bosses move a bit quicker
        float speed = 55f + phase * 24f + tier * 7f;
        if (ramTimer > 0f) {
            // 横冲：向目标侧高速平移，撞到底部边缘再停下
            ramTimer -= dt;
            float to = ramTargetX - x;
            float step = RAM_SPEED * dt;
            if (Math.abs(to) <= step) x = ramTargetX;
            else x += (to > 0f ? step : -step);
            y = laneY(8f, 6f);
            if (ramTimer <= 0f) ramTimer = 0f;
            return;
        }
        if (blink > 0f) blink = Math.max(0f, blink - dt * 2.4f);
        spin += form.spinRate * dt;
        if (!arrived) {
            // 入场滑入：一旦落到形态航道高度就"上锁"，之后各形态按自己的轨迹走
            y += 90f * dt;
            if (y >= stationY) { y = stationY; arrived = true; }
        } else {
            moveByForm(dt, speed);
        }

        if (staggerTimer <= 0f) {
            attackCd -= dt * (0.8f + 0.25f * phase) * tempo;
            minionCd -= dt;
            ramCd -= dt;
        }
    }

    /**
     * 换上某个形态，并把航道基准挪到它该在的高度。最终旗舰、回声、模拟战都走这里，
     * 保证"形态"和"它占的那条航道"永远成对出现。
     */
    public void applyForm(Form next) {
        form = next;
        stationY = clampStation(arenaY + next.lane);
    }

    /**
     * 航道高度收口：舰体上沿与顶部吊舱不能被推出屏幕上沿——一旦画到画布外，
     * 玩家既打不到护盾发生器，也看不出它下一步要丢什么。
     */
    private float clampStation(float want) {
        float lowest = radius * 0.78f + 30f;
        return want < lowest ? lowest : want;
    }

    /** 纵向波形：围绕 stationY 起伏，并对屏幕上沿收口。 */
    private float laneY(float amp, float freq) {
        return clampStation(stationY + (float) Math.sin(age * freq) * amp);
    }

    /**
     * 形态走位：七种旗舰七种轨迹。测试里可以直接 {@link #update(float)} 观察 x / y，
     * 因此这里只保证不越界（左右各留 30 虚拟单位的边距），不保证可预测的相位。
     */
    private void moveByForm(float dt, float speed) {
        float left = radius + 30f;
        float right = GameWorld.WIDTH - radius - 30f;
        float mid = GameWorld.WIDTH / 2f;
        switch (form.moveMode) {
            case MOVE_GLIDE: {
                // 静默：慢速滑行 + 大幅起伏，二阶段起会闪现到镜像位置
                x += dir * speed * 0.42f * dt;
                if (x < left) { x = left; dir = 1f; }
                if (x > right) { x = right; dir = -1f; }
                y = laneY(62f, 0.55f);
                if (staggerTimer > 0f) break;
                specialCd -= dt;
                if (specialCd <= 0f && phase >= 2) {
                    specialCd = 3.4f - 0.5f * phase;
                    x = clamp(GameWorld.WIDTH - x, left, right);
                    dir = x < mid ? 1f : -1f;
                    blink = 1f;
                    flash = Math.max(flash, 0.18f);
                }
                break;
            }
            case MOVE_HOLD: {
                // 船坞：定点悬停，只留极小的起伏，把注意力全放在放僚机上
                x += (anchorX - x) * Math.min(1f, dt * 0.55f);
                y = laneY(10f, 0.8f);
                break;
            }
            case MOVE_FIGURE8: {
                // 蜂巢：绕锚点画 8 字，横向慢、纵向快，舰体同步自旋
                x = clamp(anchorX + (float) Math.sin(age * 0.62f) * (mid - left) * 1.5f, left, right);
                y = laneY(54f, 1.24f);
                break;
            }
            case MOVE_DASH: {
                // 裂谷：短距高速横跳，每次必然跳到航道的另一侧
                if (staggerTimer <= 0f) {
                    specialCd -= dt;
                    if (specialCd <= 0f) {
                        specialCd = 1.45f - 0.13f * phase;
                        float span = mid - left;
                        float side = x <= mid ? 1f : -1f;
                        float reach = span * (0.55f + 0.4f * GameWorld.RNG.get().nextFloat());
                        dashTargetX = clamp(mid + side * reach, left, right);
                        flash = Math.max(flash, 0.12f);
                    }
                }
                float dx = dashTargetX - x;
                float step = (430f + 90f * phase) * dt;
                x += Math.abs(dx) <= step ? dx : Math.copySign(step, dx);
                x = clamp(x, left, right);
                y = laneY(18f, 2.1f);
                break;
            }
            case MOVE_BOB: {
                // 磁暴：极板舰，横向几乎不动，纵向大幅浮动
                x = clamp(anchorX + (float) Math.sin(age * 0.42f) * (mid - left) * 0.8f, left, right);
                y = laneY(88f, 1.15f);
                break;
            }
            case MOVE_SPIN: {
                // 镜面：压在中线自旋，只留一点呼吸感
                x += (anchorX - x) * Math.min(1f, dt * 0.9f);
                y = laneY(22f, 0.9f);
                break;
            }
            default: {
                // 巡逻（灰烬 / 星核）：经典的左右压线
                x += dir * speed * dt;
                if (x < left) { x = left; dir = 1f; }
                if (x > right) { x = right; dir = -1f; }
                y = laneY(26f, 0.9f);
            }
        }
    }

    /** 本形态的走位模式名，供图鉴与调试文案。 */
    public int moveMode() {
        return form.moveMode;
    }

    /** 是否已经落到航道高度：入场滑入完成，或被外部直接摆到位（图鉴、模拟战、测试）。 */
    public boolean inPosition() {
        return arrived || y >= stationY - 4f || y >= arenaY - 4f;
    }

    /** 横冲（撞击招式）是否可触发：第二艘 Boss 起、进入二阶段后。 */
    public boolean readyToRam() {
        return tier >= 1 && phase >= 2 && staggerTimer <= 0f && ramTimer <= 0f
                && ramCd <= 0f && inPosition() && !partsAllDestroyed();
    }

    /** 开始一次横冲：冲向远离当前的一侧，扫过全场。 */
    public void startRam() {
        ramTimer = RAM_DURATION;
        ramCd = 9.5f - 0.9f * tier - 0.5f * phase;
        ramTargetX = x < GameWorld.WIDTH / 2f
                ? GameWorld.WIDTH - radius - 30f
                : radius + 30f;
        flash = 0.24f;
    }

    /** 部件 destroyed 后不再阻挡巡逻，但横冲需要立刻停止。 */
    public void cancelRam() {
        ramTimer = 0f;
    }

    public boolean isRamming() {
        return ramTimer > 0f;
    }

    public boolean readyToAttack() {
        return staggerTimer <= 0f && y > 80f && attackCd <= 0f;
    }

    public void resetAttack() {
        attackCd = phase == 3 ? 1.1f : phase == 2 ? 1.5f : 1.9f;
    }

    public boolean readyToSummon() {
        return staggerTimer <= 0f && phase >= 2 && y > 120f && minionCd <= 0f;
    }

    public void resetSummon() {
        minionCd = 6.5f;
    }

    /** 三阶段棱镜召唤：第二艘 Boss 起、入场就位、从硬直中恢复后才允许。 */
    public boolean readyToSummonPrism() {
        return tier >= 1 && phase >= 3 && prismQueued && !prismsSummoned
                && staggerTimer <= 0f && inPosition();
    }

    /** 召唤一对反向环绕的棱镜，加到部件末尾；返回实际数量。 */
    public int summonPrisms() {
        if (tier < 1 || prismsSummoned) return 0;
        prismsSummoned = true;
        prismQueued = false;
        int made = 0;
        float pr = clamp(radius * 0.2f, 14f, 24f);
        float hp = 30f + 10f * level;
        for (int i = 0; i < PRISM_COUNT && partCount < MAX_PARTS; i++) {
            BossPart p = parts[partCount];
            float sign = i == 0 ? 1f : -1f;
            p.init(BossPart.Kind.PRISM, sign * radius * 1.18f, -radius * 0.1f,
                    pr, hp, 1.8f + i * 1.5f);
            p.orbit = i == 0 ? 0f : (float) Math.PI;
            p.spin = sign * (1.05f + 0.08f * tier);
            p.ox = (float) Math.cos(p.orbit) * radius * 1.18f;
            partCount++;
            made++;
        }
        flash = 0.35f;
        return made;
    }

    public int prismsAlive() {
        int n = 0;
        for (int i = 0; i < partCount; i++) {
            BossPart p = parts[i];
            if (p.active && p.kind == BossPart.Kind.PRISM) n++;
        }
        return n;
    }

    public void damage(int amount) {
        // 词缀护甲（磐垒）与护盾发生器的减伤叠乘：两条都活着时最硬
        hp -= amount * damageScale() * armor;
        flash = 0.06f;
    }

    /** 存活的护盾发生器数量（决定减伤倍率）。 */
    public int shieldGeneratorsAlive() {
        int n = 0;
        for (int i = 0; i < partCount; i++) {
            BossPart p = parts[i];
            if (p.active && p.kind == BossPart.Kind.SHIELD) n++;
        }
        return n;
    }

    /** 核心伤害倍率：护盾发生器存活时打折（两个更硬）。 */
    public float damageScale() {
        int n = shieldGeneratorsAlive();
        if (n <= 0) return 1f;
        return n >= 2 ? 0.5f : 0.68f;
    }

    /** 护盾是否仍在线（渲染层画护盾壳、音效层用）。 */
    public boolean shielded() {
        return shieldGeneratorsAlive() > 0;
    }

    /** Called when the last appendage is destroyed: brief damage window. */
    public void disarm() {
        allPartsDown = true;
        staggerTimer = 3f;
        attackCd = Math.max(attackCd, 2.2f);
    }

    public int partsAlive() {
        int n = 0;
        for (int i = 0; i < partCount; i++) if (parts[i].active) n++;
        return n;
    }

    public boolean partsAllDestroyed() {
        for (int i = 0; i < partCount; i++) if (parts[i].active) return false;
        return partCount > 0;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
