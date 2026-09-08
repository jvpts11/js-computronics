/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.client.gui.hud;

/**
 * Bridge that displays a {@link ToastData} via the vanilla toast system.
 */
public final class ToastNotification {

    private ToastNotification() {
    }

    public static void show(final ToastData data) {
        // Phase 1+: build a vanilla Toast from `data` (title, description,
        throw new UnsupportedOperationException(
                "ToastNotification.show is implemented in a later phase.");
    }
}