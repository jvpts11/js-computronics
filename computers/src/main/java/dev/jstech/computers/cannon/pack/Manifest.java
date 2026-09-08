/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon.pack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What a package says about itself.
 *
 * <p>It is plain text with one thing per line, so a player can write one by hand, read one someone else
 * wrote, and see in a diff what changed between two versions. Nothing here is compiled or encoded: a
 * package that cannot be read by eye is a package nobody can be sure of before installing it.
 */
public record Manifest(String name, String version, String house, String entry, String icon,
                       int minRamMb, List<String> files, String about) {

    /** The name of the manifest a project keeps beside its source. */
    public static final String FILE = "package.cpk";

    /** The icons a package may wear, since it cannot bring artwork of its own. */
    public static final List<String> ICONS =
            List.of("gear", "chart", "box", "bell", "wrench", "flask", "eye", "clock");

    /** What a package gets when it names no icon. */
    public static final String DEFAULT_ICON = "gear";

    public Manifest {
        files = List.copyOf(files);
    }

    /**
     * Whether this could be published as it stands, and what is wrong if not.
     *
     * <p>Every one of these is something the player can fix in the file in front of them, so the
     * complaint names the line they would fix.
     */
    public List<String> problems() {
        final List<String> found = new ArrayList<>();
        if (!isPlainName(this.name)) {
            found.add("name: must be lowercase letters, digits or dashes; got \"" + this.name + "\"");
        }
        if (!isVersion(this.version)) {
            found.add("version: must look like 1.0.0; got \"" + this.version + "\"");
        }
        if (this.entry.isBlank()) {
            found.add("entry: name the listing the package runs");
        } else if (!this.entry.toLowerCase(Locale.ROOT).endsWith(".asm")) {
            found.add("entry: must be a compiled listing; got \"" + this.entry + "\"");
        } else if (!this.files.contains(this.entry)) {
            found.add("files: must include the entry, \"" + this.entry + "\"");
        }
        if (!ICONS.contains(this.icon)) {
            found.add("icon: must be one of " + String.join(", ", ICONS) + "; got \"" + this.icon + "\"");
        }
        if (this.minRamMb < 1) {
            found.add("ram: a package needs at least a megabyte to run in");
        }
        if (this.files.isEmpty()) {
            found.add("files: a package with no files in it is not a package");
        }
        return found;
    }

    /** Writes it back out, in the order a person would want to read it. */
    public String write() {
        final StringBuilder text = new StringBuilder();
        text.append("name: ").append(this.name).append('\n');
        text.append("version: ").append(this.version).append('\n');
        text.append("house: ").append(this.house).append('\n');
        text.append("entry: ").append(this.entry).append('\n');
        text.append("icon: ").append(this.icon).append('\n');
        text.append("ram: ").append(this.minRamMb).append('\n');
        if (!this.about.isBlank()) {
            text.append("about: ").append(this.about).append('\n');
        }
        for (final String file : this.files) {
            text.append("file: ").append(file).append('\n');
        }
        return text.toString();
    }

    /**
     * Reads one.
     *
     * <p>A line it does not know is passed over rather than refused: a manifest written by a later
     * version should still install here, without its newer ideas, instead of failing to be read at all.
     */
    public static Manifest read(final String text) {
        final Map<String, String> said = new LinkedHashMap<>();
        final List<String> files = new ArrayList<>();
        for (final String raw : text.split("\n", -1)) {
            final String line = strip(raw);
            final int colon = line.indexOf(':');
            if (line.isEmpty() || colon < 0) {
                continue;
            }
            final String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            final String value = line.substring(colon + 1).trim();
            if ("file".equals(key)) {
                if (!value.isEmpty() && !files.contains(value)) {
                    files.add(value);
                }
            } else {
                said.putIfAbsent(key, value);
            }
        }
        return new Manifest(said.getOrDefault("name", ""), said.getOrDefault("version", ""),
                said.getOrDefault("house", ""), said.getOrDefault("entry", ""),
                said.getOrDefault("icon", DEFAULT_ICON), whole(said.get("ram")), files,
                said.getOrDefault("about", ""));
    }

    /** A manifest for a project that has just been started. */
    public static Manifest fresh(final String name, final String house) {
        final String entry = name + ".asm";
        return new Manifest(name, "1.0.0", house, entry, DEFAULT_ICON, 1, List.of(entry), "");
    }

    /** The same, with these files in it. */
    public Manifest with(final List<String> found) {
        return new Manifest(this.name, this.version, this.house, this.entry, this.icon, this.minRamMb,
                found, this.about);
    }

    /** What the package is called on the shelf: its name and what it is a build of. */
    public String label() {
        return this.name + " " + this.version;
    }

    private static String strip(final String line) {
        final int hash = line.indexOf('#');
        return (hash < 0 ? line : line.substring(0, hash)).trim();
    }

    private static int whole(final String value) {
        try {
            return value == null ? 1 : Integer.parseInt(value.trim());
        } catch (final NumberFormatException notANumber) {
            return 0;
        }
    }

    /** Lowercase letters, digits and dashes: what fits a file name on any of the filesystems here. */
    private static boolean isPlainName(final String name) {
        if (name == null || name.isBlank() || name.length() > 32) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '-') {
                return false;
            }
        }
        return true;
    }

    private static boolean isVersion(final String version) {
        if (version == null || version.isBlank()) {
            return false;
        }
        final String[] parts = version.split("\\.", -1);
        if (parts.length != 3) {
            return false;
        }
        for (final String part : parts) {
            if (part.isEmpty() || part.length() > 4) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (part.charAt(i) < '0' || part.charAt(i) > '9') {
                    return false;
                }
            }
        }
        return true;
    }
}
