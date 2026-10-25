package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.39 迭代 29：章节战役与关卡标识。
 *
 * <p>以前"第几关"只是屏幕左边一个数字，剧情档案和实际航程是两套叙事。
 * 现在每段航程都有编号、标题与旗舰代号：HUD 常驻、Boss 预警、肃清横幅全部对齐；
 * 肃清横幅收起后再补一段不暂停刷新的换段开场，让"下一关"有一次看得见的交接。
 */
public class SectorV139Test {

    private GameWorld world;

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
    }

    private void step(int frames) {
        for (int i = 0; i < frames; i++) world.update(1f / 60f);
    }

    @Test
    public void sectorsNeverRunPastTheLastOne() {
        assertEquals(0, Story.sectorIndex(0));
        assertEquals(0, Story.sectorIndex(1));
        assertEquals(2, Story.sectorIndex(3));
        assertEquals(7, Story.sectorIndex(8));
        assertEquals("飞过第八段后停在星核", 7, Story.sectorIndex(9));
        assertEquals(7, Story.sectorIndex(999));
        assertEquals(Story.SECTOR_COUNT, Story.SECTOR_COUNT);
    }

    @Test
    public void everySectorHasItsOwnNameAndFlagShip() {
        Set<String> names = new HashSet<>();
        for (int s = 1; s <= Story.SECTOR_COUNT; s++) {
            assertTrue(Story.sectorShort(s).startsWith("SECTOR " + s + "/8"));
            assertTrue("完整标签要带上该段标题",
                    Story.sectorTag(s).endsWith(Story.title(s - 1)));
            assertEquals(Story.title(s - 1), Story.sectorName(s));
            String boss = Story.bossName(s);
            assertFalse(boss.isEmpty());
            assertTrue("每位旗舰都要有独立代号", names.add(boss));
        }
        assertEquals("八位旗舰各不相同", Story.SECTOR_COUNT, names.size());
        assertEquals(Story.bossName(1), Story.bossName(0));
        assertEquals(Story.bossName(Story.SECTOR_COUNT),
                Story.bossName(Story.SECTOR_COUNT + 3));
        assertEquals("SECTOR 3/8 · 复制", Story.sectorShort(3));
        assertEquals("SECTOR 3/8 · 断链船坞", Story.sectorTag(3));
        assertEquals("SECTOR 8/8 · 星核之环", Story.sectorTag(9));
    }

    @Test
    public void runOpensWithTheFirstSectorIntro() {
        world.restart(GameMode.WAVES);
        assertEquals(GameWorld.STAGE_INTRO_TIME, world.stageIntroTimer, 0.0001f);
        assertEquals(1, world.stageIntroStage);
        step(30);
        assertTrue("开场横幅还没走完", world.stageIntroTimer > 0f);
        assertFalse("开场提示不暂停刷新", world.inInterlude());
        step(60 * 3);
        assertEquals(0f, world.stageIntroTimer, 0.0001f);
    }

    @Test
    public void clearingASectorHandsOverToTheNextIntro() {
        world.restart(GameMode.WAVES);
        world.stageIntroTimer = 0f;
        world.bossesDefeated = 1;          // 第一段已肃清，当前是第二段
        world.stageClearTimer = 0.02f;     // 肃清横幅只剩最后一点
        world.bossGapTimer = 30f;          // 下一位 Boss 还早，别抢戏
        step(6);
        assertEquals(2, world.stageIntroStage);
        assertTrue(world.stageIntroTimer > 1f);
        assertFalse("换段开场不能让战场停摆", world.inInterlude());
    }

    @Test
    public void introIsShownOncePerSectorTransition() {
        world.restart(GameMode.WAVES);
        world.stageIntroTimer = 0f;
        world.bossesDefeated = 2;
        world.stageClearTimer = 0.02f;
        world.bossGapTimer = 30f;
        step(2);
        float first = world.stageIntroTimer;
        assertTrue(first > 0f);
        step(10);
        assertTrue("只该往下走，不会每帧重置", world.stageIntroTimer < first);
        for (int i = 0; i < 240; i++) {
            world.update(1f / 60f);
            assertTrue("换段开场不会自我续期", world.stageIntroTimer <= first);
        }
    }

    @Test
    public void bossRushSkipsTheSectorIntro() {
        world.restart(GameMode.RUSH);
        assertEquals("连战局已有自己的入场横幅", 0f, world.stageIntroTimer, 0.0001f);
        world.bossesDefeated = 1;
        world.stageClearTimer = 0.02f;
        step(6);
        assertEquals("连战局不叠换段开场", 0f, world.stageIntroTimer, 0.0001f);
    }

    @Test
    public void restartRewindsTheSectorClock() {
        world.restart(GameMode.WAVES);
        world.bossesDefeated = 5;
        world.stageIntroTimer = 0f;
        world.stageIntroStage = 6;
        world.restart(GameMode.WAVES);
        assertEquals(1, world.stageIntroStage);
        assertEquals(GameWorld.STAGE_INTRO_TIME, world.stageIntroTimer, 0.0001f);
        assertEquals(1, world.stageNumber());
    }
}
