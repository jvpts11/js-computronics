/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.storage;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * The identity of one stored DATA type (an item, a fluid, or a chemical) preserving an item's or fluid's
 * data components. The network makes no distinction between the three: they are stored, indexed, moved and
 * displayed as data. A chemical is known only by its registry id; whatever mod owns it is reached through a
 * {@link IChemicalBridge}.
 */
public final class StorageKey {

    /** Which kind of substance a key names. */
    public enum Kind { ITEM, FLUID, CHEMICAL }

    public static final long MB_EQ_PER_ITEM = 1000L;

    private final Kind kind;
    private final ItemStack itemPrototype;   // count 1, components intact; non-empty iff this is an item key
    private final FluidStack fluidPrototype; // amount 1, components intact; non-empty iff this is a fluid key
    @Nullable
    private final ResourceLocation chemical; // registry id; non-null iff this is a chemical key
    private final int hash;

    private StorageKey(final Kind kind, final ItemStack itemPrototype, final FluidStack fluidPrototype,
                       @Nullable final ResourceLocation chemical) {
        this.kind = kind;
        this.itemPrototype = itemPrototype;
        this.fluidPrototype = fluidPrototype;
        this.chemical = chemical;
        this.hash = switch (kind) {
            case FLUID -> Objects.hash(Kind.FLUID, fluidPrototype.getFluid(), fluidPrototype.getComponents());
            case CHEMICAL -> Objects.hash(Kind.CHEMICAL, chemical);
            case ITEM -> Objects.hash(Kind.ITEM, itemPrototype.getItem(), itemPrototype.getComponents());
        };
    }

    public static StorageKey of(final ItemStack stack) {
        return new StorageKey(Kind.ITEM, stack.copyWithCount(1), FluidStack.EMPTY, null);
    }

    public static StorageKey of(final Item item) {
        return new StorageKey(Kind.ITEM, new ItemStack(item), FluidStack.EMPTY, null);
    }

    public static StorageKey of(final FluidStack fluid) {
        return new StorageKey(Kind.FLUID, ItemStack.EMPTY, fluid.copyWithAmount(1), null);
    }

    /** A chemical key by registry id (e.g. {@code mekanism:oxygen}). */
    public static StorageKey chemical(final ResourceLocation chemical) {
        return new StorageKey(Kind.CHEMICAL, ItemStack.EMPTY, FluidStack.EMPTY, chemical);
    }

    public Kind kind() {
        return kind;
    }

    public boolean isItem() {
        return kind == Kind.ITEM;
    }

    public boolean isFluid() {
        return kind == Kind.FLUID;
    }

    public boolean isChemical() {
        return kind == Kind.CHEMICAL;
    }

    /** Fluids and chemicals are measured in millibuckets; one item weighs {@link #MB_EQ_PER_ITEM}. */
    public long weight(final long quantity) {
        return kind == Kind.ITEM ? quantity * MB_EQ_PER_ITEM : quantity;
    }

    public ItemStack stack(final int count) {
        return kind == Kind.ITEM ? itemPrototype.copyWithCount(count) : ItemStack.EMPTY;
    }

    public FluidStack fluidStack(final int amount) {
        return kind == Kind.FLUID ? fluidPrototype.copyWithAmount(amount) : FluidStack.EMPTY;
    }

    /** The chemical's registry id; null for items and fluids. */
    @Nullable
    public ResourceLocation chemicalId() {
        return chemical;
    }

    public Item item() {
        return kind == Kind.ITEM ? itemPrototype.getItem() : Items.AIR;
    }

    public ItemStack prototype() {
        return itemPrototype;
    }

    public FluidStack fluidPrototype() {
        return fluidPrototype;
    }

    public Component displayName() {
        return switch (kind) {
            case FLUID -> fluidPrototype.getHoverName();
            case CHEMICAL -> ChemicalBridges.displayName(Objects.requireNonNull(chemical));
            case ITEM -> itemPrototype.getHoverName();
        };
    }

    /** The natural transfer batch: a stack of the item, or a bucket (1 000 mB) of a fluid or chemical. */
    public int batch() {
        return kind == Kind.ITEM ? Math.max(1, itemPrototype.getMaxStackSize()) : 1000;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StorageKey key) || key.kind != kind) {
            return false;
        }
        return switch (kind) {
            case FLUID -> FluidStack.isSameFluidSameComponents(fluidPrototype, key.fluidPrototype);
            case CHEMICAL -> Objects.equals(chemical, key.chemical);
            case ITEM -> ItemStack.isSameItemSameComponents(itemPrototype, key.itemPrototype);
        };
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return switch (kind) {
            case FLUID -> "fluid " + fluidPrototype.getFluid();
            case CHEMICAL -> "chemical " + chemical;
            case ITEM -> "item " + itemPrototype.getItem();
        };
    }

    /** A chemical key on disk: {@code {"chemical": "<id>"}}. */
    private static final Codec<StorageKey> CHEMICAL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("chemical").forGetter(key -> Objects.requireNonNull(key.chemical))
    ).apply(instance, StorageKey::chemical));

    /**
     * Items and fluids keep their original shape (the stack itself), so every existing save reads back
     * unchanged; a chemical is the new third alternative.
     */
    public static final Codec<StorageKey> CODEC = Codec.either(
            Codec.either(ItemStack.CODEC, FluidStack.CODEC), CHEMICAL_CODEC).xmap(
            either -> either.map(inner -> inner.map(StorageKey::of, StorageKey::of), key -> key),
            key -> switch (key.kind) {
                case ITEM -> Either.left(Either.left(key.stack(1)));
                case FLUID -> Either.left(Either.right(key.fluidStack(1)));
                case CHEMICAL -> Either.right(key);
            });

    public DataResult<StorageKey> validated() {
        if (kind == Kind.ITEM && itemPrototype.isEmpty()) {
            return DataResult.error(() -> "empty item storage key");
        }
        if (kind == Kind.CHEMICAL && chemical == null) {
            return DataResult.error(() -> "chemical storage key without an id");
        }
        return DataResult.success(this);
    }

    private static final byte WIRE_ITEM = 0;
    private static final byte WIRE_FLUID = 1;
    private static final byte WIRE_CHEMICAL = 2;

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageKey> STREAM_CODEC = StreamCodec.of(
            (buf, key) -> {
                switch (key.kind) {
                    case ITEM -> {
                        buf.writeByte(WIRE_ITEM);
                        ItemStack.STREAM_CODEC.encode(buf, key.stack(1));
                    }
                    case FLUID -> {
                        buf.writeByte(WIRE_FLUID);
                        FluidStack.STREAM_CODEC.encode(buf, key.fluidStack(1));
                    }
                    case CHEMICAL -> {
                        buf.writeByte(WIRE_CHEMICAL);
                        ResourceLocation.STREAM_CODEC.encode(buf, Objects.requireNonNull(key.chemical));
                    }
                }
            },
            buf -> {
                final byte wire = buf.readByte();
                return switch (wire) {
                    case WIRE_FLUID -> StorageKey.of(FluidStack.STREAM_CODEC.decode(buf));
                    case WIRE_CHEMICAL -> StorageKey.chemical(ResourceLocation.STREAM_CODEC.decode(buf));
                    default -> StorageKey.of(ItemStack.STREAM_CODEC.decode(buf));
                };
            });
}
