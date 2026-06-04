/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.event;

/**
 * Marker for all custom events fired by the mod.
 */
public interface JscEvent {

    String eventId();

    /**
     * Marker for events whose listeners can prevent the action that fired them.
     */
    interface Cancellable extends JscEvent {

        boolean isCancelled();

        void cancel();
    }
}
