/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os.fs;

import dev.jsc.jscomputronics.module.computing.os.FilesystemKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FsPathsTest {

    @Test
    void sizeMbEq_roundsUpToFourKilobyteUnits() {
        assertEquals(0L, FsPaths.sizeMbEq(0));
        assertEquals(1L, FsPaths.sizeMbEq(1));
        assertEquals(1L, FsPaths.sizeMbEq(4096));
        assertEquals(2L, FsPaths.sizeMbEq(4097));
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
