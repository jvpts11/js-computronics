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
 * The Legacy-era Personal Computer: the same machine as the Standard one, but built on a Legacy
 * consumer ATX board and wearing the Legacy skin. A Legacy board is not interchangeable with a
 * Standard one even though both are ATX — each PC accepts only a board of its own era.
 */
public class LegacyPersonalComputerBlock extends PersonalComputerBlock {

    // simpleCodec keys on the concrete class, so each block variant needs its own.
    public static final MapCodec<LegacyPersonalComputerBlock> CODEC =
            simpleCodec(LegacyPersonalComputerBlock::new);

    public LegacyPersonalComputerBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public HardwareEra era() {
        return HardwareEra.LEGACY;
    }

    @Override
    protected MapCodec<? extends PersonalComputerBlock> codec() {
        return CODEC;
    }
}
