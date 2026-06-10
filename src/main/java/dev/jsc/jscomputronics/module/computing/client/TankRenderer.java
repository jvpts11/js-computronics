/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.jsc.jscomputronics.module.computing.blockentity.TankBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Draws the fluid held inside a {@link TankBlockEntity} as a coloured volume that rises with the fill level, so the player can read at a glance how much fits.
 */
public class TankRenderer implements BlockEntityRenderer<TankBlockEntity> {

    private static final float INSET = 0.07F;

    public TankRenderer(final BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(final TankBlockEntity tank, final float partialTick, final PoseStack pose,
                       final MultiBufferSource buffer, final int packedLight, final int packedOverlay) {
        final FluidStack fluid = tank.fluid();
        if (fluid.isEmpty()) {
            return;
        }
        final float fill = Mth.clamp(tank.fillFraction(), 0F, 1F);
        if (fill <= 0F) {
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
        final int r = tint >> 16 & 0xFF;
        final int g = tint >> 8 & 0xFF;
        final int b = tint & 0xFF;
        final int a = (tint >>> 24) == 0 ? 0xFF : tint >>> 24;

        final float lo = INSET;
        final float hi = 1F - INSET;
        final float top = lo + (hi - lo) * fill;

        final VertexConsumer vc = buffer.getBuffer(RenderType.translucent());
        renderVolume(vc, pose.last(), packedLight, packedOverlay, r, g, b, a, sprite, fill,
                lo, lo, lo, hi, top, hi);
    }

    private static void renderVolume(final VertexConsumer vc, final PoseStack.Pose pose, final int light,
                                     final int overlay, final int r, final int g, final int b, final int a,
                                     final TextureAtlasSprite sprite, final float fill,
                                     final float x0, final float y0, final float z0,
                                     final float x1, final float y1, final float z1) {
        final float u0 = sprite.getU0();
        final float u1 = sprite.getU1();
        final float v0 = sprite.getV0();
        final float v1 = sprite.getV1();
        // Side faces fill the sprite bottom-up by the fill fraction, so the texture never stretches.
        final float vTop = v1 - (v1 - v0) * fill;

        // Vertices are wound counter-clockwise seen from outside (T x B = outward normal).
        // TOP (+Y) and BOTTOM (-Y) map the whole sprite; sides map [vTop..v1].
        quad(vc, pose, light, overlay, r, g, b, a, 0F, 1F, 0F,
                x0, y1, z0, u0, v1, x0, y1, z1, u0, v0, x1, y1, z1, u1, v0, x1, y1, z0, u1, v1);
        quad(vc, pose, light, overlay, r, g, b, a, 0F, -1F, 0F,
                x0, y0, z0, u0, v0, x1, y0, z0, u1, v0, x1, y0, z1, u1, v1, x0, y0, z1, u0, v1);
        quad(vc, pose, light, overlay, r, g, b, a, 0F, 0F, -1F,
                x0, y0, z0, u0, v1, x0, y1, z0, u0, vTop, x1, y1, z0, u1, vTop, x1, y0, z0, u1, v1);
        quad(vc, pose, light, overlay, r, g, b, a, 0F, 0F, 1F,
                x0, y0, z1, u0, v1, x1, y0, z1, u1, v1, x1, y1, z1, u1, vTop, x0, y1, z1, u0, vTop);
        quad(vc, pose, light, overlay, r, g, b, a, -1F, 0F, 0F,
                x0, y0, z0, u0, v1, x0, y0, z1, u1, v1, x0, y1, z1, u1, vTop, x0, y1, z0, u0, vTop);
        quad(vc, pose, light, overlay, r, g, b, a, 1F, 0F, 0F,
                x1, y0, z0, u0, v1, x1, y1, z0, u0, vTop, x1, y1, z1, u1, vTop, x1, y0, z1, u1, v1);
    }

    private static void quad(final VertexConsumer vc, final PoseStack.Pose pose, final int light,
                             final int overlay, final int r, final int g, final int b, final int a,
                             final float nx, final float ny, final float nz,
                             final float ax, final float ay, final float az, final float au, final float av,
                             final float bx, final float by, final float bz, final float bu, final float bv,
                             final float cx, final float cy, final float cz, final float cu, final float cv,
                             final float dx, final float dy, final float dz, final float du, final float dv) {
        vert(vc, pose, ax, ay, az, au, av, r, g, b, a, light, overlay, nx, ny, nz);
        vert(vc, pose, bx, by, bz, bu, bv, r, g, b, a, light, overlay, nx, ny, nz);
        vert(vc, pose, cx, cy, cz, cu, cv, r, g, b, a, light, overlay, nx, ny, nz);
        vert(vc, pose, dx, dy, dz, du, dv, r, g, b, a, light, overlay, nx, ny, nz);
    }

    private static void vert(final VertexConsumer vc, final PoseStack.Pose pose, final float x, final float y,
                             final float z, final float u, final float v, final int r, final int g, final int b,
                             final int a, final int light, final int overlay, final float nx, final float ny,
                             final float nz) {
        vc.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
