/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.asm;

import java.util.Objects;

/**
 * One line of a method.
 *
 * <p>A label marks the line so a branch can name it, and a comment is there for the player reading
 * the listing rather than for the machine, which is the whole point of the assembly being text.
 */
public record Instruction(String label, Opcode opcode, Operand operand, String comment) {

    public Instruction {
        Objects.requireNonNull(opcode, "opcode");
    }

    /** An instruction that carries nothing else. */
    public static Instruction of(final Opcode opcode) {
        return new Instruction(null, opcode, null, null);
    }

    /** An instruction with its operand. */
    public static Instruction of(final Opcode opcode, final Operand operand) {
        return new Instruction(null, opcode, operand, null);
    }

    /** The same instruction, marked so a branch can jump to it. */
    public Instruction labelled(final String label) {
        return new Instruction(label, this.opcode, this.operand, this.comment);
    }

    /** The same instruction with a note for whoever reads the listing. */
    public Instruction saying(final String comment) {
        return new Instruction(this.label, this.opcode, this.operand, comment);
    }
}
