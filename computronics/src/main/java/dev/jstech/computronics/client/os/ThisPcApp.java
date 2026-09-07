/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.computronics.gui.layout.ThisPcLayout;
import dev.jstech.computronics.operation.payload.EjectMediaPayload;
import dev.jstech.computronics.operation.payload.InstallFromMediaPayload;
import dev.jstech.computronics.operation.payload.RenameVolumePayload;
import dev.jstech.computronics.operation.payload.RequestThisPcPayload;
import dev.jstech.computronics.operation.payload.SetSettingPayload;
import dev.jstech.computronics.operation.payload.ThisPcPayload;
import dev.jstech.computronics.operation.payload.ThisPcPayload.WireDisk;
import dev.jstech.computronics.operation.payload.ThisPcPayload.WireMedia;
import dev.jstech.computronics.os.Branding;
import dev.jstech.computronics.os.MinSpecTooltip;
import dev.jstech.computronics.os.OsDef;
import dev.jstech.computronics.os.OsRegistry;
import dev.jstech.computronics.os.ProgramSpec;
import dev.jstech.core.client.gui.component.Button;
import dev.jstech.core.client.gui.component.CellGrid;
import dev.jstech.core.client.gui.component.Draw;
import dev.jstech.core.client.gui.component.Label;
import dev.jstech.core.client.gui.component.Panel;
import dev.jstech.core.client.gui.component.ScrollPanel;
import dev.jstech.core.client.gui.component.TextField;
import dev.jstech.core.client.gui.component.Texts;
import dev.jstech.core.client.gui.component.UiComponent;
import dev.jstech.core.client.gui.component.UiContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The "This PC" desktop app, a system page: the machine itself in a card (name, kind, era, system,
 * network), then its devices and drives (each disk with a usage bar and its system, each linked drive
 * naming what is in it and, for an installer, what it would install, with Install, Open and Eject),
 * the hardware seated in it, and the programs installed on it as a grid. Everything is fetched from
 * the server and refreshes after an action. The geometry lives in {@link ThisPcLayout}.
 *
 * <p>The card and the page are components; the page is rebuilt from each listing the server sends,
 * a row per drive with its own buttons, and scrolls as one.
 */
public final class ThisPcApp implements IDesktopApp {

    /** The usage bar's segments: the system, the items stored, and the files/programs. */
    private static final int SEG_OS = 0xFF3F77C8;
    private static final int SEG_STORE = 0xFF5B9E5B;
    private static final int SEG_FILES = 0xFFD79A3A;
    private static final int GREEN = 0xFF2E7D32;
    private static final int AMBER = 0xFFB35C00;
    private static final int RENAME_W = 34;
    private static final int NAME_MAX = 32;
    private static final long DOUBLE_CLICK_MS = 300L;

    private OsSkin skin = OsSkin.fallback();

    private final BlockPos host;
    /** The window's name, which differs by platform: "This PC" on Frames, "Disks" on Linux. */
    private final String title;
    private ThisPcPayload data = new ThisPcPayload(ThisPcPayload.WireMachine.EMPTY, List.of(), List.of(), List.of());
    private int lastX;
    private int lastY;
    private int lastMouseX;
    private int lastMouseY;

    private long lastClickAt;
    private String lastClickKey = "";
    private String selectedKey = "";
    /** The volume the inline field renames, or empty while it renames the machine itself. */
    private String volumeRenameKey = "";

    private static ThisPcApp active;

    // components
    private final Panel root = new Panel();
    private final Label nameLabel;
    private final Label kindLabel;
    private final Label systemLabel;
    private final Button renameButton;
    private final TextField nameField;
    private final ScrollPanel page = new ScrollPanel().setStep(ThisPcLayout.KV_ROW_H);
    private final SectionHeader drivesHeader;
    private final Label noDrives;
    private final List<DriveRow> rows = new ArrayList<>();
    private final SectionHeader hardwareHeader;
    private final List<Label> hwKeys = new ArrayList<>();
    private final List<Label> hwValues = new ArrayList<>();
    private final SectionHeader programsHeader;
    private final Label noPrograms;
    private final CellGrid programGrid;

    /** A section's title band across the page. */
    private static final class SectionHeader extends UiComponent {

        private final Supplier<String> text;

        SectionHeader(final Supplier<String> text) {
            this.text = text;
        }

        @Override
        public void render(final GuiGraphics g, final UiContext ctx) {
            g.fill(x() + 1, y(), right() - 1, bottom(), ctx.skin().listHover());
            g.fill(x() + 1, bottom() - 1, right() - 1, bottom(), ctx.skin().edge());
            g.drawString(ctx.font(), text.get(), x() + 4, y() + 2, ctx.skin().dim(), false);
        }
    }

    /**
     * A disk or a linked drive on the page: two lines of text, the usage bar of a disk, and the buttons
     * that act on it. A click selects it and a double-click opens it in the explorer.
     */
    private final class DriveRow extends Panel {

        private final String key;
        @Nullable
        private final WireDisk disk;
        @Nullable
        private final WireMedia media;
        private final int letterIndex;
        private final List<Button> buttons = new ArrayList<>();
        @Nullable
        private Button install;
        @Nullable
        private final Runnable open;

        DriveRow(final WireDisk disk, final int letterIndex) {
            this.key = "disk:" + disk.slot();
            this.disk = disk;
            this.media = null;
            this.letterIndex = letterIndex;
            this.open = () -> DesktopScreen.requestOpenFiles("");
            buttons.add(add(new Button("Open", open)));
        }

        DriveRow(final WireMedia media, final int letterIndex) {
            this.key = "media:" + media.readerPos();
            this.disk = null;
            this.media = media;
            this.letterIndex = letterIndex;
            this.open = media.loaded() ? () -> DesktopScreen.requestOpenFiles(key) : null;
            if (media.installable()) {
                install = add(new Button("Install", () -> install(media.readerPos())).setPrimary(true));
                buttons.add(install);
            }
            if (media.loaded()) {
                buttons.add(add(new Button("Open", open)));
                buttons.add(add(new Button("Eject", () -> eject(media.readerPos()))));
            }
        }

        /** The row across the content at {@code y}; the buttons keep to the right edge. */
        void layout(final int contentX, final int y, final int width) {
            setBounds(contentX + 1, y, width - 2, ThisPcLayout.DRIVE_ROW_H);
            final int by = y + (ThisPcLayout.DRIVE_ROW_H - ThisPcLayout.BTN_H) / 2;
            for (int i = 0; i < buttons.size(); i++) {
                buttons.get(i).setBounds(contentX + ThisPcLayout.buttonX(width, i, buttons.size()), by,
                        ThisPcLayout.BTN_W, ThisPcLayout.BTN_H);
            }
        }

        private String drive() {
            return linux() ? "" : (char) ('C' + letterIndex) + ":";
        }

        @Override
        public void render(final GuiGraphics g, final UiContext ctx) {
            final Font font = ctx.font();
            final boolean sel = key.equals(selectedKey);
            ctx.skin().listRow(g, x(), y(), width(), height(), hovered(ctx), sel);
            final int cx = x() - 1;
            final int cy = y();
            final int width = width() + 2;
            final int tx = cx + ThisPcLayout.TEXT_X;
            final int maxW = ThisPcLayout.driveTextMaxW(width, buttons.size());
            final int textColor = ctx.skin().listRowText(sel);
            if (disk != null) {
                drawDiskIcon(g, cx + 4, cy + 5);
                final String label = disk.label() + (drive().isEmpty() ? "" : "  " + drive());
                g.drawString(font, Texts.trim(font, label, maxW), tx, cy + 2, textColor, false);
                if (disk.system()) {
                    final String badge = "System" + (disk.osPath().isEmpty() ? "" : " · " + prettyOs(disk.osPath()));
                    final int bw = font.width(badge);
                    if (font.width(label) + 6 + bw <= maxW) {
                        g.drawString(font, badge, tx + maxW - bw, cy + 2, GREEN, false);
                    }
                }
                /*
                 * The bar is segmented by what actually takes the space, so "the disk is full" always
                 * comes with "of what": the system, the items stored on it, or its files.
                 */
                final int barW = maxW - 70;
                g.fill(tx, cy + 12, tx + barW, cy + 15, 0xFFD7DBE4);
                final long cap = Math.max(1, disk.capItems());
                int segX = tx;
                for (final long[] part : new long[][] {{disk.osItems(), SEG_OS}, {disk.storeItems(), SEG_STORE}, {disk.fileItems(), SEG_FILES}}) {
                    final int w = (int) Math.min(tx + barW - segX, barW * part[0] / cap);
                    if (w > 0) {
                        g.fill(segX, cy + 12, segX + w, cy + 15, (int) part[1]);
                        segX += w;
                    }
                }
                final String usage = disk.freeItems() + " of " + disk.capItems() + " it free";
                g.drawString(font, usage, tx + maxW - font.width(usage), cy + 11, ctx.skin().dim(), false);
            } else if (media != null) {
                drawMediaIcon(g, cx + 4, cy + 5, media);
                final String head = prettyDrive(media.drive()) + (drive().isEmpty() ? "" : "  " + drive())
                        + (media.loaded() ? "   " + media.mediaName() : "   no disc");
                g.drawString(font, Texts.trim(font, head, maxW), tx, cy + 2, media.loaded() ? textColor : ctx.skin().dim(), false);
                final String detail;
                int detailColor = ctx.skin().dim();
                if (!media.loaded()) {
                    detail = prettyDrive(media.drive()) + " drive, " + media.blocksAway() + " blocks away";
                } else if (media.kind().equals("OS_INSTALL")) {
                    detail = "Installs " + (media.payloadName().isEmpty() ? media.payloadPath() : media.payloadName())
                            + (media.payloadYear() > 0 ? " · " + media.payloadYear() : "") + " · bootable"
                            + (media.packageId().isEmpty() ? "" : " · package " + media.packageId());
                    detailColor = AMBER;
                } else if (media.kind().equals("PROGRAM_INSTALL")) {
                    detail = (media.installable() ? "Installs " : "Installed: ")
                            + (media.payloadName().isEmpty() ? media.payloadPath() : media.payloadName())
                            + (media.payloadYear() > 0 ? " · " + media.payloadYear() : "")
                            + (media.packageId().isEmpty() ? "" : " · package " + media.packageId());
                    detailColor = media.installable() ? GREEN : ctx.skin().dim();
                } else {
                    detail = "Data medium · " + media.stored() + " stored";
                }
                g.drawString(font, Texts.trim(font, detail, maxW), tx, cy + 11, detailColor, false);
            }
            super.render(g, ctx);
        }

        @Override
        public boolean mouseClicked(final double mx, final double my, final int button) {
            if (super.mouseClicked(mx, my, button)) {
                return true; // a button on the row
            }
            final long now = System.currentTimeMillis();
            if (key.equals(lastClickKey) && now - lastClickAt < DOUBLE_CLICK_MS && open != null) {
                open.run();
            }
            lastClickKey = key;
            lastClickAt = now;
            selectedKey = key;
            return true;
        }

        @Override
        public List<Component> tooltip(final double mx, final double my) {
            final List<Component> onButton = super.tooltip(mx, my);
            if (!onButton.isEmpty()) {
                return onButton;
            }
            if (media != null) {
                // The requirements ride on the tooltip of the detail line; the row has room for one line.
                if (!media.needs().isEmpty() && media.loaded() && !media.kind().equals("DATA") && my >= y() + 11 && my < y() + 20) {
                    final List<Component> lines = new ArrayList<>(3);
                    lines.add(Component.literal(media.payloadName().isEmpty() ? media.mediaName() : media.payloadName()));
                    for (final String need : media.needs().split(" · ")) {
                        if (!need.isBlank()) {
                            lines.add(Component.literal(need).withStyle(ChatFormatting.DARK_GRAY));
                        }
                    }
                    return lines;
                }
                return List.of();
            }
            if (disk != null) {
                final List<Component> lines = new ArrayList<>(6);
                lines.add(Component.literal(disk.label()));
                lines.add(Component.literal(disk.osPath().isEmpty() ? "No system installed" : "System: " + prettyOs(disk.osPath()))
                        .withStyle(disk.osPath().isEmpty() ? ChatFormatting.GRAY : ChatFormatting.AQUA));
                lines.add(Component.literal("  system   " + disk.osItems() + " it").withStyle(ChatFormatting.BLUE));
                lines.add(Component.literal("  items    " + disk.storeItems() + " it").withStyle(ChatFormatting.GREEN));
                lines.add(Component.literal("  files    " + disk.fileItems() + " it").withStyle(ChatFormatting.GOLD));
                lines.add(Component.literal("  free     " + disk.freeItems() + " it").withStyle(ChatFormatting.DARK_GRAY));
                return lines;
            }
            return List.of();
        }
    }

    public ThisPcApp(final BlockPos host) {
        this(host, "This PC");
    }

    public ThisPcApp(final BlockPos host, final String title) {
        this.host = host;
        this.title = title;

        nameLabel = root.add(new Label(this::machineName));
        kindLabel = root.add(new Label(this::kindLine, Label.Tone.DIM));
        systemLabel = root.add(new Label(this::systemLine).setColor(() -> data.machine().osLabel().isEmpty() ? AMBER : 0)
                .setTone(Label.Tone.DIM));
        renameButton = root.add(new Button("Rename", this::startRename));
        root.add(page);
        drivesHeader = page.add(new SectionHeader(() -> "Devices and drives"));
        noDrives = page.add(new Label("No disks installed and no drives linked", Label.Tone.DIM));
        hardwareHeader = page.add(new SectionHeader(() -> "Hardware"));
        final String[] keys = {"Board", "Processor", "Memory", "Graphics", "Power", "Peripherals", "Build"};
        for (int i = 0; i < keys.length; i++) {
            final int line = i;
            hwKeys.add(page.add(new Label(keys[i], Label.Tone.DIM)));
            hwValues.add(page.add(new Label(() -> hardwareValue(line))
                    .setColor(() -> line == 6 ? (data.machine().buildValid() ? GREEN : AMBER) : 0)));
        }
        programsHeader = page.add(new SectionHeader(() -> "Installed programs  " + data.installedPrograms().size()));
        noPrograms = page.add(new Label("None. Insert an installer, or run a package manager.", Label.Tone.DIM));
        programGrid = page.add(new CellGrid(1, 1, 1, ThisPcLayout.PROG_CELL_W, ThisPcLayout.PROG_CELL_H)
                .setWells(false)
                .setInset(2)
                .setSelected(i -> i < data.installedPrograms().size() && ("program:" + data.installedPrograms().get(i)).equals(selectedKey))
                .setRenderer(this::renderProgramCell)
                .setTooltip(this::programTooltip)
                .setOnClick((index, button, shift) -> selectedKey = "program:" + data.installedPrograms().get(index)));
        // The inline field sits over the card's name; it is added last so it stays in front.
        nameField = root.add(new TextField(NAME_MAX).setOnCommit(this::commitRename));
        nameField.setVisible(false);

        active = this;
        request();
    }

    /** Routes a "This PC" listing reply to the open window. */
    public static void accept(final ThisPcPayload payload) {
        if (active != null) {
            active.data = payload;
            active.rebuildRows();
        }
    }

    @Override
    public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
    }

    private void request() {
        PacketDistributor.sendToServer(new RequestThisPcPayload(host));
    }

    @Override
    public void onRestored() {
        active = this;
        request();
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public int defaultWidth() {
        return ThisPcLayout.DEFAULT_W;
    }

    @Override
    public int defaultHeight() {
        return ThisPcLayout.DEFAULT_H;
    }

    @Override
    public int minWidth() {
        return ThisPcLayout.MIN_W;
    }

    @Override
    public int minHeight() {
        return ThisPcLayout.MIN_H;
    }

    private boolean linux() {
        return !skin.osPath().startsWith("frames_");
    }

    // state readers

    private String machineName() {
        final ThisPcPayload.WireMachine m = data.machine();
        return m.name().isEmpty() ? m.kind() : m.name();
    }

    private String kindLine() {
        final ThisPcPayload.WireMachine m = data.machine();
        return m.name().isEmpty() ? m.era() + " era" : m.kind() + " · " + m.era() + " era";
    }

    private String systemLine() {
        final ThisPcPayload.WireMachine m = data.machine();
        final String system = m.osLabel().isEmpty() ? "No system installed"
                : m.osLabel() + " · " + Branding.houseOf(m.osLabel()).name() + " " + m.osYear();
        final String net = m.networkLabel().isEmpty() ? "not on a network" : "network " + m.networkLabel();
        return system + " · " + net;
    }

    private String hardwareValue(final int line) {
        final ThisPcPayload.WireMachine m = data.machine();
        return switch (line) {
            case 0 -> m.boardLabel().isEmpty() ? "none" : m.boardLabel();
            case 1 -> m.cpuLabel().isEmpty() ? "none" : (m.cpuCount() > 1 ? m.cpuCount() + " × " : "") + m.cpuLabel();
            case 2 -> m.ramMb() > 0 ? m.ramMb() + " it" : "none";
            case 3 -> m.gpuCount() > 0 ? m.gpuCount() + " × " + m.vramMb() + " MB VRAM" : "none";
            case 4 -> m.psuLabel().isEmpty() ? "none" : m.psuLabel();
            case 5 -> m.peripherals().isEmpty() ? "none linked" : m.peripherals();
            default -> m.buildValid() ? "OK, the machine comes up" : "not valid";
        };
    }

    /** Rebuilds the drive rows from the listing: one row per disk, then one per linked drive. */
    private void rebuildRows() {
        for (final DriveRow row : rows) {
            page.remove(row);
        }
        rows.clear();
        int letter = 0;
        for (final WireDisk d : data.disks()) {
            rows.add(page.add(new DriveRow(d, letter++)));
        }
        for (final WireMedia m : data.media()) {
            rows.add(page.add(new DriveRow(m, letter++)));
        }
    }

    // rendering

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        active = this;
        lastX = x;
        lastY = y;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        final UiContext ctx = new UiContext(skin, font, mouseX, mouseY, partialTick);
        g.fill(x, y, x + width, y + height, skin.windowBg());
        layout(x, y, width, height);

        // The machine's icon: a tower with a lit power dot.
        final ThisPcPayload.WireMachine m = data.machine();
        final int ix = x + 4;
        final int iy = y + 4;
        g.fill(ix, iy, ix + ThisPcLayout.CARD_ICON_W, iy + ThisPcLayout.CARD_H - 8, 0xFF2E3238);
        g.fill(ix + 2, iy + 2, ix + ThisPcLayout.CARD_ICON_W - 2, iy + 7, 0xFF1C1F24);
        g.fill(ix + ThisPcLayout.CARD_ICON_W - 6, iy + 3, ix + ThisPcLayout.CARD_ICON_W - 4, iy + 5,
                m.buildValid() ? 0xFF39D6C4 : 0xFFEF6A5A);
        g.fill(ix + 3, iy + 10, ix + ThisPcLayout.CARD_ICON_W - 3, iy + ThisPcLayout.CARD_H - 11, 0xFF3D434C);
        Draw.outline(g, ix, iy, ThisPcLayout.CARD_ICON_W, ThisPcLayout.CARD_H - 8, 0xFF1C1F24);
        g.fill(x, page.y() - 1, x + width, page.y(), skin.edge());

        root.render(g, ctx);
    }

    /** Places the card and lays the page out top to bottom, telling it how tall the whole content is. */
    private void layout(final int x, final int y, final int width, final int height) {
        final int tx = x + 4 + ThisPcLayout.CARD_ICON_W + 4;
        final int textMax = width - (tx - x) - RENAME_W - 8;
        final boolean renaming = nameField.isFocused();
        nameLabel.setVisible(!renaming);
        nameLabel.setBounds(tx, y + 3, textMax, 8);
        nameField.setVisible(renaming);
        nameField.setBounds(tx - 3, y + 1, textMax + 6, 11);
        kindLabel.setBounds(tx, y + 12, textMax, 8);
        systemLabel.setBounds(tx, y + 21, textMax, 8);
        renameButton.setBounds(x + width - RENAME_W - 3, y + 4, RENAME_W, ThisPcLayout.BTN_H);

        page.setBounds(x, y + ThisPcLayout.pageY(), width, ThisPcLayout.pageH(height));
        int cy = 2;
        drivesHeader.setBounds(x, page.contentY(cy), width, ThisPcLayout.HEADER_H);
        cy += ThisPcLayout.HEADER_H + 1;
        noDrives.setVisible(rows.isEmpty());
        if (rows.isEmpty()) {
            noDrives.setBounds(x + 8, page.contentY(cy + 3), width - 16, 8);
            cy += ThisPcLayout.KV_ROW_H + 4;
        } else {
            for (final DriveRow row : rows) {
                row.layout(x, page.contentY(cy), width);
                cy += ThisPcLayout.DRIVE_ROW_H;
            }
            cy += 2;
        }
        hardwareHeader.setBounds(x, page.contentY(cy), width, ThisPcLayout.HEADER_H);
        cy += ThisPcLayout.HEADER_H + 1;
        final int keyW = 52;
        for (int i = 0; i < hwKeys.size(); i++) {
            hwKeys.get(i).setBounds(x + 8, page.contentY(cy + 1), keyW, 8);
            hwValues.get(i).setBounds(x + 8 + keyW, page.contentY(cy + 1), width - 8 - keyW - 8, 8);
            cy += ThisPcLayout.KV_ROW_H;
        }
        cy += 2;
        programsHeader.setBounds(x, page.contentY(cy), width, ThisPcLayout.HEADER_H);
        cy += ThisPcLayout.HEADER_H + 1;
        final List<String> programs = data.installedPrograms();
        noPrograms.setVisible(programs.isEmpty());
        programGrid.setVisible(!programs.isEmpty());
        if (programs.isEmpty()) {
            noPrograms.setBounds(x + 8, page.contentY(cy + 3), width - 16, 8);
            cy += ThisPcLayout.KV_ROW_H + 4;
        } else {
            final int columns = ThisPcLayout.programColumns(width);
            final int gridRows = (programs.size() + columns - 1) / columns;
            programGrid.setColumns(columns).setVisibleRows(gridRows).setTotalRows(gridRows).setCellCount(programs.size())
                    .place(x + ThisPcLayout.programCellX(width, 0), page.contentY(cy));
            cy += gridRows * ThisPcLayout.PROG_CELL_H + 4;
        }
        page.setContentHeight(cy);
    }

    private void renderProgramCell(final GuiGraphics g, final UiContext ctx, final int index, final int cx, final int cy,
                                   final int w, final int h, final boolean hovered) {
        final List<String> programs = data.installedPrograms();
        if (index >= programs.size()) {
            return;
        }
        final String id = programs.get(index);
        final Font font = ctx.font();
        final boolean sel = ("program:" + id).equals(selectedKey);
        ProgramIcons.draw(g, cx + w / 2 - 6, cy + 2, 12, 12, programIdOf(id), skin.osPath());
        final String name = Texts.trim(font, prettyProgram(id), w - 4);
        g.drawString(font, name, cx + (w - font.width(name)) / 2, cy + 16, ctx.skin().listRowText(sel), false);
        final String pkg = Texts.trim(font, packageIdOf(id), w - 4);
        g.drawString(font, pkg, cx + (w - font.width(pkg)) / 2, cy + 24, ctx.skin().dim(), false);
    }

    private List<Component> programTooltip(final int index) {
        final List<String> programs = data.installedPrograms();
        if (index >= programs.size()) {
            return List.of();
        }
        final String id = programs.get(index);
        final List<Component> spec = MinSpecTooltip.programMinSpec(rl(id));
        final List<Component> lines = new ArrayList<>(spec.size() + 2);
        lines.add(Component.literal(prettyProgram(id)));
        lines.add(Component.literal(Component.translatable("program.jsc." + rl(id).getPath() + ".desc").getString())
                .withStyle(ChatFormatting.GRAY));
        lines.addAll(spec);
        return lines;
    }

    @Override
    public void renderTooltip(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY) {
        final List<Component> lines = root.tooltip(mouseX, mouseY);
        if (!lines.isEmpty()) {
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    private static void drawDiskIcon(final GuiGraphics g, final int x, final int y) {
        g.fill(x, y, x + 14, y + 10, 0xFF8B93A4);
        g.fill(x + 1, y + 1, x + 13, y + 9, 0xFFC7CDDA);
        g.fill(x + 2, y + 2, x + 12, y + 4, 0xFFEDF0F6);
        g.fill(x + 9, y + 6, x + 11, y + 8, 0xFF49E07A);
        Draw.outline(g, x, y, 14, 10, 0xFF5A6273);
    }

    private static void drawMediaIcon(final GuiGraphics g, final int x, final int y, final WireMedia m) {
        if (!m.loaded()) {
            g.fill(x + 2, y, x + 12, y + 10, 0xFFEEF0F4);
            Draw.outline(g, x + 2, y, 10, 10, 0xFFC2C7D4);
            return;
        }
        switch (m.drive()) {
            case "DOCK_STATION" -> {
                g.fill(x + 1, y + 1, x + 13, y + 9, 0xFF2E3238);
                g.fill(x + 9, y + 3, x + 12, y + 7, 0xFFB8BEC8);
                Draw.outline(g, x + 1, y + 1, 12, 8, 0xFF1C1F24);
            }
            case "FLOPPY_DRIVE" -> {
                g.fill(x + 1, y, x + 13, y + 10, 0xFF1C2438);
                g.fill(x + 4, y + 1, x + 10, y + 4, 0xFFB8BEC8);
                Draw.outline(g, x + 1, y, 12, 10, 0xFF0B1220);
            }
            default -> {
                g.fill(x + 2, y, x + 12, y + 10, 0xFFB9C0CE);
                g.fill(x + 5, y + 3, x + 9, y + 7, 0xFFEDF0F6);
                Draw.outline(g, x + 2, y, 10, 10, 0xFF6E7686);
            }
        }
    }

    // inspection (client tests; points are content-local, i.e. relative to window.x()+4 / window.y()+18)

    /** The index of the first drive whose medium name contains {@code nameContains}, or -1. */
    public int mediaRowIndex(final String nameContains) {
        for (int i = 0; i < data.media().size(); i++) {
            if (data.media().get(i).mediaName().contains(nameContains)) {
                return i;
            }
        }
        return -1;
    }

    public boolean isInstallable(final int mediaIndex) {
        return mediaIndex >= 0 && mediaIndex < data.media().size() && data.media().get(mediaIndex).installable();
    }

    /** Content-local centre of the "Install" button of drive {@code mediaIndex}, from the last render. */
    public int[] installButtonCenter(final int mediaIndex) {
        if (mediaIndex < 0 || mediaIndex >= data.media().size()) {
            return new int[] {0, 0};
        }
        final long pos = data.media().get(mediaIndex).readerPos();
        for (final DriveRow row : rows) {
            if (row.media != null && row.media.readerPos() == pos && row.install != null) {
                final int[] c = row.install.center();
                return new int[] {c[0] - lastX, c[1] - lastY};
            }
        }
        return new int[] {0, 0};
    }

    // input

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        if (!root.mouseClicked(mouseX, mouseY, button)) {
            selectedKey = "";
        }
    }

    @Override
    public void mouseReleased(final DesktopWindow window, final double mouseX, final double mouseY, final int button) {
        root.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double delta) {
        // The wheel anywhere in the window moves the page.
        return page.mouseScrolled(lastMouseX, lastMouseY, delta);
    }

    private void install(final long readerPos) {
        PacketDistributor.sendToServer(new InstallFromMediaPayload(host, readerPos));
        request();
        /*
         * The install lands server-side before this refresh is processed, so the desktop's
         * launchers pick the new program up immediately.
         */
        DesktopScreen.refreshActive();
    }

    private void eject(final long readerPos) {
        PacketDistributor.sendToServer(new EjectMediaPayload(host, readerPos));
        request();
    }

    private void startRename() {
        volumeRenameKey = "";
        nameField.set(data.machine().name());
        root.focus(nameField);
    }

    /** A disk or medium is relabelled through the same volume rename the explorer uses, typed in the card's field. */
    private void startVolumeRename() {
        String label = "";
        if (selectedKey.startsWith("disk:")) {
            final int slot = Integer.parseInt(selectedKey.substring(5));
            for (final WireDisk d : data.disks()) {
                if (d.slot() == slot) {
                    label = d.label();
                }
            }
        } else {
            final long pos = Long.parseLong(selectedKey.substring(6));
            for (final WireMedia m : data.media()) {
                if (m.readerPos() == pos) {
                    label = m.mediaName();
                }
            }
        }
        volumeRenameKey = selectedKey;
        nameField.set(label);
        root.focus(nameField);
    }

    private void commitRename(final String typed) {
        final String name = typed.trim();
        if (!volumeRenameKey.isEmpty()) {
            if (!name.isEmpty()) {
                PacketDistributor.sendToServer(new RenameVolumePayload(host, volumeRenameKey, name));
            }
            volumeRenameKey = "";
            request();
            return;
        }
        if (!name.equals(data.machine().name())) {
            // The machine's own name, the same setting the Settings app writes.
            PacketDistributor.sendToServer(new SetSettingPayload(host, "name", name));
            request();
        }
    }

    @Override
    public boolean charTyped(final char c) {
        return root.charTyped(c);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (root.keyPressed(key, scanCode, modifiers)) {
            return true;
        }
        if (key == GLFW.GLFW_KEY_F2) {
            // F2 renames the machine, or the selected disk or medium.
            if (selectedKey.startsWith("disk:") || selectedKey.startsWith("media:")) {
                startVolumeRename();
            } else {
                startRename();
            }
            return true;
        }
        return false;
    }

    // helpers

    private static String prettyDrive(final String drive) {
        return switch (drive) {
            case "FLOPPY_DRIVE" -> "Floppy";
            case "CD_DRIVE" -> "CD";
            case "DVD_DRIVE" -> "DVD";
            case "DOCK_STATION" -> "USB";
            default -> drive;
        };
    }

    private static ResourceLocation rl(final String id) {
        final ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed != null) {
            return parsed;
        }
        final String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return ResourceLocation.fromNamespaceAndPath("jsc", path);
    }

    private static ResourceLocation programIdOf(final String id) {
        return ResourceLocation.tryParse(id.contains(":") ? id : "jsc:" + id);
    }

    /** A program's human name, read from the single registry rather than a duplicated switch. */
    private static String prettyProgram(final String id) {
        final ProgramSpec spec = OsRegistry.getProgram(rl(id));
        return spec != null ? spec.displayName() : rl(id).getPath();
    }

    /** The id a package manager installs a program by. */
    private static String packageIdOf(final String id) {
        final ProgramSpec spec = OsRegistry.getProgram(rl(id));
        return spec != null ? spec.commandName() : rl(id).getPath();
    }

    private static String prettyOs(final String osPath) {
        final OsDef os = OsRegistry.getOs(rl(osPath));
        return os != null ? os.displayName() : osPath;
    }
}
