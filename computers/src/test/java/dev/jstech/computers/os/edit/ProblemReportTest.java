/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.core.language.IProgrammingLanguage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProblemReportTest {

    private static IProgrammingLanguage.Complaint at(final int line, final String message) {
        return new IProgrammingLanguage.Complaint("f", line, 1, "CN0001", message);
    }

    private static List<String> names(final List<ProblemReport.Row> rows) {
        return rows.stream().map(ProblemReport.Row::name).toList();
    }

    @Test
    void of_returnsNothingForAFolderWithNothingWrong() {
        final Map<String, List<IProgrammingLanguage.Complaint>> byFile = new LinkedHashMap<>();
        byFile.put("progs/a.can", List.of());
        byFile.put("progs/b.can", List.of());
        assertTrue(ProblemReport.of(byFile).isEmpty());
    }

    @Test
    void of_putsTheWorstFileFirst() {
        final Map<String, List<IProgrammingLanguage.Complaint>> byFile = new LinkedHashMap<>();
        byFile.put("progs/one.can", List.of(at(4, "one thing")));
        byFile.put("progs/many.can", List.of(at(1, "a"), at(2, "b"), at(3, "c")));
        assertEquals(List.of("many.can", "many.can", "many.can", "one.can"),
                names(ProblemReport.of(byFile)));
    }

    @Test
    void of_keepsTheCompilersOwnOrderWithinAFile() {
        final Map<String, List<IProgrammingLanguage.Complaint>> byFile = new LinkedHashMap<>();
        byFile.put("progs/a.can", List.of(at(9, "later"), at(2, "earlier")));
        assertEquals(List.of(9, 2),
                ProblemReport.of(byFile).stream().map(r -> r.complaint().line()).toList());
    }

    @Test
    void of_breaksATieByNameSoTheOrderNeverWobbles() {
        final Map<String, List<IProgrammingLanguage.Complaint>> byFile = new LinkedHashMap<>();
        byFile.put("progs/zeta.can", List.of(at(1, "x")));
        byFile.put("progs/alpha.can", List.of(at(1, "x")));
        assertEquals(List.of("alpha.can", "zeta.can"), names(ProblemReport.of(byFile)));
    }

    @Test
    void of_carriesTheWholePathSoARowCanBeOpened() {
        final Map<String, List<IProgrammingLanguage.Complaint>> byFile = new LinkedHashMap<>();
        byFile.put("progs/deep/a.can", List.of(at(1, "x")));
        assertEquals("progs/deep/a.can", ProblemReport.of(byFile).get(0).path());
        assertEquals("a.can", ProblemReport.of(byFile).get(0).name());
    }

    @Test
    void of_leavesOutTheFilesThatCompiled() {
        final Map<String, List<IProgrammingLanguage.Complaint>> byFile = new LinkedHashMap<>();
        byFile.put("progs/good.can", List.of());
        byFile.put("progs/bad.can", List.of(at(1, "x")));
        assertEquals(List.of("bad.can"), names(ProblemReport.of(byFile)));
    }

    @Test
    void brokenFiles_countsFilesRatherThanComplaints() {
        final Map<String, List<IProgrammingLanguage.Complaint>> byFile = new LinkedHashMap<>();
        byFile.put("progs/a.can", List.of(at(1, "x"), at(2, "y"), at(3, "z")));
        byFile.put("progs/b.can", List.of(at(1, "x")));
        byFile.put("progs/c.can", List.of());
        assertEquals(2, ProblemReport.brokenFiles(byFile));
        assertEquals(4, ProblemReport.of(byFile).size());
    }

    @Test
    void brokenFiles_isZeroForAFolderThatCompiles() {
        final Map<String, List<IProgrammingLanguage.Complaint>> byFile = new LinkedHashMap<>();
        byFile.put("progs/a.can", List.of());
        assertEquals(0, ProblemReport.brokenFiles(byFile));
        assertEquals(0, ProblemReport.brokenFiles(new LinkedHashMap<>()));
    }

    @Test
    void nameOf_readsTheLastSegmentOfAPath() {
        assertEquals("a.can", ProblemReport.nameOf("progs/a.can"));
        assertEquals("a.can", ProblemReport.nameOf("a.can"));
        assertEquals("progs/", ProblemReport.nameOf("progs/"));
    }
}
