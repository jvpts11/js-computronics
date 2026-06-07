/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.peripheral;

/**
 * Marker for a block that a peripheral cable should visually connect to — a computer (owner) or a peripheral device (endpoint).
 */
public interface PeripheralConnectable {

    PeripheralCableType peripheralType();
}
