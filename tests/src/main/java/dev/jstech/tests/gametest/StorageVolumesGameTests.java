/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.storage.ServerStorageContents;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.computronics.storage.StorageVolumes;
import dev.jstech.tests.JsTests;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * A level save encodes only the volumes written since the last one. The autosave of a big base used to spend
 * a whole tick re-encoding thousands of drives nobody had touched, and every one of those seconds was a
 * freeze for the players.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class StorageVolumesGameTests {

    private StorageVolumesGameTests() {
    }

    private static final String ARENA = "empty";

    @GameTest(template = ARENA)
    public static void save_reencodesOnlyTheVolumesWrittenSinceTheLastSave(final GameTestHelper helper) {
        final StorageVolumes store = StorageVolumes.get(helper.getLevel().getServer());
        final HolderLookup.Provider registries = helper.getLevel().registryAccess();
        final UUID stone = UUID.randomUUID();
        final UUID dirt = UUID.randomUUID();
        try {
            store.volume(stone).add(StorageKey.of(Items.STONE), 5L);
            store.volume(dirt).add(StorageKey.of(Items.DIRT), 7L);
            final CompoundTag first = store.save(new CompoundTag(), registries);
            helper.assertTrue(store.pendingEncodes() == 0,
                    "a save leaves nothing to encode; " + store.pendingEncodes() + " left");

            final CompoundTag second = store.save(new CompoundTag(), registries);
            helper.assertTrue(itemsOf(second, stone) == itemsOf(first, stone) && itemsOf(second, dirt) == itemsOf(first, dirt),
                    "an untouched volume's encoding is handed to the next save as it is");

            store.volume(stone).add(StorageKey.of(Items.STONE), 1L);
            helper.assertTrue(store.pendingEncodes() == 1,
                    "only the written volume needs encoding again; " + store.pendingEncodes() + " pending");
            final CompoundTag third = store.save(new CompoundTag(), registries);
            helper.assertTrue(itemsOf(third, stone) != itemsOf(first, stone), "the written volume is encoded afresh");
            helper.assertTrue(itemsOf(third, dirt) == itemsOf(first, dirt), "the other volume is still reused");
            final long stored = ServerStorageContents.CODEC
                    .parse(RegistryOps.create(NbtOps.INSTANCE, registries), itemsOf(third, stone))
                    .result().orElseThrow().items().getOrDefault(StorageKey.of(Items.STONE), 0L);
            helper.assertTrue(stored == 6L, "the fresh encoding carries the write; stored " + stored);
        } finally {
            store.remove(stone);
            store.remove(dirt);
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void load_keepsTheSavedEncodingsSoTheNextSaveReusesThem(final GameTestHelper helper) {
        final StorageVolumes store = StorageVolumes.get(helper.getLevel().getServer());
        final HolderLookup.Provider registries = helper.getLevel().registryAccess();
        final UUID stone = UUID.randomUUID();
        final CompoundTag saved;
        try {
            store.volume(stone).add(StorageKey.of(Items.STONE), 3L);
            saved = store.save(new CompoundTag(), registries);
        } finally {
            store.remove(stone);
        }
        // A store fresh from disk owes no encoding: what it read is what it would write. (The save carries
        // every volume of the shared test level, so only this test's volume is looked at.)
        final StorageVolumes loaded = StorageVolumes.factory().deserializer().apply(saved, registries);
        final long stored = loaded.volume(stone).count(StorageKey.of(Items.STONE));
        helper.assertTrue(stored == 3L, "the volume comes back with its contents; stored " + stored);
        helper.assertTrue(loaded.pendingEncodes() == 0,
                "nothing to encode right after a load; " + loaded.pendingEncodes() + " pending");
        final CompoundTag again = loaded.save(new CompoundTag(), registries);
        helper.assertTrue(itemsOf(again, stone) == itemsOf(saved, stone), "the loaded encoding is reused as it is");
        helper.succeed();
    }

    private static Tag itemsOf(final CompoundTag saved, final UUID id) {
        for (final Tag element : saved.getList("Volumes", Tag.TAG_COMPOUND)) {
            final CompoundTag one = (CompoundTag) element;
            if (one.hasUUID("Id") && one.getUUID("Id").equals(id)) {
                final Tag items = one.get("Items");
                if (items == null) {
                    throw new IllegalStateException("volume " + id + " was saved without contents");
                }
                return items;
            }
        }
        throw new IllegalStateException("volume " + id + " was not saved");
    }
}
