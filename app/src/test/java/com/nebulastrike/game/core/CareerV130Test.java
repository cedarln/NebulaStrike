package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * v1.30 迭代 20：生涯数据面板。
 *
 * <p>这轮要钉住三件事：
 * <ol>
 *   <li><b>数字说人话</b>：大数缩写、时长、场均与每分钟得分都有确定口径，改格式不会悄悄变意思。</li>
 *   <li><b>账本只增不减</b>：出勤 / 阵亡逐次累加，最深波次只往上刷，脏存档会被 sanitize 归零。</li>
 *   <li><b>入口不挤</b>：菜单第三行三个 140 宽入口互不重叠，生涯页打开后只认返回键。</li>
 * </ol>
 */
public class CareerV130Test {

    // ---- 纯计算：格式化 ----

    @Test
    public void largeCountsAbbreviateAtTenThousandAndOneHundredMillion() {
        assertEquals("0", Career.count(0));
        assertEquals("0", Career.count(-5));
        assertEquals("9999", Career.count(9_999));
        assertEquals("1万", Career.count(10_000));
        assertEquals("1.2万", Career.count(12_345));
        assertEquals("10万", Career.count(99_999));
        assertEquals("123.5万", Career.count(1_234_567));
        assertEquals("1亿", Career.count(100_000_000));
        assertEquals("21.5亿", Career.count(Integer.MAX_VALUE));
    }

    @Test
    public void durationsReadAsHoursMinutesSeconds() {
        assertEquals("0s", Career.duration(0));
        assertEquals("42s", Career.duration(42));
        assertEquals("1m", Career.duration(60));
        assertEquals("59m 59s", Career.duration(3_599));
        assertEquals("1h", Career.duration(3_600));
        assertEquals("1h 5m", Career.duration(3_900));
        assertEquals("25h", Career.duration(90_000));
    }

    @Test
    public void ratesAndAveragesStaySafeWhenEmpty() {
        assertEquals(0, Career.scorePerMinute(0, 600));
        assertEquals(0, Career.scorePerMinute(12_000, 0));
        assertEquals(2_400, Career.scorePerMinute(12_000, 300));
        assertEquals(0, Career.perRun(100, 0));
        assertEquals(34, Career.perRun(101, 3));
        assertEquals(0, Career.perRun(1, 99));
    }

    @Test
    public void rankTitlesAndNextThresholdsAgree() {
        assertEquals("未入册", Achievement.rank(0));
        assertEquals("列兵", Achievement.rank(4));
        assertEquals("飞行员", Achievement.rank(5));
        assertEquals("提督", Achievement.rank(24));
        assertEquals("元帅", Achievement.rank(25));
        assertEquals("星海传说", Achievement.rank(Achievement.COUNT));

        assertEquals(5, Achievement.nextRankAt(0));
        assertEquals(5, Achievement.nextRankAt(4));
        assertEquals(10, Achievement.nextRankAt(5));
        assertEquals(25, Achievement.nextRankAt(24));
        assertEquals(Achievement.COUNT, Achievement.nextRankAt(25));
        assertEquals(-1, Achievement.nextRankAt(Achievement.COUNT));
    }

    // ---- 账本：累计与清洗 ----

    @Test
    public void countersAccumulateAndWaveOnlyMovesForward() {
        Settings s = new Settings();
        for (int i = 0; i < 3; i++) s.countRunStart();
        for (int i = 0; i < 2; i++) s.countDeath();
        assertEquals(3, s.runsStarted);
        assertEquals(2, s.deaths);

        assertTrue(s.noteWave(4));
        assertFalse("旧波次不能把纪录拉回去", s.noteWave(3));
        assertEquals(4, s.bestWave);
        assertTrue(s.noteWave(9));
        assertEquals(9, s.bestWave);
    }

    @Test
    public void sanitizeClearsNegativeCareerCounters() {
        Settings s = new Settings();
        s.runsStarted = -3;
        s.deaths = -1;
        s.bestWave = -7;
        s.sanitize();
        assertEquals(0, s.runsStarted);
        assertEquals(0, s.deaths);
        assertEquals(0, s.bestWave);
    }

    @Test
    public void startingARunCountsAttendanceAndWaveOne() {
        GameWorld world = new GameWorld(20261024L);
        world.spawningEnabled = false;
        assertEquals(0, world.settings.runsStarted);
        world.restart(GameMode.WAVES);
        assertEquals(1, world.settings.runsStarted);
        assertEquals(1, world.settings.bestWave);
        world.restart(GameMode.ENDLESS);
        assertEquals(2, world.settings.runsStarted);
    }

    @Test
    public void advancingWavesRaisesTheLifetimeBest() {
        GameWorld world = new GameWorld(7L);
        world.spawningEnabled = false;
        world.restart(GameMode.WAVES);
        world.score = 1_000;   // 越过 wave 1 的 500 分线
        world.update(1f / 60f);
        assertTrue("wave 2 应写进生涯最深波次", world.settings.bestWave >= 2);
    }

    @Test
    public void careerTotalsReadFromTheSameAchievementLedger() {
        GameWorld world = new GameWorld(99L);
        world.spawningEnabled = false;
        world.restart(GameMode.WAVES);
        world.kills = 42;
        world.commitAchievements();
        int kills = world.settings.achieveTotal[Achievement.Metric.KILLS.ordinal()];
        assertEquals(42, kills);
        assertEquals("42", Career.count(kills));
    }

    // ---- 界面：入口、互斥、返回 ----

    @Test
    public void careerEntryFitsTheThirdMenuRow() {
        assertEquals(Ui.CODEX.y, Ui.ACHIEVEMENTS.y, 0.01f);
        assertEquals(Ui.CODEX.y, Ui.CAREER.y, 0.01f);
        assertTrue("CODEX 与成就入口不能重叠",
                Ui.CODEX.x + Ui.CODEX.w <= Ui.ACHIEVEMENTS.x);
        assertTrue("成就与生涯入口不能重叠",
                Ui.ACHIEVEMENTS.x + Ui.ACHIEVEMENTS.w <= Ui.CAREER.x);
        assertEquals("三个入口右缘对齐菜单", 670f, Ui.CAREER.x + Ui.CAREER.w, 0.01f);
        assertTrue("返回键在所有入口下方",
                Ui.CAREER_BACK.y > Ui.CAREER.y + Ui.CAREER.h);
    }

    @Test
    public void careerPanelOpensAndOnlyHonorsItsBackButton() {
        GameWorld world = new GameWorld(3L, true);
        Ui.Action open = Ui.hit(GameWorld.State.MENU, false, false, false, false, false,
                Ui.CAREER.cx(), Ui.CAREER.cy());
        assertEquals(Ui.Action.OPEN_CAREER, open);
        world.handleUiAction(open);
        assertTrue(world.careerOpen);
        assertNull("生涯页上不响应菜单里的其他入口",
                Ui.hit(GameWorld.State.MENU, false, false, false, false, true,
                        Ui.CODEX.cx(), Ui.CODEX.cy()));

        world.handleUiAction(Ui.Action.OPEN_SETTINGS);
        assertFalse("打开设置时生涯页要收起", world.careerOpen);
        world.handleUiAction(Ui.Action.OPEN_CAREER);
        assertTrue(world.careerOpen);
        assertEquals(Ui.Action.CAREER_BACK,
                Ui.hit(GameWorld.State.MENU, false, false, false, false, true,
                        Ui.CAREER_BACK.cx(), Ui.CAREER_BACK.cy()));
        world.handleUiAction(Ui.Action.CAREER_BACK);
        assertFalse(world.careerOpen);
    }

    @Test
    public void backKeyClosesCareerPanelBeforeLeavingTheApp() {
        GameWorld world = new GameWorld(3L, true);
        world.careerOpen = true;
        assertTrue("返回键应被生涯页消费", world.consumeBack());
        assertFalse(world.careerOpen);
        assertFalse("裸主菜单的返回键要放行", world.consumeBack());
    }
}
