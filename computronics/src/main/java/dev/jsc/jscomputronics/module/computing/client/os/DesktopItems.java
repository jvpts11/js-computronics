/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.os;

import dev.jstech.core.gui.layout.DesktopZ;
import dev.jsc.jscomputronics.module.computing.client.ChemicalSprite;
import dev.jsc.jscomputronics.module.computing.client.FluidSprite;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Draws an item inside a desktop window.
 *
 * <p>An item is a model, not a sprite: {@code renderItem} lifts it {@link DesktopZ#ITEM_LIFT} in front of
 * whatever pose it is given, and the count another {@link DesktopZ#DECORATION_LIFT}. A window that drew its
 * items with the raw calls therefore put them ~150 deep in front of the entire desktop, where they covered
 * every window in front of that one — two open windows painted their items over each other's chrome. These
 * helpers push the model back into the window's own depth band, so an item can never leave the window that
 * drew it. Every app draws its items through here; only the carried (cursor) stack, which is meant to ride
 * above everything, uses the raw call.
 */
public final class DesktopItems {

    private DesktopItems() {
    }

    /** The item's model, inside the current window's depth band. */
    public static void item(final GuiGraphics g, final ItemStack stack, final int x, final int y) {
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, DesktopZ.itemOffset());
        g.renderItem(stack, x, y);
        g.pose().popPose();
    }

    /** The item's count (and durability bar), just in front of the model it belongs to. */
    public static void count(final GuiGraphics g, final Font font, final ItemStack stack, final int x, final int y) {
        count(g, font, stack, x, y, null);
    }

    /** As above, with the count text spelled out (a network total is not the stack's own size). */
    public static void count(final GuiGraphics g, final Font font, final ItemStack stack, final int x, final int y,
                             final String label) {
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, DesktopZ.countOffset());
        g.renderItemDecorations(font, stack, x, y, label);
        g.pose().popPose();
    }

    /** Both, for the common case of an item cell that shows its total. */
    public static void itemWithCount(final GuiGraphics g, final Font font, final ItemStack stack, final int x,
                                     final int y, final String label) {
        item(g, stack, x, y);
        count(g, font, stack, x, y, label);
    }

    /**
     * Any kind of data in a cell: an item's model, a fluid's still texture or a chemical's swatch, with an
     * optional amount badge. A sprite is flat, so it sits at the window's item depth directly instead of
     * needing the lift an item model gets compensated for.
     */
    public static void data(final GuiGraphics g, final Font font, final StorageKey key, final int x, final int y,
                            @Nullable final String label) {
        if (key.isItem()) {
            item(g, key.stack(1), x, y);
            if (label != null) {
                count(g, font, key.stack(1), x, y, label);
            }
            return;
        }
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, DesktopZ.BAND_ITEM);
        if (key.isChemical()) {
            ChemicalSprite.draw(g, key, x, y);
        } else {
            FluidSprite.draw(g, key.fluidPrototype(), x, y);
        }
        g.pose().popPose();
        if (label != null) {
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, DesktopZ.BAND_COUNT);
            g.drawString(font, label, x + 17 - font.width(label), y + 9, 0xFFFFFFFF, true);
            g.pose().popPose();
        }
    }
}
