/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * DOS-style path resolution for the command line: parses a typed path (absolute, relative, or drive-qualified),
 * resolves it against the session's current location, and formats both the on-disk path and the DOS display path.
 *
 * <p>Pure logic with no Minecraft types, so the whole path algebra is unit-tested. The on-disk filesystem keeps
 * {@code '/'}-separated paths ({@link dev.jstech.computronics.os.fs.FsPaths}); the shell shows the
 * player {@code '\'}-separated DOS paths with a drive letter. This class bridges the two. Segment matching against
 * real files is case-insensitive at the filesystem layer; this class preserves the case it is given.
 */
public final class DosPath {

    private DosPath() {
    }

    /**
     * A resolved absolute location: an (upper-cased) drive letter and the directory/file segments from that drive's
     * root. An empty segment list is the drive root.
     */
    public record Location(char drive, List<String> segments) {

        public Location {
            drive = Character.toUpperCase(drive);
            segments = List.copyOf(segments);
        }

        /** The drive's root, e.g. {@code C:\}. */
        public static Location root(final char drive) {
            return new Location(drive, List.of());
        }

        /** The {@code '/'}-separated path the on-disk filesystem uses; {@code ""} for the root. */
        public String storagePath() {
            return String.join("/", segments);
        }

        /** The DOS display path, e.g. {@code C:\SYSTEM\CRAFTS} (or {@code C:\} at the root). */
        public String dosPath() {
            return drive + ":\\" + String.join("\\", segments);
        }

        public boolean isRoot() {
            return segments.isEmpty();
        }

        /** The last segment (a directory or file name), or {@code ""} at the root. */
        public String name() {
            return segments.isEmpty() ? "" : segments.get(segments.size() - 1);
        }

        /** This location's parent; the root is its own parent. */
        public Location parent() {
            if (segments.isEmpty()) {
                return this;
            }
            final List<String> up = new ArrayList<>(segments);
            up.remove(up.size() - 1);
            return new Location(drive, up);
        }

        /** A child of this location with the given name appended. */
        public Location child(final String segment) {
            final List<String> down = new ArrayList<>(segments);
            down.add(segment);
            return new Location(drive, down);
        }
    }

    /**
     * Resolves {@code input} against {@code current}. Handles a leading drive qualifier ({@code C:} or {@code C:\x}),
     * an absolute path from the current drive's root ({@code \x}), a relative path ({@code x}, {@code ..\x}, {@code .}),
     * and both separators ({@code \} and {@code /}). {@code ..} above the root is clamped to the root rather than an
     * error. A {@code null} or blank input returns {@code current} unchanged.
     */
    public static Location resolve(final Location current, final String input) {
        String in = input == null ? "" : input.trim();
        if (in.isEmpty()) {
            return current;
        }
        char drive = current.drive();
        final List<String> base;
        if (in.length() >= 2 && in.charAt(1) == ':' && Character.isLetter(in.charAt(0))) {
            /*
             * Drive-qualified: switch drive. We do not track a per-drive current directory, so a bare "C:" or a
             * "C:path" both resolve from that drive's root.
             */
            drive = Character.toUpperCase(in.charAt(0));
            in = in.substring(2);
            if (in.startsWith("\\") || in.startsWith("/")) {
                in = in.substring(1);
            }
            base = new ArrayList<>();
        } else if (in.startsWith("\\") || in.startsWith("/")) {
            in = in.substring(1);
            base = new ArrayList<>();
        } else {
            base = new ArrayList<>(current.segments());
        }
        for (final String segment : in.split("[\\\\/]+")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (!base.isEmpty()) {
                    base.remove(base.size() - 1);
                }
                continue;
            }
            base.add(segment);
        }
        return new Location(drive, base);
    }
}
