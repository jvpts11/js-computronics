/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Industrial.
 */
package dev.jstech.industrial;

import dev.jstech.core.material.MaterialForm;
import dev.jstech.core.material.MaterialItems;
import dev.jstech.core.material.ModMaterial;
import dev.jstech.industrial.block.CoalGeneratorBlock;
import dev.jstech.industrial.block.CompressorBlock;
import dev.jstech.industrial.block.ElectricFurnaceBlock;
import dev.jstech.industrial.block.MaceratorBlock;
import dev.jstech.industrial.blockentity.CoalGeneratorBlockEntity;
import dev.jstech.industrial.blockentity.CompressorBlockEntity;
import dev.jstech.industrial.blockentity.ElectricFurnaceBlockEntity;
import dev.jstech.industrial.blockentity.MaceratorBlockEntity;
import dev.jstech.industrial.menu.CoalGeneratorMenu;
import dev.jstech.industrial.menu.CompressorMenu;
import dev.jstech.industrial.menu.ElectricFurnaceMenu;
import dev.jstech.industrial.menu.MaceratorMenu;
import dev.jstech.industrial.recipe.CompressingRecipe;
import dev.jstech.industrial.recipe.MaceratingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
 * Everything the industrial mod registers: its machines, their block entities, recipes, menus and its
 * creative tab.
 */
public final class IndustrialModule {

    private IndustrialModule() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(JsIndustrial.MODID);

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(JsIndustrial.MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, JsIndustrial.MODID);

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, JsIndustrial.MODID);

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, JsIndustrial.MODID);

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, JsIndustrial.MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, JsIndustrial.MODID);

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

    public static final DeferredBlock<CompressorBlock> COMPRESSOR = BLOCKS.register(
            "compressor", () -> new CompressorBlock(machineProperties()));

    public static final DeferredItem<BlockItem> COMPRESSOR_ITEM = ITEMS.register(
            "compressor", () -> new BlockItem(COMPRESSOR.get(), new Item.Properties()));

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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CompressorBlockEntity>> COMPRESSOR_BE =
            BLOCK_ENTITIES.register("compressor",
                    () -> BlockEntityType.Builder.of(CompressorBlockEntity::new, COMPRESSOR.get()).build(null));

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

    public static final DeferredHolder<RecipeType<?>, RecipeType<CompressingRecipe>> COMPRESSING_TYPE =
            RECIPE_TYPES.register("compressing", () -> new RecipeType<CompressingRecipe>() {
                @Override
                public String toString() {
                    return "compressing";
                }
            });

    public static final DeferredHolder<RecipeSerializer<?>, CompressingRecipe.Serializer> COMPRESSING_SERIALIZER =
            RECIPE_SERIALIZERS.register("compressing", CompressingRecipe.Serializer::new);

    // Menus

    public static final DeferredHolder<MenuType<?>, MenuType<MaceratorMenu>> MACERATOR_MENU =
            MENUS.register("macerator", () -> IMenuTypeExtension.create(MaceratorMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<CoalGeneratorMenu>> COAL_GENERATOR_MENU =
            MENUS.register("coal_generator", () -> IMenuTypeExtension.create(CoalGeneratorMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ElectricFurnaceMenu>> ELECTRIC_FURNACE_MENU =
            MENUS.register("electric_furnace", () -> IMenuTypeExtension.create(ElectricFurnaceMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<CompressorMenu>> COMPRESSOR_MENU =
            MENUS.register("compressor", () -> IMenuTypeExtension.create(CompressorMenu::new));

    // Creative tab

    /** The mod's tab: its machines, then the core's material items, which have no tab of their own. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> INDUSTRIAL_TAB =
            CREATIVE_MODE_TABS.register("industrial", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.jsindustrial.industrial"))
                    .icon(() -> new ItemStack(MACERATOR_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(MACERATOR_ITEM.get());
                        output.accept(ELECTRIC_FURNACE_ITEM.get());
                        output.accept(COMPRESSOR_ITEM.get());
                        output.accept(COAL_GENERATOR_ITEM.get());
                        for (final ModMaterial material : ModMaterial.values()) {
                            for (final MaterialForm form : material.activeModForms()) {
                                output.accept(MaterialItems.get(material, form).get());
                            }
                        }
                    })
                    .build());

    public static void register(final IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        MENUS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
