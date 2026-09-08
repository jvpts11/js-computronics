/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.computers.os.OsCapability;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SystemLayout}: only a full desktop OS provisions the Windows-like folder
 * skeleton, and it lists parent directories before their children.
 */
final class SystemLayoutTest {

    @Test
    void directoriesFor_fullDesktopProvisionsSystemFolders() {
        final List<String> dirs = SystemLayout.directoriesFor(OsCapability.FULL_DESKTOP);
        assertTrue(dirs.contains("Program Files"), "must include Program Files");
        assertTrue(dirs.contains("Program Files (x86)"), "must include Program Files (x86)");
        assertTrue(dirs.contains("Windows"), "must include Windows");
        assertTrue(dirs.contains("Users"), "must include Users");
        assertTrue(dirs.contains(SystemLayout.DESKTOP_DIR), "must include the desktop directory");
    }

    @Test
    void directoriesFor_terminalAndNetworkGetNoFolders() {
        assertTrue(SystemLayout.directoriesFor(OsCapability.TERMINAL_ONLY).isEmpty(),
                "a terminal-only OS provisions no folders");
        assertTrue(SystemLayout.directoriesFor(OsCapability.NETWORK_GUI).isEmpty(),
                "a network-GUI OS provisions no folders");
    }

    @Test
    void directoriesFor_listsParentsBeforeChildren() {
        final List<String> dirs = SystemLayout.directoriesFor(OsCapability.FULL_DESKTOP);
        /*
         * Every nested path must appear after each of its ancestors, so a caller materialising the
         * list in order never references a missing parent.
         */
        for (int i = 0; i < dirs.size(); i++) {
            final String path = dirs.get(i);
            final int lastSlash = path.lastIndexOf('/');
            if (lastSlash < 0) {
                continue;
            }
            final String parent = path.substring(0, lastSlash);
            assertTrue(dirs.indexOf(parent) >= 0 && dirs.indexOf(parent) < i,
                    "parent '" + parent + "' must be listed before child '" + path + "'");
        }
    }

    @Test
    void desktopDir_isUnderUsers() {
        assertEquals("Users/Public/Desktop", SystemLayout.DESKTOP_DIR);
        assertFalse(SystemLayout.DESKTOP_DIR.startsWith("/"), "desktop dir must be relative to the root");
    }
}
