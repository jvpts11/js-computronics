/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.jstech.core.tier.HardwareEra;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Optional;

/**
 * Immutable descriptor for an operating system registered with the mod.
 *
 * <p>Each OS declares the capability tier it provides, the minimum hardware era it requires,
 * the kernel it runs on, the disk footprint it consumes on installation, an optional install
 * media item that carries the OS installer payload, and its display name (which datagen writes to
 * {@link #titleKey()}). The {@link #CODEC} keeps this JSON-serialisable, so the built-in OSes can
 * later move to a datapack; today the Java registration through {@link JSComputronicsAPI} is the source.
 *
 * @param id             unique registry key for this OS (e.g. {@code jsc:mc_dos})
 * @param capability     the capability tier this OS provides to programs and the player
 * @param minEra         the minimum hardware era required to install this OS; the era acts as a
 *                       minimum — installing on hardware at this era or later is accepted, older
 *                       hardware is rejected
 * @param kernelId       registry key of the kernel this OS runs on
 * @param footprintMb    the size of the installed system in megabytes; what that costs in items follows
 *                       from the era of the disk it lands on ({@link #footprintItemsOn})
 * @param installMediaId optional registry key of the install media item that delivers this OS;
 *                       empty when the OS is provisioned programmatically (e.g. server auto-provision)
 * @param platform       the platform (family) this OS is, which programs are gated against
 * @param displayName    the human name (English), the value datagen writes to {@link #titleKey()}
 * @param shellId        the interactive shell this OS ships ({@code cmd}, {@code bash}, {@code zsh}); it
 *                       flavours the prompt, while the command syntax comes from the kernel's shell family
 * @param packageManager the package manager the OS installs programs with ({@link PackageManagerKind#NONE}
 *                       for media-installed platforms)
 * @param installMode    how booting this OS's medium installs it (guided, or the real manual steps)
 * @param bundledDesktop the desktop environment this OS ships with, if any (the Frames editions bundle their
 *                       own; a Linux distribution boots to a TTY until one is installed)
 * @param house          who wrote it: the name on its banner, its copyright line and its install disc
 */
public record OsDef(
        ResourceLocation id,
        OsCapability capability,
        HardwareEra minEra,
        ResourceLocation kernelId,
        int footprintMb,
        Optional<ResourceLocation> installMediaId,
        Platform platform,
        String displayName,
        String shellId,
        PackageManagerKind packageManager,
        InstallMode installMode,
        Optional<ResourceLocation> bundledDesktop,
        SoftwareHouse house
) {

    public static final Codec<OsDef> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(OsDef::id),
            enumCodec(OsCapability.class).fieldOf("capability").forGetter(OsDef::capability),
            enumCodec(HardwareEra.class).fieldOf("min_era").forGetter(OsDef::minEra),
            ResourceLocation.CODEC.fieldOf("kernel").forGetter(OsDef::kernelId),
            Codec.INT.optionalFieldOf("footprint_mb", 0).forGetter(OsDef::footprintMb),
            ResourceLocation.CODEC.optionalFieldOf("install_media").forGetter(OsDef::installMediaId),
            enumCodec(Platform.class).fieldOf("platform").forGetter(OsDef::platform),
            Codec.STRING.optionalFieldOf("display_name", "").forGetter(OsDef::displayName),
            Codec.STRING.optionalFieldOf("shell", "cmd").forGetter(OsDef::shellId),
            enumCodec(PackageManagerKind.class).optionalFieldOf("package_manager", PackageManagerKind.NONE)
                    .forGetter(OsDef::packageManager),
            enumCodec(InstallMode.class).optionalFieldOf("install_mode", InstallMode.GUIDED)
                    .forGetter(OsDef::installMode),
            ResourceLocation.CODEC.optionalFieldOf("bundled_desktop").forGetter(OsDef::bundledDesktop),
            SoftwareHouse.CODEC.optionalFieldOf("house", SoftwareHouse.MIDSOFT).forGetter(OsDef::house)
    ).apply(inst, OsDef::new));

    /**
     * The media-installed, guided, desktop-bundling form used by the DOS-family systems: {@code cmd} shell, no
     * package manager, guided install, and the given bundled desktop (empty for a terminal or network OS).
     */
    public static OsDef mediaInstalled(final ResourceLocation id, final OsCapability capability,
                                       final HardwareEra minEra, final ResourceLocation kernelId,
                                       final int footprintMb, final Platform platform,
                                       final String displayName, final Optional<ResourceLocation> bundledDesktop,
                                       final SoftwareHouse house) {
        // A Frames edition ships pckmgr; anything else installed from media (MC-NET, MC-DOS) still
        // takes its programs from a disc in a linked drive.
        return new OsDef(id, capability, minEra, kernelId, footprintMb, Optional.empty(), platform,
                displayName, "cmd",
                platform == Platform.FRAMES ? PackageManagerKind.PCKMGR : PackageManagerKind.NONE,
                InstallMode.GUIDED, bundledDesktop, house);
    }

    /** A Linux distribution: TTY capability on the Linux kernel, no bundled desktop, installable from the Legacy era. */
    public static OsDef linuxDistro(final ResourceLocation id, final int footprintMb, final String displayName,
                                    final String shellId, final PackageManagerKind packageManager,
                                    final InstallMode installMode, final SoftwareHouse house) {
        return new OsDef(id, OsCapability.TERMINAL_ONLY, HardwareEra.LEGACY,
                ResourceLocation.fromNamespaceAndPath("jsc", "linux"), footprintMb, Optional.empty(),
                Platform.LINUX, displayName, shellId, packageManager, installMode, Optional.empty(), house);
    }

    public OsDef {
        if (house == null) {
            house = SoftwareHouse.MIDSOFT;
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = id.getPath();
        }
        if (shellId == null || shellId.isBlank()) {
            shellId = "cmd";
        }
        if (packageManager == null) {
            packageManager = PackageManagerKind.NONE;
        }
        if (installMode == null) {
            installMode = InstallMode.GUIDED;
        }
        if (bundledDesktop == null) {
            bundledDesktop = Optional.empty();
        }
    }

    /**
     * How many items this system takes on a disk of {@code diskEra}: its size in megabytes at that era's
     * cost per item, rounded up. A 20 GB system is 80 items on a standard disk and more than a whole
     * vintage drive.
     */
    public long footprintItemsOn(final HardwareEra diskEra) {
        return diskEra.itemsFor(footprintMb);
    }

    /** The translation key for this OS's display name, in vanilla {@code os.<ns>.<path>} form. */
    public String titleKey() {
        return "os." + id.getNamespace() + "." + id.getPath();
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(final Class<E> type) {
        return Codec.STRING.xmap(s -> Enum.valueOf(type, s.toUpperCase(Locale.ROOT)),
                e -> e.name().toLowerCase(Locale.ROOT));
    }
}
