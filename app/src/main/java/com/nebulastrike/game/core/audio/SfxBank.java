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
        put(SfxId.CRIT, crit(), 0.42f);
        put(SfxId.BULLET_POP, bulletPop(), 0.40f);
        put(SfxId.PARRY, parry(), 0.52f);
        put(SfxId.MUTATOR, mutatorRiser(), 0.60f);
        put(SfxId.MUTATOR_CLEAR, mutatorClear(), 0.50f);
        put(SfxId.GRAVITY, gravityDeploy(), 0.58f);
        put(SfxId.GRAVITY_ABSORB, gravityAbsorb(), 0.34f);
        put(SfxId.GRAVITY_BURST, gravityBurst(), 0.60f);
        put(SfxId.ULT_READY, ultReady(), 0.46f);
        put(SfxId.ULT_CAST, ultCast(), 0.92f);
        put(SfxId.ULT_LANCE, ultLance(), 0.56f);
        put(SfxId.MIRAGE_REVEAL, mirageReveal(), 0.62f);
        put(SfxId.EMP_BURST, empBurst(), 0.74f);
        put(SfxId.BROOD_CHIRP, broodChirp(), 0.48f);
        put(SfxId.ACHIEVEMENT, achievement(), 0.66f);
        put(SfxId.DAILY_START, dailyStart(), 0.6f);
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

    /** 暴击点缀音：两束高频泛音 + 极短的金属"叮"，音量刻意压低防止刷屏。 */
    private static float[] crit() {
        int n = frames(0.20);
        float[] x = new float[n];
        Random r = new Random(307);
        double ph1 = 0, ph2 = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double k = Math.exp(-t * 16);
            ph1 += 1980 / SAMPLE_RATE;
            ph2 += 2970 / SAMPLE_RATE;
            double bell = (0.6 * sine(ph1) + 0.4 * sine(ph2)) * k * 0.85;
            double tick = noise(r) * Math.exp(-t * 90) * 0.5;
            x[i] = (float) ((bell + tick) * env(t, 0.20, 0.0008, 1.0));
        }
        return x;
    }

    /** 点掉敌弹：极短的带通噪声脆裂 + 一记高频下滑"啪"，比暴击更轻更干。 */
    private static float[] bulletPop() {
        int n = frames(0.13);
        float[] x = new float[n];
        Random r = new Random(308);
        double bp = 0, prev = 0, ph = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double src = noise(r);
            // 一阶低通再取差分：剃掉噪声的中低频，只留脆响的那一下
            bp += 0.55 * (src - bp);
            double crack = (bp - prev) * Math.exp(-t * 40) * 1.4;
            prev = bp;
            ph += (2400 - 1500 * (t / 0.13)) / SAMPLE_RATE;
            double snap = (0.65 * sine(ph) + 0.35 * tri(ph)) * Math.exp(-t * 30) * 0.5;
            x[i] = (float) ((crack + snap) * env(t, 0.13, 0.0006, 1.6));
        }
        return x;
    }

    /** 弹反：上扬金属"铛"——失谐双锯齿 + 高频 bells，尾音带一点回声显得"打飞了"。 */
    private static float[] parry() {
        int n = frames(0.30);
        float[] x = new float[n];
        Random r = new Random(309);
        double ph1 = 0, ph2 = 0, ph3 = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double sweep = 1.0 + 0.9 * Math.min(1.0, t / 0.06);
            ph1 += 660 * sweep / SAMPLE_RATE;
            ph2 += 990 * sweep / SAMPLE_RATE;
            ph3 += 2640 / SAMPLE_RATE;
            double clash = (0.45 * saw(ph1) + 0.35 * saw(ph2)) * Math.exp(-t * 15);
            double bell = sine(ph3) * Math.exp(-t * 26) * 0.5;
            double pick = noise(r) * Math.exp(-t * 120) * 0.35;
            x[i] = (float) ((clash + bell + pick) * env(t, 0.30, 0.001, 1.1));
        }
        echo(x, 0.055, 0.22);
        return x;
    }

    /** 异变波开始：两记错拍上扫锯齿 + 噪声渐强，"世界要变调"的警报感。 */
    private static float[] mutatorRiser() {
        int n = frames(0.62);
        float[] x = new float[n];
        Random r = new Random(310);
        double ph1 = 0, ph2 = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double k = Math.min(1.0, t / 0.5);
            ph1 += (180 + 420 * k) / SAMPLE_RATE;
            ph2 += (271 + 640 * k) / SAMPLE_RATE;
            double body = (0.4 * saw(ph1) + 0.3 * saw(ph2)) * (0.25 + 0.75 * k);
            double hiss = noise(r) * 0.22 * k;
            x[i] = (float) ((body + hiss) * env(t, 0.62, 0.02, 0.9));
        }
        echo(x, 0.09, 0.28);
        return x;
    }

    /** 异变平息奖励：三音上行铃 + 一点噪声闪光，收尾给正反馈。 */
    private static float[] mutatorClear() {
        int n = frames(0.46);
        float[] x = new float[n];
        double ph = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double freq = t < 0.11 ? 660 : t < 0.22 ? 880 : 1320;
            double local = t - (t < 0.11 ? 0 : t < 0.22 ? 0.11 : 0.22);
            ph += freq / SAMPLE_RATE;
            double bell = sine(ph) * Math.exp(-local * 17) * 0.6;
            double spark = sine(ph * 3) * Math.exp(-local * 40) * 0.18;
            x[i] = (float) ((bell + spark) * env(t, 0.46, 0.001, 1.0));
        }
        echo(x, 0.07, 0.3);
        return x;
    }

    /**
     * 重力井登场：低频漩涡由低到高涌起再塌回，配一层反向吸入的噪声，
     * 听起来像"空间被拧开一个口"。
     */
    private static float[] gravityDeploy() {
        int n = frames(0.72);
        float[] x = new float[n];
        Random r = new Random(415);
        double ph = 0, swirl = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double k = Math.min(1.0, t / 0.45);
            // 基频先升到 210Hz 再塌回 60Hz：开口 → 稳定吸收
            double freq = t < 0.45 ? 70 + 140 * (t / 0.45) : 210 - 150 * ((t - 0.45) / 0.27);
            ph += freq / SAMPLE_RATE;
            swirl += (freq * 1.5) / SAMPLE_RATE;
            double body = (sine(ph) * 0.55 + tri(swirl) * 0.22) * (0.35 + 0.65 * Math.sin(k * Math.PI));
            // 吸入感：噪声随时间变密、末尾才被"吞掉"
            double hiss = noise(r) * 0.20 * Math.sin(k * Math.PI);
            x[i] = (float) ((body + hiss) * env(t, 0.72, 0.03, 0.8));
        }
        echo(x, 0.055, 0.34);
        return x;
    }

    /** 吞掉一颗敌弹：短促下行"咕"声，像东西掉进洞里。 */
    private static float[] gravityAbsorb() {
        int n = frames(0.16);
        float[] x = new float[n];
        double ph = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            ph += (980 - 720 * (t / 0.16)) / SAMPLE_RATE;
            x[i] = (float) (sine(ph) * env(t, 0.16, 0.002, 1.7));
        }
        return x;
    }

    /** 重力井喷还：下压后向上炸开的扇形喷射，收尾一记亮片和弦。 */
    private static float[] gravityBurst() {
        int n = frames(0.5);
        float[] x = new float[n];
        Random r = new Random(422);
        double ph1 = 0, ph2 = 0, ph3 = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double k = Math.min(1.0, t / 0.2);
            ph1 += (330 + 520 * k) / SAMPLE_RATE;
            ph2 += (495 + 660 * k) / SAMPLE_RATE;
            ph3 += (660 + 880 * k) / SAMPLE_RATE;
            double chord = (sine(ph1) + sine(ph2) * 0.7 + sine(ph3) * 0.5) * 0.34;
            double jet = noise(r) * 0.30 * Math.max(0.0, 1.0 - t / 0.3);
            x[i] = (float) ((chord * Math.exp(-t * 4.2) + jet * Math.exp(-t * 6.0)
                    + (chord + jet) * 0.35 * env(t, 0.5, 0.004, 1.4)));
        }
        echo(x, 0.07, 0.24);
        return x;
    }

    /**
     * 终极槽充满：四音上行铃 + 尾部持续颤音，告诉玩家"可以按了"，
     * 音量刻意低于开火音，免得在混战里盖掉命中反馈。
     */
    private static float[] ultReady() {
        int n = frames(0.62);
        float[] x = new float[n];
        double[] notes = {523.25, 659.25, 783.99, 1046.5};
        double ph = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            int step = (int) Math.min(notes.length - 1, t / 0.1);
            double trem = 1.0 + 0.18 * Math.sin(t * 330.0);
            ph += notes[step] * trem / SAMPLE_RATE;
            double bell = (sine(ph) + sine(ph * 2.0) * 0.35 + sine(ph * 3.17) * 0.16) * 0.3;
            x[i] = (float) (bell * env(t - step * 0.1, 0.2, 0.002, 1.1)
                    * Math.max(0.25, 1.0 - t / 0.7));
        }
        echo(x, 0.09, 0.22);
        return x;
    }

    /** 释放终极技：低频塌缩 + 破空爆音，末尾留下时停特有的金属余振。 */
    private static float[] ultCast() {
        int n = frames(1.05);
        float[] x = new float[n];
        Random r = new Random(508);
        double ph = 0, sub = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double k = Math.min(1.0, t / 0.3);
            // 基频从 180Hz 塌到 42Hz（时空"往下沉"），下方再叠一层 60Hz 次低频
            ph += (180 - 138 * k) / SAMPLE_RATE;
            sub += 60.0 / SAMPLE_RATE;
            double body = (sine(ph) * 0.6 + tri(ph * 0.5) * 0.25) * Math.exp(-t * 2.2);
            double boom = noise(r) * 0.55 * Math.exp(-t * 9.0);
            // 余振：高频金属泛音缓慢衰减，撑起时停那一段安静
            double ring = (sine(ph * 6.02) * 0.1 + sine(ph * 9.01) * 0.06)
                    * Math.max(0.0, 1.0 - t / 1.05) * Math.exp(-t * 1.6);
            x[i] = (float) ((body + boom + ring + sine(sub) * 0.3 * Math.exp(-t * 1.4))
                    * env(t, 1.05, 0.004, 0.7));
        }
        echo(x, 0.13, 0.3);
        return x;
    }

    /** 光矛齐射：连续破空声，一发比一发高，收尾是穿刺束特有的金属呼啸。 */
    private static float[] ultLance() {
        int n = frames(0.66);
        float[] x = new float[n];
        Random r = new Random(514);
        double ph = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            int idx = (int) (t / 0.055);
            double local = t - idx * 0.055;
            double freq = 740 + idx * 95 + local * 900;
            ph += freq / SAMPLE_RATE;
            double whistle = (sine(ph) * 0.4 + saw(ph * 0.5) * 0.18)
                    * Math.max(0.0, 1.0 - local / 0.055);
            double air = noise(r) * 0.16 * Math.max(0.0, 1.0 - local / 0.04);
            x[i] = (float) ((whistle + air) * env(t, 0.66, 0.003, 0.55)
                    * Math.max(0.2, 1.0 - t / 0.8));
        }
        echo(x, 0.045, 0.2);
        return x;
    }

    /**
     * 伪装者现形：一记向下跌落的方波"露馅"音 + 一小段撕裂噪声。
     * 音头刻意做脏，和干净的道具拾取音形成鲜明对比。
     */
    private static float[] mirageReveal() {
        int n = frames(0.36);
        float[] x = new float[n];
        Random r = new Random(613);
        double ph = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double f = 620 - 340 * Math.min(1.0, t / 0.16);
            ph += f / SAMPLE_RATE;
            double body = square(ph, 0.5) * 0.42 * Math.exp(-t * 7.0);
            double glitch = noise(r) * 0.30 * Math.max(0.0, 1.0 - t / 0.1)
                    * (r.nextDouble() < 0.35 ? 1.0 : 0.2);
            x[i] = (float) ((body + glitch) * env(t, 0.36, 0.002, 1.0));
        }
        echo(x, 0.04, 0.18);
        return x;
    }

    /** 磁暴艇 EMP 脉冲：低频塌缩上扫 + 电离嘶声，提示"直射弹这一轮会被掰弯"。 */
    private static float[] empBurst() {
        int n = frames(0.52);
        float[] x = new float[n];
        Random r = new Random(727);
        double ph1 = 0, ph2 = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double k = Math.min(1.0, t / 0.3);
            ph1 += (150 - 90 * k) / SAMPLE_RATE;
            ph2 += (430 - 260 * k) / SAMPLE_RATE;
            double thud = sine(ph1) * 0.55 * Math.exp(-t * 5.0);
            double hum = (saw(ph2) * 0.22 + sine(ph2 * 2.0) * 0.12) * Math.exp(-t * 3.4);
            double spark = noise(r) * 0.26 * Math.exp(-t * 9.0);
            x[i] = (float) ((thud + hum + spark) * env(t, 0.52, 0.003, 1.2));
        }
        echo(x, 0.085, 0.26);
        return x;
    }

    /** 蜂后产卵：三声带颤音的虫鸣式短音，音量不高但要"粘耳朵"，提醒清杂兵。 */
    private static float[] broodChirp() {
        int n = frames(0.42);
        float[] x = new float[n];
        Random r = new Random(841);
        double ph = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            int idx = (int) (t / 0.085);
            double local = t - idx * 0.085;
            double wobble = 1.0 + 0.22 * Math.sin(t * 90.0);
            double f = (330 + idx * 70) * wobble;
            ph += f / SAMPLE_RATE;
            double chirp = (sine(ph) * 0.34 + saw(ph * 0.5) * 0.12)
                    * Math.max(0.0, 1.0 - local / 0.085);
            double air = noise(r) * 0.08 * Math.max(0.0, 1.0 - local / 0.05);
            x[i] = (float) ((chirp + air) * env(t, 0.42, 0.002, 0.5)
                    * Math.max(0.35, 1.0 - t / 0.5));
        }
        echo(x, 0.035, 0.16);
        return x;
    }

    /**
     * 成就解锁：三音上行号角（根-五度-八度）+ 末尾高八度余韵。
     * 刻意做成"宽音程"而不是 {@code ultReady} 那种密集铃音——两者经常在同几秒内先后响起，
     * 音色拉开才分得出"攒满大招"和"拿下成就"。
     */
    private static float[] achievement() {
        int n = frames(0.9);
        float[] x = new float[n];
        double[] notes = {392.0, 587.33, 783.99};
        double ph = 0;
        double phTail = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            int step = (int) Math.min(notes.length - 1, t / 0.13);
            double local = t - step * 0.13;
            ph += notes[step] / SAMPLE_RATE;
            // 号角的方波头 + 一点三次泛音，音头比铃更"扁"
            double horn = (square(ph, 0.5) * 0.3 + sine(ph) * 0.34
                    + sine(ph * 3.0) * 0.08) * Math.max(0.0, 1.0 - local / 0.2);
            // 1.5 拍后挂一记高八度长音，像盖章的余韵
            double tail = t > 0.39 ? (sine(phTail) * 0.2 + sine(phTail * 2.0) * 0.07)
                    * Math.exp(-(t - 0.39) * 3.4) : 0.0;
            if (t > 0.39) phTail += 1567.98 / SAMPLE_RATE;
            x[i] = (float) ((horn + tail) * env(t, 0.9, 0.004, 1.0));
        }
        echo(x, 0.12, 0.24);
        return x;
    }

    /**
     * 每日挑战开局：低-高双音钟 + 一层极轻的呼吸垫。
     * 和 {@code achievement} 的三音号角刻意分开——日常局一开始就可能连解成就，
     * 两个音色撞车会让玩家分不清"今天开始了"和"刚刚解锁"。
     */
    private static float[] dailyStart() {
        int n = frames(1.1);
        float[] x = new float[n];
        double[] bells = {261.63, 392.0};
        double phA = 0, phB = 0;
        Random r = new Random(20190101);
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            int idx = t < 0.26 ? 0 : 1;
            double local = t - idx * 0.26;
            double f = bells[idx];
            // 钟声：基音 + 2.76 次泛音（金属感的那个不整数倍泛音），指数衰减
            phA += f / SAMPLE_RATE;
            phB += f * 2.76 / SAMPLE_RATE;
            double bell = (sine(phA) * 0.34 + sine(phB) * 0.13 + sine(phA * 2.0) * 0.08)
                    * Math.exp(-local * 4.2) * Math.max(0.0, 1.0 - local / 0.62);
            // 呼吸垫：整条铺底的高通噪声，只有 4% 音量，用来区分"这是新的一天"
            double pad = noise(r) * 0.04 * Math.min(1.0, t / 0.3) * Math.max(0.0, 1.0 - t / 1.1);
            x[i] = (float) ((bell + pad) * env(t, 1.1, 0.002, 0.8));
        }
        echo(x, 0.16, 0.3);
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
