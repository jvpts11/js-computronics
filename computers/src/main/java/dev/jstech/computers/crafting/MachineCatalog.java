/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import net.minecraft.world.level.block.entity.DaylightDetectorBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.SculkCatalystBlockEntity;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Enumerates the blocks that count as processing machines for the Pattern Encoder's machine picker. A block
 * qualifies when it has a block entity that is not one of the known vanilla non-machine types (decoration,
 * structure, plain storage, world mechanics). Mod blocks are never excluded, since a mod's block-entity blocks are
 * its machines. Kept out of the client screen so a GameTest can pin the catalog's contents.
 */
public final class MachineCatalog {

    private static final Set<Class<?>> NON_MACHINE_BES = Set.of(
            SignBlockEntity.class,
            BedBlockEntity.class,
            BannerBlockEntity.class,
            SkullBlockEntity.class,
            BeaconBlockEntity.class,
            ConduitBlockEntity.class,
            BellBlockEntity.class,
            EnchantingTableBlockEntity.class,
            TheEndGatewayBlockEntity.class,
            TheEndPortalBlockEntity.class,
            SpawnerBlockEntity.class,
            StructureBlockEntity.class,
            JigsawBlockEntity.class,
            CommandBlockEntity.class,
            ComparatorBlockEntity.class,
            DaylightDetectorBlockEntity.class,
            JukeboxBlockEntity.class,
            LecternBlockEntity.class,
            SculkSensorBlockEntity.class,
            SculkShriekerBlockEntity.class,
            SculkCatalystBlockEntity.class,
            BrushableBlockEntity.class,
            DecoratedPotBlockEntity.class,
            ChiseledBookShelfBlockEntity.class,
            ChestBlockEntity.class,
            EnderChestBlockEntity.class,
            BarrelBlockEntity.class,
            ShulkerBoxBlockEntity.class);

    private MachineCatalog() {
    }

    /** Whether the block qualifies as a machine for the picker. */
    public static boolean isMachineLike(final Block block) {
        if (!(block instanceof EntityBlock eb)) {
            return false;
        }
        final BlockEntity be;
        try {
            be = eb.newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
        } catch (final Exception ignored) {
            return false;
        }
        if (be == null) {
            return false;
        }
        for (final Class<?> excluded : NON_MACHINE_BES) {
            if (excluded.isInstance(be)) {
                return false;
            }
        }
        return true;
    }

    /** Every installed machine-like block, as sorted registry ids. */
    public static List<ResourceLocation> machineIds() {
        final List<ResourceLocation> ids = new ArrayList<>();
        for (final Block block : BuiltInRegistries.BLOCK) {
            if (isMachineLike(block)) {
                ids.add(BuiltInRegistries.BLOCK.getKey(block));
            }
        }
        ids.sort(Comparator.comparing(ResourceLocation::toString));
        return ids;
    }
}
