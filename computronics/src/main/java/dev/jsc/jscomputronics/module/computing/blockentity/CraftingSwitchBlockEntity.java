/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.blockentity;

import dev.jstech.core.network.DataTier;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.block.CraftingComputerBlock;
import dev.jsc.jscomputronics.module.computing.block.DataCableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Declares up to five adjacent machines for network crafting. Five of the six faces can each touch a machine the
 * network can route crafts through; the sixth face carries the crafting cable to a Crafting Computer (found by a
 * BFS through that cable, exactly like the supercomputer cluster discovers its nodes over the HPC cable). The
 * switch never receives Operations itself — the Crafting Computer discovers it and aggregates its machines.
 */
public class CraftingSwitchBlockEntity extends BlockEntity {

    private static final int FACES = 6;
    private static final int BFS_STEPS = 64;

    // Persistent per-face config: a player-set name (referenced by PROCESSING patterns), an active toggle, and a
    // generic machine category (a recipe type id, e.g. "minecraft:smelting") so patterns authored against
    // generic:<category> match this face.
    private final String[] faceNames = new String[FACES];
    private final boolean[] faceActive = new boolean[FACES];
    private final String[] faceCategories = new String[FACES];

    // Transient, recomputed every tick: which faces touch a machine, the cable face, and the linked computer.
    private final boolean[] machinePresent = new boolean[FACES];
    private Direction cableFace;
    private BlockPos linkedComputer;
    private boolean clientLinked; // client mirror of "linkedComputer != null", carried by the update tag
    private int busMachineCount;  // machines discovered over the cables via crafting buses (synced for the GUI)
    // One line per bus-discovered machine, synced so the GUI lists WHICH machines the switch found, where
    // (absolute coordinates), and through which bus (whose name is editable from the switch screen).
    private java.util.List<BusMachineLine> busMachineLines = java.util.List.of();

    /**
     * A machine discovered over the cables: its block name, the bus's name, where both sit, and the switch
     * face whose cable run reaches it — the GUI lists the machine ON that face row.
     */
    public record BusMachineLine(String blockName, String busName, BlockPos machinePos,
                                 BlockPos cablePos, int busFace, int switchFace) {
    }

    public CraftingSwitchBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.CRAFTING_SWITCH_BE.get(), pos, state);
        for (int i = 0; i < FACES; i++) {
            faceNames[i] = "";
            faceActive[i] = true;
            faceCategories[i] = dev.jsc.jscomputronics.module.computing.crafting.MachineCategory.NONE;
        }
    }


    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final CraftingSwitchBlockEntity be) {
        if (level instanceof ServerLevel serverLevel) {
            be.survey(serverLevel);
        }
    }

    /** Recomputes the cable face, the linked Crafting Computer, and which faces touch a machine. */
    private void survey(final ServerLevel level) {
        final boolean[] before = machinePresent.clone();
        final BlockPos linkedBefore = linkedComputer;
        Direction cable = null;
        for (final Direction direction : Direction.values()) {
            final BlockPos neighbor = worldPosition.relative(direction);
            if (level.getBlockState(neighbor).getBlock() instanceof DataCableBlock dataCable
                    && dataCable.tier() == DataTier.CRAFTING) {
                cable = direction;
                machinePresent[direction.get3DDataValue()] = false; // the cable face never hosts a machine
            } else {
                // A "machine" is any adjacent block with a block entity that exposes an item handler on ANY side
                // — not just the touched face. Sided machines (e.g. a mod machine whose side-config closes the
                // face the switch happens to touch) must still be recognized; the engine can reach their real
                // I/O faces through Input/Receiving buses.
                machinePresent[direction.get3DDataValue()] = exposesItemHandler(level, neighbor);
            }
        }
        this.cableFace = cable;
        this.linkedComputer = cable == null ? null
                : findComputer(level, worldPosition.relative(cable));
        final java.util.List<BusMachineLine> busBefore = busMachineLines;
        final java.util.Set<BlockPos> adjacent = new HashSet<>();
        for (final Direction direction : Direction.values()) {
            if (direction != cableFace && machinePresent[direction.get3DDataValue()]) {
                adjacent.add(worldPosition.relative(direction));
            }
        }
        final java.util.List<BusMachineLine> lines = collectBusMachines(adjacent);
        this.busMachineLines = lines;
        this.busMachineCount = lines.size();
        // The GUI reads this block entity on the client, so push an update tag whenever the survey changes.
        if (!java.util.Arrays.equals(before, machinePresent)
                || !java.util.Objects.equals(linkedBefore, linkedComputer)
                || !busBefore.equals(busMachineLines)) {
            final BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    /** Whether the block at {@code pos} offers an item handler on any face (or unsided). */
    private static boolean exposesItemHandler(final ServerLevel level, final BlockPos pos) {
        if (level.getBlockEntity(pos) == null) {
            return false;
        }
        if (level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) != null) {
            return true;
        }
        for (final Direction side : Direction.values()) {
            if (level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side) != null) {
                return true;
            }
        }
        return false;
    }

    /** BFS through crafting cables from the switch's cable face to the first Crafting Computer it reaches. */
    private BlockPos findComputer(final ServerLevel level, final BlockPos start) {
        final Set<BlockPos> visited = new HashSet<>();
        final Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        int steps = 0;
        while (!queue.isEmpty() && steps++ < BFS_STEPS) {
            final BlockPos current = queue.poll();
            final BlockState state = level.getBlockState(current);
            if (level.getBlockEntity(current) instanceof CraftingComputerBlockEntity) {
                return current;
            }
            if (state.getBlock() instanceof DataCableBlock cable && cable.tier() == DataTier.CRAFTING) {
                for (final Direction direction : Direction.values()) {
                    final BlockPos neighbor = current.relative(direction);
                    if (visited.add(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
        }
        return null;
    }

    // --- accessors for the GUI / the Crafting Computer ---

    public boolean machineOnFace(final Direction face) {
        return machinePresent[face.get3DDataValue()];
    }

    public Direction cableFace() {
        return cableFace;
    }

    public BlockPos linkedComputer() {
        return linkedComputer;
    }

    /** Whether a Crafting Computer is reachable over the crafting cable — valid on both sides. */
    public boolean isLinked() {
        return linkedComputer != null || clientLinked;
    }

    public String faceName(final Direction face) {
        return faceNames[face.get3DDataValue()];
    }

    public void setFaceName(final Direction face, final String name) {
        faceNames[face.get3DDataValue()] = name == null ? "" : name;
        setChanged();
    }

    public boolean faceActive(final Direction face) {
        return faceActive[face.get3DDataValue()];
    }

    public void setFaceActive(final Direction face, final boolean active) {
        faceActive[face.get3DDataValue()] = active;
        setChanged();
    }

    /** The face's generic category — a recipe type id such as {@code minecraft:smelting}, or empty for none. */
    public String faceCategory(final Direction face) {
        return faceCategories[face.get3DDataValue()];
    }

    public void setFaceCategory(final Direction face, final String category) {
        faceCategories[face.get3DDataValue()] = category == null ? "" : category;
        setChanged();
    }

    /** A machine declared on an active face: its name, block type, category, world position, and touched face. */
    public record DeclaredMachine(String name, String machineType, String category,
                                  BlockPos machinePos, Direction face) {
    }

    /**
     * Every machine this switch currently offers the network, from two sources: active faces (excluding the
     * cable face) that touch a block with an item handler, and machines reached over the crafting cables — any
     * block a mounted Crafting Input/Receiving Bus points at. The engine uses {@code machineType}/{@code name}
     * to find a machine for a processing pattern and {@code machinePos}/{@code face} to drive its I/O.
     */
    public java.util.List<DeclaredMachine> declaredMachines() {
        final java.util.List<DeclaredMachine> out = new java.util.ArrayList<>();
        if (level == null) {
            return out;
        }
        final java.util.Set<BlockPos> declared = new HashSet<>();
        for (final Direction direction : Direction.values()) {
            final int i = direction.get3DDataValue();
            if (direction == cableFace || !machinePresent[i] || !faceActive[i]) {
                continue;
            }
            final BlockPos machinePos = worldPosition.relative(direction);
            final var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(level.getBlockState(machinePos).getBlock());
            out.add(new DeclaredMachine(faceNames[i], key.toString(), faceCategories[i], machinePos, direction));
            declared.add(machinePos);
        }
        // Machines reached over the cables belong to the switch face their cable run hangs from: they inherit
        // that face's category (so generic patterns match them) and are gated by that face's active toggle.
        for (final BusMachineLine line : collectBusMachines(declared)) {
            final var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(level.getBlockState(line.machinePos()).getBlock());
            out.add(new DeclaredMachine(line.busName(), key.toString(), faceCategories[line.switchFace()],
                    line.machinePos(), Direction.from3DDataValue(line.busFace())));
        }
        return out;
    }

    /**
     * Walks the crafting cables reachable from this switch and returns the block each mounted Crafting
     * Input/Receiving Bus points at — how a machine that does not touch the switch itself joins the crafting
     * network. Each machine remembers the switch face its cable run starts at (BFS origin), so the GUI lists it
     * on that face row and the face's active toggle/category govern it. The bus face doubles as the I/O face
     * the engine drives; the bus's editable name doubles as the machine's name.
     */
    private java.util.List<BusMachineLine> collectBusMachines(final java.util.Set<BlockPos> declared) {
        final java.util.List<BusMachineLine> lines = new java.util.ArrayList<>();
        final java.util.Map<BlockPos, Direction> origin = new java.util.HashMap<>();
        final Deque<BlockPos> queue = new ArrayDeque<>();
        for (final Direction direction : Direction.values()) {
            if (!faceActive[direction.get3DDataValue()]) {
                continue; // the face's toggle disables its whole cable run
            }
            final BlockPos neighbor = worldPosition.relative(direction);
            if (level.getBlockState(neighbor).getBlock() instanceof DataCableBlock dataCable
                    && dataCable.tier() == DataTier.CRAFTING && origin.putIfAbsent(neighbor, direction) == null) {
                queue.add(neighbor);
            }
        }
        int steps = 0;
        while (!queue.isEmpty() && steps++ < BFS_STEPS) {
            final BlockPos current = queue.poll();
            if (!(level.getBlockEntity(current) instanceof DataCableBlockEntity cable)) {
                continue;
            }
            final Direction from = origin.get(current);
            for (final Direction face : Direction.values()) {
                if (cable.getPart(face) instanceof dev.jsc.jscomputronics.module.computing.block.part
                        .AbstractBusPart bus
                        && (bus.type() == dev.jsc.jscomputronics.module.computing.block.part.CablePartType.INPUT
                        || bus.type() == dev.jsc.jscomputronics.module.computing.block.part.CablePartType.RECEIVING)) {
                    final BlockPos machinePos = current.relative(face);
                    final var machineBlock = level.getBlockState(machinePos).getBlock();
                    // Network hardware is never a machine, even when a bus happens to point at it.
                    if (machineBlock instanceof DataCableBlock
                            || machineBlock instanceof dev.jstech.core.network.DataNetworkConnectable
                            || !declared.add(machinePos) || level.getBlockEntity(machinePos) == null) {
                        continue;
                    }
                    lines.add(new BusMachineLine(machineBlock.getName().getString(),
                            bus.name() == null ? "" : bus.name(), machinePos, current,
                            face.get3DDataValue(), from.get3DDataValue()));
                }
                final BlockPos neighbor = current.relative(face);
                if (level.getBlockState(neighbor).getBlock() instanceof DataCableBlock next
                        && next.tier() == DataTier.CRAFTING && origin.putIfAbsent(neighbor, from) == null) {
                    queue.add(neighbor);
                }
            }
        }
        return lines;
    }

    // --- persistence + client sync ---

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int i = 0; i < FACES; i++) {
            faceNames[i] = tag.getString("Name" + i);
            faceActive[i] = !tag.contains("Active" + i) || tag.getBoolean("Active" + i);
            faceCategories[i] = tag.getString("Category" + i);
        }
        // Present only in the update tag (client sync of the transient survey), never in the saved chunk data.
        if (tag.contains("MachineMask")) {
            final int mask = tag.getInt("MachineMask");
            for (int i = 0; i < FACES; i++) {
                machinePresent[i] = (mask & (1 << i)) != 0;
            }
            clientLinked = tag.getBoolean("Linked");
            final int cf = tag.getInt("CableFace");
            cableFace = cf < 0 ? null : Direction.from3DDataValue(cf);
            busMachineCount = tag.getInt("BusMachines");
            final net.minecraft.nbt.ListTag lines =
                    tag.getList("BusMachineLines", net.minecraft.nbt.Tag.TAG_COMPOUND);
            final java.util.List<BusMachineLine> parsed = new java.util.ArrayList<>(lines.size());
            for (int i = 0; i < lines.size(); i++) {
                final CompoundTag entry = lines.getCompound(i);
                parsed.add(new BusMachineLine(entry.getString("Block"), entry.getString("Bus"),
                        BlockPos.of(entry.getLong("MPos")), BlockPos.of(entry.getLong("CPos")),
                        entry.getInt("Face"), entry.getInt("SFace")));
            }
            busMachineLines = parsed;
        }
    }

    /** How many machines this switch discovered over its cables via crafting buses — valid on both sides. */
    public int busMachineCount() {
        return busMachineCount;
    }

    /** The bus-discovered machines (block name, bus name, absolute positions) — valid on both sides. */
    public java.util.List<BusMachineLine> busMachineLines() {
        return busMachineLines;
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (int i = 0; i < FACES; i++) {
            tag.putString("Name" + i, faceNames[i]);
            tag.putBoolean("Active" + i, faceActive[i]);
            tag.putString("Category" + i, faceCategories[i]);
        }
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        // The survey results are transient (recomputed server-side every tick) but the GUI reads them on the
        // client, so the update tag carries them; without this the screen always shows UNLINKED / no machine.
        int presentMask = 0;
        for (int i = 0; i < FACES; i++) {
            if (machinePresent[i]) {
                presentMask |= 1 << i;
            }
        }
        tag.putInt("MachineMask", presentMask);
        tag.putBoolean("Linked", linkedComputer != null);
        tag.putInt("CableFace", cableFace == null ? -1 : cableFace.get3DDataValue());
        tag.putInt("BusMachines", busMachineCount);
        final net.minecraft.nbt.ListTag lines = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < busMachineLines.size() && i < 8; i++) {
            final BusMachineLine line = busMachineLines.get(i);
            final CompoundTag entry = new CompoundTag();
            entry.putString("Block", line.blockName());
            entry.putString("Bus", line.busName());
            entry.putLong("MPos", line.machinePos().asLong());
            entry.putLong("CPos", line.cablePos().asLong());
            entry.putInt("Face", line.busFace());
            entry.putInt("SFace", line.switchFace());
            lines.add(entry);
        }
        tag.put("BusMachineLines", lines);
        return tag;
    }


    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
