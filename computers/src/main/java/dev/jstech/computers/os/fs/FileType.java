/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os.fs;

import java.util.Optional;

/**
 * The file types the filesystem recognises, each bound to a lowercase extension.
 *
 * <p>Two flags describe how a type is handled:
 * <ul>
 *   <li>{@code userEditable}, the player can open and edit this type in the Text Editor.</li>
 *   <li>{@code virtualProjection}, the type is generated on-the-fly from the disk's item
 *       storage and is never persisted as a real file. Only {@link #DAT} is a virtual
 *       projection; all others are stored on disk.</li>
 * </ul>
 *
 * <p>This enum is pure and carries no Minecraft dependency; it can be used freely
 * in JUnit tests and in binding code without pulling in the MC runtime.
 */
public enum FileType {

    /** IQL query script, user-editable. */
    IQL("iql", true, false),

    /** Plain text file, user-editable. */
    TXT("txt", true, false),

    /** System log, append-only; not directly editable by the player. */
    LOG("log", false, false),

    /** Configuration file, user-editable. */
    CFG("cfg", true, false),

    /** Comma-separated values, user-editable. */
    CSV("csv", true, false),

    /** Command script, user-editable. */
    CMD("cmd", true, false),

    /** Crafting-pattern data, not directly editable (managed by the Crafting Computer). */
    CRAFT("craft", false, false),

    /** Virtual read-only projection of a disk's item/fluid storage; never persisted. */
    DAT("dat", false, true),
    /*
     * What an install medium shows when opened: generated from its payload the way DAT is generated
     * from a disk's storage, never stored, never a player's to create, copy or delete.
     */
    /** A setup program a player runs to install what the medium carries. */
    EXE("exe", false, true),
    /** The same on a Linux medium. */
    SH("sh", false, true),
    /** The package manifest: name, package id, requirements and the install commands. */
    PKG("pkg", false, true),
    /** Setup information beside the installer, the way a disc of the era carried it. */
    INF("inf", false, true),
    /** Opaque installer payload (a cabinet, an image, a kernel): listed, never opened. */
    BIN("bin", false, true),

    /** Cannon source, user-editable, and what the compiler reads. */
    CAN("can", true, false),

    /**
     * The assembly the compiler writes, user-editable, and meant to be read.
     *
     * <p>It is text on purpose: a player can open what their program was turned into and follow it a
     * line at a time, which is the whole reason the compiler does not keep it to itself.
     */
    ASM("asm", true, false),

    /**
     * A Cannon package: its manifest and every file in it, in one piece of text.
     *
     * <p>Editable like the rest, because a package is something one player hands to another and the one
     * receiving it should be able to read every line before installing it. It is not {@code .pkg},
     * which already means the manifest projected off an installation disc: one extension meaning two
     * things depending on where the file sits is how a player learns not to trust what they open.
     */
    CPK("cpk", true, false),
    /** A solution: the projects a studio works on together, and which starts. */
    SLN("sln", true, false),
    /** A project: what it is made of and what it builds. */
    CANPROJ("canproj", true, false);

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
     * True for the ones written by hand or meant to be read, false for logs, patterns and payloads.
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
