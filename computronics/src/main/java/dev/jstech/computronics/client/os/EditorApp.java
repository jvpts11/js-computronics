/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.computronics.operation.payload.FileSavedPayload;
import dev.jstech.computronics.operation.payload.SaveFilePayload;
import dev.jstech.core.client.gui.component.Label;
import dev.jstech.core.client.gui.component.Panel;
import dev.jstech.core.client.gui.component.TextArea;
import dev.jstech.core.client.gui.component.TextField;
import dev.jstech.core.client.gui.component.UiContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * A multi-line text editor for the desktop, so a graphical OS can create and edit the disk's text files
 * (.txt/.iql/.cfg/.csv/.cmd). The player types a file name and content, and saves with Ctrl+S; the save
 * goes through the filesystem on the server and the status line reports the result. Tab moves between
 * the name and the text; a file opened from the explorer keeps its name (rename it there).
 */
public final class EditorApp implements IDesktopApp {

    private static final int NAME_H = 13;
    private static final int STATUS_H = 10;
    private static final int NAME_MAX = 64;
    private static final int CAPTION_W = 30;

    private OsSkin skin = OsSkin.fallback();

    private final BlockPos host;
    /** The full path of an opened file, whose name the field shows short and cannot change. */
    private String lockedPath = "";
    private String status = "Ctrl+S to save";

    private static EditorApp active;

    private final Panel root = new Panel();
    private final Label nameCaption;
    private final TextField nameField;
    private final TextArea body;
    private final Label statusLabel;

    /** The file name field: a path separator cannot be typed into it. */
    private static final class NameField extends TextField {

        NameField() {
            super(NAME_MAX);
        }

        @Override
        protected boolean accepts(final char c) {
            return super.accepts(c) && c != '/' && c != '\\';
        }
    }

    public EditorApp(final BlockPos host) {
        this.host = host;
        nameCaption = root.add(new Label("Name:", Label.Tone.DIM));
        nameField = root.add(new NameField());
        nameField.set("untitled.txt");
        body = root.add(new TextArea());
        statusLabel = root.add(new Label(() -> status, Label.Tone.DIM));
        root.focus(nameField);
        active = this;
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
        lockedPath = path;
        nameField.set(shortName(path));
        nameField.setEnabled(false);
        body.setText(content);
        root.focus(body);
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
        final UiContext ctx = new UiContext(skin, font, mouseX, mouseY, partialTick);
        g.fill(x, y, x + width, y + height, skin.windowBg());
        nameCaption.setBounds(x + 3, y + 3, CAPTION_W - 4, 8);
        nameField.setBounds(x + CAPTION_W, y, width - CAPTION_W, NAME_H);
        body.setBounds(x, y + NAME_H + 2, width, height - NAME_H - STATUS_H - 4);
        statusLabel.setBounds(x + 2, y + height - STATUS_H + 1, width - 4, 8);
        root.render(g, ctx);
    }

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        root.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double delta) {
        return body.mouseScrolled(body.x(), body.y(), delta);
    }

    @Override
    public boolean charTyped(final char c) {
        return root.charTyped(c);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_S) {
            save();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            // Tab toggles the name/body focus (the name is fixed once a file is open).
            if (!lockedPath.isEmpty()) {
                root.focus(body);
            } else {
                root.focus(nameField.isFocused() ? body : nameField);
            }
            return true;
        }
        if (nameField.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            root.focus(body);
            return true;
        }
        return root.keyPressed(key, scanCode, modifiers);
    }

    private void save() {
        final String target = lockedPath.isEmpty() ? nameField.edit().trim() : lockedPath;
        // A .dat is a read-only projection of stored items; it can never be created or written by hand.
        if (target.toLowerCase(Locale.ROOT).endsWith(".dat")) {
            status = "Cannot save a .dat file";
            DesktopScreen.showDatLockedError();
            return;
        }
        status = "Saving...";
        PacketDistributor.sendToServer(new SaveFilePayload(host, target, body.text()));
        FilesApps.diskChanged();
    }
}
