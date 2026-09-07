/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.jstech.computronics.blockentity.AbstractComputerBlockEntity;
import dev.jstech.computronics.operation.payload.FirmwareStatePayload;
import dev.jstech.computronics.operation.payload.PostCompletePayload;
import dev.jstech.computronics.operation.payload.RequestFirmwareStatePayload;
import dev.jstech.computronics.os.Branding;
import dev.jstech.computronics.os.FirmwareKind;
import dev.jstech.core.gui.Phosphor;
import dev.jstech.core.tier.HardwareEra;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The power-on self-test: what the monitor shows between switching the computer on (or a reboot) and the
 * boot target opening. Lines appear over a few seconds in the firmware's era style — memory count, detected
 * drives, the boot device — and DEL at any point enters the firmware setup instead. When the sequence ends
 * the client reports {@link PostCompletePayload} and the server swaps this screen for whatever boots.
 */
public final class BootSequenceScreen extends Screen {

    private static final int W = 340;
    private static final int H = 214;

    /** Ticks before the finished POST hands control to the boot target. */
    private static final int POST_TICKS = 70;
    /** Safety: if the server never swaps the screen (nothing could open), close on our own. */
    private static final int CLOSE_TICKS = POST_TICKS + 60;

    private final BlockPos computerPos;
    private final BlockPos monitorPos;
    private final FirmwareKind kind;
    private final String machineName;

    private static BootSequenceScreen active;

    private FirmwareStatePayload state;
    private int ticks;
    private boolean completed;
    private boolean setupRequested;

    public BootSequenceScreen(final BlockPos computerPos, final BlockPos monitorPos, final FirmwareKind kind,
                              final String machineName) {
        super(Component.literal("Power-On Self-Test"));
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

    /** Routes the server's hardware state to the running POST, which lists it as detected devices. */
    public static void accept(final FirmwareStatePayload payload) {
        if (active != null && active.computerPos.equals(payload.hostPos())) {
            active.state = payload;
        }
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
        if (!completed && !setupRequested && ticks >= POST_TICKS) {
            completed = true;
            PacketDistributor.sendToServer(new PostCompletePayload(computerPos, monitorPos, false));
        }
        if (ticks >= CLOSE_TICKS && Minecraft.getInstance().screen == this) {
            onClose();
        }
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (keyCode == InputConstants.KEY_DELETE && !completed && !setupRequested) {
            setupRequested = true;
            PacketDistributor.sendToServer(new PostCompletePayload(computerPos, monitorPos, true));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------------------
    // Render
    // ---------------------------------------------------------------------------

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        // Screen.render paints the dimmed backdrop itself; painting it again after our own drawing would
        // wash the whole POST out (the double-background bug), so super runs FIRST and the content after.
        super.render(g, mouseX, mouseY, partialTick);
        final int x = (width - W) / 2;
        final int y = (height - H) / 2;
        MonitorFrame.renderBody(g, x, y, W, H, era(), font);

        final int bg = switch (kind) {
            case CLI_BIOS -> 0xFF000000;
            case BLUE_BIOS -> 0xFF0000A8;
            case UEFI -> 0xFF10121C;
        };
        // The Vintage machine posts on a green-phosphor tube, which has exactly one colour: its text is
        // that green, brighter or dimmer, never the grey-white of a later monitor.
        final int text = switch (kind) {
            case CLI_BIOS -> Phosphor.green(0xFFB8B8B8);
            case BLUE_BIOS -> 0xFFE8E8E8;
            case UEFI -> 0xFFE6ECF6;
        };
        final int dim = switch (kind) {
            case CLI_BIOS -> Phosphor.green(0xFF707070);
            case BLUE_BIOS -> 0xFFB9C4D6;
            case UEFI -> 0xFF8090A8;
        };
        final int accent = switch (kind) {
            case CLI_BIOS -> Phosphor.green(0xFFE8E8E8);
            case BLUE_BIOS -> 0xFFFFE14D;
            case UEFI -> 0xFF5FA8D3;
        };
        g.fill(x, y, x + W, y + H, bg);

        if (kind == FirmwareKind.UEFI) {
            renderUefi(g, x, y, text, dim, accent);
        } else {
            renderClassic(g, x, y, text, dim, accent);
        }
    }

    /** The classic POST wall of text: BIOS banner, memory count, drive detection, boot line. */
    private void renderClassic(final GuiGraphics g, final int x, final int y, final int text, final int dim,
                               final int accent) {
        final List<String[]> lines = classicLines();
        int ty = y + 10;
        for (int i = 0; i < lines.size(); i++) {
            if (ticks < 4 + i * 4) {
                break; // lines appear one by one, like a machine actually finding its hardware
            }
            final String[] line = lines.get(i);
            g.drawString(font, line[0], x + 10, ty, "1".equals(line[1]) ? accent : text, false);
            ty += 10;
        }
        // The setup hint blinks at the bottom for the whole sequence.
        if ((ticks / 10) % 2 == 0 && !setupRequested) {
            g.drawString(font, "Press DEL to enter SETUP", x + 10, y + H - 14, dim, false);
        }
        if (setupRequested) {
            g.drawString(font, "Entering SETUP ...", x + 10, y + H - 14, accent, false);
        }
    }

    private List<String[]> classicLines() {
        final List<String[]> out = new ArrayList<>();
        out.add(new String[]{Branding.biosBanner(era()) + " - " + machineName, "1"});
        out.add(new String[]{Branding.firmwareCopyright(era()), "0"});
        out.add(new String[]{"", "0"});
        if (state != null) {
            out.add(new String[]{"Main Processor : " + state.cpuLabel() + " @ " + state.cpuMhz() + " MHz", "0"});
            // The memory test counts up while the POST runs, settling on the installed total.
            final long total = (long) state.ramMb() * 1024L;
            final long counted = Math.min(total, total * Math.max(0, ticks - 12) / 28L);
            out.add(new String[]{"Memory Test : " + String.format(Locale.ROOT, "%,d", counted) + " KB OK", "0"});
            out.add(new String[]{"", "0"});
            out.add(new String[]{"Detecting drives ...", "0"});
            int slot = 0;
            for (final FirmwareStatePayload.Entry entry : state.entries()) {
                final String role = entry.kind() == 0 ? ("  Disk " + slot++) : "  Media";
                out.add(new String[]{role + " : " + entry.label()
                        + (entry.detail().isEmpty() ? "" : " (" + entry.detail() + ")"), "0"});
            }
            out.add(new String[]{"", "0"});
            if (completed) {
                out.add(new String[]{"Booting ...", "1"});
            }
        } else {
            out.add(new String[]{"Reading system configuration ...", "0"});
        }
        return out;
    }

    /** The modern machines hide the wall of text: logo line, a progress bar, the setup hint. */
    private void renderUefi(final GuiGraphics g, final int x, final int y, final int text, final int dim,
                            final int accent) {
        final String logo = machineName.isEmpty() ? "JSC" : machineName;
        g.drawCenteredString(font, logo, x + W / 2, y + H / 2 - 24, text);
        g.drawCenteredString(font, "JSC UEFI 5.0", x + W / 2, y + H / 2 - 12, dim);
        // Progress: a thin bar filling across the POST duration.
        final int barW = 120;
        final int bx = x + (W - barW) / 2;
        final int by = y + H / 2 + 8;
        g.fill(bx, by, bx + barW, by + 3, 0xFF2A2D3E);
        final int fill = Math.min(barW, barW * ticks / POST_TICKS);
        g.fill(bx, by, bx + fill, by + 3, accent);
        if (setupRequested) {
            g.drawCenteredString(font, "Entering Setup ...", x + W / 2, y + H - 18, accent);
        } else if ((ticks / 10) % 2 == 0) {
            g.drawCenteredString(font, "Press DEL to enter Setup", x + W / 2, y + H - 18, dim);
        }
    }

    /** The host computer's hardware era, so the monitor bezel matches the machine. */
    private HardwareEra era() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(computerPos)
                instanceof dev.jstech.computronics.os.IOsHost be) {
            return be.displayEra();
        }
        return HardwareEra.STANDARD;
    }
}
