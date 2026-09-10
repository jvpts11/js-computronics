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
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /** What every file starts with, on one line so the sources keep their line numbers. */
    private static final String PRELUDE = "using System.*; using System.IO.*; using System.Collections.*; "
            + "using System.Utils.*; using System.Machine.*; using System.Network.*; using System.Operations.*; "
            + "namespace Tests; ";

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
        final SemanticModel model = CannonSemantics.check(List.of(new SourceFile("Monitor.can", PRELUDE + """
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
        final SemanticModel model = CannonSemantics.check(List.of(new SourceFile("Monitor.can", PRELUDE + """
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
    void types_offersAProgramsOwnTypeOnceWhenItIsNamedAfterOneOfTheLanguages() {
        /*
         * A program may call a type Console in its own namespace, and then that is the Console its
         * names reach: the list offers exactly one, the program's, and not the language's beside it.
         */
        final SemanticModel model = CannonSemantics.check(List.of(new SourceFile("Monitor.can", PRELUDE + """
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
        assertEquals("this program", items.get(0).owner());
    }

    @Test
    void types_listsTheProgramsOwnTypesBesideTheLanguages() {
        final SemanticModel model = CannonSemantics.check(List.of(new SourceFile("Monitor.can", PRELUDE + """
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

    /* A program the way an editor sees it: the line the caret is on is broken. */

    private static final String FARM = PRELUDE + """
            class Counter {
                public int Count;
                public void Bump() { this.Count = this.Count + 1; }
            }
            class Farm : IScript {
                private Counter counter = new Counter();
                public void OnInit() {
                    int total = 0;
                    counter.
                }
                public void OnTick() { string name = "x"; }
                public void OnDestroy() { }
            }
            """;

    /** The line the broken statement is on, counting from one, with the prelude on the first line. */
    private static final int BROKEN_LINE = 9;

    private record Read(SemanticModel model, CannonCompletions.Scope scope) {
    }

    /** Checks one file the way an editor does and finds the scope at {@code line}, counting from one. */
    private static Read readAt(final String text, final int line) {
        final CannonSemantics.Result result =
                CannonSemantics.checkTolerant(List.of(new SourceFile("Farm.can", text)));
        return new Read(result.model(),
                CannonCompletions.scopeAt(result.model(), result.unit("Farm.can"), "Farm.can", line));
    }

    private static List<String> names(final CannonCompletions.Scope scope) {
        return scope.variables().stream().map(v -> v.name()).toList();
    }

    @Test
    void scopeAt_findsTheTypeAroundTheCaretAndTheLocalsAboveItEvenWhenTheLineIsBroken() {
        final Read read = readAt(FARM, BROKEN_LINE);
        assertEquals("Farm", read.scope().enclosing().name());
        assertTrue(names(read.scope()).contains("total"));
        assertFalse(names(read.scope()).contains("name"), "a local of another method is out of reach");
    }

    @Test
    void scopeAt_doesNotSeeALocalDeclaredBelowTheCaret() {
        assertFalse(names(readAt(FARM, 7).scope()).contains("total"));
    }

    @Test
    void scopeAt_isNowhereOutsideEveryType() {
        final Read read = readAt(PRELUDE + "\n\nclass Farm { }", 2);
        assertEquals(CannonCompletions.Scope.NONE, read.scope());
    }

    @Test
    void resolve_readsAFieldOfTheTypeAroundTheCaretAndListsWhatItsTypeHas() {
        final Read read = readAt(FARM, BROKEN_LINE);
        final CannonCompletions.Target target =
                CannonCompletions.resolve(this.builtIns, read.model(), read.scope(), List.of("counter"));
        assertEquals("Tests.Counter", target.type().describe());
        assertFalse(target.staticSide());
        assertEquals(List.of("Bump", "Count"), labels(CannonCompletions.members(target, "")));
    }

    @Test
    void resolve_readsALocalAboveTheCaret() {
        final Read read = readAt(FARM, BROKEN_LINE);
        final CannonCompletions.Target target =
                CannonCompletions.resolve(this.builtIns, read.model(), read.scope(), List.of("total"));
        assertEquals("int", target.type().describe());
    }

    @Test
    void resolve_readsThisAndThenAChainThroughIt() {
        final Read read = readAt(FARM, BROKEN_LINE);
        assertEquals("Tests.Farm", CannonCompletions.resolve(this.builtIns, read.model(), read.scope(),
                List.of("this")).type().describe());
        assertEquals("Tests.Counter", CannonCompletions.resolve(this.builtIns, read.model(), read.scope(),
                List.of("this", "counter")).type().describe());
    }

    @Test
    void resolve_readsATypeOnItsStaticSide() {
        final Read read = readAt(FARM, BROKEN_LINE);
        final CannonCompletions.Target target =
                CannonCompletions.resolve(this.builtIns, read.model(), read.scope(), List.of("Network"));
        assertTrue(target.staticSide());
        assertTrue(labels(CannonCompletions.members(target, "Watch")).contains("Watch"));
    }

    @Test
    void resolve_givesNothingForANameThatMeansNothing() {
        final Read read = readAt(FARM, BROKEN_LINE);
        assertNull(CannonCompletions.resolve(this.builtIns, read.model(), read.scope(), List.of("nowhere")));
        assertNull(CannonCompletions.resolve(this.builtIns, read.model(), read.scope(),
                List.of("counter", "nowhere")));
        assertNull(CannonCompletions.resolve(this.builtIns, read.model(), read.scope(), List.of()));
    }

    @Test
    void resolve_worksWithoutAScopeForATypeName() {
        final CannonCompletions.Target target = CannonCompletions.resolve(this.builtIns, null,
                CannonCompletions.Scope.NONE, List.of("Network"));
        assertEquals("Network", target.type().describe());
    }

    @Test
    void names_offersTheVariablesInReachFirstAndThenTheTypesOwnMembers() {
        final Read read = readAt(FARM, BROKEN_LINE);
        final List<CannonCompletions.Item> items =
                CannonCompletions.names(this.builtIns, read.model(), read.scope(), "");
        assertEquals("total", items.get(0).label());
        assertEquals(CannonCompletions.Sort.VARIABLE, items.get(0).sort());
        assertEquals(List.of("OnDestroy", "OnInit", "OnTick"),
                labels(CannonCompletions.names(this.builtIns, read.model(), read.scope(), "On")));
        assertEquals("Farm", named(items, "counter").owner());
    }

    @Test
    void names_stillOffersTheTypesWhenThereIsNoScope() {
        final List<String> found =
                labels(CannonCompletions.names(this.builtIns, null, CannonCompletions.Scope.NONE, "Net"));
        assertEquals(List.of("Network"), found);
    }

    @Test
    void scopeAt_descendsIntoATypeNestedInAnother() {
        final String text = PRELUDE + """
                class Outer {
                    public int Width;
                    class Inner {
                        public int Depth;
                        public void Go() {
                            int d = 1;
                            Depth.
                        }
                    }
                }
                """;
        final Read read = readAt(text, 7);
        assertEquals("Inner", read.scope().enclosing().name());
        assertTrue(names(read.scope()).contains("d"));
        assertEquals("int", CannonCompletions.resolve(this.builtIns, read.model(), read.scope(),
                List.of("Depth")).type().describe());
    }

    @Test
    void resolve_readsATypeDeclaredInAnotherFileOfTheProgram() {
        final CannonSemantics.Result result = CannonSemantics.checkTolerant(List.of(
                new SourceFile("Farm.can", PRELUDE + """
                        class Farm : IScript {
                            private Silo silo = new Silo();
                            public void OnInit() { silo. }
                            public void OnTick() { }
                            public void OnDestroy() { }
                        }
                        """),
                new SourceFile("Silo.can", PRELUDE + """
                        class Silo {
                            public long Stored;
                            public void Fill(long amount) { this.Stored = this.Stored + amount; }
                        }
                        """)));
        final CannonCompletions.Scope scope =
                CannonCompletions.scopeAt(result.model(), result.unit("Farm.can"), "Farm.can", 3);
        final CannonCompletions.Target target =
                CannonCompletions.resolve(this.builtIns, result.model(), scope, List.of("silo"));
        assertEquals(List.of("Fill", "Stored"), labels(CannonCompletions.members(target, "")));
    }
}
