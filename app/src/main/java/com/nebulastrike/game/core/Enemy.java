package com.nebulastrike.game.core;

/**
 * Enemy ship. Movement pattern depends on {@link EnemyType}.
 * 需要瞄准/追踪飞船的兵种走 4 参 {@link #update(float, float, float, float)}；
 * 2 参重载保持旧调用点与测试兼容（等价于飞船在屏幕下方中央）。
 */
public class Enemy {
    public EnemyType type;
    public float x, y;
    public float hp;
    /** 满血值：医疗艇回血不能超过它，狙击塔悬停也需要知道自己是否受损。 */
    public float maxHp;
    public boolean active;
    /** age in seconds, used for sine/zig-zag patterns */
    public float age;
    /** hit flash timer for renderer */
    public float flash;
    /** cooldown until next shot; <=0 means this archetype does not fire */
    public float fireCd;
    /** 医疗艇的治疗冷却（<=0 表示本帧不治疗，由 GameWorld 统一结算）。 */
    public float healCd;
    /** 狙击塔悬停剩余时间，耗尽后继续俯冲离场。 */
    public float hoverCd;
    /** 螺旋炮台当前旋臂角度；每吐一轮转 0.5 弧度。 */
    public float spiralAngle;
    /** 螺旋炮台本轮剩余的连发轮数（0 = 下一发开始新的一轮）。 */
    public int burstCount;
    /** v1.38 切割者的光束阶段：0 巡航 1 瞄准充能 2 切割中。 */
    public int beamPhase;
    /** 充能 / 切割的剩余时间。 */
    public float beamTimer;
    /** 本次光束是否已经命中过（一发只算一次伤害）。 */
    public boolean beamHit;
    /** 光束刚切下去（一次性标记，由 GameWorld 消费并播表现）。 */
    public boolean beamJustFired;
    /** v1.42 旋翼者：旋转光刃当前角度（弧度，渲染与伤害判定共用同一条线）。 */
    public float gyreAngle;
    /** 旋翼者：本次旋转的扫向（+1 顺时针 / -1 逆时针），每次起转重掷。 */
    public float gyreSpinDir = 1f;
    /** 旋翼者：巡航蓄能计时，归零后进入预警。 */
    public float gyreRest;
    /** 旋翼者：预警 / 旋转的剩余时长。 */
    public float gyreTimer;
    /** 旋翼者：本次旋转是否已经命中过飞船（一次旋转只结算一次伤害）。 */
    public boolean gyreHit;
    /** 旋翼者刚起转（一次性标记，由 GameWorld 消费并播表现）。 */
    public boolean gyreJustStarted;
    /** v1.48 枢链者：同一对的编号（-1 = 没有搭档）。 */
    public int linkId = -1;
    /** 枢链者的左右位置：-1 左 / +1 右，决定占位与表现只播一次。 */
    public int linkSide = 1;
    /** 锁链阶段：0 悬停蓄势 1 充能预警 2 通电下扫 3 回收。 */
    public int linkPhase;
    /** 当前阶段剩余时间。 */
    public float linkTimer;
    /** 搭档已不在：锁链断开，本体失稳俯冲离场。 */
    public boolean linkLost;
    /** 本次通电刚起（一次性标记，由 GameWorld 消费并播表现）。 */
    public boolean linkJustFired;
    /** 本次下扫是否已经命中过飞船（一次只结算一次伤害）。 */
    public boolean linkHit;
    /** 坦克的齐射计数：每第 3 轮改成五连扇形扫射。 */
    public int volley;
    /** 分裂舱被击毁时生成的子机层数（防止无限分裂）。 */
    public int splitDepth;
    /** 自杀机锁定强度：越接近飞船加速越猛。 */
    public float diveSpeed;
    /** 治疗脉冲余量（1 = 刚释放，0 = 无）；渲染层据此画扩散环。 */
    public float healFx;
    /** 布雷艇下一次投雷的倒计时（<=0 时由 GameWorld 结算）。 */
    public float mineCd;
    /** 母舰下一次放机的倒计时。 */
    public float droneCd;
    /** 盾卫正面护盾耐久：>0 时挡下所有非穿透弹（激光与弹跳弹照样能穿）。 */
    public float shieldHp;
    public static final float SHIELDER_SHIELD = 6f;
    /** 伪装者剩余伪装强度：1 = 完全伪装成道具，0 = 已现形。 */
    public float cloak;
    /** 磁暴艇下一次 EMP 环形脉冲的倒计时。 */
    public float empCd;
    /** EMP 脉冲余量（1 = 刚释放），渲染层据此画扩散环。 */
    public float empFx;
    /** 蜂后下一次产卵的倒计时（产卵间隔随血量下降而缩短）。 */
    public float broodCd;
    /** 冲撞者阶段：0 下降 1 瞄准预警 2 俯冲 3 撞停硬直 4 离场。 */
    public int ramPhase;
    /** 冲撞者预警/硬直倒计时。 */
    public float ramTimer;
    /** 冲撞者的俯冲落点 x（预警线画在这里）。 */
    public float ramAimX;
    /** 冲撞者刚撞停（一次性标记，由 GameWorld 消费并播冲击表现）。 */
    public boolean ramImpact;
    /** 俯冲方向（单位向量，渲染层画速度线用）。 */
    public float ramDirX;
    public float ramDirY = 1f;
    /** 相位者阶段：0 实体 1 相位前预警 2 相位（无敌、穿弹、不开火）。 */
    public int phasePhase;
    /** 当前阶段剩余时间（实体期不用它，改用 phaseClock）。 */
    public float phaseTimer;
    /** 实体期已过时间，到 PHASER_CYCLE 进入预警。 */
    public float phaseClock;
    /** 相位结束后的实体化落点 x（渲染层在相位期画信标）。 */
    public float phaseLandX;
    /** 一次性相位事件标记（true 时由 GameWorld 消费，code 1 相位化 / 0 实体化）。 */
    public boolean phaseShifted;
    /** 刚进入相位（false 表示刚重新实体化）。 */
    public boolean phaseOut;
    /** 重新实体化后的双倍伤害窗口余量。 */
    public float phaseReentry;
    /** 幼机归属：蜂后在敌机池中的下标；-1 表示与蜂后无关。 */
    public int broodOf = -1;
    /** 精英词缀：厚血、高分、必掉道具，渲染层加一圈光环。 */
    public boolean elite;
    /** v1.27 赏金目标：由 GameWorld 现场指定，限时击毁可拿赏金与连杀。 */
    public boolean bounty;
    /** 精英倍率（生命、分值）。 */
    public static final float ELITE_HP_MULT = 2.6f;
    public static final int ELITE_SCORE_MULT = 3;
    private float originX;
    private float dir = 1f;
    private float speedScale;

    /** 狙击塔悬停高度（虚拟坐标，越小说明越靠屏幕顶部）。 */
    public static final float SNIPER_HOVER_Y = 300f;
    /** 母舰巡航高度：到位后先悬停放机，再俯冲离场。 */
    public static final float CARRIER_CRUISE_Y = 420f;

    // ---- v1.17 三种新敌机的几何参数 ----
    /** 磁暴艇磁场半径：圈内玩家的直射弹会被横向推开。 */
    public static final float MAGNET_RADIUS = 320f;
    /** 磁偏加速度（虚拟单位/秒²），足够把机炮线推开但推不动重弹与光束。 */
    public static final float MAGNET_BEND = 980f;
    /** 磁暴艇巡航高度：停在弹道上而不是压到脸上。 */
    public static final float MAGNET_HOVER_Y = 520f;
    /** 伪装者现形距离：飞船进入这个半径立刻现形（远大于撞击半径，不会被偷死）。 */
    public static final float MIRAGE_TRIGGER = 210f;
    /**
     * 伪装者最长伪装时间：到点自动现形，不会永远藏在弹道里。
     * 2.0 秒是实测值：中后期玩家的火力通常在 1.6~3.0 秒内就把它点掉（算识破给奖励），
     * 活过这个窗口的就该在屏幕中段当面变身，让"道具是假的"这件事被看见。
     */
    public static final float MIRAGE_MAX_CLOAK = 2.0f;
    /** 磁暴艇中场驻留时长：到点必须俯冲离场，否则滞空舰会塞满敌机池、把刷新卡死。 */
    public static final float MAGNETON_HOVER = 11f;
    /** 蜂后驻留时长：她要在这段时间里产够卵，然后带着剩下的幼机离开。 */
    public static final float QUEEN_HOVER = 15f;
    /** 蜂后巡航高度与回避半径：玩家贴脸她就往上游走。 */
    public static final float QUEEN_HOVER_Y = 290f;
    public static final float QUEEN_MIN_Y = 150f;
    public static final float QUEEN_AVOID_RADIUS = 320f;

    // ---- v1.21 冲撞者 ----
    /** 悬停高度：压到玩家视野上沿再瞄准，预警线才看得完整。 */
    public static final float RAMMER_HOVER_Y = 400f;
    /** 预警时长：足够从屏幕任一侧走到落点之外，又不至于等到走神。 */
    public static final float RAMMER_TELEGRAPH = 0.85f;
    /** 俯冲速度：比狙击弹更快，靠预警而不是靠反应力躲。 */
    public static final float RAMMER_DASH_SPEED = 1700f;
    /** 撞停高度：落在玩家活动区下沿附近，硬直期就贴在脸上挨打。 */
    public static final float RAMMER_BOTTOM_Y = 1450f;
    /** 撞停硬直：这段时间里受击双倍，是"躲开再反打"的奖励窗口。 */
    public static final float RAMMER_STUN = 1.4f;
    /** 瞄准期机身横移速度：跟得上走位，但可以被急转甩开。 */
    public static final float RAMMER_TRACK = 300f;

    // ---- v1.22 相位者 ----
    /** 实体期时长：可打、也会开火；结束进入预警。 */
    public static final float PHASER_CYCLE = 3.2f;
    /** 相位前预警：机身加速闪烁，给玩家 0.9 秒收枪/走位。 */
    public static final float PHASER_TELL = 0.9f;
    /** 相位期：不吃子弹、不开火、不撞船，0.7 秒后重新实体化。 */
    public static final float PHASER_GHOST = 0.7f;
    /**
     * 重新实体化后的弱点窗口：这段时间里受击双倍。
     * 相位期子弹会原样穿过，所以提前把火力铺在落点上就能吃到这份奖励。
     */
    public static final float PHASER_REENTRY = 0.55f;
    /** 相位漂移距离：落点固定在预警结束的一侧，玩家可以预瞄。 */
    public static final float PHASER_SHIFT = 190f;
    /** 驻留高度与巡游速度。 */
    public static final float PHASER_HOVER_Y = 380f;
    public static final float PHASER_HOVER = 12f;

    // ---- v1.34 缚网者 ----
    /** 悬停高度：比追猎者更低，网弹落下来时正好穿过玩家的常用走位层。 */
    public static final float SNARER_HOVER_Y = 430f;
    /** 驻留时长：约 3 轮网弹后离场。 */
    public static final float SNARER_HOVER = 12f;
    /** 悬停时的横向摆渡幅度：全程可预测，玩家能绕着它走。 */
    public static final float SNARER_SWING = 95f;

    // ---- v1.35 螺旋炮台 ----
    /** 悬停高度：留在高位，螺旋弹有足够距离展开成可读的旋臂。 */
    public static final float HELIX_HOVER_Y = 300f;
    /** 驻留时长：约 7 轮螺旋后离场。 */
    public static final float HELIX_HOVER = 15f;

    // ---- v1.38 切割者 ----
    /** 悬停高度：贴着上沿，竖切光束从敌方一路切到屏幕底。 */
    public static final float CUTTER_HOVER_Y = 280f;
    /** 驻留时长：约三次切割后离场。 */
    public static final float CUTTER_HOVER = 14f;
    /** 充能时长：足够从屏幕任一侧走到光束之外。 */
    public static final float CUTTER_TELL = 0.95f;
    /** 最后这段时间不再追踪：光束落点提前锁死，玩家能读出"该往哪边让"。 */
    public static final float CUTTER_LOCK = 0.28f;
    /** 开火时长与光束半宽（伤害判定用）。 */
    public static final float CUTTER_FIRE = 0.5f;
    public static final float CUTTER_HALF_WIDTH = 30f;
    /** 瞄准期的横向跟船速度：每帧只追一点，横移就能把光束拖到身后。 */
    public static final float CUTTER_TRACK = 240f;

    // ---- v1.42 旋翼者 ----
    /** 悬停高度：光刃从高位扫下去，整条射线都能看清。 */
    public static final float GYRE_HOVER_Y = 330f;
    /** 驻留时长：约三次旋转后离场。 */
    public static final float GYRE_HOVER = 14f;
    /** 两次旋转之间的蓄能巡游时间：够玩家重新找位，也不至于干等。 */
    public static final float GYRE_REST = 2.2f;
    /** 预警时长：起手线已经指向飞船，玩家要在这段时间里挪出这条线。 */
    public static final float GYRE_TELL = 0.9f;
    /** 一次旋转的持续时长。 */
    public static final float GYRE_SPIN_TIME = 2.4f;
    /** 角速度（弧度/秒）：约 66°/秒，一次扫过 158°。 */
    public static final float GYRE_SPIN_RATE = 1.15f;
    /** 光刃半长：从舰体一路够到屏幕下沿之外，靠"绕开角度"而不是"跑出射程"来躲。 */
    public static final float GYRE_HALF_LENGTH = 1250f;
    /** 光刃半宽（伤害判定用）；比切割者的光柱细，穿过它只需要一次果断的横移。 */
    public static final float GYRE_HALF_WIDTH = 16f;

    // ---- v1.48 枢链者 ----
    /** 悬停高度：锁链架在玩家头顶，蓄势期缓慢横移贴住玩家的横向位置。 */
    public static final float LINK_HOVER_Y = 620f;
    /** 下扫落点：锁链扫到玩家常用的活动带，逼玩家离开这条走廊。 */
    public static final float LINK_DIVE_Y = 1300f;
    /** 一对枢链者的左右半距：走廊宽 300，横移一个身位就能出去。 */
    public static final float LINK_HALF_SEP = 150f;
    /** 锁链通电半宽（伤害判定用）。 */
    public static final float LINK_HALF_WIDTH = 12f;
    /** 悬停期贴着玩家横移的速度：比飞船慢得多，玩家一横移就能把它拖开。 */
    public static final float LINK_TRACK = 110f;
    /** 第一次蓄势：刚到位就给玩家看清"它在跟着我"的时间。 */
    public static final float LINK_FIRST_REST = 1.2f;
    /** 之后两轮之间的蓄势时长。 */
    public static final float LINK_REST = 1.8f;
    /** 充能预警：细线亮起，锁定当前横向位置不再跟船。 */
    public static final float LINK_CHARGE = 0.9f;
    /** 通电下扫时长：从悬停高度扫到玩家活动带。 */
    public static final float LINK_DIVE_TIME = 1.6f;
    /** 回收时长（锁链已断电，留一个可读的收势动作）。 */
    public static final float LINK_RISE_TIME = 1.0f;
    /** 入场下降速度：一对成员用同一速度，保证锁链水平。 */
    public static final float LINK_DESCENT = 190f;

    // ---- v1.33 追猎者 ----
    /** 悬停高度：在弹幕层上方，玩家能清楚看到两侧发射管亮起。 */
    public static final float SEEKER_HOVER_Y = 340f;
    /** 驻留时长：够放 3~4 轮追踪弹，然后俯冲让位。 */
    public static final float SEEKER_HOVER = 13f;
    /** 横向跟船速度：比玩家慢，绕圈就能把它甩到屏幕边缘。 */
    public static final float SEEKER_TRACK = 120f;

    public void init(EnemyType type, float x, float y, float speedScale) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.originX = x;
        this.hp = type.hp;
        this.maxHp = type.hp;
        this.active = true;
        this.age = 0f;
        this.flash = 0f;
        this.dir = GameWorld.RNG.get().nextBoolean() ? 1f : -1f;
        this.speedScale = speedScale;
        this.healCd = 1.1f;
        this.healFx = 0f;
        this.hoverCd = type == EnemyType.SNIPER ? 8.5f
                : type == EnemyType.CARRIER ? 12f
                : type == EnemyType.MAGNETON ? MAGNETON_HOVER
                : type == EnemyType.QUEEN ? QUEEN_HOVER
                : type == EnemyType.PHASER ? PHASER_HOVER
                : type == EnemyType.SEEKER ? SEEKER_HOVER
                : type == EnemyType.SNARER ? SNARER_HOVER
                : type == EnemyType.HELIX ? HELIX_HOVER
                : type == EnemyType.CUTTER ? CUTTER_HOVER
                : type == EnemyType.GYRE ? GYRE_HOVER : 0f;
        this.spiralAngle = 0f;
        this.burstCount = 0;
        this.beamPhase = 0;
        this.beamTimer = 0f;
        this.beamHit = false;
        this.beamJustFired = false;
        this.gyreAngle = (float) (Math.PI / 2);
        // 扫向沿用巡游方向：不额外抽随机数，长跑里的确定性序列不会被新舰型打乱
        this.gyreSpinDir = dir;
        // 第一轮蓄能短一点：刚到位就该让玩家看到这艘舰"是干什么的"
        this.gyreRest = 1.2f;
        this.gyreTimer = 0f;
        this.gyreHit = false;
        this.gyreJustStarted = false;
        this.linkId = -1;
        this.linkSide = 1;
        this.linkPhase = 0;
        this.linkTimer = LINK_FIRST_REST;
        this.linkLost = false;
        this.linkJustFired = false;
        this.linkHit = false;
        this.volley = 0;
        this.splitDepth = 0;
        this.mineCd = type == EnemyType.MINER ? 1.2f : 0f;
        this.droneCd = type == EnemyType.CARRIER ? 1.6f : 0f;
        this.shieldHp = type == EnemyType.SHIELDER ? SHIELDER_SHIELD : 0f;
        this.cloak = type == EnemyType.MIRAGE ? 1f : 0f;
        // 首发放快（实测这类舰在中后期活不过 3 秒），之后按 3~4 秒周期脉冲
        this.empCd = type == EnemyType.MAGNETON ? 1.5f : 0f;
        this.empFx = 0f;
        this.broodCd = type == EnemyType.QUEEN ? 1.4f : 0f;
        this.broodOf = -1;
        this.ramPhase = 0;
        this.ramTimer = 0f;
        this.ramAimX = x;
        this.ramImpact = false;
        this.ramDirX = 0f;
        this.ramDirY = 1f;
        this.phasePhase = 0;
        this.phaseTimer = 0f;
        this.phaseClock = 0f;
        this.phaseLandX = x;
        this.phaseShifted = false;
        this.phaseOut = false;
        this.phaseReentry = 0f;
        this.elite = false;
        this.bounty = false;
        this.diveSpeed = type.baseSpeed * speedScale;
        switch (type) {
            case TANK: fireCd = 1.4f + GameWorld.RNG.get().nextFloat() * 0.6f; break;
            case HUNTER: fireCd = 2.0f + GameWorld.RNG.get().nextFloat() * 0.8f; break;
            case SPLITTER: fireCd = 2.0f + GameWorld.RNG.get().nextFloat() * 0.8f; break;
            case SNIPER: fireCd = 1.5f + GameWorld.RNG.get().nextFloat() * 0.5f; break;
            case SHIELDER: fireCd = 2.4f + GameWorld.RNG.get().nextFloat() * 0.8f; break;
            case MAGNETON: fireCd = 2.6f + GameWorld.RNG.get().nextFloat() * 0.9f; break;
            case MIRAGE: fireCd = -1f; break;   // 现形后才开始开火
            case PHASER: fireCd = 2.2f + GameWorld.RNG.get().nextFloat() * 0.7f; break;
            case SEEKER: fireCd = 2.4f + GameWorld.RNG.get().nextFloat() * 0.6f; break;
            case SNARER: fireCd = 2.9f + GameWorld.RNG.get().nextFloat() * 0.8f; break;
            case HELIX: fireCd = 1.8f + GameWorld.RNG.get().nextFloat() * 0.5f; break;
            case CUTTER: fireCd = 1.6f + GameWorld.RNG.get().nextFloat() * 0.5f; break;
            case GYRE: fireCd = -1f; break;   // 不开炮：全部威胁都在光刃上
            case LINKER: fireCd = -1f; break; // 不开炮：全部威胁都在锁链上
            default: fireCd = -1f; // does not fire
        }
    }

    /** 打上精英词缀：厚血、减速一点点方便玩家优先集火，掉落保底由 GameWorld 处理。 */
    public void makeElite() {
        elite = true;
        hp = maxHp = Math.round(type.hp * ELITE_HP_MULT);
        speedScale *= 0.9f;
        diveSpeed = type.baseSpeed * speedScale;
    }

    /** 正面护盾是否还在（挡弹判定与渲染都用它）。 */
    public boolean hasFrontShield() {
        return type == EnemyType.SHIELDER && shieldHp > 0f;
    }

    /** 受击时先扣护盾耐久，护盾崩溃后本体才 vulnerable（返回值仅用于表现）。 */
    public void chipShield(float amount) {
        if (shieldHp > 0f) shieldHp = Math.max(0f, shieldHp - amount);
    }

    public float speed() {
        return type.baseSpeed * speedScale;
    }

    /** 兼容旧调用：不需要飞船位置的兵种行为。 */
    public void update(float dt, float worldW) {
        update(dt, worldW, Float.NaN, Float.NaN);
    }

    public void update(float dt, float worldW, float shipX, float shipY) {
        age += dt;
        if (flash > 0f) flash -= dt;
        if (healFx > 0f) healFx = Math.max(0f, healFx - dt / 1.1f);
        switch (type) {
            case DRONE:
                y += speed() * dt;
                break;
            case WEAVER:
                y += speed() * dt;
                x = originX + (float) Math.sin(age * 3.2f) * 130f;
                break;
            case HUNTER:
                y += speed() * dt;
                x += dir * 260f * dt;
                if (x < type.radius || x > worldW - type.radius) {
                    dir = -dir;
                    x = clamp(x, type.radius, worldW - type.radius);
                }
                break;
            case TANK:
                y += speed() * dt;
                break;
            case SUICIDER: {
                // 锁定飞船俯冲：初速慢、越近越快，给玩家反应窗口
                float cap = speed() * 2.6f;
                diveSpeed = Math.min(cap, diveSpeed + speed() * 2.2f * dt);
                float tx = shipX, ty = shipY;
                if (Float.isNaN(tx)) { tx = originX; ty = GameWorld.HEIGHT; }
                float dx = tx - x, dy = ty - y;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < 1f) { dx = 0f; dy = 1f; d = 1f; }
                x += dx / d * diveSpeed * dt;
                y += dy / d * diveSpeed * dt;
                x = clamp(x, type.radius, worldW - type.radius);
                break;
            }
            case SPLITTER:
                y += speed() * dt;
                x = originX + (float) Math.sin(age * 1.4f) * 46f;
                break;
            case HEALER:
                y += speed() * dt;
                x = originX + (float) Math.sin(age * 1.1f) * 90f;
                if (healCd > 0f) healCd = Math.max(0f, healCd - dt);
                break;
            case SNIPER: {
                if (y < SNIPER_HOVER_Y) {
                    y += speed() * dt;
                    x = originX + (float) Math.sin(age * 0.8f) * 70f;
                    break;
                }
                hoverCd -= dt;
                if (hoverCd <= 0f) {          // 悬停结束，离场俯冲
                    y += speed() * 1.25f * dt;
                    break;
                }
                x += dir * 70f * dt;
                if (x < type.radius + 20f || x > worldW - type.radius - 20f) {
                    dir = -dir;
                    x = clamp(x, type.radius + 20f, worldW - type.radius - 20f);
                }
                break;
            }
            case MINER:
                // 布雷艇：慢速推进并左右小幅摆动，投雷倒计时由 GameWorld 结算
                y += speed() * dt;
                x = originX + (float) Math.sin(age * 0.9f) * 60f;
                x = clamp(x, type.radius, worldW - type.radius);
                if (mineCd > 0f) mineCd = Math.max(0f, mineCd - dt);
                break;
            case CARRIER: {
                // 母舰：降到巡航高度后悬停放机，超时再俯冲离场
                if (y < CARRIER_CRUISE_Y) {
                    y += speed() * dt;
                    break;
                }
                hoverCd -= dt;
                if (hoverCd <= 0f) {
                    y += speed() * 1.4f * dt;
                    break;
                }
                x += dir * 60f * dt;
                if (x < type.radius + 30f || x > worldW - type.radius - 30f) {
                    dir = -dir;
                    x = clamp(x, type.radius + 30f, worldW - type.radius - 30f);
                }
                if (droneCd > 0f) droneCd = Math.max(0f, droneCd - dt);
                break;
            }
            case SHIELDER:
                // 盾卫：正面朝下缓慢压上，偶尔射一发扇形弹逼玩家绕后
                y += speed() * dt;
                x = originX + (float) Math.sin(age * 0.7f) * 40f;
                x = clamp(x, type.radius, worldW - type.radius);
                break;
            case MIRAGE: {
                // 伪装态漂得慢、摆得小，看起来就像一枚道具；现形后才露出速度
                boolean hidden = cloak > 0f;
                y += speed() * (hidden ? 0.62f : 1.35f) * dt;
                x = originX + (float) Math.sin(age * (hidden ? 1.0f : 2.6f))
                        * (hidden ? 24f : 92f);
                x = clamp(x, type.radius, worldW - type.radius);
                break;
            }
            case MAGNETON: {
                // 磁暴艇压到中场就停住横向巡游，磁场始终生效（弹道弯曲在 GameWorld 结算）
                if (y < MAGNET_HOVER_Y && hoverCd > 0f) {
                    y += speed() * dt;
                } else if (hoverCd > 0f) {
                    hoverCd -= dt;
                    x += dir * 62f * dt;
                    if (x < type.radius + 20f || x > worldW - type.radius - 20f) {
                        dir = -dir;
                        x = clamp(x, type.radius + 20f, worldW - type.radius - 20f);
                    }
                } else {
                    // 驻留到期：俯冲离场，把敌机池的坑位让出来
                    y += speed() * 1.5f * dt;
                }
                if (empCd > 0f) empCd = Math.max(0f, empCd - dt);
                if (empFx > 0f) empFx = Math.max(0f, empFx - dt / 0.9f);
                break;
            }
            case QUEEN: {
                // 产卵倒计时全程走动：降到巡航高度的路上也在憋卵，
                // 否则一波蜂后可能整场只产一次，"优先击杀"就失去意义
                if (broodCd > 0f) broodCd = Math.max(0f, broodCd - dt);
                // 蜂后：降到巡航高度后朝飞船的横向慢慢压过来，玩家贴脸就往上游走
                if (hoverCd <= 0f) {
                    // 产卵窗口结束：不再赖在中场，直落离场
                    y += speed() * 1.35f * dt;
                    x = originX + (float) Math.sin(age * 0.6f) * 60f;
                    break;
                }
                if (y < QUEEN_HOVER_Y) {
                    y += speed() * dt;
                    x = originX + (float) Math.sin(age * 0.6f) * 60f;
                    break;
                }
                hoverCd -= dt;
                if (!Float.isNaN(shipX)) {
                    float dx = shipX - x;
                    float step = Math.abs(dx) < 24f ? 0f : (dx > 0f ? 105f : -105f);
                    x += step * dt;
                    float dy = shipY - y;
                    if (!Float.isNaN(dy) && (float) Math.sqrt(dx * dx + dy * dy)
                            < QUEEN_AVOID_RADIUS) {
                        y = Math.max(QUEEN_MIN_Y, y - speed() * 0.8f * dt);
                    } else {
                        y += (y > QUEEN_HOVER_Y + 40f ? -1f : 1f) * speed() * 0.22f * dt;
                    }
                }
                x = clamp(x, type.radius + 10f, worldW - type.radius - 10f);
                break;
            }
            case RAMMER: {
                // 冲撞者：压到上沿 → 预警线锁定 → 俯冲 → 撞停硬直 → 离场。
                // 全程只有"瞄准"和"硬直"两段时间看得见停下来的舰体，
                // 玩家要记的是这两段，而不是猜它什么时候冲。
                if (ramPhase == 0) {
                    y += speed() * dt;
                    if (!Float.isNaN(shipX)) {
                        ramAimX = clamp(shipX, type.radius + 10f, worldW - type.radius - 10f);
                    }
                    if (y >= RAMMER_HOVER_Y) {
                        y = RAMMER_HOVER_Y;
                        ramPhase = 1;
                        ramTimer = RAMMER_TELEGRAPH;
                    }
                    break;
                }
                if (ramPhase == 1) {
                    ramTimer -= dt;
                    if (!Float.isNaN(shipX)) {
                        ramAimX = clamp(shipX, type.radius + 10f, worldW - type.radius - 10f);
                        float dxAim = ramAimX - x;
                        float step = Math.min(Math.abs(dxAim), RAMMER_TRACK * dt);
                        x += dxAim > 0f ? step : -step;
                    }
                    if (ramTimer <= 0f) {
                        ramPhase = 2;
                        float dx = ramAimX - x;
                        float dy = RAMMER_BOTTOM_Y - y;
                        float d = (float) Math.sqrt(dx * dx + dy * dy);
                        if (d < 1f) { dx = 0f; dy = 1f; d = 1f; }
                        ramDirX = dx / d;
                        ramDirY = dy / d;
                    }
                    break;
                }
                if (ramPhase == 2) {
                    x = clamp(x + ramDirX * RAMMER_DASH_SPEED * dt,
                            type.radius, worldW - type.radius);
                    y += ramDirY * RAMMER_DASH_SPEED * dt;
                    if (y >= RAMMER_BOTTOM_Y) {
                        y = RAMMER_BOTTOM_Y;
                        ramPhase = 3;
                        ramTimer = RAMMER_STUN;
                        ramImpact = true;
                    }
                    break;
                }
                if (ramPhase == 3) {
                    ramTimer -= dt;
                    if (ramTimer <= 0f) ramPhase = 4;
                    break;
                }
                y += speed() * 1.35f * dt;
                break;
            }
            case SEEKER: {
                // 追猎者：压到悬停高度后就横向跟着飞船走，但追得比玩家慢；
                // 驻留到期俯冲离场，把坑位让给下一波
                if (y < SEEKER_HOVER_Y) {
                    y += speed() * dt;
                    x = originX + (float) Math.sin(age * 0.8f) * 55f;
                    break;
                }
                if (hoverCd <= 0f) {
                    y += speed() * 1.4f * dt;
                    x = originX + (float) Math.sin(age * 0.7f) * 40f;
                    break;
                }
                hoverCd -= dt;
                if (!Float.isNaN(shipX)) {
                    float dx = shipX - x;
                    x += (dx > 0f ? 1f : -1f) * Math.min(Math.abs(dx), SEEKER_TRACK * dt);
                }
                x = clamp(x, type.radius + 20f, worldW - type.radius - 20f);
                break;
            }
            case SNARER: {
                // 缚网者：压到中场后做定幅摆渡，像一张慢慢横移的网；驻留到期离场
                if (y < SNARER_HOVER_Y) {
                    y += speed() * dt;
                    break;
                }
                if (hoverCd <= 0f) {
                    y += speed() * 1.4f * dt;
                    x = originX + (float) Math.sin(age * 0.7f) * 40f;
                    break;
                }
                hoverCd -= dt;
                x = originX + (float) Math.sin(age * 0.9f) * SNARER_SWING;
                break;
            }
            case HELIX: {
                // 螺旋炮台：压到高位后原地悬停，机体缓慢自转；驻留到期俯冲离场
                if (y < HELIX_HOVER_Y) {
                    y += speed() * dt;
                    break;
                }
                if (hoverCd <= 0f) {
                    y += speed() * 1.4f * dt;
                    break;
                }
                hoverCd -= dt;
                x = originX + (float) Math.sin(age * 0.5f) * 60f;
                break;
            }
            case CUTTER: {
                // 切割者：压到上沿后小范围巡游；开火时先锁定一列——
                // 充能前段慢慢跟船，最后 0.28 秒锁死，把"往哪边让"变成一个可读的决定
                if (y < CUTTER_HOVER_Y) {
                    y += speed() * dt;
                    break;
                }
                if (hoverCd <= 0f) {
                    beamPhase = 0;
                    beamTimer = 0f;
                    y += speed() * 1.4f * dt;
                    break;
                }
                hoverCd -= dt;
                if (beamPhase == 1) {
                    beamTimer -= dt;
                    if (beamTimer > CUTTER_LOCK && !Float.isNaN(shipX)) {
                        float dxAim = shipX - x;
                        float step = Math.min(Math.abs(dxAim), CUTTER_TRACK * dt);
                        x += dxAim > 0f ? step : -step;
                    }
                    x = clamp(x, type.radius + 10f, worldW - type.radius - 10f);
                    if (beamTimer <= 0f) {
                        beamPhase = 2;
                        beamTimer = CUTTER_FIRE;
                        beamHit = false;
                        beamJustFired = true;
                    }
                    break;
                }
                if (beamPhase == 2) {
                    beamTimer -= dt;
                    if (beamTimer <= 0f) {
                        beamPhase = 0;
                        beamTimer = 0f;
                    }
                    break;
                }
                x += dir * 46f * dt;
                if (x < type.radius + 20f || x > worldW - type.radius - 20f) {
                    dir = -dir;
                    x = clamp(x, type.radius + 20f, worldW - type.radius - 20f);
                }
                break;
            }
            case LINKER: {
                // 枢链者：一对从上方压到中场，锁链架在玩家头顶并缓慢横移贴住玩家；
                // 充能 0.9 秒（细线预警、停止跟船）后一起俯冲，通电的锁链扫过整片活动带。
                // 拆掉任意一端锁链立刻断开——先点掉一侧，比硬躲两轮更省事。
                if (linkLost) {
                    // 失去搭档：锁链失稳，本体顺势俯冲离场
                    y += speed() * 1.5f * dt;
                    x += dir * 90f * dt;
                    break;
                }
                if (y < LINK_HOVER_Y) {
                    y += LINK_DESCENT * dt;
                    break;
                }
                if (linkPhase == 0) {
                    if (!Float.isNaN(shipX)) {
                        float center = clamp(shipX, LINK_HALF_SEP + type.radius,
                                worldW - LINK_HALF_SEP - type.radius);
                        float target = center + linkSide * LINK_HALF_SEP;
                        float step = Math.min(Math.abs(target - x), LINK_TRACK * dt);
                        x += target > x ? step : -step;
                    }
                    // 收势后可能还差几像素才回到悬停高度，缓慢吸回去
                    y += (LINK_HOVER_Y - y) * Math.min(1f, 6f * dt);
                    linkTimer -= dt;
                    if (linkTimer <= 0f) {
                        linkPhase = 1;
                        linkTimer = LINK_CHARGE;
                    }
                    break;
                }
                if (linkPhase == 1) {
                    // 充能期锁定：玩家在这 0.9 秒里横移出去，锁链就扫不到人
                    linkTimer -= dt;
                    if (linkTimer <= 0f) {
                        linkPhase = 2;
                        linkTimer = LINK_DIVE_TIME;
                        linkHit = false;
                        // 一对只播一次：由左端（linkSide < 0）负责报点
                        linkJustFired = linkSide < 0;
                    }
                    break;
                }
                if (linkPhase == 2) {
                    linkTimer -= dt;
                    float p = 1f - Math.max(0f, linkTimer) / LINK_DIVE_TIME;
                    y = LINK_HOVER_Y + (LINK_DIVE_Y - LINK_HOVER_Y) * p;
                    if (linkTimer <= 0f) {
                        linkPhase = 3;
                        linkTimer = LINK_RISE_TIME;
                    }
                    break;
                }
                linkTimer -= dt;
                float p = 1f - Math.max(0f, linkTimer) / LINK_RISE_TIME;
                y = LINK_DIVE_Y + (LINK_HOVER_Y - LINK_DIVE_Y) * p;
                if (linkTimer <= 0f) {
                    linkPhase = 0;
                    linkTimer = LINK_REST;
                    y = LINK_HOVER_Y;
                }
                break;
            }
            case GYRE: {
                // 旋翼者：压到高位后左右巡游蓄能，预警 0.9 秒后甩出双头光刃旋转约半圈。
                // 起手线直接指向飞船，光刃又长到够穿整个屏幕——躲法只有"绕着扫向走位"，
                // 而不是往外跑；一次旋转最多结算一次伤害，被擦到不会连续掉命。
                if (y < GYRE_HOVER_Y) {
                    y += speed() * dt;
                    break;
                }
                if (hoverCd <= 0f) {
                    beamPhase = 0;
                    y += speed() * 1.4f * dt;
                    break;
                }
                hoverCd -= dt;
                if (beamPhase == 0) {
                    x += dir * 46f * dt;
                    if (x < type.radius + 20f || x > worldW - type.radius - 20f) {
                        dir = -dir;
                        x = clamp(x, type.radius + 20f, worldW - type.radius - 20f);
                    }
                    gyreRest -= dt;
                    if (gyreRest <= 0f) {
                        beamPhase = 1;
                        gyreTimer = GYRE_TELL;
                        gyreAngle = Float.isNaN(shipX) || Float.isNaN(shipY)
                                ? (float) (Math.PI / 2)
                                : (float) Math.atan2(shipY - y, shipX - x);
                        gyreSpinDir = dir;
                    }
                    break;
                }
                if (beamPhase == 1) {
                    gyreTimer -= dt;
                    if (gyreTimer <= 0f) {
                        beamPhase = 2;
                        gyreTimer = GYRE_SPIN_TIME;
                        gyreHit = false;
                        gyreJustStarted = true;
                    }
                    break;
                }
                gyreTimer -= dt;
                gyreAngle += gyreSpinDir * GYRE_SPIN_RATE * dt;
                if (gyreTimer <= 0f) {
                    beamPhase = 0;
                    gyreTimer = 0f;
                    gyreRest = GYRE_REST;
                }
                break;
            }
            case PHASER: {
                // 相位者：压到中场后按固定节拍循环——实体 3.2s → 预警 0.9s → 相位 0.7s。
                // 相位期子弹原样穿过（GameWorld 跳过碰撞），所以"打空"不会浪费火力；
                // 落点固定在相位开始时算好，提前铺火就能吃到实体化瞬间的双倍窗口。
                if (y < PHASER_HOVER_Y) {
                    y += speed() * dt;
                    x = originX + (float) Math.sin(age * 0.9f) * 60f;
                    break;
                }
                if (hoverCd <= 0f) {
                    // 驻留到期：不再赖在中场，直落离场
                    y += speed() * 1.3f * dt;
                    x = originX + (float) Math.sin(age * 0.7f) * 40f;
                    break;
                }
                hoverCd -= dt;
                if (phaseReentry > 0f) phaseReentry = Math.max(0f, phaseReentry - dt);
                if (phasePhase == 0) {
                    phaseClock += dt;
                    x += dir * 58f * dt;
                    if (x < type.radius + 20f || x > worldW - type.radius - 20f) {
                        dir = -dir;
                        x = clamp(x, type.radius + 20f, worldW - type.radius - 20f);
                    }
                    if (phaseClock >= PHASER_CYCLE) {
                        phasePhase = 1;
                        phaseTimer = PHASER_TELL;
                    }
                    break;
                }
                if (phasePhase == 1) {
                    phaseTimer -= dt;
                    if (phaseTimer <= 0f) {
                        phasePhase = 2;
                        phaseTimer = PHASER_GHOST;
                        phaseLandX = clamp(x + dir * PHASER_SHIFT,
                                type.radius + 10f, worldW - type.radius - 10f);
                        phaseShifted = true;
                        phaseOut = true;
                    }
                    break;
                }
                // 相位期：滑向落点，期间不可碰撞、不吃子弹、不开火
                phaseTimer -= dt;
                float dxLand = phaseLandX - x;
                float step = Math.min(Math.abs(dxLand), 280f * dt);
                x += dxLand > 0f ? step : -step;
                if (phaseTimer <= 0f) {
                    x = phaseLandX;
                    phasePhase = 0;
                    phaseClock = 0f;
                    phaseReentry = PHASER_REENTRY;
                    phaseShifted = true;
                    phaseOut = false;
                }
                break;
            }
        }
    }

    /** 狙击塔正在充能（渲染层据此画预警线）。 */
    public boolean isCharging() {
        return type == EnemyType.SNIPER && fireCd > 0f && fireCd < 0.7f;
    }

    /** 切割者正在瞄准充能（渲染层画细预警线）。 */
    public boolean isBeamCharging() {
        return type == EnemyType.CUTTER && beamPhase == 1;
    }

    /** 切割者的光束正在切割（渲染层画实心光柱，GameWorld 结算伤害）。 */
    public boolean isBeamFiring() {
        return type == EnemyType.CUTTER && beamPhase == 2;
    }

    /** 充能进度 0..1，预警线随它变亮变粗。 */
    public float beamCharge() {
        if (!isBeamCharging()) return isBeamFiring() ? 1f : 0f;
        return 1f - Math.max(0f, beamTimer) / CUTTER_TELL;
    }

    /** 旋翼者正在预警（渲染层画未成形的光刃与扫向弧）。 */
    public boolean isGyreCharging() {
        return type == EnemyType.GYRE && beamPhase == 1;
    }

    /** 旋翼者的光刃正在旋转（渲染层画光刃，GameWorld 结算伤害）。 */
    public boolean isGyreSpinning() {
        return type == EnemyType.GYRE && beamPhase == 2;
    }

    /** 旋翼者预警进度 0..1。 */
    public float gyreCharge() {
        if (!isGyreCharging()) return isGyreSpinning() ? 1f : 0f;
        return 1f - Math.max(0f, gyreTimer) / GYRE_TELL;
    }

    /** 旋翼者本次旋转剩余比例 1..0，渲染层据此收细光刃尾焰。 */
    public float gyreRemain() {
        return isGyreSpinning() ? Math.max(0f, gyreTimer) / GYRE_SPIN_TIME : 0f;
    }

    /** 枢链者的锁链正在充能（渲染层画细线预警，伤害还不生效）。 */
    public boolean isLinkCharging() {
        return type == EnemyType.LINKER && !linkLost && linkPhase == 1;
    }

    /** 枢链者的锁链已经通电（渲染层画电弧，GameWorld 结算伤害）。 */
    public boolean isLinkLive() {
        return type == EnemyType.LINKER && !linkLost && linkPhase == 2;
    }

    /** 枢链者的锁链已经拉起（充能或通电，渲染层据此决定画不画）。 */
    public boolean hasLinkTether() {
        return type == EnemyType.LINKER && !linkLost && linkId >= 0
                && linkPhase >= 1 && linkPhase <= 2;
    }

    /** 充能进度 0..1，预警线随它变亮。 */
    public float linkCharge() {
        if (!isLinkCharging()) return isLinkLive() ? 1f : 0f;
        return 1f - Math.max(0f, linkTimer) / LINK_CHARGE;
    }

    /** 伪装者是否还藏在道具外形里。 */
    public boolean isCloaked() {
        return type == EnemyType.MIRAGE && cloak > 0f;
    }

    /** 现形：丢掉道具外形并开始准备开火（留 0.6 秒预警，不当场爆脸）。 */
    public void reveal() {
        if (type != EnemyType.MIRAGE || cloak <= 0f) return;
        cloak = 0f;
        fireCd = 0.6f;
    }

    /** 磁暴艇是否在场投射磁场。 */
    public boolean projectsMagnetField() {
        return type == EnemyType.MAGNETON;
    }

    /** 蜂后的产卵间隔：血量越低越急，逼玩家优先集火而不是拖着打。 */
    public float broodInterval() {
        float hurt = 1f - Math.max(0f, hp) / Math.max(1f, maxHp);
        return 2.6f - 1.2f * hurt;
    }

    public void heal(float amount) {
        if (amount <= 0f || !active) return;
        hp = Math.min(maxHp, hp + amount);
        flash = Math.max(flash, 0.06f);
    }

    /** 冲撞者是否处于撞停硬直（渲染层画眩晕环，受击双倍）。 */
    public boolean isStunned() {
        return type == EnemyType.RAMMER && ramPhase == 3;
    }

    /** 冲撞者是否正在蓄势俯冲（渲染层画预警线）。 */
    public boolean isRamming() {
        return type == EnemyType.RAMMER && ramPhase >= 1 && ramPhase <= 2;
    }

    /** 相位者是否处在相位态：不可碰撞、不吃子弹、不开火。 */
    public boolean isPhased() {
        return type == EnemyType.PHASER && phasePhase == 2;
    }

    /** 相位者是否在相位前预警（渲染层画收束框）。 */
    public boolean isPhasing() {
        return type == EnemyType.PHASER && phasePhase == 1;
    }

    /** 相位者刚重新实体化，正处于双倍伤害窗口。 */
    public boolean isReentering() {
        return type == EnemyType.PHASER && phaseReentry > 0f;
    }

    public void damage(int amount) {
        // 相位态直接免疫：这一枪原样穿过去，不算"白打"，也不消耗子弹
        if (isPhased()) return;
        // 撞停硬直 / 实体化瞬间都是设计好的惩罚窗口：躲开这一下，就该拿到双倍回报
        hp -= isStunned() || isReentering() ? amount * 2 : amount;
        flash = 0.08f;
    }

    public boolean offscreen(float worldH) {
        return y - type.radius > worldH;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
