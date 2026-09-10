/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os.edit.project;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A project as the studio keeps it on the disk: what it is, what it is made of, and what it builds.
 *
 * <p>One line per fact, {@code key: value}, so a player can read it at the prompt and fix it in any
 * editor. A project of a library kind has no entry, since it compiles into the projects that reference
 * it rather than into a listing of its own.
 *
 * @param name       what the project is called, which is also its folder and its output
 * @param kind       the shape of program it makes
 * @param language   the language its sources are in, by registry id
 * @param sources    its source files, relative to the project folder
 * @param references the names of the library projects it compiles with
 * @param entry      the listing it builds, relative to the project folder, or empty for a library
 */
public record ProjectFile(String name, Kind kind, String language, List<String> sources,
                          List<String> references, String entry) {

    /** The shapes a project can have. */
    public enum Kind {
        /** A program with a Main that runs at the terminal and returns. */
        CONSOLE,
        /** A program that stays up: set up once, called every tick, told when it stops. */
        SCRIPT,
        /** Classes shared by other projects, with no entry of its own. */
        LIBRARY,
        /** A project with nothing in it yet. */
        EMPTY;

        /** The kind as the file writes it. */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The kind a file names, or {@link #EMPTY} for a word nobody knows. */
        public static Kind of(final String key) {
            for (final Kind kind : values()) {
                if (kind.key().equalsIgnoreCase(key == null ? "" : key.trim())) {
                    return kind;
                }
            }
            return EMPTY;
        }
    }

    /** The extension a project file carries. */
    public static final String EXTENSION = "canproj";

    public ProjectFile {
        sources = List.copyOf(sources);
        references = List.copyOf(references);
        entry = entry == null ? "" : entry;
    }

    /** The file name a project of that name keeps itself in. */
    public static String fileName(final String name) {
        return name + "." + EXTENSION;
    }

    /** Where a project's listing goes when it has one: the build folder, named after the project. */
    public static String defaultEntry(final String name) {
        return "build/" + name + ".asm";
    }

    /** Whether the project builds a listing of its own. */
    public boolean buildsAListing() {
        return this.kind != Kind.LIBRARY && !this.entry.isEmpty();
    }

    /** The project with one more source, or the same one when it is already there. */
    public ProjectFile withSource(final String source) {
        if (this.sources.contains(source)) {
            return this;
        }
        final List<String> more = new ArrayList<>(this.sources);
        more.add(source);
        return new ProjectFile(this.name, this.kind, this.language, more, this.references, this.entry);
    }

    /** The project without that source, or the same one when it was not there. */
    public ProjectFile withoutSource(final String source) {
        if (!this.sources.contains(source)) {
            return this;
        }
        final List<String> fewer = new ArrayList<>(this.sources);
        fewer.remove(source);
        return new ProjectFile(this.name, this.kind, this.language, fewer, this.references, this.entry);
    }

    /** The project with one more reference, or the same one when it is already there. */
    public ProjectFile withReference(final String reference) {
        if (this.references.contains(reference) || reference.equals(this.name)) {
            return this;
        }
        final List<String> more = new ArrayList<>(this.references);
        more.add(reference);
        return new ProjectFile(this.name, this.kind, this.language, this.sources, more, this.entry);
    }

    /** The file's text. */
    public String write() {
        final StringBuilder out = new StringBuilder();
        out.append("name: ").append(this.name).append('\n');
        out.append("kind: ").append(this.kind.key()).append('\n');
        out.append("language: ").append(this.language).append('\n');
        out.append("sources: ").append(String.join(", ", this.sources)).append('\n');
        out.append("references: ").append(String.join(", ", this.references)).append('\n');
        out.append("entry: ").append(this.entry).append('\n');
        return out.toString();
    }

    /**
     * Reads a file's text. A line that is not {@code key: value} is skipped, and a key that is not
     * there gets the plainest value, so a file edited by hand and half wrong still opens.
     */
    public static ProjectFile read(final String text) {
        String name = "";
        Kind kind = Kind.EMPTY;
        String language = "";
        List<String> sources = List.of();
        List<String> references = List.of();
        String entry = "";
        for (final String raw : (text == null ? "" : text).split("\n")) {
            final String line = raw.trim();
            final int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            final String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            final String value = line.substring(colon + 1).trim();
            switch (key) {
                case "name" -> name = value;
                case "kind" -> kind = Kind.of(value);
                case "language" -> language = value;
                case "sources" -> sources = list(value);
                case "references" -> references = list(value);
                case "entry" -> entry = value;
                default -> { }
            }
        }
        return new ProjectFile(name, kind, language, sources, references, entry);
    }

    private static List<String> list(final String value) {
        final List<String> out = new ArrayList<>();
        for (final String part : value.split(",")) {
            final String item = part.trim();
            if (!item.isEmpty()) {
                out.add(item);
            }
        }
        return out;
    }
}
