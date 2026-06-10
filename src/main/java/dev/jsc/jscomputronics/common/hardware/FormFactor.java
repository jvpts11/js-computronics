/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.hardware;

/**
 * The physical form factor of a motherboard — the chassis standard that decides which computer a board fits into, the way a real board's size and mounting decide which case accepts it.
 */
public enum FormFactor {

    ATX("ATX"),

    EEB("EEB"),

    MTX("MTX");

    private final String label;

    FormFactor(final String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
