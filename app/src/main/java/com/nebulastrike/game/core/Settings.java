package com.nebulastrike.game.core;

/** Player preferences, persisted by a {@link SettingsStore}. */
public class Settings {
    public boolean sound = true;
    public boolean particles = true;
    public boolean haptics = true;

    public void toggle(String key) {
        switch (key) {
            case "sound": sound = !sound; break;
            case "particles": particles = !particles; break;
            case "haptics": haptics = !haptics; break;
        }
    }
}
