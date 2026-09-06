/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.config;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Canonical registry of all {@link ConfigKey}s used by the mod.
 */
public final class CoreConfigRegistry {

    private final Map<String, ConfigKey<?>> keysByPath = new LinkedHashMap<>();

    public <T> ConfigKey<T> register(final ConfigKey<T> key) {
        Objects.requireNonNull(key, "key must not be null");
        final String path = key.dottedPath();
        if (keysByPath.containsKey(path)) {
            throw new IllegalStateException(
                    "Config path already registered: " + path);
        }
        keysByPath.put(path, key);
        return key;
    }

    public Optional<ConfigKey<?>> lookup(final String dottedPath) {
        Objects.requireNonNull(dottedPath, "dottedPath must not be null");
        return Optional.ofNullable(keysByPath.get(dottedPath));
    }

    public boolean isWhitelisted(final String dottedPath) {
        return keysByPath.containsKey(Objects.requireNonNull(dottedPath));
    }

    public int size() {
        return keysByPath.size();
    }

    public Collection<ConfigKey<?>> allKeys() {
        return Collections.unmodifiableCollection(keysByPath.values());
    }

    public void clear() {
        keysByPath.clear();
    }
}
