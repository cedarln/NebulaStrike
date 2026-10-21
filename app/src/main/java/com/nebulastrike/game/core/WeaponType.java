package com.nebulastrike.game.core;

/**
 * Player weapon archetypes. VULCAN is the default; SPREAD/LASER/MISSILE come from powerups.
 * 参考《雷电》双武器体系：VULCAN/SPREAD/LASER 走直射弹道，MISSILE 走追踪。
 * 弹道数量由 Ship.powerLevel（1..3）扩展，见 GameWorld.fireWeapon。
 *
 * LANCE/CLUSTER/SWARM 为"融合武器"：同时装备对应的两种基础武器时自动解锁，
 * 不掉落、不替换原有武器，只作为额外一路火力叠加（见 GameWorld.applyFusion）。
 */
public enum WeaponType {
    VULCAN(0.16f, 1),
    SPREAD(0.20f, 1),
    LASER(0.09f, 1),   // 高射速窄束，穿透敌机（命中后子弹继续飞行）
    MISSILE(0.34f, 2),
    /** SPREAD + LASER：宽穿透长枪，单发高伤。 */
    LANCE(0.30f, 5),
    /** SPREAD + MISSILE：集束弹，命中后炸开破片。 */
    CLUSTER(0.32f, 2),
    /** LASER + MISSILE：蜂群穿刺束，自动追踪。 */
    SWARM(0.13f, 1);

    public final float cooldown;
    public final int damage;

    WeaponType(float cooldown, int damage) {
        this.cooldown = cooldown;
        this.damage = damage;
    }

    /** 融合武器不能由道具直接给予，只能由两种基础武器同时装备触发。 */
    public boolean isFused() {
        return this == LANCE || this == CLUSTER || this == SWARM;
    }

    /** 该融合武器需要的两种基础武器；非融合武器返回 null。 */
    public WeaponType fusionA() {
        switch (this) {
            case LANCE: return SPREAD;
            case CLUSTER: return SPREAD;
            case SWARM: return LASER;
            default: return null;
        }
    }

    public WeaponType fusionB() {
        switch (this) {
            case LANCE: return LASER;
            case CLUSTER: return MISSILE;
            case SWARM: return MISSILE;
            default: return null;
        }
    }

    /** 展示名（HUD/图鉴用）。 */
    public String label() {
        switch (this) {
            case SPREAD: return "SPREAD";
            case LASER: return "LASER";
            case MISSILE: return "MISSILE";
            case LANCE: return "LANCE";
            case CLUSTER: return "CLUSTER";
            case SWARM: return "SWARM";
            default: return "VULCAN";
        }
    }
}
