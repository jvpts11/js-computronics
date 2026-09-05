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
import com.mojang.math.Axis;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * Draws a Mainframe as one cabinet from its controller block. Every processor, memory stick, graphics
 * card, disk and the power supply is its own bone, shown only when that slot actually holds the part,
 * so what a player installs is what a player sees through the open service bay.
 *
 * <p>The machine states itself in hardware, never on a screen: three annunciator lamps for running,
 * network and fault. A Mainframe is read on a linked monitor like every other computer in the mod.
 */
public final class MainframeRenderer extends GeoBlockRenderer<MainframeBlockEntity> {

    public MainframeRenderer(final BlockEntityRendererProvider.Context context) {
        super(new MainframeGeoModel());
    }

    @Override
    protected Direction getFacing(final MainframeBlockEntity mainframe) {
        final BlockState state = mainframe.getBlockState();
        return state.hasProperty(HorizontalDirectionalBlock.FACING)
                ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
    }

    /**
     * The model faces north with the controller in the middle of its bottom front row; each facing
     * turns it about that block's centre so the cabinet lands on the multiblock's own footprint.
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
    public void preRender(final PoseStack poseStack, final MainframeBlockEntity mainframe,
                          final BakedGeoModel model, final MultiBufferSource bufferSource,
                          final VertexConsumer buffer, final boolean isReRender, final float partialTick,
                          final int packedLight, final int packedOverlay, final int colour) {
        super.preRender(poseStack, mainframe, model, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
        // The baked model is shared by every Mainframe of this era, so every flag is set on every render.
        for (int i = 0; i < MainframeBlockEntity.CPU_SLOTS; i++) {
            show(model, "cpu_" + i, mainframe.hardwareInstalled(MainframeBlockEntity.CPU_SLOTS_START + i));
        }
        for (int i = 0; i < MainframeBlockEntity.RAM_SLOTS; i++) {
            show(model, "ram_" + i, mainframe.hardwareInstalled(MainframeBlockEntity.RAM_SLOTS_START + i));
        }
        for (int i = 0; i < MainframeBlockEntity.GPU_SLOTS; i++) {
            show(model, "gpu_" + i, mainframe.hardwareInstalled(MainframeBlockEntity.GPU_SLOTS_START + i));
        }
        for (int i = 0; i < MainframeBlockEntity.DISK_SLOTS; i++) {
            final boolean seated = mainframe.hardwareInstalled(MainframeBlockEntity.DISK_SLOTS_START + i);
            show(model, "disk_" + i, seated);
            show(model, "disk_lamp_" + i, seated && mainframe.diskCarriesSystem(i));
        }
        show(model, "psu", mainframe.hardwareInstalled(MainframeBlockEntity.PSU_SLOT));

        final boolean running = mainframe.visualRunning();
        final boolean valid = mainframe.visualBuildValid();
        show(model, "lamp_run", running && valid);
        show(model, "lamp_net", running && mainframe.visualNetworked());
        // Amber-and-red for "powered but the build will not come up": parts are in and it still fails.
        show(model, "lamp_fault", running && !valid);
        show(model, "lightbar_plinth", running && valid);
        show(model, "lightbar_cornice", running && valid);
        show(model, "service_panel", !mainframe.servicePanelOff());
    }

    private static void show(final BakedGeoModel model, final String bone, final boolean visible) {
        model.getBone(bone).ifPresent(b -> b.setHidden(!visible));
    }

    /** The cabinet spans twelve blocks; culling by the controller's own block would blink it out. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(final MainframeBlockEntity mainframe) {
        return mainframe.renderBox();
    }
}
