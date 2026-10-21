package com.nebulastrike.game.core.audio;

import java.util.Random;

/**
 * Pure-Java procedural sound bank: all clips are mono float PCM at 44.1 kHz.
 * No assets and no Android imports, so every waveform can be verified on the JVM.
 */
public final class SfxBank {
    public static final int SAMPLE_RATE = 44100;
    private static final double TAU = Math.PI * 2.0;

    private final float[][] clips = new float[SfxId.values().length][];

    private SfxBank() {
        put(SfxId.SHOOT_VULCAN, shootVulcan(), 0.82f);
        put(SfxId.SHOOT_SPREAD, shootSpread(), 0.82f);
        put(SfxId.SHOOT_LASER, shootLaser(), 0.86f);
        put(SfxId.SHOOT_MISSILE, shootMissile(), 0.80f);
        put(SfxId.WINGMAN, wingman(), 0.55f);
        put(SfxId.EXPLODE, explosion(0.42, 165, 42, false), 0.86f);
        put(SfxId.EXPLODE_HEAVY, explosion(0.78, 110, 7, true), 0.94f);
        put(SfxId.PLAYER_HIT, playerHit(), 0.96f);
        put(SfxId.SHIELD_BREAK, shieldBreak(), 0.82f);
        put(SfxId.BOMB, bomb(), 0.98f);
        put(SfxId.POWERUP, powerup(), 0.78f);
        put(SfxId.EXTRA_LIFE, extraLife(), 0.85f);
        put(SfxId.COMBO, combo(), 0.72f);
        put(SfxId.GRAZE, graze(), 0.50f);
        put(SfxId.OVERDRIVE, overdrive(), 0.92f);
        put(SfxId.ALARM, alarm(0.72, 720, 466, 4), 0.80f);
        put(SfxId.BOSS_PHASE, alarm(0.50, 900, 640, 3), 0.78f);
        put(SfxId.BOSS_DOWN, bossDown(), 0.98f);
        put(SfxId.BOSS_LASER, bossLaser(), 0.92f);
        put(SfxId.PART_BREAK, partBreak(), 0.82f);
        put(SfxId.GAME_OVER, gameOver(), 0.88f);
        put(SfxId.UI, ui(), 0.48f);
        put(SfxId.WAVE, wave(), 0.66f);
        put(SfxId.ESCAPE, escape(), 0.55f);
        put(SfxId.DASH, dash(), 0.80f);
        put(SfxId.CHAIN_ZAP, chainZap(), 0.90f);
        put(SfxId.FUSION, fusion(), 0.86f);
        put(SfxId.BOSS_RAM, bossRam(), 0.96f);
        put(SfxId.HEAL, heal(), 0.62f);
        put(SfxId.ORBIT, orbit(), 0.58f);
        put(SfxId.BONUS, bonus(), 0.90f);
        put(SfxId.SHOOT_BOUNCE, shootBounce(), 0.80f);
        put(SfxId.NOVA, nova(), 0.98f);
        put(SfxId.TURRET_DEPLOY, turretDeploy(), 0.84f);
        put(SfxId.RELIC, relic(), 0.96f);
        put(SfxId.RESPAWN, respawn(), 0.78f);
        put(SfxId.MINE, mine(), 0.72f);
        put(SfxId.SHIELD_BLOCK, shieldBlock(), 0.76f);
    }

    public static SfxBank create() {
        return new SfxBank();
    }

    public float[] clip(SfxId id) {
        return clips[id.ordinal()];
    }

    private void put(SfxId id, float[] clip, float peak) {
        clips[id.ordinal()] = finish(clip, peak);
    }

    // ---- sound design ----

    private static float[] shootVulcan() {
        int n = frames(0.10);
        float[] x = new float[n];
        Random r = new Random(11);
        double ph = 0, sub = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double f = 260 + 1500 * Math.exp(-t * 30);
            ph += f / SAMPLE_RATE;
            sub += f * 0.5 / SAMPLE_RATE;
            double body = 0.62 * square(ph, 0.34) + 0.38 * sine(ph * 1.01);
            double bass = 0.35 * sine(sub);
            double click = noise(r) * Math.exp(-t * 240) * 0.55;
            x[i] = (float) (((body + bass) * 0.72 + click)
                    * env(t, 0.10, 0.0015, 1.9));
        }
        return x;
    }

    private static float[] shootSpread() {
        int n = frames(0.16);
        float[] x = new float[n];
        Random r = new Random(12);
        double[] f0 = {784, 988, 1244};
        double[] ph = new double[f0.length];
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double bend = 1.0 - 0.32 * (t / 0.16);
            double tone = 0;
            for (int k = 0; k < f0.length; k++) {
                ph[k] += f0[k] * bend / SAMPLE_RATE;
                tone += (0.55 * tri(ph[k]) + 0.45 * sine(ph[k] * 2.0)) / f0.length;
            }
            double air = noise(r) * 0.20 * Math.exp(-t * 18);
            x[i] = (float) ((tone + air) * env(t, 0.16, 0.0015, 1.6));
        }
        return x;
    }

    private static float[] shootLaser() {
        int n = frames(0.14);
        float[] x = new float[n];
        Random r = new Random(13);
        double ph = 0, mod = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double f = 850 + 2600 * Math.exp(-t * 9);
            ph += f / SAMPLE_RATE;
            mod += f * 7.3 / SAMPLE_RATE;
            double ring = sine(ph) * (0.62 + 0.38 * sine(mod));
            double body = 0.35 * saw(ph) + 0.65 * ring;
            double shimmer = 0.20 * sine(ph * 2.02);
            double grit = noise(r) * Math.exp(-t * 120) * 0.25;
            x[i] = (float) (((body + shimmer) * 0.85 + grit)
                    * env(t, 0.14, 0.0015, 1.8));
        }
        return x;
    }

    private static float[] shootMissile() {
        int n = frames(0.30);
        float[] x = new float[n];
        Random r = new Random(14);
        double ph = 0, lp = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double coef = 0.03 + 0.50 * Math.exp(-t * 10);
            lp += coef * (noise(r) - lp);
            double f = 65 + 220 * Math.exp(-t * 4.5);
            ph += f / SAMPLE_RATE;
            double thump = sine(ph) * Math.exp(-t * 13);
            double hiss = lp * Math.exp(-t * 4) * 1.15;
            x[i] = (float) ((thump * 0.9 + hiss)
                    * env(t, 0.30, 0.0015, 1.5));
        }
        return x;
    }

    private static float[] wingman() {
        int n = frames(0.07);
        float[] x = new float[n];
        double ph = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double f = 700 + 1000 * Math.exp(-t * 18);
            ph += f / SAMPLE_RATE;
            double s = 0.55 * tri(ph) + 0.45 * sine(ph);
            x[i] = (float) (s * env(t, 0.07, 0.0015, 2.0));
        }
        return x;
    }

    /** Layered blast: sub drop, filtered noise, crackle and an optional metallic ring. */
    private static float[] explosion(double dur, double baseFreq, long seed, boolean metallic) {
        int n = frames(dur);
        float[] x = new float[n];
        Random r = new Random(seed);
        double ph = 0, ringPh = 0, lp = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double coef = 0.035 + 0.34 * Math.exp(-t * 7.5);
            lp += coef * (noise(r) - lp);
            double f = 28 + baseFreq * (1.0 - 0.75 * t / dur);
            ph += f / SAMPLE_RATE;
            ringPh += f * 3.17 / SAMPLE_RATE;
            double sub = sine(ph) * Math.exp(-t * 4.2);
            double grit = lp * (1.0 + 0.25 * Math.sin(t * 80));
            double crackle = r.nextDouble() < 0.02
                    ? noise(r) * Math.exp(-t * 22) : 0.0;
            double ring = metallic
                    ? sine(ringPh) * Math.exp(-t * 7) * 0.30 : 0.0;
            double s = sub * 0.95 + grit * 1.20 + crackle * 0.70 + ring;
            x[i] = (float) (s * env(t, dur, 0.0015, 1.55));
        }
        echo(x, 0.08, 0.24);
        return x;
    }

    private static float[] playerHit() {
        float[] x = explosion(0.95, 105, 21, true);
        double ph = 0;
        for (int i = 0; i < x.length; i++) {
            double t = i / (double) SAMPLE_RATE;
            double f = 70 + 520 * Math.exp(-t * 4.5);
            ph += f / SAMPLE_RATE;
            x[i] += (float) (saw(ph) * Math.exp(-t * 3.8) * 0.18);
        }
        echo(x, 0.15, 0.30);
        return x;
    }

    private static float[] shieldBreak() {
        int n = frames(0.50);
        float[] x = new float[n];
        Random r = new Random(23);
        double[] f0 = {1568, 2093, 2637, 3136};
        double[] ph = new double[f0.length];
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double bend = 1.0 - 0.50 * (t / 0.50);
            double tone = 0;
            for (int k = 0; k < f0.length; k++) {
                ph[k] += f0[k] * bend / SAMPLE_RATE;
                tone += sine(ph[k]) * Math.exp(-t * (6.0 + k * 1.5)) * 0.25;
            }
            double hiss = noise(r) * 0.25 * Math.exp(-t * 11);
            x[i] = (float) ((tone + hiss) * env(t, 0.50, 0.001, 1.2));
        }
        echo(x, 0.07, 0.30);
        return x;
    }

    private static float[] bomb() {
        float[] x = explosion(1.45, 72, 33, true);
        Random r = new Random(34);
        double ph = 0;
        int riser = frames(0.16);
        for (int i = 0; i < riser; i++) {
            double t = i / (double) SAMPLE_RATE;
            double p = t / 0.16;
            double f = 90 + 1600 * p * p;
            ph += f / SAMPLE_RATE;
            x[i] += (float) ((sine(ph) * 0.45 + noise(r) * 0.15 * p)
                    * p * p);
        }
        int crack = frames(0.35);
        for (int i = 0; i < crack && riser + i < x.length; i++) {
            double t = i / (double) SAMPLE_RATE;
            x[riser + i] += (float) (noise(r) * Math.exp(-t * 25) * 0.35);
        }
        echo(x, 0.19, 0.32);
        return x;
    }

    private static float[] powerup() {
        double[] notes = {523.25, 659.25, 783.99, 1046.50};
        float[] x = new float[frames(0.55)];
        Random r = new Random(44);
        for (int k = 0; k < notes.length; k++) {
            int start = frames(k * 0.072);
            int len = frames(0.22);
            double ph = 0, ph2 = 0;
            for (int i = 0; i < len && start + i < x.length; i++) {
                double t = i / (double) SAMPLE_RATE;
                ph += notes[k] / SAMPLE_RATE;
                ph2 += notes[k] * 2.01 / SAMPLE_RATE;
                double s = 0.55 * tri(ph) + 0.30 * sine(ph)
                        + 0.15 * sine(ph2);
                x[start + i] += (float) (s * env(t, 0.22, 0.003, 1.35) * 0.8);
            }
        }
        for (int i = 0; i < x.length; i++) {
            double t = i / (double) SAMPLE_RATE;
            x[i] += (float) (noise(r) * Math.exp(-t * 10) * 0.03);
        }
        echo(x, 0.11, 0.22);
        return x;
    }

    private static float[] extraLife() {
        double[] notes = {523.25, 659.25, 783.99, 1046.50, 1318.51};
        float[] x = new float[frames(0.85)];
        for (int k = 0; k < notes.length; k++) {
            int start = frames(k * 0.085);
            int len = frames(0.30);
            double ph = 0, ph2 = 0;
            for (int i = 0; i < len && start + i < x.length; i++) {
                double t = i / (double) SAMPLE_RATE;
                ph += notes[k] / SAMPLE_RATE;
                ph2 += notes[k] * 2.005 / SAMPLE_RATE;
                double s = 0.45 * tri(ph) + 0.35 * sine(ph)
                        + 0.20 * sine(ph2);
                x[start + i] += (float) (s * env(t, 0.30, 0.004, 1.25) * 0.7);
            }
        }
        echo(x, 0.12, 0.28);
        return x;
    }

    private static float[] combo() {
        int n = frames(0.20);
        float[] x = new float[n];
        double ph = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            ph += 880.0 / SAMPLE_RATE;
            double s = 0.55 * sine(ph) + 0.30 * tri(ph * 2.0)
                    + 0.15 * sine(ph * 1.5);
            x[i] = (float) (s * env(t, 0.20, 0.0015, 2.2));
        }
        int start = frames(0.055);
        double ph2 = 0;
        for (int i = 0; i + start < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            ph2 += 1318.5 / SAMPLE_RATE;
            x[start + i] += (float) (sine(ph2) * env(t, 0.12, 0.002, 2.4) * 0.35);
        }
        return x;
    }

    private static float[] graze() {
        int n = frames(0.07);
        float[] x = new float[n];
        Random r = new Random(55);
        double ph = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double f = 2100 + 900 * (t / 0.07);
            ph += f / SAMPLE_RATE;
            double s = 0.60 * sine(ph) + 0.25 * sine(ph * 2.01);
            double click = noise(r) * Math.exp(-t * 300) * 0.20;
            x[i] = (float) ((s + click) * env(t, 0.07, 0.001, 2.0));
        }
        return x;
    }

    private static float[] overdrive() {
        float[] x = new float[frames(1.15)];
        Random r = new Random(66);
        double ph = 0;
        int riser = frames(0.52);
        for (int i = 0; i < riser; i++) {
            double t = i / (double) SAMPLE_RATE;
            double p = t / 0.52;
            double f = 130 + 1700 * p * p;
            ph += f / SAMPLE_RATE;
            double s = 0.45 * saw(ph) + 0.35 * sine(ph * 1.005)
                    + 0.20 * noise(r) * p;
            x[i] += (float) (s * p * p * 0.8);
        }
        float[] hit = explosion(0.45, 160, 55, true);
        mix(x, hit, riser, 0.55f);
        double[] chord = {440, 554.37, 659.25};
        int len = frames(0.60);
        for (double f : chord) {
            double p = 0;
            for (int i = 0; i < len && riser + i < x.length; i++) {
                double t = i / (double) SAMPLE_RATE;
                p += f / SAMPLE_RATE;
                double s = 0.45 * saw(p) + 0.35 * sine(p)
                        + 0.20 * tri(p * 2.0);
                x[riser + i] += (float) (s * env(t, 0.60, 0.005, 1.4) * 0.35);
            }
        }
        echo(x, 0.13, 0.28);
        return x;
    }

    private static float[] alarm(double dur, double high, double low, int beeps) {
        float[] x = new float[frames(dur)];
        double step = dur / beeps;
        for (int k = 0; k < beeps; k++) {
            double f = (k % 2 == 0) ? high : low;
            int start = frames(k * step);
            int len = frames(step * 0.82);
            double ph = 0;
            for (int i = 0; i < len && start + i < x.length; i++) {
                double t = i / (double) SAMPLE_RATE;
                ph += f / SAMPLE_RATE;
                double s = 0.55 * square(ph, 0.5) + 0.30 * tri(ph * 0.5)
                        + 0.15 * sine(ph * 1.5);
                x[start + i] += (float) (s * env(t, step * 0.82, 0.004, 0.8) * 0.7);
            }
        }
        echo(x, 0.09, 0.25);
        return x;
    }

    private static float[] bossDown() {
        float[] x = explosion(1.35, 62, 88, true);
        double[] notes = {392, 523.25, 659.25, 783.99};
        int len = frames(0.28);
        for (int k = 0; k < notes.length; k++) {
            int start = frames(0.42 + k * 0.16);
            double ph = 0, ph2 = 0;
            for (int i = 0; i < len && start + i < x.length; i++) {
                double t = i / (double) SAMPLE_RATE;
                ph += notes[k] / SAMPLE_RATE;
                ph2 += notes[k] * 1.006 / SAMPLE_RATE;
                double s = 0.45 * saw(ph2) + 0.35 * sine(ph)
                        + 0.20 * tri(ph * 2.0);
                x[start + i] += (float) (s * env(t, 0.28, 0.005, 1.5) * 0.30);
            }
        }
        echo(x, 0.18, 0.30);
        return x;
    }

    /** Boss laser: rising charge whine that snaps into a fat metallic beam. */
    private static float[] bossLaser() {
        int n = frames(0.95);
        float[] x = new float[n];
        Random r = new Random(101);
        double ph = 0, sub = 0, mod = 0;
        double fireAt = 0.22;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double p = Math.min(1.0, t / fireAt);
            double f = 240 + 2600 * p * p;
            ph += f / SAMPLE_RATE;
            mod += f * 6.7 / SAMPLE_RATE;
            sub += f * 0.25 / SAMPLE_RATE;
            double beam = (0.55 * saw(ph) + 0.45 * sine(ph * 1.01))
                    * (0.65 + 0.35 * sine(mod));
            double low = 0.35 * sine(sub);
            double crack = noise(r) * 0.24 * Math.exp(-Math.abs(t - fireAt) * 26);
            double amp = t < fireAt ? p * p : Math.exp(-(t - fireAt) * 2.4);
            x[i] = (float) ((beam + low + crack) * amp * env(t, 0.95, 0.004, 1.2));
        }
        echo(x, 0.06, 0.22);
        return x;
    }

    /** Destructible boss pod: short metallic blast plus flying shards. */
    private static float[] partBreak() {
        float[] x = explosion(0.40, 210, 103, true);
        Random r = new Random(104);
        int shards = Math.min(x.length, frames(0.30));
        double[] freqs = {1320, 1760, 2210, 2790, 3320};
        double[] ph = new double[freqs.length];
        for (int i = 0; i < shards; i++) {
            double t = i / (double) SAMPLE_RATE;
            double s = 0;
            for (int k = 0; k < freqs.length; k++) {
                ph[k] += freqs[k] * (1.0 - 0.18 * t / 0.30) / SAMPLE_RATE;
                s += sine(ph[k]) * Math.exp(-t * (9 + k * 2.2)) * 0.16;
            }
            s += noise(r) * Math.exp(-t * 24) * 0.18;
            x[i] += (float) s;
        }
        return x;
    }

    private static float[] gameOver() {
        float[] x = new float[frames(1.50)];
        double[] notes = {392.00, 329.63, 261.63, 196.00};
        for (int k = 0; k < notes.length; k++) {
            int start = frames(k * 0.27);
            int len = frames(0.42);
            double ph = 0, ph2 = 0;
            for (int i = 0; i < len && start + i < x.length; i++) {
                double t = i / (double) SAMPLE_RATE;
                ph += notes[k] / SAMPLE_RATE;
                ph2 += notes[k] * 1.006 / SAMPLE_RATE;
                double s = 0.40 * sine(ph) + 0.35 * saw(ph2)
                        + 0.25 * tri(ph * 0.5);
                x[start + i] += (float) (s * env(t, 0.42, 0.008, 1.6) * 0.55);
            }
        }
        echo(x, 0.20, 0.30);
        return x;
    }

    private static float[] ui() {
        int n = frames(0.06);
        float[] x = new float[n];
        Random r = new Random(77);
        double ph = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double f = 1000 + 700 * Math.exp(-t * 25);
            ph += f / SAMPLE_RATE;
            double s = 0.55 * tri(ph) + 0.30 * sine(ph)
                    + noise(r) * Math.exp(-t * 300) * 0.15;
            x[i] = (float) (s * env(t, 0.06, 0.001, 2.0));
        }
        return x;
    }

    private static float[] wave() {
        int n = frames(0.60);
        float[] x = new float[n];
        double ph = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            ph += 880.0 / SAMPLE_RATE;
            double s = 0.70 * sine(ph) + 0.20 * sine(ph * 2.01)
                    + 0.10 * sine(ph * 3.02);
            x[i] = (float) (s * Math.exp(-t * 7) * 0.5);
        }
        echo(x, 0.17, 0.35);
        return x;
    }

    private static float[] escape() {
        int n = frames(0.32);
        float[] x = new float[n];
        Random r = new Random(88);
        double lp = 0, ph = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double coef = 0.02 + 0.35 * Math.exp(-t * 8);
            lp += coef * (noise(r) - lp);
            double f = 70 + 420 * Math.exp(-t * 5);
            ph += f / SAMPLE_RATE;
            double s = lp * 0.8 + sine(ph) * 0.3;
            x[i] = (float) (s * env(t, 0.32, 0.002, 1.5));
        }
        return x;
    }

    /** 相位冲刺：空气被撕开的上扫噪声 + 落位时的低频"啵"。 */
    private static float[] dash() {
        int n = frames(0.30);
        float[] x = new float[n];
        Random r = new Random(201);
        double lp = 0, ph = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double p = t / 0.30;
            double coef = 0.05 + 0.30 * Math.exp(-t * 12);
            lp += coef * (noise(r) - lp);
            double open = 0.25 + 0.75 * p;            // 滤波逐渐打开
            double swish = lp * open * Math.exp(-t * 6.5) * 1.3;
            double f = 1200 + 2400 * p;
            ph += f / SAMPLE_RATE;
            double whistle = sine(ph) * Math.exp(-t * 9) * 0.28;
            double thud = 0;
            if (t > 0.18) {
                double tt = t - 0.18;
                double bf = 90 + 40 * Math.exp(-tt * 18);
                ph += bf / SAMPLE_RATE;
                thud = sine(ph) * Math.exp(-tt * 14) * 0.7;
            }
            x[i] = (float) ((swish + whistle + thud) * env(t, 0.30, 0.002, 1.1));
        }
        echo(x, 0.05, 0.20);
        return x;
    }

    /** 连锁闪电：多次高压击穿，每次带一点音高下降的金属嗡鸣。 */
    private static float[] chainZap() {
        int n = frames(0.50);
        float[] x = new float[n];
        Random r = new Random(203);
        double[] hits = {0.0, 0.09, 0.17, 0.26};
        double[] base = {2400, 2100, 1850, 1600};
        for (int k = 0; k < hits.length; k++) {
            int start = frames(hits[k]);
            int len = frames(0.16);
            double ph = 0, lp = 0;
            for (int i = 0; i < len && start + i < n; i++) {
                double t = i / (double) SAMPLE_RATE;
                lp += 0.35 * (noise(r) - lp);
                double f = base[k] * (1.0 - 0.35 * t / 0.16);
                ph += f / SAMPLE_RATE;
                double crack = lp * Math.exp(-t * 26) * 1.1;
                double ring = (0.5 * sine(ph) + 0.5 * square(ph, 0.3))
                        * Math.exp(-t * 20) * 0.45;
                x[start + i] += (float) ((crack + ring) * (1.0 - k * 0.16));
            }
        }
        echo(x, 0.045, 0.32);
        return x;
    }

    /** 武器融合：四度上行琶音 + 泛音共鸣，"解锁"的仪式感。 */
    private static float[] fusion() {
        double[] notes = {523.25, 698.46, 880.00, 1174.66, 1567.98};
        float[] x = new float[frames(0.80)];
        for (int k = 0; k < notes.length; k++) {
            int start = frames(k * 0.075);
            int len = frames(0.34);
            double ph = 0, ph2 = 0, ph3 = 0;
            for (int i = 0; i < len && start + i < x.length; i++) {
                double t = i / (double) SAMPLE_RATE;
                ph += notes[k] / SAMPLE_RATE;
                ph2 += notes[k] * 1.5 / SAMPLE_RATE;
                ph3 += notes[k] * 2.0 / SAMPLE_RATE;
                double s = 0.40 * sine(ph) + 0.28 * tri(ph2)
                        + 0.20 * sine(ph3) + 0.12 * saw(ph * 0.5);
                x[start + i] += (float) (s * env(t, 0.34, 0.004, 1.3) * 0.62);
            }
        }
        echo(x, 0.13, 0.30);
        return x;
    }

    /** Boss 横冲：低频引擎轰鸣加速，末尾一记沉重撞击。 */
    private static float[] bossRam() {
        float[] x = new float[frames(0.85)];
        Random r = new Random(207);
        double ph = 0, lp = 0;
        int rumble = frames(0.52);
        for (int i = 0; i < rumble; i++) {
            double t = i / (double) SAMPLE_RATE;
            double p = t / 0.52;
            lp += (0.02 + 0.10 * p) * (noise(r) - lp);
            double f = 55 + 120 * p;
            ph += f / SAMPLE_RATE;
            double growl = (0.6 * saw(ph) + 0.4 * sine(ph * 2.01)) * (0.35 + 0.65 * p);
            x[i] += (float) ((growl + lp * 0.5 * p) * 0.85);
        }
        float[] slam = explosion(0.40, 140, 208, true);
        mix(x, slam, rumble, 0.62f);
        echo(x, 0.11, 0.26);
        return x;
    }

    /** 医疗脉冲：柔和的双音铃，带轻微颤动，提示"敌人在回血"。 */
    private static float[] heal() {
        double[] notes = {880.0, 1174.66};
        float[] x = new float[frames(0.42)];
        for (int k = 0; k < notes.length; k++) {
            int start = frames(k * 0.09);
            int len = frames(0.26);
            double ph = 0, vib = 0;
            for (int i = 0; i < len && start + i < x.length; i++) {
                double t = i / (double) SAMPLE_RATE;
                vib += 6.0 / SAMPLE_RATE;
                ph += notes[k] * (1.0 + 0.006 * sine(vib)) / SAMPLE_RATE;
                double s = 0.65 * sine(ph) + 0.25 * sine(ph * 2.0)
                        + 0.10 * tri(ph * 3.0);
                x[start + i] += (float) (s * env(t, 0.26, 0.006, 1.1) * 0.6);
            }
        }
        echo(x, 0.09, 0.24);
        return x;
    }

    /** 轨道球命中：短促的水晶般"叮"，用于连番清扫敌弹。 */
    private static float[] orbit() {
        int n = frames(0.13);
        float[] x = new float[n];
        double ph = 0, ph2 = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double f = 1760 + 900 * Math.exp(-t * 22);
            ph += f / SAMPLE_RATE;
            ph2 += f * 1.5 / SAMPLE_RATE;
            double s = 0.55 * sine(ph) + 0.30 * sine(ph2) + 0.15 * tri(ph * 2.0);
            x[i] = (float) (s * env(t, 0.13, 0.0012, 1.9));
        }
        return x;
    }

    /** 突袭奖励：上扬琶音 + 闪烁高频，金币雨的感觉。 */
    private static float[] bonus() {
        double[] notes = {659.25, 783.99, 987.77, 1318.51, 1567.98, 1975.53};
        float[] x = new float[frames(0.90)];
        Random r = new Random(211);
        for (int k = 0; k < notes.length; k++) {
            int start = frames(k * 0.06);
            int len = frames(0.30);
            double ph = 0, ph2 = 0;
            for (int i = 0; i < len && start + i < x.length; i++) {
                double t = i / (double) SAMPLE_RATE;
                ph += notes[k] / SAMPLE_RATE;
                ph2 += notes[k] * 2.0 / SAMPLE_RATE;
                double s = 0.5 * sine(ph) + 0.3 * tri(ph) + 0.2 * sine(ph2);
                x[start + i] += (float) (s * env(t, 0.30, 0.003, 1.4) * 0.6);
            }
        }
        for (int i = frames(0.45); i < x.length; i++) {
            if (r.nextDouble() < 0.02) x[i] += (float) (noise(r) * 0.18);
        }
        echo(x, 0.10, 0.28);
        return x;
    }

    // ---- v1.10 新增音色 ----------------------------------------------------

    /** 弹跳弹：像橡胶球撞墙一样的弹性"啵嘤"，音高先降后升。 */
    private static float[] shootBounce() {
        int n = frames(0.18);
        float[] x = new float[n];
        Random r = new Random(301);
        double ph = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double p = t / 0.18;
            double f = 1100 - 620 * Math.sin(p * Math.PI);
            ph += f / SAMPLE_RATE;
            double s = 0.55 * sine(ph) + 0.30 * tri(ph * 2.0)
                    + 0.15 * sine(ph * 3.02);
            double tick = noise(r) * Math.exp(-t * 260) * 0.25;
            x[i] = (float) ((s + tick) * env(t, 0.18, 0.0015, 1.7));
        }
        return x;
    }

    /** 新星冲击：低频涌起 + 全频段噪声扩散，像冲击波扫过屏幕。 */
    private static float[] nova() {
        int n = frames(0.90);
        float[] x = new float[n];
        Random r = new Random(302);
        double ph = 0, lp = 0, hp = 0, prev = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double p = Math.min(1.0, t / 0.26);
            double swell = t < 0.26 ? p * p : Math.exp(-(t - 0.26) * 3.4);
            lp += (0.02 + 0.30 * p) * (noise(r) - lp);
            double coef = 0.35;
            double cur = lp + coef * (prev - lp);   // 简易高通，制造扩散感
            prev = lp;
            hp += coef * (cur - hp);
            double f = 44 + 220 * p;
            ph += f / SAMPLE_RATE;
            double sub = sine(ph) * Math.exp(-t * 2.6) * 1.05;
            double blast = hp * Math.exp(-t * 2.2) * 1.1;
            double shimmer = 0.18 * sine(ph * 8.3) * Math.exp(-t * 5.0);
            x[i] = (float) ((sub + blast + shimmer) * swell
                    * env(t, 0.90, 0.004, 1.1));
        }
        echo(x, 0.16, 0.30);
        return x;
    }

    /** 部署炮台：伺服电机升起，末尾一记锁定"咔哒"。 */
    private static float[] turretDeploy() {
        int n = frames(0.55);
        float[] x = new float[n];
        Random r = new Random(303);
        double ph = 0, sub = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double f = 180 + 520 * Math.min(1.0, t / 0.32);
            ph += f / SAMPLE_RATE;
            sub += f * 0.5 / SAMPLE_RATE;
            double servo = (0.5 * square(ph, 0.42) + 0.5 * saw(ph))
                    * (t < 0.32 ? 0.55 + 0.45 * (t / 0.32) : Math.exp(-(t - 0.32) * 9));
            double body = 0.35 * sine(sub);
            double click = t > 0.32
                    ? noise(r) * Math.exp(-(t - 0.32) * 160) * 0.45 : 0.0;
            x[i] = (float) ((servo * 0.6 + body + click) * env(t, 0.55, 0.002, 1.3));
        }
        echo(x, 0.06, 0.20);
        return x;
    }

    /** Boss 遗物：低八度轰鸣垫底，上面是神秘的上行和声与钟鸣。 */
    private static float[] relic() {
        float[] x = new float[frames(1.10)];
        Random r = new Random(304);
        double ph = 0;
        for (int i = 0; i < frames(0.45) && i < x.length; i++) {
            double t = i / (double) SAMPLE_RATE;
            ph += (58 + 30 * (t / 0.45)) / SAMPLE_RATE;
            x[i] += (float) ((0.6 * sine(ph) + 0.4 * saw(ph * 2.01))
                    * Math.min(1.0, t / 0.05) * 0.5);
        }
        double[] notes = {392.0, 587.33, 783.99, 1174.66, 1567.98};
        int len = frames(0.52);
        for (int k = 0; k < notes.length; k++) {
            int start = frames(0.16 + k * 0.10);
            double a = 0, b = 0;
            for (int i = 0; i < len && start + i < x.length; i++) {
                double t = i / (double) SAMPLE_RATE;
                a += notes[k] / SAMPLE_RATE;
                b += notes[k] * 2.0 / SAMPLE_RATE;
                double s = 0.45 * sine(a) + 0.30 * tri(b) + 0.25 * sine(a * 3.0);
                x[start + i] += (float) (s * env(t, 0.52, 0.005, 1.3) * 0.5);
            }
        }
        for (int i = frames(0.60); i < x.length; i++) {
            if (r.nextDouble() < 0.015) x[i] += (float) (noise(r) * 0.14);
        }
        echo(x, 0.14, 0.30);
        return x;
    }

    /** 重生护盾：气泡张开的下滑噪声 + 柔和上升哨音。 */
    private static float[] respawn() {
        int n = frames(0.60);
        float[] x = new float[n];
        Random r = new Random(305);
        double lp = 0, ph = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double p = Math.min(1.0, t / 0.36);
            lp += (0.04 + 0.18 * (1.0 - p)) * (noise(r) - lp);
            double air = lp * Math.sin(p * Math.PI) * 0.85;
            double f = 300 + 900 * p;
            ph += f / SAMPLE_RATE;
            double tone = (0.6 * sine(ph) + 0.4 * tri(ph * 2.0))
                    * Math.min(1.0, t / 0.06) * (t < 0.36 ? 0.5 : Math.exp(-(t - 0.36) * 6));
            x[i] = (float) ((air + tone * 0.55) * env(t, 0.60, 0.003, 1.0));
        }
        echo(x, 0.08, 0.26);
        return x;
    }

    /** 水雷：声呐式下滑金属"叮",带长尾巴。 */
    private static float[] mine() {
        int n = frames(0.42);
        float[] x = new float[n];
        double ph = 0, ph2 = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double f = 1400 - 620 * Math.min(1.0, t / 0.30);
            ph += f / SAMPLE_RATE;
            ph2 += f * 1.48 / SAMPLE_RATE;
            double s = 0.50 * sine(ph) + 0.28 * sine(ph2) + 0.22 * square(ph, 0.25);
            x[i] = (float) (s * env(t, 0.42, 0.0015, 1.5) * 0.9);
        }
        echo(x, 0.11, 0.32);
        return x;
    }

    /** 护盾格挡：短促的电力"嗤"，带一点低频闷响。 */
    private static float[] shieldBlock() {
        int n = frames(0.22);
        float[] x = new float[n];
        Random r = new Random(306);
        double lp = 0, ph = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            lp += 0.42 * (noise(r) - lp);
            double f = 1500 - 700 * (t / 0.22);
            ph += f / SAMPLE_RATE;
            double zap = lp * Math.exp(-t * 16) * 1.15;
            double body = (0.5 * sine(ph) + 0.5 * square(ph, 0.3))
                    * Math.exp(-t * 19) * 0.45;
            double thud = sine((60 + 30 * Math.exp(-t * 12)) * t)
                    * Math.exp(-t * 22) * 0.4;
            x[i] = (float) ((zap + body + thud) * env(t, 0.22, 0.0012, 1.2));
        }
        return x;
    }

    // ---- synthesis helpers ----

    private static int frames(double seconds) {
        return Math.max(1, (int) (seconds * SAMPLE_RATE));
    }

    private static double sine(double phase) {
        return Math.sin(phase * TAU);
    }

    private static double square(double phase, double duty) {
        double f = phase - Math.floor(phase);
        return f < duty ? 1.0 : -1.0;
    }

    private static double saw(double phase) {
        double f = phase - Math.floor(phase);
        return 2.0 * f - 1.0;
    }

    private static double tri(double phase) {
        double f = phase - Math.floor(phase);
        return f < 0.5 ? 4.0 * f - 1.0 : 3.0 - 4.0 * f;
    }

    private static double noise(Random r) {
        return r.nextDouble() * 2.0 - 1.0;
    }

    private static double env(double t, double dur, double attack, double decay) {
        double a = attack <= 0.0 ? 1.0 : Math.min(1.0, t / attack);
        double left = Math.max(0.0, 1.0 - t / dur);
        return a * Math.pow(left, decay);
    }

    private static void echo(float[] x, double delaySec, double wet) {
        int d = Math.max(1, (int) (delaySec * SAMPLE_RATE));
        if (d >= x.length) return;
        for (int i = d; i < x.length; i++) {
            x[i] += (float) (x[i - d] * wet);
        }
    }

    private static void mix(float[] dst, float[] src, int offset, float gain) {
        int n = Math.min(src.length, dst.length - offset);
        for (int i = 0; i < n; i++) {
            dst[offset + i] += src[i] * gain;
        }
    }

    /** Removes tail clicks, sanitizes NaNs and normalizes the clip to {@code peak}. */
    private static float[] finish(float[] x, float peak) {
        int fade = Math.min(x.length, (int) (0.007 * SAMPLE_RATE));
        for (int i = 0; i < fade; i++) {
            float g = (float) i / fade;
            x[x.length - 1 - i] *= g;
        }
        float max = 0f;
        for (int i = 0; i < x.length; i++) {
            float v = x[i];
            if (Float.isNaN(v) || Float.isInfinite(v)) {
                x[i] = 0f;
                continue;
            }
            float a = Math.abs(v);
            if (a > max) max = a;
        }
        if (max < 1e-5f) return x;
        float scale = peak / max;
        for (int i = 0; i < x.length; i++) {
            float v = x[i] * scale;
            x[i] = v > 1f ? 1f : (v < -1f ? -1f : v);
        }
        return x;
    }
}
