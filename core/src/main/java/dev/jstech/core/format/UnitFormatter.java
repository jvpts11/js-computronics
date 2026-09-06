/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.format;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Formats numeric values with mod units, locale-aware.
 */
public final class UnitFormatter {

    private static final long THOUSAND = 1_000L;
    private static final long MILLION = 1_000_000L;
    private static final long BILLION = 1_000_000_000L;
    private static final long TRILLION = 1_000_000_000_000L;

    private final Locale locale;
    private final NumberFormat fullFormat;
    private final NumberFormat compactFormat;

    public UnitFormatter(final Locale locale) {
        this.locale = Objects.requireNonNull(locale, "locale must not be null");
        this.fullFormat = NumberFormat.getIntegerInstance(locale);
        this.compactFormat = NumberFormat.getNumberInstance(locale);
        this.compactFormat.setMinimumFractionDigits(0);
        this.compactFormat.setMaximumFractionDigits(2);
    }

    public static UnitFormatter forCurrentLocale(){
        return new UnitFormatter(Locale.getDefault());
    }

    public String full(final long value, final Unit unit) {
        Objects.requireNonNull(unit, "unit must not be null");
        return fullFormat.format(value) + unit.suffix();
    }

    public String compact(final long value, final Unit unit) {
        Objects.requireNonNull(unit, "unit must not be null");
        if (!unit.scalable()) {
            return full(value, unit);
        }

        // Math.abs(Long.MIN_VALUE) stays negative (it overflows back to itself), which would print a
        // stray leading "-" on top of the sign prefix; clamp it to Long.MAX_VALUE so the magnitude is
        // always non-negative.
        final long abs = value == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(value);
        final String sign = value < 0 ? "-" : "";

        if (abs < THOUSAND) {
            return sign + abs + unit.suffix();
        }
        if (abs < MILLION) {
            return sign + compactFormat.format((double) abs / THOUSAND) + "K" + unit.suffix();
        }
        if (abs < BILLION) {
            return sign + compactFormat.format((double) abs / MILLION) + "M" + unit.suffix();
        }
        if (abs < TRILLION) {
            return sign + compactFormat.format((double) abs / BILLION) + "G" + unit.suffix();
        }
        return sign + compactFormat.format((double) abs / TRILLION) + "T" + unit.suffix();
    }

    public Locale locale() {
        return locale;
    }
}
