/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os.fs;

import dev.jstech.computronics.os.media.MediaFormat;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class InstallerLayoutTest {

    private static InstallerLayout.Facts program() {
        return new InstallerLayout.Facts("Crafting Manager", "craftmgr", "crafting_manager", false, false, false,
                1998, "Midsoft", "Plans and runs machine crafting from patterns",
                List.of("Frames XP or later", "128 MB disk"), "Frames", "Crafting Computer",
                List.of("pckmgr install craftmgr"));
    }

    private static InstallerLayout.Facts system(final boolean linux) {
        return new InstallerLayout.Facts(linux ? "Debian" : "Frames 11", linux ? "debian" : "frames_11",
                linux ? "debian" : "frames_11", true, false, linux, linux ? 1998 : 2021, "Midsoft",
                "", List.of("Standard hardware"), linux ? "Linux" : "Frames", "any computer", List.of());
    }

    private static List<String> paths(final List<InstallerLayout.Entry> entries) {
        return entries.stream().map(InstallerLayout.Entry::path).toList();
    }

    @Test
    public void entries_floppySpeaksEightDotThreeUppercase() {
        final List<String> paths = paths(InstallerLayout.entries(MediaFormat.FLOPPY, program()));
        assertTrue(paths.contains("SETUP.EXE"), paths.toString());
        assertTrue(paths.contains("README.TXT"), paths.toString());
        assertTrue(paths.contains("CRAFTMGR.PKG"), paths.toString());
        for (final String path : paths) {
            assertEquals(path.toUpperCase(java.util.Locale.ROOT), path, "a floppy name is uppercase: " + path);
            assertFalse(path.contains(" "), path);
        }
    }

    @Test
    public void entries_floppyTruncatesALongPackageIdToEight() {
        final InstallerLayout.Facts facts = new InstallerLayout.Facts("Integrity Monitor", "integrity",
                "integrity_monitor", false, true, false, 2026, "Midsoft", "", List.of(), "Frames", "server",
                List.of());
        assertTrue(paths(InstallerLayout.entries(MediaFormat.FLOPPY, facts)).contains("INTEGRIT.PKG"));
    }

    @Test
    public void entries_cdAddsAutorunAndACabinet() {
        final List<String> paths = paths(InstallerLayout.entries(MediaFormat.CD, program()));
        assertTrue(paths.contains("AUTORUN.INF"));
        assertTrue(paths.contains("DATA1.CAB"));
        assertTrue(paths.contains("SUPPORT"));
        assertFalse(paths.contains("I386"), "only a system disc carries an I386 folder");
    }

    @Test
    public void entries_standardMediaAreLowercaseWithSources() {
        for (final MediaFormat format : List.of(MediaFormat.DVD, MediaFormat.USB)) {
            final List<String> paths = paths(InstallerLayout.entries(format, system(false)));
            assertTrue(paths.contains("setup.exe"), format + " " + paths);
            assertTrue(paths.contains("sources/install.wim"), format + " " + paths);
            assertTrue(paths.contains("frames_11.pkg"), format + " " + paths);
        }
        assertTrue(paths(InstallerLayout.entries(MediaFormat.DVD, program())).contains("sources/data1.cab"));
    }

    @Test
    public void entries_linuxCarriesAKernelAndAnInstallScript() {
        final List<String> paths = paths(InstallerLayout.entries(MediaFormat.CD, system(true)));
        assertTrue(paths.contains("install.sh"));
        assertTrue(paths.contains("boot/vmlinuz"));
        assertTrue(paths.contains("boot/initrd.img"));
        assertFalse(paths.contains("SETUP.EXE"));
    }

    @Test
    public void entries_foldersComeBeforeTheirFiles() {
        final List<InstallerLayout.Entry> entries = InstallerLayout.entries(MediaFormat.DVD, system(false));
        int lastDir = -1;
        int firstFile = Integer.MAX_VALUE;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).directory()) {
                lastDir = i;
            } else {
                firstFile = Math.min(firstFile, i);
            }
        }
        assertTrue(lastDir < firstFile);
    }

    @Test
    public void entries_everyFileIsProjectedOrPlainText() {
        // Setup, manifests, autorun and payload are virtual types the filesystem never writes; the readme
        // and the licence are plain text so the Editor opens them, and the medium itself is locked.
        for (final MediaFormat format : MediaFormat.values()) {
            for (final InstallerLayout.Entry entry : InstallerLayout.entries(format, program())) {
                if (entry.directory()) {
                    continue;
                }
                final boolean text = entry.path().toLowerCase(java.util.Locale.ROOT).endsWith(".txt");
                assertTrue(text || entry.type().virtualProjection(), entry.path());
                assertTrue(text || !entry.type().userEditable(), entry.path());
            }
        }
    }

    @Test
    public void isSetup_findsTheSetupProgramInAnyCase() {
        assertTrue(InstallerLayout.isSetup("SETUP.EXE"));
        assertTrue(InstallerLayout.isSetup("setup.exe"));
        assertTrue(InstallerLayout.isSetup("media:123/install.sh"));
        assertFalse(InstallerLayout.isSetup("README.TXT"));
        assertFalse(InstallerLayout.isSetup("sources/install.wim"));
    }

    @Test
    public void textOf_readmeCarriesThePackageIdAndTheCommand() {
        final Optional<String> readme = InstallerLayout.textOf(MediaFormat.CD, program(), "README.TXT");
        assertTrue(readme.isPresent());
        assertTrue(readme.get().contains("Package id: craftmgr"), readme.get());
        assertTrue(readme.get().contains("pckmgr install craftmgr"), readme.get());
        assertTrue(readme.get().contains("run SETUP.EXE"), readme.get());
        assertTrue(readme.get().contains("(c) 1998 Midsoft"), readme.get());
    }

    @Test
    public void textOf_manifestListsEveryFactOnItsOwnLine() {
        final String manifest = InstallerLayout.textOf(MediaFormat.DVD, program(), "crafting_manager.pkg").orElseThrow();
        assertTrue(manifest.contains("package     craftmgr"), manifest);
        assertTrue(manifest.contains("kind        application"), manifest);
        assertTrue(manifest.contains("host        Crafting Computer"), manifest);
        assertTrue(manifest.contains("install     setup.exe"), manifest);
        final String service = InstallerLayout.manifest(MediaFormat.USB, new InstallerLayout.Facts("Mirror", "mirror",
                "mirror", false, true, false, 2026, "Midsoft", "", List.of(), "any", "Mainframe", List.of()));
        assertTrue(service.contains("kind        service"), service);
        assertTrue(service.contains("needs       none"), service);
    }

    @Test
    public void textOf_binariesHaveNoText() {
        assertTrue(InstallerLayout.textOf(MediaFormat.CD, program(), "DATA1.CAB").isEmpty());
        assertTrue(InstallerLayout.textOf(MediaFormat.CD, system(true), "boot/vmlinuz").isEmpty());
        assertTrue(InstallerLayout.textOf(MediaFormat.CD, program(), "AUTORUN.INF").orElseThrow().contains("open=SETUP.EXE"));
    }

    @Test
    public void setupName_followsTheDialect() {
        assertEquals("SETUP.EXE", InstallerLayout.setupName(MediaFormat.FLOPPY, program()));
        assertEquals("SETUP.EXE", InstallerLayout.setupName(MediaFormat.CD, program()));
        assertEquals("setup.exe", InstallerLayout.setupName(MediaFormat.USB, program()));
        assertEquals("install.sh", InstallerLayout.setupName(MediaFormat.CD, system(true)));
    }
}
