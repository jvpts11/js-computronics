/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computers.operation.payload.CraftManagerStatePayload;
import dev.jstech.computers.operation.payload.PatternStudioEditPayload;
import dev.jstech.computers.operation.payload.PatternStudioStatePayload;
import dev.jstech.computers.operation.payload.SetMachineConfigPayload;
import dev.jstech.tests.JsTests;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Battery 1, front M: every new Slice C payload survives a StreamCodec encode/decode with the buffer fully
 * consumed, including the regrouped {@link CraftManagerStatePayload} (the MediaBlock sub-record that keeps it
 * within the 6-pair composite limit) carrying a full machine list.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class MachinePayloadGameTests {

    private MachinePayloadGameTests() {
    }

    private static final String ARENA = "empty";

    @GameTest(template = ARENA)
    public static void craftManagerState_streamCodecRoundTrip(final GameTestHelper helper) {
        final List<CraftManagerStatePayload.WireRomEntry> rom = List.of(
                new CraftManagerStatePayload.WireRomEntry(0, "Iron Block", true),
                new CraftManagerStatePayload.WireRomEntry(1, "Gold Block", false));
        final List<CraftManagerStatePayload.WireMachine> machines = List.of(
                new CraftManagerStatePayload.WireMachine("@1,2,3", "jsindustrial:compressor", "N (1, 2, 3)", false, true, 4),
                new CraftManagerStatePayload.WireMachine("@4,5,6", "jsindustrial:macerator", "E (4, 5, 6)", true, false, 1));
        final CraftManagerStatePayload payload = new CraftManagerStatePayload(
                "media:42", "Floppy (A:)", List.of("alpha.craft", "beta.craft"), rom, true, "Loaded 2", machines);
        assertRoundTrip(helper, CraftManagerStatePayload.STREAM_CODEC, payload);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void craftManagerState_emptyListsRoundTrip(final GameTestHelper helper) {
        final CraftManagerStatePayload payload = new CraftManagerStatePayload(
                "", "", List.of(), List.of(), false, "", List.of());
        assertRoundTrip(helper, CraftManagerStatePayload.STREAM_CODEC, payload);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void craftManagerState_maxMachinesRoundTrip(final GameTestHelper helper) {
        final List<CraftManagerStatePayload.WireMachine> machines = new ArrayList<>();
        for (int i = 0; i < CraftManagerStatePayload.MAX_MACHINES; i++) {
            machines.add(new CraftManagerStatePayload.WireMachine("@" + i, "jsc:m" + i, "m" + i,
                    i % 3 == 0, i % 4 == 0, i + 1));
        }
        final CraftManagerStatePayload payload = new CraftManagerStatePayload(
                "media:1", "Disc", List.of(), List.of(), true, "", machines);
        assertRoundTrip(helper, CraftManagerStatePayload.STREAM_CODEC, payload);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void setMachineConfig_streamCodecRoundTrip(final GameTestHelper helper) {
        assertRoundTrip(helper, SetMachineConfigPayload.STREAM_CODEC,
                new SetMachineConfigPayload(new BlockPos(7, -3, 19), "jsindustrial:compressor", 8, true, false));
        assertRoundTrip(helper, SetMachineConfigPayload.STREAM_CODEC,
                new SetMachineConfigPayload(new BlockPos(0, 0, 0), "", 1, false, true));
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void patternStudioEdit_streamCodecRoundTrip(final GameTestHelper helper) {
        assertRoundTrip(helper, PatternStudioEditPayload.STREAM_CODEC,
                PatternStudioEditPayload.number(new BlockPos(1, 2, 3), new BlockPos(2, 2, 3),
                        PatternStudioEditPayload.PROC_SET_CHANCE, 4, 75));
        assertRoundTrip(helper, PatternStudioEditPayload.STREAM_CODEC,
                PatternStudioEditPayload.text(new BlockPos(-5, 60, -9), new BlockPos(-4, 60, -9),
                        PatternStudioEditPayload.PROC_SET_MACHINE, 0, "jsindustrial:macerator", ""));
        // An item rides the wire by value; compare the fields around it (ItemStack has no value equality).
        final PatternStudioEditPayload withItem = PatternStudioEditPayload.item(new BlockPos(0, 1, 0),
                new BlockPos(1, 1, 0), PatternStudioEditPayload.BENCH_SET_CELL, 8,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG, 3));
        final RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().registryAccess());
        PatternStudioEditPayload.STREAM_CODEC.encode(buf, withItem);
        final PatternStudioEditPayload decoded = PatternStudioEditPayload.STREAM_CODEC.decode(buf);
        helper.assertTrue(decoded.index() == 8 && decoded.action() == PatternStudioEditPayload.BENCH_SET_CELL
                && net.minecraft.world.item.ItemStack.matches(decoded.item(), withItem.item()),
                "the item edit survives the wire");
        helper.assertTrue(buf.readableBytes() == 0, "the buffer was fully consumed");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void patternStudioState_streamCodecRoundTrip(final GameTestHelper helper) {
        /*
         * The state is hand-written on the wire (far more than six fields): a full, busy state must go
         * through and come back field for field.
         */
        final var bench = new ArrayList<PatternStudioStatePayload.BenchCell>();
        for (int i = 0; i < 9; i++) {
            bench.add(new PatternStudioStatePayload.BenchCell(
                    i % 2 == 0 ? new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_PLANKS)
                            : net.minecraft.world.item.ItemStack.EMPTY,
                    i == 0 ? "minecraft:planks" : "",
                    i == 0 ? new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BIRCH_PLANKS)
                            : net.minecraft.world.item.ItemStack.EMPTY, 12L * i));
        }
        final var cell = new dev.jstech.computers.crafting.PatternWorkbench.DataCell(
                dev.jstech.computers.storage.StorageKey.of(net.minecraft.world.item.Items.RAW_IRON), 2, true);
        final PatternStudioStatePayload state = new PatternStudioStatePayload(bench,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CHEST), "Chest of any planks",
                "note", "chest.craft",
                List.of(new PatternStudioStatePayload.ProcCell(3, cell, 100, 40L)),
                List.of(new PatternStudioStatePayload.ProcCell(0, cell, 50, 0L)),
                "minecraft:furnace", 600, "", "", "",
                List.of(new PatternStudioStatePayload.Stage("Iron Ingot", false,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT))),
                "pipe", "", "pipe.craft",
                List.of(new PatternStudioStatePayload.Drive("media:12345", "DVD-RW", true, List.of("a.craft", "b.craft")),
                        new PatternStudioStatePayload.Drive("disk", "System disk (crafts)", true, List.of())),
                new PatternStudioStatePayload.Encoder(true, "Standard", "DVD-RW", "Writing a.craft", 42, 2, true, false),
                List.of(new PatternStudioStatePayload.Machine("minecraft:furnace", "Furnace")),
                true, true, false, true, false, "Sent", 1);
        final RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().registryAccess());
        PatternStudioStatePayload.STREAM_CODEC.encode(buf, state);
        final PatternStudioStatePayload decoded = PatternStudioStatePayload.STREAM_CODEC.decode(buf);
        helper.assertTrue(buf.readableBytes() == 0, "the buffer was fully consumed");
        helper.assertTrue(decoded.bench().size() == 9 && "minecraft:planks".equals(decoded.bench().get(0).tag())
                && decoded.bench().get(0).resolved().is(net.minecraft.world.item.Items.BIRCH_PLANKS)
                && decoded.bench().get(8).stock() == 96L, "the bench cells survive");
        helper.assertTrue(decoded.preview().is(net.minecraft.world.item.Items.CHEST)
                && "Chest of any planks".equals(decoded.benchName()) && "chest.craft".equals(decoded.benchOpened()),
                "the bench header survives");
        helper.assertTrue(decoded.inputs().size() == 1 && decoded.inputs().get(0).index() == 3
                && decoded.inputs().get(0).cell().estimated() && decoded.outputs().get(0).chance() == 50,
                "the machine cells survive");
        helper.assertTrue("minecraft:furnace".equals(decoded.machineType()) && decoded.timeout() == 600, "machine and timeout");
        helper.assertTrue(decoded.stages().size() == 1 && !decoded.stages().get(0).bench()
                && "pipe.craft".equals(decoded.pipeOpened()), "the pipeline survives");
        helper.assertTrue(decoded.drives().size() == 2 && decoded.drives().get(0).files().size() == 2, "the drives survive");
        helper.assertTrue(decoded.encoder().linked() && decoded.encoder().progress() == 42
                && decoded.encoder().queued() == 2 && decoded.encoder().busy(), "the encoder survives");
        helper.assertTrue(decoded.machines().size() == 1 && decoded.craftingComputer() && decoded.hasCard()
                && !decoded.romHasBench() && decoded.romHasProc() && "Sent".equals(decoded.status())
                && decoded.tabHint() == 1, "the flags survive");
        helper.succeed();
    }

    private static <T> void assertRoundTrip(final GameTestHelper helper,
                                            final StreamCodec<RegistryFriendlyByteBuf, T> codec, final T value) {
        final RegistryAccess registries = helper.getLevel().registryAccess();
        final RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        codec.encode(buf, value);
        final T decoded = codec.decode(buf);
        helper.assertTrue(decoded.equals(value), "round-trip changed the value: " + value + " -> " + decoded);
        helper.assertTrue(buf.readableBytes() == 0, "the buffer was not fully consumed (" + buf.readableBytes()
                + " bytes left)");
    }
}
