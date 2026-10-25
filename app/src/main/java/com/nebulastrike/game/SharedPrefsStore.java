package com.nebulastrike.game;

import android.content.Context;
import android.content.SharedPreferences;

import com.nebulastrike.game.core.Difficulty;
import com.nebulastrike.game.core.GameMode;
import com.nebulastrike.game.core.Settings;
import com.nebulastrike.game.core.SettingsStore;

/** SharedPreferences-backed implementation of the persistence boundary. */
public class SharedPrefsStore implements SettingsStore {
    private static final String PREFS = "nebulastrike";
    private final SharedPreferences prefs;

    public SharedPrefsStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override
    public void load(Settings into) {
        into.sound = prefs.getBoolean("sound", true);
        into.particles = prefs.getBoolean("particles", true);
        into.haptics = prefs.getBoolean("haptics", true);
        into.reducedFx = prefs.getBoolean("reducedFx", false);
        into.leftHanded = prefs.getBoolean("leftHanded", false);
        into.startLives = prefs.getInt("startLives", 3);
        into.dropRatePct = prefs.getInt("dropRatePct", 100);
        into.enemySpeedPct = prefs.getInt("enemySpeedPct", 100);
        into.startPower = prefs.getInt("startPower", 1);
        into.godMode = prefs.getBoolean("godMode", false);
        into.setDifficulty(Difficulty.byOrdinal(prefs.getInt("difficulty", Difficulty.STANDARD.ordinal())));
        into.codexSeenEnemies = prefs.getInt("codexSeenEnemies", 0);
        into.codexMaxTierSlain = prefs.getInt("codexMaxTierSlain", -1);
        for (int i = 0; i < into.codexBossKills.length; i++) {
            into.codexBossKills[i] = prefs.getInt("codexBossKills" + i, 0);
        }
        into.achieveMask = prefs.getLong("achieveMask", 0L);
        for (int i = 0; i < into.achieveBest.length; i++) {
            into.achieveBest[i] = prefs.getInt("achieveBest" + i, 0);
            into.achieveTotal[i] = prefs.getInt("achieveTotal" + i, 0);
        }
        for (int i = 0; i < into.dailyKeys.length; i++) {
            into.dailyKeys[i] = prefs.getInt("dailyKey" + i, 0);
            into.dailyScores[i] = prefs.getInt("dailyScore" + i, 0);
        }
        into.rushBest = prefs.getInt("rushBest", 0);
        into.runsStarted = prefs.getInt("runsStarted", 0);
        into.deaths = prefs.getInt("deaths", 0);
        into.bestWave = prefs.getInt("bestWave", 0);
        into.storyUnlocked = prefs.getInt("storyUnlocked", 1);
        into.campaignCleared = prefs.getBoolean("campaignCleared", false);
        into.sanitize();
    }

    @Override
    public int bestScore() {
        return prefs.getInt("bestScore", 0);
    }

    @Override
    public void saveBestScore(int score) {
        prefs.edit().putInt("bestScore", score).apply();
    }

    @Override
    public int[] leaderboard(GameMode mode) {
        if (mode == GameMode.DAILY) return new int[5];   // 日常走日历，不占分数榜
        String prefix = leaderboardPrefix(mode);
        int[] out = new int[5];
        for (int i = 0; i < out.length; i++) out[i] = prefs.getInt(prefix + i, 0);
        return out;
    }

    @Override
    public void saveLeaderboard(GameMode mode, int[] scores) {
        if (mode == GameMode.DAILY) return;
        String prefix = leaderboardPrefix(mode);
        SharedPreferences.Editor e = prefs.edit();
        for (int i = 0; i < 5 && i < scores.length; i++) e.putInt(prefix + i, scores[i]);
        e.apply();
    }

    /** 每种模式一张独立的榜：WAVES=lb，ENDLESS=elb，BOSS RUSH=brb。 */
    private static String leaderboardPrefix(GameMode mode) {
        switch (mode) {
            case ENDLESS: return "elb";
            case RUSH: return "brb";
            default: return "lb";
        }
    }

    @Override
    public void save(Settings s) {
        SharedPreferences.Editor e = prefs.edit()
                .putBoolean("sound", s.sound)
                .putBoolean("particles", s.particles)
                .putBoolean("haptics", s.haptics)
                .putBoolean("reducedFx", s.reducedFx)
                .putBoolean("leftHanded", s.leftHanded)
                .putInt("startLives", s.startLives)
                .putInt("dropRatePct", s.dropRatePct)
                .putInt("enemySpeedPct", s.enemySpeedPct)
                .putInt("startPower", s.startPower)
                .putBoolean("godMode", s.godMode)
                .putInt("difficulty", (s.difficulty == null
                        ? Difficulty.STANDARD : s.difficulty).ordinal())
                .putInt("codexSeenEnemies", s.codexSeenEnemies)
                .putInt("codexMaxTierSlain", s.codexMaxTierSlain)
                .putLong("achieveMask", s.achieveMask);
        for (int i = 0; i < s.codexBossKills.length; i++) {
            e.putInt("codexBossKills" + i, s.codexBossKills[i]);
        }
        for (int i = 0; i < s.achieveBest.length; i++) {
            e.putInt("achieveBest" + i, s.achieveBest[i]);
            e.putInt("achieveTotal" + i, s.achieveTotal[i]);
        }
        for (int i = 0; i < s.dailyKeys.length; i++) {
            e.putInt("dailyKey" + i, s.dailyKeys[i]);
            e.putInt("dailyScore" + i, s.dailyScores[i]);
        }
        e.putInt("rushBest", s.rushBest)
                .putInt("runsStarted", s.runsStarted)
                .putInt("deaths", s.deaths)
                .putInt("bestWave", s.bestWave)
                .putInt("storyUnlocked", s.storyUnlocked)
                .putBoolean("campaignCleared", s.campaignCleared);
        e.apply();
    }
}
