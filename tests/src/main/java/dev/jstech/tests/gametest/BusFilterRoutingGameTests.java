/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.block.part.ImportBusPart;
import dev.jstech.computronics.blockentity.DataCableBlockEntity;
import dev.jstech.computronics.storage.CompositeDataPort;
import dev.jstech.computronics.storage.IDataPort;
import dev.jstech.computronics.storage.ExternalDataPort;
import dev.jstech.computronics.storage.FilteredDataPort;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.tests.JsTests;
import dev.jstech.tests.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

/**
 * The two per-face routing behaviors: a bus carrying a filter restricts its face to that one key so the crafting
 * engine can drive a machine whose ingredients enter through different faces, and bus auto-placement snaps onto a
 * face that offers any kind of data, such as a fluid- or chemical-only machine face, not just an inventory.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class BusFilterRoutingGameTests {

    private BusFilterRoutingGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;
    private static final ResourceLocation CHEMICAL_TANK =
            ResourceLocation.fromNamespaceAndPath("mekanism", "basic_chemical_tank");

    @GameTest(template = ARENA)
    public static void filteredPort_routesEachKeyToItsFilteredFace(final GameTestHelper helper) {
        final ItemStackHandler faceA = new ItemStackHandler(1);
        final ItemStackHandler faceB = new ItemStackHandler(1);
        final StorageKey cobble = StorageKey.of(Items.COBBLESTONE);
        final StorageKey dirt = StorageKey.of(Items.DIRT);
        final StorageKey stone = StorageKey.of(Items.STONE);
        final IDataPort portA = new FilteredDataPort(new ExternalDataPort(faceA, null), cobble);
        final IDataPort portB = new FilteredDataPort(new ExternalDataPort(faceB, null), dirt);
        final IDataPort composite = CompositeDataPort.of(List.of(portA, portB));

        // The cobblestone route reaches only the cobblestone-filtered face.
        final long insertedCobble = composite.insert(cobble, 10, false);
        helper.assertTrue(insertedCobble == 10, "the composite must take all 10 cobblestone; took " + insertedCobble);
        helper.assertTrue(faceA.getStackInSlot(0).getCount() == 10 && faceB.getStackInSlot(0).isEmpty(),
                "cobblestone must land only on its filtered face");

        // The dirt route reaches only the other face, leaving the cobblestone face untouched.
        final long insertedDirt = composite.insert(dirt, 5, false);
        helper.assertTrue(insertedDirt == 5 && faceB.getStackInSlot(0).getCount() == 5,
                "dirt must land only on its filtered face; face held " + faceB.getStackInSlot(0).getCount());
        helper.assertTrue(faceA.getStackInSlot(0).getCount() == 10, "the dirt route must not disturb the cobblestone face");

        // A key no face is filtered for is refused, not spilled onto a mismatched face.
        final long insertedStone = composite.insert(stone, 3, false);
        helper.assertTrue(insertedStone == 0, "no face carries stone, so the filtered composite must refuse it");

        // Counts see through the filter: each face reports only its own key.
        helper.assertTrue(composite.count(cobble) == 10 && composite.count(dirt) == 5,
                "the composite must count each key on its face");

        // Extraction routes back through the matching face too.
        final long extracted = composite.extract(cobble, 4, false);
        helper.assertTrue(extracted == 4 && faceA.getStackInSlot(0).getCount() == 6,
                "extraction must pull cobblestone from its face; face now holds " + faceA.getStackInSlot(0).getCount());
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void unfilteredPort_carriesAnyKey(final GameTestHelper helper) {
        // An empty filter is a wildcard, so an unfiltered bus behaves exactly like the raw machine face.
        final ItemStackHandler face = new ItemStackHandler(1);
        final IDataPort wild = new FilteredDataPort(new ExternalDataPort(face, null), null);
        final long inserted = wild.insert(StorageKey.of(Items.STONE), 7, false);
        helper.assertTrue(inserted == 7 && face.getStackInSlot(0).getCount() == 7,
                "a null filter must carry anything; face held " + face.getStackInSlot(0).getCount());
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void chooseFace_snapsOntoAChemicalOnlyMachineFace(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final BlockPos cablePos = new BlockPos(5, 2, 3);
        final BlockPos tankPos = new BlockPos(5, 2, 4); // south of the cable; its front (north) faces the cable
        world.setBlock(cablePos, ComputingModule.ETHERNET_CABLE.get());
        final Block tank = BuiltInRegistries.BLOCK.get(CHEMICAL_TANK);
        world.placeFromItem(tankPos, tank);

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    if (!(helper.getBlockEntity(cablePos) instanceof DataCableBlockEntity cable)) {
                        helper.fail("no data cable at " + cablePos);
                        return;
                    }
                    /*
                     * The tank offers a chemical face toward the cable but no inventory there, so the old
                     * item-only auto-placement would have missed it. It is the cable's only data neighbor.
                     */
                    helper.assertTrue(!cable.neighborPort(Direction.SOUTH).isEmpty(),
                            "the cable must see the tank's chemical face as a data neighbor");

                    /*
                     * Right-click the Import Bus onto the cable's top face: auto-placement must snap it onto the
                     * chemical face (south), not the clicked face, exactly as it would onto an inventory.
                     */
                    final Player player = helper.makeMockPlayer(GameType.CREATIVE);
                    final BlockPos absCable = world.absolute(cablePos);
                    player.setPos(absCable.getX() + 0.5, absCable.getY() + 1.0, absCable.getZ() + 0.5);
                    final ItemStack busItem = new ItemStack(ComputingModule.IMPORT_BUS_ITEM.get());
                    final Vec3 hit = new Vec3(absCable.getX() + 0.5, absCable.getY() + 1.0, absCable.getZ() + 0.5);
                    final BlockHitResult where = new BlockHitResult(hit, Direction.UP, absCable, false);
                    ComputingModule.IMPORT_BUS_ITEM.get().useOn(new net.minecraft.world.item.context.UseOnContext(
                            helper.getLevel(), player, InteractionHand.MAIN_HAND, busItem, where));

                    helper.assertTrue(cable.getPart(Direction.SOUTH) instanceof ImportBusPart,
                            "the bus must auto-place on the chemical face; part there is " + cable.partType(Direction.SOUTH));
                    helper.assertTrue(!(cable.getPart(Direction.UP) instanceof ImportBusPart),
                            "the bus must not land on the empty clicked face");
                })
                .thenSucceed();
    }
}
