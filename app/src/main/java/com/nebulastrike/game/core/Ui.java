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
    public static final Rect START = new Rect(GameWorld.WIDTH / 2f - 220f, 372f, 440f, 76f);
    public static final Rect START_ENDLESS = new Rect(GameWorld.WIDTH / 2f - 220f, 462f, 440f, 76f);
    public static final Rect SETTINGS = new Rect(GameWorld.WIDTH / 2f - 220f, 552f, 210f, 64f);
    public static final Rect CHEATS = new Rect(GameWorld.WIDTH / 2f + 10f, 552f, 210f, 64f);
    /** CODEX 与成就入口共用一行（v1.18 成就系统上线后菜单不再有三个整行按钮的空间）。 */
    public static final Rect CODEX = new Rect(GameWorld.WIDTH / 2f - 220f, 632f, 210f, 64f);
    public static final Rect ACHIEVEMENTS = new Rect(GameWorld.WIDTH / 2f + 10f, 632f, 210f, 64f);
    public static final Rect CHECK_UPDATE = new Rect(GameWorld.WIDTH / 2f - 220f, 712f, 440f, 56f);

    // playing: floating joystick is position-free (handled by GameView);
    // the bomb / dash buttons are mirrored for left-handed play
    public static final Rect BOMB_RIGHT = new Rect(640f, 1270f, 180f, 180f);
    public static final Rect BOMB_LEFT = new Rect(80f, 1270f, 180f, 180f);
    /** 冲刺键：贴在炸弹键外侧（左手模式镜像到另一侧）。 */
    public static final Rect DASH_RIGHT = new Rect(640f, 1462f, 118f, 118f);
    public static final Rect DASH_LEFT = new Rect(142f, 1462f, 118f, 118f);
    /**
     * 终极技键：贴在冲刺键外侧，和冲刺排成一行（左手模式镜像）。
     * 满槽才有反应，所以刻意放在最靠边、最难误触的位置。
     */
    public static final Rect ULT_RIGHT = new Rect(770f, 1462f, 118f, 118f);
    public static final Rect ULT_LEFT = new Rect(12f, 1462f, 118f, 118f);

    public static Rect bomb(boolean leftHanded) {
        return leftHanded ? BOMB_LEFT : BOMB_RIGHT;
    }

    public static Rect dash(boolean leftHanded) {
        return leftHanded ? DASH_LEFT : DASH_RIGHT;
    }

    public static Rect ult(boolean leftHanded) {
        return leftHanded ? ULT_LEFT : ULT_RIGHT;
    }

    // settings panel (menu only)
    /** 难度分段选择器：三格并排，放在 SETTINGS 标题与开关行之间。 */
    public static final Rect DIFF_CASUAL = new Rect(GameWorld.WIDTH / 2f - 220f, 400f, 138f, 62f);
    public static final Rect DIFF_STANDARD = new Rect(GameWorld.WIDTH / 2f - 69f, 400f, 138f, 62f);
    public static final Rect DIFF_HARDCORE = new Rect(GameWorld.WIDTH / 2f + 82f, 400f, 138f, 62f);
    public static final Rect TOGGLE_SOUND = new Rect(GameWorld.WIDTH / 2f - 220f, 500f, 440f, 64f);
    public static final Rect TOGGLE_PARTICLES = new Rect(GameWorld.WIDTH / 2f - 220f, 576f, 440f, 64f);
    public static final Rect TOGGLE_HAPTICS = new Rect(GameWorld.WIDTH / 2f - 220f, 652f, 440f, 64f);
    public static final Rect TOGGLE_REDUCED_FX = new Rect(GameWorld.WIDTH / 2f - 220f, 728f, 440f, 64f);
    public static final Rect TOGGLE_LEFT_HAND = new Rect(GameWorld.WIDTH / 2f - 220f, 804f, 440f, 64f);
    public static final Rect SETTINGS_BACK = new Rect(GameWorld.WIDTH / 2f - 140f, 892f, 280f, 64f);
    // LAB（金手指）面板：四行步进器 + 无敌开关 + 恢复默认/返回
    public static final Rect CHEAT_LIVES_MINUS = new Rect(110f, 430f, 80f, 80f);
    public static final Rect CHEAT_LIVES_PLUS = new Rect(710f, 430f, 80f, 80f);
    public static final Rect CHEAT_DROP_MINUS = new Rect(110f, 532f, 80f, 80f);
    public static final Rect CHEAT_DROP_PLUS = new Rect(710f, 532f, 80f, 80f);
    public static final Rect CHEAT_SPEED_MINUS = new Rect(110f, 634f, 80f, 80f);
    public static final Rect CHEAT_SPEED_PLUS = new Rect(710f, 634f, 80f, 80f);
    public static final Rect CHEAT_POWER_MINUS = new Rect(110f, 736f, 80f, 80f);
    public static final Rect CHEAT_POWER_PLUS = new Rect(710f, 736f, 80f, 80f);
    public static final Rect TOGGLE_GOD = new Rect(110f, 838f, 680f, 76f);
    public static final Rect CHEAT_RESET = new Rect(110f, 966f, 330f, 76f);
    public static final Rect CHEATS_BACK = new Rect(460f, 966f, 330f, 76f);

    // 图鉴面板：左右两栏（敌机 / Boss），底部翻页与返回
    public static final Rect CODEX_TAB_ENEMIES = new Rect(110f, 300f, 330f, 64f);
    public static final Rect CODEX_TAB_BOSSES = new Rect(460f, 300f, 330f, 64f);
    public static final Rect CODEX_BACK = new Rect(GameWorld.WIDTH / 2f - 140f, 1420f, 280f, 64f);
    /** 图鉴行高与首行 Y（渲染与命中共用；v1.10 敌机增至 11 种，行高相应压缩）。 */
    // v1.17 敌机图鉴涨到 14 条：行高 86 -> 72，整页才不压到 BACK 按钮
    public static final float CODEX_ROW_Y = 372f;
    public static final float CODEX_ROW_H = 72f;
    public static final int CODEX_MAX_ROWS = EnemyType.values().length;

    /** 图鉴某一行（0 起）的命中矩形。 */
    public static Rect codexRow(int index) {
        return new Rect(110f, CODEX_ROW_Y + index * CODEX_ROW_H, 680f, CODEX_ROW_H - 10f);
    }

    // 成就面板（v1.18）：单局 / 生涯两页，行数比图鉴多，行高再压一档才不挡 BACK
    public static final Rect ACH_TAB_RUN = new Rect(110f, 300f, 330f, 64f);
    public static final Rect ACH_TAB_CUM = new Rect(460f, 300f, 330f, 64f);
    public static final Rect ACHIEVEMENTS_BACK = new Rect(GameWorld.WIDTH / 2f - 140f, 1420f, 280f, 64f);
    public static final float ACH_ROW_Y = 372f;
    public static final float ACH_ROW_H = 56f;

    /** 成就某一行（0 起，按当前页内的序号）的命中矩形。 */
    public static Rect achieveRow(int index) {
        return new Rect(110f, ACH_ROW_Y + index * ACH_ROW_H, 680f, ACH_ROW_H - 8f);
    }

    // pause overlay
    public static final Rect RESUME = new Rect(GameWorld.WIDTH / 2f - 220f, 330f, 440f, 88f);
    public static final Rect RESTART = new Rect(GameWorld.WIDTH / 2f - 220f, 436f, 440f, 72f);
    public static final Rect QUIT_TO_MENU = new Rect(GameWorld.WIDTH / 2f - 220f, 526f, 440f, 72f);
    // in-game pause button (top right, left of lives)
    public static final Rect PAUSE_BTN = new Rect(GameWorld.WIDTH - 340f, 18f, 64f, 64f);

    /** Logical UI actions produced by taps. */
    public enum Action {
        START, START_ENDLESS, OPEN_SETTINGS, TOGGLE_SOUND, TOGGLE_PARTICLES, TOGGLE_HAPTICS,
        TOGGLE_REDUCED_FX, TOGGLE_LEFT_HAND, SETTINGS_BACK, RESUME, RESTART, QUIT_TO_MENU, PAUSE,
        CHECK_UPDATE,
        OPEN_CHEATS, CHEATS_BACK, TOGGLE_GOD, CHEAT_RESET,
        CHEAT_LIVES_DOWN, CHEAT_LIVES_UP, CHEAT_DROP_DOWN, CHEAT_DROP_UP,
        CHEAT_SPEED_DOWN, CHEAT_SPEED_UP, CHEAT_POWER_DOWN, CHEAT_POWER_UP,
        OPEN_CODEX, CODEX_BACK, CODEX_TAB,
        OPEN_ACHIEVEMENTS, ACHIEVEMENTS_BACK, ACHIEVEMENTS_TAB,
        SET_DIFF_CASUAL, SET_DIFF_STANDARD, SET_DIFF_HARDCORE
    }

    /** 难度分段按钮 → 对应动作。 */
    public static Action diffAction(Difficulty d) {
        switch (d) {
            case CASUAL: return Action.SET_DIFF_CASUAL;
            case HARDCORE: return Action.SET_DIFF_HARDCORE;
            default: return Action.SET_DIFF_STANDARD;
        }
    }

    /** Maps a tap in virtual space to an action for the given state; null if none. */
    public static Action hit(GameWorld.State state, boolean settingsOpen, boolean cheatOpen,
                             float x, float y) {
        return hit(state, settingsOpen, cheatOpen, false, x, y);
    }

    public static Action hit(GameWorld.State state, boolean settingsOpen, boolean cheatOpen,
                             boolean codexOpen, float x, float y) {
        return hit(state, settingsOpen, cheatOpen, codexOpen, false, x, y);
    }

    public static Action hit(GameWorld.State state, boolean settingsOpen, boolean cheatOpen,
                             boolean codexOpen, boolean achieveOpen, float x, float y) {
        switch (state) {
            case MENU:
                if (codexOpen) {
                    if (CODEX_BACK.contains(x, y)) return Action.CODEX_BACK;
                    if (CODEX_TAB_ENEMIES.contains(x, y) || CODEX_TAB_BOSSES.contains(x, y)) {
                        return Action.CODEX_TAB;
                    }
                    return null;
                }
                if (achieveOpen) {
                    if (ACHIEVEMENTS_BACK.contains(x, y)) return Action.ACHIEVEMENTS_BACK;
                    if (ACH_TAB_RUN.contains(x, y) || ACH_TAB_CUM.contains(x, y)) {
                        return Action.ACHIEVEMENTS_TAB;
                    }
                    return null;
                }
                if (cheatOpen) {
                    if (CHEAT_LIVES_MINUS.contains(x, y)) return Action.CHEAT_LIVES_DOWN;
                    if (CHEAT_LIVES_PLUS.contains(x, y)) return Action.CHEAT_LIVES_UP;
                    if (CHEAT_DROP_MINUS.contains(x, y)) return Action.CHEAT_DROP_DOWN;
                    if (CHEAT_DROP_PLUS.contains(x, y)) return Action.CHEAT_DROP_UP;
                    if (CHEAT_SPEED_MINUS.contains(x, y)) return Action.CHEAT_SPEED_DOWN;
                    if (CHEAT_SPEED_PLUS.contains(x, y)) return Action.CHEAT_SPEED_UP;
                    if (CHEAT_POWER_MINUS.contains(x, y)) return Action.CHEAT_POWER_DOWN;
                    if (CHEAT_POWER_PLUS.contains(x, y)) return Action.CHEAT_POWER_UP;
                    if (TOGGLE_GOD.contains(x, y)) return Action.TOGGLE_GOD;
                    if (CHEAT_RESET.contains(x, y)) return Action.CHEAT_RESET;
                    if (CHEATS_BACK.contains(x, y)) return Action.CHEATS_BACK;
                    return null;
                }
                if (settingsOpen) {
                    if (DIFF_CASUAL.contains(x, y)) return Action.SET_DIFF_CASUAL;
                    if (DIFF_STANDARD.contains(x, y)) return Action.SET_DIFF_STANDARD;
                    if (DIFF_HARDCORE.contains(x, y)) return Action.SET_DIFF_HARDCORE;
                    if (TOGGLE_SOUND.contains(x, y)) return Action.TOGGLE_SOUND;
                    if (TOGGLE_PARTICLES.contains(x, y)) return Action.TOGGLE_PARTICLES;
                    if (TOGGLE_HAPTICS.contains(x, y)) return Action.TOGGLE_HAPTICS;
                    if (TOGGLE_REDUCED_FX.contains(x, y)) return Action.TOGGLE_REDUCED_FX;
                    if (TOGGLE_LEFT_HAND.contains(x, y)) return Action.TOGGLE_LEFT_HAND;
                    if (SETTINGS_BACK.contains(x, y)) return Action.SETTINGS_BACK;
                    return null;
                }
                if (START.contains(x, y)) return Action.START;
                if (START_ENDLESS.contains(x, y)) return Action.START_ENDLESS;
                if (SETTINGS.contains(x, y)) return Action.OPEN_SETTINGS;
                if (CHEATS.contains(x, y)) return Action.OPEN_CHEATS;
                if (CODEX.contains(x, y)) return Action.OPEN_CODEX;
                if (ACHIEVEMENTS.contains(x, y)) return Action.OPEN_ACHIEVEMENTS;
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
