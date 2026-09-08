/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.multiblock;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchResultTest {

    @Test
    void success_storesRotationAndSlaves() {
        var slaves = List.of(100L, 200L, 300L);
        IMatchResult r = new IMatchResult.Success(Rotation.EAST, slaves);
        var s = assertInstanceOf(IMatchResult.Success.class, r);
        assertSame(Rotation.EAST, s.rotation());
        assertEquals(3, s.slavePositions().size());
    }

    @Test
    void success_slavePositionsAreImmutable() {
        var slaves = new java.util.ArrayList<Long>();
        slaves.add(1L);
        slaves.add(2L);
        var s = new IMatchResult.Success(Rotation.NORTH, slaves);
        // Mutating the original list does not affect the result's view.
        slaves.add(3L);
        assertEquals(2, s.slavePositions().size());
        // The internal copy is also immutable.
        assertThrows(UnsupportedOperationException.class,
                () -> s.slavePositions().add(99L));
    }

    @Test
    void success_rejectsNullRotation() {
        assertThrows(NullPointerException.class,
                () -> new IMatchResult.Success(null, List.of()));
    }

    @Test
    void success_rejectsNullSlaves() {
        assertThrows(NullPointerException.class,
                () -> new IMatchResult.Success(Rotation.NORTH, null));
    }

    @Test
    void failure_storesAllFields() {
        IMatchResult r = new IMatchResult.Failure(1, 2, 3, 'C', "minecraft:stone");
        var f = assertInstanceOf(IMatchResult.Failure.class, r);
        assertEquals(1, f.relX());
        assertEquals(2, f.relY());
        assertEquals(3, f.relZ());
        assertEquals('C', f.expectedChar());
        assertEquals("minecraft:stone", f.actualBlockId());
    }

    @Test
    void failure_rejectsNullActualBlockId() {
        assertThrows(NullPointerException.class,
                () -> new IMatchResult.Failure(0, 0, 0, 'X', null));
    }
}
