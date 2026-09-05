/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.datacenter;

/**
 * How a Server Router spreads Operations across the Servers of one output-face section.
 */
public enum LoadBalanceMode {
    ROUND_ROBIN,
    LEAST_LOADED,
    MANUAL;

    public LoadBalanceMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
