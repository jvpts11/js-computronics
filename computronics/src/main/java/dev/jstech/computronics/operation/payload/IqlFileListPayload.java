/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: the list of {@code .iql} file names present on the Mainframe's system disk.
 *
 * <p>Sent in response to {@link RequestIqlFileListPayload} and after every successful
 * {@link SaveIqlFilePayload} write, so the NMS file picker always reflects the current disk state.
 *
 * <p>Each entry is the full file path, including the {@code .iql} extension. When the status field
 * is non-empty it reports the outcome of the preceding save (e.g. {@code "saved"} or
 * {@code "disk full"}); on a plain list-request it is empty.
 *
 * @param files  the list of {@code .iql} file paths (may be empty)
 * @param status a short outcome message after a save, or an empty string for a plain list response
 * @param ok     true when the preceding operation succeeded (meaningful only when status is non-empty)
 */
public record IqlFileListPayload(List<String> files, String status, boolean ok)
        implements CustomPacketPayload {

    /** Maximum number of file entries sent in one payload. */
    public static final int MAX_FILES = 64;

    /** Maximum characters per file name ({@value SaveIqlFilePayload#MAX_NAME_LEN} + ".iql" + safety). */
    private static final int MAX_NAME = 40;

    /** Maximum characters in the status message. */
    private static final int MAX_STATUS = 48;

    public static final CustomPacketPayload.Type<IqlFileListPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("jsc", "iql_file_list"));

    private record FileName(String value) {
        static final StreamCodec<RegistryFriendlyByteBuf, FileName> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(MAX_NAME), FileName::value,
                        FileName::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, IqlFileListPayload> STREAM_CODEC =
            StreamCodec.composite(
                    FileName.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_FILES)),
                    payload -> payload.files().stream().map(FileName::new).toList(),
                    ByteBufCodecs.stringUtf8(MAX_STATUS),
                    IqlFileListPayload::status,
                    ByteBufCodecs.BOOL,
                    IqlFileListPayload::ok,
                    (names, status, ok) -> new IqlFileListPayload(
                            names.stream().map(FileName::value).toList(), status, ok));

    @Override
    public CustomPacketPayload.Type<IqlFileListPayload> type() {
        return TYPE;
    }
}
