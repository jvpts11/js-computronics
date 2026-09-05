/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.block.part;

import dev.jsc.jscomputronics.module.computing.block.DataCableBlock;
import dev.jsc.jscomputronics.module.computing.blockentity.DataCableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import org.jetbrains.annotations.Nullable;

/**
 * A part item: right-clicking a data cable attaches a {@link CablePart} of this item's type to one of the cable's faces, like an AE2 bus snapping onto a cable.
 */
public class CablePartItem extends Item {

    private final CablePartType type;

    public CablePartItem(final Properties properties, final CablePartType type) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        final Level level = context.getLevel();
        final BlockPos clicked = context.getClickedPos();

        // Clicked a data cable directly: mount on it (clicked face, snapping to an adjacent
        // inventory when there is exactly one).
        if (level.getBlockState(clicked).getBlock() instanceof DataCableBlock
                && level.getBlockEntity(clicked) instanceof DataCableBlockEntity cable) {
            return place(context, cable, chooseFace(cable, context.getClickedFace()));
        }

        // Clicked another block (e.g. a chest): mount on an adjacent cable so the bus faces that
        // block. Prefer the cable behind the clicked face, then any other adjacent cable.
        final Direction behind = context.getClickedFace().getOpposite();
        final InteractionResult viaBehind = tryAdjacentCable(context, clicked, behind);
        if (viaBehind != InteractionResult.PASS) {
            return viaBehind;
        }
        for (final Direction direction : Direction.values()) {
            if (direction != behind) {
                final InteractionResult result = tryAdjacentCable(context, clicked, direction);
                if (result != InteractionResult.PASS) {
                    return result;
                }
            }
        }
        return InteractionResult.PASS;
    }

    private InteractionResult tryAdjacentCable(final UseOnContext context, final BlockPos clicked,
                                               final Direction toCable) {
        final Level level = context.getLevel();
        final BlockPos cablePos = clicked.relative(toCable);
        if (level.getBlockState(cablePos).getBlock() instanceof DataCableBlock
                && level.getBlockEntity(cablePos) instanceof DataCableBlockEntity cable
                && !cable.hasPart(toCable.getOpposite())) {
            // The cable's face pointing back at the clicked block is toCable's opposite.
            return place(context, cable, toCable.getOpposite());
        }
        return InteractionResult.PASS;
    }

    private InteractionResult place(final UseOnContext context, final DataCableBlockEntity cable,
                                    @Nullable final Direction face) {
        if (face == null) {
            return InteractionResult.PASS; // every candidate face is taken
        }
        final Level level = context.getLevel();
        // Crafting buses belong on crafting cables and storage buses on data cables. A storage bus on a
        // crafting cable would autonomously move items the crafting engine is accounting for (and vice versa
        // the crafting buses are inert), so a mismatched mount is refused with a hint instead.
        final boolean craftingPart = type == CablePartType.INPUT || type == CablePartType.RECEIVING;
        final boolean craftingCable =
                cable.tier() == dev.jstech.core.network.DataTier.CRAFTING;
        if (craftingPart != craftingCable) {
            if (!level.isClientSide() && context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.literal(
                        craftingPart ? "Crafting buses mount on crafting cables"
                                : "Storage buses mount on data cables"), true);
            }
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            cable.addPart(face, type.create());
            level.playSound(null, cable.getBlockPos(), SoundType.METAL.getPlaceSound(),
                    SoundSource.BLOCKS, 1.0F, 0.8F);
            if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Nullable
    private static Direction chooseFace(final DataCableBlockEntity cable, final Direction clicked) {
        if (!cable.hasPart(clicked) && !cable.neighborPort(clicked).isEmpty()) {
            return clicked;
        }
        Direction firstFree = null;
        Direction dataFace = null;
        int dataCount = 0;
        for (final Direction direction : Direction.values()) {
            if (cable.hasPart(direction)) {
                continue;
            }
            if (firstFree == null) {
                firstFree = direction;
            }
            // Any face touching a block that offers data — items, fluids, or chemicals — is a candidate, so a
            // fluid- or chemical-only machine face (e.g. a chemical tank side) snaps the bus the same way an
            // inventory does, now that buses carry every kind of data.
            if (!cable.neighborPort(direction).isEmpty()) {
                dataFace = direction;
                dataCount++;
            }
        }
        if (dataCount == 1) {
            return dataFace;
        }
        if (!cable.hasPart(clicked)) {
            return clicked;
        }
        return firstFree;
    }
}
