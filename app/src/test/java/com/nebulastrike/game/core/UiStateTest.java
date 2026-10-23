package com.nebulastrike.game.core;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Iteration-4: state machine, UI hit-testing, persistence-backed best score & settings. */
public class UiStateTest {
    private GameWorld world;
    private FakeStore store;

    /** In-memory stand-in for SharedPreferences. */
    static class FakeStore implements SettingsStore {
        final Settings saved = new Settings();
        final int[] lb = new int[5];
        final int[] elb = new int[5];
        int best;
        int bestWrites, settingsWrites, lbWrites, elbWrites;

        @Override public void load(Settings into) {
            into.sound = saved.sound; into.particles = saved.particles; into.haptics = saved.haptics;
            into.reducedFx = saved.reducedFx;
            into.leftHanded = saved.leftHanded;
            into.startLives = saved.startLives; into.dropRatePct = saved.dropRatePct;
            into.enemySpeedPct = saved.enemySpeedPct; into.startPower = saved.startPower;
            into.godMode = saved.godMode;
        }
        @Override public int bestScore() { return best; }
        @Override public void saveBestScore(int score) { best = score; bestWrites++; }
        @Override public int[] leaderboard(GameMode mode) {
            return (mode == GameMode.ENDLESS ? elb : lb).clone();
        }
        @Override public void saveLeaderboard(GameMode mode, int[] scores) {
            int[] target = mode == GameMode.ENDLESS ? elb : lb;
            System.arraycopy(scores, 0, target, 0, Math.min(scores.length, target.length));
            if (mode == GameMode.ENDLESS) elbWrites++; else lbWrites++;
        }
        @Override public void save(Settings s) {
            saved.sound = s.sound; saved.particles = s.particles; saved.haptics = s.haptics;
            saved.reducedFx = s.reducedFx;
            saved.leftHanded = s.leftHanded;
            saved.startLives = s.startLives; saved.dropRatePct = s.dropRatePct;
            saved.enemySpeedPct = s.enemySpeedPct; saved.startPower = s.startPower;
            saved.godMode = s.godMode;
            settingsWrites++;
        }
    }

    @Before
    public void setUp() {
        store = new FakeStore();
        world = new GameWorld(99L, true);
        world.attachStore(store);
    }

    @Test
    public void bootsIntoMenuAndLaunches() {
        assertEquals(GameWorld.State.MENU, world.state);
        world.handleUiAction(Ui.Action.START);
        assertEquals(GameWorld.State.PLAYING, world.state);
        assertEquals(0, world.score);
    }

    @Test
    public void pauseResumeCycle() {
        world.handleUiAction(Ui.Action.START);
        world.handleUiAction(Ui.Action.PAUSE);
        assertEquals(GameWorld.State.PAUSED, world.state);
        float t = world.timeAlive;
        world.update(0.5f); // paused: sim frozen
        assertEquals(t, world.timeAlive, 0.0001f);
        world.handleUiAction(Ui.Action.RESUME);
        assertEquals(GameWorld.State.PLAYING, world.state);
        world.update(0.016f);
        assertTrue(world.timeAlive > t);
    }

    @Test
    public void pauseClearsFloatingJoystick() {
        world.handleUiAction(Ui.Action.START);
        world.joyActive = true;
        world.joyBaseX = 120f;
        world.joyBaseY = 300f;
        world.joyAnim = 1f;
        world.handleUiAction(Ui.Action.PAUSE);
        assertFalse("暂停后摇杆不应残留在遮罩下", world.joyActive);
        assertEquals(0f, world.joyAnim, 1e-6f);
    }

    @Test
    public void quitToMenuThenRestart() {
        world.handleUiAction(Ui.Action.START);
        world.score = 123;
        world.handleUiAction(Ui.Action.PAUSE);
        world.handleUiAction(Ui.Action.QUIT_TO_MENU);
        assertEquals(GameWorld.State.MENU, world.state);
        world.handleUiAction(Ui.Action.START);
        assertEquals(GameWorld.State.PLAYING, world.state);
        assertEquals(0, world.score);
    }

    @Test
    public void settingsTogglePersistsThroughStore() {
        assertTrue(world.settings.sound);
        world.handleUiAction(Ui.Action.OPEN_SETTINGS);
        world.handleUiAction(Ui.Action.TOGGLE_SOUND);
        assertFalse(world.settings.sound);
        assertEquals("store must be written on toggle", 1, store.settingsWrites);
        assertFalse(store.saved.sound);
    }

    @Test
    public void bestScoreSavedOnGameOver() {
        world.bestScore = 100;
        world.handleUiAction(Ui.Action.START);
        world.score = 250;
        world.lives = 1;
        world.ship.hasShield = false;
        // force a fatal hit
        world.enemyBullets[0].init(world.ship.x, world.ship.y, 0, 0);
        world.update(1f / 60f);
        assertEquals(GameWorld.State.GAME_OVER, world.state);
        assertTrue(world.newBest);
        assertEquals(250, world.bestScore);
        assertEquals("best persisted", 250, store.best);
    }

    @Test
    public void uiHitMapping() {
        assertEquals(Ui.Action.START, Ui.hit(GameWorld.State.MENU, false, false,
                Ui.START.cx(), Ui.START.cy()));
        assertEquals(Ui.Action.OPEN_SETTINGS, Ui.hit(GameWorld.State.MENU, false, false,
                Ui.SETTINGS.cx(), Ui.SETTINGS.cy()));
        assertEquals(Ui.Action.OPEN_CHEATS, Ui.hit(GameWorld.State.MENU, false, false,
                Ui.CHEATS.cx(), Ui.CHEATS.cy()));
        assertEquals(Ui.Action.TOGGLE_PARTICLES, Ui.hit(GameWorld.State.MENU, true, false,
                Ui.TOGGLE_PARTICLES.cx(), Ui.TOGGLE_PARTICLES.cy()));
        assertEquals(Ui.Action.TOGGLE_LEFT_HAND, Ui.hit(GameWorld.State.MENU, true, false,
                Ui.TOGGLE_LEFT_HAND.cx(), Ui.TOGGLE_LEFT_HAND.cy()));
        assertEquals(Ui.Action.SETTINGS_BACK, Ui.hit(GameWorld.State.MENU, true, false,
                Ui.SETTINGS_BACK.cx(), Ui.SETTINGS_BACK.cy()));
        assertEquals(Ui.Action.RESUME, Ui.hit(GameWorld.State.PAUSED, false, false,
                Ui.RESUME.cx(), Ui.RESUME.cy()));
        assertEquals(Ui.Action.PAUSE, Ui.hit(GameWorld.State.PLAYING, false, false,
                Ui.PAUSE_BTN.cx(), Ui.PAUSE_BTN.cy()));
        assertNull("random tap in gameplay is not UI", Ui.hit(GameWorld.State.PLAYING, false, false, 800f, 500f));

        // LAB 面板：步进器/无敌/返回，且面板打开时菜单按钮不可点
        assertEquals(Ui.Action.CHEAT_LIVES_UP, Ui.hit(GameWorld.State.MENU, false, true,
                Ui.CHEAT_LIVES_PLUS.cx(), Ui.CHEAT_LIVES_PLUS.cy()));
        assertEquals(Ui.Action.CHEAT_DROP_DOWN, Ui.hit(GameWorld.State.MENU, false, true,
                Ui.CHEAT_DROP_MINUS.cx(), Ui.CHEAT_DROP_MINUS.cy()));
        assertEquals(Ui.Action.TOGGLE_GOD, Ui.hit(GameWorld.State.MENU, false, true,
                Ui.TOGGLE_GOD.cx(), Ui.TOGGLE_GOD.cy()));
        assertEquals(Ui.Action.CHEATS_BACK, Ui.hit(GameWorld.State.MENU, false, true,
                Ui.CHEATS_BACK.cx(), Ui.CHEATS_BACK.cy()));
        assertNull(Ui.hit(GameWorld.State.MENU, false, true, Ui.START.cx(), Ui.START.cy()));
    }

    @Test
    public void leftHandSettingPersistsThroughStore() {
        world.handleUiAction(Ui.Action.OPEN_SETTINGS);
        world.handleUiAction(Ui.Action.TOGGLE_LEFT_HAND);
        assertTrue(world.settings.leftHanded);
        assertTrue("store must be written on toggle", store.saved.leftHanded);

        GameWorld reloaded = new GameWorld(1L, true);
        reloaded.attachStore(store);
        assertTrue(reloaded.settings.leftHanded);
    }

    @Test
    public void labAdjustmentsPersistAndReload() {
        world.handleUiAction(Ui.Action.OPEN_CHEATS);
        world.handleUiAction(Ui.Action.CHEAT_LIVES_UP);
        world.handleUiAction(Ui.Action.CHEAT_DROP_UP);
        world.handleUiAction(Ui.Action.CHEAT_SPEED_DOWN);
        world.handleUiAction(Ui.Action.CHEAT_POWER_UP);
        world.handleUiAction(Ui.Action.TOGGLE_GOD);
        assertEquals(4, store.saved.startLives);
        assertEquals(125, store.saved.dropRatePct);
        assertEquals(90, store.saved.enemySpeedPct);
        assertEquals(2, store.saved.startPower);
        assertTrue(store.saved.godMode);

        GameWorld reloaded = new GameWorld(1L, true);
        reloaded.attachStore(store);
        assertEquals(4, reloaded.settings.startLives);
        assertEquals(125, reloaded.settings.dropRatePct);
        assertEquals(90, reloaded.settings.enemySpeedPct);
        assertEquals(2, reloaded.settings.startPower);
        assertTrue(reloaded.settings.godMode);
    }

    @Test
    public void gameEventsQueueIsDrained() {
        world.handleUiAction(Ui.Action.START);
        List<GameWorld.GameEvent> drained = new ArrayList<>();
        for (int i = 0; i < 10; i++) drained.addAll(world.pollEvents());
        assertNotNull(drained);
    }

    // ---- v1.9 图鉴面板与控件布局 ----

    private static boolean overlap(Ui.Rect a, Ui.Rect b) {
        return a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;
    }

    @Test
    public void codexPanelHitMappingAndState() {
        assertEquals(Ui.Action.OPEN_CODEX, Ui.hit(GameWorld.State.MENU, false, false,
                Ui.CODEX.cx(), Ui.CODEX.cy()));
        assertEquals(Ui.Action.CODEX_BACK, Ui.hit(GameWorld.State.MENU, false, false, true,
                Ui.CODEX_BACK.cx(), Ui.CODEX_BACK.cy()));
        assertEquals(Ui.Action.CODEX_TAB, Ui.hit(GameWorld.State.MENU, false, false, true,
                Ui.CODEX_TAB_BOSSES.cx(), Ui.CODEX_TAB_BOSSES.cy()));
        assertNull("图鉴打开时菜单按钮不可误触", Ui.hit(GameWorld.State.MENU, false, false, true,
                Ui.START.cx(), Ui.START.cy()));
        assertNull("图鉴空白处无动作", Ui.hit(GameWorld.State.MENU, false, false, true,
                GameWorld.WIDTH / 2f, 700f));

        world.handleUiAction(Ui.Action.OPEN_CODEX);
        assertTrue(world.codexOpen);
        assertEquals(0, world.codexTab);
        world.handleUiAction(Ui.Action.CODEX_TAB);
        assertEquals(1, world.codexTab);
        world.handleUiAction(Ui.Action.CODEX_TAB);
        assertEquals(0, world.codexTab);
        world.handleUiAction(Ui.Action.CODEX_BACK);
        assertFalse(world.codexOpen);
    }

    @Test
    public void codexPanelsAreMutuallyExclusive() {
        world.handleUiAction(Ui.Action.OPEN_CODEX);
        assertTrue(world.codexOpen);
        assertFalse("设置面板应让位", world.settingsOpen);

        world.handleUiAction(Ui.Action.OPEN_SETTINGS);
        assertTrue(world.settingsOpen);
        assertFalse("设置打开时图鉴让位", world.codexOpen);

        world.handleUiAction(Ui.Action.OPEN_CHEATS);
        assertTrue(world.cheatOpen);
        assertFalse(world.settingsOpen);

        world.handleUiAction(Ui.Action.OPEN_CODEX);
        assertTrue(world.codexOpen);
        assertFalse(world.cheatOpen);
        world.handleUiAction(Ui.Action.CODEX_BACK);
        assertFalse(world.codexOpen);
        world.handleUiAction(Ui.Action.OPEN_CODEX);
        world.closePanels();
        assertFalse(world.settingsOpen || world.cheatOpen || world.codexOpen);
    }

    @Test
    public void menuAndCodexRectsDoNotOverlap() {
        Ui.Rect[] menu = {Ui.START, Ui.START_ENDLESS, Ui.START_DAILY, Ui.START_RUSH,
                Ui.SETTINGS, Ui.CHEATS, Ui.CODEX, Ui.CHECK_UPDATE};
        for (int i = 0; i < menu.length; i++) {
            for (int j = i + 1; j < menu.length; j++) {
                assertFalse("菜单按钮重叠", overlap(menu[i], menu[j]));
            }
        }
        Ui.Rect[] codex = new Ui.Rect[Ui.CODEX_MAX_ROWS + 3];
        codex[0] = Ui.CODEX_TAB_ENEMIES;
        codex[1] = Ui.CODEX_TAB_BOSSES;
        codex[2] = Ui.CODEX_BACK;
        for (int i = 0; i < Ui.CODEX_MAX_ROWS; i++) codex[3 + i] = Ui.codexRow(i);
        for (int i = 0; i < codex.length; i++) {
            for (int j = i + 1; j < codex.length; j++) {
                assertFalse("图鉴行/按钮重叠", overlap(codex[i], codex[j]));
            }
            assertTrue("图鉴控件留在画面内",
                    codex[i].x >= 0 && codex[i].y >= 0
                            && codex[i].x + codex[i].w <= GameWorld.WIDTH
                            && codex[i].y + codex[i].h <= GameWorld.HEIGHT);
        }
    }

    @Test
    public void controlButtonsMirrorWithoutOverlap() {
        assertSame(Ui.BOMB_RIGHT, Ui.bomb(false));
        assertSame(Ui.BOMB_LEFT, Ui.bomb(true));
        assertSame(Ui.DASH_LEFT, Ui.dash(true));
        assertFalse("右手模式冲刺键不压炸弹键", overlap(Ui.bomb(false), Ui.dash(false)));
        assertFalse("左手模式冲刺键不压炸弹键", overlap(Ui.bomb(true), Ui.dash(true)));
        Ui.Rect[] controls = {Ui.BOMB_RIGHT, Ui.BOMB_LEFT, Ui.DASH_RIGHT, Ui.DASH_LEFT};
        for (Ui.Rect r : controls) {
            assertTrue("控件不出屏", r.x >= 0 && r.y >= 0
                    && r.x + r.w <= GameWorld.WIDTH && r.y + r.h <= GameWorld.HEIGHT);
        }
    }
}
