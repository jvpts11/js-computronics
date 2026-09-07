/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.sem;

import dev.jstech.computronics.cannon.ast.Operator;
import java.util.ArrayList;
import java.util.List;

/**
 * What fits where: conversions, the type an operator produces, and what a collection holds.
 *
 * <p>The rules are deliberately few. Numbers widen along one chain and never narrow on their own, a
 * reference fits its base and nothing else, and there is no conversion an addon or a clever operator
 * can add. A player who reads the chain once knows the whole of it.
 *
 * <p>{@link ITypeSymbol.Special#ERROR} converts to and from everything on purpose: it is what a type
 * that could not be resolved becomes, and letting it pass silently keeps one unknown name from
 * producing a complaint about every line that used it.
 */
public final class TypeRules {

    /** The widening chain, in order. A type converts to any type further along it and to none before. */
    private static final List<ITypeSymbol.Primitive> WIDENING = List.of(
            ITypeSymbol.Primitive.CHAR,
            ITypeSymbol.Primitive.INT,
            ITypeSymbol.Primitive.LONG,
            ITypeSymbol.Primitive.FLOAT,
            ITypeSymbol.Primitive.DOUBLE);

    private final BuiltIns builtIns;

    public TypeRules(final BuiltIns builtIns) {
        this.builtIns = builtIns;
    }

    /** Whether this is the stand-in for a type that could not be resolved. */
    public boolean isError(final ITypeSymbol type) {
        return type == null || type == ITypeSymbol.Special.ERROR;
    }

    /** Whether arithmetic applies. */
    public boolean isNumeric(final ITypeSymbol type) {
        return type instanceof ITypeSymbol.Primitive primitive && primitive.isNumeric();
    }

    /** Whether the bitwise operators and the shifts apply. */
    public boolean isIntegral(final ITypeSymbol type) {
        return type == ITypeSymbol.Primitive.CHAR || type == ITypeSymbol.Primitive.INT
                || type == ITypeSymbol.Primitive.LONG;
    }

    /** Whether values of this type are references, so null fits and the object can be disposed. */
    public boolean isReference(final ITypeSymbol type) {
        if (type instanceof ITypeSymbol.ArrayType || type instanceof ITypeSymbol.GenericType) {
            return true;
        }
        return type instanceof NamedType named && named.kind() != NamedType.Kind.ENUM;
    }

    /** The named type behind a symbol, following a filled-in collection to its definition. */
    public NamedType named(final ITypeSymbol type) {
        if (type instanceof NamedType named) {
            return named;
        }
        if (type instanceof ITypeSymbol.GenericType generic) {
            return generic.definition();
        }
        return null;
    }

    /** The arguments a filled-in collection was given, empty for anything else. */
    public List<ITypeSymbol> arguments(final ITypeSymbol type) {
        return type instanceof ITypeSymbol.GenericType generic ? generic.arguments() : List.of();
    }

    /** Whether a value of {@code from} can be used where {@code to} is wanted. */
    public boolean isAssignable(final ITypeSymbol from, final ITypeSymbol to) {
        if (this.isError(from) || this.isError(to)) {
            return true;
        }
        if (from.equals(to)) {
            return true;
        }
        if (from == ITypeSymbol.Special.NULL) {
            return this.isReference(to);
        }
        if (this.isNumeric(from) && this.isNumeric(to)) {
            final int start = WIDENING.indexOf((ITypeSymbol.Primitive) from);
            final int end = WIDENING.indexOf((ITypeSymbol.Primitive) to);
            return start >= 0 && end >= 0 && start <= end;
        }
        if (to == this.builtIns.objectType() && this.isReference(from)) {
            return true;
        }
        if (from instanceof NamedType source && to instanceof NamedType target) {
            return source.isOrDescendsFrom(target);
        }
        return false;
    }

    /** Replaces a collection's stand-in arguments with what a use site filled them in with. */
    public ITypeSymbol substitute(final ITypeSymbol type, final List<ITypeSymbol> arguments) {
        if (type instanceof ITypeSymbol.TypeParameter parameter) {
            return parameter.index() < arguments.size()
                    ? arguments.get(parameter.index()) : ITypeSymbol.Special.ERROR;
        }
        if (type instanceof ITypeSymbol.ArrayType array) {
            return new ITypeSymbol.ArrayType(this.substitute(array.element(), arguments));
        }
        if (type instanceof ITypeSymbol.GenericType generic) {
            final List<ITypeSymbol> filled = new ArrayList<>();
            for (final ITypeSymbol argument : generic.arguments()) {
                filled.add(this.substitute(argument, arguments));
            }
            return new ITypeSymbol.GenericType(generic.definition(), filled);
        }
        return type;
    }

    /** What a two-operand expression produces, or null when the operator does not apply to them. */
    public ITypeSymbol binaryResult(final Operator operator, final ITypeSymbol left, final ITypeSymbol right) {
        if (this.isError(left) || this.isError(right)) {
            return ITypeSymbol.Special.ERROR;
        }
        return switch (operator) {
            case ADD -> left == this.builtIns.stringType() || right == this.builtIns.stringType()
                    ? this.builtIns.stringType() : this.promote(left, right);
            case SUBTRACT, MULTIPLY, DIVIDE, REMAINDER -> this.promote(left, right);
            case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL ->
                    this.promote(left, right) == null ? null : ITypeSymbol.Primitive.BOOL;
            case EQUAL, NOT_EQUAL -> this.comparable(left, right) ? ITypeSymbol.Primitive.BOOL : null;
            case AND, OR -> left == ITypeSymbol.Primitive.BOOL && right == ITypeSymbol.Primitive.BOOL
                    ? ITypeSymbol.Primitive.BOOL : null;
            case BIT_AND, BIT_OR, BIT_XOR -> {
                if (left == ITypeSymbol.Primitive.BOOL && right == ITypeSymbol.Primitive.BOOL) {
                    yield ITypeSymbol.Primitive.BOOL;
                }
                yield this.isIntegral(left) && this.isIntegral(right) ? this.promote(left, right) : null;
            }
            case SHIFT_LEFT, SHIFT_RIGHT -> this.isIntegral(left) && this.isIntegral(right)
                    ? this.widen(left) : null;
            default -> null;
        };
    }

    /** What a one-operand expression produces, or null when the operator does not apply. */
    public ITypeSymbol unaryResult(final Operator operator, final ITypeSymbol operand) {
        if (this.isError(operand)) {
            return ITypeSymbol.Special.ERROR;
        }
        return switch (operator) {
            case NOT -> operand == ITypeSymbol.Primitive.BOOL ? ITypeSymbol.Primitive.BOOL : null;
            case NEGATE, PLUS -> this.isNumeric(operand) ? this.widen(operand) : null;
            case COMPLEMENT -> this.isIntegral(operand) ? this.widen(operand) : null;
            case INCREMENT, DECREMENT -> this.isNumeric(operand) ? operand : null;
            default -> null;
        };
    }

    /** What a foreach walks over gives it, or null when the value cannot be walked. */
    public ITypeSymbol elementOf(final ITypeSymbol collection) {
        if (this.isError(collection)) {
            return ITypeSymbol.Special.ERROR;
        }
        if (collection instanceof ITypeSymbol.ArrayType array) {
            return array.element();
        }
        if (collection instanceof ITypeSymbol.GenericType generic
                && generic.definition() == this.builtIns.listType()) {
            return generic.arguments().getFirst();
        }
        return null;
    }

    /** What indexing gives, or null when the value cannot be indexed by that key. */
    public ITypeSymbol indexResult(final ITypeSymbol target, final ITypeSymbol index) {
        if (this.isError(target) || this.isError(index)) {
            return ITypeSymbol.Special.ERROR;
        }
        if (target instanceof ITypeSymbol.ArrayType array) {
            return this.isAssignable(index, ITypeSymbol.Primitive.INT) ? array.element() : null;
        }
        if (target instanceof ITypeSymbol.GenericType generic) {
            if (generic.definition() == this.builtIns.listType()) {
                return this.isAssignable(index, ITypeSymbol.Primitive.INT)
                        ? generic.arguments().getFirst() : null;
            }
            if (generic.definition() == this.builtIns.mapType()) {
                return this.isAssignable(index, generic.arguments().getFirst())
                        ? generic.arguments().get(1) : null;
            }
        }
        return null;
    }

    // Two values can be compared when one fits the other: numbers against numbers, a reference
    // against a reference it could be, and null against anything a reference.
    private boolean comparable(final ITypeSymbol left, final ITypeSymbol right) {
        if (this.isNumeric(left) && this.isNumeric(right)) {
            return true;
        }
        if (left.equals(right)) {
            return true;
        }
        return this.isAssignable(left, right) || this.isAssignable(right, left);
    }

    /**
     * The type two numbers meet in, or null when either is not a number.
     *
     * <p>The stage that emits the assembly asks for this as well: a comparison gives back a bool, so
     * the type its two sides were compared as cannot be read off the result and has to be worked out
     * the same way it was here.
     */
    public ITypeSymbol promote(final ITypeSymbol left, final ITypeSymbol right) {
        if (!this.isNumeric(left) || !this.isNumeric(right)) {
            return null;
        }
        final ITypeSymbol wideLeft = this.widen(left);
        final ITypeSymbol wideRight = this.widen(right);
        return WIDENING.indexOf((ITypeSymbol.Primitive) wideLeft) >= WIDENING.indexOf((ITypeSymbol.Primitive) wideRight)
                ? wideLeft : wideRight;
    }

    // Arithmetic on a char is arithmetic on its number, as it is in the language this one borrows from.
    private ITypeSymbol widen(final ITypeSymbol type) {
        return type == ITypeSymbol.Primitive.CHAR ? ITypeSymbol.Primitive.INT : type;
    }
}
