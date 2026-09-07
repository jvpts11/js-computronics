/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os.fs;

import dev.jstech.computronics.os.fs.DiskFilesystem.FileEntry;
import dev.jstech.computronics.storage.ServerStorageContents;
import dev.jstech.computronics.storage.StorageKey;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates the read-only {@code .dat} filesystem projection from a disk's item/fluid storage.
 *
 * <p>Each {@link StorageKey} in a {@link ServerStorageContents} is projected to one
 * {@link FileEntry} of type {@link FileType#DAT}. The entry's path is derived from the key's
 * {@link StorageKey#displayName() display name}: non-filename characters are replaced, the
 * {@code .dat} extension is appended, and collisions are resolved with a numeric suffix (e.g.
 * {@code iron_ingot_2.dat}).
 *
 * <p>{@code .dat} entries are never persisted; they are generated on-demand from the disk's storage volume
 * each time {@link DiskFilesystem#list} is called. Items leave storage only via the Network
 * Interactor, and the filesystem API cannot write or delete {@code .dat} entries.
 */
public final class StorageProjection {

    private StorageProjection() {
    }

    /**
     * Builds the list of read-only {@code .dat} {@link FileEntry} records for the given storage
     * contents.
     *
     * <p>The weight of each entry equals {@link StorageKey#weight(long)} for the stored quantity,
     * matching the space already accounted for in the disk's MB budget (no double-counting).
     *
     * @param storage the disk's current item/fluid storage; may be
     *                {@link ServerStorageContents#EMPTY}
     * @return an unmodifiable list of {@code .dat} entries, one per {@link StorageKey}
     */
    public static List<FileEntry> project(final ServerStorageContents storage) {
        final Set<String> used = new LinkedHashSet<>();
        final List<FileEntry> entries = new ArrayList<>(storage.items().size());

        for (final Map.Entry<StorageKey, Long> entry : storage.items().entrySet()) {
            final StorageKey key = entry.getKey();
            final long qty = entry.getValue();

            final String baseName = sanitize(key.displayName().getString());
            final String path = uniquePath(baseName, used);
            used.add(path);

            entries.add(new FileEntry(path, FileType.DAT, key.weight(qty), true));
        }

        return List.copyOf(entries);
    }

    // Internal helpers

    /**
     * Converts an arbitrary display-name string into a valid file base name (no extension):
     * <ul>
     *   <li>Leading/trailing whitespace is stripped.</li>
     *   <li>Every character that is not alphanumeric, a hyphen, or an underscore is replaced with
     *       an underscore.</li>
     *   <li>Runs of consecutive underscores are collapsed to a single one.</li>
     *   <li>Leading and trailing underscores are removed.</li>
     *   <li>If the result is empty or longer than {@link FsPaths#MAX_NAME_LENGTH} - 4 (to leave
     *       room for the {@code .dat} suffix), it is replaced with or truncated to {@code "item"}.</li>
     * </ul>
     */
    static String sanitize(final String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "item";
        }
        /*
         * Replace anything that is not alphanumeric/hyphen/underscore/dot with underscore.
         * Then collapse consecutive underscores and trim boundary underscores.
         */
        String s = displayName.strip();
        final StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        // Collapse consecutive underscores.
        String result = sb.toString().replaceAll("_+", "_");
        // Trim leading/trailing underscores.
        result = result.replaceAll("^_+|_+$", "");

        if (result.isEmpty()) {
            return "item";
        }
        // The .dat suffix is 4 characters; leave room for it within MAX_NAME_LENGTH.
        final int maxBase = FsPaths.MAX_NAME_LENGTH - 4;
        if (result.length() > maxBase) {
            result = result.substring(0, maxBase);
        }
        return result;
    }

    /**
     * Returns a unique file path (base name + {@code .dat}) that is not already in {@code used}.
     * If {@code baseName + ".dat"} is already taken, tries {@code baseName + "_2.dat"},
     * {@code baseName + "_3.dat"}, and so on.
     */
    private static String uniquePath(final String baseName, final Set<String> used) {
        String candidate = baseName + ".dat";
        if (!used.contains(candidate)) {
            return candidate;
        }
        int suffix = 2;
        while (true) {
            candidate = baseName + "_" + suffix + ".dat";
            if (!used.contains(candidate)) {
                return candidate;
            }
            suffix++;
        }
    }
}
