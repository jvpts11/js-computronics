/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.program.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CliOutputTest {

    private static final int WIDTH = 60;

    private CliOutput out;

    @BeforeEach
    void setUp() {
        this.out = new CliOutput(WIDTH);
    }

    private List<String> text() {
        return this.out.lines().stream().map(CliLine::text).toList();
    }

    /** Where the description of a laid-out entry begins, which is the thing that has to line up. */
    private static int descriptionStart(final String line, final String description) {
        return line.indexOf(description);
    }

    @Test
    void entry_fillsTheGapWithDotsUpToTheColumn() {
        this.out.entry("  dir", "list the contents of a directory", 14);
        assertEquals("  dir ....... list the contents of a directory", text().get(0));
    }

    @Test
    void entry_startsEveryDescriptionAtTheSameColumn() {
        this.out.entry("  cd", "show or change the current directory", 16);
        this.out.entry("  uninstall", "remove an installed program", 16);
        this.out.entry("  format", "erase everything on a drive", 16);
        final List<String> lines = text();
        assertEquals(descriptionStart(lines.get(0), "show or change"),
                descriptionStart(lines.get(1), "remove an installed"));
        assertEquals(descriptionStart(lines.get(0), "show or change"),
                descriptionStart(lines.get(2), "erase everything"));
    }

    @Test
    void entry_keepsTheDotsWhateverTheDescriptionIsWorth() {
        /*
         * The bug this replaced: a long description was pushed to the right edge, so the dots had no
         * room and that one line came out without any while its neighbours had them.
         */
        this.out.entry("  mirror", "install or check the Mirror package service on the Mainframe", 16);
        assertTrue(text().get(0).contains("."), "a long description still gets its dots");
    }

    @Test
    void entry_leavesOneSpaceForANameThatReachesTheColumn() {
        this.out.entry("  averyverylongcommandname", "does a thing", 16);
        assertEquals("  averyverylongcommandname does a thing", text().get(0));
    }

    @Test
    void entry_stillStartsAtTheColumnWhenNoDotFits() {
        /*
         * A name two short of the column leaves room for the spaces around the dots and for no dots at
         * all. What matters is that the description still begins at the column, like every other line.
         */
        this.out.entry("abcdefghijklmn", "x", 16);
        assertEquals("abcdefghijklmn  x", text().get(0));
        assertEquals(16, descriptionStart(text().get(0), "x"));
    }

    @Test
    void row_stillPushesItsValueToTheRightEdge() {
        // The other shape is unchanged: a figure read off a listing belongs at the edge.
        this.out.row("capacity", "500 GB");
        final String line = text().get(0);
        assertEquals(WIDTH, line.length());
        assertTrue(line.endsWith("500 GB"));
    }

    @Test
    void row_fallsBackToTwoSpacesWhenThereIsNoRoom() {
        this.out.row("a".repeat(40), "b".repeat(30));
        assertEquals("a".repeat(40) + "  " + "b".repeat(30), text().get(0));
    }
}
