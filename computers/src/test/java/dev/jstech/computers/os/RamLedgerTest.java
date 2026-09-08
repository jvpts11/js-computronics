/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.core.tier.HardwareEra;
import java.util.List;
import org.junit.jupiter.api.Test;

class RamLedgerTest {

    @Test
    void usedMb_sumsEveryEntryAndByKind() {
        final RamLedger ledger = new RamLedger(128)
                .add("Frames XP", 64, RamLedger.Kind.SYSTEM)
                .add("IQL Engine", 24, RamLedger.Kind.SERVICE)
                .add("Files", 16, RamLedger.Kind.WINDOW)
                .add("Editor", 16, RamLedger.Kind.WINDOW);

        assertEquals(120, ledger.usedMb());
        assertEquals(32, ledger.usedMb(RamLedger.Kind.WINDOW));
        assertEquals(8, ledger.freeMb());
        assertTrue(ledger.fits(8));
        assertFalse(ledger.fits(9));
    }

    @Test
    void add_ignoresAnEmptyEntry() {
        final RamLedger ledger = new RamLedger(64).add("nothing", 0, RamLedger.Kind.WINDOW);

        assertTrue(ledger.entries().isEmpty());
        assertEquals(64, ledger.freeMb());
    }

    @Test
    void freeMb_neverGoesNegative() {
        final RamLedger ledger = new RamLedger(16).add("Frames XP", 64, RamLedger.Kind.SYSTEM);

        assertEquals(0, ledger.freeMb());
        assertFalse(ledger.fits(1));
    }

    @Test
    void eraWeightMb_growsWithTheEra() {
        assertEquals(1, RamLedger.eraWeightMb(HardwareEra.VINTAGE, ProgramKind.APP));
        assertEquals(16, RamLedger.eraWeightMb(HardwareEra.LEGACY, ProgramKind.APP));
        assertEquals(96, RamLedger.eraWeightMb(HardwareEra.STANDARD, ProgramKind.APP));
        assertTrue(RamLedger.eraWeightMb(HardwareEra.SINGULARITY, ProgramKind.APP)
                > RamLedger.eraWeightMb(HardwareEra.EXA, ProgramKind.APP));
    }

    @Test
    void eraWeightMb_halvesAServiceAndDoublesADesktop() {
        assertEquals(8, RamLedger.eraWeightMb(HardwareEra.LEGACY, ProgramKind.SERVICE));
        assertEquals(1, RamLedger.eraWeightMb(HardwareEra.VINTAGE, ProgramKind.SERVICE));
        assertEquals(32, RamLedger.eraWeightMb(HardwareEra.LEGACY, ProgramKind.DESKTOP_ENVIRONMENT));
    }

    @Test
    void bundledWeightMb_isAQuarterOfTheSystemAndAtLeastOne() {
        assertEquals(16, RamLedger.bundledWeightMb(64));
        assertEquals(192, RamLedger.bundledWeightMb(768));
        assertEquals(1, RamLedger.bundledWeightMb(2));
    }

    @Test
    void withinBudget_keepsTheLeadingItemsThatFit() {
        final List<Integer> kept = RamLedger.withinBudget(List.of(16, 16, 32, 16), w -> w, 64);

        assertEquals(List.of(16, 16, 32), kept);
    }

    @Test
    void withinBudget_dropsEverythingAfterTheFirstMisfit() {
        final List<Integer> kept = RamLedger.withinBudget(List.of(16, 64, 1), w -> w, 32);

        assertEquals(List.of(16), kept);
    }

    @Test
    void withinBudget_keepsAllWhenTheyFit() {
        final List<String> windows = List.of("Files", "Editor");

        assertEquals(windows, RamLedger.withinBudget(windows, w -> 16, 32));
    }
}
