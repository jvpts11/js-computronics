/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.client.gui.hud;

import java.util.Objects;

/**
 * Immutable description of a mod toast notification (Operation complete, attack detected, robot stuck, etc.).
 */
public record ToastData(String titleKey, String descriptionKey, Severity severity) {

    /**
     * Severity levels, ordered from least to most urgent.
     */
    public enum Severity {
        INFO,
        SUCCESS,
        WARNING,
        CRITICAL
    }

    public ToastData {
        Objects.requireNonNull(titleKey, "titleKey must not be null");
        Objects.requireNonNull(descriptionKey, "descriptionKey must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        if (titleKey.isBlank()) {
            throw new IllegalArgumentException("titleKey must not be blank");
        }
    }

    public static ToastData of(final String titleKey, final Severity severity) {
        return new ToastData(titleKey, "", severity);
    }

    public boolean hasDescription() {
        return !descriptionKey.isBlank();
    }

    public long defaultDurationMillis() {
        return switch (severity) {
            case INFO -> 3000L;
            case SUCCESS -> 3000L;
            case WARNING -> 5000L;
            case CRITICAL -> 8000L;
        };
    }

    public int accentColor() {
        return switch (severity) {
            case INFO -> 0xFF4A90D9;     // blue
            case SUCCESS -> 0xFF5CB85C;  // green
            case WARNING -> 0xFFF0AD4E;  // amber
            case CRITICAL -> 0xFFD9534F; // red
        };
    }
}