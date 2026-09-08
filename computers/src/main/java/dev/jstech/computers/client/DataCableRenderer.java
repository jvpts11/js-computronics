/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.jstech.computers.JsComputers;
import dev.jstech.computers.block.part.CablePartType;
import dev.jstech.computers.blockentity.DataCableBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the interaction-bus parts mounted on a data cable.
 */
public class DataCableRenderer implements BlockEntityRenderer<DataCableBlockEntity> {

    public static final ModelResourceLocation IMPORT_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, "block/import_bus_part"));
    public static final ModelResourceLocation EXPORT_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, "block/export_bus_part"));

    public DataCableRenderer(final BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(final DataCableBlockEntity cable, final float partialTick, final PoseStack pose,
                       final MultiBufferSource buffer, final int packedLight, final int packedOverlay) {
        if (!cable.hasAnyPart()) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        final ModelBlockRenderer renderer = mc.getBlockRenderer().getModelRenderer();
        final VertexConsumer consumer = buffer.getBuffer(RenderType.cutout());
        for (final Direction face : Direction.values()) {
            final CablePartType type = cable.partType(face);
            if (type == null) {
                continue;
            }
            final ModelResourceLocation modelLocation = switch (type) {
                case IMPORT -> IMPORT_MODEL;
                case EXPORT -> EXPORT_MODEL;
                // Input feeds like an Export, Receiving pulls like an Import; they reuse the part models for now.
                case INPUT -> EXPORT_MODEL;
                case RECEIVING -> IMPORT_MODEL;
            };
            final BakedModel model = mc.getModelManager().getModel(modelLocation);
            pose.pushPose();
            orient(pose, face);
            renderer.renderModel(pose.last(), consumer, cable.getBlockState(), model,
                    1.0F, 1.0F, 1.0F, packedLight, packedOverlay);
            pose.popPose();
        }
    }

    private static void orient(final PoseStack pose, final Direction face) {
        pose.translate(0.5, 0.5, 0.5);
        switch (face) {
            case NORTH -> { /* authored orientation */ }
            case SOUTH -> pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            case WEST -> pose.mulPose(Axis.YP.rotationDegrees(90.0F));
            case EAST -> pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
            case UP -> pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        }
        pose.translate(-0.5, -0.5, -0.5);
    }
}
