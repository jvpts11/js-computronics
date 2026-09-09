/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os.edit.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SolutionFileTest {

    private static SolutionFile two() {
        return new SolutionFile("StockWatch",
                List.of("StockWatch/StockWatch.canproj", "Helpers/Helpers.canproj"), "StockWatch");
    }

    @Test
    void write_thenRead_roundTrips() {
        assertEquals(two(), SolutionFile.read(two().write()));
    }

    @Test
    void write_listsOneProjectPerLine() {
        final String text = two().write();
        assertTrue(text.startsWith("solution: StockWatch\n"));
        assertTrue(text.contains("project: StockWatch/StockWatch.canproj\n"));
        assertTrue(text.contains("project: Helpers/Helpers.canproj\n"));
        assertTrue(text.endsWith("startup: StockWatch\n"));
    }

    @Test
    void read_survivesJunkAndAnEmptyFile() {
        final SolutionFile file = SolutionFile.read("solution: X\n???\nproject:\nproject: A/A.canproj\n");
        assertEquals("X", file.name());
        assertEquals(List.of("A/A.canproj"), file.projects());
        assertEquals("", file.startup());
        assertTrue(SolutionFile.read("").projects().isEmpty());
    }

    @Test
    void withProject_addsOnceAndMakesTheFirstTheStartup() {
        final SolutionFile empty = new SolutionFile("S", List.of(), "");
        final SolutionFile one = empty.withProject("A/A.canproj");
        assertEquals("A", one.startup(), "the first project added is the one that starts");
        assertSame(one, one.withProject("A/A.canproj"));
        final SolutionFile more = one.withProject("B/B.canproj");
        assertEquals("A", more.startup(), "a second project does not take over");
    }

    @Test
    void projectPath_andProjectNameOf_areInverses() {
        assertEquals("Helpers/Helpers.canproj", SolutionFile.projectPath("Helpers"));
        assertEquals("Helpers", SolutionFile.projectNameOf("Helpers/Helpers.canproj"));
        assertEquals("Lone", SolutionFile.projectNameOf("Lone.canproj"));
    }

    @Test
    void withStartup_changesOnlyThat() {
        final SolutionFile other = two().withStartup("Helpers");
        assertEquals("Helpers", other.startup());
        assertEquals(two().projects(), other.projects());
    }
}
