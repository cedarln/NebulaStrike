package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.26：局内祭坛 ALTAR。
 * 设计目标是"要收益就得挪开一点注意力"：打核心或站进光环引导都能充能，
 * 充满后按出生时显示的符文给赐福并清场；不理它则自然熄灭，不扣任何东西。
 */
public class AltarTest {
    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(20261026L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
    }

    private EnemyBullet enemyBulletAt(float x, float y) {
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active) continue;
            b.init(x, y, 0f, 0f);
            return b;
        }
        throw new IllegalStateException("敌弹池已满");
    }

    private void fillAndBless(Altar.Blessing kind) {
        world.spawnAltarForTest(760f, 900f, kind);
        world.altar.addCharge(Altar.MAX_CHARGE);
        world.update(1f / 60f);
    }

    @Test
    public void altarAppearsOnScheduleButNotDuringABossFight() {
        world.timeAlive = GameWorld.ALTAR_FIRST_AT - 0.01f;
        world.update(1f / 60f);
        assertTrue("计时到点应在场上生成祭坛", world.altar.active);
        assertEquals(1, world.altarsSpawned);

        world.altar.active = false;
        world.altarNextAt = 0f;
        world.bossActive = true;
        world.update(1f / 60f);
        assertFalse("Boss 在场时不再刷新新祭坛", world.altar.active);
    }

    @Test
    public void altarGoesOutQuietlyIfIgnored() {
        world.spawnAltarForTest(800f, 500f, Altar.Blessing.ARSENAL);
        int frames = (int) (Altar.LIFE * 60f) + 5;
        for (int i = 0; i < frames; i++) world.update(1f / 60f);
        assertFalse("寿命到期自然熄灭", world.altar.active);
        assertEquals("没取走就不算赐福", 0, world.altarsBlessed);
    }

    @Test
    public void playerBulletsChargeTheCore() {
        // 把祭坛摆在船的弹道上，靠自动开火充能
        world.ship.x = 450f;
        world.ship.y = 1460f;
        world.spawnAltarForTest(450f, 1080f, Altar.Blessing.VITAL);
        for (int i = 0; i < 300; i++) world.update(1f / 60f);
        assertTrue("机炮打在核心上应转化为充能", world.altar.charge > 0f || world.altarsBlessed > 0);
        assertEquals("持续输出最终会把法阵打满并爆发", 1, world.altarsBlessed);
    }

    @Test
    public void standingInsideTheAuraSwallowsEnemyBullets() {
        // 停在默认出生点（无输入时船会飞回这里）并把祭坛开在脚下
        world.ship.x = 450f;
        world.ship.y = 1460f;
        world.spawnAltarForTest(450f, 1460f, Altar.Blessing.AEGIS);
        EnemyBullet b = enemyBulletAt(450f + 84f, 1460f);
        for (int i = 0; i < 60; i++) world.update(1f / 60f);
        assertFalse("光环内的敌弹会被吸进核心", b.active);
        assertTrue(world.altar.charge > 0f);
    }

    @Test
    public void standingOutsideTheAuraLeavesBulletsAlone() {
        world.ship.x = 450f;
        world.ship.y = 1460f;
        world.spawnAltarForTest(760f, 900f, Altar.Blessing.AEGIS);
        EnemyBullet b = enemyBulletAt(820f, 900f);
        for (int i = 0; i < 45; i++) world.update(1f / 60f);
        assertTrue("人不在光环里，敌弹照旧", b.active);
        assertEquals(0f, world.altar.charge, 0.001f);
    }

    @Test
    public void blessingFiresOnceAndEmitsOneEvent() {
        fillAndBless(Altar.Blessing.FORTUNE);
        assertFalse(world.altar.active);
        assertEquals(1, world.altarsBlessed);
        int blessed = 0;
        for (GameWorld.GameEvent e : world.pollEvents()) {
            if (e.type == GameWorld.EventType.ALTAR_BLESSED) blessed++;
        }
        assertEquals(1, blessed);

        world.update(1f / 60f);
        assertEquals("同一座祭坛不会二次结算", 1, world.altarsBlessed);
    }

    @Test
    public void arsenalBlessingStacksAnExtraWeaponAndPower() {
        int powerBefore = world.ship.powerLevel;
        int specialsBefore = 0;
        for (WeaponType t : WeaponType.values()) {
            if (t != WeaponType.VULCAN && world.ship.isWeaponEquipped(t)) specialsBefore++;
        }
        fillAndBless(Altar.Blessing.ARSENAL);
        int specialsAfter = 0;
        for (WeaponType t : WeaponType.values()) {
            if (t != WeaponType.VULCAN && world.ship.isWeaponEquipped(t)) specialsAfter++;
        }
        assertTrue("军械赐福应多一路武器", specialsAfter > specialsBefore);
        assertTrue("并把火力等级顶上去", world.ship.powerLevel > powerBefore);
    }

    @Test
    public void vitalBlessingAddsALife() {
        world.lives = 1;
        fillAndBless(Altar.Blessing.VITAL);
        assertEquals(2, world.lives);
    }

    @Test
    public void aegisBlessingAddsAShieldLayer() {
        world.ship.shieldLayers = 0;
        fillAndBless(Altar.Blessing.AEGIS);
        assertTrue(world.ship.shieldLayers > 0);
    }

    @Test
    public void fortuneBlessingClearsTheScreenAndPaysForIt() {
        EnemyBullet a = enemyBulletAt(120f, 400f);
        EnemyBullet b = enemyBulletAt(780f, 1200f);
        int scoreBefore = world.score;
        fillAndBless(Altar.Blessing.FORTUNE);
        assertFalse(a.active);
        assertFalse(b.active);
        assertTrue("清场有计分", world.score > scoreBefore);
        assertTrue(world.altarCleared >= 2);
    }

    @Test
    public void restartClearsAltarState() {
        world.spawnAltarForTest(450f, 600f, Altar.Blessing.ARSENAL);
        world.altar.addCharge(40f);
        world.restart(GameMode.WAVES);
        assertFalse(world.altar.active);
        assertEquals(0, world.altarsSpawned);
        assertEquals(0, world.altarsBlessed);
        assertEquals(GameWorld.ALTAR_FIRST_AT, world.altarNextAt, 0.001f);
    }
}
