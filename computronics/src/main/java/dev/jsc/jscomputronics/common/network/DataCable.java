/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.network;

/**
 * A data network cable: carries items per tick between nodes.
 */
public interface DataCable {

    DataTier tier();

    default long maxThroughput() {
        return tier().maxThroughput();
    }

    default int maxLength() {
        return tier().maxLength();
    }
}
