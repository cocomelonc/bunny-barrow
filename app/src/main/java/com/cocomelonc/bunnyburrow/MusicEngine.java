/*
 * Bunny Burrow
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.bunnyburrow;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Original procedural background music. No recordings, samples, codecs, or
 * third-party music files are bundled; the generated composition is MIT-licensed.
 */
final class MusicEngine {
    private static final int SAMPLE_RATE = 22_050;
    private static final double BEAT_SECONDS = 0.75;
    private static final int BEAT_COUNT = 16;
    private static final double MUSIC_VOLUME = 0.070;

    private static final double[][] CHORDS = {
            {73.42, 110.00, 146.83},
            {61.74, 92.50, 123.47},
            {98.00, 146.83, 196.00},
            {110.00, 164.81, 220.00}
    };
    private static final double[] MELODY = {
            369.99, 293.66, 440.00, 369.99,
            493.88, 369.99, 587.33, 493.88,
            392.00, 440.00, 493.88, 587.33,
            659.25, 554.37, 440.00, 329.63
    };

    private final Object lock = new Object();
    private final AudioManager audioManager;
    private final AudioFocusRequest focusRequest;
    private final ExecutorService musicExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "bunny-meadow-music");
        thread.setDaemon(true);
        return thread;
    });

    private volatile AudioTrack activeTrack;
    private volatile short[] renderedTheme;
    private boolean closed;
    private boolean desired;
    private boolean hasFocus;
    private boolean focusRequested;
    private boolean focusBlocked;
    private boolean playbackBlocked;
    private boolean musicScheduled;
    private int generation;

    MusicEngine(Context context) {
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(this::onAudioFocusChanged)
                .build();
    }

    /** Idempotently synchronizes music with the visible gameplay state. */
    void setPlaying(boolean shouldPlay) {
        synchronized (lock) {
            if (closed) {
                return;
            }
            if (!shouldPlay) {
                if (!desired && !hasFocus && !focusRequested && !focusBlocked
                        && !playbackBlocked && !musicScheduled && activeTrack == null) {
                    return;
                }
                desired = false;
                focusBlocked = false;
                playbackBlocked = false;
                stopMusicLocked();
                abandonFocusLocked();
                return;
            }

            desired = true;
            if (focusBlocked || playbackBlocked) {
                return;
            }
            if (audioManager == null) {
                hasFocus = true;
                startMusicLocked();
                return;
            }
            if (!focusRequested) {
                int result = audioManager.requestAudioFocus(focusRequest);
                focusRequested = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
                hasFocus = focusRequested;
                if (!focusRequested) {
                    focusBlocked = true;
                    return;
                }
            }
            if (hasFocus) {
                startMusicLocked();
            }
        }
    }

    private void onAudioFocusChanged(int change) {
        synchronized (lock) {
            if (closed) {
                return;
            }
            if (change == AudioManager.AUDIOFOCUS_GAIN) {
                hasFocus = true;
                if (desired && !playbackBlocked) {
                    startMusicLocked();
                }
            } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                hasFocus = false;
                stopMusicLocked();
            } else if (change == AudioManager.AUDIOFOCUS_LOSS) {
                hasFocus = false;
                focusRequested = false;
                focusBlocked = true;
                stopMusicLocked();
            }
        }
    }

    private void startMusicLocked() {
        if (closed || !desired || !hasFocus || musicScheduled || playbackBlocked) {
            return;
        }
        int requestedGeneration = ++generation;
        musicScheduled = true;
        try {
            musicExecutor.execute(() -> playLoop(requestedGeneration));
        } catch (RejectedExecutionException ignored) {
            if (generation == requestedGeneration) {
                musicScheduled = false;
                playbackBlocked = true;
            }
        }
    }

    private void stopMusicLocked() {
        generation++;
        musicScheduled = false;
        AudioTrack track = activeTrack;
        activeTrack = null;
        if (track != null) {
            try {
                track.pause();
                track.flush();
            } catch (IllegalStateException ignored) {
                // The playback thread will release an already-invalidated track.
            }
        }
    }

    private void abandonFocusLocked() {
        if (audioManager != null && focusRequested) {
            audioManager.abandonAudioFocusRequest(focusRequest);
        }
        focusRequested = false;
        hasFocus = false;
    }

    private void playLoop(int requestedGeneration) {
        AudioTrack track = null;
        boolean playbackReady = false;
        try {
            short[] pcm = renderedTheme;
            if (pcm == null) {
                pcm = renderTheme();
                renderedTheme = pcm;
            }
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(pcm.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            int written = track.write(pcm, 0, pcm.length);
            if (written != pcm.length
                    || track.getState() != AudioTrack.STATE_INITIALIZED
                    || track.setLoopPoints(0, pcm.length, -1) != AudioTrack.SUCCESS) {
                return;
            }

            synchronized (lock) {
                if (!isCurrentLocked(requestedGeneration)) {
                    return;
                }
                activeTrack = track;
            }
            track.play();
            playbackReady = true;
            while (isCurrent(requestedGeneration)) {
                Thread.sleep(120L);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException ignored) {
            // Unsupported audio hardware must never interrupt or crash gameplay.
        } finally {
            if (track != null) {
                try {
                    if (track.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) {
                        track.stop();
                    }
                } catch (IllegalStateException ignored) {
                    // A focus or lifecycle transition may already have stopped it.
                }
                track.release();
            }
            synchronized (lock) {
                if (activeTrack == track) {
                    activeTrack = null;
                }
                if (generation == requestedGeneration) {
                    musicScheduled = false;
                    if (!playbackReady && desired && hasFocus) {
                        playbackBlocked = true;
                    }
                }
            }
        }
    }

    private boolean isCurrent(int requestedGeneration) {
        synchronized (lock) {
            return isCurrentLocked(requestedGeneration);
        }
    }

    private boolean isCurrentLocked(int requestedGeneration) {
        return !closed && desired && hasFocus && generation == requestedGeneration;
    }

    /** Renders a quiet 12-second four-chord loop with a soft pentatonic melody. */
    static short[] renderTheme() {
        double duration = BEAT_SECONDS * BEAT_COUNT;
        int sampleCount = (int) Math.round(duration * SAMPLE_RATE);
        short[] pcm = new short[sampleCount];
        double chordDuration = BEAT_SECONDS * 4.0;

        for (int i = 0; i < sampleCount; i++) {
            double time = i / (double) SAMPLE_RATE;
            int beat = Math.min(BEAT_COUNT - 1, (int) (time / BEAT_SECONDS));
            int chord = Math.min(CHORDS.length - 1, beat / 4);
            double beatTime = time - beat * BEAT_SECONDS;
            double beatPhase = beatTime / BEAT_SECONDS;
            double chordPhase = (time % chordDuration) / chordDuration;

            double chordEdge = Math.min(1.0,
                    Math.min(chordPhase, 1.0 - chordPhase) * 24.0);
            double pad = 0.0;
            for (double frequency : CHORDS[chord]) {
                pad += Math.sin(Math.PI * 2.0 * frequency * time)
                        + 0.10 * Math.sin(Math.PI * 4.0 * frequency * time);
            }
            pad /= CHORDS[chord].length * 1.10;

            double noteFrequency = MELODY[beat];
            double noteEnvelope = Math.min(1.0, beatTime / 0.12)
                    * Math.pow(Math.max(0.0, 1.0 - beatPhase), 2.0);
            double melody = (Math.sin(Math.PI * 2.0 * noteFrequency * beatTime)
                    + 0.16 * Math.sin(Math.PI * 4.0 * noteFrequency * beatTime)) / 1.16;

            double loopEdge = Math.min(1.0,
                    Math.min(time, duration - time) / 0.32);
            double value = loopEdge
                    * (0.57 * pad * chordEdge + 0.43 * melody * noteEnvelope);
            pcm[i] = (short) (Short.MAX_VALUE * MUSIC_VOLUME * value);
        }
        return pcm;
    }

    void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            desired = false;
            stopMusicLocked();
            abandonFocusLocked();
            closed = true;
        }
        musicExecutor.shutdownNow();
    }
}
