/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.gametest;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.computing.operation.payload.CraftManagerStatePayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.PatternEncoderEditPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.SetMachineConfigPayload;
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
 * consumed — including the regrouped {@link CraftManagerStatePayload} (the MediaBlock sub-record that keeps it
 * within the 6-pair composite limit) carrying a full machine list.
 */
@GameTestHolder(JsComputronics.MODID)
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
                new CraftManagerStatePayload.WireMachine("@1,2,3", "jsc:compressor", "N (1, 2, 3)", false, true, 4),
                new CraftManagerStatePayload.WireMachine("@4,5,6", "jsc:macerator", "E (4, 5, 6)", true, false, 1));
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
                new SetMachineConfigPayload(new BlockPos(7, -3, 19), "jsc:compressor", 8, true, false));
        assertRoundTrip(helper, SetMachineConfigPayload.STREAM_CODEC,
                new SetMachineConfigPayload(new BlockPos(0, 0, 0), "", 1, false, true));
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void patternEncoderEdit_streamCodecRoundTrip(final GameTestHelper helper) {
        assertRoundTrip(helper, PatternEncoderEditPayload.STREAM_CODEC,
                new PatternEncoderEditPayload(new BlockPos(1, 2, 3),
                        PatternEncoderEditPayload.ACTION_SET_CHANCE, 4, 75, ""));
        assertRoundTrip(helper, PatternEncoderEditPayload.STREAM_CODEC,
                new PatternEncoderEditPayload(new BlockPos(-5, 60, -9),
                        PatternEncoderEditPayload.ACTION_SET_MACHINE, 0, 0, "jsc:macerator"));
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
