/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.peripheral;

import java.util.List;

/**
 * Contract for a BlockEntity that OWNS a set of peripheral endpoints via cables of one peripheral system.
 */
public interface PeripheralOwner {

    PeripheralCableType cableType();

    List<Long> linkedEndpoints();

    int maxEndpoints();

    void onEndpointLinked(long endpointPos);

    void onEndpointUnlinked(long endpointPos);
}
