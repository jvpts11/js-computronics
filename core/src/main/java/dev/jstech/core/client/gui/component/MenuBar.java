/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A row of menu titles along the top of a window, each dropping a menu when clicked.
 *
 * <p>A title is a word and a place. The items under it are asked for the moment it opens, so a menu
 * always says what the window can do right now, greyed where it cannot. The drop-down is a
 * {@link ContextMenu}, which is what gives it the keyboard.
 */
public final class MenuBar extends UiComponent {

    /** How tall the bar is; every window that has one keeps the same. */
    public static final int HEIGHT = 10;

    private static final int GAP = 8;
    private static final int LEFT = 4;

    private final List<String> titles = new ArrayList<>();
    private final List<Supplier<List<ContextMenu.Item>>> menus = new ArrayList<>();
    private final ContextMenu menu;
    private int open = -1;

    /* The rectangle a drop-down has to stay inside: the window, set by whoever lays this out. */
    private int boundX;
    private int boundY;
    private int boundW;
    private int boundH;

    public MenuBar(final int itemWidth, final int itemHeight) {
        this.menu = new ContextMenu(itemWidth, itemHeight);
    }

    /** Adds a title and what drops from it. */
    public MenuBar add(final String title, final Supplier<List<ContextMenu.Item>> items) {
        this.titles.add(title);
        this.menus.add(items);
        return this;
    }

    /** Where a drop-down may go, which is the window this bar sits in. */
    public MenuBar setWindow(final int x, final int y, final int width, final int height) {
        this.boundX = x;
        this.boundY = y;
        this.boundW = width;
        this.boundH = height;
        return this;
    }

    public boolean isOpen() {
        return this.menu.isOpen();
    }

    public void close() {
        this.menu.close();
        this.open = -1;
    }

    /** Opens the menu under the {@code index}-th title, as a click on it would. */
    public void open(final int index) {
        if (index < 0 || index >= this.titles.size()) {
            return;
        }
        this.open = index;
        this.menu.open(this.menus.get(index).get(), titleX(index) - 2, y() + height(),
                this.boundX, this.boundY, this.boundW, this.boundH);
    }

    /** The titles, for a test that wants to find one. */
    public List<String> titles() {
        return List.copyOf(this.titles);
    }

    /** The centre of the {@code index}-th title, where a test clicks to open its menu; null for none such. */
    public int[] titleCenter(final int index) {
        if (index < 0 || index >= this.titles.size()) {
            return null;
        }
        final net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        return new int[] {titleX(index) + font.width(this.titles.get(index)) / 2, y() + height() / 2};
    }

    /** The drop-down, for a test that wants to pick an entry from it. */
    public ContextMenu menu() {
        return this.menu;
    }

    /** The left edge of a title, from the widths of the ones before it. */
    private int titleX(final int index) {
        int mx = x() + LEFT;
        final net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        for (int i = 0; i < index; i++) {
            mx += font.width(this.titles.get(i)) + GAP;
        }
        return mx;
    }

    /** Which title is under {@code mx}, or -1. */
    public int titleAt(final double mx, final double my) {
        if (my < y() || my >= y() + height()) {
            return -1;
        }
        final net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        int left = x() + LEFT;
        for (int i = 0; i < this.titles.size(); i++) {
            final int w = font.width(this.titles.get(i));
            if (mx >= left - 2 && mx < left + w + 2) {
                return i;
            }
            left += w + GAP;
        }
        return -1;
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        ctx.skin().panel(g, x(), y(), width(), height());
        int mx = x() + LEFT;
        for (int i = 0; i < this.titles.size(); i++) {
            final String title = this.titles.get(i);
            final int w = ctx.font().width(title);
            if (i == this.open && this.menu.isOpen()) {
                g.fill(mx - 2, y() + 1, mx + w + 2, y() + height() - 1, ctx.skin().accent());
            }
            g.drawString(ctx.font(), title, mx, y() + 1,
                    i == this.open && this.menu.isOpen() ? 0xFFFFFFFF : ctx.skin().text(), false);
            mx += w + GAP;
        }
        if (this.menu.isOpen()) {
            this.menu.render(g, ctx);
        } else {
            this.open = -1;
        }
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        if (this.menu.isOpen()) {
            final boolean taken = this.menu.mouseClicked(mx, my, button);
            if (!this.menu.isOpen()) {
                this.open = -1;
            }
            // A click on another title while one is open moves to it rather than only closing.
            if (!taken) {
                final int other = titleAt(mx, my);
                if (other >= 0) {
                    open(other);
                    return true;
                }
            }
            return true;
        }
        final int which = titleAt(mx, my);
        if (which >= 0) {
            open(which);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (!this.menu.isOpen()) {
            return false;
        }
        final boolean taken = this.menu.keyPressed(key, scanCode, modifiers);
        if (!this.menu.isOpen()) {
            this.open = -1;
        }
        return taken;
    }
}
