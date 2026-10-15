package com.nebulastrike.game.core;

/** Persistence boundary for best score, settings and the local top-5 leaderboard. */
public interface SettingsStore {
    void load(Settings into);

    int bestScore();

    void saveBestScore(int score);

    /** @return the persisted top-5 scores, descending; 0 = empty slot. Never null. */
    int[] leaderboard();

    void saveLeaderboard(int[] scores);

    void save(Settings settings);
}
