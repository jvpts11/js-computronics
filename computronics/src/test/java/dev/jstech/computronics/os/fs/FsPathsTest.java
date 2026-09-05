/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os.fs;

import dev.jstech.computronics.os.FilesystemKind;
import dev.jstech.core.tier.HardwareEra;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FsPathsTest {

    @Test
    void sizeMbEq_roundsUpToTheErasBlock() {
        // A standard disk's block is 268 435 bytes; a vintage one's is 1 048, so the same file weighs more there.
        assertEquals(0L, FsPaths.sizeMbEq(0, HardwareEra.STANDARD));
        assertEquals(1L, FsPaths.sizeMbEq(1, HardwareEra.STANDARD));
        assertEquals(1L, FsPaths.sizeMbEq(268_435, HardwareEra.STANDARD));
        assertEquals(2L, FsPaths.sizeMbEq(268_436, HardwareEra.STANDARD));
        assertEquals(1L, FsPaths.sizeMbEq(1_048, HardwareEra.VINTAGE));
        assertEquals(2L, FsPaths.sizeMbEq(1_049, HardwareEra.VINTAGE));
        assertEquals(3L, FsPaths.sizeMbEq(3_000, HardwareEra.VINTAGE));
        assertEquals(1L, FsPaths.sizeMbEq(3_000, HardwareEra.STANDARD));
    }

    @Test
    void isValidPath_flatRejectsSlashes() {
        assertTrue(FsPaths.isValidPath("script.iql", FilesystemKind.FLAT));
        assertFalse(FsPaths.isValidPath("dir/script.iql", FilesystemKind.FLAT));
    }

    @Test
    void isValidPath_hierarchicalAcceptsFolders() {
        assertTrue(FsPaths.isValidPath("scripts/daily.iql", FilesystemKind.HIERARCHICAL));
        assertFalse(FsPaths.isValidPath("scripts//x.iql", FilesystemKind.HIERARCHICAL));
    }

    @Test
    void isValidPath_noneRejectsEverything() {
        assertFalse(FsPaths.isValidPath("a.txt", FilesystemKind.NONE));
    }
}
