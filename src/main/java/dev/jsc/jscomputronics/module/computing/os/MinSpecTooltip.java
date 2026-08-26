/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os;

import dev.jsc.jscomputronics.common.tier.HardwareEra;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the minimum-requirement tooltip lines for an OS or a program from its registered
 * {@link OsDef} / {@link ProgramDef}. Used by install-media tooltips and the This PC app so the player
 * sees what hardware and OS something needs before installing it. Only common types are referenced, so
 * this is safe to call from item tooltips (which run client-side) without a client/server boundary.
 */
public final class MinSpecTooltip {

    private MinSpecTooltip() {
    }

    /** The minimum-spec lines for an OS installer: the hardware era it needs and the disk it occupies. */
    public static List<Component> osMinSpec(final ResourceLocation osId) {
        final List<Component> lines = new ArrayList<>(2);
        final OsDef os = osId == null ? null : OsRegistry.getOs(osId);
        if (os == null) {
            return lines;
        }
        lines.add(line("Needs " + eraLabel(os.minEra()) + " hardware or later"));
        lines.add(line("Disk footprint: " + (os.footprintItems() * 4L) + " MB"));
        return lines;
    }

    /** The minimum-spec lines for a program: the OS capability tier and OS era it requires. */
    public static List<Component> programMinSpec(final ResourceLocation progId) {
        final List<Component> lines = new ArrayList<>(2);
        final ProgramDef prog = progId == null ? null : OsRegistry.getProgram(progId);
        if (prog == null) {
            return lines;
        }
        lines.add(line("Needs a " + capabilityLabel(prog.minCapability()) + " OS"));
        lines.add(line("Min OS era: " + eraLabel(prog.minOsEra())));
        return lines;
    }

    private static Component line(final String text) {
        return Component.literal(text).withStyle(ChatFormatting.DARK_GRAY);
    }

    /** A readable name for a hardware era. */
    public static String eraLabel(final HardwareEra era) {
        return switch (era) {
            case VINTAGE -> "Vintage";
            case LEGACY -> "Legacy";
            case STANDARD -> "Standard";
            case ADVANCED -> "Advanced";
            case EXA -> "Exa";
            case SINGULARITY -> "Singularity";
        };
    }

    /** A readable name for an OS capability tier. */
    public static String capabilityLabel(final OsCapability capability) {
        return switch (capability) {
            case TERMINAL_ONLY -> "Terminal-only";
            case NETWORK_GUI -> "Network GUI";
            case FULL_DESKTOP -> "Full Desktop";
        };
    }
}
