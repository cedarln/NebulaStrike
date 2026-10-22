package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.16 迭代 6：终极技能 ULT。
 *
 * <p>整条设计的底线是<b>不许白攒</b>：击坠 / 擦弹 / 点弹 / 弹反 / 吞弹 / 拆部件 / 杀 Boss
 * 都往同一个槽里汇，槽满后继续战斗把溢出折算成分数；三档威力（34 / 67 / 100）让"早点放"
 * 和"憋满放"都是合法选择；硬核坠机只砍一半槽，不是清空。测试就是围绕这四条契约写的。
 */
public class UltimateTest {

    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(20261110L);
        world.spawningEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.lives = 999_999;
        world.ship.x = 450f;
        world.ship.y = 1460f;
        world.ship.prevX = 450f;
        world.ship.prevY = 1460f;
    }

    // ---- 辅助 ----

    private Enemy freeEnemy() {
        for (Enemy e : world.enemies) if (!e.active) return e;
        throw new IllegalStateException("敌机池已满");
    }

    private Enemy enemyAt(EnemyType type, float x, float y) {
        Enemy e = freeEnemy();
        e.init(type, x, y, 1f);
        return e;
    }

    private EnemyBullet enemyBulletAt(float x, float y, float vx, float vy) {
        for (EnemyBullet eb : world.enemyBullets) {
            if (eb.active) continue;
            eb.init(x, y, vx, vy);
            return eb;
        }
        throw new IllegalStateException("敌弹池已满");
    }

    private Bullet freeBullet() {
        for (Bullet b : world.bullets) if (!b.active) return b;
        throw new IllegalStateException("玩家弹池已满");
    }

    /** 击坠 n 架 DRONE 来充能（走真实充能路径，而不是直接改字段）。 */
    private void chargeByKills(int n) {
        for (int i = 0; i < n; i++) world.killEnemyForTest(enemyAt(EnemyType.DRONE, 300f, 400f));
    }

    private void step(int frames) {
        for (int i = 0; i < frames; i++) world.update(1f / 60f);
    }

    private boolean sawEvent(GameWorld.EventType type) {
        for (GameWorld.GameEvent e : world.pollEvents()) if (e.type == type) return true;
        return false;
    }

    private int lastEventCode(GameWorld.EventType type) {
        int found = -1;
        for (GameWorld.GameEvent e : world.pollEvents()) if (e.type == type) found = e.code;
        return found;
    }

    private int countLances() {
        int n = 0;
        for (Bullet b : world.bullets) if (b.active && b.ult) n++;
        return n;
    }

    /** 光矛射线到目标点的垂直距离：小就说明这发矛真的点名了它。 */
    private static float missDistance(Bullet lance, float tx, float ty, float ox, float oy) {
        float dx = lance.vx, dy = lance.vy;
        float d = (float) Math.hypot(dx, dy);
        dx /= d;
        dy /= d;
        float px = tx - ox, py = ty - oy;
        float along = px * dx + py * dy;
        if (along < 0f) return Float.MAX_VALUE;
        return (float) Math.abs(px * dy - py * dx);
    }

    // ---- 纯逻辑：档位与不白攒 ----

    @Test
    public void tiersComeFromTheChargeAtCastTime() {
        Ultimate u = new Ultimate();
        u.add(Ultimate.MIN_CAST - 1f);
        assertEquals(0, u.tier());
        assertFalse(u.ready());
        assertEquals("槽不够时 spend 不该扣槽", 0, u.spend());
        assertEquals(Ultimate.MIN_CAST - 1f, u.charge, 1e-5f);

        u.add(1f);
        assertEquals(1, u.tier());
        u.add(Ultimate.TIER_TWO - Ultimate.MIN_CAST);
        assertEquals(2, u.tier());
        u.add(Ultimate.CAP - Ultimate.TIER_TWO);
        assertEquals(3, u.tier());
        assertTrue(u.full());

        assertEquals(3, u.spend());
        assertEquals(0f, u.charge, 1e-5f);
        assertEquals(1, u.casts);
    }

    @Test
    public void chargePastTheCapIsSpilledNotAbsorbed() {
        Ultimate u = new Ultimate();
        u.charge = Ultimate.CAP;
        assertEquals("满槽后新槽全部算溢出", 5f, u.add(5f), 1e-5f);
        assertEquals("槽不会超过上限", Ultimate.CAP, u.charge, 1e-5f);
    }

    @Test
    public void overflowFromRealCombatBecomesScore() {
        chargeByKills((int) Math.ceil(Ultimate.CAP / GameWorld.ULT_GAIN_KILL));
        world.ult.charge = Ultimate.CAP;
        int scoreBefore = world.score;
        chargeByKills(1);
        assertEquals("满槽还留在满槽", Ultimate.CAP, world.ult.charge, 1e-5f);
        assertTrue("溢出的 3 点槽该折算成 18 分: " + (world.score - scoreBefore),
                world.score - scoreBefore >= GameWorld.ULT_GAIN_KILL * Ultimate.OVERFLOW_SCORE);
        assertTrue(world.ult.overflowSpent >= GameWorld.ULT_GAIN_KILL);
    }

    @Test
    public void readyEventFiresOnceWhenTheMeterFills() {
        world.ult.charge = Ultimate.CAP - GameWorld.ULT_GAIN_KILL;
        chargeByKills(1);
        assertTrue("攒满那一帧要提示玩家", sawEvent(GameWorld.EventType.ULT_READY));
        world.ult.charge = Ultimate.CAP;
        chargeByKills(1);
        assertFalse("满槽期间不该反复提示", sawEvent(GameWorld.EventType.ULT_READY));
    }

    @Test
    public void difficultyChangesOnlyTheChargeRate() {
        float standard = chargeAfterKill(Difficulty.STANDARD);
        float casual = chargeAfterKill(Difficulty.CASUAL);
        float hardcore = chargeAfterKill(Difficulty.HARDCORE);
        assertEquals(GameWorld.ULT_GAIN_KILL, standard, 1e-4f);
        assertEquals("休闲档攒得更快", standard * GameWorld.ULT_CHARGE_CASUAL, casual, 1e-4f);
        assertEquals("硬核档攒得更慢", standard * GameWorld.ULT_CHARGE_HARDCORE, hardcore, 1e-4f);
        assertTrue(casual > standard && standard > hardcore);
    }

    private float chargeAfterKill(Difficulty d) {
        GameWorld w = new GameWorld(777L);
        w.spawningEnabled = false;
        w.settings.setDifficulty(d);
        Enemy e = null;
        for (Enemy c : w.enemies) if (!c.active) { e = c; break; }
        assertNotNull(e);
        e.init(EnemyType.DRONE, 300f, 400f, 1f);
        w.killEnemyForTest(e);
        return w.ult.charge;
    }

    // ---- 释放：事件与演出 ----

    @Test
    public void castRefusesWithoutChargeAndReportsTheTier() {
        assertFalse(world.tryUltimate());
        assertEquals(0, world.ult.casts);
        assertEquals(0f, world.ult.stasisTimer, 0f);

        world.ult.charge = Ultimate.MIN_CAST;
        assertTrue(world.tryUltimate());
        // pollEvents 是一次性消费，两个事件要在同一批里读
        int tier = -1, lances = -1;
        for (GameWorld.GameEvent e : world.pollEvents()) {
            if (e.type == GameWorld.EventType.ULT_CAST) tier = e.code;
            else if (e.type == GameWorld.EventType.ULT_LANCE) lances = e.code;
        }
        assertEquals(1, tier);
        assertEquals(Ultimate.lancesForTier(1), lances);
        assertEquals(0f, world.ult.charge, 1e-5f);
    }

    @Test
    public void fullChargeCastIsTheHeaviest() {
        world.ult.charge = Ultimate.CAP;
        enemyAt(EnemyType.TANK, 200f, 600f);
        assertTrue(world.tryUltimate());
        assertEquals(3, lastEventCode(GameWorld.EventType.ULT_CAST));
        assertEquals(Ultimate.lancesForTier(3), countLances());
        assertEquals(1f, world.ultFlash, 1e-5f);
        assertTrue("放完要有输出窗口", world.ship.invulnRemaining()
                >= Ultimate.invulnForTier(3) - 0.01f);
        for (Bullet b : world.bullets) {
            if (!b.active || !b.ult) continue;
            assertEquals(Ultimate.damageForTier(3), b.damage);
            assertTrue("光矛要穿甲，不能被第一架机吃掉", b.piercing);
            assertEquals(GameWorld.ULT_LANCE_SPEED, (float) Math.hypot(b.vx, b.vy), 1f);
        }
    }

    @Test
    public void stasisSlowsTheFieldThenLapses() {
        world.ult.charge = Ultimate.CAP;
        world.tryUltimate();
        assertEquals(Ultimate.stasisForTier(3), world.ult.stasisTimer, 1e-5f);
        assertEquals("时停期间敌我时间被压到一成",
                Ultimate.STASIS_SCALE, world.ult.timeScale(), 1e-6f);
        int frames = (int) (Ultimate.stasisForTier(3) * 60) + 6;
        step(frames);
        assertEquals("时停该自己收掉", 1f, world.ult.timeScale(), 1e-6f);
        assertEquals(0f, world.ult.stasisTimer, 0f);
    }

    @Test
    public void stasisMovesEnemiesNoticeablySlower() {
        // 直接给时停，不释放光矛，避免把用来计时的敌机顺手打死
        Enemy slow = enemyAt(EnemyType.DRONE, 120f, 300f);
        world.ult.stasisTimer = 0.6f;
        float y0 = slow.y;
        step(24);
        float duringStasis = slow.y - y0;

        for (Enemy e : world.enemies) e.active = false;
        Enemy fast = enemyAt(EnemyType.DRONE, 120f, 300f);
        float y1 = fast.y;
        step(24);
        float normal = fast.y - y1;

        assertTrue("时停里位移要明显更短: " + duringStasis + " vs " + normal,
                duringStasis < normal * 0.5f);
    }

    // ---- 光矛点名 ----

    @Test
    public void lancesNameEveryTargetOnField() {
        float[] xs = {150f, 350f, 560f, 760f};
        for (float x : xs) enemyAt(EnemyType.WEAVER, x, 620f);
        world.ult.charge = Ultimate.CAP;
        world.tryUltimate();
        assertEquals(Ultimate.lancesForTier(3), countLances());
        for (float x : xs) {
            boolean covered = false;
            for (Bullet b : world.bullets) {
                if (!b.active || !b.ult) continue;
                if (missDistance(b, x, 620f, b.x, b.y) < 70f) { covered = true; break; }
            }
            assertTrue("每个目标都该被点到名: x=" + x, covered);
        }
    }

    @Test
    public void lancesPickTheNearestTargetsFirst() {
        Enemy near = enemyAt(EnemyType.DRONE, 450f, 1200f);
        Enemy far = enemyAt(EnemyType.DRONE, 60f, 200f);
        world.ult.charge = Ultimate.CAP;
        world.tryUltimate();
        Bullet first = null;
        for (Bullet b : world.bullets) if (b.active && b.ult) { first = b; break; }
        assertNotNull(first);
        assertTrue("第一发该指向近处那架",
                missDistance(first, near.x, near.y, first.x, first.y)
                        < missDistance(first, far.x, far.y, first.x, first.y));
    }

    @Test
    public void emptyFieldStillFiresUpward() {
        world.ult.charge = Ultimate.CAP;
        assertTrue(world.tryUltimate());
        int n = countLances();
        assertTrue("空场也要有画面: " + n, n > 0);
        for (Bullet b : world.bullets) {
            if (!b.active || !b.ult) continue;
            assertTrue("放空矛要朝天飞", b.vy < 0f);
        }
    }

    @Test
    public void bossPartsCountAsLanceTargets() {
        world.boss.init(2);
        world.boss.x = 450f;
        world.boss.y = 180f;
        assertTrue(world.boss.active);
        world.ult.charge = Ultimate.CAP;
        world.tryUltimate();

        int covered = 0;
        for (int i = 0; i < world.boss.partCount; i++) {
            BossPart p = world.boss.parts[i];
            if (!p.active) continue;
            float px = p.worldX(world.boss), py = p.worldY(world.boss);
            boolean named = false;
            for (Bullet b : world.bullets) {
                if (!b.active || !b.ult) continue;
                if (missDistance(b, px, py, b.x, b.y) < 70f) { named = true; break; }
            }
            if (named) covered++;
        }
        assertTrue("光矛要点名舰体部件而不是全叠核心: " + covered + "/"
                + world.boss.partCount, covered >= 3);
    }

    // ---- 折弹：把对面的火力变成自己的 ----

    @Test
    public void enemyBulletsAreFoldedIntoFriendlyFire() {
        for (int i = 0; i < 8; i++) {
            enemyBulletAt(120f + i * 60f, 900f + (i % 3) * 40f, 0f, 300f);
        }
        int scoreBefore = world.score;
        world.ult.charge = Ultimate.CAP;
        world.tryUltimate();
        assertEquals("满屏敌弹都该被折走", 8, world.ultConverted);
        int converted = 0;
        for (Bullet b : world.bullets) {
            if (!b.active || b.ult) continue;
            if (b.damage != GameWorld.ULT_CONVERT_DAMAGE) continue;
            converted++;
            assertTrue("折回来的弹要朝上打", b.vy < 0f);
        }
        assertEquals(8, converted);
        assertEquals("折弹给小额分数",
                8 * GameWorld.ULT_CONVERT_SCORE, world.score - scoreBefore);
        for (EnemyBullet b : world.enemyBullets) assertFalse(b.active);
    }

    @Test
    public void conversionStopsWhenTheBulletPoolIsFull() {
        for (int i = 0; i < world.bullets.length; i++) {
            Bullet b = freeBullet();
            b.init(100f, 100f, 0f, -400f, 1, false);
            b.ult = false;
        }
        for (int i = 0; i < 6; i++) enemyBulletAt(200f + i * 40f, 900f, 0f, 300f);
        world.ult.charge = Ultimate.CAP;
        assertTrue("弹池满也允许释放（时停与无敌照样给）", world.tryUltimate());
        assertEquals("不该硬塞子弹池", 0, world.ultConverted);
        assertEquals("光矛同理", 0, countLances());
        for (EnemyBullet b : world.enemyBullets) {
            if (b.y == 900f) assertTrue("没空间就保留敌弹原样", b.active);
        }
    }

    @Test
    public void foldedBulletsCanBreakIncomingBullets() {
        for (int i = 0; i < 5; i++) enemyBulletAt(200f + i * 50f, 900f, 0f, 300f);
        world.ult.charge = Ultimate.CAP;
        world.tryUltimate();
        Bullet folded = null;
        for (Bullet b : world.bullets) {
            if (b.active && !b.ult && b.damage == GameWorld.ULT_CONVERT_DAMAGE) {
                folded = b;
                break;
            }
        }
        assertNotNull(folded);
        assertTrue("折回来的弹要够重，能接进点弹幕",
                folded.damage >= GameWorld.BREAK_MIN_DAMAGE);
    }

    // ---- 与死亡、重开、状态机的关系 ----

    @Test
    public void hardcoreDeathHalvesTheMeterInsteadOfWipingIt() {
        GameWorld w = freshWorld(Difficulty.HARDCORE);
        w.ult.charge = 80f;
        enemyBulletAt(w, w.ship.x, w.ship.y, 0f, 0f);
        w.update(1f / 60f);
        assertTrue("要真的撞一次", w.hitsTaken > 0);
        assertEquals("硬核只砍一半，前面不是白打", 40f, w.ult.charge, 1e-4f);
    }

    @Test
    public void easierDifficultiesSurviveTheMeter() {
        for (Difficulty d : new Difficulty[]{Difficulty.STANDARD, Difficulty.CASUAL}) {
            GameWorld w = freshWorld(d);
            w.ult.charge = 80f;
            enemyBulletAt(w, w.ship.x, w.ship.y, 0f, 0f);
            w.update(1f / 60f);
            assertTrue(d + " 该掉一条命", w.hitsTaken > 0);
            assertEquals(d + " 的终极槽原封不动", 80f, w.ult.charge, 1e-4f);
        }
    }

    private GameWorld freshWorld(Difficulty d) {
        GameWorld w = new GameWorld(20261110L);
        w.spawningEnabled = false;
        w.settings.setDifficulty(d);
        w.lives = 999_999;
        return w;
    }

    private EnemyBullet enemyBulletAt(GameWorld w, float x, float y, float vx, float vy) {
        for (EnemyBullet eb : w.enemyBullets) {
            if (eb.active) continue;
            eb.init(x, y, vx, vy);
            return eb;
        }
        throw new IllegalStateException("敌弹池已满");
    }

    @Test
    public void noCastOutsidePlayingState() {
        world.ult.charge = Ultimate.CAP;
        world.state = GameWorld.State.PAUSED;
        assertFalse(world.tryUltimate());
        assertEquals("失败不该扣槽", Ultimate.CAP, world.ult.charge, 1e-5f);
        assertEquals(0, world.ult.casts);
    }

    @Test
    public void chargeDoesNotAccumulateOutsidePlayingState() {
        world.ult.charge = 10f;
        world.state = GameWorld.State.PAUSED;
        Enemy e = enemyAt(EnemyType.DRONE, 300f, 400f);
        world.killEnemyForTest(e);
        assertEquals("暂停时不该偷偷攒槽", 10f, world.ult.charge, 1e-5f);
    }

    @Test
    public void restartClearsTheMeterAndCounters() {
        chargeByKills(12);
        world.tryUltimate();
        assertTrue(world.ult.casts > 0);
        enemyBulletAt(300f, 900f, 0f, 300f);
        world.ult.charge = Ultimate.CAP;
        world.tryUltimate();
        assertTrue(world.ultConverted > 0);
        world.restart(GameMode.WAVES);
        assertEquals(0f, world.ult.charge, 0f);
        assertEquals(0, world.ult.casts);
        assertEquals(0, world.ult.overflowSpent);
        assertEquals(0, world.ultConverted);
        assertEquals(0f, world.ultFlash, 0f);
        assertEquals(1f, world.ult.timeScale(), 1e-6f);
    }

    @Test
    public void sameSeedProducesTheSameUltRun() {
        int[] a = simulateUlt(5150L);
        int[] b = simulateUlt(5150L);
        assertEquals(a[0], b[0]);
        assertEquals(a[1], b[1]);
        assertEquals(a[2], b[2]);
    }

    /** @return {释放次数, 折弹数, 分数} */
    private int[] simulateUlt(long seed) {
        GameWorld w = new GameWorld(seed);
        w.settings.setDifficulty(Difficulty.STANDARD);
        w.restart(GameMode.ENDLESS);
        w.lives = 999_999;
        w.ship.makeInvulnerable();
        for (int i = 0; i < 3600; i++) {
            float a = (float) (i * 0.05);
            w.moveShipTo(450f + (float) Math.cos(a) * 320f,
                    1300f + (float) Math.sin(a * 2f) * 140f);
            if (w.ult.full()) w.tryUltimate();
            w.update(1f / 60f);
            w.pollEvents();
        }
        return new int[]{w.ult.casts, w.ultConverted, w.score};
    }

    // ---- 真实对局里的节奏 ----

    @Test
    public void aRealRunFillsTheMeterAtPlayablyFastSpeed() {
        GameWorld w = new GameWorld(20261111L);
        w.settings.setDifficulty(Difficulty.STANDARD);
        w.restart(GameMode.ENDLESS);
        w.lives = 999_999;
        w.ship.makeInvulnerable();
        int firstReadyFrame = -1;
        int secondReadyFrame = -1;
        int readyEvents = 0;
        for (int i = 0; i < 5400; i++) {
            float a = (float) (i * 0.05);
            w.moveShipTo(450f + (float) Math.cos(a) * 320f,
                    1300f + (float) Math.sin(a * 2f) * 140f);
            if (w.ult.full()) w.tryUltimate();
            w.update(1f / 60f);
            for (GameWorld.GameEvent e : w.pollEvents()) {
                if (e.type != GameWorld.EventType.ULT_READY) continue;
                readyEvents++;
                if (firstReadyFrame < 0) firstReadyFrame = i;
                else if (secondReadyFrame < 0) secondReadyFrame = i;
            }
        }
        assertTrue("一局里至少该攒满一次槽: " + w.ult.charge, readyEvents >= 1);
        assertTrue("整局里点弹/擦弹这些技术活也在给槽: grazes=" + w.grazes
                        + " break=" + w.bulletsShotDown,
                w.grazes + w.bulletsShotDown > 0);
        assertTrue("首次攒满不该太早: " + (firstReadyFrame / 60f) + "s",
                firstReadyFrame > 12 * 60);
        assertTrue("首次攒满不该太晚: " + (firstReadyFrame / 60f) + "s",
                firstReadyFrame < 110 * 60);
        if (secondReadyFrame > 0) {
            assertTrue("第二槽的节奏要比第一槽快（玩家越打越顺）",
                    secondReadyFrame - firstReadyFrame <= firstReadyFrame);
        }
        System.out.println("[ULT BALANCE] casts=" + w.ult.casts
                + " firstReady=" + (firstReadyFrame / 60f) + "s"
                + " secondReady=" + (secondReadyFrame > 0 ? secondReadyFrame / 60f + "s" : "-")
                + " converted=" + w.ultConverted + " overflow=" + w.ult.overflowSpent
                + " kills=" + w.kills + " grazes=" + w.grazes
                + " break=" + w.bulletsShotDown + " parries=" + w.parries
                + " absorbed=" + w.gravityAbsorbed + " score=" + w.score);
    }

    /**
     * 一个真实对局的充能账要能对上：槽量 = 击坠 + 精英 + 擦弹 + 点弹 +（弹反 + 吞弹）×各自系数。
     * 这条断言同时证明两件事——充能源不止一条路，而且每条路都真的接进了同一个槽。
     */
    @Test
    public void everyCombatActionFeedsTheSameMeter() {
        GameWorld w = new GameWorld(9001L);
        w.settings.setDifficulty(Difficulty.STANDARD);
        w.restart(GameMode.ENDLESS);
        w.lives = 999_999;
        w.ship.makeInvulnerable();
        // 只跑 15 秒：不到攒满的时间，槽量没被溢出截断，账才好核对
        for (int i = 0; i < 900; i++) {
            float a = (float) (i * 0.05);
            w.moveShipTo(450f + (float) Math.cos(a) * 320f,
                    1300f + (float) Math.sin(a * 2f) * 140f);
            w.update(1f / 60f);
            w.pollEvents();
        }
        assertFalse("这段不该攒满，否则账会被截断", w.ult.full());
        float fromKills = (w.kills - w.elitesKilled) * GameWorld.ULT_GAIN_KILL
                + w.elitesKilled * GameWorld.ULT_GAIN_ELITE;
        float fromGrazes = w.grazes * GameWorld.ULT_GAIN_GRAZE;
        float fromBreak = w.bulletsShotDown * GameWorld.ULT_GAIN_BREAK;
        float fromOthers = w.parries * GameWorld.ULT_GAIN_PARRY
                + w.gravityAbsorbed * GameWorld.ULT_GAIN_ABSORB;
        float expected = fromKills + fromGrazes + fromBreak + fromOthers;
        assertEquals("所有战斗行为都要汇进同一个槽", expected, w.ult.charge, 0.5f);
        assertTrue("击坠是主力: " + fromKills, fromKills > 0);
        System.out.println("[ULT CHARGE] " + w.timeAlive + "s kills=" + w.kills
                + " elites=" + w.elitesKilled + " grazes=" + w.grazes
                + " break=" + w.bulletsShotDown + " parries=" + w.parries
                + " absorbed=" + w.gravityAbsorbed + " charge=" + w.ult.charge);
    }

    /**
     * 擦弹与吞弹这两条"技术活"路径也要接进同一个槽。它们不像击坠那样在短对局里必然发生，
     * 所以用注入的确定性场景各测一次。
     */
    @Test
    public void grazeAndAbsorbAlsoChargeTheMeter() {
        world.ship.reset(450f, 1000f);
        for (Enemy e : world.enemies) e.active = false;
        for (EnemyBullet eb : world.enemyBullets) eb.active = false;
        world.ult.charge = 0f;
        // 距飞船 60：落在命中判定(44)与擦弹判定(70)之间
        EnemyBullet graze = enemyBulletAt(510f, 1000f, 0f, 0f);
        world.update(1f / 60f);
        assertEquals(1, world.grazes);
        assertEquals("擦弹给一档小额充能",
                GameWorld.ULT_GAIN_GRAZE, world.ult.charge, 1e-4f);
        graze.active = false;

        world.spawnPowerUp(PowerUp.Kind.GRAVITY, world.ship.x, world.ship.y);
        world.update(1f / 60f);
        GravityWell well = null;
        for (GravityWell g : world.gravityWells) if (g.active) { well = g; break; }
        assertNotNull("重力井该上场", well);
        world.ult.charge = 0f;
        enemyBulletAt(well.x, well.y - 250f, 0f, 260f);
        for (int i = 0; i < 240 && world.gravityAbsorbed == 0; i++) world.update(1f / 60f);
        assertEquals(1, world.gravityAbsorbed);
        assertEquals("吞弹也充能", GameWorld.ULT_GAIN_ABSORB, world.ult.charge, 1e-4f);
    }

    /** 站着不动只靠基础炮也能攒到第一档，只是在弹幕里穿行来得更快——擦弹/点弹是加速阀。 */
    @Test
    public void passivePlayStillChargesJustSlower() {
        int parked = framesUntilReady(9002L, false);
        int roaming = framesUntilReady(9002L, true);
        assertTrue("站桩也该攒得出第一档: " + parked, parked > 0);
        assertTrue("在弹幕里穿行更早够放: " + roaming + " vs " + parked, roaming < parked);
    }

    /** @param roam 是否让船在屏幕里穿行（擦弹与点弹都会更多） */
    private int framesUntilReady(long seed, boolean roam) {
        GameWorld w = new GameWorld(seed);
        w.settings.setDifficulty(Difficulty.STANDARD);
        w.restart(GameMode.ENDLESS);
        w.lives = 999_999;
        w.ship.makeInvulnerable();
        for (int i = 0; i < 7200; i++) {
            if (roam) {
                float a = (float) (i * 0.05);
                w.moveShipTo(450f + (float) Math.cos(a) * 320f,
                        1300f + (float) Math.sin(a * 2f) * 140f);
            } else {
                w.moveShipTo(450f, 1460f);
            }
            w.update(1f / 60f);
            w.pollEvents();
            if (w.ult.ready()) return i;
        }
        return -1;
    }
}
