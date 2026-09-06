/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.block.part;

import org.jetbrains.annotations.Nullable;

/**
 * The kinds of part that can be attached to a data cable's face.
 */
public enum CablePartType {

    IMPORT,
    EXPORT,
    INPUT,
    RECEIVING;

    private static final CablePartType[] BY_ID = values();

    public byte id() {
        return (byte) ordinal();
    }

    @Nullable
    public static CablePartType byId(final byte id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : null;
    }

    public CablePart create() {
        return switch (this) {
            case IMPORT -> new ImportBusPart();
            case EXPORT -> new ExportBusPart();
            case INPUT -> new InputBusPart();
            case RECEIVING -> new ReceivingBusPart();
        };
    }
}
