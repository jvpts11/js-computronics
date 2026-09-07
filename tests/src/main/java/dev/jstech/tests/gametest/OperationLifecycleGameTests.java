/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Tech Series.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.operation.ComputingOperations;
import dev.jstech.computronics.operation.NetworkSelectOperation;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.core.JsCore;
import dev.jstech.core.event.IOperationLifecycleEvent;
import dev.jstech.core.operation.OperationPriority;
import dev.jstech.core.operation.OperationStatus;
import dev.jstech.core.operation.OperationType;
import dev.jstech.tests.JsTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The computing mod declares its Operation types in the core registry and the Mainframe reports every
 * Operation's life on the core event bus, so another mod can watch or drive the network without a class
 * dependency on the Mainframe's internals.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class OperationLifecycleGameTests {

    private OperationLifecycleGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;

    @GameTest(template = ARENA)
    public static void registry_declaresEveryComputingOperationType(final GameTestHelper helper) {
        final var registry = JsCore.operations();
        for (final String id : new String[] {ComputingOperations.SELECT, ComputingOperations.INSERT,
                ComputingOperations.DELETE, ComputingOperations.MOVE, ComputingOperations.CRAFT,
                ComputingOperations.PROCESSING, ComputingOperations.MULTI_STAGE, ComputingOperations.ANALYZE,
                ComputingOperations.REINDEX, ComputingOperations.VACUUM, ComputingOperations.DROP,
                ComputingOperations.LOCK, ComputingOperations.UNLOCK}) {
            helper.assertTrue(registry.contains(id), id + " must be registered");
        }
        helper.assertTrue(registry.get(ComputingOperations.SELECT).map(OperationType::argsClass).orElse(null)
                == ComputingOperations.PullArgs.class, "a SELECT takes pull arguments");
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void events_followASelectFromCreatedToCompleted(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = OperationSchedulingGameTests.storageNetwork(helper);
        final ItemStackHandler dest = new ItemStackHandler(9);
        final List<IOperationLifecycleEvent> seen = new ArrayList<>();
        final NetworkSelectOperation[] op = new NetworkSelectOperation[1];
        // The bus is shared by every test on the server: listen for this network's events only, and let
        // go of the listener when done so it does not outlive the test.
        final Consumer<IOperationLifecycleEvent> listener = event -> {
            if (event.networkUuid().equals(mainframe.networkUuid())) {
                seen.add(event);
            }
        };
        JsCore.events().subscribe(IOperationLifecycleEvent.class, listener);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> OperationSchedulingGameTests.rack(helper)
                        .getServerStorage(0).insert(Items.COBBLESTONE, 100))
                .thenExecuteAfter(2, () -> {
                    op[0] = mainframe.submitNetworkSelect(Items.COBBLESTONE, 30,
                            OperationSchedulingGameTests.port(dest), "events");
                    helper.assertTrue(op[0] != null, "the pull is accepted");
                    helper.assertTrue(seen.size() == 1 && seen.get(0) instanceof IOperationLifecycleEvent.Created c
                            && c.operationId().equals(op[0].operationId())
                            && c.typeId().equals(ComputingOperations.SELECT),
                            "Created is posted on submission; seen=" + seen);
                })
                .thenExecuteAfter(2, () -> helper.assertTrue(seen.size() == 2
                                && seen.get(1) instanceof IOperationLifecycleEvent.Started s
                                && s.operationId().equals(op[0].operationId()),
                        "Started is posted the first tick it runs; seen=" + seen))
                .thenExecuteAfter(40, () -> {
                    JsCore.events().unsubscribe(IOperationLifecycleEvent.class, listener);
                    helper.assertTrue(OperationSchedulingGameTests.count(dest) == 30, "the pull delivered");
                    helper.assertTrue(seen.size() == 3
                                    && seen.get(2) instanceof IOperationLifecycleEvent.Completed done
                                    && done.operationId().equals(op[0].operationId())
                                    && done.durationTicks() >= 10,
                            "Completed closes the life with its duration; seen=" + seen);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void events_aCancelledOperationIsDiscarded(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = OperationSchedulingGameTests.storageNetwork(helper);
        final ItemStackHandler dest = new ItemStackHandler(9);
        final List<IOperationLifecycleEvent> seen = new ArrayList<>();
        final NetworkSelectOperation[] op = new NetworkSelectOperation[1];
        final Consumer<IOperationLifecycleEvent> listener = event -> {
            if (event.networkUuid().equals(mainframe.networkUuid())) {
                seen.add(event);
            }
        };
        JsCore.events().subscribe(IOperationLifecycleEvent.class, listener);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> OperationSchedulingGameTests.rack(helper)
                        .getServerStorage(0).insert(Items.COBBLESTONE, 100))
                .thenExecuteAfter(2, () -> {
                    op[0] = mainframe.submitNetworkSelect(Items.COBBLESTONE, 30,
                            OperationSchedulingGameTests.port(dest), "events");
                    helper.assertTrue(op[0] != null, "the pull is accepted");
                })
                .thenExecuteAfter(3, () -> helper.assertTrue(mainframe.cancelOperation(op[0].operationId()),
                        "the pull is cancelled mid-seek"))
                .thenExecuteAfter(3, () -> {
                    JsCore.events().unsubscribe(IOperationLifecycleEvent.class, listener);
                    final IOperationLifecycleEvent last = seen.isEmpty() ? null : seen.get(seen.size() - 1);
                    helper.assertTrue(last instanceof IOperationLifecycleEvent.Discarded d
                            && d.operationId().equals(op[0].operationId()), "Discarded closes a cancelled life; seen=" + seen);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void registry_handlerSubmitsThroughTheMainframe(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = OperationSchedulingGameTests.storageNetwork(helper);
        final ItemStackHandler dest = new ItemStackHandler(9);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> OperationSchedulingGameTests.rack(helper)
                        .getServerStorage(0).insert(Items.COBBLESTONE, 100))
                .thenExecuteAfter(2, () -> {
                    // Another mod would drive the network like this: look the type up, hand it typed arguments.
                    @SuppressWarnings("unchecked")
                    final OperationType<ComputingOperations.PullArgs> select = (OperationType<ComputingOperations.PullArgs>)
                            JsCore.operations().get(ComputingOperations.SELECT).orElseThrow();
                    final OperationStatus status = select.handler().execute(new ComputingOperations.PullArgs(
                            mainframe, StorageKey.of(Items.COBBLESTONE), 30L, OperationSchedulingGameTests.port(dest),
                            "addon", null, OperationPriority.HIGH));
                    helper.assertTrue(status == OperationStatus.PENDING, "a timed Operation is accepted as PENDING; got " + status);
                    final var live = mainframe.activeOperationRecords();
                    helper.assertTrue(live.size() == 1 && live.get(0).priority() == OperationPriority.HIGH,
                            "the handler submitted it at the level asked; got " + live);
                })
                .thenExecuteAfter(40, () -> helper.assertTrue(OperationSchedulingGameTests.count(dest) == 30,
                        "the Operation ran through the Mainframe; got " + OperationSchedulingGameTests.count(dest)))
                .thenSucceed();
    }
}
