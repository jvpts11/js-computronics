/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.peripheral;

import java.util.Optional;

/**
 * Contract for a BlockEntity that is the LINKED endpoint of a peripheral connection — the "client" side of the link.
 */
public interface PeripheralEndpoint {

    PeripheralCableType cableType();

    Optional<Long> linkedOwner();

    void onOwnerLinked(long ownerPos);

    void onOwnerUnlinked();
}
