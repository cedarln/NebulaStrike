package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.53 迭代 43：锁定激光 LOCKON。
 *
 * <p>它不占正前方弹道：每次开火自动点名最近的敌机 / Boss 部件，从屏幕顶端落下一道
 * 贯穿光柱。首拾锁 2 个目标，叠层最多 4 个；和所有掉落武器一样叠加持有，绝不替换。
 */
public class LockOnV153Test {

    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(20261028L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.lives = 999_999;
        // 飞船靠左下：基础机炮的竖直弹道避开测试目标所在列
        world.ship.x = 120f;
        world.ship.prevX = 120f;
        world.ship.y = 1460f;
        world.ship.prevY = 1460f;
    }

    // ---- 辅助 ----

    private Enemy enemyAt(EnemyType type, float x, float y, float hp) {
        for (Enemy e : world.enemies) {
            if (e.active) continue;
            e.init(type, x, y, 1f);
            if (hp > 0f) {
                e.maxHp = hp;
                e.hp = hp;
            }
            return e;
        }
        throw new IllegalStateException("敌机池已满");
    }

    private int activeBeams() {
        int n = 0;
        for (Bullet b : world.bullets) if (b.active && b.lockOn) n++;
        return n;
    }

    private int activeMarks() {
        int n = 0;
        for (float t : world.lockMarkTime) if (t > 0f) n++;
        return n;
    }

    private void step(int frames) {
        for (int i = 0; i < frames; i++) {
            world.update(1f / 60f);
            world.pollEvents();
        }
    }

    // ---- 掉落 / 叠加 ----

    @Test
    public void lockOnIsAppendedAndDroppable() {
        PowerUp.Kind[] kinds = PowerUp.Kind.values();
        assertEquals("新道具只追加在末尾，保证既有 ordinal 不变",
                PowerUp.Kind.LOCKON, kinds[kinds.length - 1]);
        assertEquals(PowerUp.Kind.LOCKON, GameWorld.dropKind(0.895f));
        // 既有区段全部原地不动
        assertEquals(PowerUp.Kind.DECOY, GameWorld.dropKind(0.885f));
        assertEquals(PowerUp.Kind.NOVA, GameWorld.dropKind(0.90f));
        assertEquals(PowerUp.Kind.GRAVITY, GameWorld.dropKind(0.94f));
        assertEquals(PowerUp.Kind.ARC, GameWorld.dropKind(0.96f));
        assertEquals(PowerUp.Kind.REFLECT, GameWorld.dropKind(0.98f));
        assertEquals(PowerUp.Kind.TURRET, GameWorld.dropKind(0.999f));
        assertEquals("LOCKON", WeaponType.LOCKON.label());
        assertFalse("锁定激光是掉落武器，不是融合武器", WeaponType.LOCKON.isFused());
    }

    @Test
    public void collectStacksWithoutReplacingOtherWeapons() {
        world.ship.addWeapon(WeaponType.LASER);
        world.spawnPowerUp(PowerUp.Kind.LOCKON, world.ship.x, world.ship.y);
        world.update(1f / 60f);

        assertTrue(world.ship.isWeaponEquipped(WeaponType.LOCKON));
        assertTrue("原有激光不能被替换掉", world.ship.isWeaponEquipped(WeaponType.LASER));
        assertTrue("基础炮永远常驻", world.ship.isWeaponEquipped(WeaponType.VULCAN));
        assertEquals(1, world.ship.stackOf(WeaponType.LOCKON));

        world.spawnPowerUp(PowerUp.Kind.LOCKON, world.ship.x, world.ship.y);
        world.update(1f / 60f);
        assertEquals("同类锁定激光叠层", 2, world.ship.stackOf(WeaponType.LOCKON));
    }

    // ---- 目标选择 / 伤害 ----

    @Test
    public void firstPickupLocksTwoNearestTargets() {
        enemyAt(EnemyType.TANK, 450f, 900f, 400f);   // 最近
        enemyAt(EnemyType.TANK, 700f, 800f, 400f);   // 次近
        enemyAt(EnemyType.TANK, 300f, 500f, 400f);   // 最远，不该被点名

        world.ship.addWeapon(WeaponType.LOCKON);
        world.resetWeaponCooldowns();
        world.update(1f / 60f);

        assertEquals("首拾同时锁 2 个目标", 2, activeBeams());
        assertEquals("每个目标各留一个落点环", 2, activeMarks());
        boolean sawNear = false;
        boolean sawSecond = false;
        boolean sawFar = false;
        for (Bullet b : world.bullets) {
            if (!b.active || !b.lockOn) continue;
            assertTrue("光柱朝下砸", b.vy > 0f);
            assertTrue("光柱起点在目标上方", b.y < 900f);
            assertEquals("首拾伤害 4", 4, b.damage);
            if (Math.abs(b.x - 450f) < 2f) sawNear = true;
            else if (Math.abs(b.x - 700f) < 2f) sawSecond = true;
            if (Math.abs(b.x - 300f) < 2f) sawFar = true;
        }
        assertTrue("最近的敌机被点名", sawNear);
        assertTrue("次近的敌机也被点名", sawSecond);
        assertFalse("最远的敌机不在首拾射程内", sawFar);
    }

    @Test
    public void stackingLocksMoreTargetsAndHitsHarder() {
        for (int i = 0; i < 4; i++) {
            enemyAt(EnemyType.TANK, 200f + i * 160f, 900f, 400f);
        }
        world.ship.addWeapon(WeaponType.LOCKON);
        world.ship.addWeapon(WeaponType.LOCKON);
        world.ship.addWeapon(WeaponType.LOCKON);
        world.resetWeaponCooldowns();
        world.update(1f / 60f);

        assertEquals("三层叠满，同时点名 4 个目标", GameWorld.LOCKON_MAX_TARGETS, activeBeams());
        for (Bullet b : world.bullets) {
            if (b.active && b.lockOn) {
                assertEquals("叠 3 层后光柱伤害 5", 5, b.damage);
            }
        }
    }

    @Test
    public void beamFallsFromAboveAndDamagesTheLockedTarget() {
        Enemy tank = enemyAt(EnemyType.TANK, 450f, 900f, 400f);
        world.ship.addWeapon(WeaponType.LOCKON);
        world.resetWeaponCooldowns();
        step(30);

        assertTrue("光柱应命中被点名的坦克", tank.hp < 400f);
        assertTrue("一次光柱只结算一次（含暴击也不超过 8 点）", tank.hp >= 392f);
    }

    @Test
    public void emptyScreenFiresNothing() {
        world.ship.addWeapon(WeaponType.LOCKON);
        world.resetWeaponCooldowns();
        world.update(1f / 60f);

        assertEquals("没有目标就不占弹池", 0, activeBeams());
        assertEquals("也不留落点环", 0, activeMarks());
    }

    @Test
    public void locksOntoBossWhenNoMinions() {
        world.boss.init(0);
        world.boss.y = 380f;
        world.ship.addWeapon(WeaponType.LOCKON);
        world.resetWeaponCooldowns();
        world.update(1f / 60f);

        assertTrue("没有小怪时改点名 Boss 部件", activeBeams() >= 1);
        assertTrue(activeMarks() >= 1);
    }

    @Test
    public void marksExpireAndRestartClearsState() {
        enemyAt(EnemyType.TANK, 450f, 900f, 400f);
        world.ship.addWeapon(WeaponType.LOCKON);
        world.resetWeaponCooldowns();
        world.update(1f / 60f);
        assertEquals("场上只有一个目标，就只留一个落点环", 1, activeMarks());

        step(40);
        assertEquals("落点环会自己淡出", 0, activeMarks());

        // 再点名一次，然后确认重开不会把上一局的落点带进来
        world.resetWeaponCooldowns();
        world.update(1f / 60f);
        assertTrue(activeMarks() > 0);
        world.restart(GameMode.WAVES);
        assertEquals("重开清空落点环", 0, activeMarks());
        assertFalse("重开不保留锁定激光", world.ship.isWeaponEquipped(WeaponType.LOCKON));
    }
}
