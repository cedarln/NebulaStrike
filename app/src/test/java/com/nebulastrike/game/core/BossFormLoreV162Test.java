package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.62 迭代 52：形态台词与形态补给 FORM TAUNTS & FORM SUPPLY。
 *
 * <p>v1.61 让八艘旗舰长得不一样、动得不一样、招牌不一样；这一版让它们<b>开口</b>和<b>交货</b>：
 * 每个形态有一句入场台词（预警横幅）和一句三阶段台词（换段横幅），
 * 击沉它时额外必掉一件"正好治它自己那一手"的补给。
 * 补给是<b>额外一件</b>——原有的护盾 / 炸弹 / 火力 / 武器与后期机制道具一件不少，道具一律叠加。
 */
public class BossFormLoreV162Test {

    private GameWorld world;
    private final List<GameWorld.GameEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261118L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.bountyEnabled = false;
        world.perksEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.restart(GameMode.WAVES);
        world.spawningEnabled = false;
        world.perksEnabled = false;
        shieldForever();
        drain();
    }

    // ---- 辅助 ----

    private void shieldForever() {
        world.ship.makeInvulnerable(9999f);
    }

    private void step(int frames) {
        for (int i = 0; i < frames; i++) {
            world.update(1f / 60f);
            events.addAll(world.pollEvents());
        }
    }

    private void drain() {
        events.clear();
        events.addAll(world.pollEvents());
    }

    /** 让第 level 段航程的旗舰登场并当场击沉，返回它留在场上的补给种类。 */
    private List<PowerUp.Kind> spawnAndKill(int level) {
        world.bossGapTimer = 0f;
        world.stageClearTimer = 0f;
        world.bossesDefeated = level;
        world.wave = world.nextBossWave;
        step(1);
        assertTrue("旗舰该入场", world.boss.active);
        assertEquals("第 " + (level + 1) + " 段航程的形态",
                Boss.formFor(level), world.boss.form);
        // 词缀自己也会塞一件额外掉落，本节只查保底契约：把词缀压成无
        world.applyBossAffix(BossAffix.NONE);
        world.bossWarningTimer = 0f;
        world.bossPhaseBreakTimer = 0f;
        world.boss.y = world.boss.stationY;
        for (PowerUp p : world.powerups) p.active = false;
        drain();
        world.boss.hp = 0f;
        step(1);
        assertFalse("旗舰该被击破", world.boss.active);
        List<PowerUp.Kind> kinds = new ArrayList<>();
        for (PowerUp p : world.powerups) if (p.active) kinds.add(p.kind);
        return kinds;
    }

    /** 只让旗舰残响登场并击沉，返回它的补给（残响有自己的契约）。 */
    private List<PowerUp.Kind> killEcho() {
        spawnAndKill(0);
        assertTrue("干净击破应当留影", world.echoPendingTier >= 0);
        world.wave = world.echoSpawnWave;
        world.bossGapTimer = 0f;
        step(1);
        assertTrue("残响应到期入场", world.boss.active);
        assertTrue(world.echoBossFight);
        world.bossWarningTimer = 0f;
        world.boss.y = world.boss.stationY;
        for (PowerUp p : world.powerups) p.active = false;
        world.boss.hp = 0f;
        step(1);
        List<PowerUp.Kind> kinds = new ArrayList<>();
        for (PowerUp p : world.powerups) if (p.active) kinds.add(p.kind);
        return kinds;
    }

    /** 战场实力读数：任何一项都不该因为捡到道具而下降。 */
    private int[] readout() {
        int weapons = 0;
        for (WeaponType t : WeaponType.values()) if (world.ship.isWeaponEquipped(t)) weapons++;
        return new int[]{world.lives, world.ship.powerLevel, world.ship.bombs,
                world.ship.shieldLayers, world.ship.reflectLayers, world.ship.magnetStacks,
                weapons};
    }

    private static void assertNeverWorse(int[] before, int[] after, String what) {
        for (int i = 0; i < before.length; i++) {
            assertTrue(what + " 第 " + i + " 项下降: " + before[i] + " -> " + after[i],
                    after[i] >= before[i]);
        }
    }

    private void collect(PowerUp.Kind kind) {
        world.spawnPowerUp(kind, world.ship.x, world.ship.y);
        step(6);
    }

    // ---- 台词 ----

    @Test
    public void everyFormOpensItsMouthWithItsOwnLine() {
        Set<String> said = new HashSet<>();
        for (Boss.Form f : Boss.Form.values()) {
            assertTrue(f.name() + " 入场台词不能空", f.taunt != null && f.taunt.length() > 3);
            assertTrue(f.name() + " 台词太长按钮装不下: " + f.taunt, f.taunt.length() <= 16);
            assertTrue("台词重复: " + f.taunt, said.add(f.taunt));
        }
        assertEquals("八型旗舰八句不一样的话", Boss.Form.values().length, said.size());
    }

    @Test
    public void everyFormHasItsOwnPhaseThreeLine() {
        Set<String> said = new HashSet<>();
        for (Boss.Form f : Boss.Form.values()) {
            assertTrue(f.name() + " 三阶段台词不能空", f.rage != null && f.rage.length() > 2);
            assertTrue(f.name() + " 台词太长按钮装不下: " + f.rage, f.rage.length() <= 16);
            assertTrue("两句不许一样: " + f.name(), !f.rage.equals(f.taunt));
            assertTrue("三阶段台词重复: " + f.rage, said.add(f.rage));
        }
        assertEquals(Boss.Form.values().length, said.size());
    }

    @Test
    public void theFinalFlagshipCallsThePilotByName() {
        // 星核是这条时间线的终点：它开口就该点到星火号，而不是随便一句话
        assertTrue(Boss.Form.SENTINEL.taunt.contains("星火"));
        assertTrue(Boss.Form.SENTINEL.rage.contains("回声"));
    }

    @Test
    public void theEchoSharesTheVoiceButNotThePayroll() {
        // 残响是同一艘船的影子：形态（和台词）跟着本体，但补给按残响的契约结算
        Boss.Form echoForm = Boss.formFor(0);
        assertEquals("静默", echoForm.label);
        List<PowerUp.Kind> drops = killEcho();
        assertEquals("残响只交火力与武器各一件", 2, drops.size());
        assertTrue("残响该给火力", drops.contains(PowerUp.Kind.POWER));
        assertFalse("残响不掉形态补给", drops.contains(echoForm.supply));
    }

    // ---- 形态补给 ----

    @Test
    public void everyFormPaysOutItsOwnSupply() {
        Set<PowerUp.Kind> paid = new HashSet<>();
        for (int level = 0; level < Boss.Form.values().length - 1; level++) {
            Boss.Form form = Boss.formFor(level);
            List<PowerUp.Kind> drops = spawnAndKill(level);
            assertTrue(form.label + " 该掉自己的补给 " + form.supply,
                    drops.contains(form.supply));
            assertTrue("补给重复: " + form.supply, paid.add(form.supply));
        }
    }

    @Test
    public void suppliesAreEightDifferentPickups() {
        Set<PowerUp.Kind> kinds = new HashSet<>();
        for (Boss.Form f : Boss.Form.values()) {
            assertTrue(f.name() + " 必须指定补给", f.supply != null);
            assertTrue("两种形态给了同一件补给: " + f.supply, kinds.add(f.supply));
        }
        assertEquals(Boss.Form.values().length, kinds.size());
    }

    @Test
    public void theGuaranteedRewardsStillLand() {
        Boss.Form form = Boss.formFor(1);
        List<PowerUp.Kind> drops = spawnAndKill(1);
        assertTrue("护盾照旧", drops.contains(PowerUp.Kind.SHIELD));
        assertTrue("炸弹照旧", drops.contains(PowerUp.Kind.BOMB));
        assertTrue("火力照旧", drops.contains(PowerUp.Kind.POWER));
        assertTrue("形态补给照旧", drops.contains(form.supply));
        assertTrue("机制道具照旧", drops.contains(PowerUp.Kind.ORBIT)
                || drops.contains(PowerUp.Kind.CHAIN));
        assertTrue("补给只加不减: " + drops.size(), drops.size() >= 6);
    }

    @Test
    public void biggerHullsPayMoreNotLess() {
        // tier 0（第一段航程）5 件，tier 1 起 6 件：多的那件永远是形态补给，不是替换掉谁
        int first = spawnAndKill(0).size();
        int later = spawnAndKill(4).size();
        assertEquals("首段：四件保底加一件补给", 5, first);
        assertEquals("二段起：保底四件加机制道具加补给", 6, later);
        assertNotEquals(Boss.formFor(0).supply, Boss.formFor(4).supply);
    }

    @Test
    public void theFinalFlagshipPaysALife() {
        world.bossesDefeated = Story.SECTOR_COUNT - 1;
        world.wave = world.nextBossWave;
        step(1);
        assertTrue("第八段是最终旗舰", world.finalBossFight);
        assertEquals(Boss.Form.SENTINEL, world.boss.form);
        world.bossWarningTimer = 0f;
        world.boss.y = world.boss.stationY;
        for (PowerUp p : world.powerups) p.active = false;
        // 终局重构：第一次打空血只把血拉回来，第二刀才算击沉
        world.boss.hp = 0f;
        step(1);
        assertTrue("第一次打空只该触发重构", world.boss.active);
        for (PowerUp p : world.powerups) p.active = false;
        world.boss.hp = 0f;
        step(1);
        assertFalse("重构之后就该沉了", world.boss.active);
        List<PowerUp.Kind> drops = new ArrayList<>();
        for (PowerUp p : world.powerups) if (p.active) drops.add(p.kind);
        assertTrue("打穿最后一段该补一条命: " + drops, drops.contains(PowerUp.Kind.LIFE));
    }

    @Test
    public void practiceShipsNoSupplyCrate() {
        // 练功不记任何账，也不该发补给：形态台词照旧，掉落契约照旧为零
        world.state = GameWorld.State.MENU;
        world.settings.codexBossKills[2] = 1;
        world.handleUiAction(Ui.Action.OPEN_CODEX);
        world.codexTab = 1;
        Ui.Rect row = Ui.codexRow(2);
        world.handleUiAction(Ui.hit(GameWorld.State.MENU, false, false, true, false,
                false, false, world.codexTab, row.cx(), row.cy()));
        drain();
        for (int i = 0; i < 400 && !world.boss.active; i++) step(1);
        assertTrue("模拟战里旗舰自动入场", world.boss.active);
        assertEquals(Boss.formFor(2), world.boss.form);
        world.bossWarningTimer = 0f;
        world.boss.y = world.boss.stationY;
        for (PowerUp p : world.powerups) p.active = false;
        world.boss.hp = 0f;
        step(1);
        int drops = 0;
        for (PowerUp p : world.powerups) if (p.active) drops++;
        assertEquals("练功不发补给", 0, drops);
    }

    @Test
    public void suppliesAlwaysLandOnScreen() {
        for (int level = 0; level < Boss.Form.values().length - 1; level++) {
            spawnAndKill(level);
            // 旗舰贴边时补给也要落在航道内，否则等于没掉
            for (PowerUp p : world.powerups) {
                if (!p.active) continue;
                assertTrue("补给出界 x=" + p.x, p.x >= 0f && p.x <= GameWorld.WIDTH);
                assertTrue("补给出界 y=" + p.y, p.y >= 0f && p.y <= GameWorld.HEIGHT);
            }
        }
    }

    @Test
    public void noSupplyEverWeakensTheShip() {
        // 玩家要求过：捡到东西只会更厉害。八种形态补给挨个捡一遍，读数只升不降
        for (Boss.Form f : Boss.Form.values()) {
            world.restart(GameMode.WAVES);
            world.spawningEnabled = false;
            world.perksEnabled = false;
            shieldForever();
            int[] before = readout();
            collect(f.supply);
            int[] after = readout();
            assertNeverWorse(before, after, f.name() + " 的补给 " + f.supply);
        }
    }

    @Test
    public void supplyStacksWithItself() {
        // 磁暴形态给的正是磁力核心：再捡一层要能叠上去，而不是换掉手上这件
        world.restart(GameMode.WAVES);
        world.spawningEnabled = false;
        shieldForever();
        int base = world.ship.magnetStacks;
        collect(PowerUp.Kind.MAGNET);
        assertEquals("第一层", base + 1, world.ship.magnetStacks);
        collect(PowerUp.Kind.MAGNET);
        assertEquals("第二层照叠", base + 2, world.ship.magnetStacks);
    }

    @Test
    public void theRushLoopKeepsPayingFormsTheirSupply() {
        // 连战没有小怪，但击破契约不变：这一艘是什么形态，就交这一形态的货
        world.restart(GameMode.RUSH);
        world.spawningEnabled = false;
        world.perksEnabled = false;
        shieldForever();
        for (int i = 0; i < 600 && !world.boss.active; i++) step(1);
        assertTrue("连战旗舰自动入场", world.boss.active);
        Boss.Form form = world.boss.form;
        world.bossWarningTimer = 0f;
        world.bossPhaseBreakTimer = 0f;
        world.boss.y = world.boss.stationY;
        for (PowerUp p : world.powerups) p.active = false;
        world.boss.hp = 0f;
        step(1);
        boolean paid = false;
        for (PowerUp p : world.powerups) if (p.active && p.kind == form.supply) paid = true;
        assertTrue(form.label + " 形态该掉 " + form.supply, paid);
    }
}
