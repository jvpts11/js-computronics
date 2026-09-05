/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.block;

import com.mojang.serialization.MapCodec;
import dev.jstech.core.tier.HardwareEra;

/**
 * The Vintage-era Crafting Computer: the same machine as the Standard one, but built on a Vintage
 * consumer board (Baby-AT / AT) and wearing the Vintage skin.
 */
public class VintageCraftingComputerBlock extends CraftingComputerBlock {

    // simpleCodec keys on the concrete class, so each block variant needs its own.
    public static final MapCodec<VintageCraftingComputerBlock> CODEC =
            simpleCodec(VintageCraftingComputerBlock::new);

    public VintageCraftingComputerBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public HardwareEra era() {
        return HardwareEra.VINTAGE;
    }

    @Override
    protected MapCodec<? extends CraftingComputerBlock> codec() {
        return CODEC;
    }
}
