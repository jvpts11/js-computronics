/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.block;

import com.mojang.serialization.MapCodec;
import dev.jstech.computronics.ComputingModule;
import dev.jstech.core.tier.HardwareEra;
import net.minecraft.world.item.Item;

/**
 * The Legacy era's Server Rack: the same cabinet, in the grey steel and blue accents of its decade.
 * It seats Legacy and Vintage servers, never a Standard one.
 */
public class LegacyServerRackBlock extends ServerRackBlock {

    public static final MapCodec<LegacyServerRackBlock> CODEC = simpleCodec(LegacyServerRackBlock::new);

    public LegacyServerRackBlock(final Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<LegacyServerRackBlock> codec() {
        return CODEC;
    }

    @Override
    public HardwareEra era() {
        return HardwareEra.LEGACY;
    }

    @Override
    protected Item blockItem() {
        return ComputingModule.LEGACY_SERVER_RACK_ITEM.get();
    }
}
