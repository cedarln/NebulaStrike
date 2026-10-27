package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.43 迭代 33：回旋镖 BOOMERANG。
 *
 * <p>它是一把"竖线双程"武器：穿透圆盘向上抛出，顶点折返后沿发射线扫回来，
 * 同一架敌机在去程与回程各吃一次 3 点伤害；回程顺手切掉路上的敌弹。
 * 作为掉落武器叠层不替换，叠层只增加圆盘数量。
 */
public class BoomerangV143Test {

    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(20261027L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.settings.setDifficulty(Difficulty.STANDARD);
        world.lives = 999_999;
        // 飞船靠左：基础机炮的弹道避开测试用的敌机列
        world.ship.x = 120f;
        world.ship.prevX = 120f;
        world.ship.y = 1460f;
        world.ship.prevY = 1460f;
    }

    // ---- 辅助 ----

    private Enemy pinnedEnemy(float x, float y, float hp) {
        for (Enemy e : world.enemies) {
            if (e.active) continue;
            e.init(EnemyType.TANK, x, y, 1f);
            e.maxHp = hp;
            e.hp = hp;
            e.fireCd = 999f;   // 测试不需要它开火
            return e;
        }
        throw new IllegalStateException("敌机池已满");
    }

    private Bullet boomerangAt(float x, float y, float vy, float outTime, float homeX) {
        for (Bullet b : world.bullets) {
            if (b.active) continue;
            b.initBoomerang(x, y, 0f, vy, WeaponType.BOOMERANG.damage, outTime, homeX);
            return b;
        }
        throw new IllegalStateException("玩家弹池已满");
    }

    private Bullet firstBoomerang() {
        for (Bullet b : world.bullets) {
            if (b.active && b.boomerang) return b;
        }
        return null;
    }

    private int activeBoomerangs() {
        int n = 0;
        for (Bullet b : world.bullets) if (b.active && b.boomerang) n++;
        return n;
    }

    private void step(int frames) {
        for (int i = 0; i < frames; i++) world.update(1f / 60f);
    }

    /** 把敌机钉在原地，避免它自己下坠出测试弹道。 */
    private void stepHolding(Enemy e, float x, float y, int frames) {
        for (int i = 0; i < frames; i++) {
            e.x = x;
            e.y = y;
            world.update(1f / 60f);
        }
    }

    // ---- 叠加 / 掉落 ----

    @Test
    public void boomerangStacksWithoutReplacingOtherWeapons() {
        world.spawnPowerUp(PowerUp.Kind.ARC, world.ship.x, world.ship.y);
        world.update(1f / 60f);
        world.spawnPowerUp(PowerUp.Kind.BOOMERANG, world.ship.x, world.ship.y);
        world.update(1f / 60f);

        assertTrue("回旋镖应装备上", world.ship.isWeaponEquipped(WeaponType.BOOMERANG));
        assertTrue("原有电弧炮不能被替换掉", world.ship.isWeaponEquipped(WeaponType.ARC));
        assertEquals(1, world.ship.stackOf(WeaponType.BOOMERANG));

        world.spawnPowerUp(PowerUp.Kind.BOOMERANG, world.ship.x, world.ship.y);
        world.update(1f / 60f);
        assertEquals("同类回旋镖叠层", 2, world.ship.stackOf(WeaponType.BOOMERANG));
        assertFalse("回旋镖是掉落武器，不是融合武器", WeaponType.BOOMERANG.isFused());
        assertEquals("BOOMERANG", WeaponType.BOOMERANG.label());
    }

    @Test
    public void boomerangVolleyLaunchesReturningDiscs() {
        world.ship.addWeapon(WeaponType.BOOMERANG);
        world.resetWeaponCooldowns();
        world.update(1f / 60f);

        Bullet disc = firstBoomerang();
        assertNotNull("回旋镖武器应该发射带 boomerang 标记的弹体", disc);
        assertEquals(WeaponType.BOOMERANG.damage, disc.damage);
        assertEquals("回旋镖是穿透体，去程回程都要结算", true, disc.piercing);
        assertTrue("第一段朝上抛", disc.vy < 0f);
        assertEquals("发射当帧已走一步", GameWorld.BOOMERANG_OUT_TIME - 1f / 60f,
                disc.boomerangTimer, 0.0001f);
        assertFalse("发射瞬间还没折返", disc.boomerangReturning);
        assertEquals(13f, disc.radius, 0.0001f);
    }

    @Test
    public void stackingRaisesDiscCount() {
        world.ship.addWeapon(WeaponType.BOOMERANG);
        world.ship.addWeapon(WeaponType.BOOMERANG);
        world.resetWeaponCooldowns();
        world.update(1f / 60f);
        assertEquals("两层回旋镖一次两枚", 2, activeBoomerangs());

        world.ship.addWeapon(WeaponType.BOOMERANG);
        world.resetWeaponCooldowns();
        world.update(1f / 60f);
        assertTrue("三层至少三枚", activeBoomerangs() >= 3);
    }

    @Test
    public void dropTableCarvesOutBoomerangWeight() {
        assertEquals(PowerUp.Kind.BOUNCE, GameWorld.dropKind(0.86f));
        assertEquals(PowerUp.Kind.BOOMERANG, GameWorld.dropKind(0.87f));
        assertEquals(PowerUp.Kind.BOOMERANG, GameWorld.dropKind(0.879f));
        assertEquals(PowerUp.Kind.NOVA, GameWorld.dropKind(0.88f));
        // 原有核心武器权重不动
        assertEquals(PowerUp.Kind.MISSILE, GameWorld.dropKind(0.30f));
        assertEquals(PowerUp.Kind.ARC, GameWorld.dropKind(0.96f));
    }

    // ---- 弹道 ----

    @Test
    public void discTurnsAtApexAndComesBackToTheLaunchLine() {
        Bullet disc = boomerangAt(450f, 1300f, -600f, 0.3f, 900f);
        float startY = disc.y;
        float minY = startY;
        for (int i = 0; i < 15; i++) {
            world.update(1f / 60f);
            minY = Math.min(minY, disc.y);
        }
        assertFalse("去程还没结束", disc.boomerangReturning);
        assertTrue("去程应该明显向上推进", minY < startY - 100f);

        step(22);
        assertTrue("到点后必须折返", disc.boomerangReturning);
        assertTrue("折返后开始下坠", disc.vy > 0f);
        assertTrue("回程会往 homeX 收", disc.x > 480f);
        assertTrue("回程必须比顶点低", disc.y > minY + 120f);
        assertTrue("弹体持续自旋（渲染用）", disc.spin > 1f);
    }

    @Test
    public void aPinnedEnemyTakesDamageOnBothPasses() {
        Enemy e = pinnedEnemy(450f, 1000f, 1000f);
        boomerangAt(450f, 1300f, -880f, GameWorld.BOOMERANG_OUT_TIME, 450f);
        stepHolding(e, 450f, 1000f, 120);

        // 单趟贴着重型目标最多结算 3 次（3 或暴击 5 点，上限 15），
        // 累计超过 15 就说明回程确实又打了一轮；两趟封顶 30
        int dealt = Math.round(1000f - e.hp);
        assertTrue("回程必须再命中一轮（累计 > 单趟上限 15）", dealt >= 16);
        assertTrue("只有去程与回程两趟，不该持续连扣（累计 ≤ 30）", dealt <= 30);
    }

    @Test
    public void piercingPassesThroughTheWholeColumn() {
        Enemy near = pinnedEnemy(450f, 950f, 1000f);
        Enemy far = pinnedEnemy(450f, 850f, 1000f);
        Bullet disc = boomerangAt(450f, 1200f, -880f, GameWorld.BOOMERANG_OUT_TIME, 450f);
        for (int i = 0; i < 32; i++) {
            near.x = 450f;
            near.y = 950f;
            far.x = 450f;
            far.y = 850f;
            world.update(1f / 60f);
        }

        assertTrue("去程先打近处", near.hp < 1000f);
        assertTrue("穿透后继续打更远的敌机", far.hp < 1000f);
        assertTrue("打穿两架之后弹体仍然存活", disc.active);
    }

    @Test
    public void sweepCutsDownEnemyBulletsOnTheWay() {
        EnemyBullet eb = null;
        for (EnemyBullet b : world.enemyBullets) {
            if (!b.active) {
                b.init(450f, 1100f, 0f, 0f);
                eb = b;
                break;
            }
        }
        assertNotNull(eb);
        int before = world.bulletsShotDown;
        boomerangAt(450f, 1300f, -880f, GameWorld.BOOMERANG_OUT_TIME, 450f);
        step(30);

        assertFalse("回旋镖顺手切掉路径上的敌弹", eb.active);
        assertEquals("计入点弹统计", before + 1, world.bulletsShotDown);
    }

    @Test
    public void returnPassEventuallyLeavesTheScreen() {
        boomerangAt(450f, 1300f, -880f, 0.3f, 450f);
        step(180);
        assertEquals("回程飞出画面后回收，不会永远留在池里", 0, activeBoomerangs());
    }

    @Test
    public void poolReuseClearsBoomerangState() {
        Bullet disc = boomerangAt(450f, 1300f, -880f, 0.3f, 450f);
        disc.active = false;
        disc.init(100f, 200f, 0f, -900f, 1, false);

        assertFalse("复用弹体必须清掉回旋镖标记", disc.boomerang);
        assertFalse(disc.boomerangReturning);
        assertEquals(0f, disc.boomerangTimer, 0.0001f);
        assertEquals(0f, disc.spin, 0.0001f);
        assertFalse("普通弹不该继承穿透", disc.piercing);
    }
}
