/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.multiblock;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockMatcherTest {

    @Test
    void exact_matchesOnlyTheRequiredId() {
        var m = BlockMatcher.exact("jsc:casing");
        assertTrue(m.matches("jsc:casing"));
        assertFalse(m.matches("jsc:other"));
        assertFalse(m.matches("minecraft:stone"));
        assertFalse(m.matches(""));
    }

    @Test
    void exact_rejectsNullId() {
        assertThrows(NullPointerException.class, () -> BlockMatcher.exact(null));
    }

    @Test
    void anyOf_matchesAnyMember() {
        var m = BlockMatcher.anyOf(Set.of("jsc:casing_t3", "jsc:casing_t4"));
        assertTrue(m.matches("jsc:casing_t3"));
        assertTrue(m.matches("jsc:casing_t4"));
        assertFalse(m.matches("jsc:casing_t2"));
    }

    @Test
    void anyOf_rejectsNullSet() {
        assertThrows(NullPointerException.class, () -> BlockMatcher.anyOf(null));
    }

    @Test
    void anyOf_rejectsEmptySet() {
        assertThrows(IllegalArgumentException.class,
                () -> BlockMatcher.anyOf(Set.of()));
    }

    @Test
    void anyOf_isolatesItselfFromCallerMutations() {
        // If the caller mutates their set after creating the matcher,
        // the matcher's behavior must not change.
        var mutable = new HashSet<>(Set.of("jsc:a", "jsc:b"));
        var m = BlockMatcher.anyOf(mutable);
        mutable.add("jsc:c");
        assertFalse(m.matches("jsc:c"));
    }

    @Test
    void air_matchesOnlyMinecraftAir() {
        var m = BlockMatcher.air();
        assertTrue(m.matches("minecraft:air"));
        assertFalse(m.matches("minecraft:cave_air"));
        assertFalse(m.matches("jsc:casing"));
    }

    @Test
    void any_matchesEverything() {
        var m = BlockMatcher.any();
        assertTrue(m.matches("minecraft:air"));
        assertTrue(m.matches("jsc:anything"));
        assertTrue(m.matches(""));
    }
}
