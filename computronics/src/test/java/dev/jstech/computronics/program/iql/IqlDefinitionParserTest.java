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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IqlDefinitionParserTest {

    @Test
    void parse_createView_capturesQueryBody() {
        final IqlDefinition def = IqlDefinitionParser.tryParse("CREATE VIEW low_stock AS QUERY items WHERE qty < 100");
        assertEquals(IqlDefinition.Verb.CREATE, def.verb());
        assertEquals(IqlDefinition.ObjectType.VIEW, def.objectType());
        assertEquals("low_stock", def.name());
        assertEquals("QUERY items WHERE qty < 100", def.body());
        assertFalse(def.hasTrigger());
    }

    @Test
    void parse_createProcedure_capturesBlockBody() {
        final IqlDefinition def = IqlDefinitionParser.tryParse(
                "CREATE PROCEDURE restock AS { CRAFT 64 iron_ingot; MOVE 64 iron_ingot FROM A TO B }");
        assertEquals(IqlDefinition.ObjectType.PROCEDURE, def.objectType());
        assertEquals("restock", def.name());
        assertEquals("{ CRAFT 64 iron_ingot; MOVE 64 iron_ingot FROM A TO B }", def.body());
    }

    @Test
    void parse_createJob_withEveryTrigger() {
        final IqlDefinition def = IqlDefinitionParser.tryParse("CREATE JOB auto_restock AS restock EVERY 30s");
        assertEquals(IqlDefinition.ObjectType.JOB, def.objectType());
        assertEquals("auto_restock", def.name());
        assertEquals("restock", def.body());
        assertEquals(IqlDefinition.TriggerKind.EVERY, def.triggerKind());
        assertEquals("30s", def.triggerSpec());
    }

    @Test
    void parse_createJob_withWhenTrigger() {
        final IqlDefinition def = IqlDefinitionParser.tryParse("CREATE JOB on_low AS restock WHEN qty(iron_ingot) < 100");
        assertEquals(IqlDefinition.TriggerKind.WHEN, def.triggerKind());
        assertEquals("restock", def.body());
        assertEquals("qty(iron_ingot) < 100", def.triggerSpec());
    }

    @Test
    void parse_dropView_isDefinition() {
        final IqlDefinition def = IqlDefinitionParser.tryParse("DROP VIEW low_stock");
        assertEquals(IqlDefinition.Verb.DROP, def.verb());
        assertEquals(IqlDefinition.ObjectType.VIEW, def.objectType());
        assertEquals("low_stock", def.name());
    }

    @Test
    void parse_exec_namesAProcedure() {
        final IqlDefinition def = IqlDefinitionParser.tryParse("EXEC restock");
        assertEquals(IqlDefinition.Verb.EXEC, def.verb());
        assertEquals("restock", def.name());
    }

    @Test
    void parse_dropItem_isNotADefinition() {
        // DROP <item> is the item-trashing action, not a definition drop, so the definition parser declines it.
        assertNull(IqlDefinitionParser.tryParse("DROP 64 dirt"));
        assertNull(IqlDefinitionParser.tryParse("DROP diamond"));
    }

    @Test
    void parse_action_isNotADefinition() {
        assertNull(IqlDefinitionParser.tryParse("SELECT 64 iron_ingot"));
        assertNull(IqlDefinitionParser.tryParse("QUERY items"));
    }

    @Test
    void parse_createWithoutAs_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> IqlDefinitionParser.tryParse("CREATE VIEW low_stock QUERY items"));
    }

    @Test
    void parse_jobWithoutTrigger_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> IqlDefinitionParser.tryParse("CREATE JOB j AS restock"));
    }

    @Test
    void parse_createUnknownObject_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> IqlDefinitionParser.tryParse("CREATE TABLE t AS QUERY items"));
    }

    @Test
    void tryParse_routesDefinitionThroughMainParser() {
        final IqlParseResult create = IqlParser.tryParse("CREATE VIEW v AS QUERY items");
        assertTrue(create.ok());
        assertTrue(create.isDefinition());
        assertEquals("v", create.definition().name());
    }

    @Test
    void tryParse_keepsActionsAsOperations() {
        final IqlParseResult select = IqlParser.tryParse("SELECT 64 iron_ingot");
        assertTrue(select.ok());
        assertFalse(select.isDefinition());
        assertEquals(IqlVerb.SELECT, select.operation().verb());
    }

    @Test
    void tryParse_dropItemStaysAnAction() {
        final IqlParseResult drop = IqlParser.tryParse("DROP 64 dirt");
        assertTrue(drop.ok());
        assertFalse(drop.isDefinition());
        assertEquals(IqlVerb.DROP, drop.operation().verb());
    }
}
