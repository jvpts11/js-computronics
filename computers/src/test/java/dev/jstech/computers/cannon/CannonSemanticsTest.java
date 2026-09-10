/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.computers.cannon.sem.NamedType;
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

    /** What every file starts with, on one line so the sources keep their line numbers. */
    private static final String PRELUDE = "using System.*; using System.IO.*; using System.Collections.*; "
            + "using System.Utils.*; using System.Machine.*; using System.Network.*; using System.Operations.*; "
            + "namespace Tests; ";

    private static CannonSemantics.Result check(final String source) {
        return CannonSemantics.check(List.of(new SourceFile("Test.can", PRELUDE + source)));
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
                CannonSemantics.checkProgram(List.of(new SourceFile("Monitor.can", PRELUDE + SCRIPT)));
        assertClean(result);
        assertNotNull(result.model().entryPoint());
        assertEquals("Monitor", result.model().entryPoint().name());
    }

    @Test
    void checkProgram_refusesAFileWithNoEntryPoint() {
        final CannonSemantics.Result result =
                CannonSemantics.checkProgram(List.of(new SourceFile("Helper.can", PRELUDE + "class Helper { }")));
        assertReports("C3017", result);
        assertNull(result.model().entryPoint());
    }

    @Test
    void checkProgram_refusesTwoClassesThatBothWantToStart() {
        final CannonSemantics.Result result = CannonSemantics.checkProgram(List.of(new SourceFile("Two.can", PRELUDE + """
                class A : IScript { public void OnInit() { } public void OnTick() { } public void OnDestroy() { } }
                class B : IScript { public void OnInit() { } public void OnTick() { } public void OnDestroy() { } }
                """)));
        assertReports("C3017", result);
    }

    @Test
    void checkProgram_takesAStaticMainAsAProgramThatRunsAtATerminal() {
        final CannonSemantics.Result result =
                CannonSemantics.checkProgram(List.of(new SourceFile("Hello.can", PRELUDE + """
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
                CannonSemantics.checkProgram(List.of(new SourceFile("Both.can", PRELUDE + """
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
                CannonSemantics.checkProgram(List.of(new SourceFile("Watch.can", PRELUDE + """
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
                CannonSemantics.checkProgram(List.of(new SourceFile("Nearly.can", PRELUDE + """
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
    void check_letsAProgramNameATypeAfterOneOfTheLanguages() {
        // The language's Console lives in System.IO; a program's own, in its namespace, is another type.
        assertClean(check("class Console { public int Value; }"
                + " class M { void F() { Console c = new Console(); c.Value = 1; } }"));
    }

    @Test
    void check_wantsAUsingBeforeALanguageTypeIsNamedBare() {
        final CannonSemantics.Result bare = CannonSemantics.check(List.of(new SourceFile("Test.can",
                "namespace Tests; class M { void F() { Console.PrintLine(\"x\"); } }")));
        assertReports("C3040", bare);
        assertTrue(bare.lines().getFirst().contains("System.IO"), () -> String.join("\n", bare.lines()));
        final CannonSemantics.Result byType = CannonSemantics.check(List.of(new SourceFile("Test.can",
                "using System.IO.Console; namespace Tests; class M { void F() { Console.PrintLine(\"x\"); } }")));
        assertClean(byType);
        final CannonSemantics.Result byFullName = CannonSemantics.check(List.of(new SourceFile("Test.can",
                "namespace Tests; class M { void F() { System.IO.Console.PrintLine(\"x\"); } }")));
        assertClean(byFullName);
        final CannonSemantics.Result withoutStar = CannonSemantics.check(List.of(new SourceFile("Test.can",
                "using System.IO; namespace Tests; class M { void F() { Console.PrintLine(\"x\"); } }")));
        assertReports("C2012", withoutStar);
    }

    @Test
    void check_wantsEveryTypeInANamespace() {
        assertReports("C2011", CannonSemantics.check(List.of(new SourceFile("Test.can", "class M { }"))));
    }

    @Test
    void check_readsAStructARecordAndANestedType() {
        assertClean(check("""
                struct Vec { public int X; public int Y; }
                record Point(int X, int Y);
                class Outer { public class Inner { public int V; } public Inner Make() { return new Inner(); } }
                class M {
                    void F() {
                        Vec v = new Vec(); v.X = 1;
                        Point p = new Point(1, 2);
                        string s = p.ToString();
                        bool same = p.Equals(new Point(1, 2)) && p.X == 1;
                        Outer.Inner i = new Outer().Make(); i.V = 3;
                    }
                }
                """));
        assertReports("C3041", check("class B { } struct S : B { }"));
        assertReports("C3016", check("record Point(int X); class M { void F() { Point p = new Point(1); p.X = 2; } }"));
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

    private static final String TOOLS = """
            namespace Tools;
            public class Counter {
                private int n;
                public void Add(int k) { n = n + k; }
                public int Count() { return n; }
                public static int Twice(int x) { return x * 2; }
            }
            """;

    @Test
    void check_findsATypeThroughAUsingOrItsFullName() {
        final CannonSemantics.Result viaUsing = CannonSemantics.check(List.of(
                new SourceFile("Tools.can", TOOLS),
                new SourceFile("Main.can", "using Tools.*; namespace Main; class M { static void Main() { Counter c = new Counter(); c.Add(1); } }")));
        assertEquals(List.of(), viaUsing.diagnostics().stream().map(Diagnostic::code).toList());
        final CannonSemantics.Result viaType = CannonSemantics.check(List.of(
                new SourceFile("Tools.can", TOOLS),
                new SourceFile("Main.can", "using Tools.Counter; namespace Main; class M { static void Main() { Counter c = new Counter(); } }")));
        assertEquals(List.of(), viaType.diagnostics().stream().map(Diagnostic::code).toList());
        final CannonSemantics.Result viaName = CannonSemantics.check(List.of(
                new SourceFile("Tools.can", TOOLS),
                new SourceFile("Main.can", "namespace Main; class M { static void Main() { Tools.Counter c = new Tools.Counter(); "
                        + "int t = Tools.Counter.Twice(c.Count()); } }")));
        assertEquals(List.of(), viaName.diagnostics().stream().map(Diagnostic::code).toList());
        final CannonSemantics.Result bare = CannonSemantics.check(List.of(
                new SourceFile("Tools.can", TOOLS),
                new SourceFile("Main.can", "namespace Main; class M { static void Main() { Counter c = new Counter(); } }")));
        assertTrue(bare.diagnostics().stream().anyMatch(d -> d.code().equals("C3040")),
                "without a using, a name in another namespace is not visible, and the message says where it is");
        assertTrue(bare.lines().getFirst().contains("Tools"), () -> String.join("\n", bare.lines()));
        final CannonSemantics.Result plain = CannonSemantics.check(List.of(
                new SourceFile("Tools.can", TOOLS),
                new SourceFile("Main.can", "using Tools; namespace Main; class M { static void Main() { Counter c = new Counter(); } }")));
        assertTrue(plain.diagnostics().stream().anyMatch(d -> d.code().equals("C2012")),
                "a using that names a namespace without its star is told how to");
    }

    @Test
    void check_seesATypeInTheSameNamespaceWithoutAUsing() {
        final CannonSemantics.Result result = CannonSemantics.check(List.of(
                new SourceFile("Tools.can", TOOLS),
                new SourceFile("More.can", "namespace Tools; class Pair { Counter left; Counter right; }"),
                new SourceFile("Main.can", "namespace Main; class M { static void Main() { Tools.Pair p = new Tools.Pair(); } }")));
        assertEquals(List.of(), result.diagnostics().stream().map(Diagnostic::code).toList());
    }

    @Test
    void check_letsAClassExtendOneWrittenInAnotherFile() {
        final CannonSemantics.Result result = CannonSemantics.check(List.of(
                new SourceFile("B.can", PRELUDE + "class B { public void Thing() { } }"),
                new SourceFile("A.can", PRELUDE + "class A : B { static void Main() { A a = new A(); a.Thing(); } }")));
        assertEquals(List.of(), result.diagnostics().stream().map(Diagnostic::code).toList());
    }

    @Test
    void check_refusesTwoMethodsWithTheSameParameters() {
        final CannonSemantics.Result result = check(
                "class M { void Go(int a) { } void Go(int b) { } void Go(string s) { } static void Main() { } }");
        assertEquals(List.of("C3002"), result.diagnostics().stream().map(Diagnostic::code).toList());
    }

    @Test
    void check_namesTheFileEachMessageCameFrom() {
        final CannonSemantics.Result result = CannonSemantics.check(List.of(
                new SourceFile("First.can", PRELUDE + "class A { void M() { nope(); } }"),
                new SourceFile("Second.can", PRELUDE + "class B { void M() { alsoNope(); } }")));
        assertEquals(List.of("First.can", "Second.can"),
                result.diagnostics().stream().map(Diagnostic::file).toList());
    }
}
