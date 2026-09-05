/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.item;

import dev.jstech.computronics.rack.MountableRackUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Equipment that serves the rack itself rather than being a computer: the KVM switch that lets one
 * monitor address several machines, the UPS that carries the whole cabinet through an outage, the
 * cooling that buys a dense rack its thermal headroom. Rack units spend the same rack-unit budget
 * the servers do, which is what makes filling a cabinet a set of real trade-offs.
 */
public class RackUnitItem extends Item implements MountableRackUnit {

    /** What a rack unit does for the cabinet. */
    public enum Kind {
        /** Multiplexes the rack's machines onto one linked monitor, as a channel bar. */
        KVM_SWITCH("kvm_switch", 1),
        /** Carries every machine in the rack through a power outage. */
        RACK_UPS("rack_ups", 1),
        /** Active cooling: raises the cabinet's thermal budget. */
        COOLING_UNIT("cooling_unit", 1);

        private final String id;
        private final int heightU;

        Kind(final String id, final int heightU) {
            this.id = id;
            this.heightU = heightU;
        }

        public String id() {
            return id;
        }

        public int heightU() {
            return heightU;
        }
    }

    private final Kind kind;

    public RackUnitItem(final Properties properties, final Kind kind) {
        super(properties.stacksTo(1));
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public int heightU() {
        return kind.heightU();
    }

    /** The rack-unit kind of a stack, or null when the stack is not a rack unit. */
    @Nullable
    public static Kind kindOf(final ItemStack stack) {
        return stack.getItem() instanceof RackUnitItem unit ? unit.kind() : null;
    }

    /** Whether the stack is a rack unit of the given kind. */
    public static boolean is(final ItemStack stack, final Kind kind) {
        return kindOf(stack) == kind;
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        tooltip.add(Component.translatable("item." + "jsc." + kind.id() + ".tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(kind.heightU() + "U - rack equipment, not a computer")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
