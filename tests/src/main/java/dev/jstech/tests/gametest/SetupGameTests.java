/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Tech Series.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computers.ComputingModule;
import dev.jstech.computers.JsComputers;
import dev.jstech.computers.blockentity.MainframeBlockEntity;
import dev.jstech.computers.hardware.DiskSize;
import dev.jstech.computers.hardware.StorageTier;
import dev.jstech.computers.operation.payload.SetupProgressPayload;
import dev.jstech.computers.os.OsRegistry;
import dev.jstech.computers.os.ProgramSpec;
import dev.jstech.computers.os.install.SetupJob;
import dev.jstech.computers.os.install.SetupRunner;
import dev.jstech.computers.os.install.SetupTiming;
import dev.jstech.computers.os.media.MediaFormat;
import dev.jstech.computers.os.media.MediaItem;
import dev.jstech.computers.os.media.MediaKind;
import dev.jstech.computers.os.media.MediaReaderBlockEntity;
import dev.jstech.computers.program.ComputerConsoleState;
import dev.jstech.computers.program.ServerCliComputer;
import dev.jstech.computers.program.cli.ICliComputer;
import dev.jstech.tests.JsTests;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Installing a program as something a machine does over time.
 *
 * <p>An install used to be a flag that flipped the instant it was asked. Now it is a job the machine
 * holds and ticks: nothing is installed until the last tick, cancelling leaves nothing behind, a
 * refusal comes with its reason, and a job survives the machine being saved and loaded.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class SetupGameTests {

    private SetupGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;
    private static final ResourceLocation FRAMES_95 = jsc("frames_95");
    private static final ResourceLocation MINESWEEPER = jsc("minesweeper");

    private static ResourceLocation jsc(final String path) {
        return ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, path);
    }

    /** A Frames 95 Mainframe with a floppy drive beside it, the program's floppy in the drive. */
    private static MainframeBlockEntity machineWithFloppy(final GameTestHelper helper, final BlockPos pos,
                                                          final ResourceLocation program) {
        helper.setBlock(pos, ComputingModule.MAINFRAME.get());
        if (!(helper.getBlockEntity(pos) instanceof MainframeBlockEntity mainframe)) {
            throw new IllegalStateException("no Mainframe at " + pos);
        }
        final ItemStackHandler inv = mainframe.getInventory();
        inv.setStackInSlot(MainframeBlockEntity.MOTHERBOARD_SLOT, new ItemStack(ComputingModule.MOTHERBOARD_MTX_P.get()));
        inv.setStackInSlot(MainframeBlockEntity.CPU_SLOTS_START, new ItemStack(ComputingModule.CPU_SERVO_2620.get()));
        inv.setStackInSlot(MainframeBlockEntity.RAM_SLOTS_START, new ItemStack(ComputingModule.RAM_DDR3_8192.get()));
        inv.setStackInSlot(MainframeBlockEntity.PSU_SLOT, new ItemStack(ComputingModule.PSU_650G.get()));
        inv.setStackInSlot(MainframeBlockEntity.DISK_SLOTS_START,
                new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)));
        // A GPU gives the Mainframe peripheral ports so the adjacent drive can link to it.
        inv.setStackInSlot(MainframeBlockEntity.GPU_SLOTS_START, new ItemStack(ComputingModule.GPU_HD_7970.get()));
        mainframe.togglePower();
        if (!mainframe.installOs(FRAMES_95)) {
            throw new IllegalStateException("could not install Frames 95 on the test Mainframe");
        }
        final BlockPos readerPos = pos.east();
        helper.setBlock(readerPos, ComputingModule.FLOPPY_DRIVE.get());
        if (!(helper.getBlockEntity(readerPos) instanceof MediaReaderBlockEntity reader)) {
            throw new IllegalStateException("no floppy drive at " + readerPos);
        }
        final ItemStack floppy = new ItemStack(ComputingModule.FLOPPY_DISK.get());
        MediaItem.setKind(floppy, MediaKind.PROGRAM_INSTALL);
        MediaItem.setPayload(floppy, program);
        reader.mediaSlot().setStackInSlot(0, floppy);
        return mainframe;
    }

    private static ProgramSpec spec(final ResourceLocation id) {
        final ProgramSpec spec = OsRegistry.getProgram(id);
        if (spec == null) {
            throw new IllegalStateException("no program " + id);
        }
        return spec;
    }

    /** Nothing is installed until the last tick, and then it is. */
    @GameTest(template = ARENA, timeoutTicks = 500)
    public static void setup_installsWhenItsTimeIsUpAndNotBefore(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = machineWithFloppy(helper, pos, MINESWEEPER);
        final int ticks = SetupTiming.ticks(spec(MINESWEEPER).minDiskMb(), MediaFormat.FLOPPY, false,
                SetupTiming.eraFactor(mainframe.displayEra()));
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    final Optional<String> refusal = SetupRunner.begin(mainframe, helper.getLevel(),
                            helper.absolutePos(pos), spec(MINESWEEPER), MediaFormat.FLOPPY, false);
                    helper.assertTrue(refusal.isEmpty(), "the machine takes the program: " + refusal.orElse(""));
                    final SetupJob job = mainframe.console().setup();
                    helper.assertTrue(job != null && !job.removing(), "a setup job is running");
                    helper.assertTrue(job.ticksTotal() == ticks, "the job takes the floppy's time: " + job.ticksTotal());
                    helper.assertFalse(mainframe.console().isInstalled(MINESWEEPER.toString()),
                            "nothing is installed while Setup is copying");
                })
                .thenExecuteAfter(ticks / 2, () -> helper.assertFalse(
                        mainframe.console().isInstalled(MINESWEEPER.toString()), "still nothing at the halfway mark"))
                .thenExecuteAfter(ticks / 2 + 10, () -> {
                    helper.assertTrue(mainframe.console().isInstalled(MINESWEEPER.toString()),
                            "the program is installed once the time is up");
                    helper.assertTrue(mainframe.console().setup() == null, "the job is over");
                })
                .thenSucceed();
    }

    /** Cancel stops the job, and the machine is as it was. */
    @GameTest(template = ARENA, timeoutTicks = 500)
    public static void setup_cancelLeavesNothingInstalled(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = machineWithFloppy(helper, pos, MINESWEEPER);
        final int ticks = SetupTiming.ticks(spec(MINESWEEPER).minDiskMb(), MediaFormat.FLOPPY, false,
                SetupTiming.eraFactor(mainframe.displayEra()));
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> SetupRunner.begin(mainframe, helper.getLevel(),
                        helper.absolutePos(pos), spec(MINESWEEPER), MediaFormat.FLOPPY, false))
                .thenExecuteAfter(20, () -> {
                    helper.assertTrue(mainframe.console().setup() != null, "the job is running");
                    SetupRunner.cancel(mainframe, helper.getLevel(), helper.absolutePos(pos));
                    helper.assertTrue(mainframe.console().setup() == null, "cancel ends the job");
                })
                .thenExecuteAfter(ticks + 10, () -> helper.assertFalse(
                        mainframe.console().isInstalled(MINESWEEPER.toString()),
                        "a cancelled setup installs nothing, however long you wait"))
                .thenSucceed();
    }

    /** A program the machine cannot take is refused with the reason, and no job starts. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void setup_refusesWithTheReason(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = machineWithFloppy(helper, pos, MINESWEEPER);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    // Virtual Studio wants Frames XP or newer; this machine runs Frames 95.
                    final Optional<String> refusal = SetupRunner.begin(mainframe, helper.getLevel(),
                            helper.absolutePos(pos), spec(jsc("virtual_studio")), MediaFormat.DVD, false);
                    helper.assertTrue(refusal.isPresent(), "a program the system is too old for is refused");
                    helper.assertTrue(refusal.get().contains("or newer") || refusal.get().contains("needs"),
                            "the refusal says why: " + refusal.get());
                    helper.assertTrue(mainframe.console().setup() == null, "no job starts for a refused program");
                    // Removing what is not there is refused too.
                    final Optional<String> notThere = SetupRunner.begin(mainframe, helper.getLevel(),
                            helper.absolutePos(pos), spec(MINESWEEPER), null, true);
                    helper.assertTrue(notThere.isPresent() && notThere.get().contains("not installed"),
                            "removing a program that is not there says so: " + notThere.orElse(""));
                })
                .thenSucceed();
    }

    /** A job half done goes with the machine and comes back where it was. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void setup_survivesTheConsoleBeingSavedAndLoaded(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = machineWithFloppy(helper, pos, MINESWEEPER);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> SetupRunner.begin(mainframe, helper.getLevel(),
                        helper.absolutePos(pos), spec(MINESWEEPER), MediaFormat.FLOPPY, false))
                .thenExecuteAfter(10, () -> {
                    final SetupJob before = mainframe.console().setup();
                    helper.assertTrue(before != null, "the job is running");
                    final CompoundTag tag = new CompoundTag();
                    mainframe.console().save(tag);
                    final ComputerConsoleState loaded = new ComputerConsoleState();
                    loaded.load(tag);
                    final SetupJob after = loaded.setup();
                    helper.assertTrue(after != null, "the job is saved with the console");
                    helper.assertTrue(after.programId().equals(before.programId())
                                    && after.ticksLeft() == before.ticksLeft()
                                    && after.ticksTotal() == before.ticksTotal()
                                    && after.source().equals(before.source()),
                            "the job comes back exactly where it was");
                })
                .thenSucceed();
    }

    /** Removing is the same job backwards, a fifth as long, and the program is gone at the end. */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void setup_removingTakesAFifthAndUninstalls(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = machineWithFloppy(helper, pos, MINESWEEPER);
        final int factor = SetupTiming.eraFactor(mainframe.displayEra());
        final int install = SetupTiming.networkTicks(spec(MINESWEEPER).minDiskMb(), false, factor);
        final int remove = SetupTiming.networkTicks(spec(MINESWEEPER).minDiskMb(), true, factor);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    mainframe.console().install(MINESWEEPER.toString());
                    final Optional<String> refusal = SetupRunner.begin(mainframe, helper.getLevel(),
                            helper.absolutePos(pos), spec(MINESWEEPER), null, true);
                    helper.assertTrue(refusal.isEmpty(), "removing an installed program is allowed: " + refusal.orElse(""));
                    final SetupJob job = mainframe.console().setup();
                    helper.assertTrue(job != null && job.removing(), "a removal job is running");
                    // A small program sits at the floor on install, so removing is shorter but not a fifth to the tick.
                    helper.assertTrue(job.ticksTotal() == remove && remove < install,
                            "removing is quicker than installing: " + job.ticksTotal() + " of " + install);
                })
                .thenExecuteAfter(remove + 10, () -> helper.assertFalse(
                        mainframe.console().isInstalled(MINESWEEPER.toString()), "the program is gone at the end"))
                .thenSucceed();
    }

    /** The prompt's install starts the job rather than finishing on the spot. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void install_atThePromptStartsASetup(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = machineWithFloppy(helper, pos, MINESWEEPER);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    final ServerCliComputer cli = new ServerCliComputer(mainframe, helper.getLevel());
                    final ICliComputer.OpResult result = cli.install("minesweeper");
                    helper.assertTrue(result.ok(), "install is accepted: " + result.message());
                    helper.assertTrue(result.message().contains("Setting up") && result.message().contains("floppy"),
                            "the prompt says what it is doing and from where: " + result.message());
                    helper.assertTrue(mainframe.console().setup() != null, "a job is running");
                    helper.assertFalse(mainframe.console().isInstalled(MINESWEEPER.toString()),
                            "the program is not there the instant it was asked for");
                    final ICliComputer.OpResult again = cli.install("minesweeper");
                    helper.assertFalse(again.ok(), "a second install while one runs is refused");
                    helper.assertTrue(again.message().contains("still setting up"), "and says why: " + again.message());
                })
                .thenSucceed();
    }

    /** The progress payload encodes at the longest every one of its strings may be. */
    @GameTest(template = ARENA)
    public static void setupProgress_encodesAtItsLongest(final GameTestHelper helper) {
        final String id = "x".repeat(64);
        final String name = "n".repeat(64);
        final String text = "m".repeat(160);
        final SetupProgressPayload payload = new SetupProgressPayload(new BlockPos(1, 2, 3), id, name, name, 512,
                name, 999, name, SetupProgressPayload.STATE_REFUSED, text, true);
        final RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().registryAccess());
        try {
            SetupProgressPayload.STREAM_CODEC.encode(buf, payload);
        } catch (final RuntimeException e) {
            helper.fail("the payload does not encode at its longest: " + e.getMessage());
            return;
        }
        final SetupProgressPayload back = SetupProgressPayload.STREAM_CODEC.decode(buf);
        helper.assertTrue(back.equals(payload), "the payload survives the wire whole");
        // Longer than the wire allows is clipped, never fatal.
        final SetupProgressPayload longer = new SetupProgressPayload(new BlockPos(1, 2, 3), id, name, name, 1,
                name, 0, name, SetupProgressPayload.STATE_REFUSED, "m".repeat(400), false);
        final RegistryFriendlyByteBuf buf2 = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().registryAccess());
        SetupProgressPayload.STREAM_CODEC.encode(buf2, longer);
        helper.assertTrue(SetupProgressPayload.STREAM_CODEC.decode(buf2).message().length() == 160,
                "a refusal too long for the wire is shortened");
        helper.succeed();
    }
}
