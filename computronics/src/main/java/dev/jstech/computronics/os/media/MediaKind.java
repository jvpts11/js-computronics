/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os.media;

/**
 * The three content kinds a physical medium can carry.
 *
 * <ul>
 *   <li>{@link #OS_INSTALL} — a bootable OS installer; the payload identifies the OS by id.</li>
 *   <li>{@link #PROGRAM_INSTALL} — an add-on program installer; the payload identifies the program.</li>
 *   <li>{@link #DATA} — a portable storage snapshot; the content is a {@link dev.jstech.computronics.storage.ServerStorageContents} value.</li>
 * </ul>
 *
 * This enum is intentionally free of Minecraft and NeoForge imports so it can be used in pure-JUnit
 * tests without loading the game runtime. Codecs for persistence and network sync are constructed
 * inline at component-registration time in {@code ComputingModule}.
 */
public enum MediaKind {
    OS_INSTALL,
    PROGRAM_INSTALL,
    DATA
}
