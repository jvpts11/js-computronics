/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.os;

import dev.jsc.jscomputronics.module.computing.gui.layout.ThisPcLayout;
import dev.jsc.jscomputronics.module.computing.operation.payload.EjectMediaPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.InstallFromMediaPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RenameVolumePayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RequestThisPcPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.SetSettingPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.ThisPcPayload;
import dev.jsc.jscomputronics.module.computing.os.MinSpecTooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The "This PC" desktop app, a system page: the machine itself in a card (name, kind, era, system,
 * network), then its devices and drives (each disk with a usage bar and its system, each linked drive
 * naming what is in it and, for an installer, what it would install, with Install, Open and Eject),
 * the hardware seated in it, and the programs installed on it as a grid. Everything is fetched from
 * the server and refreshes after an action. The geometry lives in {@link ThisPcLayout}.
 */
public final class ThisPcApp implements DesktopApp {

    /** The usage bar's segments: the system, the items stored, and the files/programs. */
    private static final int SEG_OS = 0xFF3F77C8;
    private static final int SEG_STORE = 0xFF5B9E5B;
    private static final int SEG_FILES = 0xFFD79A3A;
    private static final int GREEN = 0xFF2E7D32;
    private static final int AMBER = 0xFFB35C00;

    private OsSkin skin = OsSkin.fallback();

    private final BlockPos host;
    /** The window's name, which differs by platform: "This PC" on Frames, "Disks" on Linux. */
    private final String title;
    private ThisPcPayload data = new ThisPcPayload(ThisPcPayload.WireMachine.EMPTY, List.of(), List.of(), List.of());
    private int scroll;
    private int contentW = ThisPcLayout.DEFAULT_W;
    private int contentH = ThisPcLayout.DEFAULT_H;

    private boolean renaming;
    private final StringBuilder renameBuf = new StringBuilder();
    private long lastClickAt;
    private String lastClickKey = "";
    private String selectedKey = "";

    /** What the last render put on screen, so a click resolves against what was drawn. */
    private final List<Hit> hits = new ArrayList<>();

    private static ThisPcApp active;

    /** A clickable region: a button, a row or a program cell, with the action a click runs. */
    private record Hit(String key, int x, int y, int w, int h, Runnable action, boolean button) {
    }

    public ThisPcApp(final BlockPos host) {
        this(host, "This PC");
    }

    public ThisPcApp(final BlockPos host, final String title) {
        this.host = host;
        this.title = title;
        active = this;
        request();
    }

    /** Routes a "This PC" listing reply to the open window. */
    public static void accept(final ThisPcPayload payload) {
        if (active != null) {
            active.data = payload;
        }
    }

    @Override
    public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
    }

    private void request() {
        PacketDistributor.sendToServer(new RequestThisPcPayload(host));
    }

    @Override
    public void onRestored() {
        active = this;
        request();
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public int defaultWidth() {
        return ThisPcLayout.DEFAULT_W;
    }

    @Override
    public int defaultHeight() {
        return ThisPcLayout.DEFAULT_H;
    }

    @Override
    public int minWidth() {
        return ThisPcLayout.MIN_W;
    }

    @Override
    public int minHeight() {
        return ThisPcLayout.MIN_H;
    }

    private boolean linux() {
        return !skin.osPath().startsWith("frames_");
    }

    // ---- rendering -------------------------------------------------------------------------

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        active = this;
        this.contentW = width;
        this.contentH = height;
        hits.clear();
        g.fill(x, y, x + width, y + height, skin.windowBg());

        renderCard(g, font, x, y, width, mouseX, mouseY);

        // The page below the card scrolls; everything it draws is clipped to it.
        final int pageY = y + ThisPcLayout.pageY();
        final int pageH = ThisPcLayout.pageH(height);
        g.fill(x, pageY - 1, x + width, pageY, skin.edge());
        pushScissor(g, x, pageY, x + width, pageY + pageH);
        int cy = pageY + 2 - scroll;
        cy = renderDrives(g, font, x, cy, width, pageY, pageH, mouseX, mouseY);
        cy = renderHardware(g, font, x, cy, width);
        cy = renderPrograms(g, font, x, cy, width, mouseX, mouseY);
        g.disableScissor();
        // The page's full height decides how far it scrolls.
        final int total = cy + scroll - (pageY + 2);
        final int max = Math.max(0, total - pageH + 2);
        if (scroll > max) {
            scroll = max;
        }
        if (scroll < 0) {
            scroll = 0;
        }
        if (max > 0) {
            final int trackH = pageH - 2;
            final int thumbH = Math.max(8, trackH * pageH / (total + 2));
            final int thumbY = pageY + 1 + (trackH - thumbH) * scroll / max;
            skin.scrollThumb(g, x + width - 4, thumbY, 3, thumbH);
        }
    }

    /** GuiGraphics.enableScissor ignores the pose, so the desktop's translation must be added by hand. */
    private static void pushScissor(final GuiGraphics g, final int x1, final int y1, final int x2, final int y2) {
        final org.joml.Matrix4f pose = g.pose().last().pose();
        final int ox = Math.round(pose.m30());
        final int oy = Math.round(pose.m31());
        g.enableScissor(x1 + ox, y1 + oy, x2 + ox, y2 + oy);
    }

    private void renderCard(final GuiGraphics g, final Font font, final int x, final int y, final int width,
                            final int mouseX, final int mouseY) {
        final ThisPcPayload.WireMachine m = data.machine();
        final int ix = x + 4;
        final int iy = y + 4;
        // The machine's icon: a tower with a lit power dot.
        g.fill(ix, iy, ix + ThisPcLayout.CARD_ICON_W, iy + ThisPcLayout.CARD_H - 8, 0xFF2E3238);
        g.fill(ix + 2, iy + 2, ix + ThisPcLayout.CARD_ICON_W - 2, iy + 7, 0xFF1C1F24);
        g.fill(ix + ThisPcLayout.CARD_ICON_W - 6, iy + 3, ix + ThisPcLayout.CARD_ICON_W - 4, iy + 5,
                m.buildValid() ? 0xFF39D6C4 : 0xFFEF6A5A);
        g.fill(ix + 3, iy + 10, ix + ThisPcLayout.CARD_ICON_W - 3, iy + ThisPcLayout.CARD_H - 11, 0xFF3D434C);
        OsSkin.outline(g, ix, iy, ThisPcLayout.CARD_ICON_W, ThisPcLayout.CARD_H - 8, 0xFF1C1F24);

        final int tx = x + 4 + ThisPcLayout.CARD_ICON_W + 4;
        final int renameW = 34;
        final int textMax = width - (tx - x) - renameW - 8;
        final String name = renaming ? renameBuf + "_" : (m.name().isEmpty() ? m.kind() : m.name());
        g.drawString(font, trim(font, name, textMax), tx, y + 3, skin.text(), false);
        final String kindLine = m.name().isEmpty() ? m.era() + " era" : m.kind() + " · " + m.era() + " era";
        g.drawString(font, trim(font, kindLine, textMax), tx, y + 12, skin.dim(), false);
        final String system = m.osLabel().isEmpty() ? "No system installed"
                : m.osLabel() + " · " + dev.jsc.jscomputronics.module.computing.os.Branding.houseOf(m.osLabel()).name()
                        + " " + m.osYear();
        final String net = m.networkLabel().isEmpty() ? "not on a network" : "network " + m.networkLabel();
        g.drawString(font, trim(font, system + " · " + net, textMax), tx, y + 21,
                m.osLabel().isEmpty() ? AMBER : skin.dim(), false);
        final int bx = x + width - renameW - 3;
        final int by = y + 4;
        button(g, font, "card-rename", bx, by, renameW, ThisPcLayout.BTN_H, "Rename", mouseX, mouseY, false,
                this::startRename);
    }

    private int renderDrives(final GuiGraphics g, final Font font, final int x, int cy, final int width,
                             final int pageY, final int pageH, final int mouseX, final int mouseY) {
        cy = header(g, font, x, cy, width, "Devices and drives");
        if (data.disks().isEmpty() && data.media().isEmpty()) {
            g.drawString(font, "No disks installed and no drives linked", x + 8, cy + 3, skin.dim(), false);
            return cy + ThisPcLayout.KV_ROW_H + 4;
        }
        int letter = 0;
        for (final ThisPcPayload.WireDisk d : data.disks()) {
            final String key = "disk:" + d.slot();
            final String drive = linux() ? "" : (char) ('C' + letter) + ":";
            letter++;
            row(g, key, x, cy, width, mouseX, mouseY, () -> DesktopScreen.requestOpenFiles(""));
            drawDiskIcon(g, x + 4, cy + 5);
            final int tx = x + ThisPcLayout.TEXT_X;
            final int maxW = ThisPcLayout.driveTextMaxW(width, 1);
            final String label = d.label() + (drive.isEmpty() ? "" : "  " + drive);
            g.drawString(font, trim(font, label, maxW), tx, cy + 2, textColor(key), false);
            if (d.system()) {
                final String badge = "System" + (d.osPath().isEmpty() ? "" : " · " + prettyOs(d.osPath()));
                final int bw = font.width(badge);
                if (font.width(label) + 6 + bw <= maxW) {
                    g.drawString(font, badge, tx + maxW - bw, cy + 2, GREEN, false);
                }
            }
            // The bar is segmented by what actually takes the space, so "the disk is full" always
            // comes with "of what": the system, the items stored on it, or its files.
            final int barW = maxW - 70;
            g.fill(tx, cy + 12, tx + barW, cy + 15, 0xFFD7DBE4);
            final long cap = Math.max(1, d.capItems());
            int segX = tx;
            for (final long[] part : new long[][]{{d.osItems(), SEG_OS}, {d.storeItems(), SEG_STORE}, {d.fileItems(), SEG_FILES}}) {
                final int w = (int) Math.min(tx + barW - segX, barW * part[0] / cap);
                if (w > 0) {
                    g.fill(segX, cy + 12, segX + w, cy + 15, (int) part[1]);
                    segX += w;
                }
            }
            final String usage = d.freeItems() + " of " + d.capItems() + " it free";
            g.drawString(font, usage, tx + maxW - font.width(usage), cy + 11, skin.dim(), false);
            button(g, font, key + ":open", ThisPcLayout.buttonX(width, 0, 1) + x, cy + (ThisPcLayout.DRIVE_ROW_H - ThisPcLayout.BTN_H) / 2,
                    ThisPcLayout.BTN_W, ThisPcLayout.BTN_H, "Open", mouseX, mouseY, false,
                    () -> DesktopScreen.requestOpenFiles(""));
            cy += ThisPcLayout.DRIVE_ROW_H;
        }
        for (final ThisPcPayload.WireMedia m : data.media()) {
            final String key = "media:" + m.readerPos();
            final String drive = linux() ? "" : (char) ('C' + letter) + ":";
            letter++;
            final int buttons = (m.installable() ? 1 : 0) + (m.loaded() ? 2 : 0);
            row(g, key, x, cy, width, mouseX, mouseY, m.loaded() ? () -> DesktopScreen.requestOpenFiles(key) : null);
            drawMediaIcon(g, x + 4, cy + 5, m);
            final int tx = x + ThisPcLayout.TEXT_X;
            final int maxW = ThisPcLayout.driveTextMaxW(width, buttons);
            final String head = prettyDrive(m.drive()) + (drive.isEmpty() ? "" : "  " + drive)
                    + (m.loaded() ? "   " + m.mediaName() : "   no disc");
            g.drawString(font, trim(font, head, maxW), tx, cy + 2, m.loaded() ? textColor(key) : skin.dim(), false);
            final String detail;
            int detailColor = skin.dim();
            if (!m.loaded()) {
                detail = prettyDrive(m.drive()) + " drive, " + m.blocksAway() + " blocks away";
            } else if (m.kind().equals("OS_INSTALL")) {
                detail = "Installs " + (m.payloadName().isEmpty() ? m.payloadPath() : m.payloadName())
                        + (m.payloadYear() > 0 ? " · " + m.payloadYear() : "") + " · bootable"
                        + (m.packageId().isEmpty() ? "" : " · package " + m.packageId());
                detailColor = AMBER;
            } else if (m.kind().equals("PROGRAM_INSTALL")) {
                detail = (m.installable() ? "Installs " : "Installed: ")
                        + (m.payloadName().isEmpty() ? m.payloadPath() : m.payloadName())
                        + (m.payloadYear() > 0 ? " · " + m.payloadYear() : "")
                        + (m.packageId().isEmpty() ? "" : " · package " + m.packageId());
                detailColor = m.installable() ? GREEN : skin.dim();
            } else {
                detail = "Data medium · " + m.stored() + " stored";
            }
            g.drawString(font, trim(font, detail, maxW), tx, cy + 11, detailColor, false);
            if (!m.needs().isEmpty() && m.loaded() && !m.kind().equals("DATA")) {
                // The requirements ride on the tooltip; the row has room for one line.
                hits.add(new Hit(key + ":needs", tx, cy + 11, maxW, 9, () -> { }, false));
            }
            int slot = 0;
            final int by = cy + (ThisPcLayout.DRIVE_ROW_H - ThisPcLayout.BTN_H) / 2;
            if (m.installable()) {
                button(g, font, key + ":install", x + ThisPcLayout.buttonX(width, slot++, buttons), by, ThisPcLayout.BTN_W,
                        ThisPcLayout.BTN_H, "Install", mouseX, mouseY, true, () -> install(m.readerPos()));
            }
            if (m.loaded()) {
                button(g, font, key + ":open", x + ThisPcLayout.buttonX(width, slot++, buttons), by, ThisPcLayout.BTN_W,
                        ThisPcLayout.BTN_H, "Open", mouseX, mouseY, false, () -> DesktopScreen.requestOpenFiles(key));
                button(g, font, key + ":eject", x + ThisPcLayout.buttonX(width, slot, buttons), by, ThisPcLayout.BTN_W,
                        ThisPcLayout.BTN_H, "Eject", mouseX, mouseY, false, () -> eject(m.readerPos()));
            }
            cy += ThisPcLayout.DRIVE_ROW_H;
        }
        return cy + 2;
    }

    private int renderHardware(final GuiGraphics g, final Font font, final int x, int cy, final int width) {
        final ThisPcPayload.WireMachine m = data.machine();
        cy = header(g, font, x, cy, width, "Hardware");
        final List<String[]> kv = new ArrayList<>();
        kv.add(new String[]{"Board", m.boardLabel().isEmpty() ? "none" : m.boardLabel()});
        kv.add(new String[]{"Processor", m.cpuLabel().isEmpty() ? "none" : (m.cpuCount() > 1 ? m.cpuCount() + " × " : "") + m.cpuLabel()});
        kv.add(new String[]{"Memory", m.ramMb() > 0 ? m.ramMb() + " it" : "none"});
        kv.add(new String[]{"Graphics", m.gpuCount() > 0 ? m.gpuCount() + " × " + m.vramMb() + " MB VRAM" : "none"});
        kv.add(new String[]{"Power", m.psuLabel().isEmpty() ? "none" : m.psuLabel()});
        kv.add(new String[]{"Peripherals", m.peripherals().isEmpty() ? "none linked" : m.peripherals()});
        kv.add(new String[]{"Build", m.buildValid() ? "OK, the machine comes up" : "not valid"});
        final int keyW = 52;
        for (final String[] pair : kv) {
            g.drawString(font, pair[0], x + 8, cy + 1, skin.dim(), false);
            final int color = pair[0].equals("Build") ? (m.buildValid() ? GREEN : AMBER) : skin.text();
            g.drawString(font, trim(font, pair[1], width - 8 - keyW - 8), x + 8 + keyW, cy + 1, color, false);
            cy += ThisPcLayout.KV_ROW_H;
        }
        return cy + 2;
    }

    private int renderPrograms(final GuiGraphics g, final Font font, final int x, int cy, final int width,
                               final int mouseX, final int mouseY) {
        final List<String> programs = data.installedPrograms();
        cy = header(g, font, x, cy, width, "Installed programs  " + programs.size());
        if (programs.isEmpty()) {
            g.drawString(font, "None. Insert an installer, or run a package manager.", x + 8, cy + 3, skin.dim(), false);
            return cy + ThisPcLayout.KV_ROW_H + 4;
        }
        final int columns = ThisPcLayout.programColumns(width);
        for (int i = 0; i < programs.size(); i++) {
            final String id = programs.get(i);
            final int cx = x + ThisPcLayout.programCellX(width, i % columns);
            final int cellY = cy + (i / columns) * ThisPcLayout.PROG_CELL_H;
            final String key = "program:" + id;
            final boolean hover = inRect(mouseX, mouseY, cx, cellY, ThisPcLayout.PROG_CELL_W - 2, ThisPcLayout.PROG_CELL_H - 2);
            skin.listRow(g, cx, cellY, ThisPcLayout.PROG_CELL_W - 2, ThisPcLayout.PROG_CELL_H - 2, hover, key.equals(selectedKey));
            hits.add(new Hit(key, cx, cellY, ThisPcLayout.PROG_CELL_W - 2, ThisPcLayout.PROG_CELL_H - 2, () -> selectedKey = key, false));
            ProgramIcons.draw(g, cx + (ThisPcLayout.PROG_CELL_W - 2) / 2 - 6, cellY + 2, 12, 12, programIdOf(id), skin.osPath());
            final String name = trim(font, prettyProgram(id), ThisPcLayout.PROG_CELL_W - 6);
            g.drawString(font, name, cx + ((ThisPcLayout.PROG_CELL_W - 2) - font.width(name)) / 2, cellY + 16,
                    skin.listRowText(key.equals(selectedKey)), false);
            final String pkg = trim(font, packageIdOf(id), ThisPcLayout.PROG_CELL_W - 6);
            g.drawString(font, pkg, cx + ((ThisPcLayout.PROG_CELL_W - 2) - font.width(pkg)) / 2, cellY + 24,
                    skin.dim(), false);
        }
        final int rows = (programs.size() + columns - 1) / columns;
        return cy + rows * ThisPcLayout.PROG_CELL_H + 4;
    }

    private int header(final GuiGraphics g, final Font font, final int x, final int cy, final int width, final String text) {
        g.fill(x + 1, cy, x + width - 1, cy + ThisPcLayout.HEADER_H, skin.listHover());
        g.fill(x + 1, cy + ThisPcLayout.HEADER_H - 1, x + width - 1, cy + ThisPcLayout.HEADER_H, skin.edge());
        g.drawString(font, text, x + 4, cy + 2, skin.dim(), false);
        return cy + ThisPcLayout.HEADER_H + 1;
    }

    /** A drive row's background with hover and selection, registered as a hit for select and double-click. */
    private void row(final GuiGraphics g, final String key, final int x, final int cy, final int width,
                     final int mouseX, final int mouseY, @org.jetbrains.annotations.Nullable final Runnable open) {
        final boolean hover = inRect(mouseX, mouseY, x + 1, cy, width - 2, ThisPcLayout.DRIVE_ROW_H);
        skin.listRow(g, x + 1, cy, width - 2, ThisPcLayout.DRIVE_ROW_H, hover, key.equals(selectedKey));
        hits.add(new Hit(key, x + 1, cy, width - 2, ThisPcLayout.DRIVE_ROW_H, () -> {
            final long now = System.currentTimeMillis();
            if (key.equals(lastClickKey) && now - lastClickAt < 300 && open != null) {
                open.run();
            }
            lastClickKey = key;
            lastClickAt = now;
            selectedKey = key;
        }, false));
    }

    private void button(final GuiGraphics g, final Font font, final String key, final int bx, final int by,
                        final int w, final int h, final String label, final int mouseX, final int mouseY,
                        final boolean primary, final Runnable action) {
        final boolean hover = inRect(mouseX, mouseY, bx, by, w, h);
        skin.button(g, font, bx, by, w, h, label, hover, false, primary);
        hits.add(new Hit(key, bx, by, w, h, action, true));
    }

    private int textColor(final String key) {
        return skin.listRowText(key.equals(selectedKey));
    }

    private static void drawDiskIcon(final GuiGraphics g, final int x, final int y) {
        g.fill(x, y, x + 14, y + 10, 0xFF8B93A4);
        g.fill(x + 1, y + 1, x + 13, y + 9, 0xFFC7CDDA);
        g.fill(x + 2, y + 2, x + 12, y + 4, 0xFFEDF0F6);
        g.fill(x + 9, y + 6, x + 11, y + 8, 0xFF49E07A);
        OsSkin.outline(g, x, y, 14, 10, 0xFF5A6273);
    }

    private static void drawMediaIcon(final GuiGraphics g, final int x, final int y, final ThisPcPayload.WireMedia m) {
        if (!m.loaded()) {
            g.fill(x + 2, y, x + 12, y + 10, 0xFFEEF0F4);
            OsSkin.outline(g, x + 2, y, 10, 10, 0xFFC2C7D4);
            return;
        }
        switch (m.drive()) {
            case "DOCK_STATION" -> {
                g.fill(x + 1, y + 1, x + 13, y + 9, 0xFF2E3238);
                g.fill(x + 9, y + 3, x + 12, y + 7, 0xFFB8BEC8);
                OsSkin.outline(g, x + 1, y + 1, 12, 8, 0xFF1C1F24);
            }
            case "FLOPPY_DRIVE" -> {
                g.fill(x + 1, y, x + 13, y + 10, 0xFF1C2438);
                g.fill(x + 4, y + 1, x + 10, y + 4, 0xFFB8BEC8);
                OsSkin.outline(g, x + 1, y, 12, 10, 0xFF0B1220);
            }
            default -> {
                g.fill(x + 2, y, x + 12, y + 10, 0xFFB9C0CE);
                g.fill(x + 5, y + 3, x + 9, y + 7, 0xFFEDF0F6);
                OsSkin.outline(g, x + 2, y, 10, 10, 0xFF6E7686);
            }
        }
    }

    // ---- inspection (client tests; points are content-local, i.e. relative to window.x()+4 / window.y()+18)

    /** The index of the first drive whose medium name contains {@code nameContains}, or -1. */
    public int mediaRowIndex(final String nameContains) {
        for (int i = 0; i < data.media().size(); i++) {
            if (data.media().get(i).mediaName().contains(nameContains)) {
                return i;
            }
        }
        return -1;
    }

    public boolean isInstallable(final int mediaIndex) {
        return mediaIndex >= 0 && mediaIndex < data.media().size() && data.media().get(mediaIndex).installable();
    }

    /** Content-local centre of the "Install" button of drive {@code mediaIndex}, from the last render. */
    public int[] installButtonCenter(final int mediaIndex) {
        if (mediaIndex < 0 || mediaIndex >= data.media().size()) {
            return new int[]{0, 0};
        }
        final String key = "media:" + data.media().get(mediaIndex).readerPos() + ":install";
        for (final Hit h : hits) {
            if (h.key().equals(key)) {
                return new int[]{h.x() - lastX + h.w() / 2, h.y() - lastY + h.h() / 2};
            }
        }
        return new int[]{0, 0};
    }

    private int lastX;
    private int lastY;

    // ---- input -----------------------------------------------------------------------------

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY,
                             final int button) {
        lastX = window.x() + 4;
        lastY = window.y() + 18;
        if (renaming) {
            commitRename();
        }
        // Buttons first, then rows and cells, so a button on a row wins over the row under it.
        for (final Hit h : hits) {
            if (h.button() && inRect(mouseX, mouseY, h.x(), h.y(), h.w(), h.h())) {
                h.action().run();
                return;
            }
        }
        for (final Hit h : hits) {
            if (!h.button() && inRect(mouseX, mouseY, h.x(), h.y(), h.w(), h.h())) {
                h.action().run();
                return;
            }
        }
        selectedKey = "";
    }

    @Override
    public boolean mouseScrolled(final double delta) {
        scroll -= (int) (delta * ThisPcLayout.KV_ROW_H);
        return true;
    }

    private void install(final long readerPos) {
        PacketDistributor.sendToServer(new InstallFromMediaPayload(host, readerPos));
        request();
        // The install lands server-side before this refresh is processed, so the desktop's
        // launchers pick the new program up immediately.
        DesktopScreen.refreshActive();
    }

    private void eject(final long readerPos) {
        PacketDistributor.sendToServer(new EjectMediaPayload(host, readerPos));
        request();
    }

    private void startRename() {
        renaming = true;
        volumeRenameKey = "";
        renameBuf.setLength(0);
        renameBuf.append(data.machine().name());
    }

    private void commitRename() {
        final String name = renameBuf.toString().trim();
        renaming = false;
        if (!volumeRenameKey.isEmpty()) {
            // A disk or medium: the same relabel the explorer's tree offers.
            if (!name.isEmpty()) {
                PacketDistributor.sendToServer(new RenameVolumePayload(host, volumeRenameKey, name));
            }
            volumeRenameKey = "";
            request();
            return;
        }
        if (!name.equals(data.machine().name())) {
            // The machine's own name, the same setting the Settings app writes.
            PacketDistributor.sendToServer(new SetSettingPayload(host, "name", name));
            request();
        }
    }

    @Override
    public boolean charTyped(final char c) {
        if (renaming && c >= 32 && c != 127 && renameBuf.length() < 32) {
            renameBuf.append(c);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (renaming) {
            switch (key) {
                case 257, 335 -> commitRename();
                case 259 -> {
                    if (renameBuf.length() > 0) {
                        renameBuf.deleteCharAt(renameBuf.length() - 1);
                    }
                }
                case 256 -> renaming = false;
                default -> {
                    return false;
                }
            }
            return true;
        }
        if (key == 291) {
            // F2 renames the machine, or the selected disk or medium.
            if (selectedKey.startsWith("disk:") || selectedKey.startsWith("media:")) {
                startVolumeRename();
            } else {
                startRename();
            }
            return true;
        }
        return false;
    }

    private void startVolumeRename() {
        // A disk or medium is relabelled through the same volume rename the explorer uses; the
        // machine card's inline field is reused for the typing.
        renaming = true;
        renameBuf.setLength(0);
        if (selectedKey.startsWith("disk:")) {
            final int slot = Integer.parseInt(selectedKey.substring(5));
            for (final ThisPcPayload.WireDisk d : data.disks()) {
                if (d.slot() == slot) {
                    renameBuf.append(d.label());
                }
            }
        } else {
            final long pos = Long.parseLong(selectedKey.substring(6));
            for (final ThisPcPayload.WireMedia m : data.media()) {
                if (m.readerPos() == pos) {
                    renameBuf.append(m.mediaName());
                }
            }
        }
        volumeRenameKey = selectedKey;
    }

    private String volumeRenameKey = "";

    @Override
    public void renderTooltip(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY) {
        for (final Hit h : hits) {
            if (!inRect(mouseX, mouseY, h.x(), h.y(), h.w(), h.h())) {
                continue;
            }
            final List<Component> lines = tooltipFor(h.key());
            if (!lines.isEmpty()) {
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
                return;
            }
        }
    }

    private List<Component> tooltipFor(final String key) {
        if (key.startsWith("program:")) {
            final String id = key.substring(8);
            final List<Component> spec = MinSpecTooltip.programMinSpec(rl(id));
            final List<Component> lines = new ArrayList<>(spec.size() + 2);
            lines.add(Component.literal(prettyProgram(id)));
            lines.add(Component.literal(Component.translatable("program.jsc." + rl(id).getPath() + ".desc").getString())
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            lines.addAll(spec);
            return lines;
        }
        if (key.endsWith(":needs")) {
            final long pos = Long.parseLong(key.substring(6, key.length() - 6));
            for (final ThisPcPayload.WireMedia m : data.media()) {
                if (m.readerPos() == pos) {
                    final List<Component> lines = new ArrayList<>(3);
                    lines.add(Component.literal(m.payloadName().isEmpty() ? m.mediaName() : m.payloadName()));
                    for (final String need : m.needs().split(" · ")) {
                        if (!need.isBlank()) {
                            lines.add(Component.literal(need).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                        }
                    }
                    return lines;
                }
            }
        }
        if (key.startsWith("disk:") && !key.contains(":open")) {
            final int slot = Integer.parseInt(key.substring(5));
            for (final ThisPcPayload.WireDisk d : data.disks()) {
                if (d.slot() == slot) {
                    final List<Component> lines = new ArrayList<>(6);
                    lines.add(Component.literal(d.label()));
                    lines.add(Component.literal(d.osPath().isEmpty() ? "No system installed" : "System: " + prettyOs(d.osPath()))
                            .withStyle(d.osPath().isEmpty() ? net.minecraft.ChatFormatting.GRAY : net.minecraft.ChatFormatting.AQUA));
                    lines.add(Component.literal("  system   " + d.osItems() + " it").withStyle(net.minecraft.ChatFormatting.BLUE));
                    lines.add(Component.literal("  items    " + d.storeItems() + " it").withStyle(net.minecraft.ChatFormatting.GREEN));
                    lines.add(Component.literal("  files    " + d.fileItems() + " it").withStyle(net.minecraft.ChatFormatting.GOLD));
                    lines.add(Component.literal("  free     " + d.freeItems() + " it").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                    return lines;
                }
            }
        }
        return List.of();
    }

    // ---- helpers ---------------------------------------------------------------------------

    private static boolean inRect(final double mx, final double my, final int x, final int y, final int w, final int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static String prettyDrive(final String drive) {
        return switch (drive) {
            case "FLOPPY_DRIVE" -> "Floppy";
            case "CD_DRIVE" -> "CD";
            case "DVD_DRIVE" -> "DVD";
            case "DOCK_STATION" -> "USB";
            default -> drive;
        };
    }

    private static ResourceLocation rl(final String id) {
        final ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed != null) {
            return parsed;
        }
        final String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return ResourceLocation.fromNamespaceAndPath("jsc", path);
    }

    private static ResourceLocation programIdOf(final String id) {
        return ResourceLocation.tryParse(id.contains(":") ? id : "jsc:" + id);
    }

    /** A program's human name, read from the single registry rather than a duplicated switch. */
    private static String prettyProgram(final String id) {
        final dev.jsc.jscomputronics.module.computing.os.ProgramSpec spec =
                dev.jsc.jscomputronics.module.computing.os.OsRegistry.getProgram(rl(id));
        return spec != null ? spec.displayName() : rl(id).getPath();
    }

    /** The id a package manager installs a program by. */
    private static String packageIdOf(final String id) {
        final dev.jsc.jscomputronics.module.computing.os.ProgramSpec spec =
                dev.jsc.jscomputronics.module.computing.os.OsRegistry.getProgram(rl(id));
        return spec != null ? spec.commandName() : rl(id).getPath();
    }

    private static String prettyOs(final String osPath) {
        final dev.jsc.jscomputronics.module.computing.os.OsDef os =
                dev.jsc.jscomputronics.module.computing.os.OsRegistry.getOs(rl(osPath));
        return os != null ? os.displayName() : osPath;
    }

    private static String trim(final Font font, final String s, final int maxW) {
        String out = s;
        while (out.length() > 2 && font.width(out) > maxW) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }
}
