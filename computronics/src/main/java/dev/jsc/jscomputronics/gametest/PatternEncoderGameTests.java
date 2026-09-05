/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.gametest;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jstech.core.tier.HardwareEra;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.PatternEncoderBlockEntity;
import dev.jsc.jscomputronics.module.computing.os.fs.CraftFile;
import dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem;
import dev.jsc.jscomputronics.module.computing.os.media.MediaFormat;
import dev.jsc.jscomputronics.testkit.CraftFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The Pattern Encoder as a burner: which media each era's encoder takes, how a job moves through seek, write
 * and verify, that the bay is locked while the head is down, that the queue drains in order, and that a job
 * survives a reload. The encoder authors nothing; the content it burns comes from a computer's Studio.
 */
@GameTestHolder(JsComputronics.MODID)
@PrefixGameTestTemplate(false)
public final class PatternEncoderGameTests {

    private PatternEncoderGameTests() {
    }

    private static final String ARENA = "empty";
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    @GameTest(template = ARENA)
    public static void encoder_acceptsTheMediaOfItsEra(final GameTestHelper helper) {
        final PatternEncoderBlockEntity vintage = place(helper, new BlockPos(1, 2, 1), ComputingModule.VINTAGE_PATTERN_ENCODER.get());
        final PatternEncoderBlockEntity legacy = place(helper, new BlockPos(3, 2, 1), ComputingModule.LEGACY_PATTERN_ENCODER.get());
        final PatternEncoderBlockEntity standard = place(helper, new BlockPos(5, 2, 1), ComputingModule.PATTERN_ENCODER.get());
        final ItemStack floppy = new ItemStack(ComputingModule.FLOPPY_DISK.get());
        final ItemStack cdRw = new ItemStack(ComputingModule.CD_RW.get());
        final ItemStack dvdRw = new ItemStack(ComputingModule.DVD_RW.get());
        final ItemStack usb = new ItemStack(ComputingModule.USB_FLASH_DRIVE.get());
        final ItemStack cdRom = new ItemStack(ComputingModule.CD_ROM.get());

        helper.assertTrue(vintage.era() == HardwareEra.VINTAGE && legacy.era() == HardwareEra.LEGACY
                && standard.era() == HardwareEra.STANDARD, "each block reports its era");
        helper.assertTrue(vintage.acceptsMedia(floppy), "a Vintage encoder takes a floppy");
        helper.assertFalse(vintage.acceptsMedia(cdRw), "a Vintage encoder refuses a CD");
        helper.assertTrue(legacy.acceptsMedia(cdRw), "a Legacy encoder takes a CD-RW");
        helper.assertFalse(legacy.acceptsMedia(floppy), "a Legacy encoder refuses a floppy");
        helper.assertFalse(legacy.acceptsMedia(dvdRw), "a Legacy encoder refuses a DVD");
        helper.assertTrue(standard.acceptsMedia(dvdRw) && standard.acceptsMedia(cdRw) && standard.acceptsMedia(usb),
                "a Standard encoder takes DVD-RW, CD-RW and USB");
        helper.assertFalse(standard.acceptsMedia(floppy), "a Standard encoder refuses a floppy");
        helper.assertFalse(standard.acceptsMedia(cdRom), "read-only media is refused everywhere");
        helper.assertTrue(PatternEncoderBlockEntity.eraAccepts(HardwareEra.ADVANCED, MediaFormat.USB),
                "later eras write what the Standard one writes");
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void encoder_burnsAQueuedFileThroughSeekWriteAndVerify(final GameTestHelper helper) {
        final HolderLookup.Provider reg = helper.getLevel().registryAccess();
        final PatternEncoderBlockEntity encoder = place(helper, POS, ComputingModule.VINTAGE_PATTERN_ENCODER.get());
        encoder.media().setStackInSlot(0, new ItemStack(ComputingModule.FLOPPY_DISK.get()));
        final String content = CraftFile.serialize(CraftFiles.oakPlanks(), reg).orElseThrow();
        final int bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        final int total = PatternEncoderBlockEntity.burnTicks(MediaFormat.FLOPPY, bytes);
        helper.assertTrue(PatternEncoderBlockEntity.seekTicks(MediaFormat.FLOPPY) == 20
                && PatternEncoderBlockEntity.verifyTicks(MediaFormat.FLOPPY) == 10 && total > 30,
                "a floppy seeks 20 ticks and verifies 10; total=" + total);

        helper.assertTrue(encoder.queueBurn("oak_planks", content), "the job is queued");
        helper.assertTrue(encoder.queued() == 1 && !encoder.busy(), "queued, not yet started");
        // Watch the job tick by tick: the phases must come in order, the bay must stay locked while the head
        // is down, and the file must only be on the disc once the write is over.
        final List<PatternEncoderBlockEntity.Phase> seen = new ArrayList<>();
        final int[] ticks = {0};
        helper.startSequence()
                .thenWaitUntil(() -> {
                    ticks[0]++;
                    final PatternEncoderBlockEntity.Phase phase = encoder.phase();
                    if (seen.isEmpty() || seen.get(seen.size() - 1) != phase) {
                        seen.add(phase);
                    }
                    if (encoder.busy()) {
                        helper.assertTrue(encoder.locked(), "the bay is locked while the head is down");
                        helper.assertTrue(encoder.ejectMedia().isEmpty(), "the medium cannot be pulled mid-job");
                    }
                    if (phase == PatternEncoderBlockEntity.Phase.SEEK || phase == PatternEncoderBlockEntity.Phase.WRITE) {
                        helper.assertTrue(CraftFiles.count(encoder.mediaStack()) == 0, "nothing is on the disc before the write ends");
                    }
                    if (phase != PatternEncoderBlockEntity.Phase.DONE) {
                        throw new net.minecraft.gametest.framework.GameTestAssertException("still burning: " + phase);
                    }
                })
                .thenExecute(() -> {
                    final List<PatternEncoderBlockEntity.Phase> order = new ArrayList<>(seen);
                    order.remove(PatternEncoderBlockEntity.Phase.IDLE);
                    helper.assertTrue(order.equals(List.of(PatternEncoderBlockEntity.Phase.SEEK,
                            PatternEncoderBlockEntity.Phase.WRITE, PatternEncoderBlockEntity.Phase.VERIFY,
                            PatternEncoderBlockEntity.Phase.DONE)), "the phases run seek, write, verify, done; got " + seen);
                    helper.assertTrue(Math.abs(ticks[0] - total) <= 3,
                            "the job takes about " + total + " ticks; took " + ticks[0]);
                    helper.assertTrue(encoder.progressPercent() == 100, "done reads 100%");
                    helper.assertTrue(encoder.completed() == 1, "one file completed");
                    final ItemStack media = encoder.mediaStack();
                    final String back = DiskFilesystem.read(media, "oak_planks.craft").orElse("");
                    helper.assertTrue(CraftFile.parse(back, reg).map(p -> p.result().getCount() == 4).orElse(false),
                            "the burned file reads back as the pattern");
                })
                .thenExecuteAfter(PatternEncoderBlockEntity.HOLD_TICKS + 1, () -> {
                    helper.assertFalse(encoder.locked(), "the bay unlocks when the job is over");
                    helper.assertFalse(encoder.ejectMedia().isEmpty(), "the medium comes out afterwards");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void encoder_drainsItsQueueInOrderAndNamesDuplicates(final GameTestHelper helper) {
        final HolderLookup.Provider reg = helper.getLevel().registryAccess();
        final PatternEncoderBlockEntity encoder = place(helper, POS, ComputingModule.LEGACY_PATTERN_ENCODER.get());
        encoder.media().setStackInSlot(0, new ItemStack(ComputingModule.CD_RW.get()));
        final String planks = CraftFile.serialize(CraftFiles.oakPlanks(), reg).orElseThrow();
        final String furnace = CraftFile.serializeProcessing(CraftFiles.furnaceIron(200), reg).orElseThrow();
        helper.assertTrue(encoder.queueBurn("oak_planks", planks), "first job queued");
        helper.assertTrue(encoder.queueBurn("iron_ingot", furnace), "second job queued");
        helper.assertTrue(encoder.queueBurn("oak_planks", planks + " "), "a third job under a taken name is queued");
        helper.assertTrue(encoder.queued() == 3, "three waiting");
        for (int i = 0; i < PatternEncoderBlockEntity.QUEUE_MAX - 3; i++) {
            helper.assertTrue(encoder.queueBurn("filler" + i, planks), "the queue takes up to its limit");
        }
        helper.assertFalse(encoder.queueBurn("overflow", planks), "the queue refuses beyond its limit");
        encoder.cancelAll();
        helper.assertTrue(encoder.queued() == 0, "cancel drops every waiting job");
        helper.assertTrue(encoder.queueBurn("oak_planks", planks) && encoder.queueBurn("iron_ingot", furnace)
                && encoder.queueBurn("oak_planks", planks + " "), "re-queued after the cancel");

        helper.startSequence()
                .thenExecuteAfter(300, () -> {
                    final ItemStack media = encoder.mediaStack();
                    helper.assertTrue(encoder.completed() == 3, "three files burned; completed=" + encoder.completed());
                    helper.assertTrue(CraftFiles.count(media) == 3, "three files on the disc");
                    helper.assertTrue(DiskFilesystem.exists(media, "oak_planks.craft"), "the first keeps its name");
                    helper.assertTrue(DiskFilesystem.exists(media, "iron_ingot.craft"), "the second keeps its name");
                    helper.assertTrue(DiskFilesystem.exists(media, "oak_planks_2.craft"),
                            "a second file under a taken name gets a suffix instead of overwriting");
                    helper.assertFalse(encoder.busy(), "idle when the queue is empty");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void encoder_waitsForMediaAndSurvivesAReload(final GameTestHelper helper) {
        final HolderLookup.Provider reg = helper.getLevel().registryAccess();
        final PatternEncoderBlockEntity encoder = place(helper, POS, ComputingModule.PATTERN_ENCODER.get());
        final String planks = CraftFile.serialize(CraftFiles.oakPlanks(), reg).orElseThrow();
        helper.assertTrue(encoder.queueBurn("oak_planks", planks), "a job queues with the bay empty");
        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    helper.assertFalse(encoder.busy(), "with no medium the job waits");
                    helper.assertTrue("Insert media".equals(encoder.statusLine()), "the display says what is missing");
                    // The block goes away and comes back with its saved state: the job is still waiting.
                    final CompoundTag saved = encoder.saveWithFullMetadata(reg);
                    helper.setBlock(POS, Blocks.AIR);
                    helper.setBlock(POS, ComputingModule.PATTERN_ENCODER.get());
                    if (!(helper.getBlockEntity(POS) instanceof PatternEncoderBlockEntity reloaded)) {
                        helper.fail("no reloaded encoder");
                        return;
                    }
                    reloaded.loadWithComponents(saved, reg);
                    helper.assertTrue(reloaded.queued() == 1, "the queued job survives the reload");
                    reloaded.media().setStackInSlot(0, new ItemStack(ComputingModule.USB_FLASH_DRIVE.get()));
                })
                .thenExecuteAfter(120, () -> {
                    if (!(helper.getBlockEntity(POS) instanceof PatternEncoderBlockEntity reloaded)) {
                        helper.fail("no reloaded encoder");
                        return;
                    }
                    helper.assertTrue(reloaded.completed() == 1 && CraftFiles.count(reloaded.mediaStack()) == 1,
                            "the waiting job runs once a medium goes in; completed=" + reloaded.completed());
                })
                .thenSucceed();
    }

    private static PatternEncoderBlockEntity place(final GameTestHelper helper, final BlockPos pos, final Block block) {
        helper.setBlock(pos, block);
        if (helper.getBlockEntity(pos) instanceof PatternEncoderBlockEntity be) {
            return be;
        }
        helper.fail("no Pattern Encoder at " + pos);
        throw new IllegalStateException("unreachable");
    }
}
