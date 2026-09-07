/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigValidatorTest {

    @Test
    void range_clampsBelowMin() {
        ConfigKeyRange<Long> range = new ConfigKeyRange<>(10L, 100L);
        assertEquals(10L, range.clamp(5L));
    }

    @Test
    void range_clampsAboveMax() {
        ConfigKeyRange<Long> range = new ConfigKeyRange<>(10L, 100L);
        assertEquals(100L, range.clamp(150L));
    }

    @Test
    void range_passesValueInside() {
        ConfigKeyRange<Long> range = new ConfigKeyRange<>(10L, 100L);
        assertEquals(50L, range.clamp(50L));
    }

    @Test
    void range_inclusiveBounds() {
        ConfigKeyRange<Long> range = new ConfigKeyRange<>(10L, 100L);
        assertTrue(range.contains(10L));
        assertTrue(range.contains(100L));
    }

    @Test
    void range_minGreaterThanMax_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConfigKeyRange<>(100L, 10L));
    }

    @Test
    void configKey_of_buildsNonRanged() {
        ConfigKey<Boolean> key = ConfigKey.of(
                List.of("disasters", "volcanoes_enabled"),
                Boolean.class,
                true);
        assertEquals("disasters.volcanoes_enabled", key.dottedPath());
        assertTrue(key.range().isEmpty());
    }

    @Test
    void configKey_ranged_buildsRangedNumeric() {
        ConfigKey<Long> key = ConfigKey.ranged(
                List.of("balance", "macerator", "fe_per_tick"),
                Long.class,
                100L,
                new ConfigKeyRange<>(10L, 1000L));
        assertEquals("balance.macerator.fe_per_tick", key.dottedPath());
        assertTrue(key.range().isPresent());
    }

    @Test
    void configKey_ranged_defaultOutsideRange_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfigKey.ranged(
                        List.of("k"),
                        Long.class,
                        50L,
                        new ConfigKeyRange<>(100L, 200L)));
    }

    @Test
    void configKey_emptyPath_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfigKey.of(List.of(), Long.class, 0L));
    }

    @Test
    void configKey_blankPathSegment_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfigKey.of(List.of("balance", "", "x"), Long.class, 0L));
    }

    @Test
    void validate_inRangeNumeric_returnsValid() {
        RecordingLogger log = new RecordingLogger();
        ConfigValidator validator = new ConfigValidator(log);
        ConfigKey<Long> key = ConfigKey.ranged(
                List.of("k"), Long.class, 50L, new ConfigKeyRange<>(0L, 100L));

        IConfigValidationResult<Long> result = validator.validate(key, 75L);

        assertInstanceOf(IConfigValidationResult.Valid.class, result);
        assertEquals(75L, result.value());
        assertTrue(log.messages.isEmpty());
    }

    @Test
    void validate_validBoolean_returnsValid() {
        ConfigValidator validator = new ConfigValidator(IConfigLogger.NOOP);
        ConfigKey<Boolean> key = ConfigKey.of(List.of("k"), Boolean.class, true);

        IConfigValidationResult<Boolean> result = validator.validate(key, false);

        assertInstanceOf(IConfigValidationResult.Valid.class, result);
        assertEquals(false, result.value());
    }

    @Test
    void validate_belowMin_clampsAndLogs() {
        RecordingLogger log = new RecordingLogger();
        ConfigValidator validator = new ConfigValidator(log);
        ConfigKey<Long> key = ConfigKey.ranged(
                List.of("balance", "macerator", "fe_per_tick"),
                Long.class, 100L, new ConfigKeyRange<>(10L, 1000L));

        IConfigValidationResult<Long> result = validator.validate(key, 5L);

        IConfigValidationResult.Clamped<Long> clamped = assertInstanceOf(
                IConfigValidationResult.Clamped.class, result);
        assertEquals(10L, clamped.value());
        assertEquals(5L, clamped.original());
        assertEquals(1, log.messages.size());
        assertTrue(log.messages.get(0).contains("balance.macerator.fe_per_tick"));
        assertTrue(log.messages.get(0).contains("clamped to 10"));
    }

    @Test
    void validate_aboveMax_clampsAndLogs() {
        RecordingLogger log = new RecordingLogger();
        ConfigValidator validator = new ConfigValidator(log);
        ConfigKey<Long> key = ConfigKey.ranged(
                List.of("k"), Long.class, 100L, new ConfigKeyRange<>(10L, 1000L));

        IConfigValidationResult<Long> result = validator.validate(key, 5000L);

        IConfigValidationResult.Clamped<Long> clamped = assertInstanceOf(
                IConfigValidationResult.Clamped.class, result);
        assertEquals(1000L, clamped.value());
        assertEquals(5000L, clamped.original());
        assertEquals(1, log.messages.size());
    }

    @Test
    void validate_doublesInRange_works() {
        ConfigValidator validator = new ConfigValidator(IConfigLogger.NOOP);
        ConfigKey<Double> key = ConfigKey.ranged(
                List.of("k"), Double.class, 1.0, new ConfigKeyRange<>(0.5, 2.0));

        IConfigValidationResult<Double> result = validator.validate(key, 0.25);

        IConfigValidationResult.Clamped<Double> clamped = assertInstanceOf(
                IConfigValidationResult.Clamped.class, result);
        assertEquals(0.5, clamped.value());
    }

    @Test
    void validate_nullValue_returnsRejectedWithDefault() {
        RecordingLogger log = new RecordingLogger();
        ConfigValidator validator = new ConfigValidator(log);
        ConfigKey<Long> key = ConfigKey.of(List.of("k"), Long.class, 42L);

        IConfigValidationResult<Long> result = validator.validate(key, null);

        IConfigValidationResult.Rejected<Long> rejected = assertInstanceOf(
                IConfigValidationResult.Rejected.class, result);
        assertEquals(42L, rejected.value());
        assertEquals(1, log.messages.size());
        assertTrue(log.messages.get(0).contains("null"));
    }

    @Test
    void validate_typeMismatch_returnsRejectedWithDefault() {
        RecordingLogger log = new RecordingLogger();
        ConfigValidator validator = new ConfigValidator(log);
        ConfigKey<Long> key = ConfigKey.of(List.of("k"), Long.class, 42L);

        // User puts a string where a long was expected.
        IConfigValidationResult<Long> result = validator.validate(key, "not a number");

        IConfigValidationResult.Rejected<Long> rejected = assertInstanceOf(
                IConfigValidationResult.Rejected.class, result);
        assertEquals(42L, rejected.value());
        assertEquals(1, log.messages.size());
        assertTrue(log.messages.get(0).contains("wrong type"));
    }

    @Test
    void whitelisted_validValue_returnsValid() {
        ConfigValidator validator = new ConfigValidator(IConfigLogger.NOOP);
        ConfigKey<String> key = ConfigKey.whitelisted(
                List.of("computing", "sql_dialect"), "SIMPLE", List.of("SIMPLE", "STANDARD"));

        IConfigValidationResult<String> result = validator.validate(key, "STANDARD");

        assertInstanceOf(IConfigValidationResult.Valid.class, result);
        assertEquals("STANDARD", result.value());
    }

    @Test
    void whitelisted_unknownValue_clampsToDefaultAndLogs() {
        RecordingLogger log = new RecordingLogger();
        ConfigValidator validator = new ConfigValidator(log);
        ConfigKey<String> key = ConfigKey.whitelisted(
                List.of("computing", "sql_dialect"), "SIMPLE", List.of("SIMPLE", "STANDARD"));

        IConfigValidationResult<String> result = validator.validate(key, "ORACLE");

        IConfigValidationResult.Rejected<String> rejected = assertInstanceOf(
                IConfigValidationResult.Rejected.class, result);
        assertEquals("SIMPLE", rejected.value());
        assertEquals(1, log.messages.size());
        assertTrue(log.messages.get(0).contains("computing.sql_dialect"));
    }

    @Test
    void whitelisted_isCaseSensitive_lowercaseRejected() {
        ConfigValidator validator = new ConfigValidator(IConfigLogger.NOOP);
        ConfigKey<String> key = ConfigKey.whitelisted(
                List.of("computing", "sql_dialect"), "SIMPLE", List.of("SIMPLE", "STANDARD"));

        IConfigValidationResult<String> result = validator.validate(key, "standard");

        assertInstanceOf(IConfigValidationResult.Rejected.class, result);
        assertEquals("SIMPLE", result.value());
    }

    @Test
    void whitelisted_nullValue_rejectedWithDefault() {
        ConfigValidator validator = new ConfigValidator(IConfigLogger.NOOP);
        ConfigKey<String> key = ConfigKey.whitelisted(
                List.of("computing", "sql_dialect"), "SIMPLE", List.of("SIMPLE", "STANDARD"));

        IConfigValidationResult<String> result = validator.validate(key, null);

        assertInstanceOf(IConfigValidationResult.Rejected.class, result);
        assertEquals("SIMPLE", result.value());
    }

    @Test
    void whitelisted_defaultNotInList_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfigKey.whitelisted(
                        List.of("k"), "MYSQL", List.of("SIMPLE", "STANDARD")));
    }

    @Test
    void whitelisted_emptyList_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfigKey.whitelisted(List.of("k"), "SIMPLE", List.of()));
    }

    @Test
    void registry_registerAndLookup_returnsKey() {
        CoreConfigRegistry registry = new CoreConfigRegistry();
        ConfigKey<Long> key = ConfigKey.of(
                List.of("balance", "x"), Long.class, 100L);
        registry.register(key);

        assertTrue(registry.lookup("balance.x").isPresent());
        assertTrue(registry.isWhitelisted("balance.x"));
    }

    @Test
    void registry_unknownPath_returnsEmpty() {
        CoreConfigRegistry registry = new CoreConfigRegistry();
        assertTrue(registry.lookup("unknown.path").isEmpty());
        assertEquals(false, registry.isWhitelisted("unknown.path"));
    }

    @Test
    void registry_duplicateRegistration_throws() {
        CoreConfigRegistry registry = new CoreConfigRegistry();
        ConfigKey<Long> a = ConfigKey.of(List.of("x"), Long.class, 1L);
        ConfigKey<Long> b = ConfigKey.of(List.of("x"), Long.class, 2L);
        registry.register(a);
        assertThrows(IllegalStateException.class, () -> registry.register(b));
    }

    @Test
    void registry_size_tracksRegistrations() {
        CoreConfigRegistry registry = new CoreConfigRegistry();
        registry.register(ConfigKey.of(List.of("a"), Long.class, 1L));
        registry.register(ConfigKey.of(List.of("b"), Long.class, 2L));
        registry.register(ConfigKey.of(List.of("c"), Long.class, 3L));
        assertEquals(3, registry.size());
    }

    @Test
    void registry_allKeys_returnsImmutableInsertionOrder() {
        CoreConfigRegistry registry = new CoreConfigRegistry();
        ConfigKey<Long> a = ConfigKey.of(List.of("aaa"), Long.class, 1L);
        ConfigKey<Long> b = ConfigKey.of(List.of("bbb"), Long.class, 2L);
        registry.register(a);
        registry.register(b);

        List<ConfigKey<?>> snapshot = new ArrayList<>(registry.allKeys());
        assertEquals(2, snapshot.size());
        assertEquals(a, snapshot.get(0));
        assertEquals(b, snapshot.get(1));
    }

    private static final class RecordingLogger implements IConfigLogger {
        final List<String> messages = new ArrayList<>();

        @Override
        public void warn(String message) {
            messages.add(message);
        }
    }

}
