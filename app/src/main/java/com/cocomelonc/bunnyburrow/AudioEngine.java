/*
 * Bunny Burrow
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.bunnyburrow;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Gentle procedural sounds with no bundled audio assets or codecs. */
final class AudioEngine {
    private static final int SAMPLE_RATE = 22_050;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "bunny-chime");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean closed;

    void playDig() {
        play(new float[]{196f, 247f}, 0.12f, 0.055f);
    }

    void playCarrot(int number) {
        float base = 523.25f * (1f + number * 0.1f);
        play(new float[]{base, base * 1.25f, base * 1.5f}, 0.40f, 0.13f);
    }

    void playLevelComplete() {
        play(new float[]{523.25f, 659.25f, 783.99f}, 0.56f, 0.14f);
    }

    void playJourneyComplete() {
        play(new float[]{392f, 523.25f, 659.25f, 783.99f}, 0.82f, 0.13f);
    }

    private void play(float[] frequencies, float durationSeconds, float volume) {
        if (closed) {
            return;
        }
        try {
            executor.execute(() -> synthesizeAndPlay(frequencies, durationSeconds, volume));
        } catch (RejectedExecutionException ignored) {
            // The optional sound was queued while the activity was closing.
        }
    }

    private void synthesizeAndPlay(float[] frequencies, float durationSeconds, float volume) {
        int sampleCount = Math.max(256, (int) (SAMPLE_RATE * durationSeconds));
        short[] pcm = new short[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            double time = i / (double) SAMPLE_RATE;
            double progress = i / (double) sampleCount;
            double attack = Math.min(1.0, progress / 0.05);
            double release = Math.pow(Math.max(0.0, 1.0 - progress), 2.6);
            double value = 0.0;
            for (int note = 0; note < frequencies.length; note++) {
                double stagger = note * 0.05;
                if (time >= stagger) {
                    double localTime = time - stagger;
                    value += Math.sin(Math.PI * 2.0 * frequencies[note] * localTime)
                            + 0.16 * Math.sin(Math.PI * 4.0 * frequencies[note] * localTime);
                }
            }
            value /= frequencies.length * 1.16;
            pcm[i] = (short) (Short.MAX_VALUE * volume * attack * release * value);
        }

        AudioTrack track = null;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(pcm.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            if (track.getState() != AudioTrack.STATE_INITIALIZED
                    || track.write(pcm, 0, pcm.length) < 0) {
                return;
            }
            track.play();
            Thread.sleep((long) (durationSeconds * 1000f) + 40L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException ignored) {
            // Optional sound must never interrupt play on unusual audio stacks.
        } finally {
            if (track != null) {
                try {
                    if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop();
                    }
                } catch (IllegalStateException ignored) {
                    // A vendor audio stack may invalidate a track during shutdown.
                }
                track.release();
            }
        }
    }

    void close() {
        closed = true;
        executor.shutdownNow();
    }
}
