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
                            List<GpuSpec> gpus,
                            List<RamSpec> rams,
                            PsuSpec psu) {

    public ComputerBuild {
        Objects.requireNonNull(motherboard, "motherboard must not be null");
        Objects.requireNonNull(psu, "psu must not be null");
        cpus = List.copyOf(cpus); // defensive copies, reject null elements
        gpus = List.copyOf(gpus);
        rams = List.copyOf(rams);
    }

    public long totalCapacity() {
        long sum = 0L;
        for (final CpuSpec cpu : cpus) {
            sum += cpu.orchestrationCapacity();
        }
        return sum;
    }

    public int parallelQueues() {
        return 1 + gpus.size();
    }

    public long ramBuffer() {
        long sum = 0L;
        for (final RamSpec ram : rams) {
            sum += ram.bufferItems();
        }
        return sum;
    }

    public int powerDraw() {
        int draw = 0;
        for (final CpuSpec cpu : cpus) {
            draw += cpu.tdpWatts();
        }
        for (final GpuSpec gpu : gpus) {
            draw += gpu.tdpWatts();
        }
        for (final RamSpec ram : rams) {
            draw += ram.tdpWatts();
        }
        return draw;
    }

    public BuildValidation validate() {
        final List<String> problems = new ArrayList<>();

        if (cpus.isEmpty()) {
            problems.add("no CPU installed");
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

        if (gpus.size() > motherboard.pcieSlots()) {
            problems.add("too many GPUs: " + gpus.size() + " installed, "
                    + motherboard.pcieSlots() + " PCIe slots");
        }
        for (final GpuSpec gpu : gpus) {
            if (!gpu.bus().fitsInto(motherboard.pcieGeneration())) {
                problems.add("GPU bus " + gpu.bus() + " is newer than board bus "
                        + motherboard.pcieGeneration());
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

        final int draw = powerDraw();
        if (draw > psu.wattage()) {
            problems.add("PSU insufficient: draw " + draw + "W exceeds " + psu.wattage() + "W");
        }

        return new BuildValidation(problems.isEmpty(), problems);
    }

    public boolean isPowered() {
        return validate().valid();
    }
}
