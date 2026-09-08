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
 * The Vintage-era Mainframe: the same orchestrator as the Standard one, but built on a Vintage MTX
 * board and wearing the Vintage skin.
 */
public class VintageMainframeBlock extends MainframeBlock {

    // simpleCodec keys on the concrete class, so each block variant needs its own.
    public static final MapCodec<VintageMainframeBlock> CODEC = simpleCodec(VintageMainframeBlock::new);

    public VintageMainframeBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public HardwareEra era() {
        return HardwareEra.VINTAGE;
    }

    @Override
    protected MapCodec<? extends MainframeBlock> codec() {
        return CODEC;
    }

    @Override
    protected net.minecraft.world.item.Item blockItem() {
        return ComputingModule.VINTAGE_MAINFRAME_ITEM.get();
    }
}
