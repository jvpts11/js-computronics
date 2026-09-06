/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.JsComputronics;
import dev.jstech.computronics.os.media.MediaItem;
import dev.jstech.computronics.os.media.MediaKind;
import dev.jstech.computronics.os.media.MediaReaderBlockEntity;
import dev.jstech.computronics.storage.ServerStorageContents;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.tests.JsTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * In-world integration tests for the OS media subsystem: the Media Reader peripheral and
 * the MediaItem. Verifies insertion, payload retrieval, NBT persistence, and the DATA kind.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class OsMediaGameTests {

    private OsMediaGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;

    /**
     * Every drive takes the media it is built for and refuses the rest, and a disc that a drive accepts
     * actually lands in its slot. A drive that will not take its own format is a dead end for the player.
     */
    @GameTest(template = ARENA)
    public static void drives_acceptTheirOwnFormatsAndRefuseTheOthers(final GameTestHelper helper) {
        final BlockPos dvdPos = new BlockPos(2, 2, 2);
        final BlockPos cdPos = new BlockPos(4, 2, 2);
        final BlockPos floppyPos = new BlockPos(6, 2, 2);
        helper.setBlock(dvdPos, ComputingModule.DVD_DRIVE.get());
        helper.setBlock(cdPos, ComputingModule.CD_DRIVE.get());
        helper.setBlock(floppyPos, ComputingModule.FLOPPY_DRIVE.get());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    if (!(helper.getBlockEntity(dvdPos) instanceof MediaReaderBlockEntity dvd)
                            || !(helper.getBlockEntity(cdPos) instanceof MediaReaderBlockEntity cd)
                            || !(helper.getBlockEntity(floppyPos) instanceof MediaReaderBlockEntity floppy)) {
                        throw new IllegalStateException("a drive is missing its block entity");
                    }
                    final ItemStack dvdRom = new ItemStack(ComputingModule.DVD_ROM.get());
                    final ItemStack cdRom = new ItemStack(ComputingModule.CD_ROM.get());
                    final ItemStack disk = new ItemStack(ComputingModule.FLOPPY_DISK.get());

                    helper.assertTrue(dvd.acceptsMedia(dvdRom), "a DVD drive must accept a DVD");
                    helper.assertTrue(dvd.acceptsMedia(cdRom), "a DVD drive also reads CDs");
                    helper.assertTrue(!dvd.acceptsMedia(disk), "a DVD drive must refuse a floppy");
                    helper.assertTrue(!cd.acceptsMedia(dvdRom), "a CD drive cannot read a DVD");
                    helper.assertTrue(cd.acceptsMedia(cdRom), "a CD drive must accept a CD");
                    helper.assertTrue(floppy.acceptsMedia(disk), "a floppy drive must accept a floppy");

                    // The disc a player actually holds is an installer off the creative tab, not a blank
                    // one: it carries a kind and a payload. Those components must not change acceptance.
                    final ItemStack installer = new ItemStack(ComputingModule.DVD_ROM.get());
                    MediaItem.setKind(installer, MediaKind.PROGRAM_INSTALL);
                    MediaItem.setPayload(installer,
                            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "kde_plasma"));
                    helper.assertTrue(dvd.acceptsMedia(installer),
                            "a DVD drive must accept a program installer pressed on a DVD");

                    // Accepting is not enough: the disc has to end up in the slot.
                    helper.assertTrue(dvd.insertMedia(installer.copyWithCount(1)).isEmpty(),
                            "inserting a DVD into a DVD drive must consume the disc");
                    helper.assertTrue(dvd.insertedKind() == MediaKind.PROGRAM_INSTALL,
                            "the inserted installer must be readable from the slot afterwards");
                })
                .thenSucceed();
    }

    /**
     * Breaking a drive that holds a disc must remove the drive and drop the disc. Dropping the disc empties
     * the slot, and the slot's client sync used to write the drive's own state back into the world in the
     * middle of the removal, which made the chunk abort it: the disc popped out and the drive stayed.
     */
    @GameTest(template = ARENA)
    public static void drive_breaksCleanlyWhileLoaded(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ComputingModule.CD_DRIVE.get());
        if (!(helper.getBlockEntity(pos) instanceof MediaReaderBlockEntity reader)) {
            helper.fail("no MediaReaderBlockEntity at " + pos);
            return;
        }
        helper.assertTrue(reader.insertMedia(new ItemStack(ComputingModule.CD_ROM.get())).isEmpty(),
                "the CD drive must take the CD");
        helper.startSequence()
                .thenExecute(() -> helper.assertTrue(helper.getLevel().destroyBlock(helper.absolutePos(pos), true),
                        "breaking the loaded drive must remove it"))
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.AIR, pos);
                    // Straight from the level: the helper's own lookup fails loudly on a missing block entity.
                    helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(pos)) == null,
                            "the drive's block entity must be gone with the block");
                })
                .thenExecuteAfter(2, () -> helper.assertItemEntityPresent(ComputingModule.CD_ROM.get(), pos, 2.0))
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void os_mediaReaderHoldsInstaller(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ComputingModule.CD_DRIVE.get());

        if (!(helper.getBlockEntity(pos) instanceof MediaReaderBlockEntity reader)) {
            helper.fail("no MediaReaderBlockEntity at " + pos);
            return;
        }

        // Build a MediaItem stack of kind OS_INSTALL stamped with jsc:mc_dos.
        final ResourceLocation mcDos = ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "mc_dos");
        final ItemStack media = new ItemStack(ComputingModule.CD_ROM.get());
        MediaItem.setKind(media, MediaKind.OS_INSTALL);
        MediaItem.setPayload(media, mcDos);

        // Insert it directly into the slot handler (simulates right-click insertion).
        reader.mediaSlot().setStackInSlot(0, media);

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ResourceLocation payload = reader.insertedPayload();
                    helper.assertTrue(payload != null, "insertedPayload() must not be null after insertion");
                    helper.assertTrue(mcDos.equals(payload),
                            "insertedPayload() must equal jsc:mc_dos; got " + payload);

                    // NBT round-trip: save then reload into a fresh BE instance.
                    final var registries = helper.getLevel().registryAccess();
                    final net.minecraft.nbt.CompoundTag saved = reader.saveWithFullMetadata(registries);

                    final MediaReaderBlockEntity reloaded =
                            new MediaReaderBlockEntity(helper.absolutePos(pos), reader.getBlockState());
                    reloaded.loadWithComponents(saved, registries);

                    final ResourceLocation afterReload = reloaded.insertedPayload();
                    helper.assertTrue(afterReload != null,
                            "insertedPayload() must not be null after NBT round-trip");
                    helper.assertTrue(mcDos.equals(afterReload),
                            "insertedPayload() must still equal jsc:mc_dos after reload; got " + afterReload);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void os_mediaStoresData(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ComputingModule.CD_DRIVE.get());

        if (!(helper.getBlockEntity(pos) instanceof MediaReaderBlockEntity reader)) {
            helper.fail("no MediaReaderBlockEntity at " + pos);
            return;
        }

        // Build a DATA medium with two item entries.
        final ItemStack media = new ItemStack(ComputingModule.CD_ROM.get());
        MediaItem.setKind(media, MediaKind.DATA);

        final StorageKey ironKey = StorageKey.of(Items.IRON_INGOT);
        final StorageKey diamondKey = StorageKey.of(Items.DIAMOND);
        final ServerStorageContents contents = new ServerStorageContents(
                Map.of(ironKey, 64L, diamondKey, 10L));
        MediaItem.setData(media, contents);

        reader.mediaSlot().setStackInSlot(0, media);

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    // Verify kind and data before round-trip.
                    helper.assertTrue(MediaKind.DATA == reader.insertedKind(),
                            "insertedKind() must be DATA; got " + reader.insertedKind());
                    helper.assertTrue(contents.equals(reader.insertedData()),
                            "insertedData() must equal the written contents");

                    // NBT round-trip: save then reload into a fresh BE instance.
                    final var registries = helper.getLevel().registryAccess();
                    final net.minecraft.nbt.CompoundTag saved = reader.saveWithFullMetadata(registries);

                    final MediaReaderBlockEntity reloaded =
                            new MediaReaderBlockEntity(helper.absolutePos(pos), reader.getBlockState());
                    reloaded.loadWithComponents(saved, registries);

                    helper.assertTrue(MediaKind.DATA == reloaded.insertedKind(),
                            "insertedKind() must be DATA after reload; got " + reloaded.insertedKind());
                    helper.assertTrue(contents.equals(reloaded.insertedData()),
                            "insertedData() must equal the written contents after reload");
                })
                .thenSucceed();
    }
}
