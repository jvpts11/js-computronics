/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os.fs;

import dev.jstech.computronics.os.OsCapability;

import java.util.List;

/**
 * The system folder skeleton a graphical desktop OS lays down on the system disk when it is
 * installed. Only a {@link OsCapability#FULL_DESKTOP} OS gets the full tree; terminal-only and
 * network OSes keep a bare filesystem.
 *
 * <p>This class is pure and carries no Minecraft dependency, so the layout can be unit-tested in
 * JUnit and reused by the install path in the binding layer. Parent directories are listed before
 * their children so a caller that materialises them in order never references a missing parent.
 */
public final class SystemLayout {

    private SystemLayout() {
    }

    /** The directory whose files are shown as icons on the desktop background. */
    public static final String DESKTOP_DIR = "Users/Public/Desktop";

    /** The ordered system directories a full desktop OS provides (parents before children). */
    private static final List<String> DESKTOP_DIRECTORIES = List.of(
            "Program Files",
            "Program Files (x86)",
            "Windows",
            "Users",
            "Users/Public",
            "Users/Public/Desktop",
            "Users/Public/Documents"
    );

    /** The directory a POSIX (Linux) system shows as the desktop once a desktop environment is installed. */
    public static final String POSIX_DESKTOP_DIR = "home/player/Desktop";

    /** The ordered system tree a POSIX (Linux) OS lays down on install (parents before children). */
    private static final List<String> POSIX_DIRECTORIES = List.of(
            "bin",
            "etc",
            "home",
            "home/player",
            "home/player/Desktop",
            "home/player/Documents",
            "media",
            "tmp",
            "usr",
            "usr/bin",
            "var"
    );

    /**
     * Returns the system directories the given capability provisions on install.
     *
     * @param capability the capability tier of the OS being installed
     * @return the ordered directory paths to create, or an empty list for non-desktop OSes
     */
    public static List<String> directoriesFor(final OsCapability capability) {
        return capability == OsCapability.FULL_DESKTOP ? DESKTOP_DIRECTORIES : List.of();
    }

    /** The desktop folder for an OS: the Unix home desktop on a POSIX kernel, the Windows-style one otherwise. */
    public static String desktopDirFor(final dev.jstech.computronics.os.KernelDef kernel) {
        return kernel != null && kernel.shellFamily() == dev.jstech.computronics.os.ShellFamily.POSIX
                ? POSIX_DESKTOP_DIR : DESKTOP_DIR;
    }

    /**
     * Returns the system directories an OS provisions on install, by its kernel's shell family: a POSIX
     * kernel lays down the Unix tree regardless of capability (a Linux TTY still has /home and /etc), a DOS
     * kernel follows the capability rule above.
     */
    public static List<String> directoriesFor(final dev.jstech.computronics.os.OsDef os,
                                              final dev.jstech.computronics.os.KernelDef kernel) {
        if (kernel != null && kernel.shellFamily() == dev.jstech.computronics.os.ShellFamily.POSIX) {
            return POSIX_DIRECTORIES;
        }
        return directoriesFor(os.capability());
    }
}
