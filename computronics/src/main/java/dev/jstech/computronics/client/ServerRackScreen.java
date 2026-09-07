/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client;

import dev.jstech.computronics.gui.layout.ServerRackLayout;
import dev.jstech.computronics.hardware.ComputerBuild;
import dev.jstech.computronics.item.ServerItem;
import dev.jstech.computronics.menu.ServerRackMenu;
import dev.jstech.computronics.operation.payload.RackBayPowerPayload;
import dev.jstech.computronics.rack.RackChassis;
import dev.jstech.computronics.rack.RackLayout;
import dev.jstech.computronics.rack.RaidMode;
import dev.jstech.core.client.gui.theme.EraTheme;
import dev.jstech.core.client.gui.theme.EraThemes;
import dev.jstech.core.client.gui.theme.JsTechTheme;
import dev.jstech.core.tier.HardwareEra;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Screen for the Server Rack: physical only, by design: a rack-unit ruler, one row per unit with
 * the server slot and the rack's five hotswap slots, and a power switch per bay. No console and no
 * terminal live here: software access always goes through a monitor cabled to the rack.
 */
public class ServerRackScreen extends AbstractContainerScreen<ServerRackMenu> {

    private static final int ROWS = ServerRackLayout.ROWS;

    public ServerRackScreen(final ServerRackMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
        this.imageWidth = ServerRackLayout.WIDTH;
        this.imageHeight = ServerRackLayout.HEIGHT;
        this.titleLabelX = -10000;
        this.inventoryLabelY = -10000;
    }

    /** The top row of the unit occupying {@code row} (the row itself when it holds the machine). */
    private int unitTopOf(final int row) {
        final int covered = coveredBy(row);
        return covered >= 0 ? covered : row;
    }

    /** The top row of the chassis covering {@code row} from above, or -1 when the row is its own. */
    private int coveredBy(final int row) {
        for (int i = 0; i < row; i++) {
            final RackChassis chassis = ServerItem.chassisOf(menu.serverInBay(i));
            if (chassis != null && row < i + chassis.heightU()) {
                return i;
            }
        }
        return -1;
    }

    /*
     * The cabinet's own look
     *
     * The rack is drawn as a piece of equipment rather than a flat panel: a brushed body, side rails
     * the rows sit between, relief on every bay, and status lights. Rack hardware is the one screen in
     * the mod that IS a physical object, so it wears the cabinet's OWN material, and a cabinet built
     * in 1994 is not made of the same metal as one built today, so there is a set per era, matching the
     * colours of that era's block model. The labels follow the era skin like every other screen.
     */

    /** Everything the cabinet is made of, so one set of values can be swapped for another era's. */
    private record Materials(int bodyTop, int bodyBottom, int bodyEdge, int bodyGloss,
                             int headTop, int headBottom, int rail, int rowRule,
                             int srvTop, int srvBottom, int srvEdge,
                             int slotTop, int slotBottom, int slotEdge, int slotGloss, int vent,
                             int pwrOnTop, int pwrOnBottom, int pwrOffTop, int pwrOffBottom,
                             int blockedFill, int blockedText, int gadgetMark) {
    }

    /** Today's machine: black steel and cyan. Unchanged, to the value. */
    private static final Materials STANDARD_RACK = new Materials(
            0xFF171D24, 0xFF10151B, 0xFF39434F, 0xFF4A5563,
            0xFF222A33, 0xFF171D25, 0xFF2A323B, 0xFF1B2129,
            0xFF2B333D, 0xFF1A2028, 0xFF454F5C,
            0xFF232A33, 0xFF161B21, 0xFF3C4653, 0xFF4D5765, 0xFF0D1116,
            0xFF243B2A, 0xFF16261A, 0xFF3B2424, 0xFF261616,
            0xFF232834, 0xFF4A5262, 0xFFF0B23A);

    /** The grey-and-cream machine room of the nineties, the colours of the Legacy cabinet. */
    private static final Materials LEGACY_RACK = new Materials(
            0xFFB4B0A0, 0xFF9C9888, 0xFF6E6A58, 0xFFE4E0D0,
            0xFFC4C0AC, 0xFFAAA694, 0xFF8A8676, 0xFF9A9684,
            0xFFD2CEBC, 0xFFB4B0A0, 0xFF6E6A58,
            0xFFC0BCA8, 0xFFA6A290, 0xFF6E6A58, 0xFFE4E0D0, 0xFF57544A,
            0xFF3E7A46, 0xFF265A2E, 0xFF9A3A32, 0xFF6E221C,
            0xFFA6A290, 0xFF6E6A58, 0xFFB8860B);

    /** Beige plastic and a green screen: the Vintage cabinet, lit the way its own monitor is. */
    private static final Materials VINTAGE_RACK = new Materials(
            0xFF0A160A, 0xFF040D04, 0xFF1E5A1E, 0xFF2E8B2E,
            0xFF0E1E0E, 0xFF071407, 0xFF103810, 0xFF0A2A0A,
            0xFF103010, 0xFF071807, 0xFF1E5A1E,
            0xFF0C260C, 0xFF041004, 0xFF1E5A1E, 0xFF2E8B2E, 0xFF000000,
            0xFF10401A, 0xFF072207, 0xFF3E1410, 0xFF1E0A08,
            0xFF061806, 0xFF2E8B2E, 0xFFFFB000);

    /** What this cabinet is made of; resolved from its era every tick, so a rebuild repaints it live. */
    private Materials mat = STANDARD_RACK;
    private EraTheme theme = EraThemes.STANDARD;

    private void resolveEra() {
        final HardwareEra era = menu.rackEra();
        this.theme = EraThemes.ofNullable(era);
        this.mat = era == HardwareEra.VINTAGE ? VINTAGE_RACK
                : era == HardwareEra.LEGACY ? LEGACY_RACK : STANDARD_RACK;
    }

    @Override
    protected void init() {
        super.init();
        resolveEra();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        resolveEra();
    }

    /** What this cabinet is called: a compute cabinet is not a server rack, and each era has its own name. */
    private String cabinetName() {
        return title.getString().toUpperCase(java.util.Locale.ROOT);
    }

    /** A raised face: vertical gradient, outline, and a light top edge. */
    private static void raised(final GuiGraphics g, final int x, final int y, final int w, final int h,
                              final int top, final int bottom, final int edge, final int gloss) {
        g.fillGradient(x, y, x + w, y + h, top, bottom);
        g.fill(x, y, x + w, y + 1, edge);
        g.fill(x, y + h - 1, x + w, y + h, edge);
        g.fill(x, y, x + 1, y + h, edge);
        g.fill(x + w - 1, y, x + w, y + h, edge);
        if (gloss != 0) {
            g.fill(x + 1, y + 1, x + w - 1, y + 2, gloss);
        }
    }

    /** A status light: a lit core with a dimmer halo, so it reads as a lamp and not a square. */
    private static void led(final GuiGraphics g, final int x, final int y, final int color) {
        final int halo = (color & 0x00FFFFFF) | 0x55000000;
        g.fill(x, y - 1, x + 3, y + 4, halo);
        g.fill(x - 1, y, x + 4, y + 3, halo);
        g.fill(x, y, x + 3, y + 3, color);
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;
        // Cabinet body.
        g.fillGradient(x, y, x + imageWidth, y + imageHeight, mat.bodyTop(), mat.bodyBottom());
        g.fill(x, y, x + imageWidth, y + 1, mat.bodyGloss());
        g.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, mat.bodyEdge());
        g.fill(x, y, x + 1, y + imageHeight, mat.bodyEdge());
        g.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, mat.bodyEdge());
        // Header plate.
        raised(g, x + ServerRackLayout.HEADER_X, y + ServerRackLayout.HEADER_Y,
                ServerRackLayout.HEADER_W, ServerRackLayout.HEADER_H, mat.headTop(), mat.headBottom(),
                mat.bodyEdge(), mat.bodyGloss());
        // The mounting rails the rows sit between.
        final int railTop = y + ServerRackLayout.rowY(0) - 2;
        final int railBottom = y + ServerRackLayout.rowY(ROWS - 1) + ServerRackLayout.SLOT + 2;
        g.fill(x + 4, railTop, x + 7, railBottom, mat.rail());
        g.fill(x + imageWidth - 7, railTop, x + imageWidth - 4, railBottom, mat.rail());

        for (int row = 0; row < ROWS; row++) {
            final int top = y + ServerRackLayout.rowY(row);
            // Rack-unit ruler cell, recessed into the rail.
            g.fill(x + ServerRackLayout.RULER_X, top, x + ServerRackLayout.RULER_X + 14,
                    top + ServerRackLayout.SLOT, 0xFF141920);
            g.fill(x + ServerRackLayout.RULER_X, top, x + ServerRackLayout.RULER_X + 14, top + 1, 0xFF262D36);
            // Row separator, so eight units read as eight shelves.
            g.fill(x + 8, top + ServerRackLayout.SLOT, x + imageWidth - 8,
                    top + ServerRackLayout.SLOT + 1, mat.rowRule());
            /*
             * The server slot: a mounted unit's top row shows the machine; a covered row is part
             * of the chassis above, so its cell reads as continuation rather than a free slot.
             */
            final boolean covered = coveredBy(row) >= 0;
            if (covered) {
                g.fill(x + ServerRackLayout.SERVER_X, top,
                        x + ServerRackLayout.SERVER_X + ServerRackLayout.SLOT,
                        top + ServerRackLayout.SLOT, mat.blockedFill());
            } else {
                raised(g, x + ServerRackLayout.SERVER_X, top, ServerRackLayout.SLOT,
                        ServerRackLayout.SLOT, mat.srvTop(), mat.srvBottom(), mat.srvEdge(), 0xFF5B6673);
            }
            // The five hotswap slots of this row, colored by what the mounted chassis cables.
            for (int column = 0; column < ServerRackLayout.FRONT_SLOTS; column++) {
                final int slotX = x + ServerRackLayout.frontSlotX(column);
                final RackLayout.SlotRole role =
                        menu.frontSlotRole(row * RackLayout.SLOTS_PER_U + column);
                switch (role) {
                    case DRIVE -> {
                        raised(g, slotX, top, ServerRackLayout.SLOT, ServerRackLayout.SLOT,
                                mat.slotTop(), mat.slotBottom(), mat.slotEdge(), mat.slotGloss());
                        // Two vent slits: the tray face of a drive caddy.
                        g.fill(slotX + 3, top + 6, slotX + ServerRackLayout.SLOT - 3, top + 7, mat.vent());
                        g.fill(slotX + 3, top + 11, slotX + ServerRackLayout.SLOT - 3, top + 12, mat.vent());
                    }
                    case GADGET -> {
                        raised(g, slotX, top, ServerRackLayout.SLOT, ServerRackLayout.SLOT,
                                mat.slotTop(), mat.slotBottom(), mat.slotEdge(), mat.slotGloss());
                        // A gadget bay wears an amber sill instead of drive vents.
                        g.fill(slotX + 2, top + ServerRackLayout.SLOT - 4,
                                slotX + ServerRackLayout.SLOT - 2, top + ServerRackLayout.SLOT - 2,
                                0xFF7A5C17);
                    }
                    case BLOCKED_NO_UNIT, BLOCKED_BUDGET -> {
                        g.fill(slotX, top, slotX + ServerRackLayout.SLOT,
                                top + ServerRackLayout.SLOT, 0xFF141920);
                        g.fill(slotX, top, slotX + ServerRackLayout.SLOT, top + 1, 0xFF262D36);
                    }
                }
            }
            // A rebuilding array draws its progress as a thin bar under the row's status text.
            final int rebuild = menu.rebuildPermille(row);
            if (rebuild > 0) {
                final int barX = x + ServerRackLayout.STATUS_X;
                final int barW = ServerRackLayout.PWR_X - ServerRackLayout.STATUS_X - 4;
                final int barY = top + ServerRackLayout.SLOT - 4;
                g.fill(barX, barY, barX + barW, barY + 2, 0xFF232834);
                g.fill(barX, barY, barX + barW * rebuild / 1000, barY + 2, JsTechTheme.amber());
            }
            // The power switch on a mounted unit's top row, with the bay's own status lamp.
            if (!covered && menu.serverInBay(row).getItem() instanceof ServerItem) {
                final boolean on = menu.bayPowerOn(row);
                final int px = x + ServerRackLayout.PWR_X;
                final int py = top + ServerRackLayout.PWR_DY;
                raised(g, px, py, ServerRackLayout.PWR_W, ServerRackLayout.PWR_H,
                        on ? mat.pwrOnTop() : mat.pwrOffTop(), on ? mat.pwrOnBottom() : mat.pwrOffBottom(),
                        on ? 0xFF3F6B4B : 0xFF6B3F3F, on ? 0xFF4F7A5B : 0xFF7A4F4F);
                led(g, x + ServerRackLayout.STATUS_X - 8, top + 7,
                        on ? JsTechTheme.green() : JsTechTheme.red());
            }
        }

        // Player inventory, in the same material as the bays so the panel reads as one object.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                raised(g, x + ServerRackLayout.INV_X + col * 18, y + ServerRackLayout.INV_Y + row * 18,
                        18, 18, mat.slotTop(), mat.slotBottom(), mat.slotEdge(), mat.slotGloss());
            }
        }
        for (int col = 0; col < 9; col++) {
            raised(g, x + ServerRackLayout.INV_X + col * 18, y + ServerRackLayout.HOTBAR_Y,
                    18, 18, mat.slotTop(), mat.slotBottom(), mat.slotEdge(), mat.slotGloss());
        }
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        final boolean linked = menu.networkLinked();
        JsTechTheme.text(g, font, cabinetName(), 12, 11, JsTechTheme.text());
        /*
         * The header carries the cabinet summary beside the link pill: used rack units and whether
         * the rack's rear cable sits on a network.
         */
        int usedU = 0;
        for (int i = 0; i < ROWS; i++) {
            final RackChassis chassis = ServerItem.chassisOf(menu.serverInBay(i));
            if (chassis != null) {
                usedU += chassis.heightU();
            }
        }
        /*
         * A cabinet past its thermal budget says so where the link state would sit: it is the thing
         * the player most needs to know about this rack right now.
         */
        final int throttle = menu.throttlePercent();
        final String pill = throttle < 100
                ? usedU + "/" + ROWS + "U  THROTTLED " + throttle + "%"
                : usedU + "/" + ROWS + "U  " + (linked ? "LINKED" : "OFFLINE");
        final int pillColor = throttle < 100 ? JsTechTheme.amber()
                : linked ? JsTechTheme.green() : JsTechTheme.red();
        final int pillX = 232 - font.width(pill);
        JsTechTheme.text(g, font, pill, pillX, 11, pillColor);
        g.fill(pillX - 6, 11, pillX - 2, 15, pillColor);

        for (int row = 0; row < ROWS; row++) {
            final int top = ServerRackLayout.rowY(row);
            JsTechTheme.textS(g, font, (row + 1) + "U", ServerRackLayout.RULER_X + 2, top + 6,
                    JsTechTheme.dim());
            for (int column = 0; column < ServerRackLayout.FRONT_SLOTS; column++) {
                final RackLayout.SlotRole role =
                        menu.frontSlotRole(row * RackLayout.SLOTS_PER_U + column);
                if ((role == RackLayout.SlotRole.BLOCKED_NO_UNIT
                        || role == RackLayout.SlotRole.BLOCKED_BUDGET)
                        && menu.frontSlotStack(row * RackLayout.SLOTS_PER_U + column).isEmpty()) {
                    JsTechTheme.textS(g, font, "x", ServerRackLayout.frontSlotX(column) + 7, top + 6,
                            mat.blockedText());
                }
            }
            final int covered = coveredBy(row);
            if (covered >= 0) {
                JsTechTheme.textS(g, font, "^ " + (covered + 1) + "U", ServerRackLayout.STATUS_X,
                        top + 6, JsTechTheme.dim());
                continue;
            }
            final ItemStack server = menu.serverInBay(row);
            if (!(server.getItem() instanceof ServerItem)) {
                continue;
            }
            final ComputerBuild build = ServerItem.build(server);
            final String state;
            final int color;
            if (build == null) {
                state = "INCOMPLETE";
                color = JsTechTheme.red();
            } else if (!menu.bayPowerOn(row)) {
                state = "OFF";
                color = JsTechTheme.dim();
            } else if (linked) {
                state = "ONLINE";
                color = JsTechTheme.green();
            } else {
                state = "READY";
                color = JsTechTheme.amber();
            }
            // An array replaces the plain machine state in the row: its health is what matters here.
            final String raid = menu.raidLabel(row);
            final int rebuild = menu.rebuildPermille(row);
            if (rebuild > 0 && build != null) {
                JsTechTheme.textS(g, font, "REBUILD " + rebuild / 10 + "%",
                        ServerRackLayout.STATUS_X, top + 6, JsTechTheme.amber());
            } else if (raid != null && build != null) {
                final int raidColor = raid.endsWith("FAILED") ? JsTechTheme.red()
                        : raid.endsWith("DEGRADED") ? JsTechTheme.amber() : JsTechTheme.green();
                JsTechTheme.textS(g, font, raid, ServerRackLayout.STATUS_X, top + 6, raidColor);
            } else {
                JsTechTheme.textS(g, font, state, ServerRackLayout.STATUS_X, top + 6, color);
            }
            JsTechTheme.textS(g, font, "PWR", ServerRackLayout.PWR_X + 3,
                    top + ServerRackLayout.PWR_DY + 2,
                    menu.bayPowerOn(row) ? JsTechTheme.green() : JsTechTheme.red());
        }
    }

    /*
     * The rack GUI stays physical: it REPORTS the array's health but never configures it. A RAID
     * controller is set up in its own firmware setup at power-on, like the real thing; see the
     * machine's STORAGE page in the BIOS.
     */

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        final double relX = mouseX - leftPos;
        final double relY = mouseY - topPos;
        for (int row = 0; row < ROWS; row++) {
            if (coveredBy(row) >= 0 || !(menu.serverInBay(row).getItem() instanceof ServerItem)) {
                continue;
            }
            final int top = ServerRackLayout.rowY(row) + ServerRackLayout.PWR_DY;
            if (relX >= ServerRackLayout.PWR_X && relX < ServerRackLayout.PWR_X + ServerRackLayout.PWR_W
                    && relY >= top && relY < top + ServerRackLayout.PWR_H) {
                PacketDistributor.sendToServer(new RackBayPowerPayload(menu.rackPos(), row));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        /*
         * The cabinet's labels are drawn in its era's skin, and the default is always restored so an
         * unthemed draw elsewhere still gets the frozen Standard look.
         */
        JsTechTheme.bind(theme);
        try {
            super.render(g, mouseX, mouseY, partialTick);
            renderTooltip(g, mouseX, mouseY);
            renderRackTooltip(g, mouseX, mouseY);
        } finally {
            JsTechTheme.unbind();
        }
    }

    private void renderRackTooltip(final GuiGraphics g, final int mouseX, final int mouseY) {
        final int relX = mouseX - leftPos;
        final int relY = mouseY - topPos;
        /*
         * A chassis this cabinet does not seat says so the moment it is carried over a row, instead of
         * the slot silently refusing it, so the player learns which cabinet it belongs in before letting go.
         */
        final net.minecraft.world.item.ItemStack carried = menu.getCarried();
        final dev.jstech.computronics.rack.RackChassis carriedChassis =
                dev.jstech.computronics.item.ServerItem.chassisOf(carried);
        final boolean refused = carriedChassis != null
                && net.minecraft.client.Minecraft.getInstance().level != null
                && net.minecraft.client.Minecraft.getInstance().level.getBlockEntity(menu.rackPos())
                        instanceof dev.jstech.computronics.blockentity.ServerRackBlockEntity rack
                && !rack.acceptsChassis(carried);
        for (int row = 0; row < ROWS; row++) {
            final int top = ServerRackLayout.rowY(row);
            if (relY < top || relY >= top + ServerRackLayout.SLOT) {
                continue;
            }
            if (refused) {
                final String cabinet = switch (carriedChassis.rackType()) {
                    case SERVER -> "a Server Rack";
                    case SUPERCOMPUTER -> "a Supercomputer Rack";
                    case AI -> "an AI Rack";
                };
                g.renderTooltip(font, Component.literal("This chassis belongs in " + cabinet), mouseX, mouseY);
                return;
            }
            // Blocked front slots explain themselves (the mounted chassis derives the reason).
            for (int column = 0; column < ServerRackLayout.FRONT_SLOTS; column++) {
                final int slotX = ServerRackLayout.frontSlotX(column);
                if (relX < slotX || relX >= slotX + ServerRackLayout.SLOT) {
                    continue;
                }
                final int index = row * RackLayout.SLOTS_PER_U + column;
                if (!menu.frontSlotStack(index).isEmpty()) {
                    return; // the item's own tooltip already shows
                }
                if (menu.frontSlotRole(index) == RackLayout.SlotRole.DRIVE) {
                    final String raid = menu.raidLabel(unitTopOf(row));
                    if (raid != null) {
                        g.renderTooltip(font, Component.literal("Array member slot - " + raid),
                                mouseX, mouseY);
                        return;
                    }
                }
                if (menu.frontSlotRole(index) == RackLayout.SlotRole.GADGET) {
                    /*
                     * Point at where a controller is actually configured, so the player is never
                     * left clicking a gadget hoping something opens.
                     */
                    g.renderTooltip(font, Component.literal(
                            "Gadget bay - a RAID Controller is configured in the machine's firmware (STORAGE)"),
                            mouseX, mouseY);
                    return;
                }
                final String tip = switch (menu.frontSlotRole(index)) {
                    case DRIVE -> "Drive bay - hotswap a disk here";
                    case GADGET -> "Gadget bay - RAID controller or cache card";
                    case BLOCKED_NO_UNIT -> "Blocked - no unit in this row cables these slots";
                    case BLOCKED_BUDGET -> "Blocked - the chassis does not cable this slot";
                };
                g.renderTooltip(font, Component.literal(tip), mouseX, mouseY);
                return;
            }
            // The unit's status area shows the machine summary.
            if (relX >= ServerRackLayout.STATUS_X && relX < ServerRackLayout.PWR_X && coveredBy(row) < 0
                    && menu.serverInBay(row).getItem() instanceof ServerItem) {
                final ItemStack server = menu.serverInBay(row);
                final List<Component> lines = new ArrayList<>();
                final UUID uuid = ServerItem.nodeUuid(server);
                lines.add(Component.literal(uuid != null
                        ? "Node " + uuid.toString().substring(0, 8) : "Unassigned node"));
                final RackChassis chassis = ServerItem.chassisOf(server);
                if (chassis != null) {
                    lines.add(Component.literal(chassis.heightU() + "U - " + chassis.driveSlots()
                            + " drive + " + chassis.gadgetSlots() + " gadget bays")
                            .withStyle(ChatFormatting.GRAY));
                }
                if (ServerItem.build(server) == null) {
                    lines.add(Component.literal("Incomplete - needs a board + PSU")
                            .withStyle(ChatFormatting.RED));
                } else if (!menu.networkLinked()) {
                    lines.add(Component.literal("Rack cable not on a network")
                            .withStyle(ChatFormatting.YELLOW));
                }
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
                return;
            }
        }
    }
}
