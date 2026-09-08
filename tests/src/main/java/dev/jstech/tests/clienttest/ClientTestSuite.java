/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.tests.clienttest;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The explicit list of client test classes. Tests are discovered by reflection from these classes only (
 * no classpath scanning) and ordered by name so every shard sees the same list and can take its slice.
 */
public final class ClientTestSuite {

    private static final List<Class<?>> CLASSES = List.of(
            CannonEditorClientTests.class,
            CraftingChainClientTests.class,
            FilesSyncClientTests.class,
            FullJourneyClientTests.class,
            InstallMediaClientTests.class,
            TaskbarClientTests.class,
            MekanismClientTests.class,
            UiSweepClientTests.class);

    private ClientTestSuite() {
    }

    /**
     * One discovered test: its display name, the method to invoke, its timeout and its index in the whole
     * (sorted) suite, and the index places the test's world area, so shards never overlap.
     */
    public record Entry(String name, Method method, int timeoutTicks, int index) {
    }

    /** Every test in the suite, sorted by name and numbered. */
    public static List<Entry> all() {
        final List<Entry> found = new ArrayList<>();
        for (final Class<?> type : CLASSES) {
            for (final Method method : type.getDeclaredMethods()) {
                final ClientTest annotation = method.getAnnotation(ClientTest.class);
                if (annotation == null) {
                    continue;
                }
                if (!Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers())
                        || method.getParameterCount() != 1
                        || method.getParameterTypes()[0] != ClientTestContext.class) {
                    throw new IllegalStateException("client test " + type.getSimpleName() + "." + method.getName()
                            + " must be public static and take a single ClientTestContext");
                }
                found.add(new Entry(type.getSimpleName() + "." + method.getName(), method,
                        annotation.timeoutTicks(), 0));
            }
        }
        found.sort(Comparator.comparing(Entry::name));
        final List<Entry> entries = new ArrayList<>(found.size());
        for (int i = 0; i < found.size(); i++) {
            final Entry e = found.get(i);
            entries.add(new Entry(e.name(), e.method(), e.timeoutTicks(), i));
        }
        return entries;
    }

    /** The slice of the suite that shard {@code shard} of {@code shards} runs (round-robin by index). */
    public static List<Entry> shard(final int shard, final int shards) {
        final List<Entry> all = all();
        final List<Entry> mine = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            if (i % shards == shard) {
                mine.add(all.get(i));
            }
        }
        return mine;
    }
}
