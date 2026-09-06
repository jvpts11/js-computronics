/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.event;

/**
 * Marker for all custom events fired by the mod.
 */
public interface CoreEvent {

    String eventId();

    /**
     * Marker for events whose listeners can prevent the action that fired them.
     */
    interface Cancellable extends CoreEvent {

        boolean isCancelled();

        void cancel();
    }
}
