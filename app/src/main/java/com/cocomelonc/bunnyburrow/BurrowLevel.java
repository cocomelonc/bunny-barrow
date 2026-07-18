/*
 * Bunny Burrow
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.bunnyburrow;

/** Immutable tilemap and gentle color palette for one underground garden. */
final class BurrowLevel {
    static final int COLS = 14;
    static final int ROWS = 8;

    static final char SOIL = '.';
    static final char ROCK = '#';
    static final char ROOT = 'R';
    static final char WATER = '~';
    static final char START = 'S';
    static final char CARROT = 'C';
    static final char EXIT = 'E';

    final int nameRes;
    final int backgroundTop;
    final int backgroundBottom;
    final int soilA;
    final int soilB;
    final int tunnelColor;
    final int rockColor;
    final int rootColor;
    final int waterColor;
    final int accentColor;
    final int seed;
    final String[] map;
    final int startRow;
    final int startCol;
    final int exitRow;
    final int exitCol;
    final int carrotCount;

    private BurrowLevel(
            int nameRes,
            int backgroundTop,
            int backgroundBottom,
            int soilA,
            int soilB,
            int tunnelColor,
            int rockColor,
            int rootColor,
            int waterColor,
            int accentColor,
            int seed,
            String... map
    ) {
        this.nameRes = nameRes;
        this.backgroundTop = backgroundTop;
        this.backgroundBottom = backgroundBottom;
        this.soilA = soilA;
        this.soilB = soilB;
        this.tunnelColor = tunnelColor;
        this.rockColor = rockColor;
        this.rootColor = rootColor;
        this.waterColor = waterColor;
        this.accentColor = accentColor;
        this.seed = seed;
        this.map = map.clone();

        if (map.length != ROWS) {
            throw new IllegalArgumentException("Burrow must contain exactly " + ROWS + " rows");
        }
        int starts = 0;
        int exits = 0;
        int carrots = 0;
        int foundStartRow = -1;
        int foundStartCol = -1;
        int foundExitRow = -1;
        int foundExitCol = -1;
        for (int row = 0; row < ROWS; row++) {
            if (map[row].length() != COLS) {
                throw new IllegalArgumentException("Burrow row " + row + " must contain " + COLS + " tiles");
            }
            for (int col = 0; col < COLS; col++) {
                char tile = map[row].charAt(col);
                if (!isKnownTile(tile)) {
                    throw new IllegalArgumentException("Unknown burrow tile: " + tile);
                }
                if (tile == START) {
                    starts++;
                    foundStartRow = row;
                    foundStartCol = col;
                } else if (tile == EXIT) {
                    exits++;
                    foundExitRow = row;
                    foundExitCol = col;
                } else if (tile == CARROT) {
                    carrots++;
                }
            }
        }
        if (starts != 1 || exits != 1) {
            throw new IllegalArgumentException("Burrow needs exactly one start and one exit");
        }
        if (carrots != 3) {
            throw new IllegalArgumentException("Every burrow needs exactly three carrots");
        }
        startRow = foundStartRow;
        startCol = foundStartCol;
        exitRow = foundExitRow;
        exitCol = foundExitCol;
        carrotCount = carrots;
    }

    char tileAt(int row, int col) {
        return map[row].charAt(col);
    }

    boolean isDiggable(int row, int col) {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) {
            return false;
        }
        char tile = tileAt(row, col);
        return tile != ROCK && tile != ROOT && tile != WATER;
    }

    static BurrowLevel[] createAll() {
        return new BurrowLevel[]{
                softEarth(), littleRoots(), pebblePatch(), mintyStream(), carrotCorner(),
                cloverBurrow(), twistyTunnel(), peachSoil(), moonlitBurrow(), sunnyMeadow()
        };
    }

    private static BurrowLevel softEarth() {
        return new BurrowLevel(
                R.string.level_1,
                0xFFF8E7DD, 0xFFE5D6E7, 0xFFDDBB9D, 0xFFE6C6AA,
                0xFFF6E1BE, 0xFFAAA2AE, 0xFF987A62, 0xFF89C4CC, 0xFFF1A35E, 1109,
                "..............",
                ".S..C.........",
                ".....##.......",
                "...C..........",
                "........##....",
                "..C...........",
                "..........E...",
                ".............."
        );
    }

    private static BurrowLevel littleRoots() {
        return new BurrowLevel(
                R.string.level_2,
                0xFFF3E9D8, 0xFFDCE5D7, 0xFFD6B493, 0xFFE1C19F,
                0xFFF3DEB9, 0xFFA49DA5, 0xFF8F755E, 0xFF8EC6C5, 0xFFF0A15A, 2219,
                "..............",
                ".S..R...C..C..",
                "....R.........",
                ".C..RRRRR.....",
                "....R.........",
                "....R.........",
                "....R......E..",
                ".............."
        );
    }

    private static BurrowLevel pebblePatch() {
        return new BurrowLevel(
                R.string.level_3,
                0xFFF0E8E2, 0xFFD9DDE9, 0xFFD2B39C, 0xFFDFC0A9,
                0xFFF0DFC7, 0xFF9E9DAA, 0xFF8F7565, 0xFF8BC0CA, 0xFFF3A765, 3343,
                "..#.....#.....",
                ".S..C.....C...",
                "....##........",
                ".C......##....",
                "..............",
                "....###.......",
                "..........E...",
                ".....#........"
        );
    }

    private static BurrowLevel mintyStream() {
        return new BurrowLevel(
                R.string.level_4,
                0xFFE7F0E8, 0xFFD5E4E4, 0xFFCEB39E, 0xFFDAC0AA,
                0xFFF0DEC2, 0xFF9DA5A8, 0xFF897865, 0xFF79BEC7, 0xFFF1A05E, 4483,
                "......~.......",
                ".S.C..~...C...",
                "......~.......",
                "..............",
                "......~.......",
                "..C...~.......",
                "......~....E..",
                ".............."
        );
    }

    private static BurrowLevel carrotCorner() {
        return new BurrowLevel(
                R.string.level_5,
                0xFFFFE8DE, 0xFFEBD8D5, 0xFFDAB095, 0xFFE5BDA1,
                0xFFF5DDBB, 0xFFA49CA3, 0xFF92715E, 0xFF8CC3C8, 0xFFF09A5C, 5521,
                "..............",
                ".S..R.C.R..C..",
                "....R...R.....",
                ".C..R...R.....",
                "....R...R.....",
                "....R.........",
                "....RRRR...E..",
                ".............."
        );
    }

    private static BurrowLevel cloverBurrow() {
        return new BurrowLevel(
                R.string.level_6,
                0xFFE7F1E5, 0xFFD4E3D7, 0xFFCBAF91, 0xFFD8B99A,
                0xFFEEDDBA, 0xFF9D9DA4, 0xFF7E765A, 0xFF83BEC4, 0xFFF1A662, 6661,
                "..............",
                ".S.C..RR...C..",
                "......R.......",
                "..RR..R..RR...",
                "...C.....R....",
                "..RRRR...R....",
                "...........E..",
                ".............."
        );
    }

    private static BurrowLevel twistyTunnel() {
        return new BurrowLevel(
                R.string.level_7,
                0xFFECE6F2, 0xFFD9D9E8, 0xFFC8A98F, 0xFFD6B69C,
                0xFFF0DDC0, 0xFF9D99A8, 0xFF83705E, 0xFF80B9C6, 0xFFF0A05B, 7723,
                "..............",
                ".S.RRRR...C.C.",
                "...R......R...",
                ".C.R.RRRR.R...",
                "...R......R...",
                "...RRRR...R...",
                "............E.",
                ".............."
        );
    }

    private static BurrowLevel peachSoil() {
        return new BurrowLevel(
                R.string.level_8,
                0xFFFFE7DB, 0xFFF0D5CF, 0xFFDDAE91, 0xFFE7BA9E,
                0xFFF7DAB6, 0xFFA59AA0, 0xFF8C6F5D, 0xFF8FC3C5, 0xFFF19B58, 8839,
                "..............",
                ".S..#C..#..C..",
                "....#...#.....",
                ".C..#...#.....",
                "....#...#.....",
                "........#.....",
                "....###....E..",
                ".............."
        );
    }

    private static BurrowLevel moonlitBurrow() {
        return new BurrowLevel(
                R.string.level_9,
                0xFFDCDDEE, 0xFFC8D6E3, 0xFFBCA694, 0xFFCBB29E,
                0xFFE6D8C0, 0xFF9295A7, 0xFF766C66, 0xFF72B2C3, 0xFFF0A25F, 9941,
                "....~~~.......",
                ".S..~C....C...",
                "....~.........",
                "....~~~.......",
                "..............",
                "..C..~~~......",
                ".....~.....E..",
                ".....~~~......"
        );
    }

    private static BurrowLevel sunnyMeadow() {
        return new BurrowLevel(
                R.string.level_10,
                0xFFF4E7CB, 0xFFD9D9E6, 0xFFD1AD87, 0xFFDEBA95,
                0xFFF6DDAE, 0xFF9A99A2, 0xFF806E57, 0xFF7EBBC2, 0xFFF3A04F, 11059,
                "..............",
                ".S.C.R....C...",
                ".....R..~~~...",
                ".C...R..~.....",
                ".....R..~~~...",
                ".....R........",
                ".....RR....E..",
                ".............."
        );
    }

    private static boolean isKnownTile(char tile) {
        return tile == SOIL || tile == ROCK || tile == ROOT || tile == WATER
                || tile == START || tile == CARROT || tile == EXIT;
    }
}
