/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics;

import dev.jstech.computronics.JsComputronics;
import dev.jstech.computronics.block.DataCableBlock;
import dev.jstech.computronics.block.part.CablePart;
import dev.jstech.computronics.blockentity.DataCableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Makes a left-click pick a single bus part off a data cable instead of breaking the whole block.
 */
@EventBusSubscriber(modid = JsComputronics.MODID)
public final class CablePartBreakHandler {

    private CablePartBreakHandler() {
    }

    @SubscribeEvent
    public static void onBlockBreak(final BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getState().getBlock() instanceof DataCableBlock)) {
            return;
        }
        final BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof DataCableBlockEntity cable) || !cable.hasAnyPart()) {
            return;
        }
        final Player player = event.getPlayer();
        final Vec3 start = player.getEyePosition();
        final Vec3 end = start.add(player.getViewVector(1.0F).scale(player.blockInteractionRange() + 1.0));
        final Direction face = DataCableBlock.aimedPart(level, pos, start, end);
        if (face == null) {
            return; // aiming at the cable itself: let it break (its parts drop with it)
        }
        event.setCanceled(true);
        final CablePart removed = cable.removePart(face);
        if (removed == null) {
            return;
        }
        removed.dropContents(level);
        level.playSound(null, pos, SoundType.METAL.getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
        if (!player.getAbilities().instabuild && !player.getInventory().add(removed.partItem())) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), removed.partItem());
        }
    }
}
