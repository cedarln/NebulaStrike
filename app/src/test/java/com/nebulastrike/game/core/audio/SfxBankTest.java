package com.nebulastrike.game.core.audio;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** 合成音效库的波形健全性：可听、无 NaN、无削波、尾音无爆音。 */
public class SfxBankTest {
    private static SfxBank bank;

    @BeforeClass
    public static void setUp() {
        bank = SfxBank.create();
    }

    @Test
    public void everyClipIsAudibleFiniteAndBounded() {
        for (SfxId id : SfxId.values()) {
            float[] clip = bank.clip(id);
            assertNotNull(id.name(), clip);
            assertTrue(id.name() + " too short", clip.length >= SfxBank.SAMPLE_RATE / 20);
            float peak = 0f;
            for (float v : clip) {
                assertTrue(id.name() + " contains NaN/Inf", Float.isFinite(v));
                peak = Math.max(peak, Math.abs(v));
            }
            assertTrue(id.name() + " too quiet: " + peak, peak >= 0.30f);
            assertTrue(id.name() + " clips: " + peak, peak <= 1.0f);
            assertTrue(id.name() + " tail click: " + clip[clip.length - 1],
                    Math.abs(clip[clip.length - 1]) < 0.02f);
        }
    }

    @Test
    public void synthesisIsDeterministic() {
        SfxBank again = SfxBank.create();
        for (SfxId id : SfxId.values()) {
            float[] a = bank.clip(id);
            float[] b = again.clip(id);
            assertEquals(id.name(), a.length, b.length);
            for (int i = 0; i < a.length; i += Math.max(1, a.length / 13)) {
                assertEquals(id.name(), a[i], b[i], 0f);
            }
        }
    }

    @Test
    public void laserHasMoreZeroCrossingsThanExplosion() {
        int laser = zeroCrossings(bank.clip(SfxId.SHOOT_LASER));
        int boom = zeroCrossings(bank.clip(SfxId.EXPLODE));
        assertTrue("laser should be brighter than a blast", laser > boom);
    }

    private static int zeroCrossings(float[] samples) {
        int n = 0;
        for (int i = 1; i < samples.length; i++) {
            if ((samples[i - 1] < 0f) != (samples[i] < 0f)) n++;
        }
        return n;
    }
}
