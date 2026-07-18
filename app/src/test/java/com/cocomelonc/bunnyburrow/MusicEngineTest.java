/*
 * Bunny Burrow
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.bunnyburrow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class MusicEngineTest {
    @Test
    public void proceduralThemeIsQuietSmoothAndNonSilent() {
        short[] pcm = MusicEngine.renderTheme();
        int peak = 0;
        int nonZero = 0;
        for (short sample : pcm) {
            peak = Math.max(peak, Math.abs((int) sample));
            if (sample != 0) {
                nonZero++;
            }
        }

        assertEquals(264_600, pcm.length);
        assertTrue(nonZero > pcm.length / 2);
        assertTrue(peak > 300);
        assertTrue(peak < 5_000);
        assertEquals(0, pcm[0]);
        assertTrue(Math.abs((int) pcm[pcm.length - 1]) < 16);
    }
}
