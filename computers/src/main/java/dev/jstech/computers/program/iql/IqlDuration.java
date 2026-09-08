/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.program.iql;

import java.util.Locale;

/**
 * Parses an IQL job interval ({@code EVERY <duration>}) into server ticks. Pure logic, no Minecraft.
 * A bare number is seconds; a suffix sets the unit: {@code t} ticks, {@code s} seconds, {@code m} minutes,
 * {@code h} hours. So {@code 30s} is 600 ticks, {@code 5m} is 6000, {@code 100t} is 100.
 */
public final class IqlDuration {

    private static final long TICKS_PER_SECOND = 20L;

    private IqlDuration() {
    }

    public static long toTicks(final String spec) {
        final String text = spec.strip().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            throw new IllegalArgumentException("empty duration");
        }
        final char last = text.charAt(text.length() - 1);
        final boolean hasUnit = !Character.isDigit(last);
        final String number = hasUnit ? text.substring(0, text.length() - 1).strip() : text;
        final long value;
        try {
            value = Long.parseLong(number);
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException("not a duration: " + spec);
        }
        if (value < 0) {
            throw new IllegalArgumentException("duration must be >= 0: " + spec);
        }
        return switch (hasUnit ? last : 's') {
            case 't' -> value;
            case 's' -> value * TICKS_PER_SECOND;
            case 'm' -> value * TICKS_PER_SECOND * 60L;
            case 'h' -> value * TICKS_PER_SECOND * 3600L;
            default -> throw new IllegalArgumentException("unknown duration unit '" + last + "' in: " + spec);
        };
    }
}
