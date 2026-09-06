/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os;

/**
 * The capability tier of an operating system — what it can do for the player.
 *
 * <p>Ordered from least to most capable; a higher ordinal implies every capability below it.
 * Use ordinal comparison for gating: an OS can run a program whose {@code minCapability} ordinal is
 * less than or equal to this value.
 */
public enum OsCapability {
    /** CLI terminal only — no graphical applications. Used by MC-DOS. */
    TERMINAL_ONLY,
    /** Full-screen network GUI (the Network Interactor) plus a terminal, but no application desktop. */
    NETWORK_GUI,
    /** A graphical desktop with a window manager, filesystem, and application runtime. */
    FULL_DESKTOP
}
