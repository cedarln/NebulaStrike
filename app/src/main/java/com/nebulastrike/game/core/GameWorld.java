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
        OVERDRIVE, GRAZE, SHIELD_BROKEN, COMBO
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
    public final Bullet[] bullets = new Bullet[96];
    public final EnemyBullet[] enemyBullets = new EnemyBullet[48];
    public final Enemy[] enemies = new Enemy[48];
    public final Particle[] particles = new Particle[256];
    public final PowerUp[] powerups = new PowerUp[12];
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
    public int bossesDefeated;
    int nextBossWave = 4;
    /** boss dead-flash timer for the renderer */
    public float bossFlash;

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
        if (slowTimer > 0f) slowTimer = Math.max(0f, slowTimer - dt);
        float timeScale = slowTimer > 0f ? SLOW_TIME_SCALE : 1f;
        updateDifficulty();
        updateSpawning(dt);
        updateBoss(dt * timeScale);

        // 摇杆出现/消失动画（约 0.12 秒完成，8 倍速率逼近目标值）
        float joyTarget = joyActive ? 1f : 0f;
        if (joyAnim < joyTarget) joyAnim = Math.min(joyTarget, joyAnim + dt * 8f);
        else if (joyAnim > joyTarget) joyAnim = Math.max(joyTarget, joyAnim - dt * 8f);

        boolean fired = ship.update(dt, WIDTH, HEIGHT);
        if (fired) {
            int before = activeBulletCount();
            fireBullet();
            bulletsFired += activeBulletCount() - before;
        }
        updateWingman(dt);
        updateShipFx(dt);

        for (Bullet b : bullets) {
            if (!b.active) continue;
            if (b.homing) steerMissile(b, dt);
            b.update(dt);
            if (b.y < -b.radius || b.x < -40f || b.x > WIDTH + 40f) b.active = false;
        }
        for (PowerUp p : powerups) {
            if (!p.active) continue;
            p.update(dt, ship.x, ship.y);
            if (p.offscreen(HEIGHT)) p.active = false;
        }
        for (Enemy e : enemies) {
            if (!e.active) continue;
            e.update(dt * timeScale, WIDTH);
            if (e.offscreen(HEIGHT)) {
                e.active = false;
                events.add(new GameEvent(EventType.ENEMY_ESCAPED, e.x, HEIGHT));
                continue;
            }
            tryEnemyFire(e, dt * timeScale);
        }
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
        if (boss.readyToAttack()) {
            bossAttack();
            boss.resetAttack();
        }
        if (boss.readyToSummon()) {
            summonMinions();
            boss.resetSummon();
        }
        if (boss.hp <= 0) {
            boss.active = false;
            bossActive = false;
            bossesDefeated++;
            addScore(400 + boss.level * 200);
            explode(boss.x, boss.y, 2);
            explode(boss.x - 60f, boss.y + 30f, 3);
            explode(boss.x + 60f, boss.y - 20f, 0);
            addShake(0.8f, 26f);
            bossFlash = 1f;
            // breathing room before regular spawns resume
            spawnTimer = 2.5f;
            formationTimer = Math.max(formationTimer, 5f);
            // reward: guaranteed pickups
            spawnPowerUp(PowerUp.Kind.SHIELD, boss.x - 50f, boss.y);
            spawnPowerUp(PowerUp.Kind.BOMB, boss.x + 50f, boss.y);
            spawnPowerUp(PowerUp.Kind.MISSILE, boss.x, boss.y + 40f);
            events.add(new GameEvent(EventType.BOSS_DEFEATED, boss.x, boss.y));
        }
    }

    public boolean bossActive;

    private void bossAttack() {
        switch (boss.phase) {
            case 1: // wide fan of 7
                for (int i = -3; i <= 3; i++) {
                    EnemyBullet b = freeEnemyBullet();
                    if (b == null) return;
                    float ang = (float) (Math.PI / 2 + i * 0.22);
                    b.initRadius(boss.x, boss.y + Boss.RADIUS * 0.7f,
                            (float) Math.cos(ang) * 300f, (float) Math.sin(ang) * 300f, 13f);
                }
                break;
            case 2: // fan + aimed twin volley
                for (int i = -2; i <= 2; i++) {
                    EnemyBullet b = freeEnemyBullet();
                    if (b == null) return;
                    float ang = (float) (Math.PI / 2 + i * 0.18);
                    b.initRadius(boss.x, boss.y + Boss.RADIUS * 0.7f,
                            (float) Math.cos(ang) * 330f, (float) Math.sin(ang) * 330f, 13f);
                }
                for (int i = -1; i <= 1; i += 2) {
                    EnemyBullet b = freeEnemyBullet();
                    if (b == null) return;
                    float dx = ship.x - boss.x, dy = ship.y - boss.y;
                    float len = (float) Math.sqrt(dx * dx + dy * dy);
                    b.initRadius(boss.x + i * 40f, boss.y + Boss.RADIUS * 0.5f,
                            dx / len * 430f, dy / len * 430f, 11f);
                }
                break;
            default: // phase 3: dense double helix
                float spin = boss.age * 2.4f;
                for (int i = 0; i < 8; i++) {
                    EnemyBullet b = freeEnemyBullet();
                    if (b == null) return;
                    float ang = (float) (Math.PI / 2 + spin + i * Math.PI / 4);
                    b.initRadius(boss.x, boss.y + Boss.RADIUS * 0.6f,
                            (float) Math.cos(ang) * 320f, (float) Math.sin(ang) * 320f, 12f);
                }
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
        float margin = type.radius + 30f;
        float x = margin + RNG.get().nextFloat() * (WIDTH - margin * 2);
        e.init(type, x, -type.radius - 10f, enemySpeedScale);
    }

    /** Enemy-type mix drifts heavier as the run goes on (1.6x faster in endless). */
    private EnemyType pickEnemyType() {
        float t = mode == GameMode.ENDLESS ? timeAlive * 1.6f : timeAlive;
        float r = RNG.get().nextFloat();
        float wTank = t > 35f ? Math.min(0.20f, 0.12f + (t - 35f) / 600f) : 0f;
        float wHunter = t > 20f ? Math.min(0.32f, 0.25f + (t - 20f) / 800f) : 0f;
        float wWeaver = t > 10f ? Math.min(0.35f, 0.30f + (t - 10f) / 1000f) : 0f;
        if (r < wTank) return EnemyType.TANK;
        if (r < wTank + wHunter) return EnemyType.HUNTER;
        if (r < wTank + wHunter + wWeaver) return EnemyType.WEAVER;
        return EnemyType.DRONE;
    }

    /** 弹道生成：武器类型 × 火力等级（1..3）共同决定数量与形状。 */
    private void fireBullet() {
        shotsFired++;
        // OVERDRIVE 期间临时提升一级火力，和永久火力等级叠加但不超过上限
        int lvl = Math.min(Ship.MAX_POWER,
                ship.powerLevel + (ship.overdriveTimer > 0f ? 1 : 0));
        switch (ship.weapon) {
            case SPREAD: {
                // 3 / 5 / 7 向扇形
                int rays = 1 + lvl;                       // 2..4 → 每侧 rays-1
                for (int i = -(rays - 1); i <= rays - 1; i++) {
                    Bullet b = freeBullet();
                    if (b == null) return;
                    float ang = (float) (-Math.PI / 2 + i * 0.22);
                    b.init(ship.x, ship.y - Ship.RADIUS - 6f,
                            (float) Math.cos(ang) * 1150f, (float) Math.sin(ang) * 1150f,
                            WeaponType.SPREAD.damage, false);
                }
                break;
            }
            case LASER: {
                // 1 / 2 / 3 束穿透激光
                int beams = lvl;
                for (int i = 0; i < beams; i++) {
                    Bullet b = freeBullet();
                    if (b == null) return;
                    float off = (i - (beams - 1) / 2f) * 26f;
                    b.initLaser(ship.x + off, ship.y - Ship.RADIUS - 10f,
                            0f, -1500f, WeaponType.LASER.damage);
                }
                break;
            }
            case MISSILE: {
                // 2 / 3 / 4 发追踪
                if (lvl == 1) {
                    for (int i = -1; i <= 1; i += 2) {
                        Bullet b = freeBullet();
                        if (b == null) return;
                        b.init(ship.x + i * 18f, ship.y - Ship.RADIUS - 6f,
                                i * 220f, -640f, WeaponType.MISSILE.damage, true);
                    }
                } else {
                    Bullet b = freeBullet(); // 中路直发追踪
                    if (b == null) return;
                    b.init(ship.x, ship.y - Ship.RADIUS - 6f, 0f, -700f,
                            WeaponType.MISSILE.damage, true);
                    int pair = lvl == 2 ? 1 : 2;
                    for (int i = -pair; i <= pair; i += 2) {
                        b = freeBullet();
                        if (b == null) return;
                        b.init(ship.x + i * 20f, ship.y - Ship.RADIUS - 6f,
                                i * 260f, -620f, WeaponType.MISSILE.damage, true);
                    }
                }
                break;
            }
            default: {
                // VULCAN：单发 / 双发平行 / 三向
                if (lvl == 1) {
                    Bullet b = freeBullet();
                    if (b == null) return;
                    b.init(ship.x, ship.y - Ship.RADIUS - 6f, -1250f);
                } else {
                    for (int i = -1; i <= 1; i += 2) {
                        Bullet b = freeBullet();
                        if (b == null) return;
                        b.init(ship.x + i * 16f, ship.y - Ship.RADIUS - 6f, -1250f);
                    }
                    if (lvl >= 3) {
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
        // boss: body vs ship, then bullets vs boss
        if (boss.active && !ship.invulnerable) {
            float dx = ship.x - boss.x, dy = ship.y - boss.y;
            float rr = Ship.RADIUS + Boss.RADIUS;
            if (dx * dx + dy * dy <= rr * rr) {
                onPlayerHit();
            }
        }
        if (boss.active) {
            for (Bullet b : bullets) {
                if (!b.active || b.hitCooldown > 0f) continue;
                float dx = b.x - boss.x, dy = b.y - boss.y;
                float rr = b.radius + Boss.RADIUS;
                if (dx * dx + dy * dy <= rr * rr) {
                    if (b.piercing) b.hitCooldown = 0.06f; // 激光穿透，冷却防连击
                    else b.active = false;
                    registerHit(b);
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
                    if (e.hp <= 0) {
                        killEnemy(e);
                        if (b.piercing) continue; // 激光穿过残骸继续命中后面的敌机
                        break;
                    }
                }
            }
            if (!e.active) continue;
            // enemies vs ship（扫掠：高速拖动时不会瞬移穿过敌机）
            if (!ship.invulnerable) {
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
        if (!ship.invulnerable) {
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
                ship.weapon = WeaponType.SPREAD;
                ship.weaponTimer = Ship.WEAPON_DURATION;
                break;
            case MISSILE:
                ship.weapon = WeaponType.MISSILE;
                ship.weaponTimer = Ship.WEAPON_DURATION;
                break;
            case LASER:
                ship.weapon = WeaponType.LASER;
                ship.weaponTimer = Ship.WEAPON_DURATION;
                break;
            case POWER:
                if (ship.powerLevel < Ship.MAX_POWER) ship.powerLevel++;
                else addScore(300); // 满级折算奖励分（雷电式）
                break;
            case SHIELD:
                ship.hasShield = true;
                break;
            case BOMB:
                if (ship.bombs < Ship.MAX_BOMBS) ship.bombs++;
                else addScore(50); // overflow converts to salvage points
                break;
            case RAPID:
                ship.rapidTimer = Ship.RAPID_DURATION;
                break;
            case X2:
                x2Timer = X2_DURATION;
                break;
            case LIFE:
                if (lives < Math.max(MAX_LIVES, settings.startLives)) lives++;
                else addScore(200); // 满命时折算奖励分
                break;
            case WINGMAN:
                ship.wingmanTimer = Ship.WINGMAN_DURATION;
                break;
            case TIME:
                slowTimer = SLOW_DURATION;
                break;
        }
        events.add(new GameEvent(EventType.POWERUP_COLLECTED, p.x, p.y, p.kind.ordinal()));
    }

    /** 统一得分入口：双倍分数道具激活时翻倍。 */
    void addScore(int base) {
        score += base * (x2Timer > 0f ? 2 : 1);
    }

    /** Second-finger tap: clears bullets, damages everything, huge shake. */
    public void detonateBomb() {
        if (state != State.PLAYING || ship.bombs <= 0) return;
        ship.bombs--;
        bombFlash = 1f;
        for (EnemyBullet b : enemyBullets) {
            if (b.active) {
                b.active = false;
                spark(b.x, b.y, 2, 0);
            }
        }
        for (Enemy e : enemies) {
            if (!e.active) continue;
            e.damage(3);
            e.flash = 0.2f;
            if (e.hp <= 0) killEnemy(e);
        }
        addShake(0.55f, 24f);
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
        comboKills++;
        if (comboKills > maxCombo) maxCombo = comboKills;
        comboTimer = COMBO_WINDOW;
        addScore(e.type.score * comboMultiplier());
        kills++;
        explode(e.x, e.y, e.type.colorHint);
        addShake(e.type == EnemyType.TANK ? 0.22f : 0.05f,
                 e.type == EnemyType.TANK ? 9f : 2.5f);
        maybeDropPowerUp(e);
        events.add(new GameEvent(EventType.ENEMY_KILLED, e.x, e.y, e.type.ordinal()));
        if (comboKills % 5 == 0 && comboKills <= 20) {
            events.add(new GameEvent(EventType.COMBO, e.x, e.y, comboKills));
        }
    }

    /** Tanks drop often, drones rarely. 武器/火力类道具权重最高（雷电式成长曲线）。 */
    private void maybeDropPowerUp(Enemy e) {
        float chance = e.type == EnemyType.TANK ? 0.45f
                : e.type == EnemyType.HUNTER ? 0.12f : 0.05f;
        chance = Math.min(1f, chance * settings.dropRatePct / 100f);
        if (RNG.get().nextFloat() >= chance) return;
        spawnPowerUp(dropKind(RNG.get().nextFloat()), e.x, e.y);
    }

    /** 掉落分布（11 种）。独立成纯函数便于测试边界。 */
    public static PowerUp.Kind dropKind(float roll) {
        if (roll < 0.15f) return PowerUp.Kind.SPREAD;
        if (roll < 0.29f) return PowerUp.Kind.POWER;
        if (roll < 0.41f) return PowerUp.Kind.LASER;
        if (roll < 0.52f) return PowerUp.Kind.MISSILE;
        if (roll < 0.63f) return PowerUp.Kind.SHIELD;
        if (roll < 0.72f) return PowerUp.Kind.BOMB;
        if (roll < 0.79f) return PowerUp.Kind.RAPID;
        if (roll < 0.84f) return PowerUp.Kind.X2;
        if (roll < 0.88f) return PowerUp.Kind.LIFE;
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
                break;
            case OPEN_CHEATS:
                cheatOpen = true;
                settingsOpen = false;
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

    /** 菜单里的设置/LAB 面板互斥；返回键统一关闭。 */
    public void closePanels() {
        settingsOpen = false;
        cheatOpen = false;
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
        if (ship.hasShield) { // bubble eats the hit
            ship.hasShield = false;
            ship.makeInvulnerable();
            addShake(0.25f, 10f);
            events.add(new GameEvent(EventType.SHIELD_BROKEN, ship.x, ship.y));
            return;
        }
        comboKills = 0;
        comboTimer = 0f;
        hitsTaken++;
        addShake(0.4f, 16f);
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
        joyActive = false;
        joyAnim = 0f;
        boss.active = false;
        bossActive = false;
        bossFlash = 0f;
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
