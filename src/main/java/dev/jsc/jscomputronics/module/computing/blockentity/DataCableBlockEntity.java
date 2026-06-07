/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.blockentity;

import dev.jsc.jscomputronics.common.network.ConnectivityIndex;
import dev.jsc.jscomputronics.common.network.DataTier;
import dev.jsc.jscomputronics.common.network.NetworkBridge;
import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.block.DataCableBlock;
import dev.jsc.jscomputronics.module.computing.block.part.CablePart;
import dev.jsc.jscomputronics.module.computing.block.part.CablePartType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * BlockEntity backing a {@link DataCableBlock}.
 */
public class DataCableBlockEntity extends BlockEntity {

    private final CablePart[] parts = new CablePart[6];
    private final byte[] partTypes = {-1, -1, -1, -1, -1, -1};

    public DataCableBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.DATA_CABLE_BE.get(), pos, state);
    }

    public static void serverTick(final net.minecraft.world.level.Level level, final BlockPos pos,
                                  final BlockState state, final DataCableBlockEntity cable) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        for (final CablePart part : cable.parts) {
            if (part != null) {
                part.serverTick();
            }
        }
    }

    public DataTier tier() {
        return getBlockState().getBlock() instanceof DataCableBlock cable
                ? cable.tier()
                : DataTier.T1_ETHERNET;
    }

    // Helpers the parts use to reach the world, the network and neighbors

    @Nullable
    public ServerLevel serverLevel() {
        return level instanceof ServerLevel serverLevel ? serverLevel : null;
    }

    @Nullable
    public NetworkUuid network() {
        final ServerLevel serverLevel = serverLevel();
        if (serverLevel == null) {
            return null;
        }
        return NetworkSystem.get(serverLevel).connectivity().networkOf(worldPosition.asLong()).orElse(null);
    }

    @Nullable
    public MainframeBlockEntity mainframe() {
        final ServerLevel serverLevel = serverLevel();
        final NetworkUuid network = network();
        if (serverLevel == null || network == null) {
            return null;
        }
        return NetworkSystem.get(serverLevel).mainframePositionOf(network)
                .map(pos -> serverLevel.getBlockEntity(BlockPos.of(pos)))
                .filter(MainframeBlockEntity.class::isInstance)
                .map(MainframeBlockEntity.class::cast)
                .orElse(null);
    }

    @Nullable
    public IItemHandler neighborHandler(final Direction face) {
        final ServerLevel serverLevel = serverLevel();
        if (serverLevel == null) {
            return null;
        }
        return serverLevel.getCapability(Capabilities.ItemHandler.BLOCK,
                worldPosition.relative(face), face.getOpposite());
    }

    // Part hosting

    public boolean hasPart(final Direction face) {
        return partTypes[face.get3DDataValue()] >= 0;
    }

    @Nullable
    public CablePartType partType(final Direction face) {
        return CablePartType.byId(partTypes[face.get3DDataValue()]);
    }

    @Nullable
    public CablePart getPart(final Direction face) {
        return parts[face.get3DDataValue()];
    }

    public void addPart(final Direction face, final CablePart part) {
        final int idx = face.get3DDataValue();
        part.attach(this, face);
        parts[idx] = part;
        partTypes[idx] = part.type().id();
        setChanged();
        syncToClients();
    }

    @Nullable
    public CablePart removePart(final Direction face) {
        final int idx = face.get3DDataValue();
        final CablePart removed = parts[idx];
        parts[idx] = null;
        partTypes[idx] = -1;
        if (removed != null) {
            setChanged();
            syncToClients();
        }
        return removed;
    }

    public boolean hasAnyPart() {
        for (final byte type : partTypes) {
            if (type >= 0) {
                return true;
            }
        }
        return false;
    }

    public void dropAllParts(final ServerLevel serverLevel) {
        for (int i = 0; i < parts.length; i++) {
            final CablePart part = parts[i];
            if (part == null) {
                continue;
            }
            part.dropContents(serverLevel);
            Containers.dropItemStack(serverLevel, worldPosition.getX(), worldPosition.getY(),
                    worldPosition.getZ(), part.partItem());
            parts[i] = null;
            partTypes[i] = -1;
        }
    }

    public void dropAllBuffers(final ServerLevel serverLevel) {
        for (final CablePart part : parts) {
            if (part != null) {
                part.dropContents(serverLevel);
            }
        }
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide()) {
            final BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // Connectivity (transient runtime index)

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            final ConnectivityIndex index = NetworkSystem.get(serverLevel).connectivity();
            final long encodedPos = worldPosition.asLong();
            if (!index.contains(encodedPos)) {
                index.onCablePlaced(encodedPos, networkNeighbors(serverLevel));
            }
        }
    }

    private Set<Long> networkNeighbors(final ServerLevel serverLevel) {
        final Set<Long> neighbors = new HashSet<>();
        final DataTier myTier = tier();
        for (final Direction direction : Direction.values()) {
            final BlockPos neighborPos = worldPosition.relative(direction);
            final var block = serverLevel.getBlockState(neighborPos).getBlock();
            if (block instanceof DataCableBlock other && other.tier() == myTier) {
                neighbors.add(neighborPos.asLong());
            } else if (block instanceof NetworkBridge) {
                neighbors.add(neighborPos.asLong());
            }
        }
        return neighbors;
    }

    // Persistence (full parts on disk; type array over the wire)

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        final ListTag list = new ListTag();
        for (int i = 0; i < parts.length; i++) {
            final CablePart part = parts[i];
            if (part == null) {
                continue;
            }
            final CompoundTag entry = new CompoundTag();
            entry.putByte("Face", (byte) i);
            entry.putByte("Type", part.type().id());
            final CompoundTag data = new CompoundTag();
            part.save(data, registries);
            entry.put("Data", data);
            list.add(entry);
        }
        if (!list.isEmpty()) {
            tag.put("Parts", list);
        }
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Arrays.fill(parts, null);
        Arrays.fill(partTypes, (byte) -1);
        final ListTag list = tag.getList("Parts", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag entry = list.getCompound(i);
            final int idx = entry.getByte("Face") & 0xFF;
            final CablePartType type = CablePartType.byId(entry.getByte("Type"));
            if (idx < 0 || idx >= parts.length || type == null) {
                continue;
            }
            final Direction face = Direction.from3DDataValue(idx);
            final CablePart part = type.create();
            part.attach(this, face);
            part.load(entry.getCompound("Data"), registries);
            parts[idx] = part;
            partTypes[idx] = type.id();
        }
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        tag.putByteArray("PartTypes", partTypes.clone());
        return tag;
    }

    @Override
    public void handleUpdateTag(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        readPartTypes(tag);
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(final Connection net, final ClientboundBlockEntityDataPacket pkt,
                             final HolderLookup.Provider registries) {
        readPartTypes(pkt.getTag());
        if (level != null) {
            // Refresh the render mesh and the collision/selection shape with the new parts.
            final BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private void readPartTypes(@Nullable final CompoundTag tag) {
        if (tag != null && tag.contains("PartTypes")) {
            final byte[] incoming = tag.getByteArray("PartTypes");
            for (int i = 0; i < partTypes.length; i++) {
                partTypes[i] = i < incoming.length ? incoming[i] : (byte) -1;
            }
        }
    }
}
