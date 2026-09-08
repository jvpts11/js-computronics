/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.os;

import dev.jstech.computers.program.MinesweeperGame;
import dev.jstech.core.client.gui.component.Button;
import dev.jstech.core.client.gui.component.Panel;
import dev.jstech.core.client.gui.component.UiComponent;
import dev.jstech.core.client.gui.component.UiContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

/**
 * Minesweeper as a desktop app. The window chrome follows the installed skin, while the minefield keeps the
 * game's own classic identity (grey bevelled cells, red LED counters, the reset face, and the canonical
 * number colours). All rules live in the pure {@link MinesweeperGame}; the board component only draws it
 * and turns clicks into reveals and flags.
 */
public final class MinesweeperApp implements IDesktopApp {

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
    private static final int PANEL_H = 24;

    private OsSkin skin = OsSkin.fallback();
    private MinesweeperGame.Difficulty difficulty = MinesweeperGame.Difficulty.BEGINNER;
    private MinesweeperGame game = new MinesweeperGame(difficulty, System.nanoTime());
    private boolean timing;
    private long startMs;
    private long frozenSeconds;

    private final Panel root = new Panel();
    private final Button[] difficultyButtons = new Button[MinesweeperGame.Difficulty.values().length];
    private final Button face;
    private final Board board;

    /** The minefield: a grid of cells sized to the window, revealed with the left button and flagged with the right. */
    private final class Board extends UiComponent {

        private int cell = 12;

        @Override
        public void render(final GuiGraphics g, final UiContext ctx) {
            sunken(g, x() - 2, y() - 2, width() + 4, height() + 4);
            for (int r = 0; r < game.rows(); r++) {
                for (int c = 0; c < game.cols(); c++) {
                    drawCell(g, ctx.font(), r, c);
                }
            }
        }

        private void drawCell(final GuiGraphics g, final Font font, final int r, final int c) {
            final int cx = x() + c * cell;
            final int cy = y() + r * cell;
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
                        g.drawString(font, s, cx + (cell - font.width(s)) / 2 + 1, cy + (cell - 8) / 2 + 1, NUMBER[n], false);
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

        @Override
        public boolean mouseClicked(final double mx, final double my, final int button) {
            if (game.isFinished()) {
                return true;
            }
            final int c = (int) ((mx - x()) / cell);
            final int r = (int) ((my - y()) / cell);
            if (r < 0 || r >= game.rows() || c < 0 || c >= game.cols()) {
                return true;
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
            return true;
        }
    }

    public MinesweeperApp() {
        final MinesweeperGame.Difficulty[] all = MinesweeperGame.Difficulty.values();
        for (int i = 0; i < all.length; i++) {
            final MinesweeperGame.Difficulty d = all[i];
            difficultyButtons[i] = root.add(new Button(shortLabel(d), () -> newGame(d)));
        }
        face = root.add(new Button(this::faceGlyph, () -> newGame(difficulty)));
        board = root.add(new Board());
    }

    private static String shortLabel(final MinesweeperGame.Difficulty d) {
        return switch (d) {
            case BEGINNER -> "Beg";
            case INTERMEDIATE -> "Int";
            case EXPERT -> "Exp";
        };
    }

    private String faceGlyph() {
        return switch (game.state()) {
            case LOST -> ":(";
            case WON -> "B)";
            default -> ":)";
        };
    }

    private void newGame(final MinesweeperGame.Difficulty d) {
        this.difficulty = d;
        this.game = new MinesweeperGame(d, System.nanoTime());
        this.timing = false;
        this.frozenSeconds = 0;
    }

    @Override
    public String title() {
        return "Minesweeper";
    }

    @Override
    public int defaultWidth() {
        return 190;
    }

    @Override
    public int defaultHeight() {
        return 232;
    }

    @Override
    public int minWidth() {
        return 150;
    }

    @Override
    public int minHeight() {
        return 180;
    }

    @Override
    public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
    }

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        final UiContext ctx = new UiContext(skin, font, mouseX, mouseY, partialTick);
        g.fill(x, y, x + width, y + height, skin.windowBg());

        // Difficulty selector row.
        final MinesweeperGame.Difficulty[] all = MinesweeperGame.Difficulty.values();
        int dx = x + 4;
        for (int i = 0; i < all.length; i++) {
            final int dw = font.width(shortLabel(all[i])) + 10;
            difficultyButtons[i].setBounds(dx, y + 3, dw, 13);
            difficultyButtons[i].setPrimary(all[i] == difficulty);
            dx += dw + 3;
        }

        // Status panel: mine counter, reset face, timer, in a sunken frame.
        final int panelY = y + 19;
        sunken(g, x + 4, panelY, width - 8, PANEL_H);
        led(g, font, x + 8, panelY + 4, game.minesRemaining());
        led(g, font, x + width - 8 - 26, panelY + 4, elapsedSeconds());
        face.setBounds(x + width / 2 - 9, panelY + 3, 18, 18);

        // Board.
        final int boardTop = panelY + PANEL_H + 4;
        final int boardBottom = y + height - 4;
        final int rows = game.rows();
        final int cols = game.cols();
        board.cell = Math.max(7, Math.min(18, Math.min((width - 8) / cols, (boardBottom - boardTop) / rows)));
        final int bw = board.cell * cols;
        board.setBounds(x + (width - bw) / 2, boardTop, bw, board.cell * rows);
        root.render(g, ctx);
    }

    /** A 3-glyph red LED readout for a value, clamped to what fits (negative values keep a leading minus). */
    private static void led(final GuiGraphics g, final Font font, final int x, final int y, final int value) {
        g.fill(x, y, x + 26, y + 15, LED_BG);
        final String s;
        if (value < 0) {
            s = "-" + String.format(Locale.ROOT, "%02d", Math.min(99, -value));
        } else {
            s = String.format(Locale.ROOT, "%03d", Math.min(999, value));
        }
        g.drawString(font, s, x + 3, y + 4, LED_ON, false);
    }

    private static void sunken(final GuiGraphics g, final int x, final int y, final int w, final int h) {
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
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        root.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseReleased(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        root.mouseReleased(mouseX, mouseY, button);
    }
}
