/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.persistence;

import dev.jstech.core.uuid.NetworkUuid;
import dev.jstech.core.uuid.NetworkUuidState;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SavedData binding for the per-dimension network registry.
 */
public final class NetworkRegistrySavedData extends CoreSavedData{

    public static final String DATA_NAME = "jstech_network_registry";

    private static final String KEY_NETWORKS = "networks";
    private static final String KEY_UUID = "uuid";
    private static final String KEY_STATE = "state";

    private NetworkRegistryState state;

    private NetworkRegistrySavedData(final NetworkRegistryState state) {
        this.state = state;
    }

    public static NetworkRegistrySavedData create() {
        return new NetworkRegistrySavedData(NetworkRegistryState.empty());
    }

    public static NetworkRegistrySavedData load(
            final CompoundTag tag,
            final HolderLookup.Provider registries) {
        final Map<NetworkUuid, NetworkUuidState> networks = new LinkedHashMap<>();
        final ListTag list = tag.getList(KEY_NETWORKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag entry = list.getCompound(i);
            try {
                final NetworkUuid uuid = new NetworkUuid(UUID.fromString(entry.getString(KEY_UUID)));
                networks.put(uuid, parseState(entry.getString(KEY_STATE)));
            } catch (final IllegalArgumentException ignored) {
                // Corrupt UUID string — skip it rather than crash the load.
            }
        }
        // Backward compatibility: an older save stored a plain string list (every network ACTIVE).
        if (networks.isEmpty()) {
            final ListTag legacy = tag.getList(KEY_NETWORKS, Tag.TAG_STRING);
            for (int i = 0; i < legacy.size(); i++) {
                try {
                    networks.put(new NetworkUuid(UUID.fromString(legacy.getString(i))),
                            NetworkUuidState.ACTIVE);
                } catch (final IllegalArgumentException ignored) {
                    // Skip a corrupt entry.
                }
            }
        }
        return new NetworkRegistrySavedData(NetworkRegistryState.ofStates(networks));
    }

    private static NetworkUuidState parseState(final String raw) {
        try {
            return NetworkUuidState.valueOf(raw);
        } catch (final IllegalArgumentException ignored) {
            return NetworkUuidState.ACTIVE;
        }
    }

    public static SavedData.Factory<NetworkRegistrySavedData> factory() {
        return new SavedData.Factory<>(
                NetworkRegistrySavedData::create,
                NetworkRegistrySavedData::load);
    }

    public static NetworkRegistrySavedData get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries) {
        final ListTag list = new ListTag();
        state.states().forEach((uuid, networkState) -> {
            final CompoundTag entry = new CompoundTag();
            entry.putString(KEY_UUID, uuid.value().toString());
            entry.putString(KEY_STATE, networkState.name());
            list.add(entry);
        });
        tag.put(KEY_NETWORKS, list);
        return tag;
    }

    public NetworkRegistryState state() {
        return state;
    }

    public NetworkUuidState networkState(final NetworkUuid uuid) {
        return state.stateOf(uuid);
    }

    public void addNetwork(final NetworkUuid uuid) {
        final NetworkRegistryState next = state.withNetwork(uuid);
        if (next != state) {
            state = next;
            setDirty();
        }
    }

    public void setNetworkState(final NetworkUuid uuid, final NetworkUuidState networkState) {
        final NetworkRegistryState next = state.withState(uuid, networkState);
        if (next != state) {
            state = next;
            setDirty();
        }
    }

    public void removeNetwork(final NetworkUuid uuid) {
        final NetworkRegistryState next = state.withoutNetwork(uuid);
        if (next != state) {
            state = next;
            setDirty();
        }
    }
}
