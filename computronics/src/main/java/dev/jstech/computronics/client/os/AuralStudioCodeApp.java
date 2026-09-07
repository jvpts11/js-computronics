/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.computronics.operation.payload.DiskFilesPayload;
import dev.jstech.computronics.os.edit.InkPalette;
import dev.jstech.core.client.gui.component.Draw;
import dev.jstech.core.client.gui.component.ListView;
import dev.jstech.core.client.gui.component.Panel;
import dev.jstech.core.client.gui.component.TabStrip;
import dev.jstech.core.client.gui.component.UiContext;
import dev.jstech.core.language.IProgrammingLanguage;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Aural Studio Code: the light editor, with the machine's programs down the side and the compiler
 * reading over the player's shoulder.
 *
 * <p>All it owns is the arrangement. What the files are, which are open and what is wrong with them is
 * the workspace's; the explorer, the tabs and the text are the toolkit's own components. That is what
 * lets the other two editors be a different arrangement of the same parts rather than a second copy.
 */
public final class AuralStudioCodeApp implements IDesktopApp {

    private static final int RAIL_W = 14;
    private static final int SIDE_W = 76;
    private static final int CAPTION_H = 9;
    private static final int TAB_H = 10;
    private static final int STATUS_H = 9;
    private static final int ROW_H = 9;

    private final CodeWorkspace workspace;
    private OsSkin skin = OsSkin.fallback();

    private final Panel root = new Panel();
    private final ListView<DiskFilesPayload.WireFile> explorer;
    private final TabStrip tabs;

    public AuralStudioCodeApp(final BlockPos host) {
        this.workspace = new CodeWorkspace(host);
        this.explorer = this.root.add(new ListView<>(this.workspace::files, ROW_H, this::drawFileRow))
                .setOnClick(this::onFilePicked);
        this.tabs = this.root.add(new TabStrip(this.workspace::tabLabels).fitToLabels(10).setUnderline(false));
        this.tabs.setOnSelect(this.workspace::setCurrent);
        this.workspace.refresh();
    }

    /** One row of the explorer: the file's name, in the colour a selected row asks for. */
    private void drawFileRow(final GuiGraphics g, final UiContext ctx, final DiskFilesPayload.WireFile file,
                             final int index, final int x, final int y, final int width, final int height,
                             final boolean hovered, final boolean selected) {
        g.drawString(ctx.font(), shortName(file.path()), x + 2, y + 1, ctx.skin().listRowText(selected), false);
    }

    private void onFilePicked(final int index, final int button, final double mx, final double my) {
        if (index >= 0 && index < this.workspace.files().size()) {
            this.workspace.open(this.workspace.files().get(index).path());
        }
    }

    private static String shortName(final String path) {
        final int slash = path.lastIndexOf('/');
        return slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : path;
    }

    /* The window */

    @Override
    public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
        this.workspace.setPalette(InkPalette.forGround(osSkin.isDark()));
    }

    @Override
    public String title() {
        final CodeWorkspace.Doc doc = this.workspace.current();
        return doc == null ? "Aural Studio Code"
                : doc.name() + (doc.dirty() ? " *" : "") + " - Aural Studio Code";
    }

    @Override
    public int defaultWidth() {
        return 300;
    }

    @Override
    public int defaultHeight() {
        return 176;
    }

    @Override
    public int minWidth() {
        return 210;
    }

    @Override
    public int minHeight() {
        return 110;
    }

    @Override
    public void onRestored() {
        this.workspace.refresh();
    }

    @Override
    public void onClosed() {
        this.workspace.release();
    }

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        final UiContext ctx = new UiContext(this.skin, font, mouseX, mouseY, partialTick);
        g.fill(x, y, x + width, y + height, this.skin.windowBg());

        drawRail(g, x, y, height);
        this.skin.panel(g, x + RAIL_W, y, SIDE_W, height);
        g.drawString(font, "EXPLORER", x + RAIL_W + 4, y + 1, this.skin.dim(), false);
        this.explorer.setBounds(x + RAIL_W, y + CAPTION_H, SIDE_W, height - CAPTION_H);

        final int codeX = x + RAIL_W + SIDE_W;
        final int codeW = width - RAIL_W - SIDE_W;
        this.skin.panel(g, codeX, y, codeW, TAB_H);
        this.tabs.setBounds(codeX, y, codeW, TAB_H);
        this.tabs.setSelected(this.workspace.currentIndex());

        final CodeWorkspace.Doc doc = this.workspace.current();
        final int bodyY = y + TAB_H;
        final int bodyH = height - TAB_H - STATUS_H;
        if (doc != null) {
            doc.area().setBounds(codeX, bodyY, codeW, bodyH);
        }
        this.root.setBounds(x, y, width, height);
        this.root.render(g, ctx);
        if (doc != null) {
            doc.area().render(g, ctx);
        } else {
            drawEmpty(g, font, codeX, bodyY, codeW, bodyH);
        }
        drawStatus(g, font, x, y + height - STATUS_H, width, doc);
    }

    /** The rail: today it holds the one thing there is to show, the machine's programs. */
    private void drawRail(final GuiGraphics g, final int x, final int y, final int height) {
        this.skin.panel(g, x, y, RAIL_W, height);
        for (int i = 0; i < 3; i++) {
            g.fill(x + 3, y + 4 + i * 3, x + 11, y + 5 + i * 3, this.skin.accent());
        }
    }

    private void drawEmpty(final GuiGraphics g, final Font font, final int x, final int y,
                           final int width, final int height) {
        final InkPalette palette = InkPalette.forGround(this.skin.isDark());
        g.fill(x, y, x + width, y + height, palette.ground());
        Draw.pushScissor(g, x, y, x + width, y + height);
        g.drawString(font, "Pick a program on the left", x + 6, y + 6, palette.gutterText(), false);
        Draw.popScissor(g);
    }

    private void drawStatus(final GuiGraphics g, final Font font, final int x, final int y,
                            final int width, final CodeWorkspace.Doc doc) {
        this.skin.statusBar(g, x, y, width, STATUS_H);
        final IProgrammingLanguage language = this.workspace.language();
        final String left = language == null ? "no file" : language.displayName();
        g.drawString(font, left, x + 3, y + 1, this.skin.dim(), false);
        if (!this.workspace.status().isEmpty()) {
            g.drawString(font, this.workspace.status(), x + 5 + font.width(left) + 6, y + 1,
                    this.skin.dim(), false);
        }
        if (doc != null) {
            final String where = "Ln " + (doc.area().document().cursorLine() + 1)
                    + ", Col " + (doc.area().document().cursorCol() + 1);
            g.drawString(font, where, x + width - font.width(where) - 3, y + 1, this.skin.dim(), false);
        }
    }

    /* Input */

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        if (this.root.mouseClicked(mouseX, mouseY, button)) {
            return;
        }
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null && doc.area().contains(mouseX, mouseY)) {
            doc.area().mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    public boolean charTyped(final char c) {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null && doc.area().charTyped(c)) {
            this.workspace.edited();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_S) {
            this.workspace.save();
            return true;
        }
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc != null && doc.area().keyPressed(key, scanCode, modifiers)) {
            this.workspace.edited();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(final double delta) {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc == null) {
            return false;
        }
        return doc.area().mouseScrolled(doc.area().x(), doc.area().y(), delta);
    }

    @Override
    public void renderTooltip(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY) {
        final CodeWorkspace.Doc doc = this.workspace.current();
        if (doc == null) {
            return;
        }
        final String message = doc.area().messageAt(mouseX, mouseY);
        if (!message.isEmpty()) {
            g.renderTooltip(font, Component.literal(message), mouseX, mouseY);
        }
    }
}
