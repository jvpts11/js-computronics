/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkCategoryTest {

    @Test
    void hasThreeCategoriesABC() {
        assertEquals(3, NetworkCategory.values().length);
        assertEquals(NetworkCategory.A, NetworkCategory.values()[0]);
        assertEquals(NetworkCategory.B, NetworkCategory.values()[1]);
        assertEquals(NetworkCategory.C, NetworkCategory.values()[2]);
    }

    @Test
    void categoryA_doesNotHaveUuid() {
        assertFalse(NetworkCategory.A.hasUuid());
    }

    @Test
    void categoriesB_andC_haveUuid() {
        assertTrue(NetworkCategory.B.hasUuid());
        assertTrue(NetworkCategory.C.hasUuid());
    }

    @Test
    void onlyCategoryC_canReceiveOperations() {
        assertFalse(NetworkCategory.A.canReceiveOperations());
        assertFalse(NetworkCategory.B.canReceiveOperations());
        assertTrue(NetworkCategory.C.canReceiveOperations());
    }
}
