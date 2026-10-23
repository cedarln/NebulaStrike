package com.nebulastrike.game.core;

/**
 * v1.28 道具连携（SYNERGY）：两套系统同时生效时额外解锁的被动强化。
 * <p>
 * 连携只做"加法"：不替换、不削减任何已有系统的数值，条件一旦不满足就自动失效，
 * 重新凑齐时会再次点亮。判定与文案都集中在这里，方便 HUD、音效与测试共用一套口径。
 */
public enum Synergy {
    /** 反射护罩 + 护盾：挨打的那一下也被拿来反击，而且反得更快更重。 */
    MIRROR("镜盾", "反射充能 ×1.6 · 反射弹伤害 +1"),
    /** 散射 + 火力 ≥2：扇形弹获得穿透，正对密集编队时收益最高。 */
    TRIDENT("三叉戟", "散射弹穿透敌机"),
    /** 僚机 + 轨道球：一近一远的两层护卫同时上线，组成空战编队。 */
    VANGUARD("编队", "僚机射速 +30% · 轨道球伤害 +1"),
    /** 速射 + 擦弹过载：两条射速加成叠乘。 */
    OVERCHARGE("过载链", "射速再 +60%"),
    /** 电弧炮 + 弹跳弹：电流顺着反弹继续传导。 */
    CONDUCTION("导电", "电弧连锁跳跃 +1");

    public final String label;
    /** 一行效果说明（HUD 提示与 README 复用）。 */
    public final String blurb;

    Synergy(String label, String blurb) {
        this.label = label;
        this.blurb = blurb;
    }

    public int bit() {
        return 1 << ordinal();
    }

    /** 当前装配是否满足这一组连携。 */
    public boolean holds(Ship s) {
        switch (this) {
            case MIRROR:
                return s.reflectLayers > 0 && s.hasShield;
            case TRIDENT:
                return s.powerLevel >= 2 && s.isWeaponEquipped(WeaponType.SPREAD);
            case VANGUARD:
                return s.wingmanTimer > 0f && s.orbitTimer > 0f;
            case OVERCHARGE:
                return s.rapidTimer > 0f && s.overdriveTimer > 0f;
            case CONDUCTION:
                return s.isWeaponEquipped(WeaponType.ARC)
                        && s.isWeaponEquipped(WeaponType.BOUNCE);
            default:
                return false;
        }
    }
}
