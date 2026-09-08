/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.program;

import java.util.Random;

/**
 * The pure rules of Minesweeper: a grid of cells, a set of mines, flood-fill reveal, flagging, and the
 * win/lose state. Mines are placed on the first reveal so the opening click is always safe (and, when the
 * board has room, so are its neighbours), which is the standard fair-start behaviour. Placement is driven
 * by a seeded {@link Random} so the engine is fully deterministic and unit-testable; it touches no
 * Minecraft type.
 */
public final class MinesweeperGame {

    /** Standard difficulty presets (rows, cols, mines). */
    public enum Difficulty {
        BEGINNER(9, 9, 10),
        INTERMEDIATE(16, 16, 40),
        EXPERT(16, 30, 99);

        public final int rows;
        public final int cols;
        public final int mines;

        Difficulty(final int rows, final int cols, final int mines) {
            this.rows = rows;
            this.cols = cols;
            this.mines = mines;
        }
    }

    public enum State { PLAYING, WON, LOST }

    private final int rows;
    private final int cols;
    private final int mineCount;
    private final Random random;

    private final boolean[] mine;
    private final boolean[] revealed;
    private final boolean[] flagged;
    private final int[] adjacent;

    private boolean minesPlaced;
    private int revealedCount;
    private int flagCount;
    private State state = State.PLAYING;

    public MinesweeperGame(final Difficulty difficulty, final long seed) {
        this(difficulty.rows, difficulty.cols, difficulty.mines, seed);
    }

    public MinesweeperGame(final int rows, final int cols, final int mineCount, final long seed) {
        if (rows < 1 || cols < 1) {
            throw new IllegalArgumentException("board must be at least 1x1");
        }
        if (mineCount < 0 || mineCount >= rows * cols) {
            throw new IllegalArgumentException("mine count must be in [0, rows*cols)");
        }
        this.rows = rows;
        this.cols = cols;
        this.mineCount = mineCount;
        this.random = new Random(seed);
        this.mine = new boolean[rows * cols];
        this.revealed = new boolean[rows * cols];
        this.flagged = new boolean[rows * cols];
        this.adjacent = new int[rows * cols];
    }

    public int rows() {
        return rows;
    }

    public int cols() {
        return cols;
    }

    public int mineCount() {
        return mineCount;
    }

    public State state() {
        return state;
    }

    public boolean isFinished() {
        return state != State.PLAYING;
    }

    public boolean isRevealed(final int r, final int c) {
        return revealed[idx(r, c)];
    }

    public boolean isFlagged(final int r, final int c) {
        return flagged[idx(r, c)];
    }

    /** Whether the given cell holds a mine (meaningful for rendering once the game is lost). */
    public boolean isMine(final int r, final int c) {
        return mine[idx(r, c)];
    }

    /** The number of mines adjacent to a revealed cell (0..8); only meaningful once mines are placed. */
    public int adjacent(final int r, final int c) {
        return adjacent[idx(r, c)];
    }

    /** Mines minus flags placed, i.e. the counter the player watches; may go negative with over-flagging. */
    public int minesRemaining() {
        return mineCount - flagCount;
    }

    /**
     * Reveals a cell. The first reveal of the game places the mines (avoiding this cell, and its neighbours
     * when the board has room). Revealing a mine loses the game; revealing a zero flood-fills its region.
     * A no-op on a flagged cell, an already-revealed cell, or once the game is finished.
     */
    public void reveal(final int r, final int c) {
        final int i = idx(r, c);
        if (state != State.PLAYING || revealed[i] || flagged[i]) {
            return;
        }
        if (!minesPlaced) {
            placeMines(r, c);
        }
        if (mine[i]) {
            revealed[i] = true;
            state = State.LOST;
            return;
        }
        floodReveal(r, c);
        if (revealedCount == rows * cols - mineCount) {
            state = State.WON;
        }
    }

    /** Toggles a flag on an unrevealed cell. A no-op once the game is finished or the cell is revealed. */
    public void toggleFlag(final int r, final int c) {
        final int i = idx(r, c);
        if (state != State.PLAYING || revealed[i]) {
            return;
        }
        flagged[i] = !flagged[i];
        flagCount += flagged[i] ? 1 : -1;
    }

    private void floodReveal(final int startR, final int startC) {
        // Iterative flood fill so a large empty region never overflows the stack.
        final int[] stack = new int[rows * cols];
        int top = 0;
        stack[top++] = idx(startR, startC);
        while (top > 0) {
            final int i = stack[--top];
            if (revealed[i] || flagged[i]) {
                continue;
            }
            revealed[i] = true;
            revealedCount++;
            if (adjacent[i] != 0) {
                continue;
            }
            final int r = i / cols;
            final int c = i % cols;
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr == 0 && dc == 0) {
                        continue;
                    }
                    final int nr = r + dr;
                    final int nc = c + dc;
                    if (inBounds(nr, nc)) {
                        final int ni = idx(nr, nc);
                        if (!revealed[ni] && !flagged[ni] && !mine[ni]) {
                            stack[top++] = ni;
                        }
                    }
                }
            }
        }
    }

    private void placeMines(final int safeR, final int safeC) {
        final boolean[] blocked = new boolean[rows * cols];
        int freeCells = rows * cols;
        // Keep the first click and, when there is room, its neighbours mine-free for a fair opening.
        final boolean protectNeighbours = mineCount <= rows * cols - 9;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (!protectNeighbours && (dr != 0 || dc != 0)) {
                    continue;
                }
                final int nr = safeR + dr;
                final int nc = safeC + dc;
                if (inBounds(nr, nc) && !blocked[idx(nr, nc)]) {
                    blocked[idx(nr, nc)] = true;
                    freeCells--;
                }
            }
        }
        int placed = 0;
        while (placed < mineCount) {
            final int i = random.nextInt(rows * cols);
            if (mine[i] || blocked[i]) {
                continue;
            }
            mine[i] = true;
            placed++;
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                adjacent[idx(r, c)] = countAdjacentMines(r, c);
            }
        }
        minesPlaced = true;
    }

    private int countAdjacentMines(final int r, final int c) {
        int n = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) {
                    continue;
                }
                if (inBounds(r + dr, c + dc) && mine[idx(r + dr, c + dc)]) {
                    n++;
                }
            }
        }
        return n;
    }

    private boolean inBounds(final int r, final int c) {
        return r >= 0 && r < rows && c >= 0 && c < cols;
    }

    private int idx(final int r, final int c) {
        if (!inBounds(r, c)) {
            throw new IndexOutOfBoundsException("cell out of bounds: " + r + "," + c);
        }
        return r * cols + c;
    }
}
