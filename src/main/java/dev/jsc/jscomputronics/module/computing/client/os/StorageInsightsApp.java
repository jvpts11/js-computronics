/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.os;

import dev.jsc.jscomputronics.module.computing.client.JscOsTheme;
import dev.jsc.jscomputronics.module.computing.operation.payload.ItemDetailPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.NetworkItemEntry;
import dev.jsc.jscomputronics.module.computing.operation.payload.RequestItemDetailPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RequestStorageInsightsPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.StorageInsightsPayload;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Storage Insights: a dashboard over the network's contents. It shows totals, the biggest types as a bar
 * chart (searchable, with pinned favourites first), the types running low against an adjustable threshold,
 * and how full each server is. Clicking a type opens a detail view — where it is stored, what it makes, and
 * which buses filter it. Data is computed server-side; the dashboard re-requests on a slow cadence.
 */
public final class StorageInsightsApp implements DesktopApp {

    private static final int REFRESH_FRAMES = 60;
    private static final int C_CRIT = 0xFFD1495B;
    private static final int C_PIN = 0xFFE0A020;

    private record Hit(int x, int y, int w, int h, Runnable onClick) {
        boolean contains(final double mx, final double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final BlockPos host;
    private final BlockPos monitorPos;
    private OsSkin skin = OsSkin.fallback();
    private StorageInsightsPayload data;
    private int threshold = 64;
    private int frame;
    private String search = "";
    private boolean searchFocused;
    private final Set<String> pinned = new LinkedHashSet<>();
    private boolean detailMode;
    private ItemDetailPayload detail;
    private final List<Hit> hits = new ArrayList<>();

    private static StorageInsightsApp active;

    public StorageInsightsApp(final BlockPos host, final BlockPos monitorPos) {
        this.host = host;
        this.monitorPos = monitorPos;
        active = this;
        request();
    }

    public static void accept(final StorageInsightsPayload payload) {
        if (active != null) {
            active.data = payload;
        }
    }

    public static void acceptDetail(final ItemDetailPayload payload) {
        if (active != null) {
            active.detail = payload;
        }
    }

    private void request() {
        PacketDistributor.sendToServer(new RequestStorageInsightsPayload(host, monitorPos));
    }

    @Override
    public void onRestored() {
        active = this;
        request();
    }

    @Override public String title() {
        return "Storage Insights";
    }

    @Override public int defaultWidth() {
        return 320;
    }

    @Override public int defaultHeight() {
        return 208;
    }

    @Override public int minWidth() {
        return 270;
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
        if (!detailMode && frame % REFRESH_FRAMES == 0) {
            request();
        }
        final int px = x + 6;
        final int pw = width - 12;
        if (detailMode) {
            renderDetail(g, font, px, y + 6, pw, height - 12, mouseX, mouseY);
            return;
        }
        if (data == null) {
            g.drawString(font, "Reading network...", px, y + 8, skin.dim(), false);
            return;
        }
        dashboard(g, font, px, y + 6, pw, height - 12, mouseX, mouseY);
    }

    private void dashboard(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                           final int h, final int mouseX, final int mouseY) {
        // Search bar + summary tiles on the first row.
        final int searchW = (int) (w * 0.42);
        skin.field(g, x, y, searchW, 13, searchFocused);
        final String shown = search.isEmpty() && !searchFocused ? "search item..."
                : (searchFocused ? search + "_" : search);
        g.drawString(font, trim(font, shown, searchW - 6), x + 3, y + 3,
                search.isEmpty() && !searchFocused ? skin.dim() : skin.text(), false);
        hits.add(new Hit(x, y, searchW, 13, () -> searchFocused = true));
        final int lowCount = lowBelowThreshold().size();
        smallTile(g, font, x + searchW + 6, y, "TYPES", String.valueOf(data.typeCount()), false);
        smallTile(g, font, x + searchW + 6 + (w - searchW - 6) / 3, y, "TOTAL", JscOsTheme.fmt(data.totalItems()),
                false);
        smallTile(g, font, x + searchW + 6 + 2 * (w - searchW - 6) / 3, y, "LOW", String.valueOf(lowCount),
                lowCount > 0);

        final int colTop = y + 18;
        final int colH = h - 18;
        final int leftW = (int) (w * 0.56);
        topItems(g, font, x, colTop, leftW - 6, colH, mouseX, mouseY);
        rightColumn(g, font, x + leftW, colTop, w - leftW, colH, mouseX, mouseY);
    }

    private void smallTile(final GuiGraphics g, final Font font, final int x, final int y, final String key,
                           final String value, final boolean alert) {
        g.drawString(font, key + " " + value, x, y + 3, alert ? C_CRIT : skin.dim(), false);
    }

    private void topItems(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                          final int h, final int mouseX, final int mouseY) {
        g.drawString(font, "TOP ITEMS", x, y, skin.dim(), false);
        final List<NetworkItemEntry> shown = displayItems();
        final long max = data.topItems().isEmpty() ? 1 : Math.max(1, data.topItems().get(0).total());
        int ry = y + 12;
        final int rowH = 15;
        final int barX = x + 96;
        final int qtyW = 34;
        final int barW = Math.max(10, w - 96 - qtyW - 4);
        for (final NetworkItemEntry e : shown) {
            if (ry + rowH > y + h) {
                break;
            }
            final boolean hov = mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + rowH;
            if (hov) {
                g.fill(x, ry, x + w, ry + rowH - 1, skin.listHover());
            }
            // Pin star.
            final boolean pin = pinned.contains(e.key().toString());
            g.drawString(font, pin ? "*" : "-", x, ry + 2, pin ? C_PIN : skin.dim(), false);
            final String pinKey = e.key().toString();
            hits.add(new Hit(x - 1, ry, 8, rowH, () -> togglePin(pinKey)));
            itemIcon(g, e.key(), x + 8, ry, 12);
            g.drawString(font, trim(font, e.key().displayName().getString(), 96 - 24), x + 22, ry + 2,
                    skin.text(), false);
            g.fill(barX, ry + 4, barX + barW, ry + 10, skin.fieldBg());
            g.fill(barX, ry + 4, barX + (int) (barW * Math.min(1.0, (double) e.total() / max)), ry + 10,
                    skin.accent());
            final String q = JscOsTheme.fmt(e.total());
            g.drawString(font, q, x + w - font.width(q), ry + 2, skin.dim(), false);
            final ItemStack stack = e.key().stack(1);
            hits.add(new Hit(x + 8, ry, w - 8, rowH, () -> openDetail(stack)));
            ry += rowH;
        }
        if (shown.isEmpty()) {
            g.drawString(font, search.isEmpty() ? "loading..." : "no match", x + 2, y + 14, skin.dim(), false);
        }
    }

    private void rightColumn(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                             final int h, final int mouseX, final int mouseY) {
        g.drawString(font, "LOW STOCK", x, y, skin.dim(), false);
        int ry = y + 12;
        final int rowH = 13;
        final List<NetworkItemEntry> low = lowBelowThreshold();
        if (low.isEmpty()) {
            g.drawString(font, "all stocked", x, ry + 1, skin.dim(), false);
            ry += rowH;
        } else {
            for (final NetworkItemEntry e : low) {
                if (ry + rowH > y + h / 2) {
                    break;
                }
                g.fill(x, ry, x + w, ry + rowH - 1, 0x18D1495B);
                itemIcon(g, e.key(), x + 1, ry, 11);
                g.drawString(font, trim(font, e.key().displayName().getString(), w - 56), x + 15, ry + 2,
                        skin.text(), false);
                final String s = e.total() + "/" + threshold;
                g.drawString(font, s, x + w - font.width(s), ry + 2, C_CRIT, false);
                final ItemStack stack = e.key().stack(1);
                hits.add(new Hit(x, ry, w, rowH, () -> openDetail(stack)));
                ry += rowH;
            }
        }

        final int thY = y + h / 2 + 2;
        g.drawString(font, "Threshold", x, thY + 2, skin.dim(), false);
        final int minusX = x + w - 46;
        final int plusX = x + w - 14;
        button(g, font, minusX, thY, 12, 11, "-", mouseX, mouseY, () -> threshold = Math.max(1, threshold - 16));
        g.drawString(font, String.valueOf(threshold), (minusX + plusX + 12) / 2 - font.width(String.valueOf(threshold)) / 2,
                thY + 2, skin.text(), false);
        button(g, font, plusX, thY, 12, 11, "+", mouseX, mouseY, () -> threshold = Math.min(4096, threshold + 16));

        final int svY = thY + 16;
        g.drawString(font, "BY SERVER", x, svY, skin.dim(), false);
        int sy = svY + 12;
        long smax = 1;
        for (final NetworkItemEntry.StorageShare s : data.servers()) {
            smax = Math.max(smax, s.qty());
        }
        for (final NetworkItemEntry.StorageShare s : data.servers()) {
            if (sy + 11 > y + h) {
                break;
            }
            g.drawString(font, trim(font, s.label(), 44), x, sy, skin.dim(), false);
            final int bx = x + 46;
            final int bw = Math.max(8, w - 46 - 30);
            g.fill(bx, sy, bx + bw, sy + 6, skin.fieldBg());
            g.fill(bx, sy, bx + (int) (bw * Math.min(1.0, (double) s.qty() / smax)), sy + 6, skin.accent());
            final String q = JscOsTheme.fmt(s.qty());
            g.drawString(font, q, x + w - font.width(q), sy - 1, skin.dim(), false);
            sy += 11;
        }
    }

    private void renderDetail(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                              final int h, final int mouseX, final int mouseY) {
        // Back button + item header.
        final boolean bhov = mouseX >= x && mouseX < x + 34 && mouseY >= y && mouseY < y + 12;
        skin.button(g, font, x, y, 34, 12, "< Back", bhov, false, false);
        hits.add(new Hit(x, y, 34, 12, () -> {
            detailMode = false;
            detail = null;
        }));
        if (detail == null) {
            g.drawString(font, "Loading item...", x + 42, y + 3, skin.dim(), false);
            return;
        }
        itemIcon(g, detail.item(), x + 40, y - 1, 16);
        g.drawString(font, trim(font, detail.item().getHoverName().getString(), w - 130), x + 58, y + 3,
                skin.text(), false);
        final String tot = JscOsTheme.fmt(detail.total()) + " total";
        g.drawString(font, tot, x + w - font.width(tot), y + 3, skin.dim(), false);
        g.fill(x, y + 15, x + w, y + 16, skin.edge());

        // Three sections down the panel.
        int ry = y + 19;
        final int third = (y + h - ry) / 3;
        ry = section(g, font, x, ry, w, third, "STORED IN", detail.storedIn(),
                s -> s.label() + "  " + JscOsTheme.fmt(s.qty()));
        ry = usesSection(g, font, x, ry, w, third);
        busesSection(g, font, x, ry, w, y + h - ry);
    }

    private int section(final GuiGraphics g, final Font font, final int x, final int y, final int w, final int h,
                        final String title, final List<NetworkItemEntry.StorageShare> rows,
                        final java.util.function.Function<NetworkItemEntry.StorageShare, String> line) {
        g.drawString(font, title, x, y, skin.dim(), false);
        int ry = y + 11;
        if (rows.isEmpty()) {
            g.drawString(font, "-", x + 2, ry, skin.dim(), false);
            return y + h;
        }
        for (final NetworkItemEntry.StorageShare s : rows) {
            if (ry + 10 > y + h) {
                break;
            }
            g.drawString(font, trim(font, line.apply(s), w - 4), x + 2, ry, skin.text(), false);
            ry += 10;
        }
        return y + h;
    }

    private int usesSection(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                            final int h) {
        g.drawString(font, "USED TO MAKE", x, y, skin.dim(), false);
        if (detail.usedToMake().isEmpty()) {
            g.drawString(font, "nothing on the network", x + 2, y + 11, skin.dim(), false);
            return y + h;
        }
        int ix = x + 2;
        final int iy = y + 11;
        for (final ItemStack s : detail.usedToMake()) {
            if (ix + 16 > x + w) {
                break;
            }
            itemIcon(g, s, ix, iy, 14);
            hits.add(new Hit(ix, iy, 14, 14, () -> openDetail(s.copy())));
            ix += 16;
        }
        return y + h;
    }

    private void busesSection(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                              final int h) {
        g.drawString(font, "BUSES", x, y, skin.dim(), false);
        if (detail.buses().isEmpty()) {
            g.drawString(font, "not on any bus filter", x + 2, y + 11, skin.dim(), false);
            return;
        }
        int ry = y + 11;
        for (final ItemDetailPayload.BusRef b : detail.buses()) {
            if (ry + 10 > y + h) {
                break;
            }
            g.drawString(font, trim(font, b.name(), w - 60), x + 2, ry, skin.text(), false);
            g.drawString(font, b.kind(), x + w - font.width(b.kind()), ry, skin.accent(), false);
            ry += 10;
        }
    }

    private void openDetail(final ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        detailMode = true;
        detail = null;
        searchFocused = false;
        PacketDistributor.sendToServer(new RequestItemDetailPayload(host, monitorPos, stack));
    }

    private void togglePin(final String key) {
        if (!pinned.remove(key)) {
            pinned.add(key);
        }
    }

    /** Top items filtered by the search box, with pinned favourites sorted to the front. */
    private List<NetworkItemEntry> displayItems() {
        final List<NetworkItemEntry> out = new ArrayList<>();
        final String q = search.toLowerCase(Locale.ROOT);
        for (final NetworkItemEntry e : data.topItems()) {
            if (q.isEmpty() || e.key().displayName().getString().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(e);
            }
        }
        out.sort((a, b) -> Boolean.compare(pinned.contains(b.key().toString()), pinned.contains(a.key().toString())));
        return out;
    }

    private List<NetworkItemEntry> lowBelowThreshold() {
        if (data == null) {
            return List.of();
        }
        final List<NetworkItemEntry> out = new ArrayList<>();
        for (final NetworkItemEntry e : data.lowItems()) {
            if (e.total() < threshold) {
                out.add(e);
            }
        }
        return out;
    }

    private void itemIcon(final GuiGraphics g, final StorageKey key, final int x, final int y, final int size) {
        if (key.isItem()) {
            itemIcon(g, key.stack(1), x, y, size);
        } else {
            final int c = key.isFluid() ? 0xFF3A78C8 : 0xFF9A6BC9;
            g.fill(x + 1, y + 1, x + size - 1, y + size - 1, c);
            OsSkin.outline(g, x + 1, y + 1, size - 2, size - 2, skin.edge());
        }
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

    private void button(final GuiGraphics g, final Font font, final int x, final int y, final int w, final int h,
                        final String label, final int mouseX, final int mouseY, final Runnable onClick) {
        final boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        skin.button(g, font, x, y, w, h, label, hov, false, false);
        hits.add(new Hit(x, y, w, h, onClick));
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
        searchFocused = false;
    }

    @Override
    public boolean charTyped(final char c) {
        if (searchFocused && c >= ' ' && c != 127 && search.length() < 32) {
            search += c;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (searchFocused && key == 259 && !search.isEmpty()) {
            search = search.substring(0, search.length() - 1);
            return true;
        }
        return false;
    }
}
