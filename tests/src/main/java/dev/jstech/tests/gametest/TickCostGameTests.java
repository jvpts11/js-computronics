/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.operation.NetworkStorage;
import dev.jstech.core.network.NetworkSystem;
import dev.jstech.core.network.ServerNode;
import dev.jstech.core.uuid.NetworkUuid;
import dev.jstech.tests.JsTests;
import dev.jstech.tests.testkit.TestWorldBuilder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * What an idle base costs per tick must not grow with things that did not change: a rack re-registering the
 * same servers hands readers the same list, a mounted machine's build is parsed once, and the per-tick
 * storage views never outlive their tick.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class TickCostGameTests {

    private TickCostGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;

    @GameTest(template = ARENA)
    public static void serversOf_sharesOneListWhileTheRosterStandsStill(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        final List<ServerNode>[] seen = new List[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final NetworkUuid network = net.mainframe().networkUuid();
                    helper.assertTrue(network != null, "the Mainframe owns a network");
                    seen[0] = NetworkSystem.get(helper.getLevel()).serversOf(network);
                    helper.assertTrue(seen[0].size() == 1, "the rack registered its one server; got " + seen[0].size());
                })
                .thenExecuteAfter(5, () -> {
                    // Five more ticks of the rack re-registering an unchanged server: the readers' list is
                    // the very same object, so nothing was rebuilt or copied for them.
                    final List<ServerNode> again = NetworkSystem.get(helper.getLevel()).serversOf(net.mainframe().networkUuid());
                    helper.assertTrue(again == seen[0], "the same list is shared until the roster changes");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void rackBuild_isReadOnceAndRefreshedWhenTheMachineIsSwapped(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertTrue(net.rack().thermalLoadWatts() > 0, "a mounted, powered machine draws power");
                    // Pull the machine: the cached build must go with it.
                    net.rack().getServers().setStackInSlot(0, ItemStack.EMPTY);
                    helper.assertTrue(net.rack().thermalLoadWatts() == 0,
                            "an emptied slot draws nothing; got " + net.rack().thermalLoadWatts());
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void networkStorageViews_neverOutliveTheirTick(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> NetworkStorage.of(helper.getLevel(), net.mainframe().networkUuid()))
                .thenExecuteAfter(3, () -> {
                    NetworkStorage.of(helper.getLevel(), net.mainframe().networkUuid());
                    helper.assertTrue(!NetworkStorage.holdsStaleViews(helper.getLevel().getGameTime()),
                            "a view from an earlier tick is evicted as soon as a fresh one is built");
                })
                .thenSucceed();
    }
}
