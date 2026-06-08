/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * The identity of a stored item type, preserving its data components (enchantments, custom name, damage, dyes, …) — so the network is NOT a flattening "everything is the bare item" store: an enchanted sword that enters the network is the same enchanted sword when it comes back out.
 */
public final class StorageKey {

    private final ItemStack prototype; // always count 1; carries the components
    private final int hash;

    private StorageKey(final ItemStack prototype) {
        this.prototype = prototype;
        this.hash = Objects.hash(prototype.getItem(), prototype.getComponents());
    }

    public static StorageKey of(final ItemStack stack) {
        return new StorageKey(stack.copyWithCount(1));
    }

    public static StorageKey of(final Item item) {
        return new StorageKey(new ItemStack(item));
    }

    public Item item() {
        return prototype.getItem();
    }

    public ItemStack stack(final int count) {
        return prototype.copyWithCount(count);
    }

    public ItemStack prototype() {
        return prototype;
    }

    public boolean hasComponents() {
        return !prototype.getComponentsPatch().isEmpty();
    }

    @Override
    public boolean equals(final Object other) {
        return this == other
                || (other instanceof StorageKey key && ItemStack.isSameItemSameComponents(prototype, key.prototype));
    }

    @Override
    public int hashCode() {
        return hash;
    }

    public static final Codec<StorageKey> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            ItemStack.CODEC.fieldOf("item").forGetter(StorageKey::prototype)
    ).apply(builder, StorageKey::of));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageKey> STREAM_CODEC =
            ItemStack.STREAM_CODEC.map(StorageKey::of, StorageKey::prototype);
}
