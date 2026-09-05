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
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the content of an {@code .iql} file that the player asked to open, together
 * with the file name so the NMS screen can track the current file for Save vs Save As.
 *
 * <p>When {@code ok} is false the file could not be read (not found, corrupt, etc.) and
 * {@code content} is an empty string. The screen should show a status-bar error in that case and
 * leave the editor unchanged.
 *
 * @param fileName the full file name (with {@code .iql} extension) that was opened, or empty on failure
 * @param content  the file's text content, or an empty string when ok is false
 * @param ok       true when the file was found and read successfully
 */
public record IqlFileContentPayload(String fileName, String content, boolean ok)
        implements CustomPacketPayload {

    private static final int MAX_NAME = 40;

    public static final CustomPacketPayload.Type<IqlFileContentPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("jsc", "iql_file_content"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IqlFileContentPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_NAME), IqlFileContentPayload::fileName,
                    ByteBufCodecs.stringUtf8(SaveScriptPayload.MAX_LEN), IqlFileContentPayload::content,
                    ByteBufCodecs.BOOL, IqlFileContentPayload::ok,
                    IqlFileContentPayload::new);

    @Override
    public CustomPacketPayload.Type<IqlFileContentPayload> type() {
        return TYPE;
    }
}
