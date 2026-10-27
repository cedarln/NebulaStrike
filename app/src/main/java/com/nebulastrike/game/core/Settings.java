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

    // ---- 每日挑战（v1.19）：最近 N 天的"日期键 → 当日最佳"日历 ----
    /** 日期键（yyyyMMdd），{@code dailyKeys[0]} 是最近一天；0 表示空槽。 */
    public final int[] dailyKeys = new int[DailyChallenge.HISTORY_SLOTS];
    /** 与 {@link #dailyKeys} 一一对应的当日最佳分。 */
    public final int[] dailyScores = new int[DailyChallenge.HISTORY_SLOTS];

    // ---- BOSS RUSH（v1.20）：单局深入的最多 Boss 数 ----
    /** 一次 BOSS RUSH 里连续击败的 Boss 数量纪录；0 = 还没打过。 */
    public volatile int rushBest;

    // ---- 生涯账本（v1.30）：面板用的出勤 / 阵亡 / 最深波次 ----
    /** 开局次数（含中途退出）。 */
    public volatile int runsStarted;
    /** 阵亡次数（最后一命掉光才算一次）。 */
    public volatile int deaths;
    /** 到达过的最深波次。 */
    public volatile int bestWave;

    // ---- 主线档案（v1.32）：已解密章数（1 起，1 = 只看到序章） ----
    public volatile int storyUnlocked = 1;
    /** v1.40：是否已经打穿过八段航程（结局档案永久保留）。 */
    public volatile boolean campaignCleared;

    // ---- 回声周目（v1.49）：通关主线后解锁的 NG+ ----
    /** 回声碎片：每一场回声 Boss 掉一枚，是后日谈档案的解锁货币。只增不减。 */
    public volatile int echoShards;
    /** 回声周目通关次数。 */
    public volatile int echoClears;

    /** 是否已经可以进入回声周目（主线通关即解锁）。 */
    public boolean echoUnlocked() {
        return campaignCleared;
    }

    /**
     * 解密到第 n 章。档案只前进不回收：重开一局、换模式都不会把已经读过的章节重新锁上。
     *
     * @return true 表示本次确实解开了新章节（调用方据此落盘与弹提示）
     */
    public boolean noteStory(int chapters) {
        int want = clamp(chapters, 1, Story.CHAPTER_COUNT);
        if (want <= storyUnlocked) return false;
        storyUnlocked = want;
        return true;
    }

    /** 开新局记一次出勤。 */
    public void countRunStart() {
        if (runsStarted < Integer.MAX_VALUE) runsStarted++;
    }

    /** 最后一命掉光时记一次阵亡。 */
    public void countDeath() {
        if (deaths < Integer.MAX_VALUE) deaths++;
    }

    /** @return true 表示刷新了最深波次（调用方据此标记落盘）。 */
    public boolean noteWave(int wave) {
        if (wave <= bestWave) return false;
        bestWave = wave;
        return true;
    }

    /** 某天的历史最佳分；没打过返回 0。 */
    public int dailyBest(int dateKey) {
        for (int i = 0; i < dailyKeys.length; i++) {
            if (dailyKeys[i] == dateKey) return dailyScores[i];
        }
        return 0;
    }

    /**
     * 记录一次日常成绩：同一天只涨不跌，新的一天插到最前面并把同分那天挤掉。
     *
     * @return true 表示刷新了今天的最佳
     */
    public boolean recordDaily(int dateKey, int score) {
        if (dateKey <= 0 || score <= 0) return false;
        for (int i = 0; i < dailyKeys.length; i++) {
            if (dailyKeys[i] != dateKey) continue;
            if (score <= dailyScores[i]) return false;
            dailyScores[i] = score;
            return true;
        }
        for (int i = dailyKeys.length - 1; i > 0; i--) {
            dailyKeys[i] = dailyKeys[i - 1];
            dailyScores[i] = dailyScores[i - 1];
        }
        dailyKeys[0] = dateKey;
        dailyScores[0] = score;
        return true;
    }

    /** 已打卡天数。 */
    public int dailyPlayed() {
        int n = 0;
        for (int key : dailyKeys) if (key > 0) n++;
        return n;
    }

    /**
     * 连续打卡天数（从今天往回数）。中间断一天就重新计数——
     * 这是唯一的"惩罚"，但断掉的只是连胜，历史分数一条都不会丢。
     */
    public int dailyStreak(int todayKey) {
        int streak = 0;
        int want = todayKey;
        for (int i = 0; i < dailyKeys.length && dailyKeys[i] > 0; i++) {
            if (dailyKeys[i] == want) {
                streak++;
                want = DailyChallenge.previousKey(want);
            } else if (dailyKeys[i] < want) {
                break;   // 日历是倒序的，遇到更早又不对齐的那天就是断档
            }
        }
        return streak;
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
        // 日常日历：日期键必须像 yyyyMMdd，且月日合法；脏值整槽清空（宁可不显示历史也不能画出 0000-00）
        for (int i = 0; i < dailyKeys.length; i++) {
            if (!validDateKey(dailyKeys[i])) {
                dailyKeys[i] = 0;
            }
            if (dailyScores[i] < 0 || dailyKeys[i] == 0) {
                dailyScores[i] = 0;
            }
        }
        if (rushBest < 0) rushBest = 0;
        if (runsStarted < 0) runsStarted = 0;
        if (deaths < 0) deaths = 0;
        if (bestWave < 0) bestWave = 0;
        if (echoShards < 0) echoShards = 0;
        if (echoClears < 0) echoClears = 0;
        storyUnlocked = clamp(storyUnlocked, 1, Story.CHAPTER_COUNT);
    }

    /** yyyyMMdd 形式的日期键合法性检查：年份合理、月份 1..12、日期 1..31。 */
    private static boolean validDateKey(int key) {
        if (key < 20240101 || key > 99991231) return false;
        int mm = (key / 100) % 100;
        int dd = key % 100;
        return mm >= 1 && mm <= 12 && dd >= 1 && dd <= 31;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
