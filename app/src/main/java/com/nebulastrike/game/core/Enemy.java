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
                : type == EnemyType.SNARER ? SNARER_HOVER : 0f;
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
