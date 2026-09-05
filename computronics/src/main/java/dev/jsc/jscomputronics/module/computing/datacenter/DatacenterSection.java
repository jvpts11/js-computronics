/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.datacenter;

import dev.jstech.core.uuid.NodeUuid;
import net.minecraft.core.Direction;

import java.util.List;
import java.util.Set;

/**
 * One datacenter section: the Server Racks (and the Servers inside them) reachable through a single output face of a Server Router, without crossing back through the router.
 */
public record DatacenterSection(
        Direction face,
        Set<Long> rackPositions,
        List<NodeUuid> servers,
        long totalStorageItems
) {

    public int rackCount() {
        return rackPositions.size();
    }

    public int serverCount() {
        return servers.size();
    }
}
