package com.nebulastrike.game.core;

/**
 * Player weapon archetypes. VULCAN is the default; others come from powerups.
 * 参考《雷电》双武器体系：VULCAN/SPREAD/LASER 走直射弹道，MISSILE 走追踪。
 * 弹道数量由 Ship.powerLevel（1..3）扩展，见 GameWorld.fireBullet。
 */
public enum WeaponType {
    VULCAN(0.16f, 1),
    SPREAD(0.20f, 1),
    LASER(0.09f, 1),   // 高射速窄束，穿透敌机（命中后子弹继续飞行）
    MISSILE(0.34f, 2);

    public final float cooldown;
    public final int damage;

    WeaponType(float cooldown, int damage) {
        this.cooldown = cooldown;
        this.damage = damage;
    }
}
