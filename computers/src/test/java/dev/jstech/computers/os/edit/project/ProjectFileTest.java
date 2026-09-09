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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectFileTest {

    private static ProjectFile stockWatch() {
        return new ProjectFile("StockWatch", ProjectFile.Kind.SCRIPT, "jsc:cannon",
                List.of("StockWatch.can", "Helpers.can"), List.of("Helpers"), "build/StockWatch.asm");
    }

    @Test
    void write_thenRead_roundTripsEveryFact() {
        final ProjectFile back = ProjectFile.read(stockWatch().write());
        assertEquals(stockWatch(), back);
    }

    @Test
    void write_isOneFactPerLineAPlayerCanRead() {
        final String text = stockWatch().write();
        assertTrue(text.contains("name: StockWatch\n"));
        assertTrue(text.contains("kind: script\n"));
        assertTrue(text.contains("sources: StockWatch.can, Helpers.can\n"));
        assertTrue(text.contains("references: Helpers\n"));
        assertTrue(text.contains("entry: build/StockWatch.asm\n"));
    }

    @Test
    void read_skipsWhatItCannotReadAndFillsWhatIsMissing() {
        final ProjectFile file = ProjectFile.read("name: A\nthis line is nonsense\nkind: sideways\n");
        assertEquals("A", file.name());
        assertEquals(ProjectFile.Kind.EMPTY, file.kind());
        assertTrue(file.sources().isEmpty());
        assertEquals("", file.entry());
        assertEquals("", ProjectFile.read(null).name());
    }

    @Test
    void read_trimsListsAndDropsEmptyEntries() {
        final ProjectFile file = ProjectFile.read("sources:  a.can ,, b.can , \n");
        assertEquals(List.of("a.can", "b.can"), file.sources());
    }

    @Test
    void withSource_addsOnceAndKeepsOrder() {
        final ProjectFile more = stockWatch().withSource("Format.can");
        assertEquals(List.of("StockWatch.can", "Helpers.can", "Format.can"), more.sources());
        assertSame(more, more.withSource("Format.can"));
    }

    @Test
    void withReference_neverReferencesItself() {
        final ProjectFile file = stockWatch();
        assertSame(file, file.withReference("StockWatch"));
        assertEquals(List.of("Helpers", "Format"), stockWatch().withReference("Format").references());
    }

    @Test
    void buildsAListing_isFalseForALibraryAndForNoEntry() {
        assertTrue(stockWatch().buildsAListing());
        assertFalse(new ProjectFile("L", ProjectFile.Kind.LIBRARY, "jsc:cannon", List.of(), List.of(), "").buildsAListing());
        assertFalse(new ProjectFile("E", ProjectFile.Kind.CONSOLE, "jsc:cannon", List.of(), List.of(), "").buildsAListing());
    }

    @Test
    void kind_readsItsOwnWordsWhateverTheCase() {
        assertEquals(ProjectFile.Kind.CONSOLE, ProjectFile.Kind.of("Console"));
        assertEquals(ProjectFile.Kind.LIBRARY, ProjectFile.Kind.of(" library "));
        assertEquals(ProjectFile.Kind.EMPTY, ProjectFile.Kind.of("bogus"));
        assertEquals(ProjectFile.Kind.EMPTY, ProjectFile.Kind.of(null));
    }

    @Test
    void fileName_andDefaultEntry_followTheName() {
        assertEquals("StockWatch.canproj", ProjectFile.fileName("StockWatch"));
        assertEquals("build/StockWatch.asm", ProjectFile.defaultEntry("StockWatch"));
    }
}
