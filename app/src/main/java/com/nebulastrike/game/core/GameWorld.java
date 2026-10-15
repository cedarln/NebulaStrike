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

    public enum State { PLAYING, GAME_OVER }

    /** Game events consumed by the presentation layer (sfx, haptics, fx). */
    public enum EventType { ENEMY_KILLED, PLAYER_HIT, GAME_OVER, ENEMY_ESCAPED }
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
    public final Bullet[] bullets = new Bullet[64];
    public final Enemy[] enemies = new Enemy[48];
    public final Particle[] particles = new Particle[256];
    public final Star[] stars = new Star[90];

    public State state = State.PLAYING;
    /** seconds spent in the current non-PLAYING state (UI guards use it) */
    public float stateTimer;
    public int score;
    public int lives = 3;
    public int kills;
    public int spawnedCount;
    public float timeAlive;
    public int wave = 1;

    private float spawnTimer;
    private float enemySpeedScale = 1f;
    private List<GameEvent> events = new ArrayList<>();
    private List<GameEvent> eventsOut = new ArrayList<>();

    public GameWorld(long seed) {
        RNG.setSeed(seed);
        for (int i = 0; i < bullets.length; i++) bullets[i] = new Bullet();
        for (int i = 0; i < enemies.length; i++) enemies[i] = new Enemy();
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
            for (Star s : stars) s.update(dt * 0.3f, HEIGHT);
            return;
        }
        timeAlive += dt;
        updateDifficulty();
        updateSpawning(dt);

        boolean fired = ship.update(dt, WIDTH, HEIGHT);
        if (fired) fireBullet();

        for (Bullet b : bullets) {
            if (!b.active) continue;
            b.update(dt);
            if (b.y < -b.radius) b.active = false;
        }
        for (Enemy e : enemies) {
            if (!e.active) continue;
            e.update(dt, WIDTH);
            if (e.offscreen(HEIGHT)) {
                e.active = false;
                events.add(new GameEvent(EventType.ENEMY_ESCAPED, e.x, HEIGHT));
            }
        }
        for (Particle p : particles) if (p.active) p.update(dt);
        for (Star s : stars) s.update(dt, HEIGHT);

        resolveCollisions();
        if (score > wave * 500) wave++;
    }

    private void updateDifficulty() {
        // Over 2 minutes spawn interval shrinks and enemies accelerate up to +60%.
        float t = Math.min(timeAlive / 120f, 1f);
        spawnInterval = 1.1f - 0.75f * t;
        enemySpeedScale = 1f + 0.6f * t;
    }

    private float spawnInterval = 1.1f;

    private void updateSpawning(float dt) {
        spawnTimer -= dt;
        if (spawnTimer <= 0f) {
            spawnTimer = spawnInterval;
            spawnEnemy();
        }
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

    private EnemyType pickEnemyType() {
        if (timeAlive > 20f && RNG.get().nextFloat() < 0.25f) return EnemyType.HUNTER;
        if (timeAlive > 10f && RNG.get().nextFloat() < 0.30f) return EnemyType.WEAVER;
        return EnemyType.DRONE;
    }

    private void fireBullet() {
        Bullet b = freeBullet();
        if (b == null) return;
        b.init(ship.x, ship.y - Ship.RADIUS - 6f, -1250f);
    }

    private void resolveCollisions() {
        for (Enemy e : enemies) {
            if (!e.active) continue;
            // bullets vs enemies
            for (Bullet b : bullets) {
                if (!b.active) continue;
                float dx = b.x - e.x, dy = b.y - e.y;
                float rr = b.radius + e.type.radius;
                if (dx * dx + dy * dy <= rr * rr) {
                    b.active = false;
                    e.damage(1);
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
    }

    private void killEnemy(Enemy e) {
        e.active = false;
        score += e.type.score;
        kills++;
        explode(e.x, e.y, e.type.colorHint);
        events.add(new GameEvent(EventType.ENEMY_KILLED, e.x, e.y));
    }

    private void onPlayerHit() {
        lives--;
        events.add(new GameEvent(EventType.PLAYER_HIT, ship.x, ship.y));
        if (lives <= 0) {
            lives = 0;
            state = State.GAME_OVER;
            events.add(new GameEvent(EventType.GAME_OVER, ship.x, ship.y));
        } else {
            ship.makeInvulnerable();
        }
    }

    public void restart() {
        score = 0;
        lives = 3;
        kills = 0;
        spawnedCount = 0;
        timeAlive = 0f;
        stateTimer = 0f;
        wave = 1;
        spawnTimer = 0f;
        state = State.PLAYING;
        ship.reset(WIDTH / 2f, HEIGHT - 140f);
        for (Bullet b : bullets) b.active = false;
        for (Enemy e : enemies) e.active = false;
        for (Particle p : particles) p.active = false;
    }

    private void explode(float x, float y, int color) {
        for (int i = 0; i < 14; i++) {
            Particle p = freeParticle();
            if (p == null) return;
            float ang = RNG.get().nextFloat() * (float) Math.PI * 2f;
            float spd = 80f + RNG.get().nextFloat() * 320f;
            p.init(x, y, (float) Math.cos(ang) * spd, (float) Math.sin(ang) * spd,
                    0.35f + RNG.get().nextFloat() * 0.35f, 4f + RNG.get().nextFloat() * 5f, color);
        }
    }

    private void spark(float x, float y, int n, int color) {
        for (int i = 0; i < n; i++) {
            Particle p = freeParticle();
            if (p == null) return;
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

    private Enemy freeEnemy() {
        for (Enemy e : enemies) if (!e.active) return e;
        return null;
    }

    private Particle freeParticle() {
        for (Particle p : particles) if (!p.active) return p;
        return null;
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
