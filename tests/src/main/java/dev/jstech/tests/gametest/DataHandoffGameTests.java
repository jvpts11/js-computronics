/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.operation.DataHandoff;
import dev.jstech.computronics.storage.ChemicalBridges;
import dev.jstech.computronics.storage.ChemicalPort;
import dev.jstech.computronics.storage.DataContainers;
import dev.jstech.computronics.storage.LocalStore;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.tests.JsTests;
import dev.jstech.tests.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * What passes between a player's hands and a computer. A stack handed over as items is stored as its item,
 * the way a chest takes it — a bucket included. Handing over what a held container HOLDS stores the fluid or
 * chemical and returns the container emptied, never stored as an item. A held empty container over a fluid or
 * chemical entry fills from it. Every route (cursor, slot, inventory; terminal or desktop) goes through the
 * same handoff, so a bucket that works on the terminal works on the desktop too.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class DataHandoffGameTests {

    private DataHandoffGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;
    private static final ResourceLocation OXYGEN = ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen");
    private static final ResourceLocation CHEMICAL_TANK = ResourceLocation.fromNamespaceAndPath("mekanism", "basic_chemical_tank");
    private static final ResourceLocation FLUID_TANK = ResourceLocation.fromNamespaceAndPath("mekanism", "basic_fluid_tank");
    private static final BlockPos TANK = new BlockPos(6, 2, 6);

    private static StorageKey water() {
        return StorageKey.of(new FluidStack(Fluids.WATER, 1));
    }

    private static StorageKey lava() {
        return StorageKey.of(new FluidStack(Fluids.LAVA, 1));
    }

    private static StorageKey oxygen() {
        return StorageKey.chemical(OXYGEN);
    }

    private static Block tank(final ResourceLocation id) {
        return BuiltInRegistries.BLOCK.get(id);
    }

    /** Breaks the block at {@code relative} and returns the one item it dropped (a tank keeps its contents). */
    private static ItemStack pickUp(final GameTestHelper helper, final TestWorldBuilder world, final BlockPos relative,
                                    final Block block) {
        helper.getLevel().destroyBlock(world.absolute(relative), true);
        ItemStack picked = ItemStack.EMPTY;
        for (final ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                AABB.encapsulatingFullBlocks(world.absolute(relative.offset(-1, -1, -1)), world.absolute(relative.offset(1, 1, 1))))) {
            if (drop.getItem().is(block.asItem())) {
                picked = drop.getItem().copy();
                drop.discard();
            }
        }
        helper.assertTrue(!picked.isEmpty(), "breaking the tank must drop its item");
        return picked;
    }

    /** What a fluid container holds, summed over its tanks: a tank item caps what one drain hands out. */
    private static long fluidIn(final ItemStack container) {
        return FluidUtil.getFluidHandler(container).map(handler -> {
            long total = 0L;
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                total += handler.getFluidInTank(tank).getAmount();
            }
            return total;
        }).orElse(0L);
    }

    private static long chemicalIn(final ItemStack container, final ResourceLocation chemical) {
        return ChemicalBridges.itemPortFor(container).map(port -> port.count(chemical)).orElse(0L);
    }

    private static Player player(final GameTestHelper helper) {
        return helper.makeMockPlayer(GameType.CREATIVE);
    }

    // Containers

    @GameTest(template = ARENA)
    public static void drain_bucketGivesUpItsWholeFluidAndComesBackEmpty(final GameTestHelper helper) {
        final ItemStack bucket = new ItemStack(Items.WATER_BUCKET);
        helper.assertTrue(DataContainers.holdsData(bucket), "a water bucket carries data");
        final Optional<DataContainers.Drained> drained = DataContainers.drain(bucket, 5000);
        helper.assertTrue(drained.isPresent(), "a bucket with room for it must drain");
        helper.assertTrue(water().equals(drained.get().key()), "the data is water, not the bucket; got " + drained.get().key());
        helper.assertTrue(drained.get().amount() == 1000, "a bucket is 1 000 mB; got " + drained.get().amount());
        helper.assertTrue(drained.get().container().is(Items.BUCKET), "an empty bucket comes back; got " + drained.get().container());
        helper.assertTrue(bucket.is(Items.WATER_BUCKET), "the source stack is left to the caller");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void drain_bucketStaysFullWhenTheRoomIsShort(final GameTestHelper helper) {
        helper.assertTrue(DataContainers.drain(new ItemStack(Items.WATER_BUCKET), 999).isEmpty(),
                "a bucket is atomic: 999 mB of room takes nothing out of it");
        helper.assertTrue(!DataContainers.holdsData(new ItemStack(Items.BUCKET)), "an empty bucket carries no data");
        helper.assertTrue(!DataContainers.holdsData(new ItemStack(Items.DIRT)), "dirt carries no data");
        helper.assertTrue(DataContainers.drain(new ItemStack(Items.DIRT), 5000).isEmpty(), "dirt has nothing to drain");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void canTake_onlyAnEmptyContainerTakesThatData(final GameTestHelper helper) {
        helper.assertTrue(DataContainers.canTake(new ItemStack(Items.BUCKET), water()), "an empty bucket takes water");
        helper.assertTrue(DataContainers.roomFor(new ItemStack(Items.BUCKET), water(), 5000) == 1000, "a full bucket of it");
        helper.assertTrue(DataContainers.roomFor(new ItemStack(Items.BUCKET), water(), 999) == 0, "or nothing short of one");
        helper.assertTrue(!DataContainers.canTake(new ItemStack(Items.WATER_BUCKET), water()), "a full bucket takes no more");
        helper.assertTrue(!DataContainers.canTake(new ItemStack(Items.DIRT), water()), "dirt takes no water");
        helper.assertTrue(!DataContainers.canTake(new ItemStack(Items.BUCKET), oxygen()), "a bucket takes no gas");
        helper.assertTrue(!DataContainers.canTake(new ItemStack(Items.BUCKET), StorageKey.of(Items.DIRT)), "nor an item");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void drain_fluidTankItemGivesUpOnlyWhatFitsAndTakesLeftoverBack(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        world.placeFromItem(TANK, tank(FLUID_TANK));
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final IFluidHandler handler = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, world.absolute(TANK), Direction.UP);
                    helper.assertTrue(handler != null && handler.fill(new FluidStack(Fluids.WATER, 3000), IFluidHandler.FluidAction.EXECUTE) == 3000,
                            "the fluid tank must take 3 000 mB of water");
                })
                .thenExecuteAfter(2, () -> {
                    final ItemStack tankItem = pickUp(helper, world, TANK, tank(FLUID_TANK));
                    helper.assertTrue(fluidIn(tankItem) == 3000, "the tank item keeps its water; holds " + fluidIn(tankItem));
                    helper.assertTrue(DataContainers.holdsData(tankItem), "a filled tank item carries data");
                    // Room for 600 mB: a tank item is not atomic, so exactly that much comes out.
                    final Optional<DataContainers.Drained> drained = DataContainers.drain(tankItem, 600);
                    helper.assertTrue(drained.isPresent(), "a tank item drains what fits");
                    helper.assertTrue(drained.get().amount() == 600 && water().equals(drained.get().key()),
                            "600 mB of water must come out; got " + drained.get().amount() + " of " + drained.get().key());
                    helper.assertTrue(fluidIn(drained.get().container()) == 2400,
                            "the tank item keeps the rest; holds " + fluidIn(drained.get().container()));
                    helper.assertTrue(fluidIn(tankItem) == 3000, "the source stack itself is untouched");
                    final DataContainers.Filled refilled = DataContainers.fill(drained.get().container(), water(), 200);
                    helper.assertTrue(refilled.taken() == 200 && fluidIn(refilled.container()) == 2600,
                            "a refill puts the leftover back; holds " + fluidIn(refilled.container()));
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void drain_chemicalTankItemGivesUpItsGasAndTakesLeftoverBack(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        world.placeFromItem(TANK, tank(CHEMICAL_TANK));
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final Optional<ChemicalPort> port = ChemicalBridges.portFor(helper.getLevel(), world.absolute(TANK), Direction.UP);
                    helper.assertTrue(port.isPresent() && port.get().fill(OXYGEN, 800, false) == 800, "the chemical tank must take 800 mB of oxygen");
                })
                .thenExecuteAfter(2, () -> {
                    final ItemStack tankItem = pickUp(helper, world, TANK, tank(CHEMICAL_TANK));
                    helper.assertTrue(chemicalIn(tankItem, OXYGEN) == 800, "the tank item keeps its oxygen; holds " + chemicalIn(tankItem, OXYGEN));
                    helper.assertTrue(DataContainers.holdsData(tankItem), "a filled chemical tank item carries data");
                    final Optional<DataContainers.Drained> drained = DataContainers.drain(tankItem, 500);
                    helper.assertTrue(drained.isPresent(), "a chemical tank item drains what fits");
                    helper.assertTrue(drained.get().amount() == 500 && oxygen().equals(drained.get().key()),
                            "500 mB of oxygen must come out; got " + drained.get().amount() + " of " + drained.get().key());
                    helper.assertTrue(chemicalIn(drained.get().container(), OXYGEN) == 300,
                            "the tank item keeps the rest; holds " + chemicalIn(drained.get().container(), OXYGEN));
                    final DataContainers.Filled refilled = DataContainers.fill(drained.get().container(), oxygen(), 200);
                    helper.assertTrue(refilled.taken() == 200 && chemicalIn(refilled.container(), OXYGEN) == 500,
                            "a refill puts the leftover back; holds " + chemicalIn(refilled.container(), OXYGEN));
                })
                .thenSucceed();
    }

    // Handing over to the network

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void intoNetwork_rightClickWithAWaterBucketStoresWaterAndReturnsTheBucket(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        final Player player = player(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
                    final DataHandoff.Outcome outcome = DataHandoff.intoNetwork(net.mainframe(), helper.getLevel(),
                            net.mainframe().networkUuid(), player, DataHandoff.cursor(player), 1, true, "test", () -> { });
                    helper.assertTrue(outcome == DataHandoff.Outcome.DEPOSITED, "the water must deposit; got " + outcome);
                    helper.assertTrue(player.containerMenu.getCarried().isEmpty(), "the cursor is empty while the water is in flight");
                })
                .thenWaitUntil(() -> helper.assertTrue(net.storage(helper.getLevel()).count(water()) == 1000,
                        "the network must hold 1 000 mB of water; holds " + net.storage(helper.getLevel()).count(water())))
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(net.storage(helper.getLevel()).count(StorageKey.of(Items.WATER_BUCKET)) == 0, "the bucket itself is never stored");
                    helper.assertTrue(player.containerMenu.getCarried().is(Items.BUCKET), "the emptied bucket comes back onto the free cursor");
                    helper.assertTrue(player.getInventory().countItem(Items.WATER_BUCKET) == 0, "and no water bucket does");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void intoNetwork_leftClickWithAWaterBucketStoresTheBucketAsAnItem(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        final Player player = player(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
                    // Handed over as items — a left click or a shift-click — the bucket is what goes in.
                    final DataHandoff.Outcome outcome = DataHandoff.intoNetwork(net.mainframe(), helper.getLevel(),
                            net.mainframe().networkUuid(), player, DataHandoff.cursor(player), 1, false, "test", () -> { });
                    helper.assertTrue(outcome == DataHandoff.Outcome.DEPOSITED, "the bucket must deposit; got " + outcome);
                })
                .thenWaitUntil(() -> helper.assertTrue(net.storage(helper.getLevel()).count(StorageKey.of(Items.WATER_BUCKET)) == 1,
                        "the network must hold the water bucket as an item; holds " + net.storage(helper.getLevel()).count(StorageKey.of(Items.WATER_BUCKET))))
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(net.storage(helper.getLevel()).count(water()) == 0, "and no loose water");
                    helper.assertTrue(player.containerMenu.getCarried().isEmpty() && player.getInventory().countItem(Items.BUCKET) == 0,
                            "nothing comes back: the bucket itself was stored");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void intoNetwork_chemicalTankItemInASlotStoresItsGas(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        world.placeFromItem(TANK, tank(CHEMICAL_TANK));
        final Player player = player(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final Optional<ChemicalPort> port = ChemicalBridges.portFor(helper.getLevel(), world.absolute(TANK), Direction.UP);
                    helper.assertTrue(port.isPresent() && port.get().fill(OXYGEN, 800, false) == 800, "the chemical tank must take 800 mB of oxygen");
                })
                .thenExecuteAfter(2, () -> {
                    player.getInventory().setItem(0, pickUp(helper, world, TANK, tank(CHEMICAL_TANK)));
                    final DataHandoff.Outcome outcome = DataHandoff.intoNetwork(net.mainframe(), helper.getLevel(),
                            net.mainframe().networkUuid(), player, DataHandoff.inventory(player, 0), 1, true, "test", () -> { });
                    helper.assertTrue(outcome == DataHandoff.Outcome.DEPOSITED, "the tank item must deposit its gas; got " + outcome);
                    helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "the slot is empty while the gas is in flight");
                })
                .thenWaitUntil(() -> helper.assertTrue(net.storage(helper.getLevel()).count(oxygen()) == 800,
                        "the network must hold 800 mB of oxygen; holds " + net.storage(helper.getLevel()).count(oxygen())))
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(net.storage(helper.getLevel()).count(StorageKey.of(tank(CHEMICAL_TANK).asItem())) == 0,
                            "the tank item itself is never stored");
                    final ItemStack back = player.getInventory().getItem(0);
                    helper.assertTrue(back.is(tank(CHEMICAL_TANK).asItem()), "the emptied tank item comes back into its free slot; got " + back);
                    helper.assertTrue(chemicalIn(back, OXYGEN) == 0, "it comes back empty; holds " + chemicalIn(back, OXYGEN));
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void intoNetwork_plainItemsStillStoreAsItems(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        final Player player = player(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    player.containerMenu.setCarried(new ItemStack(Items.DIRT, 5));
                    final DataHandoff.Outcome outcome = DataHandoff.intoNetwork(net.mainframe(), helper.getLevel(),
                            net.mainframe().networkUuid(), player, DataHandoff.cursor(player), 5, false, "test", () -> { });
                    helper.assertTrue(outcome == DataHandoff.Outcome.DEPOSITED, "dirt must deposit; got " + outcome);
                    helper.assertTrue(player.containerMenu.getCarried().isEmpty(), "the whole stack left the cursor");
                })
                .thenWaitUntil(() -> helper.assertTrue(net.storage(helper.getLevel()).count(Items.DIRT) == 5,
                        "the network must hold 5 dirt; holds " + net.storage(helper.getLevel()).count(Items.DIRT)))
                .thenSucceed();
    }

    // Filling a held container from the network

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void fillFromNetwork_emptyBucketOnTheCursorFillsWithWaterOverTicks(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        final Player player = player(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertTrue(net.storage(helper.getLevel()).insert(water(), 3000) == 3000, "the network takes 3 000 mB of water");
                })
                .thenExecuteAfter(2, () -> {
                    player.containerMenu.setCarried(new ItemStack(Items.BUCKET));
                    final DataHandoff.Outcome outcome = DataHandoff.fillFromNetwork(net.mainframe(), helper.getLevel(),
                            net.mainframe().networkUuid(), player, DataHandoff.cursor(player), water(), "test", () -> { });
                    helper.assertTrue(outcome == DataHandoff.Outcome.FILLED, "the bucket must fill; got " + outcome);
                    helper.assertTrue(player.containerMenu.getCarried().isEmpty(), "the bucket is in flight while the SELECT runs");
                })
                .thenWaitUntil(() -> helper.assertTrue(player.containerMenu.getCarried().is(Items.WATER_BUCKET),
                        "the bucket comes back onto the cursor full of water; cursor " + player.containerMenu.getCarried()))
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(net.storage(helper.getLevel()).count(water()) == 2000,
                            "the network is down one bucket; holds " + net.storage(helper.getLevel()).count(water()));
                    helper.assertTrue(player.getInventory().countItem(Items.BUCKET) == 0, "no stray empty bucket");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void fillFromNetwork_emptyChemicalTankItemTakesOxygen(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        world.placeFromItem(TANK, tank(CHEMICAL_TANK));
        final Player player = player(helper);
        final long[] expected = {0L};
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> helper.assertTrue(
                        net.storage(helper.getLevel()).insert(oxygen(), 800) == 800, "the network takes 800 mB of oxygen"))
                // The index that a SELECT plans against catches up with a direct store write on the next tick.
                .thenExecuteAfter(2, () -> {
                    final ItemStack empty = pickUp(helper, world, TANK, tank(CHEMICAL_TANK));
                    helper.assertTrue(chemicalIn(empty, OXYGEN) == 0, "the tank item starts empty");
                    // A tank item may cap what one fill puts in; whatever it says it takes is what must arrive.
                    expected[0] = DataContainers.roomFor(empty, oxygen(), 800);
                    helper.assertTrue(expected[0] > 0L && expected[0] <= 800L, "an empty tank item takes some oxygen; room " + expected[0]);
                    player.containerMenu.setCarried(empty);
                    final DataHandoff.Outcome outcome = DataHandoff.fillFromNetwork(net.mainframe(), helper.getLevel(),
                            net.mainframe().networkUuid(), player, DataHandoff.cursor(player), oxygen(), "test", () -> { });
                    helper.assertTrue(outcome == DataHandoff.Outcome.FILLED, "the tank item must fill; got " + outcome);
                })
                .thenWaitUntil(() -> helper.assertTrue(player.containerMenu.getCarried().is(tank(CHEMICAL_TANK).asItem())
                                && chemicalIn(player.containerMenu.getCarried(), OXYGEN) == expected[0],
                        "the tank item comes back onto the cursor holding " + expected[0] + " mB; cursor "
                                + player.containerMenu.getCarried() + " holds " + chemicalIn(player.containerMenu.getCarried(), OXYGEN)))
                .thenExecuteAfter(2, () -> helper.assertTrue(net.storage(helper.getLevel()).count(oxygen()) == 800 - expected[0],
                        "the network gave exactly that much; holds " + net.storage(helper.getLevel()).count(oxygen())))
                .thenSucceed();
    }

    // A computer's own disks

    @GameTest(template = ARENA)
    public static void intoLocalStore_rightClickWithALavaBucketStoresLavaAtOnce(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        final Player player = player(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final LocalStore store = net.mainframe().localStore();
                    helper.assertTrue(store.freeWeight() >= 1000, "the Mainframe's disk has room; free " + store.freeWeight());
                    player.containerMenu.setCarried(new ItemStack(Items.LAVA_BUCKET));
                    final DataHandoff.Outcome outcome = DataHandoff.intoLocalStore(store, player, DataHandoff.cursor(player), 1, true);
                    helper.assertTrue(outcome == DataHandoff.Outcome.DEPOSITED, "the lava must deposit; got " + outcome);
                    helper.assertTrue(store.count(lava()) == 1000, "the disk holds 1 000 mB of lava; holds " + store.count(lava()));
                    helper.assertTrue(store.count(StorageKey.of(Items.LAVA_BUCKET)) == 0, "the bucket itself is never stored");
                    helper.assertTrue(player.containerMenu.getCarried().is(Items.BUCKET), "the emptied bucket is back on the cursor at once");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void intoLocalStore_bucketWithNoRoomStaysOnTheCursor(final GameTestHelper helper) {
        final Player player = player(helper);
        final LocalStore empty = new LocalStore(List.of(), () -> { });
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        final DataHandoff.Outcome outcome = DataHandoff.intoLocalStore(empty, player, DataHandoff.cursor(player), 1, true);
        helper.assertTrue(outcome == DataHandoff.Outcome.NO_ROOM, "no disks, no room; got " + outcome);
        helper.assertTrue(player.containerMenu.getCarried().is(Items.WATER_BUCKET), "the full bucket stays on the cursor");
        helper.assertTrue(player.getInventory().countItem(Items.BUCKET) == 0, "and no empty bucket appears");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void fillFromLocalStore_emptyBucketOnTheCursorFillsInHand(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        final Player player = player(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final LocalStore store = net.mainframe().localStore();
                    helper.assertTrue(store.insert(lava(), 2500) == 2500, "the disk takes 2 500 mB of lava");
                    player.containerMenu.setCarried(new ItemStack(Items.BUCKET));
                    final DataHandoff.Outcome outcome = DataHandoff.fillFromLocalStore(store, player, DataHandoff.cursor(player), lava());
                    helper.assertTrue(outcome == DataHandoff.Outcome.FILLED, "the bucket must fill; got " + outcome);
                    helper.assertTrue(player.containerMenu.getCarried().is(Items.LAVA_BUCKET), "the bucket in hand is now a lava bucket; cursor " + player.containerMenu.getCarried());
                    helper.assertTrue(store.count(lava()) == 1500, "the disk is down one bucket; holds " + store.count(lava()));
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void fillFromLocalStore_bucketNeedsAFullBucket(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        final Player player = player(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final LocalStore store = net.mainframe().localStore();
                    helper.assertTrue(store.insert(water(), 500) == 500, "the disk takes 500 mB of water");
                    player.containerMenu.setCarried(new ItemStack(Items.BUCKET));
                    final DataHandoff.Outcome outcome = DataHandoff.fillFromLocalStore(store, player, DataHandoff.cursor(player), water());
                    helper.assertTrue(outcome == DataHandoff.Outcome.NO_ROOM, "half a bucket fills nothing; got " + outcome);
                    helper.assertTrue(player.containerMenu.getCarried().is(Items.BUCKET), "the empty bucket stays on the cursor");
                    helper.assertTrue(store.count(water()) == 500, "and the water stays on the disk; holds " + store.count(water()));
                })
                .thenSucceed();
    }
}
