/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.block.part;

import dev.jsc.jscomputronics.common.operation.OperationPriority;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.DataCableBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.operation.NetworkImportOperationTask;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * An Import Bus part: pulls items out of the inventory its mounted face touches and pushes them into the network as INSERT Operations dispatched by the Mainframe.
 */
public final class ImportBusPart implements CablePart {

    private static final int MIN_BATCH = 64;
    private static final int FLUSH_TICKS = 20;

    private DataCableBlockEntity host;
    private Direction face = Direction.NORTH;

    private ItemStack buffer = ItemStack.EMPTY;
    private boolean pendingFlush;
    private int ticksSinceFlush;

    @Override
    public CablePartType type() {
        return CablePartType.IMPORT;
    }

    @Override
    public void attach(final DataCableBlockEntity host, final Direction face) {
        this.host = host;
        this.face = face;
    }

    @Override
    public void serverTick() {
        if (pendingFlush) {
            return;
        }
        final ServerLevel level = host.serverLevel();
        if (level == null) {
            return;
        }
        final NetworkUuid network = host.network();
        final MainframeBlockEntity mainframe = network == null ? null : host.mainframe();
        // The batch size and pull rate follow the network's orchestration capacity.
        final int cap = mainframe == null ? MIN_BATCH
                : (int) Math.max(MIN_BATCH, Math.min(Integer.MAX_VALUE, mainframe.capacity()));
        final IItemHandler front = host.neighborHandler(face);
        ticksSinceFlush++;

        boolean typeChange = false;
        if (front != null) {
            if (buffer.isEmpty()) {
                final Item incoming = firstType(front);
                if (incoming != null) {
                    final int pulled = pullSameType(front, incoming, cap);
                    if (pulled > 0) {
                        buffer = new ItemStack(incoming, pulled);
                        host.setChanged();
                    }
                }
            } else {
                final int room = cap - buffer.getCount();
                if (room > 0) {
                    final int pulled = pullSameType(front, buffer.getItem(), room);
                    if (pulled > 0) {
                        buffer.grow(pulled);
                        host.setChanged();
                    }
                }
                typeChange = hasOtherType(front, buffer.getItem());
            }
        }

        final boolean flush = !buffer.isEmpty()
                && (buffer.getCount() >= cap || ticksSinceFlush >= FLUSH_TICKS || typeChange);
        if (!flush) {
            return;
        }
        if (mainframe == null) {
            ticksSinceFlush = 0; // not networked: hold the buffer, do not spin
            return;
        }
        final ItemStack payload = buffer;
        buffer = ItemStack.EMPTY;
        pendingFlush = true;
        ticksSinceFlush = 0;
        if (!mainframe.submitOperation(
                new NetworkImportOperationTask(level, network, payload, host.getBlockPos(), face),
                OperationPriority.MEDIUM)) {
            buffer = payload; // dispatch failed: keep the items
            pendingFlush = false;
        }
        host.setChanged();
    }

    public void onImportComplete(final ItemStack leftover) {
        pendingFlush = false;
        ticksSinceFlush = 0;
        if (!leftover.isEmpty()) {
            if (buffer.isEmpty()) {
                buffer = leftover;
            } else if (ItemStack.isSameItemSameComponents(buffer, leftover)) {
                buffer.grow(leftover.getCount());
            }
        }
        if (host != null) {
            host.setChanged();
        }
    }

    private static int pullSameType(final IItemHandler handler, final Item incoming, final int max) {
        int pulled = 0;
        for (int i = 0; i < handler.getSlots() && pulled < max; i++) {
            final ItemStack inSlot = handler.getStackInSlot(i);
            if (inSlot.isEmpty() || !inSlot.is(incoming)) {
                continue;
            }
            pulled += handler.extractItem(i, max - pulled, false).getCount();
        }
        return pulled;
    }

    private static Item firstType(final IItemHandler handler) {
        for (int i = 0; i < handler.getSlots(); i++) {
            final ItemStack inSlot = handler.getStackInSlot(i);
            if (!inSlot.isEmpty()) {
                return inSlot.getItem();
            }
        }
        return null;
    }

    private static boolean hasOtherType(final IItemHandler handler, final Item kept) {
        for (int i = 0; i < handler.getSlots(); i++) {
            final ItemStack inSlot = handler.getStackInSlot(i);
            if (!inSlot.isEmpty() && !inSlot.is(kept)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ItemStack partItem() {
        return new ItemStack(ComputingModule.IMPORT_BUS_ITEM.get());
    }

    @Override
    public void dropContents(final ServerLevel level) {
        if (!buffer.isEmpty() && host != null) {
            net.minecraft.world.Containers.dropItemStack(level,
                    host.getBlockPos().getX(), host.getBlockPos().getY(), host.getBlockPos().getZ(), buffer);
            buffer = ItemStack.EMPTY;
        }
    }

    @Override
    public void save(final CompoundTag tag, final HolderLookup.Provider registries) {
        if (!buffer.isEmpty()) {
            tag.put("Buffer", buffer.save(registries));
        }
    }

    @Override
    public void load(final CompoundTag tag, final HolderLookup.Provider registries) {
        buffer = tag.contains("Buffer")
                ? ItemStack.parseOptional(registries, tag.getCompound("Buffer"))
                : ItemStack.EMPTY;
    }
}
