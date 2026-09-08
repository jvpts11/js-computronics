/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.program.iql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The IQL Engine's catalog of saved objects (views, procedures, jobs), keyed by type and name. Pure logic,
 * no Minecraft: the Mainframe owns one instance and serializes it to its block-entity NBT; the executor
 * reads it to run a view/procedure or to drive the job agent. Insertion order is preserved so the Object
 * Explorer lists objects the way they were created. Names are matched case-insensitively within a type, so
 * a view and a job may share a name without colliding.
 */
public final class IqlCatalog {

    private final Map<String, IqlSavedObject> objects = new LinkedHashMap<>();

    /** Stores (or replaces) an object; returns true if it replaced an existing one of the same type+name. */
    public boolean put(final IqlSavedObject object) {
        return objects.put(key(object.type(), object.name()), object) != null;
    }

    public IqlSavedObject get(final IqlDefinition.ObjectType type, final String name) {
        return objects.get(key(type, name));
    }

    public boolean contains(final IqlDefinition.ObjectType type, final String name) {
        return objects.containsKey(key(type, name));
    }

    public boolean remove(final IqlDefinition.ObjectType type, final String name) {
        return objects.remove(key(type, name)) != null;
    }

    public List<IqlSavedObject> ofType(final IqlDefinition.ObjectType type) {
        final List<IqlSavedObject> out = new ArrayList<>();
        for (final IqlSavedObject object : objects.values()) {
            if (object.type() == type) {
                out.add(object);
            }
        }
        return out;
    }

    public List<IqlSavedObject> all() {
        return new ArrayList<>(objects.values());
    }

    public void clear() {
        objects.clear();
    }

    public int size() {
        return objects.size();
    }

    public boolean isEmpty() {
        return objects.isEmpty();
    }

    private static String key(final IqlDefinition.ObjectType type, final String name) {
        return type + ":" + name.toLowerCase(Locale.ROOT);
    }
}
