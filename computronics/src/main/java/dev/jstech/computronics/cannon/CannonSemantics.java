/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon;

import dev.jstech.computronics.cannon.ast.CompilationUnit;
import dev.jstech.computronics.cannon.sem.BodyChecker;
import dev.jstech.computronics.cannon.sem.BuiltIns;
import dev.jstech.computronics.cannon.sem.Declarations;
import dev.jstech.computronics.cannon.sem.SemanticModel;
import dev.jstech.computronics.cannon.sem.TypeRules;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a set of files and works out what they mean: the door into the middle of the compiler.
 *
 * <p>There are two ways in because there are two things a player might be compiling. A program is a
 * set of files with exactly one class the runtime can start, and that is what the compiler builds.
 * A set of classes on their own is still worth checking, which is what an editor does while the
 * program is half written, and asking it for an entry point it does not have yet would be noise.
 */
public final class CannonSemantics {

    private CannonSemantics() {
    }

    /** What checking produced: the model, and everything the compiler had to say about the files. */
    public record Result(SemanticModel model, List<Diagnostic> diagnostics, boolean truncated) {

        public Result {
            diagnostics = List.copyOf(diagnostics);
        }

        /** Whether the files can go on to the next stage. */
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

    /** Checks a set of classes: every type, every member and every body, but no entry point. */
    public static Result check(final List<SourceFile> sources) {
        return analyse(sources, false);
    }

    /** Checks a whole program, which also means it has exactly one class the runtime can start. */
    public static Result checkProgram(final List<SourceFile> sources) {
        return analyse(sources, true);
    }

    private static Result analyse(final List<SourceFile> sources, final boolean wholeProgram) {
        final DiagnosticBag bag = new DiagnosticBag(sources.isEmpty() ? "" : sources.getFirst().name());
        final List<CompilationUnit> units = new ArrayList<>();
        for (final SourceFile source : sources) {
            bag.setFile(source.name());
            units.add(CannonFrontEnd.parse(source, bag));
        }

        final SemanticModel model = new SemanticModel();
        // A tree the parser had to guess its way through says nothing reliable about types, so the
        // player gets the mistakes that are certainly there rather than the ones that follow from them.
        if (bag.hasErrors()) {
            return new Result(model, bag.sorted(), bag.wasCapped());
        }
        final BuiltIns builtIns = new BuiltIns();
        final TypeRules rules = new TypeRules(builtIns);
        final Declarations declarations = new Declarations(builtIns, rules, bag, model);
        declarations.declare(units);
        declarations.fill();
        declarations.checkInterfaces();
        new BodyChecker(builtIns, rules, declarations, bag, model).check(model.declaredTypes());
        if (wholeProgram) {
            bag.setFile(sources.isEmpty() ? "" : sources.getFirst().name());
            declarations.checkEntryPoint(1, 1);
        }
        return new Result(model, bag.sorted(), bag.wasCapped());
    }
}
