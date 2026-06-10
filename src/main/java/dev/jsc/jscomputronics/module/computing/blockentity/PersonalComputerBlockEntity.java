/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.blockentity;

import dev.jsc.jscomputronics.common.hardware.ComputerBuild;
import dev.jsc.jscomputronics.common.hardware.FormFactor;
import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.storage.DataSink;
import dev.jsc.jscomputronics.module.computing.storage.LocalStore;
import dev.jsc.jscomputronics.module.computing.storage.LocalStoreSink;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Personal Computer: the player's hands-on access point to the network, assembled on a consumer ATX board (one CPU, four RAM, four PCIe, one PSU, two disks).
 */
public class PersonalComputerBlockEntity extends AbstractComputerBlockEntity
        implements ComputerTerminalHost {

    // Slot layout — kept public so the assembly Menu and Screen address slots by name.
    public static final int MOTHERBOARD_SLOT = 0;
    public static final int CPU_SLOT = 1;
    public static final int RAM_SLOTS_START = 2;
    public static final int RAM_SLOTS = 4;
    public static final int GPU_SLOTS_START = 6;
    public static final int GPU_SLOTS = 4;
    public static final int PSU_SLOT = 10;
    public static final int DISK_SLOTS_START = 11;
    public static final int DISK_SLOTS = 2;
    public static final int HARDWARE_SLOTS = 13;

    public static final int STORAGE_SLOTS = 18;

    private static final ComputerHardwareLayout LAYOUT = new ComputerHardwareLayout(
            MOTHERBOARD_SLOT, CPU_SLOT, 1, RAM_SLOTS_START, RAM_SLOTS,
            GPU_SLOTS_START, GPU_SLOTS, PSU_SLOT, DISK_SLOTS_START, DISK_SLOTS, HARDWARE_SLOTS);

    public PersonalComputerBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.PERSONAL_COMPUTER_BE.get(), pos, state, LAYOUT);
    }

    @Override
    protected Set<FormFactor> acceptedFormFactors() {
        return Set.of(FormFactor.ATX); // a PC accepts only a consumer ATX board
    }

    // Network node — a passive Category-C node read from the adjacent cable

    public static void serverTick(final Level level, final BlockPos pos,
                                  final BlockState state, final PersonalComputerBlockEntity be) {
        if (level instanceof ServerLevel serverLevel) {
            be.tickNode(serverLevel);
        }
    }

    @Override
    protected void registerNode(final NetworkSystem system, final NetworkUuid network) {
        system.registerPersonalComputer(new NetworkSystem.PersonalComputerNode(
                nodeUuid(), network, capacity(), worldPosition.asLong()));
    }

    @Override
    protected void unregisterNode(final NetworkSystem system, final NetworkUuid network) {
        system.unregisterPersonalComputer(network, nodeUuid());
    }

    private boolean onServerNetwork() {
        return networkUuid != null && level instanceof ServerLevel;
    }

    @Override
    public int networkServerCount() {
        if (!onServerNetwork()) {
            return 0;
        }
        return NetworkSystem.get((ServerLevel) level).serversOf(networkUuid).size();
    }

    // Local storage (PC-specific: lives on the installed disks)

    @Override
    public LocalStore localStore() {
        final List<ItemStack> disks = new ArrayList<>(DISK_SLOTS);
        for (int i = 0; i < DISK_SLOTS; i++) {
            disks.add(getHardware().getStackInSlot(DISK_SLOTS_START + i));
        }
        return new LocalStore(disks, this::setChanged);
    }

    public Map<StorageKey, Long> localSnapshot() {
        return localStore().view();
    }

    @Override
    public int usableStorageSlots() {
        final ComputerBuild build = currentBuild();
        if (build == null) {
            return 0;
        }
        final long capacity = build.totalStorageItems();
        return capacity <= 0 ? 0 : (int) Math.min(STORAGE_SLOTS, (capacity + 63) / 64);
    }

    @Override
    public long localStorageUsed() {
        return localStore().used();
    }

    @Override
    public long localStorageCapacity() {
        final ComputerBuild build = currentBuild();
        return build == null ? 0L : build.totalStorageItems();
    }

    @Override
    public DataSink localStorage() {
        return new LocalStoreSink(localStore());
    }

    // ComputerTerminalHost — read-only monitoring (the rest is inherited from the base)

    @Override
    public boolean computerRunning() {
        return isRunning();
    }

    @Override
    public boolean computerBuildValid() {
        return buildValid();
    }

    @Override
    public int networkLinkState() {
        return networkUuid != null ? 1 : 0; // a PC never conflicts; it only reads a network
    }

    @Override
    public long orchestrationCapacity() {
        return capacity();
    }

    @Override
    public int computerQueues() {
        return isRunning() ? 1 : 0; // a PC runs a single Operation queue
    }

    @Override
    public long computerRamBuffer() {
        return ramBuffer();
    }

    @Override
    public boolean isMainframeHost() {
        return false;
    }

    // Screen sync (ContainerData wire layout — single source of truth shared with the Menu)

    public static final int DATA_RUNNING = 0;
    public static final int DATA_BUILD_VALID = 1;
    public static final int DATA_CAPACITY = 2;
    public static final int DATA_RAM_BUFFER = 3;
    public static final int DATA_AUTOSTART = 4;
    public static final int DATA_ON_NETWORK = 5;
    public static final int DATA_SERVER_COUNT = 6;
    public static final int DATA_COUNT = DATA_SERVER_COUNT + 1;

    private final int[] clientData = new int[DATA_COUNT];

    private int computeData(final int index) {
        return switch (index) {
            case DATA_RUNNING -> isRunning() ? 1 : 0;
            case DATA_BUILD_VALID -> buildValid() ? 1 : 0;
            case DATA_CAPACITY -> (int) Math.min(Integer.MAX_VALUE, capacity());
            case DATA_RAM_BUFFER -> (int) Math.min(Integer.MAX_VALUE, ramBuffer());
            case DATA_AUTOSTART -> isAutoStart() ? 1 : 0;
            case DATA_ON_NETWORK -> networkUuid != null ? 1 : 0;
            case DATA_SERVER_COUNT -> networkServerCount();
            default -> 0;
        };
    }

    private final ContainerData dataAccess = new ContainerData() {
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

    public ContainerData getDataAccess() {
        return dataAccess;
    }
}
