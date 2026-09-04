/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.storage;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every drive's stored items, by volume id, in one save-wide store. A drive carries its id wherever it
 * goes — between machines, racks and dimensions — and finds its contents here; the contents never ride
 * on the item, so a drive full of thousands of types is still a tiny item to sync and compare.
 *
 * <p>Lives on the overworld's data storage so ids resolve from any dimension. A drive that is destroyed
 * leaves its volume behind; that leak is small and accepted.
 */
public final class StorageVolumes extends SavedData {

    public static final String DATA_NAME = "jsc_storage_volumes";
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<UUID, StorageVolume> volumes = new HashMap<>();

    public StorageVolumes() {
    }

    public static SavedData.Factory<StorageVolumes> factory() {
        return new SavedData.Factory<>(StorageVolumes::new, StorageVolumes::load);
    }

    public static StorageVolumes get(final MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), DATA_NAME);
    }

    /** The running server's store, or null off the server thread (a client, a render pass). */
    @Nullable
    public static StorageVolumes current() {
        final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null && server.isSameThread() ? get(server) : null;
    }

    /** The volume with the given id, created blank when it does not exist yet. */
    public StorageVolume volume(final UUID id) {
        return volumes.computeIfAbsent(id, key -> {
            setDirty();
            return new StorageVolume(key, this::setDirty, false);
        });
    }

    @Nullable
    public StorageVolume find(final UUID id) {
        return volumes.get(id);
    }

    /** Drops a volume for good; returns whether there was one. */
    public boolean remove(final UUID id) {
        final boolean existed = volumes.remove(id) != null;
        if (existed) {
            setDirty();
        }
        return existed;
    }

    public int count() {
        return volumes.size();
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries) {
        final RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        final ListTag list = new ListTag();
        for (final Map.Entry<UUID, StorageVolume> entry : volumes.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue; // a blank volume is recreated blank on demand; no need to write it
            }
            final CompoundTag one = new CompoundTag();
            one.putUUID("Id", entry.getKey());
            ServerStorageContents.CODEC.encodeStart(ops, entry.getValue().snapshot())
                    .resultOrPartial(error -> LOGGER.error("Could not save storage volume {}: {}", entry.getKey(), error))
                    .ifPresent(items -> one.put("Items", items));
            list.add(one);
        }
        tag.put("Volumes", list);
        return tag;
    }

    private static StorageVolumes load(final CompoundTag tag, final HolderLookup.Provider registries) {
        final StorageVolumes store = new StorageVolumes();
        final RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        for (final Tag element : tag.getList("Volumes", Tag.TAG_COMPOUND)) {
            final CompoundTag one = (CompoundTag) element;
            if (!one.hasUUID("Id")) {
                continue;
            }
            final UUID id = one.getUUID("Id");
            final StorageVolume volume = new StorageVolume(id, store::setDirty, false);
            final Tag items = one.get("Items");
            if (items != null) {
                ServerStorageContents.CODEC.parse(ops, items)
                        .resultOrPartial(error -> LOGGER.error("Could not load storage volume {}: {}", id, error))
                        .ifPresent(contents -> volume.replaceAll(contents.items()));
            }
            store.volumes.put(id, volume);
        }
        return store;
    }
}
