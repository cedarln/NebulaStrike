package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.75 迭代 65：阶段骨架 PHASE FRAME · 一型三相，拆掉的件在舰体上留疤。
 *
 * <p>v1.61 给了八型旗舰八种剪影，可同一艘船从满血打到残血还是那一个形状——换段只是换个颜色、
 * 多两枚棱镜。这一版把"打到第几段"写进轮廓本身：一阶段收着（reach）、二阶段展开（flare 把最靠
 * 舷侧的顶点推出去）、三阶段破甲（carve 把交替顶点往舰心咬成锯齿），各配一个姿态角；
 * 再加上被拆掉的每一件在舷侧留下一道疤（{@link Boss#destroyedStations()}）。
 *
 * <p>三条硬口径：① <b>纯观感</b>——判定半径、舰体半径、部件站位与火力、伤害与分数
 * 都不从这张表取值，轮廓再展再咬也不会把碰撞盒挪走；② <b>一型三相不重样，八型同相也不重样</b>，
 * 24 副骨架一一对得上，不然"三相"就只是改了个数字；③ 疤的顺序与部件清单一致、
 * 角度取站位、PRISM 不留疤（它本来就绕着船走）。
 */
public class BossPhaseFrameV175Test {

    /** 一型三相，八型共 24 副。 */
    private static final int FRAME_TOTAL = 24;
    private static final Boss.Form[] ALL_FORMS = Boss.Form.values();
    /** 姿态角预算：歪得出来"侧着飞"，但不至于把判定说成另一个形状。 */
    private static final float TILT_LIMIT = 11f;
    /** reach 预算：最宽的一型（灰烬 1.30 单位）乘到这里还在装甲底衬之内。 */
    private static final float REACH_MIN = 0.85f;
    private static final float REACH_MAX = 1.22f;

    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(20261130L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.bountyEnabled = false;
        world.perksEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.restart(GameMode.WAVES);
        drain();
    }

    // ---- 辅助 ----

    private void drain() {
        world.pollEvents();
    }

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
        drain();
        return world.boss;
    }

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

    /** 直接让第 index 件掉下来（渲染要的只是"这件不在了 + 它拆下来多久"）。 */
    private void down(int index) {
        BossPart p = world.boss.parts[index];
        assertTrue("这件本来就该还活着", p.active);
        p.hp = 0f;
        p.active = false;
    }

    /** 站位角：部件相对舰心的方向，和渲染层用的是同一个数。 */
    private static float stationAngle(BossPart p) {
        return (float) Math.atan2(p.oy, p.ox);
    }

    private int downedNonPrisms() {
        int n = 0;
        for (int i = 0; i < world.boss.partCount; i++) {
            BossPart p = world.boss.parts[i];
            if (!p.active && p.kind != BossPart.Kind.PRISM) n++;
        }
        return n;
    }

    // ---- 一、数据表：一型三相，24 副不重样 ----

    @Test
    public void everyFormHasThreeFrames() {
        assertEquals("形态数 × 三相", FRAME_TOTAL, Boss.phaseFrameCount());
        for (Boss.Form f : ALL_FORMS) {
            for (int phase = 1; phase <= 3; phase++) {
                Boss.PhaseFrame fr = Boss.frameFor(f, phase);
                assertNotNull(f.label + " 第" + phase + "相没有骨架", fr);
                assertFalse(f.label + " 的骨架没写给玩家看的那句话", fr.note.isEmpty());
                assertTrue(f.label + " 的那句话太长：" + fr.note, fr.note.length() <= 20);
            }
        }
    }

    @Test
    public void theThreeFramesOfAFormNeverRepeat() {
        for (Boss.Form f : ALL_FORMS) {
            String a = Boss.frameFor(f, 1).signature();
            String b = Boss.frameFor(f, 2).signature();
            String c = Boss.frameFor(f, 3).signature();
            assertTrue(f.label + " 的一相和二相一模一样：" + a, !a.equals(b));
            assertTrue(f.label + " 的二相和三相一模一样：" + b, !b.equals(c));
            assertTrue(f.label + " 的一相和三相一模一样：" + a, !a.equals(c));
        }
    }

    @Test
    public void theSamePhaseLooksDifferentAcrossForms() {
        for (int phase = 1; phase <= 3; phase++) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (Boss.Form f : ALL_FORMS) {
                String sig = Boss.frameFor(f, phase).signature();
                assertTrue("第" + phase + "相有两型旗舰共用一副骨架：" + f.label + " " + sig,
                        seen.add(sig));
            }
            assertEquals(ALL_FORMS.length, seen.size());
        }
    }

    @Test
    public void phaseOneIsStowedAndPhaseTwoIsSpread() {
        for (Boss.Form f : ALL_FORMS) {
            Boss.PhaseFrame one = Boss.frameFor(f, 1);
            Boss.PhaseFrame two = Boss.frameFor(f, 2);
            Boss.PhaseFrame three = Boss.frameFor(f, 3);
            assertEquals(f.label + " 一阶段不该展开舷侧", 0f, one.flare, 1e-4f);
            assertTrue(f.label + " 二阶段展得不够开：" + two.flare, two.flare >= 0.14f);
            assertTrue(f.label + " 三阶段该把展开收回来一些",
                    three.flare > 0f && three.flare < two.flare);
        }
    }

    @Test
    public void onlyPhaseThreeBitesTheOutline() {
        for (Boss.Form f : ALL_FORMS) {
            assertEquals(f.label + " 前两段不该有破甲缺口",
                    0f, Boss.frameFor(f, 1).carve, 1e-4f);
            assertEquals(f.label + " 前两段不该有破甲缺口",
                    0f, Boss.frameFor(f, 2).carve, 1e-4f);
            assertTrue(f.label + " 三阶段咬得太轻：" + Boss.frameFor(f, 3).carve,
                    Boss.frameFor(f, 3).carve >= 0.18f);
            assertTrue(f.label + " 三阶段咬穿了核心", Boss.frameFor(f, 3).carve <= 0.4f);
        }
    }

    @Test
    public void theFramesStayInsideTheSilhouetteBudget() {
        for (Boss.Form f : ALL_FORMS) {
            for (int phase = 1; phase <= 3; phase++) {
                Boss.PhaseFrame fr = Boss.frameFor(f, phase);
                assertTrue(f.label + " 第" + phase + "相缩过头：" + fr.reachX,
                        fr.reachX >= REACH_MIN && fr.reachX <= REACH_MAX);
                assertTrue(f.label + " 第" + phase + "相撑过头：" + fr.reachY,
                        fr.reachY >= REACH_MIN && fr.reachY <= REACH_MAX);
                assertTrue(f.label + " 第" + phase + "相歪太多：" + fr.tilt,
                        Math.abs(fr.tilt) <= TILT_LIMIT);
                assertTrue(f.label + " 的展开系数越界：" + fr.flare,
                        fr.flare >= 0f && fr.flare <= 0.3f);
                assertTrue(f.label + " 的破甲系数越界：" + fr.carve,
                        fr.carve >= 0f && fr.carve <= 0.4f);
            }
        }
    }

    @Test
    public void frameForClampsThePhaseAndFallsBackToAShip() {
        for (Boss.Form f : ALL_FORMS) {
            assertSame("越界的相位钳回第一段", Boss.frameFor(f, 1), Boss.frameFor(f, 0));
            assertSame(Boss.frameFor(f, 1), Boss.frameFor(f, -7));
            assertSame("越界的相位钳回第三段", Boss.frameFor(f, 3), Boss.frameFor(f, 4));
            assertSame(Boss.frameFor(f, 3), Boss.frameFor(f, 99));
        }
        assertSame("没形态时按静默兜底，渲染层不会拿到空指针",
                Boss.frameFor(Boss.Form.SPECTER, 2), Boss.frameFor(null, 2));
        assertNotSame(Boss.frameFor(Boss.Form.SPECTER, 1), Boss.frameFor(Boss.Form.SPECTER, 2));
    }

    // ---- 二、战损疤：拆掉的每一件在舷侧留一道 ----

    @Test
    public void aFreshShipHasNoScars() {
        for (Boss.Form f : ALL_FORMS) {
            use(f);
            assertEquals(f.label + " 满舰装时不该有疤", 0, world.boss.destroyedStations().length);
            assertEquals(0, downedNonPrisms());
        }
    }

    @Test
    public void everyDownedPartLeavesOneScarAtItsOwnStation() {
        Boss b = use(Boss.Form.DOCKYARD);
        int n = b.partCount;
        for (int i = 0; i < n; i++) {
            assertEquals("拆到第 " + i + " 件之前疤数不对", i, b.destroyedStations().length);
            down(i);
        }
        Boss.BreachScar[] scars = b.destroyedStations();
        assertEquals("件件留疤（船坞没有 PRISM）", n, scars.length);
        for (int i = 0; i < n; i++) {
            BossPart p = b.parts[i];
            assertEquals("疤的方向就是这件部件的站位：" + i,
                    stationAngle(p), scars[i].angle, 1e-4f);
            assertEquals(p.kind, scars[i].kind);
        }
    }

    @Test
    public void scarsKeepThePartListOrder() {
        Boss b = use(Boss.Form.HIVE);
        // 故意从后往前拆，疤仍然按部件清单的顺序排
        for (int i = b.partCount - 1; i >= 0; i--) down(i);
        Boss.BreachScar[] scars = b.destroyedStations();
        assertEquals(b.partCount, scars.length);
        for (int i = 0; i < b.partCount; i++) {
            assertEquals(stationAngle(b.parts[i]), scars[i].angle, 1e-4f);
        }
    }

    @Test
    public void prismsLeaveNoScar() {
        Boss b = use(Boss.Form.SENTINEL);
        // 棱镜是开打后才挂上去的：站位本来随轨道走，拆掉不该在舷侧留固定疤
        BossPart prism = b.parts[b.partCount - 1];
        prism.kind = BossPart.Kind.PRISM;
        prism.active = false;
        prism.ox = b.radius * 1.18f;
        prism.oy = -b.radius * 0.1f;
        assertEquals("PRISM 不进疤的账", 0, b.destroyedStations().length);

        down(0);
        Boss.BreachScar[] scars = b.destroyedStations();
        assertEquals(1, scars.length);
        assertEquals(BossPart.Kind.GUN, scars[0].kind);
        assertEquals(stationAngle(b.parts[0]), scars[0].angle, 1e-4f);
    }

    @Test
    public void scarAgeGrowsWhileTheWreckCools() {
        Boss b = use(Boss.Form.CINDER);
        down(1);
        assertEquals("刚拆下来还是新的", 0f, b.destroyedStations()[0].age, 1e-4f);
        step(60);
        float age = b.destroyedStations()[0].age;
        assertTrue("一秒后该有一秒的余烬：" + age, age > 0.85f && age < 1.15f);
        step(180);
        assertTrue("疤不会自己长回去", b.destroyedStations()[0].age > age + 2.5f);
        assertEquals("件数不随时间变", 1, b.destroyedStations().length);
    }

    @Test
    public void stationAnglesAreStableWhileTheShipFlies() {
        Boss b = use(Boss.Form.RIFT);
        down(0);
        down(2);
        float[] before = new float[2];
        Boss.BreachScar[] first = b.destroyedStations();
        for (int i = 0; i < 2; i++) before[i] = first[i].angle;
        step(240);
        Boss.BreachScar[] after = b.destroyedStations();
        assertEquals(2, after.length);
        for (int i = 0; i < 2; i++) {
            assertEquals("船飞再久，疤还是那两件的位置",
                    before[i], after[i].angle, 1e-4f);
        }
    }

    @Test
    public void wreckingPartsChangesNoHitboxOrFirepower() {
        Boss b = use(Boss.Form.MIRROR);
        float hitRadius = b.hitRadius;
        float bodyRadius = b.radius;
        int parts = b.partCount;
        Boss.CoreFinale fin = Boss.coreFinaleFor(b.form);
        for (int i = 0; i < parts; i++) {
            assertEquals("部件本来就只登记站位与火力，不登记疤",
                    hitRadius, b.hitRadius, 1e-4f);
            down(i);
            assertEquals(b.form.label + " 拆到第 " + i + " 件，判定半径没动",
                    hitRadius, b.hitRadius, 1e-4f);
            assertEquals(bodyRadius, b.radius, 1e-4f);
            assertEquals(parts, b.partCount);
            assertSame(fin, Boss.coreFinaleFor(b.form));
        }
        assertTrue("全拆光仍然是那副破甲骨架", b.partsAllDestroyed());
        assertSame(Boss.frameFor(b.form, 1), Boss.frameFor(b.form, 1));
        assertEquals("骨架表不被战损改写", FRAME_TOTAL, Boss.phaseFrameCount());
    }

    // ---- 三、相位与骨架一起走 ----

    @Test
    public void theFrameTracksThePhaseAsTheHullBreaks() {
        Boss b = use(Boss.Form.MAGNET);
        b.maxHp = 1000f;
        String one = Boss.frameFor(b.form, b.phase).signature();
        assertEquals(1, b.phase);

        b.hp = 600f;
        step(2);
        assertEquals(2, b.phase);
        String two = Boss.frameFor(b.form, b.phase).signature();
        assertTrue("换到二阶段该换一副骨架", !one.equals(two));
        assertEquals(0f, Boss.frameFor(b.form, b.phase).carve, 1e-4f);
        assertTrue(Boss.frameFor(b.form, b.phase).flare > 0f);

        b.hp = 250f;
        step(2);
        assertEquals(3, b.phase);
        String three = Boss.frameFor(b.form, b.phase).signature();
        assertTrue("换到三阶段还是同一副：" + three, !two.equals(three));
        assertTrue("三阶段读到的骨架该带着破甲缺口",
                Boss.frameFor(b.form, b.phase).carve > 0f);
    }

    @Test
    public void everyFormReachesABittenFrame() {
        for (Boss.Form f : ALL_FORMS) {
            Boss b = use(f);
            b.maxHp = 1000f;
            b.hp = 250f;
            step(2);
            assertEquals(f.label + " 该进三阶段", 3, b.phase);
            Boss.PhaseFrame fr = Boss.frameFor(b.form, b.phase);
            assertTrue(f.label + " 的三阶段没有缺口", fr.carve > 0f);
            assertTrue(f.label + " 的三阶段还带着二阶段的展开",
                    fr.flare < Boss.frameFor(f, 2).flare);
            assertFalse(f.label + " 的三阶段话是空的", fr.note.isEmpty());
        }
    }

    @Test
    public void restartResetsTheWreck() {
        Boss b = use(Boss.Form.SPECTER);
        down(0);
        down(1);
        assertEquals(2, b.destroyedStations().length);

        world.restart(GameMode.WAVES);
        drain();
        use(Boss.Form.CINDER);
        assertEquals("重开不留上一艘的疤", 0, world.boss.destroyedStations().length);
        assertEquals("相位也回到第一段", 1, world.boss.phase);
        assertSame(Boss.frameFor(world.boss.form, 1),
                Boss.frameFor(world.boss.form, world.boss.phase));
    }
}
