/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.jstech.computronics.blockentity.AbstractComputerBlockEntity;
import dev.jstech.computronics.client.MonitorFrame;
import dev.jstech.computronics.client.theme.MonitorFrameStyle;
import dev.jstech.computronics.gui.layout.DesktopIconLayout;
import dev.jstech.computronics.menu.DesktopMenu;
import dev.jstech.computronics.operation.payload.DeleteFilePayload;
import dev.jstech.computronics.operation.payload.DesktopFilesPayload;
import dev.jstech.computronics.operation.payload.DiskFilesPayload;
import dev.jstech.computronics.operation.payload.MkdirPayload;
import dev.jstech.computronics.operation.payload.MoveFilePayload;
import dev.jstech.computronics.operation.payload.RenameFilePayload;
import dev.jstech.computronics.operation.payload.RequestDesktopFilesPayload;
import dev.jstech.computronics.operation.payload.RequestFileContentPayload;
import dev.jstech.computronics.operation.payload.SaveFilePayload;
import dev.jstech.computronics.operation.payload.SetDesktopPrefsPayload;
import dev.jstech.computronics.operation.payload.SetIconPositionPayload;
import dev.jstech.computronics.os.fs.SystemLayout;
import dev.jstech.core.gui.layout.DesktopZ;
import dev.jstech.core.tier.HardwareEra;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The FULL_DESKTOP shell: a windowed desktop environment that a graphical OS (Frames 95 / XP / 11)
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
    private OsSkin skin;
    /** Per-computer accent override (0 = skin default), brightness, and clock format, from the settings. */
    private int desktopAccent;
    private int desktopBrightness = 100;
    private boolean desktopClock12h;
    /** Frames 11 taskbar layout: centered app strip (default) or left-aligned next to Start. */
    private boolean desktopTaskbarCentered = true;
    /** Frames 11 dark theme: darkens the window chrome (via the skin) and the Start menu. */
    private boolean desktopDarkMode;
    private final List<DesktopWindow> windows = new ArrayList<>();
    private final List<Launcher> launchers = new ArrayList<>();
    private final List<String> installedPrograms = new ArrayList<>();

    // --- Per-OS memory model: the system, its desktop and its services hold their share of the machine's RAM
    // (reserved, from the server) and every open program holds its own, weighed under the running system by the
    // same rule the server applies. A cooperative kernel (Frames 95) is fragile and crashes when overloaded; a
    // preemptive one (XP/11) just refuses. ---
    private int ramTotalMb;
    private int ramReservedMb;
    /** True while the cooperative OS is showing its crash screen; the desktop reboots to an empty session after. */
    private boolean crashing;
    private long crashUntil;

    /**
     * Open windows kept per-computer across leaving and re-entering the Monitor in the same session.
     * Bounded (access-ordered, eldest evicted past the cap) so a long session that visits many computers
     * does not grow this map without limit.
     */
    private static final int MAX_SAVED_DESKTOPS = 16;

    // The live app instances kept per computer while its Monitor is left, so re-entering restores each
    // program's in-progress session (terminal scrollback, an unsaved query) instead of a fresh window.
    private static final java.util.Map<BlockPos, java.util.Map<String, DesktopApp>> SAVED_APPS =
            new java.util.LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        final java.util.Map.Entry<BlockPos, java.util.Map<String, DesktopApp>> eldest) {
                    return size() > MAX_SAVED_DESKTOPS;
                }
            };

    /** Apps a running window asked to launch (e.g. Files opening the Editor); drained by the active desktop. */
    private static final java.util.List<String> PENDING_OPEN = new java.util.ArrayList<>();

    /** Lets a running app request another program be opened on the desktop. */
    public static void requestOpen(final String key) {
        PENDING_OPEN.add(key);
    }

    /** Windows a running app asked to end (the Task Manager); drained by the active desktop. */
    private static final java.util.List<String> PENDING_CLOSE = new java.util.ArrayList<>();

    /** Lets a running app end another program's window, the way a task manager does. */
    public static void requestClose(final String key) {
        PENDING_CLOSE.add(key);
    }

    /** Whether the computer at {@code pos} is on a data network, as its block entity tells the client. */
    public static boolean hostNetworked(final net.minecraft.core.BlockPos pos) {
        final net.minecraft.world.level.Level level = Minecraft.getInstance().level;
        return level != null
                && level.getBlockEntity(pos) instanceof dev.jstech.computronics.os.OsHost computer
                && computer.networkAttached();
    }

    /**
     * A pending open of the explorer at a given folder, kept apart from a plain program key by a separator no
     * program id can contain.
     */
    private static final String OPEN_FILES_AT = "Files\0";

    /** Lets a running app open the explorer already navigated to {@code dir} (a drive, a folder). */
    public static void requestOpenFiles(final String dir) {
        PENDING_OPEN.add(OPEN_FILES_AT + dir);
    }

    /**
     * Forgets every per-machine client cache: the programs' insides kept for the machines of the world the
     * player is leaving, and any open request that never found a desktop. Called on logout, so nothing of
     * one world lingers into the next.
     */
    public static void forgetClientState() {
        SAVED_APPS.clear();
        PENDING_OPEN.clear();
        PENDING_CLOSE.clear();
    }

    /** The launcher labels the active desktop can open (built-in apps plus installed programs). */
    public static java.util.List<String> openableLabels() {
        return active != null ? active.launcherLabels() : java.util.List.of();
    }

    /**
     * Applies an accent/brightness change to the live desktop immediately, so a change in the Settings
     * app shows on the chrome without waiting for the next desktop refresh.
     *
     * @param accent     the accent override ({@code 0} = skin default)
     * @param brightness the screen brightness 0..100
     */
    public static void applyLivePrefs(final int accent, final int brightness, final boolean clock12h,
                                      final String wallpaper, final boolean taskbarCentered,
                                      final boolean darkMode) {
        if (active != null) {
            active.desktopAccent = accent;
            active.desktopBrightness = brightness;
            active.desktopClock12h = clock12h;
            active.desktopWallpaper = wallpaper == null ? "" : wallpaper;
            active.desktopTaskbarCentered = taskbarCentered;
            active.desktopDarkMode = darkMode;
            active.rebuildSkin();
        }
    }

    /** Rebuilds the skin from the current accent + dark-mode prefs (dark applies only to the flat Frames 11). */
    private void rebuildSkin() {
        // The host's era picks the desktop's period look: a Linux desktop on Legacy hardware wears
        // its own era, instead of a modern flat theme on a machine from another decade.
        OsSkin base = OsSkin.forDesktop(desktopId, era());
        if (desktopDarkMode) {
            base = base.darkVariant();
        }
        this.skin = base.withAccent(desktopAccent);
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

    /**
     * Raises the error for a refused action on an installer's own files: they are generated from the
     * medium's stamp, so there is nothing to rename, copy off, delete or overwrite.
     */
    public static void showInstallerLockedError() {
        if (active != null) {
            active.showError("Error", "This file is part of the installer and cannot be changed, copied or deleted."
                    + " Run the setup program to install it.");
        }
    }

    /** Opens a modal error dialog with the given title and message over this desktop. */
    void showError(final String title, final String message) {
        this.popup = new DesktopPopup(title, message, this.font);
    }

    /** A notice from the system itself: it rises over the notification area and goes away on its own. */
    private record Balloon(String title, String body, long until) {
    }

    /** How long a balloon stays up before it fades away, in milliseconds. */
    private static final long BALLOON_MS = 9_000L;
    private static final int BALLOON_W = 152;
    @org.jetbrains.annotations.Nullable
    private Balloon balloon;

    /**
     * Raises a tray balloon. Unlike {@link #showError}, it takes nothing over: the machine is telling the
     * player something, not asking them to answer, so the desktop stays usable underneath it.
     */
    void showBalloon(final String title, final String body) {
        this.balloon = new Balloon(title, body, System.currentTimeMillis() + BALLOON_MS);
    }

    private boolean startOpen;
    /** The Frames 11 Start search box: when non-empty, the pinned grid is replaced by a filtered result list. */
    private final StringBuilder startSearch = new StringBuilder();
    /** Local cursor cached each frame, so menus drawn later in the frame can highlight the hovered entry. */
    private int hoverX;
    private int hoverY;
    /** Programs pinned to the right "places" column of the Frames XP Start menu (drawn there, not on the left). */
    private static final java.util.Set<String> XP_PLACES =
            java.util.Set.of("This PC", "Files", "Settings", "Network");
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
    /** Whether the panel's own menu is up, and where it was raised. */
    private boolean panelCtxOpen;
    private int panelCtxX;
    private int panelCtxY;
    private int deskCtxItem = -1; // index into desktopItems, or -1 for the empty background

    // Drag-and-drop of a desktop icon (a file/folder, or a program launcher) — onto a folder, an open
    // explorer, or a free grid cell.
    // Rubber-band selection: dragging on empty wallpaper sweeps a rectangle and selects every icon
    // it touches. Every desktop does this, and without it there was no way to act on more than one
    // icon at a time.
    private boolean bandActive;
    private double bandStartX;
    private double bandStartY;
    private double bandX;
    private double bandY;
    private final java.util.Set<Integer> selectedIcons = new java.util.LinkedHashSet<>();

    /** The band's rectangle in desktop coordinates: {x, y, w, h}. */
    private int[] bandRect() {
        final int bx = (int) Math.min(bandStartX, bandX);
        final int by = (int) Math.min(bandStartY, bandY);
        return new int[]{bx, by, (int) Math.abs(bandX - bandStartX), (int) Math.abs(bandY - bandStartY)};
    }

    /** Selects every desktop icon whose cell the band currently touches. */
    private void updateBandSelection() {
        selectedIcons.clear();
        final int[] r = bandRect();
        final int perCol = iconsPerColumn(sh());
        final int[] slotCells = computeSlotCells(perCol);
        final int total = Math.min(slotCells.length, launchers.size() + desktopItems.size());
        for (int i = 0; i < total; i++) {
            final int ix = iconXForCell(slotCells[i]);
            final int iy = iconYForCell(slotCells[i]);
            // The icon's clickable cell, the same box the hover highlight uses.
            if (ix + CELL_DX < r[0] + r[2] && ix + CELL_DX + CELL_W > r[0]
                    && iy + CELL_DY < r[1] + r[3] && iy + CELL_DY + CELL_H > r[1]) {
                selectedIcons.add(i);
            }
        }
    }

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

    // The work area (icons, windows, drops) is the desktop minus its panel. Every panel style but GNOME puts
    // the panel at the bottom; GNOME's top bar reserves the top TASKBAR_H pixels instead, so icons start and
    // windows clamp/maximize below it rather than sliding under it.

    /**
     * Whether this desktop wears its period chrome. Derived from the skin's form, which the era already
     * decided, so the panel and the windows can never disagree about which decade they are in.
     */
    private boolean periodPanel() {
        return skin.form() == OsSkin.Form.KDE2 || skin.form() == OsSkin.Form.GNOME1;
    }

    /**
     * The icon set to draw program icons from. A period desktop asks for its own artwork first and falls
     * back to that desktop's modern icons, so a Legacy KDE still looks like KDE even before period icons
     * are drawn for it.
     */
    private String iconSet() {
        return periodPanel()
                ? desktopId.getPath() + ProgramIcons.PERIOD_SUFFIX
                : desktopId.getPath();
    }

    /**
     * Whether the panel sits at the top. Only the modern GNOME shell does that: the GNOME of the Legacy
     * era put its panel at the bottom, and its top bar ("Activities") did not exist for another decade.
     */
    private boolean topPanel() {
        return is(dev.jstech.computronics.os.PanelStyle.GNOME) && !periodPanel();
    }

    /** The first desktop-local row of the work area. */
    private int workTop() {
        return topPanel() ? TASKBAR_H : 0;
    }

    /** One past the last desktop-local row of the work area (the bottom panel's top, or the screen bottom). */
    private int workBottom() {
        return topPanel() ? sh() : sh() - TASKBAR_H;
    }

    /** The pixels reserved for a bottom panel (none under GNOME's top bar). */
    private int bottomReserve() {
        return topPanel() ? 0 : TASKBAR_H;
    }

    // Windows 11 taskbar: each centered item (Start + one per open window) occupies this slot.
    private static final int WIN11_SLOT = 22;
    private static final int WIN11_ICON = 16;
    private static final int MENU_W = 130;
    private static final int BAND_W = 22;
    private static final int MENU_ITEM_H = 18;
    // Frames XP Start: a two-column panel (programs left, system "places" right) with a header and a footer band.
    private static final int XP_MENU_W = 202;
    private static final int XP_HEADER_H = 26;
    /** The orange band the Luna Start menu ran under its user header. */
    private static final int XP_ORANGE_H = 2;
    private static final int XP_FOOTER_H = 18;
    private static final int XP_ROW_H = 16;
    private static final int XP_LEFT_W = 120;
    /** The gap a separator sits in, between the pinned block and the rest of the left column. */
    private static final int XP_SEP_H = 5;
    /** How many of the left column's entries are drawn as pinned (bold) at its top. */
    private static final int XP_PINNED = 2;
    private static final int XP_ALL_ROW_H = 15;
    // Frames 11 Start: a compact floating panel with a search box, a pinned-app grid, and a footer power button.
    // Kept small (5 columns, tight tiles) so even a Mainframe's full app set fits above the taskbar.
    private static final int W11_MENU_W = 172;
    private static final int W11_COLS = 5;
    private static final int W11_TILE_W = 32;
    private static final int W11_TILE_H = 30;
    private static final int W11_SEARCH_H = 14;
    private static final int W11_FOOTER_H = 18;
    // KDE Plasma: a Kickoff-style launcher (places column left, app list right, search on top, session footer).
    private static final int KDE_MENU_W = 214;
    private static final int KDE_SIDE_W = 74;
    private static final int KDE_HEADER_H = 26;
    private static final int KDE_ROW_H = 16;
    private static final int KDE_FOOTER_H = 16;
    // Cinnamon: the Mint menu (favourites rail, categories, app list with a search box).
    private static final int CIN_MENU_W = 236;
    private static final int CIN_RAIL_W = 30;
    private static final int CIN_CATS_W = 84;
    private static final int CIN_HEADER_H = 22;
    private static final int CIN_ROW_H = 16;
    // GNOME: the Activities overview (search, workspace strip, app grid).
    private static final int GN_COLS = 6;
    private static final int GN_TILE_W = 40;
    private static final int GN_TILE_H = 34;
    // Desktop icons sit on a grid wide enough for a name on two lines. The old pitch was narrower than the
    // labels it drew, so "Command Prompt" ran across its neighbour and both names read as one word.
    private static final int ICON_PITCH_Y = 44;
    private static final int ICON_PITCH_X = 50;
    /** The width of the Frames XP Start pill, which the task buttons and its own hit-test both clear. */
    private static final int XP_START_W = 58;
    /** Where the first icon column starts: far enough in that its cell's highlight clears the screen edge. */
    private static final int ICON_ORIGIN_X = 14;
    /** An icon's cell: the box its highlight, its drop outline and its hit-test all use. */
    private static final int CELL_W = 46;
    private static final int CELL_H = 40;
    /** The cell's top-left corner, relative to the icon's own: the 24px icon sits centred in the cell. */
    private static final int CELL_DX = (24 - CELL_W) / 2;
    private static final int CELL_DY = -2;
    /**
     * How wide one line of an icon's label may run before it wraps, how many lines it may take, and how tall
     * a line stands. The names are drawn in the small text a dense panel uses, which is what lets a word like
     * "Calculator" fit its cell whole without the grid having to spread out across the whole desktop.
     */
    private static final int LABEL_W = CELL_W - 2;
    private static final int LABEL_LINES = 2;
    private static final int LABEL_LINE_H = 8;
    private static final String[] DESK_CTX_ICON = {"Open", "Rename", "Delete"};
    private static final String[] DESK_CTX_BG = {"New File", "New Folder", "Personalize", "Refresh"};
    private static final int DESK_CTX_W = 88;
    private static final int DESK_CTX_ITEM_H = 11;
    /**
     * The panel's own menu. Right-clicking a taskbar opens this on every desktop these imitate, and the Task
     * Manager is one entry on it rather than the click's whole meaning. A separator sits before that entry.
     */
    private static final String[] PANEL_CTX = {"Cascade Windows", "Show the Desktop", "-", "Task Manager"};
    private static final int PANEL_CTX_W = 104;

    /** A desktop/start-menu entry that opens an app when clicked. */
    // A launcher either opens a built-in app window (factory) or runs a custom action (e.g. open the
    // NMS, which is a server-side menu rather than a desktop window). Exactly one is non-null.
    /** A desktop launcher: its display label, the program id (icon + identity), and the window factory. */
    private record Launcher(String label, net.minecraft.resources.ResourceLocation programId,
                            java.util.function.Supplier<DesktopApp> factory) {
    }

    /** The desktop environment drawn: the OS's bundled one (Frames) or the Linux package installed. */
    private final ResourceLocation desktopId;
    /** The desktop environment's descriptor (chrome family, bundled apps, native names); null if unknown. */
    @org.jetbrains.annotations.Nullable
    private final dev.jstech.computronics.os.DesktopEnvironmentDef chrome;
    /** The chrome family drawn (panel placement, launcher menu, window behaviour). */
    private final dev.jstech.computronics.os.PanelStyle panel;
    /** The on-disk desktop folder (Users/Public/Desktop on the DOS family, home/player/Desktop on POSIX). */
    private final String desktopDir;

    public DesktopScreen(final DesktopMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
        this.host = menu.hostPos();
        this.monitorPos = menu.monitorPos();
        this.osId = menu.osId();
        this.desktopId = menu.desktopId();
        this.ramTotalMb = menu.ramTotalMb();
        this.ramReservedMb = menu.ramReservedMb();
        this.chrome = dev.jstech.computronics.os.OsRegistry.getDesktop(desktopId);
        this.panel = chrome != null ? chrome.panelStyle() : switch (desktopId.getPath()) {
            case "frames_xp" -> dev.jstech.computronics.os.PanelStyle.FRAMES_XP;
            case "frames_11" -> dev.jstech.computronics.os.PanelStyle.FRAMES_11;
            default -> dev.jstech.computronics.os.PanelStyle.FRAMES_95;
        };
        final dev.jstech.computronics.os.OsDef os =
                dev.jstech.computronics.os.OsRegistry.getOs(osId);
        this.desktopDir = SystemLayout.desktopDirFor(os == null ? null
                : dev.jstech.computronics.os.OsRegistry.getKernel(os.kernelId()));
        this.theme = DesktopTheme.forDesktop(desktopId);
        // A provisional skin: rebuildSkin() refines it with the host's era once the level is reachable.
        this.skin = OsSkin.forDesktop(desktopId);
    }

    private boolean is(final dev.jstech.computronics.os.PanelStyle style) {
        return panel == style;
    }

    /** A Linux desktop environment (bottom-panel KDE/Cinnamon or top-bar GNOME), as opposed to a Frames edition. */
    private boolean linuxDesktop() {
        return is(dev.jstech.computronics.os.PanelStyle.KDE)
                || is(dev.jstech.computronics.os.PanelStyle.GNOME)
                || is(dev.jstech.computronics.os.PanelStyle.CINNAMON);
    }

    /** The desktop environment's display name, for the Start band and menus. */
    private String desktopName() {
        return chrome != null ? chrome.displayName() : desktopId.getPath();
    }

    /** The megabytes a window opened under {@code key} holds: its program's weight under the running system. */
    private int windowRamMb(final String key) {
        final dev.jstech.computronics.os.OsDef os = dev.jstech.computronics.os.OsRegistry.getOs(osId);
        return os == null ? 0 : dev.jstech.computronics.os.OsHost.windowRamMb(key, os, chrome);
    }

    /** What the open windows hold together. */
    private int windowsRamMb() {
        int sum = 0;
        for (final DesktopWindow w : windows) {
            sum += windowRamMb(w.appKey());
        }
        return sum;
    }

    /** Everything held right now: the system's share, its desktop and services, and the open windows. */
    private int ramUsedMb() {
        return ramReservedMb + windowsRamMb();
    }

    /** A cooperative kernel (Frames 95) has no memory protection: overloading it crashes the whole desktop. */
    private boolean isCooperative() {
        return osId.getPath().equals("frames_95");
    }

    /**
     * Whether a window of {@code key} may open now, that is whether its program's weight still fits in the
     * free RAM; otherwise a preemptive OS refuses with the figures and a cooperative one crashes.
     */
    private boolean allowOpen(final String key) {
        if (crashing) {
            return false;
        }
        final int need = windowRamMb(key);
        final int free = ramTotalMb - ramUsedMb();
        if (need <= free) {
            return true;
        }
        if (isCooperative()) {
            crashing = true;
            crashUntil = System.currentTimeMillis() + 4200;
        } else {
            // A refusal the machine can simply report: the desktop is still there, so a balloon says it the
            // way the notification area always did, instead of taking the screen over with a dialog.
            showBalloon("Low on memory", "This computer is running out of RAM for programs. " + key
                    + " needs " + need + " MB and only " + Math.max(0, free) + " MB are free.");
        }
        return false;
    }

    /** Reboots after a crash: the session is lost (windows and their saved state), back to an empty desktop. */
    private void reboot() {
        crashing = false;
        balloon = null;
        windows.clear();
        SAVED_APPS.remove(host);
        startOpen = false;
        popup = null;
    }

    /** The cooperative-kernel crash screen: a classic blue fatal-error page, drawn in desktop-local coords. */
    private void renderCrash(final GuiGraphics g, final int sw, final int sh) {
        g.fill(0, 0, sw, sh, 0xFF0000AA);
        final int cy = sh / 3;
        final String head = " Frames ";
        final int hw = font.width(head) + 6;
        g.fill((sw - hw) / 2, cy - 2, (sw + hw) / 2, cy + 10, 0xFFAAAAAA);
        g.drawString(font, head, (sw - font.width(head)) / 2, cy, 0xFF0000AA, false);
        final String[] lines = {
            "A fatal exception has occurred.",
            "This computer ran out of memory with too many",
            "programs open, and the system became unstable.",
            "",
            "The cooperative kernel cannot recover.",
            "Rebooting...",
        };
        int ly = cy + 20;
        for (final String s : lines) {
            g.drawString(font, s, (sw - font.width(s)) / 2, ly, 0xFFFFFFFF, false);
            ly += 11;
        }
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

    /** Whether the panel's own menu is open. */
    public boolean isPanelMenuOpen() {
        return panelCtxOpen;
    }

    /** Screen position of the centre of the panel menu's {@code label} entry, or null when it is not there. */
    @org.jetbrains.annotations.Nullable
    public int[] panelMenuPoint(final String label) {
        if (!panelCtxOpen) {
            return null;
        }
        for (int i = 0; i < PANEL_CTX.length; i++) {
            if (PANEL_CTX[i].equals(label)) {
                return new int[] {ox() + panelCtxX + PANEL_CTX_W / 2,
                        oy() + panelCtxY + 1 + i * DESK_CTX_ITEM_H + DESK_CTX_ITEM_H / 2};
            }
        }
        return null;
    }

    /** Screen position of a point on the panel clear of Start and of the task buttons: its empty stretch. */
    public int[] emptyPanelPoint() {
        return new int[] {ox() + Math.max(TASK_X, taskStripRight(sw()) - 8),
                oy() + sh() - TASKBAR_H / 2};
    }

    /** The labels of the windows this desktop has open, in the order they were opened. */
    public List<String> openWindowLabels() {
        final List<String> out = new ArrayList<>();
        for (final DesktopWindow w : windows) {
            out.add(w.appKey());
        }
        return out;
    }

    /** The Start menu entries, top to bottom, as labelled for the player. */
    public List<String> launcherLabels() {
        final List<String> out = new ArrayList<>();
        for (final Launcher l : launchers) {
            out.add(l.label());
        }
        return out;
    }

    /** Whether {@code app} runs in the front (focused) window; what a recipe viewer's drop or transfer targets. */
    public boolean isFront(final DesktopApp app) {
        final DesktopWindow front = frontWindow();
        return front != null && front.app() == app;
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
        return ox() + (is(dev.jstech.computronics.os.PanelStyle.FRAMES_11) ? 4 + WIN11_SLOT / 2 : 30);
    }

    public int startButtonY() {
        // GNOME's "Activities" launcher lives in the top bar; every other panel sits at the bottom.
        return oy() + (topPanel() ? TASKBAR_H / 2 : sh() - TASKBAR_H / 2);
    }

    /** Screen coordinates of the centre of the {@code index}-th Start menu entry (valid while it is open). */
    public int startMenuItemX() {
        return ox() + startMenuX() + BAND_W + 30;
    }

    /**
     * Screen x of the centre of the {@code index}-th Start menu entry. Frames XP lays its entries in two
     * columns (programs left, places right), so the column depends on the entry.
     */
    public int startMenuItemX(final int index) {
        if (panel == dev.jstech.computronics.os.PanelStyle.FRAMES_XP
                && index >= 0 && index < launchers.size()) {
            final boolean place = XP_PLACES.contains(launchers.get(index).label());
            final int colX = ox() + startMenuX() + (place ? XP_LEFT_W + 3 : 3);
            final int colW = place ? XP_MENU_W - XP_LEFT_W - 6 : XP_LEFT_W - 6;
            return colX + colW / 2;
        }
        return startMenuItemX();
    }

    public int startMenuItemY(final int index) {
        final int tbY = sh() - TASKBAR_H;
        if (panel == dev.jstech.computronics.os.PanelStyle.FRAMES_XP
                && index >= 0 && index < launchers.size()) {
            final Launcher target = launchers.get(index);
            final boolean place = XP_PLACES.contains(target.label());
            final List<Launcher> column = place ? xpRightLaunchers() : xpLeftLaunchers();
            final int row = Math.max(0, column.indexOf(target));
            // The left column has a separator under its pinned block, so its rows are not a plain multiple.
            final int rowY = place ? row * XP_ROW_H : xpLeftRowY(row);
            return oy() + tbY - startMenuHeight() + XP_HEADER_H + XP_ORANGE_H + 3 + rowY + XP_ROW_H / 2;
        }
        return oy() + tbY - startMenuHeight() + 4 + index * MENU_ITEM_H + MENU_ITEM_H / 2;
    }

    /**
     * The host computer's hardware era, read from its block entity so the monitor frame matches the chassis.
     * Never null: a host that cannot name an era yet (a rack whose unit the client has not received) gets the
     * Standard frame, since the frame geometry is asked for every frame by the recipe viewer as well.
     */
    private HardwareEra era() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(host)
                instanceof dev.jstech.computronics.os.OsHost be) {
            final HardwareEra era = be.displayEra();
            if (era != null) {
                return era;
            }
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
        final int hour24 = totalMin / 60;
        final int minute = totalMin % 60;
        if (desktopClock12h) {
            final int h12 = hour24 % 12 == 0 ? 12 : hour24 % 12;
            return String.format(java.util.Locale.ROOT, "%d:%02d %s", h12, minute, hour24 < 12 ? "AM" : "PM");
        }
        return String.format(java.util.Locale.ROOT, "%02d:%02d", hour24, minute);
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

        // Resolve the era skin before the first frame. The panel's placement now follows the skin (a
        // period desktop panels at the bottom), so waiting for the desktop payload to arrive would draw
        // one frame with the panel on the wrong edge and then jump.
        rebuildSkin();

        buildLaunchers();

        // Restore the windows that were open when this computer's Monitor was last left.
        // The open windows are the machine's, not this client's: they arrive from the server with the
        // desktop listing requested below, and are restored in applyWindows. The app instances kept per
        // computer (SAVED_APPS) are only the programs' insides — scrollback, an unsaved query — and are
        // reattached to the restored windows when they are still around.

        // Become the active desktop and fetch the desktop-folder listing for the background icons.
        active = this;
        requestDesktop();
    }

    /**
     * Restores the windows the machine has open, once, when the server hands them over. Later refreshes
     * of the desktop listing send the same payload again and must not open everything a second time.
     */
    public static void applyWindows(
            final dev.jstech.computronics.operation.payload.DesktopWindowsPayload payload) {
        final DesktopScreen screen = active;
        if (screen == null || !screen.host.equals(payload.host()) || screen.windowsRestored) {
            return;
        }
        screen.windowsRestored = true;
        if (!screen.windows.isEmpty()) {
            return; // the player already opened something before the layout arrived; keep theirs
        }
        final java.util.Map<String, DesktopApp> savedApps = SAVED_APPS.get(screen.host);
        for (final dev.jstech.computronics.os.OpenWindow ow : payload.toOpenWindows()) {
            DesktopApp app = savedApps != null ? savedApps.get(ow.key()) : null;
            final boolean restored = app != null;
            if (app == null) {
                app = screen.factoryFor(ow.key());
            }
            if (app == null) {
                continue; // a program that is no longer installed simply does not come back
            }
            app.applySkin(screen.skin);
            if (restored) {
                app.onRestored(); // a kept instance re-asks the server for what may have changed meanwhile
            }
            final DesktopWindow w = new DesktopWindow(app, ow.key(), ow.x(), ow.y(), ow.w(), ow.h());
            // Clamp into the current work area: the monitor may be a different size from the one the
            // layout was left on, and a title bar off-screen is a window nobody can reach.
            w.moveTo(ow.x(), ow.y(), screen.workTop(), screen.sw(), screen.workBottom());
            w.setMinimized(ow.minimized());
            w.setMaximized(ow.maximized());
            screen.windows.add(w);
        }
    }

    /** Whether the machine's window layout has been applied to this desktop instance. */
    private boolean windowsRestored;

    /** The layout the machine was last told about, so only a real change is pushed to it. */
    private String pushedLayout = "";

    /**
     * Tells the machine which programs it has open, whenever that changes. Without this the machine only
     * learned its layout when the desktop closed, so anything reading its memory ledger — the Task Manager
     * above all — saw a computer running nothing while the player had five windows in front of them.
     */
    private void pushWindowsIfChanged() {
        if (!windowsRestored || powerCycling) {
            return;
        }
        final StringBuilder signature = new StringBuilder();
        for (final DesktopWindow w : windows) {
            signature.append(w.appKey()).append(w.minimized() ? '-' : '+').append(';');
        }
        final String now = signature.toString();
        if (now.equals(pushedLayout)) {
            return;
        }
        pushedLayout = now;
        PacketDistributor.sendToServer(
                dev.jstech.computronics.operation.payload.DesktopWindowsPayload.of(host, snapshotWindows()));
    }

    /**
     * Set when this desktop is closing because the player shut the machine down or restarted it, so the
     * layout is NOT handed back to the machine on the way out: the server has just cleared it, and a
     * late arrival would resurrect windows on a machine that is off or rebooting.
     */
    private boolean powerCycling;

    /** The current windows as the machine should remember them: floating bounds plus their state. */
    private java.util.List<dev.jstech.computronics.os.OpenWindow> snapshotWindows() {
        final java.util.List<dev.jstech.computronics.os.OpenWindow> out =
                new java.util.ArrayList<>(windows.size());
        for (final DesktopWindow w : windows) {
            out.add(new dev.jstech.computronics.os.OpenWindow(
                    w.appKey(), w.floatX(), w.floatY(), w.floatW(), w.floatH(), w.minimized(), w.maximized()));
        }
        return out;
    }

    /**
     * (Re)builds the launcher rail from the single program registry: every registered Frames app that opens a
     * window and is present on this computer. A pre-installed app is gated here by its host scope and OS rank
     * (e.g. the Network Manager on the Mainframe, Frames XP or newer); an installed app is gated on the server
     * (its id already sits in {@link #installedPrograms} only when it passed). No more per-program branches.
     */
    private void buildLaunchers() {
        launchers.clear();
        final boolean isMainframe = hostIs(
                dev.jstech.computronics.blockentity.MainframeBlockEntity.class);
        final boolean isCraftingComputer = hostIs(
                dev.jstech.computronics.blockentity.CraftingComputerBlockEntity.class);
        // A rack shows the desktop of the server mounted in it, so a rack host IS a server session.
        final boolean isServer = hostIs(
                dev.jstech.computronics.blockentity.ServerRackBlockEntity.class);
        final boolean isClusterManager = hostIs(
                dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity.class);
        final int rank = dev.jstech.computronics.os.OsRegistry.osVersionRank(osId);
        final dev.jstech.computronics.os.OsDef os =
                dev.jstech.computronics.os.OsRegistry.getOs(osId);
        final dev.jstech.computronics.os.Platform platform =
                os != null ? os.platform() : dev.jstech.computronics.os.Platform.FRAMES;
        for (final dev.jstech.computronics.os.ProgramSpec spec
                : dev.jstech.computronics.os.OsRegistry.programs()) {
            if (spec.kind() != dev.jstech.computronics.os.ProgramKind.APP
                    || !spec.platforms().contains(platform)
                    || !ProgramClient.hasWindow(spec.id())) {
                continue;
            }
            if (spec.preinstalled()) {
                // Built-in apps are present when the desktop environment bundles them; gate by host and OS version.
                if (chrome != null && !chrome.bundles(spec.id())) {
                    continue;
                }
                if (!hostScopeAllows(spec.hostScope(), isMainframe, isCraftingComputer, isServer, isClusterManager)) {
                    continue;
                }
                if (rank != 0 && rank < spec.minOsRank()) {
                    continue;
                }
            } else if (!installedPrograms.contains(spec.id().getPath())) {
                continue; // installed apps: the server already gated them into installedPrograms
            }
            final ProgramClient.DesktopAppFactory factory = ProgramClient.factory(spec.id());
            // Apps receive the desktop id (their skin/icon key); the Frames editions' id equals their OS id.
            launchers.add(new Launcher(launcherLabel(spec), spec.id(),
                    () -> factory.create(host, monitorPos, desktopId)));
        }
    }

    /** The program id for an open window's app key (its launcher label), for the taskbar icon; generic if none. */
    private net.minecraft.resources.ResourceLocation programIdForLabel(final String label) {
        for (final Launcher l : launchers) {
            if (l.label().equals(label)) {
                return l.programId();
            }
        }
        // A window whose program has no launcher (the Task Manager) still shows its own icon on the panel.
        final dev.jstech.computronics.os.ProgramSpec spec = chrome == null ? null : chrome.programFor(label);
        return spec != null ? spec.id()
                : net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("jsc", "generic");
    }

    /** Whether the linked host computer's block entity is (an instance of) {@code type}. */
    private boolean hostIs(final Class<?> type) {
        return Minecraft.getInstance().level != null
                && type.isInstance(Minecraft.getInstance().level.getBlockEntity(host));
    }

    /** Whether a program's host scope permits it on this computer. */
    private static boolean hostScopeAllows(final dev.jstech.computronics.os.HostScope scope,
                                           final boolean isMainframe, final boolean isCraftingComputer,
                                           final boolean isServer, final boolean isClusterManager) {
        return switch (scope) {
            case ANY -> true;
            case MAINFRAME -> isMainframe;
            case CRAFTING_COMPUTER -> isCraftingComputer;
            case SERVER -> isServer;
            case CLUSTER_MANAGEMENT_COMPUTER -> isClusterManager;
        };
    }

    /**
     * The rail label for a program: the desktop environment's native name for it (Dolphin, Konsole, Nautilus...),
     * its own display name otherwise; the shell reads "Megashell" on Frames 11.
     */
    private String launcherLabel(final dev.jstech.computronics.os.ProgramSpec spec) {
        if (chrome != null) {
            return chrome.launcherLabel(spec); // the rule the server resolves a window back to its program with
        }
        if (spec.id().getPath().equals("command_prompt")
                && is(dev.jstech.computronics.os.PanelStyle.FRAMES_11)) {
            return "Megashell";
        }
        return spec.displayName();
    }

    /** Requests the desktop folder's files so they can be drawn as background icons. */
    private void requestDesktop() {
        PacketDistributor.sendToServer(new RequestDesktopFilesPayload(host));
    }

    /**
     * Refreshes the open desktop's server-derived state (installed programs included), so a program
     * installed or removed while the desktop is up gets its launcher without closing the monitor. Called
     * after any action that can change the installed set (a shell command, an install disc, Settings).
     */
    public static void refreshActive() {
        if (active != null) {
            active.requestDesktop();
        }
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
        active.desktopAccent = payload.prefs().accent();
        active.desktopBrightness = payload.prefs().brightness();
        active.desktopClock12h = payload.prefs().clock12h();
        active.desktopTaskbarCentered = payload.prefs().taskbarCentered();
        active.desktopDarkMode = payload.prefs().darkMode();
        active.rebuildSkin();
        active.iconCells.clear();
        for (final DesktopFilesPayload.WireIconCell cell : payload.iconCells()) {
            active.iconCells.put(cell.key(), cell.cell());
        }
        // Refresh the installed-program launchers whenever the installed set changes, so ANY installable
        // program (NMS, Minesweeper, Storage Insights, ...) gets its launcher the moment it is installed.
        final java.util.Set<String> before = new java.util.HashSet<>(active.installedPrograms);
        active.installedPrograms.clear();
        active.installedPrograms.addAll(payload.programs());
        if (!before.equals(new java.util.HashSet<>(active.installedPrograms))) {
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
        // The desktop paints everything itself instead of running the container's render pass, so it posts the
        // two container render events that pass would post: a recipe viewer draws its ingredient list beside the
        // monitor from them (its plain screen-render hook skips container screens on purpose).
        NeoForge.EVENT_BUS.post(new ContainerScreenEvent.Render.Background(this, g, mouseX, mouseY));
        // Drain any cross-app open requests (e.g. Files asked to launch the Editor).
        if (!PENDING_OPEN.isEmpty()) {
            for (final String key : PENDING_OPEN) {
                if (key.startsWith(OPEN_FILES_AT)) {
                    // This PC asked for a drive or a folder to be opened in the explorer.
                    if (allowOpen("Files")) {
                        openApp("Files", new FilesApp(host, desktopId.getPath(),
                                key.substring(OPEN_FILES_AT.length()), monitorPos));
                    }
                    continue;
                }
                final DesktopApp app = factoryFor(key);
                if (app != null && allowOpen(key)) {
                    openApp(key, app);
                }
            }
            PENDING_OPEN.clear();
        }
        // Drain any request to end a window (the Task Manager), newest first so ending a repeated program
        // closes the one on top rather than the oldest copy of it.
        if (!PENDING_CLOSE.isEmpty()) {
            for (final String key : PENDING_CLOSE) {
                for (int i = windows.size() - 1; i >= 0; i--) {
                    if (windows.get(i).appKey().equals(key)) {
                        windows.remove(i);
                        break;
                    }
                }
            }
            PENDING_CLOSE.clear();
        }
        pushWindowsIfChanged();
        // Keep the inventory slots glued to the focused Network Interactor window this frame (per-frame, so a
        // dragged window does not leave its slots a tick behind).
        syncInventorySlots();
        final int sw = sw();
        final int sh = sh();
        final int ox = ox();
        final int oy = oy();
        final int lmx = mouseX - ox;
        final int lmy = mouseY - oy;
        // Cache the local cursor so the Start-menu draw (called deeper in this frame) can highlight the hovered row.
        this.hoverX = lmx;
        this.hoverY = lmy;
        final HardwareEra eraNow = era();

        // The host computer's hardware-era monitor frame wraps the desktop glass, then translate so the desktop
        // draws in local (0,0)-(sw,sh) coordinates.
        MonitorFrame.renderBody(g, ox, oy, sw, sh, eraNow, font);
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        g.enableScissor(ox, oy, ox + sw, oy + sh);

        WallpaperPainter.paint(g, sw, sh, desktopId, eraNow, desktopWallpaper);

        // A cooperative OS that ran out of memory shows its crash screen, then reboots to an empty session.
        if (crashing) {
            if (System.currentTimeMillis() >= crashUntil) {
                reboot();
            } else {
                renderCrash(g, sw, sh);
                g.disableScissor();
                g.pose().popPose();
                return;
            }
        }

        // Desktop icons: program launchers first, then the desktop folder's files and folders, laid
        // out in columns (top-down, then left-to-right) like a Windows desktop. Each icon's cell comes
        // from the free-positioning layout (a pinned cell, else the next auto-flow cell).
        final int total = launchers.size() + desktopItems.size();
        final int perCol = iconsPerColumn(sh);
        final int[] slotCells = computeSlotCells(perCol);
        final int deskDropTarget = deskDragging ? iconSlotAt(deskDragX, deskDragY, perCol) : -1;
        // The selected icon's full, wrapped label is drawn last (after every icon) so it sits on top of the
        // icon below it instead of being clipped by it.
        String selLabelText = null;
        int selLabelX = 0;
        int selLabelY = 0;
        // Each desktop layer draws at its own strictly-increasing Z (DesktopZ): the depth buffer keeps a back
        // layer behind a front one, so a back layer's batched text (an icon label) can never paint over a
        // front layer (an open window). Flushing the text batch between layers does not work — g.flush() is a
        // no-op outside a managed draw in 1.21.1 — which is why the icon-label-over-window bug kept returning.
        g.pose().pushPose();
        g.pose().translate(0, 0, DesktopZ.ICONS);
        for (int i = 0; i < total; i++) {
            final int ix = iconXForCell(slotCells[i]);
            final int iy = iconYForCell(slotCells[i]);
            // Icons draw at DesktopZ.ICONS and the Start menu at DesktopZ.MENU, so the menu covers them via the
            // depth buffer — the icons behind it stay drawn (they must not vanish) and just sit under the panel.
            final int cellX = ix + CELL_DX;
            final int cellY = iy + CELL_DY;
            if (i == selectedIcon || selectedIcons.contains(i)) {
                g.fill(cellX, cellY, cellX + CELL_W, cellY + CELL_H, 0x66000080);
            } else if (lmx >= cellX && lmx < cellX + CELL_W && lmy >= cellY && lmy < cellY + CELL_H
                    && !deskDragging) {
                // Hover feedback so the player sees which icon the cursor is over.
                g.fill(cellX, cellY, cellX + CELL_W, cellY + CELL_H, 0x28FFFFFF);
            }
            // Green drop-target outline on the folder under the cursor while dragging a real
            // file/folder icon (a launcher has no file to move into a folder, so it lights none).
            if (deskDragging && deskDragSlot >= launchers.size()
                    && i == deskDropTarget && i >= launchers.size() && i != deskDragSlot
                    && desktopItems.get(i - launchers.size()).directory()) {
                g.fill(cellX, cellY, cellX + CELL_W, cellY + 1, 0xFF49E07A);
                g.fill(cellX, cellY + CELL_H - 1, cellX + CELL_W, cellY + CELL_H, 0xFF49E07A);
                g.fill(cellX, cellY, cellX + 1, cellY + CELL_H, 0xFF49E07A);
                g.fill(cellX + CELL_W - 1, cellY, cellX + CELL_W, cellY + CELL_H, 0xFF49E07A);
            }
            final String label;
            if (i < launchers.size()) {
                ProgramIcons.draw(g, ix, iy, 24, 22, launchers.get(i).programId(), iconSet());
                label = launchers.get(i).label();
            } else {
                final int di = i - launchers.size();
                final DiskFilesPayload.WireFile f = desktopItems.get(di);
                drawDesktopIcon(g, ix, iy, f);
                label = di == deskRenaming ? deskRenameBuf + "_" + deskRenameExt : baseName(f.path());
            }
            if (i == selectedIcon) {
                // Defer the full label to a pass after every icon so nothing overdraws it.
                selLabelText = label;
                selLabelX = ix;
                selLabelY = iy;
            } else {
                // The name under the icon: centred, wrapped inside its own cell over at most two lines, and
                // cut with an ellipsis past that. A name wider than the cell used to run across its neighbour,
                // which is how "Network" and "Command Prompt" came to read as one word.
                int ly = iy + 23;
                final java.util.List<String> lines = wrapLabel(label, labelFontWidth());
                for (int li = 0; li < lines.size() && li < LABEL_LINES; li++) {
                    final String line = li == LABEL_LINES - 1 && lines.size() > LABEL_LINES
                            ? fitLabelLine(lines.get(li) + "...")
                            : fitLabelLine(lines.get(li));
                    drawIconLabel(g, line, ix + 12, ly, theme.iconText(), theme.textShadow());
                    ly += LABEL_LINE_H;
                }
            }
        }
        // Windows-style: the selected icon reveals its full name, wrapped, on a selection background.
        if (selLabelText != null) {
            int ly = selLabelY + 23;
            for (final String line : wrapLabel(selLabelText, labelFontWidth())) {
                final int lw = dev.jstech.core.client.gui.component.Texts.smallWidth(font, line);
                final int lcx = selLabelX + 12 - lw / 2;
                g.fill(lcx - 2, ly - 1, lcx + lw + 2, ly + LABEL_LINE_H, 0xE0000080);
                dev.jstech.core.client.gui.component.Texts.small(g, font, line, lcx, ly, 0xFFFFFFFF);
                ly += LABEL_LINE_H;
            }
        }
        g.pose().popPose();

        // Each window draws in a depth band of its own: an item is a model standing well in front of the pose
        // it is drawn at, so windows sharing one depth painted their items over each other (see DesktopItems).
        final DesktopWindow front = frontWindow();
        for (int i = 0; i < windows.size(); i++) {
            final DesktopWindow w = windows.get(i);
            if (w.minimized()) {
                continue;
            }
            g.pose().pushPose();
            g.pose().translate(0, 0, DesktopZ.windowZ(i, windows.size()));
            w.setFocused(w == front);
            w.render(g, font, skin, lmx, lmy, partialTick, sw, sh, bottomReserve(), workTop());
            g.pose().popPose();
        }

        // Real container-slot items for the focused Network Interactor window's inventory zone, over the
        // window the app already drew the slot backgrounds for.
        g.pose().pushPose();
        g.pose().translate(0, 0, DesktopZ.INVENTORY);
        renderInventoryItems(g, lmx, lmy, partialTick);
        g.pose().popPose();

        g.pose().pushPose();
        g.pose().translate(0, 0, DesktopZ.TASKBAR);

        final int tbY = sh - TASKBAR_H;
        final String osp = desktopId.getPath();
        if (is(dev.jstech.computronics.os.PanelStyle.FRAMES_11)) {
            // Windows 11 taskbar: dark bar, centered Start + app icons with an active indicator, clock right.
            renderWin11Taskbar(g, tbY, sw, lmx, lmy);
        } else if (periodPanel()) {
            renderPeriodPanel(g, tbY, sw, sh, lmx, lmy);
        } else if (is(dev.jstech.computronics.os.PanelStyle.GNOME)) {
            renderGnomeTopBar(g, sw, lmx, lmy);
        } else if (linuxDesktop()) {
            renderLinuxPanel(g, tbY, sw, sh, lmx, lmy);
        } else {
            // Taskbar background — 95 bevelled grey, XP Luna gradient.
            if (osp.equals("frames_xp")) {
                g.fillGradient(0, tbY, sw, sh, 0xFF4A86D4, 0xFF1C4D9C);
                g.fill(0, tbY, sw, tbY + 1, 0xFF8FBCEC);
            } else {
                g.fill(0, tbY, sw, sh, theme.taskbar());
                g.fill(0, tbY, sw, tbY + 1, 0xFFFFFFFF);
            }
            // Start button — distinct per Frames version, each with its own glyph.
            if (osp.equals("frames_xp")) {
                drawXpStart(g, tbY, sh);
            } else {
                final int sbW = 54;
                g.fill(4, tbY + 3, 4 + sbW, sh - 3, theme.startButton());
                bevel(g, 4, tbY + 3, sbW, TASKBAR_H - 6, 0xFFFFFFFF, 0xFF808080);
                // Four-pane flag logo.
                g.fill(8, tbY + 8, 11, tbY + 11, 0xFFE0454A);
                g.fill(12, tbY + 8, 15, tbY + 11, 0xFF49B84B);
                g.fill(8, tbY + 12, 11, tbY + 15, 0xFF3C74D6);
                g.fill(12, tbY + 12, 15, tbY + 15, 0xFFE6B928);
                g.drawString(font, "Start", 18, tbY + 8, 0xFF000000, false);
            }
            final int taskRight = taskStripRight(sw);
            final int btnW = taskButtonW(sw);
            final DesktopWindow taskFront = frontWindow();
            for (int i = 0; i < windows.size(); i++) {
                final int bx = taskButtonX(i, sw);
                if (bx + btnW > taskRight) {
                    break;
                }
                final DesktopWindow w = windows.get(i);
                // The window in front reads as a pushed-in button, the way a taskbar has always said which
                // program you are actually looking at.
                taskButton(g, bx, tbY + 3, btnW, TASKBAR_H - 6, osp, w == taskFront && !w.minimized());
                ProgramIcons.draw(g, bx + 4, tbY + 6, 12, 12, programIdForLabel(w.appKey()), iconSet());
                // No shadow: the taskbar button name sits on a solid button, where a shadow only muddies it
                // (a dark blob behind the dark 95 text, a halo behind the light XP text).
                g.drawString(font, trim(w.app().title(), taskTitleChars(btnW)), bx + 20, tbY + 8,
                        theme.startText(), false);
            }
            // The notification area, dressed in each version's own frame.
            final int trayX = trayLeft(sw);
            if (osp.equals("frames_xp")) {
                g.fillGradient(trayX, tbY + 2, sw, sh - 2, 0xFF1A53C4, 0xFF0D3590);
                g.fill(trayX, tbY + 2, trayX + 1, sh - 2, 0xFF4A83E6); // the lit left edge
                g.fill(trayX + 1, tbY + 2, trayX + 2, sh - 2, 0xFF0A2C7A); // and its inset shadow
                drawTray(g, tbY, sw, 0xFFFFFFFF);
            } else if (osp.equals("frames_95")) {
                g.fill(trayX, tbY + 3, sw - 2, sh - 3, theme.taskbar());
                bevel(g, trayX, tbY + 3, sw - 2 - trayX, TASKBAR_H - 6, 0xFF808080, 0xFFFFFFFF); // sunken
                drawTray(g, tbY, sw, theme.startText());
            } else {
                drawTray(g, tbY, sw, theme.startText());
            }
        }
        g.pose().popPose(); // close the TASKBAR layer

        // A tray balloon sits above the panel and under the menus, so opening Start covers it.
        if (balloon != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, DesktopZ.TASKBAR + 10);
            renderBalloon(g, tbY, sw);
            g.pose().popPose();
        }
        // The figures behind the notification area, while the cursor rests on it.
        if (!topPanel()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, DesktopZ.TASKBAR + 8);
            drawTrayTip(g, tbY, sw);
            g.pose().popPose();
        }

        // Menus (Start + desktop context), above the taskbar.
        if (startOpen || deskCtxOpen || panelCtxOpen) {
            g.pose().pushPose();
            g.pose().translate(0, 0, DesktopZ.MENU);
            if (startOpen) {
                renderStartMenu(g, tbY);
            }
            if (panelCtxOpen) {
                renderPanelContext(g, lmx, lmy);
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
                final int gx = cx + CELL_DX;
                final int gy = cy + CELL_DY;
                g.fill(gx, gy, gx + CELL_W, gy + 1, 0x804C84F0);
                g.fill(gx, gy + CELL_H - 1, gx + CELL_W, gy + CELL_H, 0x804C84F0);
                g.fill(gx, gy, gx + 1, gy + CELL_H, 0x804C84F0);
                g.fill(gx + CELL_W - 1, gy, gx + CELL_W, gy + CELL_H, 0x804C84F0);
            }
            // Drag ghost: a label trailing the cursor for the icon being moved.
            // (the rubber band is drawn below, outside the icon-drag branch)
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

        // The rubber band, over the wallpaper and its icons: a translucent fill with a solid outline,
        // the way every desktop draws one.
        if (bandActive) {
            final int[] r = bandRect();
            g.pose().pushPose();
            g.pose().translate(0, 0, DesktopZ.ICONS + 1);
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0x334C84F0);
            g.fill(r[0], r[1], r[0] + r[2], r[1] + 1, 0xCC4C84F0);
            g.fill(r[0], r[1] + r[3] - 1, r[0] + r[2], r[1] + r[3], 0xCC4C84F0);
            g.fill(r[0], r[1], r[0] + 1, r[1] + r[3], 0xCC4C84F0);
            g.fill(r[0] + r[2] - 1, r[1], r[0] + r[2], r[1] + r[3], 0xCC4C84F0);
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

        // Brightness: a per-computer dim over the whole surface (100 = none, 0 = deeply dimmed), below any popup.
        if (desktopBrightness < 100) {
            final int alpha = Math.min(210, (100 - desktopBrightness) * 21 / 10);
            g.pose().pushPose();
            g.pose().translate(0, 0, DesktopZ.POPUP - 1);
            g.fill(0, 0, sw, sh, alpha << 24);
            g.pose().popPose();
        }

        // A focused app's modal dialog renders here, above every item icon and window, so the dialog and its
        // own dim cover and darken the icons instead of them piercing through at their blit depth.
        final DesktopWindow modalWin = frontWindow();
        if (modalWin != null && modalWin.app().modalActive()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, DesktopZ.POPUP);
            modalWin.app().renderModal(g, font,
                    modalWin.x() + 4, modalWin.y() + DesktopWindow.TITLE_H + 4,
                    modalWin.width() - 8, modalWin.height() - DesktopWindow.TITLE_H - 8, lmx, lmy);
            g.pose().popPose();
        }

        // A modal error dialog sits over the whole desktop: dim the surface, then draw it on top.
        if (popup != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, DesktopZ.POPUP);
            popup.renderIn(g, new dev.jstech.core.client.gui.component.UiContext(skin, font, lmx, lmy, 0f), 0, 0, sw, sh);
            g.pose().popPose();
        }
        // The power dialog rides at the same height: it is the one choice that ends the session.
        if (powerOpen) {
            g.pose().pushPose();
            g.pose().translate(0, 0, DesktopZ.POPUP);
            renderPowerDialog(g, sw, sh, lmx, lmy);
            g.pose().popPose();
        }
        // The taskbar's own menu sits above the windows it acts on.
        if (taskMenuIndex >= 0) {
            g.pose().pushPose();
            g.pose().translate(0, 0, DesktopZ.POPUP);
            renderTaskMenu(g, lmx, lmy);
            g.pose().popPose();
        }
        g.pose().popPose(); // close the (ox, oy) desktop-origin translate

        // The container pass posts its foreground event with the pose at the gui origin and the depth test off,
        // so a listener draws over the finished screen without fighting the desktop's layered depth.
        RenderSystem.disableDepthTest();
        g.pose().pushPose();
        g.pose().translate(leftPos, topPos, 0);
        NeoForge.EVENT_BUS.post(new ContainerScreenEvent.Render.Foreground(this, g, mouseX, mouseY));
        g.pose().popPose();
        RenderSystem.enableDepthTest();
    }

    // The desktop paints its whole surface in the render() override above and does not call super.render(), so
    // the container's background pass is unused — the inventory items and cursor are drawn by render() instead.
    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
    }

    private int iconsPerColumn(final int sh) {
        // The work area is the screen minus one panel band, wherever that panel sits.
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
        return ICON_ORIGIN_X + DesktopIconLayout.col(packedCell) * ICON_PITCH_X;
    }

    private int iconYForCell(final int packedCell) {
        return workTop() + 10 + DesktopIconLayout.row(packedCell) * ICON_PITCH_Y;
    }

    /** The desktop icon slot under a desktop-local point, or {@code -1} for the empty background. */
    private int iconSlotAt(final double mx, final double my, final int perCol) {
        final int[] cells = computeSlotCells(perCol);
        for (int i = 0; i < cells.length; i++) {
            final int ix = iconXForCell(cells[i]);
            final int iy = iconYForCell(cells[i]);
            if (mx >= ix + CELL_DX && mx <= ix + CELL_DX + CELL_W
                    && my >= iy + CELL_DY && my <= iy + CELL_DY + CELL_H) {
                return i;
            }
        }
        return -1;
    }

    /** The packed grid cell ({@code col << 16 | row}) under a desktop-local point, clamped to the grid. */
    private int cellAt(final double mx, final double my, final int perCol) {
        final int col = Math.max(0, (int) Math.floor((mx - (ICON_ORIGIN_X - ICON_PITCH_X / 2.0)) / ICON_PITCH_X));
        final int row = Math.max(0, Math.min(perCol - 1,
                (int) Math.floor((my - workTop() - (10 - ICON_PITCH_Y / 2.0)) / ICON_PITCH_Y)));
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
        if (dy >= workTop() && dy < workBottom() && overWallpaper(dx, dy)) {
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
        if (dy >= workTop() && dy < workBottom() && overWallpaper(dx, dy)) {
            moveExplorerFile(origin, null, dragged, desktopDir);
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
            openApp("Files", new FilesApp(host, desktopId.getPath(), f.path(), monitorPos));
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

    /**
     * The panel's menu, drawn in the desktop's own chrome: the bevelled plate on the older Frames, a light
     * rounded panel on the newer one and on the Linux desktops. A separator row is drawn as a rule.
     */
    private void renderPanelContext(final GuiGraphics g, final int hoverMx, final int hoverMy) {
        final int mx = panelCtxX;
        final int my = panelCtxY;
        final int mh = PANEL_CTX.length * DESK_CTX_ITEM_H + 2;
        final boolean light = luminance(skin.text()) > 140;
        final int bg = light ? 0xFF262B36 : 0xFFE8E8EC;
        final int fg = light ? 0xFFE7E9EF : 0xFF1A2230;
        g.fill(mx - 1, my - 1, mx + PANEL_CTX_W + 1, my + mh + 1, light ? 0xFF11151E : 0xFF000000);
        g.fill(mx, my, mx + PANEL_CTX_W, my + mh, bg);
        g.fill(mx, my, mx + PANEL_CTX_W, my + 1, light ? 0xFF3A4150 : 0xFFFFFFFF);
        final int hover = panelCtxItemAt(hoverMx, hoverMy);
        int iy = my + 1;
        for (int k = 0; k < PANEL_CTX.length; k++) {
            if ("-".equals(PANEL_CTX[k])) {
                g.fill(mx + 4, iy + DESK_CTX_ITEM_H / 2, mx + PANEL_CTX_W - 4,
                        iy + DESK_CTX_ITEM_H / 2 + 1, light ? 0xFF3A4150 : 0xFFB6BAC4);
            } else {
                if (k == hover) {
                    g.fill(mx + 1, iy, mx + PANEL_CTX_W - 1, iy + DESK_CTX_ITEM_H, skin.accent());
                }
                g.drawString(font, PANEL_CTX[k], mx + 4, iy + 2, k == hover ? 0xFFFFFFFF : fg, false);
            }
            iy += DESK_CTX_ITEM_H;
        }
    }

    /** The panel-menu entry under a desktop-local point, or {@code -1}; a separator never answers. */
    private int panelCtxItemAt(final double mx, final double my) {
        if (!panelCtxOpen || mx < panelCtxX || mx > panelCtxX + PANEL_CTX_W) {
            return -1;
        }
        final int rel = (int) Math.floor((my - (panelCtxY + 1)) / (double) DESK_CTX_ITEM_H);
        if (rel < 0 || rel >= PANEL_CTX.length || "-".equals(PANEL_CTX[rel])) {
            return -1;
        }
        return rel;
    }

    /** Raises the panel's menu at a point, clamped so it stays on the desktop. */
    private void openPanelMenu(final int atX, final int panelY) {
        final int mh = PANEL_CTX.length * DESK_CTX_ITEM_H + 2;
        panelCtxOpen = true;
        panelCtxX = Math.max(2, Math.min(sw() - PANEL_CTX_W - 2, atX));
        // Above a bottom panel, below a top one: the menu never covers the bar it came from.
        panelCtxY = topPanel() ? panelY + TASKBAR_H + 2 : panelY - mh - 2;
    }

    /** Runs a panel-menu entry. Every one of them does something: none is there for decoration. */
    private void runPanelMenu(final int index) {
        switch (PANEL_CTX[index]) {
            case "Cascade Windows" -> cascadeWindows();
            case "Show the Desktop" -> {
                for (final DesktopWindow w : windows) {
                    w.setMinimized(true);
                }
            }
            case "Task Manager" -> openTaskManager();
            default -> {
            }
        }
    }

    /** Steps the open windows down and to the right from the work area's corner, the way a cascade does. */
    private void cascadeWindows() {
        int step = 0;
        for (final DesktopWindow w : windows) {
            if (w.minimized()) {
                continue;
            }
            w.setMaximized(false);
            w.moveTo(16 + step * 12, workTop() + 10 + step * 12, workTop(), sw(), workBottom());
            step++;
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
            final String newPath = desktopDir + "/" + newName;
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
        PacketDistributor.sendToServer(new SaveFilePayload(host, desktopDir + "/" + name, ""));
        requestDesktop();
    }

    private void newDeskFolder() {
        final String name = uniqueDeskName("New Folder", "");
        deskPendingRename = name;
        PacketDistributor.sendToServer(new MkdirPayload(host, desktopDir + "/" + name));
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

    /** The overall width of the Start menu panel, which differs per Frames version. */
    private int startMenuW() {
        if (periodPanel()) {
            return MENU_W; // the period launcher is a narrow list, whatever its desktop does today
        }
        return switch (panel) {
            case FRAMES_XP -> XP_MENU_W;
            case FRAMES_11 -> W11_MENU_W;
            case KDE -> KDE_MENU_W;
            case GNOME -> sw();
            case CINNAMON -> CIN_MENU_W;
            default -> MENU_W;
        };
    }

    private int startMenuHeight() {
        if (periodPanel()) {
            // A period launcher is a small program list, not a Plasma menu and not a full-screen
            // overview: it is sized by its own contents, like the classic launcher it is.
            return Math.max(4, launchers.size()) * MENU_ITEM_H + 8;
        }
        switch (panel) {
            case KDE -> {
                return KDE_HEADER_H + Math.max(6, launchers.size()) * KDE_ROW_H + KDE_FOOTER_H + 8;
            }
            case GNOME -> {
                return sh() - TASKBAR_H; // the overview covers the whole desktop below the top bar
            }
            case CINNAMON -> {
                return CIN_HEADER_H + Math.max(5, launchers.size()) * CIN_ROW_H + 10;
            }
            default -> {
            }
        }
        return switch (desktopId.getPath()) {
            // Two columns: the taller of programs (left) and places (right) sets the body height.
            case "frames_xp" -> XP_HEADER_H + XP_ORANGE_H
                    + Math.max(xpLeftColumnH(), xpRightLaunchers().size() * XP_ROW_H)
                    + XP_FOOTER_H + 6;
            // A pinned grid sized to the full app set (so the panel does not resize as the search filters it).
            // Layout: 6 top pad + search + 5 + 9 (Pinned label) + rows + 5 + footer + 5 bottom pad.
            case "frames_11" -> {
                final int gridRows = Math.max(1, (launchers.size() + W11_COLS - 1) / W11_COLS);
                yield 6 + W11_SEARCH_H + 5 + 9 + gridRows * W11_TILE_H + 5 + W11_FOOTER_H + 5;
            }
            default -> launchers.size() * MENU_ITEM_H + 6 + MENU_ITEM_H + 8;
        };
    }

    /**
     * The left edge of the app-icon strip on the Windows 11 taskbar: right after the Start button, so the whole
     * [Start + apps] group moves together with the taskbar alignment. Single source for render and hit-test.
     */
    private int win11AppsX(final int sw) {
        return win11StartX(sw) + WIN11_SLOT;
    }

    /**
     * The Frames 11 Start button's left edge. Centered mode places it as the leftmost of the centered
     * [Start + open apps] group (so the whole group is centered); left mode pins it to the corner. This is
     * why the taskbar-alignment setting actually moves the Start button.
     */
    private int win11StartX(final int sw) {
        if (!desktopTaskbarCentered) {
            return 4;
        }
        final int group = (windows.size() + 1) * WIN11_SLOT;
        return Math.max(4, (sw - group) / 2);
    }

    /** The Start menu's left edge: left-pinned on 95/XP; on Frames 11 it opens over the Start button (clamped). */
    private int startMenuX() {
        if (is(dev.jstech.computronics.os.PanelStyle.FRAMES_11)) {
            final int w = startMenuW();
            final int startCenter = win11StartX(sw()) + WIN11_SLOT / 2;
            return Math.max(4, Math.min(sw() - w - 4, startCenter - w / 2));
        }
        if (topPanel()) {
            return 0; // the Activities overview spans the desktop
        }
        return 4;
    }

    /** The Start menu's top edge for the given taskbar top: flush on 95/XP, floating with a gap on Frames 11. */
    private int startMenuY(final int tbY) {
        if (is(dev.jstech.computronics.os.PanelStyle.FRAMES_11)) {
            return Math.max(2, tbY - startMenuHeight() - 6); // float above the taskbar, but never off the top
        }
        if (topPanel()) {
            return TASKBAR_H; // the overview hangs below the top bar
        }
        return tbY - startMenuHeight();
    }

    /** The launchers shown in the XP left "programs" column: everything that is not a fixed system place. */
    private List<Launcher> xpLeftLaunchers() {
        final List<Launcher> out = new ArrayList<>();
        for (final Launcher l : launchers) {
            if (!XP_PLACES.contains(l.label())) {
                out.add(l);
            }
        }
        return out;
    }

    /** The y of the {@code i}-th left-column row, measured from the top of the Start menu's body. */
    private int xpLeftRowY(final int i) {
        final int n = xpLeftLaunchers().size();
        final int pinned = Math.min(XP_PINNED, n);
        return i * XP_ROW_H + (i >= pinned && n > pinned ? XP_SEP_H : 0);
    }

    /** The y of the "All Programs" row, measured from the top of the Start menu's body. */
    private int xpAllRowY() {
        return xpLeftRowY(xpLeftLaunchers().size()) + XP_SEP_H;
    }

    /** How tall the left column runs: its rows, its separators, and the "All Programs" row under them. */
    private int xpLeftColumnH() {
        return xpAllRowY() + XP_ALL_ROW_H;
    }

    /** The left edge of the footer's "Turn Off Computer" entry, shared by the drawing and the hit-test. */
    private int xpFooterOffX(final int x, final int w) {
        return x + w - 6 - (font.width("Turn Off Computer") + 14);
    }

    /** The left edge of the footer's "Log Off" entry, immediately before the Turn Off one. */
    private int xpFooterLogX(final int x, final int w) {
        return xpFooterOffX(x, w) - 8 - (font.width("Log Off") + 14);
    }

    /** The launchers shown in the XP right "places" column: the fixed system entries, in launcher order. */
    private List<Launcher> xpRightLaunchers() {
        final List<Launcher> out = new ArrayList<>();
        for (final Launcher l : launchers) {
            if (XP_PLACES.contains(l.label())) {
                out.add(l);
            }
        }
        return out;
    }

    /** The launchers matching the Frames 11 Start search box (all of them when the box is empty). */
    private List<Launcher> w11Filtered() {
        final String q = startSearch.toString().toLowerCase(java.util.Locale.ROOT).trim();
        if (q.isEmpty()) {
            return launchers;
        }
        final List<Launcher> out = new ArrayList<>();
        for (final Launcher l : launchers) {
            if (l.label().toLowerCase(java.util.Locale.ROOT).contains(q)) {
                out.add(l);
            }
        }
        return out;
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

        // Start: follows the taskbar alignment (centered as the leftmost of the centered group, or left corner),
        // with a hover highlight — the four-pane blue logo, no text.
        final int startX = win11StartX(sw);
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
                    programIdForLabel(w.appKey()), "frames_11");
            final int cx = ix + WIN11_SLOT / 2;
            if (active) {
                g.fill(cx - 6, bottom - 2, cx + 6, bottom - 1, 0xFF4C84F0); // wide pill = focused
            } else if (!w.minimized()) {
                g.fill(cx - 2, bottom - 2, cx + 2, bottom - 1, 0xFF8A93A4); // short dot = open
            }
        }

        drawTray(g, tbY, sw, 0xFFE6E8EC);
    }

    /** The balloon's box in desktop-local coordinates, or null when none is up. Draw and hit-test share it. */
    @org.jetbrains.annotations.Nullable
    private int[] balloonRect(final int tbY, final int sw) {
        if (balloon == null) {
            return null;
        }
        final int lines = font.split(Component.literal(balloon.body()), BALLOON_W - 12).size();
        final int h = 15 + lines * 9 + 5;
        final int x = Math.max(4, sw - BALLOON_W - 6);
        return new int[] {x, tbY - h - 7, BALLOON_W, h};
    }

    /** The classic notification balloon: pale yellow, a blue "i", a title, a line or two, and a close box. */
    private void renderBalloon(final GuiGraphics g, final int tbY, final int sw) {
        if (balloon != null && System.currentTimeMillis() > balloon.until()) {
            balloon = null;
        }
        final int[] r = balloonRect(tbY, sw);
        if (r == null || balloon == null) {
            return;
        }
        final int x = r[0];
        final int y = r[1];
        final int w = r[2];
        final int h = r[3];
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        g.fill(x, y, x + w, y + h, 0xFFFFFFE1);
        // The tail, pointing down at the notification area it came from: a bordered wedge, drawn as an
        // outline first and the pale fill inset into it, so it carries the same 1px edge as the box.
        final int tail = x + w - 42;
        for (int i = 0; i < 7; i++) {
            g.fill(tail + i - 1, y + h + i, tail + 14 - i, y + h + i + 1, 0xFF000000);
        }
        for (int i = 0; i < 6; i++) {
            g.fill(tail + i, y + h + i, tail + 12 - i, y + h + i + 1, 0xFFFFFFE1);
        }
        g.fill(tail, y + h - 1, tail + 12, y + h, 0xFFFFFFE1); // the tail opens into the balloon
        // The round blue "i" and the title beside it.
        g.fill(x + 6, y + 5, x + 14, y + 13, 0xFF1C53C9);
        g.fill(x + 7, y + 4, x + 13, y + 14, 0xFF1C53C9);
        g.fill(x + 9, y + 6, x + 11, y + 7, 0xFFFFFFFF);
        g.fill(x + 9, y + 8, x + 11, y + 12, 0xFFFFFFFF);
        g.drawString(font, Component.literal(balloon.title()).withStyle(net.minecraft.ChatFormatting.BOLD),
                x + 18, y + 5, 0xFF000000, false);
        int ly = y + 16;
        for (final net.minecraft.util.FormattedCharSequence line
                : font.split(Component.literal(balloon.body()), w - 12)) {
            g.drawString(font, line, x + 6, ly, 0xFF303030, false);
            ly += 9;
        }
        // The close box, the one part of a balloon anyone ever clicked.
        final int bx = x + w - 12;
        g.fill(bx, y + 4, bx + 8, y + 12, 0xFFE8E8CA);
        outline(g, bx, y + 4, 8, 8, 0xFF6A6A55);
        g.drawString(font, "x", bx + 2, y + 5, 0xFF303030, false);
    }

    /** A click on a live balloon: its close box dismisses it, and the rest of it absorbs the click. */
    private boolean balloonClick(final double mx, final double my, final int tbY, final int sw) {
        final int[] r = balloonRect(tbY, sw);
        if (r == null) {
            return false;
        }
        if (mx < r[0] || mx > r[0] + r[2] || my < r[1] || my > r[1] + r[3]) {
            return false;
        }
        balloon = null;
        return true;
    }

    /** The memory meter's text, "used/total MB", shared by the panels so they can keep room for it. */
    private String ramMeterText() {
        return ramUsedMb() + "/" + ramTotalMb + " MB";
    }

    // --- The notification area, the same on every panel: whether the machine is on a network, a speaker, a
    // memory bar and the clock. It is deliberately narrow — a wordy meter here left no room for the task
    // buttons — and the figures behind the bar are one hover away. ---
    /** The task strip: where it starts, the gap between buttons, and the width one may run to. */
    private static final int TASK_X = 64;
    private static final int TASK_GAP = 4;
    private static final int TASK_MIN_W = 44;
    private static final int TASK_MAX_W = 84;

    /**
     * How wide each task button is: the strip shared out between the open windows, never wider than is
     * comfortable nor narrower than a name can be read in. A taskbar that keeps one fixed width simply runs
     * out of room, which is what hid the open programs behind the notification area.
     */
    private int taskButtonW(final int sw) {
        final int strip = Math.max(0, taskStripRight(sw) - TASK_X);
        final int count = Math.max(1, windows.size());
        return Math.max(TASK_MIN_W, Math.min(TASK_MAX_W, strip / count - TASK_GAP));
    }

    /** The left edge of the {@code i}-th task button. */
    private int taskButtonX(final int i, final int sw) {
        return TASK_X + i * (taskButtonW(sw) + TASK_GAP);
    }

    /** The task button under a desktop-local x, or {@code -1} where the strip is empty or has run out. */
    private int taskIndexAt(final double mx, final int sw) {
        final int w = taskButtonW(sw);
        final int idx = (int) ((mx - TASK_X) / (w + TASK_GAP));
        if (idx < 0 || idx >= windows.size()) {
            return -1;
        }
        final int bx = taskButtonX(idx, sw);
        return mx <= bx + w && bx + w <= taskStripRight(sw) ? idx : -1;
    }

    /** How many characters of a title fit on a task button of {@code w} pixels, after its icon. */
    private static int taskTitleChars(final int w) {
        return Math.max(3, (w - 24) / 6);
    }

    /** The padding at each end of the notification area. */
    private static final int TRAY_PAD = 5;
    private static final int TRAY_ICON = 9;
    private static final int TRAY_GAP = 4;
    private static final int RAM_BAR_W = 26;
    private static final int RAM_BAR_H = 6;

    /** How wide the status group runs: the network icon, the speaker and the memory bar. */
    private int trayStatusWidth() {
        return TRAY_ICON + TRAY_GAP + TRAY_ICON + TRAY_GAP + RAM_BAR_W;
    }

    /** How wide the whole notification area runs: the status group, the clock, and the padding around them. */
    private int trayWidth() {
        return TRAY_PAD + trayStatusWidth() + TRAY_GAP + font.width(clockText()) + TRAY_PAD;
    }

    /** The left edge of the notification area on a panel {@code sw} wide. */
    private int trayLeft(final int sw) {
        return sw - trayWidth();
    }

    /** Where a panel's task buttons must stop: clear of the notification area at its right end. */
    private int taskStripRight(final int sw) {
        return trayLeft(sw) - 4;
    }

    /**
     * The whole notification area: the status group and then the clock, right-aligned on the panel. The
     * icons take their tone from the panel's own text, which is the one thing that already knows whether
     * this panel is a dark band or a light one.
     */
    private void drawTray(final GuiGraphics g, final int panelY, final int sw, final int textColor) {
        final int x = trayLeft(sw) + TRAY_PAD;
        drawTrayStatus(g, x, panelY, textColor);
        g.drawString(font, clockText(), x + trayStatusWidth() + TRAY_GAP, panelY + 8, textColor, false);
    }

    /** The status group alone, for a panel that puts its clock somewhere else of its own. */
    private void drawTrayStatus(final GuiGraphics g, final int x, final int panelY, final int textColor) {
        final boolean light = luminance(textColor) > 140;
        final int iconY = panelY + (TASKBAR_H - TRAY_ICON) / 2;
        drawNetworkIcon(g, x, iconY, networkAttached(), light);
        drawVolumeIcon(g, x + TRAY_ICON + TRAY_GAP, iconY, light);
        drawRamBar(g, x + 2 * (TRAY_ICON + TRAY_GAP), panelY + (TASKBAR_H - RAM_BAR_H) / 2);
    }

    /** How bright an opaque colour reads, 0 to 255, for deciding what tone sits well on it. */
    private static int luminance(final int color) {
        return (((color >> 16) & 0xFF) * 30 + ((color >> 8) & 0xFF) * 59 + (color & 0xFF) * 11) / 100;
    }

    /** Whether the host computer is on a data network right now, as its block entity tells the client. */
    private boolean networkAttached() {
        final net.minecraft.world.level.Level level = Minecraft.getInstance().level;
        return level != null
                && level.getBlockEntity(host) instanceof dev.jstech.computronics.os.OsHost computer
                && computer.networkAttached();
    }

    /**
     * The network icon: two linked machines, greyed and badged when this computer is on no network. It is
     * the one status in the tray that says something true about the machine rather than decorating it.
     */
    private static void drawNetworkIcon(final GuiGraphics g, final int x, final int y, final boolean up,
                                        final boolean light) {
        final int frame = up ? (light ? 0xFF2058D8 : 0xFF1A3A78) : 0xFF6E7686;
        final int screen = up ? (light ? 0xFFCFE4FF : 0xFF9FC0F0) : 0xFFB6BAC4;
        g.fill(x + 4, y, x + 9, y + 4, frame);
        g.fill(x + 5, y + 1, x + 8, y + 3, screen);
        g.fill(x, y + 5, x + 5, y + 9, frame);
        g.fill(x + 1, y + 6, x + 4, y + 8, screen);
        if (!up) {
            g.fill(x + 5, y + 5, x + 9, y + 9, 0xFFD03A2A);
            g.fill(x + 6, y + 6, x + 8, y + 7, 0xFFFFFFFF);
        }
    }

    /** The speaker, with the two arcs a volume icon has always had. */
    private static void drawVolumeIcon(final GuiGraphics g, final int x, final int y, final boolean light) {
        final int c = light ? 0xFFE8EEF8 : 0xFF3A4150;
        g.fill(x, y + 3, x + 2, y + 6, c);
        g.fill(x + 2, y + 2, x + 3, y + 7, c);
        g.fill(x + 3, y + 1, x + 4, y + 8, c);
        g.fill(x + 6, y + 3, x + 7, y + 6, c);
        g.fill(x + 8, y + 1, x + 9, y + 8, c);
    }

    /** The memory bar: a dark trough filled green shading to amber, and red once the machine is nearly full. */
    private void drawRamBar(final GuiGraphics g, final int x, final int y) {
        g.fill(x, y, x + RAM_BAR_W, y + RAM_BAR_H, 0xFF2A2F3A);
        g.fill(x + 1, y + 1, x + RAM_BAR_W - 1, y + RAM_BAR_H - 1, 0xFF11151E);
        final int innerW = RAM_BAR_W - 2;
        final int used = ramUsedMb();
        if (ramTotalMb <= 0 || used <= 0) {
            return;
        }
        final int fillW = (int) Math.min(innerW, (long) innerW * used / ramTotalMb);
        final boolean nearlyFull = used * 100L >= ramTotalMb * 95L;
        for (int px = 0; px < fillW; px++) {
            final float t = innerW <= 1 ? 0f : (float) px / (innerW - 1);
            final int color = nearlyFull ? 0xFFEF6A5A : blend(0xFF5FE07A, 0xFFF0B23A, t);
            g.fill(x + 1 + px, y + 1, x + 2 + px, y + RAM_BAR_H - 1, color);
        }
    }

    /** Linear blend of two opaque colours, {@code t} from the first (0) to the second (1). */
    private static int blend(final int from, final int to, final float t) {
        final int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        final int gr = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        final int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    /** The figures behind the tray, shown while the cursor rests on it: the link and the memory. */
    private void drawTrayTip(final GuiGraphics g, final int panelY, final int sw) {
        if (hoverY < panelY || hoverX < trayLeft(sw)) {
            return;
        }
        final String link = networkAttached() ? "Network connected" : "No network";
        final String mem = "RAM " + ramMeterText();
        final int w = Math.max(font.width(link), font.width(mem)) + 8;
        final int h = 22;
        final int x = Math.max(2, sw - w - 2);
        final int y = panelY - h - 2;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF101318);
        g.fill(x, y, x + w, y + h, 0xFFF2F4F8);
        g.drawString(font, link, x + 4, y + 3, 0xFF202430, false);
        g.drawString(font, mem, x + 4, y + 12, 0xFF505868, false);
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
                            final String osp, final boolean active) {
        switch (osp) {
            case "frames_xp" -> {
                if (active) {
                    // Pushed in: the gradient runs the other way, with a shadow along the top edge.
                    g.fillGradient(x, y, x + w, y + h, 0xFF1E4FBC, 0xFF3670DC);
                    g.fill(x, y, x + w, y + 1, 0x40000000);
                } else {
                    g.fillGradient(x, y, x + w, y + h, 0xFF5B95DD, 0xFF2C5FA8);
                    g.fill(x, y, x + w, y + 1, 0x33FFFFFF);
                }
                outline(g, x, y, w, h, 0xFF1A4CBF);
            }
            case "frames_11" -> g.fill(x, y, x + w, y + h, 0xFFE3E5EE);
            default -> {
                g.fill(x, y, x + w, y + h, theme.taskButton());
                // The classic bevel inverts when the button is pressed: dark on top, light underneath.
                bevel(g, x, y, w, h, active ? 0xFF808080 : 0xFFFFFFFF, active ? 0xFFFFFFFF : 0xFF808080);
            }
        }
    }

    /**
     * The Frames XP Start button: a glossy green pill flush with the left edge and rounded at its right end,
     * carrying the four-pane flag and the word in italics. It is the one control of that desktop everybody
     * pictures, and a plain green rectangle never read as it.
     */
    private void drawXpStart(final GuiGraphics g, final int tbY, final int sh) {
        final int top = tbY + 1;
        final int bottom = sh - 1;
        final int h = bottom - top;
        final int round = 6;
        xpStartBand(g, 0, top, XP_START_W - round, h);
        for (int i = 0; i < round; i++) {
            final double d = i + 1;
            final int inset = (int) Math.round(round - Math.sqrt(Math.max(0.0, round * round - d * d)));
            xpStartBand(g, XP_START_W - round + i, top + inset, 1, h - inset * 2);
        }
        g.fill(2, top + 1, XP_START_W - round, top + 1 + h / 3, 0x3AFFFFFF); // the gloss along the top
        // The flag: four panes, the top row lifted a pixel so the whole thing leans the way it always did.
        final int fx = 7;
        final int fy = tbY + 8;
        g.fill(fx, fy + 1, fx + 4, fy + 4, 0xFFE0454A);
        g.fill(fx + 5, fy, fx + 9, fy + 3, 0xFF49B84B);
        g.fill(fx, fy + 5, fx + 4, fy + 8, 0xFF3C74D6);
        g.fill(fx + 5, fy + 4, fx + 9, fy + 7, 0xFFE6B928);
        g.drawString(font, net.minecraft.network.chat.Component.literal("start")
                        .withStyle(net.minecraft.ChatFormatting.BOLD, net.minecraft.ChatFormatting.ITALIC),
                fx + 13, tbY + 8, 0xFFFFFFFF, true);
    }

    /** One vertical slice of the Start pill: light crown, body, and a darker foot, as the Luna button had. */
    private static void xpStartBand(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        final int q = Math.max(1, h / 4);
        g.fillGradient(x, y, x + w, y + q, 0xFF8FDD72, 0xFF57C04B);
        g.fillGradient(x, y + q, x + w, y + h - q, 0xFF4CB745, 0xFF2E9A33);
        g.fillGradient(x, y + h - q, x + w, y + h, 0xFF2E9A33, 0xFF24802A);
    }

    /** Whether a desktop-local point is on the bottom panel's Start button. */
    private boolean startButtonHit(final double mx, final double my, final int tbY) {
        if (is(dev.jstech.computronics.os.PanelStyle.FRAMES_XP)) {
            return my >= tbY && mx >= 0 && mx <= XP_START_W;
        }
        return my >= tbY + 3 && mx >= 4 && mx <= 58;
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
        return desktopName();
    }

    /**
     * Draws the Start menu. Each Frames version has its OWN layout, not just its own palette: 95 is the
     * classic side-band vertical list, XP is a two-column programs/places panel, and 11 is a centered
     * floating panel with a search box and a pinned-app grid.
     */
    private void renderStartMenu(final GuiGraphics g, final int tbY) {
        if (periodPanel()) {
            // A period desktop had a plain vertical launcher, not a modern Plasma menu and certainly not
            // the GNOME overview, which belongs to a shell released a decade later.
            renderStartMenuPeriod(g, tbY);
            return;
        }
        switch (panel) {
            case FRAMES_XP -> renderStartMenuXp(g, tbY);
            case FRAMES_11 -> renderStartMenu11(g, tbY);
            case KDE -> renderStartMenuKde(g, tbY);
            case GNOME -> renderOverviewGnome(g);
            case CINNAMON -> renderStartMenuCinnamon(g, tbY);
            default -> renderStartMenu95(g, tbY);
        }
    }

    /**
     * The launcher of a Legacy-era Unix desktop: a raised panel with a coloured side band carrying the
     * desktop's name and one vertical list of programs. Drawn from the skin's own primitives, so it
     * carries the same relief as that skin's windows and panel instead of the modern flat chrome.
     */
    private void renderStartMenuPeriod(final GuiGraphics g, final int tbY) {
        final int x = startMenuX();
        final int h = startMenuHeight();
        final int w = startMenuW();
        final int y = tbY - h;
        skin.panel(g, x, y, w, h);

        // Side band with the desktop's name, rotated, the way the launchers of that period carried it.
        g.fill(x + 2, y + 2, x + 2 + BAND_W, y + h - 2, skin.accent());
        g.pose().pushPose();
        g.pose().translate(x + BAND_W - 3, y + h - 8, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-90));
        g.drawString(font, desktopName(), 0, 0, 0xFFFFFFFF, false);
        g.pose().popPose();

        final int itemX = x + BAND_W + 6;
        int my = y + 4;
        for (final Launcher l : launchers) {
            final boolean hov = hoverY >= my && hoverY < my + MENU_ITEM_H
                    && hoverX >= itemX && hoverX < x + w;
            skin.listRow(g, itemX, my, x + w - 4 - itemX, MENU_ITEM_H, hov, false);
            ProgramIcons.draw(g, itemX + 2, my + 1, 14, 14, programIdForLabel(l.label()), iconSet());
            g.drawString(font, l.label(), itemX + 20, my + 4,
                    hov ? skin.listRowText(true) : skin.text(), false);
            my += MENU_ITEM_H;
        }
    }

    /** Frames 95: the classic Start menu with a rotated OS-name side band and a single vertical program list. */
    private void renderStartMenu95(final GuiGraphics g, final int tbY) {
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
            final boolean hov = hoverY >= my && hoverY < my + MENU_ITEM_H && hoverX >= itemX && hoverX < x + MENU_W;
            if (hov) {
                g.fill(itemX, my, x + MENU_W - 2, my + MENU_ITEM_H, theme.titleActive());
            }
            ProgramIcons.draw(g, itemX, my, 16, 14, l.programId(), iconSet());
            g.drawString(font, l.label(), itemX + 20, my + 3, hov ? 0xFFFFFFFF : theme.menuText(), false);
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

    /** Whether the open launcher has a live search box (Frames 11's Start, GNOME's Activities overview). */
    private boolean searchableStart() {
        // The period launcher is a plain program list with no search field, so it is not searchable
        // even though the modern GNOME shell it replaces is.
        return is(dev.jstech.computronics.os.PanelStyle.FRAMES_11)
                || (is(dev.jstech.computronics.os.PanelStyle.GNOME) && !periodPanel());
    }

    // ---------------------------------------------------------------------------------------------
    // Linux desktop environments: panels
    // ---------------------------------------------------------------------------------------------

    /**
     * The KDE Plasma / Cinnamon bottom panel: a dark bar with the launcher button on the left, one task button
     * per open window (icon + title, accent underline when focused) at the same 88px pitch the classic taskbar
     * uses (so the shared click handling applies), and the clock and process meter on the right.
     */
    private void renderLinuxPanel(final GuiGraphics g, final int tbY, final int sw, final int sh,
                                  final int lmx, final int lmy) {
        final boolean kde = is(dev.jstech.computronics.os.PanelStyle.KDE);
        g.fill(0, tbY, sw, sh, theme.taskbar());
        g.fill(0, tbY, sw, tbY + 1, theme.taskbarEdge());
        // Launcher button.
        final boolean startHot = startOpen || (lmx >= 4 && lmx <= 58 && lmy >= tbY);
        if (startHot) {
            g.fill(4, tbY + 2, 58, sh - 2, 0x22FFFFFF);
        }
        if (kde) {
            g.fill(8, tbY + 5, 22, tbY + 19, theme.startButton());
            g.drawString(font, "K", 12, tbY + 8, 0xFFFFFFFF, false);
            g.drawString(font, "Apps", 26, tbY + 8, theme.startText(), false);
        } else {
            g.fill(8, tbY + 5, 22, tbY + 19, theme.startButton());
            g.fill(11, tbY + 8, 19, tbY + 16, 0xFFFFFFFF);
            g.fill(13, tbY + 10, 17, tbY + 14, theme.startButton());
            g.drawString(font, "Menu", 26, tbY + 8, theme.startText(), false);
        }
        // Task buttons.
        final int taskRight = taskStripRight(sw);
        final int btnW = taskButtonW(sw);
        final DesktopWindow front = frontWindow();
        for (int i = 0; i < windows.size(); i++) {
            final int bx = taskButtonX(i, sw);
            if (bx + btnW > taskRight) {
                break;
            }
            final DesktopWindow w = windows.get(i);
            final boolean active = w == front && !w.minimized();
            g.fill(bx, tbY + 3, bx + btnW, sh - 3, active ? 0x30FFFFFF : theme.taskButton());
            if (active) {
                g.fill(bx, sh - 4, bx + btnW, sh - 3, theme.startButton());
            }
            ProgramIcons.draw(g, bx + 3, tbY + 4, 16, 16, programIdForLabel(w.appKey()), iconSet());
            g.drawString(font, trim(w.app().title(), taskTitleChars(btnW)), bx + 22, tbY + 8,
                    theme.startText(), false);
        }
        drawTray(g, tbY, sw, theme.startText());
    }

    /**
     * The panel of a Legacy-era Unix desktop, at the bottom for both KDE and GNOME. It is drawn entirely
     * out of the skin's own primitives — a raised launcher stud, raised task buttons, a sunken clock —
     * so the panel is made of the same relief the windows are, instead of the flat modern band.
     */
    private void renderPeriodPanel(final GuiGraphics g, final int tbY, final int sw, final int sh,
                                   final int lmx, final int lmy) {
        final boolean kde = skin.form() == OsSkin.Form.KDE2;
        skin.statusBar(g, 0, tbY, sw, TASKBAR_H);

        // Launcher: KDE's K, GNOME's footprint. Both are a raised square stud, not a wide Start slab.
        final boolean startHot = startOpen || (lmx >= 4 && lmx <= 58 && lmy >= tbY);
        skin.button(g, font, 4, tbY + 3, 54, TASKBAR_H - 6, kde ? "K  Apps" : "▲  Menu",
                startHot, startOpen, false);

        // Task buttons: pressed when that window is the one in front, exactly as a period panel showed it.
        // The strip's origin and its shared-out width are the ones the taskbar click handler tests against,
        // so the button a player sees and the button they hit are the same rectangle.
        final int taskRight = taskStripRight(sw);
        final int btnW = taskButtonW(sw);
        final DesktopWindow front = frontWindow();
        for (int i = 0; i < windows.size(); i++) {
            final int bx = taskButtonX(i, sw);
            if (bx + btnW > taskRight) {
                break;
            }
            final DesktopWindow w = windows.get(i);
            final boolean active = w == front && !w.minimized();
            final boolean hot = lmx >= bx && lmx <= bx + btnW && lmy >= tbY;
            skin.button(g, font, bx, tbY + 3, btnW, TASKBAR_H - 6, "", hot, active, false);
            ProgramIcons.draw(g, bx + 3, tbY + 5, 12, 12, programIdForLabel(w.appKey()), iconSet());
            g.drawString(font, trim(w.app().title(), taskTitleChars(btnW)), bx + 19,
                    tbY + 8 + (active ? 1 : 0), skin.text(), false);
        }

        // A sunken well on the right: the period panels all recessed their status area rather than
        // floating the text on the band.
        final int trayX = trayLeft(sw);
        skin.field(g, trayX, tbY + 4, sw - trayX - 3, TASKBAR_H - 8, false);
        drawTray(g, tbY, sw, skin.text());
    }

    /**
     * The GNOME top bar: "Activities" on the left (lit while the overview is open), the clock centered, and the
     * process meter on the right. It occupies the top {@code TASKBAR_H} pixels.
     */
    private void renderGnomeTopBar(final GuiGraphics g, final int sw, final int lmx, final int lmy) {
        g.fill(0, 0, sw, TASKBAR_H, theme.taskbar());
        g.fill(0, TASKBAR_H - 1, sw, TASKBAR_H, theme.taskbarEdge());
        final boolean hot = startOpen || (lmx < 64 && lmy < TASKBAR_H);
        if (hot) {
            g.fill(4, 3, 62, TASKBAR_H - 3, 0x22FFFFFF);
        }
        g.drawString(font, "Activities", 8, 8, theme.startText(), false);
        final String clock = clockText();
        g.drawString(font, clock, (sw - font.width(clock)) / 2, 8, theme.startText(), false);
        // GNOME keeps its clock in the middle, so only the status group sits at the right end.
        drawTrayStatus(g, sw - TRAY_PAD - trayStatusWidth(), 0, theme.startText());
    }

    // ---------------------------------------------------------------------------------------------
    // Linux desktop environments: launchers
    // ---------------------------------------------------------------------------------------------

    /** KDE Plasma's Kickoff: a dark two-pane launcher with a places column, an app list and a session footer. */
    private void renderStartMenuKde(final GuiGraphics g, final int tbY) {
        final int x = startMenuX();
        final int w = KDE_MENU_W;
        final int h = startMenuHeight();
        final int y = tbY - h;
        g.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x40000000);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF1B1E24);
        g.fill(x, y, x + w, y + h, 0xFF31363B);
        // Header: the user and the search hint.
        g.fill(x + 6, y + 6, x + 20, y + 20, theme.startButton());
        g.drawString(font, hostAccountLabel(), x + 26, y + 6, 0xFFEFF0F1, false);
        g.drawString(font, "Type to search...", x + 26, y + 15, 0xFF8A9199, false);
        // Side column: places.
        final int bodyTop = y + KDE_HEADER_H;
        final int bodyBot = y + h - KDE_FOOTER_H;
        g.fill(x, bodyTop, x + KDE_SIDE_W, bodyBot, 0xFF232629);
        final String[] places = {"Favorites", "All Apps", "System", "Utilities"};
        for (int i = 0; i < places.length; i++) {
            final int py = bodyTop + 4 + i * 14;
            if (i == 0) {
                g.fill(x, py - 2, x + KDE_SIDE_W, py + 10, theme.startButton());
            }
            g.drawString(font, places[i], x + 8, py, i == 0 ? 0xFFFFFFFF : 0xFFBDC3C7, false);
        }
        // App list.
        int my = bodyTop + 4;
        final int listX = x + KDE_SIDE_W + 4;
        final int listW = w - KDE_SIDE_W - 8;
        for (final Launcher l : launchers) {
            final boolean hov = hoverY >= my && hoverY < my + KDE_ROW_H && hoverX >= listX && hoverX < listX + listW;
            if (hov) {
                g.fill(listX, my, listX + listW, my + KDE_ROW_H, 0x443DAEE9);
            }
            ProgramIcons.draw(g, listX + 2, my, 16, KDE_ROW_H, l.programId(), iconSet());
            g.drawString(font, trim(l.label(), 18), listX + 22, my + 4, 0xFFEFF0F1, false);
            my += KDE_ROW_H;
        }
        // Footer: session actions.
        g.fill(x, bodyBot, x + w, y + h, 0xFF232629);
        g.drawString(font, "Sleep", x + 8, bodyBot + 4, 0xFF8A9199, false);
        final String off = "Shut Down";
        final int offX = x + w - font.width(off) - 8;
        final boolean offHov = hoverY >= bodyBot && hoverX >= offX - 4 && hoverX < x + w;
        g.drawString(font, off, offX, bodyBot + 4, offHov ? 0xFFFFFFFF : 0xFFBDC3C7, false);
    }

    private boolean handleStartClickKde(final int mx, final int my, final int tbY) {
        final int x = startMenuX();
        final int w = KDE_MENU_W;
        final int h = startMenuHeight();
        final int y = tbY - h;
        if (mx < x || mx > x + w || my < y || my > y + h) {
            return false;
        }
        final int bodyTop = y + KDE_HEADER_H;
        final int bodyBot = y + h - KDE_FOOTER_H;
        if (my >= bodyBot) {
            if (mx >= x + w - font.width("Shut Down") - 12) {
                openPowerDialog();
                closeStart();
            }
            return true;
        }
        if (my >= bodyTop && mx >= x + KDE_SIDE_W + 4) {
            final int row = (my - (bodyTop + 4)) / KDE_ROW_H;
            if (row >= 0 && row < launchers.size()) {
                runLauncher(launchers.get(row));
                closeStart();
            }
        }
        return true;
    }

    /**
     * GNOME's Activities overview: a translucent layer over the desktop with a search box, a workspace strip,
     * the app grid (or the search results) and a dash of the first few apps along the bottom.
     */
    private void renderOverviewGnome(final GuiGraphics g) {
        final int sw = sw();
        final int sh = sh();
        final int top = TASKBAR_H;
        g.fill(0, top, sw, sh, 0xD00F0F14);
        // Search box.
        final int fieldW = Math.min(180, sw - 40);
        final int fieldX = (sw - fieldW) / 2;
        final int fieldY = top + 8;
        g.fill(fieldX, fieldY, fieldX + fieldW, fieldY + 14, 0x33FFFFFF);
        final String q = startSearch.toString();
        if (q.isEmpty()) {
            final String hint = "Type to search";
            g.drawString(font, hint, fieldX + (fieldW - font.width(hint)) / 2, fieldY + 3, 0xFFB8BBC8, false);
        } else {
            g.drawString(font, trim(q, (fieldW - 8) / 6), fieldX + 6, fieldY + 3, 0xFFFFFFFF, false);
        }
        final List<Launcher> filtered = w11Filtered();
        int contentTop = fieldY + 22;
        if (q.isEmpty()) {
            // Workspace strip: the current workspace (with a hint of the open windows) and an empty one.
            final int wsW = Math.min(90, (sw - 40) / 2);
            final int wsX = (sw - (wsW * 2 + 10)) / 2;
            g.fill(wsX, contentTop, wsX + wsW, contentTop + 34, 0x33FFFFFF);
            outline(g, wsX, contentTop, wsW, 34, 0xFFFFFFFF);
            if (!windows.isEmpty()) {
                g.fill(wsX + 8, contentTop + 8, wsX + wsW - 8, contentTop + 26, 0xFFF6F5F4);
            }
            g.fill(wsX + wsW + 10, contentTop, wsX + wsW * 2 + 10, contentTop + 34, 0x22FFFFFF);
            contentTop += 42;
            // App grid.
            final int gridX = (sw - GN_COLS * GN_TILE_W) / 2;
            for (int i = 0; i < filtered.size(); i++) {
                final int col = i % GN_COLS;
                final int row = i / GN_COLS;
                final int tx = gridX + col * GN_TILE_W;
                final int ty = contentTop + row * GN_TILE_H;
                if (ty + GN_TILE_H > sh - 26) {
                    break;
                }
                final Launcher l = filtered.get(i);
                if (hoverX >= tx && hoverX < tx + GN_TILE_W && hoverY >= ty && hoverY < ty + GN_TILE_H) {
                    g.fill(tx + 2, ty, tx + GN_TILE_W - 2, ty + GN_TILE_H - 2, 0x33FFFFFF);
                }
                ProgramIcons.draw(g, tx + (GN_TILE_W - 16) / 2, ty + 3, 16, 16, l.programId(), iconSet());
                String label = l.label();
                while (label.length() > 3 && font.width(label) > GN_TILE_W - 2) {
                    label = label.substring(0, label.length() - 1);
                }
                g.drawString(font, label, tx + (GN_TILE_W - font.width(label)) / 2, ty + 22, 0xFFFFFFFF, false);
            }
            // Dash: the first apps as a pill along the bottom.
            final int dashN = Math.min(5, launchers.size());
            final int dashW = dashN * 22 + 8;
            final int dashX = (sw - dashW) / 2;
            final int dashY = sh - 24;
            g.fill(dashX, dashY, dashX + dashW, dashY + 20, 0x66000000);
            for (int i = 0; i < dashN; i++) {
                ProgramIcons.draw(g, dashX + 4 + i * 22 + 3, dashY + 2, 16, 16, launchers.get(i).programId(),
                        iconSet());
            }
        } else {
            g.drawString(font, filtered.isEmpty() ? "No results" : "Applications", fieldX, contentTop, 0xFFB8BBC8, false);
            int my = contentTop + 12;
            for (final Launcher l : filtered) {
                if (hoverY >= my && hoverY < my + 16 && hoverX >= fieldX && hoverX < fieldX + fieldW) {
                    g.fill(fieldX, my, fieldX + fieldW, my + 16, 0x33FFFFFF);
                }
                ProgramIcons.draw(g, fieldX + 2, my, 16, 16, l.programId(), iconSet());
                g.drawString(font, l.label(), fieldX + 22, my + 4, 0xFFFFFFFF, false);
                my += 16;
            }
        }
    }

    private boolean handleOverviewClickGnome(final int mx, final int my) {
        final int sw = sw();
        final int sh = sh();
        final int top = TASKBAR_H;
        if (my < top) {
            return false; // the top bar handles its own clicks
        }
        final int fieldW = Math.min(180, sw - 40);
        final int fieldX = (sw - fieldW) / 2;
        final int fieldY = top + 8;
        final List<Launcher> filtered = w11Filtered();
        if (startSearch.length() > 0) {
            int ry = fieldY + 22 + 12;
            for (final Launcher l : filtered) {
                if (my >= ry && my < ry + 16 && mx >= fieldX && mx < fieldX + fieldW) {
                    runLauncher(l);
                    closeStart();
                    return true;
                }
                ry += 16;
            }
            if (my >= fieldY && my < fieldY + 14) {
                return true;
            }
            closeStart();
            return true;
        }
        final int gridTop = fieldY + 22 + 42;
        final int gridX = (sw - GN_COLS * GN_TILE_W) / 2;
        if (my >= gridTop && mx >= gridX && mx < gridX + GN_COLS * GN_TILE_W) {
            final int col = (mx - gridX) / GN_TILE_W;
            final int row = (my - gridTop) / GN_TILE_H;
            final int idx = row * GN_COLS + col;
            if (idx >= 0 && idx < filtered.size() && gridTop + (row + 1) * GN_TILE_H <= sh - 26) {
                runLauncher(filtered.get(idx));
                closeStart();
                return true;
            }
        }
        final int dashN = Math.min(5, launchers.size());
        final int dashW = dashN * 22 + 8;
        final int dashX = (sw - dashW) / 2;
        final int dashY = sh - 24;
        if (my >= dashY && my < dashY + 20 && mx >= dashX && mx < dashX + dashW) {
            final int idx = (mx - dashX - 4) / 22;
            if (idx >= 0 && idx < dashN) {
                runLauncher(launchers.get(idx));
                closeStart();
            }
            return true;
        }
        if (my >= fieldY && my < fieldY + 14) {
            return true; // the search box keeps the overview open
        }
        closeStart(); // clicking the overview backdrop leaves it, as GNOME does
        return true;
    }

    /** Cinnamon's Mint menu: a favourites rail, a categories column and the app list with a search hint. */
    private void renderStartMenuCinnamon(final GuiGraphics g, final int tbY) {
        final int x = startMenuX();
        final int w = CIN_MENU_W;
        final int h = startMenuHeight();
        final int y = tbY - h;
        g.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x40000000);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF1F1F1F);
        g.fill(x, y, x + w, y + h, 0xFF2F2F2F);
        // Favourites rail: the first apps as icons.
        g.fill(x, y, x + CIN_RAIL_W, y + h, 0xFF262626);
        final int favN = Math.min(4, launchers.size());
        for (int i = 0; i < favN; i++) {
            final int fy = y + 8 + i * 22;
            if (hoverX >= x && hoverX < x + CIN_RAIL_W && hoverY >= fy - 3 && hoverY < fy + 19) {
                g.fill(x + 2, fy - 3, x + CIN_RAIL_W - 2, fy + 19, 0x3369B03B);
            }
            ProgramIcons.draw(g, x + (CIN_RAIL_W - 16) / 2, fy, 16, 16, launchers.get(i).programId(),
                    iconSet());
        }
        // Categories.
        final int catsX = x + CIN_RAIL_W;
        g.fill(catsX + CIN_CATS_W - 1, y, catsX + CIN_CATS_W, y + h, 0xFF3A3A3A);
        final String[] cats = {"All", "Accessories", "Office", "System", "Preferences"};
        for (int i = 0; i < cats.length; i++) {
            final int cy = y + CIN_HEADER_H + i * 14;
            if (i == 0) {
                g.fill(catsX, cy - 2, catsX + CIN_CATS_W - 1, cy + 10, theme.startButton());
            }
            g.drawString(font, cats[i], catsX + 8, cy, i == 0 ? 0xFFFFFFFF : 0xFFBDBDBD, false);
        }
        // Search hint + app list.
        final int listX = catsX + CIN_CATS_W + 4;
        final int listW = x + w - listX - 4;
        g.fill(listX, y + 5, listX + listW, y + 17, 0xFF222222);
        outline(g, listX, y + 5, listW, 12, 0xFF444444);
        g.drawString(font, "Search", listX + 4, y + 7, 0xFF9A9A9A, false);
        int my = y + CIN_HEADER_H;
        for (final Launcher l : launchers) {
            final boolean hov = hoverY >= my && hoverY < my + CIN_ROW_H && hoverX >= listX && hoverX < listX + listW;
            if (hov) {
                g.fill(listX, my, listX + listW, my + CIN_ROW_H, 0x3369B03B);
            }
            ProgramIcons.draw(g, listX + 2, my, 16, CIN_ROW_H, l.programId(), iconSet());
            g.drawString(font, trim(l.label(), 16), listX + 22, my + 4, 0xFFE8E8E8, false);
            my += CIN_ROW_H;
        }
    }

    private boolean handleStartClickCinnamon(final int mx, final int my, final int tbY) {
        final int x = startMenuX();
        final int w = CIN_MENU_W;
        final int h = startMenuHeight();
        final int y = tbY - h;
        if (mx < x || mx > x + w || my < y || my > y + h) {
            return false;
        }
        if (mx < x + CIN_RAIL_W) {
            final int favN = Math.min(4, launchers.size());
            for (int i = 0; i < favN; i++) {
                final int fy = y + 8 + i * 22;
                if (my >= fy - 3 && my < fy + 19) {
                    runLauncher(launchers.get(i));
                    closeStart();
                    return true;
                }
            }
            return true;
        }
        final int listX = x + CIN_RAIL_W + CIN_CATS_W + 4;
        if (mx >= listX && my >= y + CIN_HEADER_H) {
            final int row = (my - (y + CIN_HEADER_H)) / CIN_ROW_H;
            if (row >= 0 && row < launchers.size()) {
                runLauncher(launchers.get(row));
                closeStart();
            }
        }
        return true;
    }

    /** Frames XP: a two-column panel — programs on the left, system places on the right — with header/footer bands. */
    private void renderStartMenuXp(final GuiGraphics g, final int tbY) {
        final int x = startMenuX();
        final int w = XP_MENU_W;
        final int h = startMenuHeight();
        final int y = tbY - h;
        // Panel with a thin border.
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF13315F);
        g.fill(x, y, x + w, y + h, theme.menuBg());
        // Header band: the player's own face and name over the Luna blue, the way this menu always opened.
        g.fillGradient(x, y, x + w, y + XP_HEADER_H, 0xFF3B7BD4, 0xFF1E4E9E);
        drawPlayerFace(g, x + 4, y + 3, XP_HEADER_H - 6);
        g.drawString(font, playerName(), x + 4 + XP_HEADER_H - 6 + 5, y + (XP_HEADER_H - 8) / 2,
                0xFFFFFFFF, true);
        // The orange rule under the header, lit along its top edge.
        g.fill(x, y + XP_HEADER_H, x + w, y + XP_HEADER_H + 1, 0xFFFFD268);
        g.fill(x, y + XP_HEADER_H + 1, x + w, y + XP_HEADER_H + XP_ORANGE_H, 0xFFF4A11E);
        // Body: left programs column over white, right places column over a tinted panel.
        final int bodyTop = y + XP_HEADER_H + XP_ORANGE_H;
        final int bodyBot = y + h - XP_FOOTER_H;
        final int split = x + XP_LEFT_W;
        g.fill(x, bodyTop, split, bodyBot, 0xFFFFFFFF);
        g.fill(split, bodyTop, x + w, bodyBot, 0xFFDCE7F6);
        g.fill(split, bodyTop, split + 1, bodyBot, 0xFFB6C6E0);
        drawXpLeftColumn(g, x + 3, bodyTop + 3, XP_LEFT_W - 6);
        drawXpColumn(g, xpRightLaunchers(), split + 3, bodyTop + 3, w - XP_LEFT_W - 6, 0xFF1A3A70, 0, false);
        // Footer band: log off and turn off, right-aligned, mirroring the header gradient.
        final int footY = bodyBot;
        g.fillGradient(x, footY, x + w, y + h, 0xFF3B7BD4, 0xFF1E4E9E);
        final int offX = xpFooterOffX(x, w);
        final int logX = xpFooterLogX(x, w);
        final int textY = footY + (XP_FOOTER_H - 8) / 2;
        if (hoverY >= footY && hoverX >= logX && hoverX < offX - 4) {
            g.fill(logX - 2, footY + 2, offX - 6, y + h - 2, 0x33FFFFFF);
        }
        g.fill(logX, footY + 5, logX + 8, footY + 13, 0xFFE0A020);
        g.fill(logX + 3, footY + 8, logX + 8, footY + 10, 0xFFFFFFFF);
        g.drawString(font, "Log Off", logX + 12, textY, 0xFFFFFFFF, true);
        if (hoverY >= footY && hoverX >= offX && hoverX < x + w - 2) {
            g.fill(offX - 2, footY + 2, x + w - 3, y + h - 2, 0x33FFFFFF);
        }
        g.fill(offX, footY + 5, offX + 8, footY + 13, 0xFFE24C4C);
        g.fill(offX + 3, footY + 3, offX + 5, footY + 9, 0xFFFFFFFF);
        g.drawString(font, "Turn Off Computer", offX + 12, textY, 0xFFFFFFFF, true);
    }

    /** The name shown on the Start menu's header: the player's own. */
    private static String playerName() {
        return Minecraft.getInstance().getUser().getName();
    }

    /**
     * The player's face from their own skin, hat layer included, at {@code size} pixels square. A client
     * without a player yet falls back to a plain plate, so the header never renders as a hole.
     */
    private static void drawPlayerFace(final GuiGraphics g, final int x, final int y, final int size) {
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFFFFFFFF); // the little white frame XP drew
        final net.minecraft.client.player.AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            g.fill(x, y, x + size, y + size, 0xFF2F6FD6);
            return;
        }
        final net.minecraft.resources.ResourceLocation skin = player.getSkin().texture();
        g.blit(skin, x, y, size, size, 8.0F, 8.0F, 8, 8, 64, 64);
        g.blit(skin, x, y, size, size, 40.0F, 8.0F, 8, 8, 64, 64);
    }

    /**
     * The XP Start menu's left column: the pinned entries in bold, a separator, the rest, and the
     * "All Programs" row that opens the page listing everything installed, services included.
     */
    private void drawXpLeftColumn(final GuiGraphics g, final int colX, final int colY, final int colW) {
        final List<Launcher> items = xpLeftLaunchers();
        final int pinned = Math.min(XP_PINNED, items.size());
        drawXpColumn(g, items, colX, colY, colW, theme.menuText(), pinned, true);
        if (items.size() > pinned) {
            final int sepY = colY + pinned * XP_ROW_H + XP_SEP_H / 2;
            g.fill(colX + 3, sepY, colX + colW - 3, sepY + 1, 0xFF9FBBE6);
        }
        final int afterRows = colY + xpLeftRowY(items.size());
        g.fill(colX + 3, afterRows + XP_SEP_H / 2, colX + colW - 3, afterRows + XP_SEP_H / 2 + 1, 0xFF9FBBE6);
        final int allY = colY + xpAllRowY();
        if (hoverY >= allY && hoverY < allY + XP_ALL_ROW_H && hoverX >= colX && hoverX < colX + colW) {
            g.fill(colX, allY, colX + colW, allY + XP_ALL_ROW_H, 0x333B7BD4);
        }
        g.drawString(font, Component.literal("All Programs").withStyle(net.minecraft.ChatFormatting.BOLD),
                colX + 4, allY + 4, theme.menuText(), false);
        // The green chevron that always sat at the end of this row.
        final int ax = colX + colW - 10;
        for (int i = 0; i < 5; i++) {
            g.fill(ax + i, allY + 3 + i, ax + i + 1, allY + 12 - i, 0xFF2F9A33);
        }
    }

    /**
     * Draws one XP Start column as an icon+label list, with a hover highlight on the row under the cursor.
     * The first {@code boldCount} entries are the pinned ones and are drawn in bold. The left column's rows
     * are spaced by {@link #xpLeftRowY(int)}, which leaves the gap its separator sits in.
     */
    private void drawXpColumn(final GuiGraphics g, final List<Launcher> items, final int colX, final int colY,
                              final int colW, final int textColor, final int boldCount,
                              final boolean leftColumn) {
        for (int i = 0; i < items.size(); i++) {
            final Launcher l = items.get(i);
            final int my = colY + (leftColumn ? xpLeftRowY(i) : i * XP_ROW_H);
            if (hoverY >= my && hoverY < my + XP_ROW_H && hoverX >= colX && hoverX < colX + colW) {
                g.fill(colX, my, colX + colW, my + XP_ROW_H, 0x333B7BD4);
            }
            ProgramIcons.draw(g, colX + 1, my, 14, 12, l.programId(), iconSet());
            final String label = trim(l.label(), (colW - 20) / 6);
            if (i < boldCount) {
                g.drawString(font, Component.literal(label).withStyle(net.minecraft.ChatFormatting.BOLD),
                        colX + 18, my + 4, textColor, false);
            } else {
                g.drawString(font, label, colX + 18, my + 4, textColor, false);
            }
        }
    }

    /** Frames 11: a centered floating panel with a search box, a pinned-app grid, and a footer power button. */
    private void renderStartMenu11(final GuiGraphics g, final int tbY) {
        final int x = startMenuX();
        final int w = W11_MENU_W;
        final int h = startMenuHeight();
        final int y = startMenuY(tbY);
        // The Start panel follows the window skin, so dark mode darkens it along with every program.
        final int panelBg = skin.windowBg();
        final int panelText = skin.text();
        final int panelDim = skin.dim();
        final int panelEdge = skin.edge();
        final int panelHover = skin.listHover();
        // Soft drop shadow, then the panel with a hairline border.
        g.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x40000000);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, skin.windowBorder());
        g.fill(x, y, x + w, y + h, panelBg);
        // Search box.
        final int fieldX = x + 6;
        final int fieldW = w - 12;
        final int fieldY = y + 6;
        g.fill(fieldX, fieldY, fieldX + fieldW, fieldY + W11_SEARCH_H, skin.fieldBg());
        outline(g, fieldX, fieldY, fieldW, W11_SEARCH_H, panelEdge);
        // Magnifier glyph.
        outline(g, fieldX + 4, fieldY + 3, 5, 5, panelDim);
        g.fill(fieldX + 8, fieldY + 7, fieldX + 10, fieldY + 9, panelDim);
        final String q = startSearch.toString();
        if (q.isEmpty()) {
            g.drawString(font, "Type here to search", fieldX + 13, fieldY + 3, panelDim, false);
        } else {
            g.drawString(font, trim(q, (fieldW - 16) / 6), fieldX + 13, fieldY + 3, panelText, false);
        }
        final int contentTop = fieldY + W11_SEARCH_H + 5;
        final List<Launcher> filtered = w11Filtered();
        if (q.isEmpty()) {
            // Pinned label + the app grid.
            g.drawString(font, "Pinned", x + 8, contentTop, panelDim, false);
            final int gridTop = contentTop + 9;
            final int gridX = x + (w - W11_COLS * W11_TILE_W) / 2;
            for (int i = 0; i < filtered.size(); i++) {
                final int col = i % W11_COLS;
                final int row = i / W11_COLS;
                final int tx = gridX + col * W11_TILE_W;
                final int ty = gridTop + row * W11_TILE_H;
                drawW11Tile(g, filtered.get(i), tx, ty, panelText, panelHover, panelEdge);
            }
        } else {
            // Search results as a vertical list.
            g.drawString(font, filtered.isEmpty() ? "No results" : "Best match", x + 8, contentTop, panelDim, false);
            int my = contentTop + 11;
            final int rowW = w - 12;
            for (final Launcher l : filtered) {
                if (hoverY >= my && hoverY < my + 15 && hoverX >= x + 6 && hoverX < x + 6 + rowW) {
                    g.fill(x + 6, my, x + 6 + rowW, my + 15, panelHover);
                }
                ProgramIcons.draw(g, x + 8, my + 1, 13, 12, l.programId(), iconSet());
                g.drawString(font, l.label(), x + 24, my + 4, panelText, false);
                my += 15;
            }
        }
        // Footer: a separator, an account label on the left, and a power button on the right.
        final int footY = y + h - W11_FOOTER_H;
        g.fill(x + 8, footY, x + w - 8, footY + 1, panelEdge);
        g.drawString(font, hostAccountLabel(), x + 12, footY + (W11_FOOTER_H - 8) / 2, panelText, false);
        final int pwX = x + w - 22;
        final int pwY = footY + (W11_FOOTER_H - 12) / 2;
        final boolean pwHov = hoverX >= pwX - 2 && hoverX < pwX + 14 && hoverY >= footY;
        if (pwHov) {
            g.fill(pwX - 3, footY + 2, pwX + 15, footY + W11_FOOTER_H - 2, panelHover);
        }
        outline(g, pwX, pwY, 12, 12, panelText);
        g.fill(pwX + 5, pwY - 1, pwX + 7, pwY + 6, panelText); // power stem
    }

    /** Draws one Frames 11 pinned tile: an icon over a centered label, with a hover background. */
    private void drawW11Tile(final GuiGraphics g, final Launcher l, final int tx, final int ty,
                             final int labelColor, final int hoverBg, final int hoverEdge) {
        if (hoverX >= tx && hoverX < tx + W11_TILE_W && hoverY >= ty && hoverY < ty + W11_TILE_H) {
            g.fill(tx + 1, ty + 1, tx + W11_TILE_W - 1, ty + W11_TILE_H - 1, hoverBg);
            outline(g, tx + 1, ty + 1, W11_TILE_W - 2, W11_TILE_H - 2, hoverEdge);
        }
        ProgramIcons.draw(g, tx + (W11_TILE_W - 16) / 2, ty + 3, 16, 14, l.programId(), iconSet());
        // Truncate the label to the tile width by dropping characters (no ellipsis, which would be wider).
        String label = l.label();
        while (label.length() > 3 && font.width(label) > W11_TILE_W - 2) {
            label = label.substring(0, label.length() - 1);
        }
        g.drawString(font, label, tx + (W11_TILE_W - font.width(label)) / 2, ty + 20, labelColor, false);
    }

    /** A short account line for the Frames 11 Start footer: the computer's name, or a generic label. */
    private String hostAccountLabel() {
        return (computerName == null || computerName.isBlank()) ? "Local account" : trim(computerName, 22);
    }

    /** A thin one-pixel rectangle outline used by the Frames 11 Start panel. */
    private static void outline(final GuiGraphics g, final int x, final int y, final int w, final int h,
                               final int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** Closes the Start menu and clears any Frames 11 search text so it reopens fresh. */
    private void closeStart() {
        startOpen = false;
        startSearch.setLength(0);
    }

    // ---- Power ------------------------------------------------------------------------------------
    //
    // Shutting down used to close the window and leave the machine running — the computer stayed on
    // the network with everything still open. The three real choices now live in one dialog, and each
    // one reaches the machine.

    private static final int POWER_W = 190;
    private static final int POWER_ROW_H = 20;
    private static final String[][] POWER_CHOICES = {
            {"Shut down", "the machine powers off"},
            {"Restart", "power-cycle, back at the POST"},
            {"Log off", "leave the screen, keep it running"},
    };

    private boolean powerOpen;
    // The desktop surface the dialog was centred on, so the click test lands where it was drawn.
    private int powerSurfaceW;
    private int powerSurfaceH;

    // ---- Taskbar context menu ---------------------------------------------------------------------

    private static final int TASK_MENU_W = 118;
    private static final int TASK_MENU_ROW_H = 12;
    private static final String[] TASK_MENU_ITEMS = {
            "Bring to front", "Minimize", "Maximize", "Minimize others", "Close",
    };

    /** The taskbar entry whose menu is open, or -1. */
    private int taskMenuIndex = -1;
    private int taskMenuX;
    private int taskMenuY;

    private int taskMenuHeight() {
        return 4 + TASK_MENU_ITEMS.length * TASK_MENU_ROW_H;
    }

    /** Draws the taskbar's context menu; returns false when none is open. */
    private boolean renderTaskMenu(final GuiGraphics g, final int mouseX, final int mouseY) {
        if (taskMenuIndex < 0 || taskMenuIndex >= windows.size()) {
            taskMenuIndex = -1;
            return false;
        }
        final int x = taskMenuX;
        final int y = taskMenuY - taskMenuHeight();
        skin.windowShadow(g, x, y, TASK_MENU_W, taskMenuHeight());
        skin.panel(g, x, y, TASK_MENU_W, taskMenuHeight());
        for (int i = 0; i < TASK_MENU_ITEMS.length; i++) {
            final int rowY = y + 2 + i * TASK_MENU_ROW_H;
            final boolean hovered = mouseX >= x + 2 && mouseX < x + TASK_MENU_W - 2
                    && mouseY >= rowY && mouseY < rowY + TASK_MENU_ROW_H;
            if (hovered) {
                g.fill(x + 2, rowY, x + TASK_MENU_W - 2, rowY + TASK_MENU_ROW_H, skin.listHover());
            }
            final boolean destructive = i == TASK_MENU_ITEMS.length - 1;
            g.drawString(font, TASK_MENU_ITEMS[i], x + 8, rowY + 2,
                    destructive ? 0xFFC04A3E : skin.text(), false);
        }
        return true;
    }

    /** Handles a click while the taskbar menu is open; returns whether it consumed the click. */
    private boolean clickTaskMenu(final double mouseXAbs, final double mouseYAbs) {
        if (taskMenuIndex < 0) {
            return false;
        }
        final int index = taskMenuIndex;
        taskMenuIndex = -1;
        if (index >= windows.size()) {
            return true;
        }
        final double mouseX = mouseXAbs - ox();
        final double mouseY = mouseYAbs - oy();
        final int x = taskMenuX;
        final int y = taskMenuY - taskMenuHeight();
        for (int i = 0; i < TASK_MENU_ITEMS.length; i++) {
            final int rowY = y + 2 + i * TASK_MENU_ROW_H;
            if (mouseX < x + 2 || mouseX >= x + TASK_MENU_W - 2
                    || mouseY < rowY || mouseY >= rowY + TASK_MENU_ROW_H) {
                continue;
            }
            final DesktopWindow w = windows.get(index);
            switch (i) {
                case 0 -> {
                    w.setMinimized(false);
                    bringToFront(index);
                }
                case 1 -> w.setMinimized(true);
                case 2 -> {
                    w.setMinimized(false);
                    w.setMaximized(!w.maximized());
                    bringToFront(index);
                }
                case 3 -> {
                    for (final DesktopWindow other : windows) {
                        other.setMinimized(other != w);
                    }
                    w.setMinimized(false);
                }
                default -> windows.remove(index);
            }
            return true;
        }
        return true; // a click outside the menu just dismisses it
    }

    private void openPowerDialog() {
        powerOpen = true;
    }

    private int powerHeight() {
        return 22 + POWER_CHOICES.length * POWER_ROW_H;
    }

    private int powerX() {
        return (powerSurfaceW - POWER_W) / 2;
    }

    private int powerY() {
        return (powerSurfaceH - powerHeight()) / 2;
    }

    /** Draws the power dialog over the desktop; returns false when it is not open. */
    private boolean renderPowerDialog(final GuiGraphics g, final int surfaceW, final int surfaceH,
                                      final int mouseX, final int mouseY) {
        if (!powerOpen) {
            return false;
        }
        powerSurfaceW = surfaceW;
        powerSurfaceH = surfaceH;
        final int x = powerX();
        final int y = powerY();
        g.fill(0, 0, surfaceW, surfaceH, 0x99000000);
        skin.windowShadow(g, x, y, POWER_W, powerHeight());
        skin.windowFrame(g, x, y, POWER_W, powerHeight());
        skin.titleBar(g, x, y, POWER_W, 14);
        g.drawString(font, "Power", x + 6, y + 3, skin.titleText(), false);
        for (int i = 0; i < POWER_CHOICES.length; i++) {
            final int rowY = y + 18 + i * POWER_ROW_H;
            final boolean hovered = mouseX >= x + 4 && mouseX < x + POWER_W - 4
                    && mouseY >= rowY && mouseY < rowY + POWER_ROW_H - 2;
            if (hovered) {
                g.fill(x + 4, rowY, x + POWER_W - 4, rowY + POWER_ROW_H - 2, skin.listHover());
            }
            g.drawString(font, POWER_CHOICES[i][0], x + 12, rowY + 2, skin.text(), false);
            g.drawString(font, POWER_CHOICES[i][1], x + 12, rowY + 11, skin.dim(), false);
        }
        return true;
    }

    /**
     * Handles a click while the power dialog is up; returns whether it consumed the click. The
     * coordinates come in absolute and are moved into the desktop's own space, where it was drawn.
     */
    private boolean clickPowerDialog(final double mouseXAbs, final double mouseYAbs) {
        if (!powerOpen) {
            return false;
        }
        final double mouseX = mouseXAbs - ox();
        final double mouseY = mouseYAbs - oy();
        final int x = powerX();
        final int y = powerY();
        for (int i = 0; i < POWER_CHOICES.length; i++) {
            final int rowY = y + 18 + i * POWER_ROW_H;
            if (mouseX >= x + 4 && mouseX < x + POWER_W - 4
                    && mouseY >= rowY && mouseY < rowY + POWER_ROW_H - 2) {
                // The machine is going down or restarting: the desktop closing after this must not
                // hand its windows back to a machine whose session has just ended.
                powerCycling = true;
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new dev.jstech.computronics.operation.payload
                                .MachinePowerPayload(host, monitorPos, i));
                powerOpen = false;
                return true;
            }
        }
        // A click anywhere else dismisses it: no accidental shutdowns.
        powerOpen = false;
        return true;
    }

    /** Toggles the Start menu open/closed, always opening with an empty search box. */
    private void toggleStart() {
        if (startOpen) {
            closeStart();
        } else {
            startOpen = true;
            startSearch.setLength(0);
        }
    }

    /**
     * Resolves a click inside the (version-specific) Start menu. Returns true when the click landed on the
     * panel (and was acted on or absorbed); false when it fell outside, so the caller closes the menu.
     */
    private boolean handleStartMenuClick(final int mx, final int my, final int tbY) {
        if (periodPanel()) {
            return handleStartClickPeriod(mx, my, tbY);
        }
        return switch (panel) {
            case FRAMES_XP -> handleStartClickXp(mx, my, tbY);
            case FRAMES_11 -> handleStartClick11(mx, my, tbY);
            case KDE -> handleStartClickKde(mx, my, tbY);
            case GNOME -> handleOverviewClickGnome(mx, my);
            case CINNAMON -> handleStartClickCinnamon(mx, my, tbY);
            default -> handleStartClick95(mx, my, tbY);
        };
    }

    /**
     * Clicks in the period launcher. The rows are laid out from the same origin and pitch the renderer
     * uses, so what the player sees and what they hit are one list.
     */
    private boolean handleStartClickPeriod(final int mx, final int my, final int tbY) {
        final int x = startMenuX();
        final int w = startMenuW();
        final int h = startMenuHeight();
        final int y = tbY - h;
        if (mx < x || mx > x + w || my < y || my > y + h) {
            return false;
        }
        final int idx = (int) Math.floor((my - (y + 4)) / (double) MENU_ITEM_H);
        if (idx >= 0 && idx < launchers.size()) {
            runLauncher(launchers.get(idx));
        }
        closeStart();
        return true;
    }

    private boolean handleStartClick95(final int mx, final int my, final int tbY) {
        final int h = startMenuHeight();
        final int y = tbY - h;
        if (mx < startMenuX() || mx > startMenuX() + MENU_W || my < y || my > y + h) {
            return false;
        }
        final int itemsTop = y + 4;
        final int idx = (int) Math.floor((my - itemsTop) / (double) MENU_ITEM_H);
        if (idx >= 0 && idx < launchers.size()) {
            runLauncher(launchers.get(idx));
        } else {
            final int shutY = itemsTop + launchers.size() * MENU_ITEM_H + 6;
            if (my >= shutY && my <= shutY + MENU_ITEM_H) {
                openPowerDialog();
            }
        }
        closeStart();
        return true;
    }

    private boolean handleStartClickXp(final int mx, final int my, final int tbY) {
        final int x = startMenuX();
        final int w = XP_MENU_W;
        final int h = startMenuHeight();
        final int y = tbY - h;
        if (mx < x || mx > x + w || my < y || my > y + h) {
            return false;
        }
        final int bodyTop = y + XP_HEADER_H + XP_ORANGE_H;
        final int bodyBot = y + h - XP_FOOTER_H;
        final int split = x + XP_LEFT_W;
        if (my >= bodyTop && my < bodyBot) {
            final int dy = my - (bodyTop + 3);
            if (mx < split) {
                // The left column's rows are spaced around a separator, so they are walked, not divided.
                final List<Launcher> col = xpLeftLaunchers();
                for (int i = 0; i < col.size(); i++) {
                    final int ry = xpLeftRowY(i);
                    if (dy >= ry && dy < ry + XP_ROW_H) {
                        runLauncher(col.get(i));
                        closeStart();
                        return true;
                    }
                }
                final int allY = xpAllRowY();
                if (dy >= allY && dy < allY + XP_ALL_ROW_H) {
                    openAllPrograms();
                }
            } else {
                final List<Launcher> col = xpRightLaunchers();
                final int row = dy / XP_ROW_H;
                if (row >= 0 && row < col.size()) {
                    runLauncher(col.get(row));
                }
            }
        } else if (my >= bodyBot) {
            // The footer: log off leaves the machine, turn off asks the power dialog.
            if (mx >= xpFooterOffX(x, w)) {
                openPowerDialog();
            } else if (mx >= xpFooterLogX(x, w)) {
                closeStart();
                onClose();
                return true;
            }
        }
        closeStart();
        return true;
    }

    /** "All Programs": the page that lists everything installed on this machine, services included. */
    private void openAllPrograms() {
        for (final Launcher l : launchers) {
            if (l.programId().getPath().equals("settings")) {
                runLauncher(l);
                return;
            }
        }
    }

    private boolean handleStartClick11(final int mx, final int my, final int tbY) {
        final int x = startMenuX();
        final int w = W11_MENU_W;
        final int h = startMenuHeight();
        final int y = startMenuY(tbY);
        if (mx < x || mx > x + w || my < y || my > y + h) {
            return false;
        }
        // Footer power button (right side): shut the computer down.
        final int footY = y + h - W11_FOOTER_H;
        if (my >= footY) {
            if (mx >= x + w - 24) {
                openPowerDialog();
                closeStart();
            }
            return true; // clicks elsewhere in the footer are absorbed, keeping the menu open
        }
        final int contentTop = y + 6 + W11_SEARCH_H + 5;
        final List<Launcher> filtered = w11Filtered();
        if (startSearch.length() > 0) {
            // Result list rows.
            int ry = contentTop + 11;
            for (final Launcher l : filtered) {
                if (my >= ry && my < ry + 15) {
                    runLauncher(l);
                    closeStart();
                    return true;
                }
                ry += 15;
            }
            return true; // absorb clicks on the search box / empty space
        }
        // Pinned grid tiles.
        final int gridTop = contentTop + 9;
        final int gridX = x + (w - W11_COLS * W11_TILE_W) / 2;
        if (my >= gridTop && mx >= gridX) {
            final int col = (mx - gridX) / W11_TILE_W;
            final int row = (my - gridTop) / W11_TILE_H;
            if (col >= 0 && col < W11_COLS) {
                final int idx = row * W11_COLS + col;
                if (idx >= 0 && idx < filtered.size()) {
                    runLauncher(filtered.get(idx));
                    closeStart();
                    return true;
                }
            }
        }
        return true; // clicks on the search box or padding keep the menu open
    }

    @Override
    public boolean mouseClicked(final double mouseXAbs, final double mouseYAbs, final int button) {
        if (crashing) {
            return true; // the crash screen swallows input until the reboot completes
        }
        // The power dialog is modal: it decides the fate of the whole machine, so nothing behind it
        // takes the click. An open taskbar menu takes the next click the same way.
        if (clickPowerDialog(mouseXAbs, mouseYAbs) || clickTaskMenu(mouseXAbs, mouseYAbs)) {
            return true;
        }
        // A modal dialog swallows every click; only its OK button dismisses it.
        if (popup != null) {
            popup.mouseClicked(mouseXAbs - ox(), mouseYAbs - oy(), button);
            if (!popup.isOpen()) {
                popup = null;
            }
            return true;
        }
        // An app-level modal dialog isolates its window: route the click to it and to nothing behind it
        // (inventory slots, other windows, the taskbar), just like the desktop popup above.
        if (focusModal()) {
            final DesktopWindow f = frontWindow();
            if (f != null) {
                f.app().mouseClicked(f, mouseXAbs - ox(), mouseYAbs - oy(), button);
            }
            return true;
        }
        final double mouseX = mouseXAbs - ox();
        final double mouseY = mouseYAbs - oy();
        final int tbY = sh() - TASKBAR_H;

        // A balloon is dismissed by clicking it, and it swallows that click so nothing under it reacts.
        if (balloonClick(mouseX, mouseY, tbY, sw())) {
            return true;
        }

        // The panel's menu takes the next click wherever it lands: on an entry it runs it, anywhere else it
        // just closes, which is what a menu does.
        if (panelCtxOpen) {
            final int entry = panelCtxItemAt(mouseX, mouseY);
            panelCtxOpen = false;
            if (entry >= 0) {
                runPanelMenu(entry);
            }
            return true;
        }

        // GNOME: the top bar's Activities corner toggles the overview; the rest of the bar is inert.
        // Only while the bar IS at the top: a period GNOME panels at the bottom, and swallowing clicks
        // along the top edge there ate the title bars of every window parked up there.
        if (topPanel() && mouseY < TASKBAR_H) {
            if (mouseX < 64) {
                toggleStart();
            } else if (button == 1) {
                openPanelMenu((int) mouseX, 0);
            }
            return true;
        }
        // Windows 11 taskbar: Start sits left, the app icons are centered, so they have their own hit test.
        if (is(dev.jstech.computronics.os.PanelStyle.FRAMES_11) && mouseY >= tbY) {
            final int startX = win11StartX(sw());
            if (mouseX >= startX && mouseX < startX + WIN11_SLOT) {
                toggleStart();
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

        if (startButtonHit(mouseX, mouseY, tbY)) {
            toggleStart();
            return true;
        }
        if (startOpen) {
            if (handleStartMenuClick((int) mouseX, (int) mouseY, tbY)) {
                return true;
            }
            startOpen = false;
        }

        // Taskbar buttons: click toggles minimize / brings the window forward (Windows-style).
        if (mouseY >= tbY + 3 && mouseX >= TASK_X) {
            final int idx = taskIndexAt(mouseX, sw());
            final int bxStart = idx < 0 ? 0 : taskButtonX(idx, sw());
            if (idx >= 0) {
                // Right-click opens the window's own menu, so a program can be closed without first
                // going to it — the thing every taskbar does and this one did not.
                if (button == 1) {
                    taskMenuIndex = idx;
                    taskMenuX = bxStart;
                    taskMenuY = tbY + 3;
                    return true;
                }
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
        // A right-click on the panel itself, clear of Start and of the buttons, opens the panel's own menu,
        // the way every one of these desktops offers it. The Task Manager is one entry on that menu.
        if (button == 1 && mouseY >= tbY) {
            openPanelMenu((int) mouseX, tbY);
            return true;
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
                // A click landing on an active inventory slot (only the front inventory-band window has them) is
                // a real container click: let the vanilla container drive the cursor, drag, and shift-click.
                if (w.app() instanceof InventoryBandApp && !(w.app() instanceof NetworkInteractorApp)
                        && !w.app().modalActive() && slotUnderMouse(mouseXAbs, mouseYAbs) != null) {
                    return super.mouseClicked(mouseXAbs, mouseYAbs, button);
                }
                if (w.app() instanceof NetworkInteractorApp ni) {
                    // Shift-click an inventory slot inserts that whole stack into the network (Network tab) or
                    // local storage (Local tab), like MC-NET — instead of the vanilla quick-move between slots.
                    if (!ni.hasPopup() && hasShiftDown()) {
                        final net.minecraft.world.inventory.Slot slot = slotUnderMouse(mouseXAbs, mouseYAbs);
                        final int target = ni.shiftInsertTarget();
                        if (slot != null && slot.hasItem() && target >= 0) {
                            PacketDistributor.sendToServer(
                                    new dev.jstech.computronics.operation.payload
                                            .NiShiftInsertPayload(host, monitorPos, slot.getContainerSlot(), target));
                            return true;
                        }
                    }
                    // While the request/storage dialog is open it is modal over the window — even over the
                    // inventory band — so the app gets the click instead of the vanilla container.
                    if (!ni.hasPopup() && slotUnderMouse(mouseXAbs, mouseYAbs) != null) {
                        return super.mouseClicked(mouseXAbs, mouseYAbs, button);
                    }
                    // A held stack dropped on the item grid goes to the network (Network tab) or local storage
                    // (Storage tab) — the desktop owns the cursor, so it routes the handoff here. Left = the
                    // whole stack as items; right = one, or what a held container holds; and a held empty
                    // container right-clicked on a fluid or chemical entry fills from it, so the entry under
                    // the cursor travels with a right-click.
                    if (!ni.hasPopup() && !menu.getCarried().isEmpty() && (button == 0 || button == 1)) {
                        final double lx = mouseX - (w.x() + 4);
                        final double ly = mouseY - (w.y() + 18);
                        final int target = ni.cursorDepositTarget(lx, ly);
                        if (target >= 0) {
                            PacketDistributor.sendToServer(
                                    new dev.jstech.computronics.operation.payload
                                            .NiDepositPayload(host, monitorPos, target, button == 0,
                                            button == 1 ? ni.cursorDepositEntry(lx, ly) : java.util.Optional.empty()));
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
        selectedIcons.clear();
        // A click on empty desktop while holding a stack would make the vanilla container throw the item to the
        // world (no slot under the cursor). Swallow it so nothing is ever dropped by clicking the wallpaper.
        if (!menu.getCarried().isEmpty()) {
            return true;
        }
        // Pressing on bare wallpaper starts a rubber band; the drag handler grows it from here.
        if (button == 0 && mouseY >= workTop() && mouseY < workBottom() && overWallpaper(mouseX, mouseY)) {
            bandActive = true;
            bandStartX = mouseX;
            bandStartY = mouseY;
            bandX = mouseX;
            bandY = mouseY;
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
                    workTop(), sw(), workBottom());
            return true;
        }
        if (resizing != null) {
            resizing.applyResize(mouseXAbs - ox(), mouseYAbs - oy(), workTop(), sw(), workBottom());
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
        // Sweeping the wallpaper: extend the band and reselect what it now covers.
        if (bandActive) {
            bandX = mouseXAbs - ox();
            bandY = mouseYAbs - oy();
            updateBandSelection();
            return true;
        }
        // No window drag/resize in progress. While the front Network Interactor holds a stack on the cursor,
        // a drag is the vanilla "spread across slots" gesture — hand it to the container, not the app.
        final DesktopWindow w = frontWindow();
        if (w != null && w.app() instanceof InventoryBandApp && !menu.getCarried().isEmpty()) {
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
            popup.mouseReleased(mouseX - ox(), mouseY - oy(), button);
            return true;
        }
        // Letting go ends the sweep; whatever it covered stays selected.
        if (bandActive) {
            bandActive = false;
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
        // Frames 11 edge snapping: releasing a dragged window against a screen edge tiles it (top = maximize,
        // left/right = that half). A modern-OS gesture the earlier editions do not have.
        if (dragging != null && (is(dev.jstech.computronics.os.PanelStyle.FRAMES_11) || linuxDesktop())) {
            final int lx = (int) (mouseX - ox());
            final int ly = (int) (mouseY - oy());
            final int top = workTop();
            final int workH = workBottom() - top;
            final int halfW = sw() / 2;
            if (ly <= top + 4) {
                dragging.setMaximized(true);
            } else if (lx <= 4) {
                dragging.snapTo(0, top, halfW, workH);
            } else if (lx >= sw() - 4) {
                dragging.snapTo(halfW, top, sw() - halfW, workH);
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
        // The Frames 11 Start search box captures typing while it is open (it is always focused when shown).
        if (startOpen && searchableStart() && c >= 32 && c != 127 && startSearch.length() < 24) {
            startSearch.append(c);
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
            popup.keyPressed(key, scanCode, modifiers);
            if (!popup.isOpen()) {
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
        // While the Start menu is open it owns the keyboard: Escape closes it, and on Frames 11 the search box
        // takes Backspace (edit) and Enter (launch the top result). This runs before ESC reaches the desktop.
        if (startOpen) {
            if (key == 256) { // Escape
                closeStart();
                return true;
            }
            if (searchableStart()) {
                if (key == 259) { // Backspace
                    if (startSearch.length() > 0) {
                        startSearch.deleteCharAt(startSearch.length() - 1);
                    }
                    return true;
                }
                if ((key == 257 || key == 335) && startSearch.length() > 0) { // Enter launches the first result
                    final List<Launcher> hits = w11Filtered();
                    if (!hits.isEmpty()) {
                        runLauncher(hits.get(0));
                        closeStart();
                    }
                    return true;
                }
                // Swallow every other key so the open search box owns the keyboard: this stops a background
                // window from eating letters and stops the inventory key from closing the desktop. charTyped
                // is a separate GLFW event, so typed characters still reach the search box below.
                return true;
            }
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

    /** Whether the front (focused) window's app has a modal dialog open — the desktop then disables everything behind it. */
    private boolean focusModal() {
        final DesktopWindow f = frontWindow();
        return f != null && f.app().modalActive();
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
        return w != null && w.app() instanceof InventoryBandApp ? w : null;
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
        if (w == null || !(w.app() instanceof InventoryBandApp app)) {
            menu.setSlotsActive(false);
            return;
        }
        // Resolve the window's rectangle for this frame first, so slot positions never lag a frame behind a
        // drag, resize, or maximize (curX/curY are otherwise only refreshed when the window itself renders).
        w.resolveGeometry(sw(), sh(), bottomReserve(), workTop());
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
        // A modal dialog in the focused app disables the inventory: still draw the items (the dialog's dim
        // darkens them) but give no hover highlight and no click target.
        final boolean modal = focusModal();
        // lmx/lmy and the slot coordinates are both desktop-local (already inside the ox/oy translate).
        // Draw the items directly at the local slot coordinates: delegating to the inherited renderSlot would
        // add leftPos/topPos a second time (leftPos==ox()), double-offsetting the icons from their backgrounds.
        for (final var slot : menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            final net.minecraft.world.item.ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                DesktopItems.item(g, stack, slot.x, slot.y);
                DesktopItems.count(g, font, stack, slot.x, slot.y);
            }
            if (!modal && lmx >= slot.x && lmx < slot.x + 16 && lmy >= slot.y && lmy < slot.y + 16) {
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
        final int top = workTop();
        final int workH = workBottom() - top;
        final int availW = sw() - 16;
        final int availH = workH - 16;
        final int w = availW >= app.minWidth() ? Math.min(app.defaultWidth(), availW) : availW;
        final int h = availH >= app.minHeight() ? Math.min(app.defaultHeight(), availH) : availH;
        final int x = Math.max(48, (sw() - w) / 2 + windows.size() * 12);
        final int y = Math.max(top + 6, top + (workH - h) / 2 + windows.size() * 12);
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
        // A program the desktop shows no launcher for (the Task Manager) still opens, and still comes back
        // with the session, so it is looked up by the same label the panel calls it.
        final dev.jstech.computronics.os.ProgramSpec spec = chrome == null ? null : chrome.programFor(key);
        final ProgramClient.DesktopAppFactory factory = spec == null ? null : ProgramClient.factory(spec.id());
        return factory == null ? null : factory.create(host, monitorPos, desktopId);
    }

    /**
     * Opens the Task Manager, or brings it forward when it is already up. It is the panel's own right-click
     * destination and has no launcher of its own, exactly as on the desktops this imitates.
     */
    private void openTaskManager() {
        final dev.jstech.computronics.os.ProgramSpec spec = dev.jstech.computronics.os.OsRegistry
                .getProgram(dev.jstech.computronics.program.Programs.TASK_MANAGER);
        if (spec == null) {
            return;
        }
        final String key = chrome != null ? chrome.launcherLabel(spec) : spec.displayName();
        for (int i = 0; i < windows.size(); i++) {
            if (windows.get(i).appKey().equals(key)) {
                windows.get(i).setMinimized(false);
                windows.add(windows.remove(i));
                return;
            }
        }
        final DesktopApp app = factoryFor(key);
        if (app != null && allowOpen(key)) {
            app.applySkin(skin);
            openApp(key, app);
        }
    }

    /** Starts a launcher: a built-in app opens a window; an action-based one (e.g. the NMS) runs its action. */
    private void runLauncher(final Launcher l) {
        if (allowOpen(l.label())) {
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

    /** Word-wraps a label to {@code maxW} pixels, capped at three lines, for a selected desktop icon. */
    /** The width, in font units, one line of an icon label may run to before it wraps. */
    private static int labelFontWidth() {
        return dev.jstech.core.client.gui.component.Texts.smallFits(LABEL_W);
    }

    /**
     * One label line, cut with an ellipsis when a single unbreakable word is wider than its cell. The test is
     * the width the line is actually drawn at, not the width it would have at full size, or a name that fits
     * its cell by a pixel gets cut for no reason.
     */
    private String fitLabelLine(final String s) {
        if (dev.jstech.core.client.gui.component.Texts.smallWidth(font, s) <= LABEL_W) {
            return s;
        }
        final int units = Math.max(1, labelFontWidth() - font.width("..."));
        return font.plainSubstrByWidth(s, units) + "...";
    }

    /** An icon's label line, centred under the icon and drawn in the small text, with the theme's shadow. */
    private void drawIconLabel(final GuiGraphics g, final String line, final int cx, final int y,
                               final int color, final boolean shadow) {
        final int lx = cx - dev.jstech.core.client.gui.component.Texts.smallWidth(font, line) / 2;
        if (shadow) {
            dev.jstech.core.client.gui.component.Texts.small(g, font, line, lx + 1, y + 1, 0xFF000000);
        }
        dev.jstech.core.client.gui.component.Texts.small(g, font, line, lx, y, color);
    }

    private java.util.List<String> wrapLabel(final String s, final int maxW) {
        final java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (final String word : s.split(" ")) {
            final String cand = cur.length() == 0 ? word : cur + " " + word;
            if (cur.length() == 0 || font.width(cand) <= maxW) {
                cur = new StringBuilder(cand);
            } else {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        while (out.size() > 3) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    @Override
    public void removed() {
        // The layout goes to the machine: the windows the player leaves behind are what the machine
        // has open, for whoever looks next and after the game is closed. Not when the desktop is closing
        // because the machine is going down — that layout belongs to a session that just ended, and the
        // server has already cleared it.
        if (!powerCycling) {
            PacketDistributor.sendToServer(
                    dev.jstech.computronics.operation.payload.DesktopWindowsPayload.of(
                            host, snapshotWindows()));
        }
        // The programs' insides stay in this client as a convenience, keyed by the same launcher keys the
        // machine's layout uses, so a restored window picks its session back up when it is still here.
        final java.util.Map<String, DesktopApp> apps = new java.util.LinkedHashMap<>();
        for (final DesktopWindow w : windows) {
            apps.put(w.appKey(), w.app());
        }
        if (apps.isEmpty()) {
            SAVED_APPS.remove(host);
        } else {
            SAVED_APPS.put(host, apps);
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
