/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.computronics.operation.payload.RequestSettingsPayload;
import dev.jstech.computronics.operation.payload.SetSettingPayload;
import dev.jstech.computronics.operation.payload.SettingsSnapshotPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The Settings desktop app: one per-computer control panel, drawn through the running OS skin so its
 * form changes with the OS (a basic bevel on Frames 95, the richest layout on Frames 11). A left nav
 * lists eight pages — six live, two placeholders — and the right pane edits or shows each one.
 *
 * <p>All editable knobs round-trip through the server: opening the app requests a
 * {@link SettingsSnapshotPayload}, and every change sends a {@link SetSettingPayload} and redraws
 * from the refreshed snapshot the server replies with.
 */
public final class SettingsApp implements DesktopApp {

    private static final String[] NAV = {
            "Personalize", "System", "Network", "Storage", "Display", "Programs", "Sound", "Users"};
    private static final int FIRST_SOON = 6;

    private static final int[] ACCENTS = {
            0xFF3A6AE0, 0xFF12A26F, 0xFFD1633F, 0xFF7B52C9, 0xFFC93D6A, 0xFFC98320};
    private static final String[] THEMES = {"system", "ocean", "slate"};

    /** One clickable region built during render and hit-tested on click. */
    private record Hit(int x, int y, int w, int h, Runnable onClick) {
        boolean contains(final double mx, final double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final BlockPos host;
    private OsSkin skin = OsSkin.fallback();
    private SettingsSnapshotPayload data;
    private int page;
    private final List<Hit> hits = new ArrayList<>();

    private boolean editingName;
    private final StringBuilder nameBuf = new StringBuilder();

    private static SettingsApp active;

    /** The monitor this desktop runs on (the firmware restart reopens setup there); null when unknown. */
    @org.jetbrains.annotations.Nullable
    private BlockPos monitorPos;

    /** The desktop form: knows its monitor, so "Restart to firmware" can reopen the setup on it. */
    public SettingsApp(final BlockPos host, @org.jetbrains.annotations.Nullable final BlockPos monitorPos) {
        this(host);
        this.monitorPos = monitorPos;
    }

    public SettingsApp(final BlockPos host) {
        this.host = host;
        active = this;
        PacketDistributor.sendToServer(new RequestSettingsPayload(host));
    }

    @Override
    public void onRestored() {
        active = this;
        PacketDistributor.sendToServer(new RequestSettingsPayload(host));
    }

    /** Routes a settings snapshot reply to the open Settings window. */
    public static void accept(final SettingsSnapshotPayload payload) {
        if (active != null && active.host.equals(payload.hostPos())) {
            active.data = payload;
            // Reflect accent, brightness, clock, wallpaper, taskbar layout and dark mode on the live desktop now.
            DesktopScreen.applyLivePrefs(payload.accent(), payload.brightness(), payload.clock12h(),
                    payload.wallpaper(), payload.taskbarCentered(), payload.darkMode());
        }
    }

    @Override public String title() {
        return "Settings";
    }

    @Override public int defaultWidth() {
        return 262;
    }

    @Override public int defaultHeight() {
        return 224;
    }

    @Override public int minWidth() {
        return 236;
    }

    @Override public int minHeight() {
        return 160;
    }

    @Override public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
    }

    private void set(final String key, final String value) {
        PacketDistributor.sendToServer(new SetSettingPayload(host, key, value));
    }

    // -------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        hits.clear();
        g.fill(x, y, x + width, y + height, skin.windowBg());

        // Left navigation.
        final int navW = 78;
        final int rowH = 15;
        for (int i = 0; i < NAV.length; i++) {
            final int ry = y + 4 + i * rowH;
            final boolean soon = i >= FIRST_SOON;
            final boolean sel = page == i;
            final boolean hov = inRect(mouseX, mouseY, x + 3, ry, navW, rowH);
            skin.listRow(g, x + 3, ry, navW, rowH, hov, sel);
            final int tc = sel ? skin.listRowText(true) : (soon ? skin.dim() : skin.text());
            g.drawString(font, NAV[i], x + 8, ry + 4, tc, false);
            final int target = i;
            hits.add(new Hit(x + 3, ry, navW, rowH, () -> {
                page = target;
                editingName = false;
            }));
        }
        // Divider.
        g.fill(x + navW + 5, y + 3, x + navW + 6, y + height - 3, skin.edge());

        final int px = x + navW + 11;
        final int pw = width - navW - 15;
        if (data == null) {
            g.drawString(font, "Loading...", px, y + 8, skin.dim(), false);
            return;
        }
        switch (page) {
            case 0 -> personalize(g, font, px, y + 6, pw, mouseX, mouseY);
            case 1 -> system(g, font, px, y + 6, pw, mouseX, mouseY);
            case 2 -> network(g, font, px, y + 6, pw, mouseX, mouseY);
            case 3 -> storage(g, font, px, y + 6, pw, mouseX, mouseY);
            case 4 -> display(g, font, px, y + 6, pw, mouseX, mouseY);
            case 5 -> programs(g, font, px, y + 6, pw, mouseX, mouseY);
            default -> comingSoon(g, font, px, y + 6, pw, height - 12);
        }
    }

    private void heading(final GuiGraphics g, final Font font, final String title, final int x, final int y) {
        // No shadow: on the light XP/11 panels a dark-on-light shadow reads as a muddy duplicate.
        g.drawString(font, title, x, y, skin.text(), false);
    }

    private void personalize(final GuiGraphics g, final Font font, final int x, int y, final int w,
                             final int mouseX, final int mouseY) {
        heading(g, font, "Personalize", x, y);
        y += 13;
        g.drawString(font, "Wallpaper", x, y, skin.dim(), false);
        y += 10;
        final int tw = 34;
        final int th = 21;
        for (int i = 0; i < WallpaperPainter.STYLES.length; i++) {
            final int sx = x + i * (tw + 4);
            final String style = WallpaperPainter.STYLES[i];
            wallpaperSwatch(g, sx, y, tw, th, style);
            if (style.equals(data.wallpaper())) {
                OsSkin.outline(g, sx - 1, y - 1, tw + 2, th + 2, skin.accent());
            } else {
                OsSkin.outline(g, sx, y, tw, th, skin.edge());
            }
            hits.add(new Hit(sx, y, tw, th, () -> set("wallpaper", style)));
        }
        y += th + 8;

        // Accent and theme only on the richer skins (scales with the OS).
        if (skin.form() != OsSkin.Form.BEVEL) {
            g.drawString(font, "Accent", x, y, skin.dim(), false);
            y += 10;
            for (int i = 0; i < ACCENTS.length; i++) {
                final int sx = x + i * 18;
                g.fill(sx, y, sx + 14, y + 14, ACCENTS[i]);
                final boolean on = (data.accent() & 0xFFFFFF) == (ACCENTS[i] & 0xFFFFFF);
                OsSkin.outline(g, sx - (on ? 1 : 0), y - (on ? 1 : 0), 14 + (on ? 2 : 0), 14 + (on ? 2 : 0),
                        on ? skin.text() : skin.edge());
                final int argb = ACCENTS[i];
                hits.add(new Hit(sx, y, 14, 14, () -> set("accent", String.format(java.util.Locale.ROOT,
                        "%06X", argb & 0xFFFFFF))));
            }
            y += 22;
            g.drawString(font, "Theme", x, y, skin.dim(), false);
            y += 10;
            int tx = x;
            for (final String theme : THEMES) {
                final boolean on = theme.equals(data.themePreset().isEmpty() ? "system" : data.themePreset());
                final int bw = font.width(theme) + 12;
                skin.button(g, font, tx, y, bw, 13, theme, inRect(mouseX, mouseY, tx, y, bw, 13), false, on);
                hits.add(new Hit(tx, y, bw, 13, () -> set("theme", theme)));
                tx += bw + 4;
            }
            y += 19;
        }
        g.drawString(font, "Clock", x, y, skin.dim(), false);
        y += 10;
        toggleButtons(g, font, x, y, w, mouseX, mouseY, "24-hour", "12-hour", !data.clock12h(),
                () -> set("clock", "24h"), () -> set("clock", "12h"));
        y += 19;

        // Taskbar alignment and dark mode are Frames 11 concepts only, so they appear exclusively on the flat skin.
        if (skin.form() == OsSkin.Form.FLAT) {
            g.drawString(font, "Taskbar", x, y, skin.dim(), false);
            y += 10;
            toggleButtons(g, font, x, y, w, mouseX, mouseY, "Center", "Left", data.taskbarCentered(),
                    () -> set("taskbar", "center"), () -> set("taskbar", "left"));
            y += 19;
            g.drawString(font, "Appearance", x, y, skin.dim(), false);
            y += 10;
            toggleButtons(g, font, x, y, w, mouseX, mouseY, "Light", "Dark", !data.darkMode(),
                    () -> set("darkmode", "off"), () -> set("darkmode", "on"));
        }
    }

    private void system(final GuiGraphics g, final Font font, final int x, int y, final int w,
                        final int mouseX, final int mouseY) {
        heading(g, font, "System", x, y);
        y += 13;
        g.drawString(font, "Computer name", x, y, skin.dim(), false);
        y += 10;
        final int fw = Math.min(w, 130);
        skin.field(g, x, y, fw, 13, editingName);
        final String shown = editingName ? nameBuf + "_"
                : (data.computerName().isEmpty() ? "(unnamed)" : data.computerName());
        g.drawString(font, shown, x + 4, y + 3, skin.text(), false);
        hits.add(new Hit(x, y, fw, 13, () -> {
            editingName = true;
            nameBuf.setLength(0);
            nameBuf.append(data.computerName());
        }));
        y += 20;

        heading(g, font, "About", x, y);
        y += 12;
        y = specRow(g, font, x, y, w, "Processor", data.cpuLabel() + " - " + data.cpuMhz() + " MHz");
        if (data.ramMb() > 0) {
            y = specRow(g, font, x, y, w, "Memory", group(data.ramMb()) + " it");
        }
        if (data.vramMb() > 0) {
            y = specRow(g, font, x, y, w, "Graphics", data.vramMb() + " MB VRAM");
        }
        y = specRow(g, font, x, y, w, "System", prettyOs(data.osLabel()));
        y = specRow(g, font, x, y, w, "Platform", data.platform());
        // Restart into the firmware setup (the boot manager): the way to reach it once an OS is installed.
        if (monitorPos != null) {
            y += 4;
            final String label = "Restart to firmware";
            final int bw = font.width(label) + 12;
            skin.button(g, font, x, y, bw, 13, label, inRect(mouseX, mouseY, x, y, bw, 13), false, false);
            hits.add(new Hit(x, y, bw, 13, () -> PacketDistributor.sendToServer(
                    new dev.jstech.computronics.operation.payload.RequestFirmwarePayload(
                            host, monitorPos))));
        }
    }

    private void network(final GuiGraphics g, final Font font, final int x, int y, final int w,
                         final int mouseX, final int mouseY) {
        heading(g, font, "Network", x, y);
        y += 13;
        g.drawString(font, "System disk public share", x, y, skin.dim(), false);
        y += 11;
        final int permille = data.netshare();
        final String label = String.format(java.util.Locale.ROOT, "%.1f%% (%d/1000)", permille / 10.0, permille);
        stepper(g, font, x, y, mouseX, mouseY, label,
                () -> set("netshare", Integer.toString(Math.max(0, permille - 50))),
                () -> set("netshare", Integer.toString(Math.min(1000, permille + 50))));
        y += 20;
        // A share bar.
        g.fill(x, y, x + Math.min(w, 150), y + 6, skin.fieldBg());
        g.fill(x, y, x + Math.min(w, 150) * permille / 1000, y + 6, skin.accent());
        OsSkin.outline(g, x, y, Math.min(w, 150), 6, skin.edge());
        y += 16;
        g.drawString(font, "Link: on the data network", x, y, skin.dim(), false);
    }

    private void storage(final GuiGraphics g, final Font font, final int x, int y, final int w,
                         final int mouseX, final int mouseY) {
        heading(g, font, "Storage", x, y);
        y += 13;
        for (final SettingsSnapshotPayload.DiskUse d : data.disks()) {
            final String cap = (d.capMb() >= 1000 ? (d.capMb() / 1000) + " GB" : d.capMb() + " MB");
            g.drawString(font, d.label() + (d.system() ? "  [sys]" : ""), x, y, skin.text(), false);
            g.drawString(font, cap, x + w - font.width(cap), y, skin.dim(), false);
            y += 10;
            final int barW = Math.min(w, 150);
            g.fill(x, y, x + barW, y + 5, skin.fieldBg());
            final long frac = d.capMb() > 0 ? Math.min(barW, barW * d.usedMb() / d.capMb()) : 0;
            g.fill(x, y, x + (int) frac, y + 5, skin.accent());
            OsSkin.outline(g, x, y, barW, 5, skin.edge());
            y += 11;
        }
        if (data.disks().isEmpty()) {
            g.drawString(font, "No disks installed", x, y, skin.dim(), false);
        }
    }

    private void display(final GuiGraphics g, final Font font, final int x, int y, final int w,
                         final int mouseX, final int mouseY) {
        heading(g, font, "Display", x, y);
        y += 13;
        g.drawString(font, "Brightness", x, y, skin.dim(), false);
        y += 10;
        final int b = data.brightness();
        stepper(g, font, x, y, mouseX, mouseY, b + "%",
                () -> set("brightness", Integer.toString(Math.max(0, b - 10))),
                () -> set("brightness", Integer.toString(Math.min(100, b + 10))));
        y += 20;
        g.drawString(font, "Monitor: linked display", x, y, skin.dim(), false);
    }

    private void programs(final GuiGraphics g, final Font font, final int x, int y, final int w,
                          final int mouseX, final int mouseY) {
        heading(g, font, "Programs", x, y);
        y += 13;
        if (data.installed().isEmpty()) {
            g.drawString(font, "No programs installed", x, y, skin.dim(), false);
            return;
        }
        for (final String id : data.installed()) {
            final net.minecraft.resources.ResourceLocation rl =
                    net.minecraft.resources.ResourceLocation.tryParse(id);
            final dev.jstech.computronics.os.ProgramSpec spec =
                    rl == null ? null : dev.jstech.computronics.os.OsRegistry.getProgram(rl);
            final String name = spec != null ? spec.displayName()
                    : (id.contains(":") ? id.substring(id.indexOf(':') + 1) : id);
            g.drawString(font, "- " + name, x, y, skin.text(), false);
            // Per-row uninstall: the desktop counterpart of the shell's package removal.
            final String btn = "[Uninstall]";
            final int bw = font.width(btn);
            final int bx = x + w - bw;
            final boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= y - 1 && mouseY < y + 10;
            g.drawString(font, btn, bx, y, hover ? skin.text() : skin.dim(), false);
            hits.add(new Hit(bx, y - 1, bw, 11, () -> {
                PacketDistributor.sendToServer(
                        new dev.jstech.computronics.operation.payload.UninstallProgramPayload(host, id));
                // The uninstall lands before these refreshes are processed (same connection, in order).
                PacketDistributor.sendToServer(new RequestSettingsPayload(host));
                DesktopScreen.refreshActive();
            }));
            y += 12;
        }
    }

    private void comingSoon(final GuiGraphics g, final Font font, final int x, final int y,
                            final int w, final int h) {
        final String a = NAV[page];
        final String line1 = a;
        final String line2 = "Coming in a future update";
        g.drawString(font, line1, x + (w - font.width(line1)) / 2, y + h / 2 - 10, skin.text(), false);
        g.drawString(font, line2, x + (w - font.width(line2)) / 2, y + h / 2 + 2, skin.dim(), false);
    }

    // -------------------------------------------------------------------------
    // Small controls
    // -------------------------------------------------------------------------

    private int specRow(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                        final String label, final String value) {
        g.drawString(font, label, x, y, skin.dim(), false);
        g.drawString(font, value, x + w - font.width(value), y, skin.text(), false);
        return y + 11;
    }

    private void toggleButtons(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                               final int mouseX, final int mouseY, final String a, final String b,
                               final boolean aOn, final Runnable onA, final Runnable onB) {
        final int aw = font.width(a) + 12;
        final int bw = font.width(b) + 12;
        skin.button(g, font, x, y, aw, 13, a, inRect(mouseX, mouseY, x, y, aw, 13), false, aOn);
        skin.button(g, font, x + aw + 4, y, bw, 13, b, inRect(mouseX, mouseY, x + aw + 4, y, bw, 13), false, !aOn);
        hits.add(new Hit(x, y, aw, 13, onA));
        hits.add(new Hit(x + aw + 4, y, bw, 13, onB));
    }

    private void stepper(final GuiGraphics g, final Font font, final int x, final int y,
                         final int mouseX, final int mouseY, final String value,
                         final Runnable dec, final Runnable inc) {
        skin.button(g, font, x, y, 15, 13, "-", inRect(mouseX, mouseY, x, y, 15, 13), false, false);
        hits.add(new Hit(x, y, 15, 13, dec));
        g.drawString(font, value, x + 21, y + 3, skin.text(), false);
        final int ix = x + 21 + Math.max(38, font.width(value) + 6);
        skin.button(g, font, ix, y, 15, 13, "+", inRect(mouseX, mouseY, ix, y, 15, 13), false, false);
        hits.add(new Hit(ix, y, 15, 13, inc));
    }

    private boolean inRect(final int mx, final int my, final int x, final int y, final int w, final int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static String group(final long n) {
        return String.format(java.util.Locale.ROOT, "%,d", n);
    }

    /** A representative wallpaper preview that always fits the thumbnail (the real painter overflows small sizes). */
    private static void wallpaperSwatch(final GuiGraphics g, final int x, final int y, final int w, final int h,
                                        final String style) {
        switch (style) {
            case "win95" -> g.fill(x, y, x + w, y + h, 0xFF1C7C7C);
            case "winxp" -> g.fillGradient(x, y, x + w, y + h, 0xFF5B95DD, 0xFF2C5FA8);
            case "win11" -> {
                g.fillGradient(x, y, x + w, y + h, 0xFF2C3C68, 0xFF141C33);
                g.fill(x + w / 2 - 3, y + h / 2 - 3, x + w / 2 + 3, y + h / 2 + 3, 0x55FFFFFF);
            }
            default -> g.fill(x, y, x + w, y + h, 0xFF3A6A9A);
        }
    }

    private static String prettyOs(final String path) {
        return switch (path) {
            case "frames_95" -> "Frames 95";
            case "frames_xp" -> "Frames XP";
            case "frames_11" -> "Frames 11";
            case "mc_dos" -> "MC-DOS";
            case "mc_net" -> "MC-NET";
            default -> path;
        };
    }

    // -------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY,
                             final int button) {
        if (button != 0) {
            return;
        }
        for (final Hit h : hits) {
            if (h.contains(mouseX, mouseY)) {
                h.onClick().run();
                return;
            }
        }
        editingName = false;
    }

    @Override
    public boolean charTyped(final char c) {
        if (editingName && c >= 32 && c != 127 && nameBuf.length() < 24) {
            nameBuf.append(c);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (!editingName) {
            return false;
        }
        if (key == 259 && nameBuf.length() > 0) { // backspace
            nameBuf.deleteCharAt(nameBuf.length() - 1);
            return true;
        }
        if (key == 257 || key == 335) { // enter
            set("name", nameBuf.toString());
            editingName = false;
            return true;
        }
        if (key == 256) { // esc cancels the edit
            editingName = false;
            return true;
        }
        return false;
    }
}
