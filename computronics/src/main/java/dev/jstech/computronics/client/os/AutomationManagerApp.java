/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.computronics.operation.payload.AutomationPayload;
import dev.jstech.computronics.operation.payload.CreateAutomationJobPayload;
import dev.jstech.computronics.operation.payload.JobActionPayload;
import dev.jstech.computronics.operation.payload.RequestAutomationPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Automation Manager: the front-end for the network's standing jobs. It shows whether a job engine is
 * online on the Mainframe, lists the saved jobs with pause/resume/delete, and creates new ones from a form
 * (Keep Stock / Batch Craft / Periodic Move) that the server compiles into jobs — no IQL is typed here.
 * Requires the Automation Engine (or the IQL Engine) on the Mainframe for jobs to actually run.
 */
public final class AutomationManagerApp implements DesktopApp {

    private static final int REFRESH_FRAMES = 40;
    private static final int C_GOOD = 0xFF2EA043;
    private static final int C_WARN = 0xFFE0A020;

    private static final String[] TYPE_LABELS = {"Keep Stock", "Batch Craft", "Move", "IQL"};

    private record Hit(int x, int y, int w, int h, Runnable onClick) {
        boolean contains(final double mx, final double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final BlockPos host;
    private final BlockPos monitorPos;
    private OsSkin skin = OsSkin.fallback();
    private AutomationPayload data;
    private int newType;
    private final Map<String, String> fields = new LinkedHashMap<>();
    private String focused;
    private int jobScroll;
    private int frame;
    private final List<Hit> hits = new ArrayList<>();

    private static AutomationManagerApp active;

    public AutomationManagerApp(final BlockPos host, final BlockPos monitorPos) {
        this.host = host;
        this.monitorPos = monitorPos;
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

    @Override public String title() {
        return "Automation Manager";
    }

    @Override public int defaultWidth() {
        return 320;
    }

    @Override public int defaultHeight() {
        return 216;
    }

    @Override public int minWidth() {
        return 280;
    }

    @Override public int minHeight() {
        return 190;
    }

    @Override public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
        active = this;
    }

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        hits.clear();
        g.fill(x, y, x + width, y + height, skin.windowBg());
        frame++;
        if (frame % REFRESH_FRAMES == 0) {
            request();
        }
        final int px = x + 6;
        final int pw = width - 12;
        if (data == null) {
            g.drawString(font, "Contacting Mainframe...", px, y + 8, skin.dim(), false);
            return;
        }

        // Engine status bar.
        final boolean on = data.engineOnline();
        g.fill(px, y + 6, px + pw, y + 20, on ? 0x162EA043 : 0x22E0A020);
        g.fill(px + 4, y + 11, px + 8, y + 15, on ? C_GOOD : C_WARN);
        g.drawString(font, on ? data.engineLabel() + " online" : "No engine - install the Automation Engine "
                + "on the Mainframe", px + 12, y + 9, on ? skin.text() : C_WARN, false);
        g.drawString(font, data.jobs().size() + " jobs", px + pw - font.width(data.jobs().size() + " jobs"),
                y + 9, skin.dim(), false);

        // Split: job list (top) and the new-job form (bottom).
        final int listTop = y + 24;
        final int formH = 78;
        final int listH = y + height - formH - listTop - 4;
        jobList(g, font, px, listTop, pw, listH, mouseX, mouseY);
        g.fill(px, listTop + listH + 1, px + pw, listTop + listH + 2, skin.edge());
        newJobForm(g, font, px, listTop + listH + 4, pw, formH, mouseX, mouseY);
    }

    private void jobList(final GuiGraphics g, final Font font, final int x, final int top, final int w,
                         final int h, final int mouseX, final int mouseY) {
        g.drawString(font, "JOB", x + 2, top, skin.dim(), false);
        g.drawString(font, "TYPE", x + (int) (w * 0.40), top, skin.dim(), false);
        g.drawString(font, "TRIGGER", x + (int) (w * 0.62), top, skin.dim(), false);
        g.drawString(font, "ACT", x + w - 22, top, skin.dim(), false);
        final List<AutomationPayload.JobRow> jobs = data.jobs();
        final int rowH = 13;
        final int listTop = top + 11;
        final int maxRows = Math.max(1, (h - 11) / rowH);
        jobScroll = Math.max(0, Math.min(jobScroll, Math.max(0, jobs.size() - maxRows)));
        if (jobs.isEmpty()) {
            g.drawString(font, "No jobs yet - create one below.", x + 2, listTop + 2, skin.dim(), false);
            return;
        }
        int ry = listTop;
        for (int i = jobScroll; i < jobs.size() && ry + rowH <= top + h; i++) {
            final AutomationPayload.JobRow j = jobs.get(i);
            skin.listRow(g, x, ry, w, rowH, false, false);
            g.drawString(font, trim(font, j.name(), (int) (w * 0.40) - 6), x + 2, ry + 3,
                    j.paused() ? skin.dim() : skin.text(), false);
            g.drawString(font, trim(font, j.type(), (int) (w * 0.22) - 4), x + (int) (w * 0.40), ry + 3,
                    skin.dim(), false);
            g.drawString(font, trim(font, j.trigger(), (int) (w * 0.24) - 4), x + (int) (w * 0.62), ry + 3,
                    j.paused() ? C_WARN : C_GOOD, false);
            // Pause/resume + delete glyphs.
            final int ppX = x + w - 22;
            final int delX = x + w - 10;
            g.drawString(font, j.paused() ? ">" : "=", ppX, ry + 3, skin.text(), false);
            g.drawString(font, "x", delX, ry + 3, 0xFFC0504A, false);
            final String jobName = j.name();
            final boolean paused = j.paused();
            hits.add(new Hit(ppX - 2, ry, 12, rowH, () -> action(jobName,
                    paused ? JobActionPayload.ACTION_RESUME : JobActionPayload.ACTION_PAUSE)));
            hits.add(new Hit(delX - 2, ry, 12, rowH, () -> action(jobName, JobActionPayload.ACTION_DELETE)));
            ry += rowH;
        }
    }

    private void newJobForm(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                            final int h, final int mouseX, final int mouseY) {
        g.drawString(font, "NEW JOB", x + 2, y, skin.dim(), false);
        // Type segmented control.
        int sx = x + 44;
        for (int i = 0; i < TYPE_LABELS.length; i++) {
            final int sw = font.width(TYPE_LABELS[i]) + 10;
            final boolean onSeg = i == newType;
            skin.button(g, font, sx, y - 2, sw, 12, TYPE_LABELS[i], false, false, onSeg);
            final int target = i;
            hits.add(new Hit(sx, y - 2, sw, 12, () -> {
                newType = target;
                focused = null;
            }));
            sx += sw + 2;
        }

        // Fields for the chosen type, laid out in two columns.
        final int fy = y + 14;
        final int colW = (w - 6) / 2;
        field(g, font, "name", "Name", x, fy, colW, mouseX, mouseY);
        switch (newType) {
            case CreateAutomationJobPayload.TYPE_KEEP_STOCK -> {
                field(g, font, "item", "Item id", x + colW + 6, fy, colW, mouseX, mouseY);
                field(g, font, "amount", "Keep at least", x, fy + 24, colW, mouseX, mouseY);
            }
            case CreateAutomationJobPayload.TYPE_BATCH_CRAFT -> {
                field(g, font, "item", "Item id", x + colW + 6, fy, colW, mouseX, mouseY);
                field(g, font, "amount", "Amount", x, fy + 24, colW, mouseX, mouseY);
                field(g, font, "interval", "Every (30s)", x + colW + 6, fy + 24, colW, mouseX, mouseY);
            }
            case CreateAutomationJobPayload.TYPE_PERIODIC_MOVE -> {
                field(g, font, "item", "Item (blank=all)", x + colW + 6, fy, colW, mouseX, mouseY);
                field(g, font, "from", "From", x, fy + 24, colW / 2 - 2, mouseX, mouseY);
                field(g, font, "to", "To", x + colW / 2 + 2, fy + 24, colW / 2 - 2, mouseX, mouseY);
                field(g, font, "interval", "Every (30s)", x + colW + 6, fy + 24, colW, mouseX, mouseY);
            }
            case CreateAutomationJobPayload.TYPE_IQL_SCRIPT -> {
                field(g, font, "interval", "Every (30s)", x + colW + 6, fy, colW, mouseX, mouseY);
                g.drawString(font, "Script (on Mainframe disk):", x, fy + 22, skin.dim(), false);
                final String sel = fields.getOrDefault("item", "");
                if (data.iqlFiles().isEmpty()) {
                    g.drawString(font, "no .iql files - save one in the NMS", x, fy + 32, skin.dim(), false);
                } else {
                    int cx = x;
                    int cyf = fy + 31;
                    for (final String f : data.iqlFiles()) {
                        final int cw = font.width(f) + 8;
                        if (cx + cw > x + w) {
                            cx = x;
                            cyf += 12;
                        }
                        if (cyf > fy + 44) {
                            break;
                        }
                        skin.button(g, font, cx, cyf, cw, 11, f, false, false, f.equals(sel));
                        final String pick = f;
                        hits.add(new Hit(cx, cyf, cw, 11, () -> fields.put("item", pick)));
                        cx += cw + 3;
                    }
                }
            }
            default -> { }
        }

        // Create button.
        final String cap = "Create job";
        final int cw = font.width(cap) + 14;
        final int cbx = x + w - cw;
        final int cby = y + h - 12;
        final boolean chov = mouseX >= cbx && mouseX < cbx + cw && mouseY >= cby && mouseY < cby + 12;
        skin.button(g, font, cbx, cby, cw, 12, cap, chov, false, true);
        hits.add(new Hit(cbx, cby, cw, 12, this::create));
    }

    private void field(final GuiGraphics g, final Font font, final String key, final String label,
                       final int x, final int y, final int w, final int mouseX, final int mouseY) {
        g.drawString(font, label, x, y, skin.dim(), false);
        final boolean foc = key.equals(focused);
        skin.field(g, x, y + 9, w, 12, foc);
        final String v = fields.getOrDefault(key, "");
        final String show = v.isEmpty() && !foc ? "" : (foc ? v + "_" : v);
        g.drawString(font, trim(font, show, w - 6), x + 3, y + 12, skin.text(), false);
        hits.add(new Hit(x, y + 9, w, 12, () -> focused = key));
    }

    private void action(final String name, final int act) {
        PacketDistributor.sendToServer(new JobActionPayload(host, monitorPos, name, act));
    }

    private void create() {
        final long amount = parseLong(fields.getOrDefault("amount", ""));
        PacketDistributor.sendToServer(new CreateAutomationJobPayload(host, monitorPos, newType,
                fields.getOrDefault("name", ""), fields.getOrDefault("item", ""), amount,
                fields.getOrDefault("from", ""), fields.getOrDefault("to", ""),
                fields.getOrDefault("interval", "")));
        fields.clear();
        focused = null;
    }

    private static long parseLong(final String s) {
        try {
            return Math.max(1, Long.parseLong(s.trim()));
        } catch (final NumberFormatException e) {
            return 1;
        }
    }

    private static String trim(final Font font, final String s, final int maxWidth) {
        if (maxWidth <= 0 || font.width(s) <= maxWidth) {
            return s;
        }
        String out = s;
        while (!out.isEmpty() && font.width(out + "...") > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "...";
    }

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY,
                             final int button) {
        if (button != 0) {
            return;
        }
        for (final Hit hit : hits) {
            if (hit.contains(mouseX, mouseY)) {
                hit.onClick().run();
                return;
            }
        }
        focused = null;
    }

    @Override
    public boolean mouseScrolled(final double delta) {
        jobScroll = Math.max(0, jobScroll - (int) Math.signum(delta));
        return true;
    }

    @Override
    public boolean charTyped(final char c) {
        if (focused == null || c < ' ' || c == 127) {
            return false;
        }
        // Amount is digits only; the other fields take no spaces (item ids and names are single tokens).
        if ("amount".equals(focused) && (c < '0' || c > '9')) {
            return true;
        }
        if (c == ' ') {
            return true;
        }
        final String cur = fields.getOrDefault(focused, "");
        if (cur.length() < 48) {
            fields.put(focused, cur + c);
        }
        return true;
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (focused == null) {
            return false;
        }
        if (key == 259) { // backspace
            final String cur = fields.getOrDefault(focused, "");
            if (!cur.isEmpty()) {
                fields.put(focused, cur.substring(0, cur.length() - 1));
            }
            return true;
        }
        if (key == 256) { // escape clears focus (the window stays open)
            focused = null;
            return true;
        }
        return false;
    }
}
