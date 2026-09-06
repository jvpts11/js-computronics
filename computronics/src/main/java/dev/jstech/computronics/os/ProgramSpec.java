/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

/**
 * The single descriptor for a program known to the mod: its identity and CLI metadata, the OS platforms
 * and version it needs, the raw hardware minimums, how it runs, and where it may live. This merges what
 * used to be two parallel records ({@code program.Program} for CLI metadata and {@code os.ProgramDef}
 * for gating), so a program is described in one place and adding one no longer means editing many.
 *
 * <p>A program is gated on three independent things: the OS <em>platform</em> (and, within the Frames
 * family, its version rank), the raw hardware (CPU/VRAM/disk), and the {@link HostScope host} it may
 * live on. See {@link OsGating} and {@link OsRegistry}. Requirements do not depend on the hardware era.
 *
 * <p>The {@link #CODEC} keeps this JSON-serialisable so the built-in registrations can later move to a
 * datapack; today the source of truth is the Java registration through {@link JSComputronicsAPI}.
 *
 * @param id           unique registry key (e.g. {@code jsc:nms})
 * @param commandName  the short word the Command Prompt's {@code run}/{@code programs} verbs use
 * @param displayName  the human name (English), the value datagen writes to {@link #titleKey()}
 * @param preinstalled whether every compatible computer ships with it (no install step)
 * @param platforms    the OS platforms it supports (runs on any one of them)
 * @param minCpuMhz    minimum CPU clock in MHz (checked against the best installed CPU)
 * @param minVramMb    minimum VRAM in MB (checked against the total installed VRAM)
 * @param minDiskMb    disk footprint in MB the program needs free to install
 * @param kind         how it runs (foreground app or headless service)
 * @param minOsRank    minimum Frames version rank (0 any / 2 XP+ / 3 11); see {@code OsRegistry.osVersionRank}
 * @param hostScope    which computer it may live on (any, a Crafting Computer, or the Mainframe)
 * @param iconId       base id for its per-OS icon sprites ({@code textures/gui/program/<path>/<os>.png})
 * @param minEra       the oldest hardware generation that may install it (a gate)
 * @param era          the generation the software was written in: what it ships on and the year on its
 *                     banner, never a gate; a 2010s tool can still run on 2000s hardware if minEra allows
 * @param house        who wrote it, or {@link SoftwareHouse#BUNDLED} for a program credited to whichever
 *                     system or desktop ships it
 * @param ramMb        megabytes the program holds while it runs; 0 means "derive it": a bundled program
 *                     weighs a share of the system that ships it, anything else weighs by its generation
 *                     ({@link RamLedger#eraWeightMb})
 */
public record ProgramSpec(
        ResourceLocation id,
        String commandName,
        String displayName,
        boolean preinstalled,
        Set<Platform> platforms,
        int minCpuMhz,
        int minVramMb,
        int minDiskMb,
        ProgramKind kind,
        int minOsRank,
        HostScope hostScope,
        ResourceLocation iconId,
        dev.jstech.core.tier.HardwareEra minEra,
        dev.jstech.core.tier.HardwareEra era,
        SoftwareHouse house,
        int ramMb
) {

    public static final Codec<ProgramSpec> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(ProgramSpec::id),
            Codec.STRING.optionalFieldOf("command_name", "").forGetter(ProgramSpec::commandName),
            Codec.STRING.optionalFieldOf("display_name", "").forGetter(ProgramSpec::displayName),
            Codec.BOOL.optionalFieldOf("preinstalled", false).forGetter(ProgramSpec::preinstalled),
            enumCodec(Platform.class).listOf().xmap(Set::copyOf, List::copyOf)
                    .fieldOf("platforms").forGetter(ProgramSpec::platforms),
            Codec.INT.optionalFieldOf("min_cpu_mhz", 0).forGetter(ProgramSpec::minCpuMhz),
            Codec.INT.optionalFieldOf("min_vram_mb", 0).forGetter(ProgramSpec::minVramMb),
            Codec.INT.optionalFieldOf("min_disk_mb", 0).forGetter(ProgramSpec::minDiskMb),
            enumCodec(ProgramKind.class).optionalFieldOf("kind", ProgramKind.APP).forGetter(ProgramSpec::kind),
            Codec.INT.optionalFieldOf("min_os_rank", 0).forGetter(ProgramSpec::minOsRank),
            enumCodec(HostScope.class).optionalFieldOf("host_scope", HostScope.ANY).forGetter(ProgramSpec::hostScope),
            ResourceLocation.CODEC.optionalFieldOf("icon_id", ResourceLocation.fromNamespaceAndPath("jsc", "generic"))
                    .forGetter(ProgramSpec::iconId),
            enumCodec(dev.jstech.core.tier.HardwareEra.class)
                    .optionalFieldOf("min_era", dev.jstech.core.tier.HardwareEra.VINTAGE)
                    .forGetter(ProgramSpec::minEra),
            // Absent means "derive it from the OS rank", which the compact constructor does.
            enumCodec(dev.jstech.core.tier.HardwareEra.class)
                    .optionalFieldOf("era", null)
                    .forGetter(ProgramSpec::era),
            SoftwareHouse.CODEC.optionalFieldOf("house", SoftwareHouse.BUNDLED).forGetter(ProgramSpec::house),
            Codec.INT.optionalFieldOf("ram_mb", 0).forGetter(ProgramSpec::ramMb)
    ).apply(inst, ProgramSpec::new));

    /**
     * Compact constructor: fills sensible defaults from the id (command name and display name from the
     * path, icon from the id) and takes an unmodifiable copy of the platform set.
     */
    public ProgramSpec {
        if (id == null) {
            throw new IllegalArgumentException("program id must not be null");
        }
        if (commandName == null || commandName.isBlank()) {
            commandName = id.getPath();
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = titleCase(id.getPath());
        }
        if (kind == null) {
            kind = ProgramKind.APP;
        }
        if (hostScope == null) {
            hostScope = HostScope.ANY;
        }
        if (iconId == null) {
            iconId = id;
        }
        if (minEra == null) {
            minEra = dev.jstech.core.tier.HardwareEra.VINTAGE;
        }
        if (era == null) {
            era = eraFromRank(minOsRank);
        }
        if (house == null) {
            house = SoftwareHouse.BUNDLED;
        }
        if (ramMb < 0) {
            ramMb = 0;
        }
        platforms = Set.copyOf(platforms);
    }

    /**
     * The generation a program belongs to when nothing says otherwise, read off the Frames version it
     * needs: only-on-11 is Standard, XP-or-later is Legacy, anything else is Vintage. The built-in
     * registrations override this per program; the rule is only the default for a program that never said.
     */
    private static dev.jstech.core.tier.HardwareEra eraFromRank(final int minOsRank) {
        if (minOsRank >= 3) {
            return dev.jstech.core.tier.HardwareEra.STANDARD;
        }
        if (minOsRank >= 2) {
            return dev.jstech.core.tier.HardwareEra.LEGACY;
        }
        return dev.jstech.core.tier.HardwareEra.VINTAGE;
    }

    /**
     * Convenience factory for the common case: no CPU/VRAM minimum and the icon derived from the id. Keeps
     * the built-in registrations readable while the canonical constructor stays available for the rest.
     */
    public static ProgramSpec of(final ResourceLocation id, final String commandName, final String displayName,
                                 final boolean preinstalled, final Set<Platform> platforms, final int minDiskMb,
                                 final ProgramKind kind, final int minOsRank, final HostScope hostScope) {
        return new ProgramSpec(id, commandName, displayName, preinstalled, platforms, 0, 0, minDiskMb,
                kind, minOsRank, hostScope, id, dev.jstech.core.tier.HardwareEra.VINTAGE, null,
                SoftwareHouse.BUNDLED, 0);
    }

    /**
     * The same program, but only available from {@code era} onwards. Software cannot predate the
     * hardware generation it was written for: a desktop environment of the 2010s has no business
     * running on a machine of the 1990s.
     */
    public ProgramSpec withMinEra(final dev.jstech.core.tier.HardwareEra oldest) {
        return new ProgramSpec(id, commandName, displayName, preinstalled, platforms, minCpuMhz, minVramMb,
                minDiskMb, kind, minOsRank, hostScope, iconId, oldest, era, house, ramMb);
    }

    /**
     * The same program, stamped as written in {@code generation}. This decides the medium it ships on and
     * the year on its banner; it never gates where it installs, which stays {@link #minEra()}'s job.
     */
    public ProgramSpec withEra(final dev.jstech.core.tier.HardwareEra generation) {
        return new ProgramSpec(id, commandName, displayName, preinstalled, platforms, minCpuMhz, minVramMb,
                minDiskMb, kind, minOsRank, hostScope, iconId, minEra, generation, house, ramMb);
    }

    /** The same program, credited to {@code maker}: the name on its disc, its banner and its about line. */
    public ProgramSpec withHouse(final SoftwareHouse maker) {
        return new ProgramSpec(id, commandName, displayName, preinstalled, platforms, minCpuMhz, minVramMb,
                minDiskMb, kind, minOsRank, hostScope, iconId, minEra, era, maker, ramMb);
    }

    /** The same program, holding {@code megabytes} of RAM while it runs. */
    public ProgramSpec withRam(final int megabytes) {
        return new ProgramSpec(id, commandName, displayName, preinstalled, platforms, minCpuMhz, minVramMb,
                minDiskMb, kind, minOsRank, hostScope, iconId, minEra, era, house, megabytes);
    }

    /**
     * The megabytes this program holds while it runs under {@code system}: its declared size, else a share of
     * the system's own when it ships with the system, else the weight of its generation. A bundled Files is
     * a few megabytes on a nineties system and far more on a modern one; an installed tool weighs what it is.
     */
    public int ramMbOn(final OsDef system) {
        if (preinstalled) {
            return RamLedger.bundledWeightMb(system.ramMb());
        }
        if (ramMb > 0) {
            return ramMb;
        }
        return RamLedger.eraWeightMb(era, kind);
    }

    /** Who to credit where the program is shown: its own house, or {@code shipper} when it is bundled. */
    public SoftwareHouse houseOr(final SoftwareHouse shipper) {
        return house.or(shipper);
    }

    /** The translation key for this program's display name, in vanilla {@code program.<ns>.<path>} form. */
    public String titleKey() {
        return "program." + id.getNamespace() + "." + id.getPath();
    }

    /** Whether a player installs this program (true) or it ships with the computer (false). */
    public boolean installable() {
        return !preinstalled;
    }

    /** A readable name from a path segment: {@code storage_insights} to {@code Storage Insights}. */
    private static String titleCase(final String path) {
        final String[] parts = path.split("_");
        final StringBuilder sb = new StringBuilder();
        for (final String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(final Class<E> type) {
        return Codec.STRING.xmap(s -> Enum.valueOf(type, s.toUpperCase(java.util.Locale.ROOT)),
                e -> e.name().toLowerCase(java.util.Locale.ROOT));
    }
}
