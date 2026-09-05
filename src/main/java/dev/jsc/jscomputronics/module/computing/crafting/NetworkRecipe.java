/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A recipe the network can craft: exactly one of a bench {@link CraftingPattern}, a {@link ProcessingPattern},
 * or a {@link MultiStagePattern}. This is the single type the Recipe ROM, the craft catalog and the engine hold,
 * so they handle all three without each having to branch on the concrete kind. Its {@link #resultKey()} is the
 * item or fluid the recipe produces, used by the catalog and as the dedupe/lookup key.
 */
public record NetworkRecipe(Optional<CraftingPattern> bench, Optional<ProcessingPattern> proc,
                            Optional<MultiStagePattern> multi) {

    public static NetworkRecipe ofBench(final CraftingPattern pattern) {
        return new NetworkRecipe(Optional.of(pattern), Optional.empty(), Optional.empty());
    }

    public static NetworkRecipe ofProcessing(final ProcessingPattern pattern) {
        return new NetworkRecipe(Optional.empty(), Optional.of(pattern), Optional.empty());
    }

    public static NetworkRecipe ofMultiStage(final MultiStagePattern pattern) {
        return new NetworkRecipe(Optional.empty(), Optional.empty(), Optional.of(pattern));
    }

    /** True when this recipe runs through a machine (processing or multi-stage), not just a bench craft. */
    public boolean usesMachine() {
        return proc.isPresent() || multi.isPresent();
    }

    public static final Codec<NetworkRecipe> CODEC = RecordCodecBuilder.create(i -> i.group(
            CraftingPattern.CODEC.optionalFieldOf("bench").forGetter(NetworkRecipe::bench),
            ProcessingPattern.CODEC.optionalFieldOf("proc").forGetter(NetworkRecipe::proc),
            MultiStagePattern.CODEC.optionalFieldOf("multi").forGetter(NetworkRecipe::multi)
    ).apply(i, NetworkRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.optional(CraftingPattern.STREAM_CODEC), NetworkRecipe::bench,
                    ByteBufCodecs.optional(ProcessingPattern.STREAM_CODEC), NetworkRecipe::proc,
                    ByteBufCodecs.optional(MultiStagePattern.STREAM_CODEC), NetworkRecipe::multi,
                    NetworkRecipe::new);

    /** The item or fluid this recipe produces — the final stage's primary output; null if malformed/empty. */
    @Nullable
    public StorageKey resultKey() {
        if (bench.isPresent()) {
            return StorageKey.of(bench.get().result());
        }
        if (proc.isPresent()) {
            final ProcessingPattern.ProcessingOutput out = proc.get().primaryOutput();
            return out == null ? null : out.key();
        }
        if (multi.isPresent()) {
            final MultiStagePattern.Stage last = multi.get().finalStage();
            if (last != null) {
                if (last.bench().isPresent()) {
                    return StorageKey.of(last.bench().get().result());
                }
                if (last.proc().isPresent()) {
                    final ProcessingPattern.ProcessingOutput out = last.proc().get().primaryOutput();
                    return out == null ? null : out.key();
                }
            }
        }
        return null;
    }

    /** What the recipe is called where it is listed: its author's name, or its result's name when it has none. */
    public String displayName() {
        if (bench.isPresent()) {
            return bench.get().displayName();
        }
        if (proc.isPresent()) {
            return proc.get().displayName();
        }
        if (multi.isPresent()) {
            return multi.get().displayName();
        }
        return "recipe";
    }

    /** Same recipe = same kind producing the same result, used to dedupe loads into the ROM. */
    public boolean sameRecipe(final NetworkRecipe other) {
        if (bench.isPresent() && other.bench.isPresent()) {
            return bench.get().sameRecipe(other.bench.get());
        }
        if (proc.isPresent() && other.proc.isPresent()) {
            return proc.get().sameRecipe(other.proc.get());
        }
        if (multi.isPresent() && other.multi.isPresent()) {
            final StorageKey a = resultKey();
            final StorageKey b = other.resultKey();
            return a != null && a.equals(b);
        }
        return false;
    }
}
