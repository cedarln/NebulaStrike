package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.50 迭代 40：回声档案 · 后日谈。
 *
 * <p>回声周目掉落的碎片不只是"分数"，还能在剧情面板的第二个页签里逐段解密后日谈。
 * 前六段顺序解锁、越往后越贵；第七段是隐藏档案，免费，但要前六段全解且至少通关一次回声周目。
 */
public class EchoArchiveV150Test {

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
        world.settings.campaignCleared = true;
    }

    private void openArchiveTab() {
        world.handleUiAction(Ui.Action.OPEN_STORY);
        world.handleUiAction(Ui.Action.STORY_TAB_ECHO);
    }

    private int eventCode(GameWorld.EventType type) {
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i).type == type) return events.get(i).code;
        }
        return -1;
    }

    private boolean hasEvent(GameWorld.EventType type) {
        for (GameWorld.GameEvent e : events) {
            if (e.type == type) return true;
        }
        return false;
    }

    private static final class RecordingStore implements SettingsStore {
        Settings saved;
        int saves;

        @Override
        public void load(Settings into) {
            into.campaignCleared = true;
        }

        @Override
        public int bestScore() {
            return 0;
        }

        @Override
        public void saveBestScore(int score) {
        }

        @Override
        public int[] leaderboard(GameMode mode) {
            return new int[5];
        }

        @Override
        public void saveLeaderboard(GameMode mode, int[] scores) {
        }

        @Override
        public void save(Settings settings) {
            saved = settings;
            saves++;
        }
    }

    @Test
    public void archiveDataIsCoherent() {
        assertEquals("七段后日谈（六段正篇 + 一段隐藏）", 7, EchoArchive.COUNT);
        assertEquals("前六段总费用 2+3+4+5+6+8", 28, EchoArchive.totalCost());
        assertEquals("隐藏档案不是商品", 0, EchoArchive.cost(EchoArchive.COUNT - 1));
        assertTrue(EchoArchive.hidden(EchoArchive.COUNT - 1));
        assertFalse(EchoArchive.hidden(0));

        for (int i = 0; i < EchoArchive.COUNT; i++) {
            assertFalse("标签不能为空：" + i, EchoArchive.tag(i).isEmpty());
            assertFalse("标题不能为空：" + i, EchoArchive.title(i).isEmpty());
            assertTrue("每段至少一段正文：" + i, EchoArchive.body(i).length >= 2);
            for (String line : EchoArchive.body(i)) {
                assertFalse("正文不能有空行：" + i, line.trim().isEmpty());
            }
            if (i > 0 && !EchoArchive.hidden(i)) {
                assertTrue("越往后越贵（隐藏档案除外）", EchoArchive.cost(i) > EchoArchive.cost(i - 1));
            }
        }

        assertEquals("越界查询要安全", "", EchoArchive.tag(-1));
        assertEquals("", EchoArchive.title(EchoArchive.COUNT));
        assertEquals(0, EchoArchive.cost(99));
        assertEquals(0, EchoArchive.body(-1).length);
        assertFalse(EchoArchive.reachable(0L, 99, 1));
        assertEquals("越界置位不改掩码", 0L, EchoArchive.unlock(0L, -1));
        assertEquals(0L, EchoArchive.unlock(0L, EchoArchive.COUNT));
    }

    @Test
    public void fragmentsUnlockInOrder() {
        long mask = 0L;
        assertTrue("第一段随时可看", EchoArchive.reachable(mask, 0, 0));
        assertFalse("第二段要先解第一段", EchoArchive.reachable(mask, 1, 0));

        mask = EchoArchive.unlock(mask, 0);
        assertTrue(EchoArchive.unlocked(mask, 0));
        assertEquals(1, EchoArchive.unlockedCount(mask));
        assertTrue(EchoArchive.reachable(mask, 1, 0));
        assertFalse("不能跳级", EchoArchive.reachable(mask, 2, 0));

        assertFalse("隐藏档案要先凑齐六段", EchoArchive.reachable(mask, EchoArchive.COUNT - 1, 5));
        for (int i = 0; i < EchoArchive.COUNT - 1; i++) mask = EchoArchive.unlock(mask, i);
        assertFalse("六段齐了但没通关过回声周目", EchoArchive.reachable(mask, EchoArchive.COUNT - 1, 0));
        assertTrue("通关一次后隐藏档案出现", EchoArchive.reachable(mask, EchoArchive.COUNT - 1, 1));
        assertFalse(EchoArchive.complete(mask));
        mask = EchoArchive.unlock(mask, EchoArchive.COUNT - 1);
        assertTrue(EchoArchive.complete(mask));
    }

    @Test
    public void unlockingSpendsShardsAndPersistsImmediately() {
        RecordingStore store = new RecordingStore();
        world.attachStore(store);
        world.settings.echoShards = 5;
        openArchiveTab();
        world.echoArchivePage = 0;

        assertTrue(world.echoArchiveCanUnlock());
        world.handleUiAction(Ui.Action.STORY_ARCHIVE_UNLOCK);
        events.addAll(world.pollEvents());

        assertEquals("第一段花 2 枚碎片", 3, world.settings.echoShards);
        assertTrue(EchoArchive.unlocked(world.settings.echoArchiveMask, 0));
        assertTrue(hasEvent(GameWorld.EventType.ECHO_ARCHIVE_UNLOCK));
        assertEquals("事件带上片段序号", 0, eventCode(GameWorld.EventType.ECHO_ARCHIVE_UNLOCK));
        assertNotNull("解密后立即落盘", store.saved);
        assertEquals(1, store.saves);
        assertSame(world.settings, store.saved);

        // 再次点击同一段：已解密，不再扣费
        world.handleUiAction(Ui.Action.STORY_ARCHIVE_UNLOCK);
        assertEquals(3, world.settings.echoShards);
        assertEquals(1, store.saves);
    }

    @Test
    public void insufficientShardsOrLockedPagesDoNothing() {
        RecordingStore store = new RecordingStore();
        world.attachStore(store);
        openArchiveTab();
        world.echoArchivePage = 0;
        world.settings.echoShards = 1;
        events.addAll(world.pollEvents());

        assertFalse(world.echoArchiveCanUnlock());
        world.handleUiAction(Ui.Action.STORY_ARCHIVE_UNLOCK);
        assertEquals(1, world.settings.echoShards);
        assertEquals(0L, world.settings.echoArchiveMask);
        assertEquals(0, store.saves);

        // 余额够了但还没解上一段：第二段仍然锁着
        world.settings.echoShards = 99;
        world.echoArchivePage = 1;
        assertFalse(world.echoArchiveCanUnlock());
        world.handleUiAction(Ui.Action.STORY_ARCHIVE_UNLOCK);
        assertEquals(99, world.settings.echoShards);
        assertFalse(EchoArchive.unlocked(world.settings.echoArchiveMask, 1));

        // 主线页签上的解密按钮不生效
        world.echoArchivePage = 0;
        world.handleUiAction(Ui.Action.STORY_TAB_MAIN);
        world.handleUiAction(Ui.Action.STORY_ARCHIVE_UNLOCK);
        assertEquals(99, world.settings.echoShards);
        assertEquals(0L, world.settings.echoArchiveMask);
        events.addAll(world.pollEvents());
        assertFalse(hasEvent(GameWorld.EventType.ECHO_ARCHIVE_UNLOCK));
    }

    @Test
    public void hiddenFragmentIsFreeButNeedsACompletedEchoRun() {
        world.settings.echoArchiveMask = (1L << (EchoArchive.COUNT - 1)) - 1L;
        world.settings.echoShards = 0;
        openArchiveTab();
        world.echoArchivePage = EchoArchive.COUNT - 1;

        assertFalse("没有通关记录时隐藏档案仍锁着", world.echoArchiveCanUnlock());
        world.handleUiAction(Ui.Action.STORY_ARCHIVE_UNLOCK);
        assertFalse(EchoArchive.unlocked(world.settings.echoArchiveMask, EchoArchive.COUNT - 1));

        // 打穿一次回声周目后自动开放，且不花碎片
        world.settings.echoClears = 1;
        assertTrue(world.echoArchiveCanUnlock());
        world.handleUiAction(Ui.Action.STORY_ARCHIVE_UNLOCK);
        assertTrue(EchoArchive.unlocked(world.settings.echoArchiveMask, EchoArchive.COUNT - 1));
        assertEquals("隐藏档案免费", 0, world.settings.echoShards);
        assertTrue(EchoArchive.complete(world.settings.echoArchiveMask));
    }

    @Test
    public void storyTabsNavigateIndependently() {
        world.settings.storyUnlocked = 3;
        world.handleUiAction(Ui.Action.OPEN_STORY);
        assertEquals("默认停在最新解密的一章", 2, world.storyPage);
        assertEquals("默认是主线页签", 0, world.storyTab);

        world.handleUiAction(Ui.Action.STORY_TAB_ECHO);
        assertEquals(1, world.storyTab);
        int pageBefore = world.storyPage;

        world.handleUiAction(Ui.Action.STORY_NEXT);
        assertEquals(1, world.echoArchivePage);
        assertEquals("翻档案不动主线页码", pageBefore, world.storyPage);

        world.handleUiAction(Ui.Action.STORY_PREV);
        assertEquals(0, world.echoArchivePage);

        for (int i = 0; i < 20; i++) world.handleUiAction(Ui.Action.STORY_NEXT);
        assertEquals("封顶在最后一段", EchoArchive.COUNT - 1, world.echoArchivePage);
        for (int i = 0; i < 20; i++) world.handleUiAction(Ui.Action.STORY_PREV);
        assertEquals("下限是第一段", 0, world.echoArchivePage);
        assertEquals(pageBefore, world.storyPage);

        world.handleUiAction(Ui.Action.STORY_TAB_MAIN);
        world.handleUiAction(Ui.Action.STORY_NEXT);
        assertNotEquals("主线页签继续翻章节", pageBefore, world.storyPage);

        world.handleUiAction(Ui.Action.STORY_BACK);
        assertFalse(world.storyOpen);
    }

    @Test
    public void archiveSurvivesSanitizeAndStore() {
        Settings s = new Settings();
        s.echoArchiveMask = -1L;
        s.echoShards = -4;
        s.echoClears = -1;
        s.sanitize();
        assertEquals("多余的位要裁掉", (1L << EchoArchive.COUNT) - 1L, s.echoArchiveMask);
        assertEquals(0, s.echoShards);
        assertEquals(0, s.echoClears);

        s = new Settings();
        s.echoArchiveMask = EchoArchive.unlock(0L, 1);
        s.echoShards = 6;
        s.echoClears = 2;
        Settings copy = new Settings();
        copy.echoArchiveMask = s.echoArchiveMask;
        copy.echoShards = s.echoShards;
        copy.echoClears = s.echoClears;
        copy.sanitize();
        assertEquals(s.echoArchiveMask, copy.echoArchiveMask);
        assertEquals(6, copy.echoShards);
        assertEquals(2, copy.echoClears);
    }

    @Test
    public void storyPanelHitTargetsTheArchiveControls() {
        assertEquals(Ui.Action.STORY_TAB_MAIN, hitStory(Ui.STORY_TAB_MAIN.cx(), Ui.STORY_TAB_MAIN.cy()));
        assertEquals(Ui.Action.STORY_TAB_ECHO, hitStory(Ui.STORY_TAB_ECHO.cx(), Ui.STORY_TAB_ECHO.cy()));
        assertEquals(Ui.Action.STORY_ARCHIVE_UNLOCK,
                hitStory(Ui.STORY_ARCHIVE_UNLOCK.cx(), Ui.STORY_ARCHIVE_UNLOCK.cy()));
        assertEquals(Ui.Action.STORY_PREV, hitStory(Ui.STORY_PREV.cx(), Ui.STORY_PREV.cy()));
        assertEquals(Ui.Action.STORY_NEXT, hitStory(Ui.STORY_NEXT.cx(), Ui.STORY_NEXT.cy()));
        assertEquals(Ui.Action.STORY_BACK, hitStory(Ui.STORY_BACK.cx(), Ui.STORY_BACK.cy()));

        Ui.Rect row = Ui.storyArchiveRow(3);
        assertEquals(680f, row.w, 0.001f);
        assertEquals(Ui.STORY_ARCHIVE_ROW_Y + 3 * Ui.STORY_ARCHIVE_ROW_H, row.y, 0.001f);
        assertEquals("行与行之间留缝", Ui.STORY_ARCHIVE_ROW_H - 6f, row.h, 0.001f);
        Ui.Rect last = Ui.storyArchiveRow(EchoArchive.COUNT - 1);
        assertTrue("最后一行要落在详情卡上方", last.y + last.h <= 790f);

        assertNull("面板打开时空白处不接点击",
                hitStory(Ui.STORY_TAB_ECHO.cx(), Ui.STORY_ARCHIVE_ROW_Y + 700f));
    }

    private Ui.Action hitStory(float x, float y) {
        return Ui.hit(GameWorld.State.MENU, false, false, false, false, false, true, x, y);
    }
}
