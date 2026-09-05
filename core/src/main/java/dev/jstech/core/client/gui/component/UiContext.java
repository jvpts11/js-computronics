/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.component;

import dev.jstech.core.client.gui.skin.Skin;
import net.minecraft.client.gui.Font;

/**
 * What a component needs to draw one frame: the skin it paints through, the font, where the cursor is and the
 * partial tick. Built once per frame by whoever owns the component tree and handed down through it.
 *
 * @param skin        the look to draw through
 * @param font        the font every label uses
 * @param mouseX      the cursor, in the same coordinates the components are laid out in
 * @param mouseY      the cursor, in the same coordinates the components are laid out in
 * @param partialTick the render partial tick
 */
public record UiContext(Skin skin, Font font, int mouseX, int mouseY, float partialTick) {

    /** Whether the cursor is inside the rectangle. */
    public boolean over(final int x, final int y, final int w, final int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
