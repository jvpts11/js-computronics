/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CodeRunsTest {

    private static CodeRuns.Span span(final int line, final int column, final int length, final CodeRuns.Ink ink) {
        return new CodeRuns.Span(line, column, length, ink);
    }

    /** Every run of a row, rendered as the text it would paint, so a case reads as what the player sees. */
    private static List<String> painted(final String line, final List<CodeRuns.Run> runs) {
        return runs.stream().map(r -> r.ink() + ":" + line.substring(r.start(), r.start() + r.length())).toList();
    }

    /** Whether the runs cover the row end to end with no gap and no overlap. */
    private static boolean covers(final int width, final List<CodeRuns.Run> runs) {
        int at = 0;
        for (final CodeRuns.Run run : runs) {
            if (run.start() != at || run.length() <= 0) {
                return false;
            }
            at += run.length();
        }
        return at == width;
    }

    @Test
    void byLine_fillsARowWithoutSpansAsOnePlainRun() {
        final List<List<CodeRuns.Run>> out = CodeRuns.byLine(List.of("int a;"), List.of());
        assertEquals(List.of(new CodeRuns.Run(0, 6, CodeRuns.Ink.PLAIN)), out.get(0));
    }

    @Test
    void byLine_returnsNoRunsForAnEmptyRow() {
        final List<List<CodeRuns.Run>> out = CodeRuns.byLine(List.of("", "a"), List.of());
        assertTrue(out.get(0).isEmpty());
        assertEquals(1, out.get(1).size());
    }

    @Test
    void byLine_fillsTheGapsBetweenSpansWithPlain() {
        final String line = "int a = 1;";
        final List<List<CodeRuns.Run>> out = CodeRuns.byLine(List.of(line), List.of(
                span(1, 1, 3, CodeRuns.Ink.KEYWORD),
                span(1, 9, 1, CodeRuns.Ink.NUMBER)));
        assertEquals(List.of("KEYWORD:int", "PLAIN: a = ", "NUMBER:1", "PLAIN:;"), painted(line, out.get(0)));
        assertTrue(covers(line.length(), out.get(0)));
    }

    @Test
    void byLine_ordersSpansGivenOutOfOrder() {
        final String line = "int a = 1;";
        final List<List<CodeRuns.Run>> out = CodeRuns.byLine(List.of(line), List.of(
                span(1, 9, 1, CodeRuns.Ink.NUMBER),
                span(1, 1, 3, CodeRuns.Ink.KEYWORD)));
        assertEquals(List.of("KEYWORD:int", "PLAIN: a = ", "NUMBER:1", "PLAIN:;"), painted(line, out.get(0)));
    }

    @Test
    void byLine_cutsASpanThatReachesPastTheEndOfItsRow() {
        final String line = "abc";
        final List<List<CodeRuns.Run>> out = CodeRuns.byLine(List.of(line), List.of(
                span(1, 2, 99, CodeRuns.Ink.TEXT)));
        assertEquals(List.of("PLAIN:a", "TEXT:bc"), painted(line, out.get(0)));
        assertTrue(covers(line.length(), out.get(0)));
    }

    @Test
    void byLine_dropsASpanThatStartsPastTheEndOfItsRow() {
        final String line = "abc";
        final List<List<CodeRuns.Run>> out = CodeRuns.byLine(List.of(line), List.of(
                span(1, 10, 4, CodeRuns.Ink.TEXT)));
        assertEquals(List.of("PLAIN:abc"), painted(line, out.get(0)));
    }

    @Test
    void byLine_dropsASpanOnARowThatDoesNotExist() {
        final List<List<CodeRuns.Run>> out = CodeRuns.byLine(List.of("abc"), List.of(
                span(7, 1, 2, CodeRuns.Ink.KEYWORD),
                span(0, 1, 2, CodeRuns.Ink.KEYWORD)));
        assertEquals(1, out.size());
        assertEquals(List.of("PLAIN:abc"), painted("abc", out.get(0)));
    }

    @Test
    void byLine_dropsASpanOfNoLengthOrOfNoColumn() {
        final List<List<CodeRuns.Run>> out = CodeRuns.byLine(List.of("abc"), List.of(
                span(1, 1, 0, CodeRuns.Ink.KEYWORD),
                span(1, 0, 2, CodeRuns.Ink.KEYWORD)));
        assertEquals(List.of("PLAIN:abc"), painted("abc", out.get(0)));
    }

    @Test
    void byLine_givesOverlappedCharactersToTheSpanThatStartsFirst() {
        final String line = "abcdef";
        final List<List<CodeRuns.Run>> out = CodeRuns.byLine(List.of(line), List.of(
                span(1, 1, 4, CodeRuns.Ink.COMMENT),
                span(1, 3, 4, CodeRuns.Ink.TEXT)));
        assertEquals(List.of("COMMENT:abcd", "TEXT:ef"), painted(line, out.get(0)));
        assertTrue(covers(line.length(), out.get(0)));
    }

    @Test
    void byLine_prefersTheLongerSpanWhenTwoStartTogether() {
        final String line = "abcdef";
        final List<List<CodeRuns.Run>> out = CodeRuns.byLine(List.of(line), List.of(
                span(1, 1, 2, CodeRuns.Ink.NAME),
                span(1, 1, 5, CodeRuns.Ink.COMMENT)));
        assertEquals(List.of("COMMENT:abcde", "PLAIN:f"), painted(line, out.get(0)));
    }

    @Test
    void byLine_keepsRowsSeparate() {
        final List<String> lines = List.of("int a;", "int b;");
        final List<List<CodeRuns.Run>> out = CodeRuns.byLine(lines, List.of(
                span(2, 1, 3, CodeRuns.Ink.KEYWORD)));
        assertEquals(List.of("PLAIN:int a;"), painted(lines.get(0), out.get(0)));
        assertEquals(List.of("KEYWORD:int", "PLAIN: b;"), painted(lines.get(1), out.get(1)));
    }

    @Test
    void byLine_coversEveryRowEndToEndForAWholeProgram() {
        final List<String> lines = List.of(
                "class Monitor : IScript {",
                "    private int floor = 512; // the line",
                "",
                "}");
        final List<List<CodeRuns.Run>> out = CodeRuns.byLine(lines, List.of(
                span(1, 1, 5, CodeRuns.Ink.KEYWORD),
                span(1, 7, 7, CodeRuns.Ink.NAME),
                span(2, 5, 7, CodeRuns.Ink.KEYWORD),
                span(2, 25, 12, CodeRuns.Ink.COMMENT),
                span(4, 1, 1, CodeRuns.Ink.SYMBOL)));
        for (int i = 0; i < lines.size(); i++) {
            assertTrue(covers(lines.get(i).length(), out.get(i)), "row " + (i + 1) + " is not covered");
        }
    }
}
