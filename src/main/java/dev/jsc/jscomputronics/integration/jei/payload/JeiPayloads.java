/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.integration.jei.payload;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.computing.blockentity.PatternEncoderBlockEntity;
import dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern;
import dev.jsc.jscomputronics.module.computing.menu.PatternEncoderMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Registers and handles the {@link SetPatternPayload} used by the JEI recipe-transfer integration.
 *
 * This class has no dependency on JEI types and is always loaded regardless of whether JEI
 * is installed.  The payload is only ever sent by {@code PatternEncoderTransferHandler}, which
 * is guarded by the {@code @JeiPlugin} lifecycle, so the handler runs silently when JEI is absent.
 */
@EventBusSubscriber(modid = JsComputronics.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class JeiPayloads {

    private JeiPayloads() {
    }

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(
                SetPatternPayload.TYPE,
                SetPatternPayload.STREAM_CODEC,
                JeiPayloads::handleSetPattern);
        event.registrar("1").playToServer(
                SetProcessingPatternPayload.TYPE,
                SetProcessingPatternPayload.STREAM_CODEC,
                JeiPayloads::handleSetProcessingPattern);
    }

    private static void handleSetProcessingPattern(
            final SetProcessingPatternPayload payload,
            final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            // Same trust model as handleSetPattern: the open, still-valid menu decides which encoder is written.
            if (!(player.containerMenu instanceof PatternEncoderMenu menu)
                    || !menu.stillValid(player)) {
                return;
            }
            if (!(player.serverLevel().getBlockEntity(menu.blockEntityPos())
                    instanceof PatternEncoderBlockEntity be)) {
                return;
            }
            be.applyProcessingCells(payload.inputs(), payload.outputs());
            menu.setActiveTab(PatternEncoderMenu.TAB_PROCESSING);
        });
    }

    private static void handleSetPattern(
            final SetPatternPayload payload,
            final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            // Validate the open menu first; resolve the BE from the menu's own position
            // rather than payload.pos() so a spoofed BlockPos cannot write to a different
            // PatternEncoder that the player is not actually interacting with.
            if (!(player.containerMenu instanceof PatternEncoderMenu menu)
                    || !menu.stillValid(player)) {
                return;
            }
            if (!(player.serverLevel().getBlockEntity(menu.blockEntityPos())
                    instanceof PatternEncoderBlockEntity be)) {
                return;
            }
            final List<ItemStack> grid = payload.grid();
            for (int i = 0; i < CraftingPattern.GRID_SIZE; i++) {
                be.setGhost(i, i < grid.size() ? grid.get(i) : ItemStack.EMPTY);
            }
        });
    }
}
