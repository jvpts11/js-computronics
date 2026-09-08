/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon;

import dev.jstech.computers.cannon.ast.CompilationUnit;
import dev.jstech.computers.cannon.lex.Lexer;
import dev.jstech.computers.cannon.lex.Token;
import dev.jstech.computers.cannon.parse.Parser;
import java.util.ArrayList;
import java.util.List;

/**
 * The one door into the front of the compiler: source text in, a tree and a list of messages out.
 *
 * <p>Nothing here touches the world, a computer or a tick, which is deliberate: reading a program is
 * pure text work, so it can be tested on its own and later run off the tick without moving anything.
 */
public final class CannonFrontEnd {

    private CannonFrontEnd() {
    }

    /**
     * What reading one file produced.
     *
     * <p>The tree is always present, because the parser reads as much as it can even after a
     * mistake, but it is only worth passing on when {@link #ok()} says so.
     */
    public record Result(CompilationUnit unit, List<Diagnostic> diagnostics, boolean truncated) {

        public Result {
            diagnostics = List.copyOf(diagnostics);
        }

        /** Whether the file can go on to the next stage. */
        public boolean ok() {
            return this.diagnostics.stream().noneMatch(Diagnostic::isError);
        }

        /** The messages as the console prints them, one per line, plus a note if any were dropped. */
        public List<String> lines() {
            final List<String> lines = new ArrayList<>();
            for (final Diagnostic diagnostic : this.diagnostics) {
                lines.add(diagnostic.format());
            }
            if (this.truncated) {
                lines.add("too many errors; the rest were not reported");
            }
            return lines;
        }
    }

    /** Reads one file as far as it can, reporting everything wrong with it on the way. */
    public static Result parse(final SourceFile source) {
        final DiagnosticBag bag = new DiagnosticBag(source.name());
        final CompilationUnit unit = parse(source, bag);
        return new Result(unit, bag.sorted(), bag.wasCapped());
    }

    /**
     * Reads one file into a tree, reporting into a bag the caller owns.
     *
     * <p>This is the form the rest of the compiler uses, because a compilation can span several
     * files and everything they have to say belongs in one list, in one order.
     */
    public static CompilationUnit parse(final SourceFile source, final DiagnosticBag diagnostics) {
        final List<Token> tokens = new Lexer(source, diagnostics).tokenize();
        return new Parser(tokens, diagnostics).parse(source.name());
    }
}
