/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.blockentity.PersonalComputerBlockEntity;
import dev.jstech.computronics.operation.NetworkStorage;
import dev.jstech.computronics.program.ServerCliComputer;
import dev.jstech.computronics.program.iql.IqlParseResult;
import dev.jstech.computronics.program.iql.IqlParser;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.computronics.terminal.ComputerTerminalHost;
import dev.jstech.tests.JsTests;
import dev.jstech.tests.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * IQL run against the network through the same server-side CLI the Command Prompt and NMS use, checking the
 * parts that must never misbehave under a storm of statements: QUERY reflects the real network, a manual LOCK
 * takes and releases hold of a stock type and accounts for the amount, and malformed or unknown-target
 * statements are rejected without crashing. None of these verbs moves items, so the network's counts must be
 * exactly what was seeded throughout.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class IqlStressGameTests {

    private IqlStressGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 8;
    private static final int COBBLE = 500;
    private static final int DIRT = 300;

    private static void run(final ServerCliComputer cli, final String statement) {
        final IqlParseResult parsed = IqlParser.tryParse(statement);
        if (parsed.ok()) {
            cli.execute(parsed.operation());
        }
    }

    @GameTest(template = ARENA, timeoutTicks = 2000)
    public static void iqlQueryLockAndAdversarialAreSafe(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final MainframeBlockEntity mainframe = world.placeRunningMainframe(new BlockPos(1, 2, 2));
        world.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        world.placeSeededRack(new BlockPos(2, 2, 1));
        world.setBlock(new BlockPos(3, 2, 2), ComputingModule.PERSONAL_ROUTER.get());
        world.setBlock(new BlockPos(4, 2, 2), ComputingModule.ETHERNET_CABLE.get());
        final PersonalComputerBlockEntity pc = world.placeRunningPersonalComputer(new BlockPos(5, 2, 2));
        final ComputerTerminalHost host = pc;
        final StorageKey cobble = StorageKey.of(Items.COBBLESTONE);
        final StorageKey dirt = StorageKey.of(Items.DIRT);

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final NetworkStorage storage = NetworkStorage.of(helper.getLevel(), mainframe.networkUuid());
                    storage.insert(cobble, COBBLE);
                    storage.insert(dirt, DIRT);
                })
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = new ServerCliComputer(host, helper.getLevel());

                    // QUERY must reflect the real network, not a canned schema.
                    helper.assertFalse(cli.queryObject("items", null, "", 64).isEmpty(),
                            "QUERY items must list the network's stock");
                    helper.assertFalse(cli.queryObject("servers", null, "", 64).isEmpty(),
                            "QUERY servers must list the rack's server");

                    // A manual LOCK must take hold of a stock type; UNLOCK must release it.
                    run(cli, "LOCK 100 cobblestone");
                    helper.assertTrue(mainframe.networkIndex().isManuallyLocked(cobble),
                            "LOCK must mark cobblestone as manually locked");
                    run(cli, "UNLOCK cobblestone");
                    helper.assertFalse(mainframe.networkIndex().isManuallyLocked(cobble),
                            "UNLOCK must release the lock on cobblestone");

                    // Locking everything of a type (quantity 0 == all) must hold the whole stock.
                    run(cli, "LOCK 0 dirt");
                    helper.assertTrue(mainframe.networkIndex().isManuallyLocked(dirt),
                            "LOCK 0 must lock the whole dirt stock");

                    // A second lock while already locked, and an unlock of an unlocked type, must not corrupt state.
                    run(cli, "LOCK 50 dirt");
                    run(cli, "UNLOCK cobblestone");
                    helper.assertTrue(mainframe.networkIndex().isManuallyLocked(dirt), "dirt stays locked");
                    run(cli, "UNLOCK dirt");
                    helper.assertFalse(mainframe.networkIndex().isManuallyLocked(dirt), "UNLOCK must release dirt");

                    // Malformed, incomplete and unknown-target statements must be refused, never crash. (A
                    // non-positive quantity is NOT malformed: like CRAFT and SELECT, it means "all", so it is
                    // left out here to avoid actually locking cobblestone.)
                    for (final String bad : new String[]{
                            "", "   ", "LOCK", "UNLOCK", "LOCK 5 not_a_real_item",
                            "SELECT", "SELECT 5 not_a_real_item", "COUNT nonsense WHERE", "garbage tokens", "operation"}) {
                        run(cli, bad);
                    }
                    helper.assertTrue(!cli.lock("not_a_real_item", 1).ok(), "LOCK of an unknown item must be rejected");
                    helper.assertTrue(!cli.select("not_a_real_item", 1).ok(), "SELECT of an unknown item must be rejected");
                })
                .thenExecute(() -> {
                    // None of these verbs move items, so the stock is exactly what was seeded, and no lock lingers.
                    final NetworkStorage storage = NetworkStorage.of(helper.getLevel(), mainframe.networkUuid());
                    helper.assertTrue(storage.count(cobble) == COBBLE, "cobblestone stock must be untouched; got "
                            + storage.count(cobble));
                    helper.assertTrue(storage.count(dirt) == DIRT, "dirt stock must be untouched; got " + storage.count(dirt));
                    helper.assertFalse(mainframe.networkIndex().isManuallyLocked(cobble), "no lock may linger on cobblestone");
                    helper.assertFalse(mainframe.networkIndex().isManuallyLocked(dirt), "no lock may linger on dirt");
                    final int dropped = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            net.minecraft.world.phys.AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(0, 0, 0)),
                                    helper.absolutePos(new BlockPos(16, 8, 16)))).size();
                    helper.assertTrue(dropped == 0, dropped + " items leaked to the world during the IQL run");
                })
                .thenSucceed();
    }
}
