/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Tech Series.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computers.ComputingModule;
import dev.jstech.computers.block.part.InputBusPart;
import dev.jstech.computers.block.part.ReceivingBusPart;
import dev.jstech.computers.blockentity.DataCableBlockEntity;
import dev.jstech.computers.blockentity.MainframeBlockEntity;
import dev.jstech.computers.blockentity.PersonalComputerBlockEntity;
import dev.jstech.computers.blockentity.ServerRackBlockEntity;
import dev.jstech.computers.crafting.NetworkProcessingOperation;
import dev.jstech.computers.crafting.ProcessingPattern;
import dev.jstech.computers.hardware.DiskSize;
import dev.jstech.computers.hardware.StorageTier;
import dev.jstech.computers.operation.NetworkSelectOperation;
import dev.jstech.computers.operation.NetworkStorage;
import dev.jstech.computers.operation.payload.OperationRecord;
import dev.jstech.computers.program.ServerCliComputer;
import dev.jstech.computers.program.cli.CliCommands;
import dev.jstech.computers.program.cli.CliShell;
import dev.jstech.computers.storage.StorageKey;
import dev.jstech.computers.terminal.IComputerTerminalHost;
import dev.jstech.core.operation.OperationBalance;
import dev.jstech.core.util.ShortId;
import dev.jstech.tests.JsTests;
import dev.jstech.tests.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * An Operation in flight can be stopped: from a task manager or the command prompt it settles as DISCARDED,
 * hands back what it held, and frees what it reserved. Operations saved with the Mainframe and left unresumed
 * past the orphan expiry are discarded on the reload instead of resumed.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class OperationCancelGameTests {

    private OperationCancelGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;
    private static final BlockPos MAINFRAME = new BlockPos(1, 2, 2);
    private static final BlockPos FURNACE = new BlockPos(5, 2, 5);

    private static NetworkSelectOperation pull(final GameTestHelper helper, final MainframeBlockEntity mainframe,
                                               final ItemStackHandler dest, final long amount, final String label) {
        final NetworkSelectOperation op = mainframe.submitNetworkSelect(Items.COBBLESTONE, amount,
                OperationSchedulingGameTests.port(dest), label);
        helper.assertTrue(op != null, label + " dispatched");
        return op;
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void cancel_queuedSelectIsDiscardedAndFreesItsReservation(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = OperationSchedulingGameTests.storageNetwork(helper);
        final ItemStackHandler fullDest = OperationSchedulingGameTests.fullHandler();
        final ItemStackHandler goodDest = new ItemStackHandler(9);
        final ItemStackHandler lateDest = new ItemStackHandler(9);
        final NetworkSelectOperation[] queued = new NetworkSelectOperation[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> OperationSchedulingGameTests.rack(helper)
                        .getServerStorage(0).insert(Items.COBBLESTONE, 100))
                .thenExecuteAfter(2, () -> {
                    pull(helper, mainframe, fullDest, 30, "full"); // holds the queue, stalls on a full destination
                    queued[0] = pull(helper, mainframe, goodDest, 30, "good"); // waits behind it, holding 30 reserved
                })
                .thenExecuteAfter(6, () -> {
                    helper.assertTrue(mainframe.cancelOperation(queued[0].operationId()), "a queued op can be cancelled");
                    helper.assertTrue(!mainframe.cancelOperation(queued[0].operationId()),
                            "cancelling it again is refused: it has settled");
                })
                .thenExecuteAfter(2, () -> {
                    final List<OperationRecord> live = mainframe.activeOperationRecords();
                    helper.assertTrue(live.size() == 1, "only the stalled op is left in flight; got " + live);
                    final List<OperationRecord> log = mainframe.recentOperations();
                    helper.assertTrue(!log.isEmpty() && log.get(0).status() == OperationRecord.STATUS_DISCARDED
                                    && log.get(0).moved() == 0L && log.get(0).id().equals(queued[0].operationId()),
                            "the cancelled op is logged DISCARDED having moved nothing; log=" + log);
                    helper.assertTrue(NetworkStorage.of(helper.getLevel(), mainframe.networkUuid())
                            .count(Items.COBBLESTONE) == 100, "the storage is untouched");
                    // Its reservation is gone: a pull that needs the freed items can be granted them now.
                    pull(helper, mainframe, lateDest, 60, "late");
                })
                .thenExecuteAfter(60, () -> helper.assertTrue(OperationSchedulingGameTests.count(lateDest) == 60,
                        "the freed items served the later pull; got " + OperationSchedulingGameTests.count(lateDest)))
                .thenSucceed();
    }

    /** The furnace rig: the crafting network plus a switch, a furnace and its input/receiving buses. */
    private static TestWorldBuilder.CraftingNetwork furnaceRig(final GameTestHelper helper,
                                                               final TestWorldBuilder world) {
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        world.setBlock(new BlockPos(5, 2, 3), ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(new BlockPos(5, 2, 4), ComputingModule.CRAFTING_SWITCH.get());
        world.setBlock(FURNACE, Blocks.FURNACE);
        world.setBlock(new BlockPos(5, 3, 5), ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(new BlockPos(5, 1, 5), ComputingModule.CRAFTING_CABLE.get());
        return net;
    }

    private static void wireFurnaceBuses(final TestWorldBuilder world) {
        if (world.getBlockEntity(new BlockPos(5, 3, 5)) instanceof DataCableBlockEntity c) {
            c.addPart(Direction.DOWN, new InputBusPart());
        }
        if (world.getBlockEntity(new BlockPos(5, 1, 5)) instanceof DataCableBlockEntity c) {
            c.addPart(Direction.UP, new ReceivingBusPart());
        }
    }

    private static ProcessingPattern smeltIron() {
        return new ProcessingPattern(
                List.of(new ProcessingPattern.ProcessingInput(StorageKey.of(Items.RAW_IRON), 1L)),
                List.of(new ProcessingPattern.ProcessingOutput(StorageKey.of(Items.IRON_INGOT), 1L, 100)),
                "minecraft:furnace", 600);
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void cancel_processingRunIsDiscarded(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = furnaceRig(helper, world);
        final NetworkProcessingOperation[] run = new NetworkProcessingOperation[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    wireFurnaceBuses(world);
                    net.seed(Items.RAW_IRON, 32);
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    run[0] = net.mainframe().submitNetworkProcessing(smeltIron(), 16, "cancel");
                    helper.assertTrue(run[0] != null, "the processing operation is accepted");
                })
                .thenExecuteAfter(40, () -> {
                    helper.assertTrue(!net.mainframe().activeOperationRecords().isEmpty(),
                            "the run is still going before the cancel");
                    helper.assertTrue(net.mainframe().cancelOperation(run[0].operationId()), "a running step can be cancelled");
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(net.mainframe().activeOperationRecords().isEmpty(),
                            "nothing is left in flight; got " + net.mainframe().activeOperationRecords());
                    final boolean discarded = net.mainframe().recentOperations().stream()
                            .anyMatch(r -> r.status() == OperationRecord.STATUS_DISCARDED
                                    && r.id().equals(run[0].operationId()));
                    helper.assertTrue(discarded, "the run is logged DISCARDED; recent=" + net.mainframe().recentOperations());
                })
                .thenSucceed();
    }

    private static boolean cliContains(final CliShell.Response response, final String needle) {
        final String lower = needle.toLowerCase(Locale.ROOT);
        return response.lines().stream()
                .anyMatch(line -> line.text().toLowerCase(Locale.ROOT).contains(lower));
    }

    @GameTest(template = ARENA)
    public static void cli_opsListsTheIdAndCancelStopsTheOperation(final GameTestHelper helper) {
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos router = new BlockPos(3, 2, 2);
        final BlockPos eth = new BlockPos(4, 2, 2);
        final BlockPos pc = new BlockPos(5, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3); // behind the cable (rear-only connection)
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final MainframeBlockEntity mainframe = world.placeRunningMainframe(MAINFRAME);
        world.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        world.setBlock(router, ComputingModule.PERSONAL_ROUTER.get());
        world.setBlock(eth, ComputingModule.ETHERNET_CABLE.get());
        final PersonalComputerBlockEntity computer = world.placeRunningPersonalComputer(pc);
        world.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        final ServerRackBlockEntity rackBe = world.blockEntity(rack, ServerRackBlockEntity.class);
        // HDD drives: the ten-tick seek keeps the pull in flight long enough to list and cancel it.
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        rackBe.insertDrive(0, new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)));
        rackBe.insertDrive(0, new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)));
        final String[] shortId = new String[1];
        final ServerCliComputer cli = new ServerCliComputer((IComputerTerminalHost) computer, helper.getLevel());
        final CliShell shell = CliCommands.newShell(50);
        helper.startSequence()
                /*
                 * Seed a tick ahead of the prompt: the index catalogues the stock on the next tick, and a pull
                 * planned against an index that does not know the items yet settles FAILED on the spot.
                 */
                .thenExecuteAfter(SETTLE + 6, () -> rackBe.getServerStorage(0).insert(Items.COBBLESTONE, 200))
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(cliContains(shell.run("operation select 30 cobblestone", cli), "SELECT queued"),
                            "the pull queues");
                    final List<OperationRecord> live = mainframe.activeOperationRecords();
                    helper.assertTrue(live.size() == 1, "one op in flight; got " + live.size());
                    helper.assertTrue(live.get(0).status() != OperationRecord.STATUS_FAILED,
                            "the pull is live, not failed; got " + live.get(0).status());
                    shortId[0] = ShortId.of(live.get(0).id().toString());
                    helper.assertTrue(cliContains(shell.run("ops", cli), shortId[0]),
                            "ops lists the operation by its short id " + shortId[0]);
                    helper.assertTrue(cliContains(shell.run("cancel nope", cli), "no operation"),
                            "an unknown id is reported");
                    helper.assertTrue(cliContains(shell.run("cancel " + shortId[0], cli), "cancelled SELECT"),
                            "cancel by short id stops the pull");
                    // Settled but not yet logged: it leaves the in-flight list on the Mainframe's next tick.
                    helper.assertTrue(cliContains(shell.run("cancel " + shortId[0], cli), "already settled"),
                            "a second cancel in the same tick finds it settled");
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(cliContains(shell.run("cancel " + shortId[0], cli), "no operation"),
                            "the settled operation is no longer in flight");
                    final List<OperationRecord> log = mainframe.recentOperations();
                    helper.assertTrue(!log.isEmpty() && log.get(0).status() == OperationRecord.STATUS_DISCARDED,
                            "the cancelled pull is logged DISCARDED; log=" + log);
                    helper.assertTrue(NetworkStorage.of(helper.getLevel(), mainframe.networkUuid())
                            .count(Items.COBBLESTONE) == 200, "nothing left the network");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void orphanExpiry_savedOperationsPastTheTtlAreDiscardedOnReload(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = furnaceRig(helper, world);
        final CompoundTag[] snapshot = new CompoundTag[1];
        final UUID[] runId = new UUID[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    wireFurnaceBuses(world);
                    net.seed(Items.RAW_IRON, 32);
                    if (world.getBlockEntity(FURNACE) instanceof FurnaceBlockEntity furnace) {
                        furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 8));
                    }
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final NetworkProcessingOperation run = net.mainframe().submitNetworkProcessing(smeltIron(), 16, "orphan");
                    helper.assertTrue(run != null, "the processing operation is accepted");
                    runId[0] = run.operationId();
                })
                .thenExecuteAfter(40, () -> {
                    helper.assertTrue(!net.mainframe().activeOperationRecords().isEmpty(),
                            "the operation must still be running before the reload");
                    snapshot[0] = net.mainframe().saveWithoutMetadata(helper.getLevel().registryAccess());
                    helper.assertTrue(snapshot[0].contains("ActiveOperations"),
                            "the Mainframe's NBT must carry the in-flight operation");
                    helper.assertTrue(snapshot[0].getLong("ActiveOperationsSavedAt") > 0L,
                            "the save is stamped with the game time");
                    world.setBlock(MAINFRAME, Blocks.AIR);
                })
                .thenExecuteAfter(SETTLE, () -> {
                    // A one-tick expiry: by the time the fresh Mainframe boots and resumes, the list is stale.
                    OperationBalance.setOrphanedOperationsExpiryTicks(1L);
                    world.setBlock(MAINFRAME, ComputingModule.MAINFRAME.get());
                    final MainframeBlockEntity fresh = world.blockEntity(MAINFRAME, MainframeBlockEntity.class);
                    fresh.loadWithComponents(snapshot[0], helper.getLevel().registryAccess());
                })
                .thenExecuteAfter(80, () -> {
                    OperationBalance.reset();
                    final MainframeBlockEntity fresh = world.blockEntity(MAINFRAME, MainframeBlockEntity.class);
                    helper.assertTrue(fresh.activeOperationRecords().isEmpty(),
                            "an expired operation is not resumed; active=" + fresh.activeOperationRecords());
                    final boolean discarded = fresh.recentOperations().stream()
                            .anyMatch(r -> r.status() == OperationRecord.STATUS_DISCARDED && r.id().equals(runId[0]));
                    helper.assertTrue(discarded, "the expired operation is logged DISCARDED; recent="
                            + fresh.recentOperations());
                })
                .thenSucceed();
    }
}
