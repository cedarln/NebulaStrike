package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.13 迭代 3：冲刺弹反 PARRY。
 * 冲刺期间无敌，本来"躲掉一颗弹"是零收益、连擦弹都拿不到；弹反把这段位移变成进攻：
 * 贴身敌弹被折返成伤害 {@link GameWorld#PARRY_DAMAGE} 的友军子弹，同一次冲刺里
 * 连续弹反按 streak 倍率计分，并补一点擦弹槽。
 */
public class ParryTest {

    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(20261022L);
        world.spawningEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.ship.x = 450f;
        world.ship.y = 900f;
        world.ship.prevX = 450f;
        world.ship.prevY = 900f;
    }

    // ---- 辅助 ----

    private EnemyBullet enemyBulletAt(float x, float y, float vx, float vy) {
        for (EnemyBullet eb : world.enemyBullets) {
            if (eb.active) continue;
            eb.init(x, y, vx, vy);
            return eb;
        }
        throw new IllegalStateException("敌弹池已满");
    }

    /** 朝 (tx,ty) 发起一次冲刺；冲刺方向就取自这个目标点。 */
    private void dashToward(float tx, float ty) {
        world.ship.dashCharge = Ship.DASH_MAX_CHARGE;
        world.moveShipTo(tx, ty);
        assertTrue("冲刺应当触发成功", world.tryDash());
    }

    private int activePlayerBullets() {
        int n = 0;
        for (Bullet b : world.bullets) if (b.active) n++;
        return n;
    }

    private int eventCode(GameWorld.EventType type) {
        int found = -1;
        for (GameWorld.GameEvent e : world.pollEvents()) {
            if (e.type == type) found = e.code;
        }
        return found;
    }

    private Enemy dummy(float x, float y) {
        Enemy e = world.enemies[0];
        e.init(EnemyType.TANK, x, y, 1f);
        e.hp = 999_999f;
        e.maxHp = 999_999f;
        return e;
    }

    // ---- 弹反触发 ----

    @Test
    public void dashIntoABulletParriesIt() {
        EnemyBullet eb = enemyBulletAt(450f, 850f, 0f, 260f);
        dashToward(450f, 300f);
        world.update(1f / 60f);
        assertFalse("冲刺路径上的敌弹应被弹反", eb.active);
        assertEquals(1, world.parries);
        assertEquals("事件 code 带上本次冲刺的连击序号",
                1, eventCode(GameWorld.EventType.PARRY));
    }

    @Test
    public void theSameBulletHitsTheShipWithoutADash() {
        enemyBulletAt(450f, 900f, 0f, 0f);
        world.update(1f / 60f);
        assertEquals("不冲刺就该正常吃一发", 1, world.hitsTaken);
        assertEquals(0, world.parries);
    }

    @Test
    public void parrySpawnsAFriendlyBulletWithParryDamage() {
        enemyBulletAt(450f, 850f, 0f, 260f);
        int before = activePlayerBullets();
        dashToward(450f, 300f);
        world.update(1f / 60f);
        Bullet parried = null;
        for (Bullet b : world.bullets) {
            if (b.active && b.damage == GameWorld.PARRY_DAMAGE) parried = b;
        }
        assertTrue("应生成一发弹反子弹", parried != null);
        assertTrue("弹反弹必须朝屏幕上方飞，否则立刻出屏没有收益",
                parried.vy < 0f);
        assertTrue(activePlayerBullets() > before);
    }

    @Test
    public void sidewaysBulletsAreDeflectedUpwardsToo() {
        // 横向飞的敌弹若原样折返会立刻飞出侧边界，弹反必须保底朝上
        enemyBulletAt(450f, 855f, 600f, 0f);
        dashToward(450f, 300f);
        world.update(1f / 60f);
        Bullet parried = null;
        for (Bullet b : world.bullets) {
            if (b.active && b.damage == GameWorld.PARRY_DAMAGE) parried = b;
        }
        assertTrue(parried != null);
        assertEquals(-900f, parried.vy, 0.01f);
    }

    @Test
    public void parriedShotIsAcceleratedToNormalBulletSpeed() {
        // 慢速敌弹（120/s）原样折返会变成飘半屏的"气球"，弹速必须拉到保底值
        enemyBulletAt(450f, 850f, 0f, 120f);
        dashToward(450f, 300f);
        world.update(1f / 60f);
        Bullet parried = null;
        for (Bullet b : world.bullets) {
            if (b.active && b.damage == GameWorld.PARRY_DAMAGE) parried = b;
        }
        assertTrue(parried != null);
        float sp = (float) Math.sqrt(parried.vx * parried.vx + parried.vy * parried.vy);
        assertTrue("弹速应拉到保底: " + sp, sp >= GameWorld.PARRY_MIN_SPEED - 0.01f);
    }

    // ---- 收益与代价 ----

    @Test
    public void consecutiveParriesScaleWithStreak() {
        enemyBulletAt(450f, 860f, 0f, 260f);
        enemyBulletAt(450f, 840f, 0f, 260f);
        int before = world.score;
        dashToward(450f, 300f);
        world.update(1f / 60f);
        assertEquals("一次冲刺可以连弹多枚", 2, world.parries);
        int gained = world.score - before;
        // 标准档无倍率：15×1 + 15×2
        assertEquals("连反得分应体现 streak 倍率: " + gained, 45, gained);
    }

    @Test
    public void parryRefillsGrazeCharge() {
        world.grazeCharge = 0f;
        enemyBulletAt(450f, 850f, 0f, 260f);
        dashToward(450f, 300f);
        world.update(1f / 60f);
        assertTrue("弹反补擦弹槽", world.grazeCharge >= GameWorld.PARRY_GRAZE_CHARGE - 0.0001f);
    }

    @Test
    public void parryNeverCostsALife() {
        int livesBefore = world.lives;
        enemyBulletAt(450f, 850f, 0f, 260f);
        dashToward(450f, 300f);
        for (int i = 0; i < 20; i++) world.update(1f / 60f);
        assertEquals(livesBefore, world.lives);
        assertEquals(0, world.hitsTaken);
    }

    @Test
    public void streakResetsBetweenDashes() {
        enemyBulletAt(450f, 850f, 0f, 260f);
        dashToward(450f, 300f);
        world.update(1f / 60f);
        // 等这次冲刺结束、能量回满
        for (int i = 0; i < 30; i++) world.update(1f / 60f);
        world.pollEvents();
        // 第二轮贴着飞船当前位置来，避免飞船这段时间自己飘走
        enemyBulletAt(world.ship.x, world.ship.y - 50f, 0f, 260f);
        int before = world.score;
        dashToward(world.ship.x, world.ship.y - 600f);
        world.update(1f / 60f);
        assertEquals(2, world.parries);
        int gained = world.score - before;
        assertEquals("新一轮冲刺从 streak 1 重新开始",
                GameWorld.PARRY_SCORE, gained);
    }

    // ---- 与其他系统联动 ----

    @Test
    public void parriedBulletDamagesEnemies() {
        // 敌弹斜着飞来，折返后走 (450,850) → 右上 45° 左右的路径，
        // 靶机放在这条路上但避开飞船正上方的机炮流，确保掉血只可能来自弹反弹
        enemyBulletAt(450f, 850f, -600f, 0f);
        Enemy e = dummy(600f, 665f);
        dashToward(450f, 300f);
        for (int i = 0; i < 40 && e.hp >= 999_999f; i++) world.update(1f / 60f);
        assertTrue("弹反弹应该真的能打中敌机: hp=" + e.hp, e.hp < 999_999f);
        assertTrue("单发伤害不超过暴击上限: hp=" + e.hp,
                e.hp >= 999_999f - Math.round(GameWorld.PARRY_DAMAGE * GameWorld.CRIT_MULT));
    }

    @Test
    public void parriedBulletCountsAsAPointDefenseShot() {
        // 弹反弹伤害 3 ≥ BREAK_MIN_DAMAGE，于是能顺势接着点弹
        enemyBulletAt(450f, 850f, 0f, 260f);
        dashToward(450f, 300f);
        world.update(1f / 60f);
        Bullet parried = null;
        for (Bullet b : world.bullets) {
            if (b.active && b.damage == GameWorld.PARRY_DAMAGE) parried = b;
        }
        assertTrue(parried != null);
        // 冲刺还在继续的话这发新敌弹会被再次弹反而不是点掉，先把船移出轨迹
        world.ship.dashTimer = 0f;
        world.ship.x = 450f;
        world.ship.y = 1400f;
        world.ship.prevX = 450f;
        world.ship.prevY = 1400f;
        parried.x = 450f;
        parried.y = 800f;
        EnemyBullet target = enemyBulletAt(450f, 795f, 0f, 0f);
        world.update(1f / 60f);
        assertFalse("弹反弹应能接着点掉敌弹", target.active);
        assertEquals(1, world.bulletsShotDown);
    }

    @Test
    public void parriedShotsCountTowardsAccuracy() {
        // 白送的弹也要计入射出数，否则命中率会被推高到失真
        int firedBefore = world.bulletsFired;
        enemyBulletAt(450f, 850f, 0f, 260f);
        dashToward(450f, 300f);
        world.update(1f / 60f);
        assertTrue(world.bulletsFired > firedBefore);
        assertTrue("命中率不可能超过 100%: " + world.accuracyPct(),
                world.accuracyPct() <= 100);
    }

    // ---- 统计与生命周期 ----

    @Test
    public void eliteKillsAreTrackedSeparately() {
        Enemy e = world.enemies[0];
        e.init(EnemyType.DRONE, 450f, 200f, 1f);
        e.hp = 1f;
        world.killEnemyForTest(e);
        assertEquals(0, world.elitesKilled);
        Enemy elite = world.enemies[1];
        elite.init(EnemyType.TANK, 460f, 200f, 1f);
        elite.makeElite();
        elite.hp = 1f;
        world.killEnemyForTest(elite);
        assertEquals(1, world.elitesKilled);
        assertEquals(2, world.kills);
    }

    @Test
    public void parryFlashDecays() {
        enemyBulletAt(450f, 850f, 0f, 260f);
        dashToward(450f, 300f);
        world.update(1f / 60f);
        assertEquals(1f, world.parryFlash, 0.001f);
        for (int i = 0; i < 30; i++) world.update(1f / 60f);
        assertEquals(0f, world.parryFlash, 0.001f);
    }

    @Test
    public void restartClearsParryStats() {
        enemyBulletAt(450f, 850f, 0f, 260f);
        dashToward(450f, 300f);
        world.update(1f / 60f);
        assertTrue(world.parries > 0);
        world.restart(GameMode.WAVES);
        assertEquals(0, world.parries);
        assertEquals(0f, world.parryFlash, 0.001f);
        assertEquals(0, world.elitesKilled);
    }

    @Test
    public void repeatedDashParriesDoNotLeakPools() {
        for (int i = 0; i < 200; i++) {
            EnemyBullet eb = enemyBulletAt(450f, 850f, 0f, 260f);
            world.ship.dashTimer = 0f;   // 上一轮冲刺可能还没走完
            // 这组只验证弹反与池子回收：别让擦弹槽在中途触发过载清屏，否则某一轮
            // 的测试弹会先被 OVERDRIVE 收掉，parry 数就会随机少一
            world.grazeCharge = 0f;
            dashToward(450f, 300f);
            world.update(1f / 60f);
            assertFalse("第 " + i + " 轮冲刺没有弹到敌弹", eb.active);
            world.pollEvents();
            // 把船拉回来，保证每轮都在同一段轨迹上弹反
            world.ship.x = 450f;
            world.ship.y = 900f;
            world.ship.prevX = 450f;
            world.ship.prevY = 900f;
        }
        assertEquals(200, world.parries);
        int activeEnemyBullets = 0;
        for (EnemyBullet eb : world.enemyBullets) if (eb.active) activeEnemyBullets++;
        // 分数堆到几千分会触发 Boss 出场（它自己会放弹），所以这里只要求池子没被占满：
        // 真正要守的是"弹反不会把池子吃掉"——每轮都还能取到空槽，否则辅助函数会直接抛异常
        assertTrue("敌弹池该留有余量: " + activeEnemyBullets + "/"
                + world.enemyBullets.length,
                activeEnemyBullets < world.enemyBullets.length / 2);
        assertTrue("玩家弹池该留有余量: " + activePlayerBullets() + "/"
                + world.bullets.length,
                activePlayerBullets() < world.bullets.length / 2);
    }

    @Test
    public void realRunSeesParriesWhenThePilotDashes() {
        // 60 秒真实对局 + 每 2 秒朝弹幕方向冲刺一次：弹反必须真的会发生
        GameWorld w = new GameWorld(42L);
        w.spawningEnabled = true;
        w.lives = 999_999;
        w.ship.makeInvulnerable();
        for (int i = 0; i < 60 * 60; i++) {
            float a = i * 0.05f;
            w.moveShipTo(450f + (float) Math.cos(a) * 300f,
                    1100f + (float) Math.sin(a * 2f) * 180f);
            if (i % 120 == 0) w.tryDash();
            w.update(1f / 60f);
            w.pollEvents();
        }
        assertTrue("真实对局里冲刺应当弹到敌弹: " + w.parries, w.parries >= 3);
        assertTrue("弹反不该把分数刷爆: " + w.score, w.score < 60_000);
    }
}
