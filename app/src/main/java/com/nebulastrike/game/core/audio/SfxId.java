package com.nebulastrike.game.core.audio;

/** Every one-shot effect produced by {@link SfxBank}. */
public enum SfxId {
    SHOOT_VULCAN,
    SHOOT_SPREAD,
    SHOOT_LASER,
    SHOOT_MISSILE,
    WINGMAN,
    EXPLODE,
    EXPLODE_HEAVY,
    PLAYER_HIT,
    SHIELD_BREAK,
    BOMB,
    POWERUP,
    EXTRA_LIFE,
    COMBO,
    GRAZE,
    OVERDRIVE,
    ALARM,
    BOSS_PHASE,
    BOSS_DOWN,
    BOSS_LASER,
    PART_BREAK,
    GAME_OVER,
    UI,
    WAVE,
    ESCAPE,
    /** v1.9：相位冲刺、连锁闪电、武器融合、Boss 横冲、治疗脉冲、轨道球、突袭奖励 */
    DASH,
    CHAIN_ZAP,
    FUSION,
    BOSS_RAM,
    HEAL,
    ORBIT,
    BONUS,
    /** v1.10：弹跳弹、新星冲击、部署炮台、Boss 遗物、重生护盾、水雷、护盾格挡 */
    SHOOT_BOUNCE,
    NOVA,
    TURRET_DEPLOY,
    RELIC,
    RESPAWN,
    MINE,
    SHIELD_BLOCK,
    /** v1.11：暴击金属点缀音 */
    CRIT,
    /** v1.12：点掉敌弹的脆裂声（code==1 时为水雷被点爆，音高更低） */
    BULLET_POP,
    /** v1.13：冲刺弹反的"铛"金属上扬音（连击越高音越高） */
    PARRY,
    /** v1.14：异变波开始的错拍上扫警报 */
    MUTATOR,
    /** v1.14：异变平息奖励的三音上行铃 */
    MUTATOR_CLEAR
}
