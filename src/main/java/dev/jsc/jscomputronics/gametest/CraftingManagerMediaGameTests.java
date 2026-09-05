/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.gametest;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.PatternEncoderBlockEntity;
import dev.jsc.jscomputronics.module.computing.operation.payload.ComputingPayloads;
import dev.jsc.jscomputronics.module.computing.operation.payload.CraftManagerStatePayload;
import dev.jsc.jscomputronics.module.computing.os.media.MediaReaderBlockEntity;
import dev.jsc.jscomputronics.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Crafting Manager shows the disc that carries the recipes. A computer usually has more than one drive
 * linked, and the first of them may hold a blank medium: a demo went wrong when three crafts written onto a
 * DVD-RW were nowhere to be seen because the manager was looking at the first writable medium it found.
 */
@GameTestHolder(JsComputronics.MODID)
@PrefixGameTestTemplate(false)
public final class CraftingManagerMediaGameTests {

    private CraftingManagerMediaGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;
    // The crafting network's computer sits at (5,2,2) with its rear on the cable to the west; the drives go
    // on its free sides, the floppy drive first so it is the first linked endpoint.
    private static final BlockPos FLOPPY_DRIVE = new BlockPos(5, 2, 3);
    private static final BlockPos DVD_DRIVE = new BlockPos(5, 2, 1);
    private static final BlockPos ENCODER = new BlockPos(1, 2, 4);

    @GameTest(template = ARENA)
    public static void craftManagerState_showsTheDriveWhoseDiscHoldsTheCrafts(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        world.setBlock(FLOPPY_DRIVE, ComputingModule.FLOPPY_DRIVE.get());
        world.setBlock(DVD_DRIVE, ComputingModule.DVD_DRIVE.get());
        // Three... well, one craft written onto a DVD-RW by the encoder, the way a player does it.
        world.setBlock(ENCODER, ComputingModule.PATTERN_ENCODER.get());
        final PatternEncoderBlockEntity encoder = world.blockEntity(ENCODER, PatternEncoderBlockEntity.class);
        encoder.media().setStackInSlot(0, new ItemStack(ComputingModule.DVD_RW.get()));
        encoder.setGhost(0, new ItemStack(Items.OAK_LOG));
        helper.assertTrue(encoder.writePattern(), "the encoder writes the craft onto the DVD-RW");
        final ItemStack dvd = encoder.media().getStackInSlot(0);
        encoder.media().setStackInSlot(0, ItemStack.EMPTY);

        final MediaReaderBlockEntity floppyDrive = world.blockEntity(FLOPPY_DRIVE, MediaReaderBlockEntity.class);
        final MediaReaderBlockEntity dvdDrive = world.blockEntity(DVD_DRIVE, MediaReaderBlockEntity.class);
        floppyDrive.mediaSlot().setStackInSlot(0, new ItemStack(ComputingModule.FLOPPY_DISK.get()));
        dvdDrive.mediaSlot().setStackInSlot(0, dvd);

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final BlockPos computer = net.cc().getBlockPos();
                    helper.assertTrue(computer.equals(floppyDrive.ownerPos()) && computer.equals(dvdDrive.ownerPos()),
                            "both drives link to the Crafting Computer; got " + floppyDrive.ownerPos() + " and "
                                    + dvdDrive.ownerPos());
                    final CraftManagerStatePayload state =
                            ComputingPayloads.buildCraftManagerState(net.cc(), helper.getLevel());
                    helper.assertTrue(state.mediaVolumeKey().equals("media:" + dvdDrive.getBlockPos().asLong()),
                            "the manager shows the DVD drive, whose disc holds the craft; got " + state.mediaVolumeKey());
                    helper.assertTrue(state.mediaFiles().size() == 1,
                            "the DVD's one craft is listed; got " + state.mediaFiles());
                    // With the DVD out, the blank floppy is still offered: a download needs a writable target.
                    dvdDrive.mediaSlot().setStackInSlot(0, ItemStack.EMPTY);
                    final CraftManagerStatePayload fallback =
                            ComputingPayloads.buildCraftManagerState(net.cc(), helper.getLevel());
                    helper.assertTrue(fallback.mediaVolumeKey().equals("media:" + floppyDrive.getBlockPos().asLong())
                                    && fallback.mediaFiles().isEmpty(),
                            "without crafts anywhere, the first writable medium is offered; got "
                                    + fallback.mediaVolumeKey());
                })
                .thenSucceed();
    }

    /**
     * A recipe file renamed in the explorer to the longest name the filesystem allows, and a ROM pattern whose
     * result was renamed past the wire field, must still leave the manager's state sendable: an unsendable
     * payload disconnects the player on every refresh.
     */
    @GameTest(template = ARENA)
    public static void craftManagerState_fitsTheWireWithLongNames(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        world.setBlock(DVD_DRIVE, ComputingModule.DVD_DRIVE.get());
        world.setBlock(ENCODER, ComputingModule.PATTERN_ENCODER.get());
        final PatternEncoderBlockEntity encoder = world.blockEntity(ENCODER, PatternEncoderBlockEntity.class);
        encoder.media().setStackInSlot(0, new ItemStack(ComputingModule.DVD_RW.get()));
        encoder.setGhost(0, new ItemStack(Items.OAK_LOG));
        helper.assertTrue(encoder.writePattern(), "the encoder writes the craft onto the DVD-RW");
        final ItemStack dvd = encoder.media().getStackInSlot(0);
        encoder.media().setStackInSlot(0, ItemStack.EMPTY);

        // The explorer lets a player rename a file to anything up to the filesystem's limit.
        final String longName = "a".repeat(dev.jsc.jscomputronics.module.computing.os.fs.FsPaths.MAX_NAME_LENGTH - 6)
                + ".craft";
        String written = null;
        for (final dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.FileEntry entry
                : dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.list(dvd, "",
                        dev.jsc.jscomputronics.module.computing.os.FilesystemKind.HIERARCHICAL)) {
            if (entry.type() == dev.jsc.jscomputronics.module.computing.os.fs.FileType.CRAFT) {
                written = entry.path();
            }
        }
        helper.assertTrue(written != null, "the craft is on the disc");
        helper.assertTrue(dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.rename(dvd, written, longName,
                        dev.jsc.jscomputronics.module.computing.os.FilesystemKind.HIERARCHICAL),
                "the craft takes the longest name the filesystem allows");
        world.blockEntity(DVD_DRIVE, MediaReaderBlockEntity.class).mediaSlot().setStackInSlot(0, dvd);

        // A ROM pattern whose result carries a name longer than the wire field.
        final java.util.List<ItemStack> grid = new java.util.ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        grid.set(0, new ItemStack(Items.OAK_LOG));
        final ItemStack renamed = new ItemStack(Items.OAK_PLANKS, 4);
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("n".repeat(70)));
        helper.assertTrue(net.cc().loadPattern(
                        new dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern(grid, renamed)),
                "the renamed pattern goes into the ROM");

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final CraftManagerStatePayload state =
                            ComputingPayloads.buildCraftManagerState(net.cc(), helper.getLevel());
                    final RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
                            io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
                    CraftManagerStatePayload.STREAM_CODEC.encode(buf, state); // must not throw
                    final CraftManagerStatePayload decoded = CraftManagerStatePayload.STREAM_CODEC.decode(buf);
                    helper.assertTrue(decoded.mediaFiles().contains(longName),
                            "the long file name travels whole; got " + decoded.mediaFiles());
                    boolean clamped = false;
                    for (final CraftManagerStatePayload.WireRomEntry entry : decoded.romEntries()) {
                        clamped |= entry.name().length() == 64 && entry.name().startsWith("nnnn");
                    }
                    helper.assertTrue(clamped, "the long result name is cut to its field; got " + decoded.romEntries());
                })
                .thenSucceed();
    }

    /**
     * Writing a recipe onto a disc that already holds a different one under the same name must not erase it:
     * the newcomer takes the next free suffix, and the same recipe written twice lands on its own file.
     */
    @GameTest(template = ARENA)
    public static void uniquePath_keepsARecipeAlreadyOnTheDiscUnderThatName(final GameTestHelper helper) {
        final ItemStack dvd = new ItemStack(ComputingModule.DVD_RW.get());
        final dev.jsc.jscomputronics.module.computing.os.FilesystemKind kind =
                dev.jsc.jscomputronics.module.computing.os.FilesystemKind.HIERARCHICAL;
        final String first = dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.uniquePath(
                dvd, "oak_planks", ".craft", "recipe A");
        helper.assertTrue(first.equals("oak_planks.craft"), "an empty disc takes the plain name; got " + first);
        helper.assertTrue(dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.write(dvd, first,
                        dev.jsc.jscomputronics.module.computing.os.fs.FileType.CRAFT, "recipe A", 1_000_000L, kind)
                        == dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.WriteResult.OK,
                "the first recipe is written");
        final String second = dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.uniquePath(
                dvd, "oak_planks", ".craft", "recipe B");
        helper.assertTrue(second.equals("oak_planks_2.craft"),
                "a different recipe with the same name takes the next suffix; got " + second);
        final String again = dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem.uniquePath(
                dvd, "oak_planks", ".craft", "recipe A");
        helper.assertTrue(again.equals("oak_planks.craft"), "the same recipe lands on its own file; got " + again);
        helper.succeed();
    }
}
