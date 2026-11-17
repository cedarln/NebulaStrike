package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.70 迭代 60：形态舰装 · FORM LOADOUT。
 *
 * <p>形态一路加到八型之后，剩下的"全都长一个样"其实只剩一件事：部件挂在船体的哪儿。
 * 以前八个形态共用一套站位——两门炮永远在左右同一高度，护盾器永远压在核心下面，
 * 于是先拆哪一件、哪件挡在核心前面，每一型都是同一个答案。这一版把站位交回给形态自己：
 * 静默把炮推到翼尖并压上舷侧，灰烬把所有件摊到舰体下缘一条弧上，船坞摊成上下两层甲板，
 * 蜂巢沿舰体绕一圈斜臂，裂谷拉两对对角线，磁暴挤成左右两个极点，镜面分内外两圈，
 * 星核拿部件在核心前面垒一道护心弧墙。
 *
 * <p>三条硬口径：① <b>只挪位置</b>——件数、种类、半径、血量、开火节奏仍然只看舰级，
 * v1.31 那本平衡账一个字没动；② 站位仍在舰影包络内、互相隔开、不叠在核心上，
 * 换形态不会把部件推出屏幕或叠成一个靶子；③ 词缀、走位、棱镜召唤这些既有行为照旧，
 * 飞行中不会把站位滑走。
 */
public class BossFormLoadoutV170Test {

    private static final Boss.Form[] FORMS = Boss.Form.values();
    /** 舰级 0..3 的部件件数：这一版没有改它，改它就得重开平衡账。 */
    private static final int[] COUNT_BY_TIER = {2, 6, 9, 12};

    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(20261125L);
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

    private void step(int frames) {
        for (int i = 0; i < frames; i++) {
            // 这一版量的是站位，别让飞船把旗舰打掉血、触发转阶段与棱镜召唤
            world.ship.weaponTimer = 999f;
            world.update(1f / 60f);
            world.pollEvents();
        }
    }

    /** 单独造一艘"这一型、这一关"的旗舰：init 按关卡把半径/件数/血量摆好，再换成指定形态重排。 */
    private Boss bossOf(Boss.Form form, int level) {
        Boss b = new Boss();
        b.init(level);
        b.form = form;
        b.layoutParts();
        return b;
    }

    /** 站位归一化（除以舰半径），这样不同舰级比的是同一件事。 */
    private float[][] stationsOf(Boss b) {
        float[][] s = new float[b.partCount][2];
        for (int i = 0; i < b.partCount; i++) {
            s[i][0] = b.parts[i].ox / b.radius;
            s[i][1] = b.parts[i].oy / b.radius;
        }
        return s;
    }

    /** 按件数预演某一型的站位。 */
    private float[][] grid(Boss.Form form, int n) {
        return Boss.formStations(form, n);
    }

    private String inventory(Boss b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.partCount; i++) {
            BossPart p = b.parts[i];
            sb.append(p.kind).append('|').append((int) (p.radius * 100)).append('|')
                    .append((int) (p.maxHp * 100)).append('|')
                    .append((int) (p.fireCd * 100)).append(";");
        }
        return sb.toString();
    }

    private float meanY(float[][] s) {
        float sum = 0f;
        for (float[] q : s) sum += q[1];
        return sum / s.length;
    }

    private float minPairGap(float[][] s) {
        float best = Float.MAX_VALUE;
        for (int i = 0; i < s.length; i++) {
            for (int j = i + 1; j < s.length; j++) {
                best = Math.min(best, (float) Math.hypot(s[j][0] - s[i][0], s[j][1] - s[i][1]));
            }
        }
        return best;
    }

    private float minDistanceFromCore(float[][] s) {
        float best = Float.MAX_VALUE;
        for (float[] q : s) best = Math.min(best, (float) Math.hypot(q[0], q[1]));
        return best;
    }

    // ---- ① 八型八种舰装 ----

    @Test
    public void theEightFormsNameEightDifferentLoadouts() {
        Set<String> seen = new HashSet<>();
        for (Boss.Form f : FORMS) {
            assertNotNull("每型都得报出自己的舰装", f.loadout);
            assertFalse("舰装不能是空的", f.loadout.trim().isEmpty());
            assertTrue(f + " 的舰装名太长了，横幅放不下: " + f.loadout,
                    f.loadout.length() <= 6);
            assertTrue("舰装名重复: " + f.loadout, seen.add(f.loadout));
        }
        assertEquals("形态有几型，舰装就有几种", FORMS.length, seen.size());
    }

    @Test
    public void theWeeklyFormCycleCarriesSevenDifferentRigs() {
        Set<String> seen = new HashSet<>();
        for (int level = 0; level < FORMS.length - 1; level++) {
            seen.add(Boss.formFor(level).loadout);
        }
        assertEquals("循环里的每一型各有各的排布", FORMS.length - 1, seen.size());
    }

    // ---- ② 只挪位置，不改账 ----

    @Test
    public void partCountStillComesFromRankNotForm() {
        for (int tier = 0; tier <= 3; tier++) {
            for (Boss.Form f : FORMS) {
                assertEquals(f + " 在舰级 " + tier + " 的件数被改了",
                        COUNT_BY_TIER[tier], bossOf(f, tier).partCount);
            }
        }
    }

    @Test
    public void theInventoryIsIdenticalAcrossForms() {
        for (int tier = 0; tier <= 3; tier++) {
            String expected = inventory(bossOf(Boss.Form.SPECTER, tier));
            for (Boss.Form f : FORMS) {
                assertEquals(f + " 在舰级 " + tier + " 偷偷换了部件清单",
                        expected, inventory(bossOf(f, tier)));
            }
        }
    }

    @Test
    public void theHullAndBountyDoNotDependOnTheRig() {
        for (Boss.Form f : FORMS) {
            Boss a = bossOf(f, 2);
            Boss b = bossOf(Boss.Form.SPECTER, 2);
            assertEquals(f + " 的舰体血量变了", b.maxHp, a.maxHp, 0.001f);
            assertEquals(f + " 的命中判定了变大", b.hitRadius, a.hitRadius, 0.001f);
            for (BossPart.Kind k : BossPart.Kind.values()) {
                assertEquals(f + " 的 " + k + " 件数变了", countKind(b, k), countKind(a, k));
            }
        }
    }

    private int countKind(Boss b, BossPart.Kind kind) {
        int n = 0;
        for (int i = 0; i < b.partCount; i++) {
            if (b.parts[i].kind == kind) n++;
        }
        return n;
    }

    // ---- ③ 站位真的各不相同 ----

    @Test
    public void everyPairOfFormsSpreadsItsPartsDifferently() {
        for (int tier = 0; tier <= 3; tier++) {
            for (Boss.Form a : FORMS) {
                float[][] ga = stationsOf(bossOf(a, tier));
                for (Boss.Form b : FORMS) {
                    if (a == b) continue;
                    float[][] gb = stationsOf(bossOf(b, tier));
                    boolean differs = false;
                    for (int i = 0; i < ga.length; i++) {
                        if (Math.abs(ga[i][0] - gb[i][0]) > 0.02f
                                || Math.abs(ga[i][1] - gb[i][1]) > 0.02f) {
                            differs = true;
                            break;
                        }
                    }
                    assertTrue(a + " 和 " + b + " 在舰级 " + tier + " 摆的是同一副舰装", differs);
                }
            }
        }
    }

    @Test
    public void theRigIsJustTheStationGrid() {
        for (Boss.Form f : FORMS) {
            for (int tier = 0; tier <= 3; tier++) {
                Boss b = bossOf(f, tier);
                float[][] want = grid(f, b.partCount);
                float[][] got = stationsOf(b);
                for (int i = 0; i < b.partCount; i++) {
                    assertEquals(f + "/" + tier + " 第 " + i + " 件没按站位表摆",
                            want[i][0], got[i][0], 0.001f);
                    assertEquals(f + "/" + tier + " 第 " + i + " 件没按站位表摆",
                            want[i][1], got[i][1], 0.001f);
                }
            }
        }
    }

    // ---- ④ 包络、间距、不挡核心 ----

    @Test
    public void stationsStayOnTheHull() {
        for (Boss.Form f : FORMS) {
            for (int n = 1; n <= 12; n++) {
                for (float[] q : grid(f, n)) {
                    assertTrue(f + " n=" + n + " 横推出舰影: " + q[0],
                            Math.abs(q[0]) <= 1.06f);
                    assertTrue(f + " n=" + n + " 高到贴屏幕上沿: " + q[1], q[1] >= -0.71f);
                    assertTrue(f + " n=" + n + " 低到压进玩家航线: " + q[1], q[1] <= 0.97f);
                    assertFalse(f + " n=" + n + " 站位缺数",
                            Float.isNaN(q[0]) || Float.isNaN(q[1]));
                }
            }
        }
    }

    @Test
    public void noTwoStationsCollapseIntoOneTarget() {
        for (Boss.Form f : FORMS) {
            for (int n = 2; n <= 12; n++) {
                float gap = minPairGap(grid(f, n));
                assertTrue(f + " n=" + n + " 有两件叠在一起，间距只有 " + gap, gap >= 0.255f);
            }
        }
    }

    @Test
    public void nothingSitsOnTopOfTheCore() {
        for (Boss.Form f : FORMS) {
            for (int n = 1; n <= 12; n++) {
                float d = minDistanceFromCore(grid(f, n));
                assertTrue(f + " n=" + n + " 有件压在核心正中 " + d, d >= 0.28f);
            }
        }
    }

    @Test
    public void theLayoutIsTheSameEveryTimeItIsRolled() {
        Boss a = bossOf(Boss.Form.HIVE, 3);
        Boss b = bossOf(Boss.Form.HIVE, 3);
        assertTrue(Arrays.deepEquals(stationsOf(a), stationsOf(b)));
        b.layoutParts();
        assertTrue("重排一次就换了一副站位", Arrays.deepEquals(stationsOf(a), stationsOf(b)));
        assertEquals(inventory(a), inventory(b));
    }

    // ---- ⑤ 八型各自的说法 ----

    @Test
    public void cinderPressesEverythingForward() {
        for (int tier = 1; tier <= 3; tier++) {
            float[][] s = stationsOf(bossOf(Boss.Form.CINDER, tier));
            assertTrue("灰烬的件该全压在舰体下缘: " + meanY(s), meanY(s) >= 0.5f);
            for (float[] q : s) {
                assertTrue("灰烬不该有件跑到核心上面: " + Arrays.toString(q), q[1] > 0f);
            }
        }
    }

    @Test
    public void specterHangsItsWingsHighAndWide() {
        float[][] wide = stationsOf(bossOf(Boss.Form.SPECTER, 3));
        assertTrue("静默的件该压在核心之上: " + meanY(wide), meanY(wide) < 0f);
        float reach = 0f;
        for (float[] q : wide) reach = Math.max(reach, Math.abs(q[0]));
        assertTrue("静默的翼尖该推到舰影外缘: " + reach, reach >= 1.0f);
    }

    @Test
    public void dockyardSplitsIntoTwoDeckLines() {
        float[][] s = stationsOf(bossOf(Boss.Form.DOCKYARD, 3));
        int upper = 0, lower = 0;
        for (float[] q : s) {
            if (Math.abs(q[1] - 0.52f) < 0.03f) upper++;
            else if (Math.abs(q[1] + 0.06f) < 0.03f) lower++;
        }
        assertEquals("上层甲板排满", 6, upper);
        assertEquals("下层甲板排满", 6, lower);
    }

    @Test
    public void sentinelWallsTheCoreOff() {
        for (int tier = 0; tier <= 3; tier++) {
            float[][] s = stationsOf(bossOf(Boss.Form.SENTINEL, tier));
            for (float[] q : s) {
                assertTrue("星核的件必须全在核心与玩家之间: " + Arrays.toString(q), q[1] > 0f);
            }
        }
        // 墙是朝玩家鼓出来的弧：正对核心那一列得有两件以上前后叠着
        float[][] wall = stationsOf(bossOf(Boss.Form.SENTINEL, 3));
        assertTrue("护心墙的正前方不止一层", tallPartsAheadOfCore(wall) >= 2);
    }

    private int tallPartsAheadOfCore(float[][] s) {
        int n = 0;
        for (float[] q : s) {
            if (Math.abs(q[0]) < 0.25f && q[1] > 0.6f) n++;
        }
        return n;
    }

    @Test
    public void mirrorRingsBothSidesOfTheCore() {
        float[][] s = stationsOf(bossOf(Boss.Form.MIRROR, 3));
        int behind = 0, ahead = 0;
        for (float[] q : s) {
            if (q[1] < 0f) behind++;
            else ahead++;
        }
        assertTrue("镜面该有件绕到核心后面: " + behind, behind >= 3);
        assertTrue("镜面该有件留在核心前面: " + ahead, ahead >= 2);
    }

    @Test
    public void magnetClumpsPartsIntoTwoPoles() {
        float[][] s = stationsOf(bossOf(Boss.Form.MAGNET, 3));
        float closestToCentre = Float.MAX_VALUE;
        for (float[] q : s) closestToCentre = Math.min(closestToCentre, Math.abs(q[0]));
        assertTrue("磁暴的两极不该贴中线: " + closestToCentre, closestToCentre >= 0.35f);
    }

    @Test
    public void hiveWalksItsPartsAroundTheHull() {
        float[][] s = stationsOf(bossOf(Boss.Form.HIVE, 3));
        float low = Float.MAX_VALUE, high = -Float.MAX_VALUE;
        for (float[] q : s) {
            low = Math.min(low, q[1]);
            high = Math.max(high, q[1]);
        }
        assertTrue("蜂巢的斜臂要真的绕过舰体", high - low >= 1.1f);
    }

    // ---- ⑥ 局内：真旗舰按这一型的舰装入场 ----

    /** 叫出旗舰、跳过演出、压掉词缀。 */
    private Boss triggerBoss(int level) {
        if (world.boss.active) {
            world.boss.hp = 0f;
            step(1);
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
        drain();
        return world.boss;
    }

    @Test
    public void aSpawnedBossWearsItsOwnFormsRig() {
        for (int level = 0; level <= 6; level++) {
            Boss b = triggerBoss(level);
            float[][] want = grid(b.form, b.partCount);
            float[][] got = stationsOf(b);
            for (int i = 0; i < b.partCount; i++) {
                assertEquals("关卡 " + level + "（" + b.form + "）第 " + i + " 件",
                        want[i][0], got[i][0], 0.001f);
                assertEquals("关卡 " + level + "（" + b.form + "）第 " + i + " 件",
                        want[i][1], got[i][1], 0.001f);
            }
            Boss reference = bossOf(b.form, b.level);
            assertEquals("局内排布与预演不一致: " + b.form + " 关卡 " + level,
                    inventory(reference), inventory(b));
        }
    }

    @Test
    public void anAffixDoesNotRemodelTheShip() {
        Boss b = triggerBoss(3);
        float[][] plain = stationsOf(b);
        for (BossAffix affix : BossAffix.values()) {
            world.applyBossAffix(affix);
            b.layoutParts();
            assertTrue(affix + " 不该挪部件", Arrays.deepEquals(plain, stationsOf(b)));
        }
        world.applyBossAffix(BossAffix.NONE);
    }

    @Test
    public void flyingDoesNotSlideThePartsOffTheirStations() {
        Boss b = triggerBoss(5);
        float[][] want = stationsOf(b);
        b.phase = 1;
        step(240);
        float[][] got = stationsOf(b);
        for (int i = 0; i < want.length; i++) {
            if (b.parts[i].kind == BossPart.Kind.PRISM) continue;
            assertEquals("飞了 4 秒，" + b.form + " 的第 " + i + " 件滑走了",
                    want[i][0], got[i][0], 0.001f);
            assertEquals(want[i][1], got[i][1], 0.001f);
        }
    }

    @Test
    public void theFinalBossKeepsTheHeartWall() {
        Boss b = triggerBoss(0);
        b.form = Boss.Form.SENTINEL;
        b.tier = 3;
        b.radius = 60f + 14f * b.tier;
        b.layoutParts();
        assertEquals("最终旗舰的舰装就是护心弧墙", "护心弧墙", b.form.loadout);
        assertEquals(12, b.partCount);
        for (float[] q : stationsOf(b)) {
            assertTrue("最终旗舰的件不能在核心上面: " + Arrays.toString(q), q[1] > 0f);
        }
    }

    @Test
    public void destroyingPartsDoesNotMoveTheRest() {
        Boss b = triggerBoss(3);
        float[][] before = stationsOf(b);
        b.parts[0].active = false;
        b.parts[0].hp = 0f;
        b.layoutParts();
        float[][] after = stationsOf(b);
        assertEquals("重排之后件数按清单来，不受战损影响", before.length, after.length);
        for (int i = 0; i < before.length; i++) {
            assertTrue(Arrays.equals(before[i], after[i]));
        }
    }

    @Test
    public void theRigSurvivesARestart() {
        Boss b = triggerBoss(4);
        Boss.Form form = b.form;
        float[][] want = stationsOf(b);
        world.restart(GameMode.WAVES);
        Boss after = triggerBoss(4);
        assertEquals("重开还是那一型", form, after.form);
        assertTrue(Arrays.deepEquals(want, stationsOf(after)));
    }

    @Test
    public void theGridHandlesTinyAndOversizedRosters() {
        for (Boss.Form f : FORMS) {
            assertEquals("0 件就是空表", 0, grid(f, 0).length);
            assertEquals(1, grid(f, 1).length);
            assertTrue("件数爆了也要各自站开", minPairGap(grid(f, 16)) >= 0.2f);
            for (float[] q : grid(f, 16)) {
                assertTrue(f + " 16 件挤出包络", Math.abs(q[0]) <= 1.06f && q[1] <= 0.97f
                        && q[1] >= -0.71f);
            }
        }
    }

    @Test
    public void everyFormHasAStationEvenAtTheEdgeOfTheCycle() {
        List<String> labels = new ArrayList<>();
        for (Boss.Form f : FORMS) labels.add(f.label + "·" + f.loadout);
        assertEquals("形态与舰装一一配对", FORMS.length, new HashSet<>(labels).size());
        assertFalse(labels.toString(), labels.contains(null));
    }
}
