/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.datagen;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.HardwareItems;
import dev.jstech.computronics.JsComputronics;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/**
 * Generates the base English ({@code en_us}) language file.
 */
public class JscLanguageProvider extends LanguageProvider {

    public JscLanguageProvider(final PackOutput output) {
        super(output, JsComputronics.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("itemGroup.jsc.computing", "J's Computronics");
        addBlock(ComputingModule.ETHERNET_CABLE, "Ethernet Cable");
        addBlock(ComputingModule.HBW_CABLE, "HBW Cable");
        addBlock(ComputingModule.HPC_CABLE, "High Compute Cable");
        addBlock(ComputingModule.CRAFTING_CABLE, "Crafting Cable");
        addBlock(ComputingModule.CRAFTING_SWITCH, "Crafting Switch");
        addBlock(ComputingModule.PERIPHERAL_CABLE, "Peripheral Cable");
        addBlock(ComputingModule.PERSONAL_ROUTER, "Personal Router");
        addBlock(ComputingModule.SERVER_ROUTER, "Server Router");
        addBlock(ComputingModule.MONITOR, "Monitor");
        addBlock(ComputingModule.VINTAGE_MONITOR, "Vintage Monitor");
        addBlock(ComputingModule.LEGACY_MONITOR, "Legacy Monitor");
        addBlock(ComputingModule.TANK, "Tank");
        add("block.jsc.monitor.unlinked", "No computer linked");
        add("block.jsc.monitor.no_computer", "No computer found in range over a Peripheral Cable");
        add("block.jsc.monitor.no_gpu", "The computer has no GPU - install a GPU to host monitors (4 per GPU)");
        add("block.jsc.monitor.at_capacity", "The computer's monitor outputs are all in use");
        add("block.jsc.monitor.no_os", "No operating system - power the computer on and use the monitor to enter its firmware");
        add("block.jsc.monitor.no_power", "No signal - the computer is powered off");
        add("block.jsc.rack.wrong_chassis", "This chassis belongs in a different rack");
        add("block.jsc.rack.row_taken", "That rack unit is taken or too small for this");
        add("block.jsc.monitor.rack_empty", "The rack holds no computer to show");
        add("block.jsc.monitor.needs_kvm", "This rack holds several computers - mount a KVM Switch to pick one");
        add("block.jsc.monitor.hint_command_prompt", "Sneak-use to open the Command Prompt");
        // Program display names come from the single program registry, so a new program's name is written
        // once (on its ProgramSpec) and datagen emits its translation key here automatically.
        for (final dev.jstech.computronics.os.ProgramSpec program
                : dev.jstech.computronics.os.OsBootstrap.builtinPrograms()) {
            add(program.titleKey(), program.displayName());
            // Every program says what it does, in one line, wherever it is shown: on its install
            // disc, in the package manager, and in the installed-programs list. A program the player
            // cannot tell apart from its neighbours might as well not be installable.
        }
        addProgramBlurbs();
        addBlock(ComputingModule.MAINFRAME, "Mainframe");
        add("item.jsc.mainframe.tooltip", "Forms a 3x2x2 structure when placed");
        addBlock(ComputingModule.VINTAGE_MAINFRAME, "Vintage Mainframe");
        addBlock(ComputingModule.LEGACY_MAINFRAME, "Legacy Mainframe");
        addBlock(ComputingModule.PERSONAL_COMPUTER, "Personal Computer");
        addBlock(ComputingModule.VINTAGE_PERSONAL_COMPUTER, "Vintage Personal Computer");
        addBlock(ComputingModule.LEGACY_PERSONAL_COMPUTER, "Legacy Personal Computer");
        addBlock(ComputingModule.CRAFTING_COMPUTER, "Crafting Computer");
        addBlock(ComputingModule.VINTAGE_CRAFTING_COMPUTER, "Vintage Crafting Computer");
        addBlock(ComputingModule.LEGACY_CRAFTING_COMPUTER, "Legacy Crafting Computer");
        addBlock(ComputingModule.CLUSTER_MANAGEMENT_COMPUTER, "Cluster Management Computer");
        addBlock(ComputingModule.VINTAGE_CLUSTER_MANAGEMENT_COMPUTER, "Vintage Cluster Management Computer");
        addBlock(ComputingModule.LEGACY_CLUSTER_MANAGEMENT_COMPUTER, "Legacy Cluster Management Computer");
        addBlock(ComputingModule.PATTERN_ENCODER, "Pattern Encoder");
        addBlock(ComputingModule.LEGACY_PATTERN_ENCODER, "Legacy Pattern Encoder");
        addBlock(ComputingModule.VINTAGE_PATTERN_ENCODER, "Vintage Pattern Encoder");
        addBlock(ComputingModule.FLOPPY_DRIVE, "Floppy Drive");
        addBlock(ComputingModule.CD_DRIVE, "CD Drive");
        addBlock(ComputingModule.DVD_DRIVE, "DVD Drive");
        addBlock(ComputingModule.DOCK_STATION, "Dock Station");
        add(ComputingModule.FLOPPY_DISK.get(), "Floppy Disk");
        add(ComputingModule.CD_ROM.get(), "CD-ROM");
        add(ComputingModule.CD_RW.get(), "CD-RW");
        add(ComputingModule.DVD_ROM.get(), "DVD-ROM");
        add(ComputingModule.DVD_RW.get(), "DVD-RW");
        add(ComputingModule.USB_FLASH_DRIVE.get(), "USB Flash Drive");
        // Operating-system display names (the ids stay technical; players see these).
        // OS display names, like the programs, come from the single OS registry.
        for (final dev.jstech.computronics.os.OsDef os
                : dev.jstech.computronics.os.OsBootstrap.builtinOses()) {
            add(os.titleKey(), os.displayName());
        }
        add(ComputingModule.SUPERCOMPUTER_NODE.get(), "Supercomputer Node");
        add("item.jsc.supercomputer_node.tooltip", "A full computer: assemble it, seat a Phi, wire it with High Compute Cable");
        addBlock(ComputingModule.HBW_INTERFACE, "HBW Interface");
        add("item.jsc.hbw_interface.tooltip", "Uplinks a node cluster to the HBW backbone");
        add(ComputingModule.PHI_5100.get(), "Integra Phi 5100 Co-processor");
        add(ComputingModule.PHI_7120.get(), "Integra Phi 7120 Co-processor");
        add(ComputingModule.PHI_7290.get(), "Integra Phi 7290 Co-processor");
        add(ComputingModule.PHI_9000.get(), "Integra Phi 9000 Co-processor");
        addBlock(ComputingModule.SERVER_RACK, "Server Rack");
        addBlock(ComputingModule.LEGACY_SERVER_RACK, "Legacy Server Rack");
        addBlock(ComputingModule.VINTAGE_SERVER_RACK, "Vintage Server Rack");
        addBlock(ComputingModule.SUPERCOMPUTER_RACK, "Supercomputer Rack");
        add(ComputingModule.IMPORT_BUS_ITEM.get(), "Import Bus");
        add("item.jsc.import_bus.tooltip", "Right-click a data cable to attach; pulls items into the network");
        add(ComputingModule.EXPORT_BUS_ITEM.get(), "Export Bus");
        add("item.jsc.export_bus.tooltip", "Right-click a data cable to attach; pushes the filtered item out");
        add(ComputingModule.INPUT_BUS_ITEM.get(), "Crafting Input Bus");
        add("item.jsc.input_bus.tooltip",
                "Right-click a crafting cable to attach; marks the face machine crafts deliver inputs through");
        add(ComputingModule.RECEIVING_BUS_ITEM.get(), "Crafting Receiving Bus");
        add("item.jsc.receiving_bus.tooltip",
                "Right-click a crafting cable to attach; marks the face machine crafts collect outputs from");
        add(ComputingModule.MOTHERBOARD_MTX_P.get(), "MTX-P Motherboard");
        add(ComputingModule.MOTHERBOARD_ATX_P.get(), "ATX-P Motherboard");
        add(ComputingModule.CPU_SERVO_2620.get(), "Integra Servo 2620");
        add(ComputingModule.CPU_SERVO_2690.get(), "Integra Servo 2690");
        add(ComputingModule.CPU_SERVO_2699.get(), "Integra Servo 2699");
        add(ComputingModule.CPU_ASCENT_965.get(), "Velocion Ascent X4 965");
        add(ComputingModule.RAM_DDR3_8192.get(), "Stratix DDR3-8192");
        add(ComputingModule.GPU_HD_7970.get(), "Pyrix Radiance HD 7970");
        add(ComputingModule.CRAFTING_CARD_T2.get(), "Forge Logic Crafting Card");
        add(ComputingModule.CRAFTING_CARD_T3.get(), "Forge Logic Crafting Card T3");
        add(ComputingModule.SERIAL_CONSOLE_CARD.get(), "Serial Console Card");
        add(ComputingModule.MANAGEMENT_NIC.get(), "Management NIC");
        add(ComputingModule.FABRIC_HOST_ADAPTER.get(), "Fabric Host Adapter");
        add(ComputingModule.PSU_650G.get(), "MF PowerGold 650G");
        add(ComputingModule.MOTHERBOARD_EEB_P.get(), "EEB-P Server Board");
        add(ComputingModule.SERVER_CASE.get(), "Server Case");
        add(ComputingModule.SERVER.get(), "Server");
        add(ComputingModule.LEGACY_SERVER_CASE.get(), "Legacy Server Case");
        add(ComputingModule.LEGACY_SERVER.get(), "Legacy Server");
        add(ComputingModule.VINTAGE_SERVER_CASE.get(), "Vintage Server Case");
        add(ComputingModule.VINTAGE_SERVER.get(), "Vintage Server");
        add(ComputingModule.STORAGE_SERVER_CASE.get(), "Storage Server Case");
        add(ComputingModule.STORAGE_SERVER.get(), "Storage Server");
        add(ComputingModule.COMPUTE_SERVER_CASE.get(), "Compute Server Case");
        add(ComputingModule.COMPUTE_SERVER.get(), "Compute Server");
        add(ComputingModule.RAID_CONTROLLER.get(), "RAID Controller");
        add(ComputingModule.CACHE_CARD.get(), "Cache Card");
        add(ComputingModule.KVM_SWITCH.get(), "KVM Switch");
        add(ComputingModule.RACK_UPS.get(), "Rack UPS");
        add(ComputingModule.COOLING_UNIT.get(), "Cooling Unit");
        add("item.jsc.kvm_switch.tooltip", "Lets one monitor address every machine in the rack");
        add("item.jsc.rack_ups.tooltip", "Carries the whole rack through a power outage");
        add("item.jsc.cooling_unit.tooltip", "Active cooling: raises the rack's thermal budget");
        add("item.jsc.raid_controller.tooltip", "Bay gadget: joins the bay's drives into one volume");
        add("item.jsc.cache_card.tooltip", "Bay gadget: cuts read latency on the bay's drives");
        add("item.jsc.server.tooltip", "Operates only inside a Server Rack");
        add("item.jsc.server_case.tooltip", "Crafting ingredient for a Server");
        add("menu.jsc.server_assembly", "Server Assembly");
        add("menu.jsc.network_overview", "Network Overview");
        // Storage tab — the per-disk public/private slider and its readouts.
        add("jsc.gui.storage.public_private", "Public / Private");
        add("jsc.gui.storage.public", "%s%% public");
        add("jsc.gui.storage.private", "%s%% private");
        add("jsc.gui.storage.always_public", "Public - network storage");
        add("jsc.gui.storage.no_disk", "no disk");
        // Advancements: the computing branch and its two hard-way Linux challenges.
        add("advancements.jsc.computing.root.title", "Computronics");
        add("advancements.jsc.computing.root.description", "Boot a computer into an operating system");
        add("advancements.jsc.computing.i_use_arch_btw.title", "I Use Arch BTW");
        add("advancements.jsc.computing.i_use_arch_btw.description", "Install Arch Linux by hand from the live medium and boot it");
        add("advancements.jsc.computing.recompile.title", "Didn't Like It? Recompile!");
        add("advancements.jsc.computing.recompile.description", "Build Gentoo from source and boot it");
        add("jsc.gui.storage.disk", "Disk %s");
        // Expansion card bus-family mismatch: shown when a card cannot enter a slot due to incompatible bus.
        add("jsc.gui.computer.slot.bus_mismatch", "Wrong slot type: this card requires a %s slot");
        for (final ComputingModule.DiskEntry disk : ComputingModule.DISKS) {
            add(disk.item().get(), disk.displayName());
        }
        addHardwareCatalog();
    }

    /**
     * Display names for the per-era hardware catalog. Names follow the product lines (Integra,
     * Velocion, Stratix, Visara, Pyrix, MF). The Standard era's first items already have names in
     * {@link #addTranslations()}; this method covers the items added across the Vintage, Legacy and
     * Standard eras.
     */
    /**
     * One line per program saying what it does. Shown on its install disc, in the package manager
     * and in the installed-programs list, so the answer to "what is this for?" is always at hand
     * instead of only in the head of whoever wrote it.
     */
    private void addProgramBlurbs() {
        add("program.jsc.network.desc", "Browse the storage and machines on this computer's network.");
        add("program.jsc.this_pc.desc", "The machine itself: its hardware, its disks and what fills them.");
        add("program.jsc.disks.desc", "The volumes attached to this machine, and what occupies each one.");
        add("program.jsc.settings.desc", "Change how this computer looks and behaves.");
        add("program.jsc.files.desc", "Browse, open and organise the files on this computer's disks.");
        add("program.jsc.editor.desc", "Write and edit text files.");
        add("program.jsc.command_prompt.desc", "A shell: everything the machine can do, typed.");
        add("program.jsc.system_monitor.desc", "Live load, memory and running work on this machine.");
        add("program.jsc.task_manager.desc", "What this machine is running, what it is spending, and how to end it.");
        add("program.jsc.calculator.desc", "A calculator.");
        add("program.jsc.network_manager.desc",
                "The Mainframe's control room: nodes, storage and operations across the network.");
        add("program.jsc.nms.desc",
                "Query the network in IQL, inspect the index and run maintenance from one console.");
        add("program.jsc.iqlengine.desc",
                "The service that compiles and runs IQL on the Mainframe. The NMS is its front end.");
        add("program.jsc.crafting_manager.desc",
                "Load crafting patterns and watch the jobs the network is working through.");
        add("program.jsc.pattern_studio.desc",
                "Author bench, machine and multi-stage recipes, then burn them onto media at a linked encoder.");
        add("program.jsc.minesweeper.desc", "Minesweeper.");
        add("program.jsc.storage_insights.desc",
                "Where the network's storage went: biggest types, what is running low, how full each server is.");
        add("program.jsc.craft_planner.desc",
                "Plan a craft before committing it: what it needs, what is missing and what it will cost.");
        add("program.jsc.automation_engine.desc",
                "The service that runs standing automation rules on the Mainframe.");
        add("program.jsc.automation_manager.desc",
                "Write and supervise the rules the Automation Engine runs.");
        add("program.jsc.predictive_cache.desc",
                "Keeps this bay's most-wanted items staged in memory, cutting read latency by 15%. "
                        + "Stacks with a Cache Card.");
        add("program.jsc.load_balancer.desc",
                "Spreads writes across the bay's drives instead of filling them one after another.");
        add("program.jsc.integrity_monitor.desc",
                "Re-reads this bay after a hot swap, so the network index never has to doubt it.");
        add("program.jsc.remote_control.desc",
                "Take over another machine on the network and use it on this screen.");
        add("program.jsc.mirror.desc",
                "The network's package repository. Every package manager installs from it.");
        add("program.jsc.screenfetch.desc", "Prints the system's identity, with its distribution's logo.");
        add("program.jsc.cannonc.desc",
                "Compiles a Cannon program into the assembly the runtime reads.");
        add("program.jsc.cannonrt.desc",
                "Runs compiled Cannon programs, and brings the 'cannon' command to the prompt.");
        add("program.jsc.kde_plasma.desc", "The KDE Plasma desktop environment.");
        add("program.jsc.gnome.desc", "The GNOME desktop environment.");
        add("program.jsc.cinnamon.desc", "The Cinnamon desktop environment.");
    }

    /** Every catalog item's name comes from its registration, so adding hardware is one line there. */
    private void addHardwareCatalog() {
        HardwareItems.displayNames().forEach((holder, name) -> add(holder.get(), name));
    }
}
