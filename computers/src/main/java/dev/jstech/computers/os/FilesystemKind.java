/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os;

/**
 * The filesystem model provided by a kernel.
 *
 * <p>This enum is pure and carries no Minecraft dependency, so it can be used freely in JUnit tests
 * and in kernel definitions without pulling in the binding layer.
 */
public enum FilesystemKind {
    /** No filesystem support, so data lives in memory only. */
    NONE,
    /** A flat key-value store with no directory hierarchy. */
    FLAT,
    /** A full hierarchical directory tree (folders within folders). */
    HIERARCHICAL
}
