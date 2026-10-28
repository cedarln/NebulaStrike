package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.51 迭代 41：机体选择 HULL。
 *
 * <p>三架机体只改基础手感与起手补给：游隼更快更灵但火力稀，壁垒火力足、起手多炸弹多护盾
 * 但转向慢，星火居中。它们不碰任何道具叠加规则——捡到的每一件道具照样叠。
 */
public class HullV151Test {

    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(20261028L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.bountyEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.lives = 999_999;
    }

    private static final class RecordingStore implements SettingsStore {
        Settings saved;
        int saves;

        @Override
        public void load(Settings into) {
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
    public void threeHullsHaveDistinctTradeoffs() {
        assertEquals(3, Hull.COUNT);
        assertEquals(Hull.SPARK, Hull.of(-4));
        assertEquals(Hull.SPARK, Hull.of(0));
        assertEquals(Hull.FALCON, Hull.of(1));
        assertEquals(Hull.BULWARK, Hull.of(2));
        assertEquals("越界回落到最后一架", Hull.BULWARK, Hull.of(99));

        assertTrue(Hull.FALCON.speedMul > Hull.SPARK.speedMul);
        assertTrue(Hull.BULWARK.speedMul < Hull.SPARK.speedMul);
        assertTrue("壁垒开火更快", Hull.BULWARK.fireCdMul < Hull.SPARK.fireCdMul);
        assertTrue("游隼开火更慢", Hull.FALCON.fireCdMul > Hull.SPARK.fireCdMul);
        assertEquals(2, Hull.BULWARK.startBombs);
        assertEquals(1, Hull.BULWARK.startShield);
        assertEquals(0.8f, Hull.BULWARK.respawnBonus, 0.0001f);
        assertTrue(Hull.FALCON.dashRechargeMul > 1f);
        assertTrue("游隼冲刺充能最快",
                Hull.FALCON.dashRechargeMul > Hull.SPARK.dashRechargeMul
                        && Hull.SPARK.dashRechargeMul > Hull.BULWARK.dashRechargeMul);

        for (Hull h : Hull.values()) {
            assertFalse(h.label.isEmpty());
            assertFalse(h.tag.isEmpty());
            assertFalse(h.blurb.isEmpty());
            assertTrue(h.fireCdMul > 0f);
            assertTrue(h.speedMul > 0f);
        }
    }

    @Test
    public void menuChipsSwitchAndPersistImmediately() {
        GameWorld menu = new GameWorld(20261028L, true);
        RecordingStore store = new RecordingStore();
        menu.attachStore(store);
        assertEquals(GameWorld.State.MENU, menu.state);
        assertEquals(Hull.SPARK, menu.hull());

        Ui.Rect bulwark = Ui.hullChip(2);
        Ui.Action action = Ui.hit(GameWorld.State.MENU, false, false,
                bulwark.cx(), bulwark.cy());
        assertEquals(Ui.Action.HULL_BULWARK, action);
        menu.handleUiAction(action);
        assertEquals(2, menu.settings.hull);
        assertEquals(Hull.BULWARK, menu.hull());
        assertEquals("换机体立即落盘", 1, store.saves);
        assertNotNull(store.saved);

        // 点同一架：不再重复写盘
        menu.handleUiAction(action);
        assertEquals(1, store.saves);

        Ui.Rect falcon = Ui.hullChip(1);
        menu.handleUiAction(Ui.hit(GameWorld.State.MENU, false, false,
                falcon.cx(), falcon.cy()));
        assertEquals(1, menu.settings.hull);
        assertEquals(2, store.saves);
        assertEquals("换机体不开局", GameWorld.State.MENU, menu.state);
    }

    @Test
    public void hullIsAppliedOnRestart() {
        world.settings.hull = Hull.BULWARK.ordinal();
        world.restart(GameMode.WAVES);
        assertEquals(Hull.BULWARK, world.hull());
        assertEquals("重装机体移速更慢", Hull.BULWARK.speedMul, world.ship.speedScale, 0.0001f);
        assertEquals("开局两枚炸弹", 2, world.ship.bombs);
        assertEquals("开局一层护盾", 1, world.ship.shieldLayers);
        assertTrue(world.ship.hasShield);

        world.settings.hull = Hull.FALCON.ordinal();
        world.restart(GameMode.WAVES);
        assertEquals(Hull.FALCON.speedMul, world.ship.speedScale, 0.0001f);
        assertEquals("游隼只带一枚炸弹", 1, world.ship.bombs);
        assertEquals("没有起手护盾", 0, world.ship.shieldLayers);
        assertFalse(world.ship.hasShield);
    }

    @Test
    public void dashRechargeAndSpeedScaleComeFromHull() {
        world.settings.hull = Hull.FALCON.ordinal();
        world.restart(GameMode.WAVES);
        world.update(1f / 60f);
        assertEquals("无圣物时纯机体倍率", Hull.FALCON.dashRechargeMul,
                world.ship.dashRechargeScale, 0.0001f);

        // 机体只改跟随速度，不碰冲刺瞬时速度
        world.ship.setTarget(world.ship.x + 400f, world.ship.y);
        float before = world.ship.x;
        world.update(1f / 60f);
        assertTrue("游隼确实更快跟上拖动目标", world.ship.x > before);
        assertEquals("冲刺速度是常量", Ship.DASH_SPEED, 3400f, 0.001f);
    }

    @Test
    public void hullChipsFitBetweenEchoPlaqueAndBottom() {
        Ui.Rect first = Ui.hullChip(0);
        Ui.Rect last = Ui.hullChip(Hull.COUNT - 1);
        assertTrue("机体条不能压住回声铭牌", first.y >= Ui.START_ECHO.y + Ui.START_ECHO.h);

        for (int i = 0; i < Hull.COUNT; i++) {
            Ui.Rect r = Ui.hullChip(i);
            assertEquals(Ui.HULL_CHIP_W, r.w, 0.001f);
            assertEquals(Ui.HULL_CHIP_H, r.h, 0.001f);
            assertTrue("卡片不越出左右安全区", r.x >= 80f && r.x + r.w <= 820f);
            assertTrue("卡片不越过屏幕底部", r.y + r.h <= 1550f);
            if (i > 0) {
                Ui.Rect prev = Ui.hullChip(i - 1);
                assertTrue("卡片之间不重叠", r.x >= prev.x + prev.w);
            }
        }
        assertTrue("三枚居中且等距", Math.abs((first.x + last.x + last.w) / 2f - 450f) < 0.01f);
    }

    @Test
    public void sanitizeClampsHullIndex() {
        Settings s = new Settings();
        s.hull = 42;
        s.sanitize();
        assertEquals(Hull.COUNT - 1, s.hull);
        s.hull = -7;
        s.sanitize();
        assertEquals(0, s.hull);
        s.hull = 1;
        s.sanitize();
        assertEquals(1, s.hull);
    }
}
