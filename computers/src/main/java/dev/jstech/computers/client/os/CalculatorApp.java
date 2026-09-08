/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.os;

import dev.jstech.computers.program.CalcEngine;
import dev.jstech.core.client.gui.component.Button;
import dev.jstech.core.client.gui.component.Panel;
import dev.jstech.core.client.gui.component.UiContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * The Scientific Calculator: a pre-installed desktop app whose keypad and display are pure client UI over
 * the {@link CalcEngine} evaluator. Buttons build an infix expression string; {@code =} evaluates it and
 * shows the formatted result. The {@code DEG}/{@code RAD} key toggles how trig functions read their angle.
 */
public final class CalculatorApp implements IDesktopApp {

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

    private OsSkin skin = OsSkin.fallback();
    private String input = "";
    private String result = "";
    private boolean degrees;
    private boolean justResult;

    private final Panel root = new Panel();
    private final Button[][] keys = new Button[KEYS.length][COLS];
    private final Button equals;

    public CalculatorApp() {
        for (int r = 0; r < KEYS.length; r++) {
            for (int c = 0; c < COLS; c++) {
                final String key = KEYS[r][c];
                final Button button = new Button(key, () -> press(key)).setPrimary(isOperator(key));
                if ("DEG".equals(key)) {
                    button.setLabel(() -> degrees ? "DEG" : "RAD");
                }
                keys[r][c] = root.add(button);
            }
        }
        equals = root.add(new Button("=", this::evaluate).setPrimary(true));
    }

    @Override
    public String title() {
        return "Calculator";
    }

    @Override
    public int defaultWidth() {
        return 176;
    }

    @Override
    public int defaultHeight() {
        return 208;
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

        /*
         * Display: the running expression on top, the last result below, both right-aligned with their
         * ends kept in view, since the most recently typed part is what matters.
         */
        skin.field(g, x + 2, y + 2, width - 4, DISPLAY_H - 4, false);
        final String shown = tail(font, input.isEmpty() ? "0" : input, width - 12);
        g.drawString(font, shown, x + width - 6 - font.width(shown), y + 6, skin.text(), false);
        final String res = tail(font, result.isEmpty() ? "" : "= " + result, width - 12);
        g.drawString(font, res, x + width - 6 - font.width(res), y + 18, skin.accent(), false);

        // Keypad: six labelled rows plus a wide "=" row at the bottom.
        final int padTop = y + DISPLAY_H;
        final int rows = KEYS.length + 1;
        final int cellW = (width - (COLS + 1) * GAP) / COLS;
        final int cellH = (height - DISPLAY_H - (rows + 1) * GAP) / rows;
        for (int r = 0; r < KEYS.length; r++) {
            final int by = padTop + GAP + r * (cellH + GAP);
            for (int c = 0; c < COLS; c++) {
                keys[r][c].setBounds(x + GAP + c * (cellW + GAP), by, cellW, cellH);
            }
        }
        equals.setBounds(x + GAP, padTop + GAP + KEYS.length * (cellH + GAP), width - 2 * GAP, cellH);
        root.render(g, ctx);
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

    /** As much of the string's end as fits, marked with a leading ".." when the start was dropped. */
    private static String tail(final Font font, final String s, final int maxWidth) {
        if (font.width(s) <= maxWidth) {
            return s;
        }
        String out = s;
        while (out.length() > 1 && font.width(".." + out) > maxWidth) {
            out = out.substring(1);
        }
        return ".." + out;
    }

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        root.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseReleased(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        root.mouseReleased(mouseX, mouseY, button);
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
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            evaluate();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            press("<-");
            return true;
        }
        return false;
    }
}
