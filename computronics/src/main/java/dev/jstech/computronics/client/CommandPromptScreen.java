/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client;

import dev.jstech.computronics.menu.CommandPromptMenu;
import dev.jstech.computronics.operation.payload.CommandOutputPayload;
import dev.jstech.computronics.operation.payload.ConsoleInitPayload;
import dev.jstech.computronics.operation.payload.RequestConsoleInitPayload;
import dev.jstech.computronics.operation.payload.RunCommandPayload;
import dev.jstech.computronics.program.cli.CliStyle;
import dev.jstech.core.tier.HardwareEra;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Command Prompt: a full CLI over the computer the Monitor is bound to. A typed line is echoed, sent to the server to run through the shell, and the styled result is appended to the scrollback. Up/Down walk the input history; the mouse wheel scrolls back through output. The same OS skin as the rest of the computing GUIs, square corners and all.
 */
public class CommandPromptScreen<M extends CommandPromptMenu> extends AbstractComputerScreen<M> {

    private static final int CONSOLE = 0xFF070A0E;
    private static final int MAX_SCROLLBACK = 512;
    private static final float TEXT_SCALE = 0.85f;
    private static final int LINE_H = 9;

    private final Deque<Line> scrollback = new ArrayDeque<>();
    /** Whether the machine's identity line has been added, so a late init reply adds it only once. */
    private boolean identityShown;
    private final List<String> history = new ArrayList<>();
    private final List<String> commandNames = new ArrayList<>();
    private final Map<String, String> commandUsage = new LinkedHashMap<>();
    private final List<String> deviceNames = new ArrayList<>();

    /** The verbs whose first argument is a device, so Tab completes {@code /dev/sdX} for them. */
    private static final java.util.Set<String> DEVICE_VERBS =
            java.util.Set.of("mkfs.ext4", "mkfs", "mount", "grub-install");
    private int historyIndex = -1;
    private int scrollOffset;
    private int completionCycle;
    private boolean programmaticEdit;

    private EditBox input;

    /** The DOS prompt, synced from the server after each command so it tracks the current directory. */
    private String dosPrompt = "C:\\>";

    public CommandPromptScreen(final M menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
        this.imageWidth = 256;
        this.imageHeight = 178;
        this.titleLabelX = -10000;
        this.inventoryLabelY = -10000;
    }

    /** Whether this terminal wears the MC-DOS identity (the dedicated DOS screen overrides). */
    protected boolean dosStyle() {
        return false;
    }

    /**
     * A bare terminal draws no window chrome — just the glass and the text, the way a real console
     * fills its display. The MC-DOS terminal and the Linux TTY override this; the MC-NET Command
     * Prompt keeps its program window.
     */
    protected boolean bareTerminal() {
        return false;
    }

    @Override
    protected void init() {
        // Every console fills the standard monitor viewport (the same one the desktops use), instead of
        // the small fixed window it used to open in.
        this.imageWidth = Math.min(this.width - 44, 384);
        this.imageHeight = Math.min(this.height - 60, 256);
        super.init();
        // Start the input box just past the "jsc> " prompt so the caret never sits on top of it.
        final int promptW = font.width(prompt() + " ");
        input = new EditBox(font, leftPos + 10 + promptW, topPos + imageHeight - 18,
                imageWidth - 18 - promptW, 11, Component.literal("command"));
        input.setBordered(false);
        input.setMaxLength(RunCommandPayload.MAX_LEN);
        // Use the era's primary text color: dark for light-panel eras (Legacy), light for dark-panel
        // eras (Standard, Vintage). The input strip adopts the era's panel background, so the text
        // must track the era — a fixed light color disappears on Legacy's cream panel.
        // A bare console is always dark glass, so its input is always light; the windowed prompt tracks
        // the era theme (dark text on Legacy's cream panel, light on the dark eras).
        input.setTextColor(colorOf(CliStyle.PROMPT));
        input.setFocused(true);
        // A real edit (typing/backspace) restarts Tab cycling; our own programmatic setValue does not.
        input.setResponder(s -> {
            if (!programmaticEdit) {
                completionCycle = 0;
            }
        });
        setInitialFocus(input);
        addRenderableWidget(input);
        if (scrollback.isEmpty()) {
            // Opening a terminal starts a session, and a session announces itself. Restoring the old
            // scrollback used to drop the player mid-conversation with no sign the program had just
            // been opened; the command history (arrow keys) still persists, which is the part worth
            // keeping — a fresh terminal emulator behaves exactly like this.
            if (menu.shellId().equals("live")) {
                // A booted installer medium: the live ISO's banner, already logged in as root.
                push(menu.osLabel() + " installation medium (tty1)", CliStyle.ACCENT);
                push("", CliStyle.PLAIN);
                push(menu.hostname() + " login: root (automatic login)", CliStyle.PLAIN);
                push("Type 'help' for the installation walkthrough.", CliStyle.DIM);
                push("", CliStyle.PLAIN);
            } else if (menu.posixShell()) {
                // A Linux TTY: the getty banner and an automatic login, then the shell greeting.
                push(menu.osLabel() + " " + menu.hostname() + " tty1", CliStyle.ACCENT);
                push("", CliStyle.PLAIN);
                push(menu.hostname() + " login: player", CliStyle.PLAIN);
                push("Password:", CliStyle.PLAIN);
                push("Welcome to " + menu.osLabel() + " (Linux 6.8-jsc x86_64)", CliStyle.DIM);
                push("", CliStyle.PLAIN);
            } else if (dosStyle()) {
                // MC-DOS wears a period boot banner instead of the generic shell greeting. The lines are
                // kept short on purpose so they never overflow the narrow 256px window.
                push(menu.osLabel() + "  Version 1.0  [Network Build]", CliStyle.ACCENT);
                push(dev.jstech.computronics.os.Branding.systemCopyright(
                        menu.osLabel(), screenEra()), CliStyle.DIM);
                push("640K base memory", CliStyle.DIM);
                push("", CliStyle.PLAIN);
            } else {
                push(dev.jstech.computronics.os.Branding.houseOf(menu.osLabel()).name()
                        + " Shell v1.0", CliStyle.ACCENT);
                push("type 'help' for commands, TAB to complete", CliStyle.DIM);
                push("", CliStyle.PLAIN);
            }
        }
        // Ask the server for this computer's saved history and the command list (for completion).
        PacketDistributor.sendToServer(new RequestConsoleInitPayload(menu.hostPos()));
    }

    // --- output ----------------------------------------------------------------------------------

    /** Routes a server output payload to the open Command Prompt, if one is showing. */
    public static void accept(final CommandOutputPayload payload) {
        if (Minecraft.getInstance().screen instanceof CommandPromptScreen screen) {
            screen.apply(payload);
        }
    }

    /** Seeds the open Command Prompt with the computer's saved history and the command list. */
    public static void acceptInit(final ConsoleInitPayload payload) {
        if (Minecraft.getInstance().screen instanceof CommandPromptScreen screen) {
            screen.applyInit(payload);
        }
    }

    private void applyInit(final ConsoleInitPayload payload) {
        // Only this computer's console seeds this screen: a reply raced from another machine's prompt must
        // never leak its history (Up-arrow on computer B recalling computer A's commands).
        if (!menu.hostPos().equals(payload.hostPos())) {
            return;
        }
        history.clear();
        history.addAll(payload.history());
        historyIndex = -1;
        commandNames.clear();
        commandUsage.clear();
        for (final ConsoleInitPayload.WireCommand command : payload.commands()) {
            commandNames.add(command.name());
            commandUsage.put(command.name(), command.usage());
        }
        deviceNames.clear();
        deviceNames.addAll(payload.devices());
        // Say which machine this session is on. It arrives with the init reply (a tick after the
        // window opens) rather than being guessed client-side, and it is what makes a terminal
        // opened over ssh or a KVM channel obviously belong to the machine it is talking to.
        if (!identityShown) {
            identityShown = true;
            final String machine = menu.hostname().isBlank() ? "machine" : menu.hostname();
            final String drives = payload.devices().isEmpty() ? "no drives"
                    : payload.devices().size() + (payload.devices().size() == 1 ? " drive" : " drives");
            push("machine: " + machine + "  ·  " + drives, CliStyle.DIM);
            push("", CliStyle.PLAIN);
        }
    }

    private void apply(final CommandOutputPayload payload) {
        if (payload.clear()) {
            scrollback.clear();
        }
        for (final CommandOutputPayload.WireLine line : payload.lines()) {
            push(line.text(), styleOf(line.style()));
        }
        // An empty prompt means "unchanged"; otherwise track the new current directory.
        if (!payload.prompt().isEmpty()) {
            dosPrompt = payload.prompt();
            reflowInput();
        }
        scrollOffset = 0;
    }

    /** Repositions the input box after the DOS prompt width changes (e.g. after a {@code cd}). */
    private void reflowInput() {
        if (input == null) {
            return;
        }
        final int promptW = font.width(prompt() + " ");
        input.setX(leftPos + 10 + promptW);
        input.setWidth(Math.max(20, imageWidth - 18 - promptW));
    }

    /** The console's scrollback, oldest first — what the player can read on the prompt right now. */
    public List<String> scrollbackText() {
        final List<String> lines = new ArrayList<>(scrollback.size());
        for (final Line line : scrollback) {
            lines.add(line.text());
        }
        return lines;
    }

    private void push(final String text, final CliStyle style) {
        // Wrap to the console's usable width so a long line (a help row, a path) never leaks past the
        // glass. Wrapping happens as lines land, so scrollback and the wheel scroll count real rows.
        final int maxPx = font == null ? Integer.MAX_VALUE
                : Math.max(40, (int) ((imageWidth - 20) / TEXT_SCALE));
        String rest = text;
        while (true) {
            if (font == null || font.width(rest) <= maxPx) {
                pushRaw(rest, style);
                return;
            }
            String piece = font.plainSubstrByWidth(rest, maxPx);
            // Prefer breaking at the last space when one sits reasonably far in, like a real terminal.
            final int space = piece.lastIndexOf(' ');
            if (space > piece.length() / 2) {
                piece = piece.substring(0, space);
            }
            if (piece.isEmpty()) {
                pushRaw(rest, style);
                return;
            }
            pushRaw(piece, style);
            rest = rest.substring(piece.length()).stripLeading();
            if (rest.isEmpty()) {
                return;
            }
        }
    }

    private void pushRaw(final String text, final CliStyle style) {
        scrollback.addLast(new Line(text, style));
        while (scrollback.size() > MAX_SCROLLBACK) {
            scrollback.removeFirst();
        }
    }

    private void submit() {
        final String line = input.getValue().trim();
        input.setValue("");
        historyIndex = -1;
        push(prompt() + " " + line, CliStyle.PROMPT);
        if (line.isEmpty()) {
            return;
        }
        if (history.isEmpty() || !history.get(history.size() - 1).equals(line)) {
            history.add(line);
        }
        scrollOffset = 0;
        PacketDistributor.sendToServer(new RunCommandPayload(menu.monitorPos(), menu.hostPos(), line));
    }

    // --- rendering -------------------------------------------------------------------------------

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;
        // The monitor frame wraps the whole software window in the host computer's hardware-era bezel.
        MonitorFrame.renderBody(g, x, y, imageWidth, imageHeight, screenEra(), font);
        if (bareTerminal()) {
            // A raw console: the whole glass is the terminal, no program window around it.
            g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF000000);
            return;
        }
        JscOsTheme.window(g, x, y, imageWidth, imageHeight);
        JscOsTheme.headerBar(g, x + 6, y + 6, imageWidth - 12);
        // The console panel.
        final int top = y + 26;
        final int bottom = y + imageHeight - 22;
        g.fill(x + 6, top, x + imageWidth - 6, bottom, CONSOLE);
        g.fill(x + 6, top, x + imageWidth - 6, top + 1, JscOsTheme.line());
        // Input strip.
        g.fill(x + 6, y + imageHeight - 20, x + imageWidth - 6, y + imageHeight - 8, JscOsTheme.panel());
        g.fill(x + 6, y + imageHeight - 20, x + imageWidth - 6, y + imageHeight - 19, JscOsTheme.line());
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        if (!bareTerminal()) {
            JscOsTheme.text(g, font, "COMMAND PROMPT", 12, 11, JscOsTheme.text());
            JscOsTheme.textRight(g, font, "PROGRAM", imageWidth - 10, 11, JscOsTheme.accent());
        }

        // Console scrollback, newest at the bottom, honoring the scroll offset.
        final int top = bareTerminal() ? 8 : 27;
        final int bottom = imageHeight - 23;
        final int visible = (bottom - top) / LINE_H;
        final List<Line> all = new ArrayList<>(scrollback);
        final int total = all.size();
        final int end = Math.max(0, total - scrollOffset);
        final int start = Math.max(0, end - visible);
        int row = 0;
        for (int i = start; i < end; i++) {
            final Line line = all.get(i);
            drawSmall(g, line.text(), 10, top + row * LINE_H, colorOf(line.style()));
            row++;
        }
        if (scrollOffset > 0) {
            JscOsTheme.textSRight(g, font, "scrolled +" + scrollOffset, imageWidth - 10, bottom - 7, JscOsTheme.dim());
        }

        // Prompt glyph before the input box.
        JscOsTheme.text(g, font, prompt(), 10, imageHeight - 18, JscOsTheme.accent());

        // Usage hint: once the verb is recognised, show how it is used, dimmed on the right.
        final String typed = input == null ? "" : input.getValue().trim();
        final int space = typed.indexOf(' ');
        final String verb = (space < 0 ? typed : typed.substring(0, space)).toLowerCase(Locale.ROOT);
        final String usage = commandUsage.get(verb);
        if (usage != null && !usage.isEmpty()) {
            // The hint shares the input line: it gets the room to the right of what is typed, and is cut
            // short rather than drawn over the prompt when a long usage does not fit.
            final int promptW = font.width(prompt() + " ");
            final int typedW = font.width(input == null ? "" : input.getValue());
            final int room = imageWidth - 10 - (10 + promptW + typedW + 12);
            if (room >= 40) {
                String hint = verb + " " + usage;
                if (JscOsTheme.widthS(font, hint) > room) {
                    String cut = hint;
                    while (cut.length() > 3 && JscOsTheme.widthS(font, cut + "..") > room) {
                        cut = cut.substring(0, cut.length() - 1);
                    }
                    hint = cut + "..";
                }
                JscOsTheme.textSRight(g, font, hint, imageWidth - 10, imageHeight - 17, JscOsTheme.dim());
            }
        }

        JscOsTheme.textS(g, font, "ENTER run    UP/DOWN history    wheel scroll    ESC close",
                10, imageHeight - 7, JscOsTheme.dim());
    }

    private void drawSmall(final GuiGraphics g, final String text, final int x, final int y, final int color) {
        if (text.isEmpty()) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private static CliStyle styleOf(final int ordinal) {
        final CliStyle[] values = CliStyle.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : CliStyle.PLAIN;
    }

    private int colorOf(final CliStyle style) {
        // A Vintage machine draws on a green-phosphor tube, which has ONE colour: every style comes out
        // as that green, brighter or dimmer, so an error still reads as an error without being red.
        final int color = terminalColor(style);
        return screenEra() == HardwareEra.VINTAGE ? dev.jstech.core.gui.Phosphor.green(color) : color;
    }

    /** The colour a style has on a monitor that can show colour. */
    private static int terminalColor(final CliStyle style) {
        return switch (style) {
            case PROMPT -> 0xFFCDD6E2;        // light gray-white for the echoed user command line
            case ACCENT, HEADER -> 0xFF39D6C4; // cyan for system messages
            case OK -> 0xFF5FE07A;             // green for success
            case ERROR -> 0xFFEF6A5A;          // red for errors
            case WARN -> 0xFFF0B23A;           // amber for warnings
            case INFO -> 0xFF2AA7E0;            // blue for informational output
            case DIM -> 0xFF7D8A9C;            // dim gray for hints and secondary output
            // The extended palette: brand-tinted terminal colors (screenfetch logos and the like).
            case ORANGE -> 0xFFE95420;
            case MAGENTA -> 0xFFE0447C;
            case BLUE -> 0xFF5A8FD6;
            case CYAN -> 0xFF2FA6E8;
            case PURPLE -> 0xFF9E8FD6;
            default -> 0xFFCDD6E2;             // plain = light gray
        };
    }

    // --- input -----------------------------------------------------------------------------------

    @Override
    public boolean keyPressed(final int key, final int scan, final int mods) {
        if (key == 257 || key == 335) { // Enter / numpad Enter
            submit();
            return true;
        }
        if (key == 258) { // Tab — complete the command word
            complete();
            return true;
        }
        if (key == 265) { // Up — older history
            recallHistory(-1);
            return true;
        }
        if (key == 264) { // Down — newer history
            recallHistory(1);
            return true;
        }
        if (key == 256) { // Esc closes the prompt
            onClose();
            return true;
        }
        // Everything else (typing, backspace, arrows within the line) goes to the input box, so the
        // inventory key never reaches the screen and closes it mid-command.
        if (input != null) {
            input.keyPressed(key, scan, mods);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(final char c, final int mods) {
        return input != null && input.charTyped(c, mods);
    }

    /**
     * Completes the command word the player is typing against the known command names, cycling
     * through the matches on repeated Tab. Only the first word (the verb) is completed for now.
     */
    private void complete() {
        final String text = input.getValue();
        if (text.isEmpty()) {
            return;
        }
        if (text.contains(" ")) {
            completeDevice(text);
            return; // other arguments are not completed yet; only the leading command word
        }
        final String prefix = text.toLowerCase(Locale.ROOT);
        final List<String> matches = new ArrayList<>();
        for (final String name : commandNames) {
            if (name.startsWith(prefix)) {
                matches.add(name);
            }
        }
        if (matches.isEmpty()) {
            return;
        }
        final String pick = matches.get(completionCycle % matches.size());
        completionCycle++;
        programmaticEdit = true;
        input.setValue(matches.size() == 1 ? pick + " " : pick);
        input.moveCursorToEnd(false);
        programmaticEdit = false;
    }

    /**
     * Completes a {@code /dev/<device>} first argument for the device verbs (mkfs.ext4, mount,
     * grub-install), cycling through the drives the server reported — so the Arch/Gentoo install never
     * needs the device names typed out by hand.
     */
    private void completeDevice(final String text) {
        final int space = text.indexOf(' ');
        final String verb = text.substring(0, space).toLowerCase(Locale.ROOT);
        final String arg = text.substring(space + 1);
        if (!DEVICE_VERBS.contains(verb) || deviceNames.isEmpty() || arg.contains(" ")) {
            return; // only the first argument of a device verb is completed
        }
        final String argPrefix = arg.toLowerCase(Locale.ROOT);
        final List<String> matches = new ArrayList<>();
        for (final String device : deviceNames) {
            final String full = "/dev/" + device;
            if (full.startsWith(argPrefix) || device.startsWith(argPrefix)) {
                matches.add(full);
            }
        }
        if (matches.isEmpty()) {
            return;
        }
        final String pick = matches.get(completionCycle % matches.size());
        completionCycle++;
        programmaticEdit = true;
        input.setValue(text.substring(0, space + 1) + pick + (matches.size() == 1 ? " " : ""));
        input.moveCursorToEnd(false);
        programmaticEdit = false;
    }

    private void recallHistory(final int direction) {
        if (history.isEmpty()) {
            return;
        }
        if (historyIndex == -1) {
            historyIndex = history.size();
        }
        historyIndex = Math.max(0, Math.min(history.size(), historyIndex + direction));
        if (historyIndex >= history.size()) {
            historyIndex = -1;
            input.setValue("");
        } else {
            input.setValue(history.get(historyIndex));
        }
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double dx, final double dy) {
        final int top = 27;
        final int bottom = imageHeight - 23;
        final int visible = (bottom - top) / LINE_H;
        final int maxScroll = Math.max(0, scrollback.size() - visible);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + (int) Math.signum(dy)));
        return true;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // Keep the input color in sync if a board swap changes the era while the screen is open. This
        // must repeat the bare-console rule, not just take the era's text colour: a bare terminal is
        // dark glass, and on Legacy the era text is dark for its cream panel — writing that here left
        // the player typing near-black on black.
        if (input != null) {
            input.setTextColor(colorOf(CliStyle.PROMPT));
        }
    }

    @Override
    public void removed() {
        // Nothing to remember: a terminal that is closed is over, and the next one opens fresh with
        // its own banner. The command history lives on the machine and comes back with the session.
        super.removed();
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
    }


    /** The initial POSIX prompt (home directory) in the installed shell's style, before the server syncs one. */
    private String initialPosixPrompt() {
        if (menu.shellId().equals("live")) {
            return "root@" + menu.hostname() + " ~ #";
        }
        return menu.shellId().equals("zsh")
                ? "player@" + menu.hostname() + " ~ %"
                : "player@" + menu.hostname() + ":~$";
    }

    /** The shell prompt: the server-synced prompt for MC-DOS and Linux (drive/directory aware), the jsc prompt otherwise. */
    private String prompt() {
        if (menu.posixShell()) {
            return dosPrompt.equals("C:\\>") ? initialPosixPrompt() : dosPrompt;
        }
        return dosStyle() ? dosPrompt : "jsc>";
    }

    @Override
    protected HardwareEra screenEra() {
        // Resolve the host computer's era from its block entity on the client so the first frame already wears
        // the right era skin; the synced era slot lagged a tick and flashed the default era on open. Fall back
        // to the synced value when the host isn't client-loaded.
        final net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(menu.hostPos())
                instanceof dev.jstech.computronics.blockentity.AbstractComputerBlockEntity host) {
            return host.displayEra();
        }
        return menu.hardwareEra();
    }

    /** One scrollback line: its text and the style that colours it. */
    private record Line(String text, CliStyle style) {
    }
}
