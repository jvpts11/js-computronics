/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.computronics.operation.payload.CraftManagerStatePayload;
import dev.jstech.computronics.operation.payload.CraftManagerStatePayload.WireMachine;
import dev.jstech.computronics.operation.payload.CraftManagerStatePayload.WireRomEntry;
import dev.jstech.computronics.operation.payload.DownloadToMediaPayload;
import dev.jstech.computronics.operation.payload.LoadFromMediaPayload;
import dev.jstech.computronics.operation.payload.RemoveRomCraftPayload;
import dev.jstech.computronics.operation.payload.RequestCraftManagerPayload;
import dev.jstech.computronics.operation.payload.SetMachineConfigPayload;
import dev.jstech.core.client.gui.component.Button;
import dev.jstech.core.client.gui.component.ColumnHeader;
import dev.jstech.core.client.gui.component.Draw;
import dev.jstech.core.client.gui.component.Label;
import dev.jstech.core.client.gui.component.ListView;
import dev.jstech.core.client.gui.component.Panel;
import dev.jstech.core.client.gui.component.TabStrip;
import dev.jstech.core.client.gui.component.Texts;
import dev.jstech.core.client.gui.component.UiContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Crafting Manager desktop app: moves {@code .craft} recipe files between a removable medium in a
 * linked drive and the Crafting Computer's recipe store.
 *
 * <p>The left pane lists the {@code .craft} files on the medium; the right pane lists the recipes
 * loaded on the computer (its Recipe ROM, mirrored as files under {@code crafts/} on its disk). The
 * action bar loads a selection (or every missing file) from the medium, downloads loaded recipes back
 * onto the medium, or removes loaded recipes. Every action needs a Crafting Card installed; without
 * one the app shows a banner and disables the buttons. The Machines tab lists the crafting network's
 * machines by type, each with a pause and a feed switch, and a jobs cap per type.
 */
public final class CraftingManagerApp implements IDesktopApp {

    private static final int WARN_BG = 0xFFFCE3A1;
    private static final int WARN_TEXT = 0xFF6B4E00;

    private static final int PAD = 5;
    private static final int HEADER_H = 12;
    private static final int ROW_H = 11;
    private static final int BTN_H = 13;
    private static final int BAR_H = BTN_H + PAD * 2;
    private static final int TAB_H = 13;
    private static final int M_ROW_H = 15;
    private static final int MAX_JOBS = 16;
    private static final int REFRESH_EVERY_FRAMES = 40;

    private static CraftingManagerApp active;

    private final BlockPos host;
    private OsSkin skin = OsSkin.fallback();

    private String mediaVolumeKey = "";
    private String mediaLabel = "";
    private List<String> mediaFiles = List.of();
    private List<WireRomEntry> romEntries = List.of();
    private boolean hasCard;
    private boolean loaded;
    private String status = "";
    private List<WireMachine> machines = List.of();
    private int tab; // 0 = Recipes, 1 = Machines

    private final Set<Integer> selectedMedia = new HashSet<>();
    private final Set<Integer> selectedRom = new HashSet<>();

    private int lastMouseX;
    private int lastMouseY;
    private int lastW;

    /*
     * Frames since the state was last asked for. The window outlives the screen it was opened on (a machine's
     * open windows come back with their program instances when the monitor is entered again), so the state
     * is re-asked for while the window is shown, not only when it is created: a disc put in the drive after
     * the window opened must show up on its own.
     */
    private int refreshFrames;

    /** A machine-type group header row: the type, how many machines it has, and its shared Max Jobs. */
    private record MachineGroup(String typeKey, int count, int maxJobs) {
    }

    // components
    private final Panel root = new Panel();
    private final TabStrip tabs;
    private final Label warnLabel;
    private final Label mediaHeader;
    private final Label romHeader;
    private final ListView<String> mediaList;
    private final Label mediaEmpty;
    private final ListView<WireRomEntry> romList;
    private final Label romEmpty;
    private final Label statusLabel;
    private final Button[] actions = new Button[4];
    private final Label noCardLabel;
    private final Label noMachinesLabel;
    private final ColumnHeader machineColumns;
    private final ListView<Object> machineList;

    public CraftingManagerApp(final BlockPos host) {
        this.host = host;

        tabs = root.add(new TabStrip(List.of("Recipes", "Machines")).setOnSelect(i -> tab = i));
        warnLabel = root.add(new Label("A Crafting Card is required to manage recipes.").setColor(WARN_TEXT));
        mediaHeader = root.add(new Label(() -> mediaVolumeKey.isEmpty() ? "Removable media: none" : "Media: " + mediaLabel, Label.Tone.DIM));
        romHeader = root.add(new Label(() -> "This computer  (" + romEntries.size() + "/50)", Label.Tone.DIM));
        mediaList = root.add(new ListView<String>(() -> mediaFiles, ROW_H, this::renderMediaRow)
                .setPadding(1)
                .setOnClick((index, button, mx, my) -> toggle(selectedMedia, index, mediaFiles.size())));
        mediaEmpty = root.add(new Label(() -> mediaVolumeKey.isEmpty() ? "Insert a disc into a linked drive" : "No .craft files",
                Label.Tone.DIM));
        romList = root.add(new ListView<WireRomEntry>(() -> romEntries, ROW_H, this::renderRomRow)
                .setPadding(1)
                .setOnClick((index, button, mx, my) -> toggle(selectedRom, index, romEntries.size())));
        romEmpty = root.add(new Label("No recipes loaded", Label.Tone.DIM));
        statusLabel = root.add(new Label(() -> status).setColor(() -> status.contains("full") ? WARN_TEXT : skin.dim()));
        // Short labels: four buttons share the bar, and the narrowest default window leaves ~55px each.
        actions[0] = root.add(new Button("Load →", this::loadSelected));
        actions[1] = root.add(new Button("Load all", this::loadAll));
        actions[2] = root.add(new Button("← Download", this::downloadSelected));
        actions[3] = root.add(new Button("Remove", this::removeSelected));

        noCardLabel = root.add(new Label("A Crafting Card is required.", Label.Tone.DIM));
        noMachinesLabel = root.add(new Label("No machines on the crafting network.", Label.Tone.DIM));
        machineColumns = root.add(new ColumnHeader(List.of("MACHINE", "STATE", "FEED")).setSortable(false));
        machineList = root.add(new ListView<Object>(this::machineDisplay, M_ROW_H, this::renderMachineRow)
                .setOnClick(this::machineRowClicked));

        active = this;
        request();
    }

    private void request() {
        PacketDistributor.sendToServer(new RequestCraftManagerPayload(host));
    }

    @Override
    public void onRestored() {
        active = this;
        request();
    }

    /** Delivers a state refresh from the server to the open window. */
    public static void accept(final CraftManagerStatePayload payload) {
        if (active == null) {
            return;
        }
        active.mediaVolumeKey = payload.mediaVolumeKey();
        active.mediaLabel = payload.mediaLabel();
        active.mediaFiles = payload.mediaFiles();
        active.romEntries = payload.romEntries();
        active.machines = payload.machines();
        active.hasCard = payload.hasCard();
        if (!payload.status().isEmpty()) {
            active.status = payload.status();
        }
        active.loaded = true;
        active.selectedMedia.removeIf(i -> i >= active.mediaFiles.size());
        active.selectedRom.removeIf(i -> i >= active.romEntries.size());
    }

    @Override
    public void applySkin(final OsSkin osSkin) {
        /*
         * Runs each frame for the window being drawn: with two Crafting Computers open in turn, the replies
         * must reach the window on screen, not the instance created last.
         */
        active = this;
        this.skin = osSkin;
    }

    @Override
    public String title() {
        return "Crafting Manager";
    }

    @Override
    public int defaultWidth() {
        return 280;
    }

    @Override
    public int defaultHeight() {
        return 196;
    }

    @Override
    public int minWidth() {
        return 240;
    }

    @Override
    public int minHeight() {
        return 150;
    }

    // rendering

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        lastW = width;
        if (++refreshFrames >= REFRESH_EVERY_FRAMES) {
            refreshFrames = 0;
            request();
        }
        final UiContext ctx = new UiContext(skin, font, mouseX, mouseY, partialTick);
        g.fill(x, y, x + width, y + height, skin.windowBg());
        Draw.outline(g, x, y, width, height, skin.edge());
        layout(x, y, width, height);

        // The bands the components sit on: the card warning, the pane headers, the status line, the action bar.
        if (warnLabel.visible()) {
            g.fill(x + 1, y + TAB_H + 1, x + width - 1, y + TAB_H + 1 + HEADER_H, WARN_BG);
        }
        if (tab == 0) {
            g.fill(mediaHeader.x() - 3, mediaHeader.y() - 2, mediaHeader.right() + 3, mediaHeader.y() - 2 + HEADER_H, skin.panelBg());
            g.fill(romHeader.x() - 3, romHeader.y() - 2, romHeader.right() + 3, romHeader.y() - 2 + HEADER_H, skin.panelBg());
            for (final ListView<?> list : List.of(mediaList, romList)) {
                g.fill(list.x(), list.y(), list.right(), list.bottom(), skin.fieldBg());
                Draw.outline(g, list.x(), list.y(), list.width(), list.height(), skin.edge());
            }
            final int barY = actions[0].y() - PAD;
            if (!status.isEmpty()) {
                g.fill(x + 1, barY - 10, x + width - 1, barY, skin.panelBg());
            }
            g.fill(x + 1, barY, x + width - 1, y + height - 1, skin.panelBg());
            g.fill(x + 1, barY, x + width - 1, barY + 1, skin.edge());
        }
        root.render(g, ctx);
    }

    /** Places the tab's components from the content rectangle; the other tab's are hidden. */
    private void layout(final int x, final int y, final int width, final int height) {
        tabs.setBounds(x, y, width, TAB_H);
        tabs.setSelected(tab);
        final int cy = y + TAB_H;
        final int ch = height - TAB_H;
        final boolean recipes = tab == 0;

        warnLabel.setVisible(recipes && loaded && !hasCard);
        warnLabel.setBounds(x + 4, cy + 3, width - 8, 8);
        final int top = loaded && !hasCard ? cy + 1 + HEADER_H : cy;
        final int barY = cy + ch - BAR_H;
        final int listTop = top + HEADER_H;
        final int listH = barY - 1 - listTop;
        final int paneW = (width - PAD * 3) / 2;
        final int leftX = x + PAD;
        final int rightX = leftX + paneW + PAD;

        mediaHeader.setVisible(recipes);
        mediaHeader.setBounds(leftX + 3, top + 2, paneW - 6, 8);
        romHeader.setVisible(recipes);
        romHeader.setBounds(rightX + 3, top + 2, paneW - 6, 8);
        mediaList.setVisible(recipes);
        mediaList.setBounds(leftX, listTop, paneW, listH);
        mediaEmpty.setVisible(recipes && mediaFiles.isEmpty());
        mediaEmpty.setBounds(leftX + 4, listTop + 4, paneW - 8, 8);
        romList.setVisible(recipes);
        romList.setBounds(rightX, listTop, paneW, listH);
        romEmpty.setVisible(recipes && romEntries.isEmpty());
        romEmpty.setBounds(rightX + 4, listTop + 4, paneW - 8, 8);
        statusLabel.setVisible(recipes && !status.isEmpty());
        statusLabel.setBounds(x + 4, barY - 9, width - 8, 8);

        final boolean[] enabled = {
                hasCard && !selectedMedia.isEmpty(),
                hasCard && !mediaVolumeKey.isEmpty(),
                hasCard && !selectedRom.isEmpty() && !mediaVolumeKey.isEmpty(),
                hasCard && !selectedRom.isEmpty(),
        };
        final int btnW = (width - PAD * (actions.length + 1)) / actions.length;
        for (int i = 0; i < actions.length; i++) {
            actions[i].setVisible(recipes);
            actions[i].setEnabled(loaded && enabled[i]);
            actions[i].setBounds(x + PAD + i * (btnW + PAD), barY + PAD, btnW, BTN_H);
        }

        final boolean machinesTab = !recipes;
        noCardLabel.setVisible(machinesTab && !hasCard);
        noCardLabel.setBounds(x + PAD, cy + PAD, width - PAD * 2, 8);
        noMachinesLabel.setVisible(machinesTab && hasCard && machines.isEmpty());
        noMachinesLabel.setBounds(x + PAD, cy + PAD, width - PAD * 2, 8);
        final boolean showMachines = machinesTab && hasCard && !machines.isEmpty();
        machineColumns.setVisible(showMachines);
        machineColumns.setBounds(x + 1, cy, width - 2, 12);
        machineColumns.setColumnX(x + PAD, machineButtonX(x, width, 0), machineButtonX(x, width, 1));
        machineList.setVisible(showMachines);
        machineList.setBounds(x, cy + 12, width, Math.max(M_ROW_H, ch - 12 - PAD));
    }

    private void renderMediaRow(final GuiGraphics g, final UiContext ctx, final String file, final int index, final int x,
                                final int y, final int w, final int h, final boolean hovered, final boolean selected) {
        renderSelectableRow(g, ctx, file, selectedMedia.contains(index), x, y, w, h, hovered);
    }

    private void renderRomRow(final GuiGraphics g, final UiContext ctx, final WireRomEntry entry, final int index, final int x,
                              final int y, final int w, final int h, final boolean hovered, final boolean selected) {
        renderSelectableRow(g, ctx, (entry.inMedia() ? "= " : "") + entry.name(), selectedRom.contains(index), x, y, w, h, hovered);
    }

    private static void renderSelectableRow(final GuiGraphics g, final UiContext ctx, final String text, final boolean sel,
                                            final int x, final int y, final int w, final int h, final boolean hovered) {
        ctx.skin().listRow(g, x, y, w, h, hovered, sel);
        g.drawString(ctx.font(), Texts.clip(ctx.font(), text, w - 6), x + 3, y + 2, ctx.skin().listRowText(sel), false);
    }

    // actions

    /**
     * The wire indices of the selected ROM rows. List positions are NOT the payload indices: machine recipes
     * carry offset indices (MACHINE_ROM_BASE + i), so every action must send {@code entry.index()}; sending
     * the raw list position would remove or export a different (bench) recipe.
     */
    private List<Integer> selectedRomIndices() {
        final List<Integer> out = new ArrayList<>();
        for (final int pos : selectedRom) {
            if (pos >= 0 && pos < romEntries.size()) {
                out.add(romEntries.get(pos).index());
            }
        }
        return out;
    }

    private void loadSelected() {
        final List<String> files = new ArrayList<>();
        for (final int i : selectedMedia) {
            if (i < mediaFiles.size()) {
                files.add(mediaFiles.get(i));
            }
        }
        if (!files.isEmpty()) {
            PacketDistributor.sendToServer(new LoadFromMediaPayload(host, mediaVolumeKey, files, false));
        }
    }

    private void loadAll() {
        PacketDistributor.sendToServer(new LoadFromMediaPayload(host, mediaVolumeKey, List.of(), true));
    }

    private void downloadSelected() {
        if (!selectedRom.isEmpty()) {
            PacketDistributor.sendToServer(new DownloadToMediaPayload(host, mediaVolumeKey, selectedRomIndices()));
        }
    }

    private void removeSelected() {
        if (!selectedRom.isEmpty()) {
            PacketDistributor.sendToServer(new RemoveRomCraftPayload(host, selectedRomIndices()));
            selectedRom.clear();
        }
    }

    private static void toggle(final Set<Integer> set, final int index, final int size) {
        if (index < 0 || index >= size) {
            return;
        }
        if (!set.remove(index)) {
            set.add(index);
        }
    }

    // the Machines tab: physical machines grouped by type, with per-machine Pause/Feed and a per-type Max Jobs

    private static int machineButtonW(final int width) {
        return Math.max(20, (width / 2 - PAD * 2) / 3);
    }

    private static int machineButtonX(final int x, final int width, final int idx) {
        return x + width / 2 + PAD + idx * (machineButtonW(width) + 2);
    }

    /** The Machines tab as a flat display list: a group header per machine type, then that type's machines. */
    private List<Object> machineDisplay() {
        final Map<String, List<WireMachine>> byType = new LinkedHashMap<>();
        for (final WireMachine m : machines) {
            byType.computeIfAbsent(m.typeKey(), k -> new ArrayList<>()).add(m);
        }
        final List<Object> items = new ArrayList<>();
        for (final Map.Entry<String, List<WireMachine>> e : byType.entrySet()) {
            items.add(new MachineGroup(e.getKey(), e.getValue().size(), e.getValue().get(0).typeMaxJobs()));
            items.addAll(e.getValue());
        }
        return items;
    }

    /** "mekanism:ultimate_infusing_factory" -> "Ultimate Infusing Factory". */
    private static String prettyType(final String typeKey) {
        final int colon = typeKey.indexOf(':');
        final String base = (colon >= 0 ? typeKey.substring(colon + 1) : typeKey).replace('_', ' ');
        final StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (final char c : base.toCharArray()) {
            sb.append(cap && c != ' ' ? Character.toUpperCase(c) : c);
            cap = c == ' ';
        }
        return sb.toString();
    }

    private void renderMachineRow(final GuiGraphics g, final UiContext ctx, final Object item, final int index, final int x,
                                  final int y, final int w, final int h, final boolean hovered, final boolean selected) {
        final Font font = ctx.font();
        final int bw = machineButtonW(lastW);
        if (item instanceof MachineGroup grp) {
            final String head = prettyType(grp.typeKey()) + "  ·  " + grp.count() + (grp.count() == 1 ? " machine" : " machines");
            g.drawString(font, Texts.clip(font, head, lastW / 2 - PAD), x + PAD, y + 3, ctx.skin().accent(), false);
            rowButton(g, ctx, machineButtonX(x, lastW, 2), y, bw, "Jobs " + (grp.maxJobs() == 0 ? "Auto" : String.valueOf(grp.maxJobs())));
        } else if (item instanceof WireMachine m) {
            g.drawString(font, Texts.clip(font, m.label(), lastW / 2 - PAD * 2 - 6), x + PAD + 6, y + 3, ctx.skin().text(), false);
            rowButton(g, ctx, machineButtonX(x, lastW, 0), y, bw, m.locked() ? "Paused" : "Running");
            rowButton(g, ctx, machineButtonX(x, lastW, 1), y, bw, m.feedMax() ? "Fill" : "1 lot");
        }
    }

    /** A button drawn inside a list row; the row's click handler tells which one was hit by its column. */
    private static void rowButton(final GuiGraphics g, final UiContext ctx, final int bx, final int by, final int bw,
                                  final String label) {
        ctx.skin().button(g, ctx.font(), bx, by, bw, BTN_H, Texts.clip(ctx.font(), label, bw - 4), ctx.over(bx, by, bw, BTN_H),
                false, false);
    }

    private void machineRowClicked(final int index, final int button, final double mx, final double my) {
        final List<Object> items = machineDisplay();
        if (button != 0 || index < 0 || index >= items.size()) {
            return;
        }
        final int[] rect = machineList.rowRect(index);
        if (my >= rect[1] + BTN_H) {
            return; // the row is taller than its buttons
        }
        final int bw = machineButtonW(lastW);
        final Object item = items.get(index);
        if (item instanceof MachineGroup grp && inButton(machineButtonX(rect[0], lastW, 2), bw, mx)) {
            // Max Jobs cycles ...->MAX_JOBS->Auto, per machine TYPE.
            final int jobs = grp.maxJobs() >= MAX_JOBS ? 0 : grp.maxJobs() + 1;
            PacketDistributor.sendToServer(new SetMachineConfigPayload(host, grp.typeKey(), jobs, false, false));
        } else if (item instanceof WireMachine m) {
            if (inButton(machineButtonX(rect[0], lastW, 0), bw, mx)) {
                PacketDistributor.sendToServer(new SetMachineConfigPayload(host, m.machineKey(), 0, !m.locked(), m.feedMax()));
            } else if (inButton(machineButtonX(rect[0], lastW, 1), bw, mx)) {
                PacketDistributor.sendToServer(new SetMachineConfigPayload(host, m.machineKey(), 0, m.locked(), !m.feedMax()));
            }
        }
    }

    private static boolean inButton(final int bx, final int bw, final double mx) {
        return mx >= bx && mx < bx + bw;
    }

    // input

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        if (!loaded) {
            return;
        }
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
        // The wheel elsewhere in the window moves the machines, or the longer of the two recipe panes.
        final int step = delta > 0 ? -1 : 1;
        if (tab == 1) {
            machineList.setScroll(machineList.scroll() + step);
        } else if (romEntries.size() > mediaFiles.size()) {
            romList.setScroll(romList.scroll() + step);
        } else {
            mediaList.setScroll(mediaList.scroll() + step);
        }
        return true;
    }

    // inspection (client tests)

    public boolean isLoaded() {
        return loaded;
    }

    public boolean hasCard() {
        return hasCard;
    }

    public int activeTab() {
        return tab;
    }

    public String status() {
        return status;
    }

    public List<String> mediaFiles() {
        return mediaFiles;
    }

    /** The ROM entries' display names, as listed. */
    public List<String> romNames() {
        final List<String> out = new ArrayList<>();
        for (final WireRomEntry e : romEntries) {
            out.add(e.name());
        }
        return out;
    }

    public List<WireMachine> machines() {
        return machines;
    }

    /** Centre of the Recipes/Machines tab {@code index} (0 or 1), in the coordinates the content is drawn in. */
    public int[] tabCenter(final int index) {
        return tabs.tabCenter(index);
    }

    /** Centre of action button {@code index}: 0 Load, 1 Load all, 2 Download, 3 Remove. */
    public int[] actionButtonCenter(final int index) {
        return actions[Math.max(0, Math.min(actions.length - 1, index))].center();
    }

    /** Centre of the {@code index}-th visible row of the media (left) list. */
    public int[] mediaRowCenter(final int index) {
        return mediaList.rowCenter(index);
    }

    /** Centre of the {@code index}-th visible row of the ROM (right) list. */
    public int[] romRowCenter(final int index) {
        return romList.rowCenter(index);
    }
}
