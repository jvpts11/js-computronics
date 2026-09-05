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
 * The Legacy-era Monitor: the same peripheral as the Standard one, but wearing a flat LCD chassis
 * from the early-2000s generation and displaying an XP-Luna-style desktop when lit. Backed by the
 * shared {@code MONITOR_BE}.
 */
public class LegacyMonitorBlock extends MonitorBlock {

    // simpleCodec keys on the concrete class, so each monitor variant needs its own.
    public static final MapCodec<LegacyMonitorBlock> CODEC = simpleCodec(LegacyMonitorBlock::new);

    public LegacyMonitorBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public HardwareEra era() {
        return HardwareEra.LEGACY;
    }

    @Override
    protected MapCodec<? extends MonitorBlock> codec() {
        return CODEC;
    }
}
