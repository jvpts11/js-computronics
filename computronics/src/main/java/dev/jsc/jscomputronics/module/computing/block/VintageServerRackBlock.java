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
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import net.minecraft.world.item.Item;

/**
 * The Vintage era's Server Rack: the beige minicomputer cabinet with brown trim and a chrome strip.
 * It seats Vintage servers only.
 */
public class VintageServerRackBlock extends ServerRackBlock {

    public static final MapCodec<VintageServerRackBlock> CODEC = simpleCodec(VintageServerRackBlock::new);

    public VintageServerRackBlock(final Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<VintageServerRackBlock> codec() {
        return CODEC;
    }

    @Override
    public HardwareEra era() {
        return HardwareEra.VINTAGE;
    }

    @Override
    protected Item blockItem() {
        return ComputingModule.VINTAGE_SERVER_RACK_ITEM.get();
    }
}
