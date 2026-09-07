/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.machine;

import dev.jstech.computronics.blockentity.AbstractComputerBlockEntity;
import dev.jstech.computronics.cannon.run.Halt;
import dev.jstech.computronics.cannon.run.IHost;
import dev.jstech.computronics.cannon.run.Values;
import dev.jstech.computronics.hardware.ComputerBuild;
import dev.jstech.computronics.hardware.CpuSpec;
import dev.jstech.computronics.os.OsDef;
import dev.jstech.computronics.program.cli.ICliComputer;
import dev.jstech.computronics.storage.StorageKey;
import java.util.Locale;

/**
 * The machine, as the program running on it can read it.
 *
 * <p>Everything here is a picture taken when it is asked for, not a live view: what a program is handed
 * is its own to hold, and if it wants to know again it asks again. That is also what keeps any of this
 * writable to a save, because a picture is made of numbers and text and nothing else.
 */
public final class HostComputer {

    /** Reading a single number off the machine barely costs anything; gathering a list costs more. */
    private static final int GLANCE = dev.jstech.computronics.cannon.CannonCosts.GLANCE;
    private static final int GATHER = dev.jstech.computronics.cannon.CannonCosts.GATHER;

    private HostComputer() {
    }

    /** Whether this is one of the things read here. */
    public static boolean handles(final String owner) {
        return "Computer".equals(owner);
    }

    /** Answers one of them. */
    public static IHost.Reply call(final AbstractComputerBlockEntity machine, final ICliComputer shell,
                                  final String member, final int line) {
        return switch (member) {
            case "Name" -> IHost.Reply.of(name(machine), GLANCE);
            case "Cpu" -> IHost.Reply.of(cpu(machine), GLANCE);
            case "Os" -> IHost.Reply.of(os(machine), GLANCE);
            case "RamMb" -> IHost.Reply.of(machine.ramTotalMb(), GLANCE);
            case "FreeRamMb" -> IHost.Reply.of(machine.ramLedger().freeMb(), GLANCE);
            case "Online" -> IHost.Reply.of(machine.isRunning(), GLANCE);
            case "Disks" -> IHost.Reply.of(disks(shell), GATHER);
            case "Programs" -> IHost.Reply.of(programs(machine), GATHER);
            case "Processes" -> IHost.Reply.of(processes(machine), GATHER);
            default -> throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line, "Computer has no " + member);
        };
    }

    /** What the machine is called: the name its owner gave it, or what kind of machine it is. */
    private static String name(final AbstractComputerBlockEntity machine) {
        final String given = machine.customName();
        return given.isEmpty() ? machine.getBlockState().getBlock()
                .getName().getString() : given;
    }

    private static Values.Obj cpu(final AbstractComputerBlockEntity machine) {
        final Values.Obj made = new Values.Obj("CpuInfo");
        final ComputerBuild build = machine.currentBuild();
        int cores = 0;
        int mhz = 0;
        String era = "";
        if (build != null && !build.cpus().isEmpty()) {
            for (final CpuSpec one : build.cpus()) {
                cores += one.cores();
                mhz = Math.max(mhz, one.freqMhz());
            }
            era = build.cpus().getFirst().era().name().toLowerCase(Locale.ROOT);
        }
        made.set("Mhz", mhz);
        made.set("Cores", cores);
        made.set("Era", era);
        return made;
    }

    private static Values.Obj os(final AbstractComputerBlockEntity machine) {
        final Values.Obj made = new Values.Obj("OsInfo");
        final OsDef installed = machine.installedOs();
        made.set("Id", installed == null ? "" : installed.id().toString());
        made.set("Name", installed == null ? "" : installed.displayName());
        return made;
    }

    private static Values.ListValue disks(final ICliComputer shell) {
        final Values.ListValue all = new Values.ListValue();
        for (final ICliComputer.MountInfo mount : shell.mounts()) {
            final Values.Obj made = new Values.Obj("DiskInfo");
            made.set("Mount", String.valueOf(mount.drive()));
            made.set("CapacityMb", mount.capacityMbEq() / StorageKey.MB_EQ_PER_ITEM);
            made.set("UsedMb", (mount.capacityMbEq() - mount.freeMbEq()) / StorageKey.MB_EQ_PER_ITEM);
            all.items().add(made);
        }
        return all;
    }

    private static Values.ListValue programs(final AbstractComputerBlockEntity machine) {
        final Values.ListValue all = new Values.ListValue();
        all.items().addAll(machine.console().installed());
        return all;
    }

    private static Values.ListValue processes(final AbstractComputerBlockEntity machine) {
        final Values.ListValue all = new Values.ListValue();
        for (final MachinePrograms.Live one : machine.cannon().all()) {
            final Values.Obj made = new Values.Obj("ProcessInfo");
            made.set("Id", one.id());
            made.set("Name", one.name());
            made.set("State", one.process().state().name().toLowerCase(Locale.ROOT));
            made.set("HeldBytes", one.process().heldBytes());
            all.items().add(made);
        }
        return all;
    }
}
