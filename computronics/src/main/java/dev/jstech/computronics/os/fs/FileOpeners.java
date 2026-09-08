/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os.fs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which programs can open a kind of file, and which of them does it by default.
 *
 * <p>A desktop that opens everything in the text editor is a desktop with one program in it. Saying
 * this out loud, once, is what lets a double-click reach the right program, what lets a player pick a
 * different one, and what lets the machine offer the kinds of file it can actually make.
 *
 * <p>What a machine really has is a separate question, answered by the caller: this says what could
 * open a file, and the caller keeps only what is installed. That way a machine with no editor on it
 * still says honestly that a source file is a thing an editor opens.
 */
public final class FileOpeners {

    /** The plain text editor every desktop ships, and the last resort for anything readable. */
    public static final String EDITOR = "editor";

    /**
     * The programs that can open each kind of file, best first.
     *
     * <p>Best means the one a player most likely wants: a source file opens in the editor written for
     * the language rather than in the one that treats it as text, though that one can still open it.
     */
    private static final Map<FileType, List<String>> BY_TYPE = new LinkedHashMap<>();

    static {
        final List<String> code = List.of("aural_studio_code", "aural_studio", "exposure", EDITOR);
        BY_TYPE.put(FileType.CAN, code);
        BY_TYPE.put(FileType.ASM, code);
        BY_TYPE.put(FileType.IQL, List.of("nms", EDITOR));
        BY_TYPE.put(FileType.CRAFT, List.of("crafting_manager"));
        BY_TYPE.put(FileType.TXT, List.of(EDITOR));
        BY_TYPE.put(FileType.CFG, List.of(EDITOR));
        BY_TYPE.put(FileType.CSV, List.of(EDITOR));
        BY_TYPE.put(FileType.CMD, List.of(EDITOR));
        BY_TYPE.put(FileType.LOG, List.of(EDITOR));
    }

    /**
     * The kinds of file a player can make from nothing, in the order a menu offers them.
     *
     * <p>Only the ones that mean anything empty: a pattern is written by the machine that encodes it and
     * a compiled program by the compiler, so neither belongs on a menu that creates a blank one.
     */
    private static final List<FileType> CREATABLE =
            List.of(FileType.TXT, FileType.CAN, FileType.IQL, FileType.CFG, FileType.CSV, FileType.CMD);

    private FileOpeners() {
    }

    /** The kinds of file a player can create, in the order to offer them. */
    public static List<FileType> creatable() {
        return CREATABLE;
    }

    /**
     * Every program that could open {@code path}, best first, whether or not the machine has it.
     *
     * <p>A kind nobody claims opens in nothing rather than falling back to the text editor. That is not
     * a gap: a {@code .dat} is a read-only view of what a drive is holding and a {@code .exe} is a
     * program, and handing either to an editor would offer to edit something that cannot be edited.
     */
    public static List<String> forPath(final String path) {
        final FileType type = typeOf(path);
        return type == null ? List.of() : BY_TYPE.getOrDefault(type, List.of());
    }

    /**
     * The programs that can open {@code path} on a machine that has {@code installed}, best first.
     *
     * <p>The plain editor is always there, so a readable file is never one a machine cannot open; a file
     * whose only opener is missing comes back empty, which is the honest answer and the one that lets a
     * caller say so instead of opening the wrong thing.
     */
    public static List<String> available(final String path, final List<String> installed) {
        final List<String> out = new ArrayList<>();
        for (final String program : forPath(path)) {
            if (program.equals(EDITOR) || installed.contains(program)) {
                out.add(program);
            }
        }
        return out;
    }

    /** The program that opens {@code path} by default on such a machine, or empty when none can. */
    public static String defaultFor(final String path, final List<String> installed) {
        final List<String> can = available(path, installed);
        return can.isEmpty() ? "" : can.getFirst();
    }

    /** The type of a path, or null when nothing claims its extension. */
    private static FileType typeOf(final String path) {
        final int dot = path == null ? -1 : path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) {
            return null;
        }
        return FileType.fromExtension(path.substring(dot + 1).toLowerCase(Locale.ROOT)).orElse(null);
    }
}
