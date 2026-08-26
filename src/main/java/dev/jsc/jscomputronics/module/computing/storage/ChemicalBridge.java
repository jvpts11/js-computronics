/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * What an integration must provide for its chemicals to be network data: a port onto a block's chemical
 * capability, and the identity of a chemical (existence, name, colour) by registry id. The core never names a
 * chemical mod; a bridge is registered only when its mod is present.
 */
public interface ChemicalBridge {

    /** The chemical port of the block at {@code pos} as seen from {@code side}, if the block has one. */
    Optional<ChemicalPort> portFor(Level level, BlockPos pos, @Nullable Direction side);

    /** Whether {@code chemical} is a registered chemical of this bridge's mod. */
    boolean exists(ResourceLocation chemical);

    /** The chemical's display name, as its mod shows it. */
    Component displayName(ResourceLocation chemical);

    /** The chemical's colour (ARGB), for the GUIs' tinted swatch. */
    int tint(ResourceLocation chemical);
}
