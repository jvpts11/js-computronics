/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computers.operation.NetworkStorage;
import dev.jstech.computers.storage.ChemicalBridges;
import dev.jstech.computers.storage.IChemicalPort;
import dev.jstech.computers.storage.ExternalDataPort;
import dev.jstech.computers.storage.StorageKey;
import dev.jstech.tests.JsTests;
import dev.jstech.tests.testkit.TestWorldBuilder;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/**
 * "Everything is data": a chemical is a third kind of storage key next to items and fluids. The network stores
 * it by id and millibucket, saves and syncs it, and moves it in and out of real machines through the chemical
 * bridge, here Mekanism's, present on the dev runtime.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class ChemicalDataGameTests {

    private ChemicalDataGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;
    private static final ResourceLocation OXYGEN = ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen");
    private static final ResourceLocation TANK = ResourceLocation.fromNamespaceAndPath("mekanism", "basic_chemical_tank");

    @GameTest(template = ARENA)
    public static void storageKey_chemicalRoundTripsThroughNbtAndTheWire(final GameTestHelper helper) {
        final StorageKey oxygen = StorageKey.chemical(OXYGEN);
        helper.assertTrue(oxygen.isChemical() && !oxygen.isItem() && !oxygen.isFluid(), "a chemical key is only a chemical");
        helper.assertTrue(oxygen.weight(250) == 250, "a chemical weighs its millibuckets, like a fluid");
        helper.assertTrue(oxygen.batch() == 1000, "a chemical batch is a bucket");
        helper.assertTrue(oxygen.equals(StorageKey.chemical(OXYGEN)) && oxygen.hashCode() == StorageKey.chemical(OXYGEN).hashCode(),
                "chemical keys compare by id");
        helper.assertTrue(!oxygen.equals(StorageKey.of(Items.IRON_INGOT)) && !oxygen.equals(StorageKey.of(new FluidStack(Fluids.WATER, 1))),
                "a chemical never equals an item or a fluid");

        final RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
        for (final StorageKey key : new StorageKey[]{oxygen, StorageKey.of(Items.IRON_INGOT), StorageKey.of(new FluidStack(Fluids.WATER, 1))}) {
            final Tag saved = StorageKey.CODEC.encodeStart(ops, key).getOrThrow();
            final StorageKey back = StorageKey.CODEC.parse(ops, saved).getOrThrow();
            helper.assertTrue(key.equals(back), key + " must survive the NBT codec; got " + back);
            final RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            StorageKey.STREAM_CODEC.encode(buf, key);
            final StorageKey wire = StorageKey.STREAM_CODEC.decode(buf);
            helper.assertTrue(key.equals(wire), key + " must survive the wire; got " + wire);
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void networkStorage_holdsAndSelectsAChemicalAsData(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        final StorageKey oxygen = StorageKey.chemical(OXYGEN);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final NetworkStorage storage = net.storage(helper.getLevel());
                    final long stored = storage.insert(oxygen, 4000);
                    helper.assertTrue(stored == 4000, "the server must take 4 000 mB of oxygen as data; took " + stored);
                    helper.assertTrue(storage.count(oxygen) == 4000, "the network must count the oxygen; got " + storage.count(oxygen));
                    // A chemical weighs its millibuckets: 4 000 mB = 4 items' worth of disk.
                    helper.assertTrue(net.rack().getServerStorage(0).usedWeight() >= 4000,
                            "the disk must account the chemical's weight; used=" + net.rack().getServerStorage(0).usedWeight());
                    final long[] delivered = {0};
                    final long moved = storage.select(oxygen, 1500, (key, amount, simulate) -> {
                        if (!simulate) {
                            delivered[0] += amount;
                        }
                        return amount;
                    });
                    helper.assertTrue(moved == 1500 && delivered[0] == 1500, "SELECT must hand 1 500 mB to the sink; moved=" + moved);
                    helper.assertTrue(storage.count(oxygen) == 2500, "2 500 mB must remain; got " + storage.count(oxygen));
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void chemicalBridge_movesOxygenIntoAndOutOfAMekanismTank(final GameTestHelper helper) {
        helper.assertTrue(ChemicalBridges.anyRegistered(), "the Mekanism chemical bridge must be registered on the dev runtime");
        final Block tank = BuiltInRegistries.BLOCK.get(TANK);
        helper.assertTrue(tank != null && tank != Blocks.AIR, "Mekanism's basic chemical tank must exist");
        final BlockPos pos = new BlockPos(2, 2, 2);
        // Placed like a player would: the tank's factory side configuration travels in its item components.
        TestWorldBuilder.forGameTest(helper).placeFromItem(pos, tank);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    // A fresh tank takes input on every face and only gives output through its front (north).
                    final Optional<IChemicalPort> input = ChemicalBridges.portFor(helper.getLevel(), helper.absolutePos(pos), Direction.UP);
                    final Optional<IChemicalPort> output = ChemicalBridges.portFor(helper.getLevel(), helper.absolutePos(pos), Direction.NORTH);
                    helper.assertTrue(input.isPresent() && output.isPresent(), "the bridge must expose the tank's chemical handler on both faces");
                    final long filled = input.get().fill(OXYGEN, 500, false);
                    helper.assertTrue(filled == 500, "the tank must take 500 mB of oxygen; took " + filled);
                    helper.assertTrue(input.get().count(OXYGEN) == 500, "the port must count the oxygen in the tank");
                    helper.assertTrue(input.get().available().contains(OXYGEN), "the port must list oxygen as available");
                    helper.assertTrue(input.get().drain(OXYGEN, 500, true) == 0, "an input-only face must refuse to give oxygen back");
                    final long drained = output.get().drain(OXYGEN, 500, false);
                    helper.assertTrue(drained == 500 && output.get().count(OXYGEN) == 0, "the output face must give the oxygen back; drained " + drained);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void externalPort_carriesAChemicalBetweenTheNetworkAndAMekanismTank(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        final BlockPos tankPos = new BlockPos(5, 2, 5);
        world.placeFromItem(tankPos, BuiltInRegistries.BLOCK.get(TANK));
        final StorageKey oxygen = StorageKey.chemical(OXYGEN);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final NetworkStorage storage = net.storage(helper.getLevel());
                    storage.insert(oxygen, 2000);
                    // Input faces and the output face are distinct ports, exactly as a machine's buses see them.
                    final ExternalDataPort port = new ExternalDataPort(null, null,
                            ChemicalBridges.portFor(helper.getLevel(), world.absolute(tankPos), Direction.UP).orElse(null));
                    final ExternalDataPort outputPort = new ExternalDataPort(null, null,
                            ChemicalBridges.portFor(helper.getLevel(), world.absolute(tankPos), Direction.NORTH).orElse(null));
                    helper.assertTrue(!port.isEmpty() && !outputPort.isEmpty(), "the tank must offer a chemical port on both faces");
                    // Network -> machine: SELECT feeds the tank through the same port a machine input uses.
                    final long fed = storage.select(oxygen, 1200, port);
                    helper.assertTrue(fed == 1200 && port.count(oxygen) == 1200, "SELECT must deliver 1 200 mB into the tank; fed=" + fed);
                    helper.assertTrue(storage.count(oxygen) == 800, "the network must have 800 mB left");
                    // Machine -> network: collecting output pulls it back through the output face.
                    final long pulled = outputPort.extract(oxygen, 1200, false);
                    helper.assertTrue(pulled == 1200, "the port must drain the tank; got " + pulled);
                    final long back = storage.insert(oxygen, pulled);
                    helper.assertTrue(back == 1200 && storage.count(oxygen) == 2000, "the oxygen must land back in the network as data");
                })
                .thenSucceed();
    }

    /*
     * The bus cable hangs south of the Ethernet at (4,2,2); tank A's front (north) touches the cable's south
     * face (the tank's only output face) and tank B stands west of the cable, taking input on its east face.
     */
    private static final BlockPos BUS_CABLE = new BlockPos(4, 2, 3);
    private static final BlockPos TANK_A = new BlockPos(4, 2, 4);
    private static final BlockPos TANK_B = new BlockPos(3, 2, 3);

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void importBus_pullsAGasOutOfATankIntoTheNetwork(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        world.setBlock(BUS_CABLE, dev.jstech.computers.ComputingModule.ETHERNET_CABLE.get());
        world.placeFromItem(TANK_A, BuiltInRegistries.BLOCK.get(TANK));
        final StorageKey oxygen = StorageKey.chemical(OXYGEN);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final Optional<IChemicalPort> tank = ChemicalBridges.portFor(helper.getLevel(), world.absolute(TANK_A), Direction.UP);
                    helper.assertTrue(tank.isPresent() && tank.get().fill(OXYGEN, 500, false) == 500, "the tank must take 500 mB of oxygen");
                    if (world.getBlockEntity(BUS_CABLE) instanceof dev.jstech.computers.blockentity.DataCableBlockEntity cable) {
                        cable.addPart(Direction.SOUTH, new dev.jstech.computers.block.part.ImportBusPart());
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(net.storage(helper.getLevel()).count(oxygen) >= 500,
                        "the Import Bus must bring the oxygen into the network as data; got " + net.storage(helper.getLevel()).count(oxygen)
                                + " active=" + net.mainframe().activeOperationRecords()))
                .thenExecute(() -> {
                    final Optional<IChemicalPort> tank = ChemicalBridges.portFor(helper.getLevel(), world.absolute(TANK_A), Direction.UP);
                    helper.assertTrue(tank.isPresent() && tank.get().count(OXYGEN) == 0, "the tank must be drained; holds "
                            + tank.map(t -> t.count(OXYGEN)).orElse(-1L));
                    helper.assertTrue(net.storage(helper.getLevel()).count(oxygen) == 500, "exactly 500 mB must be in the network");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void exportBus_metersAGasIntoATankUpToItsMax(final GameTestHelper helper) {
        /*
         * The filter names the chemical the way a bucket names a fluid: with an item that carries it, here a
         * tank item that held oxygen when it was picked up. The max keeps the faced tank at 300 mB, no more.
         */
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        world.setBlock(BUS_CABLE, dev.jstech.computers.ComputingModule.ETHERNET_CABLE.get());
        world.placeFromItem(TANK_A, BuiltInRegistries.BLOCK.get(TANK));
        world.placeFromItem(TANK_B, BuiltInRegistries.BLOCK.get(TANK));
        final StorageKey oxygen = StorageKey.chemical(OXYGEN);
        final ItemStack[] filterItem = {ItemStack.EMPTY};
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final Optional<IChemicalPort> tank = ChemicalBridges.portFor(helper.getLevel(), world.absolute(TANK_A), Direction.UP);
                    helper.assertTrue(tank.isPresent() && tank.get().fill(OXYGEN, 100, false) == 100, "tank A must take 100 mB of oxygen");
                    net.storage(helper.getLevel()).insert(oxygen, 2000);
                    // Pick tank A up: the item keeps its gas, and that item is the filter.
                    helper.getLevel().destroyBlock(world.absolute(TANK_A), true);
                })
                .thenExecuteAfter(2, () -> {
                    for (final ItemEntity drop : helper.getLevel().getEntitiesOfClass(
                            ItemEntity.class,
                            AABB.encapsulatingFullBlocks(world.absolute(TANK_A.offset(-1, -1, -1)), world.absolute(TANK_A.offset(1, 1, 1))))) {
                        if (drop.getItem().is(BuiltInRegistries.BLOCK.get(TANK).asItem())) {
                            filterItem[0] = drop.getItem().copy();
                            drop.discard();
                        }
                    }
                    helper.assertTrue(!filterItem[0].isEmpty(), "picking the tank up must drop its item");
                    helper.assertTrue(ChemicalBridges.chemicalOf(filterItem[0]).filter(OXYGEN::equals).isPresent(),
                            "the tank item must carry the oxygen; got " + ChemicalBridges.chemicalOf(filterItem[0]));
                    if (world.getBlockEntity(BUS_CABLE) instanceof dev.jstech.computers.blockentity.DataCableBlockEntity cable) {
                        final var bus = new dev.jstech.computers.block.part.ExportBusPart();
                        cable.addPart(Direction.WEST, bus);
                        bus.setFilter(filterItem[0]);
                        bus.getDataAccess().set(1, 300); // max: keep the faced block at 300 mB
                    }
                })
                .thenWaitUntil(() -> {
                    final Optional<IChemicalPort> tank = ChemicalBridges.portFor(helper.getLevel(), world.absolute(TANK_B), Direction.UP);
                    helper.assertTrue(tank.isPresent() && tank.get().count(OXYGEN) >= 300, "the Export Bus must push oxygen into tank B; holds "
                            + tank.map(t -> t.count(OXYGEN)).orElse(-1L) + " active=" + net.mainframe().activeOperationRecords());
                })
                .thenExecuteAfter(40, () -> {
                    final Optional<IChemicalPort> tank = ChemicalBridges.portFor(helper.getLevel(), world.absolute(TANK_B), Direction.UP);
                    helper.assertTrue(tank.isPresent() && tank.get().count(OXYGEN) == 300, "the max must hold tank B at 300 mB; holds "
                            + tank.map(t -> t.count(OXYGEN)).orElse(-1L));
                    helper.assertTrue(net.storage(helper.getLevel()).count(oxygen) == 1700, "the network must have handed over exactly 300 mB; holds "
                            + net.storage(helper.getLevel()).count(oxygen));
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void externalPort_offersEveryKindOfDataTheBlockHas(final GameTestHelper helper) {
        /*
         * The one factory every production port goes through must find each kind a block offers on a face (
         * a chemical tank has item slots and a chemical tank, a furnace only item slots) and none it lacks.
         */
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final BlockPos tank = new BlockPos(2, 2, 2);
        final BlockPos furnace = new BlockPos(4, 2, 2);
        world.placeFromItem(tank, BuiltInRegistries.BLOCK.get(TANK));
        world.setBlock(furnace, Blocks.FURNACE);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ExternalDataPort tankPort = ExternalDataPort.at(helper.getLevel(), world.absolute(tank), Direction.UP);
                    helper.assertTrue(tankPort.kinds().contains(StorageKey.Kind.CHEMICAL) && tankPort.kinds().contains(StorageKey.Kind.ITEM)
                                    && !tankPort.kinds().contains(StorageKey.Kind.FLUID),
                            "a chemical tank's top offers items and chemicals, not fluids; got " + tankPort.kinds());
                    final ExternalDataPort furnacePort = ExternalDataPort.at(helper.getLevel(), world.absolute(furnace), Direction.UP);
                    helper.assertTrue(furnacePort.kinds().equals(java.util.Set.of(StorageKey.Kind.ITEM)),
                            "a furnace offers items only; got " + furnacePort.kinds());
                    helper.assertTrue(ExternalDataPort.at(helper.getLevel(), world.absolute(new BlockPos(6, 2, 2)), Direction.UP).isEmpty(),
                            "air offers nothing");
                })
                .thenSucceed();
    }
}
