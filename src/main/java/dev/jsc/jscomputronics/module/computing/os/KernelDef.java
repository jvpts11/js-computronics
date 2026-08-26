/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os;

import net.minecraft.resources.ResourceLocation;

/**
 * Immutable descriptor for a kernel that an OS can run on top of.
 *
 * <p>A kernel defines the scheduling model and filesystem model available to the OS. Community
 * addons can register custom kernels via {@link JSComputronicsAPI#registerKernel(KernelDef)} to
 * ship alternative kernel implementations (e.g. Unix-like) without modifying the mod core.
 *
 * @param id         unique registry key for this kernel (e.g. {@code jsc:dos})
 * @param scheduler  the task-scheduling model this kernel provides
 * @param filesystem the filesystem model this kernel provides
 */
public record KernelDef(
        ResourceLocation id,
        SchedulerKind scheduler,
        FilesystemKind filesystem
) {}
