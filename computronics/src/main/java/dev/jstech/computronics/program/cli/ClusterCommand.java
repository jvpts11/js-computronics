/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program.cli;

import dev.jstech.computronics.blockentity.ClusterManagementComputerBlockEntity;
import dev.jstech.computronics.blockentity.HbwInterfaceBlockEntity;
import dev.jstech.computronics.blockentity.ServerRackBlockEntity;
import dev.jstech.computronics.item.ServerItem;
import dev.jstech.computronics.os.OsDef;
import dev.jstech.computronics.os.IOsHost;
import dev.jstech.computronics.os.OsRegistry;
import dev.jstech.computronics.rack.RackChassis;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The shell's way into the clusters, for players who prefer a prompt or run a system with no desktop:
 * the same actions as the Cluster Manager, on the same machine only.
 */
public final class ClusterCommand implements ICliCommand {

    @Override
    public String name() {
        return "cluster";
    }

    @Override
    public String summary() {
        return "manage the supercomputers and datacenters on the network (Cluster Management Computer)";
    }

    @Override
    public String usage() {
        return "list | nodes <name> | power <name> [R:U] on|off | install <name> system|program | status | cancel";
    }

    @Override
    public boolean available(final ICliComputer computer) {
        return computer.hostBlock() instanceof ClusterManagementComputerBlockEntity;
    }

    @Override
    public void run(final CliContext ctx) {
        if (!(ctx.computer().hostBlock() instanceof ClusterManagementComputerBlockEntity cmc)) {
            ctx.out().error("cluster: this command only exists on a Cluster Management Computer");
            return;
        }
        if (cmc.clusterCard() == null) {
            ctx.out().error("cluster: no cluster interface card installed");
            return;
        }
        final String sub = ctx.arg(0).toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list", "ls", "" -> list(ctx, cmc);
            case "nodes" -> nodes(ctx, cmc, ctx.arg(1));
            case "power" -> power(ctx, cmc);
            case "install" -> install(ctx, cmc);
            case "status" -> status(ctx, cmc);
            case "cancel" -> ctx.out().info(cmc.cancelJob() ? "job cancelled after the nodes being written" : "no job running");
            default -> ctx.out().error("usage: cluster " + usage());
        }
    }

    /** A named cluster on the machine's network: supercomputers by interface name, sections by label. */
    private record Named(String name, ClusterManagementComputerBlockEntity.ClusterRef ref, String state, String load) {
    }

    private static List<Named> clusters(final ClusterManagementComputerBlockEntity cmc) {
        final List<Named> out = new ArrayList<>();
        int i = 1;
        for (final HbwInterfaceBlockEntity hub : cmc.supercomputers()) {
            // Unnamed supercomputers are numbered in the order the network registered their interfaces.
            final String name = ClusterManagementComputerBlockEntity.supercomputerName(hub, i - 1);
            out.add(new Named(name, new ClusterManagementComputerBlockEntity.ClusterRef(
                    RackChassis.RackType.SUPERCOMPUTER, hub.getBlockPos(), null),
                    hub.clusterOnline() ? "online" : "offline",
                    hub.craftSlotsInUse() + "/" + hub.parallelCrafts() + " crafts"));
            i++;
        }
        for (final ClusterManagementComputerBlockEntity.SectionRef section : cmc.datacenterSections()) {
            out.add(new Named(section.label(), new ClusterManagementComputerBlockEntity.ClusterRef(
                    RackChassis.RackType.SERVER, section.routerPos(), section.face()),
                    section.section().serverCount() > 0 ? "online" : "empty",
                    section.section().serverCount() + " servers"));
        }
        return out;
    }

    private static Named find(final ClusterManagementComputerBlockEntity cmc, final String name) {
        for (final Named c : clusters(cmc)) {
            if (c.name().equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }

    private static void list(final CliContext ctx, final ClusterManagementComputerBlockEntity cmc) {
        final List<Named> all = clusters(cmc);
        if (all.isEmpty()) {
            ctx.out().dim("no clusters reached on this network");
            return;
        }
        ctx.out().header(String.format(Locale.ROOT, "%-14s %-22s %-8s %s", "KIND", "NAME", "STATE", "LOAD"));
        for (final Named c : all) {
            final String kind = c.ref().kind() == RackChassis.RackType.SUPERCOMPUTER ? "supercomputer" : "datacenter";
            final String reach = cmc.reaches(c.ref().kind()) ? "" : "  (out of this card's reach)";
            ctx.out().line(String.format(Locale.ROOT, "%-14s %-22s %-8s %s%s", kind, c.name(), c.state(), c.load(), reach));
        }
    }

    private static void nodes(final CliContext ctx, final ClusterManagementComputerBlockEntity cmc, final String name) {
        final Named c = find(cmc, name);
        if (c == null) {
            ctx.out().error("cluster: no cluster named " + name);
            return;
        }
        ctx.out().header(String.format(Locale.ROOT, "%-8s %-18s %-14s %s", "RACK U", "NODE", "SYSTEM", "POWER"));
        int rackIndex = 0;
        BlockPos lastRack = null;
        for (final ClusterManagementComputerBlockEntity.NodeRef node : cmc.nodesOf(c.ref())) {
            if (!node.rack().equals(lastRack)) {
                rackIndex++;
                lastRack = node.rack();
            }
            if (!(cmc.getLevel().getBlockEntity(node.rack()) instanceof ServerRackBlockEntity rack)) {
                continue;
            }
            final IOsHost host = rack.unitHost(node.row());
            final ResourceLocation osId = host.installedOsId();
            final OsDef os = osId == null ? null : OsRegistry.getOs(osId);
            ctx.out().line(String.format(Locale.ROOT, "R%-2d U%-4d %-18s %-14s %s", rackIndex, node.row() + 1,
                    ClusterManagementComputerBlockEntity.nodeName(rack, node.row()),
                    os == null ? "none" : os.displayName(), rack.bayPowerOn(node.row()) ? "on" : "off"));
        }
    }

    private static void power(final CliContext ctx, final ClusterManagementComputerBlockEntity cmc) {
        final Named c = find(cmc, ctx.arg(1));
        if (c == null) {
            ctx.out().error("cluster: no cluster named " + ctx.arg(1));
            return;
        }
        final String last = ctx.arg(ctx.argCount() - 1).toLowerCase(Locale.ROOT);
        final boolean on = last.equals("on");
        if (!on && !last.equals("off")) {
            ctx.out().error("usage: cluster power <name> [R:U] on|off");
            return;
        }
        if (ctx.argCount() >= 4) {
            // R:U addresses one node: rack index in cluster order, unit row from 1.
            final String[] parts = ctx.arg(2).toUpperCase(Locale.ROOT).replace("R", "").replace("U", "").split(":");
            try {
                final int rackIndex = Integer.parseInt(parts[0]);
                final int row = Integer.parseInt(parts[1]) - 1;
                int index = 0;
                BlockPos lastRack = null;
                for (final ClusterManagementComputerBlockEntity.NodeRef node : cmc.nodesOf(c.ref())) {
                    if (!node.rack().equals(lastRack)) {
                        index++;
                        lastRack = node.rack();
                    }
                    if (index == rackIndex && node.row() == row) {
                        final ServerRackBlockEntity rack = (ServerRackBlockEntity) cmc.getLevel().getBlockEntity(node.rack());
                        if (rack != null && rack.bayPowerOn(row) != on && cmc.toggleNode(node.rack(), row)) {
                            ctx.out().ok(ClusterManagementComputerBlockEntity.nodeName(rack, row) + " bay switched " + last);
                        } else {
                            ctx.out().dim("already " + last);
                        }
                        return;
                    }
                }
                ctx.out().error("cluster: no node at " + ctx.arg(2));
            } catch (final RuntimeException badAddress) {
                ctx.out().error("usage: cluster power <name> R:U on|off");
            }
            return;
        }
        final int changed = cmc.powerAll(c.ref(), on);
        ctx.out().ok(changed + " bay" + (changed == 1 ? "" : "s") + " switched " + last);
    }

    private static void install(final CliContext ctx, final ClusterManagementComputerBlockEntity cmc) {
        final Named c = find(cmc, ctx.arg(1));
        if (c == null) {
            ctx.out().error("cluster: no cluster named " + ctx.arg(1));
            return;
        }
        final String what = ctx.arg(2).toLowerCase(Locale.ROOT);
        final ClusterManagementComputerBlockEntity.JobKind kind;
        if (what.equals("system")) {
            kind = ClusterManagementComputerBlockEntity.JobKind.SYSTEM;
        } else if (what.equals("program")) {
            kind = ClusterManagementComputerBlockEntity.JobKind.PROGRAM;
        } else {
            ctx.out().error("usage: cluster install <name> system|program");
            return;
        }
        final String result = cmc.startJob(c.ref(), kind);
        if (cmc.job() != null) {
            ctx.out().ok(result + " · " + cmc.parallelLanes() + " at a time; follow it with 'cluster status'");
        } else {
            ctx.out().error("cluster: " + result);
        }
    }

    private static void status(final CliContext ctx, final ClusterManagementComputerBlockEntity cmc) {
        final ClusterManagementComputerBlockEntity.InstallJob job = cmc.job();
        if (job == null) {
            ctx.out().dim(cmc.lastJobSummary().isEmpty() ? "no job running" : "last job " + cmc.lastJobSummary());
            return;
        }
        ctx.out().info("job: install " + job.medium().label() + " · " + job.done() + " done · " + job.lanes().size()
                + " writing · " + job.queued() + " queued · " + job.skipped() + " skipped"
                + (job.cancelled() ? " · cancelling" : ""));
        for (final ClusterManagementComputerBlockEntity.Lane lane : job.lanes()) {
            ctx.out().line("  " + lane.name() + " ... " + (lane.permille() / 10) + "%");
        }
    }

    /** The card's reach, for the shell's own messages. */
    static String reachWord(final ClusterManagementComputerBlockEntity cmc) {
        return cmc.clusterCard() == null ? "none" : cmc.clusterCard().reach().name().toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unused")
    private static String serverName(final ServerRackBlockEntity rack, final int row) {
        return ServerItem.customName(rack.getServers().getStackInSlot(row));
    }
}
