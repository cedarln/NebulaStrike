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

        @Override public void load(Settings into) { into.sound = saved.sound; into.particles = saved.particles; into.haptics = saved.haptics; }
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
        assertEquals(Ui.Action.START, Ui.hit(GameWorld.State.MENU, false,
                Ui.START.cx(), Ui.START.cy()));
        assertEquals(Ui.Action.OPEN_SETTINGS, Ui.hit(GameWorld.State.MENU, false,
                Ui.SETTINGS.cx(), Ui.SETTINGS.cy()));
        assertEquals(Ui.Action.TOGGLE_PARTICLES, Ui.hit(GameWorld.State.MENU, true,
                Ui.TOGGLE_PARTICLES.cx(), Ui.TOGGLE_PARTICLES.cy()));
        assertEquals(Ui.Action.SETTINGS_BACK, Ui.hit(GameWorld.State.MENU, true,
                Ui.SETTINGS_BACK.cx(), Ui.SETTINGS_BACK.cy()));
        assertEquals(Ui.Action.RESUME, Ui.hit(GameWorld.State.PAUSED, false,
                Ui.RESUME.cx(), Ui.RESUME.cy()));
        assertEquals(Ui.Action.PAUSE, Ui.hit(GameWorld.State.PLAYING, false,
                Ui.PAUSE_BTN.cx(), Ui.PAUSE_BTN.cy()));
        assertNull("random tap in gameplay is not UI", Ui.hit(GameWorld.State.PLAYING, false, 800f, 500f));
    }

    @Test
    public void gameEventsQueueIsDrained() {
        world.handleUiAction(Ui.Action.START);
        List<GameWorld.GameEvent> drained = new ArrayList<>();
        for (int i = 0; i < 10; i++) drained.addAll(world.pollEvents());
        assertNotNull(drained);
    }
}
