/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os.fs;

import java.nio.charset.StandardCharsets;

/**
 * An immutable value representing a single file stored on a disk.
 *
 * <p>{@code content} is the serialised file payload as a UTF-8 string — plain text for text
 * types ({@link FileType#IQL}, {@link FileType#TXT}, etc.) and a serialised representation
 * for binary-ish types such as {@link FileType#CRAFT}.
 *
 * <p>This record is pure and carries no Minecraft dependency.
 *
 * @param path    the full path of the file within the volume (e.g. {@code "script.iql"}
 *                for FLAT, or {@code "scripts/daily.iql"} for HIERARCHICAL)
 * @param type    the file type
 * @param content the file's text content encoded as a UTF-8 string
 */
public record StoredFile(String path, FileType type, String content) {

    /**
     * Returns the file size in raw bytes (UTF-8 encoding of {@link #content}).
     */
    public int byteSize() {
        return content.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * Returns the disk-space cost in mB-equivalents (1 mB-eq = 4 096 bytes),
     * rounded up to the nearest block.
     */
    public long weight() {
        return FsPaths.sizeMbEq(byteSize());
    }
}
