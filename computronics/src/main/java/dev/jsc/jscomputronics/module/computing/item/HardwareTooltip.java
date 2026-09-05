/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.item;

import dev.jstech.core.tier.HardwareEra;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * The lines every hardware component's tooltip shares. A board seats only parts of its own generation,
 * so the generation has to be readable on the part itself — otherwise the only way to find out whether
 * two pieces go together is to try them in the slot and watch nothing happen.
 *
 * <p>One place, so the wording cannot drift between a CPU and the memory it has to match.
 */
public final class HardwareTooltip {

    private HardwareTooltip() {
    }

    /**
     * Appends the component's hardware generation, the axis that decides what fits with what, in the
     * colour of that generation's screens so the era reads before the word does.
     */
    public static void appendEra(final List<Component> tooltip, final HardwareEra era) {
        if (era == null) {
            return;
        }
        tooltip.add(eraName(era, label(era) + " era"));
    }

    /** {@code text} in the colour of {@code era}'s screens: the one styling every era mention shares. */
    public static Component eraName(final HardwareEra era, final String text) {
        return Component.literal(text).withStyle(style -> style.withColor(era.screenColor()));
    }

    /** An era name in title case: {@code LEGACY} reads as "Legacy". */
    public static String label(final HardwareEra era) {
        final String name = era.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }
}
