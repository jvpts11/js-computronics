/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.gametest;

import dev.jsc.jscomputronics.module.computing.blockentity.CraftingComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern;
import dev.jsc.jscomputronics.module.computing.crafting.NetworkRecipe;
import dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern;
import dev.jsc.jscomputronics.module.computing.operation.NetworkOperation;
import dev.jsc.jscomputronics.module.computing.operation.NetworkStorage;
import dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import dev.jstech.tests.JsTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Fusion Reactor shell from raw stock to a formed multiblock, all of it through the network: every part is
 * requested with flat patterns (bench and machine side by side), made by one Metallurgic Infuser and one
 * Crafting Computer, then taken out of network storage and placed in the shape Mekanism's validator expects — 36
 * frames on the ring positions of each face and edge, and the controller, ports, glass and adapters on the
 * plus-shaped casing positions. The final assertion is Mekanism's own: the controller reports a formed reactor.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class FusionReactorBuildGameTests {

    private FusionReactorBuildGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = MekanismRig.SETTLE;
    private static final String INFUSER_ID = "mekanism:metallurgic_infuser";
    // The 5x5x5 sits east of the machine rig, its minimum corner here (arena-relative).
    private static final BlockPos SHELL_MIN = new BlockPos(9, 2, 9);
    private static final BlockPos CONTROLLER = SHELL_MIN.offset(2, 4, 2);

    private static Item mek(final String path) {
        return MekanismRig.item(MekanismRig.mek(path));
    }

    private static Item gen(final String path) {
        return MekanismRig.item(MekanismRig.generators(path));
    }

    private static Block block(final String path) {
        return Block.byItem(gen(path));
    }

    private static CraftingPattern bench(final String layout, final Map<Character, Item> keys, final Item result, final int count) {
        final List<ItemStack> grid = new ArrayList<>(CraftingPattern.GRID_SIZE);
        for (int i = 0; i < CraftingPattern.GRID_SIZE; i++) {
            final char c = layout.charAt(i);
            grid.add(c == ' ' ? ItemStack.EMPTY : new ItemStack(keys.get(c)));
        }
        return new CraftingPattern(grid, new ItemStack(result, count));
    }

    private static ProcessingPattern infuse(final Item in, final Item extra, final long extraCount, final Item out) {
        return new ProcessingPattern(
                List.of(new ProcessingPattern.ProcessingInput(StorageKey.of(in), 1),
                        new ProcessingPattern.ProcessingInput(StorageKey.of(extra), extraCount)),
                List.of(new ProcessingPattern.ProcessingOutput(StorageKey.of(out), 1, 100)),
                INFUSER_ID, 400);
    }

    /** Loads every recipe of the shell into the computer's Recipe ROM: five machine patterns, nine bench ones. */
    private static void loadRecipes(final GameTestHelper helper, final CraftingComputerBlockEntity cc) {
        final Item frame = gen("fusion_reactor_frame");
        final Item atomic = mek("alloy_atomic");
        final Item ultimate = mek("ultimate_control_circuit");
        final ProcessingPattern[] machines = {
                infuse(Items.COPPER_INGOT, Items.REDSTONE, 1, mek("alloy_infused")),
                infuse(mek("alloy_infused"), mek("dust_diamond"), 2, mek("alloy_reinforced")),
                infuse(mek("alloy_reinforced"), mek("dust_refined_obsidian"), 4, atomic),
                infuse(mek("ingot_osmium"), Items.REDSTONE, 2, mek("basic_control_circuit")),
                infuse(Items.IRON_INGOT, Items.COAL, 1, mek("enriched_iron"))};
        for (final ProcessingPattern machine : machines) {
            helper.assertTrue(cc.loadMachineRecipe(NetworkRecipe.ofProcessing(machine)), "machine recipe must load");
        }
        final CraftingPattern[] benches = {
                bench("A#A#X#A#A", Map.of('A', atomic, '#', mek("pellet_polonium"), 'X', mek("steel_casing")), frame, 4),
                bench("ACA      ", Map.of('A', atomic, 'C', mek("elite_control_circuit")), ultimate, 1),
                bench("ACA      ", Map.of('A', mek("alloy_reinforced"), 'C', mek("advanced_control_circuit")), mek("elite_control_circuit"), 1),
                bench("ACA      ", Map.of('A', mek("alloy_infused"), 'C', mek("basic_control_circuit")), mek("advanced_control_circuit"), 1),
                bench("AOAO OAOA", Map.of('A', mek("alloy_infused"), 'O', mek("ingot_osmium")), mek("basic_chemical_tank"), 1),
                bench("CGCFTFFFF", Map.of('C', ultimate, 'G', Items.GLASS_PANE, 'F', frame, 'T', mek("basic_chemical_tank")),
                        gen("fusion_reactor_controller"), 1),
                bench(" F FCF F ", Map.of('F', frame, 'C', ultimate), gen("fusion_reactor_port"), 2),
                bench(" R RFR R ", Map.of('F', frame, 'R', Items.REDSTONE), gen("fusion_reactor_logic_adapter"), 1),
                bench("SISIGISIS", Map.of('S', mek("enriched_iron"), 'I', mek("ingot_lead"), 'G', Items.GLASS), gen("reactor_glass"), 4)};
        for (final CraftingPattern pattern : benches) {
            helper.assertTrue(cc.loadPattern(pattern), "bench pattern must load");
        }
    }

    /**
     * Raw stock for the whole shell, to the unit, with the parts requested in {@link #parts()} order: the
     * controller's and ports' surplus frames feed the next part, and the 51 frames of the shell itself are one
     * request of their own, so 17 frame crafts (68 frames) cover the 66 used and two frames are left over.
     */
    private static void seedRawStock(final TestWorldBuilderSeed seed) {
        seed.put(Items.COPPER_INGOT, 96);           // 76 atomic + 8 (elite) + 8 (advanced) + 4 (tank) infused alloys
        seed.put(Items.REDSTONE, 112);              // 96 infusions + 4 basic circuits x2 + 2 adapters x4
        seed.put(mek("dust_diamond"), 168);         // 84 reinforced x2
        seed.put(mek("dust_refined_obsidian"), 304); // 76 atomic x4
        seed.put(mek("pellet_polonium"), 68);       // 17 frame crafts
        seed.put(mek("steel_casing"), 17);
        seed.put(mek("ingot_osmium"), 8);
        seed.put(Items.IRON_INGOT, 8);
        seed.put(Items.COAL, 8);
        seed.put(mek("ingot_lead"), 8);
        seed.put(Items.GLASS, 2);
        seed.put(Items.GLASS_PANE, 1);
    }

    private interface TestWorldBuilderSeed {
        void put(Item item, int count);
    }

    /** The parts to request, in order, and how many of each. */
    private static Map<Item, Integer> parts() {
        final Map<Item, Integer> parts = new LinkedHashMap<>();
        parts.put(gen("fusion_reactor_controller"), 1);
        parts.put(gen("fusion_reactor_port"), 4);
        parts.put(gen("fusion_reactor_logic_adapter"), 2);
        parts.put(gen("reactor_glass"), 8);
        parts.put(gen("fusion_reactor_frame"), 51); // 36 ring/edge frames + 15 on casing cells
        return parts;
    }

    /**
     * The block for one shell position, or null where the validator ignores the cell. On every face the ring
     * cells (the diamond around the centre) and the middle of every edge take a frame; the plus-shaped cells
     * take casing: the controller on top, a port in the middle of each side, two logic adapters, glass on the
     * arms of the top and bottom, frames elsewhere.
     */
    private static Block shellBlockAt(final int x, final int y, final int z) {
        final boolean ex = x == 0 || x == 4;
        final boolean ey = y == 0 || y == 4;
        final boolean ez = z == 0 || z == 4;
        final int extremes = (ex ? 1 : 0) + (ey ? 1 : 0) + (ez ? 1 : 0);
        if (extremes == 3) {
            return null; // corner
        }
        if (extremes == 2) {
            // An edge: only its middle cell is part of the structure, and it is a frame.
            final int along = !ex ? x : !ey ? y : z;
            return along == 2 ? block("fusion_reactor_frame") : null;
        }
        if (extremes == 0) {
            return null; // interior
        }
        // A face: (u, v) are the two coordinates along the face.
        final int u = ex ? y : x;
        final int v = ez ? y : z;
        final boolean ring = (u == 1 || u == 3) && (v == 1 || v == 3);
        final boolean plus = (u == 2 && v >= 1 && v <= 3) || (v == 2 && u >= 1 && u <= 3);
        if (ring) {
            return block("fusion_reactor_frame");
        }
        if (!plus) {
            return null;
        }
        final boolean centre = u == 2 && v == 2;
        if (ey) {
            if (centre) {
                return y == 4 ? block("fusion_reactor_controller") : block("fusion_reactor_frame");
            }
            return block("reactor_glass"); // the four arms of the top and of the bottom: eight glass
        }
        if (centre) {
            return block("fusion_reactor_port");
        }
        // Two logic adapters: the upper arm of the north and south faces.
        if (ez && y == 3 && x == 2) {
            return block("fusion_reactor_logic_adapter");
        }
        return block("fusion_reactor_frame");
    }

    private static boolean reactorFormed(final BlockEntity controller) {
        try {
            final Method getMultiblock = controller.getClass().getMethod("getMultiblock");
            final Object data = getMultiblock.invoke(controller);
            return data != null && (Boolean) data.getClass().getMethod("isFormed").invoke(data);
        } catch (final ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    @GameTest(template = ARENA, timeoutTicks = 90000)
    public static void shell_isCraftedFromRawStockAndFormsTheReactor(final GameTestHelper helper) {
        final MekanismRig.Rig rig = MekanismRig.build(helper, MekanismRig.mek("metallurgic_infuser"));
        final Map<Item, Integer> parts = parts();
        final List<Item> order = new ArrayList<>(parts.keySet());
        final int[] next = {0};
        final int[] planAttempts = {0};
        final String[] failure = new String[1];
        final NetworkOperation[] current = new NetworkOperation[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    MekanismRig.mountBuses(helper);
                    MekanismRig.mountBottomInputBus(helper);
                    seedRawStock((item, count) -> rig.net().seed(item, count));
                    loadRecipes(helper, rig.net().cc());
                })
                // Request the parts one after another; each craft plans its own machine steps. Assertion
                // exceptions keep this step waiting; a real failure is recorded and checked right after.
                .thenWaitUntil(() -> {
                    if (failure[0] != null) {
                        return;
                    }
                    MekanismRig.power(helper);
                    if (current[0] != null && !current[0].isDone()) {
                        throw new net.minecraft.gametest.framework.GameTestAssertException("still crafting " + order.get(next[0] - 1)
                                + ": " + rig.net().mainframe().activeOperationRecords());
                    }
                    if (current[0] != null && current[0].toRecord().status() != OperationRecord.STATUS_COMPLETED) {
                        failure[0] = order.get(next[0] - 1) + " must complete; status=" + current[0].toRecord().status()
                                + " recent=" + rig.net().mainframe().recentOperations();
                        return;
                    }
                    if (next[0] < order.size()) {
                        final Item item = order.get(next[0]);
                        current[0] = rig.net().mainframe().submitNetworkCraft(StorageKey.of(item), parts.get(item), false, "reactor", null);
                        if (current[0] == null) {
                            // The storage index catches up with the seeded stock over a few ticks: retry briefly.
                            if (++planAttempts[0] < 40) {
                                throw new net.minecraft.gametest.framework.GameTestAssertException("planning " + item);
                            }
                            failure[0] = "the Mainframe must plan " + item + " x" + parts.get(item) + " from the raw stock; stock="
                                    + rig.net().storage(helper.getLevel()).query() + " recipes=" + rig.net().mainframe().networkMachineRecipes().size()
                                    + "/" + rig.net().mainframe().networkPatterns().size();
                            return;
                        }
                        planAttempts[0] = 0;
                        next[0]++;
                        throw new net.minecraft.gametest.framework.GameTestAssertException("crafting " + item);
                    }
                })
                .thenExecute(() -> helper.assertTrue(failure[0] == null, String.valueOf(failure[0])))
                .thenExecute(() -> {
                    final NetworkStorage storage = rig.net().storage(helper.getLevel());
                    for (final Map.Entry<Item, Integer> part : parts.entrySet()) {
                        final long have = storage.count(part.getKey());
                        helper.assertTrue(have >= part.getValue(), part.getKey() + ": need " + part.getValue() + ", have " + have
                                + " stock=" + storage.query() + " recent=" + rig.net().mainframe().recentOperations()
                                + " dropped=" + helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                                        net.minecraft.world.phys.AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(16, 8, 16)))).size());
                    }
                    // Take the parts out of the network and build the shell.
                    int placed = 0;
                    for (int x = 0; x < 5; x++) {
                        for (int y = 0; y < 5; y++) {
                            for (int z = 0; z < 5; z++) {
                                final Block block = shellBlockAt(x, y, z);
                                if (block == null) {
                                    continue;
                                }
                                final long taken = storage.select(StorageKey.of(block.asItem()), 1, (key, amount, simulate) -> amount);
                                helper.assertTrue(taken == 1, "network storage must hand out one " + block.asItem() + " for the shell");
                                rig.world().placeFromItem(SHELL_MIN.offset(x, y, z), block);
                                placed++;
                            }
                        }
                    }
                    helper.assertTrue(placed == 66, "the shell is 66 blocks; placed " + placed);
                    // Seventeen frame crafts made 68 frames; 66 went into parts and the shell. Everything else is spent.
                    helper.assertTrue(storage.count(gen("fusion_reactor_frame")) == 2,
                            "two surplus frames must remain; got " + storage.count(gen("fusion_reactor_frame")));
                    for (final Item raw : new Item[]{Items.COPPER_INGOT, Items.REDSTONE, mek("dust_diamond"), mek("dust_refined_obsidian"),
                            mek("pellet_polonium"), mek("steel_casing"), mek("ingot_osmium"), Items.IRON_INGOT, Items.COAL, mek("ingot_lead"),
                            Items.GLASS, Items.GLASS_PANE, mek("alloy_infused"), mek("alloy_reinforced"), mek("alloy_atomic"),
                            mek("basic_control_circuit"), mek("advanced_control_circuit"), mek("elite_control_circuit"),
                            mek("ultimate_control_circuit"), mek("basic_chemical_tank"), mek("enriched_iron")}) {
                        helper.assertTrue(storage.count(raw) == 0, raw + " must be spent to the unit; left " + storage.count(raw));
                    }
                })
                .thenWaitUntil(() -> {
                    final BlockEntity controller = helper.getBlockEntity(CONTROLLER);
                    helper.assertTrue(controller != null && reactorFormed(controller),
                            "Mekanism must report the reactor formed at the controller; be=" + controller);
                })
                .thenSucceed();
    }
}
