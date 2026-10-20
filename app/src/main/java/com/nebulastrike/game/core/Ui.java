package com.nebulastrike.game.core;

/**
 * Canvas-drawn UI layout in virtual coordinates. Screens and buttons share these
 * rects so input hit-testing (GameView) and drawing (Renderer) stay in sync.
 */
public final class Ui {
    public static final class Rect {
        public final float x, y, w, h;
        public Rect(float x, float y, float w, float h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
        public boolean contains(float px, float py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
        public float cx() { return x + w / 2f; }
        public float cy() { return y + h / 2f; }
    }

    // menu
    public static final Rect START = new Rect(GameWorld.WIDTH / 2f - 220f, 400f, 440f, 76f);
    public static final Rect START_ENDLESS = new Rect(GameWorld.WIDTH / 2f - 220f, 492f, 440f, 76f);
    public static final Rect SETTINGS = new Rect(GameWorld.WIDTH / 2f - 220f, 584f, 440f, 64f);
    public static final Rect CHECK_UPDATE = new Rect(GameWorld.WIDTH / 2f - 220f, 715f, 440f, 56f);

    // playing: virtual D-pad (held buttons, handled by GameView's press tracking)
    // 十字布局，中心 (170, 1320)；BOMB 为右下角大圆键
    public static final Rect DPAD_UP = new Rect(115f, 1155f, 110f, 110f);
    public static final Rect DPAD_DOWN = new Rect(115f, 1375f, 110f, 110f);
    public static final Rect DPAD_LEFT = new Rect(5f, 1265f, 110f, 110f);
    public static final Rect DPAD_RIGHT = new Rect(225f, 1265f, 110f, 110f);
    public static final Rect BOMB = new Rect(640f, 1270f, 180f, 180f);
    // settings toggles (drawn on the settings panel in both menu and pause)
    public static final Rect TOGGLE_SOUND = new Rect(GameWorld.WIDTH / 2f - 220f, 470f, 440f, 64f);
    public static final Rect TOGGLE_PARTICLES = new Rect(GameWorld.WIDTH / 2f - 220f, 546f, 440f, 64f);
    public static final Rect TOGGLE_HAPTICS = new Rect(GameWorld.WIDTH / 2f - 220f, 622f, 440f, 64f);
    public static final Rect TOGGLE_REDUCED_FX = new Rect(GameWorld.WIDTH / 2f - 220f, 698f, 440f, 64f);
    public static final Rect SETTINGS_BACK = new Rect(GameWorld.WIDTH / 2f - 140f, 786f, 280f, 64f);
    // pause overlay
    public static final Rect RESUME = new Rect(GameWorld.WIDTH / 2f - 220f, 330f, 440f, 88f);
    public static final Rect RESTART = new Rect(GameWorld.WIDTH / 2f - 220f, 436f, 440f, 72f);
    public static final Rect QUIT_TO_MENU = new Rect(GameWorld.WIDTH / 2f - 220f, 526f, 440f, 72f);
    // in-game pause button (top right, left of lives)
    public static final Rect PAUSE_BTN = new Rect(GameWorld.WIDTH - 340f, 18f, 64f, 64f);

    /** Logical UI actions produced by taps. */
    public enum Action {
        START, START_ENDLESS, OPEN_SETTINGS, TOGGLE_SOUND, TOGGLE_PARTICLES, TOGGLE_HAPTICS,
        TOGGLE_REDUCED_FX, SETTINGS_BACK, RESUME, RESTART, QUIT_TO_MENU, PAUSE, CHECK_UPDATE
    }

    /** Maps a tap in virtual space to an action for the given state; null if none. */
    public static Action hit(GameWorld.State state, boolean settingsOpen, float x, float y) {
        switch (state) {
            case MENU:
                if (settingsOpen) {
                    if (TOGGLE_SOUND.contains(x, y)) return Action.TOGGLE_SOUND;
                    if (TOGGLE_PARTICLES.contains(x, y)) return Action.TOGGLE_PARTICLES;
                    if (TOGGLE_HAPTICS.contains(x, y)) return Action.TOGGLE_HAPTICS;
                    if (TOGGLE_REDUCED_FX.contains(x, y)) return Action.TOGGLE_REDUCED_FX;
                    if (SETTINGS_BACK.contains(x, y)) return Action.SETTINGS_BACK;
                    return null;
                }
                if (START.contains(x, y)) return Action.START;
                if (START_ENDLESS.contains(x, y)) return Action.START_ENDLESS;
                if (SETTINGS.contains(x, y)) return Action.OPEN_SETTINGS;
                if (CHECK_UPDATE.contains(x, y)) return Action.CHECK_UPDATE;
                return null;
            case PAUSED:
                if (RESUME.contains(x, y)) return Action.RESUME;
                if (RESTART.contains(x, y)) return Action.RESTART;
                if (QUIT_TO_MENU.contains(x, y)) return Action.QUIT_TO_MENU;
                return null;
            case PLAYING:
                if (PAUSE_BTN.contains(x, y)) return Action.PAUSE;
                return null;
            default:
                return null; // GAME_OVER: whole-screen tap handled by caller
        }
    }
}
