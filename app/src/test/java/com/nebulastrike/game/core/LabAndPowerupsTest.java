package com.nebulastrike.game.core;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * v1.6：LAB（金手指）参数、僚机/缓时道具、拖动跟手与扫掠碰撞。
 * 只测试纯 Java 模拟层。
 */
public class LabAndPowerupsTest {

    // ---- LAB 参数生效 ----

    @Test
    public void labSettingsApplyOnRestart() {
        GameWorld w = new GameWorld(11L, true);
        w.settings.startLives = 6;
        w.settings.startPower = 2;
        w.handleUiAction(Ui.Action.START);
        assertEquals(6, w.lives);
        assertEquals(2, w.ship.powerLevel);
    }

    @Test
    public void godModeIgnoresDamage() {
        GameWorld w = new GameWorld(11L, true);
        w.settings.godMode = true;
        w.handleUiAction(Ui.Action.START);
        w.spawningEnabled = false;
        EnemyBullet b = freeEnemyBullet(w);
        b.init(w.ship.x, w.ship.y, 0f, 0f);
        w.update(1f / 60f);
        assertEquals(3, w.lives);
        assertEquals(GameWorld.State.PLAYING, w.state);
        assertFalse("命中判定仍然消耗子弹", b.active);
    }

    @Test
    public void dropRateMultiplierForcesTankDrop() {
        GameWorld w = new GameWorld(5L, true);
        w.settings.dropRatePct = Settings.DROP_MAX; // 300% -> tank 45% clamped to 100%
        w.handleUiAction(Ui.Action.START);
        w.spawningEnabled = false;
        Enemy tank = firstFreeEnemy(w);
        tank.init(EnemyType.TANK, 200f, 200f, 1f);
        w.killEnemyForTest(tank);
        assertEquals(1, activePowerUps(w));
    }

    @Test
    public void enemySpeedSettingScalesMovement() {
        GameWorld slow = new GameWorld(5L, true);
        slow.settings.enemySpeedPct = Settings.SPEED_MIN;
        slow.handleUiAction(Ui.Action.START);
        slow.spawningEnabled = false;
        slow.update(1f / 60f);
        Enemy a = firstFreeEnemy(slow);
        a.init(EnemyType.DRONE, 450f, 200f, slow.enemySpeedScale);
        float startA = a.y;
        for (int i = 0; i < 30; i++) slow.update(1f / 60f);
        float slowDy = a.y - startA;

        GameWorld fast = new GameWorld(5L, true);
        fast.settings.enemySpeedPct = Settings.SPEED_MAX;
        fast.handleUiAction(Ui.Action.START);
        fast.spawningEnabled = false;
        fast.update(1f / 60f);
        Enemy b = firstFreeEnemy(fast);
        b.init(EnemyType.DRONE, 450f, 200f, fast.enemySpeedScale);
        float startB = b.y;
        for (int i = 0; i < 30; i++) fast.update(1f / 60f);
        float fastDy = b.y - startB;

        assertTrue("200% 敌速应明显快于 50%: " + fastDy + " vs " + slowDy,
                fastDy > slowDy * 2.5f);
    }

    @Test
    public void cheatResetRestoresDefaults() {
        GameWorld w = new GameWorld(11L, true);
        w.settings.startLives = 8;
        w.settings.dropRatePct = 300;
        w.settings.enemySpeedPct = 50;
        w.settings.startPower = 3;
        w.settings.godMode = true;
        w.handleUiAction(Ui.Action.CHEAT_RESET);
        assertFalse(w.settings.cheatsActive());
        assertEquals(3, w.settings.startLives);
        assertEquals(100, w.settings.dropRatePct);
        assertEquals(100, w.settings.enemySpeedPct);
        assertEquals(1, w.settings.startPower);
    }

    // ---- 僚机 WINGMAN ----

    @Test
    public void wingmanFiresDroneBulletsAndExpires() {
        GameWorld w = new GameWorld(5L, true);
        w.handleUiAction(Ui.Action.START);
        w.spawningEnabled = false;
        w.spawnPowerUp(PowerUp.Kind.WINGMAN, w.ship.x, w.ship.y);
        w.update(1f / 60f);
        assertEquals(Ship.WINGMAN_DURATION, w.ship.wingmanTimer, 1e-3f);

        clearBullets(w);
        for (int i = 0; i < 30; i++) w.update(1f / 60f);
        assertTrue("僚机应自动开火", droneBullets(w) > 0);

        w.ship.wingmanTimer = 0.1f;
        for (int i = 0; i < 10; i++) w.update(1f / 60f);
        assertEquals(0f, w.ship.wingmanTimer, 1e-3f);
        clearBullets(w);
        for (int i = 0; i < 30; i++) w.update(1f / 60f);
        assertEquals("过期后不再发射", 0, droneBullets(w));
    }

    // ---- 缓时 TIME ----

    @Test
    public void timePowerUpSlowsProjectilesThenExpires() {
        GameWorld w = new GameWorld(5L, true);
        w.handleUiAction(Ui.Action.START);
        w.spawningEnabled = false;
        clearEnemies(w);
        w.spawnPowerUp(PowerUp.Kind.TIME, w.ship.x, w.ship.y);
        w.update(1f / 60f);
        assertEquals(GameWorld.SLOW_DURATION, w.slowTimer, 1e-3f);

        EnemyBullet b = freeEnemyBullet(w);
        b.init(100f, 400f, 0f, 300f);
        for (int i = 0; i < 60; i++) w.update(1f / 60f);
        float slowDy = b.y - 400f;
        assertTrue("缓时中子弹应明显变慢", slowDy < 160f);

        w.slowTimer = 0f;
        b.y = 400f;
        for (int i = 0; i < 60; i++) w.update(1f / 60f);
        float normalDy = b.y - 400f;
        assertTrue("缓时结束后恢复正常速度: " + slowDy + " vs " + normalDy,
                normalDy > slowDy * 1.8f);
    }

    // ---- 拖动跟手 ----

    @Test
    public void dragReachesTargetQuicklyAndSettlesExactly() {
        Ship s = new Ship();
        s.reset(100f, 800f);
        s.setTarget(400f, 800f);
        for (int i = 0; i < 12; i++) s.update(1f / 60f, GameWorld.WIDTH, GameWorld.HEIGHT);
        assertTrue("0.2 秒内应完成 300px 位移的 96%+，x=" + s.x, s.x > 390f);

        s.reset(100f, 800f);
        s.setTarget(101.2f, 800f);
        s.update(1f / 60f, GameWorld.WIDTH, GameWorld.HEIGHT);
        assertEquals("1px 级小位移应一帧到位", 101.2f, s.x, 1e-4f);
    }

    // ---- 扫掠碰撞：高速拖动不穿弹 ----

    @Test
    public void sweptCollisionCatchesBulletCrossedBetweenFrames() {
        GameWorld w = new GameWorld(5L, true);
        w.handleUiAction(Ui.Action.START);
        w.spawningEnabled = false;
        clearEnemies(w);
        clearEnemyBullets(w);
        w.ship.reset(450f, 1000f);
        w.ship.hasShield = false;
        w.ship.invulnerable = false;
        w.moveShipTo(450f, 600f);
        EnemyBullet b = freeEnemyBullet(w);
        b.init(450f, 940f, 0f, 0f); // 位于本帧扫掠路径中间
        int lives = w.lives;
        w.update(1f / 20f); // 120px/帧：离散点位检测会漏判
        assertEquals(lives - 1, w.lives);
        assertFalse(b.active);
    }

    // ---- helpers ----

    private static Enemy firstFreeEnemy(GameWorld w) {
        for (Enemy e : w.enemies) if (!e.active) return e;
        throw new AssertionError("enemy pool exhausted");
    }

    private static EnemyBullet freeEnemyBullet(GameWorld w) {
        for (EnemyBullet b : w.enemyBullets) if (!b.active) return b;
        throw new AssertionError("enemy bullet pool exhausted");
    }

    private static int activePowerUps(GameWorld w) {
        int n = 0;
        for (PowerUp p : w.powerups) if (p.active) n++;
        return n;
    }

    private static int droneBullets(GameWorld w) {
        int n = 0;
        for (Bullet b : w.bullets) if (b.active && b.drone) n++;
        return n;
    }

    private static void clearBullets(GameWorld w) {
        for (Bullet b : w.bullets) b.active = false;
    }

    private static void clearEnemies(GameWorld w) {
        for (Enemy e : w.enemies) e.active = false;
    }

    private static void clearEnemyBullets(GameWorld w) {
        for (EnemyBullet b : w.enemyBullets) b.active = false;
    }
}
