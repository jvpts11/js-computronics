/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon;

import dev.jstech.computers.cannon.ast.CompilationUnit;
import dev.jstech.computers.cannon.sem.BodyChecker;
import dev.jstech.computers.cannon.sem.BuiltIns;
import dev.jstech.computers.cannon.sem.Declarations;
import dev.jstech.computers.cannon.sem.SemanticModel;
import dev.jstech.computers.cannon.sem.TypeRules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * What checking produced: the model, everything the compiler had to say about the files, and the
     * tree read from each file, by the file's name.
     */
    public record Result(SemanticModel model, List<Diagnostic> diagnostics, boolean truncated,
                         Map<String, CompilationUnit> units) {

        public Result {
            diagnostics = List.copyOf(diagnostics);
            units = Map.copyOf(units);
        }

        /** Whether the files can go on to the next stage. */
        public boolean ok() {
            return this.diagnostics.stream().noneMatch(Diagnostic::isError);
        }

        /** The tree read from the file called {@code name}, or null when no file had that name. */
        public CompilationUnit unit(final String name) {
            return this.units.get(name);
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

    /**
     * Checks what it can of sources that may not parse, for an editor.
     *
     * <p>A file being typed is broken most of the time, and an editor still has to say what the
     * program's types are and what their members take. The parser leaves out what it could not read
     * and keeps the rest, so the checker runs over that; what it says about the mistakes is less
     * certain than after a clean parse, which is why the compiler proper stops at the parser.
     */
    public static Result checkTolerant(final List<SourceFile> sources) {
        final DiagnosticBag bag = new DiagnosticBag(sources.isEmpty() ? "" : sources.getFirst().name());
        return result(sources, bag, analyse(sources, bag, false, true));
    }

    private static Result analyse(final List<SourceFile> sources, final boolean wholeProgram) {
        final DiagnosticBag bag = new DiagnosticBag(sources.isEmpty() ? "" : sources.getFirst().name());
        return result(sources, bag, analyse(sources, bag, wholeProgram));
    }

    private static Result result(final List<SourceFile> sources, final DiagnosticBag bag, final Analysis analysis) {
        final Map<String, CompilationUnit> units = new LinkedHashMap<>();
        for (int i = 0; i < sources.size() && i < analysis.units().size(); i++) {
            units.putIfAbsent(sources.get(i).name(), analysis.units().get(i));
        }
        return new Result(analysis.model(), bag.sorted(), bag.wasCapped(), units);
    }

    /**
     * Everything the middle of the compiler built, for the stage that writes the assembly.
     *
     * <p>The stage after this one needs more than the model: it has to resolve a type it meets in a
     * cast, and ask what two numbers meet in, which is what these carry. The trees come along, one per
     * source in the order the sources were given, for an editor asking where in a file a caret is.
     */
    record Analysis(SemanticModel model, BuiltIns builtIns, TypeRules rules, Declarations declarations,
                    List<CompilationUnit> units) {
    }

    /** Reads and checks into a bag the caller owns, and hands back what the next stage needs. */
    static Analysis analyse(final List<SourceFile> sources, final DiagnosticBag bag,
                            final boolean wholeProgram) {
        return analyse(sources, bag, wholeProgram, false);
    }

    private static Analysis analyse(final List<SourceFile> sources, final DiagnosticBag bag,
                                    final boolean wholeProgram, final boolean tolerant) {
        final List<CompilationUnit> units = new ArrayList<>();
        for (final SourceFile source : sources) {
            bag.setFile(source.name());
            units.add(CannonFrontEnd.parse(source, bag));
        }

        final SemanticModel model = new SemanticModel();
        final BuiltIns builtIns = new BuiltIns();
        final TypeRules rules = new TypeRules(builtIns);
        final Declarations declarations = new Declarations(builtIns, rules, bag, model);
        /*
         * A tree the parser had to guess its way through says nothing reliable about types, so the
         * player gets the mistakes that are certainly there rather than the ones that follow from them.
         * An editor asks anyway, since a file being typed is that tree most of the time.
         */
        if (bag.hasErrors() && !tolerant) {
            return new Analysis(model, builtIns, rules, declarations, units);
        }
        declarations.declare(units);
        declarations.fill();
        declarations.checkInterfaces();
        new BodyChecker(builtIns, rules, declarations, bag, model).check(model.declaredTypes());
        if (wholeProgram) {
            bag.setFile(sources.isEmpty() ? "" : sources.getFirst().name());
            declarations.checkEntryPoint(1, 1);
        }
        return new Analysis(model, builtIns, rules, declarations, units);
    }
}
