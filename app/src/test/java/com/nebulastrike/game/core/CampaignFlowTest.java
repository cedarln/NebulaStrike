package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * v1.31：关卡与 Boss 之间的过场。
 *
 * <p>以前 wave 一到 Boss 立刻贴脸、Boss 一死小怪立刻续上，整场没有呼吸点。
 * 这轮把两端都接上过场：入场预警时舰体已经在滑入但炮口关闭、刷新暂停；
 * 击破后先清场再停 4 秒，让掉落有时间捡、让人知道"这一关结束了"。
 */
public class CampaignFlowTest {
    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(20261025L);
        world.lives = 999_999;
        world.spawningEnabled = true;
        world.ship.x = 450f;
        world.ship.prevX = 450f;
        world.ship.y = 1460f;
        world.ship.prevY = 1460f;
    }

    private void step(int frames) {
        for (int i = 0; i < frames; i++) world.update(1f / 60f);
    }

    private void triggerBoss() {
        world.bossGapTimer = 0f;
        world.nextBossWave = 4;
        world.wave = 4;
        world.update(1f / 60f);
    }

    private int activeEnemies() {
        int n = 0;
        for (Enemy e : world.enemies) if (e.active) n++;
        return n;
    }

    private int activeEnemyBullets() {
        int n = 0;
        for (EnemyBullet b : world.enemyBullets) if (b.active) n++;
        return n;
    }

    @Test
    public void bossArrivesWithWarningAndNoImmediateFire() {
        triggerBoss();
        assertTrue(world.boss.active);
        assertTrue("入场先进入预警", world.bossWarningTimer > 0f);
        assertTrue(world.inInterlude());
        assertEquals("第一关", 1, world.stageNumber());

        // 把舰体放进射程：预警期间炮口仍然是关的
        world.boss.y = 200f;
        int spawnedBefore = world.spawnedCount;
        step(60);
        assertEquals("预警期间不补小怪", spawnedBefore, world.spawnedCount);
        assertEquals("预警期间没有敌弹", 0, activeEnemyBullets());
        assertTrue(world.bossWarningTimer > 0f);
    }

    @Test
    public void bossOpensFireAfterWarning() {
        triggerBoss();
        world.boss.y = 200f;
        step((int) Math.ceil(GameWorld.BOSS_WARNING_TIME * 60f) + 2);
        assertEquals("预警结束", 0f, world.bossWarningTimer, 0.001f);
        assertFalse(world.inInterlude());
        step(90);
        assertTrue("预警结束后炮塔恢复开火", activeEnemyBullets() > 0);
    }

    @Test
    public void warningEventCarriesStageNumber() {
        triggerBoss();
        List<GameWorld.GameEvent> events = world.pollEvents();
        GameWorld.GameEvent warning = null;
        for (GameWorld.GameEvent e : events) {
            if (e.type == GameWorld.EventType.BOSS_WARNING) warning = e;
        }
        assertNotNull("必须发出 BOSS_WARNING 事件", warning);
        assertEquals(1, warning.code);
    }

    @Test
    public void bossDeathClearsFieldAndPausesSpawns() {
        triggerBoss();
        world.bossWarningTimer = 0f;
        Enemy tank = null;
        for (Enemy e : world.enemies) if (!e.active) { tank = e; break; }
        assertNotNull(tank);
        tank.init(EnemyType.TANK, 200f, 200f, 1f);
        for (EnemyBullet b : world.enemyBullets) {
            if (!b.active) {
                b.init(300f, 300f, 0f, 120f);
                break;
            }
        }
        assertTrue(activeEnemies() > 0);

        world.boss.hp = 0f;
        world.update(1f / 60f);

        assertFalse(world.boss.active);
        assertEquals("击破后进入肃清过场", GameWorld.STAGE_CLEAR_TIME,
                world.stageClearTimer, 0.05f);
        assertEquals("残弹收干净", 0, activeEnemyBullets());
        assertEquals("残余小怪收干净", 0, activeEnemies());
        assertTrue(world.inInterlude());

        int spawnedBefore = world.spawnedCount;
        step(120);
        assertEquals("过场里不刷新", spawnedBefore, world.spawnedCount);
    }

    @Test
    public void stageClearEventAndNextStageNumber() {
        triggerBoss();
        world.boss.hp = 0f;
        world.update(1f / 60f);
        List<GameWorld.GameEvent> events = world.pollEvents();
        GameWorld.GameEvent clear = null;
        for (GameWorld.GameEvent e : events) {
            if (e.type == GameWorld.EventType.STAGE_CLEAR) clear = e;
        }
        assertNotNull("必须发出 STAGE_CLEAR 事件", clear);
        assertEquals("刚打完第一关", 1, clear.code);
        assertEquals("下一关是第二关", 2, world.stageNumber());
    }

    @Test
    public void spawnsResumeAfterStageClear() {
        triggerBoss();
        world.boss.hp = 0f;
        world.update(1f / 60f);
        step((int) Math.ceil(GameWorld.STAGE_CLEAR_TIME * 60f) + 20);
        assertEquals(0f, world.stageClearTimer, 0.001f);
        assertFalse(world.inInterlude());
        int spawnedBefore = world.spawnedCount;
        // 过场结束后还有 2.5 秒刷新缓冲，再等 3 秒必然见到新敌机
        step(180);
        assertTrue("过场结束后恢复刷新", world.spawnedCount > spawnedBefore);
    }

    @Test
    public void rushUsesShortWarning() {
        world.restart(GameMode.RUSH);
        world.bossGapTimer = 0f;
        world.update(1f / 60f);
        assertTrue(world.boss.active);
        assertTrue(world.bossWarningTimer <= GameWorld.BOSS_RUSH_WARNING_TIME + 0.001f);
        assertTrue(world.bossWarningTimer > 0f);
    }

    @Test
    public void interludeTimingIsDeterministic() {
        GameWorld a = new GameWorld(77L);
        GameWorld b = new GameWorld(77L);
        a.wave = b.wave = 4;
        a.update(1f / 60f);
        b.update(1f / 60f);
        for (int i = 0; i < 30; i++) {
            a.update(1f / 60f);
            b.update(1f / 60f);
        }
        assertEquals(a.bossWarningTimer, b.bossWarningTimer, 0.0001f);
        assertEquals(a.stageClearTimer, b.stageClearTimer, 0.0001f);
        assertEquals(a.boss.x, b.boss.x, 0.0001f);
    }
}
