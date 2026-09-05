/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program.cli;

import java.util.List;
import java.util.Locale;

/**
 * The POSIX view over the computer's drives. Storage keeps the DOS model underneath (the system disk is
 * {@code C:}, further disks and media readers get the next letters), and this class maps a single rooted
 * tree onto it the way a Unix shell sees it:
 * <ul>
 *   <li>{@code /} is the root of the system disk, {@code /home/player} is the user's home ({@code ~});</li>
 *   <li>every other drive is mounted at {@code /media/<letter>} (e.g. {@code /media/d});</li>
 *   <li>relative paths, {@code .} and {@code ..} resolve against the current directory as usual.</li>
 * </ul>
 * Pure (no Minecraft types), so it is unit-tested; the command set converts every path argument through
 * {@link #toDos} and then reuses the same filesystem facade the DOS shell uses.
 */
public final class PosixPath {

    /** The drive whose root is {@code /}. */
    public static final char SYSTEM_DRIVE = 'C';
    /** The home directory segments under the root. */
    public static final List<String> HOME_SEGMENTS = List.of("home", "player");
    /** The mount point directory for the other drives. */
    public static final String MEDIA_ROOT = "media";

    private PosixPath() {
    }

    /** The home directory as a location on the system drive. */
    public static DosPath.Location home() {
        return new DosPath.Location(SYSTEM_DRIVE, HOME_SEGMENTS);
    }

    /**
     * Converts a POSIX path argument into the DOS-form string {@link DosPath#resolve} understands. Absolute
     * paths become drive-qualified ({@code /x} is {@code C:\x}, {@code /media/d/x} is {@code D:\x}), a
     * leading {@code ~} expands to the home directory, and relative paths pass through unchanged (they are
     * resolved against the current directory, {@code .} and {@code ..} included).
     */
    public static String toDos(final String input) {
        final String in = input == null ? "" : input.trim();
        if (in.isEmpty()) {
            return "";
        }
        if (in.equals("~")) {
            return SYSTEM_DRIVE + ":\\" + String.join("\\", HOME_SEGMENTS);
        }
        if (in.startsWith("~/")) {
            return SYSTEM_DRIVE + ":\\" + String.join("\\", HOME_SEGMENTS) + "\\" + in.substring(2);
        }
        if (!in.startsWith("/")) {
            return in;
        }
        final String rest = in.replaceFirst("^/+", "");
        final String mediaPrefix = MEDIA_ROOT + "/";
        if (rest.startsWith(mediaPrefix)) {
            final String afterMedia = rest.substring(mediaPrefix.length());
            final int slash = afterMedia.indexOf('/');
            final String mount = slash < 0 ? afterMedia : afterMedia.substring(0, slash);
            if (mount.length() == 1 && Character.isLetter(mount.charAt(0))) {
                final char drive = Character.toUpperCase(mount.charAt(0));
                final String tail = slash < 0 ? "" : afterMedia.substring(slash + 1);
                return drive + ":\\" + tail.replace('/', '\\');
            }
        }
        return SYSTEM_DRIVE + ":\\" + rest.replace('/', '\\');
    }

    /** Renders a location as an absolute POSIX path ({@code /}, {@code /home/player/docs}, {@code /media/d/x}). */
    public static String render(final DosPath.Location location) {
        final String joined = String.join("/", location.segments());
        if (location.drive() == SYSTEM_DRIVE) {
            return "/" + joined;
        }
        final String mount = "/" + MEDIA_ROOT + "/" + Character.toString(location.drive()).toLowerCase(Locale.ROOT);
        return joined.isEmpty() ? mount : mount + "/" + joined;
    }

    /** Renders a location for the prompt, abbreviating the home directory to {@code ~}. */
    public static String renderForPrompt(final DosPath.Location location) {
        if (location.drive() == SYSTEM_DRIVE && startsWithHome(location.segments())) {
            final List<String> rest = location.segments().subList(HOME_SEGMENTS.size(), location.segments().size());
            return rest.isEmpty() ? "~" : "~/" + String.join("/", rest);
        }
        return render(location);
    }

    private static boolean startsWithHome(final List<String> segments) {
        if (segments.size() < HOME_SEGMENTS.size()) {
            return false;
        }
        for (int i = 0; i < HOME_SEGMENTS.size(); i++) {
            if (!segments.get(i).equalsIgnoreCase(HOME_SEGMENTS.get(i))) {
                return false;
            }
        }
        return true;
    }
}
