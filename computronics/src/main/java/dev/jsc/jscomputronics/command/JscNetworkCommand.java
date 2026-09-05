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
import dev.jsc.jscomputronics.testkit.BenchmarkLoad;
import dev.jsc.jscomputronics.testkit.BigBaseScenario;
import dev.jsc.jscomputronics.testkit.TestWorldBuilder;
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
 * Developer commands under {@code /jsc}: <ul> <li>{@code /jsc net} — inspect (and {@code net assign}, for testing, seed) the data network at the cable the player is looking at.</li> <li>{@code /jsc op submit <count>} / {@code /jsc op status} — submit self-test Operations to, and read the dispatch counters of, the Mainframe the player is looking at, to exercise the virtual-thread runtime.</li> <li>{@code /jsc benchmark build [types|expert]} — raise the scale benchmark's base (a 48-block square starting one block east of the player and extending east and south) in the real world, at the default size, with a given catalog size, or at expert-pack scale; {@code /jsc benchmark load <opsPerTick> <ticks>} and {@code stop} put the same traffic on it, for watching a profiler while it works.</li> </ul>
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
                                        .executes(context -> opStatus(context.getSource()))))
                        .then(Commands.literal("benchmark")
                                .then(Commands.literal("build")
                                        .executes(context -> benchmarkBuild(context.getSource(),
                                                BigBaseScenario.Params.DEFAULT))
                                        .then(Commands.literal("expert")
                                                .executes(context -> benchmarkBuild(context.getSource(),
                                                        BigBaseScenario.Params.EXPERT_PACK)))
                                        .then(Commands.argument("types", IntegerArgumentType.integer(1, 100_000))
                                                .executes(context -> benchmarkBuild(context.getSource(),
                                                        BigBaseScenario.Params.DEFAULT.withTypes(
                                                                IntegerArgumentType.getInteger(context, "types"))))))
                                .then(Commands.literal("load")
                                        .then(Commands.argument("opsPerTick", IntegerArgumentType.integer(1, 1000))
                                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 72_000))
                                                        .executes(context -> benchmarkLoad(context.getSource(),
                                                                IntegerArgumentType.getInteger(context, "opsPerTick"),
                                                                IntegerArgumentType.getInteger(context, "ticks"))))))
                                .then(Commands.literal("stop")
                                        .executes(context -> benchmarkStop(context.getSource())))));
    }

    private static int benchmarkBuild(final CommandSourceStack source, final BigBaseScenario.Params params)
            throws CommandSyntaxException {
        final ServerPlayer player = source.getPlayerOrException();
        final ServerLevel level = player.serverLevel();
        // The scenario's floor row is y = 2 of its box; the box starts one block east of the player.
        final BlockPos origin = player.blockPosition().offset(1, -2, 0);
        final int types = params.types();
        final long start = System.nanoTime();
        final TestWorldBuilder world = TestWorldBuilder.at(level, origin);
        final BigBaseScenario.Built built = BigBaseScenario.build(world, params);
        // A GameTest builds into an empty arena; here the same base lands in whatever the player is standing
        // on. Everything between the machines is cleared afterwards and given a floor, so the base is a room
        // that can be walked into and read — a base buried in rock cannot be inspected or profiled.
        final int cleared = carveRoom(level, world);
        BenchmarkLoad.remember(built);
        final double ms = (System.nanoTime() - start) / 1_000_000.0;
        final NetworkUuid network = built.mainframe().networkUuid();
        final int servers = network == null ? 0
                : NetworkSystem.get(level).serversOf(network).size();
        final BlockPos mainframePos = built.mainframe().getBlockPos();
        source.sendSuccess(() -> Component.literal(String.format(
                "Built the benchmark base: %d cabinets, %d servers, %d nodes, %d PCs, %d item types in %.0f ms; "
                        + "cleared %d blocks. Mainframe at %d %d %d. "
                        + "Run '/jsc benchmark load <opsPerTick> <ticks>' to put traffic on it.",
                params.racks(), params.servers(), params.nodes(), params.personalComputers(), types, ms, cleared,
                mainframePos.getX(), mainframePos.getY(), mainframePos.getZ())), false);
        // Say so plainly when the base did not come up: a benchmark on a base with no network measures
        // nothing at all, and the numbers would look fine.
        if (network == null || servers < params.servers()) {
            source.sendFailure(Component.literal(String.format(
                    "The base did not come up whole: network %s, %d of %d servers registered. Build it somewhere"
                            + " flat and open (a superflat world or the air) and try again.",
                    network == null ? "MISSING" : "ok", servers, params.servers())));
        }
        return 1;
    }

    /**
     * Clears everything inside the built base's own bounding box that the base did not place, and lays a
     * floor one block under it. Returns how many blocks were changed.
     */
    private static int carveRoom(final ServerLevel level, final TestWorldBuilder world) {
        final net.minecraft.world.level.levelgen.structure.BoundingBox box = world.writtenBox();
        if (box == null) {
            return 0;
        }
        final net.minecraft.world.level.block.state.BlockState air =
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        final net.minecraft.world.level.block.state.BlockState floor =
                net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState();
        int changed = 0;
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = box.minX() - 1; x <= box.maxX() + 1; x++) {
            for (int z = box.minZ() - 1; z <= box.maxZ() + 1; z++) {
                for (int y = box.minY() - 1; y <= box.maxY() + 2; y++) {
                    cursor.set(x, y, z);
                    if (world.wrote(cursor)) {
                        continue;
                    }
                    final boolean isFloor = y == box.minY() - 1;
                    final net.minecraft.world.level.block.state.BlockState wanted = isFloor ? floor : air;
                    if (!level.getBlockState(cursor).equals(wanted)) {
                        level.setBlock(cursor, wanted, 2);
                        changed++;
                    }
                }
            }
        }
        return changed;
    }

    private static int benchmarkLoad(final CommandSourceStack source, final int opsPerTick, final int ticks) {
        final BigBaseScenario.Built built = BenchmarkLoad.lastBuilt();
        if (built == null || built.mainframe().isRemoved()) {
            source.sendFailure(Component.literal("Build the benchmark base first: /jsc benchmark build"));
            return 0;
        }
        BenchmarkLoad.start(new BenchmarkLoad.Session(built.mainframe(), built.catalog(), opsPerTick, ticks,
                built.workload(20, 256, 64), System.nanoTime()));
        source.sendSuccess(() -> Component.literal(
                "Loading the base with " + opsPerTick + " operations a tick for " + ticks + " ticks."), false);
        return 1;
    }

    private static int benchmarkStop(final CommandSourceStack source) {
        final BenchmarkLoad.Session session = BenchmarkLoad.active();
        final boolean stopped = BenchmarkLoad.stop();
        source.sendSuccess(() -> Component.literal(stopped && session != null
                ? "Stopped after " + session.ticks() + " ticks: " + session.submitted() + " operations submitted, "
                        + session.accepted() + " accepted."
                : "No benchmark load is running."), false);
        return stopped ? 1 : 0;
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
