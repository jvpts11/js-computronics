/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.block.part;

import dev.jstech.computers.blockentity.DataCableBlockEntity;
import dev.jstech.computers.storage.ExternalDataPort;
import dev.jstech.computers.storage.StorageKey;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Shared base for the two bus parts (Import, Export): both carry the same configuration surface (an optional
 * name (so a query can address the bus by it), a single ghost filter slot, a min/max stock window with
 * hysteresis, and a continuous/redstone mode) over a data cable face. Only the per-tick transfer direction
 * differs, which each subclass supplies in {@link #serverTick()}.
 */
public abstract sealed class AbstractBusPart implements ICablePart permits ImportBusPart, ExportBusPart {

    public static final int MODE_CONTINUOUS = 0;
    public static final int MODE_REDSTONE = 1;

    /** The longest name a bus may carry; keeps the name field and any query reference bounded. */
    public static final int MAX_NAME_LENGTH = 32;

    protected DataCableBlockEntity host;
    protected Direction face = Direction.NORTH;

    protected final ItemStackHandler filter = new ItemStackHandler(1) {
        @Override
        public int getSlotLimit(final int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(final int slot) {
            markHostChanged();
        }
    };

    protected int min;
    protected int max;
    protected int mode = MODE_CONTINUOUS;
    protected boolean linked;
    protected boolean active = true;
    protected String name = "";

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
    public void attach(final DataCableBlockEntity host, final Direction face) {
        this.host = host;
        this.face = face;
    }

    @Override
    public boolean hasMenu() {
        return true;
    }

    /** Builds this bus's configuration menu, dispatched by {@link CablePartType} so the cable stays generic. */
    public abstract AbstractContainerMenu createMenu(int containerId, Inventory inventory,
                                                     DataCableBlockEntity cable, Direction mountedFace);

    public ItemStackHandler getFilterHandler() {
        return filter;
    }

    public ContainerData getDataAccess() {
        return data;
    }

    public String name() {
        return name;
    }

    /** Sets the bus name, trimmed and length-clamped; an empty name means the bus is unaddressable by query. */
    public void setName(final String newName) {
        final String trimmed = newName == null ? "" : newName.strip();
        this.name = trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
        markHostChanged();
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

    /**
     * The storage key the filter selects, or {@code null} for an empty filter. A fluid container in the slot
     * (e.g. a filled bucket) selects its FLUID, and an item carrying a chemical (a filled tank item, a
     * hohlraum) selects that CHEMICAL, so a bus can target any kind of data the same way it targets an item.
     */
    @Nullable
    public StorageKey filterKey() {
        final ItemStack stack = filter.getStackInSlot(0);
        if (stack.isEmpty()) {
            return null;
        }
        return FluidUtil.getFluidContained(stack)
                .filter(f -> !f.isEmpty())
                .map(StorageKey::of)
                .or(() -> dev.jstech.computers.storage.ChemicalBridges.chemicalOf(stack)
                        .map(StorageKey::chemical))
                .orElseGet(() -> StorageKey.of(stack));
    }

    /** Whether a redstone-mode bus is currently held off because its block has no neighbor signal. */
    protected boolean redstoneBlocked() {
        return mode == MODE_REDSTONE && host != null && host.serverLevel() != null
                && !host.serverLevel().hasNeighborSignal(host.getBlockPos());
    }

    protected ExternalDataPort neighborPort() {
        return host.neighborPort(face);
    }

    protected void markHostChanged() {
        if (host != null) {
            host.setChanged();
        }
    }

    @Override
    public void save(final CompoundTag tag, final HolderLookup.Provider registries) {
        tag.put("Filter", filter.serializeNBT(registries));
        tag.putInt("Min", min);
        tag.putInt("Max", max);
        tag.putInt("Mode", mode);
        // Persist the hysteresis gate so a reload does not forget a min/max hold and over-push/pull one batch.
        tag.putBoolean("Active", active);
        if (!name.isEmpty()) {
            tag.putString("Name", name);
        }
    }

    @Override
    public void load(final CompoundTag tag, final HolderLookup.Provider registries) {
        filter.deserializeNBT(registries, tag.getCompound("Filter"));
        min = tag.getInt("Min");
        max = tag.getInt("Max");
        mode = tag.getInt("Mode");
        // Default to active when the key is absent (old saves), matching the field initializer.
        active = !tag.contains("Active") || tag.getBoolean("Active");
        name = tag.getString("Name");
    }
}
