package com.nebulastrike.game.core;

/** Enemy archetypes. Values are tuned for the 1600x900 virtual space. */
public enum EnemyType {
    /** Falls straight down, 1 HP, small. */
    DRONE(26f, 1, 230f, 10, 1),
    /** Sine-wave descent, 1 HP, dodges more. */
    WEAVER(30f, 1, 190f, 15, 2),
    /** Slow brick, 4 HP, worth more. Unlocked in iteration 2. */
    TANK(48f, 4, 120f, 30, 0),
    /** Zig-zag hunter, 2 HP. Unlocked in iteration 2. */
    HUNTER(30f, 2, 330f, 25, 3);

    public final float radius;
    public final int hp;
    public final float baseSpeed;
    public final int score;
    /** Palette index used by the renderer (0 cyan, 1 green, 2 orange, 3 magenta). */
    public final int colorHint;

    EnemyType(float radius, int hp, float baseSpeed, int score, int colorHint) {
        this.radius = radius;
        this.hp = hp;
        this.baseSpeed = baseSpeed;
        this.score = score;
        this.colorHint = colorHint;
    }
}
