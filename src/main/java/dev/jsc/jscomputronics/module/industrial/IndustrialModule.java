/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.industrial;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.industrial.block.CoalGeneratorBlock;
import dev.jsc.jscomputronics.module.industrial.block.ElectricFurnaceBlock;
import dev.jsc.jscomputronics.module.industrial.block.MaceratorBlock;
import dev.jsc.jscomputronics.module.industrial.blockentity.CoalGeneratorBlockEntity;
import dev.jsc.jscomputronics.module.industrial.blockentity.ElectricFurnaceBlockEntity;
import dev.jsc.jscomputronics.module.industrial.blockentity.MaceratorBlockEntity;
import dev.jsc.jscomputronics.module.industrial.menu.CoalGeneratorMenu;
import dev.jsc.jscomputronics.module.industrial.menu.ElectricFurnaceMenu;
import dev.jsc.jscomputronics.module.industrial.menu.MaceratorMenu;
import dev.jsc.jscomputronics.module.industrial.recipe.MaceratingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registration entry point for the Industrial module.
 */
public final class IndustrialModule {

    private IndustrialModule() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(JsComputronics.MODID);

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(JsComputronics.MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, JsComputronics.MODID);

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, JsComputronics.MODID);

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, JsComputronics.MODID);

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, JsComputronics.MODID);

    private static BlockBehaviour.Properties machineProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.5F)
                .requiresCorrectToolForDrops();
    }

    // Blocks & items

    public static final DeferredBlock<MaceratorBlock> MACERATOR = BLOCKS.register(
            "macerator", () -> new MaceratorBlock(machineProperties()));

    public static final DeferredItem<BlockItem> MACERATOR_ITEM = ITEMS.register(
            "macerator", () -> new BlockItem(MACERATOR.get(), new Item.Properties()));

    public static final DeferredBlock<CoalGeneratorBlock> COAL_GENERATOR = BLOCKS.register(
            "coal_generator", () -> new CoalGeneratorBlock(machineProperties()));

    public static final DeferredItem<BlockItem> COAL_GENERATOR_ITEM = ITEMS.register(
            "coal_generator", () -> new BlockItem(COAL_GENERATOR.get(), new Item.Properties()));

    public static final DeferredBlock<ElectricFurnaceBlock> ELECTRIC_FURNACE = BLOCKS.register(
            "electric_furnace", () -> new ElectricFurnaceBlock(machineProperties()));

    public static final DeferredItem<BlockItem> ELECTRIC_FURNACE_ITEM = ITEMS.register(
            "electric_furnace", () -> new BlockItem(ELECTRIC_FURNACE.get(), new Item.Properties()));

    public static final DeferredItem<Item> IRON_DUST = ITEMS.register(
            "iron_dust", () -> new Item(new Item.Properties()));

    // Block entities

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MaceratorBlockEntity>> MACERATOR_BE =
            BLOCK_ENTITIES.register("macerator",
                    () -> BlockEntityType.Builder.of(MaceratorBlockEntity::new, MACERATOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CoalGeneratorBlockEntity>> COAL_GENERATOR_BE =
            BLOCK_ENTITIES.register("coal_generator",
                    () -> BlockEntityType.Builder.of(CoalGeneratorBlockEntity::new, COAL_GENERATOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectricFurnaceBlockEntity>> ELECTRIC_FURNACE_BE =
            BLOCK_ENTITIES.register("electric_furnace",
                    () -> BlockEntityType.Builder.of(ElectricFurnaceBlockEntity::new, ELECTRIC_FURNACE.get()).build(null));

    // Recipes

    public static final DeferredHolder<RecipeType<?>, RecipeType<MaceratingRecipe>> MACERATING_TYPE =
            RECIPE_TYPES.register("macerating", () -> new RecipeType<MaceratingRecipe>() {
                @Override
                public String toString() {
                    return "macerating";
                }
            });

    public static final DeferredHolder<RecipeSerializer<?>, MaceratingRecipe.Serializer> MACERATING_SERIALIZER =
            RECIPE_SERIALIZERS.register("macerating", MaceratingRecipe.Serializer::new);

    // Menus

    public static final DeferredHolder<MenuType<?>, MenuType<MaceratorMenu>> MACERATOR_MENU =
            MENUS.register("macerator", () -> IMenuTypeExtension.create(MaceratorMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<CoalGeneratorMenu>> COAL_GENERATOR_MENU =
            MENUS.register("coal_generator", () -> IMenuTypeExtension.create(CoalGeneratorMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ElectricFurnaceMenu>> ELECTRIC_FURNACE_MENU =
            MENUS.register("electric_furnace", () -> IMenuTypeExtension.create(ElectricFurnaceMenu::new));

    public static void register(final IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        MENUS.register(modEventBus);
    }
}
