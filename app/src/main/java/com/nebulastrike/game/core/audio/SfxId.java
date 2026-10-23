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
    MUTATOR_CLEAR,
    /** v1.15：重力井登场（下吸式低频漩涡） */
    GRAVITY,
    /** v1.15：吞掉一颗敌弹（库存越多音越高） */
    GRAVITY_ABSORB,
    /** v1.15：重力井喷还（由抑到扬的爆开声） */
    GRAVITY_BURST,
    /** v1.16：终极槽充满的上扬提示铃（一局内每次攒满只响一次） */
    ULT_READY,
    /** v1.16：释放终极技（低频塌缩 + 破空爆音） */
    ULT_CAST,
    /** v1.16：光矛齐射的破空连响（档位越高越密） */
    ULT_LANCE,
    /** v1.17：伪装者脱壳现形的"露馅"音（音头做脏，区别于干净的拾取音） */
    MIRAGE_REVEAL,
    /** v1.17：磁暴艇 EMP 脉冲（低频塌缩 + 电离嘶声） */
    EMP_BURST,
    /** v1.17：蜂后产卵的三声虫鸣颤音 */
    BROOD_CHIRP,
    /** v1.18：成就解锁的三音号角（比攒满铃更宽，末尾挂高八度余韵） */
    ACHIEVEMENT,
    /** v1.19：每日挑战开局的双音钟（先低后高，像"今天开始了"的盖章） */
    DAILY_START,
    /** v1.20：BOSS RUSH 开局（低音铜管上行 + 定音鼓重击，擂台开场的感觉） */
    BOSS_RUSH,
    /** v1.21：冲撞者撞停的闷响（低频塌陷 + 碎石噪声，提示"现在是双倍伤害窗口"） */
    RAM_IMPACT,
    /** v1.22：相位者切换相位的空灵扫频（code 1 下沉，code 0 上浮） */
    PHASE_SHIFT,
    /** v1.23：反射护罩弹回敌弹的金属回旋音（code 1 表示还有就绪层，音色更亮） */
    REFLECT,
    /** v1.24：电弧炮连锁放电的电流噼啪声（连锁目标越多音越高） */
    ARC_ZAP,
    /** v1.25：Boss 弹幕网展开的低频电网嗡鸣 */
    BOSS_NET,
    /** v1.25：Boss 召唤棱镜的晶体琶音（召唤两枚时音更亮） */
    BOSS_PRISM,
    /** v1.26：祭坛降临的低频石鸣 + 空灵泛音 */
    ALTAR_SPAWN,
    /** v1.26：祭坛吞掉一颗敌弹的短促"啵"（充能越高音越高） */
    ALTAR_ABSORB,
    /** v1.26：祭坛赐福爆发的四音上行圣咏 */
    ALTAR_BLESS,
    /** v1.27：赏金目标发布的号角（两音上行 + 定音鼓） */
    BOUNTY_START,
    /** v1.27：赏金兑现的金币铃（连杀越高音越亮） */
    BOUNTY_CLAIM,
    /** v1.27：赏金目标脱逃的低沉收尾（不刺耳，只报个信） */
    BOUNTY_FAIL,
    /** v1.28：点亮一组道具连携的双音"接通"铃 */
    SYNERGY_ON,
    /** v1.29：相位核心的冲刺冲击波（低频轰 + 快速上扫的气浪） */
    PHASE_BURST
}
