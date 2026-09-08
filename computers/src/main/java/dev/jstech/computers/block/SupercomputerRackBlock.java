/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.block;

import com.mojang.serialization.MapCodec;
import dev.jstech.computers.rack.RackChassis;
import dev.jstech.core.network.DataTier;

import java.util.EnumSet;
import java.util.Set;

/**
 * The Supercomputer Rack: the same 8U cabinet as the Server Rack (units, front slots, KVM, the
 * physical GUI), but it seats only Supercomputer Nodes, and its rear port speaks only the high-compute
 * fabric. A supercomputer is every such rack tied together by that fabric behind one HBW Interface;
 * the interface is what joins the data network, so this cabinet never carries a data-cable tier.
 */
public class SupercomputerRackBlock extends ServerRackBlock {

    public static final MapCodec<SupercomputerRackBlock> CODEC = simpleCodec(SupercomputerRackBlock::new);

    public SupercomputerRackBlock(final Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<SupercomputerRackBlock> codec() {
        return CODEC;
    }

    @Override
    public RackChassis.RackType rackType() {
        return RackChassis.RackType.SUPERCOMPUTER;
    }

    @Override
    protected net.minecraft.world.item.Item blockItem() {
        return dev.jstech.computers.ComputingModule.SUPERCOMPUTER_RACK_ITEM.get();
    }

    @Override
    public Set<DataTier> acceptedCableTiers() {
        /*
         * Only the compute fabric reaches a supercomputer cabinet. A data cable on this port would
         * put the nodes on the data network directly, which is exactly what the HBW Interface exists
         * to prevent.
         */
        return EnumSet.of(DataTier.HPC);
    }
}
