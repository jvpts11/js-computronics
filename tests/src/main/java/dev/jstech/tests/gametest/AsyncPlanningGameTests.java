/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Tech Series.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.crafting.PendingCraftOperation;
import dev.jstech.computronics.operation.NetworkOperation;
import dev.jstech.computronics.operation.payload.OperationRecord;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.core.operation.OperationPriority;
import dev.jstech.tests.JsTests;
import dev.jstech.tests.testkit.CraftFiles;
import dev.jstech.tests.testkit.TestWorldBuilder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * A craft request plans off the tick: the request is listed at once as a pending craft, a virtual thread
 * makes the plan, and the real craft takes over on the main thread when the plan lands — carrying the level
 * and the settle callback given meanwhile — while the log shows one craft, not two. A request nothing can
 * make is refused at once, and one cancelled while planning never starts.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class AsyncPlanningGameTests {

    private AsyncPlanningGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;
    private static final StorageKey PLANKS = StorageKey.of(Items.OAK_PLANKS);

    private static long craftRecords(final MainframeBlockEntity mainframe) {
        return mainframe.recentOperations().stream()
                .filter(r -> r.type() == OperationRecord.TYPE_CRAFT).count();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void craftRequest_plansOffTheTickThenRunsOneCraft(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        final NetworkOperation[] request = new NetworkOperation[1];
        final int[] settled = {0};
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    net.seed(Items.OAK_LOG, 2);
                    net.cc().loadPattern(CraftFiles.oakPlanks());
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    request[0] = net.mainframe().submitCraftRequest(PLANKS, 8, false, "test", () -> settled[0]++);
                    helper.assertTrue(request[0] instanceof PendingCraftOperation,
                            "the request is accepted as a pending craft while its plan is made");
                    request[0].setPriority(OperationPriority.HIGH);
                    final List<OperationRecord> live = net.mainframe().activeOperationRecords();
                    helper.assertTrue(live.size() == 1 && live.get(0).id().equals(request[0].operationId())
                                    && live.get(0).type() == OperationRecord.TYPE_CRAFT
                                    && live.get(0).status() == OperationRecord.STATUS_PENDING,
                            "the pending craft is listed at once; got " + live);
                })
                // The plan is made on a virtual thread promoted on the next tick and lands the tick after: the
                // placeholder is still the only thing listed one tick in.
                .thenExecuteAfter(1, () -> {
                    final List<OperationRecord> live = net.mainframe().activeOperationRecords();
                    helper.assertTrue(!request[0].isDone() && live.size() == 1
                                    && live.get(0).id().equals(request[0].operationId())
                                    && live.get(0).status() == OperationRecord.STATUS_PENDING,
                            "the request stays pending while the plan is made; got " + live);
                })
                // A two-run bench craft is over in a couple of ticks, so the outcome is checked from the log.
                .thenExecuteAfter(40, () -> {
                    final var storage = net.storage(helper.getLevel());
                    helper.assertTrue(storage.count(StorageKey.of(Items.OAK_PLANKS)) == 8,
                            "the craft delivered the planks; got " + storage.count(StorageKey.of(Items.OAK_PLANKS)));
                    final PendingCraftOperation pending = (PendingCraftOperation) request[0];
                    helper.assertTrue(pending.isDone() && pending.delivered() != null,
                            "the placeholder handed over to a real craft");
                    final List<OperationRecord> crafts = net.mainframe().recentOperations().stream()
                            .filter(r -> r.type() == OperationRecord.TYPE_CRAFT).toList();
                    helper.assertTrue(crafts.size() == 1, "one craft in the log, none for the placeholder; log="
                            + net.mainframe().recentOperations());
                    helper.assertTrue(crafts.get(0).id().equals(pending.delivered().operationId())
                                    && crafts.get(0).status() == OperationRecord.STATUS_COMPLETED
                                    && crafts.get(0).priority() == OperationPriority.HIGH,
                            "the log carries the real craft, completed at the level given while planning; got "
                                    + crafts.get(0));
                    helper.assertTrue(settled[0] == 1, "the settle callback fired once, from the real craft; got "
                            + settled[0]);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void craftRequest_nothingMakesIt_isRefusedAtOnce(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> net.cc().loadPattern(CraftFiles.oakPlanks()))
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertTrue(net.mainframe().submitCraftRequest(StorageKey.of(Items.PISTON), 1, true,
                            "test", null) == null, "no pattern makes pistons: refused on the spot");
                    helper.assertTrue(net.mainframe().activeOperationRecords().isEmpty(), "nothing was queued");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void craftRequest_cancelledWhilePlanning_neverStarts(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        final int[] settled = {0};
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    net.seed(Items.OAK_LOG, 2);
                    net.cc().loadPattern(CraftFiles.oakPlanks());
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final NetworkOperation request = net.mainframe().submitCraftRequest(PLANKS, 8, false, "test",
                            () -> settled[0]++);
                    helper.assertTrue(request != null, "the request is accepted");
                    helper.assertTrue(net.mainframe().cancelOperation(request.operationId()),
                            "the pending craft can be cancelled");
                    helper.assertTrue(settled[0] == 1, "cancelling settles the request; callback fired " + settled[0]);
                })
                .thenExecuteAfter(40, () -> {
                    helper.assertTrue(net.mainframe().activeOperationRecords().isEmpty(),
                            "no craft started; got " + net.mainframe().activeOperationRecords());
                    final boolean discarded = net.mainframe().recentOperations().stream()
                            .anyMatch(r -> r.type() == OperationRecord.TYPE_CRAFT
                                    && r.status() == OperationRecord.STATUS_DISCARDED);
                    helper.assertTrue(discarded, "the cancelled request is logged DISCARDED; log="
                            + net.mainframe().recentOperations());
                    helper.assertTrue(net.storage(helper.getLevel()).count(StorageKey.of(Items.OAK_LOG)) == 2,
                            "the logs were never touched");
                })
                .thenSucceed();
    }
}
