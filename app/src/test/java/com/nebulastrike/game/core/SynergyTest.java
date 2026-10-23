package com.nebulastrike.game.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * v1.28：道具连携 SYNERGY。
 * 设计原则是"只加不减"：两件已有道具同时在线时，额外解锁一层被动加成；
 * 任意一件过期就自动收回，继续叠加不会替换或削弱任何既有系统。
 */
public class SynergyTest {
    private GameWorld world;

    @Before
    public void setUp() {
        world = new GameWorld(2818L);
        world.spawningEnabled = false;
        world.mutatorsEnabled = false;
        world.bountyEnabled = false;
    }

    private GameWorld newWorld(long seed) {
        GameWorld w = new GameWorld(seed);
        w.spawningEnabled = false;
        w.mutatorsEnabled = false;
        w.bountyEnabled = false;
        return w;
    }

    private void step(GameWorld w, int frames) {
        for (int i = 0; i < frames; i++) {
            w.ship.makeInvulnerable();
            w.update(1f / 60f);
            w.pollEvents();
        }
    }

    private int count(GameWorld w, GameWorld.EventType type) {
        int n = 0;
        for (GameWorld.GameEvent e : w.pollEvents()) {
            if (e.type == type) n++;
        }
        return n;
    }

    /** 每一组都必须"两件齐了"才成立，单独一件不算连携。 */
    @Test
    public void eachPairNeedsBothHalves() {
        Ship s = new Ship();
        s.reset(450f, 1400f);
        for (Synergy sync : Synergy.values()) {
            assertFalse("空装配不该有连携 " + sync, sync.holds(s));
        }

        s.addReflect();
        assertFalse("只有反射不算镜盾", Synergy.MIRROR.holds(s));
        s.addShield();
        assertTrue("反射 + 护盾 = 镜盾", Synergy.MIRROR.holds(s));

        Ship t = new Ship();
        t.reset(450f, 1400f);
        t.addWeapon(WeaponType.SPREAD);
        assertFalse("散射在火力 1 时不算三叉戟", Synergy.TRIDENT.holds(t));
        t.powerLevel = 2;
        assertTrue("散射 + 火力 ≥2 = 三叉戟", Synergy.TRIDENT.holds(t));

        Ship u = new Ship();
        u.reset(450f, 1400f);
        u.wingmanTimer = 5f;
        assertFalse("只有僚机不算编队", Synergy.VANGUARD.holds(u));
        u.orbitTimer = 5f;
        assertTrue("僚机 + 轨道球 = 编队", Synergy.VANGUARD.holds(u));

        Ship v = new Ship();
        v.reset(450f, 1400f);
        v.rapidTimer = 5f;
        assertFalse("只有速射不算过载链", Synergy.OVERCHARGE.holds(v));
        v.overdriveTimer = 5f;
        assertTrue("速射 + 过载 = 过载链", Synergy.OVERCHARGE.holds(v));

        Ship x = new Ship();
        x.reset(450f, 1400f);
        x.addWeapon(WeaponType.ARC);
        assertFalse("只有电弧不算导电", Synergy.CONDUCTION.holds(x));
        x.addWeapon(WeaponType.BOUNCE);
        assertTrue("电弧 + 弹跳 = 导电", Synergy.CONDUCTION.holds(x));
    }

    /** 镜盾把反射充能从 6 秒压到 3.75 秒。 */
    @Test
    public void mirrorShortensReflectRecharge() {
        Ship plain = new Ship();
        plain.reset(450f, 1400f);
        plain.addReflect();
        assertTrue(plain.consumeReflect());
        assertEquals(Ship.REFLECT_RECHARGE, plain.reflectCd[0], 0.001f);

        world.ship.addReflect();
        world.ship.addShield();
        world.refreshSynergies(1f / 60f);
        assertTrue(world.hasSynergy(Synergy.MIRROR));
        assertTrue(world.ship.consumeReflect());
        assertEquals(Ship.REFLECT_RECHARGE / Ship.MIRROR_RECHARGE_MULT,
                world.ship.reflectCd[0], 0.001f);
    }

    /** 镜盾还让弹回去的敌弹多 1 点伤害。 */
    @Test
    public void mirrorEmpowersReflectedBullet() {
        world.ship.addReflect();
        world.ship.addShield();
        world.refreshSynergies(1f / 60f);
        EnemyBullet src = new EnemyBullet();
        src.init(world.ship.x, world.ship.y, 0f, 220f);
        world.reflectEnemyBullet(src);
        Bullet out = null;
        for (Bullet b : world.bullets) {
            if (b.active && b.reflected) out = b;
        }
        assertNotNull("应弹出一发友军子弹", out);
        assertEquals(3, out.damage);
    }

    /** 三叉戟：散射弹获得穿透；没连携时仍是普通实弹。 */
    @Test
    public void tridentMakesSpreadPierce() {
        GameWorld plain = newWorld(311L);
        plain.ship.powerLevel = 1;
        plain.ship.addWeapon(WeaponType.SPREAD);
        plain.refreshSynergies(1f / 60f);
        assertFalse("火力 1 + 散射只是普通扇形", plain.hasSynergy(Synergy.TRIDENT));
        plain.resetWeaponCooldowns();
        plain.update(1f / 60f);
        int plainPiercing = 0;
        for (Bullet b : plain.bullets) {
            if (b.active && b.piercing && !b.arc) plainPiercing++;
        }
        assertEquals("无连携时散射不该穿透", 0, plainPiercing);

        GameWorld linked = newWorld(311L);
        linked.ship.powerLevel = 2;
        linked.ship.addWeapon(WeaponType.SPREAD);
        linked.refreshSynergies(1f / 60f);
        assertTrue(linked.hasSynergy(Synergy.TRIDENT));
        linked.resetWeaponCooldowns();
        linked.update(1f / 60f);
        int piercing = 0;
        for (Bullet b : linked.bullets) {
            if (b.active && b.piercing && !b.arc) piercing++;
        }
        assertTrue("三叉戟下散射弹应穿透", piercing > 0);
    }

    /** 编队连携：同样 0.6 秒，僚机多打一轮。 */
    @Test
    public void vanguardSpeedsUpWingman() {
        GameWorld solo = newWorld(312L);
        solo.ship.wingmanTimer = 10f;
        step(solo, 36);
        int soloShots = solo.wingmanShots;

        GameWorld pair = newWorld(312L);
        pair.ship.wingmanTimer = 10f;
        pair.ship.orbitTimer = 10f;
        step(pair, 36);
        assertTrue(pair.hasSynergy(Synergy.VANGUARD));
        assertTrue("僚机 + 轨道球应打得更快", pair.wingmanShots > soloShots);
    }

    /** 导电：电弧连锁跳跃从 1 次加到 2 次。 */
    @Test
    public void conductionAddsAnArcJump() {
        GameWorld plain = newWorld(313L);
        plain.ship.addWeapon(WeaponType.ARC);
        plain.refreshSynergies(1f / 60f);
        plain.resetWeaponCooldowns();
        plain.update(1f / 60f);
        assertEquals(1, maxArcJumps(plain));

        GameWorld linked = newWorld(313L);
        linked.ship.addWeapon(WeaponType.ARC);
        linked.ship.addWeapon(WeaponType.BOUNCE);
        linked.refreshSynergies(1f / 60f);
        assertTrue(linked.hasSynergy(Synergy.CONDUCTION));
        linked.resetWeaponCooldowns();
        linked.update(1f / 60f);
        assertEquals(2, maxArcJumps(linked));
    }

    private int maxArcJumps(GameWorld w) {
        int jumps = 0;
        boolean saw = false;
        for (Bullet b : w.bullets) {
            if (!b.active || !b.arc) continue;
            saw = true;
            jumps = Math.max(jumps, b.arcJumps);
        }
        assertTrue("应有一发电弧弹", saw);
        return jumps;
    }

    /** 过载链：同样的速射 + 过载，开启连携后 1 秒内应多打三分之一以上的轮次。 */
    @Test
    public void overchargeLiftsFireRate() {
        // 对照组：装配完全一样，只把连携层关掉
        GameWorld control = newWorld(314L);
        control.ship.overdriveTimer = 5f;
        control.ship.rapidTimer = 5f;
        control.synergiesEnabled = false;
        step(control, 60);
        assertFalse(control.hasSynergy(Synergy.OVERCHARGE));

        GameWorld linked = newWorld(314L);
        linked.ship.overdriveTimer = 5f;
        linked.ship.rapidTimer = 5f;
        step(linked, 60);
        assertTrue(linked.hasSynergy(Synergy.OVERCHARGE));
        assertTrue("过载链的射速应明显高于对照组：actually "
                + linked.shotsFired + " vs " + control.shotsFired,
                linked.shotsFired >= control.shotsFired * 4 / 3);
    }

    /** 点亮只播一次；条件消失后重新凑齐会再次点亮。 */
    @Test
    public void synergyLightsOnceAndReArms() {
        world.ship.addReflect();
        world.ship.addShield();
        world.refreshSynergies(1f / 60f);
        assertEquals(1, count(world, GameWorld.EventType.SYNERGY_ON));
        assertEquals(Synergy.MIRROR.ordinal(), world.synergyToastIndex);
        assertTrue(world.synergyToast > 0f);

        world.refreshSynergies(1f / 60f);
        assertEquals("条件持续满足时不重复播报", 0,
                count(world, GameWorld.EventType.SYNERGY_ON));

        world.ship.reflectLayers = 0;
        world.refreshSynergies(1f / 60f);
        assertFalse(world.hasSynergy(Synergy.MIRROR));

        world.ship.addReflect();
        world.refreshSynergies(1f / 60f);
        assertEquals("重新凑齐应再次点亮", 1,
                count(world, GameWorld.EventType.SYNERGY_ON));
        assertEquals(2, world.synergyUnlockCount);
    }

    /** 任意一件过期就收回加成；沙盒开关也能整层关掉。 */
    @Test
    public void expiredItemsDropTheSynergy() {
        world.ship.wingmanTimer = 5f;
        world.ship.orbitTimer = 5f;
        world.refreshSynergies(1f / 60f);
        assertTrue(world.hasSynergy(Synergy.VANGUARD));

        world.ship.orbitTimer = 0f;
        world.refreshSynergies(1f / 60f);
        assertFalse("轨道球过期后编队应失效", world.hasSynergy(Synergy.VANGUARD));

        world.ship.orbitTimer = 5f;
        world.refreshSynergies(1f / 60f);
        assertTrue(world.hasSynergy(Synergy.VANGUARD));
        world.synergiesEnabled = false;
        world.refreshSynergies(1f / 60f);
        assertEquals("沙盒开关关闭时整层连携不生效", 0, world.synergyMask);
    }

    /** 重开清空连携状态，不带进下一局。 */
    @Test
    public void restartClearsSynergyState() {
        world.ship.addReflect();
        world.ship.addShield();
        world.refreshSynergies(1f / 60f);
        assertTrue(world.synergyMask != 0);
        world.restart(GameMode.WAVES);
        assertEquals(0, world.synergyMask);
        assertEquals(0f, world.synergyToast, 0f);
        assertEquals(-1, world.synergyToastIndex);
        assertEquals(0, world.synergyUnlockCount);
    }
}
