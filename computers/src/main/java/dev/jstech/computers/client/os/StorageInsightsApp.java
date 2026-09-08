/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.os;

import dev.jstech.core.client.gui.theme.JsTechTheme;
import dev.jstech.computers.operation.payload.ItemDetailPayload;
import dev.jstech.computers.operation.payload.ItemDetailPayload.BusRef;
import dev.jstech.computers.operation.payload.NetworkItemEntry;
import dev.jstech.computers.operation.payload.NetworkItemEntry.StorageShare;
import dev.jstech.computers.operation.payload.RequestItemDetailPayload;
import dev.jstech.computers.operation.payload.RequestStorageInsightsPayload;
import dev.jstech.computers.operation.payload.StorageInsightsPayload;
import dev.jstech.computers.storage.StorageKey;
import dev.jstech.core.client.gui.component.Button;
import dev.jstech.core.client.gui.component.CellGrid;
import dev.jstech.core.client.gui.component.Draw;
import dev.jstech.core.client.gui.component.Label;
import dev.jstech.core.client.gui.component.ListView;
import dev.jstech.core.client.gui.component.Panel;
import dev.jstech.core.client.gui.component.SearchField;
import dev.jstech.core.client.gui.component.Texts;
import dev.jstech.core.client.gui.component.UiContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Storage Insights: a dashboard over the network's contents. It shows totals, the biggest types as a bar
 * chart (searchable, with pinned favourites first), the types running low against an adjustable threshold,
 * and how full each server is. Clicking a type opens a detail view: where it is stored, what it makes, and
 * which buses filter it. Data is computed server-side; the dashboard re-requests on a slow cadence.
 */
public final class StorageInsightsApp implements IDesktopApp {

    private static final int REFRESH_FRAMES = 60;
    private static final int C_CRIT = 0xFFD1495B;
    private static final int C_PIN = 0xFFE0A020;
    private static final int TOP_ROW_H = 15;
    private static final int LOW_ROW_H = 13;
    private static final int SERVER_ROW_H = 11;
    private static final int DETAIL_ROW_H = 10;
    private static final int USE_CELL = 16;
    private static final int PIN_W = 8;
    private static final int SEARCH_MAX = 32;

    private final BlockPos host;
    private final BlockPos monitorPos;
    private OsSkin skin = OsSkin.fallback();
    @Nullable
    private StorageInsightsPayload data;
    private int threshold = 64;
    private int frame;
    private final Set<String> pinned = new LinkedHashSet<>();
    private boolean detailMode;
    @Nullable
    private ItemDetailPayload detail;
    private int lastMouseX;
    private int lastMouseY;

    private static StorageInsightsApp active;

    // components
    private final Panel root = new Panel();
    private final Label loadingLabel;
    private final SearchField search;
    private final Label typesTile;
    private final Label totalTile;
    private final Label lowTile;
    private final Label topHeader;
    private final ListView<NetworkItemEntry> topList;
    private final Label topEmpty;
    private final Label lowHeader;
    private final ListView<NetworkItemEntry> lowList;
    private final Label lowEmpty;
    private final Label thresholdLabel;
    private final Button thresholdMinus;
    private final Label thresholdValue;
    private final Button thresholdPlus;
    private final Label serverHeader;
    private final ListView<StorageShare> serverList;

    private final Button back;
    private final Label detailLoading;
    private final Label detailName;
    private final Label detailTotal;
    private final Label storedHeader;
    private final ListView<StorageShare> storedList;
    private final Label storedEmpty;
    private final Label usesHeader;
    private final CellGrid usesGrid;
    private final Label usesEmpty;
    private final Label busesHeader;
    private final ListView<BusRef> busList;
    private final Label busesEmpty;

    public StorageInsightsApp(final BlockPos host, final BlockPos monitorPos) {
        this.host = host;
        this.monitorPos = monitorPos;

        loadingLabel = root.add(new Label("Reading network...", Label.Tone.DIM));
        search = root.add(new SearchField(SEARCH_MAX));
        search.setPlaceholder("search item...");
        typesTile = root.add(new Label(() -> "TYPES " + (data == null ? 0 : data.typeCount()), Label.Tone.DIM));
        totalTile = root.add(new Label(() -> "TOTAL " + JsTechTheme.fmt(data == null ? 0 : data.totalItems()), Label.Tone.DIM));
        lowTile = root.add(new Label(() -> "LOW " + lowBelowThreshold().size(), Label.Tone.DIM)
                .setColor(() -> lowBelowThreshold().isEmpty() ? 0 : C_CRIT));
        topHeader = root.add(new Label("TOP ITEMS", Label.Tone.DIM));
        topList = root.add(new ListView<NetworkItemEntry>(this::displayItems, TOP_ROW_H, this::renderTopRow).setOnClick(this::topClicked));
        topEmpty = root.add(new Label(() -> search.query().isEmpty() ? "loading..." : "no match", Label.Tone.DIM));
        lowHeader = root.add(new Label("LOW STOCK", Label.Tone.DIM));
        lowList = root.add(new ListView<NetworkItemEntry>(this::lowBelowThreshold, LOW_ROW_H, this::renderLowRow).setOnClick(this::lowClicked));
        lowEmpty = root.add(new Label("all stocked", Label.Tone.DIM));
        thresholdLabel = root.add(new Label("Threshold", Label.Tone.DIM));
        thresholdMinus = root.add(new Button("-", () -> threshold = Math.max(1, threshold - 16)));
        thresholdValue = root.add(new Label(() -> String.valueOf(threshold)).setAlign(Label.Align.CENTER));
        thresholdPlus = root.add(new Button("+", () -> threshold = Math.min(4096, threshold + 16)));
        serverHeader = root.add(new Label("BY SERVER", Label.Tone.DIM));
        serverList = root.add(new ListView<StorageShare>(() -> data == null ? List.of() : data.servers(), SERVER_ROW_H, this::renderServerRow));

        back = root.add(new Button("< Back", () -> {
            detailMode = false;
            detail = null;
        }));
        detailLoading = root.add(new Label("Loading item...", Label.Tone.DIM));
        detailName = root.add(new Label(() -> detail == null ? "" : detail.item().getHoverName().getString()));
        detailTotal = root.add(new Label(() -> detail == null ? "" : JsTechTheme.fmt(detail.total()) + " total", Label.Tone.DIM)
                .setAlign(Label.Align.RIGHT));
        storedHeader = root.add(new Label("STORED IN", Label.Tone.DIM));
        storedList = root.add(new ListView<StorageShare>(() -> detail == null ? List.of() : detail.storedIn(), DETAIL_ROW_H, this::renderStoredRow));
        storedEmpty = root.add(new Label("-", Label.Tone.DIM));
        usesHeader = root.add(new Label("USED TO MAKE", Label.Tone.DIM));
        usesGrid = root.add(new CellGrid(1, 1, 1, USE_CELL)
                .setWells(false)
                .setInset(2)
                .setRenderer((g, ctx, index, cx, cy, w, h, hovered) -> {
                    if (detail != null && index < detail.usedToMake().size()) {
                        itemIcon(g, detail.usedToMake().get(index), cx, cy, w);
                    }
                })
                .setOnClick((index, button, shift) -> {
                    if (detail != null && index < detail.usedToMake().size()) {
                        openDetail(detail.usedToMake().get(index).copy());
                    }
                }));
        usesEmpty = root.add(new Label("nothing on the network", Label.Tone.DIM));
        busesHeader = root.add(new Label("BUSES", Label.Tone.DIM));
        busList = root.add(new ListView<BusRef>(() -> detail == null ? List.of() : detail.buses(), DETAIL_ROW_H, this::renderBusRow));
        busesEmpty = root.add(new Label("not on any bus filter", Label.Tone.DIM));

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

    @Override
    public String title() {
        return "Storage Insights";
    }

    @Override
    public int defaultWidth() {
        return 320;
    }

    @Override
    public int defaultHeight() {
        return 208;
    }

    @Override
    public int minWidth() {
        return 270;
    }

    @Override
    public int minHeight() {
        return 170;
    }

    @Override
    public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
        active = this;
    }

    // rendering

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        frame++;
        if (!detailMode && frame % REFRESH_FRAMES == 0) {
            request();
        }
        final UiContext ctx = new UiContext(skin, font, mouseX, mouseY, partialTick);
        g.fill(x, y, x + width, y + height, skin.windowBg());
        final int px = x + 6;
        final int py = y + 6;
        final int pw = width - 12;
        final int ph = height - 12;
        if (detailMode) {
            layoutDetail(px, py, pw, ph);
        } else {
            layoutDashboard(px, py, pw, ph);
        }
        root.render(g, ctx);
        if (detailMode && detail != null) {
            // The item's own icon beside its name, and the rule under the header.
            itemIcon(g, detail.item(), px + 40, py - 1, 16);
            g.fill(px, py + 15, px + pw, py + 16, skin.edge());
        }
    }

    private void setDashboardVisible(final boolean on) {
        for (final var c : List.of(search, typesTile, totalTile, lowTile, topHeader, topList, topEmpty, lowHeader, lowList,
                lowEmpty, thresholdLabel, thresholdMinus, thresholdValue, thresholdPlus, serverHeader, serverList)) {
            c.setVisible(on);
        }
    }

    private void setDetailVisible(final boolean on) {
        for (final var c : List.of(back, detailLoading, detailName, detailTotal, storedHeader, storedList, storedEmpty, usesHeader,
                usesGrid, usesEmpty, busesHeader, busList, busesEmpty)) {
            c.setVisible(on);
        }
    }

    private void layoutDashboard(final int x, final int y, final int w, final int h) {
        setDetailVisible(false);
        final boolean ready = data != null;
        loadingLabel.setVisible(!ready);
        loadingLabel.setBounds(x, y + 2, w, 8);
        setDashboardVisible(ready);
        if (!ready) {
            return;
        }
        // Search bar + summary tiles on the first row.
        final int searchW = (int) (w * 0.42);
        search.setBounds(x, y, searchW, 13);
        final int tileW = (w - searchW - 6) / 3;
        typesTile.setBounds(x + searchW + 6, y + 3, tileW, 8);
        totalTile.setBounds(x + searchW + 6 + tileW, y + 3, tileW, 8);
        lowTile.setBounds(x + searchW + 6 + 2 * tileW, y + 3, tileW, 8);

        final int colTop = y + 18;
        final int colH = h - 18;
        final int leftW = (int) (w * 0.56);
        topHeader.setBounds(x, colTop, leftW - 6, 8);
        topList.setBounds(x, colTop + 12, leftW - 6, Math.max(TOP_ROW_H, colH - 12));
        final boolean noTop = displayItems().isEmpty();
        topList.setVisible(!noTop);
        topEmpty.setVisible(noTop);
        topEmpty.setBounds(x + 2, colTop + 14, leftW - 8, 8);

        final int rx = x + leftW;
        final int rw = w - leftW;
        lowHeader.setBounds(rx, colTop, rw, 8);
        final boolean noLow = lowBelowThreshold().isEmpty();
        lowList.setVisible(!noLow);
        lowList.setBounds(rx, colTop + 12, rw, Math.max(LOW_ROW_H, colH / 2 - 12));
        lowEmpty.setVisible(noLow);
        lowEmpty.setBounds(rx, colTop + 13, rw, 8);

        final int thY = colTop + colH / 2 + 2;
        thresholdLabel.setBounds(rx, thY + 2, rw - 48, 8);
        thresholdMinus.setBounds(rx + rw - 46, thY, 12, 11);
        thresholdValue.setBounds(rx + rw - 34, thY + 2, 20, 8);
        thresholdPlus.setBounds(rx + rw - 14, thY, 12, 11);

        final int svY = thY + 16;
        serverHeader.setBounds(rx, svY, rw, 8);
        serverList.setBounds(rx, svY + 12, rw, Math.max(SERVER_ROW_H, colTop + colH - (svY + 12)));
    }

    private void layoutDetail(final int x, final int y, final int w, final int h) {
        setDashboardVisible(false);
        loadingLabel.setVisible(false);
        setDetailVisible(true);
        back.setBounds(x, y, 34, 12);
        final boolean ready = detail != null;
        detailLoading.setVisible(!ready);
        detailLoading.setBounds(x + 42, y + 3, w - 42, 8);
        for (final var c : List.of(detailName, detailTotal, storedHeader, storedList, storedEmpty, usesHeader, usesGrid, usesEmpty,
                busesHeader, busList, busesEmpty)) {
            c.setVisible(ready);
        }
        if (!ready) {
            return;
        }
        detailName.setBounds(x + 58, y + 3, w - 130, 8);
        detailTotal.setBounds(x + w - 72, y + 3, 72, 8);

        // Three sections down the panel.
        final int top = y + 19;
        final int third = (y + h - top) / 3;
        storedHeader.setBounds(x, top, w, 8);
        final boolean noStored = detail.storedIn().isEmpty();
        storedList.setVisible(!noStored);
        storedList.setBounds(x + 2, top + 11, w - 4, Math.max(DETAIL_ROW_H, third - 11));
        storedEmpty.setVisible(noStored);
        storedEmpty.setBounds(x + 2, top + 11, w - 4, 8);

        final int usesTop = top + third;
        usesHeader.setBounds(x, usesTop, w, 8);
        final boolean noUses = detail.usedToMake().isEmpty();
        usesGrid.setVisible(!noUses);
        final int cols = Math.max(1, (w - 2) / USE_CELL);
        usesGrid.setColumns(cols).setVisibleRows(1).setTotalRows(1).setCellCount(Math.min(cols, detail.usedToMake().size()))
                .place(x + 2, usesTop + 11);
        usesEmpty.setVisible(noUses);
        usesEmpty.setBounds(x + 2, usesTop + 11, w - 4, 8);

        final int busesTop = top + third * 2;
        busesHeader.setBounds(x, busesTop, w, 8);
        final boolean noBuses = detail.buses().isEmpty();
        busList.setVisible(!noBuses);
        busList.setBounds(x + 2, busesTop + 11, w - 4, Math.max(DETAIL_ROW_H, y + h - (busesTop + 11)));
        busesEmpty.setVisible(noBuses);
        busesEmpty.setBounds(x + 2, busesTop + 11, w - 4, 8);
    }

    private void renderTopRow(final GuiGraphics g, final UiContext ctx, final NetworkItemEntry e, final int index, final int x,
                              final int y, final int w, final int h, final boolean hovered, final boolean selected) {
        final Font font = ctx.font();
        if (hovered) {
            g.fill(x, y, x + w, y + h - 1, ctx.skin().listHover());
        }
        final long max = data == null || data.topItems().isEmpty() ? 1 : Math.max(1, data.topItems().get(0).total());
        final boolean pin = pinned.contains(e.key().toString());
        g.drawString(font, pin ? "*" : "-", x, y + 2, pin ? C_PIN : ctx.skin().dim(), false);
        itemIcon(g, e.key(), x + 8, y, 12);
        g.drawString(font, Texts.clip(font, e.key().displayName().getString(), 96 - 24), x + 22, y + 2, ctx.skin().text(), false);
        final int barX = x + 96;
        final int qtyW = 34;
        final int barW = Math.max(10, w - 96 - qtyW - 4);
        g.fill(barX, y + 4, barX + barW, y + 10, ctx.skin().fieldBg());
        g.fill(barX, y + 4, barX + (int) (barW * Math.min(1.0, (double) e.total() / max)), y + 10, ctx.skin().accent());
        final String q = JsTechTheme.fmt(e.total());
        g.drawString(font, q, x + w - font.width(q), y + 2, ctx.skin().dim(), false);
    }

    private void topClicked(final int index, final int button, final double mx, final double my) {
        final List<NetworkItemEntry> shown = displayItems();
        if (button != 0 || index < 0 || index >= shown.size()) {
            return;
        }
        final NetworkItemEntry e = shown.get(index);
        if (mx < topList.x() + PIN_W) {
            togglePin(e.key().toString());
        } else {
            openDetail(e.key().stack(1));
        }
    }

    private void renderLowRow(final GuiGraphics g, final UiContext ctx, final NetworkItemEntry e, final int index, final int x,
                              final int y, final int w, final int h, final boolean hovered, final boolean selected) {
        final Font font = ctx.font();
        g.fill(x, y, x + w, y + h - 1, 0x18D1495B);
        itemIcon(g, e.key(), x + 1, y, 11);
        g.drawString(font, Texts.clip(font, e.key().displayName().getString(), w - 56), x + 15, y + 2, ctx.skin().text(), false);
        final String s = e.total() + "/" + threshold;
        g.drawString(font, s, x + w - font.width(s), y + 2, C_CRIT, false);
    }

    private void lowClicked(final int index, final int button, final double mx, final double my) {
        final List<NetworkItemEntry> low = lowBelowThreshold();
        if (button == 0 && index >= 0 && index < low.size()) {
            openDetail(low.get(index).key().stack(1));
        }
    }

    private void renderServerRow(final GuiGraphics g, final UiContext ctx, final StorageShare s, final int index, final int x,
                                 final int y, final int w, final int h, final boolean hovered, final boolean selected) {
        final Font font = ctx.font();
        long smax = 1;
        if (data != null) {
            for (final StorageShare share : data.servers()) {
                smax = Math.max(smax, share.qty());
            }
        }
        g.drawString(font, Texts.clip(font, s.label(), 44), x, y, ctx.skin().dim(), false);
        final int bx = x + 46;
        final int bw = Math.max(8, w - 46 - 30);
        g.fill(bx, y, bx + bw, y + 6, ctx.skin().fieldBg());
        g.fill(bx, y, bx + (int) (bw * Math.min(1.0, (double) s.qty() / smax)), y + 6, ctx.skin().accent());
        final String q = JsTechTheme.fmt(s.qty());
        g.drawString(font, q, x + w - font.width(q), y - 1, ctx.skin().dim(), false);
    }

    private void renderStoredRow(final GuiGraphics g, final UiContext ctx, final StorageShare s, final int index, final int x,
                                 final int y, final int w, final int h, final boolean hovered, final boolean selected) {
        g.drawString(ctx.font(), Texts.clip(ctx.font(), s.label() + "  " + JsTechTheme.fmt(s.qty()), w - 4), x, y, ctx.skin().text(), false);
    }

    private void renderBusRow(final GuiGraphics g, final UiContext ctx, final BusRef b, final int index, final int x,
                              final int y, final int w, final int h, final boolean hovered, final boolean selected) {
        final Font font = ctx.font();
        g.drawString(font, Texts.clip(font, b.name(), w - 60), x, y, ctx.skin().text(), false);
        g.drawString(font, b.kind(), x + w - font.width(b.kind()), y, ctx.skin().accent(), false);
    }

    // state

    private void openDetail(final ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        detailMode = true;
        detail = null;
        root.focus(null);
        PacketDistributor.sendToServer(new RequestItemDetailPayload(host, monitorPos, stack));
    }

    private void togglePin(final String key) {
        if (!pinned.remove(key)) {
            pinned.add(key);
        }
    }

    /** Top items filtered by the search box, with pinned favourites sorted to the front. */
    private List<NetworkItemEntry> displayItems() {
        if (data == null) {
            return List.of();
        }
        final List<NetworkItemEntry> out = new ArrayList<>();
        final String q = search.query();
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
            Draw.outline(g, x + 1, y + 1, size - 2, size - 2, skin.edge());
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
        return root.mouseScrolled(lastMouseX, lastMouseY, delta);
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
