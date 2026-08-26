/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.os;

import dev.jsc.jscomputronics.module.computing.operation.payload.InstallFromMediaPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RenameVolumePayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RequestThisPcPayload;
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
 * The "This PC" desktop app: shows the disks installed in the computer (with a used/capacity bar and
 * a badge for the system disk), the removable media in its linked drives (with an Install button for
 * a program installer), and the programs installed on the computer. Lists are fetched from the
 * server and refresh after an install.
 */
public final class ThisPcApp implements DesktopApp {

    private static final int HEADER_H = 12;
    private static final int ROW_H = 20;
    private OsSkin skin = OsSkin.fallback();
    private int PANEL = 0xFFFFFFFF;
    private int EDGE = 0xFF6E7686;
    private int TEXT = 0xFF1A2230;
    private int SUB = 0xFF60687A;
    private int SEL_BG = 0xFF000080;

    private final BlockPos host;
    private final List<Row> rows = new ArrayList<>();
    private int scroll;
    private int selected = -1;
    private int renaming = -1;
    private final StringBuilder renameBuf = new StringBuilder();
    private String renameKey = "";
    private long lastClickAt;
    private int lastClickRow = -1;
    private int contentW = 240; // last content width, captured in renderContent for click math

    private static ThisPcApp active;

    private enum Kind { HEADER, DISK, MEDIA, PROGRAM, EMPTY }

    private record Row(Kind kind, ThisPcPayload.WireDisk disk, ThisPcPayload.WireMedia media, String text) {
        static Row header(final String t) {
            return new Row(Kind.HEADER, null, null, t);
        }

        static Row empty(final String t) {
            return new Row(Kind.EMPTY, null, null, t);
        }
    }

    public ThisPcApp(final BlockPos host) {
        this.host = host;
        active = this;
        request();
    }

    /** Routes a "This PC" listing reply to the open window. */
    public static void accept(final ThisPcPayload payload) {
        if (active != null) {
            active.rebuild(payload);
        }
    }

    @Override
    public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
        this.PANEL = osSkin.windowBg();
        this.EDGE = osSkin.edge();
        this.TEXT = osSkin.text();
        this.SUB = osSkin.dim();
        this.SEL_BG = osSkin.accent();
    }

    private void request() {
        PacketDistributor.sendToServer(new RequestThisPcPayload(host));
    }

    private void rebuild(final ThisPcPayload payload) {
        rows.clear();
        rows.add(Row.header("Disks"));
        if (payload.disks().isEmpty()) {
            rows.add(Row.empty("No disks installed"));
        } else {
            for (final ThisPcPayload.WireDisk d : payload.disks()) {
                rows.add(new Row(Kind.DISK, d, null, null));
            }
        }
        rows.add(Row.header("Removable Media"));
        if (payload.media().isEmpty()) {
            rows.add(Row.empty("No drives linked"));
        } else {
            for (final ThisPcPayload.WireMedia m : payload.media()) {
                rows.add(new Row(Kind.MEDIA, null, m, null));
            }
        }
        rows.add(Row.header("Installed Programs"));
        if (payload.installedPrograms().isEmpty()) {
            rows.add(Row.empty("None"));
        } else {
            for (final String p : payload.installedPrograms()) {
                rows.add(new Row(Kind.PROGRAM, null, null, p));
            }
        }
        if (selected >= rows.size()) {
            selected = -1;
        }
    }

    @Override
    public String title() {
        return "This PC";
    }

    @Override
    public int defaultWidth() {
        return 244;
    }

    @Override
    public int defaultHeight() {
        return 184;
    }

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        this.contentW = width;
        g.fill(x, y, x + width, y + height, PANEL);
        outline(g, x, y, width, height);

        final int visiblePx = height - 2;
        clampScroll(visiblePx);
        int ry = y + 1 - scroll;
        for (int i = 0; i < rows.size(); i++) {
            final Row r = rows.get(i);
            final int rowH = r.kind() == Kind.HEADER ? HEADER_H : ROW_H;
            // Only draw rows that intersect the visible panel.
            if (ry + rowH > y && ry < y + height - 1) {
                drawRow(g, font, r, i, x, ry, width, mouseX, mouseY);
            }
            ry += rowH;
        }
    }

    private void drawRow(final GuiGraphics g, final Font font, final Row r, final int index, final int x,
                         final int ry, final int width, final int mouseX, final int mouseY) {
        // Hover feedback on the selectable rows (disks, media, programs) so the cursor target is clear.
        if (r.kind() != Kind.HEADER && r.kind() != Kind.EMPTY
                && mouseX >= x && mouseX < x + width && mouseY >= ry && mouseY < ry + ROW_H) {
            g.fill(x + 1, ry, x + width - 1, ry + ROW_H, 0x22000000);
        }
        final boolean editing = index == renaming;
        switch (r.kind()) {
            case HEADER -> {
                g.fill(x + 1, ry, x + width - 1, ry + HEADER_H, 0xFFE6E8EF);
                g.fill(x + 1, ry + HEADER_H - 1, x + width - 1, ry + HEADER_H, 0xFFC2C7D4);
                g.drawString(font, r.text(), x + 4, ry + 2, 0xFF3A4256, false);
            }
            case DISK -> {
                final ThisPcPayload.WireDisk d = r.disk();
                drawDiskIcon(g, x + 4, ry + 3);
                final String name = editing ? renameBuf + "_"
                        : (d.osPath().isEmpty() ? d.label() : d.label() + "  [" + d.osPath() + "]");
                g.drawString(font, trim(font, name, width - 92), x + 22, ry + 2, TEXT, false);
                if (d.system()) {
                    g.drawString(font, "System", x + width - 40, ry + 2, 0xFF2E7D32, false);
                }
                // Second line: the usage bar on the left, the usage text right-aligned beside it — the text
                // gets its own 70px column so it never runs into the bar or the name above.
                final int barX = x + 22;
                final int barW = width - 26 - 70;
                g.fill(barX, ry + 13, barX + barW, ry + 16, 0xFFD7DBE4);
                final long cap = Math.max(1, d.capItems());
                final int fill = (int) Math.min(barW, barW * d.usedItems() / cap);
                g.fill(barX, ry + 13, barX + fill, ry + 16, 0xFF3F77C8);
                final String usage = d.usedItems() + " / " + d.capItems() + " it";
                g.drawString(font, trim(font, usage, 66), x + width - font.width(trim(font, usage, 66)) - 4,
                        ry + 11, SUB, false);
            }
            case MEDIA -> {
                final ThisPcPayload.WireMedia m = r.media();
                drawMediaIcon(g, x + 4, ry + 3);
                final String label = editing ? prettyDrive(m.drive()) + ":  " + renameBuf + "_"
                        : (m.mediaName().isEmpty()
                                ? prettyDrive(m.drive()) + "  (empty)"
                                : prettyDrive(m.drive()) + ":  " + m.mediaName());
                g.drawString(font, trim(font, label, width - 60), x + 22, ry + 2, TEXT, false);
                if (!m.kind().isEmpty()) {
                    g.drawString(font, m.kind().toLowerCase(java.util.Locale.ROOT).replace('_', ' '),
                            x + 22, ry + 9, SUB, false);
                }
                if (m.installable()) {
                    drawButton(g, font, x + width - 48, ry + 3, 44, "Install", mouseX, mouseY);
                }
            }
            case PROGRAM -> {
                ProgramIcons.draw(g, x + 4, ry + 2, 12, 12, programLabel(r.text()));
                g.drawString(font, prettyProgram(r.text()), x + 20, ry + 4, TEXT, false);
            }
            case EMPTY -> g.drawString(font, r.text(), x + 8, ry + 4, SUB, false);
            default -> { }
        }
    }

    // --- inspection (client tests; points are content-local, i.e. relative to window.x()+4 / window.y()+18) ---

    /** The index of the first media row whose medium name contains {@code nameContains}, or -1. */
    public int mediaRowIndex(final String nameContains) {
        for (int i = 0; i < rows.size(); i++) {
            final Row r = rows.get(i);
            if (r.kind() == Kind.MEDIA && r.media().mediaName().contains(nameContains)) {
                return i;
            }
        }
        return -1;
    }

    public boolean isInstallable(final int rowIndex) {
        return rowIndex >= 0 && rowIndex < rows.size() && rows.get(rowIndex).kind() == Kind.MEDIA
                && rows.get(rowIndex).media().installable();
    }

    /** Content-local centre of the "Install" button on media row {@code rowIndex} (same walk as the click). */
    public int[] installButtonCenter(final int rowIndex) {
        int ry = 1 - scroll;
        for (int i = 0; i < rowIndex && i < rows.size(); i++) {
            ry += rows.get(i).kind() == Kind.HEADER ? HEADER_H : ROW_H;
        }
        return new int[]{contentW - 48 + 22, ry + 3 + 6};
    }

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY,
                             final int button) {
        final int contentX = window.x() + 4;
        final int contentY = window.y() + 18;
        final int width = contentW;
        // Resolve which row the cursor is over by walking the variable-height list.
        int ry = contentY + 1 - scroll;
        for (int i = 0; i < rows.size(); i++) {
            final Row r = rows.get(i);
            final int rowH = r.kind() == Kind.HEADER ? HEADER_H : ROW_H;
            if (mouseY >= ry && mouseY < ry + rowH) {
                onRowClicked(i, r, mouseX, contentX, width);
                return;
            }
            ry += rowH;
        }
        selected = -1;
    }

    private void onRowClicked(final int index, final Row r, final double mouseX, final int contentX,
                              final int width) {
        if (r.kind() == Kind.MEDIA && r.media().installable()) {
            final int btnX = contentX + width - 48;
            if (mouseX >= btnX && mouseX <= btnX + 44) {
                PacketDistributor.sendToServer(new InstallFromMediaPayload(host, r.media().readerPos()));
                request();
                return;
            }
        }
        final long now = System.currentTimeMillis();
        final boolean dbl = index == lastClickRow && now - lastClickAt < 300;
        lastClickRow = index;
        lastClickAt = now;
        selected = index;
        if (dbl && r.kind() == Kind.DISK) {
            // Open the file explorer (which browses the system disk).
            DesktopScreen.requestOpen("Files");
        }
    }

    @Override
    public boolean mouseScrolled(final double delta) {
        scroll -= (int) (delta * ROW_H);
        return true;
    }

    @Override
    public boolean charTyped(final char c) {
        if (renaming >= 0 && c >= 32 && c != 127 && renameBuf.length() < 32) {
            renameBuf.append(c);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (renaming >= 0) {
            switch (key) {
                case 257, 335 -> commitRename();   // Enter / numpad Enter
                case 259 -> {                      // Backspace
                    if (renameBuf.length() > 0) {
                        renameBuf.deleteCharAt(renameBuf.length() - 1);
                    }
                }
                case 256 -> renaming = -1;         // Esc cancels
                default -> {
                    return false;
                }
            }
            return true;
        }
        if (key == 291) {                          // F2 renames the selected disk/medium (Windows-style)
            startRename();
            return true;
        }
        return false;
    }

    /** Begins renaming the selected disk or inserted medium, seeding the buffer with its current label. */
    private void startRename() {
        if (selected < 0 || selected >= rows.size()) {
            return;
        }
        final Row r = rows.get(selected);
        renameBuf.setLength(0);
        if (r.kind() == Kind.DISK) {
            renaming = selected;
            renameKey = "disk:" + r.disk().slot();
            renameBuf.append(r.disk().label());
        } else if (r.kind() == Kind.MEDIA && !r.media().mediaName().isEmpty()) {
            renaming = selected;
            renameKey = "media:" + r.media().readerPos();
            renameBuf.append(r.media().mediaName());
        }
    }

    /** Sends the new label to the server and refreshes the listing. */
    private void commitRename() {
        final String label = renameBuf.toString().trim();
        if (!label.isEmpty()) {
            PacketDistributor.sendToServer(new RenameVolumePayload(host, renameKey, label));
        }
        renaming = -1;
        request();
    }

    @Override
    public void renderTooltip(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY) {
        // Walk the variable-height rows the same way renderContent does, and show the min-spec of a
        // hovered program row so the player sees what OS/era it needs before relying on it.
        int ry = y + 1 - scroll;
        for (final Row r : rows) {
            final int rowH = r.kind() == Kind.HEADER ? HEADER_H : ROW_H;
            if (mouseY >= ry && mouseY < ry + rowH && ry >= y && ry < y + height) {
                final List<Component> lines = specFor(r);
                if (!lines.isEmpty()) {
                    g.renderComponentTooltip(font, lines, mouseX, mouseY);
                }
                return;
            }
            ry += rowH;
        }
    }

    private static List<Component> specFor(final Row r) {
        if (r.kind() != Kind.PROGRAM) {
            return List.of();
        }
        final List<Component> spec = MinSpecTooltip.programMinSpec(rl(r.text()));
        if (spec.isEmpty()) {
            return List.of();
        }
        final List<Component> lines = new ArrayList<>(spec.size() + 1);
        lines.add(Component.literal(prettyProgram(r.text())));
        lines.addAll(spec);
        return lines;
    }

    private static ResourceLocation rl(final String id) {
        final ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed != null) {
            return parsed;
        }
        final String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return ResourceLocation.fromNamespaceAndPath("jsc", path);
    }

    private void clampScroll(final int visiblePx) {
        int total = 0;
        for (final Row r : rows) {
            total += r.kind() == Kind.HEADER ? HEADER_H : ROW_H;
        }
        final int max = Math.max(0, total - visiblePx);
        scroll = Math.max(0, Math.min(scroll, max));
    }

    private void drawButton(final GuiGraphics g, final Font font, final int x, final int y,
                            final int w, final String label, final int mouseX, final int mouseY) {
        final boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 11;
        g.fill(x, y, x + w, y + 11, hover ? 0xFF3F77C8 : 0xFFDDE2EC);
        outlineColor(g, x, y, w, 11, 0xFF6E7686);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 2, hover ? 0xFFFFFFFF : TEXT, false);
    }

    private static void drawDiskIcon(final GuiGraphics g, final int x, final int y) {
        g.fill(x, y, x + 14, y + 10, 0xFF8B93A4);
        g.fill(x + 1, y + 1, x + 13, y + 9, 0xFFC7CDDA);
        g.fill(x + 2, y + 2, x + 12, y + 4, 0xFFEDF0F6);
        g.fill(x + 9, y + 6, x + 11, y + 8, 0xFF49E07A); // activity light
        outlineColor(g, x, y, 14, 10, 0xFF5A6273);
    }

    private static void drawMediaIcon(final GuiGraphics g, final int x, final int y) {
        g.fill(x + 2, y, x + 12, y + 10, 0xFFB9C0CE);   // disc body
        g.fill(x + 5, y + 3, x + 9, y + 7, 0xFFEDF0F6);  // hub
        outlineColor(g, x + 2, y, 10, 10, 0xFF6E7686);
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

    private static String programLabel(final String id) {
        final String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return switch (path) {
            case "nms" -> "Network";
            default -> "default";
        };
    }

    private static String prettyProgram(final String id) {
        final String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return switch (path) {
            case "nms" -> "Network Management Studio";
            case "iqlengine" -> "IQL Engine";
            case "command_prompt" -> "Command Prompt";
            case "crafting_manager" -> "Crafting Manager";
            default -> path;
        };
    }

    private static String trim(final Font font, final String s, final int maxW) {
        String out = s;
        while (out.length() > 2 && font.width(out) > maxW) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private void outline(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        outlineColor(g, x, y, w, h, EDGE);
    }

    private static void outlineColor(final GuiGraphics g, final int x, final int y, final int w, final int h,
                                     final int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
