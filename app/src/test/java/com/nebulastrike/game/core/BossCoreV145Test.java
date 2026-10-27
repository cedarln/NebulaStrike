package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.45 迭代 35：Boss 核心暴露 CORE BREAK。
 *
 * <p>Boss 入场后每 11.5 秒把舰体正中打开 3.4 秒：这一段窗口里，打在核心圈内的子弹
 * 造成双倍伤害并额外计分。节奏是固定的，玩家能在预警弧收拢时提前把火力对到正中；
 * 打偏到舰体边缘或部件上只按普通伤害结算。
 */
public class BossCoreV145Test {

    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(777L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.ship.makeInvulnerable();
        world.ship.y = 1460f;
        world.ship.prevY = 1460f;
    }

    // ---- 辅助 ----

    private void triggerBoss() {
        world.wave = 4;
        world.update(1f / 60f);
        world.bossWarningTimer = 0f;
        world.bossPhaseBreakTimer = 0f;
        // 直接放到驻留位：入场滑入时 y 还是负数，测试放的子弹会被上边界回收
        world.boss.y = world.boss.arenaY;
        assertTrue("Boss 应当已入场", world.boss.active);
    }

    private void disableParts() {
        for (int i = 0; i < world.boss.partCount; i++) world.boss.parts[i].active = false;
    }

    private Bullet playerBulletAt(float x, float y, int damage) {
        for (Bullet b : world.bullets) {
            if (b.active) continue;
            b.init(x, y, 0f, 0f, damage, false);
            return b;
        }
        throw new IllegalStateException("玩家弹池已满");
    }

    /**
     * 边跑边收事件：慢时 / 拾取减速都不影响"最终会不会开核"的判断。
     * 这段测试要连跑二三十秒，重生保护只有几秒，所以每帧续一次无敌——
     * 否则舰体被流弹打空、进入 GAME_OVER，整段模拟就停了，测的就不是核心节拍了。
     */
    private GameWorld.GameEvent waitFor(GameWorld.EventType type, int maxFrames) {
        for (int i = 0; i < maxFrames; i++) {
            world.ship.makeInvulnerable(1f);
            world.update(1f / 60f);
            for (GameWorld.GameEvent e : world.pollEvents()) {
                if (e.type == type) return e;
            }
        }
        return null;
    }

    private void drainEvents(int frames) {
        for (int i = 0; i < frames; i++) {
            world.ship.makeInvulnerable(1f);
            world.update(1f / 60f);
            world.pollEvents();
        }
    }

    // ---- 节拍 ----

    @Test
    public void coreStaysClosedDuringFirstDelay() {
        triggerBoss();
        assertEquals("入场时把第一次开核排进队列",
                GameWorld.CORE_FIRST_DELAY, world.bossCoreCooldown, 0.001f);
        assertFalse(world.bossCoreOpen());

        // 前 5 秒（小于 6.5 秒的首开延迟）不该有任何窗口
        drainEvents(300);
        assertFalse("首开延迟之前不开核", world.bossCoreOpen());
        assertEquals(0f, world.bossCoreTimer, 0.001f);
    }

    @Test
    public void coreOpensAfterFirstDelay() {
        triggerBoss();
        GameWorld.GameEvent open = waitFor(GameWorld.EventType.BOSS_CORE_OPEN, 12 * 60);

        assertNotNull("入场 6.5 秒后应当开核", open);
        assertTrue(world.bossCoreOpen());
        assertEquals("开核事件带上窗口秒数", (int) GameWorld.CORE_WINDOW, open.code);
        assertTrue("窗口期有命中闪光铺垫", world.bossCoreFlash > 0f);
    }

    @Test
    public void coreClosesAfterWindowAndRequeues() {
        triggerBoss();
        assertNotNull(waitFor(GameWorld.EventType.BOSS_CORE_OPEN, 12 * 60));
        GameWorld.GameEvent close = waitFor(GameWorld.EventType.BOSS_CORE_CLOSE,
                (int) (GameWorld.CORE_WINDOW * 60f) + 40);

        assertNotNull("窗口到点必须合拢", close);
        assertFalse(world.bossCoreOpen());
        assertEquals("下一次开核按固定间隔排队",
                GameWorld.CORE_INTERVAL, world.bossCoreCooldown, GameWorld.CORE_INTERVAL);
    }

    @Test
    public void coreWindowRepeatsOnItsOwn() {
        triggerBoss();
        // 拉高血量：这一段要跑 20 多秒，别让自动机炮把 Boss 提前打掉、也别触发换段
        world.boss.maxHp = 200_000f;
        world.boss.hp = 200_000f;
        assertNotNull(waitFor(GameWorld.EventType.BOSS_CORE_OPEN, 12 * 60));
        assertNotNull(waitFor(GameWorld.EventType.BOSS_CORE_CLOSE,
                (int) (GameWorld.CORE_WINDOW * 60f) + 40));
        assertNotNull("合拢后按间隔再开一次", waitFor(GameWorld.EventType.BOSS_CORE_OPEN,
                20 * 60));
        assertTrue(world.bossCoreOpen());
    }

    @Test
    public void warningWindowBlocksTheCore() {
        triggerBoss();
        world.bossCoreCooldown = 0.05f;
        world.bossWarningTimer = 6f;
        drainEvents(120);

        assertFalse("入场预警期不该开核", world.bossCoreOpen());
        assertEquals("预警期连冷却也不该走", 0.05f, world.bossCoreCooldown, 0.001f);
    }

    @Test
    public void phaseBreakClosesAnOpenCore() {
        triggerBoss();
        world.bossCoreTimer = 2f;
        world.bossPhaseBreakTimer = 0.5f;
        world.update(1f / 60f);

        assertFalse("换段过场把窗口收掉", world.bossCoreOpen());
        assertEquals(0f, world.bossCoreTimer, 0.001f);
    }

    // ---- 伤害倍率 ----

    @Test
    public void coreHitDealsDoubleDamage() {
        triggerBoss();
        disableParts();
        world.bossCoreTimer = GameWorld.CORE_WINDOW;
        float before = world.boss.hp;
        playerBulletAt(world.boss.x, world.boss.y, 1);
        world.update(1f / 60f);

        assertEquals("核心圈内 1 点弹伤变 2 点", before - 2f, world.boss.hp, 0.001f);
        assertEquals("命中核心会给闪光反馈", 1f, world.bossCoreFlash, 0.001f);
    }

    @Test
    public void closedCoreDealsNormalDamage() {
        triggerBoss();
        disableParts();
        float before = world.boss.hp;
        playerBulletAt(world.boss.x, world.boss.y, 1);
        world.update(1f / 60f);

        assertEquals("合拢时舰体正中仍是普通伤害", before - 1f, world.boss.hp, 0.001f);
    }

    @Test
    public void hullEdgeHitsMissTheBonus() {
        triggerBoss();
        disableParts();
        world.boss.hitRadius = 90f;   // 放大判定，留出"核心之外、舰体之内"的环
        world.bossCoreTimer = GameWorld.CORE_WINDOW;
        float before = world.boss.hp;
        playerBulletAt(world.boss.x + GameWorld.CORE_RADIUS + 16f, world.boss.y, 1);
        world.update(1f / 60f);

        assertEquals("贴着核心外沿打舰体不算核心命中", before - 1f, world.boss.hp, 0.001f);
    }

    @Test
    public void partHitsDoNotGetTheBonus() {
        triggerBoss();
        world.bossCoreTimer = GameWorld.CORE_WINDOW;
        BossPart part = world.boss.parts[0];
        float before = part.hp;
        playerBulletAt(part.worldX(world.boss), part.worldY(world.boss), 1);
        world.update(1f / 60f);

        assertEquals("部件不吃核心倍率", before - 1f, part.hp, 0.001f);
    }

    @Test
    public void coreHitsAddScore() {
        triggerBoss();
        disableParts();
        world.bossCoreTimer = GameWorld.CORE_WINDOW;
        int before = world.score;
        playerBulletAt(world.boss.x, world.boss.y, 1);
        world.update(1f / 60f);

        assertTrue("核心命中额外计分", world.score > before);
    }

    // ---- 清理 ----

    @Test
    public void restartClearsCoreState() {
        triggerBoss();
        world.bossCoreTimer = 2f;
        world.bossCoreCooldown = 3f;
        world.bossCoreFlash = 0.5f;

        world.restart(GameMode.WAVES);

        assertEquals(0f, world.bossCoreTimer, 0.001f);
        assertEquals(0f, world.bossCoreCooldown, 0.001f);
        assertEquals(0f, world.bossCoreFlash, 0.001f);
    }

    @Test
    public void killingTheBossZeroesTheCoreClock() {
        triggerBoss();
        // 把最大血量一起压小：血量比例留在第一段，打空这一发不会顺手触发换段过场
        world.boss.maxHp = 1.6f;
        world.boss.hp = 1.5f;
        world.bossCoreTimer = 2f;
        world.bossCoreCooldown = 3f;
        disableParts();
        // 清掉 Boss 刚打出的弹：非穿透弹会先去点敌弹，别让它抢在核心之前
        for (EnemyBullet eb : world.enemyBullets) eb.active = false;
        playerBulletAt(world.boss.x, world.boss.y, 5);
        world.update(1f / 60f);
        // 死亡结算排在碰撞之后，这一帧只把血打空，下一帧 boss 才真正退场
        world.update(1f / 60f);

        assertFalse(world.boss.active);
        assertEquals(0f, world.bossCoreTimer, 0.001f);
        assertEquals(0f, world.bossCoreCooldown, 0.001f);
    }

    @Test
    public void coreIsOnlyAvailableWhileTheBossIsAlive() {
        triggerBoss();
        world.boss.active = false;
        assertFalse(world.bossCoreOpen());
        assertNull("收尾后不应再有窗口事件", waitFor(GameWorld.EventType.BOSS_CORE_OPEN, 30));
    }
}
