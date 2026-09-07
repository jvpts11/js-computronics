/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.jstech.computronics.item.CabinetBlockItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Draws a cabinet's item as the cabinet: the same GeckoLib model the world shows, scaled into the slot.
 * A cabinet is several blocks around an origin at its controller, so it is shrunk to fit a single block
 * of room and re-centred on the item's own middle; otherwise it would render at full size, mostly
 * outside the slot.
 */
public final class CabinetItemRenderer extends GeoItemRenderer<CabinetBlockItem> {

    /** How much of the item's own block of room the cabinet fills. */
    private static final float FILL = 0.86F;

    public CabinetItemRenderer() {
        super(new CabinetItemModel());
    }

    @Override
    public void preRender(final PoseStack poseStack, final CabinetBlockItem item, final BakedGeoModel model,
                          final MultiBufferSource bufferSource, final VertexConsumer buffer,
                          final boolean isReRender, final float partialTick, final int packedLight,
                          final int packedOverlay, final int colour) {
        super.preRender(poseStack, item, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                packedOverlay, colour);
        /*
         * 16 model units make a block: shrink the cabinet's longest side to FILL of one block, then
         * bring the middle of the cabinet onto the item's own middle. Scaling first means the offset is
         * read in blocks of the cabinet's own space, which is how the fit is written.
         */
        final CabinetBlockItem.Fit fit = item.fit();
        final float scale = FILL * 16.0F / fit.span();
        poseStack.scale(scale, scale, scale);
        poseStack.translate(fit.offsetX(), fit.offsetY(), fit.offsetZ());
    }

    /** The model of the cabinet the item places, and the atlas painted for it. */
    private static final class CabinetItemModel extends GeoModel<CabinetBlockItem> {

        @Override
        public ResourceLocation getModelResource(final CabinetBlockItem item) {
            return item.modelResource();
        }

        @Override
        public ResourceLocation getTextureResource(final CabinetBlockItem item) {
            return item.textureResource();
        }

        @Override
        public ResourceLocation getAnimationResource(final CabinetBlockItem item) {
            return item.animationResource();
        }
    }
}
