/*
 * Bunny Burrow
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.bunnyburrow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Arrays;

public final class BunnyWorldTest {
    @Test
    public void tunnelOnlyExtendsToAdjacentDiggableTiles() {
        BunnyWorld world = new BunnyWorld(BurrowLevel.createAll(), null);
        world.startJourney(0);
        assertFalse(world.extendTunnel(1, 4));
        assertFalse(world.extendTunnel(2, 5));
        assertTrue(world.extendTunnel(1, 2));
        assertTrue(world.isDug(1, 2));
    }

    @Test
    public void tappingOldTunnelProvidesGentleBacktracking() {
        BunnyWorld world = new BunnyWorld(BurrowLevel.createAll(), null);
        world.startJourney(0);
        assertTrue(world.extendTunnel(1, 2));
        assertTrue(world.extendTunnel(1, 3));
        settle(world);
        assertEquals(3, world.getCol());
        assertTrue(world.tapCell(1, 1));
        settle(world);
        assertEquals(1, world.getCol());
    }

    @Test
    public void allTenBurrowsCanBeCompletedByDrawingValidTunnels() {
        BurrowLevel[] levels = BurrowLevel.createAll();
        BunnyWorld world = new BunnyWorld(levels, null);
        world.startJourney(0);

        for (int levelIndex = 0; levelIndex < levels.length; levelIndex++) {
            assertEquals(levelIndex, world.getLevelIndex());
            BurrowLevel level = world.getLevel();
            for (int row = 0; row < BurrowLevel.ROWS; row++) {
                for (int col = 0; col < BurrowLevel.COLS; col++) {
                    if (level.tileAt(row, col) == BurrowLevel.CARROT) {
                        digTo(world, row, col);
                    }
                }
            }
            assertEquals(3, world.getCarrotsCollected());
            digTo(world, level.exitRow, level.exitCol);
            assertEquals(BunnyWorld.State.LEVEL_COMPLETE, world.getState());
            world.continueAfterLevel();
        }
        assertEquals(BunnyWorld.State.JOURNEY_COMPLETE, world.getState());
    }

    @Test
    public void resumeLevelIsClamped() {
        BunnyWorld world = new BunnyWorld(BurrowLevel.createAll(), null);
        world.startJourney(200);
        assertEquals(9, world.getLevelIndex());
        world.startJourney(-2);
        assertEquals(0, world.getLevelIndex());
    }

    private static void digTo(BunnyWorld world, int destinationRow, int destinationCol) {
        int startRow = world.getPlannedRow();
        int startCol = world.getPlannedCol();
        int total = BurrowLevel.ROWS * BurrowLevel.COLS;
        int[] parent = new int[total];
        Arrays.fill(parent, -1);
        boolean[] visited = new boolean[total];
        ArrayDeque<Integer> open = new ArrayDeque<>();
        int start = startRow * BurrowLevel.COLS + startCol;
        int goal = destinationRow * BurrowLevel.COLS + destinationCol;
        visited[start] = true;
        open.add(start);
        int[] rowStep = {-1, 0, 1, 0};
        int[] colStep = {0, 1, 0, -1};

        while (!open.isEmpty() && !visited[goal]) {
            int current = open.removeFirst();
            int row = current / BurrowLevel.COLS;
            int col = current % BurrowLevel.COLS;
            for (int direction = 0; direction < rowStep.length; direction++) {
                int nextRow = row + rowStep[direction];
                int nextCol = col + colStep[direction];
                if (!world.getLevel().isDiggable(nextRow, nextCol)) {
                    continue;
                }
                int next = nextRow * BurrowLevel.COLS + nextCol;
                if (visited[next]) {
                    continue;
                }
                visited[next] = true;
                parent[next] = current;
                open.addLast(next);
            }
        }
        assertTrue("No route to " + destinationRow + "," + destinationCol, visited[goal]);
        ArrayDeque<Integer> path = new ArrayDeque<>();
        for (int cursor = goal; cursor != start; cursor = parent[cursor]) {
            path.addFirst(cursor);
        }
        while (!path.isEmpty()) {
            int next = path.removeFirst();
            assertTrue(world.extendTunnel(next / BurrowLevel.COLS, next % BurrowLevel.COLS));
        }
        settle(world);
        assertEquals(destinationRow, world.getRow());
        assertEquals(destinationCol, world.getCol());
    }

    private static void settle(BunnyWorld world) {
        int guard = 0;
        while (world.isMoving() && world.getState() == BunnyWorld.State.PLAYING) {
            world.update(0.1f);
            if (++guard > 1000) {
                throw new AssertionError("Rabbit did not settle");
            }
        }
    }
}
