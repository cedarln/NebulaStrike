package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.57 迭代 47：禁卫 WARDEN 与三片公转护环。
 *
 * <p>盾卫把"正面"做成一块挡板，禁卫把"护盾"拆成三片绕着舰体公转的实体：
 * 直射弹要先啃掉挡在路上的那一片，三片同时碎掉才能摸到暴露的核心（此时受击双倍），
 * 但碎掉的护环 3.2 秒后会自己重铸。它不逼玩家换武器，只逼玩家读节奏；
 * 贯穿火力（激光 / 长枪）照旧无视护环，保持"穿透流克制重甲"的老规矩。
 */
public class WardenV157Test {

    private GameWorld world;
    private final List<GameWorld.GameEvent> events = new ArrayList<>();

    @Before
    public void setUp() {
        world = new GameWorld(20261028L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.bountyEnabled = false;
        world.perksEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.lives = 999_999;
        world.ship.x = 450f;
        world.ship.prevX = 450f;
        world.ship.y = 1460f;
        world.ship.prevY = 1460f;
        world.ship.setTarget(450f, 1460f);
        events.clear();
    }

    private Enemy spawnWarden(float x) {
        for (Enemy e : world.enemies) {
            if (e.active) continue;
            e.init(EnemyType.WARDEN, x, 120f, 1f);
            // 自动火力不该替这些用例做决定
            e.hp = e.maxHp = 9999f;
            return e;
        }
        throw new IllegalStateException("敌机池已满");
    }

    private Bullet freeBullet() {
        for (Bullet b : world.bullets) if (!b.active) return b;
        throw new IllegalStateException("玩家弹池已满");
    }

    private Bullet bulletAt(float x, float y, int damage) {
        Bullet b = freeBullet();
        b.init(x, y, 0f, 0f, damage, false);
        return b;
    }

    private float plateX(Enemy e, int i) {
        return e.x + (float) Math.cos(e.aegisPlateAngle(i)) * Enemy.AEGIS_RADIUS;
    }

    private float plateY(Enemy e, int i) {
        return e.y + (float) Math.sin(e.aegisPlateAngle(i)) * Enemy.AEGIS_RADIUS;
    }

    private void step(int frames) {
        for (int i = 0; i < frames; i++) world.update(1f / 60f);
    }

    private int countEvents(GameWorld.EventType type) {
        int n = 0;
        for (GameWorld.GameEvent e : events) if (e.type == type) n++;
        return n;
    }

    private int lastCode(GameWorld.EventType type) {
        int code = Integer.MIN_VALUE;
        for (GameWorld.GameEvent e : events) if (e.type == type) code = e.code;
        return code;
    }

    // ---- 护环 ----

    @Test
    public void wardenHoldsThreePlatesWhileHovering() {
        Enemy e = spawnWarden(800f);
        assertEquals(3, e.aegisPlatesAlive());
        step(240);   // 4 秒：压到悬停位
        assertEquals("悬停在设计高度", Enemy.WARDEN_HOVER_Y, e.y, 2f);
        assertEquals("三片护环都还在", 3, e.aegisPlatesAlive());
        assertFalse("没有破防", e.isAegisExposed());
        assertTrue("护环在公转", e.aegisAngle > 0f);
    }

    @Test
    public void aDirectShotChipsThePlateInItsWay() {
        Enemy e = spawnWarden(800f);
        step(2);
        Bullet b = bulletAt(plateX(e, 0), plateY(e, 0), 1);
        int hpBefore = (int) e.aegisHp[0];
        world.update(1f / 60f);
        events.addAll(world.pollEvents());
        assertFalse("护环吃掉了这一发", b.active);
        assertEquals("护环耐久 -1", hpBefore - 1, (int) e.aegisHp[0]);
        assertEquals("护环挡弹有独立音效事件", 1,
                countEvents(GameWorld.EventType.AEGIS_HIT));
        assertEquals("事件带上剩余护环数", 3, lastCode(GameWorld.EventType.AEGIS_HIT));
    }

    @Test
    public void piercingShotsPassStraightThroughTheAegis() {
        Enemy e = spawnWarden(800f);
        step(2);
        Bullet b = bulletAt(plateX(e, 1), plateY(e, 1), 2);
        b.piercing = true;
        assertEquals("贯穿火力不啃护环", -1, world.aegisPlateHit(e, b));
        assertEquals(3, e.aegisPlatesAlive());
    }

    @Test
    public void threeBrokenPlatesExposeTheCoreAndAnnounceIt() {
        Enemy e = spawnWarden(800f);
        step(2);
        for (int i = 0; i < 3; i++) {
            e.chipAegis(0);
            e.chipAegis(1);
            e.chipAegis(2);
        }
        assertEquals("三片全碎", 0, e.aegisPlatesAlive());
        assertTrue("核心暴露", e.isAegisExposed());
        assertEquals("暴露时长 3.5 秒", Enemy.AEGIS_OPEN, e.aegisOpenTimer, 0.001f);

        world.update(1f / 60f);
        events.addAll(world.pollEvents());
        assertEquals("破防事件只播一次", 1, countEvents(GameWorld.EventType.AEGIS_BREAK));
        assertEquals("事件带上暴露时长", (int) (Enemy.AEGIS_OPEN * 100f),
                lastCode(GameWorld.EventType.AEGIS_BREAK));
    }

    @Test
    public void exposedCoreTakesDoubleDamage() {
        Enemy e = spawnWarden(800f);
        e.hp = e.maxHp = 500f;
        // 飞船在 450，禁卫在 800：自动火力打不到它，伤害只来自本用例插入的子弹
        step(2);
        bulletAt(e.x, e.y, 1);
        world.update(1f / 60f);
        assertEquals("普通状态吃 1 点", 499f, e.hp, 0.001f);

        e.aegisOpenTimer = Enemy.AEGIS_OPEN;
        bulletAt(e.x, e.y, 1);
        world.update(1f / 60f);
        assertEquals("暴露的核心吃双倍", 497f, e.hp, 0.001f);
    }

    @Test
    public void shatteredPlatesRebuildOnTheirOwn() {
        Enemy e = spawnWarden(800f);
        step(2);
        for (int i = 0; i < 3; i++) e.chipAegis(0);
        assertEquals("第一片已碎", 0f, e.aegisHp[0], 0.001f);
        step((int) Math.ceil(Enemy.AEGIS_RESPAWN * 60f) + 4);
        assertEquals("重铸后耐久回满", Enemy.AEGIS_PLATE_HP, e.aegisHp[0], 0.001f);
    }

    @Test
    public void exposureWindowClosesOnItsOwn() {
        Enemy e = spawnWarden(800f);
        step(2);
        for (int i = 0; i < 3; i++) {
            e.chipAegis(0);
            e.chipAegis(1);
            e.chipAegis(2);
        }
        assertTrue(e.isAegisExposed());
        step((int) Math.ceil(Enemy.AEGIS_OPEN * 60f) + 4);
        assertFalse("窗口结束后核心重新被护环保护", e.isAegisExposed());
    }

    // ---- 攻击与出场 ----

    @Test
    public void wardenFiresSlowAimedPairsOnlyWhileOnStation() {
        Enemy e = spawnWarden(450f);
        e.hp = e.maxHp = 9999f;
        assertEquals(0, activeEnemyBullets());
        int guard = 0;
        while (activeEnemyBullets() < 2 && guard++ < 600) world.update(1f / 60f);
        assertEquals("一轮只放两发", 2, activeEnemyBullets());
        for (EnemyBullet b : world.enemyBullets) {
            if (!b.active) continue;
            assertEquals("弹速慢，压力留给护环节奏", 300f,
                    (float) Math.hypot(b.vx, b.vy), 40f);
        }
    }

    private int activeEnemyBullets() {
        int n = 0;
        for (EnemyBullet b : world.enemyBullets) if (b.active) n++;
        return n;
    }

    @Test
    public void wardenEntersTheGuaranteedEncounterRosterAt215Seconds() {
        int idx = -1;
        for (int i = 0; i < GameWorld.V117_TYPES.length; i++) {
            if (GameWorld.V117_TYPES[i] == EnemyType.WARDEN) idx = i;
        }
        assertTrue("禁卫应当在保底遭遇名单里", idx >= 0);
        assertEquals("解锁窗口与常规刷新一致", 215f, GameWorld.V117_WINDOWS[idx], 0.01f);
        assertTrue("中后期舰型", EnemyType.WARDEN.isAdvanced());
        assertEquals("重甲", 10, EnemyType.WARDEN.hp);
    }

    @Test
    public void platesDropWithTheHull() {
        Enemy e = spawnWarden(800f);
        step(2);
        e.active = false;   // 击毁
        step(2);
        assertEquals("舰体没了，护环也不该继续存在", 0, activeWardens());
    }

    private int activeWardens() {
        int n = 0;
        for (Enemy e : world.enemies) {
            if (e.active && e.type == EnemyType.WARDEN) n++;
        }
        return n;
    }
}
