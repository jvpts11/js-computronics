/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.common.tier.HardwareEra;
import dev.jsc.jscomputronics.module.computing.blockentity.AbstractComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.operation.payload.InstallOsPayload;
import dev.jsc.jscomputronics.module.computing.os.FirmwareKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Firmware setup, shown in a centred window (the monitor's screen) when the linked computer has no
 * OS installed. The window's look is era-correct, chosen by {@link FirmwareKind}:
 * <ul>
 *   <li>{@link FirmwareKind#CLI_BIOS} — a green monochrome phosphor POST/BIOS (Vintage era);</li>
 *   <li>{@link FirmwareKind#BLUE_BIOS} — a classic blue BIOS setup utility (Legacy era);</li>
 *   <li>{@link FirmwareKind#UEFI} — a modern graphical UEFI panel UI (Standard era).</li>
 * </ul>
 *
 * <p>The window is centred over the dimmed game (not full-screen). Clicking the INSTALL OS action
 * sends {@link InstallOsPayload}; the server scans the computer's linked media readers and, when the
 * era gate passes, installs the OS onto the system disk.
 */
public class FirmwareScreen extends Screen {

    // The glass (content) size. Kept a touch under the old 348x224 so the monitor frame drawn around it still
    // fits the viewport at a high GUI scale; the era panels below adapt to this height.
    private static final int W = 340;
    private static final int H = 214;

    // Vintage CLI BIOS — green monochrome phosphor (period-correct CRT).
    private static final int CLI_BG     = 0xFF021207;
    private static final int CLI_TEXT   = 0xFF35D158;
    private static final int CLI_BRIGHT = 0xFF87FFAC;
    private static final int CLI_DIM    = 0xFF1A7C39;

    // Legacy blue BIOS (Award/AMI).
    private static final int BLUE_BG     = 0xFF0000A8;
    private static final int BLUE_TITLE  = 0xFFB9B9B9;
    private static final int BLUE_BORDER = 0xFF6FB7FF;
    private static final int BLUE_TEXT   = 0xFFFFFFFF;
    private static final int BLUE_VALUE  = 0xFF39D6C4;
    private static final int BLUE_AMBER  = 0xFFFFE14D;
    private static final int BLUE_DIM    = 0xFFB9C4D6;
    private static final int BLUE_SELBG  = 0xFFD9D9D9;

    // Standard UEFI.
    private static final int UEFI_BG     = 0xFF1E2030;
    private static final int UEFI_HEAD   = 0xFF11131F;
    private static final int UEFI_PANEL  = 0xFF2A2D3E;
    private static final int UEFI_PH     = 0xFF3A4060;
    private static final int UEFI_TEXT   = 0xFFE6ECF6;
    private static final int UEFI_KEY    = 0xFFA8B2C6;
    private static final int UEFI_ACCENT = 0xFF5FA8D3;
    private static final int UEFI_AMBER  = 0xFFF0B23A;
    private static final int UEFI_OK     = 0xFF5FE07A;
    private static final int UEFI_DIM    = 0xFF8090A8;

    private final BlockPos computerPos;
    private final FirmwareKind kind;
    private final String machineName;

    // INSTALL OS hit-box, recomputed each render() for the active layout.
    private int hitX;
    private int hitY;
    private int hitW;
    private int hitH;

    public FirmwareScreen(final BlockPos computerPos, final FirmwareKind kind, final String machineName) {
        super(Component.literal("Firmware Setup"));
        this.computerPos = computerPos;
        this.kind = kind;
        this.machineName = machineName;
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        final int x = (width - W) / 2;
        final int y = (height - H) / 2;
        // The host computer's hardware-era monitor frame wraps the firmware window.
        MonitorFrame.renderBody(g, x, y, W, H, era(), font);
        switch (kind) {
            case CLI_BIOS  -> renderCliBios(g, x, y, mouseX, mouseY);
            case BLUE_BIOS -> renderBlueBios(g, x, y, mouseX, mouseY);
            case UEFI      -> renderUefi(g, x, y, mouseX, mouseY);
        }
    }

    /**
     * The host computer's hardware era, read from its block entity so the monitor frame matches the chassis. When
     * the host is not client-loaded, it falls back to the era the firmware kind implies (CLI = Vintage, blue BIOS =
     * Legacy, UEFI = Standard).
     */
    private HardwareEra era() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(computerPos) instanceof AbstractComputerBlockEntity host) {
            return host.displayEra();
        }
        return switch (kind) {
            case CLI_BIOS -> HardwareEra.VINTAGE;
            case BLUE_BIOS -> HardwareEra.LEGACY;
            case UEFI -> HardwareEra.STANDARD;
        };
    }

    private boolean overHit(final double mouseX, final double mouseY) {
        return mouseX >= hitX && mouseX <= hitX + hitW && mouseY >= hitY && mouseY <= hitY + hitH;
    }

    /** Screen centre of the install/boot action drawn on the last frame (client tests click it). */
    public int[] installButtonCenter() {
        return new int[]{hitX + hitW / 2, hitY + hitH / 2};
    }

    private void line2(final GuiGraphics g, final String a, final String b, final int x, final int y,
                       final int ca, final int cb) {
        g.drawString(font, a, x, y, ca, false);
        g.drawString(font, b, x + font.width(a), y, cb, false);
    }

    /** A 1px window border in the given colour around the W x H window at (x, y). */
    private void border(final GuiGraphics g, final int x, final int y, final int color) {
        g.fill(x - 1, y - 1, x + W + 1, y, color);
        g.fill(x - 1, y + H, x + W + 1, y + H + 1, color);
        g.fill(x - 1, y, x, y + H, color);
        g.fill(x + W, y, x + W + 1, y + H, color);
    }

    // ---------------------------------------------------------------------------
    // Vintage — green monochrome POST/BIOS
    // ---------------------------------------------------------------------------

    private void renderCliBios(final GuiGraphics g, final int x, final int y, final int mouseX, final int mouseY) {
        g.fill(x, y, x + W, y + H, CLI_BG);
        border(g, x, y, CLI_DIM);
        final int tx = x + 14;
        int ty = y + 12;
        final int lh = 11;

        g.drawString(font, "J's Computronics BIOS  v1.02", tx, ty, CLI_BRIGHT, false);
        ty += lh;
        g.drawString(font, "(C) 2026 J's Computronics Corp.", tx, ty, CLI_DIM, false);
        ty += lh + 4;
        g.drawString(font, "CPU .............. detected", tx, ty, CLI_TEXT, false);
        ty += lh;
        line2(g, "Memory Test ...... ", "OK", tx, ty, CLI_TEXT, CLI_BRIGHT);
        ty += lh + 4;
        g.drawString(font, "Detecting peripherals ...", tx, ty, CLI_TEXT, false);
        ty += lh;
        line2(g, "  Monitor ............ ", "connected", tx, ty, CLI_TEXT, CLI_BRIGHT);
        ty += lh;
        line2(g, "  System Disk [C:] ... ", "not installed", tx, ty, CLI_TEXT, CLI_DIM);
        ty += lh + 6;
        g.drawString(font, ">> No operating system found.", tx, ty, CLI_BRIGHT, false);
        ty += lh;
        g.drawString(font, "   Insert OS media, then install:", tx, ty, CLI_DIM, false);
        ty += lh + 6;

        final String label = " INSTALL OS ";
        hitX = tx;
        hitY = ty;
        hitW = font.width(label) + 4;
        hitH = 13;
        final boolean hot = overHit(mouseX, mouseY);
        g.fill(hitX, hitY, hitX + hitW, hitY + hitH, hot ? CLI_TEXT : CLI_BRIGHT);
        g.drawString(font, label, hitX + 2, hitY + 3, CLI_BG, false);

        g.drawString(font, "click INSTALL OS  -  ESC: close", tx, y + H - 14, CLI_DIM, false);
    }

    // ---------------------------------------------------------------------------
    // Legacy — classic blue BIOS setup utility
    // ---------------------------------------------------------------------------

    private void renderBlueBios(final GuiGraphics g, final int x, final int y, final int mouseX, final int mouseY) {
        g.fill(x, y, x + W, y + H, BLUE_BG);
        border(g, x, y, BLUE_BORDER);

        // Title bar.
        g.fill(x, y, x + W, y + 14, BLUE_TITLE);
        drawCentered(g, "J's Computronics BIOS Setup Utility", x + W / 2, y + 3, BLUE_BG);
        // Tabs.
        g.drawString(font, "Main", x + 8, y + 18, BLUE_TITLE, false);
        g.drawString(font, "Boot", x + 44, y + 18, BLUE_DIM, false);
        g.drawString(font, "Exit", x + 80, y + 18, BLUE_DIM, false);
        g.fill(x, y + 28, x + W, y + 29, BLUE_BORDER);

        final int top = y + 34;
        final int boxW = 206;
        final int boxX = x + 8;
        final int helpX = boxX + boxW + 8;
        final int helpW = W - boxW - 24;
        final int boxH = H - (top - y) - 24;

        drawBox(g, boxX, top, boxW, boxH, "System / Boot Configuration");
        int ry = top + 18;
        final int rh = 13;
        kv(g, "BIOS Version", "Legacy", boxX, boxW, ry, BLUE_VALUE);
        ry += rh;
        kv(g, "Processor", "detected", boxX, boxW, ry, BLUE_VALUE);
        ry += rh;
        kv(g, "System Memory", "OK", boxX, boxW, ry, BLUE_VALUE);
        ry += rh + 6;
        kv(g, "1st Boot Device", "Floppy A:", boxX, boxW, ry, BLUE_VALUE);
        ry += rh;
        kv(g, "2nd Boot Device", "Disk C:", boxX, boxW, ry, BLUE_VALUE);
        ry += rh;
        kv(g, "OS Status", "Not Installed", boxX, boxW, ry, BLUE_AMBER);
        ry += rh + 8;

        hitX = boxX + 2;
        hitY = ry - 3;
        hitW = boxW - 4;
        hitH = 13;
        g.fill(hitX, hitY, hitX + hitW, hitY + hitH, BLUE_SELBG);
        g.drawString(font, "> Install Operating System", hitX + 6, hitY + 3, BLUE_BG, false);

        drawBox(g, helpX, top, helpW, boxH, "Item Help");
        drawWrapped(g, "No OS on the system disk. Insert OS media in a linked reader, then choose "
                + "Install Operating System.", helpX + 6, top + 18, helpW - 12, BLUE_DIM);

        g.fill(x, y + H - 16, x + W, y + H, BLUE_TITLE);
        g.drawString(font, "Enter: Install OS   -   ESC: Exit", x + 8, y + H - 12, BLUE_BG, false);
    }

    private void kv(final GuiGraphics g, final String k, final String v, final int bx, final int boxW,
                    final int y, final int vColor) {
        g.drawString(font, k, bx + 8, y, BLUE_TEXT, false);
        g.drawString(font, v, bx + boxW - font.width(v) - 8, y, vColor, false);
    }

    private void drawBox(final GuiGraphics g, final int x, final int y, final int w, final int h,
                         final String header) {
        g.fill(x, y, x + w, y + 1, BLUE_BORDER);
        g.fill(x, y + h - 1, x + w, y + h, BLUE_BORDER);
        g.fill(x, y, x + 1, y + h, BLUE_BORDER);
        g.fill(x + w - 1, y, x + w, y + h, BLUE_BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + 12, BLUE_BORDER);
        g.drawString(font, header, x + 6, y + 3, BLUE_BG, false);
    }

    // ---------------------------------------------------------------------------
    // Standard — modern UEFI panels
    // ---------------------------------------------------------------------------

    private void renderUefi(final GuiGraphics g, final int x, final int y, final int mouseX, final int mouseY) {
        g.fill(x, y, x + W, y + H, UEFI_BG);
        border(g, x, y, UEFI_PH);

        g.fill(x, y, x + W, y + 20, UEFI_HEAD);
        g.fill(x, y + 20, x + W, y + 22, UEFI_ACCENT);
        g.drawString(font, "J's Computronics", x + 10, y + 6, UEFI_TEXT, false);
        g.drawString(font, "UEFI", x + W - font.width("UEFI") - 10, y + 6, UEFI_DIM, false);

        final int top = y + 30;
        final int sysX = x + 10;
        final int sysW = 188;
        final int bootX = sysX + sysW + 10;
        final int bootW = W - sysW - 30;
        final int panelH = H - (top - y) - 24;

        panel(g, sysX, top, sysW, panelH, "System Information");
        int ry = top + 22;
        final int rh = 15;
        urow(g, "Processor", "detected", sysX, sysW, ry);
        ry += rh;
        urow(g, "Memory", "detected", sysX, sysW, ry);
        ry += rh;
        urow(g, "Motherboard", "ATX-P", sysX, sysW, ry);
        ry += rh;
        urow(g, "Hardware Era", "Standard", sysX, sysW, ry);
        ry += rh;
        urowc(g, "Monitor", "Connected", sysX, sysW, ry, UEFI_OK);
        ry += rh;
        urow(g, "System Disk", "Empty", sysX, sysW, ry);

        panel(g, bootX, top, bootW, panelH, "Boot Manager");
        urowc(g, "Boot Device", "None", bootX, bootW, top + 22, UEFI_AMBER);
        urowc(g, "OS Status", "Not Inst.", bootX, bootW, top + 37, UEFI_AMBER);

        hitX = bootX + 10;
        hitY = top + 56;
        hitW = bootW - 20;
        hitH = 22;
        final boolean hot = overHit(mouseX, mouseY);
        g.fill(hitX, hitY, hitX + hitW, hitY + hitH, hot ? UEFI_TEXT : UEFI_ACCENT);
        drawCentered(g, "INSTALL OS", hitX + hitW / 2, hitY + 7, 0xFF0B1018);

        g.fill(x, y + H - 16, x + W, y + H, UEFI_HEAD);
        final String hint = "Click INSTALL OS  -  ESC: Exit";
        g.drawString(font, hint, x + W - font.width(hint) - 10, y + H - 12, UEFI_DIM, false);
    }

    private void panel(final GuiGraphics g, final int x, final int y, final int w, final int h,
                       final String header) {
        g.fill(x, y, x + w, y + h, UEFI_PANEL);
        g.fill(x, y, x + w, y + 14, UEFI_PH);
        g.fill(x, y, x + 3, y + 14, UEFI_ACCENT);
        g.drawString(font, header, x + 7, y + 3, UEFI_TEXT, false);
    }

    private void urow(final GuiGraphics g, final String k, final String v, final int x, final int w,
                      final int y) {
        urowc(g, k, v, x, w, y, UEFI_TEXT);
    }

    private void urowc(final GuiGraphics g, final String k, final String v, final int x, final int w,
                       final int y, final int vColor) {
        g.drawString(font, k, x + 8, y, UEFI_KEY, false);
        g.drawString(font, v, x + w - font.width(v) - 8, y, vColor, false);
    }

    // ---------------------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------------------

    private void drawCentered(final GuiGraphics g, final String s, final int cx, final int y, final int color) {
        g.drawString(font, s, cx - font.width(s) / 2, y, color, false);
    }

    private void drawWrapped(final GuiGraphics g, final String text, final int x, final int y,
                             final int maxW, final int color) {
        final StringBuilder lineBuf = new StringBuilder();
        int ly = y;
        for (final String word : text.split(" ")) {
            final String trial = lineBuf.isEmpty() ? word : lineBuf + " " + word;
            if (font.width(trial) > maxW && !lineBuf.isEmpty()) {
                g.drawString(font, lineBuf.toString(), x, ly, color, false);
                ly += 10;
                lineBuf.setLength(0);
                lineBuf.append(word);
            } else {
                lineBuf.setLength(0);
                lineBuf.append(trial);
            }
        }
        if (!lineBuf.isEmpty()) {
            g.drawString(font, lineBuf.toString(), x, ly, color, false);
        }
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (button == 0 && overHit(mouseX, mouseY)) {
            PacketDistributor.sendToServer(new InstallOsPayload(computerPos));
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(final int key, final int scan, final int mods) {
        if (key == 256) { // ESC
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
