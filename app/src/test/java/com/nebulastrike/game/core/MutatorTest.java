package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.14 迭代 4：随机异变波 MUTATOR。
 * 对局中段起，每隔一段时间随机开启一段十几秒的全局异变：某一条规则被推向极端，
 * 同时窗口内所有得分加权。这里逐项验证倍率确实落在对应钩子上，以及排程 / 收尾奖励 /
 * 重开清场的行为，最后跑一段真实对局确认窗口真的会出现、数值没有失控。
 */
public class MutatorTest {

    private static final float DT = 1f / 60f;

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

    /** 把排程推到眼前：下一帧就该掷异变了。 */
    private void dueForMutator() {
        world.spawningEnabled = true;
        world.mutatorNextAt = 0f;
    }

    private Enemy putEnemy(EnemyType type, float x, float y) {
        Enemy e = world.enemies[0];
        e.init(type, x, y, 1f);
        return e;
    }

    private boolean sawEvent(GameWorld.EventType type) {
        for (GameWorld.GameEvent e : world.pollEvents()) {
            if (e.type == type) return true;
        }
        return false;
    }

    /** 跑到敌机开火为止，返回用掉的帧数（-1 = 400 帧内没开火）。 */
    private int framesUntilTankFires(GameWorld w, Mutator m) {
        if (m.isActive()) w.forceMutator(m);
        Enemy e = w.enemies[0];
        e.init(EnemyType.TANK, 450f, 400f, 1f);
        e.fireCd = 1.0f;   // init 会随机冷却，这里钉死成 1 秒好比较
        for (int frame = 1; frame <= 400; frame++) {
            w.update(DT);
            int active = 0;
            for (EnemyBullet b : w.enemyBullets) if (b.active) active++;
            if (active > 0) return frame;
            if (!e.active) return -1;
        }
        return -1;
    }

    private GameWorld freshWorld(long seed) {
        GameWorld w = new GameWorld(seed);
        w.settings.setDifficulty(Difficulty.STANDARD);
        w.spawningEnabled = false;
        w.ship.x = 450f;
        w.ship.y = 900f;
        w.ship.prevX = 450f;
        w.ship.prevY = 900f;
        return w;
    }

    // ---- 排程 ----

    @Test
    public void mutatorStartsWhenScheduled() {
        dueForMutator();
        world.update(DT);
        assertTrue("到点就该开启异变窗口", world.mutator.isActive());
        assertEquals(Mutator.COUNT, Mutator.values().length - 1);
        assertTrue("投骰不能投出 NONE", world.mutator != Mutator.NONE);
        assertEquals(1, world.mutatorsSeen);
        assertTrue(sawEvent(GameWorld.EventType.MUTATOR_START));
        assertEquals(GameWorld.MUTATOR_DURATION, world.mutatorTimer, 0.01f);
    }

    @Test
    public void everyMutatorIsReachable() {
        // 换种子多掷几次：每一种异变都必须能被排出来，否则等于白做
        boolean[] hit = new boolean[Mutator.values().length];
        for (int seed = 0; seed < 60; seed++) {
            GameWorld w = new GameWorld(1000L + seed);
            w.spawningEnabled = false;
            w.settings.setDifficulty(Difficulty.STANDARD);
            w.spawningEnabled = true;
            w.mutatorNextAt = 0f;
            w.update(DT);
            hit[w.mutator.ordinal()] = true;
        }
        for (Mutator m : Mutator.values()) {
            if (m == Mutator.NONE) continue;   // NONE 只用来表示"窗口外"，不会被投出
            assertTrue("异变从未被投出过：" + m, hit[m.ordinal()]);
        }
    }

    @Test
    public void bossFightNeverStacksIronRain() {
        // Boss 自带弹幕；再叠一层"开火频率 ×1.9"就没人看得清屏幕了，投骰要避开它
        dueForMutator();
        world.bossActive = true;
        for (int seed = 0; seed < 40; seed++) {
            GameWorld w = new GameWorld(500L + seed);
            w.settings.setDifficulty(Difficulty.STANDARD);
            w.spawningEnabled = true;
            w.bossActive = true;
            w.mutatorNextAt = 0f;
            w.update(DT);
            assertTrue("Boss 战中也该有异变窗口", w.mutator.isActive());
            assertTrue("Boss 战里不能掷出铁雨", w.mutator != Mutator.IRON_RAIN);
        }
    }

    @Test
    public void mutatorsCanBeSwitchedOff() {
        dueForMutator();
        world.mutatorsEnabled = false;
        for (int i = 0; i < 120; i++) world.update(DT);
        assertFalse(world.mutator.isActive());
    }

    @Test
    public void windowEndsAndPaysAClearBonus() {
        world.forceMutator(Mutator.IRON_RAIN);
        int frames = (int) (GameWorld.MUTATOR_DURATION * 60) + 10;
        for (int i = 0; i < frames; i++) world.update(DT);
        assertEquals("窗口必须自己关掉", Mutator.NONE, world.mutator);
        assertTrue("异变平息应当有奖励", world.mutatorBonus >= GameWorld.MUTATOR_CLEAR_BONUS);
        assertTrue(sawEvent(GameWorld.EventType.MUTATOR_CLEAR));
        assertEquals(world.timeAlive + GameWorld.MUTATOR_GAP, world.mutatorNextAt, 0.5f);
    }

    @Test
    public void bannerDecaysAndRestartClearsEverything() {
        world.forceMutator(Mutator.SWARM);
        assertTrue(world.mutatorBanner > 0f);
        for (int i = 0; i < 240; i++) world.update(DT);
        assertEquals(0f, world.mutatorBanner, 0.001f);

        world.forceMutator(Mutator.GRAZE_TIDE);
        world.mutatorsSeen = 3;
        world.mutatorBonus = 999;
        world.restart(GameMode.WAVES);
        assertEquals(Mutator.NONE, world.mutator);
        assertEquals(0f, world.mutatorTimer, 0.001f);
        assertEquals(0, world.mutatorsSeen);
        assertEquals(0, world.mutatorBonus);
        assertEquals(GameWorld.MUTATOR_FIRST_AT, world.mutatorNextAt, 0.001f);
        assertEquals(0f, world.mutatorBanner, 0.001f);
    }

    // ---- 各项倍率确实生效 ----

    @Test
    public void ironRainNearlyDoublesEnemyFireRate() {
        int normalFrames = framesUntilTankFires(freshWorld(7L), Mutator.NONE);
        int rainFrames = framesUntilTankFires(freshWorld(7L), Mutator.IRON_RAIN);
        assertTrue("正常冷却应该在一秒左右开火: " + normalFrames,
                normalFrames > 50 && normalFrames < 70);
        assertTrue("铁雨里开火要明显更快: " + rainFrames,
                rainFrames > 0 && rainFrames < normalFrames * 0.75f);
    }

    @Test
    public void swarmDoublesSpawnRateAndWeakensNewEnemies() {
        world.update(DT);
        float baseInterval = world.spawnInterval;
        Enemy plain = putEnemy(EnemyType.TANK, 450f, 300f);
        assertEquals(EnemyType.TANK.hp, plain.hp, 0.001f);

        GameWorld w = new GameWorld(7L);
        w.settings.setDifficulty(Difficulty.STANDARD);
        w.spawningEnabled = true;
        w.forceMutator(Mutator.SWARM);
        w.update(DT);
        assertTrue("虫群出怪更密", w.spawnInterval < baseInterval * 0.65f);

        // 用同一个世界验证登场血量：新出的敌机血量被削到六成
        GameWorld s = new GameWorld(11L);
        s.settings.setDifficulty(Difficulty.STANDARD);
        s.spawningEnabled = true;
        s.forceMutator(Mutator.SWARM);
        Enemy weakest = null;
        for (int i = 0; i < 600 && weakest == null; i++) {
            s.update(DT);
            for (Enemy e : s.enemies) {
                if (e.active) { weakest = e; break; }
            }
        }
        assertTrue("虫群窗口里必须出新敌机", weakest != null);
        assertTrue("新敌机应当被削弱: " + weakest.hp + "/" + weakest.type.hp,
                weakest.hp <= weakest.type.hp * 0.61f);
    }

    @Test
    public void fortressThickensEnemiesAndDropsMorePickups() {
        GameWorld w = new GameWorld(13L);
        w.settings.setDifficulty(Difficulty.STANDARD);
        w.spawningEnabled = true;
        w.forceMutator(Mutator.FORTRESS);
        Enemy tough = null;
        for (int i = 0; i < 600 && tough == null; i++) {
            w.update(DT);
            for (Enemy e : w.enemies) {
                if (e.active) { tough = e; break; }
            }
        }
        assertTrue("钢铁洪流窗口里必须出新敌机", tough != null);
        assertTrue("新敌机应当更硬: " + tough.hp + "/" + tough.type.hp,
                tough.hp >= tough.type.hp * 2.1f);
        assertEquals(tough.hp, tough.maxHp, 0.001f);

        int baseline = dropsOverKills(Mutator.NONE, 240);
        int arsenal = dropsOverKills(Mutator.ARSENAL, 240);
        assertTrue("掉落必须真的变多: " + baseline + " -> " + arsenal,
                arsenal >= baseline + 10);
    }

    /** 击坠 n 架无人机数掉落：用来对比 dropScale，清掉道具池避免 20 格上限影响计数。 */
    private int dropsOverKills(Mutator m, int killsToMake) {
        GameWorld w = new GameWorld(91L);
        w.settings.setDifficulty(Difficulty.STANDARD);
        w.spawningEnabled = false;
        if (m.isActive()) w.forceMutator(m);
        int dropped = 0;
        for (int i = 0; i < killsToMake; i++) {
            Enemy e = w.enemies[0];
            e.init(EnemyType.DRONE, 300f + (i % 7) * 40f, 400f, 1f);
            w.killEnemyForTest(e);
            dropped += countAndClear(w);
        }
        return dropped;
    }

    private int countAndClear(GameWorld w) {
        int n = 0;
        for (PowerUp p : w.powerups) {
            if (p.active) { p.active = false; n++; }
        }
        return n;
    }

    @Test
    public void turboSpeedsUpNewEnemies() {
        world.update(DT);
        float base = world.enemySpeedScale;
        GameWorld w = new GameWorld(17L);
        w.settings.setDifficulty(Difficulty.STANDARD);
        w.spawningEnabled = false;
        w.forceMutator(Mutator.TURBO);
        w.update(DT);
        assertTrue("疾行要真的提速: " + base + " -> " + w.enemySpeedScale,
                w.enemySpeedScale > base * 1.3f);
    }

    @Test
    public void critStormTriplesCritChance() {
        GameWorld base = new GameWorld(23L);
        base.settings.setDifficulty(Difficulty.STANDARD);
        int baseCrits = countCrits(base, 300);

        world.forceMutator(Mutator.CRIT_STORM);
        int stormCrits = countCrits(world, 300);
        assertTrue("暴击率必须真的上去: " + baseCrits + " -> " + stormCrits,
                stormCrits > baseCrits * 2);
    }

    private int countCrits(GameWorld w, int rolls) {
        int crits = 0;
        for (int i = 0; i < rolls; i++) {
            if (w.damageOrCritForTest(3) != 3) crits++;
        }
        return crits;
    }

    @Test
    public void grazeTideTriplesGrazeCharge() {
        // 敌弹从擦弹带内掠过：窗口里一次擦弹顶三次
        EnemyBullet eb = null;
        for (EnemyBullet b : world.enemyBullets) {
            if (!b.active) { b.initRadius(500f, 900f, 0f, -120f, 8f); eb = b; break; }
        }
        assertTrue("需要一颗敌弹", eb != null);
        world.forceMutator(Mutator.GRAZE_TIDE);
        world.update(DT);
        assertTrue("擦弹充能要三倍: " + world.grazeCharge,
                world.grazeCharge >= GameWorld.GRAZE_CHARGE_PER_BULLET * 2.9f);
        assertEquals(1, world.grazes);
    }

    @Test
    public void scoreScaleAppliesToEverythingInTheWindow() {
        int base = world.addScore(100, GameWorld.SCORE_ENEMY);
        assertEquals(100, base);

        GameWorld w = new GameWorld(29L);
        w.settings.setDifficulty(Difficulty.STANDARD);
        w.forceMutator(Mutator.IRON_RAIN);
        assertEquals(160, w.addScore(100, GameWorld.SCORE_ENEMY));

        GameWorld calm = new GameWorld(29L);
        calm.settings.setDifficulty(Difficulty.STANDARD);
        calm.forceMutator(Mutator.NONE);
        assertEquals("NONE 不该加权", 100, calm.addScore(100, GameWorld.SCORE_ENEMY));
    }

    // ---- 与既有系统共存 ----

    @Test
    public void parryScoreIsBoostedInsideTheWindowToo() {
        world.forceMutator(Mutator.CRIT_STORM);   // scoreScale 1.3
        EnemyBullet eb = null;
        for (EnemyBullet b : world.enemyBullets) {
            if (!b.active) { b.init(450f, 850f, 0f, 260f); eb = b; break; }
        }
        world.ship.dashTimer = 0f;
        world.ship.dashCharge = Ship.DASH_MAX_CHARGE;
        world.moveShipTo(450f, 300f);
        assertTrue(world.tryDash());
        world.update(DT);
        assertFalse("冲刺贴身必须弹反", eb.active);
        assertEquals(1, world.parries);
        assertEquals(Math.round(GameWorld.PARRY_SCORE * 1.3f), world.score);
        assertEquals(1, world.maxParryStreak);
    }

    @Test
    public void mutatorNeverSpawnsMoreThanThePoolHolds() {
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.spawningEnabled = true;
        world.lives = 999_999;
        world.forceMutator(Mutator.SWARM);
        for (int i = 0; i < 1800; i++) {
            world.update(DT);
            int active = 0;
            for (Enemy e : world.enemies) if (e.active) active++;
            assertTrue("敌机池溢出: " + active, active <= world.enemies.length);
        }
    }

    @Test
    public void realRunSeesSeveralMutationWindows() {
        GameWorld w = new GameWorld(42L);
        w.spawningEnabled = true;
        w.settings.setDifficulty(Difficulty.STANDARD);
        w.lives = 999_999;
        for (int i = 0; i < 6000; i++) {   // 100 秒真实对局
            w.update(DT);
            w.pollEvents();
        }
        assertTrue("100 秒至少该遇到两个异变窗口: " + w.mutatorsSeen, w.mutatorsSeen >= 2);
        assertTrue("异变加成不该把总分抬飞: " + w.score, w.score < 120_000);
        assertTrue("得分构成里必须记得这部分分数",
                w.scoreBySource[GameWorld.SCORE_OTHER] > 0);
    }
}
