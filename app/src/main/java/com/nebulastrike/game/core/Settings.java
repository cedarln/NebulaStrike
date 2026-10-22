package com.nebulastrike.game.core;

/** Player preferences, persisted by a {@link SettingsStore}. Written on the UI thread, read on the game thread. */
public class Settings {
    public volatile boolean sound = true;
    public volatile boolean particles = true;
    public volatile boolean haptics = true;
    /** force reduced particle emission (for low-end devices); off = auto quality */
    public volatile boolean reducedFx = false;
    /** mirror the bomb button to the bottom-left corner */
    public volatile boolean leftHanded = false;
    /** 难度档位：影响掉率、重生保护、坠机惩罚、敌速、精英率与得分倍率。 */
    public volatile Difficulty difficulty = Difficulty.STANDARD;

    // ---- LAB（金手指）：只影响新开的对局，开局时读取 ----
    public static final int LIVES_MIN = 1, LIVES_MAX = 9;
    public static final int DROP_MIN = 50, DROP_MAX = 300, DROP_STEP = 25;
    public static final int SPEED_MIN = 50, SPEED_MAX = 200, SPEED_STEP = 10;
    public static final int POWER_MIN = 1, POWER_MAX = 3;
    public volatile int startLives = 3;
    public volatile int dropRatePct = 100;
    public volatile int enemySpeedPct = 100;
    public volatile int startPower = 1;
    public volatile boolean godMode;

    // ---- 图鉴（Codex）：跨对局累计的解锁进度 ----
    /** 敌机登场即解锁，按 EnemyType.ordinal() 存位。 */
    public volatile int codexSeenEnemies;
    /** 每个舰级（tier 0..3）累计击毁次数。 */
    public final int[] codexBossKills = new int[4];
    /** 已击毁的最高舰级；-1 = 尚未击毁任何 Boss。 */
    public volatile int codexMaxTierSlain = -1;

    // ---- 成就（v1.18）：跨对局累计，只增不减 ----
    /** 已解锁成就位图（第 n 位 = {@code Achievement.values()[n]}）。 */
    public volatile long achieveMask;
    /** 每种统计口径的历史最佳（单局成就按它判定）。 */
    public final int[] achieveBest = new int[Achievement.Metric.COUNT];
    /** 每种统计口径的生涯累计（生涯成就按它判定）。 */
    public final int[] achieveTotal = new int[Achievement.Metric.COUNT];

    public boolean achievementUnlocked(Achievement a) {
        return a != null && a.unlockedIn(achieveMask);
    }

    public int achievementsUnlocked() {
        return Long.bitCount(achieveMask);
    }

    /** 按解锁条数换算的军衔称号（菜单与成就页共用）。 */
    public String achievementRank() {
        return Achievement.rank(achievementsUnlocked());
    }

    public boolean enemySeen(EnemyType type) {
        return (codexSeenEnemies & (1 << type.ordinal())) != 0;
    }

    public boolean bossUnlocked(int tier) {
        return tier >= 0 && tier < codexBossKills.length && codexBossKills[tier] > 0;
    }

    public int codexCompletion() {
        int seen = 0;
        for (EnemyType t : EnemyType.values()) if (enemySeen(t)) seen++;
        int slain = 0;
        for (int i = 0; i < codexBossKills.length; i++) if (bossUnlocked(i)) slain++;
        return seen + slain;
    }

    public int codexTotal() {
        return EnemyType.values().length + codexBossKills.length;
    }

    public void toggle(String key) {
        switch (key) {
            case "sound": sound = !sound; break;
            case "particles": particles = !particles; break;
            case "haptics": haptics = !haptics; break;
            case "reducedFx": reducedFx = !reducedFx; break;
            case "leftHanded": leftHanded = !leftHanded; break;
        }
    }

    public void adjust(String key, int delta) {
        switch (key) {
            case "lives": startLives = clamp(startLives + delta, LIVES_MIN, LIVES_MAX); break;
            case "drop": dropRatePct = clamp(dropRatePct + delta * DROP_STEP, DROP_MIN, DROP_MAX); break;
            case "speed": enemySpeedPct = clamp(enemySpeedPct + delta * SPEED_STEP, SPEED_MIN, SPEED_MAX); break;
            case "power": startPower = clamp(startPower + delta, POWER_MIN, POWER_MAX); break;
            case "difficulty": {
                Difficulty[] all = Difficulty.values();
                int next = (difficulty.ordinal() + delta) % all.length;
                if (next < 0) next += all.length;
                difficulty = all[next];
                break;
            }
        }
    }

    /** 直接指定难度档位（设置面板的分段按钮用）。 */
    public void setDifficulty(Difficulty d) {
        difficulty = d != null ? d : Difficulty.STANDARD;
    }

    public void resetCheats() {
        startLives = 3;
        dropRatePct = 100;
        enemySpeedPct = 100;
        startPower = 1;
        godMode = false;
    }

    public boolean cheatsActive() {
        return startLives != 3 || dropRatePct != 100 || enemySpeedPct != 100
                || startPower != 1 || godMode;
    }

    /** Clamps values loaded from disk so a corrupted prefs file cannot break the sim. */
    public void sanitize() {
        startLives = clamp(startLives, LIVES_MIN, LIVES_MAX);
        dropRatePct = clamp(dropRatePct, DROP_MIN, DROP_MAX);
        enemySpeedPct = clamp(enemySpeedPct, SPEED_MIN, SPEED_MAX);
        startPower = clamp(startPower, POWER_MIN, POWER_MAX);
        if (difficulty == null) difficulty = Difficulty.STANDARD;
        codexSeenEnemies &= (1 << EnemyType.values().length) - 1;
        codexMaxTierSlain = clamp(codexMaxTierSlain, -1, codexBossKills.length - 1);
        for (int i = 0; i < codexBossKills.length; i++) {
            if (codexBossKills[i] < 0) codexBossKills[i] = 0;
        }
        // 成就：位图掐掉未定义的位（枚举以后增删也不会读到老档的脏位），账本只认非负数
        achieveMask &= (1L << Achievement.COUNT) - 1L;
        for (int i = 0; i < achieveBest.length; i++) {
            if (achieveBest[i] < 0) achieveBest[i] = 0;
        }
        for (int i = 0; i < achieveTotal.length; i++) {
            if (achieveTotal[i] < 0) achieveTotal[i] = 0;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
