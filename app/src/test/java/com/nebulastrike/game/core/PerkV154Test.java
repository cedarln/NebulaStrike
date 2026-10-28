package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.54 迭代 44：局内三选一 PERK。
 *
 * <p>设计约束：强化只做加法——同类最多叠 3 层，不替换任何已有武器/道具；
 * 选卡期间整局冻结，避免玩家低头做决策时被流弹惩罚。
 */
public class PerkV154Test {

    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(20261028L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.bountyEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.lives = 999_999;
        world.perksEnabled = true;
        world.ship.x = 120f;
        world.ship.prevX = 120f;
        world.ship.y = 1460f;
        world.ship.prevY = 1460f;
    }

    // ---- 辅助 ----

    private void triggerBoss() {
        world.wave = 4;
        world.update(1f / 60f);
        assertTrue("Boss 应已入场", world.boss.active);
        world.bossWarningTimer = 0f;
    }

    private void killBoss() {
        world.boss.hp = 0f;
        world.update(1f / 60f);
    }

    private void stepUntilOffer() {
        for (int i = 0; i < 240 && !world.perkOpen; i++) {
            world.update(1f / 60f);
        }
        assertTrue("击破旗舰后应弹出强化卡", world.perkOpen);
    }

    private List<GameWorld.GameEvent> step(int frames) {
        List<GameWorld.GameEvent> all = new ArrayList<>();
        for (int i = 0; i < frames; i++) {
            world.update(1f / 60f);
            all.addAll(world.pollEvents());
        }
        return all;
    }

    private static boolean hasEvent(List<GameWorld.GameEvent> events,
                                    GameWorld.EventType type) {
        for (GameWorld.GameEvent e : events) {
            if (e.type == type) return true;
        }
        return false;
    }

    // ---- 触发与冻结 ----

    @Test
    public void bossDefeatOffersThreeDistinctPerks() {
        triggerBoss();
        killBoss();
        stepUntilOffer();

        assertEquals("一次展示三张卡", Perk.CHOICES, world.perkChoicesCount);
        Set<Perk> seen = new HashSet<>();
        for (int i = 0; i < world.perkChoicesCount; i++) {
            Perk p = world.perkChoices[i];
            assertNotNull("候选不能为空", p);
            assertTrue("候选不能重复", seen.add(p));
        }
        assertEquals("六类强化里应能抽出三张不同的", 3, seen.size());
    }

    @Test
    public void choosingFreezesEverythingUntilPlayerPicks() {
        triggerBoss();
        killBoss();
        stepUntilOffer();

        // 在卡片后面塞一颗敌弹：冻结期间它连一帧都不该动
        EnemyBullet probe = null;
        for (EnemyBullet b : world.enemyBullets) {
            if (!b.active) { probe = b; break; }
        }
        assertNotNull(probe);
        probe.init(100f, 100f, 120f, 0f);
        float x0 = probe.x;

        float time0 = world.timeAlive;
        float cd0 = world.weaponCdForTest(WeaponType.VULCAN);
        step(60);

        assertEquals("选卡时对局时钟冻结", time0, world.timeAlive, 0.0001f);
        assertTrue("选卡时弹道冻结", probe.active);
        assertEquals("选卡时敌弹不移动", x0, probe.x, 0.0001f);
        assertEquals("选卡时武器冷却不推进", cd0,
                world.weaponCdForTest(WeaponType.VULCAN), 0.0001f);
        assertEquals("卡片仍在等待选择", Perk.CHOICES, world.perkChoicesCount);
    }

    @Test
    public void choosePerkStacksAndClosesCard() {
        triggerBoss();
        killBoss();
        stepUntilOffer();
        Perk picked = world.perkChoices[1];

        assertTrue("合法下标应被接受", world.choosePerk(1));
        assertFalse("选完立即关闭", world.perkOpen);
        assertEquals("选中项叠一层", 1, world.perkStacksOf(picked));
        assertEquals("只记录一次选择", 1, world.perksChosen);
        assertEquals("强化横幅开始计时", GameWorld.PERK_FLASH_TIME,
                world.perkFlash, 0.001f);

        List<GameWorld.GameEvent> events = world.pollEvents();
        boolean chosenEvent = false;
        for (GameWorld.GameEvent e : events) {
            if (e.type == GameWorld.EventType.PERK_CHOSEN
                    && e.code == picked.ordinal()) chosenEvent = true;
        }
        assertTrue("必须发出 PERK_CHOSEN 事件", chosenEvent);

        // 关闭后再点同一张卡不再生效
        assertFalse(world.choosePerk(1));
        assertEquals(1, world.perkStacksOf(picked));
    }

    @Test
    public void outOfRangeChoiceIsIgnored() {
        triggerBoss();
        killBoss();
        stepUntilOffer();
        assertFalse(world.choosePerk(-1));
        assertFalse(world.choosePerk(Perk.CHOICES));
        assertTrue("非法点击不关卡片", world.perkOpen);
    }

    // ---- 六类强化的数值 ----

    @Test
    public void overclockShortensAllWeaponCooldowns() {
        assertEquals(1f, world.perkCooldownMul(), 0.0001f);
        world.grantPerkForTest(Perk.OVERCLOCK);
        world.grantPerkForTest(Perk.OVERCLOCK);
        assertEquals("两层超频 → 冷却 ×0.84", 0.84f, world.perkCooldownMul(), 0.0001f);

        // 与机体/速射相乘：开火冷却确实变短，而且基础炮仍然常驻
        WeaponType type = WeaponType.VULCAN;
        world.resetWeaponCooldowns();
        world.update(1f / 60f);
        float expected = type.cooldown * world.hull().fireCdMul * 0.84f;
        assertEquals(expected, world.weaponCdForTest(type), 0.002f);
    }

    @Test
    public void thrusterRaisesShipSpeedOnTopOfHull() {
        assertEquals(world.hull().speedMul, world.ship.speedScale, 0.0001f);
        world.grantPerkForTest(Perk.THRUSTER);
        assertEquals(world.hull().speedMul * 1.08f, world.ship.speedScale, 0.0001f);
        world.grantPerkForTest(Perk.THRUSTER);
        world.grantPerkForTest(Perk.THRUSTER);
        assertEquals("三层推进器 → +24%", world.hull().speedMul * 1.24f,
                world.ship.speedScale, 0.0001f);
    }

    @Test
    public void armorRaisesShieldCapAndAddsLayer() {
        assertEquals(0, world.ship.shieldLayers);
        assertEquals(Ship.MAX_SHIELD_LAYERS, world.ship.shieldCap);
        world.grantPerkForTest(Perk.ARMOR);
        assertEquals("立刻补一层", 1, world.ship.shieldLayers);
        assertEquals("护盾上限 +1", Ship.MAX_SHIELD_LAYERS + 1, world.ship.shieldCap);

        world.grantPerkForTest(Perk.ARMOR);
        assertEquals(2, world.ship.shieldLayers);
        assertEquals(Ship.MAX_SHIELD_LAYERS + 2, world.ship.shieldCap);
    }

    @Test
    public void arsenalRaisesBombCapAndAddsBomb() {
        assertEquals(1, world.ship.bombs);
        world.grantPerkForTest(Perk.ARSENAL);
        assertEquals("炸弹上限 +1", Ship.MAX_BOMBS + 1, world.ship.bombCap);
        assertEquals("立刻补一颗", 2, world.ship.bombs);

        // 拾取炸弹道具时吃的是新的上限，而不是旧常量
        world.ship.bombs = world.ship.bombCap;
        world.spawnPowerUp(PowerUp.Kind.BOMB, world.ship.x, world.ship.y);
        world.update(1f / 60f);
        assertEquals("上限已满时转成奖励分，不会溢出", world.ship.bombCap,
                world.ship.bombs);
    }

    @Test
    public void luckAndCritRaiseTheirRates() {
        assertEquals(1f, world.perkDropMul(), 0.0001f);
        assertEquals(0f, world.perkCritBonus(), 0.0001f);
        world.grantPerkForTest(Perk.LUCK);
        world.grantPerkForTest(Perk.CRIT);
        world.grantPerkForTest(Perk.CRIT);
        assertEquals("一层幸运 +22% 掉落", 1.22f, world.perkDropMul(), 0.0001f);
        assertEquals("两层精准 +10% 暴击", 0.10f, world.perkCritBonus(), 0.0001f);
    }

    // ---- 上限、候选池与重置 ----

    @Test
    public void eachPerkCapsAtThreeStacks() {
        for (int i = 0; i < 5; i++) world.grantPerkForTest(Perk.OVERCLOCK);
        assertEquals("同类最多三层", Perk.MAX_STACKS,
                world.perkStacksOf(Perk.OVERCLOCK));
        assertEquals(1f - 0.08f * Perk.MAX_STACKS, world.perkCooldownMul(), 0.0001f);
    }

    @Test
    public void offerOnlyContainsPerksThatCanStillStack() {
        for (Perk p : Perk.values()) {
            for (int i = 0; i < Perk.MAX_STACKS; i++) {
                if (p != Perk.LUCK) world.grantPerkForTest(p);
            }
        }
        triggerBoss();
        killBoss();
        stepUntilOffer();
        assertEquals("只剩幸运能叠，就只出这一张", 1, world.perkChoicesCount);
        assertEquals(Perk.LUCK, world.perkChoices[0]);
    }

    @Test
    public void allMaxedConvertsToScoreInsteadOfCard() {
        for (Perk p : Perk.values()) {
            for (int i = 0; i < Perk.MAX_STACKS; i++) world.grantPerkForTest(p);
        }
        assertFalse(world.perkAvailable());
        triggerBoss();
        int before = world.score;
        killBoss();
        assertEquals("全满时不再排队发卡", 0f, world.perkOfferTimer, 0.0001f);
        step(180);
        assertFalse("全满时不弹任何卡", world.perkOpen);
        assertTrue("击破与折算奖励都应入账", world.score > before + GameWorld.PERK_MAXED_SCORE);
    }

    @Test
    public void finalBossNeverOffersPerk() {
        world.restart(GameMode.WAVES);
        world.perksEnabled = true;
        // 飞到最终旗舰：先让 Boss 入场，再打空两次（第一次触发终局重构）
        world.boss.active = false;
        world.bossActive = false;
        world.bossesDefeated = Story.SECTOR_COUNT - 1;
        world.bossGapTimer = 0f;
        world.nextBossWave = 4;
        world.wave = 4;
        world.update(1f / 60f);
        assertTrue(world.finalBossFight);
        world.bossWarningTimer = 0f;
        world.boss.hp = 0f;
        world.update(1f / 60f);
        world.boss.hp = 0f;
        world.update(1f / 60f);

        assertTrue("最终旗舰应直接进入通关流程", world.campaignVictory);
        assertEquals("通关不排队强化", 0f, world.perkOfferTimer, 0.0001f);
        step(180);
        assertFalse("通关演出不会被强化卡打断", world.perkOpen);
    }

    @Test
    public void restartClearsPerkProgress() {
        world.grantPerkForTest(Perk.THRUSTER);
        world.grantPerkForTest(Perk.ARMOR);
        world.grantPerkForTest(Perk.ARSENAL);
        world.restart(GameMode.WAVES);

        for (Perk p : Perk.values()) {
            assertEquals("强化随一局结束清零", 0,
                    world.perkStacksOf(p));
        }
        assertEquals(0, world.perksChosen);
        assertEquals(Ship.MAX_BOMBS, world.ship.bombCap);
        assertEquals(world.hull().speedMul, world.ship.speedScale, 0.0001f);
        assertEquals(Ship.MAX_SHIELD_LAYERS, world.ship.shieldCap);
        assertFalse(world.perkOpen);
    }

    @Test
    public void offerEventCarriesCardCount() {
        triggerBoss();
        killBoss();
        List<GameWorld.GameEvent> events = new ArrayList<>();
        for (int i = 0; i < 240 && !world.perkOpen; i++) {
            world.update(1f / 60f);
            events.addAll(world.pollEvents());
        }
        assertTrue("必须发出 PERK_OFFER 事件", hasEvent(events, GameWorld.EventType.PERK_OFFER));
        for (GameWorld.GameEvent e : events) {
            if (e.type == GameWorld.EventType.PERK_OFFER) {
                assertEquals(Perk.CHOICES, e.code);
            }
        }
    }
}
