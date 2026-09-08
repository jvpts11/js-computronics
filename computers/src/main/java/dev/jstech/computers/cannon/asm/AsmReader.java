/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon.asm;

import dev.jstech.computers.cannon.CannonError;
import dev.jstech.computers.cannon.DiagnosticBag;
import dev.jstech.computers.cannon.Shape;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads a listing back into a program.
 *
 * <p>The runtime loads this text, not something else the compiler kept to itself, so a player can
 * open a file, follow what it does, and know that is what runs. That only holds if the text can be
 * read back exactly, which is what this does.
 *
 * <p>Like the rest of the compiler it never throws: a line it cannot read is reported and the next
 * one is tried, so a file mangled in an editor says everything that is wrong with it at once.
 */
public final class AsmReader {

    private final String[] lines;
    private final DiagnosticBag diagnostics;

    private AsmType type;
    private AsmMethod.Builder method;

    public AsmReader(final String text, final DiagnosticBag diagnostics) {
        this.lines = text.split("\n", -1);
        this.diagnostics = diagnostics;
    }

    /** Reads the whole listing. Always gives back a program, even one nothing could be read into. */
    public AsmProgram read() {
        int at = 0;
        while (at < this.lines.length && code(this.lines[at]).isBlank()) {
            at++;
        }
        if (at >= this.lines.length || !code(this.lines[at]).trim().startsWith(".asm ")) {
            this.diagnostics.error(1, 1, CannonError.MISSING_VERSION_LINE);
            return new AsmProgram();
        }
        final String versionText = code(this.lines[at]).trim().substring(".asm ".length()).trim();
        final int version = number(versionText, at + 1, -1);
        if (version > AsmProgram.VERSION) {
            this.diagnostics.error(at + 1, 1, CannonError.VERSION_TOO_NEW, AsmProgram.VERSION, version);
            return new AsmProgram(version);
        }
        final AsmProgram program = new AsmProgram(version < 0 ? AsmProgram.VERSION : version);
        for (int line = at + 1; line < this.lines.length; line++) {
            this.readLine(program, this.lines[line], line + 1);
        }
        this.closeMethod();
        return program;
    }

    private void readLine(final AsmProgram program, final String raw, final int line) {
        final String code = code(raw).stripTrailing();
        if (code.isBlank()) {
            return;
        }
        final String trimmed = code.trim();
        if (trimmed.startsWith(".")) {
            this.readDirective(program, trimmed, line);
            return;
        }
        this.readInstruction(trimmed, comment(raw), line);
    }

    private void readDirective(final AsmProgram program, final String text, final int line) {
        final int space = text.indexOf(' ');
        final String word = space < 0 ? text.substring(1) : text.substring(1, space);
        final String rest = space < 0 ? "" : text.substring(space + 1).trim();
        switch (word) {
            case "start" -> {
                /*
                 * "<Type> <shape>"; a listing that names no shape is a script, which is what the only
                 * kind of program there used to be would have been.
                 */
                final int split = rest.indexOf(' ');
                program.setEntryPoint(split < 0 ? rest : rest.substring(0, split),
                        Shape.of(split < 0 ? "" : rest.substring(split + 1).trim()));
            }
            case "class", "interface", "enum" -> {
                this.closeMethod();
                this.type = this.readTypeHead(AsmType.Kind.written(word), rest);
                program.addType(this.type);
            }
            case "delegate" -> {
                this.closeMethod();
                this.type = this.readDelegate(rest, line);
                program.addType(this.type);
            }
            case "field" -> this.readField(rest, line);
            case "event" -> this.readEvent(rest, line);
            case "value" -> this.readValue(rest, line);
            case "method" -> this.readMethodHead(rest, line);
            default -> this.diagnostics.error(line, 1, CannonError.UNKNOWN_DIRECTIVE, "." + word);
        }
    }

    private AsmType readTypeHead(final AsmType.Kind kind, final String rest) {
        final int colon = rest.indexOf(':');
        final AsmType read = new AsmType(kind, (colon < 0 ? rest : rest.substring(0, colon)).trim());
        if (colon >= 0) {
            for (final String base : splitTypes(rest.substring(colon + 1))) {
                read.addBase(base);
            }
        }
        return read;
    }

    /*
     * ".delegate bool Finder(string, out int)" carries the whole shape on one line, because a
     * delegate is a shape and nothing else.
     */
    private AsmType readDelegate(final String rest, final int line) {
        final int open = rest.indexOf('(');
        final int close = rest.lastIndexOf(')');
        if (open < 0 || close < open) {
            this.diagnostics.error(line, 1, CannonError.MALFORMED_OPERAND, rest, ".delegate");
            return new AsmType(AsmType.Kind.DELEGATE, rest);
        }
        final int nameStart = nameStart(rest, open);
        final AsmType read = new AsmType(AsmType.Kind.DELEGATE, rest.substring(nameStart, open).trim());
        read.setInvoke(new AsmMethod("Invoke", rest.substring(0, nameStart).trim(),
                splitTypes(rest.substring(open + 1, close)), false, 0, null));
        return read;
    }

    private void readField(final String rest, final int line) {
        if (this.outsideType(".field", line)) {
            return;
        }
        final boolean isStatic = rest.startsWith("static ");
        final String body = isStatic ? rest.substring("static ".length()).trim() : rest;
        final int split = body.lastIndexOf(' ');
        if (split < 0) {
            this.diagnostics.error(line, 1, CannonError.MALFORMED_OPERAND, rest, ".field");
            return;
        }
        this.type.addField(new AsmType.Field(body.substring(split + 1).trim(),
                body.substring(0, split).trim(), isStatic));
    }

    private void readEvent(final String rest, final int line) {
        if (this.outsideType(".event", line)) {
            return;
        }
        final int split = rest.lastIndexOf(' ');
        if (split < 0) {
            this.diagnostics.error(line, 1, CannonError.MALFORMED_OPERAND, rest, ".event");
            return;
        }
        this.type.addEvent(new AsmType.Event(rest.substring(split + 1).trim(),
                rest.substring(0, split).trim()));
    }

    private void readValue(final String rest, final int line) {
        if (this.outsideType(".value", line)) {
            return;
        }
        final int equals = rest.indexOf('=');
        if (equals < 0) {
            this.diagnostics.error(line, 1, CannonError.MALFORMED_OPERAND, rest, ".value");
            return;
        }
        this.type.addValue(new AsmType.Value(rest.substring(0, equals).trim(),
                this.number(rest.substring(equals + 1).trim(), line, 0)));
    }

    private void readMethodHead(final String rest, final int line) {
        this.closeMethod();
        if (this.outsideType(".method", line)) {
            return;
        }
        final boolean isStatic = rest.startsWith("static ");
        String body = isStatic ? rest.substring("static ".length()).trim() : rest;
        int slots = 0;
        boolean hasBody = false;
        final int slotsAt = body.lastIndexOf(" slots ");
        if (slotsAt >= 0) {
            slots = this.number(body.substring(slotsAt + " slots ".length()).trim(), line, 0);
            body = body.substring(0, slotsAt).trim();
            hasBody = true;
        }
        final int open = body.indexOf('(');
        final int close = body.lastIndexOf(')');
        if (open < 0 || close < open) {
            this.diagnostics.error(line, 1, CannonError.MALFORMED_OPERAND, rest, ".method");
            return;
        }
        final int nameStart = nameStart(body, open);
        this.method = new AsmMethod.Builder(body.substring(nameStart, open).trim(),
                body.substring(0, nameStart).trim(), splitTypes(body.substring(open + 1, close)),
                isStatic, slots, hasBody);
    }

    private void readInstruction(final String text, final String comment, final int line) {
        if (this.method == null) {
            this.diagnostics.error(line, 1, CannonError.INSTRUCTION_OUTSIDE_METHOD);
            return;
        }
        String rest = text;
        String label = null;
        final int colon = rest.indexOf(':');
        final int space = rest.indexOf(' ');
        if (colon > 0 && (space < 0 || colon < space)) {
            label = rest.substring(0, colon).trim();
            rest = rest.substring(colon + 1).trim();
        }
        final int split = rest.indexOf(' ');
        final String word = split < 0 ? rest : rest.substring(0, split);
        final String operandText = split < 0 ? "" : rest.substring(split + 1).trim();
        final Opcode opcode = Opcode.written(word);
        if (opcode == null) {
            this.diagnostics.error(line, 1, CannonError.UNKNOWN_INSTRUCTION, word);
            return;
        }
        if (!opcode.takesOperand() && !operandText.isEmpty()) {
            this.diagnostics.error(line, 1, CannonError.UNEXPECTED_OPERAND, word);
            return;
        }
        if (opcode.takesOperand() && operandText.isEmpty()) {
            this.diagnostics.error(line, 1, CannonError.MISSING_OPERAND, word);
            return;
        }
        final IOperand operand = opcode.takesOperand()
                ? this.readOperand(opcode, operandText, line) : null;
        if (opcode.takesOperand() && operand == null) {
            return;
        }
        this.method.add(new Instruction(label, opcode, operand, comment), line);
    }

    private IOperand readOperand(final Opcode opcode, final String text, final int line) {
        try {
            return switch (opcode.shape()) {
                case I4, SLOT -> {
                    final int value = Integer.parseInt(text);
                    yield opcode.shape() == Opcode.Shape.SLOT
                            ? new IOperand.Slot(value) : new IOperand.I4(value);
                }
                case I8 -> new IOperand.I8(Long.parseLong(text));
                case R4 -> new IOperand.R4(Float.parseFloat(text));
                case R8 -> new IOperand.R8(Double.parseDouble(text));
                case TEXT -> new IOperand.Text(unquote(text));
                case LABEL -> new IOperand.Label(text);
                case FIELD -> {
                    final int dot = text.lastIndexOf('.');
                    yield dot < 0 ? new IOperand.Field(null, text)
                            : new IOperand.Field(text.substring(0, dot), text.substring(dot + 1));
                }
                case METHOD -> this.readMethodOperand(text, line);
                case CONSTRUCTOR -> this.readConstructorOperand(text, line);
                default -> new IOperand.Type(text);
            };
        } catch (final NumberFormatException notANumber) {
            this.diagnostics.error(line, 1, CannonError.MALFORMED_OPERAND, text, opcode.text());
            return null;
        }
    }

    private IOperand readMethodOperand(final String text, final int line) {
        final int open = text.indexOf('(');
        final int close = text.indexOf(')', open + 1);
        final int arrow = text.indexOf("->", close < 0 ? 0 : close);
        if (open < 0 || close < 0 || arrow < 0) {
            this.diagnostics.error(line, 1, CannonError.MALFORMED_OPERAND, text, "call");
            return null;
        }
        final String head = text.substring(0, open);
        final int dot = head.lastIndexOf('.');
        if (dot < 0) {
            this.diagnostics.error(line, 1, CannonError.MALFORMED_OPERAND, text, "call");
            return null;
        }
        return new IOperand.Method(head.substring(0, dot).trim(), head.substring(dot + 1).trim(),
                splitTypes(text.substring(open + 1, close)), text.substring(arrow + 2).trim());
    }

    private IOperand readConstructorOperand(final String text, final int line) {
        final int open = text.indexOf('(');
        final int close = text.lastIndexOf(')');
        if (open < 0 || close < open) {
            this.diagnostics.error(line, 1, CannonError.MALFORMED_OPERAND, text, "newobj");
            return null;
        }
        return new IOperand.Constructor(text.substring(0, open).trim(),
                splitTypes(text.substring(open + 1, close)));
    }

    private boolean outsideType(final String directive, final int line) {
        if (this.type == null) {
            this.diagnostics.error(line, 1, CannonError.DIRECTIVE_OUTSIDE_TYPE, directive);
            return true;
        }
        return false;
    }

    private void closeMethod() {
        if (this.method != null && this.type != null) {
            this.checkLabels(this.method);
            this.type.addMethod(this.method.build());
        }
        this.method = null;
    }

    /*
     * A branch that names a label nothing carries would send the runtime nowhere, so it is caught
     * here, where the line it was written on is still known.
     */
    private void checkLabels(final AsmMethod.Builder built) {
        final Set<String> marked = new HashSet<>();
        for (final Instruction instruction : built.instructions()) {
            if (instruction.label() != null) {
                marked.add(instruction.label());
            }
        }
        final List<Instruction> instructions = built.instructions();
        for (int i = 0; i < instructions.size(); i++) {
            if (instructions.get(i).operand() instanceof IOperand.Label target
                    && !marked.contains(target.name())) {
                this.diagnostics.error(built.lineOf(i), 1, CannonError.UNKNOWN_LABEL, target.name());
            }
        }
    }

    private int number(final String text, final int line, final int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (final NumberFormatException notANumber) {
            this.diagnostics.error(line, 1, CannonError.MALFORMED_OPERAND, text, "a whole number");
            return fallback;
        }
    }

    // Everything after a semicolon is for the reader, unless the semicolon is inside a piece of text.
    private static String code(final String line) {
        final int end = commentStart(line);
        return end < 0 ? line : line.substring(0, end);
    }

    private static String comment(final String line) {
        final int start = commentStart(line);
        return start < 0 ? null : line.substring(start + 1).trim();
    }

    private static int commentStart(final String line) {
        boolean inText = false;
        for (int i = 0; i < line.length(); i++) {
            final char c = line.charAt(i);
            if (c == '\\' && inText) {
                i++;
            } else if (c == '"') {
                inText = !inText;
            } else if (c == ';' && !inText) {
                return i;
            }
        }
        return -1;
    }

    // A comma inside angle brackets belongs to the type it is in, not to the list around it.
    private static List<String> splitTypes(final String text) {
        final List<String> types = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                add(types, text.substring(start, i));
                start = i + 1;
            }
        }
        add(types, text.substring(start));
        return types;
    }

    private static void add(final List<String> types, final String value) {
        if (!value.isBlank()) {
            types.add(value.trim());
        }
    }

    // The name of a method or a delegate is the word just before its brackets.
    private static int nameStart(final String text, final int open) {
        int at = open - 1;
        while (at >= 0 && (Character.isLetterOrDigit(text.charAt(at)) || text.charAt(at) == '_')) {
            at--;
        }
        return at + 1;
    }

    private static String unquote(final String text) {
        if (text.length() < 2 || text.charAt(0) != '"' || text.charAt(text.length() - 1) != '"') {
            return text;
        }
        final StringBuilder value = new StringBuilder();
        for (int i = 1; i < text.length() - 1; i++) {
            final char c = text.charAt(i);
            if (c != '\\' || i + 1 >= text.length() - 1) {
                value.append(c);
                continue;
            }
            i++;
            value.append(switch (text.charAt(i)) {
                case 'n' -> '\n';
                case 't' -> '\t';
                case 'r' -> '\r';
                case '0' -> '\0';
                default -> text.charAt(i);
            });
        }
        return value.toString();
    }
}
