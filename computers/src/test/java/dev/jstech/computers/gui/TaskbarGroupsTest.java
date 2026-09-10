/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskbarGroupsTest {

    private static TaskbarGroups.Window win(final String key, final boolean minimized, final int serial) {
        return new TaskbarGroups.Window(key, minimized, serial);
    }

    @Test
    void group_putsEveryWindowOfOneProgramUnderOneEntry() {
        final List<TaskbarGroups.Entry> entries = TaskbarGroups.group(
                List.of(win("Files", false, 1), win("Editor", false, 2), win("Files", false, 3)),
                List.of(), "Files");
        assertEquals(2, entries.size(), "two programs, two entries");
        assertEquals("Files", entries.get(0).key());
        assertEquals(2, entries.get(0).windows(), "both Files windows count under the one entry");
        assertEquals("Editor", entries.get(1).key());
    }

    @Test
    void group_keepsTheOrderProgramsWereFirstOpenedNotTheStackingOrder() {
        // The front window (last in stacking order) belongs to Files, opened first.
        final List<TaskbarGroups.Entry> entries = TaskbarGroups.group(
                List.of(win("Editor", false, 2), win("Files", false, 1)), List.of(), "Files");
        assertEquals("Files", entries.get(0).key(), "Files was opened first, so it comes first");
        assertEquals("Editor", entries.get(1).key());
    }

    @Test
    void group_listsPinnedProgramsFirstAndKeepsThemWithNoWindow() {
        final List<TaskbarGroups.Entry> entries = TaskbarGroups.group(
                List.of(win("Editor", false, 1)), List.of("Terminal", "Files"), "Editor");
        assertEquals(List.of("Terminal", "Files", "Editor"),
                entries.stream().map(TaskbarGroups.Entry::key).toList(),
                "pinned programs come first in their own order, then the open ones");
        assertEquals(TaskbarGroups.State.PINNED, entries.get(0).state());
        assertFalse(entries.get(0).open());
        assertTrue(entries.get(0).pinned());
        assertFalse(entries.get(2).pinned());
    }

    @Test
    void group_aPinnedProgramThatIsOpenTakesItsPinnedPlace() {
        final List<TaskbarGroups.Entry> entries = TaskbarGroups.group(
                List.of(win("Editor", false, 1), win("Files", false, 2)), List.of("Files"), "Files");
        assertEquals("Files", entries.get(0).key(), "the pinned place is where its windows are listed");
        assertEquals(1, entries.get(0).windows());
        assertTrue(entries.get(0).pinned());
        assertEquals(TaskbarGroups.State.ACTIVE, entries.get(0).state());
    }

    @Test
    void group_statesFollowTheFrontWindowAndMinimizedOnes() {
        final List<TaskbarGroups.Entry> entries = TaskbarGroups.group(
                List.of(win("Files", true, 1), win("Files", true, 2), win("Editor", false, 3), win("Shell", false, 4)),
                List.of(), "Editor");
        assertEquals(TaskbarGroups.State.MINIMIZED, entries.get(0).state(), "every Files window is minimized");
        assertEquals(TaskbarGroups.State.ACTIVE, entries.get(1).state(), "the Editor holds the front window");
        assertEquals(TaskbarGroups.State.OPEN, entries.get(2).state(), "the Shell is open behind");
    }

    @Test
    void group_oneMinimizedWindowDoesNotMinimizeTheEntry() {
        final List<TaskbarGroups.Entry> entries = TaskbarGroups.group(
                List.of(win("Files", true, 1), win("Files", false, 2)), List.of(), null);
        assertEquals(TaskbarGroups.State.OPEN, entries.get(0).state());
    }

    @Test
    void group_ignoresAnEmptyPinnedKey() {
        final List<TaskbarGroups.Entry> entries = TaskbarGroups.group(List.of(), List.of("", "Files"), null);
        assertEquals(1, entries.size());
        assertEquals("Files", entries.get(0).key());
    }

    @Test
    void indexOf_findsAnEntryByItsProgram() {
        final List<TaskbarGroups.Entry> entries = TaskbarGroups.group(
                List.of(win("Files", false, 1), win("Editor", false, 2)), List.of(), null);
        assertEquals(1, TaskbarGroups.indexOf(entries, "Editor"));
        assertEquals(-1, TaskbarGroups.indexOf(entries, "Shell"));
    }
}
