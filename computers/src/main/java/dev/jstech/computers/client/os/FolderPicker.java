/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.os;

import dev.jstech.computers.operation.payload.DiskFilesPayload;
import dev.jstech.computers.operation.payload.RequestDiskFilesPayload;
import dev.jstech.core.client.gui.component.Button;
import dev.jstech.core.client.gui.component.Label;
import dev.jstech.core.client.gui.component.ListView;
import dev.jstech.core.client.gui.component.Popup;
import dev.jstech.core.client.gui.component.UiContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A small window for choosing a folder on the machine's disk.
 *
 * <p>It lists the folders inside the one it is looking at, goes into one on a double click and back up
 * on the row above them all, and hands the folder it is on to whoever asked when Open is pressed. Both
 * studios open folders, so neither draws its own.
 */
public final class FolderPicker implements CodeFileReplies.IReader {

    private static final int ROW_H = 9;
    private static final int W = 150;
    private static final int H = 96;

    private final BlockPos host;
    private final Popup popup;
    private final ListView<String> rows;
    private final Label where;
    private String dir = "";
    private final List<String> folders = new ArrayList<>();
    private Consumer<String> onPick = folder -> { };
    private long lastClickAt;
    private int lastClickRow = -1;

    public FolderPicker(final BlockPos host, final String title) {
        this.host = host;
        this.popup = new Popup(title, W, H).setDim(0x40000000).setLayouter(this::layout);
        this.where = this.popup.add(new Label(() -> "C:\\" + this.dir.replace('/', '\\'), Label.Tone.DIM));
        this.rows = this.popup.add(new ListView<>(this::rowLabels, ROW_H, this::drawRow)).setOnClick(this::onRow);
        this.open = this.popup.add(new Button("Open", this::pick).setPrimary(true));
        this.cancel = this.popup.add(new Button("Cancel", this.popup::close));
    }

    private final Button open;
    private final Button cancel;

    /** Opens the picker on {@code start}, calling {@code onPick} with the folder chosen. */
    public void open(final String start, final Consumer<String> onPick) {
        this.onPick = onPick;
        this.popup.open();
        go(start == null ? "" : start);
    }

    public boolean isOpen() {
        return this.popup.isOpen();
    }

    public void close() {
        this.popup.close();
    }

    /** The folder the picker is on. */
    public String folder() {
        return this.dir;
    }

    private void go(final String target) {
        this.dir = target;
        this.folders.clear();
        this.rows.setSelected(-1);
        CodeFileReplies.expectListing(this, target);
        PacketDistributor.sendToServer(new RequestDiskFilesPayload(this.host, target));
    }

    @Override
    public void onListing(final DiskFilesPayload listing) {
        this.folders.clear();
        for (final DiskFilesPayload.WireFile file : listing.files()) {
            if (file.directory() && !file.path().startsWith("media:")) {
                this.folders.add(file.path());
            }
        }
    }

    /** The rows: the way up, then every folder here, by its last name. */
    private List<String> rowLabels() {
        final List<String> out = new ArrayList<>();
        if (!this.dir.isEmpty()) {
            out.add("..");
        }
        for (final String folder : this.folders) {
            final int slash = folder.lastIndexOf('/');
            out.add(slash >= 0 ? folder.substring(slash + 1) : folder);
        }
        return out;
    }

    private void drawRow(final GuiGraphics g, final UiContext ctx, final String label, final int index,
                         final int x, final int y, final int width, final int height,
                         final boolean hovered, final boolean selected) {
        g.drawString(ctx.font(), label, x + 3, y + 1, ctx.skin().listRowText(selected), false);
    }

    /** A second click on the same row within a moment goes into it, the way a double click does. */
    private void onRow(final int index, final int button, final double mx, final double my) {
        final long now = System.currentTimeMillis();
        final boolean again = index == this.lastClickRow && now - this.lastClickAt < 350;
        this.lastClickAt = now;
        this.lastClickRow = index;
        if (!again) {
            return;
        }
        final List<String> labels = rowLabels();
        if (index < 0 || index >= labels.size()) {
            return;
        }
        if (labels.get(index).equals("..")) {
            final int slash = this.dir.lastIndexOf('/');
            go(slash >= 0 ? this.dir.substring(0, slash) : "");
            return;
        }
        final int at = this.dir.isEmpty() ? index : index - 1;
        if (at >= 0 && at < this.folders.size()) {
            go(this.folders.get(at));
        }
    }

    /** Open hands over the selected folder, or the one the picker is on when none is selected. */
    private void pick() {
        final int index = this.rows.selected();
        final List<String> labels = rowLabels();
        String chosen = this.dir;
        if (index >= 0 && index < labels.size() && !labels.get(index).equals("..")) {
            final int at = this.dir.isEmpty() ? index : index - 1;
            if (at >= 0 && at < this.folders.size()) {
                chosen = this.folders.get(at);
            }
        }
        this.popup.close();
        this.onPick.accept(chosen);
    }

    private void layout(final Popup p) {
        final int x = p.x() + 4;
        final int y = p.contentTop() + 2;
        final int w = p.width() - 8;
        this.where.setBounds(x, y, w, 9);
        this.rows.setBounds(x, y + 10, w, p.bottom() - (y + 10) - 18);
        final int by = p.bottom() - 15;
        this.cancel.setBounds(p.right() - 42, by, 38, 11);
        this.open.setBounds(p.right() - 83, by, 38, 11);
    }

    public void render(final GuiGraphics g, final UiContext ctx, final int cx, final int cy, final int cw,
                       final int ch) {
        if (this.popup.isOpen()) {
            this.popup.renderIn(g, ctx, cx, cy, cw, ch);
        }
    }

    public boolean mouseClicked(final double mx, final double my, final int button) {
        return this.popup.mouseClicked(mx, my, button);
    }

    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        return this.popup.keyPressed(key, scanCode, modifiers);
    }

    public boolean charTyped(final char c) {
        return this.popup.charTyped(c);
    }

    public void release() {
        CodeFileReplies.forget(this);
    }
}
