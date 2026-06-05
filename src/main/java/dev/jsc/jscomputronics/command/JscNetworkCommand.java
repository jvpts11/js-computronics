/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.common.network.ConnectivityIndex;
import dev.jsc.jscomputronics.common.network.DataTier;
import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.module.computing.block.DataCableBlock;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Optional;

/**
 * Developer command {@code /jsc net} to inspect (and, for testing, seed) the data network at the cable the player is looking at.
 */
@EventBusSubscriber(modid = JsComputronics.MODID)
public final class JscNetworkCommand {

    private static final double REACH = 20.0;

    private JscNetworkCommand() {
    }

    @SubscribeEvent
    public static void register(final RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("jsc")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("net")
                                .executes(context -> info(context.getSource()))
                                .then(Commands.literal("assign")
                                        .executes(context -> assign(context.getSource())))));
    }

    private static int info(final CommandSourceStack source) throws CommandSyntaxException {
        final ServerPlayer player = source.getPlayerOrException();
        final ServerLevel level = player.serverLevel();
        final BlockPos pos = targetCable(player, level);
        if (pos == null) {
            source.sendFailure(Component.literal("Look at a data cable."));
            return 0;
        }
        final ConnectivityIndex index = NetworkSystem.get(level).connectivity();
        final DataTier tier = ((DataCableBlock) level.getBlockState(pos).getBlock()).tier();
        final Optional<NetworkUuid> uuid = index.networkOf(pos.asLong());
        source.sendSuccess(() -> Component.literal(String.format(
                "%s @ %s | network: %s | total cables: %d | components: %d",
                tier.name(), pos.toShortString(),
                uuid.map(value -> value.value().toString()).orElse("unassigned"),
                index.size(), index.componentCount())), false);
        return 1;
    }

    private static int assign(final CommandSourceStack source) throws CommandSyntaxException {
        final ServerPlayer player = source.getPlayerOrException();
        final ServerLevel level = player.serverLevel();
        final BlockPos pos = targetCable(player, level);
        if (pos == null) {
            source.sendFailure(Component.literal("Look at a data cable."));
            return 0;
        }
        final NetworkUuid uuid = NetworkUuid.random();
        NetworkSystem.get(level).connectivity().assignUuid(pos.asLong(), uuid);
        source.sendSuccess(() -> Component.literal(
                "Assigned " + uuid.value() + " to this segment."), false);
        return 1;
    }

    private static BlockPos targetCable(final ServerPlayer player, final ServerLevel level) {
        final HitResult hit = player.pick(REACH, 1.0F, false);
        if (hit instanceof BlockHitResult blockHit) {
            final BlockPos pos = blockHit.getBlockPos();
            if (level.getBlockState(pos).getBlock() instanceof DataCableBlock) {
                return pos;
            }
        }
        return null;
    }
}
