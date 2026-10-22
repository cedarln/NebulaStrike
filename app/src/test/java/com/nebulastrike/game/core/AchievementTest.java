package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * v1.18 迭代 8：成就系统。
 *
 * <p>这轮的契约有三条，测试逐条钉住：
 * <ol>
 *   <li><b>只增不减</b>：单局取历史最佳、生涯按增量累加，坠机 / 挨打 / 重开 / 换难度 /
 *       开 LAB 都不会让进度或已解锁的成就倒退；解锁奖励的分数只加不减。</li>
 *   <li><b>门槛必须可达</b>：每条成就都跑一次"真实读数 → 结算 → 解锁"的闭环，
 *       防止写出一条永远无法达成的成就，或者引用了一个根本没接线的统计口径。</li>
 *   <li><b>账不能重复记</b>：同一局无论结算几次（暂停、退出、坠机都会结算）累计值都只加一次。</li>
 * </ol>
 */
public class AchievementTest {

    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(20261201L);
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.lives = 999_999;
        world.restart(GameMode.ENDLESS);
        world.spawningEnabled = false;
    }

    // ---- 辅助 ----

    /** 内存版存档：统计落盘次数，并能把成就字段原样读回来。 */
    static class FakeStore implements SettingsStore {
        final Settings saved = new Settings();
        int settingsWrites;

        @Override public void load(Settings into) {
            into.achieveMask = saved.achieveMask;
            copy(saved.achieveBest, into.achieveBest);
            copy(saved.achieveTotal, into.achieveTotal);
            into.sanitize();
        }
        @Override public int bestScore() { return 0; }
        @Override public void saveBestScore(int score) { }
        @Override public int[] leaderboard(GameMode mode) { return new int[5]; }
        @Override public void saveLeaderboard(GameMode mode, int[] scores) { }
        @Override public void save(Settings s) {
            saved.achieveMask = s.achieveMask;
            copy(s.achieveBest, saved.achieveBest);
            copy(s.achieveTotal, saved.achieveTotal);
            settingsWrites++;
        }
        private static void copy(int[] from, int[] to) {
            System.arraycopy(from, 0, to, 0, Math.min(from.length, to.length));
        }
    }

    /** 把某个统计口径的"本局读数"开到指定值（走的就是对局里那些真实字段）。 */
    private void drive(Achievement.Metric m, int value) {
        switch (m) {
            case KILLS: world.kills = value; break;
            case GRAZE: world.grazes = value; break;
            case PARRY: world.parries = value; break;
            case BREAK: world.bulletsShotDown = value; break;
            case COMBO: world.maxCombo = value; break;
            case TIME: world.timeAlive = value; break;
            case SCORE: world.score = value; break;
            case ACC:
                world.bulletsFired = 200;
                world.shotsHit = 200 * value / 100;
                break;
            case CLEAN_KILLS: world.hitsTaken = 0; world.kills = value; break;
            case GRAVITY: world.gravityAbsorbed = value; break;
            case ULT_TIER: world.ult.bestTier = value; break;
            case ULT_CONVERT: world.ultConverted = value; break;
            case ULT_CASTS: world.ult.casts = value; break;
            case BOSSES: world.bossesDefeated = value; break;
            case RUSH: world.rushesTriggered = value; break;
            case SPOT: world.miragesSpotted = value; break;
            case MUTATORS: world.mutatorsSeen = value; break;
            case DASH: world.ship.dashesUsed = value; break;
            case RELIC: world.relicsGained = value; break;
            case FUSION: world.fusions = value; break;
            case MAGNET_KILLS:
                world.typeKills[EnemyType.MAGNETON.ordinal()] = value;
                break;
            case QUEEN_KILLS:
                world.typeKills[EnemyType.QUEEN.ordinal()] = value;
                break;
            default: throw new AssertionError("未接线的统计口径：" + m);
        }
    }

    private boolean hasEvent(GameWorld.EventType type, int code) {
        for (GameWorld.GameEvent e : world.pollEvents()) {
            if (e.type == type && e.code == code) return true;
        }
        return false;
    }

    /**
     * 换一份干净成就账本并重开一局。Settings 是 GameWorld 的 final 字段，
     * 所以这里只清账本，然后靠 restart 把本局读数归零。
     */
    private Settings cleanLedger() {
        Settings s = world.settings;
        s.achieveMask = 0L;
        java.util.Arrays.fill(s.achieveBest, 0);
        java.util.Arrays.fill(s.achieveTotal, 0);
        world.restart(GameMode.ENDLESS);
        world.lives = 999_999;
        world.spawningEnabled = false;
        return s;
    }

    // ---- 定义表 ----

    @Test
    public void achievementTableIsConsistent() {
        assertTrue("位图放不下就要换存储结构", Achievement.COUNT <= Achievement.MAX_FOR_BITMAP);
        Set<String> labels = new HashSet<>();
        for (Achievement a : Achievement.values()) {
            assertNotNull(a.label());
            assertNotNull(a.desc());
            assertTrue("门槛必须为正：" + a.label(), a.target > 0);
            assertNotNull("每条成就都要有统计口径：" + a.label(), a.metric);
            assertTrue("解锁位图必须对得上枚举序号", a.unlockedIn(a.bit()));
            assertTrue("不能一条就达成：" + a.label(), a.target > 1 || a == Achievement.FIRST_BLOOD);
            assertTrue("名号重复：" + a.label(), labels.add(a.label()));
        }
    }

    @Test
    public void targetsDifferWithinSameMetricAndScope() {
        for (Achievement.Scope scope : Achievement.Scope.values()) {
            for (Achievement.Metric m : Achievement.Metric.values()) {
                Set<Integer> targets = new HashSet<>();
                for (Achievement a : Achievement.values()) {
                    if (a.scope != scope || a.metric != m) continue;
                    assertTrue("同口径同范围门槛重复：" + m, targets.add(a.target));
                }
            }
        }
    }

    @Test
    public void rankLadderEndsAtLegendAndStartsUnlisted() {
        assertEquals("未入册", Achievement.rank(0));
        assertEquals("星海传说", Achievement.rank(Achievement.COUNT));
        String previous = Achievement.rank(0);
        int changes = 0;
        for (int i = 1; i <= Achievement.COUNT; i++) {
            String now = Achievement.rank(i);
            assertFalse("称号不能倒退", now.equals("未入册"));
            if (!now.equals(previous)) changes++;
            previous = now;
        }
        assertTrue("军衔至少分七档", changes >= 7);
    }

    // ---- 可达性：每条成就都真的能被达成 ----

    @Test
    public void everySingleRunAchievementIsReachableInOneRun() {
        for (Achievement a : Achievement.values()) {
            if (a.scope != Achievement.Scope.RUN) continue;
            Settings s = new Settings();
            Achievements run = new Achievements(null);
            int[] metrics = new int[Achievement.Metric.COUNT];
            metrics[a.metric.ordinal()] = a.target;
            run.commit(s, metrics);
            assertTrue("单局成就无法达成：" + a.label(), a.unlockedIn(s.achieveMask));
        }
    }

    @Test
    public void everyCareerAchievementIsReachableAcrossRuns() {
        for (Achievement a : Achievement.values()) {
            if (a.scope != Achievement.Scope.CUM) continue;
            Settings s = new Settings();
            Achievements run = new Achievements(null);
            int[] metrics = new int[Achievement.Metric.COUNT];
            // 分五局打满：验证"增量入账"在跨局时同样能凑够门槛
            int perRun = (a.target + 4) / 5;
            for (int i = 0; i < 5; i++) {
                metrics[a.metric.ordinal()] = perRun;
                run.commit(s, metrics);
                run.beginRun();
            }
            assertTrue("生涯成就无法达成：" + a.label(), a.unlockedIn(s.achieveMask));
            assertEquals("生涯账本应恰好累到 " + a.label(), perRun * 5,
                    s.achieveTotal[a.metric.ordinal()]);
        }
    }

    @Test
    public void everyMetricIsActuallyWiredIntoTheRunSnapshot() {
        // 逐个口径把读数推到门槛，走 GameWorld 的真实结算通道，
        // 任何"填了 metric 但 GameWorld 没接线"的成就都会在这里暴露
        for (Achievement.Metric m : Achievement.Metric.values()) {
            Settings s = cleanLedger();
            drive(m, 1_000_000);
            world.commitAchievements();
            assertTrue("统计口径没接进对局读数：" + m,
                    s.achieveBest[m.ordinal()] > 0 || s.achieveTotal[m.ordinal()] > 0);
        }
    }

    // ---- 解锁行为 ----

    @Test
    public void firstKillUnlocksImmediatelyWithScoreAndEvent() {
        int before = world.score;
        world.kills = 1;
        world.commitAchievements();
        assertTrue(Achievement.FIRST_BLOOD.unlockedIn(world.settings.achieveMask));
        assertEquals("解锁奖励只发一次", before + GameWorld.ACHIEVEMENT_SCORE, world.score);
        assertEquals(GameWorld.ACHIEVEMENT_SCORE, world.achieveScore);
        assertEquals(1, world.achievesThisRun);
        assertTrue("要发解锁事件", hasEvent(GameWorld.EventType.ACHIEVEMENT_UNLOCKED,
                Achievement.FIRST_BLOOD.ordinal()));
        assertEquals(Achievement.FIRST_BLOOD, world.achieveBannerType);
        assertTrue(world.achieveBanner > 0f);
    }

    @Test
    public void celebrationScoreDoesNotShiftWavePacing() {
        // wave 每 500 分进一档，并决定 Boss 什么时候来。
        // 成就的庆祝分是白送的，算进节拍就等于"解锁成就 → Boss 提前 → 对局变难"，
        // 违反新系统不得削弱既有体验的约定，所以这条要单独钉住。
        world.restart(GameMode.WAVES);
        world.spawningEnabled = false;
        world.score = 400;
        world.wave = 1;
        world.kills = 1;
        world.commitAchievements();
        assertTrue("解锁确实发了分", world.achieveScore > 0);
        assertTrue(world.score > 500);
        for (int i = 0; i < 8; i++) world.update(1f / 60f);
        assertEquals("庆祝分不该把 Boss 节拍提前", 1, world.wave);
        // 反过来：真实打出来的分数照常推进节拍
        world.score += 600;
        world.update(1f / 60f);
        assertEquals(2, world.wave);
    }

    @Test
    public void celebrationLeavesTheSimulationItselfUntouched() {
        // 同一颗种子、同一套走位，唯一区别是"这一局会不会触发一次解锁庆祝"。
        // 庆祝的粒子如果用对局随机源取角度，后面的敌机排布就会被推歪——
        // 成就只能往对局里加东西，不能改掉对局本身的进程。
        GameWorld withCelebration = new GameWorld(77777L);
        String a = playIdenticalRun(withCelebration, false);
        GameWorld alreadyEarned = new GameWorld(77777L);
        String b = playIdenticalRun(alreadyEarned, true);
        assertEquals("解锁庆祝不该改变对局进程", b, a);
        assertTrue("对照组确实触发过解锁", withCelebration.achievesThisRun > 0);
    }

    /** 固定走位跑 30 秒，返回不含分数的对局指纹；preEarned=true 时把首次击坠提前记为已解锁。 */
    private String playIdenticalRun(GameWorld w, boolean preEarned) {
        w.settings.achieveMask = 0L;
        if (preEarned) w.settings.achieveMask |= Achievement.FIRST_BLOOD.bit();
        w.restart(GameMode.ENDLESS);
        w.lives = 999_999;
        w.ship.makeInvulnerable();
        for (int i = 0; i < 30 * 60; i++) {
            float ang = i * 0.03f;
            w.moveShipTo(450f + (float) Math.cos(ang) * 220f,
                    1250f + (float) Math.sin(ang * 1.7f) * 120f);
            w.update(1f / 60f);
            w.pollEvents();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("kills=").append(w.kills)
          .append(" spawned=").append(w.spawnedCount)
          .append(" wave=").append(w.wave)
          .append(" grazes=").append(w.grazes)
          .append(" break=").append(w.bulletsShotDown);
        for (Enemy e : w.enemies) {
            if (e.active) sb.append('|').append(Math.round(e.x)).append(',').append(Math.round(e.y));
        }
        return sb.toString();
    }

    @Test
    public void alreadyUnlockedAchievementNeverPaysAgain() {
        world.kills = 1;
        world.commitAchievements();
        world.pollEvents();          // 收掉首次解锁的那条广播事件
        int score = world.score;
        int unlocked = world.achievesThisRun;
        for (int i = 0; i < 5; i++) {
            world.kills += 10;
            world.commitAchievements();
        }
        assertEquals("重复结算不该再发分", score, world.score);
        assertEquals(unlocked, world.achievesThisRun);
        assertFalse(hasEvent(GameWorld.EventType.ACHIEVEMENT_UNLOCKED,
                Achievement.FIRST_BLOOD.ordinal()));
    }

    @Test
    public void unlockIsBroadcastOnlyOnceEvenWhenSeveralLandTogether() {
        Settings s = cleanLedger();
        world.hitsTaken = 1;         // 别把"无伤击坠"也算进来，这一条只测一次多解锁
        world.kills = 5000;          // 同时满足 初次击坠 / 百人斩 / 星海清扫者
        world.commitAchievements();
        int paid = 0;
        for (Achievement a : Achievement.values()) {
            if (a.unlockedIn(s.achieveMask)) paid++;
        }
        assertEquals(3, paid);
        assertEquals("每解锁一条发一笔分", 3 * GameWorld.ACHIEVEMENT_SCORE, world.achieveScore);
    }

    @Test
    public void judgmentIsThrottledButCommitIsImmediate() {
        world.kills = 1;
        world.update(1f / 60f);      // 第一帧远不到 0.5 秒判定窗口
        assertFalse("对局内判定应当节流", Achievement.FIRST_BLOOD
                .unlockedIn(world.settings.achieveMask));
        for (int i = 0; i < 40; i++) world.update(1f / 60f);
        assertTrue("过完判定间隔应自动结算", Achievement.FIRST_BLOOD
                .unlockedIn(world.settings.achieveMask));
    }

    @Test
    public void achievementBannerFadesOutOnItsOwn() {
        world.kills = 1;
        world.commitAchievements();
        assertTrue(world.achieveBanner > 0f);
        for (int i = 0; i < 240; i++) world.update(1f / 60f);
        assertEquals(0f, world.achieveBanner, 0.001f);
    }

    @Test
    public void careerTotalsUseDeltasSoRepeatedCommitsDoNotDoubleCount() {
        Settings s = cleanLedger();
        for (int i = 0; i < 6; i++) {
            world.kills += 7;
            world.commitAchievements();   // 暂停 / 退出 / 反复结算都走同一条路
        }
        assertEquals("六次结算共 42 次击坠应当只入账一次", 42,
                s.achieveTotal[Achievement.Metric.KILLS.ordinal()]);
    }

    @Test
    public void restartKeepsCareerProgressButClearsRunCounters() {
        world.kills = 60;
        world.grazes = 30;
        world.commitAchievements();
        Settings s = world.settings;
        world.restart(GameMode.WAVES);
        world.spawningEnabled = false;
        assertEquals(60, s.achieveTotal[Achievement.Metric.KILLS.ordinal()]);
        assertEquals(60, s.achieveBest[Achievement.Metric.KILLS.ordinal()]);
        assertEquals(0, world.kills);
        assertEquals("单局最佳不会因为重开而归零", 60,
                s.achieveBest[Achievement.Metric.KILLS.ordinal()]);
        assertEquals("本局解锁计数归零", 0, world.achievesThisRun);
        assertEquals(0, world.achieveScore);
        world.kills = 10;
        world.commitAchievements();
        assertEquals(70, s.achieveTotal[Achievement.Metric.KILLS.ordinal()]);
        assertEquals("重开后单局最佳仍是那一局的 60", 60,
                s.achieveBest[Achievement.Metric.KILLS.ordinal()]);
        assertEquals("擦弹的历史最佳同样跨局保留", 30,
                s.achieveBest[Achievement.Metric.GRAZE.ordinal()]);
        assertEquals("本局擦弹读数已清零", 0, world.grazes);
    }

    @Test
    public void progressNeverRegressesAfterGettingHit() {
        world.kills = 200;
        world.commitAchievements();
        assertTrue(Achievement.CENTURY.unlockedIn(world.settings.achieveMask));
        assertTrue(Achievement.IRON_BODY.unlockedIn(world.settings.achieveMask));
        world.hitsTaken = 3;
        world.kills = 0;
        world.commitAchievements();
        assertTrue("挨打不能没收已拿到的成就",
                Achievement.IRON_BODY.unlockedIn(world.settings.achieveMask));
        assertEquals("历史最佳不回退", 200,
                world.settings.achieveBest[Achievement.Metric.CLEAN_KILLS.ordinal()]);
    }

    @Test
    public void accuracyNeedsASampleSize() {
        Settings s = cleanLedger();
        world.bulletsFired = 120;
        world.shotsHit = 120;              // 100% 命中，但样本不够
        world.commitAchievements();
        assertFalse("样本不足不该送神射手",
                Achievement.MARKSMAN.unlockedIn(s.achieveMask));
        world.bulletsFired = 200;
        world.shotsHit = 200;                 // 样本够了，命中率也够
        world.commitAchievements();
        assertTrue(Achievement.MARKSMAN.unlockedIn(s.achieveMask));
    }

    @Test
    public void cleanRunMetricDropsToZeroAfterFirstHit() {
        Settings s = cleanLedger();
        world.kills = 100;
        world.commitAchievements();
        world.hitsTaken = 1;
        world.kills = 900;
        world.commitAchievements();
        assertEquals("挨打之后金身读数应为 0（历史最佳仍是无损那段）", 100,
                s.achieveBest[Achievement.Metric.CLEAN_KILLS.ordinal()]);
        assertFalse(Achievement.IRON_BODY.unlockedIn(s.achieveMask));
    }

    @Test
    public void cheatsAndCasualDifficultyStillCount() {
        Settings s = cleanLedger();
        s.godMode = true;
        s.setDifficulty(Difficulty.CASUAL);
        world.restart(GameMode.WAVES);
        world.spawningEnabled = false;
        world.kills = 130;
        world.grazes = 160;
        world.commitAchievements();
        assertTrue("LAB 无敌与休闲档不屏蔽成就",
                Achievement.CENTURY.unlockedIn(s.achieveMask)
                        && Achievement.SLICK.unlockedIn(s.achieveMask));
    }

    @Test
    public void gameOverCommitRecordsProgressWithoutChangingScore() {
        world.kills = 5000;
        world.state = GameWorld.State.GAME_OVER;
        int score = world.score;
        world.commitAchievements();
        assertEquals("结算状态不再改分", score, world.score);
        assertTrue(Achievement.SWEEPER.unlockedIn(world.settings.achieveMask));
        assertEquals(0, world.achieveScore);
    }

    @Test
    public void pauseQuitAndDeathEachSettleTheRun() {
        FakeStore store = new FakeStore();
        world.attachStore(store);
        world.kills = 12;
        world.pauseIfPlaying();
        assertEquals("暂停即入账", 12, store.saved.achieveTotal[Achievement.Metric.KILLS.ordinal()]);
        world.handleUiAction(Ui.Action.RESUME);
        world.kills = 40;
        world.handleUiAction(Ui.Action.QUIT_TO_MENU);
        assertEquals("退出到菜单补上增量", 40,
                store.saved.achieveTotal[Achievement.Metric.KILLS.ordinal()]);
        assertTrue("退出前已落盘", store.settingsWrites > 0);
    }

    @Test
    public void settingsRoundTripKeepsEveryLedger() {
        FakeStore store = new FakeStore();
        world.attachStore(store);
        world.kills = 130;
        world.grazes = 20;
        world.typeKills[EnemyType.MAGNETON.ordinal()] = 4;
        world.commitAchievements();
        world.flushCodex();                  // 有变化才落盘：把账本刷进存档
        Settings reloaded = new Settings();
        store.load(reloaded);
        assertEquals(world.settings.achieveMask, reloaded.achieveMask);
        assertArrayEquals(world.settings.achieveBest, reloaded.achieveBest);
        assertArrayEquals(world.settings.achieveTotal, reloaded.achieveTotal);
        assertEquals(reloaded.achievementsUnlocked(), world.achievementsUnlocked());
    }

    @Test
    public void sanitizeDropsUnknownBitsAndNegativeLedgers() {
        Settings s = new Settings();
        s.achieveMask = -1L;                       // 全是脏位
        s.achieveBest[0] = -9;
        s.achieveTotal[1] = -9;
        s.sanitize();
        assertEquals("脏位必须掐干净", (1L << Achievement.COUNT) - 1L, s.achieveMask);
        assertEquals(0, s.achieveBest[0]);
        assertEquals(0, s.achieveTotal[1]);
    }

    // ---- 接线：统计口径取自真实战斗事件 ----

    @Test
    public void typeKillsAndFusionAreCountedByRealCombat() {
        Enemy e = null;
        for (Enemy c : world.enemies) {
            if (!c.active) { e = c; break; }
        }
        assertNotNull(e);
        e.init(EnemyType.MAGNETON, 300f, 200f, 1f);
        world.killEnemyForTest(e);
        assertEquals(1, world.typeKills[EnemyType.MAGNETON.ordinal()]);

        world.spawnPowerUp(PowerUp.Kind.SPREAD, world.ship.x, world.ship.y);
        world.spawnPowerUp(PowerUp.Kind.LASER, world.ship.x, world.ship.y);
        world.update(1f / 60f);
        assertEquals("首次融合计数", 1, world.fusions);
        world.spawnPowerUp(PowerUp.Kind.SPREAD, world.ship.x, world.ship.y);
        world.update(1f / 60f);
        assertEquals("重复拾取不再算一次融合", 1, world.fusions);

        world.commitAchievements();
        assertEquals(1, world.settings.achieveTotal[Achievement.Metric.FUSION.ordinal()]);
    }

    @Test
    public void ultBestTierRecordsTheFattestCast() {
        world.ult.charge = Ultimate.MIN_CAST;
        assertTrue(world.tryUltimate());
        assertEquals(1, world.ult.bestTier);
        world.ult.charge = Ultimate.CAP;
        assertTrue(world.tryUltimate());
        assertEquals(3, world.ult.bestTier);
        world.commitAchievements();
        assertTrue(Achievement.FULL_CHARGE.unlockedIn(world.settings.achieveMask));
    }

    // ---- 界面：入口、页签、返回 ----

    @Test
    public void menuButtonOpensAchievementsAndPanelsStayExclusive() {
        Ui.Action a = Ui.hit(GameWorld.State.MENU, false, false, false, false,
                Ui.ACHIEVEMENTS.cx(), Ui.ACHIEVEMENTS.cy());
        assertEquals(Ui.Action.OPEN_ACHIEVEMENTS, a);
        world.handleUiAction(a);
        assertTrue(world.achieveOpen);
        world.handleUiAction(Ui.Action.OPEN_CODEX);
        assertTrue("图鉴打开时成就页要收起", world.codexOpen && !world.achieveOpen);
        world.handleUiAction(Ui.Action.OPEN_ACHIEVEMENTS);
        assertFalse("成就页打开时图鉴要收起", world.codexOpen);
        assertNull("成就页上不响应图鉴按钮", Ui.hit(GameWorld.State.MENU, false, false,
                false, true, Ui.CODEX.cx(), Ui.CODEX.cy()));
    }

    @Test
    public void achievementPageHasTabsAndBackButton() {
        world.achieveOpen = true;
        assertEquals(Ui.Action.ACHIEVEMENTS_TAB, Ui.hit(GameWorld.State.MENU, false, false,
                false, true, Ui.ACH_TAB_CUM.cx(), Ui.ACH_TAB_CUM.cy()));
        world.handleUiAction(Ui.Action.ACHIEVEMENTS_TAB);
        assertEquals(1, world.achieveTab);
        world.handleUiAction(Ui.Action.ACHIEVEMENTS_TAB);
        assertEquals(0, world.achieveTab);
        assertEquals(Ui.Action.ACHIEVEMENTS_BACK, Ui.hit(GameWorld.State.MENU, false, false,
                false, true, Ui.ACHIEVEMENTS_BACK.cx(), Ui.ACHIEVEMENTS_BACK.cy()));
        world.handleUiAction(Ui.Action.ACHIEVEMENTS_BACK);
        assertFalse(world.achieveOpen);
    }

    @Test
    public void backKeyClosesAchievementPageBeforeLeavingTheApp() {
        world.state = GameWorld.State.MENU;   // 返回键的面板优先级只在主菜单生效
        world.achieveOpen = true;
        assertTrue("返回键应被成就页消费", world.consumeBack());
        assertFalse(world.achieveOpen);
        assertFalse("裸主菜单的返回键要放行", world.consumeBack());
    }

    @Test
    public void achievementPageFitsBothScopesWithoutOverlappingBack() {
        int run = 0, cum = 0;
        for (Achievement a : Achievement.values()) {
            if (a.scope == Achievement.Scope.RUN) run++; else cum++;
        }
        float runBottom = Ui.ACH_ROW_Y + run * Ui.ACH_ROW_H;
        assertTrue("单局页最后一行压到返回按钮：" + runBottom,
                runBottom <= Ui.ACHIEVEMENTS_BACK.y - 20f);
        assertTrue("生涯页行数不应多于单局页", cum <= run);
    }

    @Test
    public void progressReadoutUsesBestForRunsAndTotalsForCareer() {
        Settings s = new Settings();
        s.achieveBest[Achievement.Metric.KILLS.ordinal()] = 88;
        s.achieveTotal[Achievement.Metric.KILLS.ordinal()] = 3000;
        assertEquals(88, Achievements.valueOf(s, Achievement.CENTURY));
        assertEquals(3000, Achievements.valueOf(s, Achievement.SWEEPER));
        s.achieveMask = Achievement.FIRST_BLOOD.bit() | Achievement.SWEEPER.bit();
        assertEquals(2, s.achievementsUnlocked());
        assertEquals(Achievement.rank(2), s.achievementRank());
    }

    @Test
    public void runDoesNotLoseAnythingByUnlocking() {
        // "绝不白捡"的契约在成就上的体现：解锁前后生命、武器、道具状态一字不变
        List<String> before = snapshotState();
        int score = world.score;
        int lives = world.lives;
        world.kills = 1;
        world.commitAchievements();
        List<String> after = snapshotState();
        assertEquals("解锁成就不得改变任何战斗状态", before, after);
        assertEquals(lives, world.lives);
        assertTrue("分数只会更高", world.score > score);
    }

    private List<String> snapshotState() {
        List<String> out = new ArrayList<>();
        out.add("lives=" + world.lives);
        out.add("power=" + world.ship.powerLevel);
        for (WeaponType t : WeaponType.values()) {
            out.add(t + "=" + (world.ship.isWeaponEquipped(t) ? world.ship.stackOf(t) : 0));
        }
        out.add("relics=" + world.relicMask);
        out.add("shield=" + world.ship.shieldLayers);
        return out;
    }

    private static void assertArrayEquals(int[] expected, int[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("第 " + i + " 项账本不一致", expected[i], actual[i]);
        }
    }
}
