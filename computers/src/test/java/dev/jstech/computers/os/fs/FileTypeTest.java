/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os.fs;

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
    void virtualProjectionsAreTheDatAndTheInstallerFiles() {
        /*
         * A projection is generated, never stored: the .dat from a disk's storage, what an installer
         * shows when opened, and what an installed program leaves in its folder. Every one of them is
         * also closed to the player's edits.
         */
        final java.util.Set<FileType> virtual = java.util.EnumSet.of(FileType.DAT, FileType.EXE, FileType.SH,
                FileType.PKG, FileType.INF, FileType.BIN, FileType.INI, FileType.SYS, FileType.FON);
        for (FileType t : FileType.values()) {
            assertEquals(virtual.contains(t), t.virtualProjection(), t + " virtual");
            if (virtual.contains(t)) assertFalse(t.userEditable(), t + " must not be editable");
        }
    }

    @Test
    void datIsNotUserEditable() {
        assertFalse(FileType.DAT.userEditable());
        assertFalse(FileType.LOG.userEditable());
        assertTrue(FileType.TXT.userEditable());
    }
}
