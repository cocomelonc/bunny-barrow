/*
 * Bunny Burrow
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.bunnyburrow;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Dependency-free renderer and touch layer. A fixed 1280x720 logical canvas
 * scales uniformly to phones, tablets, foldables, and resizable windows.
 */
final class BunnyBurrowView extends View implements BunnyWorld.Listener {
    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;
    private static final float BOARD_LEFT = 164f;
    private static final float BOARD_TOP = 102f;
    private static final float TILE = 68f;
    private static final float PAUSE_X = 1218f;
    private static final float PAUSE_Y = 53f;

    // The overlay card deliberately keeps at least 68 logical pixels of inner padding.
    private static final float OVERLAY_LEFT = 310f;
    private static final float OVERLAY_TOP = 168f;
    private static final float OVERLAY_RIGHT = 970f;
    private static final float OVERLAY_BOTTOM = 552f;
    private static final float OVERLAY_PADDING = 72f;

    private static final String PREFS = "bunny_burrow_progress";
    private static final String PREF_RESUME_LEVEL = "resume_level";
    private static final String PREF_LANGUAGE = "language";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final android.graphics.Typeface regular;
    private final android.graphics.Typeface bold;
    private final SharedPreferences preferences;
    private final AudioEngine audio = new AudioEngine();
    private final MusicEngine music;
    private final BunnyWorld world;
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random(0xB00B177L);

    private Context localizedContext;
    private String language;
    private LinearGradient outsideGradient;
    private LinearGradient levelGradient;
    private float viewScale = 1f;
    private float viewOffsetX;
    private float viewOffsetY;
    private long lastFrameNanos;
    private long lastDigSoundNanos;
    private boolean hostResumed = true;
    private boolean drawingTunnel;
    private int lastTouchRow = -1;
    private int lastTouchCol = -1;
    private float hintTime;
    private BunnyWorld.State lastVisualState = BunnyWorld.State.TITLE;
    private float overlayProgress = 1f;

    BunnyBurrowView(Context context) {
        super(context);
        music = new MusicEngine(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setKeepScreenOn(true);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        language = preferences.getString(PREF_LANGUAGE, "en");
        if (!"ru".equals(language)) {
            language = "en";
        }
        applyLanguage(language);
        android.graphics.Typeface font = context.getResources().getFont(R.font.nunito);
        regular = android.graphics.Typeface.create(font, android.graphics.Typeface.NORMAL);
        bold = android.graphics.Typeface.create(font, android.graphics.Typeface.BOLD);
        setContentDescription(text(R.string.accessibility_game));

        world = new BunnyWorld(BurrowLevel.createAll(), this);
        outsideGradient = new LinearGradient(
                0f, 0f, 0f, WORLD_HEIGHT,
                new int[]{0xFFF8EADF, 0xFFE4E7DD, 0xFFDDD8E9},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP
        );
        rebuildLevelGradient();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        viewScale = Math.min(width / WORLD_WIDTH, height / WORLD_HEIGHT);
        viewOffsetX = (width - WORLD_WIDTH * viewScale) * 0.5f;
        viewOffsetY = (height - WORLD_HEIGHT * viewScale) * 0.5f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0f : (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        dt = Math.min(dt, 0.05f);
        if (hostResumed) {
            world.update(dt);
            updateParticles(dt);
            hintTime += dt;
        }

        BunnyWorld.State state = world.getState();
        music.setPlaying(hostResumed && state == BunnyWorld.State.PLAYING);
        if (state != lastVisualState) {
            lastVisualState = state;
            overlayProgress = isOverlayState(state) ? 0f : 1f;
        }
        if (isOverlayState(state) && hostResumed) {
            overlayProgress = Math.min(1f, overlayProgress + dt * 5.5f);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(outsideGradient);
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
        paint.setShader(null);

        canvas.save();
        canvas.translate(viewOffsetX, viewOffsetY);
        canvas.scale(viewScale, viewScale);
        float time = now / 1_000_000_000f;
        if (state == BunnyWorld.State.TITLE) {
            drawTitle(canvas, time);
        } else {
            drawLevel(canvas, time);
        }
        canvas.restore();

        if (hostResumed) {
            postInvalidateOnAnimation();
        }
    }

    private static boolean isOverlayState(BunnyWorld.State state) {
        return state == BunnyWorld.State.PAUSED
                || state == BunnyWorld.State.LEVEL_COMPLETE
                || state == BunnyWorld.State.JOURNEY_COMPLETE;
    }

    private void drawTitle(Canvas canvas, float time) {
        paint.setShader(outsideGradient);
        canvas.drawRect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT, paint);
        paint.setShader(null);

        drawSun(canvas, 151f, 126f);
        drawCloud(canvas, 1030f, 120f, 1.03f);
        drawCloud(canvas, 252f, 264f, 0.68f);
        drawFittedText(canvas, text(R.string.game_title), 654f, 154f,
                70f, 760f, 0xFF665C6D, true);
        drawFittedText(canvas, text(R.string.game_subtitle), 654f, 211f,
                25f, 710f, 0xFF8A7E8D, false);

        drawTitleLandscape(canvas, time);
        canvas.save();
        canvas.translate(326f, 432f + (float) Math.sin(time * 2f) * 2f);
        canvas.scale(1.62f, 1.62f);
        drawRabbitAtOrigin(canvas, time, 1f, 0f);
        canvas.restore();

        float pulse = 0.98f + 0.025f * (float) Math.sin(time * 2.6f);
        canvas.save();
        canvas.scale(pulse, pulse, 706f, 337f);
        drawPill(canvas, 515f, 294f, 897f, 380f, 0xF8FFF9EF, 0x255F5260);
        drawFittedText(canvas, text(R.string.touch_to_begin), 706f, 347f,
                29f, 330f, 0xFF685E70, true);
        canvas.restore();

        drawFittedText(canvas, text(R.string.draw_to_dig), 716f, 424f,
                21f, 590f, 0xE9685F6D, false);
        drawFittedText(canvas, text(R.string.find_carrots), 716f, 459f,
                21f, 590f, 0xE9685F6D, false);
        drawLanguageSwitch(canvas, 1167f, 56f);

        int resume = preferences.getInt(PREF_RESUME_LEVEL, 0);
        float first = 640f - (world.getLevelCount() - 1) * 11f;
        for (int i = 0; i < world.getLevelCount(); i++) {
            paint.setColor(i == resume ? 0xFFF5AB62 : 0x78FFFFFF);
            canvas.drawCircle(first + i * 22f, 674f, i == resume ? 6f : 4f, paint);
        }
    }

    private void drawTitleLandscape(Canvas canvas, float time) {
        path.reset();
        path.moveTo(0f, 547f);
        path.cubicTo(205f, 497f, 332f, 538f, 508f, 516f);
        path.cubicTo(728f, 485f, 895f, 535f, 1280f, 500f);
        path.lineTo(1280f, 720f);
        path.lineTo(0f, 720f);
        path.close();
        paint.setColor(0xFFB9D1B1);
        canvas.drawPath(path, paint);

        paint.setColor(0xFFD6B392);
        canvas.drawRect(0f, 585f, WORLD_WIDTH, WORLD_HEIGHT, paint);
        paint.setColor(0xFFE2C09F);
        for (int i = 0; i < 12; i++) {
            float x = 35f + i * 112f;
            canvas.drawCircle(x, 632f + Math.floorMod(i * 19, 48), 5f, paint);
            canvas.drawCircle(x + 42f, 683f - Math.floorMod(i * 13, 36), 3f, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(26f);
        paint.setColor(0xFFF3D9B6);
        path.reset();
        path.moveTo(392f, 604f);
        path.cubicTo(493f, 655f, 587f, 604f, 668f, 650f);
        path.cubicTo(760f, 700f, 848f, 620f, 948f, 664f);
        canvas.drawPath(path, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
        drawCarrot(canvas, 685f, 636f, time, false);
        drawCarrot(canvas, 944f, 642f, time + 1.5f, false);
        drawBurrowOpening(canvas, 1100f, 548f, true, time);
        drawTitleFlowers(canvas, time);
    }

    private void drawLevel(Canvas canvas, float time) {
        paint.setShader(levelGradient);
        canvas.drawRect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT, paint);
        paint.setShader(null);
        drawCloud(canvas, 72f, 56f, 0.54f);
        drawCloud(canvas, 1088f, 651f, 0.46f);

        drawBoard(canvas, time);
        drawParticles(canvas);
        float bunnyX = BOARD_LEFT + (world.getVisualCol() + 0.5f) * TILE;
        float bunnyY = BOARD_TOP + (world.getVisualRow() + 0.5f) * TILE
                + 8f - world.getHopHeight() * 18f;
        canvas.save();
        canvas.translate(bunnyX, bunnyY);
        canvas.scale(0.66f, 0.66f);
        drawRabbitAtOrigin(canvas, time, world.getFacing(), world.getHopHeight());
        canvas.restore();
        drawHud(canvas, time);

        if (hintTime < 6f && world.getState() == BunnyWorld.State.PLAYING) {
            float alpha = hintTime < 4.4f ? 1f : Math.max(0f, (6f - hintTime) / 1.6f);
            drawHint(canvas, alpha);
        }

        if (world.getState() == BunnyWorld.State.PAUSED) {
            drawOverlay(canvas, R.string.paused, R.string.touch_to_continue, time, true);
        } else if (world.getState() == BunnyWorld.State.LEVEL_COMPLETE) {
            drawOverlay(canvas, R.string.level_complete, R.string.touch_to_continue, time, false);
        } else if (world.getState() == BunnyWorld.State.JOURNEY_COMPLETE) {
            drawJourneyComplete(canvas, time);
        }
    }

    private void drawBoard(Canvas canvas, float time) {
        BurrowLevel level = world.getLevel();
        rect.set(BOARD_LEFT - 12f, BOARD_TOP - 12f,
                BOARD_LEFT + BurrowLevel.COLS * TILE + 12f,
                BOARD_TOP + BurrowLevel.ROWS * TILE + 12f);
        paint.setColor(0x2A5B4B50);
        canvas.drawRoundRect(rect.left + 5f, rect.top + 8f, rect.right + 5f,
                rect.bottom + 8f, 31f, 31f, paint);
        paint.setColor(0xC8F9EEE1);
        canvas.drawRoundRect(rect, 31f, 31f, paint);

        for (int row = 0; row < BurrowLevel.ROWS; row++) {
            for (int col = 0; col < BurrowLevel.COLS; col++) {
                float left = BOARD_LEFT + col * TILE;
                float top = BOARD_TOP + row * TILE;
                paint.setColor(((row + col) & 1) == 0 ? level.soilA : level.soilB);
                canvas.drawRoundRect(left + 1.5f, top + 1.5f,
                        left + TILE - 1.5f, top + TILE - 1.5f, 13f, 13f, paint);
                drawSoilDetails(canvas, left, top, row, col, level.seed);
            }
        }

        drawTunnels(canvas, level);

        for (int row = 0; row < BurrowLevel.ROWS; row++) {
            for (int col = 0; col < BurrowLevel.COLS; col++) {
                float cx = BOARD_LEFT + (col + 0.5f) * TILE;
                float cy = BOARD_TOP + (row + 0.5f) * TILE;
                char tile = level.tileAt(row, col);
                if (tile == BurrowLevel.ROCK) {
                    drawRock(canvas, cx, cy, level.rockColor);
                } else if (tile == BurrowLevel.ROOT) {
                    drawRoot(canvas, cx, cy, level.rootColor, row + col);
                } else if (tile == BurrowLevel.WATER) {
                    drawWater(canvas, cx, cy, level.waterColor, time, row + col);
                } else if (tile == BurrowLevel.CARROT
                        && !world.isCarrotCollected(row, col)) {
                    drawCarrot(canvas, cx, cy + 5f, time + row + col, false);
                } else if (tile == BurrowLevel.START) {
                    drawStartRing(canvas, cx, cy);
                } else if (tile == BurrowLevel.EXIT) {
                    drawBurrowOpening(canvas, cx, cy,
                            world.getCarrotsCollected() == level.carrotCount, time);
                }
            }
        }
        drawPlannedMarker(canvas, time);
    }

    private void drawTunnels(Canvas canvas, BurrowLevel level) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(35f);
        paint.setColor(darken(level.tunnelColor, 0.91f));
        for (int row = 0; row < BurrowLevel.ROWS; row++) {
            for (int col = 0; col < BurrowLevel.COLS; col++) {
                if (!world.isDug(row, col)) {
                    continue;
                }
                float cx = BOARD_LEFT + (col + 0.5f) * TILE;
                float cy = BOARD_TOP + (row + 0.5f) * TILE;
                if (col + 1 < BurrowLevel.COLS && world.isDug(row, col + 1)) {
                    canvas.drawLine(cx, cy, cx + TILE, cy, paint);
                }
                if (row + 1 < BurrowLevel.ROWS && world.isDug(row + 1, col)) {
                    canvas.drawLine(cx, cy, cx, cy + TILE, paint);
                }
            }
        }
        paint.setStrokeWidth(29f);
        paint.setColor(level.tunnelColor);
        for (int row = 0; row < BurrowLevel.ROWS; row++) {
            for (int col = 0; col < BurrowLevel.COLS; col++) {
                if (!world.isDug(row, col)) {
                    continue;
                }
                float cx = BOARD_LEFT + (col + 0.5f) * TILE;
                float cy = BOARD_TOP + (row + 0.5f) * TILE;
                if (col + 1 < BurrowLevel.COLS && world.isDug(row, col + 1)) {
                    canvas.drawLine(cx, cy, cx + TILE, cy, paint);
                }
                if (row + 1 < BurrowLevel.ROWS && world.isDug(row + 1, col)) {
                    canvas.drawLine(cx, cy, cx, cy + TILE, paint);
                }
            }
        }
        paint.setStyle(Paint.Style.FILL);
        for (int row = 0; row < BurrowLevel.ROWS; row++) {
            for (int col = 0; col < BurrowLevel.COLS; col++) {
                if (world.isDug(row, col)) {
                    float cx = BOARD_LEFT + (col + 0.5f) * TILE;
                    float cy = BOARD_TOP + (row + 0.5f) * TILE;
                    paint.setColor(level.tunnelColor);
                    canvas.drawCircle(cx, cy, 17.5f, paint);
                    paint.setColor(0x45FFFFFF);
                    canvas.drawCircle(cx - 5f, cy - 5f, 3f, paint);
                }
            }
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawSoilDetails(Canvas canvas, float left, float top,
                                 int row, int col, int seed) {
        int value = seed + row * 89 + col * 47;
        if (Math.floorMod(value, 4) == 0) {
            paint.setColor(0x307B5D4D);
            canvas.drawCircle(left + 15f + Math.floorMod(value, 33), top + 20f, 2.5f, paint);
            canvas.drawCircle(left + 25f, top + 48f, 1.8f, paint);
        }
        if (Math.floorMod(value, 9) == 0) {
            paint.setColor(0x42FFF0D4);
            canvas.drawOval(left + 48f, top + 43f, left + 54f, top + 47f, paint);
        }
    }

    private void drawRock(Canvas canvas, float cx, float cy, int color) {
        path.reset();
        path.moveTo(cx - 27f, cy + 15f);
        path.quadTo(cx - 31f, cy - 8f, cx - 12f, cy - 25f);
        path.quadTo(cx + 16f, cy - 28f, cx + 29f, cy - 3f);
        path.quadTo(cx + 31f, cy + 20f, cx + 8f, cy + 26f);
        path.quadTo(cx - 13f, cy + 27f, cx - 27f, cy + 15f);
        paint.setColor(color);
        canvas.drawPath(path, paint);
        paint.setColor(0x66FFFFFF);
        canvas.drawOval(cx - 13f, cy - 17f, cx + 9f, cy - 8f, paint);
        paint.setColor(0x24705C62);
        canvas.drawCircle(cx + 13f, cy + 11f, 3f, paint);
    }

    private void drawRoot(Canvas canvas, float cx, float cy, int color, int phase) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(7f);
        paint.setColor(color);
        path.reset();
        path.moveTo(cx - 23f, cy - 29f);
        path.cubicTo(cx - 10f, cy - 8f, cx + 4f, cy - 3f, cx + 20f, cy + 28f);
        canvas.drawPath(path, paint);
        paint.setStrokeWidth(4f);
        path.reset();
        path.moveTo(cx - 3f, cy - 3f);
        path.quadTo(cx + 14f, cy - 12f, cx + 24f, cy - 18f);
        path.moveTo(cx + 6f, cy + 8f);
        path.quadTo(cx - 9f, cy + 17f, cx - 19f, cy + 23f);
        canvas.drawPath(path, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x55FFF0D4);
        canvas.drawCircle(cx - 18f + Math.floorMod(phase, 5), cy - 19f, 3f, paint);
    }

    private void drawWater(Canvas canvas, float cx, float cy, int color, float time, int phase) {
        paint.setColor(color);
        canvas.drawRoundRect(cx - TILE / 2f + 1.5f, cy - TILE / 2f + 1.5f,
                cx + TILE / 2f - 1.5f, cy + TILE / 2f - 1.5f, 12f, 12f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(2.2f);
        paint.setColor(0x80F5FFFF);
        float offset = (float) Math.sin(time * 1.8f + phase) * 3f;
        canvas.drawArc(cx - 25f + offset, cy - 10f, cx + 2f + offset, cy + 2f,
                195f, 95f, false, paint);
        canvas.drawArc(cx - 3f - offset, cy + 9f, cx + 25f - offset, cy + 21f,
                195f, 95f, false, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawCarrot(Canvas canvas, float cx, float cy, float time, boolean tiny) {
        float scale = tiny ? 0.72f : 1f;
        float bob = (float) Math.sin(time * 2.4f) * 2.2f;
        canvas.save();
        canvas.translate(cx, cy + bob);
        canvas.scale(scale, scale);
        paint.setColor(0xFF719B68);
        path.reset();
        path.moveTo(0f, -19f);
        path.cubicTo(-18f, -34f, -23f, -23f, -5f, -12f);
        path.cubicTo(-1f, -35f, 11f, -37f, 8f, -12f);
        path.cubicTo(22f, -29f, 29f, -16f, 7f, -7f);
        path.close();
        canvas.drawPath(path, paint);
        paint.setColor(0xFFF0A05B);
        path.reset();
        path.moveTo(-14f, -10f);
        path.quadTo(0f, -17f, 14f, -10f);
        path.quadTo(9f, 18f, 0f, 29f);
        path.quadTo(-10f, 15f, -14f, -10f);
        path.close();
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0x65C96B45);
        canvas.drawLine(-8f, 0f, 2f, -2f, paint);
        canvas.drawLine(-5f, 10f, 4f, 8f, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();
    }

    private void drawStartRing(Canvas canvas, float cx, float cy) {
        paint.setColor(0x3668514B);
        canvas.drawOval(cx - 27f, cy - 19f, cx + 27f, cy + 20f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setColor(0x8BF8DFBC);
        canvas.drawOval(cx - 25f, cy - 18f, cx + 25f, cy + 18f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawBurrowOpening(Canvas canvas, float cx, float cy, boolean open, float time) {
        if (open) {
            paint.setColor(0x48FFF3A8);
            canvas.drawCircle(cx, cy, 33f + (float) Math.sin(time * 2f) * 3f, paint);
        }
        paint.setColor(open ? 0xFFFFDEA0 : 0xFFB9A690);
        canvas.drawOval(cx - 29f, cy - 25f, cx + 29f, cy + 26f, paint);
        paint.setColor(open ? 0xFF8EC5A0 : 0xFF796A65);
        canvas.drawOval(cx - 21f, cy - 17f, cx + 21f, cy + 24f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(open ? 0xFFF7E8B4 : 0xFFAA9787);
        canvas.drawLine(cx - 11f, cy - 9f, cx - 11f, cy + 17f, paint);
        canvas.drawLine(cx + 11f, cy - 9f, cx + 11f, cy + 17f, paint);
        canvas.drawLine(cx - 11f, cy - 2f, cx + 11f, cy - 2f, paint);
        canvas.drawLine(cx - 11f, cy + 8f, cx + 11f, cy + 8f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawPlannedMarker(Canvas canvas, float time) {
        if (world.getState() != BunnyWorld.State.PLAYING || !world.isMoving()) {
            return;
        }
        float cx = BOARD_LEFT + (world.getPlannedCol() + 0.5f) * TILE;
        float cy = BOARD_TOP + (world.getPlannedRow() + 0.5f) * TILE;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(0xB4FFF3D4);
        canvas.drawCircle(cx, cy, 16f + (float) Math.sin(time * 4f) * 3f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawRabbitAtOrigin(Canvas canvas, float time, float facing, float hop) {
        canvas.save();
        canvas.scale(facing < 0f ? -1f : 1f, 1f);
        float idle = hop > 0f ? 0f : (float) Math.sin(time * 2.2f) * 0.8f;
        canvas.translate(0f, idle);

        paint.setColor(0x2865575C);
        canvas.drawOval(-50f, 34f, 56f, 46f, paint);
        paint.setColor(0xFFFFFAED);
        canvas.drawCircle(-44f, 7f, 25f, paint);
        paint.setColor(0xFFD5B19A);
        canvas.drawCircle(-44f, 7f, 13f, paint);

        paint.setColor(0xFFC7A28E);
        canvas.drawOval(-34f, -11f, 34f, 42f, paint);
        paint.setColor(0xFFD6B29E);
        canvas.drawOval(5f, -20f, 59f, 37f, paint);

        paint.setColor(0xFFC7A28E);
        path.reset();
        path.moveTo(11f, -16f);
        path.cubicTo(-1f, -53f, 2f, -82f, 17f, -82f);
        path.cubicTo(34f, -80f, 28f, -43f, 26f, -13f);
        path.close();
        canvas.drawPath(path, paint);
        path.reset();
        path.moveTo(29f, -15f);
        path.cubicTo(29f, -55f, 42f, -81f, 55f, -74f);
        path.cubicTo(68f, -64f, 48f, -34f, 43f, -7f);
        path.close();
        canvas.drawPath(path, paint);
        paint.setColor(0xFFF2B5B0);
        path.reset();
        path.moveTo(13f, -23f);
        path.cubicTo(8f, -53f, 10f, -71f, 17f, -72f);
        path.cubicTo(23f, -68f, 20f, -45f, 20f, -22f);
        path.close();
        canvas.drawPath(path, paint);
        path.reset();
        path.moveTo(35f, -20f);
        path.cubicTo(36f, -49f, 46f, -69f, 52f, -65f);
        path.cubicTo(57f, -57f, 44f, -35f, 41f, -16f);
        path.close();
        canvas.drawPath(path, paint);

        paint.setColor(0xFF62575D);
        canvas.drawCircle(40f, -4f, 4.8f, paint);
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(41.4f, -5.4f, 1.5f, paint);
        paint.setColor(0xFF544A50);
        canvas.drawCircle(58f, 10f, 5.5f, paint);
        paint.setColor(0x74EF9E9C);
        canvas.drawCircle(42f, 14f, 6.5f, paint);
        paint.setColor(0xFFB88F7D);
        canvas.drawOval(-17f, 33f, 2f, 43f, paint);
        canvas.drawOval(22f, 32f, 43f, 42f, paint);
        canvas.restore();
    }

    private void drawHud(Canvas canvas, float time) {
        BurrowLevel level = world.getLevel();
        drawPill(canvas, 178f, 18f, 1104f, 86f, 0xE7FFF9EF, 0x20594F58);
        drawCarrot(canvas, 224f, 51f, time, true);
        drawFittedText(canvas, text(R.string.carrots_label) + "  "
                        + world.getCarrotsCollected() + "/" + level.carrotCount,
                334f, 61f, 21f, 185f, 0xFF665B65, true);
        drawFittedText(canvas, (world.getLevelIndex() + 1) + " / " + world.getLevelCount()
                        + "  ·  " + text(level.nameRes),
                720f, 61f, 28f, 520f, 0xFF655B68, true);

        paint.setColor(0xEFFFFFF5);
        canvas.drawCircle(PAUSE_X, PAUSE_Y, 34f, paint);
        paint.setColor(0xFF716875);
        canvas.drawRoundRect(PAUSE_X - 9f, PAUSE_Y - 12f,
                PAUSE_X - 3f, PAUSE_Y + 12f, 3f, 3f, paint);
        canvas.drawRoundRect(PAUSE_X + 3f, PAUSE_Y - 12f,
                PAUSE_X + 9f, PAUSE_Y + 12f, 3f, 3f, paint);
    }

    private void drawHint(Canvas canvas, float alpha) {
        int a = Math.round(alpha * 226f);
        drawPill(canvas, 333f, 658f, 947f, 708f,
                Color.argb(a, 255, 248, 234),
                Color.argb(Math.round(alpha * 24f), 79, 67, 76));
        drawFittedText(canvas, text(R.string.draw_to_dig), 640f, 690f,
                19f, 560f, Color.argb(Math.round(alpha * 255f), 104, 91, 100), false);
    }

    private void drawOverlay(Canvas canvas, int titleRes, int subtitleRes,
                             float time, boolean showLanguage) {
        float eased = overlayProgress * overlayProgress * (3f - 2f * overlayProgress);
        paint.setColor(Color.argb(Math.round(126f * eased), 91, 78, 88));
        canvas.drawRect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT, paint);

        float cardScale = 0.95f + 0.05f * eased;
        canvas.save();
        canvas.scale(cardScale, cardScale, 640f, 360f);
        rect.set(OVERLAY_LEFT, OVERLAY_TOP, OVERLAY_RIGHT, OVERLAY_BOTTOM);
        paint.setColor(Color.argb(Math.round(248f * eased), 255, 249, 237));
        canvas.drawRoundRect(rect, 46f, 46f, paint);

        // Content width is bounded by the explicit 72 px padding on both sides.
        float contentWidth = OVERLAY_RIGHT - OVERLAY_LEFT - OVERLAY_PADDING * 2f;
        float iconPulse = 1f + 0.03f * (float) Math.sin(time * 2.2f);
        canvas.save();
        canvas.translate(640f, 251f);
        canvas.scale(iconPulse, iconPulse);
        drawCarrot(canvas, 0f, 0f, time, true);
        canvas.restore();
        drawFittedText(canvas, text(titleRes), 640f, 355f,
                39f, contentWidth, Color.argb(Math.round(255f * eased), 99, 88, 103), true);
        drawFittedText(canvas, text(subtitleRes), 640f, 420f,
                22f, contentWidth, Color.argb(Math.round(255f * eased), 133, 119, 132), false);
        if (showLanguage) {
            drawLanguageSwitch(canvas, 640f, 492f);
        }
        canvas.restore();
    }

    private void drawJourneyComplete(Canvas canvas, float time) {
        float eased = overlayProgress * overlayProgress * (3f - 2f * overlayProgress);
        paint.setColor(Color.argb(Math.round(132f * eased), 91, 78, 88));
        canvas.drawRect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT, paint);
        float cardScale = 0.95f + 0.05f * eased;
        canvas.save();
        canvas.scale(cardScale, cardScale, 640f, 360f);
        rect.set(278f, 117f, 1002f, 604f);
        paint.setColor(Color.argb(Math.round(250f * eased), 255, 249, 237));
        canvas.drawRoundRect(rect, 48f, 48f, paint);
        canvas.save();
        canvas.translate(640f, 270f);
        canvas.scale(1.25f, 1.25f);
        drawRabbitAtOrigin(canvas, time, 1f, 0f);
        canvas.restore();
        drawFittedText(canvas, text(R.string.journey_complete), 640f, 404f,
                41f, 580f, 0xFF625868, true);
        drawFittedText(canvas, text(R.string.journey_complete_subtitle), 640f, 457f,
                21f, 580f, 0xFF857786, false);
        drawPill(canvas, 452f, 494f, 828f, 560f, 0xFFFFDEA0, 0x245B505B);
        drawFittedText(canvas, text(R.string.play_again), 640f, 536f,
                23f, 325f, 0xFF675C68, true);
        canvas.restore();
    }

    private void drawLanguageSwitch(Canvas canvas, float cx, float cy) {
        drawPill(canvas, cx - 58f, cy - 27f, cx + 58f, cy + 27f,
                0xEFFFFFF4, 0x20584F5A);
        float selectedX = "en".equals(language) ? cx - 28f : cx + 28f;
        paint.setColor(0xFFFFCE78);
        canvas.drawCircle(selectedX, cy, 21f, paint);
        drawFittedText(canvas, "EN", cx - 28f, cy + 7f, 17f, 36f, 0xFF625866, true);
        drawFittedText(canvas, "RU", cx + 28f, cy + 7f, 17f, 36f, 0xFF625866, true);
    }

    private void drawTitleFlowers(Canvas canvas, float time) {
        int[] colors = {0xFFF3A7B5, 0xFFFFCC72, 0xFFB4A8D9, 0xFFF4BEA0};
        for (int i = 0; i < 16; i++) {
            float x = 25f + i * 83f;
            float y = 557f + Math.floorMod(i * 19, 26);
            drawTinyFlower(canvas, x, y, colors[i % colors.length], time + i);
        }
    }

    private void drawTinyFlower(Canvas canvas, float cx, float cy, int color, float time) {
        paint.setColor(0xFF73936C);
        canvas.drawRoundRect(cx - 1.5f, cy + 5f, cx + 1.5f, cy + 24f, 2f, 2f, paint);
        float pulse = 1f + 0.025f * (float) Math.sin(time * 2f);
        canvas.save();
        canvas.scale(pulse, pulse, cx, cy);
        paint.setColor(color);
        for (int petal = 0; petal < 6; petal++) {
            double angle = petal * Math.PI / 3.0;
            canvas.drawCircle(cx + (float) Math.cos(angle) * 8f,
                    cy + (float) Math.sin(angle) * 8f, 6f, paint);
        }
        paint.setColor(0xFFFFD36E);
        canvas.drawCircle(cx, cy, 5f, paint);
        canvas.restore();
    }

    private void drawSun(Canvas canvas, float cx, float cy) {
        paint.setColor(0x65FFF0A8);
        canvas.drawCircle(cx, cy, 77f, paint);
        paint.setColor(0xFFFFD985);
        canvas.drawCircle(cx, cy, 47f, paint);
    }

    private void drawCloud(Canvas canvas, float cx, float cy, float scale) {
        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(scale, scale);
        paint.setColor(0x8AFFFDF4);
        canvas.drawCircle(-34f, 8f, 25f, paint);
        canvas.drawCircle(0f, -4f, 34f, paint);
        canvas.drawCircle(35f, 9f, 24f, paint);
        canvas.drawRoundRect(-58f, 6f, 58f, 34f, 17f, 17f, paint);
        canvas.restore();
    }

    private void drawPill(Canvas canvas, float left, float top, float right, float bottom,
                          int fillColor, int shadowColor) {
        float radius = (bottom - top) * 0.5f;
        paint.setColor(shadowColor);
        canvas.drawRoundRect(left + 3f, top + 5f, right + 3f, bottom + 5f,
                radius, radius, paint);
        paint.setColor(fillColor);
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);
    }

    private void drawFittedText(Canvas canvas, String value, float centerX, float baseline,
                                float preferredSize, float maxWidth, int color, boolean useBold) {
        paint.setTypeface(useBold ? bold : regular);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(preferredSize);
        float width = paint.measureText(value);
        if (width > maxWidth && width > 0f) {
            paint.setTextSize(preferredSize * maxWidth / width);
        }
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawText(value, centerX, baseline, paint);
    }

    private void spawnParticles(float col, float row, int color, int count) {
        float x = BOARD_LEFT + (col + 0.5f) * TILE;
        float y = BOARD_TOP + (row + 0.5f) * TILE;
        for (int i = 0; i < count; i++) {
            float angle = random.nextFloat() * (float) Math.PI * 2f;
            float speed = 45f + random.nextFloat() * 80f;
            particles.add(new Particle(
                    x, y,
                    (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed - 28f,
                    0.75f + random.nextFloat() * 0.55f,
                    3f + random.nextFloat() * 4f,
                    color
            ));
        }
    }

    private void updateParticles(float dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle particle = particles.get(i);
            particle.life -= dt;
            if (particle.life <= 0f) {
                particles.remove(i);
                continue;
            }
            particle.x += particle.velocityX * dt;
            particle.y += particle.velocityY * dt;
            particle.velocityY += 34f * dt;
        }
    }

    private void drawParticles(Canvas canvas) {
        for (Particle particle : particles) {
            float alpha = Math.min(1f, particle.life * 2f);
            paint.setColor(Color.argb(Math.round(alpha * 255f),
                    Color.red(particle.color), Color.green(particle.color), Color.blue(particle.color)));
            canvas.drawCircle(particle.x, particle.y, particle.radius * alpha, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (viewScale <= 0f) {
            return true;
        }
        float x = (event.getX() - viewOffsetX) / viewScale;
        float y = (event.getY() - viewOffsetY) / viewScale;
        int action = event.getActionMasked();
        BunnyWorld.State state = world.getState();

        if (action == MotionEvent.ACTION_DOWN) {
            if (state == BunnyWorld.State.TITLE) {
                if (isLanguageHit(x, y, 1167f, 56f)) {
                    toggleLanguage();
                } else {
                    world.startJourney(preferences.getInt(PREF_RESUME_LEVEL, 0));
                    hintTime = 0f;
                    rebuildLevelGradient();
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                }
                invalidate();
                return true;
            }
            if (state == BunnyWorld.State.PLAYING) {
                if (distance(x, y, PAUSE_X, PAUSE_Y) <= 43f) {
                    world.pause();
                    drawingTunnel = false;
                    invalidate();
                    return true;
                }
                int row = boardRow(y);
                int col = boardCol(x);
                if (insideBoard(x, y, row, col) && world.tapCell(row, col)) {
                    drawingTunnel = true;
                    lastTouchRow = row;
                    lastTouchCol = col;
                    playDigFeedback();
                }
                invalidate();
                return true;
            }
            if (state == BunnyWorld.State.PAUSED) {
                if (isLanguageHit(x, y, 640f, 492f)) {
                    toggleLanguage();
                } else {
                    world.resume();
                }
                invalidate();
                return true;
            }
            if (state == BunnyWorld.State.LEVEL_COMPLETE) {
                world.continueAfterLevel();
                hintTime = 0f;
                rebuildLevelGradient();
                invalidate();
                return true;
            }
            if (state == BunnyWorld.State.JOURNEY_COMPLETE) {
                preferences.edit().putInt(PREF_RESUME_LEVEL, 0).apply();
                world.restartJourney();
                hintTime = 0f;
                rebuildLevelGradient();
                invalidate();
                return true;
            }
        } else if (action == MotionEvent.ACTION_MOVE
                && state == BunnyWorld.State.PLAYING && drawingTunnel) {
            int row = boardRow(y);
            int col = boardCol(x);
            if (insideBoard(x, y, row, col)
                    && (row != lastTouchRow || col != lastTouchCol)) {
                extendToward(row, col);
                lastTouchRow = row;
                lastTouchCol = col;
                invalidate();
            }
            return true;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            drawingTunnel = false;
            if (action == MotionEvent.ACTION_UP) {
                performClick();
            }
            return true;
        }
        return true;
    }

    private void extendToward(int destinationRow, int destinationCol) {
        int guard = 0;
        while ((world.getPlannedRow() != destinationRow
                || world.getPlannedCol() != destinationCol) && guard++ < 24) {
            int row = world.getPlannedRow();
            int col = world.getPlannedCol();
            int rowDistance = destinationRow - row;
            int colDistance = destinationCol - col;
            int nextRow = row;
            int nextCol = col;
            if (Math.abs(colDistance) >= Math.abs(rowDistance) && colDistance != 0) {
                nextCol += Integer.signum(colDistance);
            } else if (rowDistance != 0) {
                nextRow += Integer.signum(rowDistance);
            }
            if (!world.extendTunnel(nextRow, nextCol)) {
                break;
            }
            playDigFeedback();
        }
    }

    private void playDigFeedback() {
        long now = System.nanoTime();
        if (now - lastDigSoundNanos > 130_000_000L) {
            audio.playDig();
            lastDigSoundNanos = now;
        }
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    private int boardRow(float y) {
        return (int) ((y - BOARD_TOP) / TILE);
    }

    private int boardCol(float x) {
        return (int) ((x - BOARD_LEFT) / TILE);
    }

    private boolean insideBoard(float x, float y, int row, int col) {
        return x >= BOARD_LEFT && y >= BOARD_TOP
                && row >= 0 && row < BurrowLevel.ROWS
                && col >= 0 && col < BurrowLevel.COLS;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private boolean isLanguageHit(float x, float y, float centerX, float centerY) {
        return Math.abs(x - centerX) <= 70f && Math.abs(y - centerY) <= 42f;
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x1 - x2, y1 - y2);
    }

    private void toggleLanguage() {
        language = "en".equals(language) ? "ru" : "en";
        preferences.edit().putString(PREF_LANGUAGE, language).apply();
        applyLanguage(language);
        setContentDescription(text(R.string.accessibility_game));
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        invalidate();
    }

    private void applyLanguage(String languageCode) {
        Configuration configuration = new Configuration(getResources().getConfiguration());
        configuration.setLocale(Locale.forLanguageTag(languageCode));
        localizedContext = getContext().createConfigurationContext(configuration);
    }

    private String text(int resource) {
        return localizedContext.getString(resource);
    }

    private void rebuildLevelGradient() {
        BurrowLevel level = world.getLevel();
        levelGradient = new LinearGradient(0f, 0f, 0f, WORLD_HEIGHT,
                new int[]{level.backgroundTop, level.backgroundBottom}, null, Shader.TileMode.CLAMP);
    }

    @Override
    public void onCarrotCollected(float col, float row) {
        audio.playCarrot(world.getCarrotsCollected());
        spawnParticles(col, row, 0xFFFFC26F, 18);
        announceForAccessibility(text(R.string.accessibility_carrot_found));
    }

    @Override
    public void onLevelComplete(int completedLevel) {
        int resume = Math.min(completedLevel + 1, world.getLevelCount() - 1);
        preferences.edit().putInt(PREF_RESUME_LEVEL, resume).apply();
        audio.playLevelComplete();
        drawingTunnel = false;
        announceForAccessibility(text(R.string.accessibility_level_complete));
    }

    @Override
    public void onJourneyComplete() {
        audio.playJourneyComplete();
    }

    boolean handleBack() {
        BunnyWorld.State state = world.getState();
        if (state == BunnyWorld.State.PLAYING) {
            world.pause();
            drawingTunnel = false;
            invalidate();
            return true;
        }
        if (state == BunnyWorld.State.PAUSED
                || state == BunnyWorld.State.LEVEL_COMPLETE
                || state == BunnyWorld.State.JOURNEY_COMPLETE) {
            world.showTitle();
            drawingTunnel = false;
            invalidate();
            return true;
        }
        return false;
    }

    void onHostPause() {
        hostResumed = false;
        music.setPlaying(false);
        drawingTunnel = false;
        lastFrameNanos = 0L;
        world.pause();
    }

    void onHostResume() {
        hostResumed = true;
        lastFrameNanos = 0L;
        invalidate();
    }

    void close() {
        hostResumed = false;
        music.close();
        audio.close();
    }

    private static int darken(int color, float factor) {
        return Color.rgb(Math.round(Color.red(color) * factor),
                Math.round(Color.green(color) * factor),
                Math.round(Color.blue(color) * factor));
    }

    private static final class Particle {
        float x;
        float y;
        final float velocityX;
        float velocityY;
        float life;
        final float radius;
        final int color;

        Particle(float x, float y, float velocityX, float velocityY,
                 float life, float radius, int color) {
            this.x = x;
            this.y = y;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.life = life;
            this.radius = radius;
            this.color = color;
        }
    }
}
