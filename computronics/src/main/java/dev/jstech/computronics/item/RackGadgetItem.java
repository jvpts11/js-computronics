/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.item;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.rack.RaidMode;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A gadget that goes into one of a bay's gadget slots on the rack's front panel, changing how that
 * bay's drives behave: a RAID Controller aggregates them into one logical volume, a Cache Card cuts
 * read latency. A gadget serves the machine mounted in its row and nothing else.
 */
public class RackGadgetItem extends Item {

    /** What a rack gadget does for its bay. */
    public enum Kind {
        /** Aggregates the bay's drives into a single logical volume (mode set on the gadget). */
        RAID_CONTROLLER,
        /** Cuts the read latency of queries served by the bay's drives. */
        CACHE_CARD
    }

    /** The read-latency cut a Cache Card gives its bay, in percent. */
    public static final int CACHE_LATENCY_CUT_PERCENT = 25;

    private final Kind kind;

    public RackGadgetItem(final Properties properties, final Kind kind) {
        super(properties.stacksTo(1));
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    /** The gadget kind of a stack, or null when the stack is not a rack gadget. */
    @Nullable
    public static Kind kindOf(final ItemStack stack) {
        return stack.getItem() instanceof RackGadgetItem gadget ? gadget.kind() : null;
    }

    /** The RAID mode configured on a controller stack ({@link RaidMode#NONE} when unset). */
    public static RaidMode raidMode(final ItemStack stack) {
        if (kindOf(stack) != Kind.RAID_CONTROLLER) {
            return RaidMode.NONE;
        }
        final String stored = stack.get(ComputingModule.RAID_MODE.get());
        if (stored == null) {
            return RaidMode.NONE;
        }
        try {
            return RaidMode.valueOf(stored);
        } catch (final IllegalArgumentException ignored) {
            return RaidMode.NONE; // an unknown mode degrades to independent volumes
        }
    }

    /** Writes a RAID mode onto a controller stack (a no-op for any other item). */
    public static void setRaidMode(final ItemStack stack, final RaidMode mode) {
        if (kindOf(stack) == Kind.RAID_CONTROLLER) {
            stack.set(ComputingModule.RAID_MODE.get(), mode.name());
        }
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        tooltip.add(Component.translatable("item.jsc." + (kind == Kind.RAID_CONTROLLER
                ? "raid_controller" : "cache_card") + ".tooltip").withStyle(ChatFormatting.GRAY));
        if (kind == Kind.RAID_CONTROLLER) {
            final RaidMode mode = raidMode(stack);
            tooltip.add(Component.literal(mode == RaidMode.NONE
                            ? "Unconfigured - the bay's drives stay independent"
                            : mode.name() + " - needs " + mode.minDrives() + "+ drives")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.literal("-" + CACHE_LATENCY_CUT_PERCENT + "% read latency in its bay")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
