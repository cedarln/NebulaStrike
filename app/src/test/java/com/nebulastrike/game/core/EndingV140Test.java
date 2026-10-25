package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.40 迭代 30：最终旗舰与主线大结局。
 *
 * <p>战役模式的第 8 段不再循环普通 Boss：最终旗舰血量更厚、第一次打空会"终局重构"
 * 回血再战，击破后解锁终章并进入结局演出；通关状态永久落盘，重开后主菜单与剧情档案
 * 都能看到"已经通关"。无尽 / 日常 / 连战不触发结局，仍然按原来的无限队列推进。
 */
public class EndingV140Test {

    private GameWorld world;
    private final List<GameWorld.GameEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261025L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.bountyEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.lives = 999_999;
        world.ship.x = 450f;
        world.ship.prevX = 450f;
        world.ship.y = 1460f;
        world.ship.prevY = 1460f;
        // 飞到对应航段，然后立刻让 Boss 入场（不播长预警）
        world.restart(GameMode.WAVES);
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
        for (GameWorld.GameEvent e : events) {
            if (e.type == type) return e.code;
        }
        return -1;
    }

    private void killFinalBossTwice() {
        spawnBossAt(Story.SECTOR_COUNT - 1);
        world.boss.hp = 0f;
        world.update(1f / 60f);
        assertFalse("第一次打空不会直接结算", world.campaignVictory);
        world.boss.hp = 0f;
        world.update(1f / 60f);
    }

    @Test
    public void finalFlagshipOnlyGuardsTheCampaignsLastSector() {
        spawnBossAt(Story.SECTOR_COUNT - 1);
        assertTrue(world.finalBossFight);
        assertEquals("最终旗舰比常规第 8 位高两级",
                GameWorld.FINAL_BOSS_LEVEL, world.boss.level);
        assertEquals(420f + 200f * GameWorld.FINAL_BOSS_LEVEL, world.boss.maxHp, 0.001f);

        world.restart(GameMode.ENDLESS);
        spawnBossAt(Story.SECTOR_COUNT - 1);
        assertFalse("无尽模式没有终点，继续按常规队列循环", world.finalBossFight);
        assertEquals(Story.SECTOR_COUNT - 1, world.boss.level);

        world.restart(GameMode.RUSH);
        spawnBossAt(Story.SECTOR_COUNT - 1);
        assertFalse("连战模式同样不进入结局", world.finalBossFight);
    }

    @Test
    public void finalFlagshipRebirthsOnceThenStaysDown() {
        spawnBossAt(Story.SECTOR_COUNT - 1);
        for (EnemyBullet b : world.enemyBullets) b.active = false;
        world.enemyBullets[0].init(120f, 120f, 0f, 120f);

        world.boss.hp = 0f;
        world.update(1f / 60f);
        drainEvents();
        assertTrue(world.boss.active);
        assertTrue(world.finalBossRebirthed);
        assertEquals(world.boss.maxHp * 0.35f, world.boss.hp, 0.001f);
        assertTrue("重构时清掉满屏弹幕", !world.enemyBullets[0].active);
        assertTrue(world.bossPhaseBreakTimer > 0f);
        assertTrue(hasEvent(GameWorld.EventType.BOSS_REBIRTH));
        assertEquals("重构不算击破", Story.SECTOR_COUNT - 1, world.bossesDefeated);

        world.boss.hp = 0f;
        world.update(1f / 60f);
        assertFalse(world.boss.active);
        assertEquals(Story.SECTOR_COUNT, world.bossesDefeated);
    }

    @Test
    public void clearingTheCampaignUnlocksTheEndingAndPlaysIt() {
        world.settings.storyUnlocked = 1;
        world.score = 4321;
        world.bestScore = 0;
        killFinalBossTwice();
        drainEvents();

        assertTrue(world.campaignVictory);
        assertTrue(world.campaignFirstClear);
        assertEquals(GameWorld.State.GAME_OVER, world.state);
        assertEquals("首次通关要报给结算页", 1, eventCode(GameWorld.EventType.CAMPAIGN_CLEAR));
        assertEquals(Story.CHAPTER_COUNT, world.settings.storyUnlocked);
        assertTrue(world.settings.campaignCleared);
        assertTrue("成绩照常入账", world.newBest);
        assertEquals("最高分取通关时的总分", world.score, world.bestScore);
        assertTrue("击破最终旗舰的奖励分也算进去", world.score > 4321);

        assertTrue("结局演出先播", world.endingActive());
        for (int i = 0; i < 60 * 3; i++) world.update(1f / 60f);
        assertTrue("三段终章还没读完", world.endingActive());
        world.skipEnding();
        assertFalse(world.endingActive());
        assertEquals(GameWorld.ENDING_TIME, world.stateTimer, 0.0001f);
        for (int i = 0; i < 60; i++) world.update(1f / 60f);
        assertFalse(world.endingActive());
        assertEquals("演出结束后仍是通关结算", GameWorld.State.GAME_OVER, world.state);
    }

    @Test
    public void secondClearIsMarkedButKeepsTheArchive() {
        killFinalBossTwice();
        assertTrue(world.campaignFirstClear);

        world.restart(GameMode.WAVES);
        assertFalse("重开回到普通战役", world.campaignVictory);
        assertEquals("已解密的终章不会回收", Story.CHAPTER_COUNT, world.settings.storyUnlocked);
        assertTrue(world.settings.campaignCleared);

        // 首次通关的 CAMPAIGN_CLEAR 还在世界队列里：先排空，别让第二次结算读到旧的 code
        drainEvents();
        events.clear();
        killFinalBossTwice();
        drainEvents();
        assertFalse("再次通关不再是首次", world.campaignFirstClear);
        assertEquals(0, eventCode(GameWorld.EventType.CAMPAIGN_CLEAR));
        assertTrue(world.settings.campaignCleared);
    }

    @Test
    public void regularBossStillUsesTheNormalClearInterlude() {
        spawnBossAt(Story.SECTOR_COUNT - 2);
        assertFalse(world.finalBossFight);
        world.boss.hp = 0f;
        world.update(1f / 60f);
        drainEvents();

        assertFalse(world.campaignVictory);
        assertEquals(GameWorld.State.PLAYING, world.state);
        assertTrue("普通旗舰走肃清过场", world.stageClearTimer > 0f);
        assertTrue(hasEvent(GameWorld.EventType.STAGE_CLEAR));
        assertFalse(hasEvent(GameWorld.EventType.CAMPAIGN_CLEAR));
        assertEquals(Story.SECTOR_COUNT - 1, world.bossesDefeated);
    }

    @Test
    public void restartClearsTheVictoryState() {
        killFinalBossTwice();
        assertTrue(world.campaignVictory);
        world.restart(GameMode.WAVES);
        assertFalse(world.campaignVictory);
        assertFalse(world.campaignFirstClear);
        assertFalse(world.finalBossFight);
        assertFalse(world.finalBossRebirthed);
        assertFalse(world.endingActive());
        assertEquals(GameWorld.State.PLAYING, world.state);
    }
}
