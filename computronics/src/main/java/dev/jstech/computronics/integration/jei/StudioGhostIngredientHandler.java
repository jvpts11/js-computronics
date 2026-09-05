/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.integration.jei;

import dev.jstech.computronics.client.os.DesktopScreen;
import dev.jstech.computronics.client.os.PatternStudioApp;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Lets an item dragged out of the viewer's ingredient list be dropped onto a Pattern Studio cell. The targets
 * are the ghost cells the Studio drew in its last frame, translated from desktop to screen coordinates; the
 * drop sends the same edit a click with the item on the cursor would.
 */
public final class StudioGhostIngredientHandler implements IGhostIngredientHandler<DesktopScreen> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(final DesktopScreen screen, final ITypedIngredient<I> ingredient,
                                               final boolean doStart) {
        final PatternStudioApp studio = PatternStudioApp.active();
        final List<Target<I>> targets = new ArrayList<>();
        if (studio == null || !screen.isFront(studio) || ingredient.getItemStack().isEmpty()) {
            return targets;
        }
        final int ox = screen.desktopX();
        final int oy = screen.desktopY();
        for (final int[] cell : studio.ghostCells()) {
            final int kind = cell[4];
            final int index = cell[5];
            final Rect2i area = new Rect2i(ox + cell[0], oy + cell[1], cell[2], cell[3]);
            targets.add(new Target<>() {
                @Override
                public Rect2i getArea() {
                    return area;
                }

                @Override
                public void accept(final I dropped) {
                    final ItemStack stack = dropped instanceof ItemStack s ? s
                            : ingredient.getItemStack().orElse(ItemStack.EMPTY);
                    if (!stack.isEmpty()) {
                        studio.dropInto(kind, index, stack);
                    }
                }
            });
        }
        return targets;
    }

    @Override
    public void onComplete() {
    }
}
