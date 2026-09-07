/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.machine;

import dev.jstech.computronics.JsComputronics;
import dev.jstech.computronics.cannon.CannonCompiler;
import dev.jstech.computronics.cannon.Diagnostic;
import dev.jstech.computronics.cannon.DiagnosticBag;
import dev.jstech.computronics.cannon.Shape;
import dev.jstech.computronics.cannon.SourceFile;
import dev.jstech.computronics.cannon.asm.AsmProgram;
import dev.jstech.computronics.cannon.asm.AsmReader;
import dev.jstech.computronics.cannon.lex.Lexer;
import dev.jstech.computronics.cannon.run.Loaded;
import dev.jstech.computronics.cannon.run.Process;
import dev.jstech.computronics.cannon.run.Values;
import dev.jstech.computronics.cannon.save.SnapshotTag;
import dev.jstech.core.language.ILanguageProcess;
import dev.jstech.core.language.IProgrammingLanguage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Cannon, as the machines of the series know it.
 *
 * <p>Everything specific to the language stays behind this: the machine asks for a program and gets
 * something it can give a share of the tick to, and knows nothing of scripts, heaps or instructions. A
 * pack that would rather its computers spoke something else takes this out of the registry and puts its
 * own in, and every part of the machines carries on working.
 */
public final class CannonLanguage implements IProgrammingLanguage {

    /** The one instance; the registry holds it and everything else asks the registry. */
    public static final CannonLanguage INSTANCE = new CannonLanguage();

    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "cannon");

    private CannonLanguage() {
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Cannon";
    }

    @Override
    public Set<String> sourceExtensions() {
        return Set.of("can");
    }

    @Override
    public Set<String> binaryExtensions() {
        return Set.of("asm");
    }

    @Override
    public CompileResult compile(final List<SourceText> sources) {
        final List<SourceFile> files = new ArrayList<>();
        for (final SourceText source : sources) {
            files.add(new SourceFile(source.name(), source.text()));
        }
        final CannonCompiler.Result built = CannonCompiler.compile(files);
        if (built.ok()) {
            return CompileResult.of(built.assembly());
        }
        final List<Complaint> complaints = new ArrayList<>();
        for (final Diagnostic one : built.diagnostics()) {
            complaints.add(new Complaint(one.file(), one.line(), one.column(), one.code(), one.message()));
        }
        return CompileResult.failed(complaints);
    }

    @Override
    public List<IProgrammingLanguage.Token> tokenize(final String text) {
        /*
         * An editor mostly colours text that does not compile, so whatever the lexer complains about is
         * thrown away and the pieces it did make sense of are handed back.
         */
        final DiagnosticBag bag = new DiagnosticBag("editor");
        final List<IProgrammingLanguage.Token> out = new ArrayList<>();
        /*
         * Both Token and Kind are names this interface itself declares, so the language's own are
         * written out in full rather than imported into a fight with them.
         */
        for (final dev.jstech.computronics.cannon.lex.Token token
                : new Lexer(new SourceFile("editor", text), bag).tokenize()) {
            if (token.kind() == dev.jstech.computronics.cannon.lex.TokenKind.END_OF_FILE) {
                break;
            }
            out.add(new IProgrammingLanguage.Token(token.line(), token.column(),
                    token.text().length(), kindOf(token.kind())));
        }
        return out;
    }

    /** What an editor should paint that piece of text as. */
    private static Kind kindOf(final dev.jstech.computronics.cannon.lex.TokenKind kind) {
        if (kind.isKeyword()) {
            return Kind.KEYWORD;
        }
        return switch (kind) {
            case IDENTIFIER -> Kind.NAME;
            case STRING_LITERAL, CHAR_LITERAL -> Kind.TEXT;
            case INT_LITERAL, LONG_LITERAL, FLOAT_LITERAL, DOUBLE_LITERAL -> Kind.NUMBER;
            default -> Kind.SYMBOL;
        };
    }

    @Override
    @Nullable
    public ILanguageProcess start(final String binary, final long heapBytes, final BlockEntity machine) {
        final Loaded program = read(binary);
        if (program == null || program.entryPoint() == null) {
            return null;
        }
        final Process process = new Process(program, heapBytes, new MachineHost(machine));
        if (program.shape() == Shape.CONSOLE) {
            process.beginStatic(program.entryPoint(), "Main");
        } else {
            final Values.Obj script = process.create(program.entryPoint());
            if (script == null) {
                return null;
            }
            process.begin(script, "OnInit");
        }
        return new CannonProgram(process);
    }

    @Override
    @Nullable
    public ILanguageProcess restore(final String binary, final CompoundTag saved, final BlockEntity machine) {
        final Loaded program = read(binary);
        if (program == null) {
            return null;
        }
        return new CannonProgram(Process.restore(program,
                SnapshotTag.read(CannonProgram.snapshotOf(saved)), new MachineHost(machine)));
    }

    /** Reads a listing, or null when it is not one. */
    @Nullable
    private static Loaded read(final String binary) {
        final DiagnosticBag bag = new DiagnosticBag("listing");
        final AsmProgram program = new AsmReader(binary, bag).read();
        return bag.hasErrors() ? null : Loaded.of(program);
    }
}
