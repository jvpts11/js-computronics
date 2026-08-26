/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program.iql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jsc.jscomputronics.module.computing.program.iql.IqlCondition.And;
import dev.jsc.jscomputronics.module.computing.program.iql.IqlCondition.Comparison;
import dev.jsc.jscomputronics.module.computing.program.iql.IqlCondition.Op;
import dev.jsc.jscomputronics.module.computing.program.iql.IqlCondition.Or;
import org.junit.jupiter.api.Test;

class IqlConditionParserTest {

    @Test
    void parse_simpleComparison() {
        final Comparison c = assertInstanceOf(Comparison.class, IqlConditionParser.parse("qty < 100"));
        assertEquals("qty", c.field());
        assertEquals(Op.LT, c.op());
        assertEquals("100", c.value());
    }

    @Test
    void parse_equalsAcceptsBothSpellings() {
        assertEquals(Op.EQ, assertInstanceOf(Comparison.class, IqlConditionParser.parse("enchant = mending")).op());
        assertEquals(Op.EQ, assertInstanceOf(Comparison.class, IqlConditionParser.parse("enchant == mending")).op());
    }

    @Test
    void parse_wordOperators() {
        assertEquals(Op.CONTAINS, assertInstanceOf(Comparison.class, IqlConditionParser.parse("name contains rare")).op());
        assertEquals(Op.HAS, assertInstanceOf(Comparison.class, IqlConditionParser.parse("tag has ore")).op());
        assertEquals(Op.LIKE, assertInstanceOf(Comparison.class, IqlConditionParser.parse("item like *_log")).op());
    }

    @Test
    void parse_functionCallField() {
        final Comparison c = assertInstanceOf(Comparison.class, IqlConditionParser.parse("qty(cobblestone) < 1"));
        assertEquals("qty(cobblestone)", c.field());
        assertEquals(Op.LT, c.op());
    }

    @Test
    void parse_quotedStringValue() {
        assertEquals("rare ore", assertInstanceOf(Comparison.class, IqlConditionParser.parse("name contains \"rare ore\"")).value());
    }

    @Test
    void parse_andBindsTighterThanOr() {
        // a OR b AND c  ==  a OR (b AND c)
        final Or root = assertInstanceOf(Or.class, IqlConditionParser.parse("a = 1 OR b = 2 AND c = 3"));
        assertInstanceOf(Comparison.class, root.left());
        assertInstanceOf(And.class, root.right());
    }

    @Test
    void parse_parenthesesOverridePrecedence() {
        // (a OR b) AND c  ==  And(Or(a, b), c)
        final And root = assertInstanceOf(And.class, IqlConditionParser.parse("(a = 1 OR b = 2) AND c = 3"));
        assertInstanceOf(Or.class, root.left());
        assertInstanceOf(Comparison.class, root.right());
    }

    @Test
    void parse_notNegatesNextPrimary() {
        final IqlCondition.Not not = assertInstanceOf(IqlCondition.Not.class, IqlConditionParser.parse("NOT enchant = silk_touch"));
        assertInstanceOf(Comparison.class, not.inner());
    }

    @Test
    void parse_evaluatesParsedTreeAgainstRow() {
        // The parsed tree is the same one IqlCondition evaluates, so a round-trip through matches works.
        final IqlCondition c = IqlConditionParser.parse("qty < 100 AND enchant = mending");
        assertEquals(Boolean.TRUE, c.matches(field -> switch (field) {
            case "qty" -> "40";
            case "enchant" -> "mending";
            default -> null;
        }));
    }

    @Test
    void parse_malformedConditionThrows() {
        assertThrows(IllegalArgumentException.class, () -> IqlConditionParser.parse("qty <"));
        assertThrows(IllegalArgumentException.class, () -> IqlConditionParser.parse("< 100"));
        assertThrows(IllegalArgumentException.class, () -> IqlConditionParser.parse("(qty < 1"));
    }
}
