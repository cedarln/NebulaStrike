package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.59 迭代 49：Boss 模拟战 PRACTICE。
 *
 * <p>从图鉴 Boss 页点一艘已经打赢过的旗舰，直接进一间只属于它的练功房：
 * 没有小怪、没有异变/祭坛/赏金/护航，挨打不掉命，击破后同一艘船立刻回来。
 * 练功不写任何账本——图鉴击坠数、剧情、成就、生涯出勤与五张榜都保持原样。
 */
public class PracticeV159Test {

    private GameWorld world;
    private final List<GameWorld.GameEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261028L, true);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.bountyEnabled = false;
        world.perksEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
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

    /** 走真实入口：解锁指定档位 → 打开图鉴 Boss 页 → 点那一行。 */
    private void enterPractice(int tier) {
        world.settings.codexBossKills[tier] = Math.max(1, world.settings.codexBossKills[tier]);
        world.handleUiAction(Ui.Action.OPEN_CODEX);
        world.codexTab = 1;
        Ui.Rect row = Ui.codexRow(tier);
        world.handleUiAction(Ui.hit(GameWorld.State.MENU, false, false, true, false, false,
                false, world.codexTab, row.cx(), row.cy()));
        drain();
    }

    /** 等目标旗舰入场，并跳过预警，让武器与计时立刻进入实战状态。 */
    private void waitForBoss() {
        for (int i = 0; i < 400 && !world.boss.active; i++) step(1);
        assertTrue("模拟战里旗舰应该自动入场", world.boss.active);
        world.bossWarningTimer = 0f;
        world.bossPhaseBreakTimer = 0f;
        world.boss.y = world.boss.arenaY;
        drain();
    }

    private void killBoss() {
        world.boss.hp = 0f;
        world.update(1f / 60f);
        drain();
    }

    private int activePowerUps() {
        int n = 0;
        for (PowerUp p : world.powerups) if (p.active) n++;
        return n;
    }

    // ---- 入口 ----

    @Test
    public void onlyUnlockedBossesStartPractice() {
        world.handleUiAction(Ui.Action.OPEN_CODEX);
        world.codexTab = 1;
        Ui.Rect locked = Ui.codexRow(3);
        world.handleUiAction(Ui.hit(GameWorld.State.MENU, false, false, true, false, false,
                false, world.codexTab, locked.cx(), locked.cy()));
        assertEquals("没打赢过的旗舰不能练", GameWorld.State.MENU, world.state);
        assertEquals(-1, world.practiceTier);

        enterPractice(2);
        assertEquals(GameMode.PRACTICE, world.mode);
        assertTrue(world.practice());
        assertEquals("进入的就是点的那一档", 2, world.practiceTier);
        assertEquals(GameWorld.State.PLAYING, world.state);
        assertFalse("面板应该已经收起", world.codexOpen);
    }

    @Test
    public void entryOnlyWorksFromTheBossTab() {
        world.settings.codexBossKills[0] = 1;
        world.handleUiAction(Ui.Action.OPEN_CODEX);
        world.codexTab = 0;
        Ui.Rect row = Ui.codexRow(0);
        Ui.Action enemyRow = Ui.hit(GameWorld.State.MENU, false, false, true, false, false,
                false, world.codexTab, row.cx(), row.cy());
        assertNull("敌机页的行不是模拟战入口", enemyRow);
        assertEquals(GameWorld.State.MENU, world.state);

        world.codexTab = 1;
        world.handleUiAction(Ui.hit(GameWorld.State.MENU, false, false, true, false, false,
                false, world.codexTab, row.cx(), row.cy()));
        assertEquals(GameMode.PRACTICE, world.mode);
        assertEquals(0, world.practiceTier);
    }

    @Test
    public void practiceStartsWithAFixedLoadoutAndNoStoryIntro() {
        enterPractice(1);
        assertEquals("练功固定三命", GameWorld.PRACTICE_LIVES, world.lives);
        assertEquals("练功固定满档火力", GameWorld.PRACTICE_START_POWER,
                world.ship.powerLevel);
        assertEquals("不播开场航段", 0f, world.stageIntroTimer, 0.001f);
        assertEquals("开场很快就有船", GameWorld.PRACTICE_OPENING,
                world.bossGapTimer, 0.001f);
    }

    // ---- 练功房规则 ----

    @Test
    public void onlyTheTargetFlagshipSpawns() {
        world.spawningEnabled = true;
        world.mutatorsEnabled = true;
        world.bountyEnabled = true;
        world.perksEnabled = true;
        enterPractice(2);
        // 把 Boss 按住不让它入场：剩下的时间只可能属于小怪/子系统
        world.bossGapTimer = 999f;
        world.altarNextAt = 0f;
        world.escortTimer = 0f;
        world.bountyNextAt = 0f;
        step(180);

        assertEquals("模拟战不刷常规敌机", 0, world.spawnedCount);
        assertEquals("不刷祭坛", 0, world.altarsSpawned);
        assertEquals("不刷护航", 0, world.escortsSaved + world.escortsLost);
        assertFalse(world.escort.active);
        assertEquals("不掷异变", Mutator.NONE, world.mutator);
        assertEquals("没有小怪可悬赏", 0, world.bountiesClaimed + world.bountiesFailed);
        boolean anyEnemy = false;
        for (Enemy e : world.enemies) if (e.active) anyEnemy = true;
        assertFalse(anyEnemy);
    }

    @Test
    public void theChosenFlagshipArrivesWithoutAnAffix() {
        enterPractice(3);
        waitForBoss();
        assertEquals("档位对应同一艘船", 3, world.boss.tier);
        assertEquals("练功按该档首个形态的数值", 3, world.boss.level);
        assertEquals("练功不掷词缀", BossAffix.NONE, world.boss.affix);
    }

    @Test
    public void hitsCostNoLifeAndTheRunKeepsGoing() {
        enterPractice(0);
        waitForBoss();
        world.ship.invulnerable = false;
        world.ship.hasShield = false;
        int lives = world.lives;
        int deaths = world.settings.deaths;

        for (int i = 0; i < 4; i++) {
            world.ship.invulnerable = false;
            world.ship.hasShield = false;
            world.enemyBullets[0].init(world.ship.x, world.ship.y, 0f, 0f);
            world.update(1f / 60f);
        }

        assertEquals("模拟战不掉命", lives, world.lives);
        assertEquals("也不记阵亡", deaths, world.settings.deaths);
        assertEquals(GameWorld.State.PLAYING, world.state);
        assertEquals(4, world.hitsTaken);
    }

    @Test
    public void defeatingTheBossBringsTheSameOneBack() {
        enterPractice(1);
        waitForBoss();
        int tier = world.boss.tier;
        killBoss();

        assertEquals(1, world.practiceKills);
        assertTrue("应该记下最快击破", world.practiceBestClear > 0f);
        assertEquals("模拟战不推进航段", 0, world.bossesDefeated);
        assertEquals("击破后歇一口气", GameWorld.PRACTICE_GAP, world.bossGapTimer, 0.001f);
        assertEquals("模拟战不掉养成资源", 0, activePowerUps());
        assertFalse("不放强化卡", world.perkOpen);
        assertEquals("不触发换关", 0, countEvents(GameWorld.EventType.STAGE_CLEAR));
        assertFalse(world.boss.active);

        step(150);
        assertTrue("同一艘船应该回来", world.boss.active);
        assertEquals(tier, world.boss.tier);
        assertEquals(1, world.boss.level);
        assertEquals("新一轮计时从 0 开始", 0f, world.practiceBossTime, 0.001f);
    }

    @Test
    public void theClockMeasuresFightingNotTheWarning() {
        enterPractice(0);
        waitForBoss();
        step(120);
        assertEquals("两秒实战计时", 2f, world.practiceBossTime, 0.05f);
        world.bossWarningTimer = 2f;   // 人为回到预警：计时不该跟着走
        float before = world.practiceBossTime;
        step(60);
        assertEquals("预警时间不计入击破用时", before, world.practiceBossTime, 0.001f);
    }

    // ---- 不写账本 ----

    @Test
    public void practiceWritesNothingToBoardsOrTheCodex() {
        world.settings.codexBossKills[2] = 3;
        world.bestScore = 12345;
        world.board(GameMode.WAVES)[0] = 500;
        world.settings.storyUnlocked = 4;
        long achieveMask = world.settings.achieveMask;
        int runsStarted = world.settings.runsStarted;

        enterPractice(2);
        assertEquals("模拟战不算正式出勤", runsStarted, world.settings.runsStarted);
        waitForBoss();
        killBoss();
        step(30);

        assertEquals("图鉴击坠数不被练功刷高", 3, world.settings.codexBossKills[2]);
        assertEquals("战役最高分不动", 12345, world.bestScore);
        assertEquals("分数榜不动", 500, world.board(GameMode.WAVES)[0]);
        assertEquals("剧情不解锁", 4, world.settings.storyUnlocked);
        assertEquals("成就账本不动", achieveMask, world.settings.achieveMask);
        assertEquals(-1, world.scoreRank);
        assertFalse(world.newBest);

        world.handleUiAction(Ui.Action.QUIT_TO_MENU);
        assertEquals(GameWorld.State.MENU, world.state);
        assertEquals("回菜单后清掉练功目标", -1, world.practiceTier);
        assertEquals("退出也不补记出勤", runsStarted, world.settings.runsStarted);
    }

    @Test
    public void restartKeepsTheTargetButResetsTheSessionReadout() {
        enterPractice(1);
        waitForBoss();
        killBoss();
        assertEquals(1, world.practiceKills);

        world.restart(GameMode.PRACTICE);
        assertEquals("重开还是练同一艘", 1, world.practiceTier);
        assertEquals(0, world.practiceKills);
        assertEquals(0f, world.practiceBestClear, 0.001f);
        assertFalse(world.boss.active);

        world.restart(GameMode.WAVES);
        assertEquals("切回正式局就清掉目标", -1, world.practiceTier);
        assertFalse(world.practice());
        assertNotEquals(GameMode.PRACTICE, world.mode);
    }
}
