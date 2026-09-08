/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.os;

import dev.jstech.computers.operation.payload.AutomationPayload;
import dev.jstech.computers.operation.payload.CreateAutomationJobPayload;
import dev.jstech.computers.operation.payload.JobActionPayload;
import dev.jstech.computers.operation.payload.RequestAutomationPayload;
import dev.jstech.core.client.gui.component.Button;
import dev.jstech.core.client.gui.component.ColumnHeader;
import dev.jstech.core.client.gui.component.Label;
import dev.jstech.core.client.gui.component.ListView;
import dev.jstech.core.client.gui.component.Panel;
import dev.jstech.core.client.gui.component.TextField;
import dev.jstech.core.client.gui.component.Texts;
import dev.jstech.core.client.gui.component.UiContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Automation Manager: the front-end for the network's standing jobs. It shows whether a job engine is
 * online on the Mainframe, lists the saved jobs with pause/resume/delete, and creates new ones from a form
 * (Keep Stock / Batch Craft / Periodic Move / IQL script) that the server compiles into jobs; no IQL is
 * typed here. Requires the Automation Engine (or the IQL Engine) on the Mainframe for jobs to actually run.
 */
public final class AutomationManagerApp implements IDesktopApp {

    private static final int REFRESH_FRAMES = 40;
    private static final int C_GOOD = 0xFF2EA043;
    private static final int C_WARN = 0xFFE0A020;
    private static final int C_DELETE = 0xFFC0504A;
    private static final int JOB_ROW_H = 13;
    private static final int FORM_H = 78;
    private static final int FIELD_MAX = 48;

    private static final String[] TYPE_LABELS = {"Keep Stock", "Batch Craft", "Move", "IQL"};

    private final BlockPos host;
    private final BlockPos monitorPos;
    private OsSkin skin = OsSkin.fallback();
    @Nullable
    private AutomationPayload data;
    private int newType;
    private int frame;
    private int lastMouseX;
    private int lastMouseY;
    /** The .iql files the script buttons were built for; a different list rebuilds them. */
    private List<String> scriptsBuiltFor = List.of();
    private String script = "";

    private static AutomationManagerApp active;

    /** A form field that takes one token: item ids and names carry no spaces. */
    private static class TokenField extends TextField {

        TokenField() {
            super(FIELD_MAX);
        }

        @Override
        protected boolean accepts(final char c) {
            return super.accepts(c) && c != ' ';
        }
    }

    /** A form field that takes a number. */
    private static final class DigitField extends TokenField {

        @Override
        protected boolean accepts(final char c) {
            return c >= '0' && c <= '9';
        }
    }

    // components
    private final Panel root = new Panel();
    private final Label loadingLabel;
    private final Label engineLabel;
    private final Label jobsLabel;
    private final ColumnHeader jobColumns;
    private final ListView<AutomationPayload.JobRow> jobList;
    private final Label noJobsLabel;
    private final Label newJobLabel;
    private final Button[] typeButtons = new Button[TYPE_LABELS.length];
    private final Label nameCaption;
    private final TextField nameField;
    private final Label itemCaption;
    private final TextField itemField;
    private final Label amountCaption;
    private final TextField amountField;
    private final Label intervalCaption;
    private final TextField intervalField;
    private final Label fromCaption;
    private final TextField fromField;
    private final Label toCaption;
    private final TextField toField;
    private final Label scriptCaption;
    private final Label noScriptsLabel;
    private final Panel scripts = new Panel();
    private final Button create;

    public AutomationManagerApp(final BlockPos host, final BlockPos monitorPos) {
        this.host = host;
        this.monitorPos = monitorPos;

        loadingLabel = root.add(new Label("Contacting Mainframe...", Label.Tone.DIM));
        engineLabel = root.add(new Label(this::engineText).setColor(() -> data != null && data.engineOnline() ? 0 : C_WARN));
        jobsLabel = root.add(new Label(() -> data == null ? "" : data.jobs().size() + " jobs", Label.Tone.DIM).setAlign(Label.Align.RIGHT));
        jobColumns = root.add(new ColumnHeader(List.of("JOB", "TYPE", "TRIGGER", "ACT")).setSortable(false));
        jobList = root.add(new ListView<AutomationPayload.JobRow>(() -> data == null ? List.of() : data.jobs(), JOB_ROW_H, this::renderJobRow)
                .setOnClick(this::jobClicked));
        noJobsLabel = root.add(new Label("No jobs yet - create one below.", Label.Tone.DIM));
        newJobLabel = root.add(new Label("NEW JOB", Label.Tone.DIM));
        for (int i = 0; i < TYPE_LABELS.length; i++) {
            final int type = i;
            typeButtons[i] = root.add(new Button(TYPE_LABELS[i], () -> {
                newType = type;
                root.focus(null);
            }));
        }
        nameCaption = root.add(new Label("Name", Label.Tone.DIM));
        nameField = root.add(new TokenField());
        itemCaption = root.add(new Label(() -> newType == CreateAutomationJobPayload.TYPE_PERIODIC_MOVE ? "Item (blank=all)" : "Item id",
                Label.Tone.DIM));
        itemField = root.add(new TokenField());
        amountCaption = root.add(new Label(() -> newType == CreateAutomationJobPayload.TYPE_KEEP_STOCK ? "Keep at least" : "Amount",
                Label.Tone.DIM));
        amountField = root.add(new DigitField());
        intervalCaption = root.add(new Label("Every (30s)", Label.Tone.DIM));
        intervalField = root.add(new TokenField());
        fromCaption = root.add(new Label("From", Label.Tone.DIM));
        fromField = root.add(new TokenField());
        toCaption = root.add(new Label("To", Label.Tone.DIM));
        toField = root.add(new TokenField());
        scriptCaption = root.add(new Label("Script (on Mainframe disk):", Label.Tone.DIM));
        noScriptsLabel = root.add(new Label("no .iql files - save one in the NMS", Label.Tone.DIM));
        root.add(scripts);
        create = root.add(new Button("Create job", this::create).setPrimary(true));

        active = this;
        request();
    }

    public static void accept(final AutomationPayload payload) {
        if (active != null) {
            active.data = payload;
        }
    }

    private void request() {
        PacketDistributor.sendToServer(new RequestAutomationPayload(host, monitorPos));
    }

    @Override
    public void onRestored() {
        active = this;
        request();
    }

    @Override
    public String title() {
        return "Automation Manager";
    }

    @Override
    public int defaultWidth() {
        return 320;
    }

    @Override
    public int defaultHeight() {
        return 216;
    }

    @Override
    public int minWidth() {
        return 280;
    }

    @Override
    public int minHeight() {
        return 190;
    }

    @Override
    public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
        active = this;
    }

    private String engineText() {
        if (data == null) {
            return "";
        }
        return data.engineOnline() ? data.engineLabel() + " online" : "No engine - install the Automation Engine on the Mainframe";
    }

    // rendering

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        frame++;
        if (frame % REFRESH_FRAMES == 0) {
            request();
        }
        final UiContext ctx = new UiContext(skin, font, mouseX, mouseY, partialTick);
        g.fill(x, y, x + width, y + height, skin.windowBg());
        final int px = x + 6;
        final int pw = width - 12;
        layout(font, px, y, pw, height);
        if (data != null) {
            // The engine status band and its lamp, and the rule over the form.
            final boolean on = data.engineOnline();
            g.fill(px, y + 6, px + pw, y + 20, on ? 0x162EA043 : 0x22E0A020);
            g.fill(px + 4, y + 11, px + 8, y + 15, on ? C_GOOD : C_WARN);
            g.fill(px, newJobLabel.y() - 5, px + pw, newJobLabel.y() - 4, skin.edge());
        }
        root.render(g, ctx);
    }

    private void layout(final Font font, final int x, final int y, final int w, final int height) {
        final boolean ready = data != null;
        loadingLabel.setVisible(!ready);
        loadingLabel.setBounds(x, y + 8, w, 8);
        engineLabel.setVisible(ready);
        engineLabel.setBounds(x + 12, y + 9, w - 60, 8);
        jobsLabel.setVisible(ready);
        jobsLabel.setBounds(x + w - 48, y + 9, 48, 8);

        // Split: job list (top) and the new-job form (bottom).
        final int listTop = y + 24;
        final int listH = y + height - FORM_H - listTop - 4;
        final boolean noJobs = ready && data.jobs().isEmpty();
        jobColumns.setVisible(ready);
        jobColumns.setBounds(x, listTop - 1, w, 11);
        jobColumns.setColumnX(x + 2, x + (int) (w * 0.40), x + (int) (w * 0.62), x + w - 22);
        jobList.setVisible(ready && !noJobs);
        jobList.setBounds(x, listTop + 11, w, Math.max(JOB_ROW_H, listH - 11));
        noJobsLabel.setVisible(noJobs);
        noJobsLabel.setBounds(x + 2, listTop + 13, w - 4, 8);

        final int fy = listTop + listH + 4;
        newJobLabel.setVisible(ready);
        newJobLabel.setBounds(x + 2, fy, 40, 8);
        int sx = x + 44;
        for (int i = 0; i < TYPE_LABELS.length; i++) {
            final int sw = font.width(TYPE_LABELS[i]) + 10;
            typeButtons[i].setVisible(ready);
            typeButtons[i].setBounds(sx, fy - 2, sw, 12);
            typeButtons[i].setPrimary(i == newType);
            sx += sw + 2;
        }
        // Fields for the chosen type, laid out in two columns.
        final int rowY = fy + 14;
        final int colW = (w - 6) / 2;
        final int right = x + colW + 6;
        for (final var c : List.of(nameCaption, nameField, itemCaption, itemField, amountCaption, amountField, intervalCaption,
                intervalField, fromCaption, fromField, toCaption, toField, scriptCaption, noScriptsLabel, scripts)) {
            c.setVisible(false);
        }
        if (ready) {
            field(nameCaption, nameField, x, rowY, colW);
            switch (newType) {
                case CreateAutomationJobPayload.TYPE_KEEP_STOCK -> {
                    field(itemCaption, itemField, right, rowY, colW);
                    field(amountCaption, amountField, x, rowY + 24, colW);
                }
                case CreateAutomationJobPayload.TYPE_BATCH_CRAFT -> {
                    field(itemCaption, itemField, right, rowY, colW);
                    field(amountCaption, amountField, x, rowY + 24, colW);
                    field(intervalCaption, intervalField, right, rowY + 24, colW);
                }
                case CreateAutomationJobPayload.TYPE_PERIODIC_MOVE -> {
                    field(itemCaption, itemField, right, rowY, colW);
                    field(fromCaption, fromField, x, rowY + 24, colW / 2 - 2);
                    field(toCaption, toField, x + colW / 2 + 2, rowY + 24, colW / 2 - 2);
                    field(intervalCaption, intervalField, right, rowY + 24, colW);
                }
                case CreateAutomationJobPayload.TYPE_IQL_SCRIPT -> {
                    field(intervalCaption, intervalField, right, rowY, colW);
                    scriptCaption.setVisible(true);
                    scriptCaption.setBounds(x, rowY + 22, w, 8);
                    layoutScripts(font, x, rowY + 31, w, rowY + 44);
                }
                default -> { }
            }
        }
        create.setVisible(ready);
        final int cw = font.width(create.label()) + 14;
        create.setBounds(x + w - cw, fy + FORM_H - 12, cw, 12);
    }

    private static void field(final Label caption, final TextField field, final int x, final int y, final int w) {
        caption.setVisible(true);
        caption.setBounds(x, y, w, 8);
        field.setVisible(true);
        field.setBounds(x, y + 9, w, 12);
    }

    /** One button per script on the Mainframe's disk, flowing across the form; rebuilt when the files change. */
    private void layoutScripts(final Font font, final int x, final int top, final int w, final int maxY) {
        final List<String> files = data == null ? List.of() : data.iqlFiles();
        if (files.isEmpty()) {
            noScriptsLabel.setVisible(true);
            noScriptsLabel.setBounds(x, top + 1, w, 8);
            return;
        }
        if (!files.equals(scriptsBuiltFor)) {
            scripts.clear();
            for (final String f : files) {
                scripts.add(new Button(f, () -> script = f));
            }
            scriptsBuiltFor = List.copyOf(files);
        }
        scripts.setVisible(true);
        scripts.setBounds(x, top, w, maxY - top + 11);
        int cx = x;
        int cy = top;
        for (final var c : scripts.children()) {
            final Button b = (Button) c;
            final int cw = font.width(b.label()) + 8;
            if (cx + cw > x + w) {
                cx = x;
                cy += 12;
            }
            b.setVisible(cy <= maxY);
            b.setBounds(cx, cy, cw, 11);
            b.setPrimary(b.label().equals(script));
            cx += cw + 3;
        }
    }

    private void renderJobRow(final GuiGraphics g, final UiContext ctx, final AutomationPayload.JobRow j, final int index,
                              final int x, final int y, final int w, final int h, final boolean hovered, final boolean selected) {
        final Font font = ctx.font();
        ctx.skin().listRow(g, x, y, w, h, false, false);
        final int typeX = jobColumns.columnX(1);
        final int triggerX = jobColumns.columnX(2);
        g.drawString(font, Texts.clip(font, j.name(), typeX - x - 6), x + 2, y + 3, j.paused() ? ctx.skin().dim() : ctx.skin().text(), false);
        g.drawString(font, Texts.clip(font, j.type(), triggerX - typeX - 4), typeX, y + 3, ctx.skin().dim(), false);
        g.drawString(font, Texts.clip(font, j.trigger(), (x + w - 22) - triggerX - 4), triggerX, y + 3, j.paused() ? C_WARN : C_GOOD, false);
        // Pause/resume + delete glyphs.
        g.drawString(font, j.paused() ? ">" : "=", x + w - 22, y + 3, ctx.skin().text(), false);
        g.drawString(font, "x", x + w - 10, y + 3, C_DELETE, false);
    }

    private void jobClicked(final int index, final int button, final double mx, final double my) {
        if (data == null || button != 0 || index < 0 || index >= data.jobs().size()) {
            return;
        }
        final AutomationPayload.JobRow j = data.jobs().get(index);
        final int ppX = jobList.right() - 22;
        final int delX = jobList.right() - 10;
        if (mx >= delX - 2) {
            action(j.name(), JobActionPayload.ACTION_DELETE);
        } else if (mx >= ppX - 2) {
            action(j.name(), j.paused() ? JobActionPayload.ACTION_RESUME : JobActionPayload.ACTION_PAUSE);
        }
    }

    private void action(final String name, final int act) {
        PacketDistributor.sendToServer(new JobActionPayload(host, monitorPos, name, act));
    }

    private void create() {
        final String item = newType == CreateAutomationJobPayload.TYPE_IQL_SCRIPT ? script : itemField.edit();
        PacketDistributor.sendToServer(new CreateAutomationJobPayload(host, monitorPos, newType,
                nameField.edit(), item, parseLong(amountField.edit()), fromField.edit(), toField.edit(), intervalField.edit()));
        for (final TextField f : List.of(nameField, itemField, amountField, intervalField, fromField, toField)) {
            f.set("");
        }
        script = "";
        root.focus(null);
    }

    private static long parseLong(final String s) {
        try {
            return Math.max(1, Long.parseLong(s.trim()));
        } catch (final NumberFormatException e) {
            return 1;
        }
    }

    // input

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        root.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseReleased(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        root.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double delta) {
        if (root.mouseScrolled(lastMouseX, lastMouseY, delta)) {
            return true;
        }
        jobList.setScroll(jobList.scroll() + (delta > 0 ? -1 : 1));
        return true;
    }

    @Override
    public boolean charTyped(final char c) {
        return root.charTyped(c);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        return root.keyPressed(key, scanCode, modifiers);
    }
}
