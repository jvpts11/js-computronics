/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program.iql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.computronics.program.iql.IqlCondition.Comparison;
import dev.jstech.computronics.program.iql.IqlCondition.Op;
import org.junit.jupiter.api.Test;

class IqlParserTest {

    @Test
    void parse_simpleSelect() {
        final IqlOperation op = IqlParser.parse("SELECT 1000 cobblestone");
        assertEquals(IqlVerb.SELECT, op.verb());
        assertEquals(1000L, op.quantity());
        assertEquals("cobblestone", op.item());
    }

    @Test
    void parse_allQuantity() {
        final IqlOperation op = IqlParser.parse("SELECT ALL diamond");
        assertEquals(IqlOperation.ALL, op.quantity());
        assertEquals("diamond", op.item());
    }

    @Test
    void parse_quantityIsOptional() {
        final IqlOperation op = IqlParser.parse("SELECT cobblestone");
        assertEquals(IqlOperation.NONE, op.quantity());
        assertEquals("cobblestone", op.item());
    }

    @Test
    void parse_verbIsCaseInsensitive() {
        assertEquals(IqlVerb.INSERT, IqlParser.parse("insert 64 iron").verb());
    }

    @Test
    void parse_emptyStatementThrows() {
        assertThrows(IllegalArgumentException.class, () -> IqlParser.parse(""));
        assertThrows(IllegalArgumentException.class, () -> IqlParser.parse("   "));
    }

    @Test
    void parse_unknownVerbThrows() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> IqlParser.parse("frobnicate 1 stone"));
        assertTrue(e.getMessage().contains("unknown verb"));
    }

    @Test
    void parse_percentageQuantityIsRejectedForNow() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> IqlParser.parse("SELECT 50% diamond"));
        assertTrue(e.getMessage().contains("percentage"));
    }

    @Test
    void parse_moveCarriesFromAndTo() {
        final IqlOperation op = IqlParser.parse("MOVE 500 redstone FROM ServerA TO ServerB");
        assertEquals(IqlVerb.MOVE, op.verb());
        assertEquals("ServerA", op.from());
        assertEquals("ServerB", op.to());
    }

    @Test
    void parse_deleteExportsToDestination() {
        assertEquals("Furnace", IqlParser.parse("DELETE 64 iron TO Furnace").to());
    }

    @Test
    void parse_insertImportsFromExternalSource() {
        final IqlOperation op = IqlParser.parse("INSERT 64 cobblestone FROM CobblestoneFarm");
        assertEquals("CobblestoneFarm", op.from());
        assertTrue(op.hasFrom());
    }

    @Test
    void parse_fiveFlowViolationsThrow() {
        // DELETE (export) needs a TO; MOVE (internal) needs both ends; SELECT pulls here, so no TO.
        assertThrows(IllegalArgumentException.class, () -> IqlParser.parse("DELETE 64 iron"));
        assertThrows(IllegalArgumentException.class, () -> IqlParser.parse("MOVE 500 redstone FROM ServerA"));
        assertThrows(IllegalArgumentException.class, () -> IqlParser.parse("SELECT 1000 cobblestone TO ServerA"));
    }

    @Test
    void parse_whereFilterAttachesToTheItems() {
        final IqlOperation op = IqlParser.parse("MOVE diamond_pickaxe FROM A TO B WHERE enchant = mending");
        assertTrue(op.hasWhere());
        final Comparison where = assertInstanceOf(Comparison.class, op.where());
        assertEquals("enchant", where.field());
        assertEquals(Op.EQ, where.op());
        assertEquals("mending", where.value());
    }

    @Test
    void parse_ifGuardIsSeparateFromWhere() {
        // The five-flow example: import cobblestone from a farm only while stock is empty.
        final IqlOperation op = IqlParser.parse("INSERT cobblestone FROM CobblestoneFarm IF qty(cobblestone) < 1");
        assertEquals("CobblestoneFarm", op.from());
        assertTrue(op.hasGuard());
        assertFalse(op.hasWhere());
        final Comparison guard = assertInstanceOf(Comparison.class, op.guard());
        assertEquals("qty(cobblestone)", guard.field());
        assertEquals(Op.LT, guard.op());
    }

    @Test
    void parse_orderByAndLimit() {
        final IqlOperation op = IqlParser.parse("QUERY items ORDER BY qty DESC LIMIT 10");
        assertEquals("qty", op.orderBy());
        assertTrue(op.orderByDescending());
        assertEquals(10, op.limit());
        assertTrue(op.hasLimit());
    }

    @Test
    void parse_orderByDefaultsToAscending() {
        final IqlOperation op = IqlParser.parse("QUERY items ORDER BY qty");
        assertEquals("qty", op.orderBy());
        assertFalse(op.orderByDescending());
    }

    @Test
    void parse_queryNamesAnObject() {
        final IqlOperation op = IqlParser.parse("QUERY servers");
        assertEquals(IqlVerb.QUERY, op.verb());
        assertEquals("servers", op.item());
        assertEquals(IqlOperation.NONE, op.quantity());
    }

    @Test
    void parse_showIsAnAliasOfQuery() {
        final IqlOperation op = IqlParser.parse("SHOW items WHERE name contains \"rare\"");
        assertEquals(IqlVerb.QUERY, op.verb());
        assertEquals("items", op.item());
        assertTrue(op.hasWhere());
    }

    @Test
    void parse_readRejectsFlowClauses() {
        assertThrows(IllegalArgumentException.class, () -> IqlParser.parse("QUERY items FROM ServerA"));
        assertThrows(IllegalArgumentException.class, () -> IqlParser.parse("QUERY items TO ServerA"));
        assertThrows(IllegalArgumentException.class, () -> IqlParser.parse("QUERY items IF qty(x) < 1"));
    }

    @Test
    void parse_maintenanceTakesOptionalObject() {
        assertEquals(IqlVerb.ANALYZE, IqlParser.parse("ANALYZE").verb());
        assertEquals("items", IqlParser.parse("VACUUM items").item());
        assertEquals(IqlVerb.REINDEX, IqlParser.parse("REINDEX").verb());
    }

    @Test
    void parse_lockReservesAnItem() {
        final IqlOperation op = IqlParser.parse("LOCK 64 iron_ingot FROM ServerA");
        assertEquals(IqlVerb.LOCK, op.verb());
        assertEquals(64L, op.quantity());
        assertEquals("ServerA", op.from());
    }

    @Test
    void parse_dropDestroysWithoutDestination() {
        final IqlOperation op = IqlParser.parse("DROP 64 dirt");
        assertEquals(IqlVerb.DROP, op.verb());
        assertFalse(op.hasTo());
    }

    @Test
    void parse_trailingGarbageThrows() {
        assertThrows(IllegalArgumentException.class, () -> IqlParser.parse("SELECT 64 iron nonsense"));
    }

    @Test
    void tryParse_succeedsWithoutThrowing() {
        final IqlParseResult result = IqlParser.tryParse("SELECT 1000 cobblestone");
        assertTrue(result.ok());
        assertEquals(IqlVerb.SELECT, result.operation().verb());
    }

    @Test
    void tryParse_reportsErrorMessageInsteadOfThrowing() {
        final IqlParseResult result = IqlParser.tryParse("DELETE 64 iron");
        assertFalse(result.ok());
        assertTrue(result.error().contains("TO"));
    }

    @Test
    void tryParse_pointsAtTheOffendingToken() {
        // tokens: [SELECT, 64, iron, nonsense] -> the stray word is index 3.
        final IqlParseResult result = IqlParser.tryParse("SELECT 64 iron nonsense");
        assertFalse(result.ok());
        assertEquals(3, result.position());
    }

    @Test
    void tryParse_emptyStatementHasNoPosition() {
        final IqlParseResult result = IqlParser.tryParse("   ");
        assertFalse(result.ok());
        assertEquals(IqlParseResult.NO_POSITION, result.position());
    }

    @Test
    void parse_anyItemWildcard_isAnItem() {
        // '*' is the "any item" wildcard, parsed as the item (quantity stays unspecified).
        final IqlOperation op = IqlParser.parse("SELECT * FROM ServerA");
        assertEquals(IqlVerb.SELECT, op.verb());
        assertEquals(IqlOperation.ANY_ITEM, op.item());
        assertTrue(op.isAnyItem());
        assertEquals("ServerA", op.from());
    }

    @Test
    void parse_moveAllItemsBetweenServers() {
        // "bring everything from A to B": MOVE * FROM A TO B.
        final IqlOperation op = IqlParser.parse("MOVE * FROM ServerA TO ServerB");
        assertEquals(IqlVerb.MOVE, op.verb());
        assertTrue(op.isAnyItem());
        assertEquals("ServerA", op.from());
        assertEquals("ServerB", op.to());
    }
}
