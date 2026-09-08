/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os;

import dev.jstech.core.tier.HardwareEra;

import java.util.Map;

/**
 * Who made a machine or a system, and when.
 *
 * <p>Every firmware banner and system greeting used to carry the same present-day line, so a machine
 * built from parts three decades old booted claiming a copyright from this year. A banner is one of the
 * few places the fiction states its own age out loud, so it says the year that machine's era belongs to
 * and names the house that wrote its software, which each system carries as its {@link SoftwareHouse}.
 *
 * <p>Pure text and numbers, no rendering, so a screen on either firmware can share it.
 */
public final class Branding {

    /** The hardware house: the machines, their boards, and the firmware that posts them. */
    public static final String HARDWARE_HOUSE = "JSC Technologies";

    /**
     * The year a system was released, for the ones whose names say it, keyed by the name the system prints
     * of itself. Everything else falls back to its era's year, which is what a banner needs: a decade, not
     * a date.
     */
    private static final Map<String, Integer> RELEASE_YEARS = Map.of(
            "MC-DOS", 1987,
            "MC-NET", 1992,
            "Frames 95", 1995,
            "Frames XP", 2001,
            "Frames 11", 2021);

    private Branding() {
    }

    /** The year the machines of an era were built, for a firmware banner. */
    public static int year(final HardwareEra era) {
        if (era == null) {
            return 2026;
        }
        return switch (era) {
            case VINTAGE -> 1987;
            case LEGACY -> 1998;
            case STANDARD -> 2026;
            case ADVANCED -> 2044;
            case EXA -> 2071;
            case SINGULARITY -> 2099;
        };
    }

    /** The year a system shipped: its own, where its name says so, else its era's. */
    public static int osYear(final String osName, final HardwareEra era) {
        final Integer own = RELEASE_YEARS.get(osName == null ? "" : osName.trim());
        return own != null ? own : year(era);
    }

    /** The firmware version an era's machines carry, so a 1987 board does not claim a modern BIOS. */
    public static String biosVersion(final HardwareEra era) {
        if (era == null) {
            return "4.06";
        }
        return switch (era) {
            case VINTAGE -> "1.02";
            case LEGACY -> "2.41";
            default -> "4.06";
        };
    }

    /** The firmware banner's first line: who built the board and which firmware it runs. */
    public static String biosBanner(final HardwareEra era) {
        return HARDWARE_HOUSE + " BIOS  v" + biosVersion(era);
    }

    /** The copyright line under a firmware banner. */
    public static String firmwareCopyright(final HardwareEra era) {
        return "Copyright (C) " + year(era) + " " + HARDWARE_HOUSE;
    }

    /**
     * The house of the system that prints itself as {@code osName}. Screens carry the system's name, not its
     * definition, so the lookup goes by the name the registry knows; an unknown name falls back to the house
     * that writes the Frames line, which is what every banner said before systems named their makers.
     */
    public static SoftwareHouse houseOf(final String osName) {
        final String wanted = osName == null ? "" : osName.trim();
        for (final OsDef os : OsRegistry.oses()) {
            if (os.displayName().equals(wanted)) {
                return os.house();
            }
        }
        return SoftwareHouse.MIDSOFT;
    }

    /** The copyright line a system prints under its own name. */
    public static String systemCopyright(final String osName, final HardwareEra era) {
        return "(C) " + osYear(osName, era) + " " + houseOf(osName).legalName();
    }
}
