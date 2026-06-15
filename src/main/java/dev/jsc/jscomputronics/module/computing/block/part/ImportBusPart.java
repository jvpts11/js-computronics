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
import dev.jsc.jscomputronics.module.computing.storage.ExternalDataPort;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * An Import Bus part: pulls whatever data its mounted face touches — items OR fluids, with no distinction — and pushes it into the network as INSERT Operations dispatched by the Mainframe.
 */
public final class ImportBusPart implements CablePart {

    private static final int MIN_BATCH = 64;
    private static final int FLUSH_TICKS = 20;

    private DataCableBlockEntity host;
    private Direction face = Direction.NORTH;

    private StorageKey bufferKey;
    private long bufferAmount;
    private dev.jsc.jscomputronics.module.computing.operation.NetworkInsertOperation activeOp;
    private StorageKey flushedKey;
    private long flushedAmount;
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
            if (leftover > 0L && flushedKey != null && bufferKey == null) {
                bufferKey = flushedKey;
                bufferAmount = leftover;
                host.setChanged();
            }
            activeOp = null;
            flushedKey = null;
            flushedAmount = 0L;
            ticksSinceFlush = 0;
        }
        final ServerLevel level = host.serverLevel();
        if (level == null) {
            return;
        }
        final NetworkUuid network = host.network();
        final MainframeBlockEntity mainframe = network == null ? null : host.mainframe();
        // The batch size and pull rate follow the network's orchestration capacity.
        final long cap = mainframe == null ? 1L
                : Math.max(1L, Math.min(Integer.MAX_VALUE, mainframe.capacity()));
        final ExternalDataPort port = host.neighborPort(face);
        ticksSinceFlush++;

        boolean typeChange = false;
        if (!port.isEmpty()) {
            if (bufferKey == null) {
                final List<StorageKey> available = port.available();
                if (!available.isEmpty()) {
                    final StorageKey pick = available.get(0);
                    final long pulled = port.extract(pick, cap, false);
                    if (pulled > 0L) {
                        bufferKey = pick;
                        bufferAmount = pulled;
                        host.setChanged();
                    }
                }
            } else {
                final long room = cap - bufferAmount;
                if (room > 0L) {
                    final long pulled = port.extract(bufferKey, room, false);
                    if (pulled > 0L) {
                        bufferAmount += pulled;
                        host.setChanged();
                    }
                }
                typeChange = port.available().stream().anyMatch(k -> !k.equals(bufferKey));
            }
        }

        final boolean flush = bufferKey != null
                && (bufferAmount >= cap || ticksSinceFlush >= FLUSH_TICKS || typeChange);
        if (!flush) {
            return;
        }
        if (mainframe == null) {
            ticksSinceFlush = 0; // not networked: hold the buffer, do not spin
            return;
        }
        final StorageKey payloadKey = bufferKey;
        final long payloadAmount = bufferAmount;
        bufferKey = null;
        bufferAmount = 0L;
        ticksSinceFlush = 0;
        // Push the buffered data into the network as a timed INSERT; whatever does not fit comes back
        // as the Operation's leftover and is re-buffered when it finishes (above).
        activeOp = mainframe.submitNetworkInsert(payloadKey, payloadAmount, "import");
        flushedKey = payloadKey;
        flushedAmount = payloadAmount;
        if (activeOp == null) {
            bufferKey = payloadKey; // dispatch failed (not running): keep the data
            bufferAmount = payloadAmount;
            flushedKey = null;
            flushedAmount = 0L;
        }
        host.setChanged();
    }

    @Override
    public ItemStack partItem() {
        return new ItemStack(ComputingModule.IMPORT_BUS_ITEM.get());
    }

    @Override
    public void dropContents(final ServerLevel level) {
        // Drop a buffered item back into the world; a buffered fluid (rare, transient) is discarded.
        // If an INSERT operation is in flight, the payload was already extracted from the source but
        // not yet confirmed by the network, so drop the in-flight amount too; nothing is silently lost.
        final StorageKey drop = bufferKey != null ? bufferKey : flushedKey;
        final long dropAmount = bufferKey != null ? bufferAmount : flushedAmount;
        if (drop != null && !drop.isFluid() && dropAmount > 0L && host != null) {
            net.minecraft.world.Containers.dropItemStack(level,
                    host.getBlockPos().getX(), host.getBlockPos().getY(), host.getBlockPos().getZ(),
                    drop.stack((int) Math.min(dropAmount, Integer.MAX_VALUE)));
        }
        bufferKey = null;
        bufferAmount = 0L;
        flushedKey = null;
        flushedAmount = 0L;
    }

    @Override
    public void save(final CompoundTag tag, final HolderLookup.Provider registries) {
        if (bufferKey != null && bufferAmount > 0L) {
            StorageKey.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), bufferKey)
                    .result().ifPresent(encoded -> tag.put("BufferKey", encoded));
            tag.putLong("BufferAmount", bufferAmount);
        }
        // Persist the in-flight payload so a save/reload cannot destroy items that were extracted
        // from the source but whose INSERT operation has not yet been confirmed by the network.
        if (flushedKey != null && flushedAmount > 0L) {
            StorageKey.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), flushedKey)
                    .result().ifPresent(encoded -> tag.put("FlushedKey", encoded));
            tag.putLong("FlushedAmount", flushedAmount);
        }
    }

    @Override
    public void load(final CompoundTag tag, final HolderLookup.Provider registries) {
        bufferKey = null;
        bufferAmount = 0L;
        flushedKey = null;
        flushedAmount = 0L;
        if (tag.contains("BufferKey")) {
            StorageKey.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("BufferKey"))
                    .result().ifPresent(key -> {
                        bufferKey = key;
                        bufferAmount = tag.getLong("BufferAmount");
                    });
        }
        // Recover items that were in flight before the reload; the timed operation is gone but the
        // data must not be lost, so move them back into the buffer to be re-inserted next tick.
        if (tag.contains("FlushedKey")) {
            StorageKey.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("FlushedKey"))
                    .result().ifPresent(key -> {
                        bufferKey = key;
                        bufferAmount = tag.getLong("FlushedAmount");
                    });
        }
    }
}
