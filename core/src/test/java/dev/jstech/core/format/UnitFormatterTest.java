/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.format;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitFormatterTest {

    private static final Locale EN = Locale.US;
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    @Test
    void allCanonicalUnits_haveSuffix() {
        assertEquals(" FE", Unit.FE.suffix());
        assertEquals(" FE/t", Unit.FE_PER_TICK.suffix());
        assertEquals(" MB", Unit.MB.suffix());
        assertEquals(" it/t", Unit.IT_PER_TICK.suffix());
    }

    @Test
    void allCanonicalUnits_areScalable() {
        for (Unit unit : Unit.values()) {
            assertTrue(unit.scalable(),
                    "Phase 0 unit " + unit + " should be scalable");
        }
    }

    @Test
    void full_smallValue_enUS() {
        UnitFormatter formatter = new UnitFormatter(EN);
        assertEquals("0 FE", formatter.full(0L, Unit.FE));
        assertEquals("42 FE", formatter.full(42L, Unit.FE));
        assertEquals("999 FE", formatter.full(999L, Unit.FE));
    }

    @Test
    void full_largeValue_enUS_usesCommaGrouping() {
        UnitFormatter formatter = new UnitFormatter(EN);
        assertEquals("1,234,567 FE", formatter.full(1_234_567L, Unit.FE));
    }

    @Test
    void full_negativeValue_enUS() {
        UnitFormatter formatter = new UnitFormatter(EN);
        assertEquals("-500 FE", formatter.full(-500L, Unit.FE));
    }

    @Test
    void full_largeValue_ptBR_usesDotGrouping() {
        UnitFormatter formatter = new UnitFormatter(PT_BR);
        // pt_BR uses '.' as thousands separator
        assertEquals("1.234.567 FE", formatter.full(1_234_567L, Unit.FE));
    }

    @Test
    void compact_subThousand_unchanged() {
        UnitFormatter formatter = new UnitFormatter(EN);
        assertEquals("0 FE", formatter.compact(0L, Unit.FE));
        assertEquals("999 FE", formatter.compact(999L, Unit.FE));
    }

    @Test
    void compact_thousands_usesK() {
        UnitFormatter formatter = new UnitFormatter(EN);
        assertEquals("1K FE", formatter.compact(1_000L, Unit.FE));
        assertEquals("1.5K FE", formatter.compact(1_500L, Unit.FE));
        assertEquals("999.99K FE", formatter.compact(999_990L, Unit.FE));
    }

    @Test
    void compact_millions_usesM() {
        UnitFormatter formatter = new UnitFormatter(EN);
        assertEquals("1M FE", formatter.compact(1_000_000L, Unit.FE));
        assertEquals("1.23M FE", formatter.compact(1_230_000L, Unit.FE));
    }

    @Test
    void compact_billions_usesG() {
        UnitFormatter formatter = new UnitFormatter(EN);
        assertEquals("1G FE", formatter.compact(1_000_000_000L, Unit.FE));
        assertEquals("2.5G FE", formatter.compact(2_500_000_000L, Unit.FE));
    }

    @Test
    void compact_trillions_usesT() {
        UnitFormatter formatter = new UnitFormatter(EN);
        assertEquals("1T FE", formatter.compact(1_000_000_000_000L, Unit.FE));
    }

    @Test
    void compact_negativeValue() {
        UnitFormatter formatter = new UnitFormatter(EN);
        assertEquals("-1.5K FE", formatter.compact(-1_500L, Unit.FE));
        assertEquals("-2M FE", formatter.compact(-2_000_000L, Unit.FE));
    }

    @Test
    void compact_ptBR_usesCommaForDecimal() {
        UnitFormatter formatter = new UnitFormatter(PT_BR);
        assertEquals("1,5K FE", formatter.compact(1_500L, Unit.FE));
        assertEquals("1,23M FE", formatter.compact(1_230_000L, Unit.FE));
    }

    @Test
    void compact_appliesToAllUnits() {
        UnitFormatter formatter = new UnitFormatter(EN);
        assertEquals("8K FE/t", formatter.compact(8_000L, Unit.FE_PER_TICK));
        assertEquals("1.5M MB", formatter.compact(1_500_000L, Unit.MB));
        assertEquals("500 it/t", formatter.compact(500L, Unit.IT_PER_TICK));
    }

    @Test
    void compact_atExactThreshold() {
        UnitFormatter formatter = new UnitFormatter(EN);
        // Exactly at threshold should bump to next unit.
        assertEquals("1K FE", formatter.compact(1_000L, Unit.FE));
        assertEquals("1M FE", formatter.compact(1_000_000L, Unit.FE));
    }

    @Test
    void compact_justBelowThreshold() {
        UnitFormatter formatter = new UnitFormatter(EN);
        assertEquals("999 FE", formatter.compact(999L, Unit.FE));
    }

    @Test
    void compact_minLong_singleSignNoDoubleNegative() {
        /*
         * Math.abs(Long.MIN_VALUE) overflows back to a negative value; the magnitude must be clamped so
         * the output carries exactly one leading sign and never a "--" prefix.
         */
        UnitFormatter formatter = new UnitFormatter(EN);
        String result = formatter.compact(Long.MIN_VALUE, Unit.FE);
        assertTrue(result.endsWith(" FE"));
        assertFalse(result.contains("--"), "expected no double-negative, got: " + result);
        assertTrue(result.startsWith("-"), "expected a single leading sign, got: " + result);
        assertFalse(result.substring(1).contains("-"), "expected only one sign, got: " + result);
    }

    @Test
    void nullLocale_throws() {
        assertThrows(NullPointerException.class, () -> new UnitFormatter(null));
    }

    @Test
    void nullUnit_throws() {
        UnitFormatter formatter = new UnitFormatter(EN);
        assertThrows(NullPointerException.class, () -> formatter.full(100L, null));
        assertThrows(NullPointerException.class, () -> formatter.compact(100L, null));
    }

    @Test
    void forCurrentLocale_returnsNonNullFormatter() {
        UnitFormatter formatter = UnitFormatter.forCurrentLocale();
        assertEquals(Locale.getDefault(), formatter.locale());
    }
}
