/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.integration.jei.payload;

import dev.jstech.computers.JsComputers;
import dev.jstech.computers.operation.payload.PatternStudioPayloads;
import dev.jstech.computers.os.IOsHost;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Registers and handles the payloads the recipe-viewer integration sends: a recipe dropped into the Pattern
 * Studio's bench or machine draft. This class has no dependency on the viewer's types and is always loaded;
 * the payloads are only ever sent by the viewer plugin, which exists only when the viewer is installed.
 */
@EventBusSubscriber(modid = JsComputers.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class JeiPayloads {

    private JeiPayloads() {
    }

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(SetPatternPayload.TYPE, SetPatternPayload.STREAM_CODEC,
                JeiPayloads::handleSetPattern);
        event.registrar("1").playToServer(SetProcessingPatternPayload.TYPE, SetProcessingPatternPayload.STREAM_CODEC,
                JeiPayloads::handleSetProcessingPattern);
    }

    private static void handleSetPattern(final SetPatternPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            // The host is resolved through the monitor the player is at, never from the position alone.
            final IOsHost host = PatternStudioPayloads.studioHost(player, level, payload.host(), payload.monitorPos());
            if (host == null) {
                return;
            }
            PatternStudioPayloads.applyBenchGrid(host, level, payload.grid(), payload.recipeId());
            PacketDistributor.sendToPlayer(player, PatternStudioPayloads.buildState(level, host, "Recipe placed on the bench", 0));
        });
    }

    private static void handleSetProcessingPattern(final SetProcessingPatternPayload payload,
                                                   final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            final IOsHost host = PatternStudioPayloads.studioHost(player, level, payload.host(), payload.monitorPos());
            if (host == null) {
                return;
            }
            PatternStudioPayloads.applyProcessingCells(host, level, payload.inputs(), payload.outputs(),
                    payload.recipeType());
            PacketDistributor.sendToPlayer(player, PatternStudioPayloads.buildState(level, host, "Recipe placed in the machine draft", 1));
        });
    }
}
