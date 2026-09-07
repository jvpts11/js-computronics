/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.network.payload;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Central registration point for all mod custom payloads.
 */
public final class CorePacketHandlerRegistry {

    private CorePacketHandlerRegistry() {
    }

    public static final String PROTOCOL_VERSION = "1";

    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playBidirectional(
                PingPayload.TYPE,
                PingPayload.STREAM_CODEC,
                CorePacketHandlerRegistry::handlePing);
    }

    private static void handlePing(
            final PingPayload payload,
            final IPayloadContext context) {
        /*
         * Echo back to sender. The reply travels the opposite direction,
         * which is why the payload is registered bidirectionally.
         */
        context.reply(payload);
    }
}
