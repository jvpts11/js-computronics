/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os;

import dev.jsc.jscomputronics.common.tier.HardwareEra;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable descriptor for a program registered with the mod.
 *
 * <p>A program declares the minimum OS capability and minimum OS era it requires. Both conditions
 * must be satisfied at runtime for the program to launch; see {@link OsGating#canRun}.
 *
 * @param id            unique registry key for this program (e.g. {@code jsc:command_prompt})
 * @param minCapability the minimum OS capability tier required to run this program
 * @param minOsEra      the minimum OS era required to run this program
 * @param kind          classification of how this program runs and what tier it needs
 */
public record ProgramDef(
        ResourceLocation id,
        OsCapability minCapability,
        HardwareEra minOsEra,
        ProgramKind kind
) {}
