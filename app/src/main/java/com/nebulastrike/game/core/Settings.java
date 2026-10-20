package com.nebulastrike.game.core;

/** Player preferences, persisted by a {@link SettingsStore}. Written on the UI thread, read on the game thread. */
public class Settings {
    public volatile boolean sound = true;
    public volatile boolean particles = true;
    public volatile boolean haptics = true;
    /** force reduced particle emission (for low-end devices); off = auto quality */
    public volatile boolean reducedFx = false;

    public void toggle(String key) {
        switch (key) {
            case "sound": sound = !sound; break;
            case "particles": particles = !particles; break;
            case "haptics": haptics = !haptics; break;
            case "reducedFx": reducedFx = !reducedFx; break;
        }
    }
}
