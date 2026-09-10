/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.crafting;

import java.util.ArrayList;
import java.util.List;

/**
 * One way the network can make an item, as the craft dialog offers it when there is more than one: what the
 * recipe is called, what kind it is, the machines it runs through, how many stages it has, how long the amount
 * asked would take, and its direct inputs against the stock. Plain text and numbers only, so the dialog's
 * comparison of two choices can be worked out without the game.
 *
 * @param label         the recipe's name as its author gave it, or its result's name
 * @param kind          one of {@link #KIND_BENCH}, {@link #KIND_PROCESSING}, {@link #KIND_MULTI_STAGE}
 * @param machines      the machines the recipe runs through, in order; empty for a bench recipe
 * @param stages        how many stages the recipe has (one for a bench or processing recipe)
 * @param estimateTicks how long the amount asked would take, or zero when there is no estimate
 * @param inputs        the recipe's direct inputs for the amount asked, with what the network holds of each
 * @param feasible      whether the network can deliver the amount asked through this recipe, crafting what is
 *                      short where a pattern makes it
 */
public record RecipeChoice(String label, String kind, List<String> machines, int stages, int estimateTicks,
                           List<Input> inputs, boolean feasible) {

    public static final String KIND_BENCH = "bench";
    public static final String KIND_PROCESSING = "processing";
    public static final String KIND_MULTI_STAGE = "multi-stage";

    /**
     * One direct input of a recipe for the amount asked.
     *
     * @param name      the input's display name
     * @param need      how much the amount asked consumes
     * @param have      how much the network holds
     * @param craftable whether another pattern on the network makes the input, so a shortfall can be covered
     */
    public record Input(String name, long need, long have, boolean craftable) {

        public boolean satisfied() {
            return have >= need;
        }

        public long shortfall() {
            return Math.max(0L, need - have);
        }
    }

    public RecipeChoice {
        machines = List.copyOf(machines);
        inputs = List.copyOf(inputs);
    }

    /** The inputs the network is short of, in the recipe's order. */
    public List<Input> shortInputs() {
        final List<Input> out = new ArrayList<>();
        for (final Input in : inputs) {
            if (!in.satisfied()) {
                out.add(in);
            }
        }
        return out;
    }

    public boolean allInStock() {
        return shortInputs().isEmpty();
    }

    /** Whether every input that is short can be crafted by some other pattern on the network. */
    public boolean shortIsCraftable() {
        for (final Input in : shortInputs()) {
            if (!in.craftable()) {
                return false;
            }
        }
        return true;
    }

    /** The machines in order, joined with arrows; "Bench" for a bench recipe. */
    public String machinesLine() {
        return machines.isEmpty() ? "Bench" : String.join(" -> ", machines);
    }

    /** The one-line description under the name: kind, machines, stages and time. */
    public String kindLine() {
        final StringBuilder out = new StringBuilder(kind).append(" · ").append(machinesLine())
                .append(" · ").append(stages).append(stages == 1 ? " stage" : " stages");
        if (estimateTicks > 0) {
            out.append(" · ~").append(seconds(estimateTicks)).append(" s");
        }
        return out.toString();
    }

    /** The note under the inputs: everything in stock, or what is missing and whether it can be crafted. */
    public String stockNote() {
        final List<Input> missing = shortInputs();
        if (missing.isEmpty()) {
            return "everything in stock";
        }
        final StringBuilder out = new StringBuilder("missing ");
        for (int i = 0; i < missing.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(missing.get(i).shortfall()).append(' ').append(missing.get(i).name());
        }
        out.append(shortIsCraftable() ? " · can be crafted" : " · nothing makes it");
        return out.toString();
    }

    /** Whole seconds for a tick count, never less than one for a positive count. */
    public static long seconds(final int ticks) {
        return ticks <= 0 ? 0L : Math.max(1L, Math.round(ticks / 20.0));
    }
}
