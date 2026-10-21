package com.nebulastrike.game.core;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 系统返回键的行为回归：游戏内任何状态下返回键都必须被游戏自己消化，
 * 只有"裸主菜单"才放行给 Activity（否则会出现游玩中一回车就退回桌面）。
 */
public class BackKeyTest {
    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(11L, false);
        world.spawningEnabled = false;
    }

    @Test
    public void backDuringPlayPausesInsteadOfQuitting() {
        world.handleUiAction(Ui.Action.START);
        assertEquals(GameWorld.State.PLAYING, world.state);

        assertTrue(world.consumeBack());
        assertEquals("返回键应先暂停，而不是退出", GameWorld.State.PAUSED, world.state);
    }

    @Test
    public void backWhilePausedResumes() {
        world.handleUiAction(Ui.Action.START);
        world.consumeBack();
        assertEquals(GameWorld.State.PAUSED, world.state);

        assertTrue(world.consumeBack());
        assertEquals(GameWorld.State.PLAYING, world.state);
    }

    @Test
    public void backOnGameOverGoesToMenu() {
        world.handleUiAction(Ui.Action.START);
        world.lives = 1;
        world.ship.hasShield = false;
        world.enemyBullets[0].init(world.ship.x, world.ship.y, 0, 0);
        world.update(1f / 60f);
        assertEquals(GameWorld.State.GAME_OVER, world.state);

        assertTrue(world.consumeBack());
        assertEquals(GameWorld.State.MENU, world.state);
    }

    @Test
    public void backClosesAnyOpenPanel() {
        GameWorld menu = new GameWorld(11L, true);
        menu.handleUiAction(Ui.Action.OPEN_SETTINGS);
        assertTrue(menu.consumeBack());
        assertFalse(menu.settingsOpen);
        assertEquals("关面板不应退出游戏", GameWorld.State.MENU, menu.state);

        menu.handleUiAction(Ui.Action.OPEN_CHEATS);
        assertTrue(menu.consumeBack());
        assertFalse(menu.cheatOpen);

        menu.handleUiAction(Ui.Action.OPEN_CODEX);
        assertTrue(menu.consumeBack());
        assertFalse(menu.codexOpen);
    }

    @Test
    public void bareMenuLetsActivityExit() {
        GameWorld menu = new GameWorld(11L, true);
        assertEquals(GameWorld.State.MENU, menu.state);
        assertFalse("裸主菜单的返回键交给系统，正常退出应用", menu.consumeBack());
    }

    @Test
    public void pauseOverlayBackAndPauseButtonAgree() {
        world.handleUiAction(Ui.Action.START);
        world.handleUiAction(Ui.Action.PAUSE);
        assertEquals(GameWorld.State.PAUSED, world.state);
        assertTrue(world.consumeBack());
        assertEquals(GameWorld.State.PLAYING, world.state);
    }
}
