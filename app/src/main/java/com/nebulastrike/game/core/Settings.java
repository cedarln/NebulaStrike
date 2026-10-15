package com.nebulastrike.game.core;

/** Player preferences, persisted by a {@link SettingsStore}. */
public class Settings {
    public boolean sound = true;
    public boolean particles = true;
    public boolean haptics = true;
    /** force reduced particle emission (for low-end devices); off = auto quality */
    public boolean reducedFx = false;

    public void toggle(String key) {
        switch (key) {
            case "sound": sound = !sound; break;
            case "particles": particles = !particles; break;
            case "haptics": haptics = !haptics; break;
            case "reducedFx": reducedFx = !reducedFx; break;
        }
    }
}
