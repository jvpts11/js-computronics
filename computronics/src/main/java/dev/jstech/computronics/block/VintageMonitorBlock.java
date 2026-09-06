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
 * The Vintage-era Monitor: the same peripheral as the Standard one, but wearing a CRT chassis and
 * displaying a DOS/phosphor-green screen when lit. Backed by the shared {@code MONITOR_BE}.
 */
public class VintageMonitorBlock extends MonitorBlock {

    // simpleCodec keys on the concrete class, so each monitor variant needs its own.
    public static final MapCodec<VintageMonitorBlock> CODEC = simpleCodec(VintageMonitorBlock::new);

    public VintageMonitorBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public HardwareEra era() {
        return HardwareEra.VINTAGE;
    }

    @Override
    protected MapCodec<? extends MonitorBlock> codec() {
        return CODEC;
    }
}
