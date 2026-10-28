package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.52 迭代 42：聚焦模式 FOCUS。
 *
 * <p>拖动时用第二根手指按住空白处：移速减半、开火更快、散射收束，并在机身上画出真实判定点。
 * 它只是"看得更准、走得更细"的辅助，不改变任何伤害或道具规则。
 */
public class FocusV152Test {

    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(20261028L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.bountyEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.settings.hull = Hull.SPARK.ordinal();
        world.lives = 999_999;
    }

    private int activePlayerBullets() {
        int n = 0;
        for (Bullet b : world.bullets) if (b.active) n++;
        return n;
    }

    @Test
    public void focusOnlyWorksWhilePlaying() {
        GameWorld menu = new GameWorld(20261028L, true);
        menu.setFocus(true);
        assertFalse("菜单里不该进入聚焦", menu.focus);
        assertFalse(menu.ship.focus);

        world.setFocus(true);
        assertTrue(world.focus);
        assertTrue(world.ship.focus);

        world.pauseIfPlaying();
        assertEquals(GameWorld.State.PAUSED, world.state);
        assertFalse("暂停自动松开聚焦", world.focus);
        assertFalse(world.ship.focus);

        world.setFocus(true);
        assertFalse("暂停中按下也不进入聚焦", world.focus);
    }

    @Test
    public void focusHalvesFollowSpeed() {
        world.ship.setTarget(world.ship.x + 600f, world.ship.y);
        float x0 = world.ship.x;
        world.update(1f / 60f);
        float normalStep = world.ship.x - x0;

        world.restart(GameMode.WAVES);
        world.ship.setTarget(world.ship.x + 600f, world.ship.y);
        world.setFocus(true);
        float x1 = world.ship.x;
        world.update(1f / 60f);
        float focusStep = world.ship.x - x1;

        assertTrue("普通模式确实在移动", normalStep > 0f);
        assertTrue("聚焦模式也在移动", focusStep > 0f);
        assertEquals("聚焦移速约为一半", 0.5f, focusStep / normalStep, 0.06f);
    }

    @Test
    public void focusFiresFasterButOnlyWhileHeld() {
        world.restart(GameMode.WAVES);
        world.resetWeaponCooldowns();
        world.shotsFired = 0;
        for (int i = 0; i < 120; i++) world.update(1f / 60f);
        int normalShots = world.shotsFired;

        world.restart(GameMode.WAVES);
        world.resetWeaponCooldowns();
        world.shotsFired = 0;
        world.setFocus(true);
        for (int i = 0; i < 120; i++) world.update(1f / 60f);
        int focusShots = world.shotsFired;

        assertTrue("聚焦每秒打出更多轮：" + normalShots + " vs " + focusShots,
                focusShots > normalShots);
        assertTrue("但不应快得离谱", focusShots <= normalShots * 1.35f);
    }

    @Test
    public void focusNarrowsSpreadPattern() {
        world.restart(GameMode.WAVES);
        world.ship.powerLevel = 3;
        world.ship.addWeapon(WeaponType.SPREAD);
        world.resetWeaponCooldowns();
        for (Bullet b : world.bullets) b.active = false;
        world.update(1f / 60f);
        int normalBullets = activePlayerBullets();

        world.restart(GameMode.WAVES);
        world.ship.powerLevel = 3;
        world.ship.addWeapon(WeaponType.SPREAD);
        world.resetWeaponCooldowns();
        for (Bullet b : world.bullets) b.active = false;
        world.setFocus(true);
        world.update(1f / 60f);
        int focusBullets = activePlayerBullets();

        assertTrue("聚焦把扇形收窄，弹道更少也更集中：" + normalBullets + " vs " + focusBullets,
                focusBullets < normalBullets);
        assertTrue("收窄后仍有火力", focusBullets > 0);
    }

    @Test
    public void restartAndStateChangeClearFocus() {
        world.setFocus(true);
        world.restart(GameMode.WAVES);
        assertFalse("重开清掉聚焦", world.focus);
        assertFalse(world.ship.focus);

        world.setFocus(true);
        world.state = GameWorld.State.GAME_OVER;
        world.update(1f / 60f);
        assertFalse("结算自动松开聚焦", world.focus);
    }
}
