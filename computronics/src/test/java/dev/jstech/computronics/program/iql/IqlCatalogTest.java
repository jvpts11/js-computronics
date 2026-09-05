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
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.computronics.program.iql.IqlDefinition.ObjectType;
import dev.jstech.computronics.program.iql.IqlDefinition.TriggerKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IqlCatalogTest {

    private IqlCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new IqlCatalog();
    }

    private static IqlSavedObject view(final String name) {
        return new IqlSavedObject(ObjectType.VIEW, name, "QUERY items", TriggerKind.NONE, "");
    }

    @Test
    void put_thenGet_returnsTheObject() {
        catalog.put(view("low_stock"));
        assertEquals("QUERY items", catalog.get(ObjectType.VIEW, "low_stock").body());
        assertTrue(catalog.contains(ObjectType.VIEW, "low_stock"));
    }

    @Test
    void get_isCaseInsensitiveOnName() {
        catalog.put(view("LowStock"));
        assertTrue(catalog.contains(ObjectType.VIEW, "lowstock"));
    }

    @Test
    void remove_dropsTheObject() {
        catalog.put(view("v"));
        assertTrue(catalog.remove(ObjectType.VIEW, "v"));
        assertFalse(catalog.contains(ObjectType.VIEW, "v"));
        assertNull(catalog.get(ObjectType.VIEW, "v"));
    }

    @Test
    void remove_unknownObject_returnsFalse() {
        assertFalse(catalog.remove(ObjectType.VIEW, "missing"));
    }

    @Test
    void put_replacesSameTypeAndName() {
        catalog.put(view("v"));
        assertTrue(catalog.put(new IqlSavedObject(ObjectType.VIEW, "v", "QUERY servers", TriggerKind.NONE, "")));
        assertEquals(1, catalog.size());
        assertEquals("QUERY servers", catalog.get(ObjectType.VIEW, "v").body());
    }

    @Test
    void sameName_differentType_doNotCollide() {
        catalog.put(view("restock"));
        catalog.put(new IqlSavedObject(ObjectType.PROCEDURE, "restock", "{ QUERY items }", TriggerKind.NONE, ""));
        assertEquals(2, catalog.size());
        assertTrue(catalog.contains(ObjectType.VIEW, "restock"));
        assertTrue(catalog.contains(ObjectType.PROCEDURE, "restock"));
    }

    @Test
    void ofType_filtersByType() {
        catalog.put(view("a"));
        catalog.put(view("b"));
        catalog.put(new IqlSavedObject(ObjectType.JOB, "j", "QUERY items", TriggerKind.EVERY, "30s"));
        assertEquals(2, catalog.ofType(ObjectType.VIEW).size());
        assertEquals(1, catalog.ofType(ObjectType.JOB).size());
        assertTrue(catalog.ofType(ObjectType.PROCEDURE).isEmpty());
    }
}
