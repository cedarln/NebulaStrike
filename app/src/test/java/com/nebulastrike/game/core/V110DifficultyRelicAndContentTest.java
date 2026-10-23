package com.nebulastrike.game.core;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * v1.10 回归测试：难度档位、重生保护、Boss 圣物，以及新星/炮台/弹跳弹/盾卫/布雷艇/母舰
 * 这几套新机制。每个用例都从 freshWorld() 起一手干净的战场，关闭随机刷怪只留手工目标。
 */
public class V110DifficultyRelicAndContentTest {

    private GameWorld freshWorld() {
        GameWorld w = new GameWorld(4242L);
        w.spawningEnabled = false;
        w.settings.setDifficulty(Difficulty.STANDARD);
        return w;
    }

    // ---- 难度档位 ----

    @Test
    public void standardPresetIsNumericallyIdenticalToLegacy() {
        assertEquals(1.0f, Difficulty.STANDARD.scoreScale, 0f);
        assertEquals(1.0f, Difficulty.STANDARD.dropScale, 0f);
        assertEquals(1.0f, Difficulty.STANDARD.speedScale, 0f);
        assertEquals(1.0f, Difficulty.STANDARD.eliteScale, 0f);
        assertEquals("标准档重生保护 = 历史常量", Ship.RESPAWN_INVULN,
                Difficulty.STANDARD.respawnInvuln, 0.001f);
        assertEquals(Difficulty.Penalty.KEEP_WEAPONS, Difficulty.STANDARD.penalty);
    }

    @Test
    public void presetsOrderProtectionDropsAndPunishmentMonotonically() {
        assertTrue(Difficulty.CASUAL.respawnInvuln > Difficulty.STANDARD.respawnInvuln);
        assertTrue(Difficulty.STANDARD.respawnInvuln > Difficulty.HARDCORE.respawnInvuln);
        assertTrue(Difficulty.CASUAL.respawnClearRadius > Difficulty.STANDARD.respawnClearRadius);
        assertTrue("休闲掉率最高、硬核最稀",
                Difficulty.CASUAL.dropScale > 1f && Difficulty.HARDCORE.dropScale < 1f);
        assertTrue("硬核敌机更快", Difficulty.HARDCORE.speedScale > 1f
                && Difficulty.CASUAL.speedScale < 1f);
        assertTrue("硬核精英最多", Difficulty.HARDCORE.eliteScale > Difficulty.STANDARD.eliteScale
                && Difficulty.STANDARD.eliteScale > Difficulty.CASUAL.eliteScale);
        assertEquals(Difficulty.Penalty.KEEP_ALL, Difficulty.CASUAL.penalty);
        assertEquals(Difficulty.Penalty.STRIP, Difficulty.HARDCORE.penalty);
    }

    @Test
    public void byOrdinalFallsBackToStandardForCorruptSaves() {
        assertEquals(Difficulty.HARDCORE, Difficulty.byOrdinal(Difficulty.HARDCORE.ordinal()));
        assertEquals(Difficulty.STANDARD, Difficulty.byOrdinal(99));
        assertEquals(Difficulty.STANDARD, Difficulty.byOrdinal(-1));
    }

    @Test
    public void scoreScaleAppliesToEveryAward() {
        GameWorld std = freshWorld();
        std.addScore(100, GameWorld.SCORE_PICKUP);
        assertEquals(100, std.score);

        GameWorld hard = freshWorld();
        hard.settings.setDifficulty(Difficulty.HARDCORE);
        hard.addScore(100, GameWorld.SCORE_PICKUP);
        assertEquals("硬核 1.5 倍", 150, hard.score);

        GameWorld casual = freshWorld();
        casual.settings.setDifficulty(Difficulty.CASUAL);
        casual.addScore(100, GameWorld.SCORE_PICKUP);
        assertEquals("休闲打 75 折", 75, casual.score);
    }

    @Test
    public void difficultySpeedScalesOnTopOfTheLabSlider() {
        GameWorld w = freshWorld();
        w.restart(GameMode.WAVES);
        w.update(1f / 60f);
        float standard = w.enemySpeedScale;
        w.settings.setDifficulty(Difficulty.HARDCORE);
        w.update(1f / 60f);
        assertEquals(standard * Difficulty.HARDCORE.speedScale, w.enemySpeedScale, 0.02f);
        w.settings.setDifficulty(Difficulty.STANDARD);
        w.settings.enemySpeedPct = 50;
        w.update(1f / 60f);
        assertEquals("LAB 滑杆与难度系数相乘", standard * 0.5f, w.enemySpeedScale, 0.02f);
    }

    @Test
    public void difficultySelectorIsTappableFromTheSettingsPanel() {
        GameWorld w = new GameWorld(4242L, true);
        w.settingsOpen = true;
        Ui.Action a = Ui.hit(GameWorld.State.MENU, true, false, false,
                Ui.DIFF_HARDCORE.cx(), Ui.DIFF_HARDCORE.cy());
        assertEquals(Ui.Action.SET_DIFF_HARDCORE, a);
        w.handleUiAction(a);
        assertEquals(Difficulty.HARDCORE, w.settings.difficulty);

        w.handleUiAction(Ui.hit(GameWorld.State.MENU, true, false, false,
                Ui.DIFF_CASUAL.cx(), Ui.DIFF_CASUAL.cy()));
        assertEquals(Difficulty.CASUAL, w.settings.difficulty);
        assertEquals(Ui.Action.SET_DIFF_STANDARD, Ui.diffAction(Difficulty.STANDARD));
        // 三格并排互不重叠，整行落在标题之下、音效开关之上，且都留在舞台内
        assertFalse(Ui.DIFF_CASUAL.contains(Ui.DIFF_STANDARD.cx(), Ui.DIFF_STANDARD.cy()));
        assertFalse(Ui.DIFF_HARDCORE.contains(Ui.DIFF_STANDARD.cx(), Ui.DIFF_STANDARD.cy()));
        assertFalse(Ui.DIFF_STANDARD.contains(Ui.DIFF_CASUAL.cx(), Ui.DIFF_CASUAL.cy()));
        assertTrue("选择器整行应在音效开关之上",
                Ui.DIFF_HARDCORE.y + Ui.DIFF_HARDCORE.h <= Ui.TOGGLE_SOUND.y);
        assertTrue("三段都在屏幕内", Ui.DIFF_CASUAL.x > 0f
                && Ui.DIFF_HARDCORE.x + Ui.DIFF_HARDCORE.w < GameWorld.WIDTH);
    }

    // ---- 重生保护 ----

    @Test
    public void respawnGrantsGraceAndSweepsBulletsAroundTheShip() {
        GameWorld w = freshWorld();
        EnemyBullet near = freeEnemyBullet(w);
        near.init(w.ship.x + 120f, w.ship.y - 40f, 0f, 120f);
        EnemyBullet far = freeEnemyBullet(w);
        far.init(w.ship.x + 280f, w.ship.y, 0f, 120f);
        EnemyBullet killer = freeEnemyBullet(w);

        killShip(w, killer);

        assertEquals(2, w.lives);
        assertEquals(Difficulty.STANDARD.respawnInvuln, w.respawnGrace, 0.001f);
        assertTrue(w.ship.invulnerable);
        assertFalse("清弹半径内的敌弹应被抹掉", near.active);
        assertTrue("半径外的敌弹保留", far.active);
        assertTrue(hasEvent(w, GameWorld.EventType.RESPAWN));

        clearBullets(w);
        w.update(1f / 60f);
        assertTrue("重生把武器冷却清零，保护期内立刻能开火", countActive(w) > 0);
    }

    @Test
    public void casualRespawnProtectionIsLongerAndWider() {
        GameWorld w = freshWorld();
        w.settings.setDifficulty(Difficulty.CASUAL);
        EnemyBullet far = freeEnemyBullet(w);
        far.init(w.ship.x + 280f, w.ship.y, 0f, 120f);
        EnemyBullet killer = freeEnemyBullet(w);

        killShip(w, killer);

        assertEquals(Difficulty.CASUAL.respawnInvuln, w.respawnGrace, 0.001f);
        assertFalse("休闲档 330 半径把 280 处的敌弹也清掉", far.active);
        assertEquals(2, w.lives);
    }

    @Test
    public void graceTimerTicksDownAndShieldBreakAlsoGivesProtection() {
        GameWorld w = freshWorld();
        w.settings.setDifficulty(Difficulty.HARDCORE);
        killShip(w, freeEnemyBullet(w));
        assertEquals(Difficulty.HARDCORE.respawnInvuln, w.respawnGrace, 0.001f);
        float before = w.respawnGrace;
        for (int i = 0; i < 30; i++) w.update(1f / 60f);
        assertTrue(w.respawnGrace < before);
        assertTrue("保护还没走完", w.respawnGrace > 0f);

        // 破盾同样给一段保护时间，避免"破盾即连坐"
        GameWorld s = freshWorld();
        s.ship.addShield();
        s.ship.invulnerable = false;
        EnemyBullet hit = freeEnemyBullet(s);
        hit.init(s.ship.x, s.ship.y, 0f, 0f);
        s.update(1f / 60f);
        assertEquals("护盾挡住这一发，不掉命", 3, s.lives);
        assertEquals(0, s.ship.shieldLayers);
        assertEquals(Difficulty.STANDARD.respawnInvuln, s.respawnGrace, 0.001f);
        assertTrue(hasEvent(s, GameWorld.EventType.SHIELD_BROKEN));
    }

    // ---- 坠机惩罚 ----

    @Test
    public void standardDeathDropsOnePowerLevelButKeepsWeapons() {
        GameWorld w = freshWorld();
        w.spawnPowerUp(PowerUp.Kind.SPREAD, w.ship.x, w.ship.y);
        w.update(1f / 60f);
        w.ship.powerLevel = 3;
        killShip(w, freeEnemyBullet(w));
        assertEquals(2, w.ship.powerLevel);
        assertTrue(w.ship.isWeaponEquipped(WeaponType.SPREAD));
    }

    @Test
    public void casualDeathKeepsLoadoutIntact() {
        GameWorld w = freshWorld();
        w.settings.setDifficulty(Difficulty.CASUAL);
        w.spawnPowerUp(PowerUp.Kind.SPREAD, w.ship.x, w.ship.y);
        w.update(1f / 60f);
        w.ship.powerLevel = 3;
        killShip(w, freeEnemyBullet(w));
        assertEquals("休闲档火力不降级", 3, w.ship.powerLevel);
        assertTrue(w.ship.isWeaponEquipped(WeaponType.SPREAD));
    }

    @Test
    public void hardcoreDeathStripsWeaponsRelicsAndTurrets() {
        GameWorld w = freshWorld();
        w.settings.setDifficulty(Difficulty.HARDCORE);
        w.spawnPowerUp(PowerUp.Kind.SPREAD, w.ship.x, w.ship.y);
        w.spawnPowerUp(PowerUp.Kind.LASER, w.ship.x, w.ship.y);
        w.spawnPowerUp(PowerUp.Kind.TURRET, w.ship.x, w.ship.y);
        w.update(1f / 60f);
        assertTrue(w.ship.isWeaponEquipped(WeaponType.SPREAD));
        assertTrue(w.ship.isWeaponEquipped(WeaponType.LANCE));
        assertNotNull(activeTurret(w));
        activeTurret(w).y = 300f;         // 挪开，别让它替飞船挡下这发必中弹
        w.ship.powerLevel = 3;
        w.wave = 4;
        killBoss(w);                        // 先拿一枚圣物
        assertTrue(w.relicMask != 0);

        killShip(w, freeEnemyBullet(w));

        assertEquals(1, w.ship.powerLevel);
        assertFalse("硬核坠机清空特殊武器", w.ship.isWeaponEquipped(WeaponType.SPREAD));
        assertFalse(w.ship.isWeaponEquipped(WeaponType.LASER));
        assertFalse(w.ship.isWeaponEquipped(WeaponType.LANCE));
        assertEquals("圣物全部失去", 0, w.relicMask);
        assertEquals("炮台全部拆除", 0, activeTurretCount(w));
        assertEquals(0f, w.ship.nanoTimer, 0.001f);
        assertTrue("基础炮永远保留", w.ship.isWeaponEquipped(WeaponType.VULCAN));
    }

    // ---- Boss 圣物 ----

    @Test
    public void bossKillGrantsExactlyOneNewRelic() {
        GameWorld w = freshWorld();
        w.wave = 4;
        killBoss(w);
        assertEquals(1, Integer.bitCount(w.relicMask));
        assertEquals(1, w.relicsGained);
        assertNotNull(w.lastRelic);
        assertTrue(w.relicBanner > 0f);
        assertFalse(w.relicsComplete());
        assertSame(Relic.byOrdinal(w.lastRelic.ordinal()), w.lastRelic);
        assertTrue(hasEvent(w, GameWorld.EventType.RELIC_GAINED));
    }

    @Test
    public void allRelicsCollectWithoutDuplicates() {
        GameWorld w = freshWorld();
        for (int i = 0; i < Relic.COUNT; i++) {
            w.wave = 4 + i * 4;
            killBoss(w);
            assertEquals("第 " + (i + 1) + " 次击败应拿到没拿过的那枚",
                    i + 1, Integer.bitCount(w.relicMask));
        }
        assertTrue(w.relicsComplete());
        assertEquals(Relic.COUNT, w.relicsGained);
        w.wave = 4 + Relic.COUNT * 4;
        killBoss(w);
        assertEquals("集齐后不再重复发放", Relic.COUNT, w.relicsGained);
        assertEquals(Relic.COUNT, Integer.bitCount(w.relicMask));
    }

    /** 集齐全部圣物 = 覆盖所有被动效果，无需依赖随机顺序。 */
    @Test
    public void relicPassivesFireOnceAllRelicsAreOwned() {
        GameWorld w = freshWorld();
        for (int i = 0; i < Relic.COUNT; i++) {
            w.wave = 4 + i * 4;
            killBoss(w);
        }
        for (PowerUp p : w.powerups) p.active = false;   // 清掉 Boss 掉落，隔离后续观察
        assertTrue(w.hasRelic(Relic.AEGIS_FIELD));
        assertTrue(w.hasRelic(Relic.BEAM_CORE));
        assertTrue(w.hasRelic(Relic.ETERNAL_WING));
        assertTrue(w.hasRelic(Relic.MIRROR_LANCE));
        assertEquals("圣盾外壳：护盾上限 +1", Ship.MAX_SHIELD_LAYERS + 1, w.ship.shieldCap);

        // 永恒僚机：僚机计时被持续续期，不会归零
        w.ship.wingmanTimer = 0f;
        w.update(1f / 60f);
        assertTrue(w.ship.wingmanTimer > 0f);

        // 光束核心：约 1.1 秒一束宽穿透光束（伤害 4、半径 22 是它的专属尺寸）
        boolean beamSeen = false;
        for (int i = 0; i < 90; i++) {
            w.update(1f / 60f);
            for (Bullet b : w.bullets) {
                if (b.active && b.piercing && b.damage == 4 && b.radius == 22f) beamSeen = true;
            }
        }
        assertTrue("光束核心应自动开火", beamSeen);
    }

    @Test
    public void mirrorLanceAddsTwoRoundsToTheBaseCannon() {
        GameWorld plain = freshWorld();
        clearBullets(plain);
        plain.resetWeaponCooldowns();
        plain.update(1f / 60f);
        int oneShot = countActive(plain);

        GameWorld w = freshWorld();
        for (int i = 0; i < Relic.COUNT; i++) {
            w.wave = 4 + i * 4;
            killBoss(w);
        }
        for (PowerUp p : w.powerups) p.active = false;
        w.ship.powerLevel = 1;
        w.ship.weapon = WeaponType.VULCAN;
        for (WeaponType t : WeaponType.values()) {
            w.ship.weaponTimers[t.ordinal()] = 0f;
            w.ship.weaponStacks[t.ordinal()] = 0;
        }
        clearBullets(w);
        w.resetWeaponCooldowns();
        w.update(1f / 60f);
        assertEquals("常驻基础炮 + 镜像枪两路 25° 斜射", oneShot + 2, countActive(w));
    }

    // ---- 新道具：弹跳弹 / 新星 / 炮台 / 纳米 ----

    @Test
    public void bounceRoundRicochetsOffWalls() {
        GameWorld w = freshWorld();
        w.ship.invulnerable = true;
        Bullet b = freeBullet(w);
        b.init(30f, 900f, -900f, -300f, 2, false);
        b.bounces = GameWorld.MAX_BOUNCES;
        w.update(0.02f);
        assertTrue("撞左壁后水平速度反向", b.vx > 0f);
        assertEquals(GameWorld.MAX_BOUNCES - 1, b.bounces);
        assertTrue("反弹后仍在场", b.active);
        assertEquals(14f, b.x, 0.01f);
    }

    @Test
    public void bounceWeaponIsUnblockedByShielders() {
        GameWorld w = freshWorld();
        w.spawnPowerUp(PowerUp.Kind.BOUNCE, w.ship.x, w.ship.y);
        w.update(1f / 60f);
        assertTrue(w.ship.isWeaponEquipped(WeaponType.BOUNCE));
        clearBullets(w);
        w.resetWeaponCooldowns();
        w.update(1f / 60f);
        int balls = 0;
        for (Bullet b : w.bullets) if (b.active && b.bounces > 0) balls++;
        assertTrue("弹跳弹应该上膛", balls > 0);

        Enemy shielder = freeEnemy(w);
        shielder.init(EnemyType.SHIELDER, 450f, 900f, 1f);
        float hp0 = shielder.hp;
        Bullet ball = freeBullet(w);
        ball.init(450f, 930f, 0f, -1150f, 1, false);
        ball.bounces = GameWorld.MAX_BOUNCES;
        w.update(1f / 60f);
        assertTrue("弹跳弹绕开正面护盾，直接打舰体", shielder.hp < hp0);
        assertEquals(Enemy.SHIELDER_SHIELD, shielder.shieldHp, 0.001f);
    }

    @Test
    public void novaClearsEveryBulletAndDamagesEveryEnemy() {
        GameWorld w = freshWorld();
        Enemy tank = freeEnemy(w);
        tank.init(EnemyType.TANK, 200f, 500f, 1f);
        float hp0 = tank.hp;
        for (int i = 0; i < 5; i++) {
            EnemyBullet b = w.enemyBullets[i];
            b.init(100f + i * 80f, 900f, 0f, 200f);
        }
        w.spawnPowerUp(PowerUp.Kind.NOVA, w.ship.x, w.ship.y);
        w.update(1f / 60f);

        for (EnemyBullet b : w.enemyBullets) assertFalse("新星应清光全场敌弹", b.active);
        assertEquals(hp0 - GameWorld.NOVA_DAMAGE, tank.hp, 0.001f);
        assertTrue(w.novaFlash > 0f);
        assertTrue(hasEvent(w, GameWorld.EventType.NOVA));
    }

    @Test
    public void turretDeploysHoldsGroundAndAbsorbsFire() {
        GameWorld w = freshWorld();
        w.spawnPowerUp(PowerUp.Kind.TURRET, w.ship.x, w.ship.y);
        w.update(1f / 60f);
        Turret t = activeTurret(w);
        assertNotNull("拾取 TURRET 应立刻部署炮台", t);
        assertTrue(hasEvent(w, GameWorld.EventType.TURRET_DEPLOY));

        t.y = w.ship.y - 320f;             // 挪开一点，避免和飞船的判定混在一起
        boolean fired = false;
        for (int i = 0; i < 40; i++) {
            w.update(1f / 60f);
            for (Bullet b : w.bullets) if (b.active && b.drone) fired = true;
        }
        assertTrue("炮台每 0.5 秒三向齐射", fired);
        assertTrue("炮台会随战场缓慢下移", t.y > w.ship.y - 320f);

        float hp0 = t.hp;
        t.y = w.ship.y - 320f;
        EnemyBullet shot = freeEnemyBullet(w);
        shot.initRadius(t.x, t.y, 0f, 0f, 10f);
        w.update(1f / 60f);
        assertTrue("敌弹被炮台吃掉并扣耐久", t.hp < hp0);
        assertFalse(shot.active);
        assertEquals(3, w.lives);
    }

    @Test
    public void extraTurretsConvertToScoreWhenTheSlotsAreFull() {
        GameWorld w = freshWorld();
        for (int i = 0; i < w.turrets.length; i++) {
            w.spawnPowerUp(PowerUp.Kind.TURRET, w.ship.x, w.ship.y);
            w.update(1f / 60f);
        }
        assertEquals(w.turrets.length, activeTurretCount(w));
        int before = w.score;
        w.spawnPowerUp(PowerUp.Kind.TURRET, w.ship.x, w.ship.y);
        w.update(1f / 60f);
        assertEquals("满员时不浪费拾取，折算 250 分", 250, w.score - before);
        assertEquals(w.turrets.length, activeTurretCount(w));
    }

    @Test
    public void naniteAutoRegeneratesShieldLayers() {
        GameWorld w = freshWorld();
        w.spawnPowerUp(PowerUp.Kind.NANITE, w.ship.x, w.ship.y);
        w.update(1f / 60f);
        assertTrue(w.ship.nanoTimer > 0f);
        assertEquals(0, w.ship.shieldLayers);
        for (int i = 0; i < 25; i++) w.update(1f / 60f);   // 0.4s：第一次脉冲
        assertEquals("纳米修复会自己补一层", 1, w.ship.shieldLayers);
        for (int i = 0; i < (int) (Ship.NANITE_PERIOD * 60f) + 20; i++) w.update(1f / 60f);
        assertTrue("持续修复会补到第二层", w.ship.shieldLayers >= 2);
        assertTrue(w.ship.shieldLayers <= Ship.MAX_SHIELD_LAYERS);
    }

    // ---- 新敌机 ----

    @Test
    public void shielderBlocksFrontalRoundsButNotLasers() {
        GameWorld w = freshWorld();
        w.ship.invulnerable = true;
        Enemy e = freeEnemy(w);
        e.init(EnemyType.SHIELDER, 450f, 900f, 1f);
        assertEquals(Enemy.SHIELDER_SHIELD, e.shieldHp, 0.001f);
        float hp0 = e.hp;

        Bullet round = freeBullet(w);
        round.init(450f, 930f, 0f, -1150f, 1, false);
        w.update(1f / 60f);
        assertEquals("正面挡弹只掉护盾", Enemy.SHIELDER_SHIELD - 1f, e.shieldHp, 0.001f);
        assertEquals("舰体无伤", hp0, e.hp, 0.001f);
        assertFalse(round.active);
        assertTrue(hasEvent(w, GameWorld.EventType.SHIELD_BLOCK));

        Bullet beam = freeBullet(w);
        beam.initBeam(450f, 930f, 0f, -1450f, 3, 9f, 46f);
        w.update(1f / 60f);
        assertTrue("穿透光束无视护盾", e.hp < hp0);
    }

    @Test
    public void shieldBubblePopsAndThenTheHullIsVulnerable() {
        GameWorld w = freshWorld();
        w.ship.invulnerable = true;
        Enemy e = freeEnemy(w);
        e.init(EnemyType.SHIELDER, 450f, 900f, 1f);
        e.shieldHp = 1f;
        Bullet round = freeBullet(w);
        round.init(450f, 930f, 0f, -1150f, 1, false);
        w.update(1f / 60f);
        assertEquals(0f, e.shieldHp, 0.001f);
        assertFalse(e.hasFrontShield());
        assertTrue(hasEvent(w, GameWorld.EventType.SHIELD_BLOCK));

        float afterPop = e.hp;
        Bullet next = freeBullet(w);
        next.init(450f, 930f, 0f, -1150f, 1, false);
        w.update(1f / 60f);
        assertTrue("护盾崩了之后实弹就能打本体", e.hp < afterPop);
    }

    @Test
    public void minerDropsInPlaceMines() {
        GameWorld w = freshWorld();
        Enemy e = freeEnemy(w);
        e.init(EnemyType.MINER, 450f, 600f, 1f);
        e.mineCd = 0f;
        w.update(1f / 60f);
        EnemyBullet mine = null;
        for (EnemyBullet b : w.enemyBullets) if (b.active && b.mine) mine = b;
        assertNotNull("布雷艇应投下一枚水雷", mine);
        assertEquals(450f, mine.x, 20f);
        assertTrue(hasEvent(w, GameWorld.EventType.MINE));
        assertTrue("投雷后进入下一轮冷却", e.mineCd > 1.5f);
    }

    @Test
    public void carrierLaunchesTwoDrones() {
        GameWorld w = freshWorld();
        Enemy c = freeEnemy(w);
        c.init(EnemyType.CARRIER, 450f, 400f, 1f);
        c.droneCd = 0f;
        c.hoverCd = 5f;
        int before = activeEnemies(w);
        w.update(1f / 60f);
        assertEquals("母舰一次放两架僚机", before + 2, activeEnemies(w));
        assertTrue(hasEvent(w, GameWorld.EventType.FORMATION));
    }

    @Test
    public void eliteAffixThickensHpAndSlowsTheHull() {
        Enemy e = new Enemy();
        e.init(EnemyType.TANK, 400f, 300f, 1f);
        float speedBefore = e.speed();
        float normalHp = e.type.hp;
        assertFalse(e.elite);
        e.makeElite();
        assertTrue(e.elite);
        assertEquals(Math.round(normalHp * Enemy.ELITE_HP_MULT), Math.round(e.hp), 0);
        assertEquals("精英稍慢一点，方便玩家优先集火", speedBefore * 0.9f, e.speed(), 0.001f);
    }

    // ---- 小工具 ----

    /** 让飞船硬吃一发必中弹（调用方自备 killer，方便同时摆放观察用的弹）。 */
    private void killShip(GameWorld w, EnemyBullet killer) {
        w.lives = 3;
        w.ship.hasShield = false;
        w.ship.shieldLayers = 0;
        w.ship.invulnerable = false;
        killer.init(w.ship.x, w.ship.y, 0f, 0f);
        w.update(1f / 60f);
    }

    /** 走一遍 Boss 战：wave 里程碑登场 -> 一击必杀 -> 结算掉落与圣物。 */
    private void killBoss(GameWorld w) {
        // v1.17：两场 Boss 之间有最短喘息，这里只测结算，把喘息清掉
        w.bossGapTimer = 0f;
        w.update(1f / 60f);
        assertTrue("Boss 应已登场", w.boss.active);
        w.boss.hp = 0f;
        w.update(1f / 60f);
        assertFalse(w.boss.active);
    }

    private static boolean hasEvent(GameWorld w, GameWorld.EventType type) {
        for (GameWorld.GameEvent e : w.pollEvents()) if (e.type == type) return true;
        return false;
    }

    private static Enemy freeEnemy(GameWorld w) {
        for (Enemy e : w.enemies) if (!e.active) return e;
        throw new AssertionError("no free enemy slot");
    }

    private static EnemyBullet freeEnemyBullet(GameWorld w) {
        for (EnemyBullet b : w.enemyBullets) if (!b.active) return b;
        throw new AssertionError("no free enemy bullet slot");
    }

    private static Bullet freeBullet(GameWorld w) {
        for (Bullet b : w.bullets) if (!b.active) return b;
        throw new AssertionError("no free player bullet slot");
    }

    private static void clearBullets(GameWorld w) {
        for (Bullet b : w.bullets) b.active = false;
    }

    private static int activeEnemies(GameWorld w) {
        int n = 0;
        for (Enemy e : w.enemies) if (e.active) n++;
        return n;
    }

    private static int countActive(GameWorld w) {
        int n = 0;
        for (Bullet b : w.bullets) if (b.active) n++;
        return n;
    }

    private static Turret activeTurret(GameWorld w) {
        for (Turret t : w.turrets) if (t.active) return t;
        return null;
    }

    private static int activeTurretCount(GameWorld w) {
        int n = 0;
        for (Turret t : w.turrets) if (t.active) n++;
        return n;
    }
}
