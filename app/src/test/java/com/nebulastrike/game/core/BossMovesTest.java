package com.nebulastrike.game.core;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** v1.25：Boss 新招式——弹幕网与棱镜召唤。 */
public class BossMovesTest {
    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(777L);
    }

    private void triggerBoss() {
        world.wave = 4;
        world.update(1f / 60f);
    }

    /** 进入三阶段并让世界跑完棱镜召唤流程。 */
    private void summonPrismsInWorld(int tier) {
        triggerBoss();
        world.boss.init(tier);
        world.boss.y = 150f;
        world.boss.hp = world.boss.maxHp * 0.2f;
        for (int i = 0; i < 120 && world.boss.prismsAlive() == 0; i++) {
            world.update(1f / 60f);
        }
    }

    private BossPart firstPrism() {
        BossPart inactive = null;
        for (int i = 0; i < world.boss.partCount; i++) {
            BossPart p = world.boss.parts[i];
            if (p.kind != BossPart.Kind.PRISM) continue;
            if (p.active) return p;
            inactive = p;
        }
        return inactive;
    }

    @Test
    public void netLeavesAPassableGap() {
        triggerBoss();
        world.boss.init(2);
        world.boss.y = 150f;
        int made = world.fireNet(10, 2, 180f);
        assertEquals("10 列两排、缺口占两列", 16, made);

        float step = (GameWorld.WIDTH - 108f) / 9f;
        boolean[] columnHit = new boolean[10];
        int rowA = 0, rowB = 0;
        for (EnemyBullet b : world.enemyBullets) {
            if (!b.active || !b.net) continue;
            int col = Math.round((b.x - 54f) / step);
            if (col >= 0 && col < 10) columnHit[col] = true;
            if (b.y > world.boss.y) rowA++; else rowB++;
        }
        int missing = 0;
        for (boolean hit : columnHit) if (!hit) missing++;
        assertEquals("同一缺口在两排上对齐，玩家可以直穿", 2, missing);
        assertEquals(8, rowA);
        assertEquals(8, rowB);
    }

    @Test
    public void netFiresDuringPhaseTwo() {
        triggerBoss();
        world.boss.init(2);
        world.boss.y = 150f;
        world.boss.hp = world.boss.maxHp * 0.5f;   // 二阶段
        int eventCode = -1;
        for (int i = 0; i < 60 * 20 && eventCode <= 0; i++) {
            world.update(1f / 60f);
            for (GameWorld.GameEvent e : world.pollEvents()) {
                if (e.type == GameWorld.EventType.BOSS_NET) eventCode = e.code;
            }
        }
        assertTrue("二阶段应该铺出弹幕网", eventCode > 0);
        int nets = 0;
        for (EnemyBullet b : world.enemyBullets) if (b.active && b.net) nets++;
        assertTrue("弹幕网节点仍在场上", nets > 0);
    }

    @Test
    public void netBulletsAreMarkedAndDescend() {
        world.boss.init(2);
        world.boss.y = 150f;
        world.fireNet(8, 2, 200f);
        EnemyBullet node = null;
        for (EnemyBullet b : world.enemyBullets) if (b.active && b.net) { node = b; break; }
        assertNotNull(node);
        float y0 = node.y;
        node.update(1f / 60f);
        assertEquals(y0 + 200f / 60f, node.y, 0.01f);
        EnemyBullet plain = new EnemyBullet();
        plain.init(0f, 0f, 0f, 100f);
        assertFalse("普通敌弹不应被标记为网节点", plain.net);
        plain.initRadius(0f, 0f, 0f, 100f, 10f);
        assertFalse(plain.net);
    }

    @Test
    public void scoutNeverSummonsPrisms() {
        Boss b = new Boss();
        b.init(0);
        b.y = b.arenaY;
        b.hp = b.maxHp * 0.2f;
        b.update(1f / 60f);
        assertEquals(3, b.phase);
        assertFalse("第一艘 Boss 不进棱镜招式", b.readyToSummonPrism());
        assertEquals(0, b.summonPrisms());
        assertEquals(0, b.prismsAlive());
    }

    @Test
    public void prismsSummonOnceAndOrbitTheHull() {
        Boss b = new Boss();
        b.init(1);
        b.y = b.arenaY;
        b.hp = b.maxHp * 0.2f;
        b.update(1f / 60f);
        assertTrue(b.readyToSummonPrism());
        int before = b.partCount;
        assertEquals(2, b.summonPrisms());
        assertEquals(before + 2, b.partCount);
        assertEquals(2, b.prismsAlive());
        assertEquals("同一场战斗只召唤一次", 0, b.summonPrisms());
        assertFalse(b.readyToSummonPrism());

        BossPart p = null;
        for (int i = 0; i < b.partCount; i++) {
            if (b.parts[i].kind == BossPart.Kind.PRISM) { p = b.parts[i]; break; }
        }
        assertNotNull(p);
        float ox = p.ox, oy = p.oy;
        for (int i = 0; i < 30; i++) b.update(1f / 60f);
        assertTrue("棱镜应绕舰体移动", Math.abs(p.ox - ox) + Math.abs(p.oy - oy) > 4f);
        float nx = p.ox / (b.radius * 1.18f);
        float ny = (p.oy + b.radius * 0.1f) / (b.radius * 0.6f);
        assertEquals("轨道是围绕舰体的椭圆", 1f, nx * nx + ny * ny, 0.02f);
    }

    @Test
    public void prismSummonEmitsEventOnce() {
        summonPrismsInWorld(1);
        assertEquals(2, world.boss.prismsAlive());
        int count = 0;
        for (int i = 0; i < 60; i++) {
            world.update(1f / 60f);
            for (GameWorld.GameEvent e : world.pollEvents()) {
                if (e.type == GameWorld.EventType.BOSS_PRISM) {
                    count++;
                    assertEquals(2, e.code);
                }
            }
        }
        assertEquals("召唤事件只报一次", 1, count);
    }

    @Test
    public void prismsFireFixedCrossBeams() {
        summonPrismsInWorld(2);
        assertTrue(world.boss.prismsAlive() > 0);
        for (int i = 0; i < world.boss.partCount; i++) {
            if (world.boss.parts[i].kind == BossPart.Kind.PRISM) {
                world.boss.parts[i].fireCd = 0f;
            }
        }
        int fixed = 0;
        for (int i = 0; i < 120; i++) {
            world.update(1f / 60f);
            fixed = 0;
            for (BossLaser l : world.bossLasers) {
                if (l.active && l.fixedAim) fixed++;
            }
            if (fixed >= 4) break;
        }
        assertTrue("棱镜应同时放出四向十字光束", fixed >= 4);
    }

    @Test
    public void prismsCanBeDestroyedForScore() {
        summonPrismsInWorld(1);
        BossPart prism = firstPrism();
        assertNotNull(prism);
        // 棱镜轨道与机炮吊舱有交叠：清掉其它部件，确保这一发只会落在棱镜上
        for (int i = 0; i < world.boss.partCount; i++) {
            BossPart p = world.boss.parts[i];
            if (p.kind != BossPart.Kind.PRISM) p.active = false;
        }
        prism.hp = 1f;
        int scoreBefore = world.score;
        for (Enemy e : world.enemies) e.active = false;
        Bullet shot = null;
        for (Bullet b : world.bullets) if (!b.active) { shot = b; break; }
        assertNotNull(shot);
        shot.init(prism.worldX(world.boss), prism.worldY(world.boss), 0f, -120f, 1, false);
        world.update(1f / 60f);
        assertFalse("棱镜可被击毁", prism.active);
        assertTrue(world.score > scoreBefore);
        boolean broken = world.pollEvents().stream()
                .anyMatch(e -> e.type == GameWorld.EventType.BOSS_PART_DESTROYED);
        assertTrue(broken);
        assertEquals("另一枚棱镜仍在", 1, world.boss.prismsAlive());
    }
}
