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
import dev.jstech.computronics.os.OsRegistry;
import dev.jstech.computronics.os.ProgramSpec;
import dev.jstech.computronics.os.fs.InstallerLayout;
import dev.jstech.computronics.os.media.InstallMedia;
import dev.jstech.computronics.os.media.InstallerProjection;
import dev.jstech.computronics.os.media.MediaFormat;
import dev.jstech.computronics.os.media.MediaItem;
import dev.jstech.computronics.os.media.MediaKind;
import dev.jstech.computronics.os.media.MediaReaderBlock;
import dev.jstech.computronics.os.media.MediaReaderBlockEntity;
import dev.jstech.core.tier.HardwareEra;
import dev.jstech.tests.JsTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Install media follow the software's era, say what they install, and show a disc's worth of files
 * when opened. These tests pin the registry side of that in a real server: which medium each piece
 * of software ships on, what the item's tooltip says, what the projection lists and reads, and the
 * Dock Station that now holds the flash drive.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class InstallMediaGameTests {

    private static final String ARENA = "empty";
    private static final int SETTLE = 2;

    private InstallMediaGameTests() {
    }

    private static ResourceLocation rl(final String path) {
        return ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, path);
    }

    private static ItemStack installer(final Item medium, final MediaKind kind, final String payload) {
        final ItemStack stack = new ItemStack(medium);
        MediaItem.setKind(stack, kind);
        MediaItem.setPayload(stack, rl(payload));
        return stack;
    }

    private static List<String> tooltip(final ItemStack stack) {
        final List<Component> lines = new ArrayList<>();
        stack.getItem().appendHoverText(stack, Item.TooltipContext.EMPTY, lines, TooltipFlag.NORMAL);
        final List<String> out = new ArrayList<>(lines.size());
        for (final Component line : lines) {
            out.add(line.getString());
        }
        return out;
    }

    private static boolean anyContains(final List<String> lines, final String needle) {
        for (final String line : lines) {
            if (line.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    @GameTest(template = ARENA)
    public static void installMedia_everyProgramShipsOnItsErasMedium(final GameTestHelper helper) {
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ProgramSpec nms = OsRegistry.getProgram(rl("nms"));
                    final ProgramSpec mirror = OsRegistry.getProgram(rl("mirror"));
                    final ProgramSpec craftmgr = OsRegistry.getProgram(rl("crafting_manager"));
                    final ProgramSpec mines = OsRegistry.getProgram(rl("minesweeper"));
                    final ProgramSpec cache = OsRegistry.getProgram(rl("predictive_cache"));
                    helper.assertTrue(nms != null && mirror != null && craftmgr != null && mines != null && cache != null,
                            "the built-in programs are registered");
                    helper.assertTrue(nms.era() == HardwareEra.STANDARD
                                    && InstallMedia.forProgram(nms.era(), nms.kind()) == MediaFormat.DVD,
                            "a Standard application ships on a DVD");
                    helper.assertTrue(mirror.era() == HardwareEra.STANDARD
                                    && InstallMedia.forProgram(mirror.era(), mirror.kind()) == MediaFormat.USB,
                            "a Standard service ships on a flash drive");
                    helper.assertTrue(craftmgr.era() == HardwareEra.LEGACY
                                    && InstallMedia.forProgram(craftmgr.era(), craftmgr.kind()) == MediaFormat.CD,
                            "a Legacy application ships on a CD");
                    helper.assertTrue(mines.era() == HardwareEra.VINTAGE
                                    && InstallMedia.forProgram(mines.era(), mines.kind()) == MediaFormat.FLOPPY,
                            "a Vintage program ships on a floppy");
                    helper.assertTrue(InstallMedia.forProgram(cache.era(), cache.kind()) == MediaFormat.USB,
                            "a small server daemon is no longer a floppy because it is small");
                    helper.assertTrue(nms.minEra() == HardwareEra.VINTAGE,
                            "the era never gates where a program installs: minEra is untouched");
                    helper.assertTrue(InstallMedia.forSystem(OsRegistry.getOs(rl("frames_11")).minEra()) == MediaFormat.USB
                                    && InstallMedia.forSystem(OsRegistry.getOs(rl("frames_xp")).minEra()) == MediaFormat.CD
                                    && InstallMedia.forSystem(OsRegistry.getOs(rl("mc_dos")).minEra()) == MediaFormat.FLOPPY,
                            "a system ships on its era's medium: Frames 11 on a stick, XP on a CD, MC-DOS on a floppy");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void installMedia_tooltipNamesThePackageAndTheCommand(final GameTestHelper helper) {
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final List<String> program = tooltip(installer(ComputingModule.CD_ROM.get(),
                            MediaKind.PROGRAM_INSTALL, "crafting_manager"));
                    helper.assertTrue(anyContains(program, "Package: craftmgr"), "the package id is on the disc: " + program);
                    helper.assertTrue(anyContains(program, "pckmgr install craftmgr"),
                            "and the command that installs it: " + program);
                    helper.assertTrue(anyContains(program, "1998"), "and the year it was written: " + program);
                    helper.assertTrue(anyContains(program, "SETUP.EXE"), "a CD says to run SETUP.EXE: " + program);
                    final List<String> system = tooltip(installer(ComputingModule.USB_FLASH_DRIVE.get(),
                            MediaKind.OS_INSTALL, "frames_11"));
                    helper.assertTrue(anyContains(system, "Package: frames_11"), "a system names its id too: " + system);
                    helper.assertTrue(anyContains(system, "Dock Station"), "a stick says where it plugs in: " + system);
                    helper.assertTrue(anyContains(system, "2021"), "Frames 11 carries its own year: " + system);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void installMedia_projectionListsADiscAndReadsItsReadme(final GameTestHelper helper) {
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ItemStack disc = installer(ComputingModule.CD_ROM.get(), MediaKind.PROGRAM_INSTALL, "crafting_manager");
                    helper.assertTrue(InstallerProjection.applies(disc), "an installer gets a projection");
                    final List<String> root = new ArrayList<>();
                    for (final InstallerLayout.Entry e : InstallerProjection.list(disc, "")) {
                        root.add(e.path());
                    }
                    helper.assertTrue(root.contains("SETUP.EXE") && root.contains("README.TXT")
                                    && root.contains("CRAFTMGR.PKG") && root.contains("SUPPORT"),
                            "a CD lists setup, readme, manifest and its folders: " + root);
                    helper.assertTrue(InstallerProjection.list(disc, "SUPPORT").isEmpty(),
                            "a folder on the disc lists what is inside it, which here is nothing");
                    final String readme = InstallerProjection.text(disc, "README.TXT").orElse("");
                    helper.assertTrue(readme.contains("Package id: craftmgr") && readme.contains("Crafting Manager"),
                            "the readme is generated from the stamp: " + readme);
                    final String manifest = InstallerProjection.text(disc, "CRAFTMGR.PKG").orElse("");
                    helper.assertTrue(manifest.contains("package     craftmgr") && manifest.contains("kind        application"),
                            "the manifest states the facts: " + manifest);
                    helper.assertTrue(InstallerProjection.text(disc, "DATA1.CAB").isEmpty(),
                            "a cabinet has no text to read");
                    helper.assertTrue(InstallerProjection.isSetup(disc, "SETUP.EXE") && !InstallerProjection.isSetup(disc, "README.TXT"),
                            "only the setup program runs");
                    final ItemStack blank = new ItemStack(ComputingModule.CD_RW.get());
                    helper.assertTrue(!InstallerProjection.applies(blank) && InstallerProjection.list(blank, "").isEmpty(),
                            "a blank medium projects nothing");
                    final ItemStack linux = installer(ComputingModule.CD_ROM.get(), MediaKind.OS_INSTALL, "debian");
                    final List<String> linuxRoot = new ArrayList<>();
                    for (final InstallerLayout.Entry e : InstallerProjection.list(linux, "")) {
                        linuxRoot.add(e.path());
                    }
                    helper.assertTrue(linuxRoot.contains("install.sh") && linuxRoot.contains("boot"),
                            "a Linux disc carries its install script and a boot folder: " + linuxRoot);
                    final List<String> boot = new ArrayList<>();
                    for (final InstallerLayout.Entry e : InstallerProjection.list(linux, "boot")) {
                        boot.add(e.path());
                    }
                    helper.assertTrue(boot.contains("boot/vmlinuz"), "and the kernel inside it: " + boot);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void dockStation_holdsTheStickAndIsAHubNotACube(final GameTestHelper helper) {
        final BlockPos dockPos = new BlockPos(2, 2, 2);
        final BlockPos dvdPos = new BlockPos(4, 2, 2);
        helper.setBlock(dockPos, ComputingModule.DOCK_STATION.get());
        helper.setBlock(dvdPos, ComputingModule.DVD_DRIVE.get());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    if (!(helper.getBlockEntity(dockPos) instanceof MediaReaderBlockEntity dock)
                            || !(helper.getBlockEntity(dvdPos) instanceof MediaReaderBlockEntity dvd)) {
                        throw new IllegalStateException("a drive is missing its block entity");
                    }
                    final ItemStack stick = installer(ComputingModule.USB_FLASH_DRIVE.get(), MediaKind.OS_INSTALL, "frames_11");
                    helper.assertTrue(dock.acceptsMedia(stick), "the Dock Station takes a flash drive");
                    helper.assertTrue(!dvd.acceptsMedia(stick), "a DVD drive cannot read a flash drive: Frames 11 needs the dock");
                    final net.minecraft.world.phys.AABB hub = helper.getBlockState(dockPos)
                            .getShape(helper.getLevel(), helper.absolutePos(dockPos)).bounds();
                    helper.assertTrue(hub.maxY <= 0.5D && hub.getXsize() < 1.0D,
                            "the dock is a low hub on the desk, not a full block: " + hub);
                    final net.minecraft.world.phys.AABB drive = helper.getBlockState(dvdPos)
                            .getShape(helper.getLevel(), helper.absolutePos(dvdPos)).bounds();
                    helper.assertTrue(drive.maxY >= 1.0D, "the disc drives stay full blocks");
                    helper.assertTrue(dock.insertMedia(stick.copyWithCount(1)).isEmpty(), "the stick docks");
                })
                .thenExecuteAfter(SETTLE, () -> helper.assertTrue(
                        helper.getBlockState(dockPos).getValue(MediaReaderBlock.LOADED),
                        "a docked stick flips the block state that shows it standing in the port"))
                .thenSucceed();
    }
}
