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
import dev.jstech.computronics.operation.NetworkSelectOperation;
import dev.jstech.computronics.operation.payload.OperationRecord;
import dev.jstech.computronics.program.ServerCliComputer;
import dev.jstech.computronics.program.cli.CliCommands;
import dev.jstech.computronics.program.cli.CliShell;
import dev.jstech.computronics.terminal.ComputerTerminalHost;
import dev.jstech.core.operation.OperationStatistics;
import dev.jstech.tests.JsTests;
import dev.jstech.tests.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Locale;

/**
 * The scheduler times every Operation (ticks queued or waiting, ticks running), stamps the log record with
 * it, and keeps the last hour's statistics per type for the Stats tab and the {@code stats} command.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class OperationStatisticsGameTests {

    private OperationStatisticsGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;

    private static OperationStatistics.TypeSummary summaryOf(final List<OperationStatistics.TypeSummary> summaries,
                                                             final int type) {
        for (final OperationStatistics.TypeSummary summary : summaries) {
            if (summary.type() == type) {
                return summary;
            }
        }
        return null;
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void timing_logRecordsCarryWaitAndRunAndFeedTheHourlyStatistics(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = OperationSchedulingGameTests.storageNetwork(helper);
        final ItemStackHandler first = new ItemStackHandler(9);
        final ItemStackHandler second = new ItemStackHandler(9);
        final NetworkSelectOperation[] ops = new NetworkSelectOperation[2];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> OperationSchedulingGameTests.rack(helper)
                        .getServerStorage(0).insert(Items.COBBLESTONE, 100))
                .thenExecuteAfter(2, () -> {
                    // On a single queue the second pull waits for the first: it must show waited ticks.
                    ops[0] = mainframe.submitNetworkSelect(Items.COBBLESTONE, 30, OperationSchedulingGameTests.port(first), "first");
                    ops[1] = mainframe.submitNetworkSelect(Items.COBBLESTONE, 30, OperationSchedulingGameTests.port(second), "second");
                    helper.assertTrue(ops[0] != null && ops[1] != null, "both pulls dispatched");
                })
                .thenExecuteAfter(6, () -> {
                    final List<OperationRecord> live = mainframe.activeOperationRecords();
                    helper.assertTrue(live.size() == 2, "both in flight; got " + live);
                    helper.assertTrue(live.get(0).ranTicks() >= 5 && live.get(0).waitedTicks() == 0,
                            "the first pull has been running; got " + live.get(0));
                    helper.assertTrue(live.get(1).waitedTicks() >= 5 && live.get(1).ranTicks() == 0,
                            "the second pull has been waiting; got " + live.get(1));
                })
                .thenExecuteAfter(60, () -> {
                    helper.assertTrue(OperationSchedulingGameTests.count(first) == 30
                            && OperationSchedulingGameTests.count(second) == 30, "both pulls delivered");
                    final List<OperationRecord> log = mainframe.recentOperations(); // newest first
                    helper.assertTrue(log.size() >= 2, "both logged; got " + log.size());
                    final OperationRecord older = log.get(1);
                    final OperationRecord newer = log.get(0);
                    helper.assertTrue(older.id().equals(ops[0].operationId()) && older.waitedTicks() == 0
                                    && older.ranTicks() >= 10, "the first pull ran through the HDD seek without waiting; got " + older);
                    helper.assertTrue(newer.id().equals(ops[1].operationId()) && newer.waitedTicks() >= 10
                                    && newer.ranTicks() >= 1, "the second pull waited for the queue then ran; got " + newer);
                    final long now = helper.getLevel().getGameTime();
                    final OperationStatistics.TypeSummary select =
                            summaryOf(mainframe.statistics().summaries(now), OperationRecord.TYPE_SELECT);
                    helper.assertTrue(select != null && select.count() == 2 && select.shortfalls() == 0,
                            "the hour counts two complete SELECTs; got " + select);
                    helper.assertTrue(select.averageRun() >= 5 && select.averageWait() >= 5,
                            "the averages reflect the wait and the run; got " + select);
                    helper.assertTrue(select.moved() == 60L, "the hour moved 60; got " + select.moved());
                    helper.assertTrue(mainframe.statistics().peakConcurrentLastDay(now) >= 2,
                            "two Operations were in flight at once; peak "
                                    + mainframe.statistics().peakConcurrentLastDay(now));
                })
                .thenSucceed();
    }

    private static boolean cliContains(final CliShell.Response response, final String needle) {
        final String lower = needle.toLowerCase(Locale.ROOT);
        return response.lines().stream()
                .anyMatch(line -> line.text().toLowerCase(Locale.ROOT).contains(lower));
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void cli_statsListsTheHourByType(final GameTestHelper helper) {
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos router = new BlockPos(3, 2, 2);
        final BlockPos eth = new BlockPos(4, 2, 2);
        final BlockPos pc = new BlockPos(5, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3); // behind the cable (rear-only connection)
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        world.placeRunningMainframe(new BlockPos(1, 2, 2));
        world.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        world.setBlock(router, ComputingModule.PERSONAL_ROUTER.get());
        world.setBlock(eth, ComputingModule.ETHERNET_CABLE.get());
        final PersonalComputerBlockEntity computer = world.placeRunningPersonalComputer(pc);
        world.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        final ServerRackBlockEntity rackBe = world.blockEntity(rack, ServerRackBlockEntity.class);
        TestWorldBuilder.mountDefaultServer(rackBe, 0);
        final ServerCliComputer cli = new ServerCliComputer((ComputerTerminalHost) computer, helper.getLevel());
        final CliShell shell = CliCommands.newShell(60);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> rackBe.getServerStorage(0).insert(Items.COBBLESTONE, 200))
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(cliContains(shell.run("stats", cli), "no operations settled"),
                            "an idle network has nothing to report");
                    helper.assertTrue(cliContains(shell.run("operation select 30 cobblestone", cli), "SELECT queued"),
                            "the pull queues");
                })
                .thenExecuteAfter(30, () -> {
                    final CliShell.Response stats = shell.run("stats", cli);
                    helper.assertTrue(cliContains(stats, "SELECT") && cliContains(stats, "1 ops"),
                            "stats lists the settled SELECT; got " + stats.lines());
                    helper.assertTrue(cliContains(stats, "peak 1 in flight"), "the day's peak is reported");
                })
                .thenSucceed();
    }
}
