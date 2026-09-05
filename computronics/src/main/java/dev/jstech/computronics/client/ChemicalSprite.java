/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client;

import dev.jstech.computronics.storage.ChemicalBridges;
import dev.jstech.computronics.storage.StorageKey;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

/**
 * Draws a chemical in a 16x16 cell: a swatch in the chemical's own colour with a dark frame, the same footprint
 * an item or a fluid takes, so the three kinds of data sit side by side in every grid.
 */
public final class ChemicalSprite {

    private ChemicalSprite() {
    }

    public static void draw(final GuiGraphics g, final StorageKey key, final int x, final int y) {
        final int tint = ChemicalBridges.tint(Objects.requireNonNull(key.chemicalId()));
        g.fill(x + 1, y + 1, x + 15, y + 15, 0xFF202020);
        g.fill(x + 2, y + 2, x + 14, y + 14, tint);
        // A lighter top edge gives the swatch the volume of a bottle rather than a flat patch.
        g.fill(x + 2, y + 2, x + 14, y + 4, 0x40FFFFFF);
    }
}
