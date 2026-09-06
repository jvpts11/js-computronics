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
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.computronics.cannon.asm.AsmReader;
import dev.jstech.computronics.cannon.asm.AsmWriter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmitterTest {

    private static final String ENTRY = """
                public void OnInit() { }
                public void OnTick() { }
                public void OnDestroy() { }
            """;

    private static CannonCompiler.Result build(final String source) {
        return CannonCompiler.compile(List.of(new SourceFile("Monitor.can", source)));
    }

    /** Compiles the members as a whole script and hands back the listing. */
    private static String compile(final String members) {
        return compile("", members);
    }

    /** The same, with whatever the script needs declared beside it. */
    private static String compile(final String before, final String members) {
        final CannonCompiler.Result result =
                build(before + "class Monitor : IScript {\n" + members + ENTRY + "}\n");
        assertTrue(result.ok(), () -> String.join("\n", result.lines()));
        return result.assembly();
    }

    /** The lines of one method, with the spacing squeezed out and the notes dropped. */
    private static List<String> bodyOf(final String listing, final String name) {
        final List<String> body = new ArrayList<>();
        boolean inside = false;
        for (final String raw : listing.split("\n", -1)) {
            final String line = raw.strip();
            if (line.startsWith(".method")) {
                inside = line.contains(" " + name + "(");
                continue;
            }
            if (line.startsWith(".")) {
                inside = false;
            } else if (inside && !line.isBlank()) {
                body.add(line.replaceAll("\\s+", " "));
            }
        }
        return body;
    }

    @Test
    void emit_putsAValueInAPlaceAndReadsItBack() {
        assertEquals(List.of("ldc.i4 1", "stloc 0", "ldloc 0", "stloc 1", "ret"),
                bodyOf(compile("    void M() { int a = 1; int b = a; }\n"), "M"));
    }

    @Test
    void emit_widensANumberWhereItHasTo() {
        assertEquals(List.of("ldc.i4 1", "conv.r8", "stloc 0", "ret"),
                bodyOf(compile("    void M() { double d = 1; }\n"), "M"));
    }

    @Test
    void emit_turnsAChoiceIntoBranches() {
        assertEquals(List.of("ldc.i4 0", "stloc 1", "ldloc 0", "brfalse L1", "ldc.i4 1", "stloc 1",
                        "br L2", "L1: ldc.i4 2", "stloc 1", "L2: ret"),
                bodyOf(compile("    void M(bool b) { int n = 0; if (b) { n = 1; } else { n = 2; } }\n"), "M"));
    }

    @Test
    void emit_turnsALoopIntoATestAndAJumpBack() {
        assertEquals(List.of("L1: ldc.i4 1", "brfalse L2", "br L2", "br L1", "L2: ret"),
                bodyOf(compile("    void M() { while (true) { break; } }\n"), "M"));
    }

    @Test
    void emit_countsItsWayThroughAForeach() {
        assertEquals(List.of("ldc.i4 2", "newarr int", "stloc 0",
                        "ldloc 0", "stloc 1", "ldc.i4 0", "stloc 2",
                        "L1: ldloc 2", "ldloc 1", "ldlen", "bge L3",
                        "ldloc 1", "ldloc 2", "ldelem", "stloc 3",
                        "L2: ldloc 2", "ldc.i4 1", "add", "stloc 2", "br L1", "L3: ret"),
                bodyOf(compile("    void M() { int[] a = new int[2]; foreach (int x in a) { } }\n"), "M"));
    }

    @Test
    void emit_testsEveryLabelOfASwitchBeforeAnySectionRuns() {
        assertEquals(List.of("ldloc 0", "stloc 1", "ldloc 1", "ldc.i4 1", "beq L2", "br L3",
                        "L2: br L1", "L3: br L1", "L1: ret"),
                bodyOf(compile("    void M(int n) { switch (n) { case 1: break; default: break; } }\n"), "M"));
    }

    @Test
    void emit_leavesTheRightSideOfAndUnrunWhenTheLeftSettlesIt() {
        assertEquals(List.of("ldloc 0", "brfalse L1", "ldloc 1", "br L2", "L1: ldc.i4 0",
                        "L2: stloc 2", "ret"),
                bodyOf(compile("    void M(bool a, bool b) { bool c = a && b; }\n"), "M"));
    }

    @Test
    void emit_asksTheRuntimeToPutTextTogether() {
        assertEquals(List.of("ldstr \"a\"", "ldc.i4 1", "call string.Concat(string, int) -> string",
                        "stloc 0", "ret"),
                bodyOf(compile("    void M() { string s = \"a\" + 1; }\n"), "M"));
    }

    @Test
    void emit_writesAComparisonAndTheOnesThatAreItsOpposite() {
        assertEquals(List.of("ldc.i4 1", "ldc.i4 2", "clt", "stloc 0", "ret"),
                bodyOf(compile("    void M() { bool b = 1 < 2; }\n"), "M"));
        assertEquals(List.of("ldc.i4 1", "ldc.i4 2", "cgt", "ldc.i4 0", "ceq", "stloc 0", "ret"),
                bodyOf(compile("    void M() { bool b = 1 <= 2; }\n"), "M"));
    }

    @Test
    void emit_readsAFieldThroughTheObjectItBelongsTo() {
        final String listing = compile("    int threshold = 5;\n    void M() { int n = threshold; }\n");
        assertEquals(List.of("ldthis", "ldfld threshold", "stloc 0", "ret"), bodyOf(listing, "M"));
        assertTrue(listing.contains(".field int threshold"), listing);
    }

    @Test
    void emit_putsAFieldsStartingValueInAMethodOfTheTypeItself() {
        final String listing = compile("    int threshold = 5;\n");
        assertEquals(List.of("ldthis", "ldc.i4 5", "stfld threshold", "ret"), bodyOf(listing, "Monitor"));
    }

    @Test
    void emit_storesWhatAMethodFilledInAfterTheCall() {
        final String listing = compile("""
                    bool Find(out int value) { value = 0; return true; }
                    void M() { int a; Find(out a); }
                """);
        assertEquals(List.of("ldc.i4 0", "stloc 0", "ldc.i4 1", "ret"), bodyOf(listing, "Find"));
        assertEquals(List.of("ldthis", "call Monitor.Find(out int) -> bool", "stloc 0", "pop", "ret"),
                bodyOf(listing, "M"));
    }

    @Test
    void emit_joinsAHandlerToAnEventAndCallsThroughIt() {
        final String listing = compile("delegate void Handler(int v);\n", """
                    public event Handler Changed;
                    void OnValue(int v) { }
                    void M() { Changed += OnValue; Changed(1); }
                """);
        assertEquals(List.of("ldthis", "dup", "ldfld Changed", "ldthis",
                        "ldfn Monitor.OnValue(int) -> void",
                        "call Delegate.Combine(Handler, Handler) -> Handler", "stfld Changed",
                        "ldthis", "ldfld Changed", "ldc.i4 1", "callvirt Handler.Invoke(int) -> void", "ret"),
                bodyOf(listing, "M"));
    }

    @Test
    void emit_makesALambdaIntoAMethodOfItsOwn() {
        final String listing = compile("delegate int Count(string t);\n", """
                    void Use(Count c) { }
                    void M() { Use((t) => t.Length); }
                """);
        assertEquals(List.of("ldthis", "ldthis", "ldfn Monitor.0lambda1(string) -> int",
                        "call Monitor.Use(Count) -> void", "ret"), bodyOf(listing, "M"));
        assertEquals(List.of("ldloc 0", "ldfld string.Length", "ret"), bodyOf(listing, "0lambda1"));
    }

    @Test
    void emit_movesAVariableALambdaKeepsIntoAnObjectTheyShare() {
        final String listing = compile("delegate int Count(string t);\n", """
                    void Use(Count c) { }
                    void M() { int limit = 5; Use((t) => t.Length + limit); }
                """);
        assertTrue(listing.contains(".class 0closure1"), listing);
        assertTrue(listing.contains(".field int limit"), listing);
        assertEquals(List.of("newobj 0closure1()", "stloc 0",
                        "ldloc 0", "ldc.i4 5", "stfld 0closure1.limit",
                        "ldthis", "ldloc 0", "ldfn 0closure1.0lambda1(string) -> int",
                        "call Monitor.Use(Count) -> void", "ret"),
                bodyOf(listing, "M"));
        assertEquals(List.of("ldloc 0", "ldfld string.Length", "ldthis", "ldfld 0closure1.limit",
                        "add", "ret"), bodyOf(listing, "0lambda1"));
    }

    @Test
    void emit_letsALambdaReachBothWhatItKeptAndTheObjectItWasWrittenIn() {
        final String listing = compile("delegate int Count(string t);\n", """
                    int threshold = 5;
                    void Use(Count c) { }
                    void M() { int limit = 1; Use((t) => limit + threshold); }
                """);
        assertTrue(listing.contains(".field Monitor 0this"), listing);
        assertEquals(List.of("ldthis", "ldfld 0closure1.limit",
                        "ldthis", "ldfld 0closure1.0this", "ldfld threshold", "add", "ret"),
                bodyOf(listing, "0lambda1"));
    }

    @Test
    void emit_writesThroughTheSharedObjectSoBothSidesSeeTheChange() {
        final String listing = compile("delegate int Count(string t);\n", """
                    void Use(Count c) { }
                    void M() { int limit = 1; Use((t) => limit); limit = 2; }
                """);
        assertTrue(bodyOf(listing, "M").contains("stfld 0closure1.limit"), listing);
        assertEquals(2, bodyOf(listing, "M").stream()
                .filter(line -> line.equals("stfld 0closure1.limit")).count(), listing);
    }

    @Test
    void emit_saysSoWhenALambdaKeepsAVariableALoopDeclares() {
        final CannonCompiler.Result result = build("""
                delegate int Count(string t);
                class Monitor : IScript {
                    void Use(Count c) { }
                    void M() { for (int i = 0; i < 2; i++) { int n = i; Use((t) => n); } }
                """ + ENTRY + "}\n");
        assertFalse(result.ok());
        assertTrue(result.lines().getFirst().contains("C4011"), () -> String.join("\n", result.lines()));
    }

    @Test
    void emit_writesAListingThatReadsBackAsItself() {
        final String listing = compile("""
                    private int threshold = 100;
                    private List<string> seen = new List<string>();
                    public int Count { get; private set; }

                    public Monitor(int start) {
                        threshold = start;
                    }

                    void Report() {
                        seen.Add("start");
                        for (int i = 0; i < seen.Count; i++) {
                            if (i < threshold) {
                                Console.PrintLine("at " + i);
                            }
                        }
                    }
                """);
        final DiagnosticBag bag = new DiagnosticBag("Monitor.asm");
        final String again = AsmWriter.write(new AsmReader(listing, bag).read());
        assertFalse(bag.hasErrors(), () -> String.join("\n",
                bag.sorted().stream().map(Diagnostic::format).toList()));
        assertEquals(listing, again);
    }

    @Test
    void emit_namesTheClassTheRuntimeStartsFromAndWhatKindOfProgramItIs() {
        assertTrue(compile("").startsWith(".asm 1\n.start Monitor script\n"));
    }

    @Test
    void emit_marksAProgramThatRunsAtATerminalAsOne() {
        final CannonCompiler.Result built = CannonCompiler.compile(List.of(new SourceFile("Hello.can", """
                class Hello {
                    static void Main() { Console.PrintLine("hi"); }
                }
                """)));
        assertTrue(built.ok(), () -> String.join("\n", built.lines()));
        assertTrue(built.assembly().startsWith(".asm 1\n.start Hello console\n"), built.assembly());
    }
}
