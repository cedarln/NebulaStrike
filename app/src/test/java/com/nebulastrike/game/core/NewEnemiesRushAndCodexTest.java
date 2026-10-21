package com.nebulastrike.game.core;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * v1.9 新兵种行为、突袭奖励波、得分构成与图鉴持久化的回归测试。
 * 与 {@link PhaseAndFusionTest} 分开：前者测"玩家侧新系统"，这里测"敌机与统计侧"。
 */
public class NewEnemiesRushAndCodexTest {
    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(777L);
        world.restart(GameMode.WAVES);
        world.spawningEnabled = false;
        world.settings.godMode = true;   // 长时间模拟里避免被弹幕打死，干扰断言
    }

    /** 只用于持久化断言的内存版存档，额外复制图鉴字段。 */
    static class CodexStore implements SettingsStore {
        final Settings saved = new Settings();
        int settingsWrites;

        @Override public void load(Settings into) {
            into.codexSeenEnemies = saved.codexSeenEnemies;
            into.codexMaxTierSlain = saved.codexMaxTierSlain;
            System.arraycopy(saved.codexBossKills, 0, into.codexBossKills, 0,
                    Math.min(saved.codexBossKills.length, into.codexBossKills.length));
        }
        @Override public int bestScore() { return 0; }
        @Override public void saveBestScore(int score) { }
        @Override public int[] leaderboard(GameMode mode) { return new int[5]; }
        @Override public void saveLeaderboard(GameMode mode, int[] scores) { }
        @Override public void save(Settings s) {
            saved.codexSeenEnemies = s.codexSeenEnemies;
            saved.codexMaxTierSlain = s.codexMaxTierSlain;
            System.arraycopy(s.codexBossKills, 0, saved.codexBossKills, 0,
                    Math.min(s.codexBossKills.length, saved.codexBossKills.length));
            settingsWrites++;
        }
    }

    private Enemy freeEnemy() {
        for (Enemy e : world.enemies) if (!e.active) return e;
        throw new AssertionError("no free enemy slot");
    }

    private void step(int frames) {
        for (int i = 0; i < frames; i++) world.update(1f / 60f);
    }

    /** 推进模拟并屏蔽玩家火力，用于长时间 Boss 战测试。 */
    private boolean stepUntilSeeingEvent(GameWorld.EventType type, int maxFrames) {
        for (int i = 0; i < maxFrames; i++) {
            world.update(1f / 60f);
            for (Bullet b : world.bullets) b.active = false;
            List<GameWorld.GameEvent> events = world.pollEvents();
            for (GameWorld.GameEvent e : events) if (e.type == type) return true;
        }
        return false;
    }

    private boolean hasEvent(GameWorld.EventType type) {
        List<GameWorld.GameEvent> events = world.pollEvents();
        for (GameWorld.GameEvent e : events) if (e.type == type) return true;
        return false;
    }

    private int activeEnemies(EnemyType type) {
        int n = 0;
        for (Enemy e : world.enemies) if (e.active && e.type == type) n++;
        return n;
    }

    private int activeEnemyBullets() {
        int n = 0;
        for (EnemyBullet b : world.enemyBullets) if (b.active) n++;
        return n;
    }

    private PowerUp firstActivePowerUp(PowerUp.Kind kind) {
        for (PowerUp p : world.powerups) if (p.active && p.kind == kind) return p;
        return null;
    }

    // ---- 自杀机 ----

    @Test
    public void suiciderLocksOnShipAndAccelerates() {
        Enemy e = freeEnemy();
        e.init(EnemyType.SUICIDER, 140f, 520f, 1f);
        float shipX = world.ship.x, shipY = world.ship.y;
        float startDist = (float) Math.hypot(shipX - e.x, shipY - e.y);
        float startY = e.y, startX = e.x;

        step(20);
        assertTrue("朝飞船下压", e.active);
        assertTrue(e.y > startY + 60f);
        assertTrue("朝飞船横向靠拢", e.x > startX);
        assertTrue("越追越快", e.diveSpeed > EnemyType.SUICIDER.baseSpeed);
        assertTrue("加速度有上限",
                e.diveSpeed <= EnemyType.SUICIDER.baseSpeed * 2.6f + 1f);
        float dist = (float) Math.hypot(shipX - e.x, shipY - e.y);
        assertTrue("持续缩短与飞船的距离", dist < startDist);
    }

    @Test
    public void suiciderBurstScattersFragmentRing() {
        Enemy e = freeEnemy();
        e.init(EnemyType.SUICIDER, world.ship.x + 300f, 500f, 1f);
        int before = activeEnemyBullets();
        world.killEnemyForTest(e);
        int fragments = 0;
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active && b.radius == 8f) fragments++;
        }
        assertEquals("解体散出 5 枚碎片", before + 5, activeEnemyBullets());
        assertEquals("碎片是 8 半径的小弹", 5, fragments);
        assertTrue("击杀瞬间屏幕震动", world.shakeTime > 0f);
    }

    // ---- 分裂舱 ----

    @Test
    public void splitterSplitsIntoTwoDronesOnlyOnce() {
        Enemy e = freeEnemy();
        e.init(EnemyType.SPLITTER, 160f, 500f, 1f);
        assertEquals("重甲舱 5 HP", EnemyType.SPLITTER.hp, e.maxHp, 0.001f);

        world.killEnemyForTest(e);
        assertEquals("裂成两架无人机", 2, activeEnemies(EnemyType.DRONE));
        int children = 0;
        for (Enemy c : world.enemies) {
            if (c.active && c.type == EnemyType.DRONE) {
                children++;
                assertEquals("子机只有一层分裂", 1, c.splitDepth);
                assertEquals(1f, c.hp, 0.001f);
            }
        }
        assertEquals(2, children);

        Enemy child = null;
        for (Enemy c : world.enemies) if (c.active && c.splitDepth == 1) child = c;
        assertNotNull(child);
        world.killEnemyForTest(child);
        assertEquals("子机不再继续分裂，只剩姊妹机", 1, activeEnemies(EnemyType.DRONE));
        Enemy sibling = null;
        for (Enemy c : world.enemies) if (c.active && c.type == EnemyType.DRONE) sibling = c;
        assertNotNull(sibling);
        world.killEnemyForTest(sibling);
        assertEquals(0, activeEnemies(EnemyType.DRONE));
    }

    @Test
    public void heavyHullKillAddsHitStop() {
        Enemy splitter = freeEnemy();
        splitter.init(EnemyType.SPLITTER, 200f, 400f, 1f);
        world.killEnemyForTest(splitter);
        assertTrue("重甲击杀触发顿帧", world.hitStopTimer > 0f);
        assertEquals(0.045f, world.hitStopTimer, 0.001f);

        world.hitStopTimer = 0f;
        Enemy drone = freeEnemy();
        drone.init(EnemyType.DRONE, 260f, 400f, 1f);
        world.killEnemyForTest(drone);
        assertEquals("小兵击杀不顿帧", 0f, world.hitStopTimer, 0.001f);
    }

    // ---- 医疗艇 ----

    @Test
    public void healerRepairsNearbyWoundedAlly() {
        Enemy healer = freeEnemy();
        healer.init(EnemyType.HEALER, 160f, 420f, 1f);
        Enemy tank = freeEnemy();
        tank.init(EnemyType.TANK, 160f, 520f, 1f);
        tank.hp = 1f;

        healer.healCd = 0f;
        world.update(1f / 60f);

        assertEquals("友舰回 1 点血", 2f, tank.hp, 0.001f);
        assertEquals("治疗冷却重置", 1.5f, healer.healCd, 0.001f);
        assertEquals("脉冲余量供渲染层使用", 1f, healer.healFx, 0.001f);
        assertTrue(hasEvent(GameWorld.EventType.HEAL_PULSE));
        boolean healArc = false;
        for (int i = 0; i < GameWorld.MAX_ARCS; i++) {
            if (world.arcAge[i] > 0f && world.arcKind[i] == 1) healArc = true;
        }
        assertTrue("治疗链路画成绿色电弧", healArc);

        step(80);
        assertTrue("脉冲显示会淡出", healer.healFx < 0.4f);
    }

    @Test
    public void healerIgnoresFullOrDistantTargets() {
        Enemy healer = freeEnemy();
        healer.init(EnemyType.HEALER, 160f, 420f, 1f);
        Enemy far = freeEnemy();
        far.init(EnemyType.TANK, 700f, 1050f, 1f);
        far.hp = 1f;
        Enemy full = freeEnemy();
        full.init(EnemyType.TANK, 160f, 480f, 1f);

        healer.healCd = 0f;
        world.update(1f / 60f);
        assertEquals("超出半径不回血", 1f, far.hp, 0.001f);
        assertEquals("满血不回血", EnemyType.TANK.hp, full.hp, 0.001f);
        assertFalse("无人受益时不发脉冲", hasEvent(GameWorld.EventType.HEAL_PULSE));
        assertEquals(0f, healer.healFx, 0.001f);

        far.hp = 1f;
        healer.healCd = 0f;
        step(2);
        healer.healCd = 0f;
        world.update(1f / 60f);
        assertTrue("回血不超过满血", far.hp <= far.maxHp);
    }

    @Test
    public void healerCappedAtMaxHp() {
        Enemy healer = freeEnemy();
        healer.init(EnemyType.HEALER, 160f, 420f, 1f);
        Enemy tank = freeEnemy();
        tank.init(EnemyType.TANK, 160f, 460f, 1f);
        tank.hp = EnemyType.TANK.hp - 0.5f;
        for (int pulse = 0; pulse < 6; pulse++) {
            healer.healCd = 0f;
            world.update(1f / 60f);
        }
        assertEquals("反复治疗也不会超血", EnemyType.TANK.hp, tank.hp, 0.001f);
    }

    // ---- 狙击塔 ----

    @Test
    public void sniperDescendsThenHovers() {
        Enemy e = freeEnemy();
        e.init(EnemyType.SNIPER, 700f, -40f, 1f);
        step(150);
        assertTrue("悬停在狙击高度", e.active);
        assertEquals(Enemy.SNIPER_HOVER_Y, e.y, 6f);
        assertTrue("留在屏幕内", e.x > EnemyType.SNIPER.radius
                && e.x < GameWorld.WIDTH - EnemyType.SNIPER.radius);
        assertTrue("还没冲到飞船脸上", e.y < world.ship.y - 600f);
    }

    @Test
    public void sniperTelegraphsThenFiresFastAimedBullet() {
        Enemy e = freeEnemy();
        e.init(EnemyType.SNIPER, 660f, Enemy.SNIPER_HOVER_Y, 1f);
        e.fireCd = 0.6f;
        assertTrue("预警窗口内可见充能", e.isCharging());

        step(40);
        EnemyBullet shot = null;
        for (EnemyBullet b : world.enemyBullets) {
            if (b.active && b.radius == 9f) shot = b;
        }
        assertNotNull("狙击弹是 9 半径的高速弹", shot);
        assertTrue("朝飞船方向射", shot.vy > 0f);
        assertEquals(Math.signum(world.ship.x - e.x), Math.signum(shot.vx), 0.001f);
        float speed = (float) Math.hypot(shot.vx, shot.vy);
        assertTrue("比普通敌弹快得多", speed > 500f && speed < 720f);
        assertFalse("开火后回到长冷却", e.isCharging());
    }

    @Test
    public void sniperDivesAwayWhenHoverEnds() {
        Enemy e = freeEnemy();
        e.init(EnemyType.SNIPER, 500f, Enemy.SNIPER_HOVER_Y, 1f);
        e.hoverCd = 0.02f;
        step(60);
        assertTrue("悬停时间到就离场俯冲", e.y > Enemy.SNIPER_HOVER_Y + 60f);
    }

    // ---- 突袭奖励波 ----

    @Test
    public void rushWaveSpawnsEscortsAndBonusPickup() {
        world.spawningEnabled = true;
        world.rushTimer = 0.001f;
        world.update(1f / 60f);

        assertEquals(1, world.rushesTriggered);
        assertTrue("横幅提示", world.rushActive && world.rushBanner > 0f);
        assertEquals("6 架护航敌机", 6, activeEnemies(EnemyType.WEAVER));
        assertNotNull("中央掉落金色奖励", firstActivePowerUp(PowerUp.Kind.BONUS));
        assertTrue(hasEvent(GameWorld.EventType.RUSH));
        assertEquals("倒计时重置为 32 秒", 32f, world.rushTimer, 0.001f);
        assertTrue("登场即解锁图鉴", world.settings.enemySeen(EnemyType.WEAVER));
        assertTrue(world.codexDirty);
    }

    @Test
    public void rushWaveUsesHarderEscortsLateInRun() {
        world.spawningEnabled = true;
        world.timeAlive = 90f;
        world.rushTimer = 0.001f;
        step(1);
        assertTrue("后期护航出现自杀机或仍是编队机",
                activeEnemies(EnemyType.SUICIDER) == 6 || activeEnemies(EnemyType.WEAVER) == 6);
        assertNotNull(firstActivePowerUp(PowerUp.Kind.BONUS));
    }

    @Test
    public void rushWaveSkippedWhenSpawningDisabledOrBossActive() {
        world.spawningEnabled = false;
        world.rushTimer = 0.001f;
        step(300);
        assertEquals("LAB/测试关闭刷怪时不产生突袭", 0, world.rushesTriggered);

        world.spawningEnabled = true;
        world.bossActive = true;
        world.rushTimer = 0.001f;
        step(120);
        assertEquals("Boss 战期间不打断节奏", 0, world.rushesTriggered);
        assertNull(firstActivePowerUp(PowerUp.Kind.BONUS));
    }

    @Test
    public void rushBonusClearsBulletsAndPaysOut() {
        for (int i = 0; i < 4; i++) {
            EnemyBullet b = null;
            for (EnemyBullet c : world.enemyBullets) {
                if (!c.active) { b = c; break; }
            }
            assertNotNull(b);
            b.initRadius(120f + i * 40f, 700f, 0f, 200f, 10f);
        }
        int before = world.score;
        world.spawnPowerUp(PowerUp.Kind.BONUS, world.ship.x, world.ship.y);
        world.wave = 3;
        world.update(1f / 60f);

        assertTrue("奖励分入账", world.score > before);
        assertTrue("双倍分数生效", world.x2Timer > 0f);
        assertEquals("清空敌弹", 0, activeEnemyBullets());
        assertTrue("金色奖励只由突袭波投放，捡到时也计入道具分",
                world.scoreBySource[GameWorld.SCORE_PICKUP] >= 400);
        assertTrue(world.ship.hasSpecialWeapon());
        assertTrue(hasEvent(GameWorld.EventType.POWERUP_COLLECTED));
    }

    // ---- 得分构成 ----

    @Test
    public void scoreBySourceTracksKillPoints() {
        Enemy drone = freeEnemy();
        drone.init(EnemyType.DRONE, 200f, 400f, 1f);
        world.killEnemyForTest(drone);

        assertEquals(EnemyType.DRONE.score, world.score);
        assertEquals(EnemyType.DRONE.score,
                world.scoreBySource[GameWorld.SCORE_ENEMY]);
        for (int i = 1; i < GameWorld.SCORE_SOURCES; i++) {
            assertEquals(0, world.scoreBySource[i]);
        }
    }

    @Test
    public void scoreBySourceIncludesDoublingAndSumsToScore() {
        world.x2Timer = 5f;
        Enemy a = freeEnemy();
        a.init(EnemyType.DRONE, 200f, 400f, 1f);
        Enemy b = freeEnemy();
        b.init(EnemyType.WEAVER, 260f, 400f, 1f);
        world.killEnemyForTest(a);
        world.killEnemyForTest(b);

        int sum = 0;
        for (int i = 0; i < GameWorld.SCORE_SOURCES; i++) sum += world.scoreBySource[i];
        assertEquals("(10+15)*2 双倍", (EnemyType.DRONE.score + EnemyType.WEAVER.score) * 2, world.score);
        assertEquals("构成图总和等于总分", world.score, sum);
    }

    @Test
    public void scoreBreakdownResetsOnRestart() {
        Enemy drone = freeEnemy();
        drone.init(EnemyType.DRONE, 200f, 400f, 1f);
        world.killEnemyForTest(drone);
        assertTrue(world.scoreBySource[GameWorld.SCORE_ENEMY] > 0);

        world.restart(GameMode.WAVES);
        for (int i = 0; i < GameWorld.SCORE_SOURCES; i++) {
            assertEquals(0, world.scoreBySource[i]);
        }
        assertEquals(0, world.score);
        assertEquals(0, world.rushesTriggered);
    }

    // ---- Boss 新部件与招式 ----

    private void spawnBoss(int index) {
        world.boss.active = false;
        world.bossActive = false;
        world.bossesDefeated = index;
        world.nextBossWave = 4;
        world.wave = 4;
        world.update(1f / 60f);
        assertTrue("按舰级索引生成 Boss", world.boss.active);
        assertEquals(index > 3 ? 3 : index, world.boss.tier);
        world.boss.y = world.boss.arenaY;
        world.boss.hp = world.boss.maxHp * 0.5f; // 进入二阶段
        world.boss.staggerTimer = 0f;
    }

    @Test
    public void shieldGeneratorHalvesCoreDamage() {
        spawnBoss(2);
        assertTrue("tier2 起带护盾发生器", world.boss.shielded());
        float scale = world.boss.damageScale();
        assertTrue(scale > 0f && scale < 1f);
        float hpBefore = world.boss.hp;
        world.boss.damage(10);
        assertEquals(hpBefore - 10f * scale, world.boss.hp, 0.001f);
    }

    @Test
    public void bossPodLaunchesSuicideMinions() {
        spawnBoss(1);
        BossPart pod = null;
        for (int i = 0; i < world.boss.partCount; i++) {
            if (world.boss.parts[i].kind == BossPart.Kind.POD) pod = world.boss.parts[i];
        }
        assertNotNull("tier1 起配备分裂舱", pod);
        pod.fireCd = 0.001f;
        world.update(1f / 60f);

        assertEquals("分裂舱投放自杀机僚机", 2, activeEnemies(EnemyType.SUICIDER));
        assertTrue(pod.fireCd > 4f);
        assertTrue(world.settings.enemySeen(EnemyType.SUICIDER));
    }

    @Test
    public void bossRamSweepsTheArenaAndLeaksBullets() {
        spawnBoss(1);
        world.update(1f / 60f);
        assertEquals("血量降到二阶段", 2, world.boss.phase);

        float startX = world.boss.x;
        world.boss.startRam();
        assertTrue(world.boss.isRamming());
        step(20);
        assertTrue("横冲把 Boss 拉到另一侧",
                Math.abs(world.boss.x - startX) > 100f);
        int trail = 0;
        for (EnemyBullet b : world.enemyBullets) if (b.active && b.radius == 12f) trail++;
        assertTrue("横冲沿途洒下弹丸", trail > 0);

        step(45);   // > 横冲时长
        assertFalse("冲刺时长到就停下", world.boss.isRamming());
    }

    @Test
    public void onlySecondBossOnwardsUsesRamAttack() {
        spawnBoss(0);
        assertFalse("首舰不具备横冲条件", world.boss.readyToRam());
        assertFalse("首舰整局都不会横冲",
                stepUntilSeeingEvent(GameWorld.EventType.BOSS_RAM, 700));

        spawnBoss(1);
        assertTrue("二舰起可以横冲", stepUntilSeeingEvent(GameWorld.EventType.BOSS_RAM, 700));
        assertTrue(world.boss.isRamming());
    }

    // ---- 图鉴持久化 ----

    @Test
    public void codexProgressPersistsAndOnlyWritesWhenDirty() {
        CodexStore store = new CodexStore();
        world.attachStore(store);
        assertEquals(0, store.settingsWrites);

        world.spawningEnabled = true;
        world.rushTimer = 0.001f;
        world.update(1f / 60f);
        assertTrue(world.codexDirty);
        int completion = world.settings.codexCompletion();
        assertTrue(completion > 0);

        world.flushCodex();
        assertEquals(1, store.settingsWrites);
        assertFalse(world.codexDirty);
        assertTrue(store.saved.enemySeen(EnemyType.WEAVER));
        assertEquals(completion, store.saved.codexCompletion());

        world.flushCodex();
        assertEquals("无变化不再写盘", 1, store.settingsWrites);
    }

    @Test
    public void bossKillUnlocksCodexTierAndFlushesOnGameOver() {
        CodexStore store = new CodexStore();
        world.attachStore(store);
        spawnBoss(0);
        assertFalse(store.saved.bossUnlocked(0));

        world.boss.hp = 0f;
        world.update(1f / 60f);
        assertEquals(1, world.settings.codexBossKills[0]);
        assertEquals(0, world.settings.codexMaxTierSlain);
        assertTrue(world.scoreBySource[GameWorld.SCORE_BOSS] > 0);
        assertTrue(world.codexDirty);

        world.lives = 1;
        world.settings.godMode = false;
        Enemy crash = freeEnemy();
        crash.init(EnemyType.TANK, world.ship.x, world.ship.y + 30f, 0.4f);
        step(4);
        assertEquals(GameWorld.State.GAME_OVER, world.state);
        assertTrue("结算时统一落盘", store.settingsWrites > 0);
        assertEquals(1, store.saved.codexBossKills[0]);
    }

    @Test
    public void codexRowsMatchAvailableContent() {
        assertEquals("敌机 + 舰级", EnemyType.values().length + 4, world.settings.codexTotal());
        for (EnemyType t : EnemyType.values()) {
            assertFalse(world.settings.enemySeen(t));
        }
        assertEquals(0, world.settings.codexCompletion());
    }
}
