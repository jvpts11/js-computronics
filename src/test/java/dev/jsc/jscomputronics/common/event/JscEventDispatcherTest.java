/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.event;

import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JscEventDispatcherTest {

    private static NetworkUuid net() {
        return new NetworkUuid(UUID.randomUUID());
    }

    @Test
    void singleListener_receivesEvent() {
        JscEventDispatcher dispatcher = new JscEventDispatcher();
        List<OperationLifecycleEvent.Created> received = new ArrayList<>();
        dispatcher.subscribe(OperationLifecycleEvent.Created.class, received::add);

        OperationLifecycleEvent.Created event =
                new OperationLifecycleEvent.Created(net(), 42L);
        dispatcher.post(event);

        assertEquals(1, received.size());
        assertEquals(event, received.get(0));
    }

    @Test
    void noListeners_postIsNoOp() {
        JscEventDispatcher dispatcher = new JscEventDispatcher();
        OperationLifecycleEvent.Created event =
                new OperationLifecycleEvent.Created(net(), 1L);
        // Must not throw; just returns.
        OperationLifecycleEvent.Created result = dispatcher.post(event);
        assertEquals(event, result);
    }

    @Test
    void postReturnsEvent_forFluentChaining() {
        JscEventDispatcher dispatcher = new JscEventDispatcher();
        OperationLifecycleEvent.Created event =
                new OperationLifecycleEvent.Created(net(), 1L);
        assertEquals(event, dispatcher.post(event));
    }

    @Test
    void multipleListeners_invokedInRegistrationOrder() {
        JscEventDispatcher dispatcher = new JscEventDispatcher();
        List<Integer> order = new ArrayList<>();
        dispatcher.subscribe(OperationLifecycleEvent.Started.class,
                e -> order.add(1));
        dispatcher.subscribe(OperationLifecycleEvent.Started.class,
                e -> order.add(2));
        dispatcher.subscribe(OperationLifecycleEvent.Started.class,
                e -> order.add(3));

        dispatcher.post(new OperationLifecycleEvent.Started(net(), 1L));

        assertEquals(List.of(1, 2, 3), order);
    }

    @Test
    void subscribeToSealedRoot_receivesAllChildren() {
        JscEventDispatcher dispatcher = new JscEventDispatcher();
        AtomicInteger count = new AtomicInteger();
        dispatcher.subscribe(OperationLifecycleEvent.class, e -> count.incrementAndGet());

        dispatcher.post(new OperationLifecycleEvent.Created(net(), 1L));
        dispatcher.post(new OperationLifecycleEvent.Started(net(), 1L));
        dispatcher.post(new OperationLifecycleEvent.Completed(net(), 1L, 100L));
        dispatcher.post(new OperationLifecycleEvent.Failed(net(), 1L, "test"));

        assertEquals(4, count.get());
    }

    @Test
    void subscribeToConcreteType_doesNotReceiveSiblings() {
        JscEventDispatcher dispatcher = new JscEventDispatcher();
        AtomicInteger createdCount = new AtomicInteger();
        dispatcher.subscribe(OperationLifecycleEvent.Created.class,
                e -> createdCount.incrementAndGet());

        dispatcher.post(new OperationLifecycleEvent.Created(net(), 1L));
        dispatcher.post(new OperationLifecycleEvent.Started(net(), 1L));

        assertEquals(1, createdCount.get());
    }

    @Test
    void cancelledEvent_skipsLaterListeners() {
        JscEventDispatcher dispatcher = new JscEventDispatcher();
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
        JscEventDispatcher dispatcher = new JscEventDispatcher();
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
        JscEventDispatcher dispatcher = new JscEventDispatcher();
        assertThrows(NullPointerException.class, () ->
                dispatcher.subscribe(null, e -> { }));
    }

    @Test
    void subscribeWithNullListener_throws() {
        JscEventDispatcher dispatcher = new JscEventDispatcher();
        assertThrows(NullPointerException.class, () ->
                dispatcher.subscribe(OperationLifecycleEvent.Created.class, null));
    }

    @Test
    void postNullEvent_throws() {
        JscEventDispatcher dispatcher = new JscEventDispatcher();
        assertThrows(NullPointerException.class, () -> dispatcher.post(null));
    }

    @Test
    void subscribedClassCount_reflectsRegistrations() {
        JscEventDispatcher dispatcher = new JscEventDispatcher();
        assertEquals(0, dispatcher.subscribedClassCount());
        dispatcher.subscribe(OperationLifecycleEvent.Created.class, e -> { });
        dispatcher.subscribe(OperationLifecycleEvent.Started.class, e -> { });
        assertEquals(2, dispatcher.subscribedClassCount());
    }

    @Test
    void clear_removesAllSubscriptions() {
        JscEventDispatcher dispatcher = new JscEventDispatcher();
        AtomicInteger count = new AtomicInteger();
        dispatcher.subscribe(OperationLifecycleEvent.Created.class,
                e -> count.incrementAndGet());

        dispatcher.clear();
        dispatcher.post(new OperationLifecycleEvent.Created(net(), 1L));

        assertEquals(0, count.get());
        assertEquals(0, dispatcher.subscribedClassCount());
    }

    @Test
    void operationLifecycleEvents_haveCanonicalEventIds() {
        assertEquals("operation.lifecycle.created",
                new OperationLifecycleEvent.Created(net(), 1L).eventId());
        assertEquals("operation.lifecycle.started",
                new OperationLifecycleEvent.Started(net(), 1L).eventId());
        assertEquals("operation.lifecycle.completed",
                new OperationLifecycleEvent.Completed(net(), 1L, 100L).eventId());
        assertEquals("operation.lifecycle.failed",
                new OperationLifecycleEvent.Failed(net(), 1L, "err").eventId());
    }

    @Test
    void networkPropagatingEvent_hasCanonicalEventId() {
        assertEquals("network.propagated",
                new NetworkPropagatingEvent(net(), 100L, 200L).eventId());
    }

    @Test
    void completed_negativeDuration_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new OperationLifecycleEvent.Completed(net(), 1L, -1L));
    }

    @Test
    void failed_nullReason_throws() {
        assertThrows(NullPointerException.class, () ->
                new OperationLifecycleEvent.Failed(net(), 1L, null));
    }

    @Test
    void post_deliversToAncestorInterfaceSubscriber() {
        JscEventDispatcher dispatcher = new JscEventDispatcher();
        AtomicInteger count = new AtomicInteger();
        // JscEvent is a SUPERinterface of the event's direct interface (OperationLifecycleEvent), so it
        // is only reached once the whole interface graph is walked, not just the direct interfaces.
        dispatcher.subscribe(JscEvent.class, e -> count.incrementAndGet());

        dispatcher.post(new OperationLifecycleEvent.Created(net(), 1L));

        assertEquals(1, count.get());
    }

    @Test
    void post_allowsAListenerToMutateSubscriptionsMidDispatch() {
        JscEventDispatcher dispatcher = new JscEventDispatcher();
        AtomicInteger count = new AtomicInteger();
        dispatcher.subscribe(OperationLifecycleEvent.Created.class, e -> {
            count.incrementAndGet();
            // Subscribing and clearing during dispatch must not throw ConcurrentModificationException.
            dispatcher.subscribe(OperationLifecycleEvent.Started.class, x -> { });
            dispatcher.clear();
        });

        dispatcher.post(new OperationLifecycleEvent.Created(net(), 1L));

        assertEquals(1, count.get());
    }
}
