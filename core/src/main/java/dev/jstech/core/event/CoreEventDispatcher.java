/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Pure-Java event dispatcher used in Phase 0 and as the abstraction layer for Phase 1+ NeoForge integration.
 */
public class CoreEventDispatcher {

    private final Map<Class<? extends ICoreEvent>, List<Consumer<? extends ICoreEvent>>>
            listenersByClass = new HashMap<>();

    public <E extends ICoreEvent> void subscribe(
            final Class<E> eventClass,
            final Consumer<E> listener) {
        Objects.requireNonNull(eventClass, "eventClass must not be null");
        Objects.requireNonNull(listener, "listener must not be null");
        listenersByClass
                .computeIfAbsent(eventClass, k -> new ArrayList<>())
                .add(listener);
    }

    /**
     * Removes one subscription, matched by identity on the listener; a listener that was never subscribed
     * for that class is ignored. A class left with no listeners is dropped from the count.
     */
    public <E extends ICoreEvent> void unsubscribe(
            final Class<E> eventClass,
            final Consumer<E> listener) {
        Objects.requireNonNull(eventClass, "eventClass must not be null");
        Objects.requireNonNull(listener, "listener must not be null");
        final List<Consumer<? extends ICoreEvent>> listeners = listenersByClass.get(eventClass);
        if (listeners == null) {
            return;
        }
        listeners.removeIf(existing -> existing == listener);
        if (listeners.isEmpty()) {
            listenersByClass.remove(eventClass);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <E extends ICoreEvent> E post(final E event) {
        Objects.requireNonNull(event, "event must not be null");

        // Every ICoreEvent type this event is assignable to: its whole class chain AND its whole interface
        // graph (superinterfaces included), de-duplicated and most-specific first. Collecting the full
        // graph — not just the direct interfaces — is what lets a listener on an ancestor interface
        // (e.g. ICoreEvent itself) be reached.
        final java.util.Set<Class<?>> types = new java.util.LinkedHashSet<>();
        collectEventTypes(event.getClass(), types);

        for (final Class<?> type : types) {
            final List<Consumer<? extends ICoreEvent>> listeners = listenersByClass.get(type);
            if (listeners == null) {
                continue;
            }
            // Iterate a snapshot so a listener may subscribe or clear during dispatch without a CME.
            for (final Consumer listener : new ArrayList<>(listeners)) {
                if (event instanceof ICoreEvent.ICancellable cancellable && cancellable.isCancelled()) {
                    return event;
                }
                listener.accept(event);
            }
        }
        return event;
    }

    private static void collectEventTypes(final Class<?> type, final java.util.Set<Class<?>> out) {
        if (type == null || !ICoreEvent.class.isAssignableFrom(type) || !out.add(type)) {
            return;
        }
        collectEventTypes(type.getSuperclass(), out);
        for (final Class<?> iface : type.getInterfaces()) {
            collectEventTypes(iface, out);
        }
    }

    public void clear() {
        listenersByClass.clear();
    }

    public int subscribedClassCount() {
        return listenersByClass.size();
    }
}
