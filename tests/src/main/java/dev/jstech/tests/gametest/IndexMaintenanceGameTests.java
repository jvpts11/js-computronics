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
import dev.jstech.computronics.program.ServerCliComputer;
import dev.jstech.computronics.program.cli.CliCommands;
import dev.jstech.computronics.program.cli.CliShell;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.computronics.terminal.ComputerTerminalHost;
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

import java.util.Locale;

/**
 * A REINDEX reads the disks on its tick and builds the catalog off it: the swap lands a tick or two later,
 * when the caller's completion runs, and reads meanwhile keep the old catalog.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class IndexMaintenanceGameTests {

    private IndexMaintenanceGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;
    private static final StorageKey COBBLE = StorageKey.of(Items.COBBLESTONE);

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void reindexAsync_swapsTheCatalogOnALaterTick(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = OperationSchedulingGameTests.storageNetwork(helper);
        final boolean[] done = {false};
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> OperationSchedulingGameTests.rack(helper)
                        .getServerStorage(0).insert(Items.COBBLESTONE, 100))
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(mainframe.networkIndex().available(COBBLE) == 100,
                            "the incremental pass catalogued the stock");
                    mainframe.reindexAsync(() -> done[0] = true);
                    helper.assertTrue(!done[0], "the rebuild does not complete on the tick it was asked");
                    helper.assertTrue(mainframe.networkIndex().available(COBBLE) == 100,
                            "reads keep the old catalog while the new one is built");
                })
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(done[0], "the completion ran once the catalog was swapped in");
                    helper.assertTrue(mainframe.networkIndex().available(COBBLE) == 100
                                    && mainframe.networkIndex().catalogSize() == 1
                                    && mainframe.networkIndex().indexedServerCount() == 1,
                            "the rebuilt catalog holds the server's stock; got "
                                    + mainframe.networkIndex().available(COBBLE));
                    helper.assertTrue(mainframe.networkIndex().health().isClean(), "a full rebuild leaves the index clean");
                })
                .thenSucceed();
    }

    private static boolean cliContains(final CliShell.Response response, final String needle) {
        final String lower = needle.toLowerCase(Locale.ROOT);
        return response.lines().stream()
                .anyMatch(line -> line.text().toLowerCase(Locale.ROOT).contains(lower));
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void cli_reindexStartsTheRebuildAndReturnsAtOnce(final GameTestHelper helper) {
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos router = new BlockPos(3, 2, 2);
        final BlockPos eth = new BlockPos(4, 2, 2);
        final BlockPos pc = new BlockPos(5, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3); // behind the cable (rear-only connection)
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final MainframeBlockEntity mainframe = world.placeRunningMainframe(new BlockPos(1, 2, 2));
        world.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        world.setBlock(router, ComputingModule.PERSONAL_ROUTER.get());
        world.setBlock(eth, ComputingModule.ETHERNET_CABLE.get());
        final PersonalComputerBlockEntity computer = world.placeRunningPersonalComputer(pc);
        world.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        final ServerRackBlockEntity rackBe = world.blockEntity(rack, ServerRackBlockEntity.class);
        TestWorldBuilder.mountDefaultServer(rackBe, 0);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> rackBe.getServerStorage(0).insert(Items.COBBLESTONE, 200))
                .thenExecuteAfter(2, () -> {
                    // The maintenance verbs run on the Mainframe's own prompt.
                    final ServerCliComputer cli = new ServerCliComputer((ComputerTerminalHost) mainframe, helper.getLevel());
                    final CliShell shell = CliCommands.newShell(50);
                    helper.assertTrue(cliContains(shell.run("reindex", cli), "REINDEX started"),
                            "the prompt answers at once and the rebuild runs on");
                })
                .thenExecuteAfter(3, () -> helper.assertTrue(mainframe.networkIndex().available(COBBLE) == 200,
                        "the rebuilt catalog holds the stock; got " + mainframe.networkIndex().available(COBBLE)))
                .thenSucceed();
    }
}
