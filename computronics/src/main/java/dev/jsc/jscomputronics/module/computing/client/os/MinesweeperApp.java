/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.os;

import dev.jsc.jscomputronics.module.computing.program.MinesweeperGame;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

/**
 * Minesweeper as a desktop app. The window chrome follows the installed skin, while the minefield keeps the
 * game's own classic identity (grey bevelled cells, red LED counters, the reset face, and the canonical
 * number colours). All rules live in the pure {@link MinesweeperGame}; this class only draws it and turns
 * clicks into reveals and flags.
 */
public final class MinesweeperApp implements DesktopApp {

    private static final int FACE = 0xFFC0C0C0;
    private static final int BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int BEVEL_DARK = 0xFF808080;
    private static final int GRID = 0xFF9A9A9A;
    private static final int LED_BG = 0xFF200000;
    private static final int LED_ON = 0xFFFF2B2B;
    private static final int MINE = 0xFF101010;
    private static final int FLAG_RED = 0xFFD01818;
    private static final int[] NUMBER = {
            0, 0xFF0000FF, 0xFF008000, 0xFFFF0000, 0xFF000080,
            0xFF800000, 0xFF008080, 0xFF000000, 0xFF808080,
    };

    private record Hit(int x, int y, int w, int h, Runnable onClick) {
        boolean contains(final double mx, final double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private OsSkin skin = OsSkin.fallback();
    private MinesweeperGame.Difficulty difficulty = MinesweeperGame.Difficulty.BEGINNER;
    private MinesweeperGame game = new MinesweeperGame(difficulty, System.nanoTime());
    private boolean timing;
    private long startMs;
    private long frozenSeconds;

    private final java.util.List<Hit> hits = new java.util.ArrayList<>();
    // Board geometry from the last render, so a click maps to the right cell.
    private int boardX;
    private int boardY;
    private int cell;

    private void newGame(final MinesweeperGame.Difficulty d) {
        this.difficulty = d;
        this.game = new MinesweeperGame(d, System.nanoTime());
        this.timing = false;
        this.frozenSeconds = 0;
    }

    @Override public String title() {
        return "Minesweeper";
    }

    @Override public int defaultWidth() {
        return 190;
    }

    @Override public int defaultHeight() {
        return 232;
    }

    @Override public int minWidth() {
        return 150;
    }

    @Override public int minHeight() {
        return 180;
    }

    @Override public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
    }

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        hits.clear();
        g.fill(x, y, x + width, y + height, skin.windowBg());

        // Difficulty selector row.
        int dx = x + 4;
        for (final MinesweeperGame.Difficulty d : MinesweeperGame.Difficulty.values()) {
            final String label = switch (d) {
                case BEGINNER -> "Beg";
                case INTERMEDIATE -> "Int";
                case EXPERT -> "Exp";
            };
            final int dw = font.width(label) + 10;
            final boolean hov = mouseX >= dx && mouseX < dx + dw && mouseY >= y + 3 && mouseY < y + 16;
            skin.button(g, font, dx, y + 3, dw, 13, label, hov, false, d == difficulty);
            final MinesweeperGame.Difficulty target = d;
            hits.add(new Hit(dx, y + 3, dw, 13, () -> newGame(target)));
            dx += dw + 3;
        }

        // Status panel: mine counter, reset face, timer, in a sunken frame.
        final int panelY = y + 19;
        final int panelH = 24;
        sunken(g, x + 4, panelY, width - 8, panelH);
        led(g, font, x + 8, panelY + 4, game.minesRemaining());
        final int seconds = elapsedSeconds();
        led(g, font, x + width - 8 - 26, panelY + 4, seconds);
        final int faceX = x + width / 2 - 9;
        final int faceY = panelY + 3;
        final boolean faceHov = mouseX >= faceX && mouseX < faceX + 18 && mouseY >= faceY && mouseY < faceY + 18;
        face(g, font, faceX, faceY, faceHov);
        hits.add(new Hit(faceX, faceY, 18, 18, () -> newGame(difficulty)));

        // Board.
        final int boardTop = panelY + panelH + 4;
        final int boardBottom = y + height - 4;
        final int rows = game.rows();
        final int cols = game.cols();
        cell = Math.max(7, Math.min(18, Math.min((width - 8) / cols, (boardBottom - boardTop) / rows)));
        final int bw = cell * cols;
        final int bh = cell * rows;
        boardX = x + (width - bw) / 2;
        boardY = boardTop;
        sunken(g, boardX - 2, boardY - 2, bw + 4, bh + 4);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                drawCell(g, font, r, c);
            }
        }
    }

    private void drawCell(final GuiGraphics g, final Font font, final int r, final int c) {
        final int cx = boardX + c * cell;
        final int cy = boardY + r * cell;
        final boolean lost = game.state() == MinesweeperGame.State.LOST;
        final boolean showMine = lost && game.isMine(r, c);
        if (game.isRevealed(r, c) || showMine) {
            g.fill(cx, cy, cx + cell, cy + cell, FACE);
            g.fill(cx, cy, cx + cell, cy + 1, GRID);
            g.fill(cx, cy, cx + 1, cy + cell, GRID);
            if (showMine) {
                if (game.isRevealed(r, c)) {
                    g.fill(cx + 1, cy + 1, cx + cell, cy + cell, FLAG_RED); // the mine that was triggered
                }
                final int m = Math.max(2, cell / 3);
                g.fill(cx + (cell - m) / 2, cy + (cell - m) / 2, cx + (cell + m) / 2, cy + (cell + m) / 2, MINE);
            } else {
                final int n = game.adjacent(r, c);
                if (n > 0) {
                    final String s = String.valueOf(n);
                    g.drawString(font, s, cx + (cell - font.width(s)) / 2 + 1, cy + (cell - 8) / 2 + 1,
                            NUMBER[n], false);
                }
            }
            return;
        }
        // Unrevealed raised cell.
        g.fill(cx, cy, cx + cell, cy + cell, FACE);
        g.fill(cx, cy, cx + cell, cy + 1, BEVEL_LIGHT);
        g.fill(cx, cy, cx + 1, cy + cell, BEVEL_LIGHT);
        g.fill(cx, cy + cell - 1, cx + cell, cy + cell, BEVEL_DARK);
        g.fill(cx + cell - 1, cy, cx + cell, cy + cell, BEVEL_DARK);
        if (game.isFlagged(r, c)) {
            final int fx = cx + cell / 2;
            g.fill(fx, cy + 2, fx + 1, cy + cell - 3, MINE);              // pole
            g.fill(fx - cell / 4, cy + 2, fx, cy + cell / 2, FLAG_RED);   // flag
            g.fill(cx + cell / 2 - 3, cy + cell - 3, cx + cell / 2 + 3, cy + cell - 2, MINE); // base
        }
    }

    /** A 3-glyph red LED readout for a value, clamped to what fits (negative values keep a leading minus). */
    private void led(final GuiGraphics g, final Font font, final int x, final int y, final int value) {
        g.fill(x, y, x + 26, y + 15, LED_BG);
        final String s;
        if (value < 0) {
            s = "-" + String.format(Locale.ROOT, "%02d", Math.min(99, -value));
        } else {
            s = String.format(Locale.ROOT, "%03d", Math.min(999, value));
        }
        g.drawString(font, s, x + 3, y + 4, LED_ON, false);
    }

    private void face(final GuiGraphics g, final Font font, final int x, final int y, final boolean hovered) {
        skin.button(g, font, x, y, 18, 18, "", hovered, false, false);
        final String f = switch (game.state()) {
            case LOST -> ":(";
            case WON -> "B)";
            default -> ":)";
        };
        g.drawString(font, f, x + (18 - font.width(f)) / 2, y + 5, 0xFF202020, false);
    }

    private void sunken(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        g.fill(x, y, x + w, y + h, FACE);
        g.fill(x, y, x + w, y + 1, BEVEL_DARK);
        g.fill(x, y, x + 1, y + h, BEVEL_DARK);
        g.fill(x, y + h - 1, x + w, y + h, BEVEL_LIGHT);
        g.fill(x + w - 1, y, x + w, y + h, BEVEL_LIGHT);
    }

    private int elapsedSeconds() {
        if (game.isFinished()) {
            return (int) frozenSeconds;
        }
        if (!timing) {
            return 0;
        }
        return (int) Math.min(999, (System.currentTimeMillis() - startMs) / 1000);
    }

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY,
                             final int button) {
        if (button == 0) {
            for (final Hit hit : hits) {
                if (hit.contains(mouseX, mouseY)) {
                    hit.onClick().run();
                    return;
                }
            }
        }
        if (game.isFinished()) {
            return;
        }
        final int c = (int) ((mouseX - boardX) / cell);
        final int r = (int) ((mouseY - boardY) / cell);
        if (r < 0 || r >= game.rows() || c < 0 || c >= game.cols()) {
            return;
        }
        if (button == 0) {
            if (!timing) {
                timing = true;
                startMs = System.currentTimeMillis();
            }
            game.reveal(r, c);
        } else if (button == 1) {
            game.toggleFlag(r, c);
        }
        if (game.isFinished()) {
            frozenSeconds = timing ? Math.min(999, (System.currentTimeMillis() - startMs) / 1000) : 0;
        }
    }
}
