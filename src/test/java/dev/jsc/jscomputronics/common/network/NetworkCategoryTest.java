/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 *
 * J's Computronics is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 3
 * as published by the Free Software Foundation.
 *
 * J's Computronics is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
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
