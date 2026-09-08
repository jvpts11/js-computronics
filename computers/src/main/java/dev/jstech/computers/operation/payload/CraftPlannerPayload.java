/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Server to client: the Craft Planner's answer for a chosen target. It reports whether the network can make
 * the item at all, whether the request is fully feasible from stock, how much a single run of the plan
 * yields, the largest feasible amount, the ordered {@link Stage}s the plan runs, and the raw-ingredient bill
 * ({@link #ingredients}, need vs have, short when red). Sent in reply to {@link RequestCraftPlannerPayload}
 * when a target is set.
 */
public record CraftPlannerPayload(ItemStack result, long quantity, boolean craftable, boolean feasible,
                                  long produced, long maxFeasible, List<Stage> stages,
                                  List<CraftPlanPayload.Row> ingredients, List<TreeNode> tree)
        implements CustomPacketPayload {

    public static final int MAX_STAGES = 32;
    public static final int MAX_INGREDIENTS = 48;
    public static final int MAX_TREE = 96;

    /** One ordered step of the plan: what it makes, whether a machine runs it, and how many runs. */
    public record Stage(String name, boolean machine, long runs, long produced) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Stage> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, Stage::name,
                        ByteBufCodecs.BOOL, Stage::machine,
                        ByteBufCodecs.VAR_LONG, Stage::runs,
                        ByteBufCodecs.VAR_LONG, Stage::produced,
                        Stage::new);
    }

    /**
     * One node of the recipe dependency tree, flattened in pre-order: its {@code depth} gives the indent,
     * {@code qty} is how many of it the request needs at this point, and {@code craftable} is false for a raw
     * leaf the network cannot make.
     */
    public record TreeNode(int depth, ItemStack item, long qty, boolean craftable) {
        public static final StreamCodec<RegistryFriendlyByteBuf, TreeNode> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, TreeNode::depth,
                        ItemStack.STREAM_CODEC, TreeNode::item,
                        ByteBufCodecs.VAR_LONG, TreeNode::qty,
                        ByteBufCodecs.BOOL, TreeNode::craftable,
                        TreeNode::new);
    }

    public static final CustomPacketPayload.Type<CraftPlannerPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "craft_planner"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftPlannerPayload> STREAM_CODEC =
            StreamCodec.of(CraftPlannerPayload::encode, CraftPlannerPayload::decode);

    private static void encode(final RegistryFriendlyByteBuf buf, final CraftPlannerPayload p) {
        ItemStack.STREAM_CODEC.encode(buf, p.result);
        buf.writeVarLong(p.quantity);
        buf.writeBoolean(p.craftable);
        buf.writeBoolean(p.feasible);
        buf.writeVarLong(p.produced);
        buf.writeVarLong(p.maxFeasible);
        Stage.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_STAGES)).encode(buf, p.stages);
        CraftPlanPayload.Row.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_INGREDIENTS)).encode(buf, p.ingredients);
        TreeNode.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_TREE)).encode(buf, p.tree);
    }

    private static CraftPlannerPayload decode(final RegistryFriendlyByteBuf buf) {
        final ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
        final long quantity = buf.readVarLong();
        final boolean craftable = buf.readBoolean();
        final boolean feasible = buf.readBoolean();
        final long produced = buf.readVarLong();
        final long maxFeasible = buf.readVarLong();
        final List<Stage> stages = Stage.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_STAGES)).decode(buf);
        final List<CraftPlanPayload.Row> ingredients =
                CraftPlanPayload.Row.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_INGREDIENTS)).decode(buf);
        final List<TreeNode> tree = TreeNode.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_TREE)).decode(buf);
        return new CraftPlannerPayload(result, quantity, craftable, feasible, produced, maxFeasible,
                stages, ingredients, tree);
    }

    @Override
    public CustomPacketPayload.Type<CraftPlannerPayload> type() {
        return TYPE;
    }
}
