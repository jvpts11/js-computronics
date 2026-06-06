/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One node row in the Mainframe's network overview: what kind of node it is, a short id, a pre-formatted detail string (capacity or storage), and whether it is currently online.
 */
public record NetworkNodeInfo(int kind, String id, String detail, boolean online) {

    public static final int KIND_MAINFRAME = 0;
    public static final int KIND_SERVER = 1;
    public static final int KIND_SUBFRAME = 2;
    public static final int KIND_PC = 3;

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkNodeInfo> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, NetworkNodeInfo::kind,
                    ByteBufCodecs.STRING_UTF8, NetworkNodeInfo::id,
                    ByteBufCodecs.STRING_UTF8, NetworkNodeInfo::detail,
                    ByteBufCodecs.BOOL, NetworkNodeInfo::online,
                    NetworkNodeInfo::new);

    public String kindLabel() {
        return switch (kind) {
            case KIND_MAINFRAME -> "MAINFRAME";
            case KIND_SERVER -> "SERVER";
            case KIND_SUBFRAME -> "SUBFRAME";
            case KIND_PC -> "PC";
            default -> "NODE";
        };
    }
}
