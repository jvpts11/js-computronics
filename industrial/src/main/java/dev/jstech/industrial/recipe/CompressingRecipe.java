/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Industrial.
 */
package dev.jstech.industrial.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.jstech.industrial.IndustrialModule;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * A compressing recipe for the Compressor: one input item is pressed into the result after
 * {@code processingTime} ticks (default: {@value #DEFAULT_PROCESSING_TIME}).
 */
public record CompressingRecipe(Ingredient ingredient, ItemStack result, int processingTime)
        implements Recipe<SingleRecipeInput> {

    public static final int DEFAULT_PROCESSING_TIME = 120;

    @Override
    public boolean matches(final SingleRecipeInput input, final Level level) {
        return ingredient.test(input.item());
    }

    @Override
    public ItemStack assemble(final SingleRecipeInput input, final HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(final int width, final int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(final HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return IndustrialModule.COMPRESSING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return IndustrialModule.COMPRESSING_TYPE.get();
    }

    /**
     * Codec-based serializer (persistence + network) for {@link CompressingRecipe}.
     */
    public static final class Serializer implements RecipeSerializer<CompressingRecipe> {

        public static final MapCodec<CompressingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(CompressingRecipe::ingredient),
                        ItemStack.CODEC.fieldOf("result").forGetter(CompressingRecipe::result),
                        com.mojang.serialization.Codec.INT
                                .optionalFieldOf("processing_time", DEFAULT_PROCESSING_TIME)
                                .forGetter(CompressingRecipe::processingTime)
                ).apply(instance, CompressingRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, CompressingRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        Ingredient.CONTENTS_STREAM_CODEC, CompressingRecipe::ingredient,
                        ItemStack.STREAM_CODEC, CompressingRecipe::result,
                        ByteBufCodecs.VAR_INT, CompressingRecipe::processingTime,
                        CompressingRecipe::new);

        @Override
        public MapCodec<CompressingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CompressingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
