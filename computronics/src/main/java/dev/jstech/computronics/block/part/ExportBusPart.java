/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.block.part;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.blockentity.DataCableBlockEntity;
import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.menu.ExportBusMenu;
import dev.jstech.computronics.operation.MoveLabels;
import dev.jstech.computronics.operation.NetworkSelectOperation;
import dev.jstech.computronics.operation.NetworkStorage;
import dev.jstech.computronics.storage.ExternalDataPort;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.core.uuid.NetworkUuid;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * An Export Bus part: pulls the filtered item out of the network and into the inventory its mounted face touches, as DELETE Operations dispatched by the Mainframe ("DELETE" = leaves the network for an external inventory, not destruction).
 */
public non-sealed class ExportBusPart extends AbstractBusPart {

    protected static final int EXPORT_INTERVAL = 2;

    private NetworkSelectOperation activeOp;
    private int ticksSinceExport;

    @Override
    public CablePartType type() {
        return CablePartType.EXPORT;
    }

    @Override
    public AbstractContainerMenu createMenu(final int containerId, final Inventory inventory,
                                            final DataCableBlockEntity cable, final Direction mountedFace) {
        return ExportBusMenu.create(containerId, inventory, cable, mountedFace);
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
        if (redstoneBlocked()) {
            return;
        }
        if (++ticksSinceExport < EXPORT_INTERVAL) {
            return;
        }
        ticksSinceExport = 0;

        final StorageKey key = filterKey();
        if (key == null || network == null) {
            return;
        }
        final ExternalDataPort dest = neighborPort();
        if (dest.isEmpty()) {
            return;
        }
        final MainframeBlockEntity mainframe = host.mainframe();
        if (mainframe == null) {
            return;
        }
        // Don't spin failed DELETEs forever once the network holds none of the data.
        if (NetworkStorage.of(level, network).count(key) <= 0L) {
            return;
        }
        // Throughput follows the network's orchestration capacity, not a fixed batch.
        final long batch = Math.max(1L, Math.min(Integer.MAX_VALUE, mainframe.capacity()));
        final long want = computeWant(dest, key, batch);
        if (want <= 0L) {
            return;
        }
        // Pull the data out of the network into the faced block (item or fluid) as a timed DELETE.
        activeOp = mainframe.submitNetworkDelete(key, want, dest, MoveLabels.bus("Export Bus", name()));
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

    @Override
    public ItemStack partItem() {
        return new ItemStack(ComputingModule.EXPORT_BUS_ITEM.get());
    }
}
