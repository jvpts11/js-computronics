/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.block.part.ExportBusPart;
import dev.jstech.computronics.block.part.ImportBusPart;
import dev.jstech.computronics.blockentity.DataCableBlockEntity;
import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.operation.NetworkStorage;
import dev.jstech.tests.JsTests;
import dev.jstech.tests.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Import and export buses moving a lot of stock in and out at once, with the one invariant that matters for a
 * base that churns storage: nothing is created or destroyed. Each item flows out of a source barrel into the
 * network through an Import Bus and back out to a sink barrel through an Export Bus, all in parallel; the sum of
 * an item across its source barrel, the network and its sink barrel must always equal what was seeded.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class StorageBusStressGameTests {

    private StorageBusStressGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 6;
    private static final int PER_ITEM = 64;

    private static final Item[] ITEMS = {
            Items.COBBLESTONE, Items.DIRT, Items.STONE, Items.OAK_LOG,
            Items.IRON_INGOT, Items.GOLD_INGOT, Items.REDSTONE, Items.COAL};

    private static long inContainer(final GameTestHelper helper, final BlockPos pos, final Item item) {
        long total = 0L;
        if (helper.getBlockEntity(pos) instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (container.getItem(i).is(item)) {
                    total += container.getItem(i).getCount();
                }
            }
        }
        return total;
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void busesChurnStockConservingEveryItem(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final MainframeBlockEntity mainframe = world.placeRunningMainframe(new BlockPos(1, 2, 2));
        // A cable spine east of the Mainframe, a rack for storage beside its first segment.
        for (int i = 0; i < ITEMS.length; i++) {
            world.setBlock(new BlockPos(2 + i, 2, 2), ComputingModule.HBW_CABLE.get());
        }
        world.placeSeededRack(new BlockPos(2, 2, 1));

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    for (int i = 0; i < ITEMS.length; i++) {
                        final Item item = ITEMS[i];
                        final BlockPos cablePos = new BlockPos(2 + i, 2, 2);
                        final BlockPos source = new BlockPos(2 + i, 3, 2); // above: the Import Bus pulls from here
                        final BlockPos sink = new BlockPos(2 + i, 1, 2);   // below: the Export Bus pushes to here
                        world.setBlock(source, Blocks.BARREL);
                        world.setBlock(sink, Blocks.BARREL);
                        if (helper.getBlockEntity(cablePos) instanceof DataCableBlockEntity cable) {
                            final ImportBusPart in = new ImportBusPart();
                            cable.addPart(Direction.UP, in);
                            in.setFilter(new ItemStack(item));
                            final ExportBusPart out = new ExportBusPart();
                            cable.addPart(Direction.DOWN, out);
                            out.setFilter(new ItemStack(item));
                        }
                        if (helper.getBlockEntity(source) instanceof Container container) {
                            container.setItem(0, new ItemStack(item, PER_ITEM));
                        }
                    }
                })
                // Let the buses churn, checking conservation the whole way: at no tick may the closed system hold
                // more or less of any item than was seeded.
                .thenWaitUntil(() -> {
                    final NetworkStorage storage = NetworkStorage.of(helper.getLevel(), mainframe.networkUuid());
                    long movedToSink = 0L;
                    for (int i = 0; i < ITEMS.length; i++) {
                        final Item item = ITEMS[i];
                        final long src = inContainer(helper, new BlockPos(2 + i, 3, 2), item);
                        final long net = storage.count(item);
                        final long snk = inContainer(helper, new BlockPos(2 + i, 1, 2), item);
                        final long total = src + net + snk;
                        if (total != PER_ITEM) {
                            throw new GameTestAssertException(item + ": closed-system total is " + total
                                    + ", seeded " + PER_ITEM + " (src=" + src + " net=" + net + " sink=" + snk + ")");
                        }
                        movedToSink += snk;
                    }
                    // Not done until every item has fully flowed source -> network -> sink.
                    if (movedToSink < (long) ITEMS.length * PER_ITEM) {
                        throw new GameTestAssertException("still churning; " + movedToSink + "/"
                                + ((long) ITEMS.length * PER_ITEM) + " reached the sinks");
                    }
                })
                .thenExecute(() -> {
                    final int dropped = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            net.minecraft.world.phys.AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(0, 0, 0)),
                                    helper.absolutePos(new BlockPos(16, 8, 16)))).size();
                    helper.assertTrue(dropped == 0, dropped + " items leaked to the world during the bus churn");
                })
                .thenSucceed();
    }
}
