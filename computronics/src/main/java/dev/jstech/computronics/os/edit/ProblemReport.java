/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os.edit;

import dev.jstech.core.language.IProgrammingLanguage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * What is wrong with a whole folder of programs, rather than with the one that happens to be open.
 *
 * <p>Changing something a program shares breaks the programs that used it, and those are exactly the
 * ones nobody has open. So every file is compiled and the complaints are gathered into one list, in an
 * order somebody can work down: the file that is worst off first, and within a file, from the top.
 *
 * <p>Which language reads which file is decided outside; this is handed the readings and does the
 * arithmetic, so it can be exercised without a machine or a registry.
 */
public final class ProblemReport {

    /**
     * One complaint, and which file it is about.
     *
     * @param path      the whole path on the disk, which is what an editor opens when it is clicked
     * @param name      just the file name, which is what a row shows
     * @param complaint what the compiler said
     */
    public record Row(String path, String name, IProgrammingLanguage.Complaint complaint) {
    }

    private ProblemReport() {
    }

    /**
     * Every complaint from every file, worst file first.
     *
     * <p>Files are ordered by how many complaints they have, so the one that is most broken is the one
     * a person is looking at without scrolling; ties go by name, so the order never wobbles between two
     * readings of the same disk. Within a file the complaints keep the compiler's own order, which is
     * the order they were found in the text.
     */
    public static List<Row> of(final Map<String, List<IProgrammingLanguage.Complaint>> byFile) {
        final List<String> paths = new ArrayList<>(byFile.keySet());
        paths.sort(Comparator
                .comparingInt((String path) -> -byFile.get(path).size())
                .thenComparing(ProblemReport::nameOf)
                .thenComparing(path -> path));
        final List<Row> rows = new ArrayList<>();
        for (final String path : paths) {
            final String name = nameOf(path);
            for (final IProgrammingLanguage.Complaint complaint : byFile.get(path)) {
                rows.add(new Row(path, name, complaint));
            }
        }
        return rows;
    }

    /** How many of the files have anything wrong with them. */
    public static int brokenFiles(final Map<String, List<IProgrammingLanguage.Complaint>> byFile) {
        int count = 0;
        for (final List<IProgrammingLanguage.Complaint> complaints : byFile.values()) {
            if (!complaints.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /** The file name on its own, which is what a row is labelled with. */
    public static String nameOf(final String path) {
        final int slash = path.lastIndexOf('/');
        return slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : path;
    }
}
