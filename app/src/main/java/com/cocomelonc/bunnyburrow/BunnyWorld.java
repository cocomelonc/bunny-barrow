/*
 * Bunny Burrow
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.bunnyburrow;

import java.util.ArrayDeque;
import java.util.Arrays;

/** Pure-Java tunnel drawing, movement, collection, and level progression. */
final class BunnyWorld {
    enum State {
        TITLE,
        PLAYING,
        PAUSED,
        LEVEL_COMPLETE,
        JOURNEY_COMPLETE
    }

    interface Listener {
        void onCarrotCollected(float col, float row);

        void onLevelComplete(int completedLevel);

        void onJourneyComplete();
    }

    private static final float HOPS_PER_SECOND = 5.2f;
    private static final int[] ROW_STEP = {-1, 0, 1, 0};
    private static final int[] COL_STEP = {0, 1, 0, -1};

    private final BurrowLevel[] levels;
    private final Listener listener;
    private final ArrayDeque<Integer> route = new ArrayDeque<>();

    private State state = State.TITLE;
    private BurrowLevel level;
    private int levelIndex;
    private int row;
    private int col;
    private int plannedRow;
    private int plannedCol;
    private int targetRow;
    private int targetCol;
    private float visualRow;
    private float visualCol;
    private float hopProgress;
    private float facing = 1f;
    private boolean moving;
    private boolean[][] dug;
    private boolean[][] collectedCarrots;
    private int carrotsCollected;

    BunnyWorld(BurrowLevel[] levels, Listener listener) {
        if (levels == null || levels.length == 0) {
            throw new IllegalArgumentException("At least one burrow is required");
        }
        this.levels = levels.clone();
        this.listener = listener;
        loadLevel(0);
        state = State.TITLE;
    }

    void startJourney(int requestedLevel) {
        loadLevel(clampLevel(requestedLevel));
        state = State.PLAYING;
    }

    void restartJourney() {
        startJourney(0);
    }

    void showTitle() {
        route.clear();
        moving = false;
        state = State.TITLE;
    }

    void pause() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
        }
    }

    void resume() {
        if (state == State.PAUSED) {
            state = State.PLAYING;
        }
    }

    /** Adds one adjacent tile to the tunnel being drawn. */
    boolean extendTunnel(int nextRow, int nextCol) {
        if (state != State.PLAYING || !level.isDiggable(nextRow, nextCol)) {
            return false;
        }
        if (nextRow == plannedRow && nextCol == plannedCol) {
            return true;
        }
        int distance = Math.abs(nextRow - plannedRow) + Math.abs(nextCol - plannedCol);
        if (distance != 1) {
            return false;
        }
        dug[nextRow][nextCol] = true;
        route.addLast(encode(nextRow, nextCol));
        plannedRow = nextRow;
        plannedCol = nextCol;
        return true;
    }

    /**
     * A tap digs an adjacent tile. Tapping an existing tunnel queues a safe
     * shortest route through already-dug cells, which makes backtracking easy.
     */
    boolean tapCell(int targetRow, int targetCol) {
        if (state != State.PLAYING || !level.isDiggable(targetRow, targetCol)) {
            return false;
        }
        if (Math.abs(targetRow - plannedRow) + Math.abs(targetCol - plannedCol) == 1) {
            return extendTunnel(targetRow, targetCol);
        }
        if (!dug[targetRow][targetCol]) {
            return false;
        }
        return routeThroughDug(targetRow, targetCol);
    }

    private boolean routeThroughDug(int destinationRow, int destinationCol) {
        int total = BurrowLevel.ROWS * BurrowLevel.COLS;
        int[] parent = new int[total];
        Arrays.fill(parent, -1);
        boolean[] visited = new boolean[total];
        ArrayDeque<Integer> open = new ArrayDeque<>();
        int start = encode(plannedRow, plannedCol);
        int goal = encode(destinationRow, destinationCol);
        visited[start] = true;
        open.add(start);

        while (!open.isEmpty() && !visited[goal]) {
            int current = open.removeFirst();
            int currentRow = current / BurrowLevel.COLS;
            int currentCol = current % BurrowLevel.COLS;
            for (int direction = 0; direction < ROW_STEP.length; direction++) {
                int nextRow = currentRow + ROW_STEP[direction];
                int nextCol = currentCol + COL_STEP[direction];
                if (nextRow < 0 || nextRow >= BurrowLevel.ROWS
                        || nextCol < 0 || nextCol >= BurrowLevel.COLS
                        || !dug[nextRow][nextCol]) {
                    continue;
                }
                int next = encode(nextRow, nextCol);
                if (visited[next]) {
                    continue;
                }
                visited[next] = true;
                parent[next] = current;
                open.addLast(next);
            }
        }
        if (!visited[goal]) {
            return false;
        }
        ArrayDeque<Integer> reversed = new ArrayDeque<>();
        for (int cursor = goal; cursor != start; cursor = parent[cursor]) {
            reversed.addFirst(cursor);
        }
        route.addAll(reversed);
        plannedRow = destinationRow;
        plannedCol = destinationCol;
        return true;
    }

    void update(float elapsedSeconds) {
        if (state != State.PLAYING) {
            return;
        }
        float remaining = Math.min(Math.max(elapsedSeconds, 0f), 0.1f) * HOPS_PER_SECOND;
        while (remaining > 0f && state == State.PLAYING) {
            if (!moving) {
                if (route.isEmpty()) {
                    plannedRow = row;
                    plannedCol = col;
                    return;
                }
                int next = route.removeFirst();
                targetRow = next / BurrowLevel.COLS;
                targetCol = next % BurrowLevel.COLS;
                if (targetCol != col) {
                    facing = Math.signum(targetCol - col);
                }
                hopProgress = 0f;
                moving = true;
            }

            float step = Math.min(remaining, 1f - hopProgress);
            hopProgress += step;
            remaining -= step;
            visualRow = row + (targetRow - row) * smoothStep(hopProgress);
            visualCol = col + (targetCol - col) * smoothStep(hopProgress);
            if (hopProgress >= 0.9999f) {
                row = targetRow;
                col = targetCol;
                visualRow = row;
                visualCol = col;
                moving = false;
                enterCell();
            }
        }
    }

    void continueAfterLevel() {
        if (state != State.LEVEL_COMPLETE) {
            return;
        }
        if (levelIndex + 1 >= levels.length) {
            state = State.JOURNEY_COMPLETE;
            if (listener != null) {
                listener.onJourneyComplete();
            }
            return;
        }
        loadLevel(levelIndex + 1);
        state = State.PLAYING;
    }

    private void enterCell() {
        char tile = level.tileAt(row, col);
        if (tile == BurrowLevel.CARROT && !collectedCarrots[row][col]) {
            collectedCarrots[row][col] = true;
            carrotsCollected++;
            if (listener != null) {
                listener.onCarrotCollected(visualCol, visualRow);
            }
        }
        if (tile == BurrowLevel.EXIT && carrotsCollected == level.carrotCount) {
            route.clear();
            moving = false;
            plannedRow = row;
            plannedCol = col;
            state = State.LEVEL_COMPLETE;
            if (listener != null) {
                listener.onLevelComplete(levelIndex);
            }
        }
    }

    private void loadLevel(int index) {
        levelIndex = index;
        level = levels[levelIndex];
        row = level.startRow;
        col = level.startCol;
        plannedRow = row;
        plannedCol = col;
        targetRow = row;
        targetCol = col;
        visualRow = row;
        visualCol = col;
        hopProgress = 0f;
        facing = 1f;
        moving = false;
        carrotsCollected = 0;
        dug = new boolean[BurrowLevel.ROWS][BurrowLevel.COLS];
        collectedCarrots = new boolean[BurrowLevel.ROWS][BurrowLevel.COLS];
        dug[row][col] = true;
        route.clear();
    }

    private int clampLevel(int requestedLevel) {
        return Math.max(0, Math.min(requestedLevel, levels.length - 1));
    }

    private static int encode(int encodedRow, int encodedCol) {
        return encodedRow * BurrowLevel.COLS + encodedCol;
    }

    private static float smoothStep(float value) {
        return value * value * (3f - 2f * value);
    }

    State getState() {
        return state;
    }

    BurrowLevel getLevel() {
        return level;
    }

    int getLevelIndex() {
        return levelIndex;
    }

    int getLevelCount() {
        return levels.length;
    }

    int getRow() {
        return row;
    }

    int getCol() {
        return col;
    }

    int getPlannedRow() {
        return plannedRow;
    }

    int getPlannedCol() {
        return plannedCol;
    }

    float getVisualRow() {
        return visualRow;
    }

    float getVisualCol() {
        return visualCol;
    }

    float getFacing() {
        return facing;
    }

    float getHopHeight() {
        return moving ? (float) Math.sin(hopProgress * Math.PI) : 0f;
    }

    boolean isMoving() {
        return moving || !route.isEmpty();
    }

    int getQueuedSteps() {
        return route.size() + (moving ? 1 : 0);
    }

    int getCarrotsCollected() {
        return carrotsCollected;
    }

    boolean isDug(int checkRow, int checkCol) {
        return dug[checkRow][checkCol];
    }

    boolean isCarrotCollected(int checkRow, int checkCol) {
        return collectedCarrots[checkRow][checkCol];
    }
}
