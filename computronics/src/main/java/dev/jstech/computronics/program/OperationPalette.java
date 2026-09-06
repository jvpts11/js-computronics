/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program;

import dev.jstech.computronics.operation.payload.OperationRecord;

/**
 * The single per-type colour every Operations view uses, so an Operation reads the same across the desktop
 * apps: writes ({@code INSERT}/{@code CRAFT}) are amber, destructive ops ({@code DELETE}/{@code DROP}) are
 * red, {@code MOVE} is green, the maintenance ops ({@code ANALYZE}/{@code REINDEX}/{@code VACUUM}) are grey,
 * and reads ({@code SELECT}) plus anything else take the blue accent. The colours mirror the network
 * terminal's own type palette so the whole mod agrees. Pure ARGB, no Minecraft type.
 */
public final class OperationPalette {

    private static final int AMBER = 0xFFE0A020;
    private static final int RED = 0xFFD1495B;
    private static final int GREEN = 0xFF2EA043;
    private static final int GREY = 0xFF9098A8;
    private static final int BLUE = 0xFF3A6AE0;

    private OperationPalette() {
    }

    /** The ARGB colour associated with an Operation of the given {@link OperationRecord} type. */
    public static int colorFor(final byte type) {
        return switch (type) {
            case OperationRecord.TYPE_INSERT, OperationRecord.TYPE_CRAFT -> AMBER;
            case OperationRecord.TYPE_DELETE, OperationRecord.TYPE_DROP -> RED;
            case OperationRecord.TYPE_MOVE -> GREEN;
            case OperationRecord.TYPE_ANALYZE, OperationRecord.TYPE_REINDEX, OperationRecord.TYPE_VACUUM -> GREY;
            default -> BLUE;
        };
    }

    /** The short upper-case label for an Operation type, matching the network terminal. */
    public static String labelFor(final byte type) {
        return switch (type) {
            case OperationRecord.TYPE_SELECT -> "SELECT";
            case OperationRecord.TYPE_INSERT -> "INSERT";
            case OperationRecord.TYPE_DELETE -> "DELETE";
            case OperationRecord.TYPE_MOVE -> "MOVE";
            case OperationRecord.TYPE_ANALYZE -> "ANALYZE";
            case OperationRecord.TYPE_REINDEX -> "REINDEX";
            case OperationRecord.TYPE_VACUUM -> "VACUUM";
            case OperationRecord.TYPE_DROP -> "DROP";
            case OperationRecord.TYPE_CRAFT -> "CRAFT";
            default -> "OP";
        };
    }
}
