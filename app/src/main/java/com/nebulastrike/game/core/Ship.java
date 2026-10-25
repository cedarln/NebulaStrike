package com.nebulastrike.game.core;

/**
 * Player ship. Pure simulation object: no Android imports so it is unit-testable.
 * Coordinates are in virtual units: the game runs in a fixed 900x1600 portrait space.
 * 移动为"相对拖动"模式：app 层把手指相对按下锚点的位移换算成目标点，
 * 飞船以限速平滑跟随（MOBA/射击常见的浮动摇杆手感）。
 */
public class Ship {
    public static final float RADIUS = 34f;
    /** 拖动跟随的最高速度：老版本 950 跟不上快速滑动，手感发飘。 */
    public static final float MAX_DRAG_SPEED = 2400f;
    public static final float FIRE_COOLDOWN = 0.16f;
    public static final float RESPAWN_INVULN = 2.0f;

    public float x, y;
    /** 上一帧位置（扫掠碰撞用，避免高速拖动穿过敌弹）。 */
    public float prevX, prevY;
    private float targetX, targetY;
    public boolean invulnerable;
    private float invulnTimer;

    // loadout state
    /** 当前主武器（最近拾取的特殊武器；没有特殊武器时为 VULCAN）。 */
    public WeaponType weapon = WeaponType.VULCAN;
    /** 主武器剩余时间（镜像 weaponTimers[weapon]；兼容旧代码/渲染）。 */
    public float weaponTimer;
    public static final float WEAPON_DURATION = 12f;
    /** 同类武器重复拾取可叠加的层数上限（每层增加弹道密度）。 */
    public static final int MAX_WEAPON_STACK = 3;
    /** 每种特殊武器的剩余时间与叠加层数；VULCAN 槽位恒为 0。 */
    public final float[] weaponTimers = new float[WeaponType.values().length];
    public final int[] weaponStacks = new int[WeaponType.values().length];
    /**
     * 火力等级 1..3（雷电式）：扩展弹道数量，永久生效，坠机降 1 级。
     * POWER 道具 +1（满级 300 分折算），见 GameWorld.collect/fireWeapon。
     */
    public int powerLevel = 1;
    public static final int MAX_POWER = 3;
    public boolean hasShield;
    /** 护盾层数（每层挡一次伤害）。 */
    public int shieldLayers;
    public static final int MAX_SHIELD_LAYERS = 2;
    /** 护盾层数上限：圣物 AEGIS_FIELD 会把它抬到 3。 */
    public int shieldCap = MAX_SHIELD_LAYERS;
    /**
     * v1.23 反射护罩层数：每层在充能就绪时把一发敌弹弹回成自己的火力，
     * 层数不消耗，只进入 6 秒充能——所以它是<b>叠加的被动</b>，不是一次性的盾。
     */
    public int reflectLayers;
    public static final int MAX_REFLECT_LAYERS = 2;
    public static final float REFLECT_RECHARGE = 6f;
    /** 镜盾连携的充能倍率：反射 + 护盾同时在线时充得更快。 */
    public static final float MIRROR_RECHARGE_MULT = 1.6f;
    /** 每层反射护罩的充能余量（0 = 就绪）。 */
    public final float[] reflectCd = new float[MAX_REFLECT_LAYERS];
    /** 反射充能倍率（由 GameWorld 按连携每帧刷新）。 */
    public float reflectRechargeScale = 1f;
    public int bombs = 1;
    public static final int MAX_BOMBS = 3;
    /** 速射道具剩余时间（>0 时射击冷却减半） */
    public float rapidTimer;
    public static final float RAPID_DURATION = 8f;
    /** 僚机道具剩余时间（>0 时两翼无人机自动开火） */
    public float wingmanTimer;
    public static final float WINGMAN_DURATION = 14f;
    /** 轨道炮球道具剩余时间（>0 时环绕飞船的能量球清扫敌弹并撞击敌机） */
    public float orbitTimer;
    public static final float ORBIT_DURATION = 10f;
    /** 纳米修复剩余时间：期间每隔一段时间自动补一层护盾。 */
    public float nanoTimer;
    public static final float NANITE_DURATION = 12f;
    /** 纳米修复补盾周期（秒）。 */
    public static final float NANITE_PERIOD = 3.5f;
    public static final int ORBIT_SPHERES = 3;
    public static final float ORBIT_RADIUS = 108f;
    /** 环绕角速度（弧度/秒） */
    public static final float ORBIT_SPEED = 4.2f;
    /** 轨道球当前相位，用于渲染与碰撞。 */
    public float orbitAngle;
    /** 擦弹充满后的过载时间（更快射速 + 火力临时 +1） */
    public float overdriveTimer;
    public static final float OVERDRIVE_DURATION = 5f;
    public static final float OVERDRIVE_FIRE_MULT = 0.55f;
    /** 限时道具重复拾取时最多延长到基础时长的 2 倍。 */
    public static final float MAX_TIMER_MULT = 2f;

    // ---- 相位冲刺（dash）----
    /** 冲刺能量槽上限（浮点，连续充能；HUD 按格显示）。 */
    public static final float DASH_MAX_CHARGE = 2f;
    /** 每次冲刺消耗的能量。 */
    public static final float DASH_COST = 1f;
    /** 充满一格所需秒数。 */
    public static final float DASH_RECHARGE_SECONDS = 2.4f;
    /** 相位位移持续时间与速度（远高于拖动上限）。 */
    public static final float DASH_DURATION = 0.16f;
    public static final float DASH_SPEED = 3400f;
    /** 冲刺结束后仍保留的短暂无敌余韵，避免"停下来就被打"。 */
    public static final float DASH_GRACE = 0.12f;
    /** 冲刺期间在路径上留下的残影（渲染用）。 */
    public final float[] dashTrailX = new float[6];
    public final float[] dashTrailY = new float[6];
    public int dashTrailCount;

    /** v1.34：缚网减速——拖动速度乘这个系数，且期间无法冲刺。 */
    public static final float WEB_SLOW = 0.45f;
    /** 剩余减速时间（秒）；>0 表示正被网缠住。 */
    public float webTimer;

    public float dashCharge = DASH_MAX_CHARGE;
    /** >0 = 正在相位位移。 */
    public float dashTimer;
    /** 冲刺方向（单位向量）。 */
    public float dashDirX, dashDirY = -1f;
    private float dashGrace;
    /** 冲刺充能速率倍率（相位核心圣物抬高，每帧由 GameWorld 重算）。 */
    public float dashRechargeScale = 1f;
    /** 相位核心：冲刺瞬间冲击波的清弹半径与伤害。 */
    public static final float PHASE_CORE_RECHARGE_MULT = 1.7f;
    private float lastMoveX, lastMoveY;
    /** 冲刺完成次数（统计数据）。 */
    public int dashesUsed;

    public void reset(float x, float y) {
        this.x = x;
        this.y = y;
        prevX = x;
        prevY = y;
        targetX = x;
        targetY = y;
        invulnerable = false;
        invulnTimer = 0f;
        weapon = WeaponType.VULCAN;
        weaponTimer = 0f;
        for (int i = 0; i < weaponTimers.length; i++) {
            weaponTimers[i] = 0f;
            weaponStacks[i] = 0;
        }
        powerLevel = 1;
        hasShield = false;
        shieldLayers = 0;
        shieldCap = MAX_SHIELD_LAYERS;
        reflectLayers = 0;
        for (int i = 0; i < reflectCd.length; i++) reflectCd[i] = 0f;
        reflectRechargeScale = 1f;
        bombs = 1;
        rapidTimer = 0f;
        wingmanTimer = 0f;
        overdriveTimer = 0f;
        orbitTimer = 0f;
        orbitAngle = 0f;
        nanoTimer = 0f;
        dashCharge = DASH_MAX_CHARGE;
        webTimer = 0f;
        dashTimer = 0f;
        dashGrace = 0f;
        dashDirX = 0f;
        dashDirY = -1f;
        dashTrailCount = 0;
        dashRechargeScale = 1f;
        lastMoveX = 0f;
        lastMoveY = -1f;
        dashesUsed = 0;
    }

    // ---- 相位冲刺 ----

    /**
     * 朝当前拖动方向（无方向时向上）瞬间相位位移。
     * @return true 表示能量足够并成功触发
     */
    public boolean tryDash() {
        if (webTimer > 0f || dashCharge < DASH_COST || dashTimer > 0f) return false;
        float dx = targetX - x, dy = targetY - y;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < 12f) {
            dx = lastMoveX;
            dy = lastMoveY;
            d = (float) Math.sqrt(dx * dx + dy * dy);
        }
        if (d < 1e-4f) { dx = 0f; dy = -1f; d = 1f; }
        dashDirX = dx / d;
        dashDirY = dy / d;
        dashCharge -= DASH_COST;
        dashTimer = DASH_DURATION;
        dashGrace = DASH_DURATION + DASH_GRACE;
        dashTrailCount = 0;
        dashesUsed++;
        return true;
    }

    /** 撞击/子弹判定用：无敌帧或相位位移中都不会受伤。 */
    public boolean isSafe() {
        return invulnerable || dashTimer > 0f || dashGrace > 0f;
    }

    /** 被减速网缠住：取较长的剩余时间，重复命中不会缩短控制。 */
    public void applyWeb(float seconds) {
        webTimer = Math.max(webTimer, seconds);
    }

    public boolean isWebbed() {
        return webTimer > 0f;
    }

    /** 冲刺能量是否足够（HUD 提示与输入层判断用）。 */
    public boolean canDash() {
        return webTimer <= 0f && dashCharge >= DASH_COST && dashTimer <= 0f;
    }

    /** 环绕轨道球当前位置；未激活时返回 false。 */
    public boolean orbitSphere(int i, float[] out) {
        if (orbitTimer <= 0f) return false;
        float ang = orbitAngle + i * (float) (Math.PI * 2 / ORBIT_SPHERES);
        out[0] = x + (float) Math.cos(ang) * ORBIT_RADIUS;
        out[1] = y + (float) Math.sin(ang) * ORBIT_RADIUS;
        return true;
    }

    /** 拾取特殊武器：不替换已有武器，同类则叠层并刷新持续时间。 */
    public void addWeapon(WeaponType type) {
        if (type == WeaponType.VULCAN) return;
        int i = type.ordinal();
        weaponStacks[i] = Math.min(MAX_WEAPON_STACK, weaponStacks[i] + 1);
        weaponTimers[i] = WEAPON_DURATION;
        weapon = type;
        weaponTimer = WEAPON_DURATION;
    }

    /** 当前是否存在任意特殊武器（含手动指定、尚未计时的状态）。 */
    public boolean hasSpecialWeapon() {
        if (weapon != WeaponType.VULCAN) return true;
        for (int i = 1; i < weaponStacks.length; i++) {
            if (weaponStacks[i] > 0) return true;
        }
        return false;
    }

    /** 该武器本帧是否参与射击。v1.10 起基础炮常驻：捡到任何武器都是"叠加一路火力"，
     *  而不是替换掉原来的炮，杜绝"捡了道具反而变弱"。 */
    public boolean isWeaponEquipped(WeaponType type) {
        if (type == WeaponType.VULCAN) return true;
        return weapon == type || weaponStacks[type.ordinal()] > 0;
    }

    /** 已叠层数；主武器至少按 1 层处理（兼容测试/外部直接指定）。 */
    public int stackOf(WeaponType type) {
        if (type == WeaponType.VULCAN) return 0;
        int s = weaponStacks[type.ordinal()];
        return weapon == type ? Math.max(1, s) : s;
    }

    public float timerOf(WeaponType type) {
        if (type == WeaponType.VULCAN) return 0f;
        int i = type.ordinal();
        if (weapon == type && weaponStacks[i] <= 0) return weaponTimer;
        return weaponTimers[i];
    }

    public void addShield() {
        if (!hasShield) {
            hasShield = true;
            shieldLayers = 1;
        } else if (shieldLayers < shieldCap) {
            shieldLayers++;
        }
    }

    /** @return true if a layer absorbed the hit (hasShield 同步更新) */
    public boolean consumeShield() {
        if (!hasShield) return false;
        shieldLayers = Math.max(0, shieldLayers - 1);
        if (shieldLayers == 0) hasShield = false;
        return true;
    }

    /** 叠加一层反射护罩；满层时返回 false（由调用方折算奖励分）。 */
    public boolean addReflect() {
        if (reflectLayers >= MAX_REFLECT_LAYERS) return false;
        reflectCd[reflectLayers] = 0f;
        reflectLayers++;
        return true;
    }

    /** 就绪中的反射层下标；没有就绪层时返回 -1。 */
    public int readyReflectLayer() {
        for (int i = 0; i < reflectLayers; i++) {
            if (reflectCd[i] <= 0f) return i;
        }
        return -1;
    }

    /** 消耗一次反射（层数保留，只进入充能）；返回是否成功弹反。 */
    public boolean consumeReflect() {
        int i = readyReflectLayer();
        if (i < 0) return false;
        reflectCd[i] = REFLECT_RECHARGE / Math.max(0.01f, reflectRechargeScale);
        return true;
    }

    /** 拖拽模式：设置期望位置（限速跟随）。 */
    public void setTarget(float tx, float ty) {
        targetX = tx;
        targetY = ty;
    }

    /** 暂停/死亡时冻结目标，避免恢复后继续飞向旧目标点。 */
    public void snapTarget() {
        targetX = x;
        targetY = y;
    }

    /** 平滑跟随拖动目标。射击冷却由 GameWorld 按武器分别管理，见 weaponCd[]。 */
    public void update(float dt, float worldW, float worldH) {
        prevX = x;
        prevY = y;
        if (webTimer > 0f) webTimer = Math.max(0f, webTimer - dt);
        if (dashTimer > 0f) {
            // 相位位移：直线高速穿过，落点即新目标，不会被拉回拖动前的位置
            dashTimer -= dt;
            x += dashDirX * DASH_SPEED * dt;
            y += dashDirY * DASH_SPEED * dt;
            if (dashTrailCount < dashTrailX.length) {
                dashTrailX[dashTrailCount] = x;
                dashTrailY[dashTrailCount] = y;
                dashTrailCount++;
            }
            if (dashTimer <= 0f) {
                dashTimer = 0f;
                targetX = x;
                targetY = y;
            }
        } else {
            if (dashGrace > 0f) {
                dashGrace = Math.max(0f, dashGrace - dt);
                if (dashGrace <= 0f) dashTrailCount = 0;
            }
            if (dashCharge < DASH_MAX_CHARGE) {
                dashCharge = Math.min(DASH_MAX_CHARGE,
                        dashCharge + dt * dashRechargeScale / DASH_RECHARGE_SECONDS);
            }
            float dx = targetX - x, dy = targetY - y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist < 1.5f) {
                x = targetX;
                y = targetY;
            } else {
                // 指数逼近给出"贴手"的小位移跟随，速度上限保证快速甩动不瞬移
                float k = 1f - (float) Math.exp(-24f * dt);
                float speedCap = MAX_DRAG_SPEED * (webTimer > 0f ? WEB_SLOW : 1f);
                float step = Math.min(speedCap * dt, dist * k);
                x += dx / dist * step;
                y += dy / dist * step;
                lastMoveX = dx;
                lastMoveY = dy;
            }
        }
        float margin = RADIUS;
        float clampedX = clamp(x, margin, worldW - margin);
        float clampedY = clamp(y, margin, worldH - margin);
        if (clampedX != x || clampedY != y) {
            // 冲刺撞墙：立即停在该边界并把目标对齐，避免方向向量继续指向屏外
            if (dashTimer > 0f) {
                dashTimer = 0f;
                dashGrace = Math.max(dashGrace, 0.05f);
            }
            x = clampedX;
            y = clampedY;
            targetX = clamp(targetX, margin, worldW - margin);
            targetY = clamp(targetY, margin, worldH - margin);
        }
        if (invulnerable) {
            invulnTimer -= dt;
            if (invulnTimer <= 0f) invulnerable = false;
        }
        // 所有特殊武器独立倒计时；主武器耗尽时自动把仍在生效的副武器提升为主武器
        boolean primaryExpired = false;
        for (int i = 1; i < weaponTimers.length; i++) {
            if (weaponTimers[i] <= 0f) continue;
            weaponTimers[i] -= dt;
            if (weaponTimers[i] <= 0f) {
                weaponTimers[i] = 0f;
                weaponStacks[i] = 0;
                if (weapon.ordinal() == i) primaryExpired = true;
            }
        }
        if (weapon != WeaponType.VULCAN) {
            int wi = weapon.ordinal();
            if (weaponStacks[wi] > 0) {
                weaponTimer = weaponTimers[wi];
            } else if (weaponTimer > 0f) {
                // 兼容旧逻辑/外部直接设置的计时器
                weaponTimer -= dt;
                if (weaponTimer <= 0f) {
                    weaponTimer = 0f;
                    primaryExpired = true;
                }
            }
        }
        if (primaryExpired) {
            WeaponType next = WeaponType.VULCAN;
            float best = 0f;
            for (int i = 1; i < weaponTimers.length; i++) {
                if (weaponStacks[i] > 0 && weaponTimers[i] > best) {
                    best = weaponTimers[i];
                    next = WeaponType.values()[i];
                }
            }
            weapon = next;
            weaponTimer = next == WeaponType.VULCAN ? 0f : weaponTimers[next.ordinal()];
        }
        if (rapidTimer > 0f) {
            rapidTimer -= dt;
            if (rapidTimer < 0f) rapidTimer = 0f;
        }
        if (wingmanTimer > 0f) {
            wingmanTimer -= dt;
            if (wingmanTimer < 0f) wingmanTimer = 0f;
        }
        if (orbitTimer > 0f) {
            orbitTimer -= dt;
            orbitAngle += ORBIT_SPEED * dt;
            if (orbitAngle > Math.PI * 2f) orbitAngle -= (float) (Math.PI * 2);
            if (orbitTimer < 0f) orbitTimer = 0f;
        }
        if (nanoTimer > 0f) {
            nanoTimer -= dt;
            if (nanoTimer < 0f) nanoTimer = 0f;
        }
        // 反射护罩：每层独立充能（就绪的那层会在命中时把敌弹弹回去）
        for (int i = 0; i < reflectLayers; i++) {
            if (reflectCd[i] > 0f) {
                reflectCd[i] -= dt;
                if (reflectCd[i] < 0f) reflectCd[i] = 0f;
            }
        }
        if (overdriveTimer > 0f) {
            overdriveTimer -= dt;
            if (overdriveTimer < 0f) overdriveTimer = 0f;
        }
    }

    public void makeInvulnerable() {
        makeInvulnerable(RESPAWN_INVULN);
    }

    /** 按难度指定保护时长（休闲更长、硬核更短），重生流程使用。 */
    public void makeInvulnerable(float seconds) {
        invulnerable = true;
        invulnTimer = Math.max(0f, seconds);
    }

    /** 剩余无敌时间（渲染层画重生保护倒计时气泡用）。 */
    public float invulnRemaining() {
        return invulnerable ? invulnTimer : 0f;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
