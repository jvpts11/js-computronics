/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.os;

import dev.jsc.jscomputronics.module.computing.operation.payload.FileSavedPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.SaveFilePayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * A multi-line text editor for the desktop, so a graphical OS can create and edit the disk's text files
 * (.txt/.iql/.cfg/.csv/.cmd). The player types a file name and content, and saves with Ctrl+S; the save
 * goes through the filesystem on the server and the status line reports the result.
 */
public final class EditorApp implements DesktopApp {

    private static final int NAME_H = 13;
    private static final int STATUS_H = 10;
    private static final int LINE_H = 9;

    private OsSkin skin = OsSkin.fallback();
    private int PANEL = 0xFFFFFFFF;
    private int PANEL_DIM = 0xFFE8E8EC;
    private int EDGE = 0xFF6E7686;
    private int TEXT = 0xFF1A2230;

    private final BlockPos host;
    private final StringBuilder name = new StringBuilder("untitled.txt");
    private final List<StringBuilder> lines = new ArrayList<>();
    private int curLine;
    private int curCol;
    private boolean nameFocus = true;
    private boolean nameLocked; // once a file is opened its name is fixed (rename in the explorer)
    private int scroll;
    private String status = "Ctrl+S to save";

    private static EditorApp active;

    public EditorApp(final BlockPos host) {
        this.host = host;
        active = this;
        lines.add(new StringBuilder());
    }

    /** Routes a save result to the open Editor window. */
    public static void accept(final FileSavedPayload payload) {
        if (active != null) {
            active.status = payload.message();
        }
    }

    /** Routes file content (from the Files explorer's Open) into the open Editor window. */
    public static void acceptContent(final String path, final String content, final boolean exists) {
        if (active != null) {
            active.load(path, content, exists);
        }
    }

    private void load(final String path, final String content, final boolean exists) {
        name.setLength(0);
        name.append(path);
        lines.clear();
        for (final String line : content.split("\n", -1)) {
            lines.add(new StringBuilder(line));
        }
        if (lines.isEmpty()) {
            lines.add(new StringBuilder());
        }
        curLine = 0;
        curCol = 0;
        nameFocus = false;
        nameLocked = true;
        status = exists ? "Opened " + shortName(path) : "New file " + shortName(path);
    }

    /** A readable name for display: the last path segment, with any {@code media:<pos>/} prefix dropped. */
    private static String shortName(final String path) {
        final int slash = path.lastIndexOf('/');
        return slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : path;
    }

    @Override
    public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
        this.PANEL = osSkin.fieldBg();
        this.PANEL_DIM = osSkin.panelBg();
        this.EDGE = osSkin.edge();
        this.TEXT = osSkin.text();
    }

    @Override
    public String title() {
        return "Editor";
    }

    @Override
    public int defaultWidth() {
        return 284;
    }

    @Override
    public int defaultHeight() {
        return 180;
    }

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        // Name field.
        skin.field(g, x, y, width, NAME_H, nameFocus);
        final String shownName = nameLocked ? shortName(name.toString()) : name.toString();
        g.drawString(font, "Name: " + shownName + (nameFocus ? "_" : ""), x + 3, y + 3, TEXT, false);

        // Body.
        final int bodyTop = y + NAME_H + 2;
        final int bodyH = height - NAME_H - STATUS_H - 4;
        g.fill(x, bodyTop, x + width, bodyTop + bodyH, PANEL);
        outline(g, x, bodyTop, width, bodyH);

        final int visible = Math.max(1, (bodyH - 2) / LINE_H);
        clampScroll(visible);
        int ry = bodyTop + 1;
        for (int i = scroll; i < lines.size() && (i - scroll) < visible; i++) {
            final String text = lines.get(i).toString();
            g.drawString(font, text, x + 3, ry + 1, TEXT, false);
            if (!nameFocus && i == curLine) {
                final int cx = x + 3 + font.width(text.substring(0, Math.min(curCol, text.length())));
                g.fill(cx, ry, cx + 1, ry + LINE_H, 0xFF1A2230);
            }
            ry += LINE_H;
        }

        // Status line.
        g.drawString(font, status, x + 2, y + height - STATUS_H + 1, 0xFF606878, false);
    }

    @Override
    public boolean charTyped(final char c) {
        if (c < 32 || c == 127) {
            return false;
        }
        if (nameFocus && !nameLocked) {
            if (c != '/' && c != '\\' && name.length() < 64) {
                name.append(c);
            }
        } else if (!nameFocus) {
            lines.get(curLine).insert(curCol, c);
            curCol++;
        }
        return true;
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        final boolean ctrl = (modifiers & 0x2) != 0; // GLFW_MOD_CONTROL
        if (ctrl && key == 83) { // Ctrl+S
            save();
            return true;
        }
        if (key == 258) { // Tab toggles the name/body focus (the name is fixed once a file is open)
            if (!nameLocked) {
                nameFocus = !nameFocus;
            }
            return true;
        }
        if (nameFocus) {
            if (key == 257 || key == 335) {
                nameFocus = false;
            } else if (key == 259 && name.length() > 0) {
                name.deleteCharAt(name.length() - 1);
            }
            return true;
        }
        switch (key) {
            case 257, 335 -> newline();
            case 259 -> backspace();
            case 263 -> moveLeft();
            case 262 -> moveRight();
            case 265 -> moveUp();
            case 264 -> moveDown();
            default -> {
                return false;
            }
        }
        return true;
    }

    private void newline() {
        final StringBuilder cur = lines.get(curLine);
        final String tail = cur.substring(curCol);
        cur.delete(curCol, cur.length());
        lines.add(curLine + 1, new StringBuilder(tail));
        curLine++;
        curCol = 0;
    }

    private void backspace() {
        if (curCol > 0) {
            lines.get(curLine).deleteCharAt(curCol - 1);
            curCol--;
        } else if (curLine > 0) {
            final StringBuilder prev = lines.get(curLine - 1);
            curCol = prev.length();
            prev.append(lines.remove(curLine));
            curLine--;
        }
    }

    private void moveLeft() {
        if (curCol > 0) {
            curCol--;
        } else if (curLine > 0) {
            curLine--;
            curCol = lines.get(curLine).length();
        }
    }

    private void moveRight() {
        if (curCol < lines.get(curLine).length()) {
            curCol++;
        } else if (curLine < lines.size() - 1) {
            curLine++;
            curCol = 0;
        }
    }

    private void moveUp() {
        if (curLine > 0) {
            curLine--;
            curCol = Math.min(curCol, lines.get(curLine).length());
        }
    }

    private void moveDown() {
        if (curLine < lines.size() - 1) {
            curLine++;
            curCol = Math.min(curCol, lines.get(curLine).length());
        }
    }

    private void save() {
        final String target = name.toString().trim();
        // A .dat is a read-only projection of stored items — it can never be created or written by hand.
        if (target.toLowerCase(java.util.Locale.ROOT).endsWith(".dat")) {
            status = "Cannot save a .dat file";
            DesktopScreen.showDatLockedError();
            return;
        }
        final StringBuilder content = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                content.append('\n');
            }
            content.append(lines.get(i));
        }
        status = "Saving...";
        PacketDistributor.sendToServer(new SaveFilePayload(host, target, content.toString()));
    }

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY,
                             final int button) {
        // Focus the name field or the body depending on where in the window the click landed.
        final int contentY = window.y() + 18;
        nameFocus = mouseY < contentY + NAME_H;
    }

    private void clampScroll(final int visible) {
        if (curLine < scroll) {
            scroll = curLine;
        } else if (curLine >= scroll + visible) {
            scroll = curLine - visible + 1;
        }
        if (scroll < 0) {
            scroll = 0;
        }
    }

    private void outline(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        g.fill(x, y, x + w, y + 1, EDGE);
        g.fill(x, y + h - 1, x + w, y + h, EDGE);
        g.fill(x, y, x + 1, y + h, EDGE);
        g.fill(x + w - 1, y, x + w, y + h, EDGE);
    }
}
