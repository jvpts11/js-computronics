/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os.fs;

import dev.jstech.computers.os.media.MediaFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What an install medium shows when it is opened: a setup program, a readme, a manifest and the
 * payload files a disc of that era carried. Nothing here is stored. The tree is generated from the
 * medium's payload every time it is listed, the way {@link StorageProjection} generates a disk's
 * {@code .dat} files from its storage, so an installer stays a stamp on blank media, takes no room,
 * can never be copied off or damaged, and always describes the software as the registry has it now.
 *
 * <p>The dialect follows the medium: a floppy speaks 8.3 uppercase, a CD keeps the uppercase names and
 * adds an autorun and a cabinet, a DVD or a flash drive is lowercase with a {@code sources} folder, and
 * a Linux medium carries a kernel and an initrd. Pure, so the layouts are unit-tested; the binding
 * layer supplies the {@link Facts} from the program or system registry.
 */
public final class InstallerLayout {

    /**
     * Everything the projection says about the software, as plain text.
     *
     * @param name            the software's display name
     * @param packageId       what a package manager installs it by (the command name)
     * @param idPath          the registry id's path
     * @param system          an operating system (true) or a program (false)
     * @param service         a headless service, which changes the manifest's kind line
     * @param linux           whether the medium targets Linux, which picks the Linux dialect
     * @param year            the year on the banner
     * @param house           who publishes it
     * @param description     one line on what it does
     * @param needs           the requirement lines, one per line
     * @param platforms       the platforms it runs on, already joined for display
     * @param host            the computer it installs on, already worded for display
     * @param installCommands the package-manager commands, one per line
     */
    public record Facts(String name, String packageId, String idPath, boolean system, boolean service,
                        boolean linux, int year, String house, String description, List<String> needs,
                        String platforms, String host, List<String> installCommands) {
    }

    /** One projected file or folder. */
    public record Entry(String path, FileType type, boolean directory) {
    }

    private InstallerLayout() {
    }

    /** The projected tree for a medium of {@code format} carrying {@code facts}, folders before files. */
    public static List<Entry> entries(final MediaFormat format, final Facts facts) {
        final List<Entry> out = new ArrayList<>();
        if (facts.linux()) {
            out.add(dir("boot"));
            out.add(dir("pool"));
            out.add(file("install.sh", FileType.SH));
            out.add(file("README.txt", FileType.TXT));
            out.add(file(facts.idPath() + ".pkg", FileType.PKG));
            if (facts.system()) {
                out.add(file("boot/vmlinuz", FileType.BIN));
                out.add(file("boot/initrd.img", FileType.BIN));
            } else {
                out.add(file("pool/" + facts.idPath() + "-" + facts.year() + ".tar", FileType.BIN));
            }
            return out;
        }
        switch (format) {
            case FLOPPY -> {
                out.add(file("SETUP.EXE", FileType.EXE));
                out.add(file("README.TXT", FileType.TXT));
                out.add(file("LICENSE.TXT", FileType.TXT));
                out.add(file(dosName(facts.packageId()) + ".PKG", FileType.PKG));
                if (facts.system()) {
                    out.add(file(dosName(facts.idPath()) + ".SYS", FileType.BIN));
                    out.add(file("COMMAND.COM", FileType.BIN));
                }
            }
            case CD -> {
                out.add(dir("SUPPORT"));
                if (facts.system()) {
                    out.add(dir("I386"));
                }
                out.add(file("SETUP.EXE", FileType.EXE));
                out.add(file("AUTORUN.INF", FileType.INF));
                out.add(file("README.TXT", FileType.TXT));
                out.add(file("LICENSE.TXT", FileType.TXT));
                out.add(file(dosName(facts.packageId()) + ".PKG", FileType.PKG));
                out.add(file("DATA1.CAB", FileType.BIN));
            }
            default -> {
                out.add(dir("sources"));
                out.add(dir("support"));
                out.add(file("setup.exe", FileType.EXE));
                out.add(file("autorun.inf", FileType.INF));
                out.add(file("readme.txt", FileType.TXT));
                out.add(file("license.txt", FileType.TXT));
                out.add(file(facts.idPath() + ".pkg", FileType.PKG));
                out.add(file(facts.system() ? "sources/install.wim" : "sources/data1.cab", FileType.BIN));
            }
        }
        return out;
    }

    /** Whether {@code path} is the setup program on a medium: what a double-click runs. */
    public static boolean isSetup(final String path) {
        final String base = baseName(path).toLowerCase(Locale.ROOT);
        return base.equals("setup.exe") || base.equals("install.sh");
    }

    /** The text of a projected file, or empty for one that is not text (a cabinet, a kernel). */
    public static Optional<String> textOf(final MediaFormat format, final Facts facts, final String path) {
        final String base = baseName(path).toLowerCase(Locale.ROOT);
        if (base.equals("readme.txt")) {
            return Optional.of(readme(format, facts));
        }
        if (base.endsWith(".pkg")) {
            return Optional.of(manifest(format, facts));
        }
        if (base.equals("autorun.inf")) {
            return Optional.of(autorun(format, facts));
        }
        if (base.equals("license.txt")) {
            return Optional.of(license(facts));
        }
        if (isSetup(path)) {
            return Optional.of(setupNote(facts));
        }
        return Optional.empty();
    }

    /** The readme: what it is, what it needs, how to install it, and the package id for the shell. */
    public static String readme(final MediaFormat format, final Facts facts) {
        final StringBuilder sb = new StringBuilder();
        sb.append(facts.name()).append(" (").append(facts.year()).append(")\n");
        sb.append("(c) ").append(facts.year()).append(' ').append(facts.house()).append("\n\n");
        if (!facts.description().isBlank()) {
            sb.append(facts.description()).append("\n\n");
        }
        sb.append("Requirements:\n");
        for (final String need : facts.needs()) {
            sb.append("  ").append(need).append('\n');
        }
        sb.append("Runs on: ").append(facts.platforms()).append('\n');
        sb.append("Installs on: ").append(facts.host()).append("\n\n");
        sb.append("To install, run ").append(setupName(format, facts))
                .append(", or open This PC and press Install.\n");
        sb.append("Package id: ").append(facts.packageId()).append('\n');
        for (final String command : facts.installCommands()) {
            sb.append("  ").append(command).append('\n');
        }
        return sb.toString();
    }

    /** The manifest: one fact per line, aligned, the whole truth about the medium in one screen. */
    public static String manifest(final MediaFormat format, final Facts facts) {
        final StringBuilder sb = new StringBuilder();
        line(sb, "name", facts.name());
        line(sb, "package", facts.packageId());
        line(sb, "id", "jsc:" + facts.idPath());
        line(sb, "year", Integer.toString(facts.year()));
        line(sb, "publisher", facts.house());
        line(sb, "kind", facts.system() ? "system" : (facts.service() ? "service" : "application"));
        line(sb, "platforms", facts.platforms());
        boolean first = true;
        for (final String need : facts.needs()) {
            line(sb, first ? "needs" : "", need);
            first = false;
        }
        if (first) {
            line(sb, "needs", "none");
        }
        line(sb, "host", facts.host());
        line(sb, "install", setupName(format, facts));
        for (final String command : facts.installCommands()) {
            line(sb, "", command);
        }
        return sb.toString();
    }

    private static String autorun(final MediaFormat format, final Facts facts) {
        return "[autorun]\nopen=" + setupName(format, facts) + "\nlabel=" + facts.name() + "\n";
    }

    private static String license(final Facts facts) {
        return facts.name() + "\n(c) " + facts.year() + " " + facts.house()
                + "\n\nThis software is licensed, not sold. One copy per computer.\n";
    }

    private static String setupNote(final Facts facts) {
        return "Setup for " + facts.name() + ". Run it from Files to install, or use This PC.\n";
    }

    /** The setup program's name in the medium's dialect. */
    public static String setupName(final MediaFormat format, final Facts facts) {
        if (facts.linux()) {
            return "install.sh";
        }
        return format == MediaFormat.FLOPPY || format == MediaFormat.CD ? "SETUP.EXE" : "setup.exe";
    }

    /** An id as a DOS disc would spell it: the first eight characters, uppercase. */
    static String dosName(final String id) {
        final String clean = id.replaceAll("[^A-Za-z0-9]", "");
        return clean.substring(0, Math.min(8, clean.length())).toUpperCase(Locale.ROOT);
    }

    private static void line(final StringBuilder sb, final String key, final String value) {
        sb.append(String.format(Locale.ROOT, "%-12s%s%n", key, value));
    }

    private static Entry dir(final String path) {
        return new Entry(path, FileType.TXT, true);
    }

    private static Entry file(final String path, final FileType type) {
        return new Entry(path, type, false);
    }

    private static String baseName(final String path) {
        final int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
