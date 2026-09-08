/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CommentSpansTest {

    private static String at(final String text, final CommentSpans.Span span) {
        final String[] lines = text.split("\n", -1);
        final String line = lines[span.line() - 1];
        return line.substring(span.column() - 1, span.column() - 1 + span.length());
    }

    @Test
    void find_returnsNothingForSourceWithoutComments() {
        assertTrue(CommentSpans.find("class A { }").isEmpty());
    }

    @Test
    void find_returnsNothingForNullOrEmptyText() {
        assertTrue(CommentSpans.find(null).isEmpty());
        assertTrue(CommentSpans.find("").isEmpty());
    }

    @Test
    void find_readsALineCommentToTheEndOfItsLine() {
        final String text = "int a = 1; // set it\nint b = 2;";
        final List<CommentSpans.Span> spans = CommentSpans.find(text);
        assertEquals(1, spans.size());
        assertEquals(1, spans.get(0).line());
        assertEquals(12, spans.get(0).column());
        assertEquals("// set it", at(text, spans.get(0)));
    }

    @Test
    void find_readsALineCommentThatEndsTheFileWithoutANewline() {
        final String text = "int a = 1; // last";
        final List<CommentSpans.Span> spans = CommentSpans.find(text);
        assertEquals(1, spans.size());
        assertEquals("// last", at(text, spans.get(0)));
    }

    @Test
    void find_readsABlockCommentOnOneLine() {
        final String text = "int a = /* two */ 2;";
        final List<CommentSpans.Span> spans = CommentSpans.find(text);
        assertEquals(1, spans.size());
        assertEquals(9, spans.get(0).column());
        assertEquals("/* two */", at(text, spans.get(0)));
    }

    @Test
    void find_splitsABlockCommentIntoOneSpanPerLine() {
        final String text = "/* one\n * two\n */\nint a;";
        final List<CommentSpans.Span> spans = CommentSpans.find(text);
        assertEquals(3, spans.size());
        assertEquals("/* one", at(text, spans.get(0)));
        assertEquals(" * two", at(text, spans.get(1)));
        assertEquals(" */", at(text, spans.get(2)));
        assertEquals(List.of(1, 2, 3), spans.stream().map(CommentSpans.Span::line).toList());
        assertEquals(List.of(1, 1, 1), spans.stream().map(CommentSpans.Span::column).toList());
    }

    @Test
    void find_runsAnUnterminatedBlockCommentToTheEndOfTheFile() {
        final String text = "int a;\n/* still typing\nand typing";
        final List<CommentSpans.Span> spans = CommentSpans.find(text);
        assertEquals(2, spans.size());
        assertEquals("/* still typing", at(text, spans.get(0)));
        assertEquals("and typing", at(text, spans.get(1)));
    }

    @Test
    void find_ignoresACommentOpenerInsideAString() {
        assertTrue(CommentSpans.find("string s = \"// not a comment\";").isEmpty());
        assertTrue(CommentSpans.find("string s = \"/* not a comment */\";").isEmpty());
    }

    @Test
    void find_ignoresACommentOpenerInsideACharLiteral() {
        assertTrue(CommentSpans.find("char c = '/';").isEmpty());
    }

    @Test
    void find_readsACommentAfterAStringOnTheSameLine() {
        final String text = "Console.PrintLine(\"a // b\"); // real";
        final List<CommentSpans.Span> spans = CommentSpans.find(text);
        assertEquals(1, spans.size());
        assertEquals("// real", at(text, spans.get(0)));
    }

    @Test
    void find_keepsCountingColumnsPastAnEscapedQuote() {
        final String text = "string s = \"a\\\"b\"; // after";
        final List<CommentSpans.Span> spans = CommentSpans.find(text);
        assertEquals(1, spans.size());
        assertEquals("// after", at(text, spans.get(0)));
    }

    @Test
    void find_doesNotTreatARunawayQuoteAsSwallowingTheNextLine() {
        final String text = "string s = \"open;\n// a comment";
        final List<CommentSpans.Span> spans = CommentSpans.find(text);
        assertEquals(1, spans.size());
        assertEquals(2, spans.get(0).line());
        assertEquals("// a comment", at(text, spans.get(0)));
    }

    @Test
    void find_readsSeveralCommentsInReadingOrder() {
        final String text = "// one\nint a; /* two */ int b; // three";
        final List<CommentSpans.Span> spans = CommentSpans.find(text);
        assertEquals(3, spans.size());
        assertEquals("// one", at(text, spans.get(0)));
        assertEquals("/* two */", at(text, spans.get(1)));
        assertEquals("// three", at(text, spans.get(2)));
    }

    @Test
    void find_readsAnEmptyBlockComment() {
        final String text = "int a; /**/ int b;";
        final List<CommentSpans.Span> spans = CommentSpans.find(text);
        assertEquals(1, spans.size());
        assertEquals("/**/", at(text, spans.get(0)));
    }

    @Test
    void find_readsABlockCommentOpenerAtTheVeryEndOfTheFile() {
        final String text = "int a; /*";
        final List<CommentSpans.Span> spans = CommentSpans.find(text);
        assertEquals(1, spans.size());
        assertEquals("/*", at(text, spans.get(0)));
    }

    @Test
    void find_leavesALoneSlashAlone() {
        assertTrue(CommentSpans.find("int a = 8 / 2;").isEmpty());
    }
}
