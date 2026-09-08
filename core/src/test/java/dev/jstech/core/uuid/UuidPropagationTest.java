/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.uuid;

import dev.jstech.core.uuid.UuidPropagation.IPropagationResult;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class UuidPropagationTest {

    @Test
    void bothEmpty_yieldsEmpty() {
        var result = UuidPropagation.propagate(Optional.empty(), Optional.empty());
        assertInstanceOf(IPropagationResult.Empty.class, result);
    }

    @Test
    void leftEmpty_rightPresent_inherits() {
        var rightUuid = NetworkUuid.random();
        var result = UuidPropagation.propagate(Optional.empty(), Optional.of(rightUuid));
        var inherit = assertInstanceOf(IPropagationResult.Inherit.class, result);
        assertEquals(rightUuid, inherit.uuid());
    }

    @Test
    void leftPresent_rightEmpty_inherits() {
        var leftUuid = NetworkUuid.random();
        var result = UuidPropagation.propagate(Optional.of(leftUuid), Optional.empty());
        var inherit = assertInstanceOf(IPropagationResult.Inherit.class, result);
        assertEquals(leftUuid, inherit.uuid());
    }

    @Test
    void bothPresent_sameUuid_yieldsSame() {
        var uuid = NetworkUuid.random();
        var result = UuidPropagation.propagate(Optional.of(uuid), Optional.of(uuid));
        var same = assertInstanceOf(IPropagationResult.Same.class, result);
        assertEquals(uuid, same.uuid());
    }

    @Test
    void bothPresent_differentUuids_yieldsConflict() {
        var a = NetworkUuid.random();
        var b = NetworkUuid.random();
        // Sanity: random() must not collide.
        assertNotEquals(a, b);

        var result = UuidPropagation.propagate(Optional.of(a), Optional.of(b));
        var conflict = assertInstanceOf(IPropagationResult.Conflict.class, result);
        assertEquals(a, conflict.first());
        assertEquals(b, conflict.second());
    }

    @Test
    void conflict_preservesArgumentOrder() {
        /*
         * The 'first' field of Conflict must mirror the 'sideA' input,
         * not be sorted or normalized in any way.
         */
        var a = NetworkUuid.random();
        var b = NetworkUuid.random();
        var resultAB = (IPropagationResult.Conflict)
                UuidPropagation.propagate(Optional.of(a), Optional.of(b));
        var resultBA = (IPropagationResult.Conflict)
                UuidPropagation.propagate(Optional.of(b), Optional.of(a));
        assertEquals(a, resultAB.first());
        assertEquals(b, resultAB.second());
        assertEquals(b, resultBA.first());
        assertEquals(a, resultBA.second());
    }
}
