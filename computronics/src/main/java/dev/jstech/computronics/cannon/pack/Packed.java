/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.pack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A built package: what it says about itself, and everything it is made of.
 *
 * <p>It is one piece of text, and deliberately so. A package travels to a Mirror and from there onto
 * other people's machines, and a player who is about to install one written by somebody else can open
 * it in the editor and read every line of it first. Nothing is compressed, nothing is encoded, and
 * there is nowhere for anything to hide.
 */
public record Packed(Manifest manifest, Map<String, String> files) {

    /** What the first line of a built package says. */
    public static final String HEAD = ".pkg 1";

    /** The line that starts each file inside it. */
    private static final String MARK = "--- ";

    /** The extension a built package is written under. */
    public static final String EXTENSION = ".cpk";

    public Packed {
        files = new LinkedHashMap<>(files);
    }

    /** What to call the file this is written to. */
    public String fileName() {
        return this.manifest.name() + "-" + this.manifest.version() + EXTENSION;
    }

    /** Writes the whole thing out. */
    public String write() {
        final StringBuilder text = new StringBuilder(HEAD).append('\n');
        text.append(this.manifest.write());
        for (final Map.Entry<String, String> file : this.files.entrySet()) {
            text.append(MARK).append(file.getKey()).append('\n');
            text.append(file.getValue());
            if (!file.getValue().endsWith("\n")) {
                text.append('\n');
            }
        }
        return text.toString();
    }

    /** Reads one back, or null when the text is not a package at all. */
    public static Packed read(final String text) {
        if (text == null || !text.startsWith(HEAD)) {
            return null;
        }
        final String[] lines = text.split("\n", -1);
        /*
         * A text ending in a newline splits with an empty piece after it. That piece is the end of the
         * last line, not a blank line of its own, and counting it would grow every package by one line
         * each time it went through a Mirror.
         */
        final int last = text.endsWith("\n") ? lines.length - 1 : lines.length;
        final StringBuilder head = new StringBuilder();
        final Map<String, String> files = new LinkedHashMap<>();
        String name = null;
        StringBuilder body = null;
        for (int i = 1; i < last; i++) {
            final String line = lines[i];
            if (line.startsWith(MARK)) {
                if (name != null) {
                    files.put(name, body.toString());
                }
                name = line.substring(MARK.length()).trim();
                body = new StringBuilder();
                continue;
            }
            if (name == null) {
                head.append(line).append('\n');
            } else {
                body.append(line).append('\n');
            }
        }
        if (name != null) {
            files.put(name, body.toString());
        }
        return new Packed(Manifest.read(head.toString()), files);
    }

    /**
     * What is wrong with it, if anything.
     *
     * <p>A package is checked when it is built and again when it is installed, because the two can be
     * far apart: it may have crossed a Mirror and a world save in between.
     */
    public List<String> problems() {
        final List<String> found = new ArrayList<>(this.manifest.problems());
        for (final String named : this.manifest.files()) {
            if (!this.files.containsKey(named)) {
                found.add("files: " + named + " is named but not in the package");
            }
        }
        for (final String held : this.files.keySet()) {
            if (!this.manifest.files().contains(held)) {
                found.add("files: " + held + " is in the package but not named");
            }
        }
        return found;
    }

    /** How much disk this takes, in the same characters the filesystem weighs. */
    public int size() {
        return this.write().length();
    }
}
