/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VimCommandTest {

    @Test
    void of_readsWriteOnItsOwn() {
        final VimCommand command = VimCommand.of("w");
        assertTrue(command.write());
        assertFalse(command.quit());
        assertFalse(command.force());
        assertTrue(command.ok());
    }

    @Test
    void of_readsQuitOnItsOwn() {
        final VimCommand command = VimCommand.of("q");
        assertFalse(command.write());
        assertTrue(command.quit());
        assertFalse(command.force());
    }

    @Test
    void of_readsAQuitInsistedOn() {
        final VimCommand command = VimCommand.of("q!");
        assertTrue(command.quit());
        assertTrue(command.force());
        assertFalse(command.write());
    }

    @Test
    void of_readsWriteAndQuitTogether() {
        for (final String line : new String[] {"wq", "x"}) {
            final VimCommand command = VimCommand.of(line);
            assertTrue(command.write(), line + " should write");
            assertTrue(command.quit(), line + " should quit");
            assertFalse(command.force(), line + " should not be forced");
        }
    }

    @Test
    void of_readsWriteAndQuitInsistedOn() {
        for (final String line : new String[] {"wq!", "x!"}) {
            final VimCommand command = VimCommand.of(line);
            assertTrue(command.write(), line + " should write");
            assertTrue(command.quit(), line + " should quit");
            assertTrue(command.force(), line + " should be forced");
        }
    }

    @Test
    void of_ignoresTheSpacesAroundIt() {
        assertTrue(VimCommand.of("  wq  ").write());
        assertTrue(VimCommand.of("  wq  ").quit());
    }

    @Test
    void of_doesNothingForAnEmptyLine() {
        final VimCommand command = VimCommand.of("");
        assertFalse(command.write());
        assertFalse(command.quit());
        assertTrue(command.ok());
    }

    @Test
    void of_refusesSomethingItDoesNotKnowByName() {
        final VimCommand command = VimCommand.of("wqx");
        assertFalse(command.ok());
        assertTrue(command.error().contains("wqx"));
        assertFalse(command.write(), "a line it could not read must not write");
        assertFalse(command.quit(), "a line it could not read must not quit");
    }

    @Test
    void of_refusesNothingRatherThanGuessing() {
        assertFalse(VimCommand.of("write").ok());
        assertFalse(VimCommand.of("quit").ok());
        assertFalse(VimCommand.of("!").ok());
    }

    @Test
    void of_treatsNullAsAnEmptyLine() {
        assertTrue(VimCommand.of(null).ok());
        assertFalse(VimCommand.of(null).quit());
    }

    @Test
    void unwritten_saysWhatToDoAboutIt() {
        assertTrue(VimCommand.unwritten().contains("!"));
    }

    @Test
    void unknown_namesWhatWasTyped() {
        assertEquals("E492: not an editor command: zz", VimCommand.unknown("zz").error());
    }
}
