/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.asm;

/**
 * Writes a program out as the text a player can open and read.
 *
 * <p>The compiler does not hide what it made. The listing is laid out in columns so a run of
 * instructions can be followed down the page, and it is the same text the runtime loads, so what is
 * read is exactly what runs.
 */
public final class AsmWriter {

    /** How wide the space for a label is, before the instruction begins. */
    private static final int LABEL_WIDTH = 4;

    /** How wide the space for an instruction's name is, before its operand begins. */
    private static final int OPCODE_WIDTH = 8;

    /** Where a note to the reader starts, so notes line up down the page. */
    private static final int COMMENT_COLUMN = 45;

    private AsmWriter() {
    }

    /** The whole program as text. */
    public static String write(final AsmProgram program) {
        final StringBuilder text = new StringBuilder();
        text.append(".asm ").append(program.version()).append('\n');
        if (program.entryPoint() != null) {
            text.append(".start ").append(program.entryPoint()).append('\n');
        }
        for (final AsmType type : program.types()) {
            text.append('\n');
            writeType(text, type);
        }
        return text.toString();
    }

    private static void writeType(final StringBuilder text, final AsmType type) {
        if (type.kind() == AsmType.Kind.DELEGATE) {
            final AsmMethod invoke = type.invoke();
            text.append(".delegate ").append(invoke == null ? "void" : invoke.returns())
                    .append(' ').append(type.name()).append('(')
                    .append(invoke == null ? "" : String.join(", ", invoke.parameters())).append(")\n");
            return;
        }
        text.append('.').append(type.kind().text()).append(' ').append(type.name());
        if (!type.bases().isEmpty()) {
            text.append(" : ").append(String.join(", ", type.bases()));
        }
        text.append('\n');
        for (final AsmType.Value value : type.values()) {
            text.append(".value ").append(value.name()).append(" = ").append(value.number()).append('\n');
        }
        for (final AsmType.Field field : type.fields()) {
            text.append(".field ").append(field.isStatic() ? "static " : "")
                    .append(field.type()).append(' ').append(field.name()).append('\n');
        }
        for (final AsmType.Event event : type.events()) {
            text.append(".event ").append(event.type()).append(' ').append(event.name()).append('\n');
        }
        for (final AsmMethod method : type.methods()) {
            writeMethod(text, method);
        }
    }

    private static void writeMethod(final StringBuilder text, final AsmMethod method) {
        text.append('\n').append(".method ").append(method.isStatic() ? "static " : "")
                .append(method.signature());
        if (method.hasBody()) {
            text.append(" slots ").append(method.slots());
        }
        text.append('\n');
        if (!method.hasBody()) {
            return;
        }
        for (final Instruction instruction : method.body()) {
            writeInstruction(text, instruction);
        }
    }

    private static void writeInstruction(final StringBuilder text, final Instruction instruction) {
        final int start = text.length();
        text.append(pad(instruction.label() == null ? "" : instruction.label() + ":", LABEL_WIDTH));
        text.append(pad(instruction.opcode().text(), OPCODE_WIDTH));
        if (instruction.operand() != null) {
            text.append(instruction.operand().write());
        }
        if (instruction.comment() != null) {
            while (text.length() - start < COMMENT_COLUMN) {
                text.append(' ');
            }
            text.append("; ").append(instruction.comment());
        }
        // Nothing on a line but padding is noise, so a line that ends in spaces loses them.
        while (text.length() > start && text.charAt(text.length() - 1) == ' ') {
            text.setLength(text.length() - 1);
        }
        text.append('\n');
    }

    private static String pad(final String value, final int width) {
        if (value.length() >= width) {
            return value + " ";
        }
        return value + " ".repeat(width - value.length());
    }
}
