package com.nebulastrike.game.core;

/** Player weapon archetypes. VULCAN is the default; others come from powerups. */
public enum WeaponType {
    VULCAN(0.16f, 1),
    SPREAD(0.20f, 1),
    MISSILE(0.34f, 2);

    public final float cooldown;
    public final int damage;

    WeaponType(float cooldown, int damage) {
        this.cooldown = cooldown;
        this.damage = damage;
    }
}
