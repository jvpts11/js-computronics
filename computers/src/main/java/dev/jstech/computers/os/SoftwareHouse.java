/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Who wrote a piece of software: the name a banner, a tooltip or a disc's README prints, and the legal form
 * a copyright line uses. The machines already come from named makers; the systems, desktops and programs
 * name theirs the same way, so a Linux distribution is not credited to the house that writes Frames.
 *
 * <p>A record rather than an enum so an add-on can bring its own house. {@link #BUNDLED} is the one that
 * is not a house: a program that ships with a system or a desktop is credited to whoever ships it, which
 * only the place showing it knows (see {@link DesktopEnvironmentDef#houseOf}).
 *
 * @param name      the short name, as printed beside a system or a program
 * @param legalName the form a copyright line prints, with the company suffix where the house has one
 */
public record SoftwareHouse(String name, String legalName) {

    public static final Codec<SoftwareHouse> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("name").forGetter(SoftwareHouse::name),
            Codec.STRING.optionalFieldOf("legal_name", "").forGetter(SoftwareHouse::legalName)
    ).apply(inst, SoftwareHouse::new));

    /** The systems' house: MC-DOS, the Frames editions and their own tools. */
    public static final SoftwareHouse MIDSOFT = new SoftwareHouse("Midsoft", "Midsoft Corp.");
    /** The network operating system of the Vintage era. */
    public static final SoftwareHouse NOUVELL = new SoftwareHouse("Nouvell Networks", "Nouvell Networks Inc.");
    /** Ubuntu's house. */
    public static final SoftwareHouse AXIOMATIC = new SoftwareHouse("Axiomatic Ltd.", "Axiomatic Ltd.");
    /** Fedora's house, and the automation stack's. */
    public static final SoftwareHouse RED_CAP = new SoftwareHouse("Red Cap", "Red Cap Inc.");
    public static final SoftwareHouse DEBIAN_CIRCLE = new SoftwareHouse("Debian Circle", "the Debian Circle");
    public static final SoftwareHouse ARCH_COLLECTIVE = new SoftwareHouse("Arch Collective", "the Arch Collective");
    public static final SoftwareHouse GENTOO_FOUNDRY = new SoftwareHouse("Gentoo Foundry", "the Gentoo Foundry");
    public static final SoftwareHouse KDE_GUILD = new SoftwareHouse("KDE Guild", "the KDE Guild");
    public static final SoftwareHouse GNOME_TRUST = new SoftwareHouse("GNOME Trust", "the GNOME Trust");
    /** Cinnamon's house. */
    public static final SoftwareHouse SPEARMINT = new SoftwareHouse("Spearmint Linux", "Spearmint Linux");
    /** The crafting design tools: Crafting Manager, Craft Planner and the Pattern Studio. */
    public static final SoftwareHouse AUTODECK = new SoftwareHouse("Autodeck", "Autodeck Inc.");
    /** The drive maker's software: Storage Insights and the server storage services. */
    public static final SoftwareHouse VAULTIS = new SoftwareHouse("Vaultis", "Vaultis Storage Inc.");
    /** The hardware house's own network tools. */
    public static final SoftwareHouse JSC = new SoftwareHouse(Branding.HARDWARE_HOUSE, Branding.HARDWARE_HOUSE);
    /** The open house behind the Cannon compiler and its runtime, owned by nobody who sells hardware. */
    public static final SoftwareHouse CANNON_FOUNDATION =
            new SoftwareHouse("Cannon Foundation", "the Cannon Foundation");
    /** The house behind Exposure, the editor that shows you every complaint at once. */
    public static final SoftwareHouse DAYLIGHT_FOUNDATION =
            new SoftwareHouse("Daylight Foundation", "the Daylight Foundation");

    /** Not a house: the program is credited to the system or desktop that ships it. */
    public static final SoftwareHouse BUNDLED = new SoftwareHouse("", "");

    public SoftwareHouse {
        if (name == null) {
            name = "";
        }
        if (legalName == null || legalName.isBlank()) {
            legalName = name;
        }
    }

    /** Whether this stands for "whoever ships it" rather than a house of its own. */
    public boolean bundled() {
        return name.isEmpty();
    }

    /** This house, or {@code fallback} when this is {@link #BUNDLED}. */
    public SoftwareHouse or(final SoftwareHouse fallback) {
        return bundled() ? fallback : this;
    }
}
