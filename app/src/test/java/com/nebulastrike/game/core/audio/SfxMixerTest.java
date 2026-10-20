package com.nebulastrike.game.core.audio;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 混音器：静音、声像、清空与限声。 */
public class SfxMixerTest {
    private static SfxBank bank;

    @BeforeClass
    public static void setUp() {
        bank = SfxBank.create();
    }

    @Test
    public void idleRenderIsSilent() {
        SfxMixer mixer = new SfxMixer(bank);
        float[] out = new float[512 * 2];
        Arrays.fill(out, 1f);
        mixer.render(out, 512);
        for (float v : out) assertEquals(0f, v, 0f);
    }

    @Test
    public void voiceIsAudibleAndPansLeft() {
        SfxMixer mixer = new SfxMixer(bank);
        mixer.play(SfxId.SHOOT_VULCAN, 1f, 1f, -1f);
        float[] out = new float[512 * 2];
        mixer.render(out, 512);
        double left = 0, right = 0;
        for (int i = 0; i < out.length; i += 2) {
            left += out[i] * out[i];
            right += out[i + 1] * out[i + 1];
        }
        assertTrue("voice should be audible", left > 1e-6);
        assertTrue("hard-left should dominate: L=" + left + " R=" + right, left > right * 9);
    }

    @Test
    public void clearStopsActiveVoices() {
        SfxMixer mixer = new SfxMixer(bank);
        mixer.play(SfxId.BOMB, 1f, 1f, 0f);
        mixer.render(new float[512 * 2], 512);
        mixer.clear();
        float[] out = new float[512 * 2];
        mixer.render(out, 512);
        for (float v : out) assertEquals(0f, v, 0f);
    }

    @Test
    public void voiceStealingKeepsOutputBounded() {
        SfxMixer mixer = new SfxMixer(bank);
        for (int i = 0; i < 200; i++) {
            mixer.play(SfxId.EXPLODE_HEAVY, 1f, 1f, 0f);
        }
        float[] out = new float[512 * 2];
        for (int block = 0; block < 20; block++) {
            mixer.render(out, 512);
            for (float v : out) {
                assertTrue("non-finite sample", Float.isFinite(v));
                assertTrue("sample out of range: " + v, Math.abs(v) <= 1f);
            }
        }
    }
}
