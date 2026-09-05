/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation.payload;

import dev.jstech.computronics.crafting.PatternWorkbench;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client: everything the Pattern Studio window shows, in one message. The three drafts as the
 * workbench holds them (with the network's stock behind each cell and what an "any" cell would resolve to
 * right now), the drives the computer can read recipe files from and what is on them, the linked encoder's
 * state, the machines the network declares, and whether each draft is already in this computer's Recipe ROM.
 *
 * <p>Every string is cut to its wire field by the builder: a string too long for its field disconnects the
 * player, so nothing here is allowed to grow with a name or a mod id.
 */
public record PatternStudioStatePayload(
        List<BenchCell> bench, ItemStack preview, String benchName, String benchNote, String benchOpened,
        List<ProcCell> inputs, List<ProcCell> outputs, String machineType, int timeout,
        String procName, String procNote, String procOpened,
        List<Stage> stages, String pipeName, String pipeNote, String pipeOpened,
        List<Drive> drives, Encoder encoder, List<Machine> machines,
        boolean craftingComputer, boolean hasCard, boolean romHasBench, boolean romHasProc, boolean romHasPipe,
        String status, int tabHint) implements CustomPacketPayload {

    public static final int MAX_NAME = 64;
    public static final int MAX_NOTE = 256;
    public static final int MAX_TAG = 128;
    public static final int MAX_LABEL = 64;
    public static final int MAX_KEY = 96;
    public static final int MAX_STATUS = 96;
    public static final int MAX_FILES = 64;
    public static final int MAX_DRIVES = 8;
    public static final int MAX_MACHINES = 48;
    public static final int MAX_STAGES = 16;

    /** A bench cell: the ghost item, the tag it accepts, what it resolves to now, and the stock of that. */
    public record BenchCell(ItemStack stack, String tag, ItemStack resolved, long stock) {
        static final StreamCodec<RegistryFriendlyByteBuf, BenchCell> STREAM_CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, BenchCell::stack,
                ByteBufCodecs.stringUtf8(MAX_TAG), BenchCell::tag,
                ItemStack.OPTIONAL_STREAM_CODEC, BenchCell::resolved,
                ByteBufCodecs.VAR_LONG, BenchCell::stock,
                BenchCell::new);
    }

    /** A machine-draft cell at grid index {@code index}: its data, the chance (outputs) and the stock behind it. */
    public record ProcCell(int index, PatternWorkbench.DataCell cell, int chance, long stock) {
        static final StreamCodec<RegistryFriendlyByteBuf, ProcCell> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ProcCell::index,
                PatternWorkbench.DataCell.STREAM_CODEC, ProcCell::cell,
                ByteBufCodecs.VAR_INT, ProcCell::chance,
                ByteBufCodecs.VAR_LONG, ProcCell::stock,
                ProcCell::new);
    }

    /** A pipeline stage as listed: what it makes and whether it runs on a bench or a machine. */
    public record Stage(String label, boolean bench, ItemStack result) {
        static final StreamCodec<RegistryFriendlyByteBuf, Stage> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_LABEL), Stage::label,
                ByteBufCodecs.BOOL, Stage::bench,
                ItemStack.OPTIONAL_STREAM_CODEC, Stage::result,
                Stage::new);
    }

    /**
     * A drive the Studio can open files from: {@code media:<pos>} for a linked reader or {@code disk} for the
     * system disk's crafts folder, with the {@code .craft} files it holds.
     */
    public record Drive(String key, String label, boolean writable, List<String> files) {
        static final StreamCodec<RegistryFriendlyByteBuf, Drive> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_KEY), Drive::key,
                ByteBufCodecs.stringUtf8(MAX_LABEL), Drive::label,
                ByteBufCodecs.BOOL, Drive::writable,
                ByteBufCodecs.stringUtf8(MAX_NAME).apply(ByteBufCodecs.list(MAX_FILES)), Drive::files,
                Drive::new);
    }

    /** The linked Pattern Encoder, or {@code linked == false} when there is none. */
    public record Encoder(boolean linked, String era, String media, String status, int progress, int queued,
                          boolean busy, boolean error) {
        static final StreamCodec<RegistryFriendlyByteBuf, Encoder> STREAM_CODEC = StreamCodec.of(
                (buf, e) -> {
                    buf.writeBoolean(e.linked());
                    buf.writeUtf(e.era(), MAX_LABEL);
                    buf.writeUtf(e.media(), MAX_LABEL);
                    buf.writeUtf(e.status(), MAX_STATUS);
                    buf.writeVarInt(e.progress());
                    buf.writeVarInt(e.queued());
                    buf.writeBoolean(e.busy());
                    buf.writeBoolean(e.error());
                },
                buf -> new Encoder(buf.readBoolean(), buf.readUtf(MAX_LABEL), buf.readUtf(MAX_LABEL),
                        buf.readUtf(MAX_STATUS), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                        buf.readBoolean()));

        public static Encoder none() {
            return new Encoder(false, "", "", "", 0, 0, false, false);
        }
    }

    /** A machine type the network declares (a switch face hosts one), as a picker entry. */
    public record Machine(String typeKey, String label) {
        static final StreamCodec<RegistryFriendlyByteBuf, Machine> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_KEY), Machine::typeKey,
                ByteBufCodecs.stringUtf8(MAX_LABEL), Machine::label,
                Machine::new);
    }

    public static final CustomPacketPayload.Type<PatternStudioStatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "pattern_studio_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PatternStudioStatePayload> STREAM_CODEC =
            StreamCodec.of(PatternStudioStatePayload::write, PatternStudioStatePayload::read);

    private static void write(final RegistryFriendlyByteBuf buf, final PatternStudioStatePayload p) {
        writeList(buf, p.bench(), BenchCell.STREAM_CODEC, 9);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.preview());
        buf.writeUtf(p.benchName(), MAX_NAME);
        buf.writeUtf(p.benchNote(), MAX_NOTE);
        buf.writeUtf(p.benchOpened(), MAX_NAME);
        writeList(buf, p.inputs(), ProcCell.STREAM_CODEC, PatternWorkbench.PROC_GRID);
        writeList(buf, p.outputs(), ProcCell.STREAM_CODEC, PatternWorkbench.PROC_GRID);
        buf.writeUtf(p.machineType(), MAX_KEY);
        buf.writeVarInt(p.timeout());
        buf.writeUtf(p.procName(), MAX_NAME);
        buf.writeUtf(p.procNote(), MAX_NOTE);
        buf.writeUtf(p.procOpened(), MAX_NAME);
        writeList(buf, p.stages(), Stage.STREAM_CODEC, MAX_STAGES);
        buf.writeUtf(p.pipeName(), MAX_NAME);
        buf.writeUtf(p.pipeNote(), MAX_NOTE);
        buf.writeUtf(p.pipeOpened(), MAX_NAME);
        writeList(buf, p.drives(), Drive.STREAM_CODEC, MAX_DRIVES);
        Encoder.STREAM_CODEC.encode(buf, p.encoder());
        writeList(buf, p.machines(), Machine.STREAM_CODEC, MAX_MACHINES);
        buf.writeBoolean(p.craftingComputer());
        buf.writeBoolean(p.hasCard());
        buf.writeBoolean(p.romHasBench());
        buf.writeBoolean(p.romHasProc());
        buf.writeBoolean(p.romHasPipe());
        buf.writeUtf(p.status(), MAX_STATUS);
        buf.writeVarInt(p.tabHint());
    }

    private static PatternStudioStatePayload read(final RegistryFriendlyByteBuf buf) {
        final List<BenchCell> bench = readList(buf, BenchCell.STREAM_CODEC, 9);
        final ItemStack preview = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        final String benchName = buf.readUtf(MAX_NAME);
        final String benchNote = buf.readUtf(MAX_NOTE);
        final String benchOpened = buf.readUtf(MAX_NAME);
        final List<ProcCell> inputs = readList(buf, ProcCell.STREAM_CODEC, PatternWorkbench.PROC_GRID);
        final List<ProcCell> outputs = readList(buf, ProcCell.STREAM_CODEC, PatternWorkbench.PROC_GRID);
        final String machineType = buf.readUtf(MAX_KEY);
        final int timeout = buf.readVarInt();
        final String procName = buf.readUtf(MAX_NAME);
        final String procNote = buf.readUtf(MAX_NOTE);
        final String procOpened = buf.readUtf(MAX_NAME);
        final List<Stage> stages = readList(buf, Stage.STREAM_CODEC, MAX_STAGES);
        final String pipeName = buf.readUtf(MAX_NAME);
        final String pipeNote = buf.readUtf(MAX_NOTE);
        final String pipeOpened = buf.readUtf(MAX_NAME);
        final List<Drive> drives = readList(buf, Drive.STREAM_CODEC, MAX_DRIVES);
        final Encoder encoder = Encoder.STREAM_CODEC.decode(buf);
        final List<Machine> machines = readList(buf, Machine.STREAM_CODEC, MAX_MACHINES);
        final boolean cc = buf.readBoolean();
        final boolean hasCard = buf.readBoolean();
        final boolean romBench = buf.readBoolean();
        final boolean romProc = buf.readBoolean();
        final boolean romPipe = buf.readBoolean();
        final String status = buf.readUtf(MAX_STATUS);
        final int tabHint = buf.readVarInt();
        return new PatternStudioStatePayload(bench, preview, benchName, benchNote, benchOpened, inputs, outputs,
                machineType, timeout, procName, procNote, procOpened, stages, pipeName, pipeNote, pipeOpened,
                drives, encoder, machines, cc, hasCard, romBench, romProc, romPipe, status, tabHint);
    }

    private static <T> void writeList(final RegistryFriendlyByteBuf buf, final List<T> list,
                                      final StreamCodec<RegistryFriendlyByteBuf, T> codec, final int max) {
        final int n = Math.min(list.size(), max);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            codec.encode(buf, list.get(i));
        }
    }

    private static <T> List<T> readList(final RegistryFriendlyByteBuf buf,
                                        final StreamCodec<RegistryFriendlyByteBuf, T> codec, final int max) {
        final int n = buf.readVarInt();
        if (n < 0 || n > max) {
            throw new IllegalStateException("list of " + n + " exceeds " + max);
        }
        final List<T> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(codec.decode(buf));
        }
        return out;
    }

    @Override
    public CustomPacketPayload.Type<PatternStudioStatePayload> type() {
        return TYPE;
    }
}
