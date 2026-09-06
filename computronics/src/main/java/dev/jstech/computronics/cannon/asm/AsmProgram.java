/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.asm;

import dev.jstech.computronics.cannon.Shape;
import java.util.ArrayList;
import java.util.List;

/**
 * A whole compiled program: the types it holds and the class the runtime starts from.
 *
 * <p>The version at the head is the format's, not the program's. A runtime refuses a listing whose
 * major version is above the one it knows, because a listing from a later version may use
 * instructions it has never heard of, and guessing at those would be worse than saying so.
 */
public final class AsmProgram {

    /** The version of the format this build writes and reads. */
    public static final int VERSION = 1;

    private final int version;
    private final List<AsmType> types = new ArrayList<>();
    private String entryPoint;
    private Shape shape = Shape.SCRIPT;

    public AsmProgram() {
        this(VERSION);
    }

    public AsmProgram(final int version) {
        this.version = version;
    }

    /** The format version this listing was written in. */
    public int version() {
        return this.version;
    }

    /** The types, in the order they were written. */
    public List<AsmType> types() {
        return List.copyOf(this.types);
    }

    /** The type of that name, or null. */
    public AsmType type(final String name) {
        for (final AsmType type : this.types) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        return null;
    }

    /** The class the runtime starts the program from, or null for a library. */
    public String entryPoint() {
        return this.entryPoint;
    }

    /** Whether this is a program that runs at a terminal or one that stays up. */
    public Shape shape() {
        return this.shape;
    }

    /** Names the class the runtime starts from, and says which kind of program it is. */
    public void setEntryPoint(final String entryPoint, final Shape shape) {
        this.entryPoint = entryPoint;
        this.shape = shape;
    }

    /** Adds a type. */
    public void addType(final AsmType type) {
        this.types.add(type);
    }
}
