/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.persistence;

import dev.jsc.jscomputronics.common.uuid.NetworkUuid;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * SavedData binding for the per-dimension network registry.
 */
public final class NetworkRegistrySavedData extends JscSavedData{

    public static final String DATA_NAME = "jsc_network_registry";

    private static final String KEY_NETWORKS = "networks";

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
        final Set<NetworkUuid> networks = new LinkedHashSet<>();
        final ListTag list = tag.getList(KEY_NETWORKS, 8); // 8 = string tag id
        for (int i = 0; i < list.size(); i++) {
            final String raw = list.getString(i);
            try {
                networks.add(new NetworkUuid(UUID.fromString(raw)));
            } catch (final IllegalArgumentException ignored) {
                // Corrupt UUID string — skip it rather than crash the load.
            }
        }
        return new NetworkRegistrySavedData(NetworkRegistryState.of(networks));
    }

    public static SavedData.Factory<NetworkRegistrySavedData> factory() {
        return new SavedData.Factory<>(
                NetworkRegistrySavedData::create,
                NetworkRegistrySavedData::load);
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries) {
        final ListTag list = new ListTag();
        for (final NetworkUuid uuid : state.networks()) {
            list.add(StringTag.valueOf(uuid.value().toString()));
        }
        tag.put(KEY_NETWORKS, list);
        return tag;
    }

    public NetworkRegistryState state() {
        return state;
    }

    public void addNetwork(final NetworkUuid uuid) {
        final NetworkRegistryState next = state.withNetwork(uuid);
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
