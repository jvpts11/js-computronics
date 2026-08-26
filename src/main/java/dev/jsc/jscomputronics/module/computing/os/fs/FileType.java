/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os.fs;

import java.util.Optional;

/**
 * The eight file types the filesystem recognises, each bound to a lowercase extension.
 *
 * <p>Two flags describe how a type is handled:
 * <ul>
 *   <li>{@code userEditable} — the player can open and edit this type in the Text Editor.</li>
 *   <li>{@code virtualProjection} — the type is generated on-the-fly from the disk's item
 *       storage and is never persisted as a real file. Only {@link #DAT} is a virtual
 *       projection; all others are stored on disk.</li>
 * </ul>
 *
 * <p>This enum is pure and carries no Minecraft dependency; it can be used freely
 * in JUnit tests and in binding code without pulling in the MC runtime.
 */
public enum FileType {

    /** IQL query script — user-editable. */
    IQL("iql", true, false),

    /** Plain text file — user-editable. */
    TXT("txt", true, false),

    /** System log — append-only; not directly editable by the player. */
    LOG("log", false, false),

    /** Configuration file — user-editable. */
    CFG("cfg", true, false),

    /** Comma-separated values — user-editable. */
    CSV("csv", true, false),

    /** Command script — user-editable. */
    CMD("cmd", true, false),

    /** Crafting-pattern data — not directly editable (managed by the Crafting Computer). */
    CRAFT("craft", false, false),

    /** Virtual read-only projection of a disk's item/fluid storage; never persisted. */
    DAT("dat", false, true);

    private final String extension;
    private final boolean userEditable;
    private final boolean virtualProjection;

    FileType(final String extension, final boolean userEditable, final boolean virtualProjection) {
        this.extension = extension;
        this.userEditable = userEditable;
        this.virtualProjection = virtualProjection;
    }

    /** The lowercase extension, without a leading dot (e.g. {@code "iql"}). */
    public String extension() {
        return extension;
    }

    /**
     * Whether the player can open and edit files of this type in the Text Editor.
     * True for IQL, TXT, CFG, CSV, and CMD; false for LOG, CRAFT, and DAT.
     */
    public boolean userEditable() {
        return userEditable;
    }

    /**
     * Whether files of this type are generated on-the-fly from the disk's item storage
     * rather than being persisted. Only {@link #DAT} returns true.
     */
    public boolean virtualProjection() {
        return virtualProjection;
    }

    /**
     * Resolves a file extension (case-insensitive, no leading dot) to the matching
     * {@link FileType}, or {@link Optional#empty()} if the extension is not recognised.
     *
     * @param ext the file extension to look up (e.g. {@code "iql"} or {@code "IQL"})
     * @return the matching type, or empty
     */
    public static Optional<FileType> fromExtension(final String ext) {
        if (ext == null) {
            return Optional.empty();
        }
        for (final FileType type : values()) {
            if (type.extension.equalsIgnoreCase(ext)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
