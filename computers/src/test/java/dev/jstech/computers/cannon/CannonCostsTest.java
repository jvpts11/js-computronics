/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.computers.cannon.sem.BuiltIns;
import dev.jstech.computers.cannon.sem.IMemberSymbol;
import dev.jstech.computers.cannon.sem.NamedType;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CannonCostsTest {

    /** The objects that reach out of the program into the machine, and therefore cost something. */
    private static final List<String> OUTWARD =
            List.of("Computer", "File", "Network", "Mainframe", "Operations");

    private BuiltIns builtIns;

    @BeforeEach
    void setUp() {
        this.builtIns = new BuiltIns();
    }

    /** Every member the language lets a program call on one of the outward objects. */
    private Set<String> declared() {
        final Set<String> names = new LinkedHashSet<>();
        for (final String owner : OUTWARD) {
            final NamedType type = this.builtIns.type(owner, 0);
            assertTrue(type != null, owner + " is not a type the language declares");
            for (final IMemberSymbol member : type.allMembers()) {
                if (!(member instanceof IMemberSymbol.ConstructorSymbol)) {
                    names.add(owner + "." + member.name());
                }
            }
        }
        return names;
    }

    @Test
    void of_pricesEveryCallTheLanguageOffers() {
        final Set<String> missing = new LinkedHashSet<>();
        for (final String call : declared()) {
            final String[] parts = call.split("\\.", 2);
            if (!CannonCosts.known(parts[0], parts[1])) {
                missing.add(call);
            }
        }
        assertEquals(Set.of(), missing, "these calls reach the machine and nothing says what they cost");
    }

    @Test
    void all_namesOnlyCallsTheLanguageStillOffers() {
        final Set<String> declared = declared();
        final Set<String> stale = new LinkedHashSet<>();
        for (final String call : CannonCosts.all()) {
            if (!declared.contains(call)) {
                stale.add(call);
            }
        }
        assertEquals(Set.of(), stale, "these are priced but no longer exist");
    }

    @Test
    void of_answersFreeForSomethingTheMachineIsNotAskedAbout() {
        assertEquals(0, CannonCosts.of("Math", "Floor").fixed());
        assertEquals("free", CannonCosts.of("Math", "Floor").describe());
    }

    @Test
    void describe_saysWhenEveryRowCostsOneMore() {
        assertEquals("50 plus one for every row it brings back", CannonCosts.of("Network", "Find").describe());
        assertEquals("50", CannonCosts.of("Network", "Total").describe());
    }

    @Test
    void at_chargesOneMoreForEveryRow() {
        assertEquals(50, CannonCosts.of("Network", "Find").at(0));
        assertEquals(53, CannonCosts.of("Network", "Find").at(3));
        assertEquals(50, CannonCosts.of("Network", "Total").at(3));
    }

    @Test
    void of_leavesBeingToldFree() {
        /*
         * Asking to be told costs nothing on purpose: a program that says once that it wants to know
         * when the iron runs low is doing the cheap thing, and one that asks every tick is not.
         */
        for (final String member : List.of("Watch", "WatchBelow", "WatchAbove")) {
            assertEquals(0, CannonCosts.of("Network", member).at(0), member + " should cost nothing to arm");
        }
    }

    @Test
    void of_makesAskingForWorkDearerThanReadingIt() {
        assertTrue(CannonCosts.of("Operations", "Craft").at(0) > CannonCosts.of("Operations", "Get").at(0));
        assertTrue(CannonCosts.of("File", "Write").at(0) > CannonCosts.of("File", "Read").at(0));
        assertTrue(CannonCosts.of("Computer", "Disks").at(0) > CannonCosts.of("Computer", "Name").at(0));
    }
}
