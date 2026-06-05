/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.registry;

import com.mojang.serialization.Codec;
import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.persistence.JscChunkData;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import net.minecraft.core.UUIDUtil;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

/**
 * Registers the mod's {@link AttachmentType}s.
 */
public final class JscAttachments {

    private JscAttachments() {
    }

    private static final Codec<NetworkUuid> NETWORK_UUID_CODEC =
            UUIDUtil.CODEC.xmap(NetworkUuid::new, NetworkUuid::value);

    private static final Codec<JscChunkData> CHUNK_DATA_CODEC =
            NETWORK_UUID_CODEC.listOf().xmap(
                    list -> JscChunkData.of(new LinkedHashSet<>(list)),
                    data -> List.copyOf(data.networks()));

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, JsComputronics.MODID);

    public static final Supplier<AttachmentType<NetworkSystem>> NETWORK_SYSTEM =
            ATTACHMENT_TYPES.register("network_system",
                    () -> AttachmentType.builder(NetworkSystem::new).build());

    public static final Supplier<AttachmentType<JscChunkData>> CHUNK_NETWORKS =
            ATTACHMENT_TYPES.register("chunk_networks",
                    () -> AttachmentType.builder(JscChunkData::empty)
                            .serialize(CHUNK_DATA_CODEC)
                            .build());

    public static void register(final IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
