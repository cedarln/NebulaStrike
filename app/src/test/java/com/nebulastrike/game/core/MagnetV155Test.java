package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.55 迭代 45：磁力核心 MAGNET。
 *
 * <p>一件纯被动的叠加型道具：扩大道具吸附范围，并每 4.2 秒把贴身敌弹
 * "收编"成分数。它不清全屏、不反弹、不替代任何武器——只给敢往弹幕里钻的走位付钱。
 */
public class MagnetV155Test {

    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(20261028L);
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

    // ---- 辅助 ----

    private void takeMagnet() {
        world.spawnPowerUp(PowerUp.Kind.MAGNET, world.ship.x, world.ship.y);
        world.update(1f / 60f);
    }

    private EnemyBullet enemyBulletAt(float x, float y) {
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active) continue;
            b.init(x, y, 0f, 0f);
            return b;
        }
        throw new IllegalStateException("敌弹池已满");
    }

    private List<GameWorld.GameEvent> step(int frames) {
        List<GameWorld.GameEvent> all = new ArrayList<>();
        for (int i = 0; i < frames; i++) {
            world.update(1f / 60f);
            all.addAll(world.pollEvents());
        }
        return all;
    }

    private int countEvents(List<GameWorld.GameEvent> events, GameWorld.EventType type) {
        int n = 0;
        for (GameWorld.GameEvent e : events) if (e.type == type) n++;
        return n;
    }

    private GameWorld.GameEvent firstEvent(List<GameWorld.GameEvent> events,
                                           GameWorld.EventType type) {
        for (GameWorld.GameEvent e : events) if (e.type == type) return e;
        return null;
    }

    // ---- 掉落与叠加 ----

    @Test
    public void magnetIsInDropTableAndStacksToThree() {
        assertEquals(PowerUp.Kind.MAGNET, GameWorld.dropKind(0.997f));
        assertEquals("原来 0.994 仍是炮台，MAGNET 只吃掉炮台的一半份额",
                PowerUp.Kind.TURRET, GameWorld.dropKind(0.994f));

        takeMagnet();
        takeMagnet();
        takeMagnet();
        assertEquals(3, world.magnetStacks());

        int before = world.score;
        takeMagnet();
        assertEquals("满层后再拾取不涨层", 3, world.magnetStacks());
        assertEquals("满层折算 200 分", before + 200, world.score);
    }

    @Test
    public void pickupRadiusGrowsWithStacks() {
        assertEquals("没有核心时沿用道具自带的 260", 260f, world.magnetPickupRadius(), 0.001f);
        takeMagnet();
        assertEquals(340f, world.magnetPickupRadius(), 0.001f);
        takeMagnet();
        assertEquals(490f, world.magnetPickupRadius(), 0.001f);
        takeMagnet();
        assertEquals(640f, world.magnetPickupRadius(), 0.001f);
    }

    @Test
    public void pulseRadiusAndCapGrowWithStacks() {
        assertEquals(0f, world.magnetPulseRadius(), 0.001f);
        assertEquals(0, world.magnetPulseCap());

        takeMagnet();
        assertEquals(200f, world.magnetPulseRadius(), 0.001f);
        assertEquals(10, world.magnetPulseCap());
        takeMagnet();
        assertEquals(240f, world.magnetPulseRadius(), 0.001f);
        assertEquals(12, world.magnetPulseCap());
        takeMagnet();
        assertEquals(280f, world.magnetPulseRadius(), 0.001f);
        assertEquals(14, world.magnetPulseCap());
    }

    @Test
    public void magnetDoesNotReplaceExistingWeapons() {
        world.spawnPowerUp(PowerUp.Kind.SPREAD, world.ship.x, world.ship.y);
        world.update(1f / 60f);
        assertTrue(world.ship.isWeaponEquipped(WeaponType.SPREAD));

        takeMagnet();
        assertTrue("叠加型道具不碰已有武器", world.ship.isWeaponEquipped(WeaponType.SPREAD));
    }

    // ---- 脉冲 ----

    @Test
    public void pulseConvertsNearbyBulletsIntoScoreAndEvent() {
        takeMagnet();
        EnemyBullet close = enemyBulletAt(world.ship.x + 120f, world.ship.y);
        EnemyBullet far = enemyBulletAt(world.ship.x + 250f, world.ship.y);
        int before = world.score;

        List<GameWorld.GameEvent> events = step(30);

        assertFalse("200 半径内应被转化", close.active);
        assertTrue("250 在半径外，照旧存在", far.active);
        assertTrue("转化应给分", world.score >= before + GameWorld.MAGNET_PULSE_SCORE);
        assertEquals(1, world.magnetSwept);
        GameWorld.GameEvent pulse = firstEvent(events, GameWorld.EventType.MAGNET_PULSE);
        assertNotNull("应播一次脉冲事件", pulse);
        assertEquals("code = 本次转化的弹数", 1, pulse.code);
    }

    @Test
    public void pulseCapLimitsHowManyBulletsAreSwallowed() {
        takeMagnet();
        for (int i = 0; i < 16; i++) {
            float a = (float) (Math.PI * 2 * i / 16);
            enemyBulletAt(world.ship.x + (float) Math.cos(a) * 120f,
                    world.ship.y + (float) Math.sin(a) * 120f);
        }
        step(30);

        int remaining = 0;
        for (EnemyBullet b : world.enemyBullets) if (b.active) remaining++;
        assertEquals("1 层上限 10 发，16 发只清 10", 6, remaining);
        assertEquals(10, world.magnetSwept);
    }

    @Test
    public void emptyPulseRetriesSoonInsteadOfWaitingFullCycle() {
        takeMagnet();
        List<GameWorld.GameEvent> events = step(30);

        assertEquals("空场不播脉冲事件", 0,
                countEvents(events, GameWorld.EventType.MAGNET_PULSE));
        assertTrue("空转后 0.8 秒重试", world.magnetPulseTimer > 0.5f
                && world.magnetPulseTimer <= GameWorld.MAGNET_EMPTY_RETRY);
    }

    @Test
    public void successfulPulseWaitsFullCycleBeforeNext() {
        takeMagnet();
        enemyBulletAt(world.ship.x + 120f, world.ship.y);
        step(30);
        assertEquals(1, world.magnetSwept);

        EnemyBullet next = enemyBulletAt(world.ship.x + 120f, world.ship.y);
        step(180);   // 3 秒：还没到 4.2 秒的下一轮
        assertTrue("周期未到，不该脉冲", next.active);

        step(78);    // 再 1.3 秒，越过 4.2 秒
        assertFalse("周期到了应再次脉冲", next.active);
        assertEquals(2, world.magnetSwept);
    }

    @Test
    public void withoutMagnetNoPulseHappens() {
        EnemyBullet b = enemyBulletAt(world.ship.x + 120f, world.ship.y);
        step(300);   // 5 秒
        assertTrue("没有核心时不应清弹", b.active);
        assertEquals(0, world.magnetSwept);
    }

    // ---- 吸附与重开 ----

    @Test
    public void coreWidensPickupMagnetInWorld() {
        world.spawnPowerUp(PowerUp.Kind.POWER, world.ship.x + 320f, world.ship.y);
        world.update(1f / 60f);
        PowerUp p = null;
        for (PowerUp q : world.powerups) if (q.active) p = q;
        assertNotNull(p);
        assertFalse("默认 260 半径够不到 320", p.attracted);

        takeMagnet();
        world.update(1f / 60f);   // 拾取在帧末结算，下一帧起才按新半径吸附
        assertTrue("340 半径应开始吸附", p.attracted);
    }

    @Test
    public void restartClearsMagnetState() {
        takeMagnet();
        takeMagnet();
        enemyBulletAt(world.ship.x + 120f, world.ship.y);
        step(30);
        assertTrue(world.magnetSwept > 0);

        world.restart(GameMode.WAVES);

        assertEquals(0, world.magnetStacks());
        assertEquals(0, world.magnetSwept);
        assertEquals(0f, world.magnetPulseTimer, 0.001f);
        assertEquals(260f, world.magnetPickupRadius(), 0.001f);
    }
}
