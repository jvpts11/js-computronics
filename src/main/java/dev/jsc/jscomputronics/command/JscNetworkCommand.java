/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.common.network.ConnectivityIndex;
import dev.jsc.jscomputronics.common.network.DataTier;
import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.module.computing.block.DataCableBlock;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
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
 * Developer commands under {@code /jsc}: <ul> <li>{@code /jsc net} — inspect (and {@code net assign}, for testing, seed) the data network at the cable the player is looking at.</li> <li>{@code /jsc op submit <count>} / {@code /jsc op status} — submit self-test Operations to, and read the dispatch counters of, the Mainframe the player is looking at, to exercise the virtual-thread runtime.</li> </ul>
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
                                        .executes(context -> assign(context.getSource()))))
                        .then(Commands.literal("op")
                                .then(Commands.literal("submit")
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100_000))
                                                .executes(context -> opSubmit(context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "count")))))
                                .then(Commands.literal("status")
                                        .executes(context -> opStatus(context.getSource())))));
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
                "%s @ %s | network: %s | cables in this network: %d | networks total: %d",
                tier.name(), pos.toShortString(),
                uuid.map(value -> value.value().toString()).orElse("unassigned"),
                index.componentSize(pos.asLong()), index.componentCount())), false);
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

    private static final int SELF_TEST_WORK_UNITS = 2_000_000;

    private static int opSubmit(final CommandSourceStack source, final int count) throws CommandSyntaxException {
        final ServerPlayer player = source.getPlayerOrException();
        final MainframeBlockEntity mainframe = targetMainframe(player, player.serverLevel());
        if (mainframe == null) {
            source.sendFailure(Component.literal("Look at a Mainframe."));
            return 0;
        }
        final int submitted = mainframe.submitSelfTest(count, SELF_TEST_WORK_UNITS);
        if (submitted == 0) {
            source.sendFailure(Component.literal(
                    "Mainframe is not running — power it on with a valid build first."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Submitted " + submitted + " self-test operation(s) to the dispatcher."), false);
        return submitted;
    }

    private static int opStatus(final CommandSourceStack source) throws CommandSyntaxException {
        final ServerPlayer player = source.getPlayerOrException();
        final MainframeBlockEntity mainframe = targetMainframe(player, player.serverLevel());
        if (mainframe == null) {
            source.sendFailure(Component.literal("Look at a Mainframe."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format(
                "Mainframe: %s | queues: %d | pending: %d | running: %d | completed: %d",
                mainframe.isRunning() ? "RUNNING" : "stopped",
                mainframe.parallelQueues(), mainframe.pendingOps(),
                mainframe.runningOps(), mainframe.completedOps())), false);
        return 1;
    }

    private static MainframeBlockEntity targetMainframe(final ServerPlayer player, final ServerLevel level) {
        final HitResult hit = player.pick(REACH, 1.0F, false);
        if (hit instanceof BlockHitResult blockHit
                && level.getBlockEntity(blockHit.getBlockPos()) instanceof MainframeBlockEntity mainframe) {
            return mainframe;
        }
        return null;
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
