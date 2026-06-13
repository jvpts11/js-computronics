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
import dev.jsc.jscomputronics.module.computing.block.PersonalComputerBlock;
import dev.jsc.jscomputronics.module.computing.item.DiskItem;
import dev.jsc.jscomputronics.module.computing.storage.DataSink;
import dev.jsc.jscomputronics.module.computing.storage.LocalStore;
import dev.jsc.jscomputronics.module.computing.storage.StoreSink;
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

    // Bumped on any change to the disk contents (insert/extract/disk swap) AND on a privacy-slider
    // write, so the Mainframe's incremental ANALYZE re-reads this PC's public view exactly when it
    // could have changed — moving a slider changes the public view with no item movement at all.
    private long storageModCount;

    public PersonalComputerBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.PERSONAL_COMPUTER_BE.get(), pos, state, LAYOUT);
    }

    public long storageModCount() {
        return storageModCount;
    }

    /**
     * Marks the PC's storage as changed so the network index re-reads it; used by the slider write, which
     * changes the public view without moving any item. Defers to {@link #setChanged()}, which advances the
     * counter once — so a slider write and a disk swap both bump it by exactly one.
     */
    public void bumpStorageModCount() {
        setChanged();
    }

    @Override
    public void setChanged() {
        // Any reason the BE is marked dirty (a disk swap, a content write) could have changed the
        // public view, so advance the counter the index keys its re-reads on.
        storageModCount++;
        super.setChanged();
    }

    /**
     * The era this PC belongs to, read from its block. Defaults to Standard for any block that is not a
     * {@link PersonalComputerBlock} (which never happens in practice, but keeps the read total).
     */
    private dev.jsc.jscomputronics.common.tier.HardwareEra blockEra() {
        return getBlockState().getBlock() instanceof PersonalComputerBlock pc
                ? pc.era()
                : dev.jsc.jscomputronics.common.tier.HardwareEra.STANDARD;
    }

    @Override
    protected Set<FormFactor> acceptedFormFactors() {
        // Each era takes its own consumer form factor: Vintage on Baby-AT/AT, Legacy and Standard on ATX.
        return switch (blockEra()) {
            case VINTAGE -> Set.of(FormFactor.BABY_AT, FormFactor.AT);
            default -> Set.of(FormFactor.ATX);
        };
    }

    @Override
    protected dev.jsc.jscomputronics.common.tier.HardwareEra requiredBoardEra() {
        // A PC accepts only a board of its own era, so a Legacy and a Standard ATX board are not
        // interchangeable: each installs in its matching machine alone.
        return blockEra();
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
        return new StoreSink(localStore());
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

    // Public/private storage slider — a PC publishes part of each disk to the network per disk.

    @Override
    public boolean storageHasSlider() {
        return true;
    }

    @Override
    public int diskPrivacyDiskCount() {
        return DISK_SLOTS;
    }

    @Override
    public int diskPrivacyPermille(final int diskIndex) {
        if (diskIndex < 0 || diskIndex >= DISK_SLOTS) {
            return 0;
        }
        return DiskItem.publicPermille(getHardware().getStackInSlot(DISK_SLOTS_START + diskIndex));
    }

    @Override
    public long diskUsedWeight(final int diskIndex) {
        return localStore().diskUsedWeight(diskIndex);
    }

    @Override
    public long diskCapacityWeight(final int diskIndex) {
        return localStore().diskCapacityWeight(diskIndex);
    }

    /**
     * Writes a clamped public-share permille onto the disk in {@code diskIndex} and marks storage changed so the network re-reads this PC's public view. A no-op for an out-of-range index or an empty slot.
     */
    public void setDiskPrivacy(final int diskIndex, final int permille) {
        if (diskIndex < 0 || diskIndex >= DISK_SLOTS) {
            return;
        }
        final ItemStack disk = getHardware().getStackInSlot(DISK_SLOTS_START + diskIndex);
        if (!(disk.getItem() instanceof DiskItem)) {
            return;
        }
        DiskItem.setPublicPermille(disk, permille);
        bumpStorageModCount();
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
