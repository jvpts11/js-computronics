/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.computronics.client.JscOsTheme;
import dev.jstech.computronics.operation.payload.CraftCatalogPayload;
import dev.jstech.computronics.operation.payload.CraftPlanPayload;
import dev.jstech.computronics.operation.payload.CraftPlannerPayload;
import dev.jstech.computronics.operation.payload.NiCraftPayload;
import dev.jstech.computronics.operation.payload.RequestCraftPlannerPayload;
import dev.jstech.core.client.gui.component.Button;
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
    private static final int C_AMBER = 0xFFE0A020;
    private static final int CATALOG_ROW_H = 15;
    private static final int STAGE_ROW_H = 10;
    private static final int INGREDIENT_ROW_H = 11;
    private static final int TREE_ROW_H = 11;
    private static final int SEARCH_MAX = 32;

    private final BlockPos host;
    private final BlockPos monitorPos;
    private OsSkin skin = OsSkin.fallback();
    private List<CraftCatalogPayload.Entry> catalog = List.of();
    @Nullable
    private CraftPlannerPayload plan;
    private ItemStack selected = ItemStack.EMPTY;
    private long qty = 1;
    private boolean treeMode;
    private int frame;
    private int lastMouseX;
    private int lastMouseY;

    private static CraftPlannerApp active;

    // ---- components ----
    private final Panel root = new Panel();
    private final SearchField search;
    private final ListView<CraftCatalogPayload.Entry> catalogList;
    private final Label catalogEmpty;
    private final Label pickLabel;
    private final Label nameLabel;
    private final Button qtyMinus;
    private final Label qtyLabel;
    private final Button qtyPlus;
    private final Label planningLabel;
    private final Label notCraftableLabel;
    private final Label pillLabel;
    private final Label summaryLabel;
    private final Button viewToggle;
    private final Label stagesHeader;
    private final ListView<CraftPlannerPayload.Stage> stageList;
    private final Label ingredientsHeader;
    private final ListView<CraftPlanPayload.Row> ingredientList;
    private final Label treeHeader;
    private final Label treeHint;
    private final ListView<CraftPlannerPayload.TreeNode> treeList;
    private final Button craft;

    public CraftPlannerApp(final BlockPos host, final BlockPos monitorPos) {
        this.host = host;
        this.monitorPos = monitorPos;

        search = root.add(new SearchField(SEARCH_MAX));
        search.setPlaceholder("search item...");
        catalogList = root.add(new ListView<CraftCatalogPayload.Entry>(this::filtered, CATALOG_ROW_H, this::renderCatalogRow)
                .setOnClick(this::catalogClicked));
        search.setOnEdit(() -> catalogList.setScroll(0));
        catalogEmpty = root.add(new Label(() -> catalog.isEmpty() ? "loading..." : "no match", Label.Tone.DIM));

        pickLabel = root.add(new Label("Pick an item to plan.", Label.Tone.DIM));
        nameLabel = root.add(new Label(() -> selected.getHoverName().getString()));
        qtyMinus = root.add(new Button("-", () -> setQty(qty - step())));
        qtyLabel = root.add(new Label(() -> String.valueOf(qty)).setAlign(Label.Align.CENTER));
        qtyPlus = root.add(new Button("+", () -> setQty(qty + step())));
        planningLabel = root.add(new Label("planning...", Label.Tone.DIM));
        notCraftableLabel = root.add(new Label("No pattern on the network makes this.").setColor(C_CRIT));
        pillLabel = root.add(new Label(() -> plan != null && plan.feasible() ? "Craftable" : "Partial")
                .setColor(() -> plan != null && plan.feasible() ? C_GOOD : C_AMBER));
        summaryLabel = root.add(new Label(() -> plan == null ? ""
                : "max " + JscOsTheme.fmt(plan.maxFeasible()) + "  -  " + plan.stages().size() + " stages", Label.Tone.DIM));
        viewToggle = root.add(new Button(() -> treeMode ? "Steps" : "Tree", () -> treeMode = !treeMode));
        stagesHeader = root.add(new Label("STAGES", Label.Tone.DIM));
        stageList = root.add(new ListView<CraftPlannerPayload.Stage>(() -> plan == null ? List.of() : plan.stages(), STAGE_ROW_H,
                this::renderStageRow));
        ingredientsHeader = root.add(new Label("INGREDIENTS", Label.Tone.DIM));
        ingredientList = root.add(new ListView<CraftPlanPayload.Row>(() -> plan == null ? List.of() : plan.ingredients(),
                INGREDIENT_ROW_H, this::renderIngredientRow));
        treeHeader = root.add(new Label("CRAFT TREE", Label.Tone.DIM));
        treeHint = root.add(new Label("wheel to scroll", Label.Tone.DIM).setAlign(Label.Align.RIGHT));
        treeList = root.add(new ListView<CraftPlannerPayload.TreeNode>(() -> plan == null ? List.of() : plan.tree(), TREE_ROW_H,
                this::renderTreeRow));
        craft = root.add(new Button(() -> "Craft " + qty, this::craft));

        root.focus(search);
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

    @Override
    public void onRestored() {
        active = this;
        // The catalog may have grown (recipes loaded meanwhile) and the plan's stock may have moved.
        PacketDistributor.sendToServer(new RequestCraftPlannerPayload(host, monitorPos, ItemStack.EMPTY, 0));
        requestPlan();
    }

    @Override
    public String title() {
        return "Craft Planner";
    }

    @Override
    public int defaultWidth() {
        return 320;
    }

    @Override
    public int defaultHeight() {
        return 200;
    }

    @Override
    public int minWidth() {
        return 280;
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

    // ---- rendering ----

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        frame++;
        if (frame % REFRESH_FRAMES == 0) {
            requestPlan();
        }
        final UiContext ctx = new UiContext(skin, font, mouseX, mouseY, partialTick);
        g.fill(x, y, x + width, y + height, skin.windowBg());
        final int px = x + 6;
        final int py = y + 6;
        final int ph = height - 12;
        final int leftW = Math.max(96, (int) (width * 0.38));
        layout(font, px, py, width - 12, ph, leftW);
        g.fill(px + leftW - 3, py, px + leftW - 2, py + ph, skin.edge());
        if (!selected.isEmpty()) {
            itemIcon(g, selected, px + leftW, py, 16);
            if (pillLabel.visible()) {
                final int pillW = font.width(pillLabel.text()) + 8;
                g.fill(pillLabel.x() - 4, pillLabel.y() - 2, pillLabel.x() - 4 + pillW, pillLabel.y() + 9,
                        plan != null && plan.feasible() ? 0x2E2EA043 : 0x33E0A020);
                g.fill(px + leftW, pillLabel.y() + 12, px + leftW + width - 12 - leftW, pillLabel.y() + 13, skin.edge());
            }
        }
        root.render(g, ctx);
    }

    private void layout(final Font font, final int x, final int y, final int w, final int h, final int leftW) {
        final int catW = leftW - 6;
        search.setBounds(x, y, catW, 13);
        catalogList.setBounds(x, y + 16, catW, Math.max(CATALOG_ROW_H, h - 16));
        final boolean noCatalog = filtered().isEmpty();
        catalogList.setVisible(!noCatalog);
        catalogEmpty.setVisible(noCatalog);
        catalogEmpty.setBounds(x + 2, y + 18, catW - 4, 8);

        final int rx = x + leftW;
        final int rw = w - leftW;
        final boolean picked = !selected.isEmpty();
        pickLabel.setVisible(!picked);
        pickLabel.setBounds(rx + 2, y + 4, rw - 4, 8);
        for (final var c : List.of(nameLabel, qtyMinus, qtyLabel, qtyPlus)) {
            c.setVisible(picked);
        }
        final boolean planned = picked && plan != null;
        planningLabel.setVisible(picked && plan == null);
        final boolean craftable = planned && plan.craftable();
        notCraftableLabel.setVisible(planned && !plan.craftable());
        for (final var c : List.of(pillLabel, summaryLabel, viewToggle, craft)) {
            c.setVisible(craftable);
        }
        final boolean steps = craftable && !treeMode;
        final boolean tree = craftable && treeMode;
        for (final var c : List.of(stagesHeader, stageList, ingredientsHeader, ingredientList)) {
            c.setVisible(steps);
        }
        treeHeader.setVisible(tree);
        treeList.setVisible(tree);
        treeHint.setVisible(tree && plan.tree().size() > treeList.visibleRows());
        if (!picked) {
            return;
        }
        // Header: icon + name + quantity stepper.
        final int stepX = rx + rw - 56;
        nameLabel.setBounds(rx + 20, y + 4, stepX - (rx + 20) - 4, 8);
        qtyMinus.setBounds(stepX, y, 14, 12);
        qtyLabel.setBounds(stepX + 16, y + 3, 24, 8);
        qtyPlus.setBounds(stepX + 44, y, 14, 12);
        int row = y + 20;
        planningLabel.setBounds(rx + 2, row, rw - 4, 8);
        notCraftableLabel.setBounds(rx + 2, row, rw - 4, 8);
        if (!craftable) {
            return;
        }
        // Status line: feasible pill + max + stage count, and the Steps/Tree toggle on the right.
        final int pillW = font.width(pillLabel.text()) + 8;
        pillLabel.setBounds(rx + 4, row + 2, pillW - 8, 8);
        final int togW = font.width(viewToggle.label()) + 10;
        viewToggle.setBounds(rx + rw - togW, row - 1, togW, 11);
        summaryLabel.setBounds(rx + pillW + 6, row + 2, rw - pillW - 6 - togW - 4, 8);
        row += 17;
        final int bottom = y + h - 16;
        final String cap = craft.label();
        final int cw = font.width(cap) + 14;
        craft.setBounds(rx + rw - cw, y + h - 13, cw, 13);
        craft.setPrimary(true);
        if (tree) {
            treeHeader.setBounds(rx + 2, row, rw / 2, 8);
            treeHint.setBounds(rx + rw / 2, row, rw / 2, 8);
            treeList.setBounds(rx, row + 11, rw, Math.max(TREE_ROW_H, bottom - (row + 11)));
            return;
        }
        // Stages then ingredients, sharing the remaining height.
        final int half = (bottom - row) / 2;
        stagesHeader.setBounds(rx + 2, row, rw - 4, 8);
        stageList.setBounds(rx, row + 11, rw, Math.max(STAGE_ROW_H, half - 11));
        final int iy = row + half + 2;
        ingredientsHeader.setBounds(rx + 2, iy, rw - 4, 8);
        ingredientList.setBounds(rx, iy + 11, rw, Math.max(INGREDIENT_ROW_H, bottom - (iy + 11)));
    }

    private void renderCatalogRow(final GuiGraphics g, final UiContext ctx, final CraftCatalogPayload.Entry e, final int index,
                                  final int x, final int y, final int w, final int h, final boolean hovered, final boolean selectedRow) {
        final boolean sel = ItemStack.isSameItemSameComponents(e.result(), selected);
        ctx.skin().listRow(g, x, y, w, h, hovered, sel);
        itemIcon(g, e.result(), x + 1, y, 12);
        g.drawString(ctx.font(), Texts.clip(ctx.font(), e.title(), w - 18), x + 15, y + 3,
                sel ? ctx.skin().accent() : ctx.skin().text(), false);
    }

    private void catalogClicked(final int index, final int button, final double mx, final double my) {
        final List<CraftCatalogPayload.Entry> shown = filtered();
        if (button == 0 && index >= 0 && index < shown.size()) {
            select(shown.get(index).result().copy());
        }
    }

    private void renderStageRow(final GuiGraphics g, final UiContext ctx, final CraftPlannerPayload.Stage s, final int index,
                                final int x, final int y, final int w, final int h, final boolean hovered, final boolean selectedRow) {
        final Font font = ctx.font();
        g.drawString(font, Texts.clip(font, s.name(), w - 60), x + 4, y, ctx.skin().text(), false);
        final String tag = (s.machine() ? "machine" : "bench") + " x" + s.runs();
        g.drawString(font, tag, x + w - font.width(tag), y, ctx.skin().dim(), false);
    }

    private void renderIngredientRow(final GuiGraphics g, final UiContext ctx, final CraftPlanPayload.Row r, final int index,
                                     final int x, final int y, final int w, final int h, final boolean hovered, final boolean selectedRow) {
        final Font font = ctx.font();
        final boolean ok = r.have() >= r.need();
        itemIcon(g, r.item(), x + 1, y - 1, 11);
        g.drawString(font, Texts.clip(font, r.item().getHoverName().getString(), w - 76), x + 15, y, ctx.skin().text(), false);
        final String s = ok ? "have " + JscOsTheme.fmt(r.have()) : "short " + JscOsTheme.fmt(r.need() - r.have());
        g.drawString(font, s, x + w - font.width(s), y, ok ? C_GOOD : C_CRIT, false);
    }

    /** One node of the recipe dependency tree, flattened in pre-order and drawn indented by depth. */
    private void renderTreeRow(final GuiGraphics g, final UiContext ctx, final CraftPlannerPayload.TreeNode n, final int index,
                               final int x, final int y, final int w, final int h, final boolean hovered, final boolean selectedRow) {
        final Font font = ctx.font();
        final int ix = x + 2 + n.depth() * 9;
        if (n.depth() > 0) {
            g.fill(x + 2 + (n.depth() - 1) * 9 + 3, y + 4, ix - 1, y + 5, ctx.skin().edge());
        }
        itemIcon(g, n.item(), ix, y - 1, 10);
        final String label = JscOsTheme.fmt(n.qty()) + "x " + n.item().getHoverName().getString();
        g.drawString(font, Texts.clip(font, label, w - (ix - x) - 13 - 40), ix + 12, y, ctx.skin().text(), false);
        if (!n.craftable()) {
            g.drawString(font, "raw", x + w - font.width("raw"), y, ctx.skin().dim(), false);
        }
    }

    // ---- state ----

    private void select(final ItemStack stack) {
        this.selected = stack;
        this.qty = 1;
        this.plan = null;
        treeList.setScroll(0);
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
        final String q = search.query();
        if (q.isEmpty()) {
            return catalog;
        }
        final List<CraftCatalogPayload.Entry> out = new ArrayList<>();
        for (final CraftCatalogPayload.Entry e : catalog) {
            if (e.title().toLowerCase(Locale.ROOT).contains(q)
                    || e.result().getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) {
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

    // ---- input ----

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        root.mouseClicked(mouseX, mouseY, button);
        if (root.focusedChild() == null) {
            // Typing filters the catalogue whenever nothing else holds the keyboard.
            root.focus(search);
        }
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
        // The wheel elsewhere moves the tree when it is shown, else the catalogue.
        final int step = delta > 0 ? -1 : 1;
        if (treeList.visible()) {
            treeList.setScroll(treeList.scroll() + step);
        } else {
            catalogList.setScroll(catalogList.scroll() + step);
        }
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
