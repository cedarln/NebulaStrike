package com.nebulastrike.game.core;

/** Persistence boundary for best score, settings and the local top-5 leaderboard. */
public interface SettingsStore {
    void load(Settings into);

    int bestScore();

    void saveBestScore(int score);

    /** @return the persisted top-5 scores for the mode, descending; 0 = empty slot. Never null. */
    int[] leaderboard(GameMode mode);

    void saveLeaderboard(GameMode mode, int[] scores);

    void save(Settings settings);
}
