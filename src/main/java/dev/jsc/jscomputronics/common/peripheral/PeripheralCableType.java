/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.peripheral;

public enum PeripheralCableType {

    COMPUTING(16, "peripheral_cable"),
    TELEMETRY(256, "telemetry_cable"),
    INDUSTRIAL_CONTROL(8, "industrial_peripheral_cable");

    private final int maxLength;
    private final String translationKey;

    PeripheralCableType(final int maxLength, final String translationKey) {
        this.maxLength = maxLength;
        this.translationKey = translationKey;
    }

    public int maxLength() {
        return maxLength;
    }

    public String translationKey() {
        return translationKey;
    }
}
