/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.os;

import dev.jstech.core.gui.layout.WindowGeometry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * One open program window on the {@link DesktopScreen}: a draggable, resizable frame hosting a
 * {@link IDesktopApp}. The title bar carries minimize, maximize/restore, and close boxes; the bottom-right
 * corner is a resize grip. A maximized window fills the desktop above the taskbar; a minimized window is
 * hidden and reachable from its taskbar button.
 */
public final class DesktopWindow {

    public static final int TITLE_H = 14;
    private static final int BTN = 11;
    /** The program icon in the title bar, sized to sit inside the bar with a pixel of air above and below. */
    private static final int ICON = 10;
    private static final int GRIP = 6;
    /** How far from an edge the cursor still grabs that edge for a resize. */
    private static final int BORDER_MARGIN = 4;

    // Resize-direction bitmask: a corner combines a horizontal and a vertical bit.
    public static final int RESIZE_NONE = 0;
    public static final int RESIZE_LEFT = 1;
    public static final int RESIZE_RIGHT = 2;
    public static final int RESIZE_TOP = 4;
    public static final int RESIZE_BOTTOM = 8;

    /** Hands each window the order it was opened in, which is the order the panel lists programs in. */
    private static int nextSerial;

    private final IDesktopApp app;
    private final String appKey;
    private final int serial = nextSerial++;
    /**
     * The window this one is a dialog of, or null for a program's own window. A dialog is listed with
     * its owner on the panel, always sits in front of it, and keeps the owner from taking input while
     * it is up, the way an Open or Save window holds the program that opened it.
     */
    @org.jetbrains.annotations.Nullable
    private DesktopWindow owner;
    private int x;
    private int y;
    private int w;
    private int h;

    private boolean minimized;
    private boolean maximized;
    // Which title-bar button is currently held down: 0 none, 1 minimize, 2 maximize, 3 close.
    private int pressedBtn;
    // Geometry saved before maximizing, to restore on un-maximize.
    private int restoreX;
    private int restoreY;
    private int restoreW;
    private int restoreH;

    // The rectangle actually drawn this frame (differs from x/y/w/h when maximized); hit tests use it.
    private int curX;
    private int curY;
    private int curW;
    private int curH;

    // In-progress edge/corner resize: the armed direction plus the geometry and cursor at grab time.
    private int resizeDir;
    private int resizeStartX;
    private int resizeStartY;
    private int resizeStartW;
    private int resizeStartH;
    private int resizeStartMx;
    private int resizeStartMy;

    public DesktopWindow(final IDesktopApp app, final String appKey, final int x, final int y,
                         final int w, final int h) {
        this.app = app;
        this.appKey = appKey;
        this.x = x;
        this.y = y;
        /*
         * Never open below the app's own minimum, even if a stale saved geometry asks for less, because otherwise
         * content like the Network Interactor's details panel overflows the window and gets clipped.
         */
        this.w = Math.max(w, app.minWidth());
        this.h = Math.max(h, app.minHeight());
        this.curX = x;
        this.curY = y;
        this.curW = w;
        this.curH = h;
    }

    public IDesktopApp app() {
        return app;
    }

    /** A stable key (the launcher label) identifying which program this window hosts, for persistence. */
    public String appKey() {
        return appKey;
    }

    /** The order this window was opened in among every window of the session, lowest first. */
    public int serial() {
        return serial;
    }

    /** The window this is a dialog of, or null. */
    @org.jetbrains.annotations.Nullable
    public DesktopWindow owner() {
        return owner;
    }

    public void setOwner(@org.jetbrains.annotations.Nullable final DesktopWindow value) {
        this.owner = value;
    }

    /** Whether this is a dialog of another window rather than a program's own. */
    public boolean dialog() {
        return owner != null;
    }

    /** The program this window is listed under on the panel: its own, or its owner's for a dialog. */
    public String groupKey() {
        return owner != null ? owner.groupKey() : appKey;
    }

    public void setMaximized(final boolean value) {
        if (value != maximized) {
            toggleMaximize();
        }
    }

    public int x() {
        return curX;
    }

    public int y() {
        return curY;
    }

    /** The width actually drawn this frame (the maximized or floating width). */
    public int width() {
        return curW;
    }

    /** The height actually drawn this frame (the maximized or floating height). */
    public int height() {
        return curH;
    }

    /** The floating bounds (where the window sits when it is not maximized) for persisting it. */
    public int floatX() {
        return x;
    }

    public int floatY() {
        return y;
    }

    public int floatW() {
        return w;
    }

    public int floatH() {
        return h;
    }

    public boolean minimized() {
        return minimized;
    }

    public void setMinimized(final boolean value) {
        this.minimized = value;
    }

    public boolean maximized() {
        return maximized;
    }

    /** Moves the floating window, clamped so the title bar stays reachable. Ignored while maximized. */
    public void moveTo(final int nx, final int ny, final int maxW, final int maxH) {
        moveTo(nx, ny, 0, maxW, maxH);
    }

    /**
     * Moves the floating window within the work area {@code [minY, maxH)}: the title bar can never slide under
     * a top panel (GNOME) nor below the bottom edge of the work area. Ignored while maximized.
     */
    public void moveTo(final int nx, final int ny, final int minY, final int maxW, final int maxH) {
        if (maximized) {
            return;
        }
        this.x = Math.max(0, Math.min(nx, maxW - w));
        this.y = Math.max(minY, Math.min(ny, maxH - TITLE_H));
    }

    /**
     * The resize-direction bitmask for a cursor near this window's edges/corners, or {@link #RESIZE_NONE}.
     * A maximized window cannot be resized. Edges within {@link #BORDER_MARGIN} pixels combine into corners.
     */
    public int resizeHitTest(final double mx, final double my) {
        if (maximized) {
            return RESIZE_NONE;
        }
        final int left = curX;
        final int right = curX + curW;
        final int top = curY;
        final int bottom = curY + curH;
        // The grab band is the outer ring INSIDE the window, so it never steals desktop or neighbour clicks.
        if (mx < left || mx > right || my < top || my > bottom) {
            return RESIZE_NONE;
        }
        int dir = RESIZE_NONE;
        if (mx - left <= BORDER_MARGIN) {
            dir |= RESIZE_LEFT;
        }
        if (right - mx <= BORDER_MARGIN) {
            dir |= RESIZE_RIGHT;
        }
        if (my - top <= BORDER_MARGIN) {
            dir |= RESIZE_TOP;
        }
        if (bottom - my <= BORDER_MARGIN) {
            dir |= RESIZE_BOTTOM;
        }
        return dir;
    }

    /** Arms a resize in {@code dir}, snapshotting the current geometry and cursor for the delta math. */
    public void beginResize(final int dir, final double mx, final double my) {
        if (maximized) {
            return;
        }
        this.resizeDir = dir;
        this.resizeStartX = x;
        this.resizeStartY = y;
        this.resizeStartW = w;
        this.resizeStartH = h;
        this.resizeStartMx = (int) mx;
        this.resizeStartMy = (int) my;
    }

    /**
     * Applies a resize drag in the armed direction. Only the dragged edges move (the opposite edges stay
     * anchored); the result is clamped to the screen bounds and the minimum window size.
     */
    public void applyResize(final double mx, final double my, final int maxW, final int maxH) {
        applyResize(mx, my, 0, maxW, maxH);
    }

    /** As {@link #applyResize(double, double, int, int)}, with the work area starting at {@code minY}. */
    public void applyResize(final double mx, final double my, final int minY, final int maxW, final int maxH) {
        if (maximized || resizeDir == RESIZE_NONE) {
            return;
        }
        final int dx = (int) mx - resizeStartMx;
        final int dy = (int) my - resizeStartMy;
        int l = resizeStartX;
        int t = resizeStartY;
        int r = resizeStartX + resizeStartW;
        int b = resizeStartY + resizeStartH;
        if ((resizeDir & RESIZE_LEFT) != 0) {
            l = resizeStartX + dx;
        }
        if ((resizeDir & RESIZE_RIGHT) != 0) {
            r = resizeStartX + resizeStartW + dx;
        }
        if ((resizeDir & RESIZE_TOP) != 0) {
            t = resizeStartY + dy;
        }
        if ((resizeDir & RESIZE_BOTTOM) != 0) {
            b = resizeStartY + resizeStartH + dy;
        }
        // Keep edges inside the work area.
        l = Math.max(0, l);
        t = Math.max(minY, t);
        r = Math.min(maxW, r);
        b = Math.min(maxH, b);
        // Enforce the app's minimum size by pushing the moving edge back, so the content never collapses.
        final int minW = app.minWidth();
        final int minH = app.minHeight();
        if (r - l < minW) {
            if ((resizeDir & RESIZE_LEFT) != 0) {
                l = r - minW;
            } else {
                r = l + minW;
            }
        }
        if (b - t < minH) {
            if ((resizeDir & RESIZE_TOP) != 0) {
                t = b - minH;
            } else {
                b = t + minH;
            }
        }
        this.x = l;
        this.y = t;
        this.w = r - l;
        this.h = b - t;
    }

    /**
     * Snaps this window to an explicit floating rectangle (used by the Frames 11 edge-snap gesture). Leaves
     * maximize, saving the pre-snap geometry as the restore rectangle so a later un-maximize returns here; the
     * size is clamped up to the app's minimum so the content never collapses.
     */
    public void snapTo(final int nx, final int ny, final int nw, final int nh) {
        if (maximized) {
            maximized = false;
        }
        restoreX = x;
        restoreY = y;
        restoreW = w;
        restoreH = h;
        this.x = Math.max(0, nx);
        this.y = Math.max(0, ny);
        this.w = Math.max(app.minWidth(), nw);
        this.h = Math.max(app.minHeight(), nh);
    }

    /** Toggles maximize, saving/restoring the floating geometry. */
    public void toggleMaximize() {
        if (maximized) {
            maximized = false;
            x = restoreX;
            y = restoreY;
            w = restoreW;
            h = restoreH;
        } else {
            restoreX = x;
            restoreY = y;
            restoreW = w;
            restoreH = h;
            maximized = true;
        }
    }

    /**
     * Resolves the rectangle this window occupies this frame into {@code curX/curY/curW/curH}: the maximized
     * area (the desktop above the taskbar) or the floating geometry. Called at the top of {@link #render}, and
     * also by the desktop screen before it positions the inventory slots, so slot positions never lag a frame
     * behind a drag, resize, or maximize (otherwise {@code curX/curY} only update when the window renders).
     */
    public void resolveGeometry(final int screenW, final int screenH, final int taskbarH) {
        resolveGeometry(screenW, screenH, taskbarH, 0);
    }

    /**
     * As {@link #resolveGeometry(int, int, int)}, with {@code workTop} pixels reserved above the work area (a
     * top panel), so a maximized window starts under it.
     */
    public void resolveGeometry(final int screenW, final int screenH, final int taskbarH, final int workTop) {
        /*
         * The minimum-size clamp lives in the unit-tested WindowGeometry so it can never silently go missing
         * again (the bug where a stale small geometry squashed the content and clipped the details panel).
         */
        final WindowGeometry.Rect r = WindowGeometry.resolve(x, y, w, h, app.minWidth(), app.minHeight(),
                maximized, screenW, screenH, taskbarH, workTop);
        curX = r.x();
        curY = r.y();
        curW = r.w();
        curH = r.h();
        /*
         * Persist the bumped-up floating size so a stale, too-small saved geometry is corrected once and the
         * resize/drag math (which reads w/h) stays consistent with what is rendered.
         */
        if (!maximized) {
            this.w = curW;
            this.h = curH;
        }
    }

    /** Whether this window is the one in front, the one keystrokes go to. */
    private boolean focused;

    public void setFocused(final boolean value) {
        this.focused = value;
    }

    public void render(final GuiGraphics g, final Font font, final OsSkin skin,
                       final int mouseX, final int mouseY, final float partialTick,
                       final int screenW, final int screenH, final int taskbarH) {
        render(g, font, skin, mouseX, mouseY, partialTick, screenW, screenH, taskbarH, 0);
    }

    /** As {@link #render(GuiGraphics, Font, OsSkin, int, int, float, int, int, int)}, with a top panel reserve. */
    public void render(final GuiGraphics g, final Font font, final OsSkin skin,
                       final int mouseX, final int mouseY, final float partialTick,
                       final int screenW, final int screenH, final int taskbarH, final int workTop) {
        resolveGeometry(screenW, screenH, taskbarH, workTop);
        final int wx = curX;
        final int wy = curY;
        final int ww = curW;
        final int wh = curH;

        /*
         * A soft drop shadow lifts the window off the wallpaper, then the frame (border + body) and title bar,
         * both shaped and corner-rounded by the installed OS's skin. A maximized window fills the desktop, so
         * it casts no shadow.
         */
        if (!maximized) {
            skin.windowShadow(g, wx, wy, ww, wh);
        }
        skin.windowFrame(g, wx, wy, ww, wh);
        skin.titleBar(g, wx, wy, ww, TITLE_H, focused);
        /*
         * The program's own icon at the left of the bar, the way every desktop of these generations marked
         * which program a window belongs to. A key nothing answers to simply gets no icon.
         */
        final dev.jstech.computers.os.DesktopEnvironmentDef desktop =
                dev.jstech.computers.os.OsRegistry.getDesktop(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("jsc", skin.osPath()));
        final dev.jstech.computers.os.ProgramSpec program =
                desktop == null ? null : desktop.programFor(appKey);
        final boolean titleIcon = program != null && !skin.titleCentered();
        if (titleIcon) {
            ProgramIcons.draw(g, wx + 3, wy + 2, ICON, ICON, program.iconId(), skin.iconSet());
        }
        /*
         * Where the title sits is part of the skin's identity, not a constant: the GNOME form centres it,
         * and the title is clamped short of the controls so a long one never runs under them.
         */
        final String title = app.title();
        final int textLeft = wx + (titleIcon ? 3 + ICON + 3 : 4);
        final int titleX = skin.titleCentered()
                ? Math.max(wx + 4, Math.min(wx + (ww - font.width(title)) / 2, minX() - font.width(title) - 4))
                : textLeft;
        g.drawString(font, title, titleX, wy + 3,
                focused ? skin.titleText() : 0xFF5B6674, focused && skin.textShadow());
        /*
         * The focused window also carries an accent outline, so "which one am I typing into" reads
         * at a glance even when several windows overlap.
         */
        if (focused) {
            final int accent = skin.accent();
            g.fill(wx, wy, wx + ww, wy + 1, accent);
            g.fill(wx, wy + wh - 1, wx + ww, wy + wh, accent);
            g.fill(wx, wy, wx + 1, wy + wh, accent);
            g.fill(wx + ww - 1, wy, wx + ww, wy + wh, accent);
        }

        /*
         * Title-bar controls: minimize, maximize/restore, close (left to right), drawn in the skin's shape.
         * A dialog has only the close box: it is put away with its owner, never on its own.
         */
        final int by = wy + 2;
        final int hover = buttonAt(mouseX, mouseY);
        if (!dialog()) {
            skin.windowControl(g, font, minX(), by, BTN, BTN, OsSkin.Control.MINIMIZE, hover == 1, pressedBtn == 1);
            skin.windowControl(g, font, maxX(), by, BTN, BTN,
                    maximized ? OsSkin.Control.RESTORE : OsSkin.Control.MAXIMIZE, hover == 2, pressedBtn == 2);
        }
        skin.windowControl(g, font, closeX(), by, BTN, BTN, OsSkin.Control.CLOSE, hover == 3, pressedBtn == 3);

        /*
         * Body content, drawn in the OS skin (the app keeps the skin if it has been migrated to it). Clip it
         * to the inner rect so nothing an app draws leaks past the frame when the window is resized smaller.
         * enableScissor ignores the pose in 1.21.1, so compensate for the desktop's translate.
         */
        final int cx = wx + 4;
        final int cy = wy + TITLE_H + 4;
        final int cw = ww - 8;
        final int ch = wh - TITLE_H - 8;
        final org.joml.Matrix4f mat = g.pose().last().pose();
        final dev.jstech.core.gui.layout.WindowGeometry.Rect clip =
                dev.jstech.core.gui.layout.WindowGeometry.scissor(
                        mat.m30(), mat.m31(), mat.m00(), mat.m11(), cx, cy, cx + cw, cy + ch);
        g.enableScissor(clip.x(), clip.y(), clip.x() + clip.w(), clip.y() + clip.h());
        app.applySkin(skin);
        app.renderContent(g, font, cx, cy, cw, ch, mouseX, mouseY, partialTick);
        g.disableScissor();

        // Resize grip (floating windows only).
        if (!maximized) {
            final int border = skin.windowBorder();
            final int gx = wx + ww - GRIP;
            final int gy = wy + wh - GRIP;
            g.fill(gx, gy + GRIP - 2, gx + GRIP, gy + GRIP, border);
            g.fill(gx + GRIP - 2, gy, gx + GRIP, gy + GRIP, border);
        }
    }

    /**
     * Draws this window small enough to fit a box {@code bw} by {@code bh} at ({@code bx}, {@code by}),
     * centred in it and keeping its proportions: the live picture of the window a panel shows when the
     * cursor rests on the program. The window is drawn exactly as it is, by its own program, scaled down;
     * the cursor is kept far away so nothing in it lights up as hovered.
     */
    public void renderThumbnail(final GuiGraphics g, final Font font, final OsSkin skin,
                                final int bx, final int by, final int bw, final int bh,
                                final int screenW, final int screenH, final int taskbarH, final int workTop) {
        resolveGeometry(screenW, screenH, taskbarH, workTop);
        if (curW <= 0 || curH <= 0 || bw <= 0 || bh <= 0) {
            return;
        }
        final float s = Math.min(bw / (float) curW, bh / (float) curH);
        final float dx = bx + (bw - curW * s) / 2f;
        final float dy = by + (bh - curH * s) / 2f;
        dev.jstech.core.client.gui.component.Draw.pushScissor(g, bx, by, bx + bw, by + bh);
        g.pose().pushPose();
        g.pose().translate(dx - curX * s, dy - curY * s, 0);
        g.pose().scale(s, s, 1f);
        render(g, font, skin, -10000, -10000, 0f, screenW, screenH, taskbarH, workTop);
        g.pose().popPose();
        dev.jstech.core.client.gui.component.Draw.popScissor(g);
    }

    /**
     * Drawn in a pass after every window and the taskbar: if the cursor is over this window's content
     * rectangle, the app paints its hover tooltips on top of everything. Uses the bounds from the last
     * {@link #render}, so it must be called after it.
     */
    public void renderTooltip(final GuiGraphics g, final Font font, final int mouseX, final int mouseY) {
        if (minimized) {
            return;
        }
        final int contentX = curX + 4;
        final int contentY = curY + TITLE_H + 4;
        final int contentW = curW - 8;
        final int contentH = curH - TITLE_H - 8;
        if (mouseX >= contentX && mouseX < contentX + contentW
                && mouseY >= contentY && mouseY < contentY + contentH) {
            app.renderTooltip(g, font, contentX, contentY, contentW, contentH, mouseX, mouseY);
        }
    }

    private int closeX() {
        return curX + curW - BTN - 3;
    }

    private int maxX() {
        return curX + curW - 2 * BTN - 5;
    }

    private int minX() {
        return curX + curW - 3 * BTN - 7;
    }

    public boolean closeBoxHit(final double mx, final double my) {
        return inBtn(mx, my, closeX());
    }

    public boolean maximizeBoxHit(final double mx, final double my) {
        return !dialog() && inBtn(mx, my, maxX());
    }

    public boolean minimizeBoxHit(final double mx, final double my) {
        return !dialog() && inBtn(mx, my, minX());
    }

    /** Which title-bar button is under the point: 1 = minimize, 2 = maximize, 3 = close, 0 = none. */
    public int buttonAt(final double mx, final double my) {
        if (closeBoxHit(mx, my)) {
            return 3;
        }
        if (maximizeBoxHit(mx, my)) {
            return 2;
        }
        if (minimizeBoxHit(mx, my)) {
            return 1;
        }
        return 0;
    }

    /** Marks which title-bar button is held down (1/2/3); it is drawn pushed-in until released (0 clears). */
    public void setPressedButton(final int b) {
        this.pressedBtn = b;
    }

    public int pressedButton() {
        return pressedBtn;
    }

    private boolean inBtn(final double mx, final double my, final int bx) {
        return mx >= bx && mx <= bx + BTN && my >= curY + 2 && my <= curY + 2 + BTN;
    }

    public boolean titleBarHit(final double mx, final double my) {
        return mx >= curX && mx <= curX + curW && my >= curY && my <= curY + TITLE_H
                && !closeBoxHit(mx, my) && !maximizeBoxHit(mx, my) && !minimizeBoxHit(mx, my);
    }

    public boolean bodyHit(final double mx, final double my) {
        return mx >= curX && mx <= curX + curW && my >= curY + TITLE_H && my <= curY + curH;
    }
}
