package com.nebulastrike.game;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import com.nebulastrike.game.core.Settings;

/** Short haptic feedback for impacts, gated by Settings.haptics. */
public class Haptics {
    private final Vibrator vibrator;
    private Settings settings;

    public Haptics(Context context) {
        Vibrator v;
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager vm = (VibratorManager)
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            v = vm != null ? vm.getDefaultVibrator() : null;
        } else {
            v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
        vibrator = (v != null && v.hasVibrator()) ? v : null;
    }

    public void attach(Settings settings) {
        this.settings = settings;
    }

    public void impact() { buzz(40); }

    public void tick() { buzz(20); }

    public void bomb() { buzz(90); }

    public void death() { buzz(240); }

    private void buzz(long ms) {
        if (vibrator == null) return;
        if (settings != null && !settings.haptics) return;
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(ms);
        }
    }
}
