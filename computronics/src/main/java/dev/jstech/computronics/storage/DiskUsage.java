/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A drive's usage summary, carried on the item so the hand, the creative tab and the client can read it
 * without the volume store: the stored weight, and what that weight is made of — items by the piece, fluids
 * and chemicals by the millibucket — so a drive full of water never reads as "22 944 items".
 */
public record DiskUsage(long usedWeight, long items, long fluidMb, long chemicalMb, int types) {

    public static final DiskUsage EMPTY = new DiskUsage(0L, 0L, 0L, 0L, 0);

    public static final Codec<DiskUsage> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            Codec.LONG.fieldOf("used_weight").forGetter(DiskUsage::usedWeight),
            Codec.LONG.fieldOf("items").forGetter(DiskUsage::items),
            Codec.LONG.fieldOf("fluid_mb").forGetter(DiskUsage::fluidMb),
            Codec.LONG.fieldOf("chemical_mb").forGetter(DiskUsage::chemicalMb),
            Codec.INT.fieldOf("types").forGetter(DiskUsage::types)
    ).apply(builder, DiskUsage::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, DiskUsage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, DiskUsage::usedWeight,
            ByteBufCodecs.VAR_LONG, DiskUsage::items,
            ByteBufCodecs.VAR_LONG, DiskUsage::fluidMb,
            ByteBufCodecs.VAR_LONG, DiskUsage::chemicalMb,
            ByteBufCodecs.VAR_INT, DiskUsage::types,
            DiskUsage::new);

    /** Read off the volume's running totals: a summary is rewritten on every write and must stay O(1). */
    public static DiskUsage of(final StorageVolume volume) {
        return new DiskUsage(volume.usedWeight(), volume.itemUnits(), volume.fluidUnits(), volume.chemicalUnits(),
                volume.types());
    }

    public boolean isEmpty() {
        return usedWeight <= 0L && types == 0;
    }

    /**
     * What a drive of {@code capacityItems} can hold, in both units its data comes in. Chemicals are named
     * only while a chemical mod is present: without one there is no such data to hold.
     */
    public static String capacityLine(final long capacityItems) {
        return "Holds " + group(capacityItems) + " item" + (capacityItems == 1L ? "" : "s") + ", or "
                + group(capacityItems * StorageKey.MB_EQ_PER_ITEM)
                + (ChemicalBridges.anyRegistered() ? " mB of fluid or chemical" : " mB of fluid");
    }

    /**
     * What is on the drive and how much of a drive of {@code capacityItems} that takes, e.g.
     * {@code "Used 28%: 12 items, 22,944 mB of fluid across 2 types"}.
     */
    public String summary(final long capacityItems) {
        final List<String> parts = new ArrayList<>(3);
        if (items > 0L) {
            parts.add(group(items) + " item" + (items == 1L ? "" : "s"));
        }
        if (fluidMb > 0L) {
            parts.add(group(fluidMb) + " mB of fluid");
        }
        if (chemicalMb > 0L) {
            parts.add(group(chemicalMb) + " mB of chemical");
        }
        final StringBuilder out = new StringBuilder("Used");
        final long capacityWeight = capacityItems * StorageKey.MB_EQ_PER_ITEM;
        if (capacityWeight > 0L) {
            out.append(' ').append(Math.min(100L, usedWeight * 100L / capacityWeight)).append('%');
        }
        out.append(": ").append(String.join(", ", parts));
        out.append(" across ").append(types).append(" type").append(types == 1 ? "" : "s");
        return out.toString();
    }

    private static String group(final long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }
}
