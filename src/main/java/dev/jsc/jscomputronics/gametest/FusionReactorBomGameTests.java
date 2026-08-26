/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.gametest;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.computing.crafting.CraftPlanner;
import dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern;
import dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The whole Fusion Reactor shell as a planning problem: every part of the 5x5x5 (controller, four ports, two
 * logic adapters, eight reactor glass, the laser focus matrix and the 38 plain frames) is requested against
 * one raw stock with flat patterns only — bench recipes and machine recipes side by side — and the planner
 * must find every part feasible and spend the raw stock to the unit. Polonium pellets and steel casings are
 * raw by decision; ingots (lead, osmium, iron) are raw to keep the tree at the reactor's own recipes.
 */
@GameTestHolder(JsComputronics.MODID)
@PrefixGameTestTemplate(false)
public final class FusionReactorBomGameTests {

    private FusionReactorBomGameTests() {
    }

    private static final String ARENA = "empty";
    private static final String INFUSER = "mekanism:metallurgic_infuser";

    private static Item mek(final String path) {
        return MekanismRig.item(MekanismRig.mek(path));
    }

    private static Item gen(final String path) {
        return MekanismRig.item(MekanismRig.generators(path));
    }

    private static List<ItemStack> grid() {
        final List<ItemStack> grid = new ArrayList<>(CraftingPattern.GRID_SIZE);
        for (int i = 0; i < CraftingPattern.GRID_SIZE; i++) {
            grid.add(ItemStack.EMPTY);
        }
        return grid;
    }

    /** A 3x3 bench pattern from a 9-character layout and a key map (' ' = empty). */
    private static CraftingPattern bench(final String layout, final Map<Character, Item> keys, final Item result, final int count) {
        final List<ItemStack> grid = grid();
        for (int i = 0; i < 9; i++) {
            final char c = layout.charAt(i);
            if (c != ' ') {
                grid.set(i, new ItemStack(keys.get(c)));
            }
        }
        return new CraftingPattern(grid, new ItemStack(result, count));
    }

    private static ProcessingPattern infuse(final Item in, final Item extra, final long extraCount, final Item out) {
        return new ProcessingPattern(
                List.of(new ProcessingPattern.ProcessingInput(StorageKey.of(in), 1),
                        new ProcessingPattern.ProcessingInput(StorageKey.of(extra), extraCount)),
                List.of(new ProcessingPattern.ProcessingOutput(StorageKey.of(out), 1, 100)),
                INFUSER, 400);
    }

    private static List<ProcessingPattern> machines() {
        return List.of(
                infuse(Items.COPPER_INGOT, Items.REDSTONE, 1, mek("alloy_infused")),
                infuse(mek("alloy_infused"), mek("dust_diamond"), 2, mek("alloy_reinforced")),
                infuse(mek("alloy_reinforced"), mek("dust_refined_obsidian"), 4, mek("alloy_atomic")),
                // Basic circuit: osmium + 20 mB of redstone (two dusts).
                infuse(mek("ingot_osmium"), Items.REDSTONE, 2, mek("basic_control_circuit")),
                // Enriched iron: iron + 10 mB of carbon (one coal).
                infuse(Items.IRON_INGOT, Items.COAL, 1, mek("enriched_iron")));
    }

    private static List<CraftingPattern> benches() {
        final Item frame = gen("fusion_reactor_frame");
        final Item atomic = mek("alloy_atomic");
        final Item ultimate = mek("ultimate_control_circuit");
        final List<CraftingPattern> patterns = new ArrayList<>();
        patterns.add(bench("A#A#X#A#A", Map.of('A', atomic, '#', mek("pellet_polonium"), 'X', mek("steel_casing")), frame, 4));
        patterns.add(bench("ACA      ", Map.of('A', atomic, 'C', mek("elite_control_circuit")), ultimate, 1));
        patterns.add(bench("ACA      ", Map.of('A', mek("alloy_reinforced"), 'C', mek("advanced_control_circuit")), mek("elite_control_circuit"), 1));
        patterns.add(bench("ACA      ", Map.of('A', mek("alloy_infused"), 'C', mek("basic_control_circuit")), mek("advanced_control_circuit"), 1));
        patterns.add(bench("AOAO OAOA", Map.of('A', mek("alloy_infused"), 'O', mek("ingot_osmium")), mek("basic_chemical_tank"), 1));
        patterns.add(bench("CGCFTFFFF", Map.of('C', ultimate, 'G', Items.GLASS_PANE, 'F', frame, 'T', mek("basic_chemical_tank")),
                gen("fusion_reactor_controller"), 1));
        patterns.add(bench(" F FCF F ", Map.of('F', frame, 'C', ultimate), gen("fusion_reactor_port"), 2));
        patterns.add(bench(" R RFR R ", Map.of('F', frame, 'R', Items.REDSTONE), gen("fusion_reactor_logic_adapter"), 1));
        patterns.add(bench("SISIGISIS", Map.of('S', mek("enriched_iron"), 'I', mek("ingot_lead"), 'G', Items.GLASS), gen("reactor_glass"), 4));
        patterns.add(bench(" G GRG G ", Map.of('G', gen("reactor_glass"), 'R', Items.REDSTONE_BLOCK), gen("laser_focus_matrix"), 2));
        return patterns;
    }

    /** The raw stock the shell needs, to the unit, when each part is requested on its own. */
    private static Map<StorageKey, Long> rawStock() {
        final Map<StorageKey, Long> stock = new LinkedHashMap<>();
        // 15 frame crafts (60 frames for 53 needed) + 4 ultimate circuits' 8 atomic: 68 atomic alloys.
        stock.put(StorageKey.of(Items.COPPER_INGOT), 88L);          // 68 atomic + 8 (elite) + 8 (advanced) + 4 (tank) infused alloys
        stock.put(StorageKey.of(Items.REDSTONE), 104L);             // 88 infusions + 4 basic circuits x2 + 2 adapters x4
        stock.put(StorageKey.of(mek("dust_diamond")), 152L);        // 76 reinforced x2
        stock.put(StorageKey.of(mek("dust_refined_obsidian")), 272L); // 68 atomic x4
        stock.put(StorageKey.of(mek("pellet_polonium")), 60L);      // 15 frame crafts x4
        stock.put(StorageKey.of(mek("steel_casing")), 15L);         // 15 frame crafts
        stock.put(StorageKey.of(mek("ingot_osmium")), 8L);          // 4 basic circuits + 4 for the chemical tank
        stock.put(StorageKey.of(Items.IRON_INGOT), 12L);            // 12 enriched iron for 3 reactor glass crafts
        stock.put(StorageKey.of(Items.COAL), 12L);
        stock.put(StorageKey.of(mek("ingot_lead")), 12L);
        stock.put(StorageKey.of(Items.GLASS), 3L);
        stock.put(StorageKey.of(Items.GLASS_PANE), 1L);
        stock.put(StorageKey.of(Items.REDSTONE_BLOCK), 1L);
        return stock;
    }

    @GameTest(template = ARENA)
    public static void plan_expandsEveryReactorShellPartFromRawStock(final GameTestHelper helper) {
        final List<CraftingPattern> benches = benches();
        final List<ProcessingPattern> machines = machines();
        final Map<StorageKey, Long> stock = new HashMap<>(rawStock());
        final Map<StorageKey, Long> parts = new LinkedHashMap<>();
        parts.put(StorageKey.of(gen("fusion_reactor_controller")), 1L);
        parts.put(StorageKey.of(gen("fusion_reactor_port")), 4L);
        parts.put(StorageKey.of(gen("fusion_reactor_logic_adapter")), 2L);
        parts.put(StorageKey.of(gen("reactor_glass")), 8L);
        parts.put(StorageKey.of(gen("laser_focus_matrix")), 1L);
        parts.put(StorageKey.of(gen("fusion_reactor_frame")), 38L);

        long machineRuns = 0;
        long benchRuns = 0;
        for (final Map.Entry<StorageKey, Long> part : parts.entrySet()) {
            final CraftPlanner.Plan plan = CraftPlanner.plan(part.getKey(), part.getValue(), benches, machines, stock);
            helper.assertTrue(plan.feasible(), part.getKey() + " x" + part.getValue() + " must be feasible from the raw stock; missing="
                    + plan.missing() + " stock=" + stock);
            for (final CraftPlanner.Step step : plan.steps()) {
                if (step.isMachine()) {
                    machineRuns += step.runs();
                } else {
                    benchRuns += step.runs();
                }
            }
            // The next part plans against what this one left.
            plan.rawConsumption().forEach((key, used) -> stock.merge(key, -used, Long::sum));
        }
        for (final Map.Entry<StorageKey, Long> left : stock.entrySet()) {
            helper.assertTrue(left.getValue() == 0L, left.getKey() + " must be spent to the unit; left " + left.getValue());
        }
        // 88 infused + 76 reinforced + 68 atomic + 4 basic circuits + 12 enriched iron.
        helper.assertTrue(machineRuns == 248, "the shell costs 248 machine runs; planned " + machineRuns);
        // 15 frames + 4 ultimate + 4 elite + 4 advanced + 1 tank + 1 controller + 2 ports + 2 adapters + 3 glass + 1 matrix.
        helper.assertTrue(benchRuns == 37, "the shell costs 37 bench runs; planned " + benchRuns);
        helper.succeed();
    }
}
