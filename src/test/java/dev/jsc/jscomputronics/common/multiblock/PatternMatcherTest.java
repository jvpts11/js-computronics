/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.multiblock;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternMatcherTest {

    private static BlockProvider providerFrom(Map<Long, String> world) {
        return pos -> world.getOrDefault(pos, "minecraft:air");
    }

    private static long pos(int x, int y, int z) {
        return PatternMatcher.encodePosition(x, y, z);
    }

    @Test
    void encodeDecode_roundTripsIdentity() {
        for (int[] coord : new int[][] {
                { 0, 0, 0 },
                { 1, 64, -1 },
                { -1000, 0, 1000 },
                { 12_345_678, 100, -12_345_678 },
                { -1, -1, -1 },
                { 0, -64, 0 },
                { 33_554_431, 0, -33_554_432 },  // limites de 26-bit signed
        }) {
            long encoded = PatternMatcher.encodePosition(coord[0], coord[1], coord[2]);
            int[] decoded = PatternMatcher.decodePosition(encoded);
            assertArrayEquals(coord, decoded,
                    "Round-trip failed for " + coord[0] + "," + coord[1] + "," + coord[2]);
        }
    }

    @Test
    void singleCellPattern_alwaysMatchesNorth() {
        // Pattern: just the controller. No other slots.
        var pattern = MultiblockPattern.builder("just_ctrl")
                .layer("#")
                .build();
        // World is empty (any block at 0,0,0 — even air — is fine, because
        // the controller slot doesn't validate blocks beyond "is controller").
        BlockProvider provider = providerFrom(Map.of());
        var result = PatternMatcher.match(pattern, provider, pos(0, 0, 0));
        var success = assertInstanceOf(MatchResult.Success.class, result);
        assertSame(Rotation.NORTH, success.rotation());
        assertEquals(0, success.slavePositions().size());
    }

    @Test
    void horizontalLine_matchesNorthOrientation() {
        // Pattern in canonical NORTH orientation: a 3x1x1 line where
        // x=-1 and x=+1 are casing, x=0 is controller.
        var pattern = MultiblockPattern.builder("hline")
                .layer("C#C")
                .where('C', BlockMatcher.exact("jsc:casing"))
                .build();
        // World: place controller at origin, casing at x=-1 and x=+1.
        Map<Long, String> world = new HashMap<>();
        world.put(pos(-1, 0, 0), "jsc:casing");
        world.put(pos(0, 0, 0), "jsc:controller");
        world.put(pos(1, 0, 0), "jsc:casing");
        var result = PatternMatcher.match(pattern, providerFrom(world), pos(0, 0, 0));
        var success = assertInstanceOf(MatchResult.Success.class, result);
        assertSame(Rotation.NORTH, success.rotation());
        assertEquals(2, success.slavePositions().size());
        assertTrue(success.slavePositions().contains(pos(-1, 0, 0)));
        assertTrue(success.slavePositions().contains(pos(1, 0, 0)));
    }

    @Test
    void linePattern_matchesEastRotation() {
        // Pattern authored for NORTH: line along X-axis (east-west).
        var pattern = MultiblockPattern.builder("hline")
                .layer("C#C")
                .where('C', BlockMatcher.exact("jsc:casing"))
                .build();
        // World: same line but along the Z-axis (north-south).
        Map<Long, String> world = new HashMap<>();
        world.put(pos(0, 0, -1), "jsc:casing");
        world.put(pos(0, 0, 0), "jsc:controller");
        world.put(pos(0, 0, 1), "jsc:casing");
        var result = PatternMatcher.match(pattern, providerFrom(world), pos(0, 0, 0));
        var success = assertInstanceOf(MatchResult.Success.class, result);
        assertSame(Rotation.EAST, success.rotation());
        assertEquals(2, success.slavePositions().size());
    }

    @Test
    void missingBlock_returnsFailureWithCorrectInfo() {
        // Pattern needs casing at (-1, 0, 0) but world has air there.
        var pattern = MultiblockPattern.builder("hline")
                .layer("C#C")
                .where('C', BlockMatcher.exact("jsc:casing"))
                .build();
        // World has only the controller — both casings missing.
        Map<Long, String> world = new HashMap<>();
        world.put(pos(0, 0, 0), "jsc:controller");
        var result = PatternMatcher.match(pattern, providerFrom(world), pos(0, 0, 0));
        var failure = assertInstanceOf(MatchResult.Failure.class, result);
        // Failure should be from NORTH attempt (the canonical, debug-friendly one).
        // First failing slot iterated is at (px=0, py=0, pz=0) which is rel (-1, 0, 0).
        assertEquals('C', failure.expectedChar());
        assertEquals("minecraft:air", failure.actualBlockId());
        assertEquals(-1, failure.relX());
        assertEquals(0, failure.relY());
        assertEquals(0, failure.relZ());
    }

    @Test
    void wrongBlock_returnsFailureWithActualId() {
        var pattern = MultiblockPattern.builder("hline")
                .layer("C#C")
                .where('C', BlockMatcher.exact("jsc:casing"))
                .build();
        // World has stone where casing should be.
        Map<Long, String> world = new HashMap<>();
        world.put(pos(-1, 0, 0), "minecraft:stone");
        world.put(pos(0, 0, 0), "jsc:controller");
        world.put(pos(1, 0, 0), "jsc:casing");
        var result = PatternMatcher.match(pattern, providerFrom(world), pos(0, 0, 0));
        var failure = assertInstanceOf(MatchResult.Failure.class, result);
        assertEquals("minecraft:stone", failure.actualBlockId());
        assertEquals('C', failure.expectedChar());
    }

    @Test
    void cube3x3x3_matchesAllOrientations() {
        // Cube is rotationally symmetric — any rotation should match.
        var pattern = MultiblockPattern.builder("cube")
                .layer(
                        "CCC",
                        "CCC",
                        "CCC"
                )
                .layer(
                        "CCC",
                        "C#C",
                        "CCC"
                )
                .layer(
                        "CCC",
                        "CCC",
                        "CCC"
                )
                .where('C', BlockMatcher.exact("jsc:casing"))
                .build();
        // Build the cube around origin. Controller is at center (0,1,0).
        Map<Long, String> world = new HashMap<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 1 && z == 0) continue; // skip controller pos
                    world.put(pos(x, y, z), "jsc:casing");
                }
            }
        }
        world.put(pos(0, 1, 0), "jsc:controller");
        var result = PatternMatcher.match(pattern, providerFrom(world), pos(0, 1, 0));
        var success = assertInstanceOf(MatchResult.Success.class, result);
        // 3x3x3 = 27 cells, minus 1 controller = 26 slaves.
        assertEquals(26, success.slavePositions().size());
        assertSame(Rotation.NORTH, success.rotation());
    }

    @Test
    void asymmetricPattern_eastRotation() {
        // L-shape: controller at origin, casing south, casing east-of-south.
        var pattern = MultiblockPattern.builder("L")
                .layer(
                        "#  ",
                        "C  ",
                        "CC "
                )
                .where('C', BlockMatcher.exact("jsc:casing"))
                .build();
        // Place world to match EAST rotation:
        Map<Long, String> world = new HashMap<>();
        world.put(pos(0, 0, 0), "jsc:controller");
        world.put(pos(-1, 0, 0), "jsc:casing");
        world.put(pos(-2, 0, 0), "jsc:casing");
        world.put(pos(-2, 0, 1), "jsc:casing");
        var result = PatternMatcher.match(pattern, providerFrom(world), pos(0, 0, 0));
        var success = assertInstanceOf(MatchResult.Success.class, result);
        assertSame(Rotation.EAST, success.rotation());
        assertEquals(3, success.slavePositions().size());
    }

    @Test
    void ignoreChar_isNotValidated() {
        // Pattern has a space at (1, 0, 0) — that slot should be skipped.
        var pattern = MultiblockPattern.builder("with_air")
                .layer("# C")
                .where('C', BlockMatcher.exact("jsc:casing"))
                .build();
        // World has anything (even stone) where the space is — should still match.
        Map<Long, String> world = new HashMap<>();
        world.put(pos(0, 0, 0), "jsc:controller");
        world.put(pos(1, 0, 0), "minecraft:stone"); // would normally fail
        world.put(pos(2, 0, 0), "jsc:casing");
        var result = PatternMatcher.match(pattern, providerFrom(world), pos(0, 0, 0));
        var success = assertInstanceOf(MatchResult.Success.class, result);
        // Only one slave (the casing). The space slot was ignored.
        assertEquals(1, success.slavePositions().size());
        assertTrue(success.slavePositions().contains(pos(2, 0, 0)));
    }

    @Test
    void verticalStack_matches() {
        // Three vertical layers, casing top and bottom, controller middle.
        var pattern = MultiblockPattern.builder("stack")
                .layer("C")
                .layer("#")
                .layer("C")
                .where('C', BlockMatcher.exact("jsc:casing"))
                .build();
        Map<Long, String> world = new HashMap<>();
        world.put(pos(0, 0, 0), "jsc:casing");
        world.put(pos(0, 1, 0), "jsc:controller");
        world.put(pos(0, 2, 0), "jsc:casing");
        var result = PatternMatcher.match(pattern, providerFrom(world), pos(0, 1, 0));
        var success = assertInstanceOf(MatchResult.Success.class, result);
        assertEquals(2, success.slavePositions().size());
    }

    @Test
    void controllerPosition_isNeverInSlavesList() {
        var pattern = MultiblockPattern.builder("cube_3x3")
                .layer("CCC", "C#C", "CCC")
                .where('C', BlockMatcher.exact("jsc:casing"))
                .build();
        Map<Long, String> world = new HashMap<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                world.put(pos(x, 0, z), "jsc:casing");
            }
        }
        world.put(pos(0, 0, 0), "jsc:controller");
        var result = PatternMatcher.match(pattern, providerFrom(world), pos(0, 0, 0));
        var success = assertInstanceOf(MatchResult.Success.class, result);
        // Controller pos must not be in the slaves list.
        assertTrue(success.slavePositions().stream().noneMatch(p -> p == pos(0, 0, 0)));
    }
}
