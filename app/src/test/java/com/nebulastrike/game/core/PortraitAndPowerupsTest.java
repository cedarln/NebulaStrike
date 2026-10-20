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

    // ---- 掉落权重覆盖所有 7 种 ----

    @Test
    public void dropTableCoversAllSevenKinds() {
        // 权重边界单调：各分支互斥且总和 <= 1
        java.util.Random r = new java.util.Random(3);
        java.util.Set<PowerUp.Kind> seen = java.util.EnumSet.noneOf(PowerUp.Kind.class);
        for (int i = 0; i < 20000 && seen.size() < 7; i++) {
            float roll = r.nextFloat();
            PowerUp.Kind k;
            if (roll < 0.26f) k = PowerUp.Kind.SPREAD;
            else if (roll < 0.48f) k = PowerUp.Kind.MISSILE;
            else if (roll < 0.68f) k = PowerUp.Kind.SHIELD;
            else if (roll < 0.82f) k = PowerUp.Kind.BOMB;
            else if (roll < 0.92f) k = PowerUp.Kind.RAPID;
            else if (roll < 0.98f) k = PowerUp.Kind.X2;
            else k = PowerUp.Kind.LIFE;
            seen.add(k);
        }
        assertEquals(java.util.EnumSet.allOf(PowerUp.Kind.class), seen);
    }

    private Enemy firstFreeEnemy(GameWorld w) {
        for (Enemy e : w.enemies) {
            if (!e.active) return e;
        }
        throw new AssertionError("no free enemy slot");
    }
}
