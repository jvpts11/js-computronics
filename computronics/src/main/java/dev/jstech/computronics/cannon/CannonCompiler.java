/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon;

import dev.jstech.computronics.cannon.asm.AsmProgram;
import dev.jstech.computronics.cannon.asm.AsmWriter;
import dev.jstech.computronics.cannon.asm.Emitter;
import java.util.ArrayList;
import java.util.List;

/**
 * The compiler, end to end: source files in, an assembly listing out.
 *
 * <p>This is what the shell command runs. Each stage only starts when the one before it found
 * nothing wrong, because a program that does not parse says nothing reliable about its types, and a
 * program whose types do not add up cannot be written down as instructions.
 */
public final class CannonCompiler {

    private CannonCompiler() {
    }

    /** What compiling produced: the listing when it worked, and everything the compiler had to say. */
    public record Result(String assembly, List<Diagnostic> diagnostics, boolean truncated) {

        public Result {
            diagnostics = List.copyOf(diagnostics);
        }

        /** Whether there is a listing to run. */
        public boolean ok() {
            return this.assembly != null && this.diagnostics.stream().noneMatch(Diagnostic::isError);
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

    /** Compiles a whole program, which is a set of files with one class the runtime can start. */
    public static Result compile(final List<SourceFile> sources) {
        final DiagnosticBag bag = new DiagnosticBag(sources.isEmpty() ? "" : sources.getFirst().name());
        final CannonSemantics.Analysis analysis = CannonSemantics.analyse(sources, bag, true);
        if (bag.hasErrors()) {
            return new Result(null, bag.sorted(), bag.wasCapped());
        }
        final AsmProgram program = new Emitter(analysis.model(), analysis.rules(), analysis.builtIns(),
                analysis.declarations(), bag).emit();
        if (bag.hasErrors()) {
            return new Result(null, bag.sorted(), bag.wasCapped());
        }
        return new Result(AsmWriter.write(program), bag.sorted(), bag.wasCapped());
    }
}
