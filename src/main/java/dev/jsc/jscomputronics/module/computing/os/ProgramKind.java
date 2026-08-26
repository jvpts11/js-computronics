/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os;

/**
 * Classification of a program by how it runs and what OS capability tier it requires.
 *
 * <p>{@link #APP} programs require {@link OsCapability#FULL_DESKTOP} because they open foreground
 * windows. {@link #SERVICE} programs run headlessly and are therefore usable on any capability
 * tier. {@link #HYBRID} programs have headless logic on any tier but expose a graphical panel only
 * when a full desktop is available.
 */
public enum ProgramKind {
    /** Foreground graphical application; requires {@link OsCapability#FULL_DESKTOP}. */
    APP,
    /** Headless background service; runs on any capability tier. */
    SERVICE,
    /** Headless core on any tier, optional graphical panel on {@link OsCapability#FULL_DESKTOP}. */
    HYBRID
}
