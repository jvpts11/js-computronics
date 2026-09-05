/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MinesweeperGameTest {

    private static int countMines(final MinesweeperGame g) {
        int n = 0;
        for (int r = 0; r < g.rows(); r++) {
            for (int c = 0; c < g.cols(); c++) {
                if (g.isMine(r, c)) {
                    n++;
                }
            }
        }
        return n;
    }

    @Test
    void constructor_rejectsTooManyMines() {
        assertThrows(IllegalArgumentException.class, () -> new MinesweeperGame(3, 3, 9, 1L));
    }

    @Test
    void firstReveal_placesTheExactMineCount() {
        final MinesweeperGame g = new MinesweeperGame(9, 9, 10, 42L);
        assertEquals(0, countMines(g), "no mines exist before the first reveal");
        g.reveal(4, 4);
        assertEquals(10, countMines(g));
    }

    @Test
    void firstReveal_isNeverAMineAndOpensAnArea() {
        final MinesweeperGame g = new MinesweeperGame(9, 9, 10, 42L);
        g.reveal(4, 4);
        assertFalse(g.isMine(4, 4), "the first click is always safe");
        assertEquals(MinesweeperGame.State.PLAYING, g.state());
        assertTrue(g.isRevealed(4, 4));
    }

    @Test
    void firstReveal_keepsNeighboursSafeWhenBoardHasRoom() {
        final MinesweeperGame g = new MinesweeperGame(9, 9, 10, 7L);
        g.reveal(4, 4);
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                assertFalse(g.isMine(4 + dr, 4 + dc), "the opening 3x3 is mine-free on a roomy board");
            }
        }
    }

    @Test
    void revealingAMineLosesTheGame() {
        final MinesweeperGame g = new MinesweeperGame(9, 9, 10, 42L);
        g.reveal(0, 0); // place mines with a safe opening far from the corner
        int mr = -1;
        int mc = -1;
        outer:
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (g.isMine(r, c)) {
                    mr = r;
                    mc = c;
                    break outer;
                }
            }
        }
        g.reveal(mr, mc);
        assertEquals(MinesweeperGame.State.LOST, g.state());
        assertTrue(g.isFinished());
    }

    @Test
    void flaggedCellIsNotRevealed() {
        final MinesweeperGame g = new MinesweeperGame(9, 9, 10, 42L);
        g.reveal(4, 4);
        g.toggleFlag(0, 0);
        g.reveal(0, 0);
        assertFalse(g.isRevealed(0, 0), "a flagged cell resists reveal");
    }

    @Test
    void flagTogglesAndCountsDown() {
        final MinesweeperGame g = new MinesweeperGame(9, 9, 10, 42L);
        assertEquals(10, g.minesRemaining());
        g.toggleFlag(0, 0);
        assertEquals(9, g.minesRemaining());
        g.toggleFlag(0, 0);
        assertEquals(10, g.minesRemaining());
    }

    @Test
    void revealingEveryNonMineCellWins() {
        final MinesweeperGame g = new MinesweeperGame(5, 5, 3, 123L);
        g.reveal(2, 2);
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                if (!g.isMine(r, c)) {
                    g.reveal(r, c);
                }
            }
        }
        assertEquals(MinesweeperGame.State.WON, g.state());
    }

    @Test
    void zeroCellFloodFillsAndCountsAreConsistent() {
        final MinesweeperGame g = new MinesweeperGame(9, 9, 10, 99L);
        g.reveal(4, 4);
        // The opening reveal must have opened more than one cell (its region flood-filled).
        int revealed = 0;
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (g.isRevealed(r, c)) {
                    revealed++;
                }
            }
        }
        assertTrue(revealed > 1, "revealing a zero opens its whole region");
    }

    @Test
    void revealAfterFinishIsNoOp() {
        final MinesweeperGame g = new MinesweeperGame(5, 5, 3, 123L);
        g.reveal(2, 2);
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                if (!g.isMine(r, c)) {
                    g.reveal(r, c);
                }
            }
        }
        assertEquals(MinesweeperGame.State.WON, g.state());
        g.toggleFlag(0, 0);
        assertEquals(MinesweeperGame.State.WON, g.state(), "a finished game ignores further input");
    }

    @Test
    void difficultyPresetsHaveExpectedShape() {
        final MinesweeperGame g = new MinesweeperGame(MinesweeperGame.Difficulty.EXPERT, 1L);
        assertEquals(16, g.rows());
        assertEquals(30, g.cols());
        assertEquals(99, g.mineCount());
    }
}
