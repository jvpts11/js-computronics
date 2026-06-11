/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern;
import dev.jsc.jscomputronics.module.computing.item.PatternDiscItem;
import dev.jsc.jscomputronics.module.computing.menu.PatternReaderMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Screen for the Pattern Reader: the disc readout, the selectable pattern list (click a row to toggle it), and LOAD SELECTED / LOAD ALL into the adjacent Crafting Computer's Recipe ROM with its usage shown live.
 */
public class PatternReaderScreen extends AbstractContainerScreen<PatternReaderMenu> {

    private static final int LIST_X = 8;
    private static final int LIST_Y = 60;
    private static final int ROW_H = 12;
    private static final int VISIBLE_ROWS = 4;
    private static final int LOAD_SEL_X = 8;
    private static final int LOAD_ALL_X = 122;
    private static final int BTN_Y = 112;
    private static final int BTN_H = 14;

    private final Set<Integer> selection = new LinkedHashSet<>();

    private int scroll;

    public PatternReaderScreen(final PatternReaderMenu menu, final Inventory inventory,
                               final Component title) {
        super(menu, inventory, title);
        this.imageWidth = 200;
        this.imageHeight = 218;
        this.titleLabelX = -10000;
        this.inventoryLabelY = -10000;
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;
        JscOsTheme.window(g, x, y, imageWidth, imageHeight);
        JscOsTheme.headerBar(g, x + 6, y + 6, imageWidth - 12);
        JscOsTheme.slot(g, x + 12, y + 30);

        // Pattern rows (panel strips; the selected ones carry an accent edge).
        final List<CraftingPattern> patterns = menu.discPatterns();
        clampScroll(patterns.size());
        for (int row = 0; row < Math.min(VISIBLE_ROWS, patterns.size()); row++) {
            final int index = row + scroll;
            final int rowY = y + LIST_Y + row * ROW_H;
            g.fill(x + LIST_X, rowY, x + imageWidth - 8, rowY + ROW_H - 2, JscOsTheme.PANEL);
            if (selection.contains(index)) {
                g.fill(x + LIST_X, rowY, x + LIST_X + 2, rowY + ROW_H - 2, JscOsTheme.AMBER);
            }
        }

        final boolean usable = menu.hasComputer() && !patterns.isEmpty();
        if (usable) {
            JscOsTheme.button(g, x + LOAD_SEL_X, y + BTN_Y, 106, BTN_H,
                    !selection.isEmpty() && hover(mouseX, mouseY, LOAD_SEL_X, BTN_Y, 106, BTN_H));
            JscOsTheme.button(g, x + LOAD_ALL_X, y + BTN_Y, 70, BTN_H,
                    hover(mouseX, mouseY, LOAD_ALL_X, BTN_Y, 70, BTN_H));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                JscOsTheme.slot(g, x + 8 + col * 18, y + 138 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            JscOsTheme.slot(g, x + 8 + col * 18, y + 196);
        }
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        JscOsTheme.text(g, font, "PATTERN READER", 12, 11, JscOsTheme.TEXT);

        final ItemStack disc = menu.slots.get(PatternReaderMenu.MEDIA_SLOT).getItem();
        final List<CraftingPattern> patterns = menu.discPatterns();
        if (disc.isEmpty()) {
            JscOsTheme.textS(g, font, "insert pattern media", 34, 36, JscOsTheme.DIM);
        } else {
            JscOsTheme.textS(g, font, patterns.size() + (patterns.size() == 1 ? " pattern" : " patterns"),
                    34, 32, JscOsTheme.TEXT);
            if (disc.getItem() instanceof PatternDiscItem item && item.isRewritable()) {
                final int cycles = PatternDiscItem.cyclesLeft(disc);
                JscOsTheme.textS(g, font, cycles > 0 ? cycles + " cycles left" : "read-only",
                        34, 41, cycles > 0 ? JscOsTheme.AMBER : JscOsTheme.DIM);
            } else {
                JscOsTheme.textS(g, font, "write-once", 34, 41, JscOsTheme.DIM);
            }
        }

        JscOsTheme.textS(g, font, "PATTERNS ON MEDIA", LIST_X, 53, JscOsTheme.DIM);
        for (int row = 0; row < Math.min(VISIBLE_ROWS, patterns.size()); row++) {
            final int index = row + scroll;
            final CraftingPattern p = patterns.get(index);
            final int rowY = LIST_Y + row * ROW_H + 2;
            JscOsTheme.textS(g, font, "CRAFT", LIST_X + 5, rowY, JscOsTheme.ACCENT);
            JscOsTheme.textS(g, font, p.result().getHoverName().getString() + " x" + p.result().getCount(),
                    LIST_X + 36, rowY, JscOsTheme.TEXT);
            JscOsTheme.textSRight(g, font, selection.contains(index) ? "selected" : "",
                    imageWidth - 12, rowY, JscOsTheme.AMBER);
        }
        if (patterns.size() > VISIBLE_ROWS) {
            JscOsTheme.textSRight(g, font, (scroll + 1) + "-" + Math.min(scroll + VISIBLE_ROWS, patterns.size())
                    + " / " + patterns.size(), imageWidth - 8, 53, JscOsTheme.DIM);
        }

        final boolean usable = menu.hasComputer() && !patterns.isEmpty();
        if (usable) {
            JscOsTheme.textCenter(g, font, "LOAD SELECTED (" + selection.size() + ")",
                    LOAD_SEL_X + 53, BTN_Y + 4, selection.isEmpty() ? JscOsTheme.DIM : JscOsTheme.GREEN);
            JscOsTheme.textCenter(g, font, "LOAD ALL", LOAD_ALL_X + 35, BTN_Y + 4, JscOsTheme.ACCENT);
        }

        // Target line: the adjacent computer and its ROM budget — or why nothing can load.
        if (menu.hasComputer()) {
            JscOsTheme.textS(g, font, "> Crafting Computer", LIST_X, 130, JscOsTheme.ACCENT);
            JscOsTheme.textSRight(g, font, "ROM " + menu.romUsed() + " / " + menu.romLimit(),
                    imageWidth - 8, 130, menu.romUsed() >= menu.romLimit() ? JscOsTheme.RED : JscOsTheme.TEXT);
        } else {
            JscOsTheme.textS(g, font, "no Crafting Computer adjacent", LIST_X, 130, JscOsTheme.RED);
        }
    }

    private void clampScroll(final int count) {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, count - VISIBLE_ROWS)));
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double dx, final double dy) {
        final int count = menu.discPatterns().size();
        if (count > VISIBLE_ROWS) {
            scroll -= (int) Math.signum(dy);
            clampScroll(count);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    private boolean hover(final int mouseX, final int mouseY, final int rx, final int ry, final int w, final int h) {
        final int mx = mouseX - leftPos;
        final int my = mouseY - topPos;
        return mx >= rx && mx < rx + w && my >= ry && my < ry + h;
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (button == 0) {
            final List<CraftingPattern> patterns = menu.discPatterns();
            // Row click toggles that pattern's selection (mirrored server-side by button id).
            for (int row = 0; row < Math.min(VISIBLE_ROWS, patterns.size()); row++) {
                final int index = row + scroll;
                if (hover((int) mouseX, (int) mouseY, LIST_X, LIST_Y + row * ROW_H, imageWidth - 16, ROW_H - 2)) {
                    if (!selection.remove(index)) {
                        selection.add(index);
                    }
                    sendButton(PatternReaderMenu.BUTTON_TOGGLE_BASE + index);
                    return true;
                }
            }
            final boolean usable = menu.hasComputer() && !patterns.isEmpty();
            if (usable && !selection.isEmpty()
                    && hover((int) mouseX, (int) mouseY, LOAD_SEL_X, BTN_Y, 106, BTN_H)) {
                sendButton(PatternReaderMenu.BUTTON_LOAD_SELECTED);
                selection.clear();
                return true;
            }
            if (usable && hover((int) mouseX, (int) mouseY, LOAD_ALL_X, BTN_Y, 70, BTN_H)) {
                sendButton(PatternReaderMenu.BUTTON_LOAD_ALL);
                selection.clear();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void sendButton(final int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
