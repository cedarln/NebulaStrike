package com.nebulastrike.game.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.49 迭代 39：回声周目 ECHO LOOP。
 *
 * <p>打穿主线后解锁的 NG+：同样的八段航程，敌机更厚、更快、出招更密，旗舰也更强；
 * 回报是 1.5 倍得分、独立的一张榜，以及每场 Boss 掉一枚"回声碎片"（后日谈的解锁货币）。
 * 它和 BOSS RUSH 一样锁标准难度、屏蔽 LAB——NG+ 的强度只由模式自己的倍率负责。
 */
public class EchoV149Test {

    private GameWorld world;
    private final List<GameWorld.GameEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261027L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.bountyEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.lives = 999_999;
        world.ship.x = 450f;
        world.ship.prevX = 450f;
        world.ship.y = 1460f;
        world.ship.prevY = 1460f;
    }

    private void drainEvents() {
        events.addAll(world.pollEvents());
    }

    private boolean hasEvent(GameWorld.EventType type) {
        for (GameWorld.GameEvent e : events) {
            if (e.type == type) return true;
        }
        return false;
    }

    private int eventCode(GameWorld.EventType type) {
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i).type == type) return events.get(i).code;
        }
        return -1;
    }

    private void spawnBossAt(int defeated) {
        world.boss.active = false;
        world.bossActive = false;
        world.bossesDefeated = defeated;
        world.bossGapTimer = 0f;
        world.nextBossWave = 4;
        world.wave = 4;
        world.update(1f / 60f);
        assertTrue("Boss 应已入场", world.boss.active);
        world.bossWarningTimer = 0f;
        world.boss.y = world.boss.arenaY;
    }

    private void killBossOnce() {
        world.boss.hp = 0f;
        world.update(1f / 60f);
    }

    private void killFinalBossTwice() {
        spawnBossAt(Story.SECTOR_COUNT - 1);
        killBossOnce();
        assertFalse("第一次打空只重构，不结算", world.campaignVictory);
        killBossOnce();
    }

    /** 记录落盘内容的假仓库：用来验证回声榜不会污染战役榜。 */
    private static final class RecordingStore implements SettingsStore {
        final Map<GameMode, int[]> boards = new EnumMap<>(GameMode.class);
        Integer savedBest;
        Settings savedSettings;

        @Override
        public void load(Settings into) {
            into.campaignCleared = true;
        }

        @Override
        public int bestScore() {
            return 777;
        }

        @Override
        public void saveBestScore(int score) {
            savedBest = score;
        }

        @Override
        public int[] leaderboard(GameMode mode) {
            int[] b = boards.get(mode);
            return b == null ? new int[5] : b.clone();
        }

        @Override
        public void saveLeaderboard(GameMode mode, int[] scores) {
            boards.put(mode, scores.clone());
        }

        @Override
        public void save(Settings settings) {
            savedSettings = settings;
        }
    }

    @Test
    public void echoIsLockedUntilTheCampaignIsCleared() {
        GameWorld menu = new GameWorld(20261027L, true);
        assertFalse(menu.settings.echoUnlocked());
        assertFalse(menu.settings.campaignCleared);

        Ui.Action action = Ui.hit(GameWorld.State.MENU, false, false, false,
                false, false, false, Ui.START_ECHO.cx(), Ui.START_ECHO.cy());
        assertEquals("锁住时也要接住点击，才能给解锁提示", Ui.Action.START_ECHO, action);

        menu.handleUiAction(action);
        assertFalse(menu.echo());
        assertEquals("锁住时不进入对局", GameWorld.State.MENU, menu.state);
        assertTrue("铭牌要闪一下提示解锁条件", menu.echoLockedFlash > 0f);

        menu.settings.campaignCleared = true;
        menu.handleUiAction(action);
        assertTrue(menu.echo());
        assertEquals(GameWorld.State.PLAYING, menu.state);
        events.addAll(menu.pollEvents());
        assertTrue(hasEvent(GameWorld.EventType.ECHO_START));
        assertEquals("开局事件带上当前碎片数", 0, eventCode(GameWorld.EventType.ECHO_START));
    }

    @Test
    public void echoRunPinsLivesPowerAndIgnoresLab() {
        world.settings.setDifficulty(Difficulty.HARDCORE);
        world.settings.startLives = 1;
        world.settings.startPower = 3;
        world.settings.dropRatePct = 50;
        world.settings.enemySpeedPct = 200;
        world.settings.godMode = true;

        world.restart(GameMode.ECHO);
        assertEquals("回声局固定 4 条命，不受 LAB 影响", GameWorld.ECHO_LIVES, world.lives);
        assertEquals("开局 2 级火力", GameWorld.ECHO_START_POWER, world.ship.powerLevel);
        assertEquals(GameWorld.ECHO_BANNER_TIME, world.echoBanner, 0.0001f);

        world.update(1f / 60f);
        Enemy echoDrone = world.spawnEnemyForTest(EnemyType.DRONE, 450f);
        assertNotNull(echoDrone);
        assertEquals("回声敌速 = 基础 ×1.12，不吃硬核 / LAB 的 200%",
                EnemyType.DRONE.baseSpeed * GameWorld.ECHO_SPEED_SCALE,
                echoDrone.speed(), 0.1f);
        // 池子会复用同一个对象，先把读数取出来再重开
        float echoSpeed = echoDrone.speed();

        world.restart(GameMode.WAVES);
        world.update(1f / 60f);
        Enemy labDrone = world.spawnEnemyForTest(EnemyType.DRONE, 450f);
        assertNotNull(labDrone);
        assertEquals("普通局才吃 硬核 1.15 × LAB 200%",
                EnemyType.DRONE.baseSpeed * 2f * Difficulty.HARDCORE.speedScale,
                labDrone.speed(), 0.1f);
        assertTrue(labDrone.speed() > echoSpeed * 1.8f);
    }

    @Test
    public void echoToughensHullsAndPaysOneAndAHalf() {
        world.restart(GameMode.ECHO);
        world.update(1f / 60f);
        Enemy echoTank = world.spawnEnemyForTest(EnemyType.TANK, 300f);
        assertNotNull(echoTank);
        assertEquals(EnemyType.TANK.hp * GameWorld.ECHO_HP_SCALE, echoTank.hp, 0.001f);
        assertEquals(echoTank.hp, echoTank.maxHp, 0.001f);

        world.restart(GameMode.WAVES);
        world.update(1f / 60f);
        Enemy plainTank = world.spawnEnemyForTest(EnemyType.TANK, 300f);
        assertNotNull(plainTank);
        assertEquals(EnemyType.TANK.hp, plainTank.hp, 0.001f);

        world.restart(GameMode.ECHO);
        assertEquals("回声周目得分 ×1.5", 150, world.addScore(100));
        world.x2Timer = 1f;
        assertEquals("双倍分数与回声倍率叠乘", 300, world.addScore(100));
        assertEquals("分数照常入账", 450, world.score);

        world.restart(GameMode.WAVES);
        assertEquals("普通战役保持原值", 100, world.addScore(100));
    }

    @Test
    public void echoFlagshipIsThickerAndHitsFaster() {
        world.restart(GameMode.ECHO);
        spawnBossAt(0);

        assertEquals(420f * GameWorld.ECHO_BOSS_HP_SCALE, world.boss.maxHp, 0.001f);
        assertEquals(world.boss.maxHp, world.boss.hp, 0.001f);
        assertEquals("无词缀时节奏只乘回声倍率", GameWorld.ECHO_BOSS_TEMPO,
                world.boss.tempo, 0.0001f);
        assertFalse("第一场不是最终旗舰", world.finalBossFight);

        world.restart(GameMode.WAVES);
        spawnBossAt(0);
        assertEquals("战役仍按原血量曲线", 420f, world.boss.maxHp, 0.001f);
        assertEquals(1f, world.boss.tempo, 0.0001f);
    }

    @Test
    public void everyEchoBossDropsAShard() {
        world.restart(GameMode.ECHO);
        spawnBossAt(0);
        killBossOnce();
        drainEvents();

        assertEquals(1, world.settings.echoShards);
        assertTrue("到手时 HUD 要闪一下", world.echoShardFlash > 0f);
        assertEquals(1, eventCode(GameWorld.EventType.ECHO_SHARD));
        assertEquals(1, world.bossesDefeated);
        assertFalse(world.campaignVictory);

        spawnBossAt(1);
        killBossOnce();
        drainEvents();
        assertEquals(2, world.settings.echoShards);
        assertEquals(2, eventCode(GameWorld.EventType.ECHO_SHARD));

        world.restart(GameMode.WAVES);
        assertEquals("普通战役不掉回声碎片", 2, world.settings.echoShards);
    }

    @Test
    public void echoBossesStillEndWithTheFinalFlagshipAndTheEnding() {
        world.settings.campaignCleared = true;
        world.restart(GameMode.ECHO);
        spawnBossAt(Story.SECTOR_COUNT - 1);
        assertTrue(world.finalBossFight);
        assertEquals(GameWorld.FINAL_BOSS_LEVEL, world.boss.level);
        assertEquals((420f + 200f * GameWorld.FINAL_BOSS_LEVEL) * GameWorld.ECHO_BOSS_HP_SCALE,
                world.boss.maxHp, 0.001f);

        world.score = 4321;
        killBossOnce();
        assertFalse(world.campaignVictory);
        killBossOnce();
        drainEvents();

        assertTrue(world.campaignVictory);
        assertFalse("主线早已通关，回声通关不再算首次", world.campaignFirstClear);
        assertEquals("第一次回声通关用 0 标记", 0, eventCode(GameWorld.EventType.CAMPAIGN_CLEAR));
        assertTrue("结局演出照常播放", world.endingActive());
        assertEquals(1, world.settings.echoClears);
        assertTrue("主线通关状态不会因为回声而回退", world.settings.campaignCleared);
        assertEquals("终局 1 枚 + 通关 3 枚",
                GameWorld.ECHO_SHARDS_PER_BOSS + GameWorld.ECHO_SHARDS_ON_CLEAR,
                world.settings.echoShards);

        world.skipEnding();
        assertFalse(world.endingActive());
        assertEquals(GameWorld.State.GAME_OVER, world.state);

        // 第二周目：计数继续往上叠
        world.restart(GameMode.ECHO);
        drainEvents();
        assertEquals("开局事件读的是第 2 周目的碎片数",
                GameWorld.ECHO_SHARDS_PER_BOSS + GameWorld.ECHO_SHARDS_ON_CLEAR,
                eventCode(GameWorld.EventType.ECHO_START));
        killFinalBossTwice();
        assertEquals(2, world.settings.echoClears);
        assertEquals(2 * (GameWorld.ECHO_SHARDS_PER_BOSS + GameWorld.ECHO_SHARDS_ON_CLEAR),
                world.settings.echoShards);
    }

    @Test
    public void echoScoreGoesToItsOwnBoardNotTheCampaignBest() {
        RecordingStore store = new RecordingStore();
        store.boards.put(GameMode.ECHO, new int[]{1000, 0, 0, 0, 0});
        world.attachStore(store);
        assertEquals("回声榜从存档读入", 1000, world.leaderboardEcho[0]);
        assertEquals(777, world.bestScore);

        world.settings.campaignCleared = true;
        world.restart(GameMode.ECHO);
        world.score = 4321;
        killFinalBossTwice();

        assertTrue(world.newBest);
        assertEquals("战役历史最高分不被回声改写", 777, world.bestScore);
        assertNull("也不写 CAMPAIGN 的最高分", store.savedBest);
        assertEquals(world.score, world.leaderboardEcho[0]);
        assertArrayEquals(new int[]{world.score, 1000, 0, 0, 0},
                store.boards.get(GameMode.ECHO));
        assertFalse("不碰 WAVES 的榜", store.boards.containsKey(GameMode.WAVES));
        assertNotNull(store.savedSettings);
        assertEquals(world.settings.echoShards, store.savedSettings.echoShards);
    }

    @Test
    public void restartResetsTheEchoPresentationButNotTheShardWallet() {
        world.settings.echoShards = 5;
        world.restart(GameMode.ECHO);
        drainEvents();
        assertEquals(5, eventCode(GameWorld.EventType.ECHO_START));
        world.echoShardFlash = 1f;
        world.echoLockedFlash = 2f;

        world.restart(GameMode.WAVES);
        assertEquals(0f, world.echoBanner, 0f);
        assertEquals(0f, world.echoShardFlash, 0f);
        assertEquals(0f, world.echoLockedFlash, 0f);
        assertEquals("碎片是跨局资产", 5, world.settings.echoShards);
        assertFalse(world.echo());
    }

    @Test
    public void settingsSanitizeAndUnlockFlag() {
        Settings s = new Settings();
        s.campaignCleared = false;
        assertFalse(s.echoUnlocked());
        s.campaignCleared = true;
        assertTrue(s.echoUnlocked());

        s.echoShards = -3;
        s.echoClears = -1;
        s.sanitize();
        assertEquals(0, s.echoShards);
        assertEquals(0, s.echoClears);
    }
}
