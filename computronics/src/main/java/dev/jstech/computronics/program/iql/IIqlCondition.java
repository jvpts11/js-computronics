/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program.iql;

import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The boolean tree behind an IQL {@code WHERE} filter or an {@code IF}/{@code WHEN} guard. A leaf is a
 * {@link Comparison} of a field against a value; the rest compose with {@link And}/{@link Or}/{@link Not}.
 *
 * <p>Pure logic, free of Minecraft: {@link #matches} evaluates the tree against a {@code row} — a
 * function from field name to its string value (an absent field returns {@code null}). The server fills
 * that function from a real item entry at execution time; the parser and tests build and evaluate the
 * tree without a game loaded.
 */
public sealed interface IIqlCondition
        permits IIqlCondition.Comparison, IIqlCondition.And, IIqlCondition.Or, IIqlCondition.Not {

    /** Evaluates this condition against a row (field name → value; {@code null} when the field is absent). */
    boolean matches(Function<String, String> row);

    /** The comparison operators IQL exposes. */
    enum Op { EQ, NEQ, LT, GT, LTE, GTE, CONTAINS, HAS, LIKE }

    /**
     * The value compared against {@code field} by the first comparison on that field in {@code tree}
     * (any operator, left to right); {@code null} when the field is absent or the tree is {@code null}.
     * Lets an executor read a simple filter (e.g. the {@code name} in {@code WHERE name = "x"}) out of a
     * parsed condition without walking the whole boolean tree by hand.
     */
    static String firstValue(final IIqlCondition tree, final String field) {
        return switch (tree) {
            case null -> null;
            case Comparison c -> c.field().equalsIgnoreCase(field) ? c.value() : null;
            case And a -> coalesce(firstValue(a.left(), field), firstValue(a.right(), field));
            case Or o -> coalesce(firstValue(o.left(), field), firstValue(o.right(), field));
            case Not n -> firstValue(n.inner(), field);
        };
    }

    private static String coalesce(final String first, final String second) {
        return first != null ? first : second;
    }

    /**
     * A simple item-name filter pulled from a read's {@code WHERE} ({@code name} or, failing that,
     * {@code item}), or {@code ""} when neither is present. The basis for filtering a {@code QUERY items}
     * until the full Object Explorer wiring lands.
     */
    static String itemNameFilter(final IIqlCondition tree) {
        final String byName = firstValue(tree, "name");
        if (byName != null) {
            return byName;
        }
        final String byItem = firstValue(tree, "item");
        return byItem == null ? "" : byItem;
    }

    /** A single comparison, e.g. {@code enchant = mending} or {@code qty < 100}. */
    record Comparison(String field, Op op, String value) implements IIqlCondition {
        @Override
        public boolean matches(final Function<String, String> row) {
            final String actual = row.apply(field);
            if (actual == null) {
                return false;
            }
            return switch (op) {
                case EQ -> valuesEqual(actual, value);
                case NEQ -> !valuesEqual(actual, value);
                case LT -> compare(actual, value) < 0;
                case GT -> compare(actual, value) > 0;
                case LTE -> compare(actual, value) <= 0;
                case GTE -> compare(actual, value) >= 0;
                // HAS is the same substring test as CONTAINS at this layer; multi-valued fields (e.g. a
                // list of enchantments) are joined to one string by the row provider before it reaches here.
                case CONTAINS, HAS -> actual.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT));
                case LIKE -> like(actual, value);
            };
        }

        private static boolean valuesEqual(final String a, final String b) {
            final Double na = asNumber(a);
            final Double nb = asNumber(b);
            if (na != null && nb != null) {
                return na.doubleValue() == nb.doubleValue();
            }
            return a.equalsIgnoreCase(b);
        }

        private static int compare(final String a, final String b) {
            final Double na = asNumber(a);
            final Double nb = asNumber(b);
            if (na != null && nb != null) {
                return Double.compare(na, nb);
            }
            return a.compareToIgnoreCase(b);
        }

        private static Double asNumber(final String s) {
            try {
                return Double.valueOf(s);
            } catch (final NumberFormatException e) {
                return null;
            }
        }

        private static boolean like(final String actual, final String pattern) {
            // '*' is the only wildcard; every other character is literal.
            final String[] parts = pattern.split("\\*", -1);
            final StringBuilder regex = new StringBuilder("(?i)");
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    regex.append(".*");
                }
                regex.append(Pattern.quote(parts[i]));
            }
            return actual.matches(regex.toString());
        }
    }

    /** Both sides must hold. */
    record And(IIqlCondition left, IIqlCondition right) implements IIqlCondition {
        @Override
        public boolean matches(final Function<String, String> row) {
            return left.matches(row) && right.matches(row);
        }
    }

    /** Either side holds. */
    record Or(IIqlCondition left, IIqlCondition right) implements IIqlCondition {
        @Override
        public boolean matches(final Function<String, String> row) {
            return left.matches(row) || right.matches(row);
        }
    }

    /** Negates the inner condition. */
    record Not(IIqlCondition inner) implements IIqlCondition {
        @Override
        public boolean matches(final Function<String, String> row) {
            return !inner.matches(row);
        }
    }
}
