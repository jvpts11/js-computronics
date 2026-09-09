/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.component;

import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The menu a right-click opens at the cursor: a column of actions, some greyed out, separated by lines. It
 * is kept inside a rectangle so it never hangs off the window, takes every click while it is open and
 * closes on any of them, running the action the click landed on; Escape closes it too.
 */
public final class ContextMenu extends UiComponent {

    /** One entry: a label with an action, greyed out when it cannot apply; {@link #separator()} is a line. */
    public record Item(String label, boolean enabled, Runnable action) {

        private static final String SEPARATOR = "-";

        public static Item separator() {
            return new Item(SEPARATOR, false, () -> { });
        }

        public boolean isSeparator() {
            return SEPARATOR.equals(label);
        }
    }

    private final int itemWidth;
    private final int itemHeight;
    private List<Item> items = List.of();
    private boolean open;
    /** The item the keyboard is on, or -1 while the menu is being driven by the mouse alone. */
    private int selected = -1;

    public ContextMenu(final int itemWidth, final int itemHeight) {
        this.itemWidth = itemWidth;
        this.itemHeight = itemHeight;
    }

    public boolean isOpen() {
        return open;
    }

    public List<Item> items() {
        return items;
    }

    /**
     * Opens the menu with its top-left at ({@code x}, {@code y}), moved as needed to stay inside the
     * rectangle from ({@code boundX}, {@code boundY}) of {@code boundW} by {@code boundH}.
     */
    public void open(final List<Item> entries, final int x, final int y, final int boundX, final int boundY,
                     final int boundW, final int boundH) {
        items = List.copyOf(entries);
        this.anchorX = x;
        this.anchorY = y;
        this.boundX = boundX;
        this.boundY = boundY;
        this.boundW = boundW;
        this.boundH = boundH;
        place(itemWidth);
        measured = false;
        open = true;
        selected = next(-1, 1);
    }

    /* Where the menu was asked to open, kept so it can be placed again once its labels are measured. */
    private int anchorX;
    private int anchorY;
    private int boundX;
    private int boundY;
    private int boundW;
    private int boundH;
    /** Whether the labels have been measured with a font; a menu is drawn before it can be clicked. */
    private boolean measured = true;

    /** Puts the menu at its anchor, {@code w} wide, moved as needed to stay inside its bounds. */
    private void place(final int w) {
        final int h = items.size() * itemHeight + 2;
        final int mx = Math.max(boundX, Math.min(anchorX, boundX + boundW - w - 1));
        final int my = Math.max(boundY, Math.min(anchorY, boundY + boundH - h - 1));
        setBounds(mx, my, w, h);
    }

    public void close() {
        open = false;
        selected = -1;
    }

    /** The item the keyboard is on, or -1 when the menu is driven by the mouse alone. */
    public int selected() {
        return selected;
    }

    /** Puts the keyboard on an item, ignoring an index that is a separator or disabled. */
    public ContextMenu setSelected(final int index) {
        if (index >= 0 && index < items.size() && usable(items.get(index))) {
            selected = index;
        }
        return this;
    }

    private boolean usable(final Item item) {
        return item.enabled() && !item.isSeparator();
    }

    /** The next item that can be chosen from {@code from}, walking by {@code step} and wrapping. */
    private int next(final int from, final int step) {
        if (items.isEmpty()) {
            return -1;
        }
        int at = from;
        for (int i = 0; i < items.size(); i++) {
            at = Math.floorMod(at + step, items.size());
            if (usable(items.get(at))) {
                return at;
            }
        }
        return -1;
    }

    /** The index of the item under the point, or -1. */
    public int itemAt(final double mx, final double my) {
        if (!contains(mx, my)) {
            return -1;
        }
        final int index = (int) Math.floor((my - (y() + 1)) / (double) itemHeight);
        return index >= 0 && index < items.size() ? index : -1;
    }

    /** The centre of item {@code index}, where a test clicks it. */
    public int[] itemCenter(final int index) {
        return new int[] {x() + width() / 2, y() + 1 + index * itemHeight + itemHeight / 2};
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        if (!open) {
            return;
        }
        /*
         * The menu is as wide as its widest label: a label that does not fit is a menu the player
         * cannot read. The font is only known here, so the measuring waits for the first drawing.
         */
        if (!measured) {
            int widest = itemWidth;
            for (final Item item : items) {
                if (!item.isSeparator()) {
                    widest = Math.max(widest, ctx.font().width(item.label()) + 10);
                }
            }
            place(widest);
            measured = true;
        }
        g.fill(x() - 1, y() - 1, right() + 1, bottom() + 1, 0xFF000000);
        g.fill(x(), y(), right(), bottom(), ctx.skin().panelBg());
        final int hover = itemAt(ctx.mouseX(), ctx.mouseY());
        int iy = y() + 1;
        for (int i = 0; i < items.size(); i++) {
            final Item item = items.get(i);
            if (item.isSeparator()) {
                g.fill(x() + 3, iy + itemHeight / 2, right() - 3, iy + itemHeight / 2 + 1, ctx.skin().edge());
            } else {
                // The mouse lights what it is over; with the mouse elsewhere the keyboard's item stays lit.
                final boolean lit = item.enabled() && (i == hover || (hover < 0 && i == selected));
                if (lit) {
                    g.fill(x() + 1, iy, right() - 1, iy + itemHeight, ctx.skin().accent());
                }
                g.drawString(ctx.font(), item.label(), x() + 4, iy + 2,
                        lit ? 0xFFFFFFFF : (item.enabled() ? ctx.skin().text() : ctx.skin().dim()), false);
            }
            iy += itemHeight;
        }
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        if (!open) {
            return false;
        }
        final int index = itemAt(mx, my);
        close();
        if (index >= 0) {
            final Item item = items.get(index);
            if (item.enabled() && !item.isSeparator()) {
                item.action().run();
            }
        }
        return true;
    }

    /**
     * Drives the menu from the keyboard: up and down walk it, wrapping and stepping over separators and
     * anything disabled; Enter or Tab takes what is on; Escape leaves without taking anything.
     */
    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (!open) {
            return false;
        }
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> close();
            case GLFW.GLFW_KEY_UP -> selected = next(selected, -1);
            case GLFW.GLFW_KEY_DOWN -> selected = next(selected, 1);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_TAB -> {
                final int chosen = selected;
                close();
                if (chosen >= 0 && chosen < items.size()) {
                    items.get(chosen).action().run();
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
