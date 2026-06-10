/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.block.part;

import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.DataCableBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.operation.NetworkStorage;
import dev.jsc.jscomputronics.module.computing.storage.ExternalDataPort;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * An Export Bus part: pulls the filtered item out of the network and into the inventory its mounted face touches, as DELETE Operations dispatched by the Mainframe ("DELETE" = leaves the network for an external inventory, not destruction).
 */
public final class ExportBusPart implements CablePart {

    public static final int MODE_CONTINUOUS = 0;
    public static final int MODE_REDSTONE = 1;

    private static final int BATCH = 64;
    private static final int EXPORT_INTERVAL = 2;

    private DataCableBlockEntity host;
    private Direction face = Direction.NORTH;

    private final ItemStackHandler filter = new ItemStackHandler(1) {
        @Override
        public int getSlotLimit(final int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(final int slot) {
            markHostChanged();
        }
    };

    private int min;
    private int max;
    private int mode = MODE_CONTINUOUS;
    private boolean linked;
    private boolean active = true;
    private dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperation activeOp;
    private int ticksSinceExport;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(final int index) {
            return switch (index) {
                case 0 -> min;
                case 1 -> max;
                case 2 -> mode;
                case 3 -> linked ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(final int index, final int value) {
            switch (index) {
                case 0 -> min = value;
                case 1 -> max = value;
                case 2 -> mode = value;
                case 3 -> linked = value != 0;
                default -> { /* no-op */ }
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    @Override
    public CablePartType type() {
        return CablePartType.EXPORT;
    }

    @Override
    public void attach(final DataCableBlockEntity host, final Direction face) {
        this.host = host;
        this.face = face;
    }

    @Override
    public boolean hasMenu() {
        return true;
    }

    public ItemStackHandler getFilterHandler() {
        return filter;
    }

    public ContainerData getDataAccess() {
        return data;
    }

    public void setFilter(final ItemStack stack) {
        filter.setStackInSlot(0, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        active = true;
        markHostChanged();
    }

    public void adjustMin(final int delta) {
        min = Math.max(0, min + delta);
        active = true;
        markHostChanged();
    }

    public void adjustMax(final int delta) {
        max = Math.max(0, max + delta);
        active = true;
        markHostChanged();
    }

    public void toggleMode() {
        mode = mode == MODE_CONTINUOUS ? MODE_REDSTONE : MODE_CONTINUOUS;
        markHostChanged();
    }

    public Item filterItem() {
        return filter.getStackInSlot(0).isEmpty() ? Items.AIR : filter.getStackInSlot(0).getItem();
    }

    @Override
    public void serverTick() {
        final ServerLevel level = host.serverLevel();
        if (level == null) {
            return;
        }
        final NetworkUuid network = host.network();
        linked = network != null;
        // Wait for the in-flight DELETE to finish before starting another.
        if (activeOp != null) {
            if (!activeOp.isDone()) {
                return;
            }
            activeOp = null;
        }
        if (mode == MODE_REDSTONE && !level.hasNeighborSignal(host.getBlockPos())) {
            return;
        }
        if (++ticksSinceExport < EXPORT_INTERVAL) {
            return;
        }
        ticksSinceExport = 0;

        final ItemStack filterStack = filter.getStackInSlot(0);
        if (filterStack.isEmpty() || network == null) {
            return;
        }
        final ExternalDataPort dest = host.neighborPort(face);
        if (dest.isEmpty()) {
            return;
        }
        // A fluid container in the filter (e.g. a filled bucket) exports its FLUID; any other item
        final StorageKey key = net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(filterStack)
                .filter(f -> !f.isEmpty())
                .map(StorageKey::of)
                .orElseGet(() -> StorageKey.of(filterStack));
        final MainframeBlockEntity mainframe = host.mainframe();
        if (mainframe == null) {
            return;
        }
        // Don't spin failed DELETEs forever once the network holds none of the data.
        if (NetworkStorage.of(level, network).count(key) <= 0L) {
            return;
        }
        // Throughput follows the network's orchestration capacity, not a fixed batch.
        final long batch = Math.max(BATCH, Math.min(Integer.MAX_VALUE, mainframe.capacity()));
        final long want = computeWant(dest, key, batch);
        if (want <= 0L) {
            return;
        }
        // Pull the data out of the network into the faced block (item or fluid) as a timed DELETE
        activeOp = mainframe.submitNetworkDelete(key, want, dest, "export");
    }

    private long computeWant(final ExternalDataPort dest, final StorageKey key, final long batch) {
        if (max <= 0) {
            return batch; // no cap: push up to the network throughput each cycle
        }
        final long destCount = dest.count(key);
        if (destCount >= max) {
            active = false;
            return 0L;
        }
        if (min > 0) {
            if (!active && destCount > min) {
                return 0L; // hysteresis: wait until the stock drops to the low-water mark
            }
            active = true;
        }
        return Math.min(batch, (long) max - destCount);
    }

    private void markHostChanged() {
        if (host != null) {
            host.setChanged();
        }
    }

    @Override
    public ItemStack partItem() {
        return new ItemStack(ComputingModule.EXPORT_BUS_ITEM.get());
    }

    @Override
    public void save(final CompoundTag tag, final HolderLookup.Provider registries) {
        tag.put("Filter", filter.serializeNBT(registries));
        tag.putInt("Min", min);
        tag.putInt("Max", max);
        tag.putInt("Mode", mode);
    }

    @Override
    public void load(final CompoundTag tag, final HolderLookup.Provider registries) {
        filter.deserializeNBT(registries, tag.getCompound("Filter"));
        min = tag.getInt("Min");
        max = tag.getInt("Max");
        mode = tag.getInt("Mode");
    }
}
