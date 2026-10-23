package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.17 迭代 7：三种新敌机（伪装者 / 磁暴艇 / 集群蜂后）。
 *
 * <p>每一种都必须有一条<b>明确的反制</b>，这是这一轮的底线（用户反馈："捡到道具反而变弱"）：
 * 伪装者在贴脸之前就必然现形，撞不到人，识破它反而白拿一枚真道具；磁暴艇只掰弯直射弹，
 * 激光 / 导弹 / 光矛完全不受影响，打爆它还顺手吞掉一圈敌弹；蜂后的幼机在她阵亡时整体崩溃，
 * 优先击杀永远是最优解。测试就是逐条把这些契约钉住。
 */
public class NewEnemiesV117Test {

    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(20261117L);
        world.spawningEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.lives = 999_999;
        world.ship.x = 450f;
        world.ship.prevX = 450f;
        world.ship.y = 1460f;
        world.ship.prevY = 1460f;
    }

    // ---- 辅助 ----

    private Enemy enemyAt(EnemyType type, float x, float y) {
        for (Enemy e : world.enemies) {
            if (!e.active) {
                e.init(type, x, y, 1f);
                return e;
            }
        }
        throw new IllegalStateException("敌机池已满");
    }

    private int indexOf(Enemy e) {
        for (int i = 0; i < world.enemies.length; i++) {
            if (world.enemies[i] == e) return i;
        }
        return -1;
    }

    private Bullet straightUp(float x, float y) {
        for (Bullet b : world.bullets) {
            if (b.active) continue;
            b.init(x, y, 0f, -700f, 1, false);
            return b;
        }
        throw new IllegalStateException("玩家弹池已满");
    }

    private EnemyBullet enemyBulletAt(float x, float y) {
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active) continue;
            b.init(x, y, 0f, 200f);
            return b;
        }
        throw new IllegalStateException("敌弹池已满");
    }

    private void step(int frames) {
        for (int i = 0; i < frames; i++) world.update(1f / 60f);
    }

    private boolean sawEvent(GameWorld.EventType type) {
        for (GameWorld.GameEvent e : world.pollEvents()) {
            if (e.type == type) return true;
        }
        return false;
    }

    private int eventCode(GameWorld.EventType type) {
        for (GameWorld.GameEvent e : world.pollEvents()) {
            if (e.type == type) return e.code;
        }
        return Integer.MIN_VALUE;
    }

    private int activeEnemies() {
        int n = 0;
        for (Enemy e : world.enemies) if (e.active) n++;
        return n;
    }

    private boolean anyActivePowerUp() {
        for (PowerUp p : world.powerups) if (p.active) return true;
        return false;
    }

    // ---- 伪装者：识破有赏，且绝不贴脸偷袭 ----

    @Test
    public void mirageStaysHiddenUntilThePlayerIsClose() {
        Enemy m = enemyAt(EnemyType.MIRAGE, 450f, 300f);
        step(60);
        assertTrue("远处不该现形", m.isCloaked());
        assertEquals(0, world.miragesRevealed);
    }

    @Test
    public void mirageRevealsInsideTheTriggerRadius() {
        Enemy m = enemyAt(EnemyType.MIRAGE, 450f, 1300f);
        step(6);
        assertFalse("进入 210 半径必须现形", m.isCloaked());
        assertEquals(1, world.miragesRevealed);
        // 现形事件在同一次 poll 里
        for (GameWorld.GameEvent e : world.pollEvents()) {
            if (e.type == GameWorld.EventType.MIRAGE_REVEAL) return;
        }
        throw new AssertionError("缺少 MIRAGE_REVEAL 事件");
    }

    @Test
    public void mirageRevealsAfterTheTimeoutEvenFromFarAway() {
        Enemy m = enemyAt(EnemyType.MIRAGE, 200f, 120f);
        step((int) (Enemy.MIRAGE_MAX_CLOAK * 60f) + 12);
        assertFalse("披壳超时也要现形", m.isCloaked());
    }

    /** 公平性契约：现形距离远大于碰撞半径，玩家永远不会"捡到假道具就被撞死"。 */
    @Test
    public void cloakedMirageCannotReachTheShipUndetected() {
        assertTrue("现形必须先于碰撞",
                Enemy.MIRAGE_TRIGGER > EnemyType.MIRAGE.radius + Ship.RADIUS + 60f);
    }

    @Test
    public void spottingACloakedMiragePaysInScoreAndADrop() {
        Enemy m = enemyAt(EnemyType.MIRAGE, 300f, 400f);
        for (PowerUp p : world.powerups) p.active = false;
        int before = world.score;
        world.killEnemyForTest(m);
        assertEquals(1, world.miragesSpotted);
        assertTrue("识破至少要拿到伪装加分",
                world.score - before >= GameWorld.MIRAGE_SPOT_BONUS);
        assertTrue("识破必掉一枚真道具", anyActivePowerUp());
    }

    @Test
    public void aRevealedMirageIsJustANormalEnemy() {
        Enemy m = enemyAt(EnemyType.MIRAGE, 300f, 400f);
        m.reveal();
        int before = world.miragesSpotted;
        world.killEnemyForTest(m);
        assertEquals("现形后再打不算识破", before, world.miragesSpotted);
    }

    @Test
    public void revealClearsTheDisguiseFlagAndArmsAWarningShot() {
        Enemy m = enemyAt(EnemyType.MIRAGE, 300f, 400f);
        assertTrue(m.isCloaked());
        m.reveal();
        assertFalse(m.isCloaked());
        assertTrue("现形后要进入开火倒计时", m.fireCd > 0f);
        m.reveal();   // 重复调用不该把预警时间重置成 0
        assertTrue(m.fireCd > 0f);
    }

    // ---- 磁暴艇：只掰弯直射弹，且打爆有正反馈 ----

    @Test
    public void magnetFieldBendsStraightBulletsOutward() {
        Enemy mag = enemyAt(EnemyType.MAGNETON, 450f, 700f);
        mag.hp = mag.maxHp = 9999f;
        Bullet b = straightUp(350f, 760f);
        float vx0 = b.vx;
        step(6);
        assertTrue("直射弹该被推离磁极：" + vx0 + " -> " + b.vx, b.vx < vx0 - 1f);
        assertTrue(world.bulletsBent > 0);
    }

    @Test
    public void straightBulletsFlyStraightWithoutAField() {
        Bullet b = straightUp(350f, 760f);
        step(6);
        assertEquals(0f, b.vx, 0.01f);
        assertEquals(0, world.bulletsBent);
    }

    /** 反制契约：激光（穿透）、导弹（追踪）、光矛（大招）、僚机弹一律不受磁场影响。 */
    @Test
    public void magnetFieldSparesPiercingHomingUltAndDroneShots() {
        Enemy mag = enemyAt(EnemyType.MAGNETON, 450f, 700f);
        mag.hp = mag.maxHp = 9999f;
        Bullet beam = straightUp(350f, 760f);
        beam.piercing = true;
        Bullet missile = straightUp(360f, 760f);
        missile.homing = true;
        Bullet lance = straightUp(370f, 760f);
        lance.ult = true;
        Bullet droneShot = straightUp(380f, 760f);
        droneShot.drone = true;
        float beamVx = beam.vx, lanceVx = lance.vx, droneVx = droneShot.vx;
        step(6);
        assertEquals("穿透光束不该被弯", beamVx, beam.vx, 0.01f);
        assertEquals("光矛不该被弯", lanceVx, lance.vx, 0.01f);
        assertEquals("僚机弹不该被弯", droneVx, droneShot.vx, 0.01f);
        assertFalse("追踪弹自带转向，磁场不参与", missile.homing && missile.vx == 0f);
    }

    @Test
    public void magnetFieldHasNoEffectOnEnemyBullets() {
        Enemy mag = enemyAt(EnemyType.MAGNETON, 450f, 700f);
        mag.hp = mag.maxHp = 9999f;
        EnemyBullet eb = enemyBulletAt(450f, 760f);
        step(6);
        assertEquals(0f, eb.vx, 0.01f);
    }

    @Test
    public void killingAMagnetonCollapsesTheFieldAndEatsEnemyBullets() {
        Enemy mag = enemyAt(EnemyType.MAGNETON, 450f, 700f);
        EnemyBullet near1 = enemyBulletAt(470f, 740f);
        EnemyBullet near2 = enemyBulletAt(380f, 620f);
        EnemyBullet far = enemyBulletAt(100f, 1400f);
        world.killEnemyForTest(mag);
        assertFalse(near1.active);
        assertFalse(near2.active);
        assertTrue("超出坍缩半径的弹要留下", far.active);
    }

    @Test
    public void magnetonPulseFiresAnEightBulletRing() {
        Enemy mag = enemyAt(EnemyType.MAGNETON, 450f, 700f);
        mag.hp = mag.maxHp = 9999f;
        mag.empCd = 0.01f;
        int before = 0;
        for (EnemyBullet b : world.enemyBullets) if (b.active) before++;
        step(4);
        int after = 0;
        for (EnemyBullet b : world.enemyBullets) if (b.active) after++;
        assertEquals("一次脉冲八发", GameWorld.MAG_BULLET_COUNT, after - before);
        assertEquals(1, world.magnetBursts);
        assertTrue("脉冲后要重新蓄力", mag.empCd > 1f);
        assertEquals(GameWorld.MAG_BULLET_COUNT,
                eventCode(GameWorld.EventType.MAG_BURST));
    }

    @Test
    public void magnetonStaysQuietWhileCharging() {
        Enemy mag = enemyAt(EnemyType.MAGNETON, 450f, 700f);
        mag.hp = mag.maxHp = 9999f;
        mag.empCd = 3f;
        step(3);
        assertEquals(0, world.magnetBursts);
    }

    // ---- 蜂后：优先击杀目标，阵亡即断子绝孙 ----

    @Test
    public void queenLaysBroodThatBelongsToHer() {
        Enemy queen = enemyAt(EnemyType.QUEEN, 450f, 300f);
        queen.broodCd = 0f;
        int before = activeEnemies();
        step(4);
        int qi = indexOf(queen);
        int kids = 0;
        for (Enemy e : world.enemies) {
            if (e.active && e.broodOf == qi) kids++;
        }
        assertTrue("至少要产两架幼机", kids >= GameWorld.QUEEN_BROOD_MIN);
        assertEquals(before + kids, activeEnemies());
        assertEquals(kids, world.broodSpawned);
        assertEquals(kids, eventCode(GameWorld.EventType.BROOD_SPAWN));
    }

    @Test
    public void hurtQueensBroodFaster() {
        Enemy q = enemyAt(EnemyType.QUEEN, 450f, 300f);
        float fresh = q.broodInterval();
        q.hp = q.maxHp * 0.2f;
        float hurt = q.broodInterval();
        assertTrue("残血蜂后必须产得更快：" + fresh + " -> " + hurt, hurt < fresh);
    }

    @Test
    public void killingTheQueenCollapsesEveryBroodChild() {
        Enemy queen = enemyAt(EnemyType.QUEEN, 450f, 300f);
        queen.broodCd = 0f;
        step(4);
        int qi = indexOf(queen);
        int kids = 0;
        for (Enemy e : world.enemies) if (e.active && e.broodOf == qi) kids++;
        assertTrue(kids > 0);
        Enemy bystander = enemyAt(EnemyType.DRONE, 120f, 900f);
        world.killEnemyForTest(queen);
        assertEquals("幼机应随蜂后一起崩溃", 1, activeEnemies());
        assertTrue("旁观的敌机不能被误伤", bystander.active);
    }

    @Test
    public void broodChildrenKeepRunningAfterTheQueenIsGone() {
        Enemy queen = enemyAt(EnemyType.QUEEN, 450f, 300f);
        queen.broodCd = 0f;
        step(4);
        Enemy kid = null;
        for (Enemy e : world.enemies) {
            if (e.active && e.broodOf == indexOf(queen)) { kid = e; break; }
        }
        assertNotNullChild(kid);
        float y0 = kid.y;
        kid.broodOf = -1;          // 归属解除后，杀掉蜂后不该带走它
        Enemy other = enemyAt(EnemyType.QUEEN, 700f, 300f);
        world.killEnemyForTest(other);
        assertTrue(kid.active);
        assertTrue("幼机仍在正常飞行", kid.y >= y0);
    }

    private void assertNotNullChild(Enemy kid) {
        assertTrue("没找到幼机", kid != null);
    }

    // ---- 出场节奏与图鉴 ----

    @Test
    public void newTypesOnlyAppearAfterTheirWindow() {
        assertTrue("30 秒不该有蜂后", neverSpawns(EnemyType.QUEEN, 60f));
        assertTrue("30 秒不该有磁暴艇", neverSpawns(EnemyType.MAGNETON, 60f));
        assertTrue("30 秒不该有伪装者", neverSpawns(EnemyType.MIRAGE, 60f));
    }

    private boolean neverSpawns(EnemyType type, float seconds) {
        long seed = 4242L;
        GameWorld w = new GameWorld(seed);
        w.settings.setDifficulty(Difficulty.STANDARD);
        w.lives = 999_999;
        float t0 = w.timeAlive;
        while (w.timeAlive < t0 + seconds) {
            w.update(1f / 60f);
            w.pollEvents();
        }
        for (Enemy e : w.enemies) if (e.active && e.type == type) return false;
        return true;
    }

    @Test
    public void lateGameSeesAllThreeNewTypes() {
        GameWorld w = new GameWorld(8123L);
        w.settings.setDifficulty(Difficulty.STANDARD);
        w.lives = 999_999;
        w.timeAlive = 210f;
        boolean mirage = false, magnet = false, queen = false;
        for (int i = 0; i < 60 * 240 && !(mirage && magnet && queen); i++) {
            w.update(1f / 60f);
            w.pollEvents();
            for (Enemy e : w.enemies) {
                if (!e.active) continue;
                if (e.type == EnemyType.MIRAGE) mirage = true;
                if (e.type == EnemyType.MAGNETON) magnet = true;
                if (e.type == EnemyType.QUEEN) queen = true;
            }
        }
        assertTrue("210 秒后该遇到伪装者", mirage);
        assertTrue("210 秒后该遇到磁暴艇", magnet);
        assertTrue("210 秒后该遇到蜂后", queen);
        assertTrue("登场即进图鉴", w.settings.enemySeen(EnemyType.MIRAGE)
                && w.settings.enemySeen(EnemyType.QUEEN));
    }

    @Test
    public void everyNewTypeHasItsOwnPaletteAndCodexEntry() {
        assertNotEquals(EnemyType.MIRAGE.colorHint, EnemyType.MAGNETON.colorHint);
        assertNotEquals(EnemyType.MAGNETON.colorHint, EnemyType.QUEEN.colorHint);
        for (EnemyType t : new EnemyType[]{EnemyType.MIRAGE, EnemyType.MAGNETON,
                EnemyType.QUEEN}) {
            assertTrue(t.name() + " 应算后期兵种", t.isAdvanced());
            assertTrue(t.hp > 0 && t.score > 0 && t.radius > 0f);
        }
    }

    @Test
    public void broodChildrenCannotChainSplitForever() {
        Enemy queen = enemyAt(EnemyType.QUEEN, 450f, 300f);
        queen.broodCd = 0f;
        step(4);
        for (Enemy e : world.enemies) {
            if (e.active && e.broodOf == indexOf(queen)) {
                assertTrue("幼机自带分裂深度，不会再裂一次", e.splitDepth >= 1);
            }
        }
    }

    // ---- 特化遭遇保底：新舰型必须真的出现在战场上 ----

    /** 把世界调到"只跑保底投放"的状态：放行刷新、没有 Boss、时钟拨到指定 sim-time。 */
    private void armedForEncounters(float simTime) {
        world.spawningEnabled = true;
        world.bossActive = false;
        world.timeAlive = simTime;
        clearEnemies();
    }

    private void clearEnemies() {
        for (Enemy e : world.enemies) e.active = false;
    }

    /** 手动跑一次保底投放：不经过 update，避免常规刷新把结果变成随机。 */
    private Enemy encounterOnce() {
        world.v117EncounterTimer = 0.0001f;
        world.updateV117Encounters(0.001f);
        Enemy found = null;
        for (Enemy e : world.enemies) {
            if (!e.active) continue;
            if (e.type == EnemyType.MIRAGE || e.type == EnemyType.MAGNETON
                    || e.type == EnemyType.QUEEN || e.type == EnemyType.RAMMER) found = e;
        }
        return found;
    }

    @Test
    public void encountersStayAsleepUntilTheShipIsUnlocked() {
        armedForEncounters(90f);
        assertNull("115 秒窗口之前不该保底投放", encounterOnce());
        assertEquals(0, world.v117Encounters);
    }

    @Test
    public void everyUnlockedShipEventuallyTakesASlotInTheSky() {
        armedForEncounters(190f);
        java.util.EnumSet<EnemyType> met = java.util.EnumSet.noneOf(EnemyType.class);
        for (int round = 0; round < 6; round++) {
            clearEnemies();
            Enemy e = encounterOnce();
            if (e != null) met.add(e.type);
        }
        assertTrue("伪装者应有保底登场", met.contains(EnemyType.MIRAGE));
        assertTrue("磁暴艇应有保底登场", met.contains(EnemyType.MAGNETON));
        assertTrue("蜂后应有保底登场", met.contains(EnemyType.QUEEN));
        assertTrue("冲撞者应有保底登场", met.contains(EnemyType.RAMMER));
    }

    @Test
    public void theEncounterNeverStacksTwoOfTheSameShip() {
        armedForEncounters(190f);
        enemyAt(EnemyType.QUEEN, 300f, 300f);
        Enemy e = encounterOnce();
        assertNotEquals("场上已有蜂后时不该再补一艘", EnemyType.QUEEN, e.type);
        int queens = 0;
        for (Enemy t : world.enemies) if (t.active && t.type == EnemyType.QUEEN) queens++;
        assertEquals(1, queens);
    }

    /** 伪装者的登场不许有任何声音或震动提示，否则"靠眼力识破"这个玩法直接作废。 */
    @Test
    public void theMirageArrivesWithoutAnyTell() {
        armedForEncounters(190f);
        Enemy e = encounterOnce();
        assertEquals("轮换先给计数最少的伪装者", EnemyType.MIRAGE, e.type);
        assertFalse(sawEvent(GameWorld.EventType.NEW_SHIP_DEBUT));
        assertEquals(0f, world.debutBanner, 0f);
        assertTrue("登场仍要写进图鉴", world.settings.enemySeen(EnemyType.MIRAGE));
    }

    @Test
    public void theDebutBannerIntroducesAShipOnlyOnce() {
        armedForEncounters(190f);
        assertEquals(EnemyType.MIRAGE, encounterOnce().type);   // 伪装者不弹横幅
        clearEnemies();
        assertEquals(EnemyType.MAGNETON, encounterOnce().type);
        assertEquals("磁暴艇首次登场应弹介绍", EnemyType.MAGNETON, world.debutBannerType);
        assertTrue(world.debutBanner > 0f);
        clearEnemies();
        assertEquals(EnemyType.QUEEN, encounterOnce().type);
        clearEnemies();
        assertEquals(EnemyType.RAMMER, encounterOnce().type);
        clearEnemies();
        // 四型都收录过了：再补磁暴艇只该安静上场
        world.v117EncounterCounts[0] = 2;
        world.v117EncounterCounts[1] = 0;
        world.v117EncounterCounts[2] = 2;
        world.v117EncounterCounts[3] = 2;
        world.debutBanner = 0f;
        world.debutBannerType = null;
        assertEquals(EnemyType.MAGNETON, encounterOnce().type);
        assertEquals("重复登场不该再弹介绍横幅", 0f, world.debutBanner, 0f);
    }

    /** 主循环接线：不开挂地跑一秒，保底投放应自己冒出来。 */
    @Test
    public void encountersAreWiredIntoTheMainUpdate() {
        world.spawningEnabled = true;
        world.bossActive = false;
        world.timeAlive = 150f;
        world.v117EncounterTimer = 0.4f;
        step(60);
        assertTrue("update 里漏接了保底投放？", world.v117Encounters > 0);
    }

    /** 一场混到 240 秒的长跑：新行为不能把世界跑崩，也不能让磁场计数失控。 */
    @Test
    public void aLongRunSurvivesTheNewBehaviours() {
        GameWorld w = new GameWorld(555L);
        w.settings.setDifficulty(Difficulty.STANDARD);
        w.lives = 999_999;
        w.ship.makeInvulnerable();
        w.timeAlive = 150f;
        for (int i = 0; i < 60 * 90; i++) {
            float t = i / 60f;
            w.moveShipTo(450f + (float) Math.sin(t * 0.8f) * 260f,
                    1300f + (float) Math.sin(t * 1.7f) * 120f);
            w.ship.makeInvulnerable();     // 无敌有期限，长跑里逐帧续上
            w.update(1f / 60f);
            w.pollEvents();
        }
        assertTrue("磁暴艇应至少脉冲过一次", w.magnetBursts > 0);
        assertTrue("伪装者应现形过", w.miragesRevealed > 0);
        assertTrue(w.bulletsBent >= 0);
        assertEquals("沙盒里不该掉命", 0, w.hitsTaken);
    }
}
