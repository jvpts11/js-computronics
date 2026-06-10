/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Objects;

/**
 * The identity of one stored DATA type — an item OR a fluid — preserving its data components.
 */
public final class StorageKey {

    public static final long MB_EQ_PER_ITEM = 1000L;

    private final ItemStack itemPrototype;   // count 1, components intact; non-empty iff this is an item key
    private final FluidStack fluidPrototype; // amount 1, components intact; non-empty iff this is a fluid key
    private final boolean fluid;
    private final int hash;

    private StorageKey(final ItemStack itemPrototype, final FluidStack fluidPrototype, final boolean fluid) {
        this.itemPrototype = itemPrototype;
        this.fluidPrototype = fluidPrototype;
        this.fluid = fluid;
        this.hash = fluid
                ? Objects.hash(true, fluidPrototype.getFluid(), fluidPrototype.getComponents())
                : Objects.hash(false, itemPrototype.getItem(), itemPrototype.getComponents());
    }

    public static StorageKey of(final ItemStack stack) {
        return new StorageKey(stack.copyWithCount(1), FluidStack.EMPTY, false);
    }

    public static StorageKey of(final Item item) {
        return new StorageKey(new ItemStack(item), FluidStack.EMPTY, false);
    }

    public static StorageKey of(final FluidStack fluid) {
        return new StorageKey(ItemStack.EMPTY, fluid.copyWithAmount(1), true);
    }

    public boolean isFluid() {
        return fluid;
    }

    public long weight(final long quantity) {
        return fluid ? quantity : quantity * MB_EQ_PER_ITEM;
    }

    public ItemStack stack(final int count) {
        return fluid ? ItemStack.EMPTY : itemPrototype.copyWithCount(count);
    }

    public FluidStack fluidStack(final int amount) {
        return fluid ? fluidPrototype.copyWithAmount(amount) : FluidStack.EMPTY;
    }

    public Item item() {
        return fluid ? Items.AIR : itemPrototype.getItem();
    }

    public ItemStack prototype() {
        return itemPrototype;
    }

    public FluidStack fluidPrototype() {
        return fluidPrototype;
    }

    public Component displayName() {
        return fluid ? fluidPrototype.getHoverName() : itemPrototype.getHoverName();
    }

    public int batch() {
        return fluid ? 1000 : Math.max(1, itemPrototype.getMaxStackSize());
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StorageKey key) || key.fluid != fluid) {
            return false;
        }
        return fluid
                ? FluidStack.isSameFluidSameComponents(fluidPrototype, key.fluidPrototype)
                : ItemStack.isSameItemSameComponents(itemPrototype, key.itemPrototype);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    public static final Codec<StorageKey> CODEC = Codec.either(ItemStack.CODEC, FluidStack.CODEC).xmap(
            either -> either.map(StorageKey::of, StorageKey::of),
            key -> key.fluid
                    ? com.mojang.datafixers.util.Either.right(key.fluidStack(1))
                    : com.mojang.datafixers.util.Either.left(key.stack(1)));

    public DataResult<StorageKey> validated() {
        if (!fluid && itemPrototype.isEmpty()) {
            return DataResult.error(() -> "empty item storage key");
        }
        return DataResult.success(this);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageKey> STREAM_CODEC = StreamCodec.of(
            (buf, key) -> {
                buf.writeBoolean(key.fluid);
                if (key.fluid) {
                    FluidStack.STREAM_CODEC.encode(buf, key.fluidStack(1));
                } else {
                    ItemStack.STREAM_CODEC.encode(buf, key.stack(1));
                }
            },
            buf -> buf.readBoolean()
                    ? StorageKey.of(FluidStack.STREAM_CODEC.decode(buf))
                    : StorageKey.of(ItemStack.STREAM_CODEC.decode(buf)));
}
