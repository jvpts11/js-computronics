/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.computers.cannon.CannonSemantics;
import dev.jstech.computers.cannon.SourceFile;
import dev.jstech.computers.cannon.sem.BuiltIns;
import dev.jstech.computers.cannon.sem.SemanticModel;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CannonCompletionsTest {

    private BuiltIns builtIns;

    @BeforeEach
    void setUp() {
        this.builtIns = new BuiltIns();
    }

    private static List<String> labels(final List<CannonCompletions.Item> items) {
        return items.stream().map(CannonCompletions.Item::label).toList();
    }

    private static CannonCompletions.Item named(final List<CannonCompletions.Item> items, final String label) {
        return items.stream().filter(i -> i.label().equals(label)).findFirst().orElse(null);
    }

    @Test
    void members_listsWhatTheNetworkOffersOnItsStaticSide() {
        final List<CannonCompletions.Item> items =
                CannonCompletions.members(this.builtIns, null, "Network", "Watch", true);
        assertEquals(List.of("Watch", "WatchAbove", "WatchBelow"), labels(items));
    }

    @Test
    void members_writesAMethodWithWhatItTakesAndGivesBack() {
        final List<CannonCompletions.Item> items =
                CannonCompletions.members(this.builtIns, null, "Network", "WatchBelow", true);
        assertEquals(1, items.size());
        assertEquals("WatchBelow(string, long, Action<StockEvent>) : Subscription", items.get(0).signature());
        assertEquals(CannonCompletions.Sort.METHOD, items.get(0).sort());
        assertEquals("Network", items.get(0).owner());
    }

    @Test
    void members_matchesIgnoringCase() {
        assertEquals(List.of("Watch", "WatchAbove", "WatchBelow"),
                labels(CannonCompletions.members(this.builtIns, null, "Network", "watch", true)));
    }

    @Test
    void members_listsEverythingWhenThePrefixIsEmpty() {
        final List<CannonCompletions.Item> items =
                CannonCompletions.members(this.builtIns, null, "Network", "", true);
        assertTrue(items.size() > 3);
        assertTrue(labels(items).contains("Find"));
        assertTrue(labels(items).contains("Servers"));
    }

    @Test
    void members_keepsTheStaticAndInstanceSidesApart() {
        assertTrue(CannonCompletions.members(this.builtIns, null, "Network", "Watch", false).isEmpty());
        final List<CannonCompletions.Item> onInstance =
                CannonCompletions.members(this.builtIns, null, "Subscription", "", false);
        assertEquals(List.of("Id", "Item"), labels(onInstance));
    }

    @Test
    void members_writesAPropertyWithItsType() {
        final CannonCompletions.Item item =
                named(CannonCompletions.members(this.builtIns, null, "StockEvent", "", false), "Total");
        assertEquals("Total : long", item.signature());
        assertEquals(CannonCompletions.Sort.PROPERTY, item.sort());
    }

    @Test
    void members_returnsNothingForATypeThatDoesNotExist() {
        assertTrue(CannonCompletions.members(this.builtIns, null, "Nowhere", "", true).isEmpty());
        assertTrue(CannonCompletions.members(this.builtIns, null, "", "", true).isEmpty());
        assertTrue(CannonCompletions.members(this.builtIns, null, null, "", true).isEmpty());
    }

    @Test
    void members_readsTheProgramsOwnTypes() {
        final SemanticModel model = CannonSemantics.check(List.of(new SourceFile("Monitor.can", """
                class Monitor : IScript {
                    private int floor = 512;
                    public void OnInit() { }
                    public void OnTick() { }
                    public void OnDestroy() { }
                }
                """))).model();
        final List<String> onInstance =
                labels(CannonCompletions.members(this.builtIns, model, "Monitor", "On", false));
        assertEquals(List.of("OnDestroy", "OnInit", "OnTick"), onInstance);
    }

    @Test
    void members_reachesWhatAProgramsTypeInheritsFromWhatItImplements() {
        final SemanticModel model = CannonSemantics.check(List.of(new SourceFile("Monitor.can", """
                class Monitor : IScript {
                    public void OnInit() { }
                    public void OnTick() { }
                    public void OnDestroy() { }
                }
                """))).model();
        assertFalse(CannonCompletions.members(this.builtIns, model, "Monitor", "", false).isEmpty());
    }

    @Test
    void types_listsTheLanguagesOwnTypes() {
        final List<String> found = labels(CannonCompletions.types(this.builtIns, null, "Net"));
        assertEquals(List.of("Network"), found);
    }

    @Test
    void types_keepsTheLanguagesOwnWhenAProgramTriesToTakeItsName() {
        /*
         * The checker refuses a type named after one the language brings, so the name never reaches the
         * model and the list must still offer exactly one Console: the language's.
         */
        final SemanticModel model = CannonSemantics.check(List.of(new SourceFile("Monitor.can", """
                class Console {
                    public int Value;
                }
                class Monitor : IScript {
                    public void OnInit() { }
                    public void OnTick() { }
                    public void OnDestroy() { }
                }
                """))).model();
        final List<CannonCompletions.Item> items = CannonCompletions.types(this.builtIns, model, "Console");
        assertEquals(1, items.size());
        assertEquals("Cannon", items.get(0).owner());
    }

    @Test
    void types_listsTheProgramsOwnTypesBesideTheLanguages() {
        final SemanticModel model = CannonSemantics.check(List.of(new SourceFile("Monitor.can", """
                class Monitor : IScript {
                    public void OnInit() { }
                    public void OnTick() { }
                    public void OnDestroy() { }
                }
                """))).model();
        final List<String> found = labels(CannonCompletions.types(this.builtIns, model, "Mo"));
        assertEquals(List.of("Monitor"), found);
        assertEquals("this program",
                named(CannonCompletions.types(this.builtIns, model, "Mo"), "Monitor").owner());
    }

    @Test
    void types_isSortedByName() {
        final List<String> found = labels(CannonCompletions.types(this.builtIns, null, ""));
        final List<String> sorted = found.stream().sorted().toList();
        assertEquals(sorted, found);
    }
}
