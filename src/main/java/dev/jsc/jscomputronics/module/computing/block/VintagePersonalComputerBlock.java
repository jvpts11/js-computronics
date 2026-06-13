/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.block;

import com.mojang.serialization.MapCodec;
import dev.jsc.jscomputronics.common.tier.HardwareEra;

/**
 * The Vintage-era Personal Computer: the same machine as the Standard one, but built on a Vintage
 * consumer board (Baby-AT / AT) and wearing the Vintage skin.
 */
public class VintagePersonalComputerBlock extends PersonalComputerBlock {

    // simpleCodec keys on the concrete class, so each block variant needs its own.
    public static final MapCodec<VintagePersonalComputerBlock> CODEC =
            simpleCodec(VintagePersonalComputerBlock::new);

    public VintagePersonalComputerBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public HardwareEra era() {
        return HardwareEra.VINTAGE;
    }

    @Override
    protected MapCodec<? extends PersonalComputerBlock> codec() {
        return CODEC;
    }
}
