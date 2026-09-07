/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Draws a fluid as a 16×16 icon in a GUI (the still texture from the block atlas, tinted by the fluid's colour) so a fluid row renders in the network grid the same size as an item icon.
 */
public final class FluidSprite {

    private FluidSprite() {
    }

    public static void draw(final GuiGraphics g, final FluidStack fluid, final int x, final int y) {
        if (fluid.isEmpty()) {
            return;
        }
        final IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getFluid());
        final ResourceLocation texture = ext.getStillTexture(fluid);
        if (texture == null) {
            return;
        }
        final TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager()
                .getAtlas(InventoryMenu.BLOCK_ATLAS).getSprite(texture);
        final int tint = ext.getTintColor(fluid);
        final float r = (tint >> 16 & 0xFF) / 255F;
        final float green = (tint >> 8 & 0xFF) / 255F;
        final float b = (tint & 0xFF) / 255F;
        final float a = (tint >>> 24) == 0 ? 1F : (tint >>> 24) / 255F;
        g.setColor(r, green, b, a);
        g.blit(x, y, 0, 16, 16, sprite);
        g.setColor(1F, 1F, 1F, 1F);
    }
}
