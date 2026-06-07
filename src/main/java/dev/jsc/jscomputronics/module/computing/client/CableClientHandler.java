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
import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.computing.block.DataCableBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Client-side interaction for data cables that host bus parts.
 */
@EventBusSubscriber(modid = JsComputronics.MODID, value = Dist.CLIENT)
public final class CableClientHandler {

    private CableClientHandler() {
    }

    @SubscribeEvent
    public static void onLeftClick(final PlayerInteractEvent.LeftClickBlock event) {
        final Level level = event.getLevel();
        // Only suppress the CLIENT's predicted break. In single-player the integrated server shares
        if (!level.isClientSide()) {
            return;
        }
        if (!(level.getBlockState(event.getPos()).getBlock() instanceof DataCableBlock)) {
            return;
        }
        final BlockPos pos = event.getPos();
        final Player player = event.getEntity();
        final Vec3 start = player.getEyePosition();
        final Vec3 end = start.add(player.getViewVector(1.0F).scale(player.blockInteractionRange() + 1.0));
        final Direction face = DataCableBlock.aimedPart(level, pos, start, end);
        if (face != null) {
            // Suppress the client's predicted break so the cable does not flicker. Creative checks
            event.setCanceled(true);
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void onBlockHighlight(final RenderHighlightEvent.Block event) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        final BlockPos pos = event.getTarget().getBlockPos();
        if (!(mc.level.getBlockState(pos).getBlock() instanceof DataCableBlock)) {
            return;
        }
        final Vec3 start = mc.player.getEyePosition();
        final Vec3 end = start.add(mc.player.getViewVector(1.0F).scale(mc.player.blockInteractionRange() + 1.0));
        final VoxelShape shape = DataCableBlock.aimedOutlineShape(mc.level, pos, start, end);
        if (shape == null) {
            return; // no parts: let vanilla outline the cable
        }
        final Vec3 cam = event.getCamera().getPosition();
        drawOutline(event.getPoseStack(),
                event.getMultiBufferSource().getBuffer(RenderType.lines()), shape,
                pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
        event.setCanceled(true);
    }

    private static void drawOutline(final PoseStack poseStack, final VertexConsumer consumer,
                                    final VoxelShape shape, final double ox, final double oy, final double oz) {
        final PoseStack.Pose pose = poseStack.last();
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float nx = (float) (x2 - x1);
            float ny = (float) (y2 - y1);
            float nz = (float) (z2 - z1);
            final float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1.0e-5F) {
                return;
            }
            nx /= len;
            ny /= len;
            nz /= len;
            consumer.addVertex(pose, (float) (x1 + ox), (float) (y1 + oy), (float) (z1 + oz))
                    .setColor(0.0F, 0.0F, 0.0F, 0.4F).setNormal(pose, nx, ny, nz);
            consumer.addVertex(pose, (float) (x2 + ox), (float) (y2 + oy), (float) (z2 + oz))
                    .setColor(0.0F, 0.0F, 0.0F, 0.4F).setNormal(pose, nx, ny, nz);
        });
    }
}
