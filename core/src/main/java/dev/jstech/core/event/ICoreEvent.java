/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.event;

/**
 * Marker for all custom events fired by the mod.
 */
public interface ICoreEvent {

    String eventId();

    /**
     * Marker for events whose listeners can prevent the action that fired them.
     */
    interface ICancellable extends ICoreEvent {

        boolean isCancelled();

        void cancel();
    }
}
