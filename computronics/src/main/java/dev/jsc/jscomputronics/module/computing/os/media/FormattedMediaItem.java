/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os.media;

import dev.jsc.jscomputronics.module.computing.storage.ServerStorageContents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A {@link MediaItem} with a fixed physical {@link MediaFormat} (floppy, CD, DVD, USB). The format is
 * the item's identity (one item per format), not a component, so each medium has its own texture and
 * tooltip. The content it carries (OS installer / program installer / data snapshot) is still stored
 * in components and is orthogonal to the format.
 *
 * <p>{@code writable} distinguishes read-only pressed media (CD-ROM, DVD-ROM) from rewritable media
 * (floppy, CD-RW, DVD-RW, USB) for the future data-write flow.
 */
public class FormattedMediaItem extends MediaItem {

    private final MediaFormat format;
    private final boolean writable;

    public FormattedMediaItem(final Properties properties, final MediaFormat format, final boolean writable) {
        super(properties);
        this.format = format;
        this.writable = writable;
    }

    /** The fixed physical format of this medium. */
    public MediaFormat format() {
        return format;
    }

    /** Whether this medium can be written to (false for pressed ROM media). */
    public boolean writable() {
        return writable;
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        tooltip.add(Component.literal(format.name() + " · " + (writable ? "read/write" : "read-only"))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(format.capacityItems() + " item capacity")
                .withStyle(ChatFormatting.DARK_GRAY));

        // Files written on the medium (e.g. .craft recipes from the Pattern Encoder) take priority: a
        // medium carrying files reads as such, not as a blank installer. The installer/data lines only
        // show for a medium with no files of its own.
        final dev.jsc.jscomputronics.module.computing.os.fs.FilesystemContents fs = stack.getOrDefault(
                dev.jsc.jscomputronics.module.computing.ComputingModule.FILESYSTEM.get(),
                dev.jsc.jscomputronics.module.computing.os.fs.FilesystemContents.EMPTY);
        if (!fs.files().isEmpty()) {
            dev.jsc.jscomputronics.module.computing.os.fs.FilesystemTooltip.append(fs, tooltip);
            super.appendHoverText(stack, context, tooltip, flag);
            return;
        }

        final ResourceLocation payload = MediaItem.payload(stack);
        switch (MediaItem.kind(stack)) {
            case OS_INSTALL -> {
                final dev.jsc.jscomputronics.module.computing.os.OsDef os =
                        payload == null ? null : dev.jsc.jscomputronics.module.computing.os.OsRegistry.getOs(payload);
                if (payload != null) {
                    tooltip.add(Component.translatable("os.jsc." + payload.getPath())
                            .withStyle(ChatFormatting.AQUA)
                            .append(os == null ? Component.empty() : Component.literal("  "
                                    + os.house().name() + " · "
                                    + dev.jsc.jscomputronics.module.computing.os.Branding.osYear(os.displayName(), os.minEra()))
                                    .withStyle(ChatFormatting.GRAY)));
                    tooltip.add(Component.literal("Bootable installer" + (os == null ? "" : " · "
                            + dev.jsc.jscomputronics.module.computing.os.MinSpecTooltip.eraLabel(os.minEra()) + " era"))
                            .withStyle(ChatFormatting.GREEN));
                    tooltip.addAll(dev.jsc.jscomputronics.module.computing.os.MinSpecTooltip.osMinSpec(payload));
                    // The id a shell or a manifest names it by, so a stick on a shelf is enough to know it.
                    tooltip.add(Component.literal("Package: " + payload.getPath()).withStyle(ChatFormatting.GOLD));
                    tooltip.add(Component.literal(insertHint(format) + ", then install from the firmware or This PC.")
                            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                } else {
                    tooltip.add(Component.literal("blank · no files").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            case PROGRAM_INSTALL -> {
                if (payload != null) {
                    final dev.jsc.jscomputronics.module.computing.os.ProgramSpec spec =
                            dev.jsc.jscomputronics.module.computing.os.OsRegistry.getProgram(payload);
                    // Lead with the program's friendly, translated name, then its house and the year it was
                    // written. A disc of a bundled program has no shipper to lean on, so it says Midsoft.
                    tooltip.add(Component.translatable("program.jsc." + payload.getPath())
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                            .append(spec == null ? Component.empty() : Component.literal("  "
                                    + spec.houseOr(dev.jsc.jscomputronics.module.computing.os.SoftwareHouse.MIDSOFT).name()
                                    + " · " + dev.jsc.jscomputronics.module.computing.os.Branding.year(spec.era()))
                                    .withStyle(ChatFormatting.GRAY)));
                    tooltip.add(Component.literal(spec != null
                            && spec.kind() == dev.jsc.jscomputronics.module.computing.os.ProgramKind.SERVICE
                            ? "Service disc" : "Program disc").withStyle(ChatFormatting.YELLOW));
                    // What it actually does, so a disc is not just a name on a shelf.
                    tooltip.add(Component.translatable("program.jsc." + payload.getPath() + ".desc")
                            .withStyle(ChatFormatting.GRAY));
                    tooltip.addAll(dev.jsc.jscomputronics.module.computing.os.MinSpecTooltip.programMinSpec(payload));
                    if (spec != null) {
                        // The package id and the command that installs it: the only other place to learn
                        // either was the Mirror's listing on a Mainframe.
                        tooltip.add(Component.literal("Package: " + spec.commandName()).withStyle(ChatFormatting.GOLD));
                        tooltip.add(Component.literal(String.join(" · ", installCommands(spec)))
                                .withStyle(ChatFormatting.DARK_GRAY));
                    }
                    tooltip.add(Component.literal(insertHint(format) + ": run "
                            + (spec != null && spec.platforms().equals(java.util.Set.of(
                                    dev.jsc.jscomputronics.module.computing.os.Platform.LINUX))
                                    ? "install.sh" : (format == MediaFormat.FLOPPY || format == MediaFormat.CD
                                            ? "SETUP.EXE" : "setup.exe"))
                            + ", or Install from This PC.")
                            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                } else {
                    tooltip.add(Component.literal("blank · no files").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            case DATA -> {
                final ServerStorageContents data = MediaItem.data(stack);
                if (data.total() > 0) {
                    tooltip.add(Component.literal("Data medium · " + data.total() + " stored")
                            .withStyle(ChatFormatting.GREEN));
                } else {
                    tooltip.add(Component.literal("blank · no files").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }

    /** Where this medium goes, in the drive's own name: a stick is plugged, a disc is inserted. */
    private static String insertHint(final MediaFormat format) {
        return (format == MediaFormat.USB ? "Plug into a linked " : "Insert in a linked ")
                + InstallMedia.readerName(format);
    }

    /**
     * The package-manager commands that install {@code spec}, one per platform family it runs on: the
     * Frames manager first, then the Linux form. A program with no platform that has a manager gets none.
     */
    public static List<String> installCommands(final dev.jsc.jscomputronics.module.computing.os.ProgramSpec spec) {
        final List<String> commands = new java.util.ArrayList<>(2);
        if (spec.platforms().contains(dev.jsc.jscomputronics.module.computing.os.Platform.FRAMES)) {
            commands.add(command(dev.jsc.jscomputronics.module.computing.os.PackageManagerKind.PCKMGR, spec));
        }
        if (spec.platforms().contains(dev.jsc.jscomputronics.module.computing.os.Platform.LINUX)) {
            commands.add(command(dev.jsc.jscomputronics.module.computing.os.PackageManagerKind.APT, spec));
        }
        return commands;
    }

    private static String command(final dev.jsc.jscomputronics.module.computing.os.PackageManagerKind manager,
                                  final dev.jsc.jscomputronics.module.computing.os.ProgramSpec spec) {
        final String verb = manager.installVerb();
        return manager.command() + (verb.isEmpty() ? " " : " " + verb + " ") + spec.commandName();
    }
}
