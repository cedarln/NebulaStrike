package com.nebulastrike.game.core;

import org.junit.Test;

import static org.junit.Assert.*;

/** 迭代 13：竖屏坐标系、虚拟方向键移动、新道具（速射/双倍/生命）。 */
public class PortraitAndPowerupsTest {

    // ---- 竖屏坐标系 ----

    @Test
    public void virtualSpaceIsPortrait() {
        assertEquals(900f, GameWorld.WIDTH, 0.001f);
        assertEquals(1600f, GameWorld.HEIGHT, 0.001f);
        assertTrue(GameWorld.HEIGHT > GameWorld.WIDTH);
    }

    // ---- 浮动摇杆（相对拖动）与输入冻结 ----

    @Test
    public void moveShipToFollowsRelativeSlide() {
        GameWorld w = new GameWorld(7L, false);
        w.restart(GameMode.WAVES);
        float sx = w.ship.x, sy = w.ship.y;
        // 手指从锚点滑 120,80 -> 飞船目标 = 锚点飞船位 + 位移
        w.moveShipTo(sx + 120f, sy + 80f);
        for (int i = 0; i < 120; i++) w.update(1f / 60f); // 2 秒足够到位
        assertEquals(sx + 120f, w.ship.x, 2f);
        assertEquals(sy + 80f, w.ship.y, 2f);
    }

    @Test
    public void pauseFreezesShipTargetAndJoystick() {
        GameWorld w = new GameWorld(7L, false);
        w.restart(GameMode.WAVES);
        w.joyActive = true;
        w.moveShipTo(w.ship.x + 400f, w.ship.y);
        w.pauseIfPlaying();
        assertFalse(w.joyActive);
        float pausedX = w.ship.x;
        for (int i = 0; i < 30; i++) w.update(1f / 60f); // PAUSED 不推进模拟
        assertEquals(pausedX, w.ship.x, 0.001f);
        // 恢复后不漂移（目标已冻结在暂停位置）
        w.handleUiAction(Ui.Action.RESUME);
        w.update(1f / 60f);
        assertEquals(pausedX, w.ship.x, 1.5f);
    }

    @Test
    public void gameOverClearsJoystickState() {
        GameWorld w = new GameWorld(7L, false);
        w.restart(GameMode.WAVES);
        w.joyActive = true;
        w.lives = 1;
        Enemy e = firstFreeEnemy(w);
        e.init(EnemyType.DRONE, w.ship.x, w.ship.y, 1f);
        w.update(1f / 60f); // 撞机死亡
        assertEquals(GameWorld.State.GAME_OVER, w.state);
        assertFalse(w.joyActive);
    }

    @Test
    public void resetClearsRapid() {
        Ship s = new Ship();
        s.reset(100, 100);
        s.rapidTimer = 5f;
        s.reset(50, 50);
        assertEquals(0f, s.rapidTimer, 0.001f);
    }

    @Test
    public void joyAnimFadesInOnPressAndOutOnRelease() {
        GameWorld w = new GameWorld(7L, false);
        w.restart(GameMode.WAVES);
        w.joyActive = true;
        for (int i = 0; i < 20; i++) w.update(1f / 60f); // 0.33s 足够渐入完成
        assertEquals(1f, w.joyAnim, 0.001f);

        w.joyActive = false;
        for (int i = 0; i < 20; i++) w.update(1f / 60f); // 渐出
        assertEquals(0f, w.joyAnim, 0.001f);
    }

    @Test
    public void joyAnimSnapsToZeroOnPauseOrGameOver() {
        GameWorld w = new GameWorld(7L, false);
        w.restart(GameMode.WAVES);
        w.joyActive = true;
        w.update(1f / 60f);
        assertTrue(w.joyAnim > 0f);
        w.clearShipInput(); // 暂停/死亡路径
        assertEquals(0f, w.joyAnim, 0.001f);
    }

    // ---- 速射 RAPID ----

    @Test
    public void rapidDoublesFireRate() {
        GameWorld w = new GameWorld(7L, false);
        w.restart(GameMode.WAVES);
        w.ship.reset(GameWorld.WIDTH / 2f, GameWorld.HEIGHT - 140f);
        int shotsBefore = w.shotsFired;
        for (int i = 0; i < 60; i++) w.update(1f / 60f); // 1s 普通射速
        int normalShots = w.shotsFired - shotsBefore;

        w.spawnPowerUp(PowerUp.Kind.RAPID, w.ship.x, w.ship.y);
        // 碰撞收集
        w.update(1f / 60f);
        assertEquals(Ship.RAPID_DURATION, w.ship.rapidTimer, 0.01f);
        int mid = w.shotsFired;
        for (int i = 0; i < 60; i++) w.update(1f / 60f); // 1s 速射
        int rapidShots = w.shotsFired - mid;
        assertTrue("速射应打出更多子弹 " + rapidShots + " vs " + normalShots,
                rapidShots > normalShots);
    }

    @Test
    public void rapidExpiresAndHalvesCooldownBack() {
        GameWorld w = new GameWorld(7L, false);
        w.restart(GameMode.WAVES);
        w.ship.rapidTimer = 0.2f;
        for (int i = 0; i < 20; i++) w.update(1f / 60f); // 0.33s 后过期
        assertEquals(0f, w.ship.rapidTimer, 0.001f);
    }

    // ---- 双倍分数 X2 ----

    @Test
    public void x2DoublesKillScore() {
        GameWorld w = new GameWorld(7L, false);
        w.restart(GameMode.WAVES);
        // 放一个 DRONE 并击杀，记录普通分
        Enemy e = firstFreeEnemy(w);
        e.init(EnemyType.DRONE, w.ship.x, w.ship.y - 300f, 1f);
        int base = EnemyType.DRONE.score;
        w.score = 0;
        w.killEnemyForTest(e);
        assertEquals(base, w.score);

        // 吃 X2 再击杀
        w.spawnPowerUp(PowerUp.Kind.X2, w.ship.x, w.ship.y);
        w.update(1f / 60f);
        w.score = 0;
        Enemy e2 = firstFreeEnemy(w);
        e2.init(EnemyType.DRONE, w.ship.x, w.ship.y - 300f, 1f);
        w.killEnemyForTest(e2);
        assertEquals(base * 2, w.score);

        // 8 秒后失效
        for (int i = 0; i < 8 * 60 + 5; i++) w.update(1f / 60f);
        assertEquals(0f, w.x2Timer, 0.001f);
        w.score = 0;
        Enemy e3 = firstFreeEnemy(w);
        e3.init(EnemyType.DRONE, w.ship.x, w.ship.y - 300f, 1f);
        w.killEnemyForTest(e3);
        assertEquals(base, w.score);
    }

    @Test
    public void x2AlsoDoublesBombOverflowAndLifeSalvage() {
        GameWorld w = new GameWorld(7L, false);
        w.restart(GameMode.WAVES);
        w.ship.bombs = Ship.MAX_BOMBS; // 满 -> 溢出转 50 分
        w.spawnPowerUp(PowerUp.Kind.X2, w.ship.x, w.ship.y);
        w.update(1f / 60f);
        w.score = 0;
        w.spawnPowerUp(PowerUp.Kind.BOMB, w.ship.x, w.ship.y);
        w.update(1f / 60f);
        assertEquals(100, w.score); // 50 * 2
    }

    // ---- 生命 LIFE ----

    @Test
    public void lifeAddsLifeUpToMaxThenSalvage() {
        GameWorld w = new GameWorld(7L, false);
        w.restart(GameMode.WAVES);
        w.lives = 2;
        w.spawnPowerUp(PowerUp.Kind.LIFE, w.ship.x, w.ship.y);
        w.update(1f / 60f);
        assertEquals(3, w.lives);
        w.lives = GameWorld.MAX_LIVES;
        w.score = 0;
        w.spawnPowerUp(PowerUp.Kind.LIFE, w.ship.x, w.ship.y);
        w.update(1f / 60f);
        assertEquals(GameWorld.MAX_LIVES, w.lives);
        assertEquals(200, w.score); // 满命折算
    }

    // ---- 掉落权重覆盖全部可掉落种类 ----

    @Test
    public void dropTableCoversEveryDroppableKind() {
        // 纯函数 dropKind：每个区段取一点，必须覆盖除 BONUS 外的全部枚举
        // （BONUS 只由突袭奖励波投放，不进入随机掉落表）
        float[] probe = {0f, 0.20f, 0.32f, 0.42f, 0.50f, 0.60f, 0.68f, 0.74f,
                0.80f, 0.85f, 0.89f, 0.93f, 1.0f};
        java.util.Set<PowerUp.Kind> seen = java.util.EnumSet.noneOf(PowerUp.Kind.class);
        for (float r : probe) seen.add(GameWorld.dropKind(r));
        java.util.Set<PowerUp.Kind> expected = java.util.EnumSet.allOf(PowerUp.Kind.class);
        expected.remove(PowerUp.Kind.BONUS);
        assertEquals(expected, seen);
        assertEquals(PowerUp.Kind.SPREAD, GameWorld.dropKind(0.0f));
        assertEquals(PowerUp.Kind.TIME, GameWorld.dropKind(1.0f));
        assertEquals(PowerUp.Kind.ORBIT, GameWorld.dropKind(0.68f));
        assertEquals(PowerUp.Kind.CHAIN, GameWorld.dropKind(0.74f));
    }

    private Enemy firstFreeEnemy(GameWorld w) {
        for (Enemy e : w.enemies) {
            if (!e.active) return e;
        }
        throw new AssertionError("no free enemy slot");
    }

    private int activeBullets(GameWorld w) {
        int n = 0;
        for (Bullet b : w.bullets) if (b.active) n++;
        return n;
    }

    /** 清掉场上所有玩家子弹（隔离"上一轮发射"对计数的影响）。 */
    private void clearBullets(GameWorld w) {
        for (Bullet b : w.bullets) b.active = false;
    }

    // ---- 火力等级 POWER（子弹数量） ----

    @Test
    public void powerUpsLevelAndVulcanBarrageGrows() {
        GameWorld w = new GameWorld(7L, false);
        w.restart(GameMode.WAVES);
        w.ship.reset(GameWorld.WIDTH / 2f, GameWorld.HEIGHT - 140f);
        assertEquals(1, w.ship.powerLevel);

        w.spawnPowerUp(PowerUp.Kind.POWER, w.ship.x, w.ship.y);
        w.update(1f / 60f); // 收集
        assertEquals(2, w.ship.powerLevel);
        clearBullets(w);
        w.ship.resetCooldown();
        w.update(1f / 60f); // L2：双发平行
        assertEquals(2, activeBullets(w));

        w.spawnPowerUp(PowerUp.Kind.POWER, w.ship.x, w.ship.y);
        w.update(1f / 60f);
        assertEquals(3, w.ship.powerLevel);
        clearBullets(w);
        w.ship.resetCooldown();
        w.update(1f / 60f); // L3：双平行 + 双斜
        assertEquals(4, activeBullets(w));
    }

    @Test
    public void powerAtMaxLevelConvertsToScore() {
        GameWorld w = new GameWorld(7L, false);
        w.restart(GameMode.WAVES);
        w.ship.powerLevel = Ship.MAX_POWER;
        int s0 = w.score;
        w.spawnPowerUp(PowerUp.Kind.POWER, w.ship.x, w.ship.y);
        w.update(1f / 60f);
        assertEquals(Ship.MAX_POWER, w.ship.powerLevel); // 不超上限
        assertEquals(300, w.score - s0);
    }

    @Test
    public void crashDropsPowerLevel() {
        GameWorld w = new GameWorld(7L, false);
        w.restart(GameMode.WAVES);
        w.ship.powerLevel = 3;
        w.lives = 2;
        w.ship.hasShield = false;
        w.ship.invulnerable = false;
        Enemy e = firstFreeEnemy(w);
        e.init(EnemyType.DRONE, w.ship.x, w.ship.y, 1f);
        w.update(1f / 60f); // 撞机
        assertEquals(2, w.ship.powerLevel); // 降 1 级
    }

    @Test
    public void resetRestoresPowerLevelOne() {
        Ship s = new Ship();
        s.reset(100, 100);
        s.powerLevel = 3;
        s.reset(50, 50);
        assertEquals(1, s.powerLevel);
    }

    // ---- 穿透激光 LASER（子弹类型） ----

    @Test
    public void laserCollectSwitchesWeaponAndFiresBeams() {
        GameWorld w = new GameWorld(7L, false);
        w.restart(GameMode.WAVES);
        w.ship.reset(GameWorld.WIDTH / 2f, GameWorld.HEIGHT - 140f);
        w.spawnPowerUp(PowerUp.Kind.LASER, w.ship.x, w.ship.y);
        w.update(1f / 60f);
        assertEquals(WeaponType.LASER, w.ship.weapon);
        assertEquals(Ship.WEAPON_DURATION, w.ship.weaponTimer, 0.01f);

        clearBullets(w);
        w.ship.resetCooldown();
        w.update(1f / 60f); // L1：单束
        assertEquals(1, activeBullets(w));
        for (Bullet b : w.bullets) {
            if (b.active) {
                assertTrue(b.piercing);
                assertEquals(0f, b.vx, 0.001f);
                assertTrue(b.vy < 0f);
                break;
            }
        }
    }

    @Test
    public void laserBeamsGrowWithPowerLevel() {
        GameWorld w = new GameWorld(7L, false);
        w.restart(GameMode.WAVES);
        w.ship.reset(GameWorld.WIDTH / 2f, GameWorld.HEIGHT - 140f);
        w.ship.weapon = WeaponType.LASER;
        w.ship.weaponTimer = 5f;
        w.ship.powerLevel = 3;
        w.ship.resetCooldown();
        w.update(1f / 60f); // L3：三束
        assertEquals(3, activeBullets(w));
        int lasers = 0;
        for (Bullet b : w.bullets) if (b.active && b.piercing) lasers++;
        assertEquals(3, lasers);
    }

    @Test
    public void laserPiercesThroughEnemy() {
        GameWorld w = new GameWorld(7L, false);
        w.restart(GameMode.WAVES);
        w.ship.reset(GameWorld.WIDTH / 2f, GameWorld.HEIGHT - 140f);
        w.ship.weapon = WeaponType.LASER;
        w.ship.weaponTimer = 5f;
        // TANK 高血量，放在飞船正上方
        Enemy tank = firstFreeEnemy(w);
        tank.init(EnemyType.TANK, w.ship.x, w.ship.y - 140f, 1f);
        int hp0 = (int) tank.hp;

        clearBullets(w);
        w.ship.resetCooldown();
        for (int i = 0; i < 12; i++) w.update(1f / 60f); // 0.2s：激光飞过并命中
        assertTrue("激光应命中并造成伤害", tank.hp < hp0);
        assertTrue("穿透弹命中后应继续存在", tank.hp > 0); // TANK 不死，弹未消失
        boolean beamAlive = false;
        for (Bullet b : w.bullets) if (b.active && b.piercing) beamAlive = true;
        assertTrue("激光命中后仍应飞行（穿透）", beamAlive);
    }

    // ---- 掉落池容量覆盖全部枚举 ----

    @Test
    public void allPowerupKindsSpawn() {
        GameWorld w = new GameWorld(7L, false);
        w.restart(GameMode.WAVES);
        int i = 0;
        for (PowerUp.Kind kind : PowerUp.Kind.values()) {
            w.spawnPowerUp(kind, 100f + (i++) * 60f, 400f);
        }
        int spawned = 0;
        for (PowerUp p : w.powerups) if (p.active) spawned++;
        assertEquals(PowerUp.Kind.values().length, spawned);
        assertEquals(14, spawned);
    }

    @Test
    public void magnetAttractsAndMarksPickup() {
        GameWorld w = new GameWorld(7L, false);
        w.restart(GameMode.WAVES);
        w.spawnPowerUp(PowerUp.Kind.POWER, w.ship.x + 50f, w.ship.y + 10f);
        PowerUp p = null;
        for (PowerUp q : w.powerups) if (q.active) p = q;
        p.update(1f / 60f, w.ship.x, w.ship.y); // 在磁吸范围内
        assertTrue(p.attracted);
    }
}
