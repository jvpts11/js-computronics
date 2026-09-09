/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os.fs;

import dev.jstech.computers.os.Branding;
import dev.jstech.computers.os.IOsHost;
import dev.jstech.computers.os.OsDef;
import dev.jstech.computers.os.OsRegistry;
import dev.jstech.computers.os.Platform;
import dev.jstech.computers.os.ProgramSpec;
import dev.jstech.computers.os.ProgramVersions;
import dev.jstech.computers.os.SoftwareHouse;
import dev.jstech.computers.program.ComputerConsoleState;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * What the system folder and the program folders hold: the system's own files, and one folder per
 * program installed on the machine.
 *
 * <p>Nothing here is stored. A program's folder is generated from the fact that it is installed, the
 * way an install disc's files are generated from what it installs, so it appears when the program is
 * set up and goes when it is removed, and nobody can delete the kernel. On Frames the layout is the
 * one everybody knows: the system under {@code Frames}, programs under {@code Program Files}, and
 * programs of an older generation than the system under {@code Program Files (x86)}. On Linux it is
 * {@code /usr/bin} and {@code /usr/share}.
 */
public final class ProgramFilesProjection {

    public static final String PROGRAM_FILES = "Program Files";
    public static final String PROGRAM_FILES_X86 = "Program Files (x86)";
    public static final String SYSTEM = SystemLayout.SYSTEM_DIR + "/System";
    public static final String FONTS = SystemLayout.SYSTEM_DIR + "/Fonts";
    public static final String HELP = SystemLayout.SYSTEM_DIR + "/Help";

    private ProgramFilesProjection() {
    }

    /** Every projected folder and file on {@code host}'s system disk, folders before what is in them. */
    public static List<InstallerLayout.Entry> all(final IOsHost host) {
        final OsDef os = host.installedOs();
        if (os == null) {
            return List.of();
        }
        final List<InstallerLayout.Entry> out = new ArrayList<>();
        if (os.platform() == Platform.FRAMES) {
            frames(host, os, out);
        } else if (os.platform() == Platform.LINUX) {
            linux(host, os, out);
        }
        return out;
    }

    private static void frames(final IOsHost host, final OsDef os, final List<InstallerLayout.Entry> out) {
        out.add(dir(SYSTEM));
        out.add(dir(FONTS));
        out.add(dir(HELP));
        out.add(file(SYSTEM + "/kernel.sys", FileType.SYS));
        out.add(file(SYSTEM + "/frames.ini", FileType.INI));
        out.add(file(SYSTEM + "/boot.log", FileType.LOG));
        out.add(file(FONTS + "/system.fon", FileType.FON));
        out.add(file(HELP + "/readme.txt", FileType.TXT));
        // What every edition ships with lives with the system, the way notepad always has.
        for (final ProgramSpec spec : OsRegistry.programs()) {
            if (spec.preinstalled() && spec.platforms().contains(Platform.FRAMES)) {
                out.add(file(SYSTEM + "/" + spec.commandName() + ".exe", FileType.EXE));
            }
        }
        for (final ProgramSpec spec : installed(host)) {
            /*
             * A program written for an older generation than the system goes where a system of the
             * day put programs made for the one before it.
             */
            final boolean older = spec.era() != null && os.minEra() != null
                    && spec.era().ordinal() < os.minEra().ordinal();
            final String folder = (older ? PROGRAM_FILES_X86 : PROGRAM_FILES) + "/" + spec.displayName();
            out.add(dir(folder));
            out.add(file(folder + "/" + spec.commandName() + ".exe", FileType.EXE));
            out.add(file(folder + "/readme.txt", FileType.TXT));
            out.add(file(folder + "/uninstall.exe", FileType.EXE));
        }
    }

    private static void linux(final IOsHost host, final OsDef os, final List<InstallerLayout.Entry> out) {
        out.add(file("etc/os-release", FileType.CFG));
        out.add(dir("usr/share"));
        for (final ProgramSpec spec : installed(host)) {
            out.add(file("usr/bin/" + spec.commandName(), FileType.BIN));
            out.add(dir("usr/share/" + spec.commandName()));
            out.add(file("usr/share/" + spec.commandName() + "/readme", FileType.TXT));
        }
    }

    /** The programs installed on the machine, in the order they were installed, that the registry knows. */
    private static List<ProgramSpec> installed(final IOsHost host) {
        final ComputerConsoleState console = host.console();
        final List<ProgramSpec> out = new ArrayList<>();
        if (console == null) {
            return out;
        }
        final Set<String> ids = new LinkedHashSet<>(console.installed());
        final ResourceLocation desktop = host.installedDesktopId();
        if (desktop != null) {
            ids.add(desktop.toString());
        }
        for (final String id : ids) {
            final ResourceLocation key = ResourceLocation.tryParse(id);
            final ProgramSpec spec = key == null ? null : OsRegistry.getProgram(key);
            if (spec != null) {
                out.add(spec);
            }
        }
        return out;
    }

    /** The projected folders and files directly inside {@code dir}, folders first. */
    public static List<InstallerLayout.Entry> list(final IOsHost host, final String dir) {
        final String prefix = dir.isEmpty() ? "" : dir + "/";
        final List<InstallerLayout.Entry> out = new ArrayList<>();
        for (final InstallerLayout.Entry entry : all(host)) {
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

    /** Whether {@code path} is a projected folder, or has projected things inside it. */
    public static boolean isDir(final IOsHost host, final String path) {
        if (path.isEmpty()) {
            return false;
        }
        for (final InstallerLayout.Entry entry : all(host)) {
            if (entry.directory() && entry.path().equals(path) || entry.path().startsWith(path + "/")) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code path} is a projected file. */
    public static boolean isFile(final IOsHost host, final String path) {
        for (final InstallerLayout.Entry entry : all(host)) {
            if (!entry.directory() && entry.path().equals(path)) {
                return true;
            }
        }
        return false;
    }

    /** The text of a projected file, or empty for one that is not text or not there. */
    public static Optional<String> text(final IOsHost host, final String path) {
        final OsDef os = host.installedOs();
        if (os == null || !isFile(host, path)) {
            return Optional.empty();
        }
        if (path.equals(SYSTEM + "/frames.ini")) {
            return Optional.of("[system]\nedition=" + os.displayName() + "\nversion=" + ProgramVersions.of(os.id())
                    + "\nyear=" + Branding.osYear(os.displayName(), os.minEra()) + "\n\n[boot]\nkernel=kernel.sys\n"
                    + "shell=" + shellOf(os) + "\n");
        }
        if (path.equals(SYSTEM + "/boot.log")) {
            return Optional.of("POST ok\nkernel.sys loaded\n" + os.displayName() + " started\n"
                    + "desktop up\n");
        }
        if (path.equals(HELP + "/readme.txt")) {
            return Optional.of(os.displayName() + "\n(c) " + Branding.osYear(os.displayName(), os.minEra())
                    + " Midsoft\n\nThis folder holds the system. Do not move or delete what is in it.\n"
                    + "Programs you install go under Program Files; your own files under Users.\n");
        }
        if (path.equals("etc/os-release")) {
            return Optional.of("NAME=\"" + os.displayName() + "\"\nVERSION=\"" + ProgramVersions.of(os.id()) + "\"\n"
                    + "ID=" + os.id().getPath() + "\nPRETTY_NAME=\"" + os.displayName() + " "
                    + ProgramVersions.of(os.id()) + "\"\n");
        }
        for (final ProgramSpec spec : installed(host)) {
            final String frames = "/" + spec.displayName() + "/readme.txt";
            if (path.endsWith(frames) && (path.startsWith(PROGRAM_FILES) || path.startsWith(PROGRAM_FILES_X86))
                    || path.equals("usr/share/" + spec.commandName() + "/readme")) {
                return Optional.of(readme(spec));
            }
        }
        return Optional.empty();
    }

    private static String shellOf(final OsDef os) {
        return os.id().getPath().equals("frames_95") ? "explorer.exe" : "frames.exe";
    }

    private static String readme(final ProgramSpec spec) {
        final String key = "program.jsc." + spec.id().getPath() + ".desc";
        final String description = Component.translatable(key).getString().replace(key, "");
        return spec.displayName() + " " + ProgramVersions.of(spec.id()) + "\n"
                + spec.houseOr(SoftwareHouse.MIDSOFT).name() + "\n\n"
                + (description.isBlank() ? "" : description + "\n\n")
                + "Installed on this computer. To remove it, use This PC or the prompt's uninstall.\n";
    }

    private static InstallerLayout.Entry dir(final String path) {
        return new InstallerLayout.Entry(path, FileType.TXT, true);
    }

    private static InstallerLayout.Entry file(final String path, final FileType type) {
        return new InstallerLayout.Entry(path, type, false);
    }
}
