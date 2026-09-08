/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computers.ComputingModule;
import dev.jstech.computers.JsComputers;
import dev.jstech.computers.os.Branding;
import dev.jstech.computers.os.OsRegistry;
import dev.jstech.computers.os.SoftwareHouse;
import dev.jstech.computers.os.media.InstallerProjection;
import dev.jstech.computers.os.media.MediaItem;
import dev.jstech.computers.os.media.MediaKind;
import dev.jstech.core.tier.HardwareEra;
import dev.jstech.tests.JsTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * Every system, desktop and program names the house that wrote it, the way every machine names its maker.
 * A bundled program is credited to whoever ships it, and a disc's README names the publisher.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class SoftwareHouseGameTests {

    private SoftwareHouseGameTests() {
    }

    private static final String ARENA = "empty";

    private static ResourceLocation rl(final String path) {
        return ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, path);
    }

    @GameTest(template = ARENA)
    public static void systemsAndDesktops_nameTheirMakers(final GameTestHelper helper) {
        final Map<String, SoftwareHouse> systems = Map.of(
                "mc_dos", SoftwareHouse.MIDSOFT, "mc_net", SoftwareHouse.NOUVELL,
                "frames_95", SoftwareHouse.MIDSOFT, "frames_11", SoftwareHouse.MIDSOFT,
                "ubuntu", SoftwareHouse.AXIOMATIC, "debian", SoftwareHouse.DEBIAN_CIRCLE,
                "fedora", SoftwareHouse.RED_CAP, "arch", SoftwareHouse.ARCH_COLLECTIVE,
                "gentoo", SoftwareHouse.GENTOO_FOUNDRY);
        systems.forEach((id, house) -> helper.assertTrue(OsRegistry.getOs(rl(id)).house().equals(house),
                id + " is " + house.name() + "'s; got " + OsRegistry.getOs(rl(id)).house().name()));
        final Map<String, SoftwareHouse> desktops = Map.of(
                "frames_xp", SoftwareHouse.MIDSOFT, "kde_plasma", SoftwareHouse.KDE_GUILD,
                "gnome", SoftwareHouse.GNOME_TRUST, "cinnamon", SoftwareHouse.SPEARMINT);
        desktops.forEach((id, house) -> helper.assertTrue(OsRegistry.getDesktop(rl(id)).house().equals(house),
                id + " is " + house.name() + "'s; got " + OsRegistry.getDesktop(rl(id)).house().name()));
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void programs_nameTheirMakersAndBundledOnesTheirShipper(final GameTestHelper helper) {
        final Map<String, SoftwareHouse> programs = Map.of(
                "nms", SoftwareHouse.MIDSOFT, "iqlengine", SoftwareHouse.MIDSOFT,
                "crafting_manager", SoftwareHouse.AUTODECK, "craft_planner", SoftwareHouse.AUTODECK,
                "automation_engine", SoftwareHouse.RED_CAP, "storage_insights", SoftwareHouse.VAULTIS,
                "predictive_cache", SoftwareHouse.VAULTIS, "network", SoftwareHouse.JSC,
                "mirror", SoftwareHouse.JSC, "screenfetch", SoftwareHouse.ARCH_COLLECTIVE);
        programs.forEach((id, house) -> helper.assertTrue(OsRegistry.getProgram(rl(id)).house().equals(house),
                id + " is " + house.name() + "'s; got " + OsRegistry.getProgram(rl(id)).house().name()));
        // A bundled program is credited to the desktop that ships it; an explicit house stays its own.
        final var files = OsRegistry.getProgram(rl("files"));
        helper.assertTrue(files.house().bundled(), "Files has no house of its own");
        helper.assertTrue(OsRegistry.getDesktop(rl("kde_plasma")).houseOf(files).equals(SoftwareHouse.KDE_GUILD),
                "Files on Plasma is the KDE Guild's");
        helper.assertTrue(OsRegistry.getDesktop(rl("frames_11")).houseOf(files).equals(SoftwareHouse.MIDSOFT),
                "Files on Frames is Midsoft's");
        helper.assertTrue(OsRegistry.getDesktop(rl("kde_plasma")).houseOf(OsRegistry.getProgram(rl("network")))
                        .equals(SoftwareHouse.JSC), "the Network Interactor stays JSC's on any desktop");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void bannersAndDiscs_nameThePublisher(final GameTestHelper helper) {
        helper.assertTrue(Branding.systemCopyright("Fedora", HardwareEra.STANDARD).contains("Red Cap"),
                "Fedora's copyright names Red Cap; got " + Branding.systemCopyright("Fedora", HardwareEra.STANDARD));
        helper.assertTrue(Branding.systemCopyright("MC-DOS", HardwareEra.VINTAGE).contains("Midsoft Corp."),
                "MC-DOS's copyright names Midsoft Corp.; got " + Branding.systemCopyright("MC-DOS", HardwareEra.VINTAGE));
        helper.assertTrue(Branding.houseOf("no such system").equals(SoftwareHouse.MIDSOFT),
                "an unknown system name falls back to Midsoft");
        final String fedora = readme(installer(ComputingModule.USB_FLASH_DRIVE.get(), MediaKind.OS_INSTALL, "fedora"));
        helper.assertTrue(fedora.contains("Red Cap"), "the Fedora installer's README names Red Cap: " + fedora);
        final String manager = readme(installer(ComputingModule.CD_ROM.get(), MediaKind.PROGRAM_INSTALL, "crafting_manager"));
        helper.assertTrue(manager.contains("Autodeck"), "the Crafting Manager disc's README names Autodeck: " + manager);
        helper.succeed();
    }

    private static ItemStack installer(final Item medium, final MediaKind kind, final String payload) {
        final ItemStack stack = new ItemStack(medium);
        MediaItem.setKind(stack, kind);
        MediaItem.setPayload(stack, rl(payload));
        return stack;
    }

    /** The README the disc projects, whichever case its layout spells the name in. */
    private static String readme(final ItemStack disc) {
        final Optional<String> upper = InstallerProjection.text(disc, "README.TXT");
        return upper.or(() -> InstallerProjection.text(disc, "README.txt")).orElse("(no readme)");
    }
}
