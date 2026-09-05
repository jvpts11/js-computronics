/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PosixPathTest {

    @Test
    void toDos_rootAbsolute_mapsToSystemDriveRoot() {
        assertEquals("C:\\", PosixPath.toDos("/"));
    }

    @Test
    void toDos_absolutePath_mapsToSystemDrive() {
        assertEquals("C:\\etc\\os-release", PosixPath.toDos("/etc/os-release"));
    }

    @Test
    void toDos_tilde_expandsToHome() {
        assertEquals("C:\\home\\player", PosixPath.toDos("~"));
        assertEquals("C:\\home\\player\\docs", PosixPath.toDos("~/docs"));
    }

    @Test
    void toDos_mediaMount_mapsToDriveLetter() {
        assertEquals("D:\\", PosixPath.toDos("/media/d"));
        assertEquals("E:\\recipes\\iron.craft", PosixPath.toDos("/media/e/recipes/iron.craft"));
    }

    @Test
    void toDos_mediaWithoutLetter_staysOnSystemDrive() {
        assertEquals("C:\\media\\usb", PosixPath.toDos("/media/usb"));
    }

    @Test
    void toDos_relative_passesThrough() {
        assertEquals("docs/../notes", PosixPath.toDos("docs/../notes"));
        assertEquals("", PosixPath.toDos("   "));
    }

    @Test
    void render_systemDrive_isRootedTree() {
        assertEquals("/", PosixPath.render(DosPath.Location.root('C')));
        assertEquals("/home/player/docs", PosixPath.render(new DosPath.Location('C', List.of("home", "player", "docs"))));
    }

    @Test
    void render_otherDrive_isMediaMount() {
        assertEquals("/media/d", PosixPath.render(DosPath.Location.root('D')));
        assertEquals("/media/e/x", PosixPath.render(new DosPath.Location('E', List.of("x"))));
    }

    @Test
    void renderForPrompt_home_abbreviatesToTilde() {
        assertEquals("~", PosixPath.renderForPrompt(PosixPath.home()));
        assertEquals("~/docs", PosixPath.renderForPrompt(PosixPath.home().child("docs")));
        assertEquals("/etc", PosixPath.renderForPrompt(new DosPath.Location('C', List.of("etc"))));
    }

    @Test
    void roundTrip_resolveThroughDosPath_landsOnExpectedLocation() {
        final DosPath.Location cwd = PosixPath.home();
        final DosPath.Location resolved = DosPath.resolve(cwd, PosixPath.toDos("../shared"));
        assertEquals("/home/shared", PosixPath.render(resolved));
        final DosPath.Location media = DosPath.resolve(cwd, PosixPath.toDos("/media/d/backup"));
        assertEquals("/media/d/backup", PosixPath.render(media));
    }
}
