/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FileOpenersTest {

    private static final List<String> NOTHING = List.of();
    private static final List<String> EVERYTHING =
            List.of("virtual_studio_code", "virtual_studio", "exposure", "nms", "crafting_manager");

    @Test
    void defaultFor_opensASourceFileInACodeEditorWhenThereIsOne() {
        assertEquals("virtual_studio_code", FileOpeners.defaultFor("progs/a.can", EVERYTHING));
        assertEquals("virtual_studio_code", FileOpeners.defaultFor("progs/a.asm", EVERYTHING));
    }

    @Test
    void defaultFor_runsACompiledProgramWhenTheRuntimeIsThere() {
        // Opening a compiled program means running it; reading its listing is what "Open with" is for.
        assertEquals(FileOpeners.RUNTIME, FileOpeners.defaultFor("progs/a.asm", List.of("cannonrt", "exposure")));
        assertEquals("exposure", FileOpeners.defaultFor("progs/a.asm", List.of("exposure")));
    }

    @Test
    void defaultFor_fallsBackToThePlainEditorOnAMachineWithNoCodeEditor() {
        assertEquals(FileOpeners.EDITOR, FileOpeners.defaultFor("progs/a.can", NOTHING));
    }

    @Test
    void defaultFor_opensPlainTextInTheEditor() {
        assertEquals(FileOpeners.EDITOR, FileOpeners.defaultFor("notes.txt", EVERYTHING));
    }

    @Test
    void defaultFor_opensAQueryInTheStudioWhenItIsInstalled() {
        assertEquals("nms", FileOpeners.defaultFor("q.iql", EVERYTHING));
        assertEquals(FileOpeners.EDITOR, FileOpeners.defaultFor("q.iql", NOTHING));
    }

    @Test
    void defaultFor_saysNothingOpensAPatternWithoutItsProgram() {
        assertEquals("crafting_manager", FileOpeners.defaultFor("iron.craft", EVERYTHING));
        assertEquals("", FileOpeners.defaultFor("iron.craft", NOTHING));
    }

    @Test
    void defaultFor_saysNothingOpensAKindNobodyClaims() {
        assertEquals("", FileOpeners.defaultFor("noextension", EVERYTHING));
        assertEquals("", FileOpeners.defaultFor("trailing.", EVERYTHING));
        assertEquals("", FileOpeners.defaultFor(null, EVERYTHING));
    }

    @Test
    void defaultFor_refusesToOpenWhatCannotBeEdited() {
        /*
         * A .dat is a read-only view of what a drive is holding and an .exe is a program: handing either
         * to the text editor would offer to edit something the filesystem itself refuses to write.
         */
        for (final String path : List.of("items.dat", "setup.exe", "thing.bin", "disc.inf")) {
            assertEquals("", FileOpeners.defaultFor(path, EVERYTHING), path + " must open in nothing");
        }
    }

    @Test
    void available_ordersTheCodeEditorsBestFirst() {
        assertEquals(List.of("virtual_studio_code", "virtual_studio", "exposure", FileOpeners.EDITOR),
                FileOpeners.available("a.can", EVERYTHING));
    }

    @Test
    void available_keepsOnlyWhatTheMachineHas() {
        assertEquals(List.of("exposure", FileOpeners.EDITOR),
                FileOpeners.available("a.can", List.of("exposure")));
    }

    @Test
    void available_alwaysOffersThePlainEditorForSomethingReadable() {
        for (final String path : List.of("a.can", "a.asm", "a.txt", "a.iql", "a.cfg", "a.csv", "a.cmd", "a.log")) {
            assertTrue(FileOpeners.available(path, NOTHING).contains(FileOpeners.EDITOR),
                    path + " should still be readable on a bare machine");
        }
    }

    @Test
    void available_readsTheExtensionWhateverItsCase() {
        assertEquals(FileOpeners.available("A.CAN", EVERYTHING), FileOpeners.available("a.can", EVERYTHING));
    }

    @Test
    void creatable_offersOnlyTheKindsWorthMakingEmpty() {
        final List<FileType> kinds = FileOpeners.creatable();
        assertTrue(kinds.contains(FileType.TXT), "a text file is the plain case");
        assertTrue(kinds.contains(FileType.CAN), "a program has to be startable from nothing");
        assertFalse(kinds.contains(FileType.CRAFT), "a pattern is written by the encoder, not by hand");
        assertFalse(kinds.contains(FileType.ASM), "assembly is what the compiler produces");
        assertFalse(kinds.contains(FileType.DAT), "a projection of stored items is not a file to create");
    }

    @Test
    void creatable_offersOnlyKindsAPlayerCanThenEdit() {
        for (final FileType type : FileOpeners.creatable()) {
            assertTrue(type.userEditable(), type + " is offered for creation but cannot be edited");
        }
    }
}
