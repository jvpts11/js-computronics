/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os.edit.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectTemplateTest {

    @Test
    void project_startsWithOneSourceNamedAfterTheProject() {
        final ProjectFile file = ProjectTemplate.CONSOLE_APP.project("Sorter");
        assertEquals("Sorter", file.name());
        assertEquals(ProjectFile.Kind.CONSOLE, file.kind());
        assertEquals(List.of("Sorter.can"), file.sources());
        assertEquals("build/Sorter.asm", file.entry());
        assertEquals(ProjectTemplate.LANGUAGE, file.language());
    }

    @Test
    void project_ofALibraryOrEmptyHasNoEntry() {
        assertEquals("", ProjectTemplate.CLASS_LIBRARY.project("Helpers").entry());
        final ProjectFile empty = ProjectTemplate.EMPTY_PROJECT.project("Blank");
        assertEquals("", empty.entry());
        assertTrue(empty.sources().isEmpty());
        assertEquals("", ProjectTemplate.EMPTY_PROJECT.firstSource("Blank"));
    }

    @Test
    void source_namesTheClassAfterTheProject() {
        assertTrue(ProjectTemplate.CONSOLE_APP.source("Sorter").contains("class Sorter {"));
        assertTrue(ProjectTemplate.CONSOLE_APP.source("Sorter").contains("static void Main()"));
        assertTrue(ProjectTemplate.SCRIPT.source("Watch").contains("class Watch : IScript"));
        assertTrue(ProjectTemplate.SCRIPT.source("Watch").contains("OnTick()"));
        assertTrue(ProjectTemplate.CLASS_LIBRARY.source("Helpers").contains("class Helpers {"));
        assertEquals("", ProjectTemplate.EMPTY_PROJECT.source("Blank"));
    }

    @Test
    void tags_carryTheLanguageThePlatformsAndTheKind() {
        assertEquals(List.of("Cannon", "Frames", "Linux", "Console"), ProjectTemplate.CONSOLE_APP.tags());
        assertEquals(List.of("Cannon", "Frames", "Linux"), ProjectTemplate.EMPTY_PROJECT.tags());
    }

    @Test
    void isKind_filtersByTheKindWordOrNotAtAll() {
        assertTrue(ProjectTemplate.SCRIPT.isKind("Script"));
        assertFalse(ProjectTemplate.SCRIPT.isKind("Console"));
        assertTrue(ProjectTemplate.SCRIPT.isKind(""));
        assertTrue(ProjectTemplate.SCRIPT.isKind(null));
    }
}
