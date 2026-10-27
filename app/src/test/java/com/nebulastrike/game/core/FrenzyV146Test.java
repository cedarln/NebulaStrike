package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.46 迭代 36：连杀狂热 FRENZY。
 *
 * <p>连杀攒到 12 就点着 6 秒狂热：全武器冷却 ×0.72、得分 ×1.25。狂热不改变连杀窗口，
 * 也不替代任何既有加成——它和双倍分数 / 速射 / 过载 / 连携各自叠乘。挨一下或连杀断档，
 * 狂热当场熄火，下一次要重新攒满 12 连。
 */
public class FrenzyV146Test {

    private GameWorld world;

    @Before
    public void setUp() {
        world = newGame();
    }

    private GameWorld newGame() {
        GameWorld w = new GameWorld(2026L);
        w.spawningEnabled = false;
        w.mutatorsEnabled = false;
        w.settings.setDifficulty(Difficulty.STANDARD);
        w.ship.makeInvulnerable(1_000f);
        w.ship.y = 1460f;
        w.ship.prevY = 1460f;
        return w;
    }

    // ---- 辅助 ----

    private Enemy freeEnemy(GameWorld w) {
        for (Enemy e : w.enemies) if (!e.active) return e;
        throw new IllegalStateException("敌机池已满");
    }

    /** 击坠 n 架无人机，全部走真实计分 / 连杀路径。 */
    private void chainKills(GameWorld w, int n) {
        for (int i = 0; i < n; i++) killDrone(w);
    }

    private void killDrone(GameWorld w) {
        Enemy e = freeEnemy(w);
        e.init(EnemyType.DRONE, 300f, 400f, 1f);
        w.killEnemyForTest(e);
    }

    private List<GameWorld.GameEvent> drain(GameWorld w) {
        List<GameWorld.GameEvent> out = new ArrayList<>();
        for (GameWorld.GameEvent e : w.pollEvents()) out.add(e);
        return out;
    }

    private void step(GameWorld w, int frames) {
        for (int i = 0; i < frames; i++) w.update(1f / 60f);
    }

    private boolean has(List<GameWorld.GameEvent> events, GameWorld.EventType type) {
        for (GameWorld.GameEvent e : events) if (e.type == type) return true;
        return false;
    }

    /** 把一架敌机直接怼到飞船上，走真实的受击路径。 */
    private void hitShip(GameWorld w) {
        // MakeInvulnerable 只保护计时器，这里直接把标志摘掉；冲刺本来就没在用
        w.ship.invulnerable = false;
        w.ship.dashTimer = 0f;
        Enemy e = freeEnemy(w);
        e.init(EnemyType.DRONE, w.ship.x, w.ship.y, 1f);
        w.update(1f / 60f);
    }

    // ---- 点火 ----

    @Test
    public void frenzyIgnitesAtTwelveChainedKills() {
        chainKills(world, GameWorld.FRENZY_KILLS - 1);
        assertFalse("11 连还不够", world.frenzyActive());
        drain(world);

        chainKills(world, 1);

        assertTrue("第 12 连点着狂热", world.frenzyActive());
        assertEquals(GameWorld.FRENZY_TIME, world.frenzyTimer, 0.001f);
        assertTrue(has(drain(world), GameWorld.EventType.FRENZY_START));
    }

    @Test
    public void frenzyEventReportsTheStreakThatLitIt() {
        chainKills(world, GameWorld.FRENZY_KILLS);
        int code = -1;
        for (GameWorld.GameEvent e : drain(world)) {
            if (e.type == GameWorld.EventType.FRENZY_START) code = e.code;
        }
        assertEquals(GameWorld.FRENZY_KILLS, code);
    }

    @Test
    public void frenzyBurnsOutAfterSixSeconds() {
        chainKills(world, GameWorld.FRENZY_KILLS);
        drain(world);
        step(world, (int) (GameWorld.FRENZY_TIME * 60f) - 5);
        assertTrue("还没到点", world.frenzyActive());
        step(world, 10);

        assertFalse("6 秒后自己熄灭", world.frenzyActive());
        assertEquals(0f, world.frenzyTimer, 0.001f);
        assertTrue(has(drain(world), GameWorld.EventType.FRENZY_END));
    }

    @Test
    public void frenzyOnlyIgnitesOncePerStreak() {
        chainKills(world, GameWorld.FRENZY_KILLS);
        drain(world);
        // 狂热期继续杀到 30 连：期间不该重复点火
        chainKills(world, 18);
        assertFalse("狂热进行中不重复点火",
                has(drain(world), GameWorld.EventType.FRENZY_START));
        assertTrue(world.frenzyActive());
    }

    @Test
    public void nextIgnitionNeedsAFreshTwelveAfterComboBreak() {
        chainKills(world, GameWorld.FRENZY_KILLS);
        assertEquals(GameWorld.FRENZY_TIME, world.frenzyTimer, 0.001f);
        // 停手 6.7 秒：狂热烧完、连杀窗口也断掉，下一轮要重新攒 12 连
        step(world, 400);
        assertEquals(0, world.comboKills);
        chainKills(world, GameWorld.FRENZY_KILLS - 1);
        assertEquals("重新攒到 11 连还不点火", 0f, world.frenzyTimer, 0.001f);
        chainKills(world, 1);
        assertTrue("第 12 连重新点着", world.frenzyActive());
    }

    // ---- 加成 ----

    @Test
    public void frenzyFiresMoreVolleysThanNormal() {
        GameWorld normal = newGame();
        GameWorld hot = newGame();
        hot.frenzyTimer = GameWorld.FRENZY_TIME;

        step(normal, 180);
        step(hot, 180);

        assertTrue("狂热期射速必须更快：normal=" + normal.bulletsFired
                        + " frenzy=" + hot.bulletsFired,
                hot.bulletsFired > normal.bulletsFired * 1.2f);
    }

    @Test
    public void frenzyBoostsKillScoreByQuarter() {
        GameWorld normal = newGame();
        GameWorld hot = newGame();
        hot.frenzyTimer = GameWorld.FRENZY_TIME;
        killDrone(normal);
        killDrone(hot);

        // 无人机 10 分，普通连杀倍率 1；狂热再乘 1.25 → 13
        assertEquals(10, normal.score);
        assertEquals(13, hot.score);
    }

    @Test
    public void frenzyStacksWithDoubleScore() {
        world.x2Timer = 8f;
        world.frenzyTimer = GameWorld.FRENZY_TIME;
        chainKills(world, 1);
        // 10 × 2（双倍分数）× 1.25（狂热） = 25
        assertEquals(25, world.score);
    }

    @Test
    public void frenzyDoesNotChangeComboMultiplier() {
        world.comboKills = 10;
        world.frenzyTimer = GameWorld.FRENZY_TIME;
        assertEquals("狂热不改连杀倍率本身", 3, world.comboMultiplier());
        chainKills(world, 1);
        assertEquals("连杀继续累计", 11, world.comboKills);
    }

    // ---- 熄火与清理 ----

    @Test
    public void takingAHitExtinguishesFrenzy() {
        chainKills(world, GameWorld.FRENZY_KILLS);
        assertTrue(world.frenzyActive());
        drain(world);

        hitShip(world);

        assertFalse("挨一下狂热当场熄火", world.frenzyActive());
        assertTrue(has(drain(world), GameWorld.EventType.FRENZY_END));
        assertEquals(0, world.comboKills);
    }

    @Test
    public void shieldBlockKeepsTheStreakAndFrenzy() {
        world.ship.addShield();
        chainKills(world, GameWorld.FRENZY_KILLS);
        hitShip(world);

        assertTrue("护盾挡下的那一下不算断连", world.frenzyActive());
        assertEquals(GameWorld.FRENZY_KILLS, world.comboKills);
    }

    @Test
    public void restartClearsFrenzyState() {
        chainKills(world, GameWorld.FRENZY_KILLS);
        assertTrue(world.frenzyActive());

        world.restart(GameMode.WAVES);

        assertFalse(world.frenzyActive());
        assertEquals(0f, world.frenzyTimer, 0.001f);
        chainKills(world, GameWorld.FRENZY_KILLS - 1);
        assertFalse("重开后从头攒", world.frenzyActive());
    }

    @Test
    public void frenzyCanReigniteDuringOneRun() {
        GameWorld w = newGame();
        chainKills(w, GameWorld.FRENZY_KILLS);
        assertTrue(w.frenzyActive());
        // 狂热结束后（连杀窗口也断了）再攒 12 连，应当再点一次
        w.frenzyTimer = 0f;
        step(w, 200);
        drain(w);
        chainKills(w, GameWorld.FRENZY_KILLS);
        assertTrue(w.frenzyActive());
        assertTrue(has(drain(w), GameWorld.EventType.FRENZY_START));
    }
}
