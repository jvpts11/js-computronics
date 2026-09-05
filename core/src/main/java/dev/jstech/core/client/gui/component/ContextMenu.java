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
        final int h = items.size() * itemHeight + 2;
        final int mx = Math.max(boundX, Math.min(x, boundX + boundW - itemWidth - 1));
        final int my = Math.max(boundY, Math.min(y, boundY + boundH - h - 1));
        setBounds(mx, my, itemWidth, h);
        open = true;
    }

    public void close() {
        open = false;
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
        g.fill(x() - 1, y() - 1, right() + 1, bottom() + 1, 0xFF000000);
        g.fill(x(), y(), right(), bottom(), ctx.skin().panelBg());
        final int hover = itemAt(ctx.mouseX(), ctx.mouseY());
        int iy = y() + 1;
        for (int i = 0; i < items.size(); i++) {
            final Item item = items.get(i);
            if (item.isSeparator()) {
                g.fill(x() + 3, iy + itemHeight / 2, right() - 3, iy + itemHeight / 2 + 1, ctx.skin().edge());
            } else {
                final boolean lit = i == hover && item.enabled();
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

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (open && key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return false;
    }
}
