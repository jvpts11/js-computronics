/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.block;

import com.mojang.serialization.MapCodec;
import dev.jstech.computers.ComputingModule;
import dev.jstech.core.tier.HardwareEra;

/**
 * The Legacy-era Mainframe: the same orchestrator as the Standard one, but built on a Legacy MTX
 * board and wearing the Legacy skin.
 */
public class LegacyMainframeBlock extends MainframeBlock {

    // simpleCodec keys on the concrete class, so each block variant needs its own.
    public static final MapCodec<LegacyMainframeBlock> CODEC = simpleCodec(LegacyMainframeBlock::new);

    public LegacyMainframeBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public HardwareEra era() {
        return HardwareEra.LEGACY;
    }

    @Override
    protected MapCodec<? extends MainframeBlock> codec() {
        return CODEC;
    }

    @Override
    protected net.minecraft.world.item.Item blockItem() {
        return ComputingModule.LEGACY_MAINFRAME_ITEM.get();
    }
}
