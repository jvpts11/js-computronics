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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CannonFrontEndTest {

    /** What every file starts with, on one line so the sources keep their line numbers. */
    private static final String PRELUDE = "using System.*; using System.IO.*; using System.Collections.*; "
            + "using System.Utils.*; using System.Machine.*; using System.Network.*; using System.Operations.*; "
            + "namespace Tests; ";

    private static CannonFrontEnd.Result parse(final String source) {
        return CannonFrontEnd.parse(new SourceFile("Monitor.can", PRELUDE + source));
    }

    @Test
    void parse_saysACleanFileIsFine() {
        final CannonFrontEnd.Result result = parse("""
                class Monitor : IScript {
                    private int threshold = 100;

                    public void OnInit() { }

                    public void OnTick() {
                        QueryResult result = Network.Current.Query("minecraft:diamond");
                        if (result.Total < threshold) {
                            Mainframe.Log(LogLevel.WARN, "Diamonds below " + threshold);
                        }
                    }

                    public void OnDestroy() { }
                }
                """);
        assertTrue(result.ok(), () -> String.join("\n", result.lines()));
        assertTrue(result.diagnostics().isEmpty());
        assertFalse(result.truncated());
        assertEquals(1, result.unit().types().size());
    }

    @Test
    void parse_writesAMessageWithTheFileLineAndColumn() {
        final CannonFrontEnd.Result result = parse("class C {\n    int x\n}");
        assertFalse(result.ok());
        assertTrue(result.lines().getFirst().startsWith("Monitor.can(3,1): error C"),
                () -> String.join("\n", result.lines()));
    }

    @Test
    void parse_ordersTheMessagesByWhereTheyAre() {
        final CannonFrontEnd.Result result = parse("class C {\n    void M() {\n        1 + 2;\n        3 + 4;\n"
                + "    }\n}");
        final List<Diagnostic> diagnostics = result.diagnostics();
        assertEquals(2, diagnostics.size());
        assertEquals(3, diagnostics.get(0).line());
        assertEquals(4, diagnostics.get(1).line());
    }

    @Test
    void parse_stopsReportingOnceTheFileIsAllMistakes() {
        final StringBuilder source = new StringBuilder("class C { void M() {\n");
        for (int i = 0; i < DiagnosticBag.MAX_DIAGNOSTICS + 50; i++) {
            source.append("    1 + 2;\n");
        }
        source.append("} }");
        final CannonFrontEnd.Result result = parse(source.toString());
        assertEquals(DiagnosticBag.MAX_DIAGNOSTICS, result.diagnostics().size());
        assertTrue(result.truncated());
        assertEquals("too many errors; the rest were not reported", result.lines().getLast());
    }

    @Test
    void parse_readsAFileWithNothingInIt() {
        final CannonFrontEnd.Result result = parse("// only a comment\n");
        assertTrue(result.ok());
        assertTrue(result.unit().types().isEmpty());
    }
}
