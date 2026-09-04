/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.testkit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The numbers a benchmark measured, written as JSON next to the project so runs can be compared over
 * time. The first run of a benchmark becomes its baseline; later runs list the timed metrics that got
 * slower than that baseline by more than a factor. Delete the baseline file to accept a new normal.
 *
 * <p>Files land in {@code local/bench/} under the project root (found by walking up from the run
 * directory to {@code settings.gradle}), which is not versioned: the numbers are one machine's.
 */
public final class BenchReport {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).withZone(ZoneOffset.UTC);

    private final String name;
    private final Map<String, Object> values = new LinkedHashMap<>();

    public BenchReport(final String name) {
        this.name = name;
        values.put("benchmark", name);
        values.put("recorded", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
    }

    public BenchReport put(final String key, final long value) {
        values.put(key, value);
        return this;
    }

    public BenchReport put(final String key, final double value) {
        values.put(key, Math.round(value * 1000.0) / 1000.0);
        return this;
    }

    public BenchReport put(final String key, final String value) {
        values.put(key, value);
        return this;
    }

    public double get(final String key) {
        final Object value = values.get(key);
        return value instanceof Number number ? number.doubleValue() : Double.NaN;
    }

    /** Writes this run; the first run of a benchmark is also saved as its baseline. Returns the run's file. */
    public Path write() throws IOException {
        final Path dir = directory();
        Files.createDirectories(dir);
        final Path run = dir.resolve(name + "-" + STAMP.format(Instant.now()) + ".json");
        final String json = GSON.toJson(values);
        Files.writeString(run, json);
        final Path baseline = baselinePath();
        if (!Files.exists(baseline)) {
            Files.writeString(baseline, json);
            LOGGER.info("[bench] {} recorded as the baseline: {}", name, baseline);
        }
        LOGGER.info("[bench] {} -> {}", name, run);
        LOGGER.info("[bench] {}", summary());
        return run;
    }

    /**
     * The metric that says how fast the machine itself is running today: raising the base is thousands of
     * block writes and no mod tick logic at all, so if it is slower than the baseline, everything measured
     * in the same run is slower for the same reason.
     */
    private static final String CONTROL_METRIC = "build_ms";
    /** How far the control is allowed to stretch the threshold, so a real regression can still be seen. */
    private static final double MAX_MACHINE_FACTOR = 3.0;

    /**
     * The timed metrics (keys ending in {@code _ms}) slower than the baseline by more than {@code factor},
     * ignoring anything under {@code floorMs} where timer noise dominates. Empty without a baseline.
     *
     * <p>The threshold is scaled by how much slower the {@linkplain #CONTROL_METRIC control} ran in this
     * same run. A benchmark taken on an idle machine and re-run while the desktop is busy reports every
     * number inflated together; without the control that reads as a regression in code that did not
     * change, and the gate cries wolf until nobody believes it.
     */
    public List<String> regressions(final double factor, final double floorMs) throws IOException {
        final Path baseline = baselinePath();
        if (!Files.exists(baseline)) {
            return List.of();
        }
        final JsonElement parsed = JsonParser.parseString(Files.readString(baseline));
        if (!parsed.isJsonObject()) {
            return List.of();
        }
        final JsonObject old = parsed.getAsJsonObject();
        final double machine = machineFactor(old);
        final List<String> slower = new ArrayList<>();
        for (final Map.Entry<String, Object> entry : values.entrySet()) {
            if (!entry.getKey().endsWith("_ms") || !(entry.getValue() instanceof Number now)) {
                continue;
            }
            final JsonElement before = old.get(entry.getKey());
            if (before == null || !before.isJsonPrimitive() || !before.getAsJsonPrimitive().isNumber()) {
                continue;
            }
            final double was = before.getAsDouble();
            if (now.doubleValue() > floorMs && now.doubleValue() > was * factor * machine) {
                slower.add(String.format(Locale.ROOT, "%s: %.3f -> %.3f ms%s", entry.getKey(), was,
                        now.doubleValue(),
                        machine > 1.0 ? String.format(Locale.ROOT, " (machine x%.2f)", machine) : ""));
            }
        }
        return slower;
    }

    /** How much slower this machine is running than when the baseline was taken; never below 1. */
    private double machineFactor(final JsonObject baseline) {
        final JsonElement before = baseline.get(CONTROL_METRIC);
        final Object now = values.get(CONTROL_METRIC);
        if (before == null || !before.isJsonPrimitive() || !before.getAsJsonPrimitive().isNumber()
                || !(now instanceof Number current) || before.getAsDouble() <= 0.0) {
            return 1.0;
        }
        return Math.max(1.0, Math.min(MAX_MACHINE_FACTOR, current.doubleValue() / before.getAsDouble()));
    }

    /** One line of every metric, for the log. */
    public String summary() {
        final StringBuilder out = new StringBuilder();
        for (final Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getKey().equals("benchmark") || entry.getKey().equals("recorded")) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(" | ");
            }
            out.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return out.toString();
    }

    private Path baselinePath() {
        return directory().resolve(name + "-baseline.json");
    }

    /** {@code local/bench} under the project when run from one of its run directories, else beside the run. */
    static Path directory() {
        Path probe = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && probe != null; i++) {
            if (Files.exists(probe.resolve("settings.gradle"))) {
                return probe.resolve("local").resolve("bench");
            }
            probe = probe.getParent();
        }
        return Paths.get("").toAbsolutePath().resolve("jsc-bench");
    }
}
