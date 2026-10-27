package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.12 迭代 2：点弹幕（重弹可打爆敌弹）。
 * 规则：只有单发伤害 >= {@link GameWorld#BREAK_MIN_DAMAGE} 的弹参与拦截，僚机弹丸不算；
 * 命中后实弹双双消失，穿透光束打爆后继续飞。被点掉的敌弹不再撞船，
 * 也不再提供擦弹充能——"保命"和"攒过载"必须二选一，否则点弹幕是纯收益。
 * 机炮（伤害 1）刻意排除在外：Boss 的弹幕会把它全部吃光，输出直接归零。
 */
public class BulletBreakTest {

    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(20261022L);
        world.spawningEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
    }

    // ---- 辅助 ----

    /** 在指定位置放一颗静止敌弹（vy=0 保证一帧内不会漂走）。 */
    private EnemyBullet enemyBulletAt(float x, float y) {
        for (EnemyBullet eb : world.enemyBullets) {
            if (eb.active) continue;
            eb.init(x, y, 0f, 0f);
            return eb;
        }
        throw new IllegalStateException("敌弹池已满");
    }

    private Bullet playerBulletAt(float x, float y) {
        for (Bullet b : world.bullets) {
            if (b.active) continue;
            b.init(x, y, 0f, -900f, 3, false);
            return b;
        }
        throw new IllegalStateException("玩家弹池已满");
    }

    private Bullet laserAt(float x, float y) {
        for (Bullet b : world.bullets) {
            if (b.active) continue;
            b.initLaser(x, y, 0f, -900f, 3);
            return b;
        }
        throw new IllegalStateException("玩家弹池已满");
    }

    private boolean hasEvent(GameWorld.EventType type, int code) {
        for (GameWorld.GameEvent e : world.pollEvents()) {
            if (e.type == type && e.code == code) return true;
        }
        return false;
    }

    // ---- 基本规则 ----

    @Test
    public void playerBulletPopsEnemyBullet() {
        EnemyBullet eb = enemyBulletAt(450f, 700f);
        Bullet b = playerBulletAt(450f, 700f);
        world.update(1f / 60f);
        assertFalse("敌弹应被打爆", eb.active);
        assertEquals(1, world.bulletsShotDown);
        assertTrue("应上报 BULLET_BREAK 事件供音效使用",
                hasEvent(GameWorld.EventType.BULLET_BREAK, 0));
    }

    @Test
    public void solidShotIsConsumedByThePop() {
        // 取舍的核心：实弹点弹后本轮不再对舰体造成伤害
        enemyBulletAt(450f, 700f);
        Bullet b = playerBulletAt(450f, 700f);
        world.update(1f / 60f);
        assertFalse("非穿透弹点掉敌弹后自身也应消失", b.active);
    }

    @Test
    public void piercingBeamKeepsFlying() {
        enemyBulletAt(450f, 700f);
        Bullet b = laserAt(450f, 700f);
        world.update(1f / 60f);
        assertTrue("激光点弹后继续飞行", b.active);
        assertTrue(b.hitCooldown > 0f);
        assertEquals(1, world.bulletsShotDown);
    }

    @Test
    public void oneShotPopsAtMostOneBulletPerFrame() {
        EnemyBullet first = enemyBulletAt(450f, 700f);
        EnemyBullet second = enemyBulletAt(451f, 701f);
        playerBulletAt(450f, 700f);
        world.update(1f / 60f);
        assertTrue(first.active ^ second.active);
        assertEquals(1, world.bulletsShotDown);
    }

    @Test
    public void poppedBulletNeverHurtsTheShip() {
        world.ship.x = 450f;
        world.ship.y = 800f;
        world.ship.prevX = 450f;
        world.ship.prevY = 800f;
        int livesBefore = world.lives;
        enemyBulletAt(450f, 800f);
        playerBulletAt(450f, 800f);
        world.update(1f / 60f);
        assertEquals("点掉撞船弹应当保住这条命", livesBefore, world.lives);
        assertEquals(0, world.hitsTaken);
        assertEquals(1, world.bulletsShotDown);
    }

    @Test
    public void unpoppedBulletStillHitsTheShip() {
        world.ship.x = 450f;
        world.ship.y = 800f;
        world.ship.prevX = 450f;
        world.ship.prevY = 800f;
        enemyBulletAt(450f, 800f);
        world.update(1f / 60f);
        assertEquals(1, world.hitsTaken);
    }

    @Test
    public void poppingABulletDeniesGrazeCharge() {
        // 放在擦弹带内但不撞船：没有实弹时应当擦到，有实弹点掉后擦不到。
        // 侧移 40 单位是为了避开机炮枪口——机炮弹本身也会点掉它。
        world.ship.x = 450f;
        world.ship.y = 800f;
        world.ship.prevX = 450f;
        world.ship.prevY = 800f;
        float gx = 490f, gy = 760f;
        enemyBulletAt(gx, gy);
        world.update(1f / 60f);
        assertEquals(1, world.grazes);
        assertTrue(world.grazeCharge > 0f);

        world = new GameWorld(20261022L);
        world.spawningEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.ship.x = 450f;
        world.ship.y = 800f;
        world.ship.prevX = 450f;
        world.ship.prevY = 800f;
        enemyBulletAt(gx, gy);
        Bullet shot = playerBulletAt(gx, gy);
        world.update(1f / 60f);
        assertFalse("实点应当被这发点弹幕消耗掉", shot.active);
        assertEquals("被点掉的敌弹不该再送擦弹充能", 0, world.grazes);
        assertEquals(1, world.bulletsShotDown);
    }

    // ---- 计分 / 统计 ----

    @Test
    public void breakAwardsScoreIntoTheOtherBucket() {
        int before = world.score;
        int otherBefore = world.scoreBySource[GameWorld.SCORE_OTHER];
        enemyBulletAt(450f, 700f);
        playerBulletAt(450f, 700f);
        world.update(1f / 60f);
        assertTrue("点弹幕要计分", world.score > before);
        assertTrue(world.scoreBySource[GameWorld.SCORE_OTHER] > otherBefore);
        assertTrue("分数必须低到不会鼓励只点弹不打机",
                world.score - before <= GameWorld.BULLET_BREAK_SCORE * 5);
    }

    @Test
    public void minePopIsFlaggedForAudio() {
        for (EnemyBullet eb : world.enemyBullets) {
            if (eb.active) continue;
            eb.initMine(450f, 700f);
            playerBulletAt(450f, 700f);
            world.update(1f / 60f);
            assertFalse(eb.active);
            assertTrue("水雷被点爆要上报 code=1",
                    hasEvent(GameWorld.EventType.BULLET_BREAK, 1));
            return;
        }
    }

    @Test
    public void distantBulletsAreLeftAloneByTheCull() {
        EnemyBullet eb = enemyBulletAt(450f, 700f);
        playerBulletAt(450f + 300f, 700f);
        world.update(1f / 60f);
        assertTrue("超出粗筛半径的配对不该结算", eb.active);
        assertEquals(0, world.bulletsShotDown);
    }

    @Test
    public void lightCannonShotsPassStraightThrough() {
        // 机炮/散射这类单发伤害 1 的弹不拦截，否则重弹幕里主输出会被敌弹吃干净
        EnemyBullet eb = enemyBulletAt(450f, 700f);
        for (Bullet b : world.bullets) {
            if (b.active) continue;
            b.init(450f, 700f, 0f, -900f, 1, false);
            break;
        }
        world.update(1f / 60f);
        assertTrue(eb.active);
        assertEquals(0, world.bulletsShotDown);
    }

    @Test
    public void wingmanDronesDoNotBreakBullets() {
        EnemyBullet eb = enemyBulletAt(450f, 700f);
        for (Bullet b : world.bullets) {
            if (b.active) continue;
            b.initDrone(450f, 700f, 0f, -900f);
            b.damage = GameWorld.BREAK_MIN_DAMAGE;   // 就算伤害够，僚机弹也不该拦截
            break;
        }
        world.update(1f / 60f);
        assertTrue(eb.active);
        assertEquals(0, world.bulletsShotDown);
    }

    @Test
    public void breakPulseDecaysAndResetsOnRestart() {
        enemyBulletAt(450f, 700f);
        playerBulletAt(450f, 700f);
        world.update(1f / 60f);
        assertEquals(1f, world.breakPulse, 0.001f);
        for (int i = 0; i < 40; i++) world.update(1f / 60f);
        assertEquals(0f, world.breakPulse, 0.001f);

        world.restart(GameMode.WAVES);
        assertEquals(0, world.bulletsShotDown);
        assertEquals(0f, world.breakPulse, 0.001f);
    }

    @Test
    public void heavyWeaponShotsBreakBulletsDuringARealRun() {
        // 真实对局里装了导弹就该点掉若干敌弹，否则这功能是死代码。
        // 单局波动很大（掉落表每次加新道具都会重排拾取序列，实测 0~13 次），
        // 所以跨几个种子累计：既守住"机制在常规交火里确实会被触发"，
        // 又不会因为某一局恰好没捡到导弹就整条测试报废。
        int totalPops = 0;
        float maxPulse = 0f;
        int runs = 0;
        for (long seed = 40L; seed < 48L && totalPops < 6; seed++) {
            GameWorld w = new GameWorld(seed);
            w.spawningEnabled = true;
            w.lives = 999_999;
            // 这里量的是"常规交火里的点弹频率"：把 Boss 节拍推远，
            // 免得 v1.31 的入场预警/肃清过场把 60 秒里的弹幕量压掉一半
            w.nextBossWave = 99;
            w.ship.makeInvulnerable();
            w.ship.addWeapon(WeaponType.MISSILE);
            for (int i = 0; i < 60 * 60; i++) {
                float a = i * 0.05f;
                w.moveShipTo(450f + (float) Math.cos(a) * 300f,
                        1200f + (float) Math.sin(a * 2f) * 150f);
                if (i % 600 == 0) w.ship.makeInvulnerable();
                w.update(1f / 60f);
                w.pollEvents();
                if (w.breakPulse > maxPulse) maxPulse = w.breakPulse;
            }
            totalPops += w.bulletsShotDown;
            runs++;
            assertTrue("点弹幕不该把分数刷爆: seed=" + seed + " score=" + w.score,
                    w.score < 40_000);
        }
        assertTrue("至少完整跑一局", runs > 0);
        assertTrue("重弹在真实对局里应当点到敌弹: " + totalPops, totalPops >= 3);
        assertTrue("点弹要有视觉脉冲反馈: " + maxPulse, maxPulse > 0f);
        assertTrue("点弹过于频繁会吞掉输出: " + totalPops, totalPops <= 100);
    }

    @Test
    public void breakWorksEveryFrameWithoutLeaking() {
        // 连续 300 帧各点一发：池子必须能自我回收，不能攒下失效对象
        for (int i = 0; i < 300; i++) {
            EnemyBullet eb = enemyBulletAt(450f, 700f);
            playerBulletAt(450f, 700f);
            world.update(1f / 60f);
            assertFalse(eb.active);
        }
        assertEquals(300, world.bulletsShotDown);
        int active = 0;
        for (EnemyBullet eb : world.enemyBullets) if (eb.active) active++;
        assertEquals(0, active);
    }
}
