package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.58 迭代 48：限时抢分 TIME ATTACK。
 *
 * <p>两分钟的硬时钟：钟停就结算，不看还剩几条命。钟本身可以被"打回来"——
 * 击破旗舰 +15 秒、精英 +2 秒、兑现悬赏 +3 秒，于是这局的节奏变成
 * "清得越快，能打的仗就越多"。它有自己的一张榜，不污染战役最高分；
 * 与日常 / 连战 / 回声一样锁标准难度并屏蔽 LAB。
 */
public class TimeAttackV158Test {

    private GameWorld world;
    private final List<GameWorld.GameEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261028L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.bountyEnabled = false;
        world.perksEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.ship.makeInvulnerable();
        world.restart(GameMode.TIME);
        drain();
    }

    // ---- 辅助 ----

    private void step(int frames) {
        for (int i = 0; i < frames; i++) {
            world.update(1f / 60f);
            events.addAll(world.pollEvents());
        }
    }

    private void drain() {
        events.clear();
        events.addAll(world.pollEvents());
    }

    private int countEvents(GameWorld.EventType type) {
        int n = 0;
        for (GameWorld.GameEvent e : events) if (e.type == type) n++;
        return n;
    }

    private GameWorld.GameEvent lastEvent(GameWorld.EventType type) {
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i).type == type) return events.get(i);
        }
        return null;
    }

    private Enemy spawnElite() {
        for (Enemy e : world.enemies) {
            if (e.active) continue;
            e.init(EnemyType.DRONE, 450f, 900f, 1f);
            e.hp = e.maxHp = 1f;
            e.elite = true;
            return e;
        }
        throw new IllegalStateException("敌机池已满");
    }

    private void shootAt(Enemy e) {
        for (Bullet b : world.bullets) {
            if (b.active) continue;
            b.init(e.x, e.y, 0f, 0f, 1, false);
            return;
        }
        throw new IllegalStateException("玩家弹池已满");
    }

    private void triggerBoss() {
        world.wave = 3;
        world.update(1f / 60f);
        world.bossWarningTimer = 0f;
        world.bossPhaseBreakTimer = 0f;
        world.boss.y = world.boss.arenaY;
        assertTrue("限时局第 3 波就该见旗舰", world.boss.active);
    }

    // ---- 开局 ----

    @Test
    public void startsWithTwoMinutesAndAFixedLoadout() {
        assertEquals(GameMode.TIME, world.mode);
        assertTrue(world.timeAttack());
        assertEquals("基础时限两分钟", 120f, world.timeLeft, 0.001f);
        assertEquals("固定三命", GameWorld.TIME_LIVES, world.lives);
        assertEquals("开局就给两路火力", GameWorld.TIME_START_POWER, world.ship.powerLevel);
        assertEquals(GameWorld.State.PLAYING, world.state);
        assertEquals("不播开场航段，第一秒就开始计时", 0f, world.stageIntroTimer, 0.001f);
        assertFalse(world.timeUp);
    }

    @Test
    public void clockOnlyRunsInTimeAttack() {
        step(60);
        assertEquals("一秒一帧，60 帧掉 1 秒", 119f, world.timeLeft, 0.02f);
        world.restart(GameMode.WAVES);
        assertEquals("普通局没有钟", 0f, world.timeLeft, 0.001f);
        step(30);
        assertEquals("普通局不会凭空开始倒计时", 0f, world.timeLeft, 0.001f);
    }

    @Test
    public void bossWaveComesEarlierInTimeAttack() {
        assertEquals("限时局第 3 波就压旗舰，才够时间买加时", 3, world.nextBossWave);
    }

    // ---- 加时 ----

    @Test
    public void eliteKillsBuyTwoSeconds() {
        Enemy e = spawnElite();
        shootAt(e);
        step(1);
        assertFalse("精英应当被击毁", e.active);
        assertEquals("精英 +2 秒", 2f, world.timeExtended, 0.001f);
        assertEquals(GameWorld.TIME_ELITE_SECONDS, world.timeBonusLast, 0.001f);
        assertTrue("HUD 应当亮起加时提示", world.timeBonusFlash > 0f);
        GameWorld.GameEvent bonus = lastEvent(GameWorld.EventType.TIME_BONUS);
        assertTrue("应当报一次加时事件", bonus != null);
        assertEquals(2, bonus.code);
        assertEquals("钟面确实往回补了", 122f, world.timeLeft, 0.05f);
    }

    @Test
    public void bossKillsBuyFifteenSeconds() {
        triggerBoss();
        float before = world.timeLeft;
        world.boss.hp = 0f;
        step(1);
        assertFalse("旗舰应当被击破", world.boss.active);
        assertEquals("旗舰 +15 秒", 15f, world.timeExtended, 0.001f);
        assertEquals("钟面回到十五秒前", before + 15f, world.timeLeft, 0.1f);
        GameWorld.GameEvent bonus = lastEvent(GameWorld.EventType.TIME_BONUS);
        assertTrue(bonus != null);
        assertEquals(15, bonus.code);
    }

    @Test
    public void bonusIsIgnoredOutsideTimeAttack() {
        world.restart(GameMode.WAVES);
        world.extendTime(15f, 450f, 900f);
        assertEquals("普通局不会被加时", 0f, world.timeExtended, 0.001f);
        assertEquals("也不会点亮 HUD", 0f, world.timeBonusFlash, 0.001f);
        assertEquals(0, countEvents(GameWorld.EventType.TIME_BONUS));
    }

    // ---- 收盘 ----

    @Test
    public void clockReachingZeroEndsTheRun() {
        world.score = 4321;
        world.timeLeft = 0.01f;
        step(2);
        assertTrue(world.timeUp);
        assertEquals(GameWorld.State.GAME_OVER, world.state);
        assertEquals(0f, world.timeLeft, 0.001f);
        assertEquals("时间到点也算收盘事件", 1, countEvents(GameWorld.EventType.TIME_UP));
    }

    @Test
    public void timeUpIsNotADeath() {
        int before = world.settings.deaths;
        world.timeLeft = 0.01f;
        step(2);
        assertEquals("钟停不算阵亡", before, world.settings.deaths);
        assertEquals("命数原样保留，方便结算页展示", GameWorld.TIME_LIVES, world.lives);
    }

    @Test
    public void timeAttackHasItsOwnBoardAndKeepsCampaignBestClean() {
        world.bestScore = 777_777;
        world.score = 5555;
        world.timeLeft = 0.01f;
        step(2);
        assertEquals("限时分数进自己的榜", 5555, world.board(GameMode.TIME)[0]);
        assertEquals("不碰战役历史最高分", 777_777, world.bestScore);
        assertTrue(world.newBest);
    }

    @Test
    public void restartClearsTheClockAndTheTimeUpFlag() {
        world.timeLeft = 0.01f;
        step(2);
        assertTrue(world.timeUp);
        world.restart(GameMode.TIME);
        assertFalse(world.timeUp);
        assertEquals(120f, world.timeLeft, 0.001f);
        assertEquals(0f, world.timeExtended, 0.001f);
    }

    @Test
    public void pauseStopsTheClock() {
        step(10);
        float frozen = world.timeLeft;
        world.pauseIfPlaying();
        step(120);
        assertEquals("暂停时两分钟不会偷偷流走", frozen, world.timeLeft, 0.001f);
    }

    @Test
    public void labCannotRewriteTheTimeBoard() {
        world.settings.godMode = true;
        world.settings.startLives = 9;
        world.settings.startPower = 3;
        world.restart(GameMode.TIME);
        assertEquals("限时局不吃 LAB 的命数", GameWorld.TIME_LIVES, world.lives);
        assertEquals("也不吃 LAB 的火力", GameWorld.TIME_START_POWER, world.ship.powerLevel);
    }
}
