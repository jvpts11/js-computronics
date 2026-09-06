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
import com.mojang.math.Axis;
import dev.jstech.computronics.blockentity.ServerRackBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * Draws a rack as one cabinet from its controller block. The cabinet bone is always there; every row
 * has one bone per unit type and only the one matching what is seated shows, so an installed server
 * is literally in the rack in its own era's shape. The supercomputer's light bar follows the bays,
 * its chassis bands follow the seated nodes, and its livery panel hides when taken off for service.
 */
public final class RackRenderer extends GeoBlockRenderer<ServerRackBlockEntity> {

    private static final int ROWS = ServerRackBlockEntity.CAPACITY_U;

    public RackRenderer(final BlockEntityRendererProvider.Context context) {
        super(new RackGeoModel());
    }

    @Override
    protected Direction getFacing(final ServerRackBlockEntity rack) {
        final BlockState state = rack.getBlockState();
        return state.hasProperty(HorizontalDirectionalBlock.FACING)
                ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
    }

    /**
     * The model faces north with the cabinet extending east and south of the controller; each facing
     * turns it about the controller's centre so the front, the right column and the rear line up with
     * the multiblock's own footprint (right = clockwise of facing, rear = opposite of facing).
     */
    @Override
    protected void rotateBlock(final Direction facing, final PoseStack poseStack) {
        switch (facing) {
            case SOUTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180));
            case WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(90));
            case EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(270));
            default -> {
            }
        }
    }

    @Override
    public void preRender(final PoseStack poseStack, final ServerRackBlockEntity rack, final BakedGeoModel model,
                          final MultiBufferSource bufferSource, final VertexConsumer buffer, final boolean isReRender,
                          final float partialTick, final int packedLight, final int packedOverlay, final int colour) {
        super.preRender(poseStack, rack, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                packedOverlay, colour);
        // The baked model is shared by every rack of this kind, so every flag is set on every render.
        // Every row bone is decided here, including units the code has no seat for yet (a bone the
        // model carries but nothing can mount stays hidden), so an empty row is really empty.
        for (final GeoBone bone : model.topLevelBones()) {
            applyRowVisibility(rack, bone);
        }
        final boolean on = rack.anyBayOn();
        show(model, "lightbar_online", on);
        show(model, "lightbar_fault", !on && rack.anyComputerSeated());
        show(model, "service_panel", !rack.servicePanelOff());
    }

    private static void applyRowVisibility(final ServerRackBlockEntity rack, final GeoBone bone) {
        final String name = bone.getName();
        if (name.startsWith("row_")) {
            final int cut = name.indexOf('_', 4);
            if (cut > 4) {
                final int row = Integer.parseInt(name.substring(4, cut));
                final String unit = name.substring(cut + 1);
                bone.setHidden(!rowBoneVisible(rack, row, unit));
            }
        }
        for (final GeoBone child : bone.getChildBones()) {
            applyRowVisibility(rack, child);
        }
    }

    /** Rows count from the top of the cabinet, like the rack's slots; a 2U unit at row r also covers r + 1. */
    private static boolean rowBoneVisible(final ServerRackBlockEntity rack, final int row, final String unit) {
        if (row < 0 || row >= ROWS) {
            return false;
        }
        final int seated = rack.unitCodeAt(row);
        if (unit.equals("band")) {
            // A chassis band lights for both rows a seated 2U node covers.
            return seated == ServerRackBlockEntity.UNIT_NODE_2U
                    || (row > 0 && rack.unitCodeAt(row - 1) == ServerRackBlockEntity.UNIT_NODE_2U);
        }
        return seated != ServerRackBlockEntity.UNIT_NONE
                && unit.equals(ServerRackBlockEntity.UNIT_BONES[seated]);
    }

    private static void show(final BakedGeoModel model, final String bone, final boolean visible) {
        model.getBone(bone).ifPresent(b -> b.setHidden(!visible));
    }

    /** The cabinet spans twelve blocks; culling by the controller's own block would blink it out. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(final ServerRackBlockEntity rack) {
        return rack.renderBox();
    }

    /** Which bone a seated unit shows in a given row. */
    public static String boneFor(final int row, final int unitCode) {
        return "row_" + row + "_" + ServerRackBlockEntity.UNIT_BONES[unitCode];
    }
}
