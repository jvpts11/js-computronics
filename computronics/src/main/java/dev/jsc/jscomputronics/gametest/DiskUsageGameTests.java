/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.gametest;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.common.hardware.DiskSize;
import dev.jsc.jscomputronics.common.hardware.StorageTier;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.storage.DiskUsage;
import dev.jsc.jscomputronics.module.computing.storage.DriveVolumes;
import dev.jsc.jscomputronics.module.computing.storage.ServerStore;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import dev.jsc.jscomputronics.module.computing.storage.StorageVolume;
import dev.jsc.jscomputronics.testkit.TestWorldBuilder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A drive's capacity is one budget in one unit of weight — an item weighs 1 000 mB-eq, a millibucket of
 * fluid or chemical weighs 1 — and its usage summary reports what that weight is made of in each kind's own
 * unit. A store never writes past its capacity, whatever the kind.
 */
@GameTestHolder(JsComputronics.MODID)
@PrefixGameTestTemplate(false)
public final class DiskUsageGameTests {

    private DiskUsageGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;

    private static StorageKey water() {
        return StorageKey.of(new FluidStack(Fluids.WATER, 1));
    }

    private static StorageKey oxygen() {
        return StorageKey.chemical(ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen"));
    }

    @GameTest(template = ARENA)
    public static void of_countsEachKindInItsOwnUnit(final GameTestHelper helper) {
        final ItemStack disk = new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500));
        final StorageVolume volume = DriveVolumes.of(disk);
        volume.add(StorageKey.of(Items.DIRT), 12);
        volume.add(water(), 22_944);
        volume.add(oxygen(), 500);
        DriveVolumes.refreshUsage(disk, volume);
        final DiskUsage usage = DriveVolumes.usage(disk);
        helper.assertTrue(usage.items() == 12, "12 items; got " + usage.items());
        helper.assertTrue(usage.fluidMb() == 22_944, "22 944 mB of fluid; got " + usage.fluidMb());
        helper.assertTrue(usage.chemicalMb() == 500, "500 mB of chemical; got " + usage.chemicalMb());
        helper.assertTrue(usage.types() == 3, "three types; got " + usage.types());
        helper.assertTrue(usage.usedWeight() == 12_000 + 22_944 + 500,
                "the weight is 1 000 per item and 1 per mB; got " + usage.usedWeight());
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void summary_namesFluidInMillibucketsNeverInItems(final GameTestHelper helper) {
        final DiskUsage water = new DiskUsage(22_944, 0, 22_944, 0, 1);
        final String line = water.summary(80);
        helper.assertTrue(line.equals("Used 28%: 22,944 mB of fluid across 1 type"), "got: " + line);
        final DiskUsage mixed = new DiskUsage(12_000 + 22_944 + 500, 12, 22_944, 500, 3);
        final String both = mixed.summary(80);
        helper.assertTrue(both.equals("Used 44%: 12 items, 22,944 mB of fluid, 500 mB of chemical across 3 types"), "got: " + both);
        // Chemicals are named only while a chemical mod (Mekanism on the dev runtime) is present.
        final String capacity = "Holds 80 items, or 80,000 mB of fluid"
                + (dev.jsc.jscomputronics.module.computing.storage.ChemicalBridges.anyRegistered() ? " or chemical" : "");
        helper.assertTrue(DiskUsage.capacityLine(80).equals(capacity), "got: " + DiskUsage.capacityLine(80));
        helper.assertTrue(DiskUsage.EMPTY.isEmpty() && !water.isEmpty(), "emptiness follows the weight");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void serverStore_neverWritesPastItsCapacityInAnyKind(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final ServerStore store = net.rack().getServerStorage(0);
                    final long freeItems = store.free();
                    helper.assertTrue(freeItems > 0L, "the seeded server has room; free " + freeItems);
                    // Items: the store takes exactly what is free and not one more.
                    final long stored = store.insert(StorageKey.of(Items.DIRT), freeItems + 5_000L);
                    helper.assertTrue(stored == freeItems, "a full store takes only its free items; took " + stored + " of " + freeItems);
                    helper.assertTrue(store.freeWeight() == 0L, "nothing left; free weight " + store.freeWeight());
                    helper.assertTrue(store.insert(water(), 1_000L) == 0L, "a full store refuses fluid too");
                    // Make one item's worth of room: exactly a bucket of fluid fits, no more.
                    helper.assertTrue(store.extract(StorageKey.of(Items.DIRT), 1L) == 1L, "one item out");
                    helper.assertTrue(store.freeWeight() == StorageKey.MB_EQ_PER_ITEM, "one item's worth free; " + store.freeWeight());
                    final long fluid = store.insert(water(), 5_000L);
                    helper.assertTrue(fluid == 1_000L, "one item's worth of room is one bucket; took " + fluid);
                    helper.assertTrue(store.freeWeight() == 0L, "and the store is full again; free " + store.freeWeight());
                })
                .thenSucceed();
    }
}
