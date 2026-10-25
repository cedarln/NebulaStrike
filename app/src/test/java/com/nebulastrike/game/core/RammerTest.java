package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.21 迭代 11：冲撞者 RAMMER。
 *
 * <p>她的存在意义不是"多一个靶子"，而是把"预警 → 让位 → 反打"做成一段可学习的节奏：
 * 先压到上沿、亮出落点线，撞停后硬直 1.4 秒且受击双倍。测试钉住四件事——
 * 预警期内不会提前冲、俯冲真的会停、硬直期伤害翻倍、125 秒之前绝不登场。
 */
public class RammerTest {

    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(20261021L);
        world.spawningEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.lives = 999_999;
        world.ship.x = 100f;
        world.ship.prevX = 100f;
        world.ship.y = 1460f;
        world.ship.prevY = 1460f;
    }

    private Enemy rammerAt(float x, float y) {
        for (Enemy e : world.enemies) {
            if (e.active) continue;
            e.init(EnemyType.RAMMER, x, y, 1f);
            return e;
        }
        throw new IllegalStateException("敌机池已满");
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
                    || e.type == EnemyType.QUEEN || e.type == EnemyType.RAMMER) found = e;
        }
        return found;
    }

    private boolean sawEvent(GameWorld.EventType type) {
        for (GameWorld.GameEvent e : world.pollEvents()) {
            if (e.type == type) return true;
        }
        return false;
    }

    // ---- 行为节奏 ----

    @Test
    public void descendsToHoverThenTelegraphsWithoutDivingEarly() {
        Enemy r = rammerAt(450f, -60f);
        for (int i = 0; i < 600 && r.ramPhase == 0; i++) {
            r.update(1f / 60f, GameWorld.WIDTH);
        }
        assertEquals("压到悬停高度后进入预警期", 1, r.ramPhase);
        assertEquals(Enemy.RAMMER_HOVER_Y, r.y, 0.5f);
        assertTrue(r.isRamming());
        // 预警期内高度锁死：玩家有时间读线、走位
        for (int i = 0; i < 12; i++) {
            r.update(1f / 60f, GameWorld.WIDTH);
        }
        assertEquals(Enemy.RAMMER_HOVER_Y, r.y, 0.5f);
        assertEquals(1, r.ramPhase);
    }

    @Test
    public void dashesDownLandsStunnedThenLeaves() {
        Enemy r = rammerAt(450f, Enemy.RAMMER_HOVER_Y);
        r.update(1f / 60f, GameWorld.WIDTH);
        assertEquals(1, r.ramPhase);

        for (int i = 0; i < 120 && r.ramPhase == 1; i++) {
            r.update(1f / 60f, GameWorld.WIDTH);
        }
        assertEquals("预警结束开始俯冲", 2, r.ramPhase);
        float before = r.y;
        r.update(1f / 60f, GameWorld.WIDTH);
        assertTrue("俯冲高度必须明显下落", r.y > before + 15f);

        for (int i = 0; i < 120 && r.ramPhase == 2; i++) {
            r.update(1f / 60f, GameWorld.WIDTH);
        }
        assertEquals("俯冲终点转入硬直", 3, r.ramPhase);
        assertEquals(Enemy.RAMMER_BOTTOM_Y, r.y, 0.5f);
        assertTrue(r.isStunned());
        assertTrue("撞停标记留给 GameWorld 播表现", r.ramImpact);

        for (int i = 0; i < 120 && r.ramPhase == 3; i++) {
            r.update(1f / 60f, GameWorld.WIDTH);
        }
        assertEquals("硬直结束转入离场", 4, r.ramPhase);
        assertFalse(r.isStunned());
        float y = r.y;
        r.update(1f / 60f, GameWorld.WIDTH);
        assertTrue(r.y > y);
    }

    @Test
    public void aimFollowsTheShipLaneAndLocksBeforeTheDash() {
        Enemy r = rammerAt(200f, Enemy.RAMMER_HOVER_Y);
        r.update(1f / 60f, GameWorld.WIDTH, 200f, 1460f);
        assertEquals(1, r.ramPhase);
        for (int i = 0; i < 12; i++) {
            r.update(1f / 60f, GameWorld.WIDTH, 700f, 1460f);
        }
        assertEquals("预警线要跟着飞船走", 700f, r.ramAimX, 1f);
        assertTrue("机身朝落点横移", r.x > 200f);

        for (int i = 0; i < 120 && r.ramPhase == 1; i++) {
            r.update(1f / 60f, GameWorld.WIDTH, 700f, 1460f);
        }
        assertEquals(2, r.ramPhase);
        assertEquals("俯冲瞬间锁定最后一次瞄准", 700f, r.ramAimX, 1f);
        assertTrue("俯冲带横向分量", r.ramDirX > 0f);
    }

    // ---- 硬直是奖励窗口 ----

    @Test
    public void aStunnedRammerTakesDoubleDamage() {
        Enemy r = rammerAt(450f, Enemy.RAMMER_HOVER_Y);
        r.update(1f / 60f, GameWorld.WIDTH);
        for (int i = 0; i < 240 && r.ramPhase != 3; i++) {
            r.update(1f / 60f, GameWorld.WIDTH);
        }
        assertTrue(r.isStunned());
        assertEquals(7f, r.hp, 0.001f);
        r.damage(3);
        assertEquals("硬直期 3 点伤害按 6 点结算", 1f, r.hp, 0.001f);

        Enemy fresh = rammerAt(700f, 100f);
        fresh.damage(3);
        assertEquals("非硬直状态照常结算", 4f, fresh.hp, 0.001f);
    }

    @Test
    public void theImpactEventFiresExactlyOncePerLanding() {
        world.ship.makeInvulnerable();
        Enemy r = rammerAt(450f, Enemy.RAMMER_BOTTOM_Y - 20f);
        r.ramPhase = 2;
        r.ramDirX = 0f;
        r.ramDirY = 1f;
        world.update(1f / 60f);
        assertTrue("撞停当帧必须发事件（音效/震动/尘土都挂在这上面）", sawEvent(GameWorld.EventType.RAM_IMPACT));
        world.update(1f / 60f);
        assertFalse("撞停只报一次", sawEvent(GameWorld.EventType.RAM_IMPACT));
    }

    // ---- 投放窗口 ----

    @Test
    public void theGuaranteedEncounterRespectsThe125SecondWindow() {
        world.spawningEnabled = true;
        world.bossActive = false;
        world.timeAlive = 124f;
        clearEnemies();
        world.v117EncounterCounts[0] = 2;   // 伪装者已轮过，别抢这一轮的坑位
        Enemy early = encounterOnce();
        assertNotNull(early);
        assertNotEquals("125 秒之前冲撞者不许露面", EnemyType.RAMMER, early.type);

        clearEnemies();
        world.timeAlive = 130f;
        Enemy r = encounterOnce();
        assertNotNull(r);
        assertEquals(EnemyType.RAMMER, r.type);
        assertTrue("登场即解锁图鉴", world.settings.enemySeen(EnemyType.RAMMER));
        assertEquals(EnemyType.RAMMER, world.debutBannerType);
        assertTrue("首次登场要弹介绍横幅", world.debutBanner > 0f);
    }

    @Test
    public void codexRowsStillFitAboveTheBackButton() {
        Ui.Rect lastOnPage = Ui.codexRow(Ui.CODEX_MAX_ROWS - 1);
        assertTrue("末行压到了翻页按钮",
                lastOnPage.y + lastOnPage.h < Ui.CODEX_PREV.y);
        assertTrue("末行压到了 BACK 按钮",
                lastOnPage.y + lastOnPage.h < Ui.CODEX_BACK.y);
        assertTrue("分页装不下全部敌机",
                Ui.codexPageCount(EnemyType.values().length) * Ui.CODEX_MAX_ROWS
                        >= EnemyType.values().length);
    }
}
