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
import dev.jsc.jscomputronics.module.computing.menu.PatternEncoderMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Screen for the Pattern Encoder: the ghost 3x3 recipe grid with a live result preview on the left, the media bay with its disc readout and the WRITE / ERASE controls on the right, and the pattern list already on the disc along the bottom.
 */
public class PatternEncoderScreen extends AbstractComputerScreen<PatternEncoderMenu> {

    private static final int WRITE_X = 100;
    private static final int WRITE_Y = 86;
    private static final int WRITE_W = 92;
    private static final int ERASE_X = 138;
    private static final int ERASE_Y = 56;
    private static final int ERASE_W = 54;
    private static final int BTN_H = 14;

    public PatternEncoderScreen(final PatternEncoderMenu menu, final Inventory inventory,
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

        // Ghost grid + result preview + media bay.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                JscOsTheme.slot(g, x + 26 + col * 18, y + 32 + row * 18);
            }
        }
        JscOsTheme.slot(g, x + 100, y + 50);
        JscOsTheme.slot(g, x + 138, y + 32);

        // WRITE always drawn; ERASE only when it can act (no dead buttons).
        JscOsTheme.button(g, x + WRITE_X, y + WRITE_Y, WRITE_W, BTN_H,
                menu.canWrite() && hover(mouseX, mouseY, WRITE_X, WRITE_Y, WRITE_W, BTN_H));
        if (menu.canErase()) {
            JscOsTheme.button(g, x + ERASE_X, y + ERASE_Y, ERASE_W, BTN_H,
                    hover(mouseX, mouseY, ERASE_X, ERASE_Y, ERASE_W, BTN_H));
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
        JscOsTheme.text(g, font, "PATTERN ENCODER", 12, 11, JscOsTheme.text());

        JscOsTheme.text(g, font, "RECIPE", 26, 23, JscOsTheme.dim());
        JscOsTheme.text(g, font, ">", 88, 55, JscOsTheme.dim());
        JscOsTheme.text(g, font, "MEDIA", 138, 23, JscOsTheme.dim());

        // Disc readout right of the media slot.
        final ItemStack disc = menu.mediaStack();
        if (disc.isEmpty()) {
            JscOsTheme.textS(g, font, "no disc", 158, 36, JscOsTheme.dim());
        } else {
            final List<CraftingPattern> patterns = PatternDiscItem.patterns(disc);
            JscOsTheme.textS(g, font, patterns.size() + (patterns.size() == 1 ? " pattern" : " patterns"),
                    158, 34, JscOsTheme.text());
            if (disc.getItem() instanceof PatternDiscItem item && item.isRewritable()) {
                final int cycles = PatternDiscItem.cyclesLeft(disc);
                JscOsTheme.textS(g, font, cycles > 0 ? cycles + " cycles" : "read-only",
                        158, 43, cycles > 0 ? JscOsTheme.amber() : JscOsTheme.dim());
            } else {
                JscOsTheme.textS(g, font, "write-once", 158, 43, JscOsTheme.dim());
            }
        }

        if (menu.canErase()) {
            JscOsTheme.textCenter(g, font, "ERASE", ERASE_X + ERASE_W / 2, ERASE_Y + 4, JscOsTheme.amber());
        }
        final boolean writable = menu.canWrite();
        JscOsTheme.textCenter(g, font, "WRITE PATTERN", WRITE_X + WRITE_W / 2, WRITE_Y + 4,
                writable ? JscOsTheme.green() : JscOsTheme.dim());

        // Why WRITE is disabled, stated inline rather than a dead button.
        if (!writable) {
            final String reason = menu.preview().isEmpty() ? "lay out a known recipe" : "insert a disc";
            JscOsTheme.textS(g, font, reason, 26, 92, JscOsTheme.dim());
        }

        // Patterns already on the disc (last three fit; the tooltip of the disc lists the count).
        final List<CraftingPattern> onDisc = disc.isEmpty() ? List.of() : PatternDiscItem.patterns(disc);
        JscOsTheme.textS(g, font, "ON THIS MEDIA", 8, 104, JscOsTheme.dim());
        if (onDisc.isEmpty()) {
            JscOsTheme.textS(g, font, "-", 8, 114, JscOsTheme.dim());
        } else {
            final int first = Math.max(0, onDisc.size() - 3);
            int rowY = 113;
            for (int i = first; i < onDisc.size(); i++) {
                final CraftingPattern p = onDisc.get(i);
                JscOsTheme.textS(g, font, "CRAFT", 8, rowY, JscOsTheme.accent());
                JscOsTheme.textS(g, font, p.result().getHoverName().getString() + " x" + p.result().getCount(),
                        40, rowY, JscOsTheme.text());
                JscOsTheme.textSRight(g, font, p.filledCells() + " ingredients", imageWidth - 8, rowY, JscOsTheme.dim());
                rowY += 9;
            }
        }
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (button == 0) {
            if (menu.canWrite() && hover((int) mouseX, (int) mouseY, WRITE_X, WRITE_Y, WRITE_W, BTN_H)) {
                sendButton(PatternEncoderMenu.BUTTON_WRITE);
                return true;
            }
            if (menu.canErase() && hover((int) mouseX, (int) mouseY, ERASE_X, ERASE_Y, ERASE_W, BTN_H)) {
                sendButton(PatternEncoderMenu.BUTTON_ERASE);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

}
