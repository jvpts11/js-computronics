/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The server-side TOML spec of the series, {@code jstech-balance.toml} next to the world save. It is built
 * from the whitelisted keys in {@link CoreConfigKeys}, one entry per key with its default and range, so the
 * file the player edits and the values the engine validates can never disagree about what exists.
 */
public final class CoreServerConfig {

    public static final String FILE_NAME = "jstech-balance.toml";

    public static final ModConfigSpec SPEC;
    private static final Map<String, ModConfigSpec.ConfigValue<?>> VALUES = new LinkedHashMap<>();

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Balance of the Operations engine. Values outside their range are clamped on load.");
        for (final ConfigKey<?> key : CoreConfigKeys.registry().allKeys()) {
            VALUES.put(key.dottedPath(), define(builder, key));
        }
        SPEC = builder.build();
    }

    private CoreServerConfig() {
    }

    private static ModConfigSpec.ConfigValue<?> define(final ModConfigSpec.Builder builder, final ConfigKey<?> key) {
        final Object defaultValue = key.defaultValue();
        if (key.range().isPresent()) {
            final ConfigKeyRange<?> range = key.range().get();
            if (defaultValue instanceof Integer value) {
                return builder.defineInRange(key.path(), value, (Integer) range.min(), (Integer) range.max());
            }
            if (defaultValue instanceof Long value) {
                return builder.defineInRange(key.path(), value, (Long) range.min(), (Long) range.max());
            }
            if (defaultValue instanceof Double value) {
                return builder.defineInRange(key.path(), value, (Double) range.min(), (Double) range.max());
            }
        }
        if (defaultValue instanceof Boolean value) {
            return builder.define(key.path(), value);
        }
        return builder.define(key.path(), defaultValue);
    }

    /** The raw value the file currently holds for a key, as the config library read it. */
    public static Object rawValue(final ConfigKey<?> key) {
        final ModConfigSpec.ConfigValue<?> value = VALUES.get(key.dottedPath());
        return value == null ? null : value.get();
    }
}
