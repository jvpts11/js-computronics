/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.multiblock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pure-Java unit tests for {@link MultiblockPatternGeometry}.
 *
 * <p>These tests cover block counting and builder invariants without requiring Minecraft types on the
 * classpath. The world-position accuracy (whether {@code allPositions} matches the reference hand-coded
 * geometry for all four horizontal facings) is verified in the NeoForge GameTest suite, where
 * {@code BlockPos} and {@code Direction} are available.
 */
class MultiblockPatternGeometryTest {

    // ── Patterns used in multiple tests ───────────────────────────────────────

    // Mainframe: 3 wide × 2 tall × 2 deep = 12 cells, all non-IGNORE
    private static final MultiblockPattern MAINFRAME_PATTERN = MultiblockPattern.builder("mainframe_test")
            .layer("P#P", "PPP")
            .layer("PPP", "PPP")
            .where('P', BlockMatcher.any())
            .build();

    // Server Rack: 2 wide × 3 tall × 2 deep = 12 cells, all non-IGNORE
    private static final MultiblockPattern SERVER_RACK_PATTERN = MultiblockPattern.builder("server_rack_test")
            .layer("#P", "PP")
            .layer("PP", "PP")
            .layer("PP", "PP")
            .where('P', BlockMatcher.any())
            .build();

    // ── blockCount ─────────────────────────────────────────────────────────────

    @Test
    void mainframePattern_blockCount_is12() {
        assertEquals(12, new MultiblockPatternGeometry(MAINFRAME_PATTERN).blockCount());
    }

    @Test
    void serverRackPattern_blockCount_is12() {
        assertEquals(12, new MultiblockPatternGeometry(SERVER_RACK_PATTERN).blockCount());
    }

    @Test
    void singleControllerCell_blockCount_is1() {
        MultiblockPattern single = MultiblockPattern.builder("single")
                .layer("#")
                .build();
        assertEquals(1, new MultiblockPatternGeometry(single).blockCount());
    }

    @Test
    void ignoreChar_isExcludedFromBlockCount() {
        // 3 × 1 × 2 = 6 cells total; one IGNORE at (x=2, y=0, z=0) → 5 counted
        MultiblockPattern holed = MultiblockPattern.builder("holed")
                .layer("#P ", "PPP")
                .where('P', BlockMatcher.any())
                .build();
        assertEquals(5, new MultiblockPatternGeometry(holed).blockCount());
    }

    @Test
    void allIgnoreExceptController_blockCount_is1() {
        // A 3×1×1 pattern where only the controller is non-IGNORE
        MultiblockPattern sparse = MultiblockPattern.builder("sparse")
                .layer(" # ")
                .build();
        assertEquals(1, new MultiblockPatternGeometry(sparse).blockCount());
    }

    @Test
    void largerPatternWithMixedIgnore_blockCountMatchesNonSpaceCellCount() {
        // 3×2×3 = 18 cells total; 4 corner spaces per layer × 2 layers = 8 IGNORE → 10 non-IGNORE
        MultiblockPattern frame = MultiblockPattern.builder("frame")
                .layer(" P ", "P#P", " P ")
                .layer(" P ", "PPP", " P ")
                .where('P', BlockMatcher.any())
                .build();
        assertEquals(10, new MultiblockPatternGeometry(frame).blockCount());
    }

    // ── constructor ────────────────────────────────────────────────────────────

    @Test
    void nullPattern_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> new MultiblockPatternGeometry(null));
    }
}
