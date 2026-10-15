package com.nebulastrike.game;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import com.nebulastrike.game.core.GameWorld;
import com.nebulastrike.game.core.Settings;

/**
 * Retro SFX via SoundPool. All samples are tiny synthesized WAVs in res/raw.
 * Resource IDs are resolved once at init via getIdentifier (no generated-R
 * dependency), then cached. Playback is gated by Settings.sound; consume() is
 * fed the simulation's event queue every frame.
 */
public class AudioEngine {
    private SoundPool pool;
    private int shootId, explodeId, bigExplodeId, hitId, powerupId,
            bombId, alarmId, gameoverId, uiId, bossdownId;
    private Settings settings;
    private boolean ready;

    public void init(Context context, Settings settings) {
        this.settings = settings;
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build();
        pool.setOnLoadCompleteListener((sp, sampleId, status) -> ready = status == 0);
        shootId = load(context, "sfx_shoot");
        explodeId = load(context, "sfx_explode");
        bigExplodeId = load(context, "sfx_big_explode");
        hitId = load(context, "sfx_hit");
        powerupId = load(context, "sfx_powerup");
        bombId = load(context, "sfx_bomb");
        alarmId = load(context, "sfx_alarm");
        gameoverId = load(context, "sfx_gameover");
        uiId = load(context, "sfx_ui");
        bossdownId = load(context, "sfx_bossdown");
    }

    private int load(Context context, String name) {
        int res = context.getResources().getIdentifier(name, "raw", context.getPackageName());
        return pool.load(context, res, 1);
    }

    public void consume(Iterable<GameWorld.GameEvent> events) {
        for (GameWorld.GameEvent e : events) {
            switch (e.type) {
                case ENEMY_KILLED: play(explodeId, 0.7f, 1f); break;
                case PLAYER_HIT: play(hitId, 1f, 1f); break;
                case GAME_OVER: play(gameoverId, 1f, 1f); break;
                case POWERUP_COLLECTED: play(powerupId, 0.9f, 1f); break;
                case BOMB: play(bombId, 1f, 1f); break;
                case FORMATION: play(uiId, 0.4f, 1.1f); break;
                case BOSS_SPAWNED: play(alarmId, 0.9f, 1f); break;
                case BOSS_DEFEATED: play(bossdownId, 1f, 1f); break;
                case BOSS_PHASE: play(alarmId, 0.7f, 1.3f); break;
            }
        }
    }

    /** Rate-limited by the weapon cooldown itself. */
    public void shoot() { play(shootId, 0.3f, 1f); }

    public void bigExplosion() { play(bigExplodeId, 1f, 1f); }

    public void uiTick() { play(uiId, 0.5f, 1.25f); }

    private void play(int soundId, float vol, float rate) {
        if (pool == null || !ready) return;
        if (settings != null && !settings.sound) return;
        pool.play(soundId, vol, vol, 1, 0, rate);
    }

    public void release() {
        if (pool != null) {
            pool.release();
            pool = null;
        }
    }
}
