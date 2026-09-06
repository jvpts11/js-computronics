/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Tech Series.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.operation.payload.OperationRecord;
import dev.jstech.core.network.NetworkSystem;
import dev.jstech.core.network.SubframeNode;
import dev.jstech.core.uuid.NodeUuid;
import dev.jstech.tests.JsTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Optional;

/**
 * A Subframe orchestrated by the Mainframe pools into it: a share of its capacity (the balance factor) and
 * its GPUs as extra dispatch queues. The Mainframe reads the network registry each tick, so the pool
 * follows the Subframes as they come and go. The Subframe is registered straight into the network
 * registry here, the way its block entity will register it.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class SubframePoolingGameTests {

    private SubframePoolingGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void subframe_addsItsShareOfCapacityAndItsQueues(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = OperationSchedulingGameTests.storageNetwork(helper);
        final ItemStackHandler first = new ItemStackHandler(9);
        final ItemStackHandler second = new ItemStackHandler(9);
        final NodeUuid subframeId = NodeUuid.random();
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    OperationSchedulingGameTests.rack(helper).getServerStorage(0).insert(Items.COBBLESTONE, 100);
                    final long own = mainframe.capacity();
                    final int ownQueues = mainframe.parallelQueues();
                    helper.assertTrue(mainframe.pooledCapacity() == own && mainframe.pooledQueues() == ownQueues,
                            "alone, the pool is the Mainframe itself");
                    // A Subframe with 1000 capacity and one GPU, orchestrated by this Mainframe.
                    NetworkSystem.get(helper.getLevel()).registerSubframe(new SubframeNode(subframeId,
                            mainframe.networkUuid(), 1000L, Optional.of(mainframe.nodeUuid()), 1));
                    helper.assertTrue(mainframe.pooledCapacity() == own + 600L,
                            "the Subframe lends 60% of its capacity; got " + mainframe.pooledCapacity() + " vs " + own);
                    helper.assertTrue(mainframe.pooledQueues() == ownQueues + 1,
                            "the Subframe's GPU adds a queue; got " + mainframe.pooledQueues());
                })
                .thenExecuteAfter(2, () -> {
                    // Two pulls on what used to be a single queue: both run at once now.
                    helper.assertTrue(mainframe.submitNetworkSelect(Items.COBBLESTONE, 30,
                            OperationSchedulingGameTests.port(first), "first") != null, "first dispatched");
                    helper.assertTrue(mainframe.submitNetworkSelect(Items.COBBLESTONE, 30,
                            OperationSchedulingGameTests.port(second), "second") != null, "second dispatched");
                })
                .thenExecuteAfter(6, () -> {
                    final List<OperationRecord> live = mainframe.activeOperationRecords();
                    helper.assertTrue(live.size() == 2 && live.get(0).status() == OperationRecord.STATUS_PROCESSING
                                    && live.get(1).status() == OperationRecord.STATUS_PROCESSING,
                            "both pulls hold a queue at once; got " + live);
                })
                .thenExecuteAfter(30, () -> {
                    helper.assertTrue(OperationSchedulingGameTests.count(first) == 30
                            && OperationSchedulingGameTests.count(second) == 30, "both delivered");
                    // The Subframe goes away: the pool shrinks back to the Mainframe alone.
                    NetworkSystem.get(helper.getLevel()).unregisterSubframe(mainframe.networkUuid(), subframeId);
                    helper.assertTrue(mainframe.pooledQueues() == mainframe.parallelQueues()
                                    && mainframe.pooledCapacity() == mainframe.capacity(),
                            "without the Subframe the pool is the Mainframe again");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void subframe_idleOneLendsNothing(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = OperationSchedulingGameTests.storageNetwork(helper);
        final NodeUuid subframeId = NodeUuid.random();
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    NetworkSystem.get(helper.getLevel()).registerSubframe(new SubframeNode(subframeId,
                            mainframe.networkUuid(), 1000L, Optional.empty(), 2));
                    helper.assertTrue(mainframe.pooledCapacity() == mainframe.capacity()
                                    && mainframe.pooledQueues() == mainframe.parallelQueues(),
                            "an idle Subframe (no orchestrator) lends neither capacity nor queues");
                    NetworkSystem.get(helper.getLevel()).unregisterSubframe(mainframe.networkUuid(), subframeId);
                })
                .thenSucceed();
    }
}
