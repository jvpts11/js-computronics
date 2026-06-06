/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.hardware;

/**
 * The available disk sizes.
 */
public enum DiskSize {

    MB_200("200mb", "200 MB", 50L),
    GB_500("500gb", "500 GB", 128_000L),
    TB_1("1tb", "1 TB", 262_144L),
    TB_2("2tb", "2 TB", 524_288L),
    TB_4("4tb", "4 TB", 1_048_576L),
    TB_8("8tb", "8 TB", 2_097_152L);

    private final String id;
    private final String displayName;
    private final long capacityItems;

    DiskSize(final String id, final String displayName, final long capacityItems) {
        this.id = id;
        this.displayName = displayName;
        this.capacityItems = capacityItems;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public long capacityItems() {
        return capacityItems;
    }
}
