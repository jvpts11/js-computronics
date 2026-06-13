/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.util;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;

/**
 * Ticker plumbing shared by every {@code EntityBlock#getTicker}: a block is handed the world's {@link BlockEntityType} and must return a ticker only when it matches the one type it serves. This is the vanilla {@code BaseEntityBlock.createTickerHelper} pattern, kept in one place so every block stops copying it.
 */
public final class BlockEntityTickers {

    private BlockEntityTickers() {
    }

    /**
     * Returns {@code ticker} typed for the given block entity type when it is the {@code expected} one, or {@code null} otherwise. The unchecked cast is safe because the equality check guarantees {@code A} is {@code E}.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <A extends BlockEntity, E extends BlockEntity> BlockEntityTicker<A> create(
            final BlockEntityType<A> given, final BlockEntityType<E> expected,
            final BlockEntityTicker<? super E> ticker) {
        return expected == given ? (BlockEntityTicker<A>) ticker : null;
    }
}
