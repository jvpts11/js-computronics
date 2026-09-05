/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.storage;

import java.util.List;

/**
 * A source that yields a quantity of one data type — an item OR a fluid — without caring which.
 */
public interface DataSource {

    long extract(StorageKey key, long amount, boolean simulate);

    List<StorageKey> available();
}
