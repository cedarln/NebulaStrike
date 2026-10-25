package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.29：两枚新圣物（相位核心 / 赏金执照）与 Boss 布雷舱。
 * 两者都只做加法：相位核心让冲刺既充得快又自带清场，赏金执照把赏金连杀的天花板与收益一起抬高。
 */
public class RelicsV129Test {
    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(1290L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
    }

    private GameWorld quietWorld(long seed) {
        GameWorld w = new GameWorld(seed);
        w.spawningEnabled = false;
        w.mutatorsEnabled = false;
        return w;
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

    private EnemyBullet placeBullet(float x, float y) {
        for (EnemyBullet b : world.enemyBullets) {
            if (!b.active) {
                b.initRadius(x, y, 0f, 40f, 10f);
                return b;
            }
        }
        throw new IllegalStateException("敌弹池已满");
    }

    private int lastEventCode(GameWorld.EventType type) {
        int code = -1;
        for (GameWorld.GameEvent e : world.pollEvents()) {
            if (e.type == type) code = e.code;
        }
        return code;
    }

    private int countParts(Boss b, BossPart.Kind kind) {
        int n = 0;
        for (int i = 0; i < b.partCount; i++) {
            if (b.parts[i].kind == kind) n++;
        }
        return n;
    }

    // ---- 相位核心 PHASE_CORE ----

    @Test
    public void phaseCoreRechargesDashFaster() {
        GameWorld plain = quietWorld(1290L);
        world.grantRelicForTest(Relic.PHASE_CORE);
        plain.ship.dashCharge = 0f;
        world.ship.dashCharge = 0f;
        for (int i = 0; i < 60; i++) {
            plain.update(1f / 60f);
            world.update(1f / 60f);
        }
        assertTrue("相位核心应让冲刺充得更快",
                world.ship.dashCharge > plain.ship.dashCharge + 0.1f);
        assertEquals(Ship.PHASE_CORE_RECHARGE_MULT, world.ship.dashRechargeScale, 0.001f);
        assertEquals("没有圣物时倍率为 1", 1f, plain.ship.dashRechargeScale, 0.001f);
    }

    @Test
    public void phaseCoreBurstClearsNearbyBulletsOnly() {
        world.grantRelicForTest(Relic.PHASE_CORE);
        float sx = world.ship.x, sy = world.ship.y;
        EnemyBullet near = placeBullet(sx + 90f, sy);
        EnemyBullet edge = placeBullet(sx - 60f, sy - 180f);
        EnemyBullet far = placeBullet(sx, sy - GameWorld.PHASE_BURST_RADIUS - 120f);

        assertTrue(world.tryDash());

        assertFalse("冲击波范围内应清弹", near.active);
        assertFalse(edge.active);
        assertTrue("范围外不动", far.active);
        assertEquals(2, lastEventCode(GameWorld.EventType.PHASE_BURST));
        assertTrue(world.phaseBurstFlash > 0f);
    }

    @Test
    public void phaseBurstBurnsPointBlankEnemies() {
        world.grantRelicForTest(Relic.PHASE_CORE);
        Enemy close = place(EnemyType.TANK, world.ship.x + 70f, world.ship.y - 40f);
        Enemy distant = place(EnemyType.TANK, world.ship.x, 200f);
        float closeHp = close.hp, distantHp = distant.hp;

        assertTrue(world.tryDash());

        assertEquals("贴脸敌机吃满冲击波伤害",
                closeHp - GameWorld.PHASE_BURST_DAMAGE, close.hp, 0.001f);
        assertEquals("远处敌机不受影响", distantHp, distant.hp, 0.001f);
    }

    @Test
    public void burstNeedsTheCore() {
        EnemyBullet near = placeBullet(world.ship.x + 40f, world.ship.y);
        assertTrue(world.tryDash());
        assertTrue("没有相位核心，冲刺不会清弹", near.active);
        assertEquals("没有圣物也不该发冲击波事件",
                -1, lastEventCode(GameWorld.EventType.PHASE_BURST));
    }

    // ---- 赏金执照 BOUNTY_LICENSE ----

    private int payout(boolean licensed) {
        GameWorld w = quietWorld(4242L);
        if (licensed) w.grantRelicForTest(Relic.BOUNTY_LICENSE);
        Enemy e = null;
        for (Enemy slot : w.enemies) {
            if (!slot.active) {
                slot.init(EnemyType.TANK, 450f, 800f, 1f);
                e = slot;
                break;
            }
        }
        assertNotNull(e);
        w.issueBountyForTest();
        w.killEnemyForTest(e);
        return w.bountyLastGain;
    }

    @Test
    public void bountyLicenseRaisesStreakCap() {
        assertEquals(GameWorld.BOUNTY_MAX_STREAK, world.bountyStreakCap());
        world.grantRelicForTest(Relic.BOUNTY_LICENSE);
        assertEquals(GameWorld.BOUNTY_MAX_STREAK_LICENSE, world.bountyStreakCap());
    }

    @Test
    public void bountyLicenseMakesPayoutsWorthMore() {
        int plain = payout(false);
        int licensed = payout(true);
        assertTrue("赏金执照应当加钱", licensed > plain);
        assertEquals(Math.round(plain * GameWorld.BOUNTY_LICENSE_SCORE_MULT), licensed);
    }

    @Test
    public void bountyLicenseKeepsStreakOnEscape() {
        world.grantRelicForTest(Relic.BOUNTY_LICENSE);
        Enemy e = place(EnemyType.TANK, 450f, 800f);
        world.issueBountyForTest();
        world.bountyStreak = 4;
        e.y = GameWorld.HEIGHT + 120f;
        e.active = false;
        world.updateBountyForTest(1f / 60f);

        assertEquals("赏金执照：脱逃只掉一连", 3, world.bountyStreak);
        assertEquals(1, world.bountiesFailed);
        assertNull(world.bountyTarget);
    }

    // ---- Boss 布雷舱 MINE ----

    @Test
    public void mineBayAppearsFromTierTwo() {
        Boss b = new Boss();
        b.init(1);
        assertEquals("第二艘还没有布雷舱", 0, countParts(b, BossPart.Kind.MINE));
        b.init(2);
        assertEquals(1, countParts(b, BossPart.Kind.MINE));
        b.init(3);
        assertEquals(1, countParts(b, BossPart.Kind.MINE));
        assertEquals("tier 3 = 5 炮 + 2 激光 + 2 分裂舱 + 2 护盾 + 1 布雷舱",
                12, b.partCount);
    }

    @Test
    public void mineBayDropsDriftingMines() {
        world.wave = 4;
        world.update(1f / 60f);
        world.boss.init(2);
        world.bossWarningTimer = 0f;   // 布雷舱测试跳过入场预警
        world.boss.y = 150f;
        world.boss.staggerTimer = 0f;
        for (int i = 0; i < world.boss.partCount; i++) {
            if (world.boss.parts[i].kind == BossPart.Kind.MINE) world.boss.parts[i].fireCd = 0f;
        }
        world.update(1f / 60f);

        int mines = 0;
        float drift = 0f;
        for (EnemyBullet b : world.enemyBullets) {
            if (!b.active || !b.mine) continue;
            mines++;
            drift += b.vx;
            assertTrue("水雷要往下走", b.vy > 0f);
        }
        assertEquals("一阶段每次投放两枚", 2, mines);
        assertTrue("两枚应反向漂移而不是直落", Math.abs(drift) < 0.001f);
        assertTrue("布雷舱开火后重新计时", mineBayOf(world.boss).fireCd > 0f);
    }

    private BossPart mineBayOf(Boss b) {
        for (int i = 0; i < b.partCount; i++) {
            if (b.parts[i].kind == BossPart.Kind.MINE) return b.parts[i];
        }
        return null;
    }
}
