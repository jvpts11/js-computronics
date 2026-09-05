/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.block;

import com.mojang.serialization.MapCodec;
import dev.jstech.core.tier.HardwareEra;

/**
 * The Legacy-era Crafting Computer: the same machine as the Standard one, but built on a Legacy
 * consumer board (ATX) and wearing the Legacy skin.
 */
public class LegacyCraftingComputerBlock extends CraftingComputerBlock {

    // simpleCodec keys on the concrete class, so each block variant needs its own.
    public static final MapCodec<LegacyCraftingComputerBlock> CODEC =
            simpleCodec(LegacyCraftingComputerBlock::new);

    public LegacyCraftingComputerBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public HardwareEra era() {
        return HardwareEra.LEGACY;
    }

    @Override
    protected MapCodec<? extends CraftingComputerBlock> codec() {
        return CODEC;
    }
}
