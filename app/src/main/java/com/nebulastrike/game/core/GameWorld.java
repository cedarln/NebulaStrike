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
        DASH, CHAIN, FUSION, BOSS_RAM, RUSH, HEAL_PULSE, ORBIT_HIT
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
    /** 多武器叠加后同屏弹丸更多，池子相应扩充。 */
    public final Bullet[] bullets = new Bullet[144];
    public final EnemyBullet[] enemyBullets = new EnemyBullet[48];
    public final Enemy[] enemies = new Enemy[48];
    public final Particle[] particles = new Particle[256];
    public final PowerUp[] powerups = new PowerUp[16];
    public final Star[] stars = new Star[90];

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
    public float grazeCharge;

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
        for (int i = 0; i < particles.length; i++) particles[i] = new Particle();
        for (int i = 0; i < stars.length; i++) {
            stars[i] = new Star();
            int layer = i % 3;
            float speed = 30f + layer * 55f;
            stars[i].init(RNG.get().nextFloat() * WIDTH, RNG.get().nextFloat() * HEIGHT,
                    1.5f + layer * 1.3f, speed, layer);
        }
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
        // hit-stop：大事件后短暂慢放（残影、爆炸与电弧仍按缩放后的时间推进，视觉上就是"凝滞一瞬"）
        if (hitStopTimer > 0f) {
            hitStopTimer = Math.max(0f, hitStopTimer - dt);
            dt *= HIT_STOP_SCALE;
        }
        if (slowTimer > 0f) slowTimer = Math.max(0f, slowTimer - dt);
        float timeScale = slowTimer > 0f ? SLOW_TIME_SCALE : 1f;
        updateDifficulty();
        updateSpawning(dt);
        updateRushWave(dt);
        updateBoss(dt * timeScale);

        // 摇杆出现/消失动画（约 0.12 秒完成，8 倍速率逼近目标值）
        float joyTarget = joyActive ? 1f : 0f;
        if (joyAnim < joyTarget) joyAnim = Math.min(joyTarget, joyAnim + dt * 8f);
        else if (joyAnim > joyTarget) joyAnim = Math.max(joyTarget, joyAnim - dt * 8f);

        boolean fired = ship.update(dt, WIDTH, HEIGHT);
        // 多武器独立射速：VULCAN 用飞船基础节奏，特殊武器各走自己的冷却并叠加
        for (int i = 1; i < weaponCd.length; i++) {
            if (weaponCd[i] > 0f) weaponCd[i] = Math.max(0f, weaponCd[i] - dt);
        }
        int bulletsBefore = activeBulletCount();
        weaponVolleyMask = 0;
        if (fired && !ship.hasSpecialWeapon()) {
            fireWeapon(WeaponType.VULCAN, 1);
        }
        for (int i = 1; i < WeaponType.values().length; i++) {
            WeaponType type = WeaponType.values()[i];
            if (!ship.isWeaponEquipped(type) || weaponCd[i] > 0f) continue;
            fireWeapon(type, ship.stackOf(type));
            weaponCd[i] = type.cooldown
                    * (ship.rapidTimer > 0f ? 0.5f : 1f)
                    * (ship.overdriveTimer > 0f ? Ship.OVERDRIVE_FIRE_MULT : 1f);
        }
        bulletsFired += activeBulletCount() - bulletsBefore;
        updateWingman(dt);
        updateShipFx(dt);

        for (Bullet b : bullets) {
            if (!b.active) continue;
            if (b.homing) steerMissile(b, dt);
            b.update(dt);
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
        }
    }

    private float wingmanCd;

    // ---- v1.9：相位冲刺 / 轨道球 / 连锁闪电 / 医疗艇 / 突袭奖励波 ----

    /** 相位冲刺：消耗一格能量，朝拖动方向瞬时位移并短暂无敌。 */
    public boolean tryDash() {
        if (state != State.PLAYING || !ship.tryDash()) return false;
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
        ship.overdriveTimer = Ship.OVERDRIVE_DURATION;
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
        e.fireCd -= dt;
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
        enemySpeedScale *= settings.enemySpeedPct / 100f;
    }

    float spawnInterval = 1.1f;
    float enemySpeedScale = 1f;
    private float formationPeriod = 1f;

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
    }

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
        // 保留至少 15% 无人机当"炮灰"：非无人机权重整体归一
        float sum = wTank + wHunter + wWeaver + wSui + wSplit + wHeal + wSnipe;
        if (sum > 0.85f) {
            float k = 0.85f / sum;
            wTank *= k; wHunter *= k; wWeaver *= k;
            wSui *= k; wSplit *= k; wHeal *= k; wSnipe *= k;
        }
        float c = 0f;
        if (r < (c += wTank)) return EnemyType.TANK;
        if (r < (c += wHunter)) return EnemyType.HUNTER;
        if (r < (c += wWeaver)) return EnemyType.WEAVER;
        if (r < (c += wSui)) return EnemyType.SUICIDER;
        if (r < (c += wSplit)) return EnemyType.SPLITTER;
        if (r < (c += wHeal)) return EnemyType.HEALER;
        if (r < (c += wSnipe)) return EnemyType.SNIPER;
        return EnemyType.DRONE;
    }

    /** 弹道生成：武器类型 × 火力等级 × 同类叠加层数共同决定数量与形状。 */
    private void fireWeapon(WeaponType type, int stack) {
        shotsFired++;
        weaponVolleyMask |= 1 << type.ordinal();
        // 火力等级（永久 POWER）+ OVERDRIVE 临时等级 + 同类叠层（每层约等于半级）
        int lvl = ship.powerLevel + (ship.overdriveTimer > 0f ? 1 : 0)
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
                        damageBossPart(p, b.damage);
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
                    boss.damage(b.damage);
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
                    if (b.piercing) b.hitCooldown = 0.06f;
                    else b.active = false;
                    registerHit(b);
                    e.damage(b.damage);
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
                if (ship.overdriveTimer <= 0f) {
                    grazeCharge = Math.min(1f, grazeCharge + GRAZE_CHARGE_PER_BULLET);
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

    /** 统一得分入口：双倍分数道具激活时翻倍。 */
    void addScore(int base) {
        addScore(base, SCORE_OTHER);
    }

    /** 带来源的得分入口，来源用于结算页"分数构成"图。 */
    void addScore(int base, int source) {
        int gained = base * (x2Timer > 0f ? 2 : 1);
        score += gained;
        int idx = source >= 0 && source < SCORE_SOURCES ? source : SCORE_OTHER;
        scoreBySource[idx] += gained;
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

    private void killEnemy(Enemy e) {
        e.active = false;
        // 分裂/子机会复用同一个空闲槽位（很可能就是本机刚让出的槽），
        // 所以先把本机属性取成本地变量，避免后续结算读到被覆写的对象。
        final EnemyType type = e.type;
        final float ex = e.x, ey = e.y;
        final int depth = e.splitDepth;
        comboKills++;
        if (comboKills > maxCombo) maxCombo = comboKills;
        comboTimer = COMBO_WINDOW;
        addScore(type.score * comboMultiplier(), SCORE_ENEMY);
        kills++;
        explode(ex, ey, type.colorHint);
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
        maybeDropPowerUp(type, ex, ey);
        events.add(new GameEvent(EventType.ENEMY_KILLED, ex, ey, type.ordinal()));
        if (comboKills % 5 == 0 && comboKills <= 20) {
            events.add(new GameEvent(EventType.COMBO, ex, ey, comboKills));
        }
    }

    /** Tanks and support hulls drop often, drones rarely. 武器/火力类道具权重最高（雷电式成长曲线）。 */
    private void maybeDropPowerUp(EnemyType type, float x, float y) {
        float chance = type == EnemyType.TANK ? 0.45f
                : type == EnemyType.SPLITTER ? 0.32f
                : type == EnemyType.HEALER ? 0.36f
                : type == EnemyType.SNIPER ? 0.22f
                : type == EnemyType.HUNTER ? 0.12f
                : type == EnemyType.SUICIDER ? 0.04f : 0.05f;
        chance = Math.min(1f, chance * settings.dropRatePct / 100f);
        if (RNG.get().nextFloat() >= chance) return;
        spawnPowerUp(dropKind(RNG.get().nextFloat()), x, y);
    }

    /**
     * 掉落分布。BONUS 只由突袭奖励波投放，不在常规掉落表内。
     * 独立成纯函数便于测试边界。
     */
    public static PowerUp.Kind dropKind(float roll) {
        if (roll < 0.13f) return PowerUp.Kind.SPREAD;
        if (roll < 0.26f) return PowerUp.Kind.POWER;
        if (roll < 0.37f) return PowerUp.Kind.LASER;
        if (roll < 0.47f) return PowerUp.Kind.MISSILE;
        if (roll < 0.56f) return PowerUp.Kind.SHIELD;
        if (roll < 0.64f) return PowerUp.Kind.BOMB;
        if (roll < 0.71f) return PowerUp.Kind.ORBIT;
        if (roll < 0.77f) return PowerUp.Kind.CHAIN;
        if (roll < 0.83f) return PowerUp.Kind.RAPID;
        if (roll < 0.87f) return PowerUp.Kind.X2;
        if (roll < 0.91f) return PowerUp.Kind.LIFE;
        if (roll < 0.95f) return PowerUp.Kind.WINGMAN;
        return PowerUp.Kind.TIME;
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
            ship.makeInvulnerable();
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
        // 雷电式惩罚：坠机火力降 1 级
        if (ship.powerLevel > 1) ship.powerLevel--;
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
            ship.makeInvulnerable();
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
