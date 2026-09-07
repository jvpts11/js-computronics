/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Tech Series.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.blockentity.PersonalComputerBlockEntity;
import dev.jstech.computronics.blockentity.ServerRackBlockEntity;
import dev.jstech.computronics.hardware.DiskSize;
import dev.jstech.computronics.hardware.StorageTier;
import dev.jstech.computronics.operation.NetworkSelectOperation;
import dev.jstech.computronics.operation.NetworkStorage;
import dev.jstech.computronics.operation.payload.OperationRecord;
import dev.jstech.computronics.program.ServerCliComputer;
import dev.jstech.computronics.program.cli.CliCommands;
import dev.jstech.computronics.program.cli.CliShell;
import dev.jstech.computronics.storage.ExternalDataPort;
import dev.jstech.computronics.terminal.IComputerTerminalHost;
import dev.jstech.core.operation.OperationPriority;
import dev.jstech.tests.JsTests;
import dev.jstech.tests.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Locale;

/**
 * The Mainframe hands its queue slots out by priority: a HIGH request runs before MEDIUM ones submitted
 * earlier, a queued Operation can be promoted while it waits, and the level travels with the request from
 * every surface (here the command prompt's {@code PRIORITY} clause). Each test runs on a Mainframe with a
 * single queue, so the order the slot is granted in is the whole story.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class OperationSchedulingGameTests {

    private OperationSchedulingGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;
    private static final BlockPos MAINFRAME = new BlockPos(1, 2, 2);
    private static final BlockPos RACK = new BlockPos(3, 2, 2);

    /**
     * A running single-queue Mainframe cabled to one Server Rack whose server sits on HDD drives. The HDD
     * seek latency (ten ticks) keeps an Operation visibly in flight for a while, so a test can look at who
     * holds the queue mid-way; on NVMe a thirty-item pull is over the tick after it is granted.
     * Package-private so the cancellation tests share the fixture.
     */
    static MainframeBlockEntity storageNetwork(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final MainframeBlockEntity mainframe = world.placeRunningMainframe(MAINFRAME);
        world.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        world.setBlock(RACK, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST)); // cables attach through the rear
        final ServerRackBlockEntity rack = world.blockEntity(RACK, ServerRackBlockEntity.class);
        rack.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        rack.insertDrive(0, new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)));
        rack.insertDrive(0, new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)));
        return mainframe;
    }

    static ServerRackBlockEntity rack(final GameTestHelper helper) {
        if (helper.getBlockEntity(RACK) instanceof ServerRackBlockEntity rack) {
            return rack;
        }
        throw new IllegalStateException("no rack at " + RACK);
    }

    static ExternalDataPort port(final ItemStackHandler handler) {
        return new ExternalDataPort(handler, null);
    }

    static ItemStackHandler fullHandler() {
        final ItemStackHandler handler = new ItemStackHandler(1);
        handler.setStackInSlot(0, new ItemStack(Items.STICK, 64));
        return handler;
    }

    static int count(final ItemStackHandler handler) {
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            total += handler.getStackInSlot(i).getCount();
        }
        return total;
    }

    private static NetworkSelectOperation pull(final GameTestHelper helper, final MainframeBlockEntity mainframe,
                                               final ItemStackHandler dest, final String label,
                                               final OperationPriority priority) {
        final NetworkSelectOperation op = mainframe.submitNetworkSelect(Items.COBBLESTONE, 30, port(dest), label);
        helper.assertTrue(op != null, label + " dispatched");
        op.setPriority(priority);
        return op;
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void priority_highRequestRunsBeforeEarlierMediumOnes(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = storageNetwork(helper);
        final ItemStackHandler first = new ItemStackHandler(9);
        final ItemStackHandler second = new ItemStackHandler(9);
        final ItemStackHandler urgent = new ItemStackHandler(9);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> rack(helper).getServerStorage(0).insert(Items.COBBLESTONE, 100))
                .thenExecuteAfter(2, () -> {
                    // Two ordinary requests first, then an urgent one: the urgent one must take the only queue.
                    pull(helper, mainframe, first, "first", OperationPriority.MEDIUM);
                    pull(helper, mainframe, second, "second", OperationPriority.MEDIUM);
                    pull(helper, mainframe, urgent, "urgent", OperationPriority.HIGH);
                })
                .thenExecuteAfter(6, () -> {
                    final List<OperationRecord> records = mainframe.activeOperationRecords();
                    helper.assertTrue(records.size() == 3, "three ops in flight; got " + records.size());
                    helper.assertTrue(records.get(2).status() == OperationRecord.STATUS_PROCESSING,
                            "the HIGH op holds the queue; got " + records.get(2).status());
                    helper.assertTrue(records.get(2).priority() == OperationPriority.HIGH,
                            "the live record carries the level; got " + records.get(2).priority());
                    helper.assertTrue(records.get(0).status() == OperationRecord.STATUS_PENDING
                                    && records.get(1).status() == OperationRecord.STATUS_PENDING,
                            "both MEDIUM ops wait behind it; got " + records.get(0).status() + "/"
                                    + records.get(1).status());
                    helper.assertTrue(count(first) == 0 && count(second) == 0,
                            "nothing has moved for the MEDIUM ops yet");
                })
                .thenExecuteAfter(120, () -> {
                    helper.assertTrue(count(urgent) == 30 && count(first) == 30 && count(second) == 30,
                            "every op delivered its 30; got " + count(urgent) + "/" + count(first) + "/" + count(second));
                    // The log is newest-first: the HIGH op settled first, then the MEDIUM ones in submission order.
                    final List<OperationRecord> log = mainframe.recentOperations();
                    helper.assertTrue(log.size() >= 3, "all three ops logged; got " + log.size());
                    helper.assertTrue(log.get(2).priority() == OperationPriority.HIGH
                                    && log.get(2).status() == OperationRecord.STATUS_COMPLETED,
                            "the HIGH op completed before the others; log=" + log);
                    helper.assertTrue(log.get(1).priority() == OperationPriority.MEDIUM
                                    && log.get(0).priority() == OperationPriority.MEDIUM,
                            "the MEDIUM ops followed; log=" + log);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 160)
    public static void priority_promotingAQueuedOpTakesTheSlotNextTick(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = storageNetwork(helper);
        final ItemStackHandler fullDest = fullHandler();
        final ItemStackHandler goodDest = new ItemStackHandler(9);
        final NetworkSelectOperation[] queued = new NetworkSelectOperation[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> rack(helper).getServerStorage(0).insert(Items.COBBLESTONE, 100))
                .thenExecuteAfter(2, () -> {
                    // op1 stalls against a full destination while holding the single queue; op2 waits behind it.
                    pull(helper, mainframe, fullDest, "full", OperationPriority.MEDIUM);
                    queued[0] = pull(helper, mainframe, goodDest, "good", OperationPriority.MEDIUM);
                })
                .thenExecuteAfter(6, () -> {
                    final List<OperationRecord> records = mainframe.activeOperationRecords();
                    helper.assertTrue(records.get(0).status() == OperationRecord.STATUS_PROCESSING
                                    && records.get(1).status() == OperationRecord.STATUS_PENDING,
                            "op1 runs and op2 queues before the change; got " + records.get(0).status() + "/"
                                    + records.get(1).status());
                    helper.assertTrue(mainframe.setOperationPriority(queued[0].operationId(), OperationPriority.HIGH),
                            "a queued op can be promoted by id");
                    helper.assertTrue(!mainframe.setOperationPriority(java.util.UUID.randomUUID(), OperationPriority.HIGH),
                            "an unknown id is refused");
                })
                .thenExecuteAfter(3, () -> {
                    final List<OperationRecord> records = mainframe.activeOperationRecords();
                    helper.assertTrue(records.size() == 2, "both ops still in flight; got " + records);
                    helper.assertTrue(records.get(1).status() == OperationRecord.STATUS_PROCESSING
                                    && records.get(1).priority() == OperationPriority.HIGH,
                            "the promoted op took the queue; got " + records.get(1).status() + " at "
                                    + records.get(1).priority());
                    helper.assertTrue(records.get(0).status() == OperationRecord.STATUS_PENDING,
                            "the stalled MEDIUM op yielded the queue; got " + records.get(0).status());
                })
                .thenExecuteAfter(40, () -> {
                    helper.assertTrue(count(goodDest) == 30, "the promoted op delivered its 30; got " + count(goodDest));
                    final long left = NetworkStorage.of(helper.getLevel(), mainframe.networkUuid()).count(Items.COBBLESTONE);
                    helper.assertTrue(left == 70, "only the promoted op moved anything; left " + left);
                    final List<OperationRecord> log = mainframe.recentOperations();
                    helper.assertTrue(!log.isEmpty() && log.get(0).priority() == OperationPriority.HIGH
                                    && log.get(0).status() == OperationRecord.STATUS_COMPLETED,
                            "the promoted op settled first, at the level it was given; log=" + log);
                })
                .thenSucceed();
    }

    private static boolean cliContains(final CliShell.Response response, final String needle) {
        final String lower = needle.toLowerCase(Locale.ROOT);
        return response.lines().stream()
                .anyMatch(line -> line.text().toLowerCase(Locale.ROOT).contains(lower));
    }

    @GameTest(template = ARENA)
    public static void priority_iqlClauseSchedulesTheStatementAtThatLevel(final GameTestHelper helper) {
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
        TestWorldBuilder.mountDefaultServer(rackBe, 0);
        helper.startSequence()
                // Seed a tick ahead of the prompt: the index catalogues the stock on the next tick, and a pull
                // planned against an index that does not know the items yet settles FAILED on the spot.
                .thenExecuteAfter(SETTLE + 6, () -> rackBe.getServerStorage(0).insert(Items.COBBLESTONE, 200))
                .thenExecuteAfter(2, () -> {
                    final ServerCliComputer cli = new ServerCliComputer((IComputerTerminalHost) computer, helper.getLevel());
                    final CliShell shell = CliCommands.newShell(50);

                    helper.assertTrue(cliContains(shell.run("operation select 30 cobblestone priority high", cli),
                            "SELECT queued"), "a SELECT with a PRIORITY clause queues");
                    final List<OperationRecord> records = mainframe.activeOperationRecords();
                    helper.assertTrue(records.size() == 1, "one op in flight; got " + records.size());
                    helper.assertTrue(records.get(0).status() != OperationRecord.STATUS_FAILED,
                            "the pull is live, not failed; got " + records.get(0).status());
                    helper.assertTrue(records.get(0).priority() == OperationPriority.HIGH,
                            "the statement's level reached the operation; got " + records.get(0).priority());

                    helper.assertTrue(cliContains(shell.run("operation select 30 cobblestone", cli), "SELECT queued"),
                            "a SELECT without the clause queues too");
                    final List<OperationRecord> both = mainframe.activeOperationRecords();
                    helper.assertTrue(both.size() == 2 && both.get(1).priority() == OperationPriority.MEDIUM,
                            "the default level is MEDIUM; got " + both);

                    helper.assertTrue(cliContains(shell.run("operation select 30 cobblestone priority urgent", cli),
                            "unknown priority level"), "an unknown level is a syntax error, not a queued op");
                    helper.assertTrue(mainframe.activeOperationRecords().size() == 2,
                            "the rejected statement queued nothing");
                })
                .thenSucceed();
    }
}
