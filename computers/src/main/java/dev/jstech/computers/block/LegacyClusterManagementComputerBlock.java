/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.block;

import com.mojang.serialization.MapCodec;
import dev.jstech.core.tier.HardwareEra;

/** The Legacy era's Cluster Management Computer: grey box, green bar graph, BNC and serial ports. */
public class LegacyClusterManagementComputerBlock extends ClusterManagementComputerBlock {

    public static final MapCodec<LegacyClusterManagementComputerBlock> CODEC =
            simpleCodec(LegacyClusterManagementComputerBlock::new);

    public LegacyClusterManagementComputerBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public HardwareEra era() {
        return HardwareEra.LEGACY;
    }

    @Override
    protected MapCodec<? extends ClusterManagementComputerBlock> codec() {
        return CODEC;
    }
}
