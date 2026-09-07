/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The client-side half of a program: how its id maps to the desktop window it opens. The
 * platform-independent descriptor ({@code os.ProgramSpec}) lives in common; this registry supplies the
 * one thing that cannot, a factory that builds the {@link IDesktopApp} on the client. Together they are
 * the whole per-program wiring, so a new program is a common {@code ProgramSpec} plus one entry here,
 * and the launcher rail, taskbar and This PC all resolve the window through this one lookup instead of a
 * hardcoded {@code switch}.
 *
 * <p>An add-on registers its own factory the same way from its client setup, keeping the desktop open to
 * third-party programs.
 */
public final class ProgramClient {

    /** Builds a program's window from the desktop context (the host computer, its monitor, and the OS id). */
    @FunctionalInterface
    public interface IDesktopAppFactory {
        IDesktopApp create(BlockPos host, BlockPos monitorPos, ResourceLocation osId);
    }

    private static final Map<ResourceLocation, IDesktopAppFactory> FACTORIES = new LinkedHashMap<>();

    static {
        registerBuiltins();
    }

    private ProgramClient() {
    }

    /** Registers the window factory for {@code id}. Safe to call from any mod's client setup. */
    public static void register(final ResourceLocation id, final IDesktopAppFactory factory) {
        FACTORIES.put(id, factory);
    }

    /** The window factory for {@code id}, or {@code null} when the program opens no desktop window. */
    public static IDesktopAppFactory factory(final ResourceLocation id) {
        return FACTORIES.get(id);
    }

    /** Whether {@code id} opens a desktop window (i.e. has a registered factory). */
    public static boolean hasWindow(final ResourceLocation id) {
        return FACTORIES.containsKey(id);
    }

    private static void registerBuiltins() {
        // Built-in Frames apps.
        register(rl("network"), (host, mon, os) -> new NetworkInteractorApp(host, mon));
        register(rl("this_pc"), (host, mon, os) -> new ThisPcApp(host));
        register(rl("settings"), (host, mon, os) -> new SettingsApp(host, mon));
        register(rl("files"), (host, mon, os) -> new FilesApp(host, os.getPath(), "", mon));
        register(rl("editor"), (host, mon, os) -> new EditorApp(host));
        register(rl("command_prompt"), (host, mon, os) -> new ShellApp(host, os));
        register(rl("system_monitor"), (host, mon, os) -> new SystemMonitorApp(host));
        register(rl("calculator"), (host, mon, os) -> new CalculatorApp());
        register(rl("network_manager"), (host, mon, os) -> new NetworkManagerApp(host, mon));
        register(rl("task_manager"), (host, mon, os) -> new TaskManagerApp(host, os));
        // Installable programs.
        register(rl("nms"), (host, mon, os) ->
                new dev.jstech.computronics.client.NmsApp(host, mon));
        register(rl("crafting_manager"), (host, mon, os) -> new CraftingManagerApp(host));
        register(rl("pattern_studio"), (host, mon, os) -> new PatternStudioApp(host, mon));
        register(rl("cluster_manager"), (host, mon, os) -> new ClusterManagerApp(host));
        register(rl("minesweeper"), (host, mon, os) -> new MinesweeperApp());
        register(rl("storage_insights"), (host, mon, os) -> new StorageInsightsApp(host, mon));
        register(rl("craft_planner"), (host, mon, os) -> new CraftPlannerApp(host, mon));
        register(rl("automation_manager"), (host, mon, os) -> new AutomationManagerApp(host, mon));
        register(rl("remote_control"), (host, mon, os) -> new RemoteControlApp(host, mon));
        // Linux's disk utility: the same volumes This PC lists, under the name that platform uses.
        register(rl("disks"), (host, mon, os) -> new ThisPcApp(host, "Disks"));
    }

    private static ResourceLocation rl(final String path) {
        return ResourceLocation.fromNamespaceAndPath("jsc", path);
    }
}
