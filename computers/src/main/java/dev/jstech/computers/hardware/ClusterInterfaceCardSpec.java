/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.hardware;

import dev.jstech.core.tier.HardwareEra;
import dev.jstech.core.tier.IndustrialTier;

import java.util.Objects;

/**
 * The card that lets a Cluster Management Computer address the racks on its network. It follows the
 * pattern of the other computer-exclusive interface cards (the VLDC plate of the Gateway, the
 * Simulation Interface of a simulation node): without it the machine is an ordinary computer. Each
 * era's card reaches further and drives more nodes at once: a serial console card talks to rack
 * servers one at a time, a management NIC reaches supercomputer fabrics too, a fabric host adapter
 * reaches everything and installs several nodes in parallel. Numbers are estimates, tuned in play.
 *
 * @param era           the era the card belongs to (the tooltip's era line; the bus decides compatibility)
 * @param tier          the industrial tier the card is fabricated at
 * @param bus           the expansion bus the card plugs into
 * @param reach         which cabinet kinds the card can drive
 * @param parallelNodes how many nodes a bulk install writes at the same time
 * @param tdpWatts      the card's power draw
 */
public record ClusterInterfaceCardSpec(HardwareEra era, IndustrialTier tier, PcieGeneration bus, Reach reach,
                                       int parallelNodes, int tdpWatts) implements IExpansionCardSpec {

    /** How far a card reaches, in cabinet kinds. Each level includes the ones before it. */
    public enum Reach {
        /** Rack servers only: datacenter sections. */
        DATACENTERS,
        /** Datacenters and supercomputer fabrics. */
        SUPERCOMPUTERS,
        /** Everything, AI racks included. */
        ALL;

        /** Whether this reach covers a cabinet of the given kind. */
        public boolean covers(final dev.jstech.computers.rack.RackChassis.RackType kind) {
            return switch (kind) {
                case SERVER -> true;
                case SUPERCOMPUTER -> this != DATACENTERS;
                case AI -> this == ALL;
            };
        }
    }

    public ClusterInterfaceCardSpec {
        Objects.requireNonNull(era, "era must not be null");
        Objects.requireNonNull(tier, "tier must not be null");
        Objects.requireNonNull(bus, "bus must not be null");
        Objects.requireNonNull(reach, "reach must not be null");
        if (parallelNodes < 1) {
            throw new IllegalArgumentException("parallelNodes must be >= 1; got " + parallelNodes);
        }
        if (tdpWatts < 0) {
            throw new IllegalArgumentException("tdpWatts must be >= 0; got " + tdpWatts);
        }
    }

    @Override
    public ExpansionCardKind kind() {
        return ExpansionCardKind.CLUSTER_INTERFACE;
    }
}
