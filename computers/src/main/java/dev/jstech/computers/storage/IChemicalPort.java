/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.storage;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A block's chemical inventory as the network sees it: substances identified by registry id and measured in
 * millibuckets, with no knowledge of which mod provides them. An {@link IChemicalBridge} supplies ports for the
 * blocks of the mod it integrates; the storage engine moves data through them exactly as it does for items and
 * fluids.
 */
public interface IChemicalPort {

    /** Pushes up to {@code amount} mB of {@code chemical} into the block; returns how much it accepted. */
    long fill(ResourceLocation chemical, long amount, boolean simulate);

    /** Pulls up to {@code amount} mB of {@code chemical} out of the block; returns how much came out. */
    long drain(ResourceLocation chemical, long amount, boolean simulate);

    /** How much of {@code chemical} the block currently holds, in mB. */
    long count(ResourceLocation chemical);

    /** Every chemical the block currently holds, in tank order, without duplicates. */
    List<ResourceLocation> available();
}
