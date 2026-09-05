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

/** The Vintage era's Cluster Management Computer: beige case, amber readout, a serial console strip. */
public class VintageClusterManagementComputerBlock extends ClusterManagementComputerBlock {

    public static final MapCodec<VintageClusterManagementComputerBlock> CODEC =
            simpleCodec(VintageClusterManagementComputerBlock::new);

    public VintageClusterManagementComputerBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public HardwareEra era() {
        return HardwareEra.VINTAGE;
    }

    @Override
    protected MapCodec<? extends ClusterManagementComputerBlock> codec() {
        return CODEC;
    }
}
