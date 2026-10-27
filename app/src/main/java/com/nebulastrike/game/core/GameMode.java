package com.nebulastrike.game.core;

/**
 * Play modes. WAVES is the tuned campaign ramp; ENDLESS applies harsher,
 * uncapped difficulty scaling with more frequent bosses for score-chasing;
 * DAILY is the date-seeded challenge run (same rules for everyone, every day);
 * RUSH is the boss-only gauntlet with its own leaderboard.
 * ECHO is the new-game-plus loop unlocked by clearing the campaign: the same
 * eight sectors, tougher echoes of every enemy, a 1.5x score scale and its own board.
 */
public enum GameMode {
    WAVES, ENDLESS, DAILY, RUSH, ECHO
}
