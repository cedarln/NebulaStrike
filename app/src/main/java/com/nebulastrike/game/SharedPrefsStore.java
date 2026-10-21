package com.nebulastrike.game;

import android.content.Context;
import android.content.SharedPreferences;

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
        into.codexSeenEnemies = prefs.getInt("codexSeenEnemies", 0);
        into.codexMaxTierSlain = prefs.getInt("codexMaxTierSlain", -1);
        for (int i = 0; i < into.codexBossKills.length; i++) {
            into.codexBossKills[i] = prefs.getInt("codexBossKills" + i, 0);
        }
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
        String prefix = mode == GameMode.ENDLESS ? "elb" : "lb";
        int[] out = new int[5];
        for (int i = 0; i < out.length; i++) out[i] = prefs.getInt(prefix + i, 0);
        return out;
    }

    @Override
    public void saveLeaderboard(GameMode mode, int[] scores) {
        String prefix = mode == GameMode.ENDLESS ? "elb" : "lb";
        SharedPreferences.Editor e = prefs.edit();
        for (int i = 0; i < 5 && i < scores.length; i++) e.putInt(prefix + i, scores[i]);
        e.apply();
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
                .putInt("codexSeenEnemies", s.codexSeenEnemies)
                .putInt("codexMaxTierSlain", s.codexMaxTierSlain);
        for (int i = 0; i < s.codexBossKills.length; i++) {
            e.putInt("codexBossKills" + i, s.codexBossKills[i]);
        }
        e.apply();
    }
}
