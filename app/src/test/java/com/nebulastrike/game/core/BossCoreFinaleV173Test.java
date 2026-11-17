package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.73 迭代 63：终章 CORE FINALE · 核心摊开那几秒，一型旗舰一个挣扎法。
 *
 * <p>v1.45 起核心暴露是一个对所有 Boss 都一样的节拍：11.5 秒一次、开 3.4 秒、正中一个 48
 * 半径的双倍圈。从这一版起这几秒归形态管：<b>窗口多长、圈多大、是一片还是两片、这几秒它还在不在打你</b>
 * 全按形态取值——静默系的窗口里它一发都攒不出来，钉位系被按在开核那一刻的坐标上，牵索系把离得近的
 * 弹头往圈里带，镜面系的核心干脆裂成两片、正中留一道缝。
 *
 * <p>三条硬口径：① <b>只改手感不改账本</b>——倍率、每击加分、开核间隔与词缀修正一概不动，
 * 八型的"时长 × 判定面积"压在同一个带宽里，谁都不是白拆的；② 钉位让路给冲撞，
 * 把正在撞过来的船按住是替玩家挡招，那是罚不是奖；③ 命中判定与渲染共用同一组 accessor，
 * 画在哪个圈里就只能打在哪个圈里，两片就是两片，不会画一片算三片。
 */
public class BossCoreFinaleV173Test {

    /** 终章收益带宽：时长 × 判定面积（{@link Boss.CoreFinale#weight()}）落在这条区间里。 */
    private static final float WEIGHT_MIN = 5200f;
    private static final float WEIGHT_MAX = 6800f;
    /** 招牌与常规最迟几拍出一次（一阶段每 4 拍），留一倍余量。 */
    private static final int BEAT_LIMIT = 12;
    /** 八型全跑一遍时用来暂存"这一型是哪几拍在动"的下标。 */
    private static final Boss.Form[] ALL_FORMS = Boss.Form.values();

    private GameWorld world;
    private final List<GameWorld.GameEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261128L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.bountyEnabled = false;
        world.perksEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.restart(GameMode.WAVES);
        events.clear();
        world.pollEvents();
    }

    // ---- 辅助：入场、按形态换船、走正常节拍开核 ----

    private Boss triggerBoss(int level) {
        if (world.boss.active) {
            world.boss.hp = 0f;
            step(2);
        }
        world.bossGapTimer = 0f;
        world.stageClearTimer = 0f;
        world.bossesDefeated = level;
        world.wave = world.nextBossWave;
        step(1);
        assertTrue("旗舰该入场", world.boss.active);
        world.applyBossAffix(BossAffix.NONE);
        world.bossWarningTimer = 0f;
        world.bossPhaseBreakTimer = 0f;
        world.ship.makeInvulnerable(9999f);
        parkBoss();
        world.pollEvents();
        return world.boss;
    }

    /** 把指定那一型请进场：常规七型按形态循环走舰级，星核是最终旗舰那一艘。 */
    private Boss use(Boss.Form f) {
        Boss b = triggerBoss(f == Boss.Form.SENTINEL
                ? Story.SECTOR_COUNT - 1 : f.ordinal());
        assertEquals("进场的那艘应该是" + f.label, f, b.form);
        return b;
    }

    private void parkBoss() {
        world.boss.y = world.boss.stationY;
        world.boss.x = GameWorld.WIDTH / 2f;
    }

    private void step(int frames) {
        for (int i = 0; i < frames; i++) {
            world.ship.weaponTimer = 999f;
            world.update(1f / 60f);
            world.pollEvents();
        }
    }

    /** 同上，但把事件收进 {@link #events}——开核那一拍要读事件里的秒数。 */
    private void stepTaken(int frames) {
        for (int i = 0; i < frames; i++) {
            world.ship.weaponTimer = 999f;
            world.update(1f / 60f);
            events.addAll(world.pollEvents());
        }
    }

    /**
     * 走正常节拍把核心打开，而不是手改计时器：钉位坐标、提示行余辉、事件里的秒数
     * 都是开核那一帧记下来的，手改就测不到这一截。
     */
    private void openCore() {
        world.bossWarningTimer = 0f;
        world.bossPhaseBreakTimer = 0f;
        world.bossCoreTimer = 0f;
        world.bossCoreCooldown = 0.02f;
        events.clear();
        for (int i = 0; i < 20 && !world.bossCoreOpen(); i++) stepTaken(1);
        assertTrue("按节拍该开核", world.bossCoreOpen());
    }

    /** 把地上的道具先收走：一笔 +300 的折算奖励混进来，核心那一击的分就量不准了。 */
    private void clearPickups() {
        for (PowerUp pu : world.powerups) pu.active = false;
    }

    private void disableParts() {
        for (int i = 0; i < world.boss.partCount; i++) world.boss.parts[i].active = false;
    }

    /** 清场后放一发玩家弹：速度给 0 就是"钉在这一点上挨一次判定"。 */
    private Bullet shot(float x, float y, float vx, float vy, int damage) {
        for (Bullet b : world.bullets) b.active = false;
        for (Bullet b : world.bullets) {
            if (!b.active) {
                b.init(x, y, vx, vy, damage, false);
                return b;
            }
        }
        throw new IllegalStateException("弹池该有空位");
    }

    private int liveEnemyBullets() {
        int n = 0;
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active) n++;
        }
        return n;
    }

    /**
     * 连续拍出若干拍，返回每一拍各自多出的敌弹数。
     * 拍与拍之间不过帧，所以差值就是那一拍本身造出来的。
     */
    private int[] beats(int count) {
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            for (EnemyBullet eb : world.enemyBullets) eb.active = false;
            int before = liveEnemyBullets();
            world.bossAttack();
            out[i] = liveEnemyBullets() - before;
        }
        return out;
    }

    private int countOf(GameWorld.EventType type) {
        int n = 0;
        for (GameWorld.GameEvent e : events) {
            if (e.type == type) n++;
        }
        return n;
    }

    /** 血量固定成"随便打也不会死、也不会换段"，让伤害与钉位的读数只反映终章本身。 */
    private void freezeHull() {
        world.boss.maxHp = 500_000f;
        world.boss.hp = 500_000f;
        world.boss.phase = 1;
    }

    // ---- 数据表：一型一条、各不重样、收益压在一条带宽里 ----

    @Test
    public void everyFormHasItsOwnFinale() {
        assertEquals("终章数据要和形态一一对上", ALL_FORMS.length, Boss.coreFinaleCount());
        for (Boss.Form f : ALL_FORMS) {
            Boss.CoreFinale fin = Boss.coreFinaleFor(f);
            assertTrue(f.label + " 的窗口太短或太长", fin.window >= 2.2f && fin.window <= 4.6f);
            assertTrue(f.label + " 的判定圈不合理", fin.radius >= 26f && fin.radius <= 52f);
            assertTrue(f.label + " 的圈数越界", fin.zones == 1 || fin.zones == 2);
            assertFalse(f.label + " 没写给玩家看的那句话", fin.note.isEmpty());
            assertTrue(f.label + " 的那句话太会长", fin.note.length() <= 26);
        }
    }

    @Test
    public void noTwoFormsShareTheSameFinaleShape() {
        Set<String> shapes = new HashSet<>();
        for (Boss.Form f : ALL_FORMS) {
            Boss.CoreFinale fin = Boss.coreFinaleFor(f);
            String shape = fin.window + "/" + fin.radius + "/" + fin.zones + "/" + fin.mode;
            assertTrue("这两型的终章长得一模一样：" + f.label + " " + shape, shapes.add(shape));
        }
        assertEquals("八型八种", ALL_FORMS.length, shapes.size());
    }

    @Test
    public void thePayoutsStayInOneBand() {
        for (Boss.Form f : ALL_FORMS) {
            Boss.CoreFinale fin = Boss.coreFinaleFor(f);
            float weight = fin.weight();
            assertTrue(f.label + " 的终章太薄：" + weight, weight >= WEIGHT_MIN);
            assertTrue(f.label + " 的终章太厚：" + weight, weight <= WEIGHT_MAX);
        }
    }

    @Test
    public void theThreeStrugglesAreAllUsed() {
        int silent = 0, anchor = 0, pull = 0, twins = 0;
        for (Boss.Form f : ALL_FORMS) {
            Boss.CoreFinale fin = Boss.coreFinaleFor(f);
            if (fin.mode == Boss.CORE_SILENT) silent++;
            else if (fin.mode == Boss.CORE_ANCHOR) anchor++;
            else if (fin.mode == Boss.CORE_PULL) pull++;
            else throw new IllegalStateException(f.label + " 的终章模式没人认识");
            if (fin.zones > 1) twins++;
        }
        assertTrue("静默系不能少于两型", silent >= 2);
        assertTrue("钉位系不能少于两型", anchor >= 2);
        assertTrue("牵索系不能少于两型", pull >= 2);
        assertEquals("裂成两片的是镜面系那两型", 2, twins);
    }

    @Test
    public void theTableLinesUpWithTheFormDeclaration() {
        assertSame(Boss.coreFinaleFor(Boss.Form.SPECTER), Boss.coreFinaleFor(ALL_FORMS[0]));
        assertEquals(Boss.CORE_PULL, Boss.coreFinaleFor(Boss.Form.SPECTER).mode);
        assertEquals(Boss.CORE_SILENT, Boss.coreFinaleFor(Boss.Form.CINDER).mode);
        assertEquals(Boss.CORE_ANCHOR, Boss.coreFinaleFor(Boss.Form.DOCKYARD).mode);
        assertEquals(2, Boss.coreFinaleFor(Boss.Form.DOCKYARD).zones);
        assertEquals(Boss.CORE_ANCHOR, Boss.coreFinaleFor(Boss.Form.HIVE).mode);
        assertEquals(Boss.CORE_PULL, Boss.coreFinaleFor(Boss.Form.RIFT).mode);
        assertEquals(Boss.CORE_SILENT, Boss.coreFinaleFor(Boss.Form.MAGNET).mode);
        assertEquals(2, Boss.coreFinaleFor(Boss.Form.MIRROR).zones);
        assertEquals(Boss.CORE_SILENT, Boss.coreFinaleFor(Boss.Form.SENTINEL).mode);
    }

    @Test
    public void theWorldReadsTheFinaleOffTheShipThatSpawned() {
        for (Boss.Form f : ALL_FORMS) {
            use(f);
            assertSame("进场后读到的应该是" + f.label + "的终章",
                    Boss.coreFinaleFor(f), world.coreFinale());
        }
    }

    // ---- 窗口与判定圈：秒数、半径、片数都归形态管 ----

    @Test
    public void theWindowSecondsComeFromTheForm() {
        for (Boss.Form f : ALL_FORMS) {
            use(f);
            events.clear();
            openCore();
            Boss.CoreFinale fin = Boss.coreFinaleFor(f);
            assertTrue(f.label + " 的窗口没按自己的秒数开",
                    world.bossCoreTimer <= fin.window + 1e-3f
                            && world.bossCoreTimer > fin.window - 0.06f);
            GameWorld.GameEvent open = null;
            for (GameWorld.GameEvent e : events) {
                if (e.type == GameWorld.EventType.BOSS_CORE_OPEN) open = e;
            }
            assertNotNull("开核要有事件", open);
            assertEquals(f.label + " 的事件秒数四舍五入报", Math.round(fin.window), open.code);
        }
    }

    @Test
    public void theHudLineLightsUpWhenTheCoreOpens() {
        use(Boss.Form.HIVE);
        openCore();
        assertTrue("提示行刚开核该亮着", world.coreFinaleFlash > GameWorld.CORE_FINALE_FLASH_TIME * 0.6f);
        step(200);
        assertEquals("提示行的余辉该自己收走", 0f, world.coreFinaleFlash, 1e-3f);
        assertFalse("这一截窗口也该合拢了", world.bossCoreOpen());
    }

    @Test
    public void singleZoneFormsPutTheZoneOnTheShip() {
        for (Boss.Form f : ALL_FORMS) {
            if (Boss.coreFinaleFor(f).zones != 1) continue;
            use(f);
            assertEquals("一片就是舰体正中", world.boss.x, world.coreZoneX(0), 1e-3f);
            assertEquals(1, world.coreZoneCount());
            assertTrue(f.label + " 的正中该算打进核心",
                    world.coreHitAt(world.boss.x, world.boss.y));
            float r = world.coreZoneRadius();
            assertFalse(f.label + " 让开 " + (r + 16) + " 就不该算了",
                    world.coreHitAt(world.boss.x + r + 16f, world.boss.y));
            assertFalse("纵向也一样，出圈就不算",
                    world.coreHitAt(world.boss.x, world.boss.y - r - 16f));
        }
    }

    @Test
    public void mirrorFormsSplitTheCoreAndLeaveASeam() {
        for (Boss.Form f : ALL_FORMS) {
            if (Boss.coreFinaleFor(f).zones != 2) continue;
            use(f);
            assertEquals(2, world.coreZoneCount());
            assertEquals("左片", world.boss.x - Boss.CORE_ZONE_OFFSET, world.coreZoneX(0), 1e-3f);
            assertEquals("右片", world.boss.x + Boss.CORE_ZONE_OFFSET, world.coreZoneX(1), 1e-3f);
            assertTrue(f.label + " 的左片该能吃双倍",
                    world.coreHitAt(world.coreZoneX(0), world.boss.y));
            assertTrue(f.label + " 的右片该能吃双倍",
                    world.coreHitAt(world.coreZoneX(1), world.boss.y));
            assertFalse(f.label + " 的正中是两道片中间的缝，不算核心",
                    world.coreHitAt(world.boss.x, world.boss.y));
        }
    }

    @Test
    public void insideWhateverZoneMeansDoubleDamage() {
        use(Boss.Form.HIVE);       // 钉位系：窗口里船不动，落点读得准
        freezeHull();
        disableParts();
        openCore();
        parkBoss();
        world.hitStopTimer = 0f;
        world.slowTimer = 0f;

        float before = world.boss.hp;
        shot(world.coreZoneX(0), world.coreZoneY(), 0f, 0f, 1);
        world.update(1f / 60f);
        assertEquals("圈内 1 点弹伤变 2 点", 2f, before - world.boss.hp, 1e-2f);

        before = world.boss.hp;
        shot(world.coreZoneX(0) + world.coreZoneRadius() + 16f, world.coreZoneY(), 0f, 0f, 1);
        world.update(1f / 60f);
        assertEquals("圈外贴舰体只吃普通伤害", 1f, before - world.boss.hp, 1e-2f);
    }

    @Test
    public void theSeamOfAMirrorCoreIsNotFreeDamage() {
        use(Boss.Form.DOCKYARD);
        freezeHull();
        disableParts();
        openCore();
        parkBoss();
        world.hitStopTimer = 0f;
        world.slowTimer = 0f;

        float before = world.boss.hp;
        shot(world.boss.x, world.boss.y, 0f, 0f, 1);
        world.update(1f / 60f);
        assertEquals("两片中间的缝按普通伤害结", 1f, before - world.boss.hp, 1e-2f);

        before = world.boss.hp;
        shot(world.coreZoneX(1), world.coreZoneY(), 0f, 0f, 1);
        world.update(1f / 60f);
        assertEquals("挪到片上就是双倍", 2f, before - world.boss.hp, 1e-2f);
    }

    @Test
    public void theMultiplierAndTheScoreAreStillOneNumberForEveryone() {
        for (Boss.Form f : ALL_FORMS) {
            use(f);
            freezeHull();
            disableParts();
            // 对照弹：窗口合拢时先打一发，把"最后一件倒下"那笔过路奖励吸干净
            world.hitStopTimer = 0f;
            world.slowTimer = 0f;
            clearPickups();
            shot(world.coreZoneX(0), world.coreZoneY(), 0f, 0f, 1);
            world.update(1f / 60f);
            openCore();
            parkBoss();
            clearPickups();
            world.hitStopTimer = 0f;
            world.slowTimer = 0f;
            int before = world.score;
            float hp = world.boss.hp;
            shot(world.coreZoneX(0), world.coreZoneY(), 0f, 0f, 1);
            world.update(1f / 60f);
            assertEquals(f.label + " 的核心一击还是 +" + GameWorld.CORE_HIT_SCORE,
                    GameWorld.CORE_HIT_SCORE, world.score - before);
            assertEquals(f.label + " 的倍率没跟着形态改",
                    GameWorld.CORE_DAMAGE_MULT, (long) (hp - world.boss.hp));
        }
    }

    // ---- 静默系：窗口那一拍它真的什么都没有 ----

    @Test
    public void silentFormsFireNothingDuringTheWindow() {
        for (Boss.Form f : ALL_FORMS) {
            if (Boss.coreFinaleFor(f).mode != Boss.CORE_SILENT) continue;
            use(f);
            freezeHull();
            openCore();
            events.clear();
            int[] per = beats(BEAT_LIMIT);
            int total = 0;
            for (int n : per) total += n;
            assertEquals(f.label + " 的终章窗口里不该多出敌弹", 0, total);
            assertEquals("憋着的这几拍不该被记成读招", 0, countOf(GameWorld.EventType.BOSS_SIGNATURE));
        }
    }

    @Test
    public void onlyTheSilentFormsHoldTheirFire() {
        for (Boss.Form f : ALL_FORMS) {
            if (Boss.coreFinaleFor(f).mode == Boss.CORE_SILENT) continue;
            use(f);
            freezeHull();
            openCore();
            int total = 0;
            for (int n : beats(BEAT_LIMIT)) total += Math.max(0, n);
            assertTrue(f.label + " 的终章不该把它的弹幕也一起收走", total > 0);
        }
    }

    @Test
    public void theCadenceComesBackWhenTheCoreShuts() {
        use(Boss.Form.MAGNET);
        freezeHull();
        openCore();
        assertEquals("窗口里憋着", 0, beats(6)[5] < 0 ? -1 : beatsTotal(6));
        world.bossCoreTimer = 0f;
        int total = beatsTotal(BEAT_LIMIT);
        assertTrue("合拢后它该接着打", total > 0);
    }

    private int beatsTotal(int count) {
        int total = 0;
        for (int n : beats(count)) total += Math.max(0, n);
        return total;
    }

    @Test
    public void aSilentBeatDoesNotSpendTheBreachSilence() {
        use(Boss.Form.CINDER);
        freezeHull();
        world.breachSilenceArmed = true;
        openCore();
        beatsTotal(BEAT_LIMIT);
        assertTrue("静默那一拍本来没排到出手，不该替玩家把哑火花掉",
                world.breachSilenceArmed);
        assertEquals(0, world.breachSilences);

        world.bossCoreTimer = 0f;
        int guard = 0;
        while (world.breachSilenceArmed && ++guard < BEAT_LIMIT) beatsTotal(1);
        assertFalse("窗口一合，攒下的那记就该在招牌那一拍兑现", world.breachSilenceArmed);
        assertEquals(1, world.breachSilences);
    }

    // ---- 钉位系：按在开核那一刻的坐标上 ----

    @Test
    public void anchorFormsStopWalkingDuringTheWindow() {
        for (Boss.Form f : ALL_FORMS) {
            if (Boss.coreFinaleFor(f).mode != Boss.CORE_ANCHOR) continue;
            use(f);
            freezeHull();
            openCore();
            float x = world.boss.x, y = world.boss.y;
            step(40);
            assertTrue(f.label + " 的钉位该真的按住横坐标", world.boss.active);
            assertEquals(f.label + " 窗口里横坐标没动", x, world.boss.x, 1e-3f);
            assertEquals(f.label + " 窗口里纵坐标没动", y, world.boss.y, 1e-3f);
        }
    }

    @Test
    public void theAnchorYieldsToARam() {
        use(Boss.Form.HIVE);
        freezeHull();
        openCore();
        float x = world.boss.x;
        world.boss.ramTimer = 1f;
        world.boss.ramTargetX = x + 240f;
        step(20);
        assertTrue("冲撞中的船不该被按住（那是替玩家挡招）", world.boss.x > x + 1f);
    }

    @Test
    public void nonAnchorFormsKeepWalking() {
        for (Boss.Form f : ALL_FORMS) {
            if (Boss.coreFinaleFor(f).mode == Boss.CORE_ANCHOR) continue;
            use(f);
            freezeHull();
            openCore();
            markWalk();
            step(40);
            assertTrue(f.label + " 不是钉位系，它该继续走位：" + walked(), walked() > 0.5f);
        }
    }

    @Test
    public void theAnchorLetsGoWhenTheWindowCloses() {
        use(Boss.Form.HIVE);
        freezeHull();
        openCore();
        float x = world.boss.x, y = world.boss.y;
        step(40);
        assertEquals("窗口里钉着横坐标", x, world.boss.x, 1e-3f);
        assertEquals("窗口里钉着纵坐标", y, world.boss.y, 1e-3f);
        world.bossCoreTimer = 0f;
        markWalk();
        step(60);
        assertTrue("合拢后它就该接着走自己的航道：" + walked(), walked() > 0.5f);
    }

    // ---- 牵索系：把够得着的弹头往圈里带 ----

    /**
     * 连拧 n 帧的牵索读数。走 world.update 整帧会被弹池复用搅浑（这一发的槽位下一秒
     * 可能就是别人的了），所以这里按帧直接叫转向规则——判据仍然只在窗口与形态上。
     */
    private float[] tow(Bullet b, int frames) {
        float tx = world.coreZoneX(0), ty = world.coreZoneY();
        float aim0 = angleError(b, tx, ty);
        float speed0 = (float) Math.hypot(b.vx, b.vy);
        for (int i = 0; i < frames; i++) {
            world.pullBulletToCore(b, 1f / 60f);
        }
        return new float[]{aim0, angleError(b, world.coreZoneX(0), world.coreZoneY()),
                speed0, (float) Math.hypot(b.vx, b.vy)};
    }

    @Test
    public void pullFormsBendShotsTowardTheZone() {
        for (Boss.Form f : ALL_FORMS) {
            if (Boss.coreFinaleFor(f).mode != Boss.CORE_PULL) continue;
            use(f);
            freezeHull();
            openCore();
            Bullet b = shot(world.coreZoneX(0) - 80f, world.coreZoneY() + 100f, -40f, -300f, 1);
            float[] r = tow(b, 8);
            assertTrue(f.label + " 的牵索该把弹头往圈里拧：起手偏 " + r[0] + "，八帧后 " + r[1],
                    r[1] < r[0]);
            assertEquals(f.label + " 只改方向不改速度", r[2], r[3], r[2] * 0.01f);
        }
    }

    @Test
    public void theTowlineIsWiredIntoTheBulletLoop() {
        // 上面绕开了整帧，这一条把"真的接进了 world.update"补上：一帧之内就该开始拐
        use(Boss.Form.SPECTER);
        freezeHull();
        openCore();
        Bullet b = shot(world.coreZoneX(0) - 90f, world.coreZoneY() + 100f, 0f, -300f, 1);
        world.update(1f / 60f);
        if (b.active) assertTrue("整帧里牵索也该动手", b.vx > 0.1f);
    }

    /** 这一截里舰体走了多远：有的形态只起伏不横移，光读横坐标会误判成"没动"。 */
    private float walked() {
        Boss b = world.boss;
        float dx = b.x - walkRefX, dy = b.y - walkRefY;
        return (float) Math.hypot(dx, dy);
    }

    private float walkRefX;
    private float walkRefY;

    private void markWalk() {
        walkRefX = world.boss.x;
        walkRefY = world.boss.y;
    }

    /** 弹头当前方向与"指向圈心"之间差了多少弧度。 */
    private float angleError(Bullet b, float tx, float ty) {
        float now = (float) Math.atan2(b.vy, b.vx);
        float want = (float) Math.atan2(ty - b.y, tx - b.x);
        float d = want - now;
        while (d > Math.PI) d -= Math.PI * 2;
        while (d < -Math.PI) d += Math.PI * 2;
        return Math.abs(d);
    }

    @Test
    public void theTowlineOnlyReachesSoFar() {
        use(Boss.Form.SPECTER);
        freezeHull();
        openCore();
        Bullet b = shot(world.coreZoneX(0) + GameWorld.CORE_PULL_RANGE + 120f,
                world.coreZoneY() + 120f, 0f, -300f, 1);
        float[] r = tow(b, 4);
        assertEquals("够不着的弹不该被拽", r[0], r[1], 1e-4f);
    }

    @Test
    public void theTowlineStopsWithTheWindow() {
        use(Boss.Form.RIFT);
        freezeHull();
        openCore();
        Bullet pulled = shot(world.coreZoneX(0) - 90f, world.coreZoneY() + 110f,
                0f, -300f, 1);
        float before = towBaseline(pulled);
        assertTrue("窗口里该被带一把", tow(pulled, 6)[1] < before);
        world.bossCoreTimer = 0f;
        Bullet idle = shot(world.coreZoneX(0) - 90f, world.coreZoneY() + 110f,
                0f, -300f, 1);
        float idleVx = idle.vx;
        float aim = angleError(idle, world.coreZoneX(0), world.coreZoneY());
        for (int i = 0; i < 6; i++) world.pullBulletToCore(idle, 1f / 60f);
        assertEquals("合拢之后弹道该还给玩家", idleVx, idle.vx, 1e-4f);
        assertEquals("合拢之后弹道该还给玩家", aim,
                angleError(idle, world.coreZoneX(0), world.coreZoneY()), 1e-4f);
    }

    /** 没被拧之前的偏角，用来给 {@link #tow} 的结果做对照。 */
    private float towBaseline(Bullet b) {
        return angleError(b, world.coreZoneX(0), world.coreZoneY());
    }

    @Test
    public void otherFormsDoNotBendYourShots() {
        for (Boss.Form f : ALL_FORMS) {
            if (Boss.coreFinaleFor(f).mode == Boss.CORE_PULL) continue;
            use(f);
            freezeHull();
            openCore();
            Bullet b = shot(world.coreZoneX(0) - 90f, world.coreZoneY() + 110f, 0f, -300f, 1);
            float aim = towBaseline(b);
            float[] r = tow(b, 4);
            assertEquals(f.label + " 不是牵索系，弹道不该被人代劳", aim, r[1], 1e-4f);
        }
    }

    @Test
    public void anAlreadyAimedShotIsLeftAlone() {
        use(Boss.Form.SPECTER);
        freezeHull();
        openCore();
        Bullet b = shot(world.coreZoneX(0), world.coreZoneY() + 130f, 0f, -300f, 1);
        float[] r = tow(b, 6);
        assertEquals("已经瞄直的弹不该被多拧", 0f, b.vx, 1e-6f);
        assertEquals("瞄直了就没有可拧的余量", 0f, r[1], 1e-4f);
    }

    @Test
    public void theTurnRateIsCapped() {
        use(Boss.Form.RIFT);
        freezeHull();
        openCore();
        Bullet b = shot(world.coreZoneX(0) - 120f, world.coreZoneY() + 60f, -200f, -220f, 1);
        float prev = (float) Math.atan2(b.vy, b.vx);
        for (int i = 0; i < 6; i++) {
            world.pullBulletToCore(b, 1f / 60f);
            float now = (float) Math.atan2(b.vy, b.vx);
            float d = Math.abs(now - prev);
            while (d > Math.PI) d = Math.abs(d - (float) (Math.PI * 2));
            assertTrue("一帧最多拧 " + (GameWorld.CORE_PULL_TURN / 60f) + " rad，不会出现直角急弯",
                    d <= GameWorld.CORE_PULL_TURN / 60f + 1e-4f);
            prev = now;
        }
    }

    @Test
    public void missilesAndTheLanceAreNotTowed() {
        use(Boss.Form.SPECTER);
        freezeHull();
        openCore();
        // 制导弹交给它自己的制导、终极光矛走自己的线：这两类弹牵索一律不碰
        Bullet missile = shot(world.coreZoneX(0) - 100f, world.coreZoneY() + 120f,
                0f, -300f, 1);
        missile.homing = true;
        float mx = missile.vx, my = missile.vy;
        world.pullBulletToCore(missile, 1f / 60f);
        assertEquals("制导弹交给它自己的制导", mx, missile.vx, 1e-6f);
        assertEquals("制导弹交给它自己的制导", my, missile.vy, 1e-6f);

        Bullet lance = shot(world.coreZoneX(0) - 100f, world.coreZoneY() + 120f,
                0f, -300f, 1);
        lance.ult = true;
        float lx = lance.vx, ly = lance.vy;
        world.pullBulletToCore(lance, 1f / 60f);
        assertEquals("终极光矛走自己的线", lx, lance.vx, 1e-6f);
        assertEquals("终极光矛走自己的线", ly, lance.vy, 1e-6f);
    }

    // ---- 边界：词缀、过场、重开 ----

    @Test
    public void blazeAffixStillKeepsTheFormsOwnWindow() {
        use(Boss.Form.SPECTER);
        freezeHull();
        world.applyBossAffix(BossAffix.BLAZE);
        openCore();
        assertEquals("疾火只把开核排得更勤，不改这一型的窗口长度",
                Boss.coreFinaleFor(Boss.Form.SPECTER).window,
                world.bossCoreTimer + 1f / 60f, 0.02f);
        world.bossCoreTimer = 0.01f;
        step(1);
        assertEquals("疾火的间隔修正照旧",
                GameWorld.CORE_INTERVAL * 0.82f, world.bossCoreCooldown, 0.02f);
    }

    @Test
    public void aPhaseBreakClosesTheWindowAndLetsEverythingGo() {
        use(Boss.Form.HIVE);
        freezeHull();
        openCore();
        markWalk();
        events.clear();
        world.bossPhaseBreakTimer = 0.5f;
        stepTaken(12);
        assertFalse("换段过场该把窗口收掉", world.bossCoreOpen());
        assertEquals(1, countOf(GameWorld.EventType.BOSS_CORE_CLOSE));
        assertTrue("窗口没了，钉位也就撤了：" + walked(), walked() > 0.5f);
        assertTrue("收掉之后它照常出手", beatsTotal(6) > 0);
    }

    @Test
    public void closedWindowLeavesTheFightAsItWas() {
        for (Boss.Form f : ALL_FORMS) {
            use(f);
            freezeHull();
            world.bossCoreTimer = 0f;
            int total = beatsTotal(4);
            Bullet b = shot(world.boss.x - 90f, world.boss.y + 110f, 0f, -300f, 1);
            step(4);
            assertTrue(f.label + " 没开核时一切都该照旧：出手 " + total,
                    total > 0 || world.boss.staggerTimer > 0f);
            if (b.active) {
                assertEquals("不开核不拽弹：" + f.label, 0f, b.vx, 1e-4f);
            }
        }
    }

    @Test
    public void restartClearsTheFinaleLedger() {
        use(Boss.Form.MAGNET);
        openCore();
        assertTrue(world.bossCoreOpen());
        world.restart(GameMode.WAVES);
        assertEquals("重开不该带着上一场的窗口", 0f, world.bossCoreTimer, 1e-3f);
        assertEquals("提示行的余辉也不该跨场", 0f, world.coreFinaleFlash, 1e-3f);
    }

    @Test
    public void aFreshShipDoesNotInheritTheOldShipsAnchor() {
        use(Boss.Form.DOCKYARD);
        freezeHull();
        parkBoss();                       // 上一艘钉在屏幕正中
        openCore();
        float x = world.boss.x;
        step(30);
        assertEquals("钉在开核那一刻", x, world.boss.x, 1e-3f);

        // 换一艘：先让它走到别处，再按正常节拍开核，钉的必须是新位置
        Boss next = use(Boss.Form.HIVE);
        freezeHull();
        world.bossCoreTimer = 0f;
        step(90);
        assertTrue("新船开核前该已经走离上一艘的锚点",
                Math.abs(next.x - x) > 20f);
        float fresh = next.x;
        openCore();
        step(30);
        assertEquals("新船钉的是它自己开核那一刻的坐标", fresh, next.x, 1e-3f);
    }
}
