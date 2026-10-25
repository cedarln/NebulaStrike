package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.32：主线档案。
 *
 * <p>剧情不是贴在菜单里的装饰：解锁进度跟着战役推进（击破一位 Boss 解密下一章），
 * 只增不减并落盘；面板可以回看已解密章节，也能预览锁定章节的解密条件。
 */
public class StoryV132Test {
    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(20261025L);
        world.state = GameWorld.State.MENU;   // 面板相关断言都发生在主菜单
        world.lives = 999_999;
        world.ship.x = 450f;
        world.ship.prevX = 450f;
        world.ship.y = 1460f;
        world.ship.prevY = 1460f;
    }

    private void triggerBoss() {
        world.state = GameWorld.State.PLAYING;
        world.bossGapTimer = 0f;
        world.nextBossWave = 4;
        world.wave = 4;
        world.update(1f / 60f);
    }

    private boolean hasEvent(GameWorld.EventType type) {
        for (GameWorld.GameEvent e : world.pollEvents()) {
            if (e.type == type) return true;
        }
        return false;
    }

    private int eventCode(GameWorld.EventType type) {
        for (GameWorld.GameEvent e : world.pollEvents()) {
            if (e.type == type) return e.code;
        }
        return -1;
    }

    @Test
    public void storyDataCoversWholeCampaign() {
        assertEquals(9, Story.CHAPTER_COUNT);
        assertEquals("9", Story.total());
        for (int i = 0; i < Story.CHAPTER_COUNT; i++) {
            assertFalse(Story.title(i).isEmpty());
            assertFalse(Story.tag(i).isEmpty());
            assertEquals("每章三段正文", 3, Story.body(i).length);
            for (String paragraph : Story.body(i)) {
                assertFalse(paragraph.isEmpty());
            }
            assertEquals(i == Story.CHAPTER_COUNT - 1, Story.isEnding(i));
            assertFalse(Story.unlockHint(i).isEmpty());
        }
        assertEquals("", Story.title(-1));
        assertEquals("", Story.title(Story.CHAPTER_COUNT));
        assertEquals(0, Story.body(Story.CHAPTER_COUNT).length);
    }

    @Test
    public void sectorAndBossMappingUnlocksOneChapterPerSector() {
        assertEquals(1, Story.chaptersForSector(0));
        assertEquals(1, Story.chaptersForSector(1));
        assertEquals(5, Story.chaptersForSector(5));
        assertEquals(8, Story.chaptersForSector(8));
        assertEquals(Story.CHAPTER_COUNT, Story.chaptersForSector(9));
        assertEquals("越界封顶", Story.CHAPTER_COUNT, Story.chaptersForSector(99));

        assertEquals(1, Story.chaptersForBosses(0));
        assertEquals(2, Story.chaptersForBosses(1));
        assertEquals(Story.CHAPTER_COUNT, Story.chaptersForBosses(8));
        assertEquals(Story.CHAPTER_COUNT, Story.chaptersForBosses(999));
        assertEquals(Story.title(1), Story.sectorTitle(2));
    }

    @Test
    public void unlockProgressOnlyMovesForward() {
        Settings s = new Settings();
        assertEquals(1, s.storyUnlocked);
        assertTrue(s.noteStory(3));
        assertEquals(3, s.storyUnlocked);
        assertFalse("重复解锁不算新进度", s.noteStory(2));
        assertEquals(3, s.storyUnlocked);
        assertTrue(s.noteStory(99));
        assertEquals(Story.CHAPTER_COUNT, s.storyUnlocked);
        assertFalse(s.noteStory(0));
    }

    @Test
    public void sanitizeClampsCorruptStoryProgress() {
        Settings s = new Settings();
        s.storyUnlocked = 999;
        s.sanitize();
        assertEquals(Story.CHAPTER_COUNT, s.storyUnlocked);
        s.storyUnlocked = -5;
        s.sanitize();
        assertEquals(1, s.storyUnlocked);
    }

    @Test
    public void menuStoryEntryFitsBesideUpdateButton() {
        Ui.Rect story = Ui.STORY;
        Ui.Rect update = Ui.CHECK_UPDATE;
        assertFalse("两个入口不能重叠",
                story.x < update.x + update.w && update.x < story.x + story.w
                        && story.y < update.y + update.h && update.y < story.y + story.h);
        assertTrue(story.x >= 0f);
        assertTrue(update.x + update.w <= GameWorld.WIDTH);
        assertTrue("入口要落在菜单按钮区上方的最佳分行",
                story.y >= Ui.CAREER.y + Ui.CAREER.h);
        assertTrue("不能压到 BEST 那一行", story.y + story.h <= 800f);
    }

    @Test
    public void storyPanelOpensOnLatestUnlockedChapter() {
        world.settings.storyUnlocked = 4;
        world.handleUiAction(Ui.Action.OPEN_STORY);
        assertTrue(world.storyOpen);
        assertEquals("默认停在刚解密的第 4 章", 3, world.storyPage);

        world.storyPage = 0;
        world.handleUiAction(Ui.Action.STORY_PREV);
        assertEquals("第一页不能再往前", 0, world.storyPage);
        for (int i = 0; i < Story.CHAPTER_COUNT + 5; i++) {
            world.handleUiAction(Ui.Action.STORY_NEXT);
        }
        assertEquals(Story.CHAPTER_COUNT - 1, world.storyPage);

        world.handleUiAction(Ui.Action.STORY_BACK);
        assertFalse(world.storyOpen);
    }

    @Test
    public void storyPanelIsExclusiveWithOtherPanels() {
        world.handleUiAction(Ui.Action.OPEN_STORY);
        assertTrue(world.storyOpen);
        world.handleUiAction(Ui.Action.OPEN_SETTINGS);
        assertFalse(world.storyOpen);
        assertTrue(world.settingsOpen);

        world.handleUiAction(Ui.Action.STORY_BACK);
        world.handleUiAction(Ui.Action.OPEN_STORY);
        world.handleUiAction(Ui.Action.OPEN_CODEX);
        assertFalse(world.storyOpen);

        world.handleUiAction(Ui.Action.OPEN_STORY);
        world.closePanels();
        assertFalse(world.storyOpen);
    }

    @Test
    public void backKeyClosesStoryPanel() {
        world.handleUiAction(Ui.Action.OPEN_STORY);
        assertTrue(world.consumeBack());
        assertFalse(world.storyOpen);
    }

    @Test
    public void storyPanelSwallowsMenuTaps() {
        world.settings.storyUnlocked = 2;
        world.handleUiAction(Ui.Action.OPEN_STORY);
        assertNull("面板打开时不能误触 CAMPAIGN",
                Ui.hit(world.state, false, false, false, false, false, true, 450f, 400f));
        assertEquals(Ui.Action.STORY_BACK, Ui.hit(world.state, false, false, false, false,
                false, true, Ui.STORY_BACK.cx(), Ui.STORY_BACK.cy()));
        assertEquals(Ui.Action.STORY_PREV, Ui.hit(world.state, false, false, false, false,
                false, true, Ui.STORY_PREV.cx(), Ui.STORY_PREV.cy()));
        assertEquals(Ui.Action.STORY_NEXT, Ui.hit(world.state, false, false, false, false,
                false, true, Ui.STORY_NEXT.cx(), Ui.STORY_NEXT.cy()));
        assertEquals("菜单里 STORY 入口可点",
                Ui.Action.OPEN_STORY,
                Ui.hit(world.state, false, false, false, false, false, false,
                        Ui.STORY.cx(), Ui.STORY.cy()));
    }

    @Test
    public void bossDefeatUnlocksNextChapter() {
        world.settings.storyUnlocked = 1;
        triggerBoss();
        world.boss.hp = 0f;
        world.update(1f / 60f);

        assertEquals("第一关打完解密第二章", 2, world.settings.storyUnlocked);
        assertEquals(2, eventCode(GameWorld.EventType.STORY_UNLOCK));
        assertTrue("解锁时弹横幅", world.storyBanner > 0f);
        assertEquals(2, world.storyBannerChapter);
        assertTrue("解锁要落盘", world.codexDirty);
    }

    @Test
    public void bossDefeatDoesNotReAnnounceKnownChapter() {
        world.settings.storyUnlocked = Story.CHAPTER_COUNT;
        triggerBoss();
        world.boss.hp = 0f;
        world.update(1f / 60f);
        assertFalse("已读完的档案不重复弹", hasEvent(GameWorld.EventType.STORY_UNLOCK));
        assertEquals(0f, world.storyBanner, 0.0001f);
        assertEquals(Story.CHAPTER_COUNT, world.settings.storyUnlocked);
    }

    @Test
    public void storyBannerTimesOut() {
        world.settings.storyUnlocked = 1;
        triggerBoss();
        world.boss.hp = 0f;
        world.update(1f / 60f);
        assertTrue(world.storyBanner > 0f);
        for (int i = 0; i < 60 * 6; i++) world.update(1f / 60f);
        assertEquals(0f, world.storyBanner, 0.0001f);
    }

    @Test
    public void browsingLockedChaptersNeverUnlocksThem() {
        world.settings.storyUnlocked = 1;
        world.handleUiAction(Ui.Action.OPEN_STORY);
        for (int i = 0; i < Story.CHAPTER_COUNT; i++) {
            world.handleUiAction(Ui.Action.STORY_NEXT);
            assertNotNull(Story.unlockHint(world.storyPage));
        }
        assertEquals("看不能代替打", 1, world.settings.storyUnlocked);
    }
}
