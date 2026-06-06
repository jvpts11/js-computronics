/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * One row in the network item view: an icon stack (count 1, just for display) and the true total held across the network, which may exceed a stack.
 */
public record NetworkItemEntry(ItemStack icon, long total) {

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkItemEntry> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.STREAM_CODEC, NetworkItemEntry::icon,
                    ByteBufCodecs.VAR_LONG, NetworkItemEntry::total,
                    NetworkItemEntry::new);
}
