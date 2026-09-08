/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation.payload;

import dev.jstech.computers.os.OpenWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * The windows open on a machine's desktop, in both directions. Client to server when a player leaves
 * the monitor (the layout they left behind becomes the machine's); server to client when the desktop
 * opens (so the player gets back the windows the machine has). One shape for both, so the two ends
 * cannot describe a window differently.
 *
 * @param host    the machine
 * @param windows the open windows, front-most last
 */
public record DesktopWindowsPayload(BlockPos host, List<WireWindow> windows) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DesktopWindowsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "desktop_windows"));

    private static final int FLAG_MINIMIZED = 1;
    private static final int FLAG_MAXIMIZED = 2;

    /** One window on the wire. The two booleans travel as flags so the codec stays a plain composite. */
    public record WireWindow(String key, int x, int y, int w, int h, int flags) {

        public static final StreamCodec<RegistryFriendlyByteBuf, WireWindow> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(64), WireWindow::key,
                        ByteBufCodecs.VAR_INT, WireWindow::x,
                        ByteBufCodecs.VAR_INT, WireWindow::y,
                        ByteBufCodecs.VAR_INT, WireWindow::w,
                        ByteBufCodecs.VAR_INT, WireWindow::h,
                        ByteBufCodecs.VAR_INT, WireWindow::flags,
                        WireWindow::new);

        public static WireWindow of(final OpenWindow window) {
            return new WireWindow(window.key(), window.x(), window.y(), window.w(), window.h(),
                    (window.minimized() ? FLAG_MINIMIZED : 0) | (window.maximized() ? FLAG_MAXIMIZED : 0));
        }

        public OpenWindow toOpenWindow() {
            return new OpenWindow(key, x, y, w, h,
                    (flags & FLAG_MINIMIZED) != 0, (flags & FLAG_MAXIMIZED) != 0);
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, DesktopWindowsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, DesktopWindowsPayload::host,
                    WireWindow.STREAM_CODEC.apply(ByteBufCodecs.list(OpenWindow.MAX)),
                    DesktopWindowsPayload::windows,
                    DesktopWindowsPayload::new);

    public static DesktopWindowsPayload of(final BlockPos host, final List<OpenWindow> windows) {
        final List<WireWindow> wire = new ArrayList<>(windows.size());
        for (final OpenWindow window : windows) {
            wire.add(WireWindow.of(window));
        }
        return new DesktopWindowsPayload(host, wire);
    }

    public List<OpenWindow> toOpenWindows() {
        final List<OpenWindow> out = new ArrayList<>(windows.size());
        for (final WireWindow window : windows) {
            out.add(window.toOpenWindow());
        }
        return out;
    }

    @Override
    public CustomPacketPayload.Type<DesktopWindowsPayload> type() {
        return TYPE;
    }
}
