package com.nebulastrike.game.core;

import org.junit.Test;

import static org.junit.Assert.*;

/** 擦弹充能 / OVERDRIVE、命中统计与左手模式。 */
public class GrazeAndStatsTest {

    // ---- 擦弹 ----

    @Test
    public void grazeChargesOncePerBulletAndTriggersOverdrive() {
        GameWorld w = new GameWorld(5L, true);
        w.handleUiAction(Ui.Action.START);
        w.spawningEnabled = false;
        w.update(1f / 60f);
        clearEnemies(w);
        clearBullets(w);
        clearEnemyBullets(w);
        w.ship.reset(450f, 1000f);
        w.ship.invulnerable = false;

        EnemyBullet b = freeEnemyBullet(w);
        b.init(510f, 1000f, 0f, 0f); // 距飞船 60：在命中(44)与擦弹(70)之间
        w.update(1f / 60f);
        assertEquals(1, w.grazes);
        assertEquals(GameWorld.GRAZE_CHARGE_PER_BULLET, w.grazeCharge, 1e-4f);

        w.update(1f / 60f); // 同一颗弹不重复充能
        assertEquals(1, w.grazes);
        assertEquals(GameWorld.GRAZE_CHARGE_PER_BULLET, w.grazeCharge, 1e-4f);

        w.grazeCharge = 0.96f;
        EnemyBullet b2 = freeEnemyBullet(w);
        b2.init(390f, 1000f, 0f, 0f);
        w.update(1f / 60f);
        assertEquals(Ship.OVERDRIVE_DURATION, w.ship.overdriveTimer, 1e-4f);
        assertEquals(0f, w.grazeCharge, 1e-4f);
        assertFalse("过载激活应清空敌弹", b.active);
        assertFalse(b2.active);
        assertTrue(w.pollEvents().stream().anyMatch(ev -> ev.type == GameWorld.EventType.OVERDRIVE));
    }

    @Test
    public void overdriveBoostsFirepowerThenExpires() {
        GameWorld w = new GameWorld(5L, true);
        w.handleUiAction(Ui.Action.START);
        w.spawningEnabled = false;
        w.update(1f / 60f);
        clearEnemies(w);
        clearBullets(w);
        clearEnemyBullets(w);
        w.ship.reset(450f, 1400f);
        w.ship.powerLevel = 1;
        w.ship.overdriveTimer = Ship.OVERDRIVE_DURATION;
        w.ship.resetCooldown();
        w.update(1f / 60f);
        assertEquals("过载期间火力临时 +1（双发）", 2, activeBullets(w));

        w.ship.overdriveTimer = 0.05f;
        for (int i = 0; i < 6; i++) w.update(1f / 60f);
        assertEquals(0f, w.ship.overdriveTimer, 1e-4f);

        clearBullets(w);
        w.ship.resetCooldown();
        w.update(1f / 60f);
        assertEquals("过载结束后回到单发", 1, activeBullets(w));
    }

    // ---- 结算统计 ----

    @Test
    public void runStatsTrackAccuracyComboDamageAndReset() {
        GameWorld w = new GameWorld(5L, true);
        w.handleUiAction(Ui.Action.START);
        w.spawningEnabled = false;
        w.update(1f / 60f);
        clearEnemies(w);
        clearBullets(w);
        clearEnemyBullets(w);
        w.ship.reset(100f, 1400f);
        w.ship.invulnerable = false;
        w.ship.hasShield = false;
        w.bulletsFired = 0;
        w.shotsHit = 0;
        w.kills = 0;
        w.maxCombo = 0;
        w.hitsTaken = 0;

        Bullet shot = w.bullets[0];
        shot.init(450f, 1000f, 0f, -1000f, 1, false);
        w.bulletsFired = 1;
        Enemy target = firstFreeEnemy(w);
        target.init(EnemyType.DRONE, 450f, 900f, 1f);
        for (int i = 0; i < 10; i++) w.update(1f / 60f);
        assertFalse(target.active);
        assertEquals(1, w.shotsHit);
        assertEquals(1, w.kills);
        assertEquals(1, w.maxCombo);
        assertTrue("命中率应大于 0", w.accuracyPct() > 0);
        assertTrue("命中率不会超过 100%", w.accuracyPct() <= 100);

        Enemy e2 = firstFreeEnemy(w);
        e2.init(EnemyType.DRONE, 450f, 700f, 1f);
        w.killEnemyForTest(e2);
        assertEquals(2, w.maxCombo);

        Enemy ram = firstFreeEnemy(w);
        ram.init(EnemyType.DRONE, w.ship.x, w.ship.y, 1f);
        w.update(1f / 60f);
        assertEquals(1, w.hitsTaken);
        assertEquals(2, w.maxCombo);

        w.handleUiAction(Ui.Action.RESTART);
        assertEquals(0, w.shotsHit);
        assertEquals(0, w.bulletsFired);
        assertEquals(0, w.maxCombo);
        assertEquals(0, w.hitsTaken);
        assertEquals(0, w.grazes);
        assertEquals(0, w.accuracyPct());
    }

    // ---- 左手模式 ----

    @Test
    public void leftHandBombRectMirrorsToTheLeftCorner() {
        assertEquals(640f, Ui.bomb(false).x, 1e-4f);
        assertEquals(80f, Ui.bomb(true).x, 1e-4f);
        assertEquals(Ui.bomb(false).y, Ui.bomb(true).y, 1e-4f);
        assertEquals(Ui.bomb(false).w, Ui.bomb(true).w, 1e-4f);
    }

    private static Enemy firstFreeEnemy(GameWorld w) {
        for (Enemy e : w.enemies) if (!e.active) return e;
        throw new AssertionError("enemy pool exhausted");
    }

    private static EnemyBullet freeEnemyBullet(GameWorld w) {
        for (EnemyBullet b : w.enemyBullets) if (!b.active) return b;
        throw new AssertionError("enemy bullet pool exhausted");
    }

    private static int activeBullets(GameWorld w) {
        int n = 0;
        for (Bullet b : w.bullets) if (b.active) n++;
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
