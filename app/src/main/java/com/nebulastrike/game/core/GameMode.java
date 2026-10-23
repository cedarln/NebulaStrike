package com.nebulastrike.game.core;

/**
 * Play modes. WAVES is the tuned campaign ramp; ENDLESS applies harsher,
 * uncapped difficulty scaling with more frequent bosses for score-chasing;
 * DAILY is the date-seeded challenge run (same rules for everyone, every day);
 * RUSH is the boss-only gauntlet with its own leaderboard.
 */
public enum GameMode {
    WAVES, ENDLESS, DAILY, RUSH
}
