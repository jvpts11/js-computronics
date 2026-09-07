/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.operation;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Central registry for {@link OperationType} instances contributed by every module of the mod.
 */
public final class OperationTypeRegistry {
    private final Map<String, OperationType<?>> registry = new HashMap<>();

    public <T extends IOperationArgs> OperationType<T> register(OperationType<T> type) {
        if (registry.containsKey(type.id())) {
            throw new IllegalStateException(
                    "Operation type already registered: " + type.id());
        }
        registry.put(type.id(), type);
        return type;
    }

    public Optional<OperationType<?>> get(String id) {
        return Optional.ofNullable(registry.get(id));
    }

    public boolean contains(String id) {
        return registry.containsKey(id);
    }

    public Collection<OperationType<?>> all() {
        return Collections.unmodifiableCollection(registry.values());
    }

    public int size() {
        return registry.size();
    }
}
