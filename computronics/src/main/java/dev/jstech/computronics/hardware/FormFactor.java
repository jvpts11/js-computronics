/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.hardware;

/**
 * The physical form factor of a motherboard, the chassis standard that decides which computer a board fits into, the way a real board's size and mounting decide which case accepts it.
 */
public enum FormFactor {

    BABY_AT("Baby-AT"),

    AT("AT"),

    ATX("ATX"),

    EATX("EATX"),

    EEB("EEB"),

    MTX("MTX"),

    SOCKET_Q("Socket Q"),

    /**
     * Mining motherboard: a specialized board with one consumer socket and many expansion slots,
     * built for mining computers rather than general computing.
     */
    MNG("MNG"),

    /**
     * Simulation motherboard: a specialized board for simulation-cluster nodes, with no conventional
     * CPU or RAM slots of its own.
     */
    SIM("SIM");

    private final String label;

    FormFactor(final String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
