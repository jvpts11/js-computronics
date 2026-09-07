/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.event;

import dev.jstech.core.uuid.NetworkUuid;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreEventDispatcherTest {

    private static final String TYPE = "jsc:select";

    private static NetworkUuid net() {
        return new NetworkUuid(UUID.randomUUID());
    }

    private static UUID op() {
        return UUID.randomUUID();
    }

    private static IOperationLifecycleEvent.Created created() {
        return new IOperationLifecycleEvent.Created(net(), op(), TYPE);
    }

    private static IOperationLifecycleEvent.Started started() {
        return new IOperationLifecycleEvent.Started(net(), op(), TYPE);
    }

    @Test
    void singleListener_receivesEvent() {
        CoreEventDispatcher dispatcher = new CoreEventDispatcher();
        List<IOperationLifecycleEvent.Created> received = new ArrayList<>();
        dispatcher.subscribe(IOperationLifecycleEvent.Created.class, received::add);

        IOperationLifecycleEvent.Created event = created();
        dispatcher.post(event);

        assertEquals(1, received.size());
        assertEquals(event, received.get(0));
    }

    @Test
    void noListeners_postIsNoOp() {
        CoreEventDispatcher dispatcher = new CoreEventDispatcher();
        IOperationLifecycleEvent.Created event = created();
        // Must not throw; just returns.
        IOperationLifecycleEvent.Created result = dispatcher.post(event);
        assertEquals(event, result);
    }

    @Test
    void postReturnsEvent_forFluentChaining() {
        CoreEventDispatcher dispatcher = new CoreEventDispatcher();
        IOperationLifecycleEvent.Created event = created();
        assertEquals(event, dispatcher.post(event));
    }

    @Test
    void multipleListeners_invokedInRegistrationOrder() {
        CoreEventDispatcher dispatcher = new CoreEventDispatcher();
        List<Integer> order = new ArrayList<>();
        dispatcher.subscribe(IOperationLifecycleEvent.Started.class,
                e -> order.add(1));
        dispatcher.subscribe(IOperationLifecycleEvent.Started.class,
                e -> order.add(2));
        dispatcher.subscribe(IOperationLifecycleEvent.Started.class,
                e -> order.add(3));

        dispatcher.post(started());

        assertEquals(List.of(1, 2, 3), order);
    }

    @Test
    void subscribeToSealedRoot_receivesAllChildren() {
        CoreEventDispatcher dispatcher = new CoreEventDispatcher();
        AtomicInteger count = new AtomicInteger();
        dispatcher.subscribe(IOperationLifecycleEvent.class, e -> count.incrementAndGet());

        dispatcher.post(created());
        dispatcher.post(started());
        dispatcher.post(new IOperationLifecycleEvent.Completed(net(), op(), TYPE, 100L));
        dispatcher.post(new IOperationLifecycleEvent.Failed(net(), op(), TYPE, "test"));
        dispatcher.post(new IOperationLifecycleEvent.Discarded(net(), op(), TYPE));

        assertEquals(5, count.get());
    }

    @Test
    void subscribeToConcreteType_doesNotReceiveSiblings() {
        CoreEventDispatcher dispatcher = new CoreEventDispatcher();
        AtomicInteger createdCount = new AtomicInteger();
        dispatcher.subscribe(IOperationLifecycleEvent.Created.class,
                e -> createdCount.incrementAndGet());

        dispatcher.post(created());
        dispatcher.post(started());

        assertEquals(1, createdCount.get());
    }

    @Test
    void cancelledEvent_skipsLaterListeners() {
        CoreEventDispatcher dispatcher = new CoreEventDispatcher();
        List<Integer> order = new ArrayList<>();

        dispatcher.subscribe(NetworkPropagatingEvent.class, e -> {
            order.add(1);
            e.cancel();
        });
        dispatcher.subscribe(NetworkPropagatingEvent.class, e -> order.add(2));
        dispatcher.subscribe(NetworkPropagatingEvent.class, e -> order.add(3));

        NetworkPropagatingEvent event = new NetworkPropagatingEvent(net(), 100L, 200L);
        dispatcher.post(event);

        assertEquals(List.of(1), order);
        assertTrue(event.isCancelled());
    }

    @Test
    void uncancelledEvent_runsAllListeners() {
        CoreEventDispatcher dispatcher = new CoreEventDispatcher();
        AtomicInteger count = new AtomicInteger();
        dispatcher.subscribe(NetworkPropagatingEvent.class,
                e -> count.incrementAndGet());
        dispatcher.subscribe(NetworkPropagatingEvent.class,
                e -> count.incrementAndGet());
        dispatcher.subscribe(NetworkPropagatingEvent.class,
                e -> count.incrementAndGet());

        NetworkPropagatingEvent event = new NetworkPropagatingEvent(net(), 100L, 200L);
        dispatcher.post(event);

        assertEquals(3, count.get());
        assertFalse(event.isCancelled());
    }

    @Test
    void cancel_isIdempotent() {
        NetworkPropagatingEvent event = new NetworkPropagatingEvent(net(), 100L, 200L);
        event.cancel();
        event.cancel();
        event.cancel();
        assertTrue(event.isCancelled());
    }

    @Test
    void subscribeWithNullClass_throws() {
        CoreEventDispatcher dispatcher = new CoreEventDispatcher();
        assertThrows(NullPointerException.class, () ->
                dispatcher.subscribe(null, e -> { }));
    }

    @Test
    void subscribeWithNullListener_throws() {
        CoreEventDispatcher dispatcher = new CoreEventDispatcher();
        assertThrows(NullPointerException.class, () ->
                dispatcher.subscribe(IOperationLifecycleEvent.Created.class, null));
    }

    @Test
    void postNullEvent_throws() {
        CoreEventDispatcher dispatcher = new CoreEventDispatcher();
        assertThrows(NullPointerException.class, () -> dispatcher.post(null));
    }

    @Test
    void subscribedClassCount_reflectsRegistrations() {
        CoreEventDispatcher dispatcher = new CoreEventDispatcher();
        assertEquals(0, dispatcher.subscribedClassCount());
        dispatcher.subscribe(IOperationLifecycleEvent.Created.class, e -> { });
        dispatcher.subscribe(IOperationLifecycleEvent.Started.class, e -> { });
        assertEquals(2, dispatcher.subscribedClassCount());
    }

    @Test
    void unsubscribe_removesOnlyThatListener() {
        CoreEventDispatcher dispatcher = new CoreEventDispatcher();
        AtomicInteger kept = new AtomicInteger();
        AtomicInteger dropped = new AtomicInteger();
        java.util.function.Consumer<IOperationLifecycleEvent.Created> keep = e -> kept.incrementAndGet();
        java.util.function.Consumer<IOperationLifecycleEvent.Created> drop = e -> dropped.incrementAndGet();
        dispatcher.subscribe(IOperationLifecycleEvent.Created.class, keep);
        dispatcher.subscribe(IOperationLifecycleEvent.Created.class, drop);

        dispatcher.unsubscribe(IOperationLifecycleEvent.Created.class, drop);
        dispatcher.post(created());

        assertEquals(1, kept.get());
        assertEquals(0, dropped.get());
        assertEquals(1, dispatcher.subscribedClassCount());
    }

    @Test
    void unsubscribe_lastListenerDropsTheClass() {
        CoreEventDispatcher dispatcher = new CoreEventDispatcher();
        java.util.function.Consumer<IOperationLifecycleEvent.Created> only = e -> { };
        dispatcher.subscribe(IOperationLifecycleEvent.Created.class, only);
        dispatcher.unsubscribe(IOperationLifecycleEvent.Created.class, only);
        assertEquals(0, dispatcher.subscribedClassCount());
        // Unknown listeners and classes are ignored, never an error.
        dispatcher.unsubscribe(IOperationLifecycleEvent.Started.class, e -> { });
    }

    @Test
    void clear_removesAllSubscriptions() {
        CoreEventDispatcher dispatcher = new CoreEventDispatcher();
        AtomicInteger count = new AtomicInteger();
        dispatcher.subscribe(IOperationLifecycleEvent.Created.class,
                e -> count.incrementAndGet());

        dispatcher.clear();
        dispatcher.post(created());

        assertEquals(0, count.get());
        assertEquals(0, dispatcher.subscribedClassCount());
    }

    @Test
    void operationLifecycleEvents_haveCanonicalEventIds() {
        assertEquals("operation.lifecycle.created", created().eventId());
        assertEquals("operation.lifecycle.started", started().eventId());
        assertEquals("operation.lifecycle.completed",
                new IOperationLifecycleEvent.Completed(net(), op(), TYPE, 100L).eventId());
        assertEquals("operation.lifecycle.failed",
                new IOperationLifecycleEvent.Failed(net(), op(), TYPE, "err").eventId());
        assertEquals("operation.lifecycle.discarded",
                new IOperationLifecycleEvent.Discarded(net(), op(), TYPE).eventId());
    }

    @Test
    void operationLifecycleEvents_carryTheOperationAndItsType() {
        final UUID id = op();
        final IOperationLifecycleEvent.Completed event =
                new IOperationLifecycleEvent.Completed(net(), id, "jsc:craft", 40L);
        assertEquals(id, event.operationId());
        assertEquals("jsc:craft", event.typeId());
        assertEquals(40L, event.durationTicks());
    }

    @Test
    void networkPropagatingEvent_hasCanonicalEventId() {
        assertEquals("network.propagated",
                new NetworkPropagatingEvent(net(), 100L, 200L).eventId());
    }

    @Test
    void completed_negativeDuration_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new IOperationLifecycleEvent.Completed(net(), op(), TYPE, -1L));
    }

    @Test
    void failed_nullReason_throws() {
        assertThrows(NullPointerException.class, () ->
                new IOperationLifecycleEvent.Failed(net(), op(), TYPE, null));
    }

    @Test
    void created_nullTypeOrId_throws() {
        assertThrows(NullPointerException.class, () ->
                new IOperationLifecycleEvent.Created(net(), null, TYPE));
        assertThrows(NullPointerException.class, () ->
                new IOperationLifecycleEvent.Created(net(), op(), null));
    }

    @Test
    void post_deliversToAncestorInterfaceSubscriber() {
        CoreEventDispatcher dispatcher = new CoreEventDispatcher();
        AtomicInteger count = new AtomicInteger();
        // ICoreEvent is a SUPERinterface of the event's direct interface (IOperationLifecycleEvent), so it
        // is only reached once the whole interface graph is walked, not just the direct interfaces.
        dispatcher.subscribe(ICoreEvent.class, e -> count.incrementAndGet());

        dispatcher.post(created());

        assertEquals(1, count.get());
    }

    @Test
    void post_allowsAListenerToMutateSubscriptionsMidDispatch() {
        CoreEventDispatcher dispatcher = new CoreEventDispatcher();
        AtomicInteger count = new AtomicInteger();
        dispatcher.subscribe(IOperationLifecycleEvent.Created.class, e -> {
            count.incrementAndGet();
            // Subscribing and clearing during dispatch must not throw ConcurrentModificationException.
            dispatcher.subscribe(IOperationLifecycleEvent.Started.class, x -> { });
            dispatcher.clear();
        });

        dispatcher.post(created());

        assertEquals(1, count.get());
    }
}
