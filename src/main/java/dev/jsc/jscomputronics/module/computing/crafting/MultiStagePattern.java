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

import java.util.List;
import java.util.Optional;

/**
 * An ordered pipeline of stages, each either a bench {@link CraftingPattern} or a {@link ProcessingPattern}.
 * Stage N+1 starts only once stage N has produced its result; stage 1's inputs come from the network and each
 * later stage chains the previous one's output. If any stage times out, the already-inserted ingredients are
 * returned to the network and the whole craft fails visibly. Fluids are only valid inside processing stages.
 */
public record MultiStagePattern(List<Stage> stages, String name, String note) {

    /** A pipeline without an author's name or note: it goes by its final result. */
    public MultiStagePattern(final List<Stage> stages) {
        this(stages, "", "");
    }

    /** One stage: exactly one of {@code bench}/{@code proc} is present. */
    public record Stage(Optional<CraftingPattern> bench, Optional<ProcessingPattern> proc) {

        public static Stage bench(final CraftingPattern pattern) {
            return new Stage(Optional.of(pattern), Optional.empty());
        }

        public static Stage proc(final ProcessingPattern pattern) {
            return new Stage(Optional.empty(), Optional.of(pattern));
        }

        public boolean isProcessing() {
            return proc.isPresent();
        }

        public static final Codec<Stage> CODEC = RecordCodecBuilder.create(i -> i.group(
                CraftingPattern.CODEC.optionalFieldOf("bench").forGetter(Stage::bench),
                ProcessingPattern.CODEC.optionalFieldOf("proc").forGetter(Stage::proc)
        ).apply(i, Stage::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Stage> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.optional(CraftingPattern.STREAM_CODEC), Stage::bench,
                        ByteBufCodecs.optional(ProcessingPattern.STREAM_CODEC), Stage::proc,
                        Stage::new);
    }

    public MultiStagePattern {
        stages = List.copyOf(stages);
        name = clamp(name, CraftingPattern.MAX_NAME);
        note = clamp(note, CraftingPattern.MAX_NOTE);
    }

    private static String clamp(final String s, final int max) {
        final String value = s == null ? "" : s.trim();
        return value.length() <= max ? value : value.substring(0, max);
    }

    public static final Codec<MultiStagePattern> CODEC = RecordCodecBuilder.create(i -> i.group(
            Stage.CODEC.listOf().fieldOf("stages").forGetter(MultiStagePattern::stages),
            Codec.STRING.optionalFieldOf("name", "").forGetter(MultiStagePattern::name),
            Codec.STRING.optionalFieldOf("note", "").forGetter(MultiStagePattern::note)
    ).apply(i, MultiStagePattern::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MultiStagePattern> STREAM_CODEC =
            StreamCodec.composite(
                    Stage.STREAM_CODEC.apply(ByteBufCodecs.list(64)), MultiStagePattern::stages,
                    ByteBufCodecs.stringUtf8(CraftingPattern.MAX_NAME), MultiStagePattern::name,
                    ByteBufCodecs.stringUtf8(CraftingPattern.MAX_NOTE), MultiStagePattern::note,
                    MultiStagePattern::new);

    /** What the pipeline is called where it is listed: its name, or its final result's when it has none. */
    public String displayName() {
        if (!name.isEmpty()) {
            return name;
        }
        final Stage last = finalStage();
        final StorageKey key = last == null ? null : outputKey(last);
        return key == null ? "pipeline" : key.displayName().getString();
    }

    /** The same pipeline under a new name and note. */
    public MultiStagePattern withName(final String newName, final String newNote) {
        return new MultiStagePattern(stages, newName, newNote);
    }

    /** The same pipeline with every bench stage's tagged cells resolved by {@code chooser}. */
    public MultiStagePattern resolved(final java.util.function.Function<String, net.minecraft.world.item.ItemStack> chooser) {
        final List<Stage> out = new java.util.ArrayList<>(stages.size());
        for (final Stage stage : stages) {
            out.add(stage.bench().isPresent() ? Stage.bench(stage.bench().get().resolved(chooser)) : stage);
        }
        return new MultiStagePattern(out, name, note);
    }

    /** The last stage, which produces the multi-stage's final result; null if the pipeline is empty. */
    public Stage finalStage() {
        return stages.isEmpty() ? null : stages.get(stages.size() - 1);
    }

    /**
     * How much each stage must produce for the pipeline to deliver {@code finalRequested} of its final result,
     * walked backwards: the last stage produces the request itself, and every earlier stage produces exactly
     * what the stage after it consumes of that output over its runs. Nine nuggets (one ingot makes nine) is
     * therefore one smelted ingot, and one iron block (nine ingots) is nine. A stage whose output the next
     * stage does not list falls back to the next stage's own demand.
     */
    public long[] stageDemands(final long finalRequested) {
        final long[] demand = new long[stages.size()];
        if (stages.isEmpty()) {
            return demand;
        }
        demand[stages.size() - 1] = Math.max(1, finalRequested);
        for (int i = stages.size() - 2; i >= 0; i--) {
            final Stage next = stages.get(i + 1);
            final long runsOfNext = ceilDiv(demand[i + 1], Math.max(1, outputPerRun(next)));
            final StorageKey produced = outputKey(stages.get(i));
            final long consumedPerRun = produced == null ? 0 : inputPerRun(next, produced);
            demand[i] = consumedPerRun > 0 ? runsOfNext * consumedPerRun : demand[i + 1];
        }
        return demand;
    }

    private static long ceilDiv(final long amount, final long perRun) {
        return (amount + perRun - 1) / perRun;
    }

    /** The primary result a stage yields per run: the bench result count, or the primary output's amount. */
    private static long outputPerRun(final Stage stage) {
        if (stage.bench().isPresent()) {
            return stage.bench().get().result().getCount();
        }
        final ProcessingPattern.ProcessingOutput out = stage.proc().map(ProcessingPattern::primaryOutput).orElse(null);
        return out == null ? 1 : out.amount();
    }

    private static StorageKey outputKey(final Stage stage) {
        if (stage.bench().isPresent()) {
            return StorageKey.of(stage.bench().get().result());
        }
        final ProcessingPattern.ProcessingOutput out = stage.proc().map(ProcessingPattern::primaryOutput).orElse(null);
        return out == null ? null : out.key();
    }

    /** How much of {@code key} one run of {@code stage} consumes (0 when the stage does not use it). */
    private static long inputPerRun(final Stage stage, final StorageKey key) {
        if (stage.bench().isPresent()) {
            return stage.bench().get().ingredientTotals().getOrDefault(key, 0L);
        }
        long total = 0;
        for (final ProcessingPattern.ProcessingInput in : stage.proc().map(ProcessingPattern::inputs).orElse(List.of())) {
            if (in.key().equals(key)) {
                total += in.amount();
            }
        }
        return total;
    }
}
