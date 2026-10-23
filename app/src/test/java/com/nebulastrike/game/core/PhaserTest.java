package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.22 迭代 12：相位者 PHASER。
 *
 * <p>她的难点不是数值而是<b>节奏</b>：实体 3.2 秒 → 预警 0.9 秒 → 相位 0.7 秒。
 * 相位期"打不到"，但子弹会原样穿过而不是被吃掉；相位结束前落点就已经标好，
 * 重新实体化的 0.55 秒里受击双倍。测试钉住这条闭环，确保它不会退化成"随机无敌的靶子"。
 */
public class PhaserTest {

    private GameWorld world;
    private final List<Integer> shiftCodes = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261022L);
        world.spawningEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.lives = 999_999;
        world.ship.x = 120f;
        world.ship.prevX = 120f;
        world.ship.y = 1460f;
        world.ship.prevY = 1460f;
        shiftCodes.clear();
    }

    private Enemy phaserAt(float x, float y) {
        for (Enemy e : world.enemies) {
            if (e.active) continue;
            e.init(EnemyType.PHASER, x, y, 1f);
            return e;
        }
        throw new IllegalStateException("敌机池已满");
    }

    private Bullet straightUp(float x, float y) {
        for (Bullet b : world.bullets) {
            if (b.active) continue;
            b.init(x, y, 0f, -700f, 1, false);
            return b;
        }
        throw new IllegalStateException("玩家弹池已满");
    }

    private int activeEnemyBullets() {
        int n = 0;
        for (EnemyBullet b : world.enemyBullets) if (b.active) n++;
        return n;
    }

    private void stepWorld(int frames) {
        for (int i = 0; i < frames; i++) {
            world.update(1f / 60f);
            for (GameWorld.GameEvent e : world.pollEvents()) {
                if (e.type == GameWorld.EventType.PHASE_SHIFT) shiftCodes.add(e.code);
            }
        }
    }

    private void stepEnemy(Enemy e, int frames) {
        for (int i = 0; i < frames; i++) e.update(1f / 60f, GameWorld.WIDTH);
    }

    // ---- 节奏 ----

    @Test
    public void theCycleRunsMaterialTellGhostAndBack() {
        Enemy p = phaserAt(450f, Enemy.PHASER_HOVER_Y);
        assertEquals(0, p.phasePhase);

        stepEnemy(p, 196);   // 3.2s 实体期（浮点累积，多跑几帧）
        assertEquals("实体期满进入预警", 1, p.phasePhase);
        assertTrue(p.isPhasing());
        assertFalse(p.isPhased());

        stepEnemy(p, 56);    // 0.9s 预警
        assertEquals("预警结束进入相位", 2, p.phasePhase);
        assertTrue(p.isPhased());
        assertFalse(p.isPhasing());

        stepEnemy(p, 44);    // 0.7s 相位
        assertEquals("相位结束重新实体化", 0, p.phasePhase);
        assertFalse(p.isPhased());
        assertTrue("实体化瞬间是双倍窗口", p.isReentering());
    }

    @Test
    public void theGhostWindowIgnoresHitsAndLetsBulletsThrough() {
        Enemy p = phaserAt(450f, 400f);
        p.phasePhase = 2;
        p.phaseTimer = Enemy.PHASER_GHOST;
        p.phaseLandX = 450f;
        Bullet b = straightUp(450f, 400f);
        float hp = p.hp;

        world.update(1f / 60f);

        assertTrue("相位期子弹必须原样穿过，不能被吃掉", b.active);
        assertEquals("相位期不结算伤害", hp, p.hp, 0.001f);
    }

    @Test
    public void aGhostHullCannotRamTheShip() {
        Enemy p = phaserAt(world.ship.x, world.ship.y);
        p.phasePhase = 2;
        p.phaseTimer = Enemy.PHASER_GHOST;
        p.phaseLandX = p.x;
        int hits = world.hitsTaken;

        world.update(1f / 60f);

        assertEquals("相位态不参与碰撞", hits, world.hitsTaken);
        assertTrue(p.active);
    }

    @Test
    public void aMaterialHullStillRamsTheShip() {
        Enemy p = phaserAt(world.ship.x, world.ship.y);
        int hits = world.hitsTaken;

        world.update(1f / 60f);

        assertEquals("实体态照常相撞（对照组）", hits + 1, world.hitsTaken);
        assertFalse(p.active);
    }

    @Test
    public void aPhasedHullDoesNotFire() {
        Enemy p = phaserAt(450f, 380f);
        p.phasePhase = 2;
        p.phaseTimer = Enemy.PHASER_GHOST;
        p.phaseLandX = 450f;
        p.fireCd = 0.001f;
        world.ship.y = 1000f;

        world.update(1f / 60f);

        assertEquals("相位期是玩家的喘息窗口，不许开火", 0, activeEnemyBullets());
    }

    // ---- 反打 ----

    @Test
    public void rematerialisingDoublesTheDamageForHalfASecond() {
        Enemy p = phaserAt(450f, 380f);
        p.phasePhase = 0;
        p.phaseReentry = Enemy.PHASER_REENTRY;
        assertEquals(6f, p.hp, 0.001f);
        p.damage(2);
        assertEquals("实体化窗口 2 点按 4 点结算", 2f, p.hp, 0.001f);

        Enemy fresh = phaserAt(700f, 200f);
        fresh.damage(2);
        assertEquals("平时照常结算", 4f, fresh.hp, 0.001f);
    }

    @Test
    public void theShiftEventFiresOnceOnEachEdge() {
        Enemy p = phaserAt(450f, Enemy.PHASER_HOVER_Y);
        p.update(1f / 60f, GameWorld.WIDTH);
        stepWorld(400);   // 足够走完 实体 → 预警 → 相位 → 实体

        assertTrue("至少经历一次相位化", shiftCodes.contains(1));
        assertTrue("并且重新实体化", shiftCodes.contains(0));
        assertEquals("相位化事件只报一次", 1, count(1));
        assertEquals("重新实体化事件只报一次", 1, count(0));
    }

    private int count(int code) {
        int n = 0;
        for (int c : shiftCodes) if (c == code) n++;
        return n;
    }

    // ---- 投放窗口与图鉴 ----

    @Test
    public void theGuaranteedEncounterRespectsThe145SecondWindow() {
        world.spawningEnabled = true;
        world.bossActive = false;
        world.timeAlive = 140f;
        clearEnemies();
        // 其他四型都已经轮过，只有相位者的计数留在 0
        world.v117EncounterCounts[0] = 2;
        world.v117EncounterCounts[1] = 2;
        world.v117EncounterCounts[2] = 2;
        world.v117EncounterCounts[3] = 2;
        world.v117EncounterCounts[4] = 0;
        Enemy early = encounterOnce();
        assertNotNull(early);
        assertNotEquals("145 秒之前相位者不许露面", EnemyType.PHASER, early.type);

        clearEnemies();
        world.timeAlive = 150f;
        Enemy p = encounterOnce();
        assertNotNull(p);
        assertEquals(EnemyType.PHASER, p.type);
        assertTrue("登场即解锁图鉴", world.settings.enemySeen(EnemyType.PHASER));
        assertEquals(EnemyType.PHASER, world.debutBannerType);
        assertTrue("首次登场要弹介绍横幅", world.debutBanner > 0f);
    }

    @Test
    public void codexStillFitsAboveTheBackButton() {
        int last = EnemyType.values().length - 1;
        Ui.Rect row = Ui.codexRow(last);
        assertTrue("第 " + (last + 1) + " 行压到了 BACK 按钮",
                row.y + row.h < Ui.CODEX_BACK.y);
        assertEquals(EnemyType.values().length, Ui.CODEX_MAX_ROWS);
    }

    private void clearEnemies() {
        for (Enemy e : world.enemies) e.active = false;
    }

    private Enemy encounterOnce() {
        world.v117EncounterTimer = 0.0001f;
        world.updateV117Encounters(0.001f);
        Enemy found = null;
        for (Enemy e : world.enemies) {
            if (!e.active) continue;
            if (e.type == EnemyType.MIRAGE || e.type == EnemyType.MAGNETON
                    || e.type == EnemyType.QUEEN || e.type == EnemyType.RAMMER
                    || e.type == EnemyType.PHASER) {
                found = e;
            }
        }
        return found;
    }
}
