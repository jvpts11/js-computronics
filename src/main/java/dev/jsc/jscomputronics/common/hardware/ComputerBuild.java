/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.hardware;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An assembly of components on a motherboard, with a PSU.
 */
public record ComputerBuild(MotherboardSpec motherboard,
                            List<CpuSpec> cpus,
                            List<ExpansionCardSpec> pcieCards,
                            List<RamSpec> rams,
                            PsuSpec psu,
                            List<DiskSpec> disks) {

    public ComputerBuild {
        Objects.requireNonNull(motherboard, "motherboard must not be null");
        Objects.requireNonNull(psu, "psu must not be null");
        cpus = List.copyOf(cpus); // defensive copies, reject null elements
        pcieCards = List.copyOf(pcieCards);
        rams = List.copyOf(rams);
        disks = List.copyOf(disks);
    }

    public ComputerBuild(final MotherboardSpec motherboard, final List<CpuSpec> cpus,
                         final List<ExpansionCardSpec> pcieCards, final List<RamSpec> rams,
                         final PsuSpec psu) {
        this(motherboard, cpus, pcieCards, rams, psu, List.of());
    }

    public List<GpuSpec> gpus() {
        final List<GpuSpec> out = new ArrayList<>();
        for (final ExpansionCardSpec card : pcieCards) {
            if (card instanceof GpuSpec gpu) {
                out.add(gpu);
            }
        }
        return out;
    }

    public List<ExpansionCardSpec> cardsOfKind(final ExpansionCardKind kind) {
        final List<ExpansionCardSpec> out = new ArrayList<>();
        for (final ExpansionCardSpec card : pcieCards) {
            if (card.kind() == kind) {
                out.add(card);
            }
        }
        return out;
    }

    public long totalCapacity() {
        long sum = 0L;
        for (final CpuSpec cpu : cpus) {
            sum += cpu.orchestrationCapacity();
        }
        return sum;
    }

    public int parallelQueues() {
        // One base CPU queue plus one extra parallel queue per installed GPU. GPUs are detected
        // the single canonical way, through gpus(), so this never drifts from the GPU accessor.
        return 1 + gpus().size();
    }

    public long ramBuffer() {
        long sum = 0L;
        for (final RamSpec ram : rams) {
            sum += ram.bufferItems();
        }
        return sum;
    }

    public StorageTier fastestDiskTier() {
        StorageTier best = StorageTier.HDD;
        for (final DiskSpec disk : disks) {
            best = best.faster(disk.tier());
        }
        return best;
    }

    /**
     * The lowest staging latency across all installed RAM modules, in ticks. When no RAM is
     * installed this returns zero — the caller already guards against an empty RAM list through
     * {@link #validate()}, so an empty list here means the build is invalid anyway.
     */
    public int bestRamLatencyTicks() {
        int best = Integer.MAX_VALUE;
        for (final RamSpec ram : rams) {
            best = Math.min(best, ram.latencyTicks());
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }

    public long totalStorageItems() {
        long sum = 0L;
        for (final DiskSpec disk : disks) {
            sum += disk.capacityItems();
        }
        return sum;
    }

    public long storageMb() {
        return totalStorageItems() * DiskSpec.MB_PER_ITEM;
    }

    public int powerDraw() {
        int draw = 0;
        for (final CpuSpec cpu : cpus) {
            draw += cpu.tdpWatts();
        }
        for (final ExpansionCardSpec card : pcieCards) {
            draw += card.tdpWatts();
        }
        for (final RamSpec ram : rams) {
            draw += ram.tdpWatts();
        }
        for (final DiskSpec disk : disks) {
            draw += disk.tdpWatts();
        }
        return draw;
    }

    public BuildValidation validate() {
        final List<String> problems = new ArrayList<>();

        if (cpus.isEmpty()) {
            problems.add("no CPU installed");
        }
        // Every computer needs RAM to do work: with a zero buffer the CPU has nothing to stage
        // through and can move nothing. A box without RAM is not a working computer.
        if (rams.isEmpty()) {
            problems.add("no RAM installed");
        }
        if (cpus.size() > motherboard.cpuSlots()) {
            problems.add("too many CPUs: " + cpus.size() + " installed, "
                    + motherboard.cpuSlots() + " sockets");
        }
        for (final CpuSpec cpu : cpus) {
            if (cpu.socket() != motherboard.socket()) {
                problems.add("CPU socket " + cpu.socket() + " does not fit board socket "
                        + motherboard.socket());
            }
        }

        if (pcieCards.size() > motherboard.pcieSlots()) {
            problems.add("too many PCIe cards: " + pcieCards.size() + " installed, "
                    + motherboard.pcieSlots() + " PCIe slots");
        }
        for (final ExpansionCardSpec card : pcieCards) {
            if (!card.bus().compatibleWith(motherboard.pcieGeneration())) {
                problems.add("expansion card bus family " + card.bus().busFamily()
                        + " is not compatible with board bus " + motherboard.pcieGeneration().busFamily());
            }
        }

        if (rams.size() > motherboard.ramSlots()) {
            problems.add("too many RAM modules: " + rams.size() + " installed, "
                    + motherboard.ramSlots() + " slots");
        }
        for (final RamSpec ram : rams) {
            if (!motherboard.acceptedRam().contains(ram.generation())) {
                problems.add("RAM generation " + ram.generation() + " not accepted by board");
            }
        }

        if (disks.size() > motherboard.diskSlots()) {
            problems.add("too many disks: " + disks.size() + " installed, "
                    + motherboard.diskSlots() + " disk slots");
        }

        // An auto-scaling PSU dimensions its output to the build's draw, so it always satisfies the
        // power requirement; only a fixed-wattage PSU can come up short.
        if (!psu.autoScaling()) {
            final int draw = powerDraw();
            if (draw > psu.wattage()) {
                problems.add("PSU insufficient: draw " + draw + "W exceeds " + psu.wattage() + "W");
            }
        }

        return new BuildValidation(problems.isEmpty(), problems);
    }

    public boolean isPowered() {
        return validate().valid();
    }
}
