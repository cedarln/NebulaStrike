package com.nebulastrike.game.core.audio;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Stereo one-shot mixer. The game thread only enqueues play requests; the
 * audio thread owns the voices and renders fixed-size buffers.
 */
public final class SfxMixer {
    public static final int MAX_VOICES = 24;

    private final SfxBank bank;
    private final Voice[] voices = new Voice[MAX_VOICES];
    private final ArrayDeque<Request> pending = new ArrayDeque<>();
    private volatile boolean clearRequested;
    private int nextVoice;

    public SfxMixer(SfxBank bank) {
        this.bank = bank;
        for (int i = 0; i < MAX_VOICES; i++) voices[i] = new Voice();
    }

    /** Safe to call from the game/UI thread. */
    public void play(SfxId id, float volume, float pitch, float pan) {
        float[] data = bank.clip(id);
        if (data == null || data.length < 2) return;
        Request r = new Request();
        r.data = data;
        r.volume = clamp(volume, 0f, 1.5f);
        r.step = clamp(pitch, 0.25f, 4f);
        float p = (clamp(pan, -1f, 1f) + 1f) * 0.5f;
        double angle = p * Math.PI * 0.5;
        r.left = (float) Math.cos(angle);
        r.right = (float) Math.sin(angle);
        synchronized (pending) {
            pending.add(r);
        }
    }

    /** Drops queued and sounding voices (sound toggled off). */
    public void clear() {
        synchronized (pending) {
            pending.clear();
        }
        clearRequested = true;
    }

    /** Called on the audio thread only. */
    public void render(float[] out, int frames) {
        Arrays.fill(out, 0, frames * 2, 0f);
        if (clearRequested) {
            clearRequested = false;
            for (Voice v : voices) v.active = false;
        }
        synchronized (pending) {
            Request r;
            while ((r = pending.poll()) != null) start(r);
        }
        for (Voice v : voices) {
            if (!v.active) continue;
            mixVoice(v, out, frames);
        }
        for (int i = 0; i < frames * 2; i++) {
            float v = out[i];
            out[i] = v > 1f ? 1f : (v < -1f ? -1f : v);
        }
    }

    private void mixVoice(Voice v, float[] out, int frames) {
        float[] data = v.data;
        int end = frames * 2;
        for (int i = 0; i < end; i += 2) {
            int idx = (int) v.pos;
            if (idx >= data.length - 1) {
                v.active = false;
                return;
            }
            float frac = (float) (v.pos - idx);
            float s = (data[idx] + (data[idx + 1] - data[idx]) * frac) * v.volume;
            out[i] += s * v.left;
            out[i + 1] += s * v.right;
            v.pos += v.step;
        }
    }

    private void start(Request r) {
        Voice v = null;
        for (int i = 0; i < MAX_VOICES; i++) {
            int idx = (nextVoice + i) % MAX_VOICES;
            if (!voices[idx].active) {
                v = voices[idx];
                nextVoice = (idx + 1) % MAX_VOICES;
                break;
            }
        }
        if (v == null) { // steal the oldest slot round-robin
            v = voices[nextVoice];
            nextVoice = (nextVoice + 1) % MAX_VOICES;
        }
        v.data = r.data;
        v.pos = 0;
        v.step = r.step;
        v.volume = r.volume;
        v.left = r.left;
        v.right = r.right;
        v.active = true;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static final class Voice {
        float[] data;
        double pos;
        float step;
        float volume;
        float left;
        float right;
        boolean active;
    }

    private static final class Request {
        float[] data;
        float volume;
        float step;
        float left;
        float right;
    }
}
