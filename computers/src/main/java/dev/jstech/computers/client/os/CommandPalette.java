/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.os;

import dev.jstech.core.client.gui.component.ListView;
import dev.jstech.core.client.gui.component.TextField;
import dev.jstech.core.client.gui.component.UiContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/**
 * A list of everything a window can do, narrowed by typing, run by Enter.
 *
 * <p>Every command a menu offers is here too, under the name the menu gives it, so a player who knows
 * what they want types three letters instead of finding the menu. The same box, given files instead of
 * commands, is how a file is opened by name.
 */
public final class CommandPalette {

    /** One thing the box can run: what it is called, the key that also runs it, and what it does. */
    public record Entry(String label, String shortcut, Runnable action) {
    }

    private static final int ROW_H = 9;
    private static final int W = 170;
    private static final int ROWS = 8;

    private final TextField query = new TextField(48);
    private final ListView<Entry> list;
    private final List<Entry> all = new ArrayList<>();
    private String prefix = ">";
    private boolean open;
    private int x;
    private int y;

    public CommandPalette() {
        this.list = new ListView<>(this::matching, ROW_H, this::drawRow).setOnClick((index, button, mx, my) -> run(index));
        this.query.setOnEdit(() -> this.list.setSelected(matching().isEmpty() ? -1 : 0));
    }

    /** Opens the box over {@code entries}; {@code prefix} is the mark typed first, ">" for commands. */
    public void open(final List<Entry> entries, final String prefix) {
        this.all.clear();
        this.all.addAll(entries);
        this.prefix = prefix;
        this.query.set("");
        this.list.setSelected(entries.isEmpty() ? -1 : 0);
        this.list.setScroll(0);
        this.open = true;
    }

    public void close() {
        this.open = false;
    }

    public boolean isOpen() {
        return this.open;
    }

    /** The entries whose name holds every word typed, in the order they were given. */
    public List<Entry> matching() {
        final String[] words = this.query.edit().toLowerCase(Locale.ROOT).trim().split("\\s+");
        final List<Entry> out = new ArrayList<>();
        for (final Entry entry : this.all) {
            final String label = entry.label().toLowerCase(Locale.ROOT);
            boolean fits = true;
            for (final String word : words) {
                if (!word.isEmpty() && !label.contains(word)) {
                    fits = false;
                    break;
                }
            }
            if (fits) {
                out.add(entry);
            }
        }
        return out;
    }

    private void run(final int index) {
        final List<Entry> shown = matching();
        if (index < 0 || index >= shown.size()) {
            return;
        }
        final Entry entry = shown.get(index);
        close();
        entry.action().run();
    }

    private void drawRow(final GuiGraphics g, final UiContext ctx, final Entry entry, final int index,
                         final int rx, final int ry, final int width, final int height,
                         final boolean hovered, final boolean selected) {
        g.drawString(ctx.font(), ctx.font().plainSubstrByWidth(entry.label(), width - 44), rx + 3, ry + 1,
                ctx.skin().listRowText(selected), false);
        if (!entry.shortcut().isEmpty()) {
            g.drawString(ctx.font(), entry.shortcut(), rx + width - ctx.font().width(entry.shortcut()) - 3, ry + 1,
                    ctx.skin().dim(), false);
        }
    }

    /** Draws the box at the top middle of the window it belongs to. */
    public void render(final GuiGraphics g, final UiContext ctx, final int winX, final int winY, final int winW) {
        if (!this.open) {
            return;
        }
        this.x = winX + (winW - W) / 2;
        this.y = winY + 4;
        final int rows = Math.min(ROWS, Math.max(1, matching().size()));
        final int h = 3 + 11 + 2 + rows * ROW_H + 3;
        g.fill(this.x - 1, this.y - 1, this.x + W + 1, this.y + h + 1, 0xFF000000);
        ctx.skin().panel(g, this.x, this.y, W, h);
        g.drawString(ctx.font(), this.prefix, this.x + 4, this.y + 5, ctx.skin().dim(), false);
        this.query.setBounds(this.x + 4 + ctx.font().width(this.prefix) + 3, this.y + 3,
                W - 8 - ctx.font().width(this.prefix) - 3, 11);
        this.query.render(g, ctx);
        this.list.setBounds(this.x + 2, this.y + 16, W - 4, rows * ROW_H);
        this.list.render(g, ctx);
        if (matching().isEmpty()) {
            g.drawString(ctx.font(), "No matching results", this.x + 5, this.y + 17, ctx.skin().dim(), false);
        }
    }

    public boolean mouseClicked(final double mx, final double my, final int button) {
        if (!this.open) {
            return false;
        }
        if (this.list.contains(mx, my)) {
            return this.list.mouseClicked(mx, my, button);
        }
        if (this.query.contains(mx, my)) {
            return true;
        }
        close();
        return true;
    }

    public boolean charTyped(final char c) {
        if (!this.open) {
            return false;
        }
        this.query.charTyped(c);
        this.list.setSelected(matching().isEmpty() ? -1 : 0);
        return true;
    }

    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (!this.open) {
            return false;
        }
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> close();
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> run(this.list.selected());
            case GLFW.GLFW_KEY_DOWN -> this.list.setSelected(Math.min(matching().size() - 1, this.list.selected() + 1));
            case GLFW.GLFW_KEY_UP -> this.list.setSelected(Math.max(0, this.list.selected() - 1));
            default -> {
                this.query.keyPressed(key, scanCode, modifiers);
                this.list.setSelected(matching().isEmpty() ? -1 : 0);
            }
        }
        return true;
    }

    public boolean mouseScrolled(final double mx, final double my, final double delta) {
        return this.open && this.list.mouseScrolled(mx, my, delta);
    }
}
