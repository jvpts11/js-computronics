/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Pure-Java event dispatcher used in Phase 0 and as the abstraction layer for Phase 1+ NeoForge integration.
 */
public class JscEventDispatcher {

    private final Map<Class<? extends JscEvent>, List<Consumer<? extends JscEvent>>>
            listenersByClass = new HashMap<>();

    public <E extends JscEvent> void subscribe(
            final Class<E> eventClass,
            final Consumer<E> listener) {
        Objects.requireNonNull(eventClass, "eventClass must not be null");
        Objects.requireNonNull(listener, "listener must not be null");
        listenersByClass
                .computeIfAbsent(eventClass, k -> new ArrayList<>())
                .add(listener);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <E extends JscEvent> E post(final E event) {
        Objects.requireNonNull(event, "event must not be null");

        // Walk up the class hierarchy collecting listeners. This covers
        // listeners subscribed to a parent class or implemented interface.
        Class<?> current = event.getClass();
        while (current != null && JscEvent.class.isAssignableFrom(current)) {
            final List<Consumer<? extends JscEvent>> listeners =
                    listenersByClass.get(current);
            if (listeners != null) {
                for (final Consumer listener : listeners) {
                    if (event instanceof JscEvent.Cancellable cancellable
                            && cancellable.isCancelled()) {
                        return event;
                    }
                    listener.accept(event);
                }
            }
            current = current.getSuperclass();
        }

        // Also walk implemented interfaces for sealed hierarchies.
        for (final Class<?> iface : event.getClass().getInterfaces()) {
            if (JscEvent.class.isAssignableFrom(iface)) {
                final List<Consumer<? extends JscEvent>> listeners =
                        listenersByClass.get(iface);
                if (listeners != null) {
                    for (final Consumer listener : listeners) {
                        if (event instanceof JscEvent.Cancellable cancellable
                                && cancellable.isCancelled()) {
                            return event;
                        }
                        listener.accept(event);
                    }
                }
            }
        }

        return event;
    }

    public void clear() {
        listenersByClass.clear();
    }

    public int subscribedClassCount() {
        return listenersByClass.size();
    }
}
