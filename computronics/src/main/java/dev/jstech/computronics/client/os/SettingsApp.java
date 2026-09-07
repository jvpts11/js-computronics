/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.computronics.operation.payload.RequestFirmwarePayload;
import dev.jstech.computronics.operation.payload.RequestSettingsPayload;
import dev.jstech.computronics.operation.payload.SetSettingPayload;
import dev.jstech.computronics.operation.payload.SettingsSnapshotPayload;
import dev.jstech.computronics.operation.payload.UninstallProgramPayload;
import dev.jstech.computronics.os.OsRegistry;
import dev.jstech.computronics.os.ProgramSpec;
import dev.jstech.core.client.gui.component.Button;
import dev.jstech.core.client.gui.component.Draw;
import dev.jstech.core.client.gui.component.Label;
import dev.jstech.core.client.gui.component.ListView;
import dev.jstech.core.client.gui.component.Panel;
import dev.jstech.core.client.gui.component.ProgressBar;
import dev.jstech.core.client.gui.component.TextField;
import dev.jstech.core.client.gui.component.UiComponent;
import dev.jstech.core.client.gui.component.UiContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * The Settings desktop app: one per-computer control panel, drawn through the running OS skin so its
 * form changes with the OS (a basic bevel on Frames 95, the richest layout on Frames 11). A left nav
 * lists eight pages, six live and two placeholders, and the right pane edits or shows each one.
 *
 * <p>All editable knobs round-trip through the server: opening the app requests a
 * {@link SettingsSnapshotPayload}, and every change sends a {@link SetSettingPayload} and rebuilds the
 * page from the refreshed snapshot the server replies with.
 */
public final class SettingsApp implements IDesktopApp {

    private static final List<String> NAV = List.of(
            "Personalize", "System", "Network", "Storage", "Display", "Programs", "Sound", "Users");
    private static final int FIRST_SOON = 6;
    private static final int NAV_W = 78;
    private static final int NAV_ROW_H = 15;
    private static final int BTN_H = 13;
    private static final int NAME_MAX = 24;

    private static final int[] ACCENTS = {
            0xFF3A6AE0, 0xFF12A26F, 0xFFD1633F, 0xFF7B52C9, 0xFFC93D6A, 0xFFC98320};
    private static final String[] THEMES = {"system", "ocean", "slate"};

    private final BlockPos host;
    private OsSkin skin = OsSkin.fallback();
    @Nullable
    private SettingsSnapshotPayload data;
    private int page;
    private int snapshots;

    private static SettingsApp active;

    /** The monitor this desktop runs on (the firmware restart reopens setup there); null when unknown. */
    @Nullable
    private BlockPos monitorPos;

    // components
    private final Panel root = new Panel();
    private final ListView<String> nav;
    private final Panel pagePanel = new Panel();
    private final Label loadingLabel;
    /** What the page was last built for; a change in any part rebuilds it. */
    private String builtFor = "";
    @Nullable
    private TextField nameField;
    @Nullable
    private Font lastFont;

    /** A wallpaper style or an accent colour as a small square that a click chooses. */
    private final class Swatch extends UiComponent {

        @Nullable
        private final String style;
        private final int color;
        private final BooleanSupplier on;
        private final Runnable onClick;

        Swatch(@Nullable final String style, final int color, final BooleanSupplier on, final Runnable onClick) {
            this.style = style;
            this.color = color;
            this.on = on;
            this.onClick = onClick;
        }

        @Override
        public void render(final GuiGraphics g, final UiContext ctx) {
            if (style != null) {
                wallpaperSwatch(g, x(), y(), width(), height(), style);
                if (on.getAsBoolean()) {
                    Draw.outline(g, x() - 1, y() - 1, width() + 2, height() + 2, ctx.skin().accent());
                } else {
                    Draw.outline(g, x(), y(), width(), height(), ctx.skin().edge());
                }
                return;
            }
            g.fill(x(), y(), right(), bottom(), color);
            final int grow = on.getAsBoolean() ? 1 : 0;
            Draw.outline(g, x() - grow, y() - grow, width() + grow * 2, height() + grow * 2,
                    on.getAsBoolean() ? ctx.skin().text() : ctx.skin().edge());
        }

        @Override
        public boolean mouseClicked(final double mx, final double my, final int button) {
            if (button != 0) {
                return false;
            }
            onClick.run();
            return true;
        }
    }

    /** The desktop form: knows its monitor, so "Restart to firmware" can reopen the setup on it. */
    public SettingsApp(final BlockPos host, @Nullable final BlockPos monitorPos) {
        this(host);
        this.monitorPos = monitorPos;
    }

    public SettingsApp(final BlockPos host) {
        this.host = host;
        nav = root.add(new ListView<String>(() -> NAV, NAV_ROW_H, this::renderNavRow).setOnClick(this::navClicked));
        loadingLabel = root.add(new Label("Loading...", Label.Tone.DIM));
        root.add(pagePanel);
        active = this;
        PacketDistributor.sendToServer(new RequestSettingsPayload(host));
    }

    @Override
    public void onRestored() {
        active = this;
        PacketDistributor.sendToServer(new RequestSettingsPayload(host));
    }

    /** Routes a settings snapshot reply to the open Settings window. */
    public static void accept(final SettingsSnapshotPayload payload) {
        if (active != null && active.host.equals(payload.hostPos())) {
            active.data = payload;
            active.snapshots++;
            // Reflect accent, brightness, clock, wallpaper, taskbar layout and dark mode on the live desktop now.
            DesktopScreen.applyLivePrefs(payload.accent(), payload.brightness(), payload.clock12h(),
                    payload.wallpaper(), payload.taskbarCentered(), payload.darkMode());
        }
    }

    @Override
    public String title() {
        return "Settings";
    }

    @Override
    public int defaultWidth() {
        return 262;
    }

    @Override
    public int defaultHeight() {
        return 224;
    }

    @Override
    public int minWidth() {
        return 236;
    }

    @Override
    public int minHeight() {
        return 160;
    }

    @Override
    public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
    }

    private void set(final String key, final String value) {
        PacketDistributor.sendToServer(new SetSettingPayload(host, key, value));
    }

    private void navClicked(final int index, final int button, final double mx, final double my) {
        if (button == 0 && index >= 0 && index < NAV.size()) {
            page = index;
        }
    }

    private void renderNavRow(final GuiGraphics g, final UiContext ctx, final String item, final int index, final int x,
                              final int y, final int w, final int h, final boolean hovered, final boolean selected) {
        final boolean sel = page == index;
        ctx.skin().listRow(g, x, y, w, h, hovered, sel);
        final int tc = sel ? ctx.skin().listRowText(true) : (index >= FIRST_SOON ? ctx.skin().dim() : ctx.skin().text());
        g.drawString(ctx.font(), item, x + 5, y + 4, tc, false);
    }

    // rendering

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        lastFont = font;
        final UiContext ctx = new UiContext(skin, font, mouseX, mouseY, partialTick);
        g.fill(x, y, x + width, y + height, skin.windowBg());
        nav.setBounds(x + 3, y + 4, NAV_W, NAV.size() * NAV_ROW_H);
        g.fill(x + NAV_W + 5, y + 3, x + NAV_W + 6, y + height - 3, skin.edge());

        final int px = x + NAV_W + 11;
        final int py = y + 6;
        final int pw = width - NAV_W - 15;
        final int ph = height - 12;
        loadingLabel.setVisible(data == null);
        loadingLabel.setBounds(px, y + 8, pw, 8);
        pagePanel.setBounds(px, py, pw, ph);
        ensurePage(px, py, pw, ph, font);
        root.render(g, ctx);
    }

    /** Rebuilds the page's components when the page, the snapshot, the skin or the space changed. */
    private void ensurePage(final int px, final int py, final int pw, final int ph, final Font font) {
        final String key = page + "|" + snapshots + "|" + skin.form() + "|" + (monitorPos != null) + "|" + px + "," + py + "," + pw + "," + ph;
        if (key.equals(builtFor)) {
            return;
        }
        if (nameField != null && nameField.isFocused() && key.startsWith(builtFor.substring(0, Math.min(builtFor.length(), 1)))) {
            // A name being typed survives a resize; the snapshot that follows its commit rebuilds the page.
            if (!builtFor.isEmpty() && builtFor.split("\\|")[1].equals(String.valueOf(snapshots)) && page == 1) {
                return;
            }
        }
        builtFor = key;
        pagePanel.clear();
        nameField = null;
        if (data == null) {
            return;
        }
        switch (page) {
            case 0 -> personalize(px, py, pw, font);
            case 1 -> system(px, py, pw, font);
            case 2 -> network(px, py, pw, font);
            case 3 -> storage(px, py, pw, font);
            case 4 -> display(px, py, pw, font);
            case 5 -> programs(px, py, pw, font);
            default -> comingSoon(px, py, pw, ph);
        }
    }

    private Label heading(final String title, final int x, final int y, final int w) {
        final Label label = pagePanel.add(new Label(title));
        label.setBounds(x, y, w, 8);
        return label;
    }

    private Label caption(final String text, final int x, final int y, final int w) {
        final Label label = pagePanel.add(new Label(text, Label.Tone.DIM));
        label.setBounds(x, y, w, 8);
        return label;
    }

    private void personalize(final int x, final int top, final int w, final Font font) {
        final SettingsSnapshotPayload d = data;
        int y = top;
        heading("Personalize", x, y, w);
        y += 13;
        caption("Wallpaper", x, y, w);
        y += 10;
        final int tw = 34;
        final int th = 21;
        for (int i = 0; i < WallpaperPainter.STYLES.length; i++) {
            final String style = WallpaperPainter.STYLES[i];
            pagePanel.add(new Swatch(style, 0, () -> style.equals(d.wallpaper()), () -> set("wallpaper", style)))
                    .setBounds(x + i * (tw + 4), y, tw, th);
        }
        y += th + 8;

        // Accent and theme only on the richer skins (scales with the OS).
        if (skin.form() != OsSkin.Form.BEVEL) {
            caption("Accent", x, y, w);
            y += 10;
            for (int i = 0; i < ACCENTS.length; i++) {
                final int argb = ACCENTS[i];
                pagePanel.add(new Swatch(null, argb, () -> (d.accent() & 0xFFFFFF) == (argb & 0xFFFFFF),
                        () -> set("accent", String.format(Locale.ROOT, "%06X", argb & 0xFFFFFF)))).setBounds(x + i * 18, y, 14, 14);
            }
            y += 22;
            caption("Theme", x, y, w);
            y += 10;
            int tx = x;
            final String current = d.themePreset().isEmpty() ? "system" : d.themePreset();
            for (final String theme : THEMES) {
                final int bw = font.width(theme) + 12;
                pagePanel.add(new Button(theme, () -> set("theme", theme)).setPrimary(theme.equals(current))).setBounds(tx, y, bw, BTN_H);
                tx += bw + 4;
            }
            y += 19;
        }
        caption("Clock", x, y, w);
        y += 10;
        toggleButtons(x, y, font, "24-hour", "12-hour", !d.clock12h(), () -> set("clock", "24h"), () -> set("clock", "12h"));
        y += 19;

        // Taskbar alignment and dark mode are Frames 11 concepts only, so they appear exclusively on the flat skin.
        if (skin.form() == OsSkin.Form.FLAT) {
            caption("Taskbar", x, y, w);
            y += 10;
            toggleButtons(x, y, font, "Center", "Left", d.taskbarCentered(), () -> set("taskbar", "center"), () -> set("taskbar", "left"));
            y += 19;
            caption("Appearance", x, y, w);
            y += 10;
            toggleButtons(x, y, font, "Light", "Dark", !d.darkMode(), () -> set("darkmode", "off"), () -> set("darkmode", "on"));
        }
    }

    private void system(final int x, final int top, final int w, final Font font) {
        final SettingsSnapshotPayload d = data;
        int y = top;
        heading("System", x, y, w);
        y += 13;
        caption("Computer name", x, y, w);
        y += 10;
        final TextField field = pagePanel.add(new TextField(NAME_MAX).setPlaceholder("(unnamed)")
                .setOnCommit(name -> set("name", name)));
        field.sync(d.computerName());
        field.setBounds(x, y, Math.min(w, 130), 13);
        nameField = field;
        y += 20;

        heading("About", x, y, w);
        y += 12;
        y = specRow(x, y, w, "Processor", d.cpuLabel() + " - " + d.cpuMhz() + " MHz");
        if (d.ramMb() > 0) {
            y = specRow(x, y, w, "Memory", group(d.ramMb()) + " MB");
        }
        if (d.vramMb() > 0) {
            y = specRow(x, y, w, "Graphics", d.vramMb() + " MB VRAM");
        }
        y = specRow(x, y, w, "System", prettyOs(d.osLabel()));
        y = specRow(x, y, w, "Platform", d.platform());
        // Restart into the firmware setup (the boot manager): the way to reach it once an OS is installed.
        final BlockPos monitor = monitorPos;
        if (monitor != null) {
            y += 4;
            final String label = "Restart to firmware";
            pagePanel.add(new Button(label, () -> PacketDistributor.sendToServer(new RequestFirmwarePayload(host, monitor))))
                    .setBounds(x, y, font.width(label) + 12, BTN_H);
        }
    }

    private void network(final int x, final int top, final int w, final Font font) {
        final SettingsSnapshotPayload d = data;
        int y = top;
        heading("Network", x, y, w);
        y += 13;
        caption("System disk public share", x, y, w);
        y += 11;
        final int permille = d.netshare();
        stepper(x, y, font, String.format(Locale.ROOT, "%.1f%% (%d/1000)", permille / 10.0, permille),
                () -> set("netshare", Integer.toString(Math.max(0, permille - 50))),
                () -> set("netshare", Integer.toString(Math.min(1000, permille + 50))));
        y += 20;
        pagePanel.add(new ProgressBar(() -> permille / 10)).setBounds(x, y, Math.min(w, 150), 6);
        y += 16;
        caption("Link: on the data network", x, y, w);
    }

    private void storage(final int x, final int top, final int w, final Font font) {
        final SettingsSnapshotPayload d = data;
        int y = top;
        heading("Storage", x, y, w);
        y += 13;
        for (final SettingsSnapshotPayload.DiskUse disk : d.disks()) {
            final String cap = disk.capMb() >= 1000 ? (disk.capMb() / 1000) + " GB" : disk.capMb() + " MB";
            pagePanel.add(new Label(disk.label() + (disk.system() ? "  [sys]" : ""))).setBounds(x, y, w - font.width(cap) - 4, 8);
            pagePanel.add(new Label(cap, Label.Tone.DIM).setAlign(Label.Align.RIGHT)).setBounds(x, y, w, 8);
            y += 10;
            final int percent = disk.capMb() > 0 ? (int) Math.min(100, 100 * disk.usedMb() / disk.capMb()) : 0;
            pagePanel.add(new ProgressBar(() -> percent)).setBounds(x, y, Math.min(w, 150), 5);
            y += 11;
        }
        if (d.disks().isEmpty()) {
            caption("No disks installed", x, y, w);
        }
    }

    private void display(final int x, final int top, final int w, final Font font) {
        final SettingsSnapshotPayload d = data;
        int y = top;
        heading("Display", x, y, w);
        y += 13;
        caption("Brightness", x, y, w);
        y += 10;
        final int b = d.brightness();
        stepper(x, y, font, b + "%",
                () -> set("brightness", Integer.toString(Math.max(0, b - 10))),
                () -> set("brightness", Integer.toString(Math.min(100, b + 10))));
        y += 20;
        caption("Monitor: linked display", x, y, w);
    }

    private void programs(final int x, final int top, final int w, final Font font) {
        final SettingsSnapshotPayload d = data;
        int y = top;
        heading("Programs", x, y, w);
        y += 13;
        if (d.installed().isEmpty()) {
            caption("No programs installed", x, y, w);
            return;
        }
        final String btn = "Uninstall";
        final int bw = font.width(btn) + 8;
        for (final String id : d.installed()) {
            final ResourceLocation rl = ResourceLocation.tryParse(id);
            final ProgramSpec spec = rl == null ? null : OsRegistry.getProgram(rl);
            final String name = spec != null ? spec.displayName() : (id.contains(":") ? id.substring(id.indexOf(':') + 1) : id);
            pagePanel.add(new Label("- " + name)).setBounds(x, y + 1, w - bw - 4, 8);
            // Per-row uninstall: the desktop counterpart of the shell's package removal.
            pagePanel.add(new Button(btn, () -> {
                PacketDistributor.sendToServer(new UninstallProgramPayload(host, id));
                // The uninstall lands before these refreshes are processed (same connection, in order).
                PacketDistributor.sendToServer(new RequestSettingsPayload(host));
                DesktopScreen.refreshActive();
            }).setLabelScale(0.85f)).setBounds(x + w - bw, y - 1, bw, 11);
            y += 12;
        }
    }

    private void comingSoon(final int x, final int y, final int w, final int h) {
        pagePanel.add(new Label(NAV.get(page)).setAlign(Label.Align.CENTER)).setBounds(x, y + h / 2 - 10, w, 8);
        pagePanel.add(new Label("Coming in a future update", Label.Tone.DIM).setAlign(Label.Align.CENTER)).setBounds(x, y + h / 2 + 2, w, 8);
    }

    // small controls

    private int specRow(final int x, final int y, final int w, final String label, final String value) {
        pagePanel.add(new Label(label, Label.Tone.DIM)).setBounds(x, y, w / 2, 8);
        pagePanel.add(new Label(value).setAlign(Label.Align.RIGHT)).setBounds(x + w / 2, y, w - w / 2, 8);
        return y + 11;
    }

    /** Two buttons of which one is lit: the setting's two states. */
    private void toggleButtons(final int x, final int y, final Font font, final String a, final String b, final boolean aOn,
                               final Runnable onA, final Runnable onB) {
        final int aw = font.width(a) + 12;
        final int bw = font.width(b) + 12;
        pagePanel.add(new Button(a, onA).setPrimary(aOn)).setBounds(x, y, aw, BTN_H);
        pagePanel.add(new Button(b, onB).setPrimary(!aOn)).setBounds(x + aw + 4, y, bw, BTN_H);
    }

    /** A value between a minus and a plus button. */
    private void stepper(final int x, final int y, final Font font, final String value, final Runnable dec, final Runnable inc) {
        pagePanel.add(new Button("-", dec)).setBounds(x, y, 15, BTN_H);
        final int valueW = Math.max(38, font.width(value) + 6);
        pagePanel.add(new Label(value)).setBounds(x + 21, y + 3, valueW, 8);
        pagePanel.add(new Button("+", inc)).setBounds(x + 21 + valueW, y, 15, BTN_H);
    }

    private static String group(final long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    /** A representative wallpaper preview that always fits the thumbnail (the real painter overflows small sizes). */
    private static void wallpaperSwatch(final GuiGraphics g, final int x, final int y, final int w, final int h,
                                        final String style) {
        switch (style) {
            case "win95" -> g.fill(x, y, x + w, y + h, 0xFF1C7C7C);
            case "winxp" -> g.fillGradient(x, y, x + w, y + h, 0xFF5B95DD, 0xFF2C5FA8);
            case "win11" -> {
                g.fillGradient(x, y, x + w, y + h, 0xFF2C3C68, 0xFF141C33);
                g.fill(x + w / 2 - 3, y + h / 2 - 3, x + w / 2 + 3, y + h / 2 + 3, 0x55FFFFFF);
            }
            default -> g.fill(x, y, x + w, y + h, 0xFF3A6A9A);
        }
    }

    private static String prettyOs(final String path) {
        return switch (path) {
            case "frames_95" -> "Frames 95";
            case "frames_xp" -> "Frames XP";
            case "frames_11" -> "Frames 11";
            case "mc_dos" -> "MC-DOS";
            case "mc_net" -> "MC-NET";
            default -> path;
        };
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
    public boolean charTyped(final char c) {
        return root.charTyped(c);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && nameField != null && nameField.isFocused()) {
            // Escape drops the edit through the field's own handling and keeps the window open.
            return root.keyPressed(key, scanCode, modifiers);
        }
        return root.keyPressed(key, scanCode, modifiers);
    }
}
