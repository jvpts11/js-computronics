/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.os;

import dev.jsc.jscomputronics.module.computing.client.JscOsTheme;
import dev.jsc.jscomputronics.module.computing.operation.payload.CraftCatalogPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.CraftPlanPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.CraftPlannerPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.NiCraftPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RequestCraftPlannerPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Craft Planner: pick a target from the network's craft catalogue and see its full plan before crafting.
 * The plan reports whether the item is craftable, whether the request is feasible from current stock, the
 * largest feasible amount, the ordered stages, and the raw-ingredient bill (need vs have, short in red).
 * The catalogue and the plan are computed server-side; the Craft button submits the same request the
 * terminal and Network Interactor use.
 */
public final class CraftPlannerApp implements DesktopApp {

    private static final int REFRESH_FRAMES = 60;
    private static final int C_GOOD = 0xFF2EA043;
    private static final int C_CRIT = 0xFFD1495B;

    private record Hit(int x, int y, int w, int h, Runnable onClick) {
        boolean contains(final double mx, final double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final BlockPos host;
    private final BlockPos monitorPos;
    private OsSkin skin = OsSkin.fallback();
    private List<CraftCatalogPayload.Entry> catalog = List.of();
    private CraftPlannerPayload plan;
    private ItemStack selected = ItemStack.EMPTY;
    private long qty = 1;
    private boolean treeMode;
    private int treeScroll;
    private String search = "";
    private int catScroll;
    private int frame;
    private final List<Hit> hits = new ArrayList<>();

    private static CraftPlannerApp active;

    public CraftPlannerApp(final BlockPos host, final BlockPos monitorPos) {
        this.host = host;
        this.monitorPos = monitorPos;
        active = this;
        PacketDistributor.sendToServer(new RequestCraftPlannerPayload(host, monitorPos, ItemStack.EMPTY, 0));
    }

    public static void acceptCatalog(final List<CraftCatalogPayload.Entry> entries) {
        if (active != null) {
            active.catalog = entries;
        }
    }

    public static void accept(final CraftPlannerPayload payload) {
        if (active != null) {
            active.plan = payload;
        }
    }

    private void requestPlan() {
        if (!selected.isEmpty()) {
            PacketDistributor.sendToServer(new RequestCraftPlannerPayload(host, monitorPos, selected, qty));
        }
    }

    @Override public String title() {
        return "Craft Planner";
    }

    @Override public int defaultWidth() {
        return 320;
    }

    @Override public int defaultHeight() {
        return 200;
    }

    @Override public int minWidth() {
        return 280;
    }

    @Override public int minHeight() {
        return 170;
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
            requestPlan();
        }
        final int px = x + 6;
        final int py = y + 6;
        final int ph = height - 12;
        final int leftW = Math.max(96, (int) (width * 0.38));
        catalogList(g, font, px, py, leftW - 6, ph, mouseX, mouseY);
        g.fill(px + leftW - 3, py, px + leftW - 2, py + ph, skin.edge());
        planPanel(g, font, px + leftW, py, width - 12 - leftW, ph, mouseX, mouseY);
    }

    private void catalogList(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                             final int h, final int mouseX, final int mouseY) {
        // Search field.
        skin.field(g, x, y, w, 13, false);
        final String shown = search.isEmpty() ? "search item..." : search;
        g.drawString(font, trim(font, shown, w - 6), x + 3, y + 3, search.isEmpty() ? skin.dim() : skin.text(), false);

        final List<CraftCatalogPayload.Entry> filtered = filtered();
        final int rowH = 15;
        final int top = y + 16;
        final int maxRows = Math.max(1, (h - 16) / rowH);
        catScroll = Math.max(0, Math.min(catScroll, Math.max(0, filtered.size() - maxRows)));
        int ry = top;
        for (int i = catScroll; i < filtered.size() && ry + rowH <= y + h; i++) {
            final CraftCatalogPayload.Entry e = filtered.get(i);
            final boolean sel = ItemStack.isSameItemSameComponents(e.result(), selected);
            final boolean hov = mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + rowH;
            skin.listRow(g, x, ry, w, rowH, hov, sel);
            itemIcon(g, e.result(), x + 1, ry, 12);
            g.drawString(font, trim(font, e.result().getHoverName().getString(), w - 18),
                    x + 15, ry + 3, sel ? skin.accent() : skin.text(), false);
            final ItemStack pick = e.result().copy();
            hits.add(new Hit(x, ry, w, rowH, () -> select(pick)));
            ry += rowH;
        }
        if (filtered.isEmpty()) {
            g.drawString(font, catalog.isEmpty() ? "loading..." : "no match", x + 2, top + 2, skin.dim(), false);
        }
    }

    private void planPanel(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                           final int h, final int mouseX, final int mouseY) {
        if (selected.isEmpty()) {
            g.drawString(font, "Pick an item to plan.", x + 2, y + 4, skin.dim(), false);
            return;
        }
        // Header: icon + name + quantity stepper.
        itemIcon(g, selected, x, y, 16);
        g.drawString(font, trim(font, selected.getHoverName().getString(), w - 84), x + 20, y + 4, skin.text(), false);
        final int stepX = x + w - 56;
        stepButton(g, font, stepX, y, "-", () -> setQty(qty - step()));
        g.drawString(font, String.valueOf(qty), stepX + 16 + (24 - font.width(String.valueOf(qty))) / 2, y + 3,
                skin.text(), false);
        stepButton(g, font, stepX + 44, y, "+", () -> setQty(qty + step()));

        int row = y + 20;
        if (plan == null) {
            g.drawString(font, "planning...", x + 2, row, skin.dim(), false);
            return;
        }
        if (!plan.craftable()) {
            g.drawString(font, "No pattern on the network makes this.", x + 2, row, C_CRIT, false);
            return;
        }
        // Status line: feasible pill + max + stage count.
        final String pill = plan.feasible() ? "Craftable" : "Partial";
        final int pillW = font.width(pill) + 8;
        g.fill(x, row, x + pillW, row + 11, plan.feasible() ? 0x2E2EA043 : 0x33E0A020);
        g.drawString(font, pill, x + 4, row + 2, plan.feasible() ? C_GOOD : 0xFFE0A020, false);
        g.drawString(font, "max " + JscOsTheme.fmt(plan.maxFeasible()) + "  -  " + plan.stages().size() + " stages",
                x + pillW + 6, row + 2, skin.dim(), false);
        // Steps/Tree toggle on the right of the status line.
        final String tog = treeMode ? "Steps" : "Tree";
        final int togW = font.width(tog) + 10;
        final int togX = x + w - togW;
        final boolean togHov = mouseX >= togX && mouseX < togX + togW && mouseY >= row && mouseY < row + 11;
        skin.button(g, font, togX, row - 1, togW, 11, tog, togHov, false, false);
        hits.add(new Hit(togX, row - 1, togW, 11, () -> treeMode = !treeMode));
        row += 14;
        g.fill(x, row, x + w, row + 1, skin.edge());
        row += 3;

        final int bottom = y + h - 16;
        if (treeMode) {
            renderTree(g, font, x, row, w, bottom - row);
            drawCraftButton(g, font, x, y, w, h, mouseX, mouseY);
            return;
        }
        // Stages then ingredients, sharing the remaining height.
        final int half = (bottom - row) / 2;
        g.drawString(font, "STAGES", x + 2, row, skin.dim(), false);
        int sy = row + 11;
        for (final CraftPlannerPayload.Stage s : plan.stages()) {
            if (sy + 10 > row + half) {
                break;
            }
            g.drawString(font, trim(font, s.name(), w - 60), x + 4, sy, skin.text(), false);
            final String tag = (s.machine() ? "machine" : "bench") + " x" + s.runs();
            g.drawString(font, tag, x + w - font.width(tag), sy, skin.dim(), false);
            sy += 10;
        }
        int iy = row + half + 2;
        g.drawString(font, "INGREDIENTS", x + 2, iy, skin.dim(), false);
        iy += 11;
        for (final CraftPlanPayload.Row r : plan.ingredients()) {
            if (iy + 11 > bottom) {
                break;
            }
            final boolean ok = r.have() >= r.need();
            itemIcon(g, r.item(), x + 1, iy - 1, 11);
            g.drawString(font, trim(font, r.item().getHoverName().getString(), w - 76), x + 15, iy, skin.text(), false);
            final String s = ok ? "have " + JscOsTheme.fmt(r.have()) : "short " + JscOsTheme.fmt(r.need() - r.have());
            g.drawString(font, s, x + w - font.width(s), iy, ok ? C_GOOD : C_CRIT, false);
            iy += 11;
        }
        drawCraftButton(g, font, x, y, w, h, mouseX, mouseY);
    }

    /** The recipe dependency tree, flattened in pre-order and drawn indented by depth. */
    private void renderTree(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                            final int h) {
        g.drawString(font, "CRAFT TREE", x + 2, y, skin.dim(), false);
        final List<CraftPlannerPayload.TreeNode> tree = plan.tree();
        final int rowH = 11;
        final int top = y + 11;
        final int maxRows = Math.max(1, (h - 11) / rowH);
        treeScroll = Math.max(0, Math.min(treeScroll, Math.max(0, tree.size() - maxRows)));
        int ry = top;
        for (int i = treeScroll; i < tree.size() && ry + rowH <= y + h; i++) {
            final CraftPlannerPayload.TreeNode n = tree.get(i);
            final int ix = x + 2 + n.depth() * 9;
            if (n.depth() > 0) {
                g.fill(x + 2 + (n.depth() - 1) * 9 + 3, ry + 4, ix - 1, ry + 5, skin.edge());
            }
            itemIcon(g, n.item(), ix, ry - 1, 10);
            final String label = JscOsTheme.fmt(n.qty()) + "x " + n.item().getHoverName().getString();
            g.drawString(font, trim(font, label, w - (ix - x) - 13 - 40), ix + 12, ry, skin.text(), false);
            if (!n.craftable()) {
                g.drawString(font, "raw", x + w - font.width("raw"), ry, skin.dim(), false);
            }
            ry += rowH;
        }
        if (tree.size() > maxRows) {
            g.drawString(font, "wheel to scroll", x + w - font.width("wheel to scroll"), y, skin.dim(), false);
        }
    }

    private void drawCraftButton(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                                 final int h, final int mouseX, final int mouseY) {
        final String cap = "Craft " + qty;
        final int cw = font.width(cap) + 14;
        final int cbx = x + w - cw;
        final int cby = y + h - 13;
        final boolean chov = mouseX >= cbx && mouseX < cbx + cw && mouseY >= cby && mouseY < cby + 13;
        skin.button(g, font, cbx, cby, cw, 13, cap, chov, false, plan.craftable());
        hits.add(new Hit(cbx, cby, cw, 13, this::craft));
    }

    private void select(final ItemStack stack) {
        this.selected = stack;
        this.qty = 1;
        this.plan = null;
        requestPlan();
    }

    private void setQty(final long q) {
        this.qty = Math.max(1, Math.min(100_000, q));
        this.plan = null;
        requestPlan();
    }

    private long step() {
        return qty < 16 ? 1 : qty < 64 ? 8 : qty < 512 ? 64 : 256;
    }

    private void craft() {
        if (!selected.isEmpty()) {
            PacketDistributor.sendToServer(new NiCraftPayload(host, monitorPos, selected, qty));
        }
    }

    private List<CraftCatalogPayload.Entry> filtered() {
        if (search.isEmpty()) {
            return catalog;
        }
        final String q = search.toLowerCase(Locale.ROOT);
        final List<CraftCatalogPayload.Entry> out = new ArrayList<>();
        for (final CraftCatalogPayload.Entry e : catalog) {
            if (e.result().getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(e);
            }
        }
        return out;
    }

    private void itemIcon(final GuiGraphics g, final ItemStack stack, final int x, final int y, final int size) {
        if (stack.isEmpty()) {
            g.fill(x + 1, y + 1, x + size - 1, y + size - 1, skin.fieldBg());
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        final float s = size / 16.0f;
        g.pose().scale(s, s, 1);
        DesktopItems.item(g, stack, 0, 0);
        g.pose().popPose();
    }

    private void stepButton(final GuiGraphics g, final Font font, final int x, final int y, final String label,
                            final Runnable onClick) {
        skin.button(g, font, x, y, 14, 12, label, false, false, false);
        hits.add(new Hit(x, y, 14, 12, onClick));
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
    }

    @Override
    public boolean mouseScrolled(final double delta) {
        if (treeMode && !selected.isEmpty()) {
            treeScroll = Math.max(0, treeScroll - (int) Math.signum(delta));
        } else {
            catScroll = Math.max(0, catScroll - (int) Math.signum(delta));
        }
        return true;
    }

    @Override
    public boolean charTyped(final char c) {
        if (c >= ' ' && c != 127) {
            search += c;
            catScroll = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (key == 259 && !search.isEmpty()) { // backspace
            search = search.substring(0, search.length() - 1);
            return true;
        }
        return false;
    }
}
