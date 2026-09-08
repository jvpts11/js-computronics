/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client;

import dev.jstech.computers.blockentity.CraftingSwitchBlockEntity;
import dev.jstech.computers.gui.layout.CraftingSwitchLayout;
import dev.jstech.computers.menu.CraftingSwitchMenu;
import dev.jstech.computers.operation.payload.SetCraftingSwitchFacePayload;
import dev.jstech.core.client.gui.theme.JsTechTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Crafting Switch screen: a master-detail panel listing the six faces (five can host a machine, one carries
 * the crafting cable) and, for the selected face, its detected machine, an editable name and an active toggle.
 * Reads the switch's block entity locally (the server keeps it in sync via the update tag) and pushes edits
 * back with {@link SetCraftingSwitchFacePayload}.
 */
public class CraftingSwitchScreen extends AbstractContainerScreen<CraftingSwitchMenu> {

    private static final int BG = 0xFF0B0E13;
    private static final int PANEL = 0xFF11161D;
    private static final int LINE = 0xFF1D2530;
    private static final int SEL = 0xFF15212A;
    private static final int ACCENT = 0xFF39D6C4;
    private static final int TEXT = 0xFFCDD6E2;
    private static final int DIM = 0xFF7D8A9C;
    private static final int GREEN = 0xFF5FE07A;

    // Category picker popup: a modal list of the installed recipe types (plus "none").
    private static final int CP_X = 24;
    private static final int CP_Y = 24;
    private static final int CP_W = 152;
    private static final int CP_ROW_H = 11;
    private static final int CP_VIS_ROWS = 7;

    private int selectedFace;
    private EditBox nameBox;
    private Button activeBtn;
    private Button categoryBtn;
    private boolean categoryPickerOpen;
    private int categoryScroll;
    private java.util.List<String> allCategories;

    public CraftingSwitchScreen(final CraftingSwitchMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
        this.imageWidth = CraftingSwitchLayout.WIDTH;
        this.imageHeight = CraftingSwitchLayout.HEIGHT;
        this.inventoryLabelY = CraftingSwitchLayout.INV_LABEL_Y;
    }

    @Override
    protected void init() {
        super.init();
        nameBox = new EditBox(this.font, leftPos + CraftingSwitchLayout.NAME_X, topPos + CraftingSwitchLayout.NAME_Y,
                CraftingSwitchLayout.NAME_W, CraftingSwitchLayout.NAME_H, Component.empty());
        nameBox.setMaxLength(48);
        nameBox.setBordered(true);
        nameBox.setResponder(this::onNameChanged);
        addRenderableWidget(nameBox);

        activeBtn = new ThemeButton(leftPos + CraftingSwitchLayout.ACTIVE_X, topPos + CraftingSwitchLayout.ACTIVE_Y,
                CraftingSwitchLayout.ACTIVE_W, CraftingSwitchLayout.ACTIVE_H,
                Component.literal("Active"), b -> toggleActive());
        addRenderableWidget(activeBtn);

        categoryBtn = new ThemeButton(leftPos + CraftingSwitchLayout.CATEGORY_X,
                topPos + CraftingSwitchLayout.CATEGORY_Y,
                CraftingSwitchLayout.CATEGORY_W, CraftingSwitchLayout.CATEGORY_H,
                Component.literal("Category"), b -> {
                    categoryPickerOpen = true;
                    categoryScroll = 0;
                });
        addRenderableWidget(categoryBtn);

        selectFace(defaultFace());
    }

    private CraftingSwitchBlockEntity blockEntity() {
        return minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.switchPos()) instanceof CraftingSwitchBlockEntity be
                ? be : null;
    }

    /** The first face that holds a machine, else face 0. */
    private int defaultFace() {
        final CraftingSwitchBlockEntity be = blockEntity();
        if (be != null) {
            for (final Direction d : Direction.values()) {
                if (be.machineOnFace(d)) {
                    return d.get3DDataValue();
                }
            }
        }
        return 0;
    }

    /** The machines whose cable run hangs off the given switch face (client-synced). */
    private java.util.List<CraftingSwitchBlockEntity.BusMachineLine> busMachinesOnFace(final int face) {
        final CraftingSwitchBlockEntity be = blockEntity();
        if (be == null) {
            return java.util.List.of();
        }
        final java.util.List<CraftingSwitchBlockEntity.BusMachineLine> out = new java.util.ArrayList<>();
        for (final var line : be.busMachineLines()) {
            if (line.switchFace() == face) {
                out.add(line);
            }
        }
        return out;
    }

    private void selectFace(final int face) {
        this.selectedFace = face;
        final CraftingSwitchBlockEntity be = blockEntity();
        final Direction d = Direction.from3DDataValue(face);
        final boolean adjacent = be != null && be.machineOnFace(d);
        final var viaBus = busMachinesOnFace(face);
        if (adjacent) {
            nameBox.setValue(be.faceName(d));
        } else if (!viaBus.isEmpty()) {
            nameBox.setValue(viaBus.get(0).busName()); // the machine's name IS its bus's name
        } else {
            nameBox.setValue(be == null ? "" : be.faceName(d));
        }
        /*
         * Active + category govern the face's whole cable run too, so they stay editable when machines hang
         * off this face via buses.
         */
        final boolean editable = adjacent || !viaBus.isEmpty();
        nameBox.setEditable(editable);
        activeBtn.active = editable;
        categoryBtn.active = editable;
        categoryPickerOpen = false;
        refreshActiveLabel();
    }

    private void refreshActiveLabel() {
        final CraftingSwitchBlockEntity be = blockEntity();
        final Direction d = Direction.from3DDataValue(selectedFace);
        final boolean on = be != null && be.faceActive(d);
        activeBtn.setMessage(Component.literal(on ? "Active: accepting" : "Inactive"));
        final String category = be == null ? "" : be.faceCategory(d);
        categoryBtn.setMessage(Component.literal(category.isEmpty() ? "Category: none"
                : "Cat: " + shortCategory(category)));
    }

    /** Trims a recipe-type id for the button label ({@code minecraft:smelting} → {@code smelting}). */
    private static String shortCategory(final String category) {
        final int colon = category.indexOf(':');
        return colon >= 0 ? category.substring(colon + 1) : category;
    }

    private static String clamp(final String s, final int maxChars) {
        return s.length() <= maxChars ? s : s.substring(0, maxChars - 1) + "…";
    }

    @Override
    public boolean keyPressed(final int key, final int scan, final int mods) {
        /*
         * While the name field has focus, route typing to it and never let a key (e.g. the inventory key
         * 'E') reach the screen and close the GUI. ESC just unfocuses the field.
         */
        if (nameBox != null && nameBox.isFocused()) {
            if (key == 256) {
                nameBox.setFocused(false);
                setFocused(null);
                return true;
            }
            nameBox.keyPressed(key, scan, mods);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(final char c, final int mods) {
        if (nameBox != null && nameBox.isFocused()) {
            return nameBox.charTyped(c, mods);
        }
        return super.charTyped(c, mods);
    }

    private void onNameChanged(final String name) {
        final CraftingSwitchBlockEntity be = blockEntity();
        if (be == null) {
            return;
        }
        final Direction d = Direction.from3DDataValue(selectedFace);
        if (be.machineOnFace(d)) {
            sendFace(d, name, be.faceActive(d), be.faceCategory(d));
            return;
        }
        // A machine reached via buses: editing the name edits its Input/Receiving Bus (patterns match by it).
        final var viaBus = busMachinesOnFace(selectedFace);
        if (!viaBus.isEmpty()) {
            final var line = viaBus.get(0);
            PacketDistributor.sendToServer(new dev.jstech.computers.operation.payload
                    .SetBusNamePayload(line.cablePos(), line.busFace(), name));
        }
    }

    private boolean faceConfigurable(final CraftingSwitchBlockEntity be, final Direction d) {
        return be != null && (be.machineOnFace(d) || !busMachinesOnFace(d.get3DDataValue()).isEmpty());
    }

    private void toggleActive() {
        final CraftingSwitchBlockEntity be = blockEntity();
        final Direction d = Direction.from3DDataValue(selectedFace);
        if (!faceConfigurable(be, d)) {
            return;
        }
        sendFace(d, be.faceName(d), !be.faceActive(d), be.faceCategory(d));
    }

    private void selectCategory(final String category) {
        final CraftingSwitchBlockEntity be = blockEntity();
        final Direction d = Direction.from3DDataValue(selectedFace);
        if (faceConfigurable(be, d)) {
            sendFace(d, be.faceName(d), be.faceActive(d), category);
        }
        categoryPickerOpen = false;
    }

    private void sendFace(final Direction face, final String name, final boolean active, final String category) {
        PacketDistributor.sendToServer(new SetCraftingSwitchFacePayload(
                menu.switchPos(), face.get3DDataValue(), name, active, category));
    }

    /** "none" first, then every installed recipe type id, the dynamic generic machine categories. */
    private java.util.List<String> categories() {
        if (allCategories == null) {
            final java.util.List<String> list = new java.util.ArrayList<>();
            list.add("");
            list.addAll(dev.jstech.computers.crafting.MachineCategory.categoryIds());
            allCategories = list;
        }
        return allCategories;
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        // The category picker is modal: a row click tags the face, any other click closes it.
        if (categoryPickerOpen) {
            final java.util.List<String> list = categories();
            final int px = leftPos + CP_X;
            final int py = topPos + CP_Y;
            for (int r = 0; r < CP_VIS_ROWS && categoryScroll + r < list.size(); r++) {
                final int ry = py + 15 + r * CP_ROW_H;
                if (mouseX >= px + 3 && mouseX < px + CP_W - 3 && mouseY >= ry && mouseY < ry + CP_ROW_H) {
                    selectCategory(list.get(categoryScroll + r));
                    return true;
                }
            }
            categoryPickerOpen = false;
            return true;
        }
        // Click a face row in the left list to select it.
        for (int i = 0; i < CraftingSwitchLayout.FACES; i++) {
            final int ry = topPos + CraftingSwitchLayout.rowY(i);
            if (mouseX >= leftPos + CraftingSwitchLayout.LIST_X
                    && mouseX < leftPos + CraftingSwitchLayout.LIST_X + CraftingSwitchLayout.LIST_W
                    && mouseY >= ry && mouseY < ry + CraftingSwitchLayout.ROW_BOX_H) {
                selectFace(i);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX,
                                 final double scrollY) {
        if (categoryPickerOpen && scrollY != 0) {
            final int max = Math.max(0, categories().size() - CP_VIS_ROWS);
            categoryScroll = Math.max(0, Math.min(max, categoryScroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * The text drawn on face row {@code face}: a machine reached over that face's cable run is listed on the
     * row exactly like an adjacent one ("SOUTH > Furnace", "+N" when several), otherwise the plain face label.
     */
    private String rowLabel(final CraftingSwitchBlockEntity be, final int face) {
        final Direction d = Direction.from3DDataValue(face);
        final var viaBus = busMachinesOnFace(face);
        if (be != null && !be.machineOnFace(d) && !viaBus.isEmpty()) {
            return dirShort(d) + " > " + clamp(viaBus.get(0).blockName(), 10)
                    + (viaBus.size() > 1 ? " +" + (viaBus.size() - 1) : "");
        }
        return faceRowLabel(be, d);
    }

    // inspection (client tests assert on what the player sees)

    public int selectedFace() {
        return selectedFace;
    }

    /** The face row text as drawn (see {@link #rowLabel}); {@code face} is a {@link Direction} 3D data value. */
    public String faceRowText(final int face) {
        return rowLabel(blockEntity(), face);
    }

    /** Whether the header pill reads LINKED (the client-synced link flag). */
    public boolean isLinkedShown() {
        final CraftingSwitchBlockEntity be = blockEntity();
        return be != null && be.isLinked();
    }

    public boolean isCategoryPickerOpen() {
        return categoryPickerOpen;
    }

    /** Window-relative centre of face row {@code face}. */
    public static int faceRowX() {
        return CraftingSwitchLayout.LIST_X + CraftingSwitchLayout.LIST_W / 2;
    }

    public static int faceRowY(final int face) {
        return CraftingSwitchLayout.rowY(face) + CraftingSwitchLayout.ROW_BOX_H / 2;
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);
        g.fill(x, y, x + imageWidth, y + 1, LINE);

        final CraftingSwitchBlockEntity be = blockEntity();
        final boolean linked = be != null && be.isLinked();

        // Header status pill. Machines found over the cables show on their face rows, so this stays simple.
        final String pill = linked ? "LINKED" : "UNLINKED";
        g.drawString(this.font, pill, x + imageWidth - 8 - this.font.width(pill),
                y + CraftingSwitchLayout.HEADER_Y + 1, linked ? GREEN : DIM, false);

        /*
         * Face rows. A machine reached over a face's cable run is listed ON that face row, exactly like an
         * adjacent one, since the face is how the player thinks of the connection.
         */
        for (int i = 0; i < CraftingSwitchLayout.FACES; i++) {
            final Direction d = Direction.from3DDataValue(i);
            final int ry = y + CraftingSwitchLayout.rowY(i);
            final int rx = x + CraftingSwitchLayout.LIST_X;
            g.fill(rx, ry, rx + CraftingSwitchLayout.LIST_W, ry + CraftingSwitchLayout.ROW_BOX_H,
                    i == selectedFace ? SEL : PANEL);
            final boolean hasMachine = be != null && (be.machineOnFace(d) || !busMachinesOnFace(i).isEmpty());
            g.drawString(this.font, rowLabel(be, i), rx + 3, ry + 2, hasMachine ? TEXT : DIM, false);
        }

        // Detail panel.
        final int dx = x + CraftingSwitchLayout.DETAIL_X;
        final Direction sel = Direction.from3DDataValue(selectedFace);
        final var selViaBus = busMachinesOnFace(selectedFace);
        if (be != null && !be.machineOnFace(sel) && !selViaBus.isEmpty()) {
            final var line = selViaBus.get(0);
            g.drawString(this.font, clamp(line.blockName(), 15), dx + 4,
                    y + CraftingSwitchLayout.MACHINE_LABEL_Y, ACCENT, false);
            g.drawString(this.font, "NAME", dx + 4, y + CraftingSwitchLayout.NAME_LABEL_Y, DIM, false);
            // Absolute coordinates, tucked between the name field and the active toggle.
            JsTechTheme.textS(g, this.font, "AT " + line.machinePos().getX() + ", " + line.machinePos().getY()
                            + ", " + line.machinePos().getZ(),
                    dx + 4, y + CraftingSwitchLayout.NAME_Y + CraftingSwitchLayout.NAME_H + 2, TEXT);
            if (selViaBus.size() > 1) {
                JsTechTheme.textSRight(g, this.font, "+" + (selViaBus.size() - 1) + " more",
                        dx + CraftingSwitchLayout.DETAIL_W - 2,
                        y + CraftingSwitchLayout.MACHINE_LABEL_Y + 1, DIM);
            }
        } else {
            final String machine = detailMachineLabel(be, sel);
            g.drawString(this.font, machine, dx + 4, y + CraftingSwitchLayout.MACHINE_LABEL_Y, ACCENT, false);
            g.drawString(this.font, "NAME", dx + 4, y + CraftingSwitchLayout.NAME_LABEL_Y, DIM, false);
        }

        /*
         * Player inventory slot frames (3 main rows + hotbar). Without these, the empty creative-mode slots have
         * nothing drawn behind them and the inventory looks like it vanished.
         */
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                JsTechTheme.slot(g, x + CraftingSwitchLayout.INV_X + col * 18,
                        y + CraftingSwitchLayout.INV_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            JsTechTheme.slot(g, x + CraftingSwitchLayout.INV_X + col * 18, y + CraftingSwitchLayout.HOTBAR_Y);
        }
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        g.drawString(this.font, "CRAFTING SWITCH", CraftingSwitchLayout.HEADER_X, CraftingSwitchLayout.HEADER_Y + 1,
                TEXT, false);
        g.drawString(this.font, this.playerInventoryTitle, CraftingSwitchLayout.INV_X,
                CraftingSwitchLayout.INV_LABEL_Y, DIM, false);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        // Keep the controls reflecting the latest synced state (e.g. a machine placed/removed while open).
        refreshActiveLabel();
        super.render(g, mouseX, mouseY, partialTick);
        if (categoryPickerOpen) {
            // Raised Z so the modal sits above the slot items, same pattern as the Pattern Encoder popups.
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            renderCategoryPicker(g, mouseX, mouseY);
            g.pose().popPose();
        } else {
            renderTooltip(g, mouseX, mouseY);
        }
    }

    /** Modal list of the dynamic machine categories (installed recipe types); a row click tags the face. */
    private void renderCategoryPicker(final GuiGraphics g, final int mouseX, final int mouseY) {
        final java.util.List<String> list = categories();
        categoryScroll = Math.max(0, Math.min(Math.max(0, list.size() - CP_VIS_ROWS), categoryScroll));
        final int px = leftPos + CP_X;
        final int py = topPos + CP_Y;
        final int ph = 15 + CP_VIS_ROWS * CP_ROW_H + 3;
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xC0000000);
        g.fill(px, py, px + CP_W, py + ph, PANEL);
        g.fill(px, py, px + 1, py + ph, LINE);
        g.fill(px + CP_W - 1, py, px + CP_W, py + ph, LINE);
        g.drawString(font, "MACHINE CATEGORY", px + 6, py + 3, ACCENT, false);
        g.fill(px + 4, py + 13, px + CP_W - 4, py + 14, LINE);
        for (int r = 0; r < CP_VIS_ROWS && categoryScroll + r < list.size(); r++) {
            final String category = list.get(categoryScroll + r);
            final int ry = py + 15 + r * CP_ROW_H;
            final boolean hovered = mouseX >= px + 3 && mouseX < px + CP_W - 3
                    && mouseY >= ry && mouseY < ry + CP_ROW_H;
            if (hovered) {
                g.fill(px + 3, ry, px + CP_W - 3, ry + CP_ROW_H, SEL);
            }
            JsTechTheme.textS(g, font, category.isEmpty() ? "none" : category, px + 8, ry + 2,
                    hovered ? TEXT : DIM);
        }
        if (list.size() > CP_VIS_ROWS) {
            JsTechTheme.textSRight(g, font, (categoryScroll + 1) + "-"
                            + Math.min(list.size(), categoryScroll + CP_VIS_ROWS) + "/" + list.size(),
                    px + CP_W - 6, py + 4, DIM);
        }
    }

    private static String faceRowLabel(final CraftingSwitchBlockEntity be, final Direction d) {
        final String dir = dirShort(d);
        if (be == null) {
            return dir;
        }
        if (be.cableFace() == d) {
            return dir + " → computer";
        }
        if (be.machineOnFace(d)) {
            final String name = be.faceName(d);
            return dir + "  " + (name.isEmpty() ? "machine" : name);
        }
        return dir + "  none";
    }

    private static String detailMachineLabel(final CraftingSwitchBlockEntity be, final Direction d) {
        if (be == null) {
            return dirShort(d);
        }
        if (be.cableFace() == d) {
            return dirShort(d) + " · crafting cable";
        }
        return dirShort(d) + (be.machineOnFace(d) ? " · machine" : " · no machine");
    }

    private static String dirShort(final Direction d) {
        return switch (d) {
            case DOWN -> "DOWN";
            case UP -> "UP";
            case NORTH -> "NORTH";
            case SOUTH -> "SOUTH";
            case WEST -> "WEST";
            case EAST -> "EAST";
        };
    }
}
