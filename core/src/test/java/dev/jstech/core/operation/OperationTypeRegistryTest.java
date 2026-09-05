/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.operation;

import dev.jstech.core.network.NetworkCategory;
import dev.jstech.core.tier.IndustrialTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationTypeRegistryTest {

    /**
     * A no-op args record used purely for testing the registry shape.
     */
    private record DummyArgs(int value) implements OperationArgs {}

    private OperationTypeRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new OperationTypeRegistry();
    }

    private OperationType<DummyArgs> sampleType(String path) {
        return new OperationType<>(
                "jsc:" + path,
                DummyArgs.class,
                OperationCategory.STORAGE,
                IndustrialTier.T2,
                EnumSet.of(NetworkCategory.C),
                args -> OperationStatus.COMPLETED
        );
    }

    @Test
    void emptyRegistry_hasZeroSize() {
        assertEquals(0, registry.size());
        assertTrue(registry.all().isEmpty());
    }

    @Test
    void register_addsType() {
        var type = sampleType("test_op");
        registry.register(type);
        assertEquals(1, registry.size());
        assertTrue(registry.contains(type.id()));
    }

    @Test
    void register_returnsSameInstance() {
        var type = sampleType("test_op");
        var returned = registry.register(type);
        assertSame(type, returned);
    }

    @Test
    void get_returnsRegisteredType() {
        var type = sampleType("test_op");
        registry.register(type);
        var fetched = registry.get(type.id());
        assertTrue(fetched.isPresent());
        assertSame(type, fetched.get());
    }

    @Test
    void get_returnsEmptyForUnregisteredId() {
        assertFalse(registry.get("jsc:unknown").isPresent());
    }

    @Test
    void contains_returnsFalseForUnregisteredId() {
        assertFalse(registry.contains("jsc:unknown"));
    }

    @Test
    void register_rejectsDuplicateId() {
        var type1 = sampleType("test_op");
        var type2 = sampleType("test_op");
        registry.register(type1);
        assertThrows(IllegalStateException.class, () -> registry.register(type2));
    }

    @Test
    void all_reflectsAllRegistered() {
        registry.register(sampleType("op1"));
        registry.register(sampleType("op2"));
        registry.register(sampleType("op3"));
        assertEquals(3, registry.all().size());
    }

    @Test
    void operationType_rejectsNullId() {
        assertThrows(NullPointerException.class, () -> new OperationType<>(
                null,
                DummyArgs.class,
                OperationCategory.STORAGE,
                IndustrialTier.T2,
                EnumSet.of(NetworkCategory.C),
                args -> OperationStatus.COMPLETED
        ));
    }

    @Test
    void operationType_rejectsEmptyRequiredCategories() {
        assertThrows(IllegalArgumentException.class, () -> new OperationType<>(
                "jsc:test",
                DummyArgs.class,
                OperationCategory.STORAGE,
                IndustrialTier.T2,
                EnumSet.noneOf(NetworkCategory.class),
                args -> OperationStatus.COMPLETED
        ));
    }

    @Test
    void operationType_rejectsMalformedId() {
        // No namespace prefix
        assertThrows(IllegalArgumentException.class, () -> new OperationType<>(
                "test_op",
                DummyArgs.class,
                OperationCategory.STORAGE,
                IndustrialTier.T2,
                EnumSet.of(NetworkCategory.C),
                args -> OperationStatus.COMPLETED
        ));
    }

    @Test
    void operationType_rejectsUppercaseInId() {
        // Uppercase not allowed in either namespace or path
        assertThrows(IllegalArgumentException.class, () -> new OperationType<>(
                "JSC:TestOp",
                DummyArgs.class,
                OperationCategory.STORAGE,
                IndustrialTier.T2,
                EnumSet.of(NetworkCategory.C),
                args -> OperationStatus.COMPLETED
        ));
    }

    @Test
    void operationType_acceptsValidConstruction() {
        var type = sampleType("valid_op");
        assertEquals("jsc:valid_op", type.id());
        assertSame(OperationCategory.STORAGE, type.category());
        assertTrue(type.requiredCategories().contains(NetworkCategory.C));
    }

    @Test
    void operationType_rejectsNullHandler() {
        assertThrows(NullPointerException.class, () -> new OperationType<>(
                "jsc:test",
                DummyArgs.class,
                OperationCategory.STORAGE,
                IndustrialTier.T2,
                EnumSet.of(NetworkCategory.C),
                null
        ));
    }
}
