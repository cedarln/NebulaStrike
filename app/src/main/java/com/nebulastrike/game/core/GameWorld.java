package com.nebulastrike.game.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Central simulation of the game. Runs in a fixed virtual space (1600x900) that the
 * renderer scales to the actual display, keeping gameplay identical on every device.
 * Pure Java: fully unit-testable on the JVM.
 */
public class GameWorld {
    public static final float WIDTH = 1600f;
    public static final float HEIGHT = 900f;

    public enum State { MENU, PLAYING, PAUSED, GAME_OVER }

    /** Game events consumed by the presentation layer (sfx, haptics, fx). */
    public enum EventType {
        ENEMY_KILLED, PLAYER_HIT, GAME_OVER, ENEMY_ESCAPED, FORMATION,
        POWERUP_COLLECTED, BOMB, BOSS_SPAWNED, BOSS_DEFEATED, BOSS_PHASE
    }
    public static final class GameEvent {
        public final EventType type;
        public final float x, y;
        public GameEvent(EventType type, float x, float y) {
            this.type = type;
            this.x = x;
            this.y = y;
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

    public State state = State.PLAYING;
    /** seconds spent in the current non-PLAYING state (UI guards use it) */
    public float stateTimer;
    /** active play mode; set by restart(mode) when a run launches from the menu */
    public GameMode mode = GameMode.WAVES;
    public int score;
    public int lives = 3;
    public int kills;
    public int spawnedCount;
    public int shotsFired;
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

    // persistence-backed state
    public int bestScore;
    public boolean newBest;
    /** local top-5 scores, descending; 0 = empty slot. Two boards: WAVES / ENDLESS */
    public final int[] leaderboard = new int[5];
    public final int[] leaderboardEndless = new int[5];
    /** 0-based rank of the last finished run on the board for the active mode; -1 = not on board */
    public int scoreRank = -1;
    public final Settings settings = new Settings();
    public boolean settingsOpen;
    private SettingsStore store;

    // ---- 应用内升级检测（状态机；网络 IO 由 app 层异步执行，这里只存状态） ----
    public enum UpdateState { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, ERROR }
    public UpdateState updateState = UpdateState.IDLE;
    public String updateVersion;          // 检测到的新版本 tag，如 "v1.2.0"
    public String updateUrl;              // 新 APK 下载地址
    public String updateError;            // 失败提示
    public int updateProgress;            // 下载进度 0-100
    public float updateMsgTimer;          // UP_TO_DATE / ERROR 提示的自动消隐
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

    /** Drags the ship via touch input. */
    public void moveShipTo(float x, float y) {
        if (state == State.PLAYING) ship.setTarget(x, y);
    }

    /** Steps the simulation. dt is clamped so backgrounding cannot explode physics. */
    public void update(float dt) {
        dt = Math.min(dt, 1f / 20f);
        if (state != State.PLAYING) {
            stateTimer += dt;
            for (Star s : stars) s.update(dt * (state == State.PAUSED ? 0.1f : 0.3f), HEIGHT);
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
        updateDifficulty();
        updateSpawning(dt);
        updateBoss(dt);

        boolean fired = ship.update(dt, WIDTH, HEIGHT);
        if (fired) fireBullet();
        updateShipFx(dt);

        for (Bullet b : bullets) {
            if (!b.active) continue;
            if (b.homing) steerMissile(b);
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
            e.update(dt, WIDTH);
            if (e.offscreen(HEIGHT)) {
                e.active = false;
                events.add(new GameEvent(EventType.ENEMY_ESCAPED, e.x, HEIGHT));
                continue;
            }
            tryEnemyFire(e, dt);
        }
        for (EnemyBullet b : enemyBullets) {
            if (!b.active) continue;
            b.update(dt);
            if (b.offscreen(WIDTH, HEIGHT)) b.active = false;
        }
        for (Particle p : particles) if (p.active) p.update(dt);
        for (Star s : stars) s.update(dt, HEIGHT);

        if (comboTimer > 0f) {
            comboTimer -= dt;
            if (comboTimer <= 0f) comboKills = 0;
        }
        if (shakeTime > 0f) {
            shakeTime -= dt;
            if (shakeTime <= 0f) shakeMag = 0f;
        }
        if (bombFlash > 0f) bombFlash = Math.max(0f, bombFlash - dt * 2.5f);
        if (bossFlash > 0f) bossFlash = Math.max(0f, bossFlash - dt * 2f);

        resolveCollisions();
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
            events.add(new GameEvent(EventType.BOSS_PHASE, boss.x, boss.y));
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
            score += 400 + boss.level * 200;
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

    private void fireBullet() {
        shotsFired++;
        switch (ship.weapon) {
            case SPREAD: {
                for (int i = -1; i <= 1; i++) {
                    Bullet b = freeBullet();
                    if (b == null) return;
                    float ang = (float) (-Math.PI / 2 + i * 0.24);
                    b.init(ship.x, ship.y - Ship.RADIUS - 6f,
                            (float) Math.cos(ang) * 1150f, (float) Math.sin(ang) * 1150f,
                            WeaponType.SPREAD.damage, false);
                }
                break;
            }
            case MISSILE: {
                for (int i = -1; i <= 1; i += 2) {
                    Bullet b = freeBullet();
                    if (b == null) return;
                    b.init(ship.x + i * 18f, ship.y - Ship.RADIUS - 6f,
                            i * 220f, -640f, WeaponType.MISSILE.damage, true);
                }
                break;
            }
            default: {
                Bullet b = freeBullet();
                if (b == null) return;
                b.init(ship.x, ship.y - Ship.RADIUS - 6f, -1250f);
            }
        }
    }

    /** Homing missiles curve toward the closest enemy. */
    private void steerMissile(Bullet b) {
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
        float turn = 6.5f * (1f / 60f); // frame-normalized steering
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
                if (!b.active) continue;
                float dx = b.x - boss.x, dy = b.y - boss.y;
                float rr = b.radius + Boss.RADIUS;
                if (dx * dx + dy * dy <= rr * rr) {
                    b.active = false;
                    boss.damage(b.damage);
                    spark(b.x, b.y, 2, 2);
                    if (boss.hp <= 0) break;
                }
            }
        }
        for (Enemy e : enemies) {
            if (!e.active) continue;
            // bullets vs enemies
            for (Bullet b : bullets) {
                if (!b.active) continue;
                float dx = b.x - e.x, dy = b.y - e.y;
                float rr = b.radius + e.type.radius;
                if (dx * dx + dy * dy <= rr * rr) {
                    b.active = false;
                    e.damage(b.damage);
                    spark(b.x, b.y, 3, 0);
                    if (e.hp <= 0) killEnemy(e);
                    break;
                }
            }
            if (!e.active) continue;
            // enemies vs ship
            if (!ship.invulnerable) {
                float dx = ship.x - e.x, dy = ship.y - e.y;
                float rr = Ship.RADIUS + e.type.radius;
                if (dx * dx + dy * dy <= rr * rr) {
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
                float dx = ship.x - b.x, dy = ship.y - b.y;
                float rr = Ship.RADIUS + b.radius;
                if (dx * dx + dy * dy <= rr * rr) {
                    b.active = false;
                    spark(b.x, b.y, 6, 2);
                    onPlayerHit();
                    break;
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
            case SHIELD:
                ship.hasShield = true;
                break;
            case BOMB:
                if (ship.bombs < Ship.MAX_BOMBS) ship.bombs++;
                else score += 50; // overflow converts to salvage points
                break;
        }
        events.add(new GameEvent(EventType.POWERUP_COLLECTED, p.x, p.y));
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

    private void killEnemy(Enemy e) {
        e.active = false;
        comboKills++;
        comboTimer = COMBO_WINDOW;
        score += e.type.score * comboMultiplier();
        kills++;
        explode(e.x, e.y, e.type.colorHint);
        addShake(e.type == EnemyType.TANK ? 0.22f : 0.05f,
                 e.type == EnemyType.TANK ? 9f : 2.5f);
        maybeDropPowerUp(e);
        events.add(new GameEvent(EventType.ENEMY_KILLED, e.x, e.y));
    }

    /** Tanks drop often, drones rarely. Weights favour weapons early. */
    private void maybeDropPowerUp(Enemy e) {
        float chance = e.type == EnemyType.TANK ? 0.45f
                : e.type == EnemyType.HUNTER ? 0.12f : 0.05f;
        if (RNG.get().nextFloat() >= chance) return;
        float roll = RNG.get().nextFloat();
        PowerUp.Kind kind;
        if (roll < 0.32f) kind = PowerUp.Kind.SPREAD;
        else if (roll < 0.60f) kind = PowerUp.Kind.MISSILE;
        else if (roll < 0.82f) kind = PowerUp.Kind.SHIELD;
        else kind = PowerUp.Kind.BOMB;
        spawnPowerUp(kind, e.x, e.y);
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
                settingsOpen = false;
                restart(GameMode.WAVES);
                break;
            case START_ENDLESS:
                settingsOpen = false;
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
                settingsOpen = false;
                state = State.MENU;
                stateTimer = 0f;
                break;
            case PAUSE:
                if (state == State.PLAYING) {
                    state = State.PAUSED;
                    stateTimer = 0f;
                }
                break;
        }
    }

    private void saveSettings() {
        if (store != null) store.save(settings);
    }

    /** Pauses gameplay (used by lifecycle callbacks). */
    public void pauseIfPlaying() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
            stateTimer = 0f;
        }
    }

    private void onPlayerHit() {
        if (ship.hasShield) { // bubble eats the hit
            ship.hasShield = false;
            ship.makeInvulnerable();
            addShake(0.25f, 10f);
            events.add(new GameEvent(EventType.POWERUP_COLLECTED, ship.x, ship.y));
            return;
        }
        comboKills = 0;
        comboTimer = 0f;
        addShake(0.4f, 16f);
        lives--;
        events.add(new GameEvent(EventType.PLAYER_HIT, ship.x, ship.y));
        if (lives <= 0) {
            lives = 0;
            state = State.GAME_OVER;
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
        lives = 3;
        kills = 0;
        spawnedCount = 0;
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
        boss.active = false;
        bossActive = false;
        bossFlash = 0f;
        bossesDefeated = 0;
        nextBossWave = m == GameMode.ENDLESS ? 3 : 4; // endless: bosses come faster
        formationTimer = m == GameMode.ENDLESS ? 6f : 8f;
        spawnTimer = 0f;
        state = State.PLAYING;
        ship.reset(WIDTH / 2f, HEIGHT - 140f);
        for (Bullet b : bullets) b.active = false;
        for (EnemyBullet b : enemyBullets) b.active = false;
        for (Enemy e : enemies) e.active = false;
        for (PowerUp p : powerups) p.active = false;
        for (Particle p : particles) p.active = false;
    }

    private void explode(float x, float y, int color) {
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
        n = (int) (n * emissionScale);
        for (int i = 0; i < n; i++) {
            Particle p = freeParticle();
            float ang = RNG.get().nextFloat() * (float) Math.PI * 2f;
            float spd = 60f + RNG.get().nextFloat() * 160f;
            p.init(x, y, (float) Math.cos(ang) * spd, (float) Math.sin(ang) * spd, 0.22f, 3f, color);
        }
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
