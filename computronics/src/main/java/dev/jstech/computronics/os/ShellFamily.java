/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os;

/**
 * The command-line syntax family a kernel gives its operating systems. It decides which command set the
 * terminal speaks and how paths are written: DOS-family kernels use drive letters, backslashes and verbs
 * like {@code dir}/{@code type}; POSIX-family kernels use a single rooted tree, forward slashes, mounted
 * media and verbs like {@code ls}/{@code cat}. An OS inherits the family from its kernel, so every
 * distribution built on the Linux kernel speaks bash syntax without declaring anything itself.
 */
public enum ShellFamily {

    /** Drive letters and DOS verbs (MC-DOS, the Frames command prompt). */
    DOS,

    /** A single rooted filesystem, mounts under {@code /media}, and Unix verbs (the Linux kernel). */
    POSIX
}
