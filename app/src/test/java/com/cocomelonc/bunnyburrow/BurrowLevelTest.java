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

import java.util.ArrayDeque;

public final class BurrowLevelTest {
    @Test
    public void tenBurrowsHaveThreeReachableCarrotsAndReachableExit() {
        BurrowLevel[] levels = BurrowLevel.createAll();
        assertEquals(10, levels.length);
        for (BurrowLevel level : levels) {
            assertEquals(3, level.carrotCount);
            boolean[][] reached = flood(level, level.startRow, level.startCol);
            assertTrue(reached[level.exitRow][level.exitCol]);
            for (int row = 0; row < BurrowLevel.ROWS; row++) {
                for (int col = 0; col < BurrowLevel.COLS; col++) {
                    if (level.tileAt(row, col) == BurrowLevel.CARROT) {
                        assertTrue("Carrot is isolated at " + row + "," + col,
                                reached[row][col]);
                    }
                }
            }
        }
    }

    private static boolean[][] flood(BurrowLevel level, int startRow, int startCol) {
        boolean[][] reached = new boolean[BurrowLevel.ROWS][BurrowLevel.COLS];
        ArrayDeque<Integer> open = new ArrayDeque<>();
        reached[startRow][startCol] = true;
        open.add(startRow * BurrowLevel.COLS + startCol);
        int[] rowStep = {-1, 0, 1, 0};
        int[] colStep = {0, 1, 0, -1};
        while (!open.isEmpty()) {
            int current = open.removeFirst();
            int row = current / BurrowLevel.COLS;
            int col = current % BurrowLevel.COLS;
            for (int direction = 0; direction < rowStep.length; direction++) {
                int nextRow = row + rowStep[direction];
                int nextCol = col + colStep[direction];
                if (level.isDiggable(nextRow, nextCol) && !reached[nextRow][nextCol]) {
                    reached[nextRow][nextCol] = true;
                    open.add(nextRow * BurrowLevel.COLS + nextCol);
                }
            }
        }
        return reached;
    }
}
