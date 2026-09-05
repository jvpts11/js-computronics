/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.jstech.computronics.operation.payload.KvmSelectPayload;
import dev.jstech.computronics.operation.payload.OpenKvmPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The KVM Switch's channel bar: one monitor, several machines. A rack holding two or more computers
 * needs the switch to say which of them the screen means, so this is what the monitor shows first —
 * pick a channel (click it, or press its number key) and the machine's own session opens on top.
 */
public final class KvmChannelScreen extends Screen {

    private static final int W = 260;
    private static final int ROW_H = 20;
    private static final int PAD = 10;

    private final BlockPos rackPos;
    private final BlockPos monitorPos;
    private final int activeChannel;
    private final List<OpenKvmPayload.Channel> channels;

    public KvmChannelScreen(final OpenKvmPayload payload) {
        super(Component.literal("KVM Switch"));
        this.rackPos = payload.rackPos();
        this.monitorPos = payload.monitorPos();
        this.activeChannel = payload.activeChannel();
        this.channels = payload.channels();
    }

    private int panelHeight() {
        return PAD * 2 + 26 + channels.size() * ROW_H;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - panelHeight()) / 2;
    }

    private void select(final int index) {
        if (index < 0 || index >= channels.size()) {
            return;
        }
        PacketDistributor.sendToServer(new KvmSelectPayload(rackPos, monitorPos,
                channels.get(index).slot()));
        onClose();
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        final int x = left();
        final int y = top() + 26;
        for (int i = 0; i < channels.size(); i++) {
            final int rowY = y + i * ROW_H;
            if (mouseX >= x + PAD && mouseX < x + W - PAD && mouseY >= rowY && mouseY < rowY + ROW_H - 2) {
                select(i);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        // The channel keys of a real switch: 1..8 pick a machine straight off the bar.
        if (keyCode >= InputConstants.KEY_1 && keyCode <= InputConstants.KEY_8) {
            select(keyCode - InputConstants.KEY_1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        final int x = left();
        final int y = top();
        JscOsTheme.window(g, x, y, W, panelHeight());
        JscOsTheme.headerBar(g, x + 6, y + 6, W - 12);
        JscOsTheme.text(g, font, "KVM SWITCH", x + 12, y + 11, JscOsTheme.text());
        final String count = channels.size() + " machines";
        JscOsTheme.text(g, font, count, x + W - 12 - font.width(count), y + 11, JscOsTheme.dim());

        for (int i = 0; i < channels.size(); i++) {
            final OpenKvmPayload.Channel channel = channels.get(i);
            final int rowY = y + 26 + i * ROW_H;
            final boolean hovered = mouseX >= x + PAD && mouseX < x + W - PAD
                    && mouseY >= rowY && mouseY < rowY + ROW_H - 2;
            final boolean active = channel.slot() == activeChannel;
            JscOsTheme.panel(g, x + PAD, rowY, W - PAD * 2, ROW_H - 2);
            if (hovered || active) {
                g.fill(x + PAD, rowY, x + PAD + 2, rowY + ROW_H - 2,
                        active ? JscOsTheme.accent() : JscOsTheme.dim());
            }
            JscOsTheme.text(g, font, "F" + (i + 1), x + PAD + 8, rowY + 6,
                    active ? JscOsTheme.accent() : JscOsTheme.dim());
            JscOsTheme.text(g, font, channel.name(), x + PAD + 34, rowY + 6, JscOsTheme.text());
            final String state = channel.running() ? "ONLINE" : "OFF";
            JscOsTheme.text(g, font, state, x + W - PAD - 8 - font.width(state), rowY + 6,
                    channel.running() ? JscOsTheme.green() : JscOsTheme.red());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
