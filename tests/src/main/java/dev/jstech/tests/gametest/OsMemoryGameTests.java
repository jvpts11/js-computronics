/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Tech Series.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.HardwareItems;
import dev.jstech.computronics.JsComputronics;
import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.hardware.DiskSize;
import dev.jstech.computronics.hardware.StorageTier;
import dev.jstech.computronics.os.HostScope;
import dev.jstech.computronics.os.OpenWindow;
import dev.jstech.computronics.os.OsDef;
import dev.jstech.computronics.os.OsRegistry;
import dev.jstech.computronics.os.Platform;
import dev.jstech.computronics.os.ProgramKind;
import dev.jstech.computronics.os.ProgramSpec;
import dev.jstech.computronics.os.RamLedger;
import dev.jstech.core.tier.HardwareEra;
import dev.jstech.tests.JsTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The memory ledger of a computer: the system's own share, the desktop package, the installed services and the
 * open windows, each weighed under the running system, and the server's trimming of a window layout to what
 * the RAM holds.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class OsMemoryGameTests {

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;

    private OsMemoryGameTests() {
    }

    @GameTest(template = ARENA)
    public static void ramLedger_holdsTheSystemTheServicesAndTheWindows(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = placeLegacyMainframe(helper, new BlockPos(2, 2, 2), id("frames_xp"));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    mainframe.setNeedsPost(false);
                    helper.assertTrue(mainframe.ramTotalMb() == 2048,
                            "one DDR2-2048 module is 2048 MB; got " + mainframe.ramTotalMb());
                    helper.assertTrue(mainframe.ramReservedMb() == 64,
                            "Frames XP holds 64 MB for itself; got " + mainframe.ramReservedMb());

                    mainframe.console().install("iqlengine");
                    helper.assertTrue(mainframe.ramReservedMb() == 88,
                            "an installed service holds its share on top of the system; got "
                                    + mainframe.ramReservedMb());

                    mainframe.setOpenWindows(List.of(
                            new OpenWindow("Files", 40, 30, 200, 140, false, false),
                            new OpenWindow("Editor", 60, 50, 180, 120, true, false)));
                    final RamLedger ledger = mainframe.ramLedger();
                    helper.assertTrue(ledger.usedMb(RamLedger.Kind.WINDOW) == 32,
                            "two bundled windows weigh 16 MB each under XP; got "
                                    + ledger.usedMb(RamLedger.Kind.WINDOW));
                    helper.assertTrue(ledger.usedMb() == 120, "the ledger adds up; got " + ledger.usedMb());
                    helper.assertTrue(mainframe.ramReservedMb() == 88,
                            "windows are not part of the reserve; got " + mainframe.ramReservedMb());
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void windowsWithinBudget_keepsTheOldestWindowsThatFit(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = placeLegacyMainframe(helper, new BlockPos(2, 2, 2), id("frames_xp"));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    mainframe.setNeedsPost(false);
                    // 2048 MB minus the system's 64 leaves 1984: fifteen 128 MB studios fit, the sixteenth does not.
                    final List<OpenWindow> asked = new ArrayList<>();
                    for (int i = 0; i < 20; i++) {
                        asked.add(new OpenWindow("Network Management Studio", 10 + i, 10, 200, 140, false, false));
                    }
                    final List<OpenWindow> kept = mainframe.windowsWithinBudget(asked);
                    helper.assertTrue(kept.size() == 15, "fifteen studios fit in 1984 MB; kept " + kept.size());
                    helper.assertTrue(kept.equals(asked.subList(0, 15)), "the oldest windows are the ones kept");

                    mainframe.setOpenWindows(kept);
                    helper.assertTrue(mainframe.ramLedger().usedMb() == 64 + 15 * 128,
                            "the kept layout fills the RAM; got " + mainframe.ramLedger().usedMb());
                    helper.assertTrue(mainframe.windowsWithinBudget(asked.subList(0, 3)).size() == 3,
                            "a layout that fits comes back whole");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void ramMbOn_weighsBundledProgramsBySystemAndTheRestByEra(final GameTestHelper helper) {
        final ProgramSpec files = OsRegistry.getProgram(id("files"));
        final OsDef frames95 = OsRegistry.getOs(id("frames_95"));
        final OsDef framesXp = OsRegistry.getOs(id("frames_xp"));
        final OsDef frames11 = OsRegistry.getOs(id("frames_11"));
        helper.assertTrue(files != null && frames95 != null && framesXp != null && frames11 != null,
                "the built-in programs and systems are registered");
        helper.assertTrue(files.ramMbOn(frames95) == 4, "Files is 4 MB on Frames 95; got " + files.ramMbOn(frames95));
        helper.assertTrue(files.ramMbOn(framesXp) == 16, "Files is 16 MB on Frames XP; got " + files.ramMbOn(framesXp));
        helper.assertTrue(files.ramMbOn(frames11) == 192, "Files is 192 MB on Frames 11; got " + files.ramMbOn(frames11));

        final ProgramSpec nms = OsRegistry.getProgram(id("nms"));
        helper.assertTrue(nms != null && nms.ramMbOn(framesXp) == 128,
                "an installable weighs what it declared wherever it runs");

        final ProgramSpec unstated = ProgramSpec.of(id("addon_tool"), "tool", "Addon Tool", false,
                        Set.of(Platform.FRAMES), 8, ProgramKind.APP, 0, HostScope.ANY)
                .withEra(HardwareEra.STANDARD);
        helper.assertTrue(unstated.ramMbOn(framesXp) == 96,
                "an installable that said nothing weighs by its generation; got " + unstated.ramMbOn(framesXp));
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void ramLedger_countsALinuxDesktopPackageOnTopOfTheSystem(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = placeLegacyMainframe(helper, new BlockPos(2, 2, 2), id("ubuntu"));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    mainframe.setNeedsPost(false);
                    helper.assertTrue(mainframe.ramReservedMb() == 48,
                            "Ubuntu at the TTY holds 48 MB; got " + mainframe.ramReservedMb());
                    mainframe.console().install("kde_plasma");
                    mainframe.setBootedDesktopId(id("kde_plasma"));
                    final RamLedger ledger = mainframe.ramLedger();
                    helper.assertTrue(ledger.usedMb(RamLedger.Kind.DESKTOP) == 224,
                            "Plasma holds 224 MB once booted; got " + ledger.usedMb(RamLedger.Kind.DESKTOP));
                    helper.assertTrue(mainframe.ramReservedMb() == 272,
                            "the desktop weighs on top of the system; got " + mainframe.ramReservedMb());
                })
                .thenSucceed();
    }

    private static ResourceLocation id(final String path) {
        return ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, path);
    }

    /** A Legacy Mainframe with one 2 GB DDR2 module, powered on with {@code osId} installed. */
    private static MainframeBlockEntity placeLegacyMainframe(final GameTestHelper helper, final BlockPos pos,
                                                            final ResourceLocation osId) {
        helper.setBlock(pos, ComputingModule.LEGACY_MAINFRAME.get());
        if (!(helper.getBlockEntity(pos) instanceof MainframeBlockEntity mainframe)) {
            throw new IllegalStateException("no MainframeBlockEntity at " + pos);
        }
        final ItemStackHandler inv = mainframe.getInventory();
        inv.setStackInSlot(MainframeBlockEntity.MOTHERBOARD_SLOT, new ItemStack(HardwareItems.MOTHERBOARD_MTX_LEGACY.get()));
        inv.setStackInSlot(MainframeBlockEntity.CPU_SLOTS_START, new ItemStack(HardwareItems.CPU_VELOCION_DUAL_285.get()));
        inv.setStackInSlot(MainframeBlockEntity.RAM_SLOTS_START, new ItemStack(HardwareItems.RAM_DDR2_2048.get()));
        inv.setStackInSlot(MainframeBlockEntity.PSU_SLOT, new ItemStack(HardwareItems.PSU_500B.get()));
        inv.setStackInSlot(MainframeBlockEntity.DISK_SLOTS_START,
                new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)));
        mainframe.togglePower();
        if (!mainframe.installOs(osId)) {
            throw new IllegalStateException("failed to install " + osId + " on the Legacy Mainframe at " + pos);
        }
        return mainframe;
    }
}
