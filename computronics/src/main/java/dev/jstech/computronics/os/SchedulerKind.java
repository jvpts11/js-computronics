/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os;

/**
 * The task-scheduling model implemented by a kernel.
 *
 * <p>This enum is pure and carries no Minecraft dependency, so it can be used freely in JUnit tests
 * and in kernel definitions without pulling in the binding layer.
 */
public enum SchedulerKind {
    /** No multitasking scheduler — single-task or batch execution only. */
    NONE,
    /** Cooperative multitasking — tasks yield voluntarily. */
    COOPERATIVE,
    /** Preemptive multitasking — the kernel forcibly context-switches tasks. */
    PREEMPTIVE
}
