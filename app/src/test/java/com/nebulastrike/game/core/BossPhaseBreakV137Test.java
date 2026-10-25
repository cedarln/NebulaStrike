package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.37 迭代 27：Boss 换段过场与增援。
 *
 * <p>以前 Boss 掉到 66% / 33% 只是血条变色，满屏弹幕照旧压下来。
 * 这轮在换段瞬间收走敌弹、放一队增援、Boss 停火 1.6 秒，
 * 让"阶段变了"成为玩家看得见、也喘得过气的节点。
 */
public class BossPhaseBreakV137Test {

    private GameWorld world;
    private final List<GameWorld.GameEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261025L);
        world.spawningEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.lives = 999_999;
        world.ship.x = 450f;
        world.ship.prevX = 450f;
        world.ship.y = 1460f;
        world.ship.prevY = 1460f;
    }

    private void startBoss() {
        world.boss.init(0);
        world.bossActive = true;
        world.bossWarningTimer = 0f;
        world.boss.y = 200f;
        for (Enemy e : world.enemies) e.active = false;
        for (EnemyBullet b : world.enemyBullets) b.active = false;
        world.pollEvents();
        events.clear();
    }

    private void dropBossTo(float hpFrac) {
        world.boss.hp = world.boss.maxHp * hpFrac;
        world.update(1f / 60f);
        events.addAll(world.pollEvents());
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

    private int reinforceEvents() {
        int n = 0;
        for (GameWorld.GameEvent e : events) {
            if (e.type == GameWorld.EventType.BOSS_REINFORCE) n++;
        }
        return n;
    }

    @Test
    public void phaseChangeClearsBulletsAndSpawnsEscort() {
        startBoss();
        for (int i = 0; i < 6; i++) {
            world.enemyBullets[i].init(300f + i * 40f, 400f, 0f, 120f);
        }
        assertEquals(6, activeEnemyBullets());

        dropBossTo(0.5f);

        assertEquals("换段应当升到第二阶段", 2, world.boss.phase);
        assertEquals("换段瞬间应收走残留弹幕", 0, activeEnemyBullets());
        assertEquals("第二阶段应来两艘增援", 2, activeEnemies());
        assertEquals(2, world.bossPhaseBreakPhase);
        assertEquals("换段过场计时应当拉满", GameWorld.BOSS_PHASE_BREAK,
                world.bossPhaseBreakTimer, 0.02f);
        assertTrue("换段期间暂停常规刷新", world.inInterlude());
        assertEquals(1, reinforceEvents());
        for (GameWorld.GameEvent e : events) {
            if (e.type == GameWorld.EventType.BOSS_REINFORCE) {
                assertEquals("事件应当带上增援数", 2, e.code);
            }
        }
    }

    @Test
    public void escortsAreReadableBacklineTypes() {
        startBoss();
        dropBossTo(0.5f);
        boolean hunter = false, splitter = false;
        for (Enemy e : world.enemies) {
            if (!e.active) continue;
            if (e.type == EnemyType.HUNTER) hunter = true;
            if (e.type == EnemyType.SPLITTER) splitter = true;
            assertTrue("增援应当落在 Boss 下方可见范围内", e.y > world.boss.y);
        }
        assertTrue("应当有一架猎手", hunter);
        assertTrue("应当有一架分裂舱", splitter);
    }

    @Test
    public void bossHoldsFireDuringBreakThenResumes() {
        startBoss();
        dropBossTo(0.5f);
        for (int i = 0; i < 60; i++) world.update(1f / 60f);
        assertEquals("换段期间 Boss 与部件都不该开火", 0, activeEnemyBullets());
        assertTrue(world.bossPhaseBreakTimer > 0f);

        int frames = (int) Math.ceil(GameWorld.BOSS_PHASE_BREAK * 60f) + 4;
        for (int i = 0; i < frames; i++) world.update(1f / 60f);
        assertEquals("过场应当结束", 0f, world.bossPhaseBreakTimer, 0.001f);
        assertFalse(world.inInterlude());

        // 清掉增援，单独观察 Boss 是否恢复开火
        for (Enemy e : world.enemies) e.active = false;
        for (EnemyBullet b : world.enemyBullets) b.active = false;
        world.boss.hp = world.boss.maxHp * 0.5f;
        world.boss.y = 200f;
        for (int i = 0; i < 150; i++) world.update(1f / 60f);
        assertTrue("过场结束后 Boss 应当恢复开火", activeEnemyBullets() > 0);
    }

    @Test
    public void finalPhaseCallsThreeEscorts() {
        startBoss();
        dropBossTo(0.2f);
        assertEquals("残血应直接进入第三阶段", 3, world.boss.phase);
        assertEquals("第三阶段应来三艘增援", 3, activeEnemies());
        assertEquals(3, world.bossPhaseBreakPhase);
        for (GameWorld.GameEvent e : events) {
            if (e.type == GameWorld.EventType.BOSS_REINFORCE) {
                assertEquals(3, e.code);
            }
        }
    }

    @Test
    public void killingBlowDoesNotCallReinforcements() {
        startBoss();
        world.boss.hp = 0f;
        world.update(1f / 60f);
        events.addAll(world.pollEvents());
        assertFalse(world.boss.active);
        assertEquals("最后一击不该再安排换段增援", 0, reinforceEvents());
        assertEquals("击破后的清场不该留下增援", 0, activeEnemies());
        assertEquals(0f, world.bossPhaseBreakTimer, 0.001f);
    }

    @Test
    public void samePhaseDoesNotReplayTheBreak() {
        startBoss();
        dropBossTo(0.5f);
        int frames = (int) Math.ceil(GameWorld.BOSS_PHASE_BREAK * 60f) + 4;
        for (int i = 0; i < frames; i++) world.update(1f / 60f);
        for (int i = 0; i < 120; i++) world.update(1f / 60f);
        events.addAll(world.pollEvents());
        assertEquals("停在第二阶段不该反复触发换段", 1, reinforceEvents());
    }

    @Test
    public void restartClearsPhaseBreak() {
        startBoss();
        dropBossTo(0.5f);
        assertTrue(world.bossPhaseBreakTimer > 0f);
        world.restart(GameMode.WAVES);
        assertEquals(0f, world.bossPhaseBreakTimer, 0.001f);
        assertEquals(0, world.bossPhaseBreakPhase);
        assertFalse(world.inInterlude());
    }
}
