/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.storage;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
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
 * goes (between machines, racks and dimensions) and finds its contents here; the contents never ride
 * on the item, so a drive full of thousands of types is still a tiny item to sync and compare.
 *
 * <p>Lives on the overworld's data storage so ids resolve from any dimension. A drive that is destroyed
 * leaves its volume behind; that leak is small and accepted.
 *
 * <p>A save encodes only the volumes written since the last one. Every key is an item stack run through
 * its codec, and the autosave of a base with thousands of drives spent whole seconds of one tick encoding
 * contents nobody had touched; an unchanged volume now hands its last encoding to the next save as it is.
 */
public final class StorageVolumes extends SavedData {

    public static final String DATA_NAME = "jsc_storage_volumes";
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<UUID, StorageVolume> volumes = new HashMap<>();
    /** Each volume's contents as last encoded, dropped the moment the volume is written. */
    private final Map<UUID, Tag> encoded = new HashMap<>();

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
            return new StorageVolume(key, () -> touched(key), false);
        });
    }

    /** A volume was written: its last encoding no longer describes it, and the store has something to save. */
    private void touched(final UUID id) {
        encoded.remove(id);
        setDirty();
    }

    @Nullable
    public StorageVolume find(final UUID id) {
        return volumes.get(id);
    }

    /** Drops a volume for good; returns whether there was one. */
    public boolean remove(final UUID id) {
        final boolean existed = volumes.remove(id) != null;
        encoded.remove(id);
        if (existed) {
            setDirty();
        }
        return existed;
    }

    public int count() {
        return volumes.size();
    }

    /** How many volumes the next save has to encode: written since the last save, or never saved. For tests. */
    public int pendingEncodes() {
        int pending = 0;
        for (final Map.Entry<UUID, StorageVolume> entry : volumes.entrySet()) {
            if (!entry.getValue().isEmpty() && !encoded.containsKey(entry.getKey())) {
                pending++;
            }
        }
        return pending;
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider registries) {
        final RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        final ListTag list = new ListTag();
        for (final Map.Entry<UUID, StorageVolume> entry : volumes.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue; // a blank volume is recreated blank on demand; no need to write it
            }
            final UUID id = entry.getKey();
            final CompoundTag one = new CompoundTag();
            one.putUUID("Id", id);
            Tag items = encoded.get(id);
            if (items == null) {
                final DataResult<Tag> fresh = ServerStorageContents.CODEC.encodeStart(ops, entry.getValue().snapshot());
                items = fresh.resultOrPartial(error -> LOGGER.error("Could not save storage volume {}: {}", id, error))
                        .orElse(null);
                if (items != null && fresh.result().isPresent()) {
                    encoded.put(id, items); // a whole encoding stands until the volume is written again
                }
            }
            if (items != null) {
                one.put("Items", items);
            }
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
            final StorageVolume volume = new StorageVolume(id, () -> store.touched(id), false);
            final Tag items = one.get("Items");
            if (items != null) {
                final DataResult<ServerStorageContents> parsed = ServerStorageContents.CODEC.parse(ops, items);
                parsed.resultOrPartial(error -> LOGGER.error("Could not load storage volume {}: {}", id, error))
                        .ifPresent(contents -> volume.replaceAll(contents.items()));
                if (parsed.result().isPresent()) {
                    store.encoded.put(id, items); // what was read is what would be written: no encoding owed
                }
            }
            store.volumes.put(id, volume);
        }
        return store;
    }
}
