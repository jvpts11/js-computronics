/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlParserTest {

    private static SqlOperation simple(final String sql) {
        final SqlParseResult result = SqlParser.parse(sql, SqlDialect.SIMPLE);
        assertTrue(result.ok(), "expected ok for: " + sql + " (got: " + result.error() + ")");
        return result.operation();
    }

    private static SqlOperation standard(final String sql) {
        final SqlParseResult result = SqlParser.parse(sql, SqlDialect.STANDARD);
        assertTrue(result.ok(), "expected ok for: " + sql + " (got: " + result.error() + ")");
        return result.operation();
    }

    // --- simple ----------------------------------------------------------------------------------

    @Test
    void simple_selectWithSourceServer() {
        final SqlOperation op = simple("SELECT 1000 Cobblestone FROM Server A");
        assertEquals(SqlOperation.Verb.SELECT, op.verb());
        assertEquals("Cobblestone", op.item());
        assertEquals(1000L, op.quantity());
        assertEquals("Server A", op.source());
    }

    @Test
    void simple_selectWithoutSource() {
        final SqlOperation op = simple("select 64 iron_ingot");
        assertEquals(SqlOperation.Verb.SELECT, op.verb());
        assertEquals("iron_ingot", op.item());
        assertEquals(64L, op.quantity());
        assertFalse(op.hasSource());
    }

    @Test
    void simple_insertAndCraftAndDelete() {
        assertEquals(SqlOperation.Verb.INSERT, simple("insert 32 redstone").verb());
        assertEquals(SqlOperation.Verb.CRAFT, simple("craft 8 hopper").verb());
        assertEquals(SqlOperation.Verb.DELETE, simple("delete 5 dirt").verb());
    }

    @Test
    void simple_moveNeedsFromAndTo() {
        final SqlOperation op = simple("move 100 cobblestone from ServerA to ServerB");
        assertEquals(SqlOperation.Verb.MOVE, op.verb());
        assertEquals("ServerA", op.source());
        assertEquals("ServerB", op.dest());
        assertFalse(SqlParser.parse("move 100 cobblestone from ServerA", SqlDialect.SIMPLE).ok());
    }

    @Test
    void simple_queryWithAndWithoutFilter() {
        assertEquals(SqlOperation.Verb.QUERY, simple("query").verb());
        assertEquals("redstone", simple("query redstone").item());
    }

    @Test
    void simple_quotedMultiWordItem() {
        assertEquals("oak planks", simple("select 4 \"oak planks\"").item());
    }

    @Test
    void simple_rejectsBadQuantityAndUnknownVerb() {
        assertFalse(SqlParser.parse("select abc cobblestone", SqlDialect.SIMPLE).ok());
        assertFalse(SqlParser.parse("select 0 cobblestone", SqlDialect.SIMPLE).ok());
        assertFalse(SqlParser.parse("frobnicate 1 thing", SqlDialect.SIMPLE).ok());
        assertFalse(SqlParser.parse("", SqlDialect.SIMPLE).ok());
    }

    // --- standard --------------------------------------------------------------------------------

    @Test
    void standard_selectStarReads() {
        final SqlOperation op = standard("SELECT * FROM network");
        assertEquals(SqlOperation.Verb.QUERY, op.verb());
    }

    @Test
    void standard_selectStarWithItemFilterAndLimit() {
        final SqlOperation op = standard("SELECT * FROM network WHERE item = 'cobblestone' LIMIT 10");
        assertEquals(SqlOperation.Verb.QUERY, op.verb());
        assertEquals("cobblestone", op.item());
        assertEquals(10, op.limit());
    }

    @Test
    void standard_selectStarCarriesServerIntoQuery() {
        final SqlOperation op = standard(
                "SELECT * FROM network WHERE item = 'cobblestone' AND server = 'ServerA'");
        assertEquals(SqlOperation.Verb.QUERY, op.verb());
        assertEquals("cobblestone", op.item());
        assertEquals("ServerA", op.source());
        assertTrue(op.hasSource());
    }

    @Test
    void standard_selectQuantityPulls() {
        final SqlOperation op = standard("SELECT 100 FROM network WHERE item = 'iron_ingot' AND server = 'ServerA'");
        assertEquals(SqlOperation.Verb.SELECT, op.verb());
        assertEquals("iron_ingot", op.item());
        assertEquals(100L, op.quantity());
        assertEquals("ServerA", op.source());
    }

    @Test
    void standard_insertValues() {
        final SqlOperation op = standard("INSERT INTO network VALUES ('redstone', 64)");
        assertEquals(SqlOperation.Verb.INSERT, op.verb());
        assertEquals("redstone", op.item());
        assertEquals(64L, op.quantity());
    }

    @Test
    void standard_deleteWithWhere() {
        final SqlOperation op = standard("DELETE 50 FROM network WHERE item = 'dirt'");
        assertEquals(SqlOperation.Verb.DELETE, op.verb());
        assertEquals("dirt", op.item());
        assertEquals(50L, op.quantity());
    }

    @Test
    void standard_rejectsUnsupportedAndIncomplete() {
        assertFalse(SqlParser.parse("UPDATE network SET x = 1", SqlDialect.STANDARD).ok());
        assertFalse(SqlParser.parse("SELECT 100 FROM network", SqlDialect.STANDARD).ok());
    }
}
