/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.os;

import dev.jsc.jscomputronics.common.gui.layout.DesktopZ;
import dev.jsc.jscomputronics.common.tier.HardwareEra;
import dev.jsc.jscomputronics.module.computing.blockentity.AbstractComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.client.MonitorFrame;
import dev.jsc.jscomputronics.module.computing.client.theme.MonitorFrameStyle;
import dev.jsc.jscomputronics.module.computing.gui.layout.DesktopIconLayout;
import dev.jsc.jscomputronics.module.computing.menu.DesktopMenu;
import dev.jsc.jscomputronics.module.computing.operation.payload.DeleteFilePayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.DesktopFilesPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.DiskFilesPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.MkdirPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.MoveFilePayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RenameFilePayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RequestDesktopFilesPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RequestFileContentPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.SaveFilePayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.SetDesktopPrefsPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.SetIconPositionPayload;
import dev.jsc.jscomputronics.module.computing.os.fs.SystemLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The FULL_DESKTOP shell: a windowed desktop environment that a graphical OS (Panes 95 / XP / 11)
 * boots into. It renders inside a centred window (the monitor's screen) over the dimmed game, never
 * full-screen. The visual chrome is chosen per OS id; this is the shared window-manager engine all
 * three desktop OSes drive (one engine, distinct chrome + program gating).
 *
 * <p>First iteration: wallpaper, launcher icons, a taskbar with a start button and clock, a start
 * menu, and stackable program windows with a draggable title bar and a close box. Program content is
 * delegated to {@link DesktopApp} instances. Visual polish is tuned in-game.
 */
public final class DesktopScreen extends AbstractContainerScreen<DesktopMenu> {

    private final BlockPos host;
    private final BlockPos monitorPos;
    private final ResourceLocation osId;
    private final DesktopTheme theme;
    private final OsSkin skin;
    private final List<DesktopWindow> windows = new ArrayList<>();
    private final List<Launcher> launchers = new ArrayList<>();
    private final List<String> installedPrograms = new ArrayList<>();

    /**
     * Open windows kept per-computer across leaving and re-entering the Monitor in the same session.
     * Bounded (access-ordered, eldest evicted past the cap) so a long session that visits many computers
     * does not grow this map without limit.
     */
    private static final int MAX_SAVED_DESKTOPS = 16;
    private static final java.util.Map<BlockPos, java.util.List<SavedWin>> SAVED_WINDOWS =
            new java.util.LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        final java.util.Map.Entry<BlockPos, java.util.List<SavedWin>> eldest) {
                    return size() > MAX_SAVED_DESKTOPS;
                }
            };

    private record SavedWin(String key, boolean minimized, boolean maximized) {
    }

    /** Apps a running window asked to launch (e.g. Files opening the Editor); drained by the active desktop. */
    private static final java.util.List<String> PENDING_OPEN = new java.util.ArrayList<>();

    /** Lets a running app request another program be opened on the desktop. */
    public static void requestOpen(final String key) {
        PENDING_OPEN.add(key);
    }

    /**
     * Raises the modal {@code .dat}-locked error dialog on the active desktop. Called from desktop
     * apps (e.g. the Files explorer) that detect a refused {@code .dat} action and need to surface
     * it; a no-op when no desktop is showing.
     */
    public static void showDatLockedError() {
        if (active != null) {
            active.showError("Error", DAT_LOCKED_MESSAGE);
        }
    }

    /** Opens a modal error dialog with the given title and message over this desktop. */
    void showError(final String title, final String message) {
        this.popup = new DesktopPopup(title, message, this.font);
    }

    private boolean startOpen;
    private int selectedIcon = -1;
    private long iconClickAt;
    @org.jetbrains.annotations.Nullable
    private DesktopWindow dragging;
    @org.jetbrains.annotations.Nullable
    private DesktopWindow resizing;
    // The window whose title-bar button is currently held down (pushed-in until release).
    @org.jetbrains.annotations.Nullable
    private DesktopWindow pressedBtnWindow;
    private int dragOffsetX;
    private int dragOffsetY;

    /** The currently shown desktop is the one that receives desktop-folder listing replies. */
    @org.jetbrains.annotations.Nullable
    private static DesktopScreen active;

    /**
     * The exact message shown whenever a player tries to copy, create, delete, rename, or otherwise
     * modify a {@code .dat} file by hand. A {@code .dat} is a read-only projection of the computer's
     * stored items, so the only sanctioned way to move those items is the Network Interactor.
     */
    static final String DAT_LOCKED_MESSAGE =
            "This file is impossible to modify, create, delete or change manually, "
                    + "use the network interactor for it.";

    /** The modal error dialog currently shown over the desktop, or {@code null} when none. */
    @org.jetbrains.annotations.Nullable
    private DesktopPopup popup;

    /** Files and folders living in the desktop folder ({@link SystemLayout#DESKTOP_DIR}), drawn as icons. */
    private final List<DiskFilesPayload.WireFile> desktopItems = new ArrayList<>();

    /**
     * Free-positioned desktop icons: the packed grid cell ({@code col << 16 | row}) each pinned icon was
     * dropped on, keyed by its stable id ({@code app:<label>} for a launcher, {@code file:<name>} for a
     * file or folder). Synced from the server and persisted on the computer, so a desktop reopened after a
     * reload shows every icon exactly where the player left it. An icon with no entry flows into the next
     * free auto-layout cell, so a fresh desktop looks just like it did before icons could be moved.
     */
    private final java.util.Map<String, Integer> iconCells = new java.util.HashMap<>();

    /** The player's chosen wallpaper style ({@code ""} = OS default) and computer name, synced from the server. */
    private String desktopWallpaper = "";
    private String computerName = "";

    // Desktop right-click context menu.
    private boolean deskCtxOpen;
    private int deskCtxX;
    private int deskCtxY;
    private int deskCtxItem = -1; // index into desktopItems, or -1 for the empty background

    // Drag-and-drop of a desktop icon (a file/folder, or a program launcher) — onto a folder, an open
    // explorer, or a free grid cell.
    private int deskDragSlot = -1; // global icon slot being dragged, or -1
    private boolean deskDragging;
    private double deskDragX;
    private double deskDragY;
    private double deskDragStartX;
    private double deskDragStartY;
    /** How far the cursor must travel from the press point before an icon click becomes a drag. */
    private static final double DRAG_THRESHOLD = 3.0;

    // Inline rename of a desktop icon.
    private int deskRenaming = -1; // index into desktopItems, or -1
    private final StringBuilder deskRenameBuf = new StringBuilder();
    private String deskRenameExt = "";
    @org.jetbrains.annotations.Nullable
    private String deskPendingRename; // enter rename on this name once the next listing arrives

    private static final int TASKBAR_H = 24;
    // Windows 11 taskbar: each centered item (Start + one per open window) occupies this slot.
    private static final int WIN11_SLOT = 22;
    private static final int WIN11_ICON = 16;
    private static final int MENU_W = 130;
    private static final int BAND_W = 22;
    private static final int MENU_ITEM_H = 18;
    private static final int ICON_PITCH_Y = 42;
    private static final int ICON_PITCH_X = 46;
    private static final String[] DESK_CTX_ICON = {"Open", "Rename", "Delete"};
    private static final String[] DESK_CTX_BG = {"New File", "New Folder", "Personalize", "Refresh"};
    private static final int DESK_CTX_W = 88;
    private static final int DESK_CTX_ITEM_H = 11;

    /** A desktop/start-menu entry that opens an app when clicked. */
    // A launcher either opens a built-in app window (factory) or runs a custom action (e.g. open the
    // NMS, which is a server-side menu rather than a desktop window). Exactly one is non-null.
    private record Launcher(String label, java.util.function.Supplier<DesktopApp> factory, Runnable action) {
        Launcher(final String label, final java.util.function.Supplier<DesktopApp> factory) {
            this(label, factory, null);
        }
    }

    public DesktopScreen(final DesktopMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
        this.host = menu.hostPos();
        this.monitorPos = menu.monitorPos();
        this.osId = menu.osId();
        this.theme = DesktopTheme.forOs(osId);
        this.skin = OsSkin.forOs(osId);
    }

    // The on-screen monitor "screen" rectangle: a centred window, not the whole game viewport. The extra slack
    // (vs the raw viewport) leaves room for the monitor frame drawn around the glass and its chin below it.
    private int sw() {
        return Math.min(width - 44, 384);
    }

    private int sh() {
        return Math.min(height - 60, 256);
    }

    private int ox() {
        return (width - sw()) / 2;
    }

    private int oy() {
        return (height - sh()) / 2;
    }

    // --- inspection (client tests drive the desktop through the same hit areas the player clicks) ---

    public boolean isStartOpen() {
        return startOpen;
    }

    /** Screen position of the desktop's top-left corner: window and app geometry is relative to it. */
    public int desktopX() {
        return ox();
    }

    public int desktopY() {
        return oy();
    }

    /** The Start menu entries, top to bottom, as labelled for the player. */
    public List<String> launcherLabels() {
        final List<String> out = new ArrayList<>();
        for (final Launcher l : launchers) {
            out.add(l.label());
        }
        return out;
    }

    /** The open window hosting the program launched under {@code label}, or null. */
    @org.jetbrains.annotations.Nullable
    public DesktopWindow windowFor(final String label) {
        for (final DesktopWindow w : windows) {
            if (w.appKey().equals(label)) {
                return w;
            }
        }
        return null;
    }

    /** Screen coordinates of the Start button's centre. */
    public int startButtonX() {
        return ox() + (osId.getPath().equals("panes_11") ? 4 + WIN11_SLOT / 2 : 30);
    }

    public int startButtonY() {
        return oy() + sh() - TASKBAR_H / 2;
    }

    /** Screen coordinates of the centre of the {@code index}-th Start menu entry (valid while it is open). */
    public int startMenuItemX() {
        return ox() + startMenuX() + BAND_W + 30;
    }

    public int startMenuItemY(final int index) {
        final int tbY = sh() - TASKBAR_H;
        return oy() + tbY - startMenuHeight() + 4 + index * MENU_ITEM_H + MENU_ITEM_H / 2;
    }

    /** The host computer's hardware era, read from its block entity so the monitor frame matches the chassis. */
    private HardwareEra era() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(host) instanceof AbstractComputerBlockEntity be) {
            return be.displayEra();
        }
        return HardwareEra.STANDARD;
    }

    /** The in-game time of day as HH:MM for the taskbar clock (Minecraft dayTime 0 = 06:00). */
    private String clockText() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return "";
        }
        final long t = mc.level.getDayTime() % 24000L;
        final int totalMin = (int) (((t + 6000L) % 24000L) * 3L / 50L);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", totalMin / 60, totalMin % 60);
    }

    /**
     * Outer bounds of the framed monitor window (the bezel plus its chin), in screen coordinates. Integrations
     * that place a side panel next to this screen (e.g. the JEI ingredient list) read this so the panel sits
     * beside the monitor rather than over it.
     */
    public MonitorFrameStyle.Geometry frameBounds() {
        return MonitorFrameStyle.forEra(era()).geometry(ox(), oy(), sw(), sh());
    }

    @Override
    protected void init() {
        // Size the container's image rect to the on-screen monitor glass, so leftPos/topPos centre exactly
        // where ox()/oy() place the desktop. The inventory/title labels the base would draw are pushed
        // off-screen — the desktop draws its own chrome.
        this.imageWidth = sw();
        this.imageHeight = sh();
        super.init();
        this.leftPos = ox();
        this.topPos = oy();
        this.titleLabelX = -10000;
        this.inventoryLabelY = -10000;

        buildLaunchers();

        // Restore the windows that were open when this computer's Monitor was last left.
        final java.util.List<SavedWin> saved = SAVED_WINDOWS.get(host);
        if (saved != null && windows.isEmpty()) {
            for (final SavedWin sw : saved) {
                final DesktopApp app = factoryFor(sw.key());
                if (app != null) {
                    openApp(sw.key(), app);
                    final DesktopWindow w = windows.get(windows.size() - 1);
                    w.setMinimized(sw.minimized());
                    w.setMaximized(sw.maximized());
                }
            }
        }

        // Become the active desktop and fetch the desktop-folder listing for the background icons.
        active = this;
        requestDesktop();
    }

    /** (Re)builds the launcher rail: the built-in apps plus any installed program that has its own window. */
    private void buildLaunchers() {
        launchers.clear();
        launchers.add(new Launcher("Network", () -> new NetworkInteractorApp(host, monitorPos)));
        launchers.add(new Launcher("This PC", () -> new ThisPcApp(host)));
        launchers.add(new Launcher("Files", () -> new FilesApp(host, osId.getPath(), "", monitorPos)));
        launchers.add(new Launcher("Editor", () -> new EditorApp(host)));
        launchers.add(new Launcher("Terminal", () -> new ShellApp(host)));
        if (installedPrograms.contains("nms")) {
            launchers.add(new Launcher("NMS",
                    () -> new dev.jsc.jscomputronics.module.computing.client.NmsApp(host, monitorPos)));
        }
        if (installedPrograms.contains("crafting_manager")) {
            launchers.add(new Launcher("Crafting Mgr", () -> new CraftingManagerApp(host)));
        }
    }

    /** Requests the desktop folder's files so they can be drawn as background icons. */
    private void requestDesktop() {
        PacketDistributor.sendToServer(new RequestDesktopFilesPayload(host));
    }

    /** Routes a desktop-folder listing reply to the active desktop. */
    public static void acceptDesktop(final DesktopFilesPayload payload) {
        if (active == null) {
            return;
        }
        active.desktopItems.clear();
        active.desktopItems.addAll(payload.files());
        active.desktopWallpaper = payload.wallpaper();
        active.computerName = payload.computerName();
        active.iconCells.clear();
        for (final DesktopFilesPayload.WireIconCell cell : payload.iconCells()) {
            active.iconCells.put(cell.key(), cell.cell());
        }
        // Refresh the installed-program launchers (e.g. the NMS appears after it is installed).
        final boolean hadNms = active.installedPrograms.contains("nms");
        final boolean hadCraftingMgr = active.installedPrograms.contains("crafting_manager");
        active.installedPrograms.clear();
        active.installedPrograms.addAll(payload.programs());
        if (active.installedPrograms.contains("nms") != hadNms
                || active.installedPrograms.contains("crafting_manager") != hadCraftingMgr) {
            active.buildLaunchers();
        }
        // Enter rename on a freshly created item once it appears in the listing.
        if (active.deskPendingRename != null) {
            for (int i = 0; i < active.desktopItems.size(); i++) {
                if (baseName(active.desktopItems.get(i).path()).equals(active.deskPendingRename)) {
                    active.startDeskRename(i);
                    break;
                }
            }
            active.deskPendingRename = null;
        }
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        // renderBackground already draws the vanilla blur + dim gradient once; a second identical fill
        // would darken the world behind the desktop to near-black (the double-dim bug). One pass only.
        renderBackground(g, mouseX, mouseY, partialTick);
        // Drain any cross-app open requests (e.g. Files asked to launch the Editor).
        if (!PENDING_OPEN.isEmpty()) {
            for (final String key : PENDING_OPEN) {
                final DesktopApp app = factoryFor(key);
                if (app != null) {
                    openApp(key, app);
                }
            }
            PENDING_OPEN.clear();
        }
        // Keep the inventory slots glued to the focused Network Interactor window this frame (per-frame, so a
        // dragged window does not leave its slots a tick behind).
        syncInventorySlots();
        final int sw = sw();
        final int sh = sh();
        final int ox = ox();
        final int oy = oy();
        final int lmx = mouseX - ox;
        final int lmy = mouseY - oy;
        final HardwareEra eraNow = era();

        // The host computer's hardware-era monitor frame wraps the desktop glass, then translate so the desktop
        // draws in local (0,0)-(sw,sh) coordinates.
        MonitorFrame.renderBody(g, ox, oy, sw, sh, eraNow, font);
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        g.enableScissor(ox, oy, ox + sw, oy + sh);

        WallpaperPainter.paint(g, sw, sh, osId, eraNow, desktopWallpaper);

        // Desktop icons: program launchers first, then the desktop folder's files and folders, laid
        // out in columns (top-down, then left-to-right) like a Windows desktop. Each icon's cell comes
        // from the free-positioning layout (a pinned cell, else the next auto-flow cell).
        final int total = launchers.size() + desktopItems.size();
        final int perCol = iconsPerColumn(sh);
        final int[] slotCells = computeSlotCells(perCol);
        // While the Start menu is open, hide any icon (and its label) that would sit under it, so the
        // text never bleeds through the menu panel.
        final int startMenuTop = startOpen ? (sh - TASKBAR_H) - startMenuHeight() : Integer.MAX_VALUE;
        final int deskDropTarget = deskDragging ? iconSlotAt(deskDragX, deskDragY, perCol) : -1;
        // Each desktop layer draws at its own strictly-increasing Z (DesktopZ): the depth buffer keeps a back
        // layer behind a front one, so a back layer's batched text (an icon label) can never paint over a
        // front layer (an open window). Flushing the text batch between layers does not work — g.flush() is a
        // no-op outside a managed draw in 1.21.1 — which is why the icon-label-over-window bug kept returning.
        g.pose().pushPose();
        g.pose().translate(0, 0, DesktopZ.ICONS);
        for (int i = 0; i < total; i++) {
            final int ix = iconXForCell(slotCells[i]);
            final int iy = iconYForCell(slotCells[i]);
            if (startOpen && ix >= startMenuX() - 2 && ix < startMenuX() + MENU_W + 2 && iy + 34 > startMenuTop) {
                continue;
            }
            if (i == selectedIcon) {
                g.fill(ix - 3, iy - 2, ix + 27, iy + 32, 0x66000080);
            } else if (lmx >= ix - 3 && lmx < ix + 27 && lmy >= iy - 2 && lmy < iy + 32 && !deskDragging) {
                // Hover feedback so the player sees which icon the cursor is over.
                g.fill(ix - 3, iy - 2, ix + 27, iy + 32, 0x28FFFFFF);
            }
            // Green drop-target outline on the folder under the cursor while dragging a real
            // file/folder icon (a launcher has no file to move into a folder, so it lights none).
            if (deskDragging && deskDragSlot >= launchers.size()
                    && i == deskDropTarget && i >= launchers.size() && i != deskDragSlot
                    && desktopItems.get(i - launchers.size()).directory()) {
                g.fill(ix - 3, iy - 2, ix + 27, iy - 1, 0xFF49E07A);
                g.fill(ix - 3, iy + 31, ix + 27, iy + 32, 0xFF49E07A);
                g.fill(ix - 3, iy - 2, ix - 2, iy + 32, 0xFF49E07A);
                g.fill(ix + 26, iy - 2, ix + 27, iy + 32, 0xFF49E07A);
            }
            final String label;
            if (i < launchers.size()) {
                ProgramIcons.draw(g, ix, iy, 24, 22, launchers.get(i).label(), osId.getPath());
                label = launchers.get(i).label();
            } else {
                final int di = i - launchers.size();
                final DiskFilesPayload.WireFile f = desktopItems.get(di);
                drawDesktopIcon(g, ix, iy, f);
                label = di == deskRenaming ? deskRenameBuf + "_" + deskRenameExt : baseName(f.path());
            }
            // Centered, trimmed label under the icon so it stays in its cell and never sprawls.
            final String lbl = trim(label, 9);
            g.drawString(font, lbl, ix + 12 - font.width(lbl) / 2, iy + 24,
                    theme.iconText(), theme.textShadow());
        }
        g.pose().popPose();

        g.pose().pushPose();
        g.pose().translate(0, 0, DesktopZ.WINDOWS);
        for (final DesktopWindow w : windows) {
            if (!w.minimized()) {
                w.render(g, font, skin, lmx, lmy, partialTick, sw, sh, TASKBAR_H);
            }
        }
        g.pose().popPose();

        // Real container-slot items for the focused Network Interactor window's inventory zone, over the
        // window the app already drew the slot backgrounds for.
        g.pose().pushPose();
        g.pose().translate(0, 0, DesktopZ.INVENTORY);
        renderInventoryItems(g, lmx, lmy, partialTick);
        g.pose().popPose();

        g.pose().pushPose();
        g.pose().translate(0, 0, DesktopZ.TASKBAR);

        final int tbY = sh - TASKBAR_H;
        final String osp = osId.getPath();
        if (osp.equals("panes_11")) {
            // Windows 11 taskbar: dark bar, centered Start + app icons with an active indicator, clock right.
            renderWin11Taskbar(g, tbY, sw, lmx, lmy);
        } else {
            // Taskbar background — 95 bevelled grey, XP Luna gradient.
            if (osp.equals("panes_xp")) {
                g.fillGradient(0, tbY, sw, sh, 0xFF4A86D4, 0xFF1C4D9C);
                g.fill(0, tbY, sw, tbY + 1, 0xFF8FBCEC);
            } else {
                g.fill(0, tbY, sw, sh, theme.taskbar());
                g.fill(0, tbY, sw, tbY + 1, 0xFFFFFFFF);
            }
            // Start button — distinct per Panes version, each with its own glyph.
            final int sbW = 54;
            if (osp.equals("panes_xp")) {
                // Glossy green orb with a highlighted top half.
                g.fillGradient(4, tbY + 2, 4 + sbW, sh - 2, 0xFF8FDB78, 0xFF1F7A22);
                g.fill(6, tbY + 3, 4 + sbW - 2, tbY + 9, 0x4DFFFFFF);
                g.drawString(font, "start", 16, tbY + 8, 0xFFFFFFFF, true);
            } else {
                g.fill(4, tbY + 3, 4 + sbW, sh - 3, theme.startButton());
                bevel(g, 4, tbY + 3, sbW, TASKBAR_H - 6, 0xFFFFFFFF, 0xFF808080);
                // Four-pane flag logo.
                g.fill(8, tbY + 8, 11, tbY + 11, 0xFFE0454A);
                g.fill(12, tbY + 8, 15, tbY + 11, 0xFF49B84B);
                g.fill(8, tbY + 12, 11, tbY + 15, 0xFF3C74D6);
                g.fill(12, tbY + 12, 15, tbY + 15, 0xFFE6B928);
                g.drawString(font, "Start", 18, tbY + 8, 0xFF000000, false);
            }
            int bx = 64;
            // Stop task buttons before the clock so they never overrun it or bleed off the right edge.
            final int taskRight = sw - 38;
            for (final DesktopWindow w : windows) {
                if (bx + 84 > taskRight) {
                    break;
                }
                taskButton(g, bx, tbY + 3, 84, TASKBAR_H - 6, osp);
                g.drawString(font, trim(w.app().title(), 12), bx + 5, tbY + 8, theme.startText(), theme.textShadow());
                bx += 88;
            }
            final String clock = clockText();
            final int clkW = font.width(clock) + 8;
            final int clkX = sw - clkW - 2;
            if (osp.equals("panes_95")) {
                g.fill(clkX, tbY + 3, sw - 2, sh - 3, theme.taskbar());
                bevel(g, clkX, tbY + 3, clkW, TASKBAR_H - 6, 0xFF808080, 0xFFFFFFFF); // sunken tray
            } else if (osp.equals("panes_xp")) {
                g.fill(clkX, tbY + 2, sw, sh - 2, 0xFF2C5FA8);
            }
            g.drawString(font, clock, sw - 4 - font.width(clock), tbY + 8, theme.startText(), theme.textShadow());
        }
        g.pose().popPose(); // close the TASKBAR layer

        // Menus (Start + desktop context), above the taskbar.
        if (startOpen || deskCtxOpen) {
            g.pose().pushPose();
            g.pose().translate(0, 0, DesktopZ.MENU);
            if (startOpen) {
                renderStartMenu(g, tbY);
            }
            if (deskCtxOpen) {
                renderDeskContext(g, lmx, lmy);
            }
            g.pose().popPose();
        }

        // Icon drag feedback (drop-target outline + ghost), above the menus.
        if (deskDragging && deskDragSlot >= 0) {
            g.pose().pushPose();
            g.pose().translate(0, 0, DesktopZ.DRAG);
            // While dragging an icon to a free spot (not onto a folder), outline the grid cell it would snap to.
            // Suppressed over a folder (the green folder outline wins) or off the wallpaper, where the drop is a no-op.
            if (deskDropTarget < 0 && deskDragX < sw && deskDragY < tbY && overWallpaper(deskDragX, deskDragY)) {
                final int cell = cellAt(deskDragX, deskDragY, perCol);
                final int cx = iconXForCell(cell);
                final int cy = iconYForCell(cell);
                g.fill(cx - 3, cy - 2, cx + 27, cy - 1, 0x804C84F0);
                g.fill(cx - 3, cy + 31, cx + 27, cy + 32, 0x804C84F0);
                g.fill(cx - 3, cy - 2, cx - 2, cy + 32, 0x804C84F0);
                g.fill(cx + 26, cy - 2, cx + 27, cy + 32, 0x804C84F0);
            }
            // Drag ghost: a label trailing the cursor for the icon being moved.
            if (deskDragSlot < total) {
                final String label = deskDragSlot < launchers.size()
                        ? launchers.get(deskDragSlot).label()
                        : baseName(desktopItems.get(deskDragSlot - launchers.size()).path());
                final int gx = (int) deskDragX + 6;
                final int gy = (int) deskDragY + 2;
                g.fill(gx, gy, gx + font.width(label) + 6, gy + 12, 0xD0303848);
                g.drawString(font, label, gx + 3, gy + 2, 0xFFFFFFFF, false);
            }
            g.pose().popPose();
        }

        g.disableScissor();
        // Hover tooltips: drawn at the base pose because the vanilla tooltip renderer translates +400 itself,
        // landing them at DesktopZ.TOOLTIP — above every window and the taskbar. The front window's app draws
        // its own hover hints (network/storage cells); the inventory zone defers to the real slot's item tooltip.
        final DesktopWindow tooltipWin = frontWindow();
        if (tooltipWin != null) {
            tooltipWin.renderTooltip(g, font, lmx, lmy);
        }
        if (hoveredSlot != null && menu.getCarried().isEmpty() && hoveredSlot.hasItem()) {
            g.renderTooltip(font, hoveredSlot.getItem(), lmx, lmy);
        }

        // The carried (cursor) stack rides above the tooltip, at the mouse.
        g.pose().pushPose();
        g.pose().translate(0, 0, DesktopZ.CURSOR);
        renderCarried(g, lmx, lmy);
        g.pose().popPose();

        // A modal error dialog sits over the whole desktop: dim the surface, then draw it on top.
        if (popup != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, DesktopZ.POPUP);
            g.fill(0, 0, sw, sh, 0x80000000);
            popup.render(g, font, sw, sh, lmx, lmy);
            g.pose().popPose();
        }
        g.pose().popPose(); // close the (ox, oy) desktop-origin translate
    }

    // The desktop paints its whole surface in the render() override above and does not call super.render(), so
    // the container's background pass is unused — the inventory items and cursor are drawn by render() instead.
    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
    }

    private int iconsPerColumn(final int sh) {
        return Math.max(1, (sh - TASKBAR_H - 12) / ICON_PITCH_Y);
    }

    /** The stable persistence id for the icon at global slot {@code i}: {@code app:<label>} or {@code file:<name>}. */
    private String iconKey(final int i) {
        if (i < launchers.size()) {
            return "app:" + launchers.get(i).label();
        }
        return "file:" + baseName(desktopItems.get(i - launchers.size()).path());
    }

    /**
     * Resolves the grid cell ({@code col << 16 | row}) of every desktop icon for this layout: a pinned icon
     * keeps its stored cell (clamped so it always lands on a real column), and the rest flow top-down then
     * left-to-right into the first cell no pinned icon already claims. This single source feeds both the icon
     * draw pass and the hit-test, so what the player sees and what they click are always the same cells.
     */
    private int[] computeSlotCells(final int perCol) {
        final int total = launchers.size() + desktopItems.size();
        final List<String> keys = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            keys.add(iconKey(i));
        }
        return DesktopIconLayout.resolve(keys, iconCells, perCol);
    }

    private static int iconXForCell(final int packedCell) {
        return 10 + DesktopIconLayout.col(packedCell) * ICON_PITCH_X;
    }

    private static int iconYForCell(final int packedCell) {
        return 10 + DesktopIconLayout.row(packedCell) * ICON_PITCH_Y;
    }

    /** The desktop icon slot under a desktop-local point, or {@code -1} for the empty background. */
    private int iconSlotAt(final double mx, final double my, final int perCol) {
        final int[] cells = computeSlotCells(perCol);
        for (int i = 0; i < cells.length; i++) {
            final int ix = iconXForCell(cells[i]);
            final int iy = iconYForCell(cells[i]);
            if (mx >= ix - 3 && mx <= ix + 27 && my >= iy - 2 && my <= iy + 34) {
                return i;
            }
        }
        return -1;
    }

    /** The packed grid cell ({@code col << 16 | row}) under a desktop-local point, clamped to the grid. */
    private static int cellAt(final double mx, final double my, final int perCol) {
        final int col = Math.max(0, (int) Math.floor((mx - (10 - ICON_PITCH_X / 2.0)) / ICON_PITCH_X));
        final int row = Math.max(0, Math.min(perCol - 1,
                (int) Math.floor((my - (10 - ICON_PITCH_Y / 2.0)) / ICON_PITCH_Y)));
        return DesktopIconLayout.pack(col, row);
    }

    /**
     * Resolves a dropped desktop icon at desktop-local point ({@code dx},{@code dy}). In priority order:
     * dropping onto an open Files explorer moves the file/folder into the folder that window shows; dropping
     * onto a desktop folder moves it inside; and dropping on the bare wallpaper pins the icon to that grid
     * cell (free positioning) and persists the spot. {@code .dat} projections cannot be moved by hand — any
     * move attempt raises the locked-file dialog instead, leaving the item where it is. Launchers have no
     * underlying file, so for them only the pin-to-cell path applies.
     */
    private void handleDeskDrop(final double dx, final double dy) {
        final int total = launchers.size() + desktopItems.size();
        if (deskDragSlot < 0 || deskDragSlot >= total) {
            return;
        }
        final boolean isLauncher = deskDragSlot < launchers.size();
        final DiskFilesPayload.WireFile src =
                isLauncher ? null : desktopItems.get(deskDragSlot - launchers.size());

        // (1) Drop onto an open Files explorer window: move the file into the folder it is showing.
        final DesktopWindow explorer = explorerWindowAt(dx, dy);
        if (explorer != null && explorer.app() instanceof FilesApp files && src != null) {
            final String destDir = files.crossWindowDropDir(explorer, dx, dy);
            if (destDir != null && !samePathParent(src.path(), destDir)) {
                if (src.readOnly()) {
                    showError("Error", DAT_LOCKED_MESSAGE);
                } else {
                    PacketDistributor.sendToServer(new MoveFilePayload(host, src.path(), destDir));
                    clearMovedIconCell(src);
                    files.refresh();
                    requestDesktop();
                }
            }
            return;
        }

        // (2) Drop onto a desktop folder icon: move the file inside it.
        final int perCol = iconsPerColumn(sh());
        final int target = iconSlotAt(dx, dy, perCol);
        if (target >= launchers.size() && target != deskDragSlot && src != null) {
            final DiskFilesPayload.WireFile dst = desktopItems.get(target - launchers.size());
            if (dst.directory()) {
                if (src.readOnly()) {
                    showError("Error", DAT_LOCKED_MESSAGE);
                } else {
                    PacketDistributor.sendToServer(new MoveFilePayload(host, src.path(), dst.path()));
                    clearMovedIconCell(src);
                    requestDesktop();
                }
                return;
            }
        }

        // (3) Drop on the bare wallpaper: pin the icon to the grid cell under the cursor and persist it,
        // unless that cell already holds another icon (so two icons never stack on the same spot).
        if (dy < sh() - TASKBAR_H && overWallpaper(dx, dy)) {
            final int cell = cellAt(dx, dy, perCol);
            final int[] cells = computeSlotCells(perCol);
            for (int i = 0; i < cells.length; i++) {
                if (i != deskDragSlot && cells[i] == cell) {
                    return; // the target cell is occupied; leave the icon where it was
                }
            }
            final String key = iconKey(deskDragSlot);
            iconCells.put(key, cell);
            PacketDistributor.sendToServer(new SetIconPositionPayload(host, key, cell));
        }
    }

    /** Whether {@code destDir} is already the parent folder of {@code srcPath} (a no-op move). */
    private static boolean samePathParent(final String srcPath, final String destDir) {
        final int slash = srcPath.lastIndexOf('/');
        final String parent = slash < 0 ? "" : srcPath.substring(0, slash);
        return parent.equals(destDir);
    }

    /** Forgets a desktop icon's pinned cell once its file has left the desktop folder (moved away). */
    private void clearMovedIconCell(final DiskFilesPayload.WireFile src) {
        iconCells.remove("file:" + baseName(src.path()));
    }

    /**
     * Handles a file dragged out of the front Files explorer and released over the bare desktop or over a
     * different explorer window: it moves the file into the destination folder (the desktop folder, or the
     * other explorer's open folder). Returns {@code true} when it consumed the drop, so the origin explorer's
     * own in-window drop logic is skipped. A {@code .dat} cannot be moved this way — it raises the locked
     * dialog instead. Returns {@code false} when the front window is not a dragging explorer or the drop
     * lands back inside the origin window (let the app handle it).
     */
    private boolean handleExplorerDropToDesktop(final double dx, final double dy) {
        final DesktopWindow front = frontWindow();
        if (front == null || !(front.app() instanceof FilesApp origin) || !origin.isDragging()) {
            return false;
        }
        final DiskFilesPayload.WireFile dragged = origin.draggedFile();
        if (dragged == null) {
            return false;
        }
        // A drop landing inside the origin explorer is its own business (move into a subfolder, onto media).
        final boolean insideOrigin = dx >= front.x() && dx <= front.x() + front.width()
                && dy >= front.y() && dy <= front.y() + front.height();
        if (insideOrigin) {
            return false;
        }
        // Dropped onto a different explorer window: move into the folder that window shows.
        final DesktopWindow otherExplorer = explorerWindowAt(dx, dy);
        if (otherExplorer != null && otherExplorer != front
                && otherExplorer.app() instanceof FilesApp dest) {
            final String destDir = dest.crossWindowDropDir(otherExplorer, dx, dy);
            moveExplorerFile(origin, dest, dragged, destDir);
            origin.cancelDrag();
            return true;
        }
        // Dropped on the bare wallpaper: move it into the desktop folder.
        if (dy < sh() - TASKBAR_H && overWallpaper(dx, dy)) {
            moveExplorerFile(origin, null, dragged, SystemLayout.DESKTOP_DIR);
            origin.cancelDrag();
            return true;
        }
        return false;
    }

    /**
     * Emits the move of {@code dragged} (from {@code origin}) into {@code destDir}, refreshing the source
     * explorer, an optional destination explorer, and the desktop icons. A {@code .dat} or a no-op move
     * (already in that folder) does nothing but show the locked dialog where appropriate.
     */
    private void moveExplorerFile(final FilesApp origin, @org.jetbrains.annotations.Nullable final FilesApp dest,
                                  final DiskFilesPayload.WireFile dragged, final String destDir) {
        if (destDir == null || samePathParent(dragged.path(), destDir)) {
            return;
        }
        if (dragged.readOnly()) {
            showError("Error", DAT_LOCKED_MESSAGE);
            return;
        }
        PacketDistributor.sendToServer(new MoveFilePayload(host, dragged.path(), destDir));
        origin.refresh();
        if (dest != null) {
            dest.refresh();
        }
        requestDesktop();
    }

    /** Opens an icon slot: a launcher starts its program; a desktop file/folder opens or navigates. */
    private void openSlot(final int slot) {
        if (slot < launchers.size()) {
            runLauncher(launchers.get(slot));
            return;
        }
        final int di = slot - launchers.size();
        if (di < 0 || di >= desktopItems.size()) {
            return;
        }
        final DiskFilesPayload.WireFile f = desktopItems.get(di);
        if (f.directory()) {
            openApp("Files", new FilesApp(host, osId.getPath(), f.path(), monitorPos));
        } else if (!f.readOnly()) {
            openApp("Editor", new EditorApp(host));
            PacketDistributor.sendToServer(new RequestFileContentPayload(host, f.path()));
        }
    }

    private void renderDeskContext(final GuiGraphics g, final int hoverMx, final int hoverMy) {
        final String[] items = deskCtxItem >= 0 ? DESK_CTX_ICON : DESK_CTX_BG;
        final int mx = deskCtxX;
        final int my = deskCtxY;
        final int mh = items.length * DESK_CTX_ITEM_H + 2;
        g.fill(mx - 1, my - 1, mx + DESK_CTX_W + 1, my + mh + 1, 0xFF000000);
        g.fill(mx, my, mx + DESK_CTX_W, my + mh, 0xFFE8E8EC);
        g.fill(mx, my, mx + DESK_CTX_W, my + 1, 0xFFFFFFFF);
        final int hover = hoverMx >= mx && hoverMx <= mx + DESK_CTX_W
                ? (int) Math.floor((hoverMy - (my + 1)) / (double) DESK_CTX_ITEM_H) : -1;
        int iy = my + 1;
        for (int k = 0; k < items.length; k++) {
            if (k == hover) {
                g.fill(mx + 1, iy, mx + DESK_CTX_W - 1, iy + DESK_CTX_ITEM_H, 0xFF000080);
            }
            g.drawString(font, items[k], mx + 4, iy + 2, k == hover ? 0xFFFFFFFF : 0xFF1A2230, false);
            iy += DESK_CTX_ITEM_H;
        }
    }

    private int deskCtxItemAt(final double mx, final double my) {
        final String[] items = deskCtxItem >= 0 ? DESK_CTX_ICON : DESK_CTX_BG;
        if (mx < deskCtxX || mx > deskCtxX + DESK_CTX_W) {
            return -1;
        }
        final int rel = (int) Math.floor((my - (deskCtxY + 1)) / (double) DESK_CTX_ITEM_H);
        return rel >= 0 && rel < items.length ? rel : -1;
    }

    private void runDeskContext(final int item) {
        if (deskCtxItem >= 0) {
            if (deskCtxItem >= desktopItems.size()) {
                return;
            }
            switch (item) {
                case 0 -> openSlot(launchers.size() + deskCtxItem);
                case 1 -> startDeskRename(deskCtxItem);
                case 2 -> deleteDeskItem(deskCtxItem);
                default -> { }
            }
        } else {
            switch (item) {
                case 0 -> newDeskFile();
                case 1 -> newDeskFolder();
                case 2 -> cycleWallpaper();
                case 3 -> requestDesktop();
                default -> { }
            }
        }
    }

    /** Cycles to the next wallpaper style and persists the choice on the computer. */
    private void cycleWallpaper() {
        int idx = 0;
        for (int i = 0; i < WallpaperPainter.STYLES.length; i++) {
            if (WallpaperPainter.STYLES[i].equals(desktopWallpaper)) {
                idx = i;
                break;
            }
        }
        desktopWallpaper = WallpaperPainter.STYLES[(idx + 1) % WallpaperPainter.STYLES.length];
        PacketDistributor.sendToServer(new SetDesktopPrefsPayload(host, desktopWallpaper, computerName));
    }

    private void startDeskRename(final int idx) {
        if (idx < 0 || idx >= desktopItems.size()) {
            return;
        }
        final DiskFilesPayload.WireFile f = desktopItems.get(idx);
        if (f.readOnly()) {
            showError("Error", DAT_LOCKED_MESSAGE);
            return;
        }
        deskRenaming = idx;
        selectedIcon = launchers.size() + idx;
        deskRenameBuf.setLength(0);
        final String name = baseName(f.path());
        if (!f.directory()) {
            final int dot = name.lastIndexOf('.');
            if (dot > 0) {
                deskRenameBuf.append(name, 0, dot);
                deskRenameExt = name.substring(dot);
            } else {
                deskRenameBuf.append(name);
                deskRenameExt = "";
            }
        } else {
            deskRenameBuf.append(name);
            deskRenameExt = "";
        }
    }

    private void commitDeskRename() {
        if (deskRenaming >= 0 && deskRenaming < desktopItems.size()) {
            final DiskFilesPayload.WireFile f = desktopItems.get(deskRenaming);
            final String oldPath = f.path();
            final String newName = deskRenameBuf.toString().trim() + deskRenameExt;
            final String newPath = SystemLayout.DESKTOP_DIR + "/" + newName;
            if (!deskRenameBuf.toString().trim().isEmpty() && !newPath.equals(oldPath)) {
                PacketDistributor.sendToServer(new RenameFilePayload(host, oldPath, newPath));
                requestDesktop();
            }
        }
        deskRenaming = -1;
    }

    private void deleteDeskItem(final int idx) {
        if (idx < 0 || idx >= desktopItems.size()) {
            return;
        }
        final DiskFilesPayload.WireFile f = desktopItems.get(idx);
        if (f.readOnly()) {
            showError("Error", DAT_LOCKED_MESSAGE);
            return;
        }
        PacketDistributor.sendToServer(new DeleteFilePayload(host, f.path()));
        requestDesktop();
    }

    private void newDeskFile() {
        final String name = uniqueDeskName("New File", ".txt");
        deskPendingRename = name;
        PacketDistributor.sendToServer(new SaveFilePayload(host, SystemLayout.DESKTOP_DIR + "/" + name, ""));
        requestDesktop();
    }

    private void newDeskFolder() {
        final String name = uniqueDeskName("New Folder", "");
        deskPendingRename = name;
        PacketDistributor.sendToServer(new MkdirPayload(host, SystemLayout.DESKTOP_DIR + "/" + name));
        requestDesktop();
    }

    private String uniqueDeskName(final String base, final String ext) {
        if (!deskNameExists(base + ext)) {
            return base + ext;
        }
        int n = 2;
        while (deskNameExists(base + " (" + n + ")" + ext)) {
            n++;
        }
        return base + " (" + n + ")" + ext;
    }

    private boolean deskNameExists(final String name) {
        for (final DiskFilesPayload.WireFile f : desktopItems) {
            if (baseName(f.path()).equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Draws a folder or document icon (~24x22) for a desktop file entry. */
    private static void drawDesktopIcon(final GuiGraphics g, final int x, final int y,
                                        final DiskFilesPayload.WireFile f) {
        if (f.directory()) {
            g.fill(x + 1, y + 1, x + 10, y + 4, 0xFFFFE9A8);   // tab
            g.fill(x + 1, y + 4, x + 23, y + 20, 0xFFF4C842);  // body
            g.fill(x + 1, y + 4, x + 23, y + 6, 0xFFFFF3C4);   // highlight
            iconOutline(g, x + 1, y + 1, 22, 19, 0xFF9A7B16);
        } else {
            final int fill;
            final int edge;
            switch (f.ext().toLowerCase(java.util.Locale.ROOT)) {
                case "iql" -> { fill = 0xFFA9D4FF; edge = 0xFF3A72B0; }
                case "dat" -> { fill = 0xFFBDEEC0; edge = 0xFF4F9B53; }
                default -> { fill = 0xFFEDEFF3; edge = 0xFF8A93A6; }
            }
            g.fill(x + 4, y + 1, x + 21, y + 21, fill);        // sheet
            g.fill(x + 16, y + 1, x + 21, y + 6, 0xFFFFFFFF);  // folded corner
            iconOutline(g, x + 4, y + 1, 17, 20, edge);
        }
    }

    private static void iconOutline(final GuiGraphics g, final int x, final int y, final int w, final int h,
                                    final int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static String baseName(final String path) {
        final int slash = path.lastIndexOf('/');
        return slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : path;
    }

    private int startMenuHeight() {
        return launchers.size() * MENU_ITEM_H + 6 + MENU_ITEM_H + 8;
    }

    /** The left edge of the CENTERED app-icon strip on the Windows 11 taskbar (the Start button sits left). */
    private int win11AppsX(final int sw) {
        return (sw - windows.size() * WIN11_SLOT) / 2;
    }

    /** The Start menu's left edge: pinned to the left on every Panes version, including Panes 11. */
    private int startMenuX() {
        return 4;
    }

    /**
     * The Windows 11 taskbar: a dark bar with the Start logo and the open windows' icons centered, each app
     * carrying an indicator under it (a wide pill for the focused window, a short dot otherwise), and the clock
     * pinned to the right. Modelled on the real Windows 11 taskbar (centered, dark, flat).
     */
    private void renderWin11Taskbar(final GuiGraphics g, final int tbY, final int sw, final int lmx, final int lmy) {
        final int bottom = tbY + TASKBAR_H;
        g.fill(0, tbY, sw, bottom, 0xF01E1F23);          // dark, slightly translucent bar
        g.fill(0, tbY, sw, tbY + 1, 0x18FFFFFF);          // faint top hairline

        final int iconY = tbY + (TASKBAR_H - WIN11_ICON) / 2;

        // Start: pinned to the LEFT corner (aligned with the Start menu, which opens on the left), with a hover
        // highlight — the four-pane blue logo, no text.
        final int startX = 4;
        if (lmx >= startX && lmx < startX + WIN11_SLOT && lmy >= tbY) {
            g.fill(startX, tbY + 2, startX + WIN11_SLOT, bottom - 2, 0x18FFFFFF);
        }
        drawWin11Start(g, startX + (WIN11_SLOT - 11) / 2, iconY + 2);

        // Open windows' app icons, CENTERED, each with hover/active background and an indicator underneath.
        final int appsX = win11AppsX(sw);
        final DesktopWindow front = frontWindow();
        for (int i = 0; i < windows.size(); i++) {
            final DesktopWindow w = windows.get(i);
            final int ix = appsX + i * WIN11_SLOT;
            final boolean hover = lmx >= ix && lmx < ix + WIN11_SLOT && lmy >= tbY;
            final boolean active = w == front && !w.minimized();
            if (hover || active) {
                g.fill(ix + 1, tbY + 2, ix + WIN11_SLOT - 1, bottom - 2, active ? 0x26FFFFFF : 0x18FFFFFF);
            }
            ProgramIcons.draw(g, ix + (WIN11_SLOT - WIN11_ICON) / 2, iconY, WIN11_ICON, WIN11_ICON - 2,
                    w.appKey(), "panes_11");
            final int cx = ix + WIN11_SLOT / 2;
            if (active) {
                g.fill(cx - 6, bottom - 2, cx + 6, bottom - 1, 0xFF4C84F0); // wide pill = focused
            } else if (!w.minimized()) {
                g.fill(cx - 2, bottom - 2, cx + 2, bottom - 1, 0xFF8A93A4); // short dot = open
            }
        }

        final String clock = clockText();
        g.drawString(font, clock, sw - font.width(clock) - 8, tbY + 8, 0xFFE6E8EC, false);
    }

    /** The Windows 11 Start glyph: four solid blue panes with a thin gap. */
    private static void drawWin11Start(final GuiGraphics g, final int x, final int y) {
        final int c = 0xFF4C84F0;
        g.fill(x, y, x + 5, y + 5, c);
        g.fill(x + 6, y, x + 11, y + 5, c);
        g.fill(x, y + 6, x + 5, y + 11, c);
        g.fill(x + 6, y + 6, x + 11, y + 11, c);
    }

    /** Draws a taskbar window button in the OS's style (95 bevelled, XP gradient, 11 flat). */
    private void taskButton(final GuiGraphics g, final int x, final int y, final int w, final int h,
                            final String osp) {
        switch (osp) {
            case "panes_xp" -> g.fillGradient(x, y, x + w, y + h, 0xFF5B95DD, 0xFF2C5FA8);
            case "panes_11" -> g.fill(x, y, x + w, y + h, 0xFFE3E5EE);
            default -> {
                g.fill(x, y, x + w, y + h, theme.taskButton());
                bevel(g, x, y, w, h, 0xFFFFFFFF, 0xFF808080);
            }
        }
    }

    /** A 1px 3D bevel: light top/left, dark bottom/right (the classic raised look). */
    private static void bevel(final GuiGraphics g, final int x, final int y, final int w, final int h,
                              final int light, final int dark) {
        g.fill(x, y, x + w, y + 1, light);
        g.fill(x, y, x + 1, y + h, light);
        g.fill(x, y + h - 1, x + w, y + h, dark);
        g.fill(x + w - 1, y, x + w, y + h, dark);
    }

    private String osBandLabel() {
        return switch (osId.getPath()) {
            case "panes_xp" -> "Panes XP";
            case "panes_11" -> "Panes 11";
            default -> "Panes 95";
        };
    }

    private void renderStartMenu(final GuiGraphics g, final int tbY) {
        final int x = startMenuX();
        final int h = startMenuHeight();
        final int y = tbY - h;
        // Raised panel.
        g.fill(x - 1, y - 1, x + MENU_W + 1, y + h + 1, 0xFF000000);
        g.fill(x, y, x + MENU_W, y + h, theme.menuBg());
        g.fill(x, y, x + MENU_W, y + 1, 0xFFFFFFFF);
        g.fill(x, y, x + 1, y + h, 0xFFFFFFFF);
        // Side band with the OS name, drawn rotated like the classic Start menu.
        g.fill(x + 1, y + 1, x + 1 + BAND_W, y + h - 1, theme.titleActive());
        g.pose().pushPose();
        g.pose().translate(x + BAND_W - 5, y + h - 7, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-90));
        g.drawString(font, osBandLabel(), 0, 0, 0xFFFFFFFF, false);
        g.pose().popPose();
        // Program items with icons.
        final int itemX = x + BAND_W + 4;
        int my = y + 4;
        for (final Launcher l : launchers) {
            ProgramIcons.draw(g, itemX, my, 16, 14, l.label(), osId.getPath());
            g.drawString(font, l.label(), itemX + 20, my + 3, theme.menuText(), false);
            my += MENU_ITEM_H;
        }
        // Separator, then Shut Down.
        g.fill(itemX, my + 1, x + MENU_W - 4, my + 2, 0xFF808080);
        g.fill(itemX, my + 2, x + MENU_W - 4, my + 3, 0xFFFFFFFF);
        my += 6;
        g.fill(itemX + 3, my + 2, itemX + 13, my + 12, 0xFFC03030);
        g.fill(itemX + 7, my, itemX + 9, my + 6, 0xFFFFFFFF);
        g.drawString(font, "Shut Down", itemX + 20, my + 3, theme.menuText(), false);
    }

    @Override
    public boolean mouseClicked(final double mouseXAbs, final double mouseYAbs, final int button) {
        // A modal dialog swallows every click; only its OK button dismisses it.
        if (popup != null) {
            if (popup.okClicked(mouseXAbs - ox(), mouseYAbs - oy())) {
                popup = null;
            }
            return true;
        }
        final double mouseX = mouseXAbs - ox();
        final double mouseY = mouseYAbs - oy();
        final int tbY = sh() - TASKBAR_H;

        // Windows 11 taskbar: Start sits left, the app icons are centered, so they have their own hit test.
        if (osId.getPath().equals("panes_11") && mouseY >= tbY) {
            if (mouseX >= 4 && mouseX < 4 + WIN11_SLOT) {
                startOpen = !startOpen;
                return true;
            }
            final int appsX = win11AppsX(sw());
            final int idx = (int) ((mouseX - appsX) / WIN11_SLOT);
            if (mouseX >= appsX && idx >= 0 && idx < windows.size()) {
                final DesktopWindow w = windows.get(idx);
                if (w.minimized()) {
                    w.setMinimized(false);
                    windows.add(windows.remove(idx));
                } else if (idx == windows.size() - 1) {
                    w.setMinimized(true);
                } else {
                    windows.add(windows.remove(idx));
                }
                return true;
            }
        }

        if (mouseY >= tbY + 3 && mouseX >= 4 && mouseX <= 58) {
            startOpen = !startOpen;
            return true;
        }
        if (startOpen) {
            final int h = startMenuHeight();
            final int y = tbY - h;
            if (mouseX >= startMenuX() && mouseX <= startMenuX() + MENU_W && mouseY >= y && mouseY <= y + h) {
                final int itemsTop = y + 4;
                final int idx = (int) Math.floor((mouseY - itemsTop) / (double) MENU_ITEM_H);
                if (idx >= 0 && idx < launchers.size()) {
                    runLauncher(launchers.get(idx));
                    startOpen = false;
                } else {
                    final int shutY = itemsTop + launchers.size() * MENU_ITEM_H + 6;
                    if (mouseY >= shutY && mouseY <= shutY + MENU_ITEM_H) {
                        onClose();
                    }
                }
                startOpen = false;
                return true;
            }
            startOpen = false;
        }

        // Taskbar buttons: click toggles minimize / brings the window forward (Windows-style).
        if (mouseY >= tbY + 3 && mouseX >= 64) {
            final int idx = (int) ((mouseX - 64) / 88);
            final int bxStart = 64 + idx * 88;
            if (idx >= 0 && idx < windows.size() && mouseX <= bxStart + 84 && bxStart + 84 <= sw() - 38) {
                final DesktopWindow w = windows.get(idx);
                if (w.minimized()) {
                    w.setMinimized(false);
                    windows.add(windows.remove(idx));
                } else if (idx == windows.size() - 1) {
                    w.setMinimized(true);
                } else {
                    windows.add(windows.remove(idx));
                }
                return true;
            }
        }

        for (int i = windows.size() - 1; i >= 0; i--) {
            final DesktopWindow w = windows.get(i);
            if (w.minimized()) {
                continue;
            }
            final int titleBtn = w.buttonAt(mouseX, mouseY);
            if (titleBtn != 0) {
                // Press the button now; the action fires on release over the same button, so the player
                // sees the pushed-in feedback of a real click instead of the window reacting instantly.
                bringToFront(i);
                w.setPressedButton(titleBtn);
                pressedBtnWindow = w;
                return true;
            }
            final int rdir = w.resizeHitTest(mouseX, mouseY);
            if (rdir != DesktopWindow.RESIZE_NONE) {
                bringToFront(i);
                resizing = w;
                w.beginResize(rdir, mouseX, mouseY);
                return true;
            }
            if (w.titleBarHit(mouseX, mouseY)) {
                bringToFront(i);
                dragging = w;
                dragOffsetX = (int) mouseX - w.x();
                dragOffsetY = (int) mouseY - w.y();
                return true;
            }
            if (w.bodyHit(mouseX, mouseY)) {
                bringToFront(i);
                // A click landing on an active inventory slot (only the front Network Interactor has them) is a
                // real container click: let the vanilla container drive the cursor, drag, and shift-click.
                if (w.app() instanceof NetworkInteractorApp ni) {
                    // Shift-click an inventory slot inserts that whole stack into the network (Network tab) or
                    // local storage (Local tab), like MC-NET — instead of the vanilla quick-move between slots.
                    if (!ni.hasPopup() && hasShiftDown()) {
                        final net.minecraft.world.inventory.Slot slot = slotUnderMouse(mouseXAbs, mouseYAbs);
                        final int target = ni.shiftInsertTarget();
                        if (slot != null && slot.hasItem() && target >= 0) {
                            PacketDistributor.sendToServer(
                                    new dev.jsc.jscomputronics.module.computing.operation.payload
                                            .NiShiftInsertPayload(host, monitorPos, slot.getContainerSlot(), target));
                            return true;
                        }
                    }
                    // While the request/storage dialog is open it is modal over the window — even over the
                    // inventory band — so the app gets the click instead of the vanilla container.
                    if (!ni.hasPopup() && slotUnderMouse(mouseXAbs, mouseYAbs) != null) {
                        return super.mouseClicked(mouseXAbs, mouseYAbs, button);
                    }
                    // A held stack dropped on the item grid deposits into the network (Network tab) or local
                    // storage (Storage tab) — the desktop owns the cursor, so it routes the deposit here.
                    if (!ni.hasPopup() && !menu.getCarried().isEmpty() && (button == 0 || button == 1)) {
                        final int target = ni.cursorDepositTarget(mouseX - (w.x() + 4), mouseY - (w.y() + 18));
                        if (target >= 0) {
                            PacketDistributor.sendToServer(
                                    new dev.jsc.jscomputronics.module.computing.operation.payload
                                            .NiDepositPayload(host, monitorPos, target, button == 0));
                            return true;
                        }
                    }
                }
                w.app().mouseClicked(w, mouseX, mouseY, button);
                return true;
            }
        }

        // An open desktop context menu takes the click first.
        if (deskCtxOpen) {
            final int item = deskCtxItemAt(mouseX, mouseY);
            deskCtxOpen = false;
            if (item >= 0) {
                runDeskContext(item);
                return true;
            }
        }
        // A click on the desktop commits any in-progress icon rename.
        if (deskRenaming >= 0) {
            commitDeskRename();
        }

        final int perCol = iconsPerColumn(sh());
        final int slot = iconSlotAt(mouseX, mouseY, perCol);

        if (button == 1) {
            // Right-click: open the desktop context menu on an icon, or on the empty background.
            selectedIcon = slot;
            deskCtxItem = slot >= launchers.size() ? slot - launchers.size() : -1;
            deskCtxX = (int) mouseX;
            deskCtxY = (int) mouseY;
            deskCtxOpen = true;
            return true;
        }

        if (slot >= 0) {
            // Windows-style: single click selects an icon, a double click opens it.
            final long now = System.currentTimeMillis();
            final boolean dbl = selectedIcon == slot && now - iconClickAt < 300;
            selectedIcon = slot;
            iconClickAt = now;
            // Arm a drag of any desktop icon — a program launcher as well as a file or folder — so all of
            // them can be freely repositioned (the launcher drag only ever pins to a cell, never moves a file).
            // The drag does not actually begin until the cursor leaves a small dead zone, so a plain click (or
            // a double-click) never turns into an accidental reposition.
            deskDragSlot = slot;
            deskDragging = false;
            deskDragStartX = mouseX;
            deskDragStartY = mouseY;
            if (dbl) {
                openSlot(slot);
                selectedIcon = -1;
            }
            return true;
        }
        selectedIcon = -1;
        // A click on empty desktop while holding a stack would make the vanilla container throw the item to the
        // world (no slot under the cursor). Swallow it so nothing is ever dropped by clicking the wallpaper.
        if (!menu.getCarried().isEmpty()) {
            return true;
        }
        return super.mouseClicked(mouseXAbs, mouseYAbs, button);
    }

    @Override
    public boolean mouseDragged(final double mouseXAbs, final double mouseYAbs, final int button,
                                final double dx, final double dy) {
        if (popup != null) {
            return true;
        }
        if (dragging != null) {
            dragging.moveTo((int) (mouseXAbs - ox()) - dragOffsetX, (int) (mouseYAbs - oy()) - dragOffsetY,
                    sw(), sh() - TASKBAR_H);
            return true;
        }
        if (resizing != null) {
            resizing.applyResize(mouseXAbs - ox(), mouseYAbs - oy(), sw(), sh() - TASKBAR_H);
            return true;
        }
        // Dragging a desktop icon across the desktop, once the cursor has left the click dead zone.
        if (deskDragSlot >= 0) {
            deskDragX = mouseXAbs - ox();
            deskDragY = mouseYAbs - oy();
            if (!deskDragging
                    && (Math.abs(deskDragX - deskDragStartX) > DRAG_THRESHOLD
                        || Math.abs(deskDragY - deskDragStartY) > DRAG_THRESHOLD)) {
                deskDragging = true;
            }
            return true;
        }
        // No window drag/resize in progress. While the front Network Interactor holds a stack on the cursor,
        // a drag is the vanilla "spread across slots" gesture — hand it to the container, not the app.
        final DesktopWindow w = frontWindow();
        if (w != null && w.app() instanceof NetworkInteractorApp && !menu.getCarried().isEmpty()) {
            return super.mouseDragged(mouseXAbs, mouseYAbs, button, dx, dy);
        }
        if (w != null) {
            w.app().mouseDragged(w, mouseXAbs - ox(), mouseYAbs - oy(), button);
            return true;
        }
        return super.mouseDragged(mouseXAbs, mouseYAbs, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button) {
        if (popup != null) {
            return true;
        }
        // A title-bar button was pressed on mousedown; fire its action only if released over the same
        // button (dragging off it cancels). Either way, clear the pushed-in state.
        if (pressedBtnWindow != null) {
            final DesktopWindow pb = pressedBtnWindow;
            final int btn = pb.pressedButton();
            pb.setPressedButton(0);
            pressedBtnWindow = null;
            if (btn != 0 && pb.buttonAt(mouseX - ox(), mouseY - oy()) == btn) {
                if (btn == 3) {
                    windows.remove(pb);
                } else if (btn == 1) {
                    pb.setMinimized(true);
                } else if (btn == 2) {
                    pb.toggleMaximize();
                }
            }
            return true;
        }
        // A dragged desktop icon: handle the drop (move into a folder / open explorer, or pin to a cell).
        if (deskDragging && deskDragSlot >= 0) {
            handleDeskDrop(mouseX - ox(), mouseY - oy());
        }
        final boolean wasDeskDrag = deskDragging;
        deskDragging = false;
        deskDragSlot = -1;
        // A file dragged out of a Files explorer and dropped on the bare desktop moves it into the desktop
        // folder. Handled here, before the app sees the release, so the explorer's own in-window drop logic
        // does not also fire. Anything else (a drop staying inside the window, or onto a removable medium)
        // falls through to the app below.
        if (!wasDeskDrag && dragging == null && resizing == null
                && handleExplorerDropToDesktop(mouseX - ox(), mouseY - oy())) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        // Route the release to the front window's app (for content drag-and-drop) unless this was a
        // desktop-icon drag, and only when no window move/resize is in progress.
        if (!wasDeskDrag && dragging == null && resizing == null) {
            final DesktopWindow w = frontWindow();
            if (w != null) {
                w.app().mouseReleased(w, mouseX - ox(), mouseY - oy(), button);
            }
        }
        dragging = null;
        resizing = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(final char c, final int modifiers) {
        if (popup != null) {
            return true;
        }
        // A desktop-icon rename captures typing before any window.
        if (deskRenaming >= 0 && c >= 32 && c != 127 && c != '/' && c != '\\' && deskRenameBuf.length() < 64) {
            deskRenameBuf.append(c);
            return true;
        }
        final DesktopWindow w = frontWindow();
        if (w != null && w.app().charTyped(c)) {
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        // A modal dialog swallows every key; Enter or Escape dismisses it, nothing leaks behind it.
        if (popup != null) {
            if (key == 257 || key == 335 || key == 256) { // Enter / numpad Enter / Escape
                popup = null;
            }
            return true;
        }
        // An in-progress desktop-icon rename consumes keys first (Enter commits, Esc cancels).
        if (deskRenaming >= 0) {
            switch (key) {
                case 257, 335 -> commitDeskRename();
                case 256 -> deskRenaming = -1;
                case 259 -> {
                    if (deskRenameBuf.length() > 0) {
                        deskRenameBuf.deleteCharAt(deskRenameBuf.length() - 1);
                    }
                }
                default -> {
                    return false;
                }
            }
            return true;
        }
        // The front window's app gets first refusal on keys, except ESC which always closes the desktop.
        final DesktopWindow w = frontWindow();
        if (key != 256 && w != null && w.app().keyPressed(key, scanCode, modifiers)) {
            return true;
        }
        // A container screen closes on the inventory key by default; the desktop must NOT, or pressing 'E'
        // would dismiss the whole shell. Swallow that key here.
        if (key == Minecraft.getInstance().options.keyInventory.getKey().getValue()) {
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double dx, final double dy) {
        if (popup != null) {
            return true;
        }
        final DesktopWindow w = frontWindow();
        if (w != null && w.app().mouseScrolled(dy)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    /** The topmost non-minimized window, which receives keyboard and scroll input. */
    @org.jetbrains.annotations.Nullable
    private DesktopWindow frontWindow() {
        for (int i = windows.size() - 1; i >= 0; i--) {
            if (!windows.get(i).minimized()) {
                return windows.get(i);
            }
        }
        return null;
    }

    /**
     * Whether a desktop-local point lands on the bare wallpaper — not over any open (non-minimized) window
     * body or title bar. Used so a free icon drop only snaps to a cell on the empty desktop, and so a
     * cross-window drag knows the cursor is on the desktop (not a window).
     */
    private boolean overWallpaper(final double mx, final double my) {
        for (final DesktopWindow w : windows) {
            if (!w.minimized() && mx >= w.x() && mx <= w.x() + w.width()
                    && my >= w.y() && my <= w.y() + w.height()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The topmost open Files-explorer window whose body is under a desktop-local point, or {@code null}.
     * Cross-window drag uses this to decide which open folder a dragged file should move into.
     */
    @org.jetbrains.annotations.Nullable
    private DesktopWindow explorerWindowAt(final double mx, final double my) {
        for (int i = windows.size() - 1; i >= 0; i--) {
            final DesktopWindow w = windows.get(i);
            if (w.minimized() || !(w.app() instanceof FilesApp)) {
                continue;
            }
            if (mx >= w.x() && mx <= w.x() + w.width() && my >= w.y() && my <= w.y() + w.height()) {
                return w;
            }
        }
        return null;
    }

    /**
     * The front window only if it hosts a Network Interactor — the one window that shows the player's real
     * inventory slots. Returns {@code null} when the front window is another app or the desktop is bare, which
     * is exactly when the inventory slots must go inert.
     */
    @org.jetbrains.annotations.Nullable
    private DesktopWindow frontNetworkInteractorWindow() {
        final DesktopWindow w = frontWindow();
        return w != null && w.app() instanceof NetworkInteractorApp ? w : null;
    }

    /**
     * Repositions the menu's 36 inventory slots over the focused Network Interactor window's inventory zone and
     * toggles them active, once per tick before the next render. When no Network Interactor is in front the
     * slots are switched off (not rendered, not hit-tested), so the inventory only appears inside that window.
     * The slot grid origin is kept relative to {@code leftPos}/{@code topPos} — the offset the container renders
     * and hit-tests slots at (since {@code leftPos == ox()} and {@code topPos == oy()}, that origin is just the
     * window-local position of the first inventory cell).
     */
    @Override
    protected void containerTick() {
        super.containerTick();
        syncInventorySlots();
    }

    /**
     * Positions the menu's 36 inventory slots over the focused Network Interactor window's inventory zone and
     * toggles them active. Run from {@link #containerTick()} and again at the top of {@link #render} so the
     * slots track a dragged/resized window per frame, not just per tick. When no Network Interactor is in front
     * the slots go inert (not rendered, not hit-tested), so the inventory only shows inside that window. The
     * grid origin is window-local — since {@code leftPos == ox()} and {@code topPos == oy()}, that is exactly
     * the offset the container measures {@code slot.x}/{@code slot.y} from. The menu only rebuilds slots when
     * the origin actually changed, so this is cheap to call every frame.
     */
    private void syncInventorySlots() {
        final DesktopWindow w = frontNetworkInteractorWindow();
        if (w == null || !(w.app() instanceof NetworkInteractorApp app)) {
            menu.setSlotsActive(false);
            return;
        }
        // Resolve the window's rectangle for this frame first, so slot positions never lag a frame behind a
        // drag, resize, or maximize (curX/curY are otherwise only refreshed when the window itself renders).
        w.resolveGeometry(sw(), sh(), TASKBAR_H);
        // The focused Network Interactor is the one that should receive network snapshots and console output,
        // so point the static routing at it whenever it is in front (matters when two windows are open).
        app.markActive();
        menu.setSlotsActive(true);
        // The window-local top-left of the first inventory cell: past the window border + title bar to the app
        // content, then the app's own inventory-zone offset. The inventory is a fixed, framed band pinned just
        // above the footer; its Y uses the window's live content height (not the app's cached field) so the
        // cells line up with their backgrounds from the very first frame. The band is always fully visible — it
        // never scrolls and is never clipped — so every one of the 36 slots is always live.
        final int contentHeight = w.height() - DesktopWindow.TITLE_H - 8;
        final int contentTop = w.y() + DesktopWindow.TITLE_H + 4;
        final int originX = w.x() + 4 + app.invCellContentX(0);
        final int originY = contentTop + app.invCellContentY(0, contentHeight);
        // The band's screen-space bounds span the full slot grid, so every one of the 36 slot rows qualifies as
        // visible — the inventory band never scrolls and is never clipped.
        final int bandBottom = contentTop + app.invBandBottom(contentHeight);
        menu.layoutInventory(originX, originY, originY, bandBottom);
    }

    /**
     * Draws the items held in the active inventory slots, plus the hover highlight, inside the desktop's
     * translated/scissored pass right after the windows — so the items sit over the front window's inventory
     * zone. Records {@link #hoveredSlot} so the carried-item and tooltip passes can use it. Coordinates are
     * desktop-local (the caller has already translated by ox()/oy()), which equals slot.x/slot.y here.
     */
    private void renderInventoryItems(final GuiGraphics g, final int lmx, final int lmy, final float partialTick) {
        hoveredSlot = null;
        if (!menu.slotsActive()) {
            return;
        }
        // lmx/lmy and the slot coordinates are both desktop-local (already inside the ox/oy translate).
        // Draw the items directly at the local slot coordinates: delegating to the inherited renderSlot would
        // add leftPos/topPos a second time (leftPos==ox()), double-offsetting the icons from their backgrounds.
        for (final var slot : menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            final net.minecraft.world.item.ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                g.renderItem(stack, slot.x, slot.y);
                g.renderItemDecorations(font, stack, slot.x, slot.y);
            }
            if (lmx >= slot.x && lmx < slot.x + 16 && lmy >= slot.y && lmy < slot.y + 16) {
                hoveredSlot = slot;
                g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x80FFFFFF);
            }
        }
    }

    /**
     * The active inventory slot under an absolute screen point, or {@code null}. Mirrors the container's own
     * hit-test ({@code slot.x + leftPos}, a 16x16 cell, active only), so a click there can be handed to the
     * vanilla container which expects the same geometry.
     */
    @org.jetbrains.annotations.Nullable
    private net.minecraft.world.inventory.Slot slotUnderMouse(final double absX, final double absY) {
        if (!menu.slotsActive()) {
            return null;
        }
        for (final var slot : menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            final int sx = slot.x + leftPos;
            final int sy = slot.y + topPos;
            if (absX >= sx && absX < sx + 16 && absY >= sy && absY < sy + 16) {
                return slot;
            }
        }
        return null;
    }

    /** Draws the carried (cursor) stack at the mouse, above everything. Desktop-local coordinates. */
    private void renderCarried(final GuiGraphics g, final int lmx, final int lmy) {
        final net.minecraft.world.item.ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
            g.renderItem(carried, lmx - 8, lmy - 8);
            g.renderItemDecorations(font, carried, lmx - 8, lmy - 8);
        }
    }

    private void openApp(final String key, final DesktopApp app) {
        // Open at the default size, clamped to the screen — but never below the app's minimum while the
        // screen still has room for it, so the content opens laid out (not collapsed) on a small monitor.
        final int availW = sw() - 16;
        final int availH = sh() - TASKBAR_H - 16;
        final int w = availW >= app.minWidth() ? Math.min(app.defaultWidth(), availW) : availW;
        final int h = availH >= app.minHeight() ? Math.min(app.defaultHeight(), availH) : availH;
        final int x = Math.max(48, (sw() - w) / 2 + windows.size() * 12);
        final int y = Math.max(6, (sh() - TASKBAR_H - h) / 2 + windows.size() * 12);
        windows.add(new DesktopWindow(app, key, x, y, w, h));
    }

    /** Recreates a program from its launcher key, for restoring persisted windows. */
    @org.jetbrains.annotations.Nullable
    private DesktopApp factoryFor(final String key) {
        for (final Launcher l : launchers) {
            if (l.label().equals(key) && l.factory() != null) {
                return l.factory().get();
            }
        }
        return null;
    }

    /** Starts a launcher: a built-in app opens a window; an action-based one (e.g. the NMS) runs its action. */
    private void runLauncher(final Launcher l) {
        if (l.action() != null) {
            l.action().run();
        } else {
            openApp(l.label(), l.factory().get());
        }
    }

    private void bringToFront(final int index) {
        if (index >= 0 && index < windows.size()) {
            windows.add(windows.remove(index));
        }
    }

    private static String trim(final String s, final int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "...";
    }

    @Override
    public void removed() {
        // Remember which programs were open so re-entering the Monitor restores them instead of a blank desktop.
        final java.util.List<SavedWin> save = new java.util.ArrayList<>();
        for (final DesktopWindow w : windows) {
            save.add(new SavedWin(w.appKey(), w.minimized(), w.maximized()));
        }
        if (save.isEmpty()) {
            SAVED_WINDOWS.remove(host);
        } else {
            SAVED_WINDOWS.put(host, save);
        }
        if (active == this) {
            active = null;
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
