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
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
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
    private dev.jsc.jscomputronics.module.computing.operation.NetworkInsertOperation activeOp;
    private StorageKey flushedKey;
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
        // Wait for the in-flight flush to finish, then re-buffer whatever the network could not store.
        if (activeOp != null) {
            if (!activeOp.isDone()) {
                return;
            }
            final long leftover = activeOp.leftover();
            if (leftover > 0L && flushedKey != null) {
                final ItemStack back = flushedKey.stack((int) Math.min(Integer.MAX_VALUE, leftover));
                if (buffer.isEmpty()) {
                    buffer = back;
                } else if (ItemStack.isSameItemSameComponents(buffer, back)) {
                    buffer.grow(back.getCount());
                }
                host.setChanged();
            }
            activeOp = null;
            flushedKey = null;
            ticksSinceFlush = 0;
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
                final ItemStack incoming = firstStack(front);
                if (!incoming.isEmpty()) {
                    final int pulled = pullSameStack(front, incoming, cap);
                    if (pulled > 0) {
                        buffer = incoming.copyWithCount(pulled);
                        host.setChanged();
                    }
                }
            } else {
                final int room = cap - buffer.getCount();
                if (room > 0) {
                    final int pulled = pullSameStack(front, buffer, room);
                    if (pulled > 0) {
                        buffer.grow(pulled);
                        host.setChanged();
                    }
                }
                typeChange = hasOtherStack(front, buffer);
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
        ticksSinceFlush = 0;
        // Push the buffered items into the network as a timed INSERT; whatever does not fit comes
        // back as the Operation's leftover and is re-buffered when it finishes (above).
        activeOp = mainframe.submitNetworkInsert(StorageKey.of(payload), payload.getCount(), "import");
        flushedKey = StorageKey.of(payload);
        if (activeOp == null) {
            buffer = payload; // dispatch failed (not running): keep the items
            flushedKey = null;
        }
        host.setChanged();
    }

    private static int pullSameStack(final IItemHandler handler, final ItemStack proto, final int max) {
        int pulled = 0;
        for (int i = 0; i < handler.getSlots() && pulled < max; i++) {
            final ItemStack inSlot = handler.getStackInSlot(i);
            if (inSlot.isEmpty() || !ItemStack.isSameItemSameComponents(inSlot, proto)) {
                continue;
            }
            pulled += handler.extractItem(i, max - pulled, false).getCount();
        }
        return pulled;
    }

    private static ItemStack firstStack(final IItemHandler handler) {
        for (int i = 0; i < handler.getSlots(); i++) {
            final ItemStack inSlot = handler.getStackInSlot(i);
            if (!inSlot.isEmpty()) {
                return inSlot.copyWithCount(1);
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean hasOtherStack(final IItemHandler handler, final ItemStack kept) {
        for (int i = 0; i < handler.getSlots(); i++) {
            final ItemStack inSlot = handler.getStackInSlot(i);
            if (!inSlot.isEmpty() && !ItemStack.isSameItemSameComponents(inSlot, kept)) {
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
