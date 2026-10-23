package com.nebulastrike.game.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * v1.20 迭代 10：BOSS RUSH 连战模式。
 *
 * <p>三条契约，逐条钉住：
 *
 * <ol>
 *   <li><b>只有 Boss，没有节拍</b>：开局 2.6 秒第一位入场，之后每打完一个喘息 1.8 秒；
 *       常规刷新、突袭波、保底投放全部停摆，wave 不再是进度条。</li>
 *   <li><b>全球同一把尺</b>：固定 3 条命、2 级火力、标准难度、屏蔽 LAB 与难度档位；
 *       越深入赏金越高（第 n 个 Boss 额外 150×n）。</li>
 *   <li><b>不污染既有模式</b>：独立 TOP 5 与"最深入纪录"，不写 CAMPAIGN 的历史最高分；
 *       退出去打普通局/日常，所有系数逐位回到 v1.19 的行为。</li>
 * </ol>
 */
public class BossRushTest {

    private static final float DT = 1f / 60f;

    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(20261023L);
        world.settings.setDifficulty(Difficulty.STANDARD);
    }

    // ---- 辅助 ----

    private static void startRush(GameWorld w) {
        w.handleUiAction(Ui.Action.START_RUSH);
        assertEquals(GameWorld.State.PLAYING, w.state);
        assertEquals(GameMode.RUSH, w.mode);
    }

    private static void advance(GameWorld w, float seconds) {
        int frames = Math.round(seconds / DT);
        for (int i = 0; i < frames; i++) w.update(DT);
    }

    /** 跑到第一位 Boss 入场（含排队），断言它真的来了。 */
    private static void waitForBoss(GameWorld w) {
        float t = 0f;
        while (!w.bossActive && t < 5f) { w.update(DT); t += DT; }
        assertTrue("连战局 5 秒内必须见到第一位 Boss", w.bossActive);
    }

    /** 把当前 Boss 打死：只把血清零，其余流程照旧走一遍。 */
    private static void killBoss(GameWorld w) {
        assertTrue("没有 Boss 可打", w.bossActive);
        w.boss.hp = 0f;
        w.update(DT);
        assertFalse("血量归零后 Boss 必须下场", w.bossActive);
    }

    /** 让船结结实实挨一发直到掉命（先等过开局保护）。 */
    private static void takeFatalHit(GameWorld w) {
        w.lives = 1;
        w.ship.shieldLayers = 0;
        float t = 0f;
        while (w.ship.invulnerable && t < 4f) { w.update(DT); t += DT; }
        w.enemyBullets[0].init(w.ship.x, w.ship.y, 0f, 0f);
        w.update(DT);
    }

    private static int activeEnemies(GameWorld w) {
        int n = 0;
        for (Enemy e : w.enemies) if (e.active) n++;
        return n;
    }

    /** 内存存档：连战榜与"最深入纪录"也要能原样读回来。 */
    static class FakeStore implements SettingsStore {
        final Settings saved = new Settings();
        final int[] campaign = new int[5];
        final int[] rush = new int[5];
        int bestScore;
        int rushBestWrites;

        @Override public void load(Settings into) {
            into.rushBest = saved.rushBest;
            into.sanitize();
        }
        @Override public int bestScore() { return bestScore; }
        @Override public void saveBestScore(int score) { bestScore = score; }
        @Override public int[] leaderboard(GameMode mode) {
            if (mode == GameMode.RUSH) return rush.clone();
            return mode == GameMode.ENDLESS ? new int[5] : campaign.clone();
        }
        @Override public void saveLeaderboard(GameMode mode, int[] scores) {
            int[] dst = mode == GameMode.RUSH ? rush : campaign;
            if (mode == GameMode.ENDLESS) return;
            System.arraycopy(scores, 0, dst, 0, Math.min(scores.length, dst.length));
        }
        @Override public void save(Settings s) {
            saved.rushBest = s.rushBest;
            rushBestWrites++;
        }
    }

    // ---- 同一把尺 ----

    @Test
    public void rushPinsLivesPowerDifficultyAndIgnoresLab() {
        world.settings.startLives = 1;
        world.settings.startPower = 3;
        world.settings.godMode = true;
        world.settings.dropRatePct = 0;
        world.settings.enemySpeedPct = 160;
        world.settings.setDifficulty(Difficulty.HARDCORE);

        startRush(world);

        assertEquals("连战固定 3 条命", GameWorld.RUSH_LIVES, world.lives);
        assertEquals("开局 2 级火力，不能靠 LAB 抬", 2, world.ship.powerLevel);
        assertEquals("难度锁定标准：100 分原样入账，不打 1.5 倍",
                100, world.addScore(100));

        world.update(DT);
        assertEquals("LAB 敌机加速不生效", 1f, world.enemySpeedScale, 0.0001f);
    }

    @Test
    public void godModeDoesNotSaveYouInRush() {
        world.settings.godMode = true;
        startRush(world);
        takeFatalHit(world);
        assertEquals("连战局屏蔽金手指：必须真的坠机", GameWorld.State.GAME_OVER, world.state);
    }

    // ---- 只有 Boss ----

    @Test
    public void firstBossArrivesQuicklyAndNoRegularEnemiesSpawn() {
        startRush(world);
        advance(world, 2.0f);
        assertFalse("2 秒时还没开场完", world.bossActive);
        assertEquals("连战局不刷小怪", 0, world.spawnedCount);
        assertEquals(0, activeEnemies(world));

        advance(world, 1.2f);
        assertTrue("2.6 秒后第一位必须入场", world.bossActive);
        assertEquals("Boss 入场前后都不许有小怪", 0, world.spawnedCount);
        assertEquals(0, activeEnemies(world));
    }

    @Test
    public void bossChainKeepsComingAndPaysMoreEachTime() {
        startRush(world);
        waitForBoss(world);
        assertEquals("第一位是 0 级", 0, world.boss.level);

        int before = world.score;
        killBoss(world);
        assertEquals(1, world.bossRushKills);
        assertEquals(1, world.bossesDefeated);
        int firstGain = world.score - before;

        advance(world, 1.5f);
        assertFalse("喘息期间不该立刻接上", world.bossActive);
        assertEquals("喘息期间也不刷小怪", 0, activeEnemies(world));

        advance(world, 0.5f);
        assertTrue("1.8 秒后下一位入场", world.bossActive);
        assertEquals("Boss 等级跟着击坠数走", 1, world.boss.level);
        assertEquals("血量随等级上涨", 620f, world.boss.maxHp, 0.001f);

        int mid = world.score;
        killBoss(world);
        assertEquals(2, world.bossRushKills);
        int secondGain = world.score - mid;
        assertTrue("第二个 Boss 赏金更高：" + firstGain + " → " + secondGain,
                secondGain > firstGain);
    }

    @Test
    public void rushNeverRollsRandomMutators() {
        startRush(world);
        world.lives = 999;   // 只想看排程，不想被打死
        advance(world, 50f); // 越过 45 秒的第一次投骰
        assertFalse("连战局每局规则一致，不掷异变", world.mutator.isActive());
        assertEquals(Mutator.NONE, world.mutator);
    }

    @Test
    public void rushStartEventFiresOnceWithTheFixedLifetime() {
        startRush(world);
        List<GameWorld.GameEvent> events = world.pollEvents();
        int count = 0;
        for (GameWorld.GameEvent e : events) {
            if (e.type == GameWorld.EventType.BOSS_RUSH_START) {
                count++;
                assertEquals(GameWorld.RUSH_LIVES, e.code);
            }
        }
        assertEquals("开局只播一次横幅音", 1, count);

        world.pollEvents();
        world.handleUiAction(Ui.Action.START);
        for (GameWorld.GameEvent e : world.pollEvents()) {
            assertFalse("普通局不该播连战开场音",
                    e.type == GameWorld.EventType.BOSS_RUSH_START);
        }
    }

    // ---- 独立榜单与最深入纪录 ----

    @Test
    public void rushKeepsItsOwnBoardAndLeavesCampaignBestAlone() {
        FakeStore store = new FakeStore();
        store.bestScore = 12345;
        store.rush[0] = 900;
        GameWorld w = new GameWorld(11L, true);
        w.attachStore(store);
        assertEquals("榜单要在 attachStore 时读回来", 900, w.board(GameMode.RUSH)[0]);

        startRush(w);
        w.score = 777;
        takeFatalHit(w);

        assertEquals(GameWorld.State.GAME_OVER, w.state);
        assertArrayEquals("连战分数进自己的榜", new int[]{900, 777, 0, 0, 0}, store.rush);
        assertArrayEquals(new int[]{0, 0, 0, 0, 0}, store.campaign);
        assertEquals("不动 CAMPAIGN 历史最高分", 12345, store.bestScore);
        assertFalse("连战局不点亮 NEW BEST", w.newBest);
    }

    @Test
    public void deepestRunRecordOnlyGrowsAndIsPersisted() {
        FakeStore store = new FakeStore();
        GameWorld w = new GameWorld(12L, true);
        w.attachStore(store);
        assertEquals(0, w.settings.rushBest);

        startRush(w);
        waitForBoss(w);
        killBoss(w);
        takeFatalHit(w);

        assertEquals(GameWorld.State.GAME_OVER, w.state);
        assertEquals("首杀就是新纪录", 1, w.settings.rushBest);
        assertTrue(w.bossRushImproved);
        assertEquals("纪录要落盘", 1, store.saved.rushBest);

        // 纪录比本局更深的对局：不倒退、不重复落盘
        FakeStore deeper = new FakeStore();
        deeper.saved.rushBest = 5;
        GameWorld b = new GameWorld(13L, true);
        b.attachStore(deeper);
        assertEquals(5, b.settings.rushBest);
        startRush(b);
        waitForBoss(b);
        killBoss(b);
        takeFatalHit(b);
        assertEquals("打不到纪录就不该改写", 5, b.settings.rushBest);
        assertFalse(b.bossRushImproved);
        assertEquals("存档里的纪录也不该被覆盖", 5, deeper.saved.rushBest);
    }

    // ---- 退出连战不污染其它模式 ----

    @Test
    public void leavingRushRestoresCampaignAndDailyRules() {
        world.settings.startLives = 2;
        world.settings.startPower = 3;
        world.settings.dropRatePct = 50;
        world.settings.enemySpeedPct = 130;
        world.settings.godMode = true;
        world.settings.setDifficulty(Difficulty.HARDCORE);

        startRush(world);
        assertEquals(3, world.lives);

        world.handleUiAction(Ui.Action.START);
        assertEquals(GameMode.WAVES, world.mode);
        assertEquals("普通局命数回到存档", 2, world.lives);
        assertEquals("普通局火力回到存档", 3, world.ship.powerLevel);
        assertEquals("普通局难度倍率回来", 150, world.addScore(100));
        world.update(DT);
        assertTrue("普通局敌机加速回来", world.enemySpeedScale > 1f);

        advance(world, 4f);
        assertTrue("普通局重新刷小怪", world.spawnedCount > 0);

        world.restart(GameMode.DAILY);
        assertEquals(GameMode.DAILY, world.mode);
        assertEquals("日常命数仍然由日期决定", DailyChallenge.livesFor(world.dailyKey), world.lives);
        assertNotNull(world.dailyRule);
    }

    @Test
    public void menuRoutesEachModeButtonToItsOwnAction() {
        assertEquals(Ui.Action.START_ENDLESS, Ui.hit(GameWorld.State.MENU, false, false,
                Ui.START_ENDLESS.cx(), Ui.START_ENDLESS.cy()));
        assertEquals(Ui.Action.START_DAILY, Ui.hit(GameWorld.State.MENU, false, false,
                Ui.START_DAILY.cx(), Ui.START_DAILY.cy()));
        assertEquals(Ui.Action.START_RUSH, Ui.hit(GameWorld.State.MENU, false, false,
                Ui.START_RUSH.cx(), Ui.START_RUSH.cy()));
        assertTrue("三格并排后仍留在屏幕内",
                Ui.START_RUSH.x + Ui.START_RUSH.w <= GameWorld.WIDTH);

        world.handleUiAction(Ui.Action.START_RUSH);
        assertEquals(GameMode.RUSH, world.mode);
        assertEquals(GameWorld.State.PLAYING, world.state);
    }

    @Test
    public void rushBannerOutlastsTheOpeningWait() {
        startRush(world);
        assertTrue(world.bossRushBanner > 0f);
        assertTrue("横幅要盖住 2.6 秒开场",
                world.bossRushBanner > GameWorld.BOSS_RUSH_OPENING);
        advance(world, 3.4f);
        assertEquals(0f, world.bossRushBanner, 0.0001f);
    }
}
