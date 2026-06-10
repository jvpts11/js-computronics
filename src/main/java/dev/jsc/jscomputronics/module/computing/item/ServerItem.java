/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.item;

import dev.jsc.jscomputronics.common.hardware.ComputerBuild;
import dev.jsc.jscomputronics.common.hardware.CpuSpec;
import dev.jsc.jscomputronics.common.hardware.DiskSpec;
import dev.jsc.jscomputronics.common.hardware.ExpansionCardSpec;
import dev.jsc.jscomputronics.common.hardware.MotherboardSpec;
import dev.jsc.jscomputronics.common.hardware.PsuSpec;
import dev.jsc.jscomputronics.common.hardware.RamSpec;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A Server: a complete computer in item form.
 */
public class ServerItem extends Item {

    public ServerItem(final Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(
            final net.minecraft.world.level.Level level, final net.minecraft.world.entity.player.Player player,
            final net.minecraft.world.InteractionHand hand) {
        // Reopen the assembly GUI so the player can change this Server's build.
        if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inv, p) -> new dev.jsc.jscomputronics.module.computing.menu.ServerAssemblyMenu(id, inv, hand),
                    Component.translatable("menu.jsc.server_assembly")),
                    buf -> buf.writeEnum(hand));
        }
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(
                player.getItemInHand(hand), level.isClientSide());
    }

    public static dev.jsc.jscomputronics.module.computing.storage.ServerStorageContents storage(final ItemStack stack) {
        return stack.getOrDefault(ComputingModule.SERVER_STORAGE.get(),
                dev.jsc.jscomputronics.module.computing.storage.ServerStorageContents.EMPTY);
    }

    public static ItemContainerContents hardware(final ItemStack stack) {
        return stack.getOrDefault(ComputingModule.SERVER_HARDWARE.get(), ItemContainerContents.EMPTY);
    }

    public static UUID nodeUuid(final ItemStack stack) {
        return stack.get(ComputingModule.SERVER_NODE_UUID.get());
    }

    public static String customName(final ItemStack stack) {
        return stack.getOrDefault(ComputingModule.COMPUTER_NAME.get(), "");
    }

    public static void setCustomName(final ItemStack stack, final String name) {
        final String trimmed = name.strip();
        if (trimmed.isEmpty()) {
            stack.remove(ComputingModule.COMPUTER_NAME.get());
        } else {
            stack.set(ComputingModule.COMPUTER_NAME.get(), trimmed);
        }
    }

    @Nullable
    public static ComputerBuild build(final ItemStack stack) {
        return buildFrom(hardware(stack).nonEmptyItems());
    }

    @Nullable
    public static ComputerBuild buildFrom(final Iterable<ItemStack> parts) {
        MotherboardSpec board = null;
        PsuSpec psu = null;
        final List<CpuSpec> cpus = new ArrayList<>();
        final List<RamSpec> rams = new ArrayList<>();
        final List<ExpansionCardSpec> pcieCards = new ArrayList<>();
        final List<DiskSpec> disks = new ArrayList<>();
        for (final ItemStack part : parts) {
            if (part.getItem() instanceof MotherboardItem m) {
                board = m.spec();
            } else if (part.getItem() instanceof PsuItem p) {
                psu = p.spec();
            } else if (part.getItem() instanceof CpuItem c) {
                cpus.add(c.spec());
            } else if (part.getItem() instanceof RamItem r) {
                rams.add(r.spec());
            } else if (part.getItem() instanceof ExpansionCardItem card) {
                pcieCards.add(card.cardSpec());
            } else if (part.getItem() instanceof DiskItem d) {
                disks.add(d.spec());
            }
        }
        if (board == null || psu == null) {
            return null;
        }
        return new ComputerBuild(board, cpus, pcieCards, rams, psu, disks);
    }

    public static long storageMb(final ItemStack stack) {
        final ComputerBuild b = build(stack);
        return b == null ? 0L : b.storageMb();
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        final long stored = storage(stack).total();
        final ComputerBuild assembled = build(stack);
        final long capacity = assembled == null ? 0L : assembled.totalStorageItems();
        tooltip.add(Component.translatable("item.jsc.server.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(stored + " / " + capacity + " items stored")
                .withStyle(ChatFormatting.DARK_GRAY));
        final UUID uuid = nodeUuid(stack);
        if (uuid != null) {
            tooltip.add(Component.literal("Node " + uuid.toString().substring(0, 8))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
