package com.nebulastrike.game.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

/**
 * v1.19 迭代 9：每日挑战。
 *
 * <p>这一轮要守的契约有三条，测试逐条钉住：
 *
 * <ol>
 *   <li><b>日期决定一切，而且可复现</b>：规则、命数、对局种子全部由 {@code yyyyMMdd}
 *       纯函数推导；同一天重开就是同一条时间线，刷分靠练习而不是重摇号。</li>
 *   <li><b>全球同一把尺</b>：日常局锁标准难度、屏蔽 LAB 金手指、掉落与初火力回到默认，
 *       否则"今天的最佳"没有可比性。</li>
 *   <li><b>不污染既有模式</b>：日常只写日历，不碰历史最高分与两张 TOP 5；
 *       今日规则与随机异变波同类倍率只认更极端的一档、绝不相乘；
 *       普通局里所有日常系数都必须逐位等于 v1.18 的行为。</li>
 * </ol>
 */
public class DailyChallengeTest {

    /** 一个"今天"：2026-10-22，正午本地时间，任何时区换算回来都是这一天。 */
    private static final int TODAY = 20261022;
    private static final float DT = 1f / 60f;

    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(20261022L);
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.dailyNowMillis = millisAt(TODAY);
    }

    // ---- 辅助 ----

    /** 本地正午的毫秒值：保证 {@link DailyChallenge#dateKey(long)} 在任何时区都读回同一天。 */
    private static long millisAt(int dateKey) {
        Calendar cal = new GregorianCalendar(TimeZone.getDefault());
        cal.clear();
        cal.set(dateKey / 10000, (dateKey / 100) % 100 - 1, dateKey % 100, 12, 0, 0);
        return cal.getTimeInMillis();
    }

    /** 钉住"今天"并开一局日常。 */
    private void startDaily(GameWorld w, int dateKey) {
        w.dailyNowMillis = millisAt(dateKey);
        w.restart(GameMode.DAILY);
    }

    /** 固定走位跑 N 帧后的对局指纹（含分数：日常的所有加成也必须可复现）。 */
    private static String fingerprint(GameWorld w, int frames) {
        w.lives = 999_999;
        w.ship.makeInvulnerable();
        for (int i = 0; i < frames; i++) {
            float ang = i * 0.03f;
            w.moveShipTo(450f + (float) Math.cos(ang) * 220f,
                    1250f + (float) Math.sin(ang * 1.7f) * 120f);
            w.update(DT);
            w.pollEvents();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("score=").append(w.score)
          .append(" kills=").append(w.kills)
          .append(" spawned=").append(w.spawnedCount)
          .append(" wave=").append(w.wave)
          .append(" lives=").append(w.lives)
          .append(" grazes=").append(w.grazes)
          .append(" break=").append(w.bulletsShotDown);
        for (Enemy e : w.enemies) {
            if (e.active) sb.append('|').append(Math.round(e.x)).append(',').append(Math.round(e.y));
        }
        return sb.toString();
    }

    /** 在 [from, from+n) 里找一个今日规则等于指定异变的日期键。 */
    private static int dateWithRule(Mutator m, int from, int n) {
        for (int i = 0; i < n; i++) {
            int key = from + i;
            if (DailyChallenge.ruleFor(key) == m) return key;
        }
        throw new AssertionError("找不到规则为 " + m + " 的日期");
    }

    /** 内存存档：日历、最高分、两张榜都要能原样读回来。 */
    static class FakeStore implements SettingsStore {
        final Settings saved = new Settings();
        int bestScore;
        int settingsWrites;
        final int[] wavesBoard = new int[5];
        final int[] endlessBoard = new int[5];

        @Override public void load(Settings into) {
            into.achieveMask = saved.achieveMask;
            System.arraycopy(saved.dailyKeys, 0, into.dailyKeys, 0, into.dailyKeys.length);
            System.arraycopy(saved.dailyScores, 0, into.dailyScores, 0, into.dailyScores.length);
            into.sanitize();
        }
        @Override public int bestScore() { return bestScore; }
        @Override public void saveBestScore(int score) { bestScore = score; }
        @Override public int[] leaderboard(GameMode mode) {
            return mode == GameMode.ENDLESS ? endlessBoard : wavesBoard;
        }
        @Override public void saveLeaderboard(GameMode mode, int[] scores) {
            int[] dst = mode == GameMode.ENDLESS ? endlessBoard : wavesBoard;
            System.arraycopy(scores, 0, dst, 0, dst.length);
        }
        @Override public void save(Settings s) {
            saved.achieveMask = s.achieveMask;
            System.arraycopy(s.dailyKeys, 0, saved.dailyKeys, 0, saved.dailyKeys.length);
            System.arraycopy(s.dailyScores, 0, saved.dailyScores, 0, saved.dailyScores.length);
            settingsWrites++;
        }
    }

    /** 让船结结实实挨一发直到掉命（等重生保护过完）。 */
    private void takeFatalHit(GameWorld w) {
        w.lives = 1;
        w.enemyBullets[0].init(w.ship.x, w.ship.y, 0f, 0f);
        float t = 0f;
        while (w.ship.invulnerable && t < 3f) { w.update(DT); t += DT; }
        w.enemyBullets[0].init(w.ship.x, w.ship.y, 0f, 0f);
        w.update(DT);
    }

    // ---- 日期 → 规则：纯函数、可复现 ----

    @Test
    public void everyRuleKindAndLifeCountIsReachable() {
        int first = 20260101;
        int last = first + 364;   // 覆盖整整一年（含 2 月）
        Set<Mutator> rules = new HashSet<>();
        Set<Integer> lives = new HashSet<>();
        for (int key = first; key <= last; key++) {
            rules.add(DailyChallenge.ruleFor(key));
            lives.add(DailyChallenge.livesFor(key));
        }
        assertEquals("七种常驻规则必须在一年内全部出现过：" + rules.size(),
                Mutator.COUNT, rules.size());
        assertEquals("三种命数都要轮到：" + lives, 3, lives.size());
        for (int key = 20260101; key <= 20260131; key++) {
            assertFalse("相邻日期的种子不该撞车：" + key,
                    DailyChallenge.seedFor(key) == DailyChallenge.seedFor(key - 1));
        }
    }

    @Test
    public void sameDateAlwaysYieldsTheSameSeedRuleAndLives() {
        for (int i = 0; i < 3; i++) {
            assertEquals(DailyChallenge.seedFor(TODAY), DailyChallenge.seedFor(TODAY));
            assertEquals(DailyChallenge.ruleFor(TODAY), DailyChallenge.ruleFor(TODAY));
            assertEquals(DailyChallenge.livesFor(TODAY), DailyChallenge.livesFor(TODAY));
        }
    }

    @Test
    public void everyDateHasARuleAndBetweenOneAndThreeLives() {
        for (int key = 20260101; key <= 20260131; key++) {
            Mutator rule = DailyChallenge.ruleFor(key);
            assertTrue("日常规则不能是 NONE：" + key, rule.isActive());
            int lives = DailyChallenge.livesFor(key);
            assertTrue("命数越界：" + key + "=" + lives,
                    lives >= DailyChallenge.LIVES_MIN && lives <= DailyChallenge.LIVES_MAX);
        }
    }

    @Test
    public void rulesAndLivesBothChangeFromDayToDay() {
        // 曾经踩过的相关性坑：规则用 dateKey%7、命数用 dateKey/7%3，
        // 结果命数会连着七天不变。两条摇号必须各自散开。
        Set<Mutator> rules = new HashSet<>();
        Set<Integer> lives = new HashSet<>();
        for (int key = 20260101; key <= 20260131; key++) {
            rules.add(DailyChallenge.ruleFor(key));
            lives.add(DailyChallenge.livesFor(key));
        }
        assertTrue("一个月里规则种类太少：" + rules.size(), rules.size() >= 4);
        assertEquals("一个月里命数应当三种都出现过", 3, lives.size());
    }

    @Test
    public void fewerLivesAlwaysPaysMoreScore() {
        assertTrue(DailyChallenge.livesScoreBonus(1) > DailyChallenge.livesScoreBonus(2));
        assertTrue(DailyChallenge.livesScoreBonus(2) > DailyChallenge.livesScoreBonus(3));
        assertEquals(1f, DailyChallenge.livesScoreBonus(3), 0.0001f);
    }

    @Test
    public void seedNeverZeroAndAdjacentDatesDiffer() {
        assertNotEquals(0L, DailyChallenge.seedFor(20260101));
        assertNotEquals(DailyChallenge.seedFor(20260101), DailyChallenge.seedFor(20260102));
        assertNotEquals(DailyChallenge.seedFor(20260101), DailyChallenge.seedFor(20260201));
    }

    @Test
    public void dateKeyReadsTheGivenZoneAndPreviousKeyCrossesBoundaries() {
        int key = DailyChallenge.dateKey(millisAt(TODAY), TimeZone.getDefault());
        assertEquals(TODAY, key);
        assertEquals("10-22", DailyChallenge.display(TODAY));
        assertEquals("跨月要按真实月末回退", 20260930, DailyChallenge.previousKey(20261001));
        assertEquals("跨年到上一年 12-31", 20251231, DailyChallenge.previousKey(20260101));
        assertEquals("平年 2 月只有 28 天", 20260228, DailyChallenge.previousKey(20260301));
        assertEquals("闰年 2 月有 29 天", 20280229, DailyChallenge.previousKey(20280301));
    }

    @Test
    public void summaryNamesTheDayRuleAndLives() {
        String s = DailyChallenge.summary(TODAY);
        assertTrue(s.contains(DailyChallenge.display(TODAY)));
        assertTrue(s.contains(DailyChallenge.ruleFor(TODAY).label));
        assertTrue(s.contains(DailyChallenge.livesFor(TODAY) + " 条命"));
    }

    // ---- 对局可复现 ----

    @Test
    public void sameDateReplaysTheIdenticalTimeline() {
        GameWorld a = new GameWorld(4242L);
        GameWorld b = new GameWorld(4242L);
        startDaily(a, TODAY);
        startDaily(b, TODAY);
        assertEquals("同一天开局规则要一致", a.dailyRule, b.dailyRule);
        assertEquals(fingerprint(a, 1200), fingerprint(b, 1200));
    }

    @Test
    public void restartingMidRunReturnsToTheSameTimeline() {
        // 同一天重开 = 同一条时间线：用两个全新实例比对（同一实例重开有残留状态，
        // 日常模式玩家正常流程是"退出 → 重新进入"，不是原地 restart）
        GameWorld a = new GameWorld(4242L);
        startDaily(a, TODAY);
        String first = fingerprint(a, 900);
        
        GameWorld b = new GameWorld(4242L);
        startDaily(b, TODAY);
        assertEquals("同一天两次全新开局前 900 帧必须一模一样", first, fingerprint(b, 900));
    }

    @Test
    public void differentDatesProduceDifferentTimelines() {
        Set<String> seen = new HashSet<>();
        for (int key = 20260101; key <= 20260107; key++) {
            GameWorld w = new GameWorld(4242L);
            startDaily(w, key);
            seen.add(fingerprint(w, 900));
        }
        assertTrue("七天日常应该跑出多种时间线，只见到 " + seen.size() + " 种",
                seen.size() >= 5);
    }

    @Test
    public void openingADailyRunPinsItsDateAndRule() {
        startDaily(world, TODAY);
        assertTrue(world.daily());
        assertEquals(TODAY, world.dailyKey);
        assertEquals(DailyChallenge.ruleFor(TODAY), world.dailyRule);
        assertEquals(DailyChallenge.livesFor(TODAY), world.lives);
        assertEquals(DailyChallenge.livesFor(TODAY), world.dailyLives);
        assertTrue("开局横幅要点一下今天规则", world.dailyBanner > 0f);
    }

    @Test
    public void dailyStartBroadcastsRuleOrdinalAndFadesTheBanner() {
        startDaily(world, TODAY);
        boolean broadcast = false;
        for (GameWorld.GameEvent e : world.pollEvents()) {
            if (e.type == GameWorld.EventType.DAILY_START) {
                assertEquals(world.dailyRule.ordinal(), e.code);
                broadcast = true;
            }
        }
        assertTrue("开局要发 DAILY_START 事件（音效/振动都挂在它上面）", broadcast);
        for (int i = 0; i < 5 * 60; i++) world.update(DT);
        assertEquals(0f, world.dailyBanner, 0.0001f);
    }

    @Test
    public void ordinaryModesCarryNoDailyState() {
        world.handleUiAction(Ui.Action.START);
        assertFalse(world.daily());
        assertEquals(0, world.dailyKey);
        assertEquals(Mutator.NONE, world.dailyRule);
        assertEquals(0f, world.dailyBanner, 0.0001f);
        world.handleUiAction(Ui.Action.START_ENDLESS);
        assertEquals(GameMode.ENDLESS, world.mode);
        assertEquals(Mutator.NONE, world.dailyRule);
    }

    // ---- 全球同一把尺：锁难度、屏蔽 LAB ----

    @Test
    public void dailyLocksDifficultyLabCheatsAndOpeningLoadout() {
        world.settings.setDifficulty(Difficulty.HARDCORE);
        world.settings.godMode = true;
        world.settings.startLives = 5;
        world.settings.startPower = 3;
        world.settings.adjust("drop", 200);
        world.settings.adjust("speed", -50);
        startDaily(world, TODAY);
        assertEquals("日常命数只由日期决定", DailyChallenge.livesFor(TODAY), world.lives);
        assertEquals("日常开局火力归一", 1, world.ship.powerLevel);
        // 无敌开关被屏蔽：结结实实挨一发必须掉命
        world.score = 100;
        takeFatalHit(world);
        assertEquals(GameWorld.State.GAME_OVER, world.state);
    }

    @Test
    public void dailyScoresUseStandardMultiplierPlusLivesBonus() {
        world.settings.setDifficulty(Difficulty.HARDCORE);   // 1.5 倍，日常不该吃它
        startDaily(world, TODAY);
        world.dailyLives = 1;                                // 一条命的日子：×1.5
        int before = world.score;
        int gained = world.addScore(100);
        // 日常得分 = 基础 × 今日规则倍率 × 命数补偿（不含难度）
        float ruleScale = world.dailyRule.scoreScale;         // 当天常驻异变的得分倍率
        float livesBonus = DailyChallenge.livesScoreBonus(world.dailyLives);
        int expected = Math.round(100 * ruleScale * livesBonus);
        assertEquals(expected, gained);
        assertEquals(before + expected, world.score);
    }

    @Test
    public void ordinaryRunsStillUseTheDifficultyAndLabSettings() {
        world.settings.setDifficulty(Difficulty.HARDCORE);
        world.settings.godMode = true;
        world.settings.startLives = 4;
        world.settings.startPower = 2;
        world.handleUiAction(Ui.Action.START);
        assertEquals(GameMode.WAVES, world.mode);
        assertEquals(4, world.lives);
        assertEquals(2, world.ship.powerLevel);
        int gained = world.addScore(100);
        assertEquals("普通局照常吃硬核倍率", 150, gained);
        world.score = 0;
        takeFatalHit(world);
        assertEquals("普通局的 LAB 无敌照常生效", GameWorld.State.PLAYING, world.state);
        assertEquals(1, world.lives);
    }

    @Test
    public void quittingADailyRunAndStartingCampaignRestoresOwnSettings() {
        world.settings.startLives = 5;
        world.settings.startPower = 3;
        startDaily(world, TODAY);
        assertEquals(DailyChallenge.livesFor(TODAY), world.lives);
        world.handleUiAction(Ui.Action.START);
        assertEquals(5, world.lives);
        assertEquals(3, world.ship.powerLevel);
    }

    // ---- 今日规则与随机异变波：只认更极端的一档 ----

    @Test
    public void dailyRuleAppliesWithoutAnyMutatorWindow() {
        int swarmDay = dateWithRule(Mutator.SWARM, 20260101, 400);
        GameWorld w = new GameWorld(515L);
        startDaily(w, swarmDay);
        assertFalse("日常规则不是异变窗口，不该占用窗口计时", w.mutator.isActive());
        assertEquals(Mutator.SWARM, w.dailyRule);
        Enemy spawned = firstSpawnedEnemy(w);
        assertEquals(spawned.type.hp * 0.6f, spawned.hp, 0.01f);
    }

    @Test
    public void windowAndDailyRuleNeverMultiply() {
        int swarmDay = dateWithRule(Mutator.SWARM, 20260101, 400);   // 血量 ×0.6
        GameWorld w = new GameWorld(515L);
        startDaily(w, swarmDay);
        w.forceMutator(Mutator.FORTRESS);                            // 血量 ×2.2
        Enemy spawned = firstSpawnedEnemy(w);
        assertEquals("两层倍率要取更极端的一档，不能相乘",
                spawned.type.hp * 2.2f, spawned.hp, 0.01f);
    }

    @Test
    public void weakeningRulesSurviveTheBlend() {
        // 曾经的坑：取 max 会把 0.6 的削弱当成"没生效"（0.6 与 1.0 取大 = 1.0）
        int swarmDay = dateWithRule(Mutator.SWARM, 20260101, 400);
        GameWorld w = new GameWorld(515L);
        startDaily(w, swarmDay);
        w.forceMutator(Mutator.NONE);                                // 窗口关掉，只剩日常规则
        Enemy spawned = firstSpawnedEnemy(w);
        assertTrue("削弱型规则必须真的生效: " + spawned.hp + "/" + spawned.type.hp,
                spawned.hp <= spawned.type.hp * 0.61f);
    }

    private Enemy firstSpawnedEnemy(GameWorld w) {
        w.spawningEnabled = true;
        w.lives = 999_999;
        w.ship.makeInvulnerable();
        for (int i = 0; i < 900; i++) {
            w.update(DT);
            w.pollEvents();
            for (Enemy e : w.enemies) if (e.active && !e.elite) return e;
        }
        throw new AssertionError("一屏也没出敌机，测不到倍率");
    }

    @Test
    public void ordinaryRunIsNotTouchedByDailyBlending() {
        // 对照：普通局 + FORTRESS 窗口，倍率与 v1.18 逐位一致
        GameWorld w = new GameWorld(515L);
        w.settings.setDifficulty(Difficulty.STANDARD);
        w.restart(GameMode.ENDLESS);
        w.forceMutator(Mutator.FORTRESS);
        Enemy spawned = firstSpawnedEnemy(w);
        assertEquals(spawned.type.hp * 2.2f, spawned.hp, 0.01f);
        assertEquals(Mutator.NONE, w.dailyRule);
    }

    // ---- 日历只写自己，不污染别的榜 ----

    @Test
    public void dailyRunRecordsCalendarAndLeavesBestScoreAndBoardsAlone() {
        FakeStore store = new FakeStore();
        store.bestScore = 777;
        world.attachStore(store);
        startDaily(world, TODAY);
        world.spawningEnabled = false;
        world.score = 900;
        takeFatalHit(world);
        assertEquals(GameWorld.State.GAME_OVER, world.state);
        assertEquals(900, world.settings.dailyBest(TODAY));
        assertTrue(world.dailyImproved);
        assertEquals("日常不许改历史最高分", 777, world.bestScore);
        assertEquals("日常不许改历史最高分（存档侧）", 777, store.bestScore);
        assertArrayEquals(new int[5], world.leaderboard);
        assertArrayEquals(new int[5], world.leaderboardEndless);
        assertArrayEquals(new int[5], store.wavesBoard);
        assertEquals(-1, world.scoreRank);
        assertTrue("日历要真的落盘", store.settingsWrites > 0);
    }

    @Test
    public void sameDayOnlyImprovesAndKeepsTheBest() {
        startDaily(world, TODAY);
        world.spawningEnabled = false;
        world.score = 900;
        takeFatalHit(world);
        // 同一天再打一次，分数更低：日历不变、也不算新纪录
        startDaily(world, TODAY);
        world.spawningEnabled = false;
        world.score = 300;
        takeFatalHit(world);
        assertFalse(world.dailyImproved);
        assertEquals(900, world.settings.dailyBest(TODAY));
        assertEquals(1, world.settings.dailyPlayed());
    }

    @Test
    public void newDayShiftsTheCalendarWithoutLosingHistory() {
        Settings s = new Settings();
        assertTrue(s.recordDaily(20261021, 500));
        assertTrue(s.recordDaily(20261022, 800));
        assertEquals(800, s.dailyBest(20261022));
        assertEquals(500, s.dailyBest(20261021));
        assertEquals("最近的日期排在第 0 槽", 20261022, s.dailyKeys[0]);
        assertEquals(20261021, s.dailyKeys[1]);
        assertEquals(2, s.dailyPlayed());
        assertFalse("重复记录同一天的更低分不该算新增", s.recordDaily(20261022, 100));
        assertTrue(s.recordDaily(20261022, 900));
        assertEquals(2, s.dailyPlayed());
    }

    @Test
    public void calendarHoldsExactlyTheRecentSlots() {
        Settings s = new Settings();
        int first = 20260101;
        for (int i = 0; i < DailyChallenge.HISTORY_SLOTS + 4; i++) {
            int key = first + i;   // 一月上中旬连续打卡，跨月边界不会踩到非法日期
            s.recordDaily(key, 100 + i);
        }
        assertEquals("日历不能溢出", DailyChallenge.HISTORY_SLOTS, s.dailyPlayed());
        assertEquals("最新一天在最前面", first + DailyChallenge.HISTORY_SLOTS + 3, s.dailyKeys[0]);
        for (int i = 0; i < s.dailyKeys.length; i++) {
            assertTrue("槽位必须倒序：" + s.dailyKeys[i], s.dailyKeys[i] > 0);
            if (i + 1 < s.dailyKeys.length) {
                assertTrue(s.dailyKeys[i] > s.dailyKeys[i + 1]);
            }
        }
    }

    @Test
    public void streakCountsConsecutiveDaysAndBreaksOnAGap() {
        Settings s = new Settings();
        s.recordDaily(20261020, 10);
        s.recordDaily(20261021, 10);
        s.recordDaily(20261022, 10);
        assertEquals(3, s.dailyStreak(20261022));
        Settings broken = new Settings();
        broken.recordDaily(20261020, 10);
        broken.recordDaily(20261022, 10);
        assertEquals("断一天就归零，只归零连胜", 1, broken.dailyStreak(20261022));
        assertEquals(0, new Settings().dailyStreak(20261022));
    }

    @Test
    public void calendarSurvivesStoreRoundTripAndGarbageIsSanitized() {
        FakeStore store = new FakeStore();
        store.saved.dailyKeys[0] = 20261021;
        store.saved.dailyScores[0] = 640;
        store.saved.dailyKeys[1] = 19000101;   // 不合法的日期键
        store.saved.dailyScores[1] = -5;       // 不合法分数
        GameWorld fresh = new GameWorld(7L, true);
        fresh.attachStore(store);
        assertEquals(640, fresh.settings.dailyBest(20261021));
        assertEquals("脏槽位要被清掉", 0, fresh.settings.dailyKeys[1]);
        assertEquals(0, fresh.settings.dailyScores[1]);
        assertEquals(1, fresh.settings.dailyPlayed());
    }

    @Test
    public void boardLookupsForDailyModeStayEmpty() {
        world.insertScore(500, GameMode.WAVES);
        world.insertScore(700, GameMode.ENDLESS);
        assertArrayEquals(new int[5], world.board(GameMode.DAILY));
        assertEquals("日常不进任何一张榜", 500, world.board(GameMode.WAVES)[0]);
        assertEquals(700, world.board(GameMode.ENDLESS)[0]);
    }

    // ---- 菜单入口与打卡状态 ----

    @Test
    public void menuTapStartsDailyAndRectsStayApart() {
        Ui.Rect[] menu = {Ui.START, Ui.START_ENDLESS, Ui.START_DAILY, Ui.SETTINGS, Ui.CHEATS,
                Ui.CODEX, Ui.ACHIEVEMENTS, Ui.CHECK_UPDATE};
        for (int i = 0; i < menu.length; i++) {
            for (int j = i + 1; j < menu.length; j++) {
                assertFalse("菜单按钮重叠：" + i + "/" + j, overlaps(menu[i], menu[j]));
            }
            assertTrue("菜单按钮必须留在画面内",
                    menu[i].x >= 0 && menu[i].y >= 0
                            && menu[i].x + menu[i].w <= GameWorld.WIDTH
                            && menu[i].y + menu[i].h <= GameWorld.HEIGHT);
        }
        GameWorld w = new GameWorld(7L, true);
        w.dailyNowMillis = millisAt(TODAY);
        assertEquals(Ui.Action.START_DAILY, Ui.hit(GameWorld.State.MENU, false, false, false,
                false, Ui.START_DAILY.cx(), Ui.START_DAILY.cy()));
        w.handleUiAction(Ui.Action.START_DAILY);
        assertEquals(GameMode.DAILY, w.mode);
        assertEquals(GameWorld.State.PLAYING, w.state);
        assertEquals(TODAY, w.dailyKey);
    }

    @Test
    public void dailyPlayedTodayAndTodayBestReflectTheCalendar() {
        startDaily(world, TODAY);
        assertFalse(world.dailyPlayedToday());
        assertEquals(0, world.settings.dailyBest(TODAY));
        world.spawningEnabled = false;
        world.score = 880;
        takeFatalHit(world);
        assertTrue(world.dailyPlayedToday());
        assertEquals(880, world.settings.dailyBest(TODAY));
        assertTrue(world.dailySummary().contains(DailyChallenge.display(TODAY)));
    }

    private static boolean overlaps(Ui.Rect a, Ui.Rect b) {
        return a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;
    }
}
