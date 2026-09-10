/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation.payload;

import dev.jstech.computers.crafting.RecipeChoice;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client: the planner's answer for the Craft popup. The rows are what the request consumes (need
 * vs have, red when short), with whether it is fully feasible, the largest feasible amount for the PARTIAL
 * button, and a time estimate. When more than one recipe on the network makes the result, {@code options}
 * lists them all and {@code recipe} says which one the rows were planned with; {@code cover} says, per row
 * that is short, what the network would craft to cover it (or that nothing can).
 */
public record CraftPlanPayload(ItemStack result, long quantity, List<Row> rows,
                               boolean feasible, long maxFeasible, int estimateTicks,
                               int recipe, List<RecipeChoice> options, List<String> cover, int stages)
        implements CustomPacketPayload {

    public static final int MAX_ROWS = 64;
    public static final int MAX_OPTIONS = 8;
    public static final int MAX_INPUTS = 12;
    public static final int MAX_MACHINES = 8;
    public static final int MAX_COVER = 6;
    public static final int MAX_TEXT = 96;

    /** A plan with no choice of recipe: what a result with a single recipe gets. */
    public CraftPlanPayload(final ItemStack result, final long quantity, final List<Row> rows,
                            final boolean feasible, final long maxFeasible, final int estimateTicks) {
        this(result, quantity, rows, feasible, maxFeasible, estimateTicks, 0, List.of(), List.of(), 1);
    }

    public record Row(ItemStack item, long need, long have) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC =
                StreamCodec.composite(
                        ItemStack.STREAM_CODEC, Row::item,
                        ByteBufCodecs.VAR_LONG, Row::need,
                        ByteBufCodecs.VAR_LONG, Row::have,
                        Row::new);

        public boolean satisfied() {
            return have >= need;
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeChoice.Input> INPUT_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_TEXT), RecipeChoice.Input::name,
                    ByteBufCodecs.VAR_LONG, RecipeChoice.Input::need,
                    ByteBufCodecs.VAR_LONG, RecipeChoice.Input::have,
                    ByteBufCodecs.BOOL, RecipeChoice.Input::craftable,
                    RecipeChoice.Input::new);

    /*
     * Written out by hand: a choice carries seven things and composite takes six pairs. The strings are cut
     * to their caps on the way out, never refused, because a cap on writeUtf drops the connection.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeChoice> CHOICE_CODEC =
            StreamCodec.of(CraftPlanPayload::encodeChoice, CraftPlanPayload::decodeChoice);

    private static void encodeChoice(final RegistryFriendlyByteBuf buf, final RecipeChoice choice) {
        buf.writeUtf(clip(choice.label()), MAX_TEXT);
        buf.writeUtf(clip(choice.kind()), MAX_TEXT);
        ByteBufCodecs.stringUtf8(MAX_TEXT).apply(ByteBufCodecs.list(MAX_MACHINES))
                .encode(buf, clipAll(choice.machines(), MAX_MACHINES));
        buf.writeVarInt(choice.stages());
        buf.writeVarInt(choice.estimateTicks());
        final List<RecipeChoice.Input> inputs = new ArrayList<>();
        for (int i = 0; i < choice.inputs().size() && i < MAX_INPUTS; i++) {
            final RecipeChoice.Input in = choice.inputs().get(i);
            inputs.add(new RecipeChoice.Input(clip(in.name()), in.need(), in.have(), in.craftable()));
        }
        INPUT_CODEC.apply(ByteBufCodecs.list(MAX_INPUTS)).encode(buf, inputs);
        buf.writeBoolean(choice.feasible());
    }

    private static RecipeChoice decodeChoice(final RegistryFriendlyByteBuf buf) {
        final String label = buf.readUtf(MAX_TEXT);
        final String kind = buf.readUtf(MAX_TEXT);
        final List<String> machines = ByteBufCodecs.stringUtf8(MAX_TEXT).apply(ByteBufCodecs.list(MAX_MACHINES)).decode(buf);
        final int stages = buf.readVarInt();
        final int estimate = buf.readVarInt();
        final List<RecipeChoice.Input> inputs = INPUT_CODEC.apply(ByteBufCodecs.list(MAX_INPUTS)).decode(buf);
        final boolean feasible = buf.readBoolean();
        return new RecipeChoice(label, kind, machines, stages, estimate, inputs, feasible);
    }

    private static String clip(final String text) {
        final String s = text == null ? "" : text;
        return s.length() <= MAX_TEXT ? s : s.substring(0, MAX_TEXT);
    }

    private static List<String> clipAll(final List<String> texts, final int max) {
        final List<String> out = new ArrayList<>();
        for (int i = 0; i < texts.size() && i < max; i++) {
            out.add(clip(texts.get(i)));
        }
        return out;
    }

    public static final CustomPacketPayload.Type<CraftPlanPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "craft_plan"));

    // Ten things to carry, so the codec is written out rather than composed.
    public static final StreamCodec<RegistryFriendlyByteBuf, CraftPlanPayload> STREAM_CODEC =
            StreamCodec.of(CraftPlanPayload::encode, CraftPlanPayload::decode);

    private static void encode(final RegistryFriendlyByteBuf buf, final CraftPlanPayload p) {
        ItemStack.STREAM_CODEC.encode(buf, p.result);
        buf.writeVarLong(p.quantity);
        Row.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ROWS)).encode(buf, p.rows);
        buf.writeBoolean(p.feasible);
        buf.writeVarLong(p.maxFeasible);
        buf.writeVarInt(p.estimateTicks);
        buf.writeVarInt(p.recipe);
        CHOICE_CODEC.apply(ByteBufCodecs.list(MAX_OPTIONS)).encode(buf, p.options);
        ByteBufCodecs.stringUtf8(MAX_TEXT).apply(ByteBufCodecs.list(MAX_COVER)).encode(buf, clipAll(p.cover, MAX_COVER));
        buf.writeVarInt(p.stages);
    }

    private static CraftPlanPayload decode(final RegistryFriendlyByteBuf buf) {
        final ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
        final long quantity = buf.readVarLong();
        final List<Row> rows = Row.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ROWS)).decode(buf);
        final boolean feasible = buf.readBoolean();
        final long maxFeasible = buf.readVarLong();
        final int estimate = buf.readVarInt();
        final int recipe = buf.readVarInt();
        final List<RecipeChoice> options = CHOICE_CODEC.apply(ByteBufCodecs.list(MAX_OPTIONS)).decode(buf);
        final List<String> cover = ByteBufCodecs.stringUtf8(MAX_TEXT).apply(ByteBufCodecs.list(MAX_COVER)).decode(buf);
        final int stages = buf.readVarInt();
        return new CraftPlanPayload(result, quantity, rows, feasible, maxFeasible, estimate, recipe, options, cover, stages);
    }

    /** Whether the player has a choice of recipe to make: more than one option came back. */
    public boolean hasChoice() {
        return options.size() > 1;
    }

    /** The option the rows were planned with, or null when the plan carries no options. */
    public RecipeChoice chosen() {
        return recipe >= 0 && recipe < options.size() ? options.get(recipe) : null;
    }

    @Override
    public CustomPacketPayload.Type<CraftPlanPayload> type() {
        return TYPE;
    }
}
