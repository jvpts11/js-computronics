/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.network;

/**
 * The role a Mainframe plays in a Failover redundancy pair.
 */
public enum FailoverRole {

    NONE,

    ACTIVE,

    PASSIVE;

    public boolean isPaired() {
        return this == ACTIVE || this == PASSIVE;
    }
}
