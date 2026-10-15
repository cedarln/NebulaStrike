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
    public void save(Settings s) {
        prefs.edit()
                .putBoolean("sound", s.sound)
                .putBoolean("particles", s.particles)
                .putBoolean("haptics", s.haptics)
                .apply();
    }
}
