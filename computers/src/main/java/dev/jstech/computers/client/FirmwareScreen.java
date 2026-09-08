/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client;

import dev.jstech.computers.blockentity.AbstractComputerBlockEntity;
import dev.jstech.computers.operation.payload.FirmwareActionPayload;
import dev.jstech.computers.operation.payload.FirmwareStatePayload;
import dev.jstech.computers.operation.payload.InstallOsPayload;
import dev.jstech.computers.operation.payload.RequestFirmwareStatePayload;
import dev.jstech.computers.os.FirmwareKind;
import dev.jstech.core.tier.HardwareEra;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The computer's firmware: a real boot manager, worn in the era-correct look chosen by {@link FirmwareKind}:
 * <ul>
 *   <li>{@link FirmwareKind#CLI_BIOS}, a green monochrome phosphor POST screen with a numbered boot menu;</li>
 *   <li>{@link FirmwareKind#BLUE_BIOS}, a classic blue setup utility with Boot / Hardware / Exit tabs;</li>
 *   <li>{@link FirmwareKind#UEFI}, a modern graphical boot manager with a side navigation.</li>
 * </ul>
 * It lists every boot entry the server reports ({@link FirmwareStatePayload}): each installed disk (with or
 * without a system) and each linked drive holding a medium (an installer or live medium is bootable). The
 * player can boot an entry, install a medium's OS onto a disk, and set the preferred boot disk, so a computer
 * with two systems dual-boots by choice. Arrow keys / digits select, Enter boots, Tab switches pages, Esc exits.
 *
 * <p>The primary INSTALL action keeps the original one-click behaviour (install from any linked installer
 * medium onto the default disk, then close), which the client journey tests drive through
 * {@link #installButtonCenter()}.
 */
public class FirmwareScreen extends Screen {

    private static final int W = 340;
    private static final int H = 214;

    // Vintage: green phosphor CLI BIOS
    private static final int CLI_BG     = 0xFF021207;
    private static final int CLI_TEXT   = 0xFF35D158;
    private static final int CLI_BRIGHT = 0xFF87FFAC;
    private static final int CLI_DIM    = 0xFF1A7C39;

    // Legacy: classic blue BIOS
    private static final int BLUE_BG     = 0xFF0000A8;
    private static final int BLUE_TITLE  = 0xFFB9B9B9;
    private static final int BLUE_BORDER = 0xFF6FB7FF;
    private static final int BLUE_TEXT   = 0xFFFFFFFF;
    private static final int BLUE_VALUE  = 0xFF39D6C4;
    private static final int BLUE_AMBER  = 0xFFFFE14D;
    private static final int BLUE_DIM    = 0xFFB9C4D6;
    private static final int BLUE_SELBG  = 0xFFD9D9D9;

    // Standard: modern UEFI
    private static final int UEFI_BG     = 0xFF1E2030;
    private static final int UEFI_HEAD   = 0xFF11131F;
    private static final int UEFI_PANEL  = 0xFF2A2D3E;
    private static final int UEFI_PH     = 0xFF3A4060;
    private static final int UEFI_TEXT   = 0xFFE6ECF6;
    private static final int UEFI_KEY    = 0xFFA8B2C6;
    private static final int UEFI_ACCENT = 0xFF5FA8D3;
    private static final int UEFI_AMBER  = 0xFFF0B23A;
    private static final int UEFI_OK     = 0xFF5FE07A;
    private static final int UEFI_DIM    = 0xFF8090A8;
    private static final int UEFI_SEL    = 0xFF23405A;

    private static final int PAGE_BOOT = 0;
    private static final int PAGE_ORDER = 1;
    private static final int PAGE_HARDWARE = 2;
    /** The storage controller's own setup, present only on a machine that has one. */
    private static final int PAGE_STORAGE = 3;

    /** The array mode highlighted on the storage page, and where its rows were drawn. */
    private int storageSel;
    private int storageRowY;
    private int storageRowH = 10;
    private static final String[] PAGES = {"Boot", "Boot Order", "Hardware", "Storage"};
    private static final int ROW_H = 12;

    /** The screen currently open, so the state reply finds it. */
    private static FirmwareScreen active;

    private final BlockPos computerPos;
    private final BlockPos monitorPos;
    private final FirmwareKind kind;
    private final String machineName;

    private FirmwareStatePayload state;
    private int page = PAGE_BOOT;
    private int selected;

    // Hit boxes recomputed each frame for the active layout.
    private final List<int[]> rowHits = new ArrayList<>();
    private final int[][] tabHits = new int[PAGES.length][];
    private int[] bootHit;
    private int[] installHit;

    // A refusal shown in the footer for a few seconds, where every firmware look draws its key hints.
    private String notice = "";
    private long noticeUntil;

    public FirmwareScreen(final BlockPos computerPos, final BlockPos monitorPos, final FirmwareKind kind,
                          final String machineName) {
        super(Component.literal("Firmware Setup"));
        this.computerPos = computerPos;
        this.monitorPos = monitorPos;
        this.kind = kind;
        this.machineName = machineName;
    }

    @Override
    protected void init() {
        super.init();
        active = this;
        PacketDistributor.sendToServer(new RequestFirmwareStatePayload(computerPos));
    }

    @Override
    public void removed() {
        if (active == this) {
            active = null;
        }
        super.removed();
    }

    /** Routes the server's boot-entry state to the open firmware screen. */
    public static void accept(final FirmwareStatePayload payload) {
        if (active != null && active.computerPos.equals(payload.hostPos())) {
            active.state = payload;
            active.selected = Math.max(0, Math.min(active.selected, Math.max(0, active.rows().size() - 1)));
        }
    }

    // Model

    /** The entries shown on the current page: all of them on Boot, only disks on Boot Order. */
    private List<FirmwareStatePayload.Entry> rows() {
        if (state == null) {
            return List.of();
        }
        if (page == PAGE_ORDER) {
            final List<FirmwareStatePayload.Entry> disks = new ArrayList<>();
            for (final FirmwareStatePayload.Entry e : state.entries()) {
                if (e.kind() == FirmwareStatePayload.KIND_DISK) {
                    disks.add(e);
                }
            }
            return disks;
        }
        return state.entries();
    }

    private FirmwareStatePayload.Entry selectedEntry() {
        final List<FirmwareStatePayload.Entry> rows = rows();
        return selected >= 0 && selected < rows.size() ? rows.get(selected) : null;
    }

    private boolean hasInstaller() {
        if (state == null) {
            return false;
        }
        for (final FirmwareStatePayload.Entry e : state.entries()) {
            if (e.kind() == FirmwareStatePayload.KIND_MEDIA && e.bootable()) {
                return true;
            }
        }
        return false;
    }

    // Actions

    /** Boots the selected entry (a disk with a system, or a bootable medium), or sets the boot order on that page. */
    private void activateSelected() {
        // On the storage page Enter applies the highlighted array mode; there are no boot entries there.
        if (page == PAGE_STORAGE) {
            if (state != null && state.raid().present()
                    && (storageSel == 0 || state.raid().drives()
                            >= dev.jstech.computers.rack.RaidMode
                                    .values()[storageSel].minDrives())) {
                PacketDistributor.sendToServer(new FirmwareActionPayload(computerPos, monitorPos,
                        FirmwareActionPayload.ACTION_RAID_MODE, storageSel, -1));
            }
            return;
        }
        final FirmwareStatePayload.Entry e = selectedEntry();
        if (e == null) {
            return;
        }
        if (page == PAGE_ORDER) {
            if (e.kind() == FirmwareStatePayload.KIND_DISK) {
                PacketDistributor.sendToServer(new FirmwareActionPayload(computerPos, monitorPos,
                        FirmwareActionPayload.ACTION_SET_BOOT, e.ref(), -1));
            }
            return;
        }
        if (!e.bootable()) {
            return;
        }
        /*
         * Booting a guided installer means installing it: that is one act, and it goes through the
         * installation sequence so both routes to a new system look and feel the same. A live medium
         * (Arch, Gentoo) really does just boot, and its system is put on the disk by hand afterwards.
         */
        if (e.kind() == FirmwareStatePayload.KIND_MEDIA && e.installMode() == 0) {
            openInstaller(e.label(), e.ref());
            return;
        }
        final int action = e.kind() == FirmwareStatePayload.KIND_DISK
                ? FirmwareActionPayload.ACTION_BOOT_DISK : FirmwareActionPayload.ACTION_BOOT_MEDIA;
        PacketDistributor.sendToServer(new FirmwareActionPayload(computerPos, monitorPos, action, e.ref(), -1));
        onClose();
    }

    /**
     * The primary install action: both the selected-medium route and the "install from whatever is
     * linked" route hand over to the installation sequence.
     */
    private void install() {
        final FirmwareStatePayload.Entry e = selectedEntry();
        if (e != null && e.kind() == FirmwareStatePayload.KIND_MEDIA && page == PAGE_BOOT) {
            if (!e.bootable()) {
                /*
                 * A medium this machine cannot install (a system newer than its era, or no installer on
                 * it) is refused here, in words. Falling through to "whatever is linked" used to open the
                 * installer anyway, which then played a write the server refused without saying so.
                 */
                notice(e.installMode() < 0 ? "There is no installer on that medium."
                        : systemNameOf(e.label()) + " needs " + reasonOf(e.detail()) + " hardware.");
                return;
            }
            /*
             * A live/source medium (Arch, Gentoo) has no one-click install: "installing" it means booting
             * its shell and putting the system on the disk by hand, so the action boots the medium.
             */
            if (e.installMode() != 0) {
                activateSelected();
                return;
            }
            openInstaller(e.label(), e.ref());
            return;
        }
        // The primary action with a disk or nothing selected: install from whatever linked medium fits.
        if (!hasInstaller()) {
            notice("No installable system in a linked drive.");
            return;
        }
        openInstaller("", -1L);
    }

    private void notice(final String text) {
        notice = text;
        noticeUntil = System.currentTimeMillis() + 5000L;
    }

    /** "Frames 95 installer" / "Arch (live)" as the row shows it, down to the system's own name. */
    private static String systemNameOf(final String label) {
        return label.replace(" installer", "").replace(" (live)", "");
    }

    /** The "why not" the server appends to a medium's detail after a dash, or the whole detail. */
    private static String reasonOf(final String detail) {
        final int dash = detail.indexOf(" - ");
        return dash < 0 ? detail : detail.substring(dash + 3);
    }

    /**
     * Hands over to the installation sequence. Writing the system is that screen's job, so the
     * player watches it happen instead of the firmware closing and the system appearing later.
     */
    private void openInstaller(final String osLabel, final long readerRef) {
        final int target = state == null ? -1 : state.installTargetSlot();
        final String targetLabel = target < 0 ? "the default disk" : "Disk " + target;
        minecraft.setScreen(new OsInstallScreen(computerPos, monitorPos, kind, osLabel, targetLabel,
                target, readerRef));
    }

    /** Screen centre of the primary install action drawn on the last frame (client tests click it). */
    public int[] installButtonCenter() {
        return installHit == null ? new int[]{width / 2, height / 2}
                : new int[]{installHit[0] + installHit[2] / 2, installHit[1] + installHit[3] / 2};
    }

    // Render

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        rowHits.clear();
        bootHit = null;
        installHit = null;
        final int x = (width - W) / 2;
        final int y = (height - H) / 2;
        MonitorFrame.renderBody(g, x, y, W, H, era(), font);
        switch (kind) {
            case CLI_BIOS  -> renderCliBios(g, x, y, mouseX, mouseY);
            case BLUE_BIOS -> renderBlueBios(g, x, y, mouseX, mouseY);
            case UEFI      -> renderUefi(g, x, y, mouseX, mouseY);
        }
    }

    private HardwareEra era() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(computerPos)
                instanceof dev.jstech.computers.os.IOsHost host) {
            return host.displayEra();
        }
        return switch (kind) {
            case CLI_BIOS -> HardwareEra.VINTAGE;
            case BLUE_BIOS -> HardwareEra.LEGACY;
            case UEFI -> HardwareEra.STANDARD;
        };
    }

    private static boolean in(final int[] r, final double mx, final double my) {
        return r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    private String eraLabel() {
        return state == null ? "-" : switch (HardwareEra.values()[Math.min(state.eraOrdinal(),
                HardwareEra.values().length - 1)]) {
            case VINTAGE -> "Vintage";
            case LEGACY -> "Legacy";
            case STANDARD -> "Standard";
            case ADVANCED -> "Advanced";
            case EXA -> "Exa";
            case SINGULARITY -> "Singularity";
        };
    }

    // Vintage: green phosphor CLI BIOS

    private void renderCliBios(final GuiGraphics g, final int x, final int y, final int mouseX, final int mouseY) {
        g.fill(x, y, x + W, y + H, CLI_BG);
        border(g, x, y, CLI_DIM);
        final int tx = x + 14;
        int ty = y + 10;
        final String title = dev.jstech.computers.os.Branding.biosBanner(era());
        g.drawString(font, title, tx, ty, CLI_BRIGHT, false);
        // The machine's name takes what is left of the line, cut short rather than drawn over the title.
        final int nameRoom = x + W - 14 - (tx + font.width(title) + 10);
        final String name = font.width(machineName) > nameRoom ? trimTo(machineName, nameRoom) : machineName;
        g.drawString(font, name, x + W - font.width(name) - 14, ty, CLI_DIM, false);
        ty += 11;
        // Page "tabs" as a bracketed menu line.
        int px = tx;
        for (int i = 0; i < PAGES.length; i++) {
            final String label = (i == page ? "[" : " ") + PAGES[i] + (i == page ? "]" : " ");
            tabHits[i] = new int[]{px, ty, font.width(label), 10};
            g.drawString(font, label, px, ty, i == page ? CLI_BRIGHT : CLI_DIM, false);
            px += font.width(label) + 8;
        }
        ty += 14;
        if (page == PAGE_HARDWARE) {
            renderHardwareLines(g, tx, ty, 11, CLI_TEXT, CLI_BRIGHT, CLI_DIM);
        } else if (page == PAGE_STORAGE) {
            renderStorageLines(g, tx, ty, 11, CLI_TEXT, CLI_BRIGHT, CLI_DIM, W - 28);
        } else {
            g.drawString(font, page == PAGE_ORDER ? "Boot Device Priority:" : "Boot Menu:", tx, ty, CLI_TEXT, false);
            ty += 12;
            final List<FirmwareStatePayload.Entry> rows = rows();
            if (state == null) {
                g.drawString(font, "  Detecting drives ...", tx, ty, CLI_DIM, false);
            } else if (rows.isEmpty()) {
                g.drawString(font, "  No bootable device found.", tx, ty, CLI_DIM, false);
            }
            for (int i = 0; i < rows.size(); i++) {
                final FirmwareStatePayload.Entry e = rows.get(i);
                final boolean sel = i == selected;
                final String mark = page == PAGE_ORDER && state.bootSlot() == e.ref()
                        && e.kind() == FirmwareStatePayload.KIND_DISK ? "*" : " ";
                final String line = (sel ? ">" : " ") + mark + (i + 1) + ". " + e.label() + "  (" + e.detail() + ")";
                rowHits.add(new int[]{tx, ty, W - 28, ROW_H});
                g.drawString(font, line, tx, ty, e.bootable() || page == PAGE_ORDER ? (sel ? CLI_BRIGHT : CLI_TEXT) : CLI_DIM, false);
                ty += ROW_H;
            }
            ty += 4;
            final String boot = page == PAGE_ORDER ? " SET FIRST " : " BOOT ";
            bootHit = new int[]{tx, ty, font.width(boot) + 4, 13};
            g.fill(bootHit[0], bootHit[1], bootHit[0] + bootHit[2], bootHit[1] + bootHit[3],
                    in(bootHit, mouseX, mouseY) ? CLI_TEXT : CLI_BRIGHT);
            g.drawString(font, boot, bootHit[0] + 2, bootHit[1] + 3, CLI_BG, false);
            final String inst = " INSTALL OS ";
            installHit = new int[]{bootHit[0] + bootHit[2] + 8, ty, font.width(inst) + 4, 13};
            g.fill(installHit[0], installHit[1], installHit[0] + installHit[2], installHit[1] + installHit[3],
                    in(installHit, mouseX, mouseY) ? CLI_TEXT : (hasInstaller() ? CLI_BRIGHT : CLI_DIM));
            g.drawString(font, inst, installHit[0] + 2, installHit[1] + 3, CLI_BG, false);
        }
        g.drawString(font, hintText(), tx, y + H - 14, CLI_DIM, false);
    }

    // Legacy: classic blue BIOS setup utility

    private void renderBlueBios(final GuiGraphics g, final int x, final int y, final int mouseX, final int mouseY) {
        g.fill(x, y, x + W, y + H, BLUE_BG);
        border(g, x, y, BLUE_BORDER);
        g.fill(x, y, x + W, y + 14, BLUE_TITLE);
        drawCentered(g, "J's Computers BIOS Setup Utility", x + W / 2, y + 3, BLUE_BG);
        int px = x + 8;
        for (int i = 0; i < PAGES.length; i++) {
            tabHits[i] = new int[]{px - 3, y + 16, font.width(PAGES[i]) + 6, 12};
            if (i == page) {
                g.fill(tabHits[i][0], tabHits[i][1], tabHits[i][0] + tabHits[i][2], tabHits[i][1] + tabHits[i][3], BLUE_TITLE);
            }
            g.drawString(font, PAGES[i], px, y + 18, i == page ? BLUE_BG : BLUE_DIM, false);
            px += font.width(PAGES[i]) + 14;
        }
        g.drawString(font, "Exit", x + W - font.width("Exit") - 8, y + 18, BLUE_DIM, false);
        g.fill(x, y + 28, x + W, y + 29, BLUE_BORDER);

        final int top = y + 34;
        final int boxW = 206;
        final int boxX = x + 8;
        final int helpX = boxX + boxW + 8;
        final int helpW = W - boxW - 24;
        final int boxH = H - (top - y) - 24;
        final String help;
        if (page == PAGE_HARDWARE) {
            drawBox(g, boxX, top, boxW, boxH, "System Information");
            renderHardwareLines(g, boxX + 8, top + 18, 13, BLUE_TEXT, BLUE_VALUE, BLUE_DIM);
            help = "The hardware this firmware detected at power-on.";
        } else if (page == PAGE_STORAGE) {
            drawBox(g, boxX, top, boxW, boxH, "Storage Controller");
            renderStorageLines(g, boxX + 8, top + 18, 13, BLUE_TEXT, BLUE_VALUE, BLUE_DIM, boxW - 16);
            help = "Up/Down chooses an array mode, Enter applies it.";
        } else {
            drawBox(g, boxX, top, boxW, boxH, page == PAGE_ORDER ? "Boot Device Priority" : "Boot Menu");
            int ry = top + 18;
            final List<FirmwareStatePayload.Entry> rows = rows();
            if (state == null) {
                g.drawString(font, "Detecting devices ...", boxX + 8, ry, BLUE_DIM, false);
                ry += ROW_H; // the message occupies a row; the actions below must not land on it
            } else if (rows.isEmpty()) {
                g.drawString(font, "No bootable device", boxX + 8, ry, BLUE_AMBER, false);
                ry += ROW_H;
            }
            for (int i = 0; i < rows.size(); i++) {
                final FirmwareStatePayload.Entry e = rows.get(i);
                final boolean sel = i == selected;
                rowHits.add(new int[]{boxX + 2, ry - 2, boxW - 4, ROW_H});
                if (sel) {
                    g.fill(boxX + 2, ry - 2, boxX + boxW - 2, ry + ROW_H - 2, BLUE_SELBG);
                }
                final String order = page == PAGE_ORDER
                        ? (state.bootSlot() == e.ref() && e.kind() == FirmwareStatePayload.KIND_DISK ? "1st " : "    ")
                        : "";
                g.drawString(font, order + e.label(), boxX + 8, ry, sel ? BLUE_BG : (e.bootable() ? BLUE_TEXT : BLUE_DIM), false);
                final String right = e.kind() == FirmwareStatePayload.KIND_DISK ? "Disk " + e.ref() : "Drive";
                g.drawString(font, right, boxX + boxW - font.width(right) - 8, ry, sel ? BLUE_BG : BLUE_VALUE, false);
                ry += ROW_H;
            }
            ry += 6;
            final String bootLabel = page == PAGE_ORDER ? "> Set as First Boot Device" : "> Boot Selected Device";
            bootHit = new int[]{boxX + 2, ry - 3, boxW - 4, 13};
            if (in(bootHit, mouseX, mouseY)) {
                g.fill(bootHit[0], bootHit[1], bootHit[0] + bootHit[2], bootHit[1] + bootHit[3], BLUE_SELBG);
            }
            g.drawString(font, bootLabel, bootHit[0] + 6, bootHit[1] + 3, in(bootHit, mouseX, mouseY) ? BLUE_BG : BLUE_AMBER, false);
            ry += 13;
            installHit = new int[]{boxX + 2, ry - 3, boxW - 4, 13};
            if (in(installHit, mouseX, mouseY)) {
                g.fill(installHit[0], installHit[1], installHit[0] + installHit[2], installHit[1] + installHit[3], BLUE_SELBG);
            }
            g.drawString(font, "> Install Operating System", installHit[0] + 6, installHit[1] + 3,
                    in(installHit, mouseX, mouseY) ? BLUE_BG : (hasInstaller() ? BLUE_AMBER : BLUE_DIM), false);
            help = page == PAGE_ORDER
                    ? "Select the disk that boots first. The choice is saved, so two installed systems dual-boot."
                    : "Select a device and press Enter to boot it. Installer media in a linked drive install onto disk "
                    + (state == null ? "-" : Integer.toString(state.installTargetSlot())) + ".";
        }
        drawBox(g, helpX, top, helpW, boxH, "Item Help");
        drawWrapped(g, help, helpX + 6, top + 18, helpW - 12, BLUE_DIM);
        g.fill(x, y + H - 16, x + W, y + H, BLUE_TITLE);
        g.drawString(font, hintText(), x + 8, y + H - 12, BLUE_BG, false);
    }

    private void drawBox(final GuiGraphics g, final int x, final int y, final int w, final int h, final String header) {
        g.fill(x, y, x + w, y + 1, BLUE_BORDER);
        g.fill(x, y + h - 1, x + w, y + h, BLUE_BORDER);
        g.fill(x, y, x + 1, y + h, BLUE_BORDER);
        g.fill(x + w - 1, y, x + w, y + h, BLUE_BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + 12, BLUE_BORDER);
        g.drawString(font, header, x + 6, y + 3, BLUE_BG, false);
    }

    // Standard: modern UEFI boot manager

    private void renderUefi(final GuiGraphics g, final int x, final int y, final int mouseX, final int mouseY) {
        g.fill(x, y, x + W, y + H, UEFI_BG);
        border(g, x, y, UEFI_PH);
        g.fill(x, y, x + W, y + 20, UEFI_HEAD);
        g.fill(x, y + 20, x + W, y + 22, UEFI_ACCENT);
        g.drawString(font, "J's Computers", x + 10, y + 6, UEFI_TEXT, false);
        g.drawString(font, "UEFI", x + W - font.width("UEFI") - 10, y + 6, UEFI_DIM, false);

        final int top = y + 30;
        final int navX = x + 10;
        final int navW = 92;
        final int mainX = navX + navW + 8;
        final int mainW = W - navW - 28;
        final int panelH = H - (top - y) - 24;
        g.fill(navX, top, navX + navW, top + panelH, UEFI_HEAD);
        int ny = top + 6;
        final String[] navLabels = {"Boot Manager", "Boot Order", "Hardware"};
        for (int i = 0; i < navLabels.length; i++) {
            tabHits[i] = new int[]{navX, ny - 2, navW, 14};
            if (i == page) {
                g.fill(navX, ny - 2, navX + navW, ny + 12, UEFI_ACCENT);
            }
            g.drawString(font, navLabels[i], navX + 8, ny + 1, i == page ? 0xFF0B1018 : UEFI_KEY, false);
            ny += 16;
        }
        g.drawString(font, "Exit", navX + 8, top + panelH - 12, UEFI_DIM, false);

        if (page == PAGE_HARDWARE) {
            panel(g, mainX, top, mainW, panelH, "System Information");
            renderHardwareLines(g, mainX + 8, top + 22, 15, UEFI_KEY, UEFI_TEXT, UEFI_DIM);
        } else if (page == PAGE_STORAGE) {
            panel(g, mainX, top, mainW, panelH, "Storage Controller");
            renderStorageLines(g, mainX + 8, top + 22, 15, UEFI_KEY, UEFI_TEXT, UEFI_DIM, mainW - 16);
        } else {
            panel(g, mainX, top, mainW, panelH, page == PAGE_ORDER ? "Boot Order" : "Boot Manager");
            int ry = top + 20;
            final List<FirmwareStatePayload.Entry> rows = rows();
            if (state == null) {
                g.drawString(font, "Scanning devices ...", mainX + 8, ry + 2, UEFI_DIM, false);
            } else if (rows.isEmpty()) {
                g.drawString(font, "No bootable device", mainX + 8, ry + 2, UEFI_AMBER, false);
            }
            /*
             * The action buttons sit at a fixed height at the foot of the panel, so the list has to stop
             * above them. Drawing past this point put BOOT and INSTALL TO DISK on top of real rows.
             */
            final int listBottom = top + panelH - 26 - 4;
            for (int i = 0; i < rows.size(); i++) {
                if (ry + ROW_H + 2 > listBottom) {
                    // Say that the list is cut off; a device silently missing reads as a missing device.
                    final String more = "+" + (rows.size() - i) + " more";
                    g.drawString(font, more, mainX + 20, listBottom - 9, UEFI_DIM, false);
                    break;
                }
                final FirmwareStatePayload.Entry e = rows.get(i);
                final boolean sel = i == selected;
                rowHits.add(new int[]{mainX + 4, ry, mainW - 8, ROW_H + 2});
                g.fill(mainX + 4, ry, mainX + mainW - 4, ry + ROW_H + 2, sel ? UEFI_SEL : UEFI_PH);
                if (sel) {
                    g.fill(mainX + 4, ry, mainX + 6, ry + ROW_H + 2, UEFI_ACCENT);
                }
                final int dot = e.kind() == FirmwareStatePayload.KIND_DISK ? (e.bootable() ? UEFI_OK : UEFI_DIM)
                        : (e.bootable() ? UEFI_AMBER : UEFI_DIM);
                g.fill(mainX + 10, ry + 4, mainX + 15, ry + 9, dot);
                final String first = page == PAGE_ORDER && state.bootSlot() == e.ref()
                        && e.kind() == FirmwareStatePayload.KIND_DISK ? "  [first]" : "";
                g.drawString(font, e.label() + first, mainX + 20, ry + 3, e.bootable() || page == PAGE_ORDER ? UEFI_TEXT : UEFI_DIM, false);
                final String detail = e.detail();
                final String shown = font.width(detail) > mainW - 130 ? trimTo(detail, mainW - 130) : detail;
                g.drawString(font, shown, mainX + mainW - font.width(shown) - 8, ry + 3, UEFI_DIM, false);
                ry += ROW_H + 4;
            }
            final int by = top + panelH - 26;
            final String bootLabel = page == PAGE_ORDER ? "SET FIRST" : "BOOT";
            bootHit = new int[]{mainX + 8, by, 64, 18};
            g.fill(bootHit[0], bootHit[1], bootHit[0] + bootHit[2], bootHit[1] + bootHit[3],
                    in(bootHit, mouseX, mouseY) ? UEFI_TEXT : UEFI_ACCENT);
            drawCentered(g, bootLabel, bootHit[0] + bootHit[2] / 2, by + 5, 0xFF0B1018);
            installHit = new int[]{mainX + 80, by, 100, 18};
            g.fill(installHit[0], installHit[1], installHit[0] + installHit[2], installHit[1] + installHit[3],
                    in(installHit, mouseX, mouseY) ? UEFI_TEXT : (hasInstaller() ? UEFI_ACCENT : UEFI_PH));
            drawCentered(g, "INSTALL TO DISK", installHit[0] + installHit[2] / 2, by + 5,
                    hasInstaller() || in(installHit, mouseX, mouseY) ? 0xFF0B1018 : UEFI_DIM);
        }
        g.fill(x, y + H - 16, x + W, y + H, UEFI_HEAD);
        final String hint = hintText();
        g.drawString(font, hint, x + W - font.width(hint) - 10, y + H - 12, UEFI_DIM, false);
    }

    private void panel(final GuiGraphics g, final int x, final int y, final int w, final int h, final String header) {
        g.fill(x, y, x + w, y + h, UEFI_PANEL);
        g.fill(x, y, x + w, y + 14, UEFI_PH);
        g.fill(x, y, x + 3, y + 14, UEFI_ACCENT);
        g.drawString(font, header, x + 7, y + 3, UEFI_TEXT, false);
    }

    // Shared helpers

    /** The hardware page's key/value lines in the caller's palette. */
    private void renderHardwareLines(final GuiGraphics g, final int x, final int y, final int lh,
                                     final int keyColor, final int valueColor, final int dimColor) {
        int ty = y;
        final String cpu = state == null ? "detecting ..." : state.cpuLabel();
        final String ram = state == null ? "detecting ..." : state.ramMb() + " it";
        final String[][] kv = {
                {"Processor", cpu},
                {"Memory", ram},
                {"Hardware Era", eraLabel()},
                {"Monitor", "connected"},
                {"Boot Disk", state == null || state.bootSlot() < 0 ? "automatic" : "Disk " + state.bootSlot()},
                {"Install Target", state == null || state.installTargetSlot() < 0 ? "no disk" : "Disk " + state.installTargetSlot()},
        };
        for (final String[] pair : kv) {
            g.drawString(font, pair[0], x, ty, keyColor, false);
            g.drawString(font, pair[1], x + 110, ty, pair[1].startsWith("detecting") ? dimColor : valueColor, false);
            ty += lh;
        }
    }

    /**
     * The storage controller page: the array this machine's bay runs, and what each mode would give.
     * Drawn in whichever era palette the caller passes, exactly like the hardware page, so all three
     * firmware looks get it without three copies of the logic.
     */
    private void renderStorageLines(final GuiGraphics g, final int x, final int y, final int lh,
                                    final int keyColor, final int valueColor, final int dimColor,
                                    final int maxWidth) {
        final FirmwareStatePayload.RaidInfo raid = state == null ? null : state.raid();
        int ty = y;
        if (raid == null || !raid.present()) {
            g.drawString(font, "No storage controller fitted.", x, ty, dimColor, false);
            /*
             * Wrapped to the panel: this sentence is longer than the box, and drawn as one line it ran
             * straight through the border and over the help panel beside it.
             */
            ty += lh;
            for (final net.minecraft.util.FormattedCharSequence line : font.split(
                    net.minecraft.network.chat.Component.literal(
                            "Mount a RAID Controller in this machine's gadget bay."), maxWidth)) {
                g.drawString(font, line, x, ty, dimColor, false);
                ty += lh;
            }
            return;
        }
        final String health = raid.members() == 0 ? "unconfigured"
                : raid.drives() < raid.members() ? "degraded" : "healthy";
        g.drawString(font, "Controller", x, ty, keyColor, false);
        g.drawString(font, "RAID Controller", x + 110, ty, valueColor, false);
        ty += lh;
        g.drawString(font, "Member drives", x, ty, keyColor, false);
        g.drawString(font, raid.drives() + (raid.members() > 0 ? " of " + raid.members() : ""),
                x + 110, ty, valueColor, false);
        ty += lh;
        g.drawString(font, "Array state", x, ty, keyColor, false);
        g.drawString(font, health, x + 110, ty, "degraded".equals(health) ? 0xFFF0B23A : valueColor, false);
        ty += lh + 4;
        g.drawString(font, "ARRAY MODE", x, ty, dimColor, false);
        ty += lh;
        storageRowY = ty;
        storageRowH = lh;
        final var modes = dev.jstech.computers.rack.RaidMode.values();
        for (int i = 0; i < modes.length; i++) {
            final var mode = modes[i];
            final boolean current = i == raid.mode();
            final boolean usable = i == 0 || raid.drives() >= mode.minDrives();
            final long capacity = i < raid.capacities().size() ? raid.capacities().get(i) : 0L;
            final String label = (storageSel == i ? "> " : "  ")
                    + (i == 0 ? "NONE (independent)" : mode.name());
            final String detail = !usable ? "needs " + mode.minDrives() + " drives"
                    : capacity + " items" + (i == 1 ? "  +25% throughput"
                            : i == 2 ? "  survives to 1 drive"
                                    : i == 3 ? "  survives 1 loss" : "");
            g.drawString(font, label, x, ty, current ? 0xFF39D6C4 : usable ? valueColor : dimColor, false);
            g.drawString(font, detail, x + 130, ty, dimColor, false);
            ty += lh;
        }
        g.drawString(font, "Enter applies the mode. Changing it erases the array.", x, ty + 4,
                dimColor, false);
    }

    private String trimTo(final String s, final int maxW) {
        String out = s;
        while (out.length() > 3 && font.width(out + "..") > maxW) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "..";
    }

    private void border(final GuiGraphics g, final int x, final int y, final int color) {
        g.fill(x - 1, y - 1, x + W + 1, y, color);
        g.fill(x - 1, y + H, x + W + 1, y + H + 1, color);
        g.fill(x - 1, y, x, y + H, color);
        g.fill(x + W, y, x + W + 1, y + H, color);
    }

    private void drawCentered(final GuiGraphics g, final String s, final int cx, final int y, final int color) {
        g.drawString(font, s, cx - font.width(s) / 2, y, color, false);
    }

    private void drawWrapped(final GuiGraphics g, final String text, final int x, final int y,
                             final int maxW, final int color) {
        final StringBuilder lineBuf = new StringBuilder();
        int ly = y;
        for (final String word : text.split(" ")) {
            final String trial = lineBuf.isEmpty() ? word : lineBuf + " " + word;
            if (font.width(trial) > maxW && !lineBuf.isEmpty()) {
                g.drawString(font, lineBuf.toString(), x, ly, color, false);
                ly += 10;
                lineBuf.setLength(0);
                lineBuf.append(word);
            } else {
                lineBuf.setLength(0);
                lineBuf.append(trial);
            }
        }
        if (!lineBuf.isEmpty()) {
            g.drawString(font, lineBuf.toString(), x, ly, color, false);
        }
    }

    // Input

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        for (int i = 0; i < tabHits.length; i++) {
            if (in(tabHits[i], mouseX, mouseY)) {
                page = i;
                selected = 0;
                return true;
            }
        }
        for (int i = 0; i < rowHits.size(); i++) {
            if (in(rowHits.get(i), mouseX, mouseY)) {
                if (selected == i) {
                    activateSelected(); // a second click on the selected row activates it
                } else {
                    selected = i;
                }
                return true;
            }
        }
        if (in(bootHit, mouseX, mouseY)) {
            activateSelected();
            return true;
        }
        if (in(installHit, mouseX, mouseY)) {
            install();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(final int key, final int scan, final int mods) {
        switch (key) {
            case 256 -> { // ESC
                onClose();
                return true;
            }
            case 258 -> { // Tab
                page = (page + 1) % PAGES.length;
                selected = 0;
                confirmFormatRef = Long.MIN_VALUE;
                return true;
            }
            case 265 -> { // Up
                if (page == PAGE_STORAGE) {
                    storageSel = Math.max(0, storageSel - 1);
                } else {
                    selected = Math.max(0, selected - 1);
                }
                confirmFormatRef = Long.MIN_VALUE;
                return true;
            }
            case 264 -> { // Down
                if (page == PAGE_STORAGE) {
                    storageSel = Math.min(
                            dev.jstech.computers.rack.RaidMode.values().length - 1,
                            storageSel + 1);
                } else {
                    selected = Math.min(Math.max(0, rows().size() - 1), selected + 1);
                }
                confirmFormatRef = Long.MIN_VALUE;
                return true;
            }
            case 257, 335 -> { // Enter
                activateSelected();
                return true;
            }
            case 70 -> { // F: format the selected disk (pressed twice, the first press only arms it)
                formatSelected();
                return true;
            }
            default -> {
                if (key >= 49 && key <= 57) { // digits 1-9 select a row
                    final int idx = key - 49;
                    if (idx < rows().size()) {
                        selected = idx;
                    }
                    return true;
                }
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    // Formatting asks twice: the first F arms the confirmation on that disk, the second one erases it.
    private long confirmFormatRef = Long.MIN_VALUE;

    private void formatSelected() {
        final FirmwareStatePayload.Entry e = selectedEntry();
        if (e == null || e.kind() != FirmwareStatePayload.KIND_DISK || page == PAGE_HARDWARE) {
            return;
        }
        if (confirmFormatRef != e.ref()) {
            confirmFormatRef = e.ref();
            return;
        }
        confirmFormatRef = Long.MIN_VALUE;
        PacketDistributor.sendToServer(new FirmwareActionPayload(computerPos, monitorPos,
                FirmwareActionPayload.ACTION_FORMAT, e.ref(), -1));
    }

    /** The bottom-row hint, warning while a format confirmation is armed on the selected disk. */
    private String hintText() {
        if (System.currentTimeMillis() < noticeUntil) {
            return notice;
        }
        final FirmwareStatePayload.Entry e = selectedEntry();
        if (e != null && e.kind() == FirmwareStatePayload.KIND_DISK && confirmFormatRef == e.ref()) {
            return "F again: FORMAT DISK (erases everything)   ESC: Exit";
        }
        return "Enter: Boot   Tab: Page   F: Format   ESC: Exit";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
