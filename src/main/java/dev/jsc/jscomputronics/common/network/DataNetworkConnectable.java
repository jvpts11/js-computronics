/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.network;

import java.util.Set;

/**
 * Marker implemented by blocks that a data cable connects to — network devices such as the Mainframe, routers and racks.
 */
public interface DataNetworkConnectable {

    default Set<DataTier> acceptedCableTiers() {
        return Set.of(DataTier.values());
    }
}
