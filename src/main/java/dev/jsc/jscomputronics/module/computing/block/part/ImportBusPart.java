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
import dev.jsc.jscomputronics.module.computing.menu.ImportBusMenu;
import dev.jsc.jscomputronics.module.computing.operation.NetworkInsertOperation;
import dev.jsc.jscomputronics.module.computing.operation.NetworkStorage;
import dev.jsc.jscomputronics.module.computing.storage.ExternalDataPort;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * An Import Bus part: pulls data from the inventory its mounted face touches — items OR fluids, with no distinction — and pushes it into the network as INSERT Operations dispatched by the Mainframe. An empty filter imports everything; a set filter imports only that one type and the min/max window keeps the NETWORK stocked of it (with hysteresis).
 */
public non-sealed class ImportBusPart extends AbstractBusPart {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final int MIN_BATCH = 64;
    private static final int FLUSH_TICKS = 20;

    private StorageKey bufferKey;
    private long bufferAmount;
    private NetworkInsertOperation activeOp;
    private StorageKey flushedKey;
    private long flushedAmount;
    private int ticksSinceFlush;

    @Override
    public CablePartType type() {
        return CablePartType.IMPORT;
    }

    @Override
    public AbstractContainerMenu createMenu(final int containerId, final Inventory inventory,
                                            final DataCableBlockEntity cable, final Direction mountedFace) {
        return ImportBusMenu.create(containerId, inventory, cable, mountedFace);
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
        linked = network != null;
        final MainframeBlockEntity mainframe = network == null ? null : host.mainframe();
        ticksSinceFlush++;
        // Redstone mode holds off pulling new data; an already-buffered payload still flushes below.
        final boolean pulling = !redstoneBlocked();
        // The batch size and pull rate follow the network's orchestration capacity.
        final long cap = mainframe == null ? 1L
                : Math.max(1L, Math.min(Integer.MAX_VALUE, mainframe.capacity()));
        final ExternalDataPort port = neighborPort();
        final StorageKey filter = filterKey(); // null = import anything

        boolean typeChange = false;
        if (pulling && !port.isEmpty() && canAcceptMore(level, network, filter)) {
            if (bufferKey == null) {
                final StorageKey pick = filter != null ? filter
                        : (port.available().isEmpty() ? null : port.available().get(0));
                if (pick != null) {
                    final long pulled = port.extract(pick, cap, false);
                    if (pulled > 0L) {
                        bufferKey = pick;
                        bufferAmount = pulled;
                        host.setChanged();
                    }
                }
            } else if (filter != null && !bufferKey.equals(filter)) {
                // The filter changed while a different type was buffered: flush the old one, add no more.
                typeChange = true;
            } else {
                final long room = cap - bufferAmount;
                if (room > 0L) {
                    final long pulled = port.extract(bufferKey, room, false);
                    if (pulled > 0L) {
                        bufferAmount += pulled;
                        host.setChanged();
                    }
                }
                if (filter == null) {
                    typeChange = port.available().stream().anyMatch(k -> !k.equals(bufferKey));
                }
            }
        }

        final boolean flush = bufferKey != null
                && (bufferAmount >= cap || bufferAmount >= MIN_BATCH || ticksSinceFlush >= FLUSH_TICKS || typeChange);
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

    /**
     * Whether the bus may pull more right now. With no filter or no max it always may; with a filter and a
     * max it stops once the network already holds {@code max} of that type, resuming only after the network
     * stock falls back to the {@code min} low-water mark (hysteresis), exactly mirroring the Export Bus but
     * measured on the network instead of the faced inventory.
     */
    private boolean canAcceptMore(final ServerLevel level, final NetworkUuid network, final StorageKey filter) {
        if (filter == null || max <= 0 || network == null) {
            return true;
        }
        final long have = NetworkStorage.of(level, network).count(filter);
        if (have >= max) {
            active = false;
            return false;
        }
        if (min > 0) {
            if (!active && have > min) {
                return false;
            }
            active = true;
        }
        return true;
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
        super.save(tag, registries);
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
        super.load(tag, registries);
        bufferKey = null;
        bufferAmount = 0L;
        flushedKey = null;
        flushedAmount = 0L;
        final var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        if (tag.contains("BufferKey")) {
            StorageKey.CODEC.parse(ops, tag.get("BufferKey"))
                    .resultOrPartial(err -> LOGGER.warn("Import bus dropped a buffered payload it could not decode: {}", err))
                    .ifPresent(key -> {
                        bufferKey = key;
                        bufferAmount = tag.getLong("BufferAmount");
                    });
        }
        // Recover items that were in flight before the reload; the timed operation is gone but the data
        // must not be lost, so move them into the buffer to be re-inserted next tick. Buffer and flushed
        // should be mutually exclusive at a save point, but if both are present, accumulate (same key) or
        // keep the larger batch rather than overwrite, so no items are silently dropped.
        if (tag.contains("FlushedKey")) {
            final long flushedAmt = tag.getLong("FlushedAmount");
            StorageKey.CODEC.parse(ops, tag.get("FlushedKey"))
                    .resultOrPartial(err -> LOGGER.warn("Import bus dropped an in-flight payload it could not decode: {}", err))
                    .ifPresent(key -> {
                        if (bufferKey == null) {
                            bufferKey = key;
                            bufferAmount = flushedAmt;
                        } else if (bufferKey.equals(key)) {
                            bufferAmount += flushedAmt;
                        } else if (flushedAmt > bufferAmount) {
                            bufferKey = key;
                            bufferAmount = flushedAmt;
                        }
                    });
        }
    }
}
