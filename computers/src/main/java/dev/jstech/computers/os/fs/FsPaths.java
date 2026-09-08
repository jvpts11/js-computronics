/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os.fs;

import dev.jstech.computers.os.FilesystemKind;
import dev.jstech.core.tier.HardwareEra;

/**
 * Pure utility methods for filesystem paths and file-size accounting.
 *
 * <p>No Minecraft types are imported here, so this class can be tested freely in JUnit.
 */
public final class FsPaths {

    /** Maximum length (in characters) for a single file or directory name segment: what a wire field carrying one must fit. */
    public static final int MAX_NAME_LENGTH = 64;

    private FsPaths() {
    }

    /**
     * Converts a byte count into mB-equivalents on a disk of {@code era}, rounding up: one mB-eq is
     * {@link HardwareEra#bytesPerMbEq()} bytes there, so the same file weighs more on older hardware.
     * Zero bytes map to zero mB-eq.
     *
     * @param bytes the raw byte count (must be &ge; 0)
     * @param era   the era of the disk the file sits on
     * @return the ceiling of {@code bytes / era.bytesPerMbEq()}
     */
    public static long sizeMbEq(final int bytes, final HardwareEra era) {
        if (bytes <= 0) {
            return 0L;
        }
        final long block = era.bytesPerMbEq();
        return (bytes + block - 1L) / block;
    }

    /**
     * Returns {@code true} if {@code name} is a legal single path segment: non-empty,
     * at most {@value #MAX_NAME_LENGTH} characters, containing no {@code '/'} and no
     * ISO control characters (codepoints 0x00-0x1F and 0x7F).
     *
     * @param name the candidate name to validate
     * @return true if the name is valid
     */
    public static boolean isValidName(final String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (c == '/' || Character.isISOControl(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns {@code true} if {@code path} is a legal path under the given filesystem kind.
     *
     * <ul>
     *   <li>{@link FilesystemKind#FLAT}, the path must be a single valid name with no
     *       {@code '/'} separator.</li>
     *   <li>{@link FilesystemKind#HIERARCHICAL}, the path is a {@code '/'}-separated
     *       sequence of segments, each of which must be a valid name. Empty segments (from
     *       leading, trailing, or consecutive slashes) are rejected.</li>
     *   <li>{@link FilesystemKind#NONE}, always returns {@code false}.</li>
     * </ul>
     *
     * @param path the candidate path
     * @param kind the filesystem model in use
     * @return true if the path is valid for the given kind
     */
    public static boolean isValidPath(final String path, final FilesystemKind kind) {
        if (path == null || kind == null) {
            return false;
        }
        return switch (kind) {
            case NONE -> false;
            case FLAT -> !path.contains("/") && isValidName(path);
            case HIERARCHICAL -> {
                // Reject paths that start or end with '/', and reject empty paths.
                if (path.isEmpty() || path.startsWith("/") || path.endsWith("/")) {
                    yield false;
                }
                final String[] segments = path.split("/", -1);
                for (final String segment : segments) {
                    // split with -1 preserves trailing empty strings; also catches "//".
                    if (!isValidName(segment)) {
                        yield false;
                    }
                }
                yield true;
            }
        };
    }

    /**
     * Returns the file name component of a path, the last {@code '/'}-delimited segment.
     * For a path with no {@code '/'}, the whole path is returned.
     *
     * @param path a non-null file path
     * @return the last segment of the path
     */
    public static String fileName(final String path) {
        final int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    /**
     * Returns the parent directory of a path, everything before the last {@code '/'}.
     * For a path with no {@code '/'} (a root-level flat name), an empty string is returned.
     *
     * @param path a non-null file path
     * @return the parent directory portion, or {@code ""} for a root-level path
     */
    public static String parentDir(final String path) {
        final int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    /**
     * Joins a directory and a child name into a path. The root directory is the empty string, so
     * joining it with {@code name} yields {@code name} with no leading separator.
     *
     * @param dir  the parent directory ({@code ""} for the root)
     * @param name the child file or directory name
     * @return {@code name} when {@code dir} is empty, otherwise {@code dir + "/" + name}
     */
    public static String join(final String dir, final String name) {
        return dir.isEmpty() ? name : dir + "/" + name;
    }

    /**
     * Returns {@code true} if {@code path} lies directly or indirectly under {@code dir}, that is,
     * {@code path} starts with {@code dir + "/"}. The root directory ({@code ""}) is an ancestor of
     * every path.
     *
     * @param dir  the candidate ancestor directory ({@code ""} for the root)
     * @param path the path to test
     * @return true if {@code path} is nested anywhere under {@code dir}
     */
    public static boolean isUnder(final String dir, final String path) {
        return dir.isEmpty() ? !path.isEmpty() : path.startsWith(dir + "/");
    }
}
