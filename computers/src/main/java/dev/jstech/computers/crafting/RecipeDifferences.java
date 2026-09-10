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
 * Says, in one line, what the recipe a player picked does differently from another recipe for the same item:
 * the ingredients it swaps, the stages it adds or saves, the time it costs or saves for the amount asked, and
 * what it is short of. Only what differs is named; two recipes that differ in nothing say so.
 */
public final class RecipeDifferences {

    private static final String SEP = " · ";

    private RecipeDifferences() {
    }

    /** The strip's text for {@code chosen} against {@code other}, for {@code quantity} of the result. */
    public static String describe(final RecipeChoice chosen, final RecipeChoice other, final long quantity) {
        final List<String> parts = new ArrayList<>();
        final String ingredients = ingredients(chosen, other);
        if (!ingredients.isEmpty()) {
            parts.add(ingredients);
        }
        final String stages = stages(chosen.stages(), other.stages());
        if (!stages.isEmpty()) {
            parts.add(stages);
        }
        final String time = time(chosen.estimateTicks(), other.estimateTicks(), quantity);
        if (!time.isEmpty()) {
            parts.add(time);
        }
        final String stock = stock(chosen, other);
        if (!stock.isEmpty()) {
            parts.add(stock);
        }
        final String head = chosen.label() + " against " + other.label() + ": ";
        if (parts.isEmpty()) {
            return head + "the same ingredients, stages and time";
        }
        return head + String.join(SEP, parts);
    }

    /** "takes A instead of X", "also takes A", or "does without X"; empty when both take the same things. */
    private static String ingredients(final RecipeChoice chosen, final RecipeChoice other) {
        final List<String> onlyChosen = namesOnlyIn(chosen, other);
        final List<String> onlyOther = namesOnlyIn(other, chosen);
        if (onlyChosen.isEmpty() && onlyOther.isEmpty()) {
            return "";
        }
        if (!onlyChosen.isEmpty() && !onlyOther.isEmpty()) {
            return "takes " + join(onlyChosen) + " instead of " + join(onlyOther);
        }
        if (!onlyChosen.isEmpty()) {
            return "also takes " + join(onlyChosen);
        }
        return "does without " + join(onlyOther);
    }

    private static List<String> namesOnlyIn(final RecipeChoice a, final RecipeChoice b) {
        final List<String> out = new ArrayList<>();
        for (final RecipeChoice.Input in : a.inputs()) {
            if (!has(b, in.name()) && !out.contains(in.name())) {
                out.add(in.name());
            }
        }
        return out;
    }

    private static boolean has(final RecipeChoice choice, final String name) {
        for (final RecipeChoice.Input in : choice.inputs()) {
            if (in.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static String join(final List<String> names) {
        return String.join(", ", names);
    }

    private static String stages(final int chosen, final int other) {
        final int delta = chosen - other;
        if (delta == 0) {
            return "";
        }
        if (delta == 1) {
            return "one more stage";
        }
        if (delta == -1) {
            return "one stage fewer";
        }
        return delta > 0 ? delta + " more stages" : (-delta) + " stages fewer";
    }

    private static String time(final int chosen, final int other, final long quantity) {
        if (chosen <= 0 || other <= 0) {
            return "";
        }
        final long a = RecipeChoice.seconds(chosen);
        final long b = RecipeChoice.seconds(other);
        if (a == b) {
            return "";
        }
        final long delta = Math.abs(a - b);
        return delta + " s " + (a > b ? "slower" : "faster") + " for " + quantity;
    }

    /** What the chosen recipe is short of, or that it has everything where the other does not. */
    private static String stock(final RecipeChoice chosen, final RecipeChoice other) {
        final List<RecipeChoice.Input> missing = chosen.shortInputs();
        if (missing.isEmpty()) {
            return other.allInStock() ? "" : "everything in stock, unlike " + other.label();
        }
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < missing.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(missing.get(i).shortfall()).append(' ').append(missing.get(i).name());
        }
        out.append(" short, ");
        out.append(chosen.shortIsCraftable() ? "which the network would craft first" : "and nothing on the network makes it");
        return out.toString();
    }
}
