/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.rack;

import dev.jstech.core.tier.HardwareEra;

/**
 * The chassis a rack-mounted computer is built in: how many rack units it spends, what the rack's
 * front bays can hold for it, how much hardware fits inside, which cabinet seats it, and the era it
 * belongs to. The era is a hard gate like on every other computer: the case takes only boards of its
 * own era, and a cabinet seats servers of its own era or earlier. The hardware inside still decides the
 * speed. Numbers are estimates, tuned in play.
 */
public enum RackChassis {
    /** The Standard 1U server. */
    SERVER(1, 3, 1, 2, Integer.MAX_VALUE, RackType.SERVER, HardwareEra.STANDARD),
    /** The Legacy 1U server: fewer drives, one expansion slot (the card that lets a player sit at it). */
    LEGACY_SERVER(1, 2, 1, 2, 1, RackType.SERVER, HardwareEra.LEGACY),
    /** The Vintage 1U server: a single drive, no bay gadgets, one socket, one expansion slot. */
    VINTAGE_SERVER(1, 1, 0, 1, 1, RackType.SERVER, HardwareEra.VINTAGE),
    /** 2U, eight drive bays, storage first. */
    STORAGE_SERVER(2, 8, 2, 1, 1, RackType.SERVER, HardwareEra.STANDARD),
    /** 2U, four sockets, compute first. */
    COMPUTE_SERVER(2, 1, 1, 4, Integer.MAX_VALUE, RackType.SERVER, HardwareEra.STANDARD),
    /** 2U node of a supercomputer: one Phi and one GPU, seated only in a Supercomputer Rack. */
    SUPERCOMPUTER_NODE(2, 1, 1, 2, 2, RackType.SUPERCOMPUTER, HardwareEra.STANDARD);

    /** The kind of cabinet a chassis seats in. */
    public enum RackType {
        SERVER,
        SUPERCOMPUTER,
        AI
    }

    private final int heightU;
    private final int driveSlots;
    private final int gadgetSlots;
    private final int maxCpus;
    private final int maxPcie;
    private final RackType rackType;
    private final HardwareEra era;

    RackChassis(final int heightU, final int driveSlots, final int gadgetSlots, final int maxCpus,
                final int maxPcie, final RackType rackType, final HardwareEra era) {
        this.heightU = heightU;
        this.driveSlots = driveSlots;
        this.gadgetSlots = gadgetSlots;
        this.maxCpus = maxCpus;
        this.maxPcie = maxPcie;
        this.rackType = rackType;
        this.era = era;
    }

    public RackType rackType() {
        return rackType;
    }

    /** The era of the case: the boards it takes and the cabinets that seat it follow from this. */
    public HardwareEra era() {
        return era;
    }

    public int heightU() {
        return heightU;
    }

    public int driveSlots() {
        return driveSlots;
    }

    public int gadgetSlots() {
        return gadgetSlots;
    }

    public int maxCpus() {
        return maxCpus;
    }

    public int maxPcie() {
        return maxPcie;
    }
}
