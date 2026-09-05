/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.module.computing.operation.payload.FirmwareActionPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RequestFirmwarePayload;
import dev.jsc.jscomputronics.module.computing.os.FirmwareKind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Installing a system, made visible. Before this, the firmware wrote the system and closed the
 * window, so a player saw the screen vanish and later found the machine already installed — the
 * single act that should feel weighty had no moment at all.
 *
 * <p>Three beats: what is about to happen and where, the work itself, and the reboot that ends it.
 * The write only goes to the server when the progress finishes, so closing the window mid-way
 * cancels cleanly. Arch and Gentoo never come here — their systems are put on the disk by hand
 * from a live shell, which is their whole point.
 */
public final class OsInstallScreen extends Screen {

    private static final int W = 320;
    private static final int H = 176;
    /** How long the write takes on screen. Long enough to read, short enough not to annoy. */
    private static final int WORK_TICKS = 70;

    private enum Phase { CONFIRM, WORKING, DONE, FAILED }

    /** The steps the progress walks through, each claiming a quarter of the work. */
    private static final String[] STEPS = {
            "preparing the disk",
            "copying the system",
            "creating folders",
            "registering the boot entry",
    };

    private final BlockPos computerPos;
    private final BlockPos monitorPos;
    private final FirmwareKind kind;
    private final String osName;
    private final String targetLabel;
    private final int targetSlot;
    private final long readerRef;

    private Phase phase = Phase.CONFIRM;
    /** Why the server refused the write, once it has; shown in place of the reboot prompt. */
    private String failure = "";
    private int ticks;
    private int[] primary;
    private int[] secondary;

    public OsInstallScreen(final BlockPos computerPos, final BlockPos monitorPos, final FirmwareKind kind,
                           final String osName, final String targetLabel, final int targetSlot,
                           final long readerRef) {
        super(Component.literal("Install system"));
        this.computerPos = computerPos;
        this.monitorPos = monitorPos;
        this.kind = kind;
        this.osName = osName == null || osName.isBlank() ? "the installer's system" : osName;
        this.targetLabel = targetLabel == null || targetLabel.isBlank() ? "the default disk" : targetLabel;
        this.targetSlot = targetSlot;
        this.readerRef = readerRef;
    }

    /**
     * The installer reopened at its last beat: the system is on the disk and the machine is still
     * waiting for the reboot that will boot it. Leaving the monitor does not restart a machine.
     */
    public static OsInstallScreen completed(final BlockPos computerPos, final BlockPos monitorPos,
                                            final FirmwareKind kind, final String osName,
                                            final String targetLabel, final int targetSlot) {
        final OsInstallScreen screen = new OsInstallScreen(computerPos, monitorPos, kind, osName, targetLabel,
                targetSlot, -1L);
        screen.phase = Phase.DONE;
        return screen;
    }

    /**
     * The installer at the beat the server put it on: the progress finished on screen, but the write
     * was refused (a system newer than the machine, a live medium, no room), and the player must hear
     * that instead of a "complete" that leaves the disk empty and the next boot in the firmware.
     */
    public static OsInstallScreen failed(final BlockPos computerPos, final BlockPos monitorPos,
                                         final FirmwareKind kind, final String osName,
                                         final String targetLabel, final String failure) {
        final OsInstallScreen screen = new OsInstallScreen(computerPos, monitorPos, kind, osName, targetLabel,
                -1, -1L);
        screen.phase = Phase.FAILED;
        screen.failure = failure;
        return screen;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    /** Progress through the write, 0..1000. */
    private int permille() {
        return Math.min(1000, ticks * 1000 / WORK_TICKS);
    }

    @Override
    public void tick() {
        if (phase == Phase.WORKING && ++ticks >= WORK_TICKS) {
            phase = Phase.DONE;
            // The system is written only now: an install the player walked away from never happened.
            PacketDistributor.sendToServer(new FirmwareActionPayload(computerPos, monitorPos,
                    FirmwareActionPayload.ACTION_INSTALL, readerRef, targetSlot));
        }
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (phase == Phase.CONFIRM && in(primary, mouseX, mouseY)) {
            phase = Phase.WORKING;
            return true;
        }
        if (phase == Phase.CONFIRM && in(secondary, mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (phase == Phase.DONE && in(primary, mouseX, mouseY)) {
            // Reboot into what was just installed: set it as the boot disk and let the machine come up.
            PacketDistributor.sendToServer(new FirmwareActionPayload(computerPos, monitorPos,
                    FirmwareActionPayload.ACTION_BOOT_DISK, targetSlot, -1));
            return true;
        }
        if (phase == Phase.DONE && in(secondary, mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (phase == Phase.FAILED && in(primary, mouseX, mouseY)) {
            // Back to the firmware, where the medium's row says what this machine can and cannot install.
            PacketDistributor.sendToServer(new RequestFirmwarePayload(computerPos, monitorPos));
            return true;
        }
        if (phase == Phase.FAILED && in(secondary, mouseX, mouseY)) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Screen centre of the primary action drawn on the last frame (client tests click it). */
    public int[] primaryButtonCenter() {
        return primary == null ? new int[]{width / 2, height / 2}
                : new int[]{primary[0] + primary[2] / 2, primary[1] + primary[3] / 2};
    }

    private static boolean in(final int[] r, final double mx, final double my) {
        return r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        final int x = left();
        final int y = top();
        final Palette p = Palette.of(kind);

        g.fill(x, y, x + W, y + H, p.back);
        g.fill(x, y, x + W, y + 1, p.edge);
        g.fill(x, y + H - 1, x + W, y + H, p.edge);
        g.fill(x, y, x + 1, y + H, p.edge);
        g.fill(x + W - 1, y, x + W, y + H, p.edge);
        g.fill(x + 1, y + 1, x + W - 1, y + 17, p.bar);
        final String title = switch (phase) {
            case CONFIRM -> "INSTALL SYSTEM";
            case WORKING -> "INSTALLING " + osName.toUpperCase(java.util.Locale.ROOT);
            case DONE -> "INSTALLATION COMPLETE";
            case FAILED -> "INSTALLATION FAILED";
        };
        g.drawString(font, title, x + 8, y + 5, p.bright, false);

        int ty = y + 26;
        switch (phase) {
            case CONFIRM -> {
                row(g, x, ty, p, "System", osName);
                row(g, x, ty += 12, p, "Source", readerRef < 0 ? "linked drive" : "installer medium");
                row(g, x, ty += 12, p, "Target", targetLabel);
                ty += 18;
                g.drawString(font, "Existing files on the target disk are kept.", x + 10, ty, p.dim, false);
                g.drawString(font, "A second system installs beside the first (dual boot).",
                        x + 10, ty + 11, p.dim, false);
                primary = button(g, x + 10, y + H - 26, 96, 16, "INSTALL", p, true, mouseX, mouseY);
                secondary = button(g, x + 114, y + H - 26, 96, 16, "CANCEL", p, false, mouseX, mouseY);
            }
            case WORKING -> {
                final int done = permille() * STEPS.length / 1000;
                for (int i = 0; i < STEPS.length; i++) {
                    final boolean finished = i < done;
                    final boolean current = i == done;
                    g.drawString(font, STEPS[i] + " ...", x + 10, ty, finished || current ? p.text : p.dim, false);
                    if (finished) {
                        g.drawString(font, "done", x + W - 60, ty, p.ok, false);
                    } else if (current) {
                        g.drawString(font, (permille() % 1000) / 10 + "%", x + W - 60, ty, p.bright, false);
                    }
                    ty += 12;
                }
                ty += 12;
                g.fill(x + 10, ty, x + W - 10, ty + 8, p.trackBg);
                g.fill(x + 10, ty, x + 10 + (W - 20) * permille() / 1000, ty + 8, p.bright);
                g.drawString(font, "Do not remove the medium.", x + 10, ty + 16, p.dim, false);
            }
            case DONE -> {
                g.drawString(font, osName + " installed on " + targetLabel + ".", x + 10, ty, p.text, false);
                g.drawString(font, "Take the installation medium out before rebooting,",
                        x + 10, ty + 16, p.dim, false);
                g.drawString(font, "or the machine boots the installer again.", x + 10, ty + 27, p.dim, false);
                primary = button(g, x + 10, y + H - 26, 96, 16, "REBOOT", p, true, mouseX, mouseY);
                secondary = button(g, x + 114, y + H - 26, 110, 16, "BACK TO SETUP", p, false, mouseX, mouseY);
            }
            case FAILED -> {
                g.drawString(font, "Nothing was written to " + targetLabel + ".", x + 10, ty, p.dim, false);
                int ly = ty + 16;
                for (final FormattedCharSequence line : font.split(Component.literal(failure), W - 20)) {
                    g.drawString(font, line, x + 10, ly, p.text, false);
                    ly += 11;
                }
                primary = button(g, x + 10, y + H - 26, 110, 16, "BACK TO SETUP", p, true, mouseX, mouseY);
                secondary = button(g, x + 128, y + H - 26, 96, 16, "CLOSE", p, false, mouseX, mouseY);
            }
        }
    }

    private void row(final GuiGraphics g, final int x, final int y, final Palette p,
                     final String key, final String value) {
        g.drawString(font, key, x + 10, y, p.dim, false);
        g.drawString(font, value, x + 90, y, p.text, false);
    }

    private int[] button(final GuiGraphics g, final int bx, final int by, final int bw, final int bh,
                         final String label, final Palette p, final boolean strong,
                         final int mouseX, final int mouseY) {
        final boolean hovered = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
        g.fill(bx, by, bx + bw, by + bh, hovered ? p.bar : p.back);
        final int edge = strong ? p.bright : p.edge;
        g.fill(bx, by, bx + bw, by + 1, edge);
        g.fill(bx, by + bh - 1, bx + bw, by + bh, edge);
        g.fill(bx, by, bx + 1, by + bh, edge);
        g.fill(bx + bw - 1, by, bx + bw, by + bh, edge);
        g.drawString(font, label, bx + (bw - font.width(label)) / 2, by + (bh - 8) / 2,
                strong ? p.bright : p.text, false);
        return new int[]{bx, by, bw, bh};
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int green(final int color) {
        return dev.jstech.core.gui.Phosphor.green(color);
    }

    /** The three firmware looks, so the installer matches the machine it is installing onto. */
    private record Palette(int back, int bar, int edge, int text, int bright, int dim, int ok, int trackBg) {

        static Palette of(final FirmwareKind kind) {
            return switch (kind) {
                // The Vintage tube is monochrome: every one of these is the phosphor, lit to the
                // brightness the colour reads at.
                case CLI_BIOS -> new Palette(0xFF020602, green(0xFF141414), green(0xFF3A3A3A), green(0xFFBEBEBE),
                        green(0xFFE8E8E8), green(0xFF6A6A6A), green(0xFF63C363), green(0xFF1E1E1E));
                case BLUE_BIOS -> new Palette(0xFF06217A, 0xFF0A2C9E, 0xFF6E8BE0, 0xFFD6DEF8,
                        0xFFFFFFFF, 0xFF9AA9DE, 0xFF9BE29B, 0xFF0A1E5E);
                case UEFI -> new Palette(0xFF10151B, 0xFF19212B, 0xFF39434F, 0xFFCDD6E2,
                        0xFF39D6C4, 0xFF7D8A9C, 0xFF5FE07A, 0xFF161D25);
            };
        }
    }
}
