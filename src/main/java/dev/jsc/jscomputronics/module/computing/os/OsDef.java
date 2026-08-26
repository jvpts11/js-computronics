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

import java.util.Optional;

/**
 * Immutable descriptor for an operating system registered with the mod.
 *
 * <p>Each OS declares the capability tier it provides, the minimum hardware era it requires,
 * the kernel it runs on, the disk footprint it consumes on installation, and an optional install
 * media item that carries the OS installer payload.
 *
 * @param id             unique registry key for this OS (e.g. {@code jsc:mc_dos})
 * @param capability     the capability tier this OS provides to programs and the player
 * @param minEra         the minimum hardware era required to install this OS; the era acts as a
 *                       minimum — installing on hardware at this era or later is accepted, older
 *                       hardware is rejected
 * @param kernelId       registry key of the kernel this OS runs on
 * @param footprintItems the number of storage slots consumed by this OS when installed (1 item = 4 MB)
 * @param installMediaId optional registry key of the install media item that delivers this OS;
 *                       empty when the OS is provisioned programmatically (e.g. server auto-provision)
 */
public record OsDef(
        ResourceLocation id,
        OsCapability capability,
        HardwareEra minEra,
        ResourceLocation kernelId,
        int footprintItems,
        Optional<ResourceLocation> installMediaId
) {}
