/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.core.multiblock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiblockPatternTest {

    @Test
    void minimalPattern_singleControllerCell() {
        var p = MultiblockPattern.builder("min")
                .layer("#")
                .build();
        assertEquals("min", p.name());
        assertEquals(1, p.sizeX());
        assertEquals(1, p.sizeY());
        assertEquals(1, p.sizeZ());
        assertEquals('#', p.charAt(0, 0, 0));
        assertEquals(0, p.controllerX());
        assertEquals(0, p.controllerY());
        assertEquals(0, p.controllerZ());
    }

    @Test
    void cube3x3x3_buildsCorrectly() {
        var p = MultiblockPattern.builder("cube")
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
                .where('C', IBlockMatcher.exact("jsc:casing"))
                .build();
        assertEquals(3, p.sizeX());
        assertEquals(3, p.sizeY());
        assertEquals(3, p.sizeZ());
        assertEquals('#', p.charAt(1, 1, 1));
        assertEquals('C', p.charAt(0, 0, 0));
        assertEquals(1, p.controllerX());
        assertEquals(1, p.controllerY());
        assertEquals(1, p.controllerZ());
    }

    @Test
    void empty_throws() {
        assertThrows(IllegalStateException.class,
                () -> MultiblockPattern.builder("empty").build());
    }

    @Test
    void noController_throws() {
        // Layer with valid blocks but no '#' anywhere.
        assertThrows(IllegalStateException.class, () ->
                MultiblockPattern.builder("noctrl")
                        .layer("CCC")
                        .where('C', IBlockMatcher.exact("jsc:casing"))
                        .build()
        );
    }

    @Test
    void multipleControllers_throws() {
        assertThrows(IllegalStateException.class, () ->
                MultiblockPattern.builder("twoctrl")
                        .layer("# #")
                        .build() // implicit: '#' twice, ignore (' ') in middle
        );
    }

    @Test
    void unmappedChar_throws() {
        assertThrows(IllegalStateException.class, () ->
                MultiblockPattern.builder("unmapped")
                        .layer("X#X") // 'X' is never mapped
                        .build()
        );
    }

    @Test
    void inconsistentRowWidth_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                MultiblockPattern.builder("ragged").layer("###", "##")
        );
    }

    @Test
    void inconsistentLayerSize_throws() {
        var b = MultiblockPattern.builder("badlayers")
                .layer("###",  // 3x1
                        "###",
                        "###");
        // Second layer is different shape:
        assertThrows(IllegalArgumentException.class, () ->
                b.layer("##", "##")
        );
    }

    @Test
    void mappingReservedChar_throws() {
        var b = MultiblockPattern.builder("reserved");
        assertThrows(IllegalArgumentException.class,
                () -> b.where('#', IBlockMatcher.exact("jsc:x")));
        assertThrows(IllegalArgumentException.class,
                () -> b.where(' ', IBlockMatcher.exact("jsc:y")));
    }

    @Test
    void emptyLayer_throws() {
        var b = MultiblockPattern.builder("empty_layer");
        assertThrows(IllegalArgumentException.class, () -> b.layer());
    }

    @Test
    void emptyRow_throws() {
        var b = MultiblockPattern.builder("empty_row");
        assertThrows(IllegalArgumentException.class, () -> b.layer(""));
    }

    @Test
    void mapping_isUnmodifiable() {
        var p = MultiblockPattern.builder("imm")
                .layer("C#")
                .where('C', IBlockMatcher.exact("jsc:casing"))
                .build();
        var map = p.mapping();
        assertEquals(1, map.size());
        assertTrue(map.containsKey('C'));
        assertThrows(UnsupportedOperationException.class,
                () -> map.put('X', IBlockMatcher.any()));
    }

    @Test
    void controllerAtEdge_locatesCorrectly() {
        // Controller in corner of a 2x1x2 pattern (top of an L-shape).
        var p = MultiblockPattern.builder("corner")
                .layer(
                        "#C",
                        "CC"
                )
                .where('C', IBlockMatcher.exact("jsc:casing"))
                .build();
        assertEquals(0, p.controllerX());
        assertEquals(0, p.controllerY());
        assertEquals(0, p.controllerZ());
        assertEquals('#', p.charAt(0, 0, 0));
        assertEquals('C', p.charAt(1, 0, 0));
        assertEquals('C', p.charAt(0, 0, 1));
    }
}
