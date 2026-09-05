/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.storage;

/**
 * A destination that accepts a quantity of one data type — an item OR a fluid — without caring which.
 */
public interface DataSink {

    long insert(StorageKey key, long amount, boolean simulate);
}
