/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program.iql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.computronics.program.iql.IIqlCondition.And;
import dev.jstech.computronics.program.iql.IIqlCondition.Comparison;
import dev.jstech.computronics.program.iql.IIqlCondition.Not;
import dev.jstech.computronics.program.iql.IIqlCondition.Op;
import dev.jstech.computronics.program.iql.IIqlCondition.Or;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class IqlConditionTest {

    private static Function<String, String> row(final Map<String, String> fields) {
        return fields::get;
    }

    @Test
    void eq_matchesStringCaseInsensitively() {
        final Comparison c = new Comparison("enchant", Op.EQ, "mending");
        assertTrue(c.matches(row(Map.of("enchant", "Mending"))));
        assertFalse(c.matches(row(Map.of("enchant", "silk_touch"))));
    }

    @Test
    void eq_matchesNumbersByValue() {
        final Comparison c = new Comparison("qty", Op.EQ, "100");
        assertTrue(c.matches(row(Map.of("qty", "100"))));
        assertFalse(c.matches(row(Map.of("qty", "99"))));
    }

    @Test
    void lt_comparesNumerically() {
        final Comparison c = new Comparison("qty", Op.LT, "100");
        assertTrue(c.matches(row(Map.of("qty", "64"))));
        assertFalse(c.matches(row(Map.of("qty", "100"))));
        assertFalse(c.matches(row(Map.of("qty", "200"))));
    }

    @Test
    void contains_isSubstringCaseInsensitive() {
        final Comparison c = new Comparison("name", Op.CONTAINS, "rare");
        assertTrue(c.matches(row(Map.of("name", "Super Rare Sword"))));
        assertFalse(c.matches(row(Map.of("name", "Common Stick"))));
    }

    @Test
    void like_supportsStarWildcard() {
        final Comparison c = new Comparison("item", Op.LIKE, "*_log");
        assertTrue(c.matches(row(Map.of("item", "oak_log"))));
        assertFalse(c.matches(row(Map.of("item", "oak_planks"))));
    }

    @Test
    void and_requiresBothSides() {
        final And c = new And(new Comparison("enchant", Op.EQ, "mending"),
                new Comparison("qty", Op.GT, "0"));
        assertTrue(c.matches(row(Map.of("enchant", "mending", "qty", "5"))));
        assertFalse(c.matches(row(Map.of("enchant", "mending", "qty", "0"))));
    }

    @Test
    void or_requiresEitherSide() {
        final Or c = new Or(new Comparison("enchant", Op.EQ, "mending"),
                new Comparison("enchant", Op.EQ, "silk_touch"));
        assertTrue(c.matches(row(Map.of("enchant", "silk_touch"))));
        assertFalse(c.matches(row(Map.of("enchant", "efficiency"))));
    }

    @Test
    void not_negatesInner() {
        final Not c = new Not(new Comparison("damaged", Op.EQ, "true"));
        assertTrue(c.matches(row(Map.of("damaged", "false"))));
        assertFalse(c.matches(row(Map.of("damaged", "true"))));
    }

    @Test
    void missingField_doesNotMatch() {
        final Comparison c = new Comparison("enchant", Op.EQ, "mending");
        assertFalse(c.matches(field -> null));
    }
}
