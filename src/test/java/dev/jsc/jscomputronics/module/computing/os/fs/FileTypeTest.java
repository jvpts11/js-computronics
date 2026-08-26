/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os.fs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileTypeTest {

    @Test
    void fromExtension_resolvesKnownExtensionsCaseInsensitively() {
        assertEquals(java.util.Optional.of(FileType.IQL), FileType.fromExtension("iql"));
        assertEquals(java.util.Optional.of(FileType.IQL), FileType.fromExtension("IQL"));
        assertEquals(java.util.Optional.empty(), FileType.fromExtension("zip"));
    }

    @Test
    void datIsTheOnlyVirtualProjection() {
        assertTrue(FileType.DAT.virtualProjection());
        for (FileType t : FileType.values()) {
            if (t != FileType.DAT) assertFalse(t.virtualProjection(), t + " must not be virtual");
        }
    }

    @Test
    void datIsNotUserEditable() {
        assertFalse(FileType.DAT.userEditable());
        assertFalse(FileType.LOG.userEditable());
        assertTrue(FileType.TXT.userEditable());
    }
}
