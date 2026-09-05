/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.JsComputronics;
import dev.jstech.computronics.block.FirmwareScreenOpener;
import dev.jstech.computronics.client.os.DesktopScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only wiring for the Computing module: binds each computer menu to its screen, registers the data-cable renderer that draws mounted bus parts, and makes the standalone funnel part models available to the model manager.
 */
@EventBusSubscriber(modid = JsComputronics.MODID, value = Dist.CLIENT)
public final class ComputingClientSetup {

    private ComputingClientSetup() {
    }

    @SubscribeEvent
    public static void onLoggingOut(final net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        // The desktop's per-machine caches belong to the world being left.
        DesktopScreen.forgetClientState();
    }

    @SubscribeEvent
    public static void registerScreens(final RegisterMenuScreensEvent event) {
        // Wire the client-side firmware screen opener so blocks can open it without importing Minecraft.
        FirmwareScreenOpener.Holder.set((pos, monitorPos, kind, name) ->
                Minecraft.getInstance().setScreen(new FirmwareScreen(pos, monitorPos, kind, name)));
        dev.jstech.computronics.block.PostScreenOpener.Holder.set((pos, monitorPos, kind, name) ->
                Minecraft.getInstance().setScreen(new BootSequenceScreen(pos, monitorPos, kind, name)));
        dev.jstech.computronics.block.InstallDoneScreenOpener.Holder.set(
                (pos, monitorPos, kind, osName, targetLabel, targetSlot, failure) -> Minecraft.getInstance().setScreen(
                        failure.isEmpty()
                                ? OsInstallScreen.completed(pos, monitorPos, kind, osName, targetLabel, targetSlot)
                                : OsInstallScreen.failed(pos, monitorPos, kind, osName, targetLabel, failure)));
        dev.jstech.computronics.block.KvmScreenOpener.Holder.set(payload ->
                Minecraft.getInstance().setScreen(new KvmChannelScreen(payload)));

        event.register(ComputingModule.DESKTOP_MENU.get(), DesktopScreen::new);
        event.register(ComputingModule.MAINFRAME_MENU.get(), MainframeScreen::new);
        event.register(ComputingModule.PERSONAL_COMPUTER_MENU.get(), PersonalComputerScreen::new);
        event.register(ComputingModule.CRAFTING_COMPUTER_MENU.get(), CraftingComputerScreen::new);
        event.register(ComputingModule.CLUSTER_MANAGEMENT_COMPUTER_MENU.get(), ClusterManagementComputerScreen::new);
        event.register(ComputingModule.PATTERN_ENCODER_MENU.get(), PatternEncoderScreen::new);
        event.register(ComputingModule.COMMAND_PROMPT_MENU.get(),
                (final dev.jstech.computronics.menu.CommandPromptMenu menu,
                 final net.minecraft.world.entity.player.Inventory inv,
                 final net.minecraft.network.chat.Component title) -> new CommandPromptScreen<>(menu, inv, title));
        event.register(ComputingModule.DOS_TERMINAL_MENU.get(), DosTerminalScreen::new);
        event.register(ComputingModule.LINUX_TTY_MENU.get(), LinuxTtyScreen::new);
        event.register(ComputingModule.SERVER_RACK_MENU.get(), ServerRackScreen::new);
        event.register(ComputingModule.SERVER_ROUTER_MENU.get(), ServerRouterScreen::new);
        event.register(ComputingModule.SERVER_ASSEMBLY_MENU.get(), ServerAssemblyScreen::new);
        event.register(ComputingModule.COMPUTER_TERMINAL_MENU.get(), ComputerTerminalScreen::new);
        event.register(ComputingModule.EXPORT_BUS_MENU.get(), ExportBusScreen::new);
        event.register(ComputingModule.IMPORT_BUS_MENU.get(), ImportBusScreen::new);
        event.register(ComputingModule.CRAFTING_SWITCH_MENU.get(), CraftingSwitchScreen::new);
        event.register(ComputingModule.INPUT_BUS_MENU.get(), InputBusScreen::new);
        event.register(ComputingModule.RECEIVING_BUS_MENU.get(), ReceivingBusScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ComputingModule.DATA_CABLE_BE.get(), DataCableRenderer::new);
        event.registerBlockEntityRenderer(ComputingModule.TANK_BE.get(), TankRenderer::new);
        // Every rack cabinet (server, per era, and supercomputer) is one GeckoLib model on its controller.
        event.registerBlockEntityRenderer(ComputingModule.SERVER_RACK_BE.get(), RackRenderer::new);
        // The Mainframe is the same idea: one cabinet per era, drawn from the controller block.
        event.registerBlockEntityRenderer(ComputingModule.MAINFRAME_BE.get(), MainframeRenderer::new);
        // The Pattern Encoder: one burner body per era, with its bay, display and lamps.
        event.registerBlockEntityRenderer(ComputingModule.PATTERN_ENCODER_BE.get(), PatternEncoderRenderer::new);
    }

    @SubscribeEvent
    public static void registerExtraModels(final ModelEvent.RegisterAdditional event) {
        event.register(DataCableRenderer.IMPORT_MODEL);
        event.register(DataCableRenderer.EXPORT_MODEL);
    }
}
