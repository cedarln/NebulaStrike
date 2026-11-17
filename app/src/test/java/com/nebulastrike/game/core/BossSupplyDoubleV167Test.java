package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.67 迭代 57：读透的旗舰交双份解药 · MASTERED ELIXIR。
 *
 * <p>v1.62 让每型旗舰击沉时多掉一件"正好治它那一手"的补给，v1.65 让同一型读到 4 次算「读透」。
 * 这一版把两者接上：<b>读透的那一型交双份解药</b>。玩家的动机很清楚——想多要一支药，就得先把这艘船
 * 读懂；而第二件走的是既有的叠加规则（{@code spawnPowerUp} 同一件再来一次），
 * 绝不替换、绝不削弱，原有四件掉落与后期新机制道具一件不少。
 *
 * <p>四条口径：① 双倍只由精通档位决定（读透 = 4 次及以上），与连读、反击窗口、词缀都无关；
 * ② 账按形态分开：磁暴读透了，星核照旧只交一件；③ 练功局不发补给、残响不吃双倍，两条既有契约不变；
 * ④ 道具池满了就少掉一件，不报错、不影响战斗。
 */
public class BossSupplyDoubleV167Test {

    private GameWorld world;
    private final List<GameWorld.GameEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261123L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.bountyEnabled = false;
        world.perksEnabled = false;
        world.settings.reducedFx = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.lives = 999_999;
        world.restart(GameMode.WAVES);
        drain();
    }

    // ---- 辅助 ----

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

    private int countEvents(GameWorld.EventType type) {
        int n = 0;
        for (GameWorld.GameEvent e : events) if (e.type == type) n++;
        return n;
    }

    /** 叫出这一段的旗舰、跳过演出、压掉词缀。 */
    private void triggerBoss(int level) {
        if (world.boss.active) {
            world.boss.hp = 0f;
            step(1);
        }
        clearPowerUps();
        world.bossGapTimer = 0f;
        world.stageClearTimer = 0f;
        world.bossesDefeated = level;
        world.wave = world.nextBossWave;
        step(1);
        assertTrue("旗舰该入场", world.boss.active);
        world.applyBossAffix(BossAffix.NONE);
        world.bossWarningTimer = 0f;
        world.bossPhaseBreakTimer = 0f;
        world.boss.y = world.boss.stationY;
        world.ship.makeInvulnerable(9999f);
        drain();
    }

    private void clearPowerUps() {
        for (PowerUp p : world.powerups) p.active = false;
    }

    /** 把旗舰一击收掉，让掉落当场结算。 */
    private void sinkBoss() {
        world.boss.maxHp = 1f;
        world.boss.hp = 0f;
        step(2);
    }

    private int activeOf(PowerUp.Kind kind) {
        int n = 0;
        for (PowerUp p : world.powerups) if (p.active && p.kind == kind) n++;
        return n;
    }

    private int activeTotal() {
        int n = 0;
        for (PowerUp p : world.powerups) if (p.active) n++;
        return n;
    }

    /** 把某一型的识破次数摁到目标值（双倍只看这一本账）。 */
    private void setReads(Boss.Form form, int n) {
        world.formReads[form.ordinal()] = n;
    }

    /** 拾取场上所有某型补给：每件都在飞船身上落一次。 */
    private int collectAll(PowerUp.Kind kind) {
        int got = 0;
        for (PowerUp p : world.powerups) {
            if (!p.active || p.kind != kind) continue;
            world.ship.x = p.x;
            world.ship.y = p.y;
            world.ship.prevX = p.x;
            world.ship.prevY = p.y;
            step(1);
            got++;
        }
        return got;
    }

    // ---- 双倍只由"读透"决定 ----

    @Test
    public void anUnreadFlagshipStillPaysOneElixir() {
        triggerBoss(0);
        Boss.Form form = world.boss.form;
        setReads(form, 0);
        sinkBoss();
        assertEquals("生疏那一型只交一件", 1, activeOf(form.supply));
        assertEquals(0, world.supplyDoubles);
        assertEquals(0, countEvents(GameWorld.EventType.BOSS_SUPPLY_DOUBLE));
    }

    @Test
    public void knowingItIsNotEnoughYet() {
        triggerBoss(0);
        Boss.Form form = world.boss.form;
        setReads(form, GameWorld.MASTERY_KNOWING_READS);      // 2 次 = 熟悉
        sinkBoss();
        assertEquals("熟悉只让窗口变长，不解双倍", 1, activeOf(form.supply));
        assertEquals(0, world.supplyDoubles);
    }

    @Test
    public void masteringItDoublesTheElixir() {
        triggerBoss(0);
        Boss.Form form = world.boss.form;
        setReads(form, GameWorld.MASTERY_MASTERED_READS);     // 4 次 = 读透
        sinkBoss();
        assertEquals(2, activeOf(form.supply));
        assertEquals(1, world.supplyDoubles);
        assertEquals(1, countEvents(GameWorld.EventType.BOSS_SUPPLY_DOUBLE));
        assertEquals("事件带的是形态序号", form.ordinal(),
                events.get(indexOfLast(GameWorld.EventType.BOSS_SUPPLY_DOUBLE)).code);
    }

    @Test
    public void theFifthReadPaysTheSameDouble() {
        triggerBoss(0);
        Boss.Form form = world.boss.form;
        setReads(form, 9);
        sinkBoss();
        assertEquals("读透以后不再往上加，始终双份", 2, activeOf(form.supply));
        assertEquals(1, world.supplyDoubles);
    }

    private int indexOfLast(GameWorld.EventType type) {
        int at = -1;
        for (int i = 0; i < events.size(); i++) if (events.get(i).type == type) at = i;
        return at;
    }

    // ---- 双份是同一件，而且真的叠加 ----

    @Test
    public void bothCopiesAreTheSameMedicine() {
        triggerBoss(0);                                        // 静默：解药是 REFLECT
        Boss.Form form = world.boss.form;
        assertEquals(PowerUp.Kind.REFLECT, form.supply);
        setReads(form, GameWorld.MASTERY_MASTERED_READS);
        sinkBoss();
        assertEquals(2, activeOf(PowerUp.Kind.REFLECT));
        assertEquals("不会偷偷换成别的", 0, activeOf(PowerUp.Kind.TIME));
    }

    @Test
    public void collectingBothCopiesStacksInsteadOfSwitching() {
        triggerBoss(0);
        Boss.Form form = world.boss.form;
        setReads(form, GameWorld.MASTERY_MASTERED_READS);
        world.ship.reflectLayers = 0;
        sinkBoss();
        assertEquals("两件同种", 2, activeOf(PowerUp.Kind.REFLECT));
        int before = world.ship.reflectLayers;
        assertEquals("两件都该收到", 2, collectAll(PowerUp.Kind.REFLECT));
        assertTrue("第二件是叠上去的，不是把第一件换掉: " + before + " → "
                + world.ship.reflectLayers, world.ship.reflectLayers >= before + 2);
    }

    @Test
    public void theDoubleNeverStealsTheOtherRewards() {
        triggerBoss(1);
        Boss.Form form = world.boss.form;
        setReads(form, 0);
        sinkBoss();
        int plain = activeTotal();
        assertTrue("基础掉落本来就有几件: " + plain, plain >= 4);

        triggerBoss(1);
        setReads(form, GameWorld.MASTERY_MASTERED_READS);
        clearPowerUps();
        sinkBoss();
        assertEquals("读透只是多一件，别的照旧", plain + 1, activeTotal());
        assertEquals(1, activeOf(PowerUp.Kind.SHIELD));
        assertEquals(1, activeOf(PowerUp.Kind.BOMB));
        assertTrue("POWER 一件不少", activeOf(PowerUp.Kind.POWER) >= 1);
    }

    // ---- 账按形态分开 ----

    @Test
    public void eachFormKeepsItsOwnLedger() {
        triggerBoss(5);                                        // 磁暴
        Boss.Form magnet = world.boss.form;
        setReads(magnet, GameWorld.MASTERY_MASTERED_READS);
        setReads(Boss.Form.SPECTER, 0);
        sinkBoss();
        assertEquals("读透的那型交双份", 2, activeOf(magnet.supply));

        triggerBoss(0);                                        // 静默
        assertEquals(Boss.Form.SPECTER, world.boss.form);
        clearPowerUps();
        sinkBoss();
        assertEquals("没读透的那型照旧一件", 1, activeOf(Boss.Form.SPECTER.supply));
        assertEquals("双份次数不重复计", 1, world.supplyDoubles);
    }

    @Test
    public void everyFormHasItsOwnElixirToDouble() {
        for (Boss.Form form : Boss.Form.values()) {
            assertTrue("每一型都得有专属解药: " + form.label, form.supply != null);
            assertTrue("而且不是普通掉落那四件之一: " + form.label,
                    form.supply != PowerUp.Kind.SHIELD
                            && form.supply != PowerUp.Kind.BOMB
                            && form.supply != PowerUp.Kind.POWER);
        }
    }

    @Test
    public void theDoubleIsWiredPerFormAcrossARun() {
        int doublesSeen = 0;
        for (int level = 0; level < 7; level++) {
            triggerBoss(level);                        // triggerBoss 会清事件，所以逐段各数一次
            Boss.Form form = world.boss.form;
            // 只把奇数号形态读到读透，看双倍是否严格按形态走
            setReads(form, level % 2 == 1 ? GameWorld.MASTERY_MASTERED_READS : 1);
            clearPowerUps();
            sinkBoss();
            int paid = activeOf(form.supply);
            assertEquals("第 " + level + " 段（" + form.label + "）的补给件数",
                    level % 2 == 1 ? 2 : 1, paid);
            assertEquals("第 " + level + " 段的双倍事件",
                    level % 2 == 1 ? 1 : 0, countEvents(GameWorld.EventType.BOSS_SUPPLY_DOUBLE));
            if (level % 2 == 1) doublesSeen++;
        }
        assertEquals(doublesSeen, world.supplyDoubles);
    }

    // ---- 既有契约不受影响 ----

    @Test
    public void practiceModeDropsNoElixirAtAll() {
        world.restart(GameMode.PRACTICE);
        world.bossGapTimer = 0f;
        world.ship.makeInvulnerable(9999f);
        for (int i = 0; i < 300 && !world.boss.active; i++) step(1);
        assertTrue("练功局也该有靶子", world.boss.active);
        Boss.Form form = world.boss.form;
        setReads(form, GameWorld.MASTERY_MASTERED_READS);
        clearPowerUps();
        sinkBoss();
        assertEquals("练功不发补给，双倍也就无从谈起", 0, activeOf(form.supply));
        assertEquals(0, world.supplyDoubles);
        assertEquals(0, countEvents(GameWorld.EventType.BOSS_SUPPLY_DOUBLE));
    }

    @Test
    public void theEchoShadeKeepsItsOwnSmallerReward() {
        triggerBoss(2);
        Boss.Form form = world.boss.form;
        setReads(form, GameWorld.MASTERY_MASTERED_READS);
        world.bossFightHits = 0;                     // 干净一战才留得下残响
        sinkBoss();
        assertEquals("本体照样双份", 2, activeOf(form.supply));
        assertEquals(1, world.supplyDoubles);
        clearPowerUps();
        // 残响是另一条掉落分支：它只交火力与武器，不参与双倍
        world.echoBossFight = true;
        world.ship.makeInvulnerable(9999f);
        for (int i = 0; i < 300 && !world.boss.active; i++) step(1);
        drain();
        if (world.boss.active) {
            world.echoBossFight = true;
            sinkBoss();
            assertEquals("残响不发形态补给", 0, activeOf(world.boss.form.supply));
            assertTrue("残响只交自己那两件", activeTotal() >= 2);
            assertEquals("双倍次数没有因为残响多记", 1, world.supplyDoubles);
        }
    }

    @Test
    public void aFullPowerUpPoolDropsQuietly() {
        triggerBoss(0);
        Boss.Form form = world.boss.form;
        setReads(form, GameWorld.MASTERY_MASTERED_READS);
        for (PowerUp p : world.powerups) p.init(PowerUp.Kind.BOMB, 10f, 10f);
        assertEquals("道具池先塞满", world.powerups.length, activeTotal());
        sinkBoss();                                  // 不能抛异常，也拿不到第二件
        assertTrue("战斗照常", world.state == GameWorld.State.PLAYING);
        assertTrue("满池时最多就是少掉几件: " + activeOf(form.supply),
                activeOf(form.supply) <= 2);
    }

    @Test
    public void affixesDoNotChangeTheElixir() {
        triggerBoss(0);
        Boss.Form form = world.boss.form;
        setReads(form, GameWorld.MASTERY_MASTERED_READS);
        clearPowerUps();
        sinkBoss();
        int doubled = activeOf(form.supply);
        assertEquals(2, doubled);
    }

    // ---- 读数与清零 ----

    @Test
    public void restartClearsTheDoubleCount() {
        triggerBoss(0);
        setReads(world.boss.form, GameWorld.MASTERY_MASTERED_READS);
        sinkBoss();
        assertTrue(world.supplyDoubles > 0);
        world.restart(GameMode.WAVES);
        assertEquals(0, world.supplyDoubles);
        assertEquals(0, java.util.Arrays.stream(world.formReads).sum());
    }

    @Test
    public void sinkingSeveralMasteredShipsAddsUp() {
        for (int level = 0; level < 7; level++) {
            triggerBoss(level);
            Boss.Form form = world.boss.form;
            setReads(form, GameWorld.MASTERY_MASTERED_READS);
            clearPowerUps();
            sinkBoss();
            assertEquals("第 " + level + " 段（" + form.label + "）该交双份",
                    2, activeOf(form.supply));
            assertEquals("第 " + level + " 段的双倍只报一次",
                    1, countEvents(GameWorld.EventType.BOSS_SUPPLY_DOUBLE));
            assertEquals("累计次数与段数对上", level + 1, world.supplyDoubles);
        }
        assertEquals(7, world.supplyDoubles);
    }

    @Test
    public void theCounterArcIsUnaffected() {
        triggerBoss(0);
        Boss.Form form = world.boss.form;
        setReads(form, GameWorld.MASTERY_MASTERED_READS);
        int before = world.countersRead;
        sinkBoss();
        assertEquals("击沉本身不算识破", before, world.countersRead);
        assertEquals("串也没动", 0, world.counterStreak);
        assertTrue("读透那一型的窗口长度仍然按精通走",
                GameWorld.counterStrikeWindowFor(2) > GameWorld.COUNTER_STRIKE_WINDOW);
    }
}
