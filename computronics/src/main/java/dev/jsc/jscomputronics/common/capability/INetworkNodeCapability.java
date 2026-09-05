/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.capability;

import dev.jsc.jscomputronics.common.network.NetworkCategory;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;

import java.util.Optional;

/**
 * Contract for any block that participates in a J's Computronics computation network as a {@link NetworkCategory#B Category B} or {@link NetworkCategory#C Category C} node.
 */
public interface INetworkNodeCapability {

    NodeUuid getNodeUuid();

    Optional<NetworkUuid> getNetworkUuid();

    NetworkCategory getCategory();
}
