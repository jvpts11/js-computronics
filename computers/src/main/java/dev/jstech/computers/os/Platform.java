/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os;

/**
 * The OS platform (family) a computer runs, and the axis a program is gated on.
 *
 * <p>A program declares the set of platforms it supports; an OS declares the single platform it is.
 * A program installs and runs only on an OS whose platform is in that set. This is independent of the
 * hardware era: it is about binary/API compatibility (a program built for one platform family does not
 * run on another), not about how advanced the hardware is.
 *
 * <p>The three current platforms are all Windows-family surfaces. Future Linux-family operating systems
 * will add their own platform values, at which point cross-platform programs will list several.
 */
public enum Platform {

    /** The MC-DOS command-line platform. */
    MC_DOS("MC-DOS"),

    /** The MC-NET single-screen network platform. */
    MC_NET("MC-NET"),

    /** The Frames graphical desktop platform (Frames 95 / XP / 11). */
    FRAMES("Frames"),

    /** The Linux platform: every distribution on the Linux kernel (TTY, or a desktop environment on top). */
    LINUX("Linux");

    private final String label;

    Platform(final String label) {
        this.label = label;
    }

    /** A human-readable name for tooltips and UI. */
    public String label() {
        return label;
    }
}
