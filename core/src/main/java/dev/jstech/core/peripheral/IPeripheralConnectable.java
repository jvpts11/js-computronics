/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.peripheral;

/**
 * Marker for a block that a peripheral cable should visually connect to, either a computer (owner) or a peripheral device (endpoint).
 */
public interface IPeripheralConnectable {

    PeripheralCableType peripheralType();
}
