package com.nebulastrike.game;

import android.content.Context;
import android.content.SharedPreferences;

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
    public int[] leaderboard() {
        int[] out = new int[5];
        for (int i = 0; i < out.length; i++) out[i] = prefs.getInt("lb" + i, 0);
        return out;
    }

    @Override
    public void saveLeaderboard(int[] scores) {
        SharedPreferences.Editor e = prefs.edit();
        for (int i = 0; i < 5 && i < scores.length; i++) e.putInt("lb" + i, scores[i]);
        e.apply();
    }

    @Override
    public void save(Settings s) {
        prefs.edit()
                .putBoolean("sound", s.sound)
                .putBoolean("particles", s.particles)
                .putBoolean("haptics", s.haptics)
                .putBoolean("reducedFx", s.reducedFx)
                .apply();
    }
}
