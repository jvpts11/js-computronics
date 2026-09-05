/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os.media;

import dev.jstech.computronics.os.Branding;
import dev.jstech.computronics.os.HostScope;
import dev.jstech.computronics.os.MinSpecTooltip;
import dev.jstech.computronics.os.OsDef;
import dev.jstech.computronics.os.OsRegistry;
import dev.jstech.computronics.os.Platform;
import dev.jstech.computronics.os.ProgramKind;
import dev.jstech.computronics.os.ProgramSpec;
import dev.jstech.computronics.os.fs.InstallerLayout;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Binds {@link InstallerLayout} to a medium in the world: reads what the medium installs from its
 * payload, gathers the facts about that software from the registries, and answers what a folder on
 * the medium lists and what a file on it says. Nothing is stored on the medium; every answer is
 * generated from the payload the moment it is asked for.
 */
public final class InstallerProjection {

    private InstallerProjection() {
    }

    /** Whether {@code medium} is an installer with a known payload, which is what gets a projection. */
    public static boolean applies(final ItemStack medium) {
        return facts(medium).isPresent();
    }

    /** The physical format of the medium, which decides the projection's dialect. */
    public static MediaFormat formatOf(final ItemStack medium) {
        return medium.getItem() instanceof FormattedMediaItem formatted ? formatted.format() : MediaFormat.CD;
    }

    /** The facts about the software on {@code medium}, or empty when it carries no installer. */
    public static Optional<InstallerLayout.Facts> facts(final ItemStack medium) {
        if (!(medium.getItem() instanceof MediaItem)) {
            return Optional.empty();
        }
        final ResourceLocation payload = MediaItem.payload(medium);
        if (payload == null) {
            return Optional.empty();
        }
        return switch (MediaItem.kind(medium)) {
            case OS_INSTALL -> Optional.ofNullable(OsRegistry.getOs(payload)).map(InstallerProjection::systemFacts);
            case PROGRAM_INSTALL -> Optional.ofNullable(OsRegistry.getProgram(payload)).map(InstallerProjection::programFacts);
            case DATA -> Optional.empty();
        };
    }

    /**
     * The projected folders and files directly inside {@code subDir} on {@code medium}, folders first.
     * Paths are relative to the medium's root, the way the real listing reports them.
     */
    public static List<InstallerLayout.Entry> list(final ItemStack medium, final String subDir) {
        final Optional<InstallerLayout.Facts> facts = facts(medium);
        if (facts.isEmpty()) {
            return List.of();
        }
        final String prefix = subDir.isEmpty() ? "" : subDir + "/";
        final List<InstallerLayout.Entry> out = new ArrayList<>();
        for (final InstallerLayout.Entry entry : InstallerLayout.entries(formatOf(medium), facts.get())) {
            if (!entry.path().startsWith(prefix)) {
                continue;
            }
            final String rest = entry.path().substring(prefix.length());
            if (rest.isEmpty() || rest.contains("/")) {
                continue;
            }
            out.add(entry);
        }
        return out;
    }

    /** The text of a projected file on {@code medium}, or empty for a binary or an unknown path. */
    public static Optional<String> text(final ItemStack medium, final String subPath) {
        return facts(medium).flatMap(f -> InstallerLayout.textOf(formatOf(medium), f, subPath));
    }

    /** Whether {@code subPath} on {@code medium} is its setup program. */
    public static boolean isSetup(final ItemStack medium, final String subPath) {
        return applies(medium) && InstallerLayout.isSetup(subPath);
    }

    private static InstallerLayout.Facts systemFacts(final OsDef os) {
        final boolean linux = os.platform() == Platform.LINUX;
        return new InstallerLayout.Facts(
                os.displayName(), os.id().getPath(), os.id().getPath(), true, false, linux,
                Branding.osYear(os.displayName(), os.minEra()), os.house().name(),
                Component.translatable("os.jsc." + os.id().getPath() + ".desc").getString()
                        .replace("os.jsc." + os.id().getPath() + ".desc", ""),
                plain(MinSpecTooltip.osMinSpec(os.id())), os.platform().label(), "any computer",
                List.of());
    }

    private static InstallerLayout.Facts programFacts(final ProgramSpec spec) {
        final boolean linux = spec.platforms().equals(java.util.Set.of(Platform.LINUX));
        return new InstallerLayout.Facts(
                spec.displayName(), spec.commandName(), spec.id().getPath(), false,
                spec.kind() == ProgramKind.SERVICE, linux, Branding.year(spec.era()),
                // A disc of a bundled program has no shipper to be credited to, so it says Midsoft.
                spec.houseOr(dev.jstech.computronics.os.SoftwareHouse.MIDSOFT).name(),
                Component.translatable("program.jsc." + spec.id().getPath() + ".desc").getString()
                        .replace("program.jsc." + spec.id().getPath() + ".desc", ""),
                plain(MinSpecTooltip.programMinSpec(spec.id())),
                MinSpecTooltip.platformsLabel(spec.platforms()), hostLabel(spec.hostScope()),
                FormattedMediaItem.installCommands(spec));
    }

    private static String hostLabel(final HostScope scope) {
        return switch (scope) {
            case MAINFRAME -> "the Mainframe";
            case SERVER -> "a server in a rack";
            case CRAFTING_COMPUTER -> "a Crafting Computer";
            case CLUSTER_MANAGEMENT_COMPUTER -> "a Cluster Management Computer";
            default -> "any computer";
        };
    }

    private static List<String> plain(final List<Component> lines) {
        final List<String> out = new ArrayList<>(lines.size());
        for (final Component line : lines) {
            final String text = line.getString().trim();
            if (!text.isEmpty()) {
                out.add(text);
            }
        }
        return out;
    }
}
