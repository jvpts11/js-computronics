/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.asm;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Every instruction of the assembly, and the one operand each of them takes.
 *
 * <p>The machine works on a stack: an instruction takes what it needs off the top and leaves its
 * result there. That is what keeps the listing short enough to read down a screen, and it is why
 * there is no instruction here that names two places at once.
 *
 * <p>Every instruction costs one from a process's budget for the tick, except a call into the
 * library or into the world, which costs what that call is documented to cost.
 */
public enum Opcode {

    LDC_I4("ldc.i4", Shape.I4),
    LDC_I8("ldc.i8", Shape.I8),
    LDC_R4("ldc.r4", Shape.R4),
    LDC_R8("ldc.r8", Shape.R8),
    LDSTR("ldstr", Shape.TEXT),
    LDNULL("ldnull", Shape.NONE),

    LDLOC("ldloc", Shape.SLOT),
    STLOC("stloc", Shape.SLOT),
    LDFLD("ldfld", Shape.FIELD),
    STFLD("stfld", Shape.FIELD),
    LDSFLD("ldsfld", Shape.FIELD),
    STSFLD("stsfld", Shape.FIELD),
    LDTHIS("ldthis", Shape.NONE),

    ADD("add", Shape.NONE),
    SUB("sub", Shape.NONE),
    MUL("mul", Shape.NONE),
    DIV("div", Shape.NONE),
    REM("rem", Shape.NONE),
    NEG("neg", Shape.NONE),
    AND("and", Shape.NONE),
    OR("or", Shape.NONE),
    XOR("xor", Shape.NONE),
    NOT("not", Shape.NONE),
    SHL("shl", Shape.NONE),
    SHR("shr", Shape.NONE),

    CONV_I4("conv.i4", Shape.NONE),
    CONV_I8("conv.i8", Shape.NONE),
    CONV_R4("conv.r4", Shape.NONE),
    CONV_R8("conv.r8", Shape.NONE),

    CEQ("ceq", Shape.NONE),
    CLT("clt", Shape.NONE),
    CGT("cgt", Shape.NONE),
    BR("br", Shape.LABEL),
    BRTRUE("brtrue", Shape.LABEL),
    BRFALSE("brfalse", Shape.LABEL),
    BEQ("beq", Shape.LABEL),
    BNE("bne", Shape.LABEL),
    BLT("blt", Shape.LABEL),
    BLE("ble", Shape.LABEL),
    BGT("bgt", Shape.LABEL),
    BGE("bge", Shape.LABEL),

    NEWOBJ("newobj", Shape.CONSTRUCTOR),
    NEWARR("newarr", Shape.TYPE),
    LDELEM("ldelem", Shape.NONE),
    STELEM("stelem", Shape.NONE),
    LDLEN("ldlen", Shape.NONE),
    DISPOSE("dispose", Shape.NONE),
    CASTCLASS("castclass", Shape.TYPE),
    ISINST("isinst", Shape.TYPE),

    CALL("call", Shape.METHOD),
    CALLVIRT("callvirt", Shape.METHOD),
    SYS("sys", Shape.TEXT),
    RET("ret", Shape.NONE),

    POP("pop", Shape.NONE),
    DUP("dup", Shape.NONE);

    /** What kind of operand an instruction takes, if any. */
    public enum Shape {
        NONE,
        I4,
        I8,
        R4,
        R8,
        TEXT,
        SLOT,
        LABEL,
        FIELD,
        METHOD,
        CONSTRUCTOR,
        TYPE
    }

    private static final Map<String, Opcode> BY_NAME;

    static {
        final Map<String, Opcode> names = new HashMap<>();
        for (final Opcode opcode : values()) {
            names.put(opcode.text, opcode);
        }
        BY_NAME = Collections.unmodifiableMap(names);
    }

    private final String text;
    private final Shape shape;

    Opcode(final String text, final Shape shape) {
        this.text = text;
        this.shape = shape;
    }

    /** The instruction written that way, or null. */
    public static Opcode written(final String text) {
        return BY_NAME.get(text);
    }

    /** How the instruction is written in a listing. */
    public String text() {
        return this.text;
    }

    /** What it takes after its name. */
    public Shape shape() {
        return this.shape;
    }

    /** Whether it takes anything at all. */
    public boolean takesOperand() {
        return this.shape != Shape.NONE;
    }

    /** Whether it can move somewhere other than the next instruction. */
    public boolean branches() {
        return this.shape == Shape.LABEL;
    }
}
