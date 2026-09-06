/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.computronics.cannon.sem.NamedType;
import java.util.List;
import org.junit.jupiter.api.Test;

class CannonSemanticsTest {

    private static final String SCRIPT = """
            class Monitor : IScript {
                private int threshold = 100;
                private List<string> seen = new List<string>();

                public void OnInit() {
                    seen.Add("start");
                }

                public void OnTick() {
                    int total = seen.Count;
                    if (total < threshold) {
                        Console.PrintLine("only " + total);
                    }
                }

                public void OnDestroy() {
                    dispose seen;
                }
            }
            """;

    private static CannonSemantics.Result check(final String source) {
        return CannonSemantics.check(List.of(new SourceFile("Test.can", source)));
    }

    private static List<String> codes(final CannonSemantics.Result result) {
        return result.diagnostics().stream().map(Diagnostic::code).toList();
    }

    private static void assertClean(final CannonSemantics.Result result) {
        assertTrue(result.ok(), () -> String.join("\n", result.lines()));
    }

    private static void assertReports(final String code, final CannonSemantics.Result result) {
        assertTrue(codes(result).contains(code),
                () -> code + " was not among " + String.join("\n", result.lines()));
    }

    @Test
    void check_acceptsAWholeScript() {
        assertClean(check(SCRIPT));
    }

    @Test
    void checkProgram_findsTheClassTheRuntimeStartsFrom() {
        final CannonSemantics.Result result =
                CannonSemantics.checkProgram(List.of(new SourceFile("Monitor.can", SCRIPT)));
        assertClean(result);
        assertNotNull(result.model().entryPoint());
        assertEquals("Monitor", result.model().entryPoint().name());
    }

    @Test
    void checkProgram_refusesAFileWithNoEntryPoint() {
        final CannonSemantics.Result result =
                CannonSemantics.checkProgram(List.of(new SourceFile("Helper.can", "class Helper { }")));
        assertReports("C3017", result);
        assertNull(result.model().entryPoint());
    }

    @Test
    void checkProgram_refusesTwoClassesThatBothWantToStart() {
        final CannonSemantics.Result result = CannonSemantics.checkProgram(List.of(new SourceFile("Two.can", """
                class A : IScript { public void OnInit() { } public void OnTick() { } public void OnDestroy() { } }
                class B : IScript { public void OnInit() { } public void OnTick() { } public void OnDestroy() { } }
                """)));
        assertReports("C3017", result);
    }

    @Test
    void checkProgram_takesAStaticMainAsAProgramThatRunsAtATerminal() {
        final CannonSemantics.Result result =
                CannonSemantics.checkProgram(List.of(new SourceFile("Hello.can", """
                        class Hello {
                            static void Main() { Console.PrintLine("hi"); }
                        }
                        """)));
        assertClean(result);
        assertEquals("Hello", result.model().entryPoint().name());
        assertEquals(Shape.CONSOLE, result.model().shape());
    }

    @Test
    void checkProgram_refusesAFileThatIsBothKindsOfProgram() {
        final CannonSemantics.Result result =
                CannonSemantics.checkProgram(List.of(new SourceFile("Both.can", """
                        class Hello { static void Main() { } }
                        class Watch : IScript {
                            public void OnInit() { }
                            public void OnTick() { }
                            public void OnDestroy() { }
                        }
                        """)));
        assertReports("C3017", result);
    }

    @Test
    void checkProgram_takesAScriptWithAMainAsAScript() {
        final CannonSemantics.Result result =
                CannonSemantics.checkProgram(List.of(new SourceFile("Watch.can", """
                        class Watch : IScript {
                            static void Main() { }
                            public void OnInit() { }
                            public void OnTick() { }
                            public void OnDestroy() { }
                        }
                        """)));
        assertClean(result);
        assertEquals(Shape.SCRIPT, result.model().shape());
    }

    @Test
    void checkProgram_doesNotTakeAMainOfTheWrongShapeAsOne() {
        final CannonSemantics.Result result =
                CannonSemantics.checkProgram(List.of(new SourceFile("Nearly.can", """
                        class Nearly {
                            void Main() { }
                            static int Main(int n) { return n; }
                        }
                        """)));
        assertReports("C3017", result);
        assertNull(result.model().entryPoint());
    }

    @Test
    void check_readsTypesInWhateverOrderTheyWereWritten() {
        assertClean(check("""
                class Uses { Made held = new Made(); }
                class Made { public int value = 1; }
                """));
    }

    @Test
    void check_reportsATypeDeclaredTwice() {
        assertReports("C3002", check("class C { }\nclass C { }"));
    }

    @Test
    void check_reportsATypeThatTakesTheNameOfALanguageType() {
        assertReports("C3002", check("class Console { }"));
    }

    @Test
    void check_reportsAMemberDeclaredTwice() {
        assertReports("C3002", check("class C { int a = 1; string a = \"x\"; }"));
    }

    @Test
    void check_letsMethodsShareANameWithDifferentParameters() {
        assertClean(check("class C { void F(int a) { } void F(string a) { } }"));
    }

    @Test
    void check_reportsAnUnknownTypeOnce() {
        final CannonSemantics.Result result = check("class C { Nope held; }");
        assertReports("C3001", result);
        assertEquals(1, result.diagnostics().size(), () -> String.join("\n", result.lines()));
    }

    @Test
    void check_reportsACollectionGivenTheWrongNumberOfArguments() {
        assertReports("C3019", check("class C { List held; }"));
        assertReports("C3019", check("class C { Map<int> held; }"));
    }

    @Test
    void check_reportsAnInterfaceMethodThatWasNeverWritten() {
        final CannonSemantics.Result result = check("""
                class Half : IScript {
                    public void OnInit() { }
                    public void OnTick() { }
                }
                """);
        assertReports("C3018", result);
    }

    @Test
    void check_acceptsAClassThatInheritsWhatItsInterfaceAsksFor() {
        assertClean(check("""
                class Base { public void OnInit() { } public void OnTick() { } }
                class Full : Base, IScript { public void OnDestroy() { } }
                """));
    }

    @Test
    void check_reportsABaseThatIsNotAClassOrAnInterface() {
        assertReports("C3031", check("enum Colour { RED }\nclass C : Colour { }"));
    }

    @Test
    void check_reportsAnEventWhoseTypeIsNotADelegate() {
        assertReports("C3032", check("class C { public event int Changed; }"));
    }

    @Test
    void check_readsAnEnumAndItsNumbers() {
        assertClean(check("enum LogLevel { INFO, WARN = 2, ERROR }"));
        assertReports("C3003", check("enum LogLevel { INFO = \"one\" }"));
    }

    @Test
    void check_keepsTheDeclaredTypesInTheOrderTheyWereWritten() {
        final CannonSemantics.Result result = check("class A { }\ninterface B { }\nenum C { X }");
        assertEquals(List.of("A", "B", "C"), result.model().declaredTypes().stream()
                .map(NamedType::name).toList());
    }

    @Test
    void check_stopsAtTheParserWhenTheFileWillNotParse() {
        final CannonSemantics.Result result = check("class C { int x = ; }");
        assertFalse(result.ok());
        assertTrue(codes(result).stream().allMatch(code -> code.startsWith("C1") || code.startsWith("C2")),
                () -> String.join("\n", result.lines()));
    }

    @Test
    void check_namesTheFileEachMessageCameFrom() {
        final CannonSemantics.Result result = CannonSemantics.check(List.of(
                new SourceFile("First.can", "class A { void M() { nope(); } }"),
                new SourceFile("Second.can", "class B { void M() { alsoNope(); } }")));
        assertEquals(List.of("First.can", "Second.can"),
                result.diagnostics().stream().map(Diagnostic::file).toList());
    }
}
