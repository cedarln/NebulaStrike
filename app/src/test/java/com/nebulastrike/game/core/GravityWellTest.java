package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.15 迭代 5：重力井道具 GRAVITY。
 * 设计意图是"把对方的火力变成自己的火力"：井口范围内的敌弹被拉扯、被吞掉
 * （吞掉的弹当帧即失活，不可能再打到船），吸满或到期时向上扇形喷还成玩家子弹，
 * 伤害 {@link GameWorld#GRAVITY_BURST_DAMAGE} 越过点弹幕门槛，于是喷出去的一波
 * 还能继续引爆敌弹。
 */
public class GravityWellTest {

    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(20261024L);
        world.spawningEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.ship.x = 450f;
        world.ship.y = 1460f;
        world.ship.prevX = 450f;
        world.ship.prevY = 1460f;
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

    /** 在船位刷一个 GRAVITY 道具并推进一帧：磁吸立刻生效，井摆在船上方。 */
    private void collectGravity() {
        world.spawnPowerUp(PowerUp.Kind.GRAVITY, world.ship.x, world.ship.y);
        world.update(1f / 60f);
    }

    private GravityWell firstWell() {
        for (GravityWell g : world.gravityWells) if (g.active) return g;
        return null;
    }

    private static float dist(EnemyBullet b, GravityWell g) {
        float dx = b.x - g.x, dy = b.y - g.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** 推进到条件成立；返回实际帧数，始终不成立返回 -1。 */
    private int runUntil(int maxFrames, Condition cond) {
        for (int i = 1; i <= maxFrames; i++) {
            world.update(1f / 60f);
            if (cond.holds()) return i;
        }
        return -1;
    }

    private interface Condition {
        boolean holds();
    }

    private boolean sawEvent(GameWorld.EventType type) {
        for (GameWorld.GameEvent e : world.pollEvents()) {
            if (e.type == type) return true;
        }
        return false;
    }

    private int lastEventCode(GameWorld.EventType type) {
        int found = -1;
        for (GameWorld.GameEvent e : world.pollEvents()) {
            if (e.type == type) found = e.code;
        }
        return found;
    }

    /** 灌满一口井：从井口正上方一批下坠的敌弹里吸够 CAP 发。 */
    private void fillToCap(GravityWell g) {
        for (int i = 0; i < GravityWell.CAP; i++) {
            enemyBulletAt(g.x + (i - GravityWell.CAP / 2f) * 3f, g.y - 290f, 0f, 300f);
        }
        assertTrue("十二发敌弹应当在有限帧内被吸满",
                runUntil(600, () -> world.gravityBursts > 0) > 0);
    }

    /** 只认喷还弹：伤害正好是 GRAVITY_BURST_DAMAGE，玩家常驻炮是 1 点伤害。 */
    private int countBurstShots() {
        int n = 0;
        for (Bullet b : world.bullets) {
            if (b.active && b.damage == GameWorld.GRAVITY_BURST_DAMAGE
                    && !b.homing && !b.piercing && !b.drone) n++;
        }
        return n;
    }

    // ---- 部署 ----

    @Test
    public void gravityPickupDeploysAWellAboveTheShip() {
        collectGravity();
        GravityWell g = firstWell();
        assertNotNull("拾取 GRAVITY 应当留下重力井", g);
        assertTrue("井要摆在船上方才截得来来袭弹幕", g.y < world.ship.y);
        assertEquals(1, world.gravityWellsDeployed);
        assertEquals(GravityWell.LIFE, g.life, 0.001f);
        assertEquals(0, g.stored);
    }

    @Test
    public void deployFiresAnEventAndDecayingFlash() {
        collectGravity();
        assertTrue("部署要有事件给音效与特效", sawEvent(GameWorld.EventType.GRAVITY_DEPLOY));
        assertEquals(1f, world.gravityFlash, 0.001f);
        world.update(1f / 60f);
        assertTrue(world.gravityFlash < 1f);
    }

    @Test
    public void thirdPickupIsCashedOutWhenBothSlotsAreBusy() {
        collectGravity();
        collectGravity();
        assertNotNull(firstWell());
        int before = world.score;
        collectGravity();
        int wells = 0;
        for (GravityWell g : world.gravityWells) if (g.active) wells++;
        assertEquals("同屏最多两口井", 2, wells);
        assertEquals("第三口折算成分数，不浪费拾取", 250, world.score - before);
        assertEquals(2, world.gravityWellsDeployed);
    }

    // ---- 吸收 ----

    @Test
    public void wellSwallowsIncomingBulletsBeforeTheyCanHit() {
        collectGravity();
        GravityWell g = firstWell();
        int hitsBefore = world.hitsTaken;
        enemyBulletAt(g.x, g.y - 250f, 0f, 260f);
        assertTrue("来袭敌弹应在若干帧内被吞掉",
                runUntil(240, () -> world.gravityAbsorbed > 0) > 0);
        assertEquals("被吞的弹不可能伤到船", hitsBefore, world.hitsTaken);
        assertEquals(1, g.stored);
        assertTrue(sawEvent(GameWorld.EventType.GRAVITY_ABSORB));
    }

    @Test
    public void pullDragsBulletsTowardTheCore() {
        collectGravity();
        GravityWell g = firstWell();
        // 斜着飞过井左侧的一发：受引力后 vx 被拉向井口、vy 被拉下来
        EnemyBullet b = enemyBulletAt(g.x - 200f, g.y - 100f, 0f, 120f);
        float d0 = dist(b, g);
        for (int i = 0; i < 10; i++) world.update(1f / 60f);
        assertTrue("引力要给它一个朝井口的横向速度: vx=" + b.vx, b.vx > 40f);
        assertTrue("引力要把它往下拽: vy=" + b.vy, b.vy > 120f);
        assertTrue("它应当离井口更近了: " + d0 + " -> " + dist(b, g), dist(b, g) < d0);
    }

    @Test
    public void bulletsOutsideThePullRadiusAreUntouched() {
        collectGravity();
        GravityWell g = firstWell();
        EnemyBullet b = enemyBulletAt(g.x, g.y - GravityWell.PULL_RADIUS - 40f, 0f, 120f);
        world.update(1f / 60f);
        assertEquals(0f, b.vx, 1e-3f);
        assertEquals(120f, b.vy, 1e-3f);
        assertEquals(0, world.gravityAbsorbed);
    }

    @Test
    public void absorbPaysSmallScoreAndRefillsSomeGrazeCharge() {
        collectGravity();
        GravityWell g = firstWell();
        world.grazeCharge = 0f;
        int before = world.score;
        enemyBulletAt(g.x, g.y - 250f, 0f, 260f);
        assertTrue(runUntil(240, () -> world.gravityAbsorbed > 0) > 0);
        assertEquals("吞一颗给小额分数",
                GameWorld.GRAVITY_ABSORB_SCORE, world.score - before);
        assertTrue("吞弹只按擦弹的三成补槽，免得比贴弹走位更划算",
                world.grazeCharge >= GameWorld.GRAZE_CHARGE_PER_BULLET
                        * GameWorld.ABSORB_GRAZE_FACTOR - 1e-5f);
        assertTrue(world.grazeCharge < GameWorld.GRAZE_CHARGE_PER_BULLET);
    }

    @Test
    public void aWholeVolleyCanBeSwallowed() {
        collectGravity();
        GravityWell g = firstWell();
        for (int i = 0; i < 6; i++) {
            enemyBulletAt(g.x + (i - 3) * 6f, g.y - 250f, 0f, 260f);
        }
        assertTrue("六发弹应当被吸干净",
                runUntil(300, () -> world.gravityAbsorbed >= 6) > 0);
        for (EnemyBullet b : world.enemyBullets) assertFalse(b.active);
        assertEquals(6, g.stored);
        assertEquals(6, world.gravityAbsorbed);
    }

    // ---- 喷还 ----

    @Test
    public void fillingToCapBurstsTheWholeStockUpward() {
        collectGravity();
        GravityWell g = firstWell();
        fillToCap(g);
        assertEquals("事件要带上喷出的发数",
                GravityWell.CAP, lastEventCode(GameWorld.EventType.GRAVITY_BURST));
        assertEquals("喷还后库存清零", 0, g.stored);
        assertTrue("井本体还在，可以继续吸下一波", g.active);
        assertTrue("喷还的弹要留在弹池里", countBurstShots() >= GravityWell.CAP - 1);
    }

    @Test
    public void burstShotsAreHeavyEnoughToBreakBullets() {
        collectGravity();
        GravityWell g = firstWell();
        fillToCap(g);
        int seen = 0;
        for (Bullet b : world.bullets) {
            if (!b.active || b.damage != GameWorld.GRAVITY_BURST_DAMAGE) continue;
            float sp = (float) Math.sqrt(b.vx * b.vx + b.vy * b.vy);
            assertEquals("喷还弹速固定，保证不会飘出半天不出屏",
                    GameWorld.GRAVITY_BURST_SPEED, sp, 1f);
            assertTrue("喷还弹要够重才能进点弹幕", b.damage >= GameWorld.BREAK_MIN_DAMAGE);
            assertTrue("整体朝上", b.vy < 0f);
            seen++;
        }
        assertTrue("喷还出去的弹应当数得出来: " + seen, seen >= GravityWell.CAP - 1);
    }

    @Test
    public void burstFansOutInsteadOfStackingOneColumn() {
        collectGravity();
        GravityWell g = firstWell();
        fillToCap(g);
        float minVx = Float.MAX_VALUE, maxVx = -Float.MAX_VALUE;
        for (Bullet b : world.bullets) {
            if (!b.active || b.damage != GameWorld.GRAVITY_BURST_DAMAGE) continue;
            minVx = Math.min(minVx, b.vx);
            maxVx = Math.max(maxVx, b.vx);
        }
        assertTrue("扇形要有左右展开: " + minVx + "~" + maxVx, maxVx - minVx > 900f);
        assertTrue("扇形不该变成横扫: ", maxVx < GameWorld.GRAVITY_BURST_SPEED);
    }

    @Test
    public void burstShotsCanShootDownEnemyBullets() {
        collectGravity();
        GravityWell g = firstWell();
        fillToCap(g);
        Bullet shot = null;
        for (Bullet b : world.bullets) {
            if (b.active && b.damage == GameWorld.GRAVITY_BURST_DAMAGE) { shot = b; break; }
        }
        assertNotNull(shot);
        // 摆成一次正面对撞：验证的是"喷还弹进入点弹幕"这条契约，不赌扇形几何
        shot.x = g.x;
        shot.y = g.y - 240f;
        shot.vx = 0f;
        shot.vy = -GameWorld.GRAVITY_BURST_SPEED;
        EnemyBullet bait = enemyBulletAt(g.x, g.y - 160f, 0f, 0f);
        int before = world.bulletsShotDown;
        int frame = runUntil(12, () -> world.bulletsShotDown > before);
        assertTrue("喷还弹应能接进点弹幕: 帧 " + frame, frame > 0);
        assertFalse("诱饵应当被引爆", bait.active);
    }

    @Test
    public void expirySpitsOutWhateverItHeld() {
        collectGravity();
        GravityWell g = firstWell();
        enemyBulletAt(g.x, g.y - 250f, 0f, 260f);
        assertTrue(runUntil(240, () -> world.gravityAbsorbed > 0) > 0);
        assertEquals(1, g.stored);
        assertTrue("井要在寿命内自然消散", runUntil(1200, () -> !g.active) > 0);
        assertEquals("到期要把库存喷出来，不能悄悄消失", 1, world.gravityBursts);
        assertEquals(0, g.stored);
        assertEquals(1, countBurstShots());
    }

    @Test
    public void emptyWellVanishesWithoutABurst() {
        collectGravity();
        GravityWell g = firstWell();
        runUntil(1200, () -> !g.active);
        assertEquals("没吸到弹就不该有喷还", 0, world.gravityBursts);
        assertFalse(sawEvent(GameWorld.EventType.GRAVITY_BURST));
    }

    // ---- 与其它系统共存 ----

    /**
     * 铁雨那种"敌弹成排压过来"的场景：井要能沿弹道稳定吃弹，把一整批挡在船外。
     * 用注入的连续弹幕而不是自然对局，保证断言可复现。
     */
    @Test
    public void steadyVolleyIsDrainedByTheWell() {
        collectGravity();
        GravityWell g = firstWell();
        int hitsBefore = world.hitsTaken;
        int frame = 0;
        while (frame < 900 && world.gravityAbsorbed < GravityWell.CAP) {
            if (frame % 20 == 0) {
                enemyBulletAt(g.x + ((frame / 20) % 3 - 1) * 40f, g.y - 320f, 0f, 280f);
            }
            world.update(1f / 60f);
            frame++;
        }
        assertTrue("一批发弹要能装满一口井: " + world.gravityAbsorbed,
                world.gravityAbsorbed >= GravityWell.CAP);
        assertTrue("装满就该喷还: " + world.gravityBursts, world.gravityBursts >= 1);
        assertEquals("井守住的通道不该掉血", hitsBefore, world.hitsTaken);
    }

    @Test
    public void mutatorScoreScaleAlsoBoostsAbsorbPayout() {
        collectGravity();
        world.forceMutator(Mutator.IRON_RAIN);   // 得分 ×1.6
        GravityWell g = firstWell();
        int before = world.score;
        enemyBulletAt(g.x, g.y - 250f, 0f, 260f);
        assertTrue(runUntil(240, () -> world.gravityAbsorbed > 0) > 0);
        assertTrue("异变窗口里吞弹也该吃到加成: " + (world.score - before),
                world.score - before >= GameWorld.GRAVITY_ABSORB_SCORE * 1.5f);
    }

    @Test
    public void hardcoreDeathClearsWellsAndSpitsTheirStock() {
        collectGravity();
        GravityWell g = firstWell();
        enemyBulletAt(g.x, g.y - 250f, 0f, 260f);
        assertTrue(runUntil(240, () -> world.gravityAbsorbed > 0) > 0);
        world.settings.setDifficulty(Difficulty.HARDCORE);
        int burstsBefore = world.gravityBursts;
        enemyBulletAt(world.ship.x, world.ship.y, 0f, 0f);   // 撞船
        world.update(1f / 60f);
        assertTrue("坠机要真的结算一次", world.hitsTaken > 0);
        assertFalse("硬核难度坠机要把井一起清掉", g.active);
        assertTrue("清场前先把扣着的弹喷出去", world.gravityBursts > burstsBefore);
    }

    @Test
    public void restartClearsWellsAndCounters() {
        collectGravity();
        GravityWell g = firstWell();
        enemyBulletAt(g.x, g.y - 250f, 0f, 260f);
        assertTrue(runUntil(240, () -> world.gravityAbsorbed > 0) > 0);
        world.restart(GameMode.WAVES);
        for (GravityWell cand : world.gravityWells) assertFalse(cand.active);
        assertEquals(0, world.gravityAbsorbed);
        assertEquals(0, world.gravityBursts);
        assertEquals(0, world.gravityWellsDeployed);
        assertEquals(0f, world.gravityFlash, 0f);
    }

    @Test
    public void realRunKeepsWellsBusyButNotOverpowered() {
        GameWorld w = new GameWorld(20261026L);
        w.settings.setDifficulty(Difficulty.STANDARD);
        w.restart(GameMode.ENDLESS);
        int wellsOnField = 0;
        for (int i = 0; i < 6000; i++) {
            if (i % 240 == 0) w.spawnPowerUp(PowerUp.Kind.GRAVITY, w.ship.x, w.ship.y);
            w.update(1f / 60f);
            for (GravityWell g : w.gravityWells) if (g.active) wellsOnField++;
        }
        assertTrue("一整局里重力井要真的上场", wellsOnField > 600);
        assertTrue("吞弹要有产出: " + w.gravityAbsorbed, w.gravityAbsorbed > 10);
        assertTrue("喷还要有产出: " + w.gravityBursts, w.gravityBursts > 0);
        assertTrue("分数不该被重力井撑爆: " + w.score, w.score < 200_000);
        int live = 0;
        for (GravityWell g : w.gravityWells) if (g.active) live++;
        assertTrue("同屏不该超过两口井", live <= 2);
    }

    @Test
    public void sameSeedProducesTheSameGravityRun() {
        int[] a = simulateWithGravity(9101L);
        int[] b = simulateWithGravity(9101L);
        assertEquals(a[0], b[0]);
        assertEquals(a[1], b[1]);
        assertEquals(a[2], b[2]);
    }

    /** @return {吞弹数, 喷还数, 分数}；重力井不引入额外随机源，必须可复现。 */
    private int[] simulateWithGravity(long seed) {
        GameWorld w = new GameWorld(seed);
        w.settings.setDifficulty(Difficulty.STANDARD);
        w.restart(GameMode.ENDLESS);
        for (int i = 0; i < 3000; i++) {
            if (i % 120 == 0) w.spawnPowerUp(PowerUp.Kind.GRAVITY, w.ship.x, w.ship.y);
            w.update(1f / 60f);
        }
        return new int[]{w.gravityAbsorbed, w.gravityBursts, w.score};
    }
}
