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

import java.util.Locale;

/**
 * Immutable descriptor for a kernel that an OS can run on top of.
 *
 * <p>A kernel defines the scheduling model and filesystem model available to the OS. Community
 * addons can register custom kernels via {@link JSComputronicsAPI#registerKernel(KernelDef)} to
 * ship alternative kernel implementations (e.g. Unix-like) without modifying the mod core. The
 * {@link #CODEC} keeps this JSON-serialisable so the built-in kernels can later move to a datapack.
 *
 * @param id          unique registry key for this kernel (e.g. {@code jsc:dos})
 * @param scheduler   the task-scheduling model this kernel provides
 * @param filesystem  the filesystem model this kernel provides
 * @param shellFamily the command-line syntax family every OS on this kernel speaks (DOS or POSIX)
 */
public record KernelDef(
        ResourceLocation id,
        SchedulerKind scheduler,
        FilesystemKind filesystem,
        ShellFamily shellFamily
) {

    public static final Codec<KernelDef> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(KernelDef::id),
            enumCodec(SchedulerKind.class).fieldOf("scheduler").forGetter(KernelDef::scheduler),
            enumCodec(FilesystemKind.class).fieldOf("filesystem").forGetter(KernelDef::filesystem),
            enumCodec(ShellFamily.class).optionalFieldOf("shell_family", ShellFamily.DOS)
                    .forGetter(KernelDef::shellFamily)
    ).apply(inst, KernelDef::new));

    public KernelDef {
        if (shellFamily == null) {
            shellFamily = ShellFamily.DOS;
        }
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(final Class<E> type) {
        return Codec.STRING.xmap(s -> Enum.valueOf(type, s.toUpperCase(Locale.ROOT)),
                e -> e.name().toLowerCase(Locale.ROOT));
    }
}
