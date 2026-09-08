/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.jstech.computers.storage.StorageKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A machine-crafting recipe: a list of inputs fed into a named machine TYPE, producing a list of outputs, with
 * a player-set timeout. Inputs and outputs are {@link StorageKey}s, so they carry items OR fluids without
 * distinction (fluid crafts only make sense here, never in a 3x3 bench {@link CraftingPattern}). The machine is
 * treated as a black box: the engine inserts the inputs, waits for the declared outputs, and fails if they do
 * not appear within {@code timeoutTicks}. Each output declares a CHANCE %: 100 = guaranteed, less = a
 * probabilistic byproduct that feeds planning but never blocks the craft (the real yield is counted at runtime).
 */
public record ProcessingPattern(List<ProcessingInput> inputs, List<ProcessingOutput> outputs,
                                String machineType, int timeoutTicks, String name, String note) {

    public static final int DEFAULT_TIMEOUT_TICKS = 200;
    public static final int FULL_CHANCE = 100;

    /** A recipe without an author's name or note: it goes by its primary output. */
    public ProcessingPattern(final List<ProcessingInput> inputs, final List<ProcessingOutput> outputs,
                             final String machineType, final int timeoutTicks) {
        this(inputs, outputs, machineType, timeoutTicks, "", "");
    }

    /**
     * One input: a key (item, fluid or chemical) and how much of it the machine consumes per run. An
     * {@code estimated} amount was worked out from a recipe that meters its input per tick (per-tick usage
     * times the machine's base duration) rather than confirmed by the author; it runs like any other, the
     * flag only lets the GUIs say so until the author edits or confirms it.
     */
    public record ProcessingInput(StorageKey key, long amount, boolean estimated) {
        public ProcessingInput(final StorageKey key, final long amount) {
            this(key, amount, false);
        }

        public static final Codec<ProcessingInput> CODEC = RecordCodecBuilder.create(i -> i.group(
                StorageKey.CODEC.fieldOf("key").forGetter(ProcessingInput::key),
                Codec.LONG.fieldOf("amount").forGetter(ProcessingInput::amount),
                Codec.BOOL.optionalFieldOf("estimated", false).forGetter(ProcessingInput::estimated)
        ).apply(i, ProcessingInput::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, ProcessingInput> STREAM_CODEC =
                StreamCodec.composite(
                        StorageKey.STREAM_CODEC, ProcessingInput::key,
                        ByteBufCodecs.VAR_LONG, ProcessingInput::amount,
                        ByteBufCodecs.BOOL, ProcessingInput::estimated,
                        ProcessingInput::new);
    }

    /** One output: a key, how much it yields per run, and the percent chance it appears (100 = guaranteed). */
    public record ProcessingOutput(StorageKey key, long amount, int chancePercent) {
        public ProcessingOutput {
            chancePercent = Math.max(1, Math.min(FULL_CHANCE, chancePercent));
        }

        public boolean probabilistic() {
            return chancePercent < FULL_CHANCE;
        }

        /** Expected yield per run for planning: {@code amount * chance / 100}. */
        public double expectedYield() {
            return amount * (chancePercent / 100.0);
        }

        public static final Codec<ProcessingOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
                StorageKey.CODEC.fieldOf("key").forGetter(ProcessingOutput::key),
                Codec.LONG.fieldOf("amount").forGetter(ProcessingOutput::amount),
                Codec.INT.fieldOf("chance").forGetter(ProcessingOutput::chancePercent)
        ).apply(i, ProcessingOutput::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, ProcessingOutput> STREAM_CODEC =
                StreamCodec.composite(
                        StorageKey.STREAM_CODEC, ProcessingOutput::key,
                        ByteBufCodecs.VAR_LONG, ProcessingOutput::amount,
                        ByteBufCodecs.VAR_INT, ProcessingOutput::chancePercent,
                        ProcessingOutput::new);
    }

    public ProcessingPattern {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        machineType = machineType == null ? "" : machineType;
        timeoutTicks = Math.max(1, timeoutTicks);
        name = clamp(name, CraftingPattern.MAX_NAME);
        note = clamp(note, CraftingPattern.MAX_NOTE);
    }

    private static String clamp(final String s, final int max) {
        final String value = s == null ? "" : s.trim();
        return value.length() <= max ? value : value.substring(0, max);
    }

    public static final Codec<ProcessingPattern> CODEC = RecordCodecBuilder.create(i -> i.group(
            ProcessingInput.CODEC.listOf().fieldOf("inputs").forGetter(ProcessingPattern::inputs),
            ProcessingOutput.CODEC.listOf().fieldOf("outputs").forGetter(ProcessingPattern::outputs),
            Codec.STRING.fieldOf("machine").forGetter(ProcessingPattern::machineType),
            Codec.INT.fieldOf("timeout").forGetter(ProcessingPattern::timeoutTicks),
            Codec.STRING.optionalFieldOf("name", "").forGetter(ProcessingPattern::name),
            Codec.STRING.optionalFieldOf("note", "").forGetter(ProcessingPattern::note)
    ).apply(i, ProcessingPattern::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProcessingPattern> STREAM_CODEC =
            StreamCodec.composite(
                    ProcessingInput.STREAM_CODEC.apply(ByteBufCodecs.list(64)), ProcessingPattern::inputs,
                    ProcessingOutput.STREAM_CODEC.apply(ByteBufCodecs.list(64)), ProcessingPattern::outputs,
                    ByteBufCodecs.stringUtf8(64), ProcessingPattern::machineType,
                    ByteBufCodecs.VAR_INT, ProcessingPattern::timeoutTicks,
                    ByteBufCodecs.stringUtf8(CraftingPattern.MAX_NAME), ProcessingPattern::name,
                    ByteBufCodecs.stringUtf8(CraftingPattern.MAX_NOTE), ProcessingPattern::note,
                    ProcessingPattern::new);

    /** What the recipe is called where it is listed: its name, or its primary output's when it has none. */
    public String displayName() {
        if (!name.isEmpty()) {
            return name;
        }
        final ProcessingOutput primary = primaryOutput();
        return primary == null ? "recipe" : primary.key().displayName().getString();
    }

    /** The same recipe under a new name and note. */
    public ProcessingPattern withName(final String newName, final String newNote) {
        return new ProcessingPattern(inputs, outputs, machineType, timeoutTicks, newName, newNote);
    }

    /** Total of each input key per run (merging duplicate keys), for reservation/planning. */
    public Map<StorageKey, Long> ingredientTotals() {
        final Map<StorageKey, Long> totals = new LinkedHashMap<>();
        for (final ProcessingInput in : inputs) {
            totals.merge(in.key(), in.amount(), Long::sum);
        }
        return totals;
    }

    /** The primary (first) output, what a craft of this pattern aims to produce; null if none declared. */
    @Nullable
    public ProcessingOutput primaryOutput() {
        return outputs.isEmpty() ? null : outputs.get(0);
    }

    /** Two patterns describe the same machine recipe when their machine, inputs and outputs all match. */
    public boolean sameRecipe(final ProcessingPattern other) {
        return machineType.equals(other.machineType)
                && inputs.equals(other.inputs)
                && outputs.equals(other.outputs);
    }
}
