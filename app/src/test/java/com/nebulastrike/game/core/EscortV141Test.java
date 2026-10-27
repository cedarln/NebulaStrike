package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.41 迭代 31：护航补给舰「星鸦」。
 *
 * <p>它按固定节拍出现在战场上空，信标把附近敌弹拉向自己：玩家要么替它清弹，
 * 要么看着它被打空。安全离场会投下一件随机武器并给分；被打空只算护航失败，
 * 不扣命不扣分。Boss 战、换段过场与 BOSS RUSH 都不出护航。
 */
public class EscortV141Test {

    private GameWorld w;

    @Before
    public void setUp() {
        w = new GameWorld(4141L);
        w.spawningEnabled = false;
        w.mutatorsEnabled = false;
        w.bountyEnabled = false;
        w.settings.setDifficulty(Difficulty.STANDARD);
        w.ship.invulnerable = true;
    }

    /** 强制到点，让护航舰出现；随后立刻关掉刷怪并清场，只留护航舰。 */
    private void arrive() {
        w.spawningEnabled = true;
        w.escortTimer = 0f;
        w.update(1f / 60f);
        assertTrue("护航舰应已入场", w.escort.active);
        w.spawningEnabled = false;
        for (Enemy e : w.enemies) e.active = false;
        for (EnemyBullet b : w.enemyBullets) b.active = false;
    }

    private void hold() {
        for (int i = 0; i < 180 && !w.escort.holding(); i++) w.update(1f / 60f);
        assertTrue("护航舰应已到信标高度", w.escort.holding());
    }

    @Test
    public void escortArrivesOnScheduleAndHoldsTheLine() {
        arrive();
        assertTrue("入场横幅要弹一次", w.escortBanner > 0f);
        assertTrue(hasEvent(w, GameWorld.EventType.ESCORT_INCOMING));
        assertEquals(Escort.MAX_HP, w.escort.hp, 0.001f);
        assertTrue(w.escort.y < 0f);

        hold();
        assertEquals(Escort.HOLD_Y, w.escort.y, 0.6f);
        assertEquals(Escort.HOLD_TIME, w.escort.holdTimer, Escort.HOLD_TIME);
        assertFalse(w.escort.leaving);
    }

    @Test
    public void beaconPullsNearbyBulletsButNotFarOnes() {
        arrive();
        hold();
        for (EnemyBullet b : w.enemyBullets) b.active = false;

        EnemyBullet near = freeBullet();
        near.init(w.escort.x + 210f, w.escort.y + 40f, 0f, 200f);
        EnemyBullet far = freeBullet();
        far.init(w.escort.x + 420f, w.escort.y, 0f, 200f);

        for (int i = 0; i < 30; i++) w.update(1f / 60f);

        assertTrue("信标半径内的敌弹应被掰向舰体", near.vx < -5f);
        assertEquals("半径外的敌弹照常直落", 0f, far.vx, 0.001f);
        assertEquals(Escort.MAX_HP, w.escort.hp, 0.001f);
    }

    @Test
    public void escortAbsorbsBulletsAndCanBeLostWithoutPunishment() {
        arrive();
        hold();
        for (EnemyBullet b : w.enemyBullets) b.active = false;
        int scoreBefore = w.score;
        int livesBefore = w.lives;

        EnemyBullet first = freeBullet();
        first.init(w.escort.x, w.escort.y, 0f, 0f);
        w.update(1f / 60f);
        assertFalse("命中护航舰的敌弹被吃掉", first.active);
        assertTrue(w.escort.hp < Escort.MAX_HP);
        assertTrue(hasEvent(w, GameWorld.EventType.ESCORT_HIT));

        w.escort.hp = 1f;
        EnemyBullet killer = freeBullet();
        killer.init(w.escort.x, w.escort.y, 0f, 0f);
        w.update(1f / 60f);

        assertFalse(w.escort.active);
        assertEquals(1, w.escortsLost);
        assertTrue(hasEvent(w, GameWorld.EventType.ESCORT_LOST));
        assertEquals("护航失败不扣命", livesBefore, w.lives);
        assertTrue("也不倒扣分数", w.score >= scoreBefore);
        assertEquals(GameWorld.ESCORT_PERIOD, w.escortTimer, 0.001f);
    }

    @Test
    public void survivingEscortDropsSupplyAndPaysOut() {
        arrive();
        hold();
        for (EnemyBullet b : w.enemyBullets) b.active = false;
        for (PowerUp p : w.powerups) p.active = false;
        int scoreBefore = w.score;

        w.escort.holdTimer = 0.01f;
        for (int i = 0; i < 200 && w.escort.active; i++) w.update(1f / 60f);

        assertFalse(w.escort.active);
        assertEquals(1, w.escortsSaved);
        assertTrue(hasEvent(w, GameWorld.EventType.ESCORT_SAVED));
        assertTrue("应投下一件补给", anyPowerUp());
        assertTrue("安全离场给一笔奖励分",
                w.score - scoreBefore >= GameWorld.ESCORT_REWARD_SCORE);
        assertEquals(GameWorld.ESCORT_PERIOD, w.escortTimer, 0.001f);
    }

    @Test
    public void bossArrivalSendsTheEscortHomeEarly() {
        arrive();
        hold();
        w.spawningEnabled = true;
        w.wave = 4;
        w.nextBossWave = 4;
        w.bossGapTimer = 0f;
        w.update(1f / 60f);
        assertTrue(w.boss.active);
        assertTrue("Boss 入场时护航舰开始撤离", w.escort.leaving);

        for (int i = 0; i < 200 && w.escort.active; i++) w.update(1f / 60f);
        assertFalse(w.escort.active);
        assertEquals("撤离时还活着就照常结算", 1, w.escortsSaved);
    }

    @Test
    public void bossRushNeverSendsAnEscort() {
        w.restart(GameMode.RUSH);
        w.escortTimer = 0f;
        for (int i = 0; i < 90; i++) w.update(1f / 60f);
        assertFalse("连战局只有 Boss，没有护航", w.escort.active);
        assertFalse(hasEvent(w, GameWorld.EventType.ESCORT_INCOMING));
    }

    @Test
    public void restartClearsTheEscortState() {
        arrive();
        w.escort.hp = 12f;
        w.restart(GameMode.WAVES);
        assertFalse(w.escort.active);
        assertEquals(Escort.MAX_HP, w.escort.hp, 0.001f);
        assertEquals(GameWorld.ESCORT_FIRST_AT, w.escortTimer, 0.001f);
        assertEquals(0f, w.escortBanner, 0.001f);
        assertEquals(0, w.escortsSaved);
        assertEquals(0, w.escortsLost);
    }

    // ---- 小工具 ----

    private boolean anyPowerUp() {
        for (PowerUp p : w.powerups) if (p.active) return true;
        return false;
    }

    private EnemyBullet freeBullet() {
        for (EnemyBullet b : w.enemyBullets) if (!b.active) return b;
        throw new AssertionError("no free enemy bullet slot");
    }

    private static boolean hasEvent(GameWorld world, GameWorld.EventType type) {
        for (GameWorld.GameEvent e : world.pollEvents()) if (e.type == type) return true;
        return false;
    }
}
