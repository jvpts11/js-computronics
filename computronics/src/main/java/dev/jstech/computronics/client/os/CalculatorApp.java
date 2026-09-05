/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.computronics.program.CalcEngine;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/**
 * The Scientific Calculator: a pre-installed desktop app whose keypad and display are pure client UI over
 * the {@link CalcEngine} evaluator. Buttons build an infix expression string; {@code =} evaluates it and
 * shows the formatted result. The {@code DEG}/{@code RAD} key toggles how trig functions read their angle.
 */
public final class CalculatorApp implements DesktopApp {

    private static final String[][] KEYS = {
            {"DEG", "C", "<-", "(", ")"},
            {"sin", "cos", "tan", "^", "sqrt"},
            {"ln", "log", "!", "pi", "e"},
            {"7", "8", "9", "/", "*"},
            {"4", "5", "6", "-", "+"},
            {"1", "2", "3", "0", "."},
    };
    private static final int COLS = 5;
    private static final int GAP = 2;
    private static final int DISPLAY_H = 30;

    private record Hit(int x, int y, int w, int h, String key) {
        boolean contains(final double mx, final double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private OsSkin skin = OsSkin.fallback();
    private String input = "";
    private String result = "";
    private boolean degrees;
    private boolean justResult;
    private final List<Hit> hits = new ArrayList<>();

    @Override public String title() {
        return "Calculator";
    }

    @Override public int defaultWidth() {
        return 176;
    }

    @Override public int defaultHeight() {
        return 208;
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

        // Display: the running expression on top, the last result below, both right-aligned.
        skin.field(g, x + 2, y + 2, width - 4, DISPLAY_H - 4, false);
        final String shown = input.isEmpty() ? "0" : input;
        g.drawString(font, clip(font, shown, width - 12), x + width - 6 - font.width(clip(font, shown, width - 12)),
                y + 6, skin.text(), false);
        final String res = result.isEmpty() ? "" : "= " + result;
        g.drawString(font, clip(font, res, width - 12), x + width - 6 - font.width(clip(font, res, width - 12)),
                y + 18, skin.accent(), false);

        // Keypad: six labelled rows plus a wide "=" row at the bottom.
        final int padTop = y + DISPLAY_H;
        final int rows = KEYS.length + 1;
        final int cellW = (width - (COLS + 1) * GAP) / COLS;
        final int cellH = (height - DISPLAY_H - (rows + 1) * GAP) / rows;
        for (int r = 0; r < KEYS.length; r++) {
            final int by = padTop + GAP + r * (cellH + GAP);
            for (int c = 0; c < COLS; c++) {
                final int bx = x + GAP + c * (cellW + GAP);
                final String key = KEYS[r][c];
                final String label = "DEG".equals(key) ? (degrees ? "DEG" : "RAD") : key;
                final boolean hov = mouseX >= bx && mouseX < bx + cellW && mouseY >= by && mouseY < by + cellH;
                skin.button(g, font, bx, by, cellW, cellH, label, hov, false, isOperator(key));
                hits.add(new Hit(bx, by, cellW, cellH, key));
            }
        }
        final int eqY = padTop + GAP + KEYS.length * (cellH + GAP);
        final int eqW = width - 2 * GAP;
        final boolean eqHov = mouseX >= x + GAP && mouseX < x + GAP + eqW && mouseY >= eqY && mouseY < eqY + cellH;
        skin.button(g, font, x + GAP, eqY, eqW, cellH, "=", eqHov, false, true);
        hits.add(new Hit(x + GAP, eqY, eqW, cellH, "="));
    }

    private void press(final String key) {
        switch (key) {
            case "C" -> {
                input = "";
                result = "";
                justResult = false;
            }
            case "<-" -> {
                if (!input.isEmpty()) {
                    input = input.substring(0, input.length() - 1);
                }
                justResult = false;
            }
            case "DEG" -> degrees = !degrees;
            case "=" -> evaluate();
            default -> append(key);
        }
    }

    private void append(final String key) {
        // After a result, a fresh value replaces it, while an operator continues from it.
        if (justResult) {
            if (startsValue(key)) {
                input = "";
            }
            justResult = false;
        }
        input += isFunction(key) ? key + "(" : key;
    }

    private void evaluate() {
        if (input.isEmpty()) {
            return;
        }
        try {
            result = format(CalcEngine.evaluate(input, degrees));
            input = result;
            justResult = true;
        } catch (final RuntimeException ex) {
            result = "Error";
            justResult = false;
        }
    }

    private static String format(final double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return "Error";
        }
        if (v == Math.rint(v) && Math.abs(v) < 1e15) {
            return Long.toString((long) v);
        }
        return new BigDecimal(v).round(new MathContext(11)).stripTrailingZeros().toPlainString();
    }

    private static boolean isFunction(final String key) {
        return switch (key) {
            case "sin", "cos", "tan", "ln", "log", "sqrt" -> true;
            default -> false;
        };
    }

    private static boolean isOperator(final String key) {
        return switch (key) {
            case "+", "-", "*", "/", "^" -> true;
            default -> false;
        };
    }

    private static boolean startsValue(final String key) {
        if (key.isEmpty()) {
            return false;
        }
        final char c = key.charAt(0);
        return Character.isDigit(c) || c == '.' || c == '(' || isFunction(key) || "pi".equals(key) || "e".equals(key);
    }

    private static String clip(final Font font, final String s, final int maxWidth) {
        if (font.width(s) <= maxWidth) {
            return s;
        }
        // Keep the tail (the most recently typed part) visible in the right-aligned field.
        String out = s;
        while (out.length() > 1 && font.width(".." + out) > maxWidth) {
            out = out.substring(1);
        }
        return ".." + out;
    }

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY,
                             final int button) {
        if (button != 0) {
            return;
        }
        for (final Hit hit : hits) {
            if (hit.contains(mouseX, mouseY)) {
                press(hit.key());
                return;
            }
        }
    }

    @Override
    public boolean charTyped(final char c) {
        if (c >= '0' && c <= '9') {
            append(String.valueOf(c));
            return true;
        }
        switch (c) {
            case '.', '+', '-', '*', '/', '^', '!', '(', ')', '%' -> {
                append(String.valueOf(c));
                return true;
            }
            case '=' -> {
                evaluate();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        // GLFW: 257 = Enter, 335 = keypad Enter, 259 = Backspace.
        if (key == 257 || key == 335) {
            evaluate();
            return true;
        }
        if (key == 259) {
            press("<-");
            return true;
        }
        return false;
    }
}
