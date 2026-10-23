package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.27：赏金猎杀 BOUNTY。
 * 设计目标是"给一屏混战一个优先目标"：不额外刷怪，只把已在场上的重甲圈成悬赏，
 * 限时击毁拿高分与保底道具，连杀越高越赚；目标脱逃只断连杀，不扣任何东西。
 */
public class BountyTest {
    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(20261027L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
    }

    private Enemy place(EnemyType type, float x, float y) {
        for (Enemy e : world.enemies) {
            if (!e.active) {
                e.init(type, x, y, 1f);
                return e;
            }
        }
        throw new IllegalStateException("敌机池已满");
    }

    private int activeEnemies() {
        int n = 0;
        for (Enemy e : world.enemies) if (e.active) n++;
        return n;
    }

    private int activePowerUps() {
        int n = 0;
        for (PowerUp p : world.powerups) if (p.active) n++;
        return n;
    }

    private int count(GameWorld.EventType type) {
        int n = 0;
        for (GameWorld.GameEvent e : world.pollEvents()) {
            if (e.type == type) n++;
        }
        return n;
    }

    @Test
    public void bountyMarksAnExistingEnemyInsteadOfSpawningOne() {
        Enemy e = place(EnemyType.TANK, 450f, 700f);
        int before = activeEnemies();
        world.issueBountyForTest();
        assertSame("悬赏应指向场上已有的敌机", e, world.bountyTarget);
        assertTrue(e.bounty);
        assertEquals("发布悬赏不额外刷怪", before, activeEnemies());
        assertTrue("悬赏目标更硬", e.hp > EnemyType.TANK.hp);
        assertEquals(GameWorld.BOUNTY_TIME, world.bountyTimer, 0.001f);
        assertEquals(1, count(GameWorld.EventType.BOUNTY_START));
    }

    @Test
    public void trashOnlyScreenGetsNoBounty() {
        place(EnemyType.DRONE, 300f, 700f);
        place(EnemyType.WEAVER, 500f, 700f);
        place(EnemyType.SUICIDER, 700f, 700f);
        world.issueBountyForTest();
        assertNull("炮灰不值得悬赏", world.bountyTarget);
    }

    @Test
    public void cloakedAndOffscreenTargetsAreSkipped() {
        Enemy mirage = place(EnemyType.MIRAGE, 300f, 700f);
        Enemy phantom = place(EnemyType.PHASER, 500f, 700f);
        phantom.phasePhase = 2;   // 相位态打不到
        place(EnemyType.TANK, 700f, GameWorld.HEIGHT - 100f);   // 已经压到底线
        world.issueBountyForTest();
        assertNull("藏起来的与够不着的都不该被悬赏", world.bountyTarget);
        assertFalse(mirage.bounty);
        assertFalse(phantom.bounty);
    }

    @Test
    public void claimingPaysScorePowerUpAndStreak() {
        Enemy e = place(EnemyType.TANK, 450f, 800f);
        world.issueBountyForTest();
        int scoreBefore = world.score;
        int exemptBefore = world.pacingExemptScore;
        int powerUpsBefore = activePowerUps();
        world.killEnemyForTest(e);

        assertNull(world.bountyTarget);
        assertFalse(e.bounty);
        assertEquals(1, world.bountyStreak);
        assertEquals(1, world.bountiesClaimed);
        assertTrue("赏金应计分", world.score > scoreBefore);
        assertTrue("每单保底一件道具", activePowerUps() > powerUpsBefore);
        assertEquals("赏金奖励不计入关卡节拍",
                world.bountyLastGain, world.pacingExemptScore - exemptBefore);
        assertEquals(1, count(GameWorld.EventType.BOUNTY_CLAIMED));
    }

    @Test
    public void streakGrowsTheReward() {
        Enemy first = place(EnemyType.TANK, 450f, 800f);
        world.issueBountyForTest();
        world.killEnemyForTest(first);
        int firstGain = world.bountyLastGain;

        Enemy second = place(EnemyType.SHIELDER, 450f, 800f);
        world.issueBountyForTest();
        world.killEnemyForTest(second);

        assertEquals(2, world.bountyStreak);
        assertTrue("连杀越高赏金越高", world.bountyLastGain > firstGain);
        assertEquals(2, world.bountiesClaimed);
    }

    @Test
    public void targetEscapingBreaksTheStreakWithoutPenalty() {
        Enemy e = place(EnemyType.TANK, 450f, 800f);
        world.issueBountyForTest();
        world.bountyStreak = 3;
        int scoreBefore = world.score;
        e.y = GameWorld.HEIGHT + 120f;
        e.active = false;   // 模拟从屏幕下方离场
        world.updateBountyForTest(1f / 60f);

        assertNull(world.bountyTarget);
        assertFalse(e.bounty);
        assertEquals("脱逃断连杀", 0, world.bountyStreak);
        assertEquals(1, world.bountiesFailed);
        assertEquals("脱逃不扣分", scoreBefore, world.score);
        assertEquals(1, count(GameWorld.EventType.BOUNTY_FAILED));
    }

    @Test
    public void timeoutCountsAsFailure() {
        Enemy e = place(EnemyType.TANK, 450f, 800f);
        world.issueBountyForTest();
        world.bountyTimer = 0.01f;
        world.updateBountyForTest(1f / 60f);
        assertNull(world.bountyTarget);
        assertEquals(1, world.bountiesFailed);
        assertFalse(e.bounty);
    }

    @Test
    public void noBountyIsIssuedWhileABossIsOnScreen() {
        place(EnemyType.TANK, 450f, 700f);
        world.timeAlive = 100f;
        world.bountyNextAt = 0f;
        world.bossActive = true;
        world.updateBountyForTest(1f / 60f);
        assertNull("Boss 战期间不发布悬赏", world.bountyTarget);

        world.bossActive = false;
        world.updateBountyForTest(1f / 60f);
        assertNotNull("Boss 离场后恢复发布", world.bountyTarget);
    }

    @Test
    public void bossRushNeverIssuesBounties() {
        world.restart(GameMode.RUSH);
        place(EnemyType.TANK, 450f, 700f);
        world.timeAlive = 100f;
        world.bountyNextAt = 0f;
        world.updateBountyForTest(1f / 60f);
        assertNull("连战局没有小怪可悬赏", world.bountyTarget);
    }

    @Test
    public void turretKillStillPaysOut() {
        Enemy e = place(EnemyType.TANK, 450f, 800f);
        world.issueBountyForTest();
        e.active = false;   // 炮台撞毁不经过 killEnemy，但残骸还在场内
        world.updateBountyForTest(1f / 60f);
        assertEquals(1, world.bountiesClaimed);
        assertEquals(0, world.bountiesFailed);
        assertNull(world.bountyTarget);
    }

    @Test
    public void restartClearsBountyState() {
        Enemy e = place(EnemyType.TANK, 450f, 700f);
        world.issueBountyForTest();
        world.bountyStreak = 4;
        world.restart(GameMode.WAVES);
        assertNull(world.bountyTarget);
        assertEquals(0, world.bountyStreak);
        assertEquals(0, world.bountyBestStreak);
        assertEquals(0, world.bountiesClaimed);
        assertEquals(0, world.bountiesFailed);
        assertEquals(0f, world.bountyBanner, 0.001f);
        assertEquals(GameWorld.BOUNTY_FIRST_AT, world.bountyNextAt, 0.001f);
        assertFalse(e.bounty);
    }
}
