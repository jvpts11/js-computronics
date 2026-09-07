/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os;

import dev.jstech.core.tier.HardwareEra;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the minimum-requirement tooltip lines for an OS or a program from its registered
 * {@link OsDef} / {@link ProgramSpec}. Used by install-media tooltips and the This PC app so the player
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
        lines.add(needsEra(os.minEra()));
        lines.add(line("Disk footprint: " + os.footprintMb() + " MB"));
        return lines;
    }

    /** "Needs X hardware or later", with the era in the colour every hardware tooltip gives it. */
    private static Component needsEra(final HardwareEra era) {
        return line("Needs ")
                .append(dev.jstech.computronics.item.HardwareTooltip.eraName(era, eraLabel(era)))
                .append(line(" hardware or later"));
    }

    /** The minimum-spec lines for a program: its OS floor (platform + version) and its hardware minimums. */
    public static List<Component> programMinSpec(final ResourceLocation progId) {
        final List<Component> lines = new ArrayList<>(4);
        final ProgramSpec prog = progId == null ? null : OsRegistry.getProgram(progId);
        if (prog == null) {
            return lines;
        }
        /*
         * The OS requirement is the headline the player cares about, so it is highlighted (aqua) after a
         * muted "Requires" label; the raw hardware minimums follow in grey.
         */
        lines.add(Component.literal("Requires ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(minOsLabel(prog)).withStyle(ChatFormatting.AQUA)));
        /*
         * The era floor sits with the hardware minimums because that is what it is: a machine of an
         * older generation cannot run it at any clock speed.
         */
        if (prog.minEra() != dev.jstech.core.tier.HardwareEra.VINTAGE) {
            // Worded exactly like the OS line above: the same requirement must not read as two rules.
            lines.add(needsEra(prog.minEra()));
        }
        if (prog.minCpuMhz() > 0) {
            lines.add(line("CPU " + prog.minCpuMhz() + " MHz+"));
        }
        if (prog.minVramMb() > 0) {
            lines.add(line("VRAM " + prog.minVramMb() + " MB+"));
        }
        if (prog.minDiskMb() > 0) {
            lines.add(line("Disk " + prog.minDiskMb() + " MB free"));
        }
        return lines;
    }

    /** The OS floor a program needs: its Frames version when it declares one, otherwise its platforms. */
    private static String minOsLabel(final ProgramSpec prog) {
        return switch (prog.minOsRank()) {
            case 3 -> "Frames 11";
            case 2 -> "Frames XP or newer";
            default -> platformsLabel(prog.platforms());
        };
    }

    /** A readable, comma-joined list of platform labels in enum order. */
    public static String platformsLabel(final java.util.Set<Platform> platforms) {
        final List<String> names = new ArrayList<>(platforms.size());
        for (final Platform p : Platform.values()) {
            if (platforms.contains(p)) {
                names.add(p.label());
            }
        }
        return String.join(", ", names);
    }

    private static MutableComponent line(final String text) {
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
