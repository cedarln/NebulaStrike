package com.nebulastrike.game.core;

/** Persistence boundary for best score + settings (implemented with SharedPreferences on Android). */
public interface SettingsStore {
    void load(Settings into);

    int bestScore();

    void saveBestScore(int score);

    void save(Settings settings);
}
