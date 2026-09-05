/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.clienttest;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects the outcome of every client test in a shard and writes {@code report.txt}: one {@code PASS} /
 * {@code FAIL} line per test (with the failure message and the screenshots it took), then a summary line.
 * The same lines go to the log under the {@code [JSC-CT]} marker so a build log alone tells the story.
 */
public final class ClientTestReport {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The outcome of one test. */
    public record Result(String name, boolean passed, String message, List<String> screenshots, int ticks) {
    }

    private final List<Result> results = new ArrayList<>();

    public void record(final Result result) {
        results.add(result);
        LOGGER.info("[JSC-CT] {} {}{} ({} ticks)", result.passed() ? "PASS" : "FAIL", result.name(),
                result.passed() ? "" : ": " + result.message(), result.ticks());
    }

    public int failures() {
        return (int) results.stream().filter(r -> !r.passed()).count();
    }

    public int total() {
        return results.size();
    }

    /** Writes the report; a write failure is logged, never thrown, so the client still shuts down cleanly. */
    public void write(final Path file, final int shard, final int shards) {
        final List<String> lines = new ArrayList<>();
        lines.add("J's Computronics client tests - shard " + shard + "/" + shards);
        for (final Result r : results) {
            lines.add((r.passed() ? "PASS " : "FAIL ") + r.name()
                    + (r.passed() ? "" : ": " + r.message()) + " [" + r.ticks() + " ticks]");
            for (final String shot : r.screenshots()) {
                lines.add("    screenshot " + shot);
            }
        }
        final String summary = (failures() == 0 ? "ALL PASSED" : failures() + " FAILED")
                + " - " + total() + " tests";
        lines.add(summary);
        LOGGER.info("[JSC-CT] {}", summary);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            LOGGER.error("[JSC-CT] could not write the report to {}", file, e);
        }
    }
}
