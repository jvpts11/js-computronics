/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.blockentity;

import dev.jsc.jscomputronics.common.hardware.FormFactor;
import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.item.PhiCoprocessorItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * One node of a Supercomputer cluster — a full computer the player assembles like any other: server board, CPU, RAM, PSU, disks, plus a single co-processor bay for the Phi that does the crafting work.
 */
public class SupercomputerNodeBlockEntity extends AbstractComputerBlockEntity {

    public static final int MOTHERBOARD_SLOT = 0;
    public static final int CPU_SLOT = 1;
    public static final int RAM_SLOTS_START = 2;
    public static final int RAM_SLOTS = 2;
    public static final int PHI_SLOT = 4;
    public static final int PSU_SLOT = 5;
    public static final int DISK_SLOTS_START = 6;
    public static final int DISK_SLOTS = 2;
    public static final int HARDWARE_SLOTS = 8;

    private static final ComputerHardwareLayout LAYOUT = new ComputerHardwareLayout(
            MOTHERBOARD_SLOT, CPU_SLOT, 1, RAM_SLOTS_START, RAM_SLOTS,
            PHI_SLOT, 1, PSU_SLOT, DISK_SLOTS_START, DISK_SLOTS, HARDWARE_SLOTS);

    public SupercomputerNodeBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.SUPERCOMPUTER_NODE_BE.get(), pos, state, LAYOUT);
    }

    @Override
    protected Set<FormFactor> acceptedFormFactors() {
        return Set.of(FormFactor.EEB);
    }

    @Override
    protected boolean isValidPcieCard(final ItemStack stack) {
        // The single expansion bay is the co-processor seat — nothing else fits.
        return stack.getItem() instanceof PhiCoprocessorItem;
    }

    public static void serverTick(final Level level, final BlockPos pos,
                                  final BlockState state, final SupercomputerNodeBlockEntity be) {
        if (level instanceof ServerLevel serverLevel) {
            be.tickNode(serverLevel);
            be.syncRunningVisual(serverLevel);
        }
    }

    private void syncRunningVisual(final ServerLevel serverLevel) {
        if (getBlockState().getBlock()
                instanceof dev.jsc.jscomputronics.module.computing.block.SupercomputerNodeBlock) {
            final boolean running = isRunning();
            if (getBlockState().getValue(
                    dev.jsc.jscomputronics.module.computing.block.SupercomputerNodeBlock.FILLED) != running) {
                serverLevel.setBlock(worldPosition, getBlockState().setValue(
                        dev.jsc.jscomputronics.module.computing.block.SupercomputerNodeBlock.FILLED, running),
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    protected void registerNode(final NetworkSystem system, final NetworkUuid network) {
        // Cluster nodes never join the data network individually: the HBW Interface
    }

    @Override
    protected void unregisterNode(final NetworkSystem system, final NetworkUuid network) {
        // See registerNode: the interface owns the cluster's registration.
    }

    @Nullable
    public PhiCoprocessorItem installedPhi() {
        return getHardware().getStackInSlot(PHI_SLOT).getItem() instanceof PhiCoprocessorItem phi
                ? phi : null;
    }

    // Screen sync

    public static final int DATA_RUNNING = 0;
    public static final int DATA_BUILD_VALID = 1;
    public static final int DATA_AUTOSTART = 2;
    public static final int DATA_COUNT = DATA_AUTOSTART + 1;

    private final int[] clientData = new int[DATA_COUNT];

    private int computeData(final int index) {
        return switch (index) {
            case DATA_RUNNING -> isRunning() ? 1 : 0;
            case DATA_BUILD_VALID -> buildValid() ? 1 : 0;
            case DATA_AUTOSTART -> isAutoStart() ? 1 : 0;
            default -> 0;
        };
    }

    private final net.minecraft.world.inventory.ContainerData dataAccess =
            new net.minecraft.world.inventory.ContainerData() {
        @Override
        public int get(final int index) {
            if (level != null && level.isClientSide) {
                return index >= 0 && index < clientData.length ? clientData[index] : 0;
            }
            return computeData(index);
        }

        @Override
        public void set(final int index, final int value) {
            if (index >= 0 && index < clientData.length) {
                clientData[index] = value;
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public net.minecraft.world.inventory.ContainerData getDataAccess() {
        return dataAccess;
    }
}
