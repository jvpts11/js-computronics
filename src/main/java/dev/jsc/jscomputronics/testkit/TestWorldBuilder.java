/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.testkit;

import dev.jsc.jscomputronics.common.hardware.DiskSize;
import dev.jsc.jscomputronics.common.hardware.StorageTier;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.block.DataCableBlock;
import dev.jsc.jscomputronics.module.computing.blockentity.CraftingComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.PersonalComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import dev.jsc.jscomputronics.module.computing.operation.NetworkStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.function.UnaryOperator;

/**
 * Builds the standard test scenarios (a booted Mainframe, running computers, a seeded Server Rack, the
 * crafting network) directly in a {@link ServerLevel}, so the same fixtures serve both the headless
 * GameTests and the client-driven tests. Positions handed to this builder are relative: a GameTest maps
 * them through its arena, a client test through the origin of the area it owns.
 */
public final class TestWorldBuilder {

    /** The Network OS id installed on every test Mainframe: the minimal OS that enables orchestration. */
    public static final ResourceLocation NETWORK_OS = ResourceLocation.fromNamespaceAndPath("jsc", "mc_net");

    private final ServerLevel level;
    private final UnaryOperator<BlockPos> toAbsolute;

    private TestWorldBuilder(final ServerLevel level, final UnaryOperator<BlockPos> toAbsolute) {
        this.level = level;
        this.toAbsolute = toAbsolute;
    }

    /** A builder whose relative positions are the GameTest arena's, exactly like {@code helper.setBlock}. */
    public static TestWorldBuilder forGameTest(final GameTestHelper helper) {
        return new TestWorldBuilder(helper.getLevel(), helper::absolutePos);
    }

    /** A builder whose relative positions are offsets from {@code origin}. */
    public static TestWorldBuilder at(final ServerLevel level, final BlockPos origin) {
        return new TestWorldBuilder(level, origin::offset);
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos absolute(final BlockPos relative) {
        return toAbsolute.apply(relative);
    }

    public void setBlock(final BlockPos relative, final Block block) {
        setBlock(relative, block.defaultBlockState());
    }

    public void setBlock(final BlockPos relative, final BlockState state) {
        // Flag 3 (update neighbours + send to clients) matches what GameTestHelper.setBlock does.
        level.setBlock(absolute(relative), state, 3);
    }

    public BlockState getBlockState(final BlockPos relative) {
        return level.getBlockState(absolute(relative));
    }

    public BlockEntity getBlockEntity(final BlockPos relative) {
        return level.getBlockEntity(absolute(relative));
    }

    /** The block entity at {@code relative}, or an {@link IllegalStateException} naming what was expected. */
    public <T extends BlockEntity> T blockEntity(final BlockPos relative, final Class<T> type) {
        final BlockEntity be = getBlockEntity(relative);
        if (type.isInstance(be)) {
            return type.cast(be);
        }
        throw new IllegalStateException("no " + type.getSimpleName() + " at " + relative);
    }

    // Hardware builds

    /**
     * Installs the minimal valid Mainframe build: board, CPU, RAM, PSU, a disk large enough for the Network
     * OS footprint, and the Network OS itself so Operations get dispatched. A valid build never powers on by
     * itself; callers toggle power when they want the machine running.
     */
    public static void installMainframeBuild(final MainframeBlockEntity be) {
        final ItemStackHandler inv = be.getInventory();
        inv.setStackInSlot(MainframeBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(ComputingModule.MOTHERBOARD_MTX_P.get()));
        inv.setStackInSlot(MainframeBlockEntity.CPU_SLOTS_START,
                new ItemStack(ComputingModule.CPU_SERVO_2620.get()));
        inv.setStackInSlot(MainframeBlockEntity.RAM_SLOTS_START,
                new ItemStack(ComputingModule.RAM_DDR3_8192.get()));
        inv.setStackInSlot(MainframeBlockEntity.PSU_SLOT,
                new ItemStack(ComputingModule.PSU_650G.get()));
        // The Network OS needs 512 item-slots of disk; a 500 GB HDD provides 2 000. The disk must be in
        // place before installOs() so the footprint check passes.
        inv.setStackInSlot(MainframeBlockEntity.DISK_SLOTS_START,
                new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)));
        be.installOs(NETWORK_OS);
    }

    /** Places a Mainframe controller, installs the valid build and powers it on. */
    public MainframeBlockEntity placeRunningMainframe(final BlockPos relative) {
        setBlock(relative, ComputingModule.MAINFRAME.get());
        final MainframeBlockEntity be = blockEntity(relative, MainframeBlockEntity.class);
        installMainframeBuild(be);
        be.togglePower();
        return be;
    }

    /** Places a Personal Computer next to a cable (rear toward it), installs its hardware and powers it on. */
    public PersonalComputerBlockEntity placeRunningPersonalComputer(final BlockPos relative) {
        setBlock(relative, ComputingModule.PERSONAL_COMPUTER.get());
        faceRearTowardCable(relative);
        final PersonalComputerBlockEntity be = blockEntity(relative, PersonalComputerBlockEntity.class);
        final ItemStackHandler hw = be.getHardware();
        hw.setStackInSlot(PersonalComputerBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(ComputingModule.MOTHERBOARD_ATX_P.get()));
        hw.setStackInSlot(PersonalComputerBlockEntity.CPU_SLOT,
                new ItemStack(ComputingModule.CPU_ASCENT_965.get()));
        hw.setStackInSlot(PersonalComputerBlockEntity.RAM_SLOTS_START,
                new ItemStack(ComputingModule.RAM_DDR3_8192.get()));
        hw.setStackInSlot(PersonalComputerBlockEntity.PSU_SLOT,
                new ItemStack(ComputingModule.PSU_650G.get()));
        be.togglePower();
        return be;
    }

    /**
     * Places a Crafting Computer next to a cable (rear toward it), installs its hardware including a
     * Crafting Card, and powers it on.
     */
    public CraftingComputerBlockEntity placeRunningCraftingComputer(final BlockPos relative) {
        setBlock(relative, ComputingModule.CRAFTING_COMPUTER.get());
        faceRearTowardCable(relative);
        final CraftingComputerBlockEntity be = blockEntity(relative, CraftingComputerBlockEntity.class);
        final ItemStackHandler hw = be.getHardware();
        hw.setStackInSlot(CraftingComputerBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(ComputingModule.MOTHERBOARD_ATX_P.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.CPU_SLOT,
                new ItemStack(ComputingModule.CPU_ASCENT_965.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.RAM_SLOTS_START,
                new ItemStack(ComputingModule.RAM_DDR3_8192.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.PCIE_SLOTS_START,
                new ItemStack(ComputingModule.CRAFTING_CARD_T2.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.PSU_SLOT,
                new ItemStack(ComputingModule.PSU_650G.get()));
        be.togglePower();
        return be;
    }

    /** Seeds slot 0 of the rack at {@code relative} with the default server. */
    public ServerRackBlockEntity seedServer(final BlockPos relative) {
        final ServerRackBlockEntity rack = blockEntity(relative, ServerRackBlockEntity.class);
        rack.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        return rack;
    }

    /** Places a Server Rack (default orientation) and seeds it with the default server. */
    public ServerRackBlockEntity placeSeededRack(final BlockPos relative) {
        setBlock(relative, ComputingModule.SERVER_RACK.get());
        return seedServer(relative);
    }

    /** Places a Server Rack facing {@code facing} (cables attach through its rear) and seeds it. */
    public ServerRackBlockEntity placeSeededRack(final BlockPos relative, final Direction facing) {
        setBlock(relative, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, facing));
        return seedServer(relative);
    }

    /**
     * Turns a just-placed computer so its rear (its only data port) meets an adjacent horizontal cable.
     * Computers connect through the back face alone, so a fixture that drops one beside a cable must orient
     * it. Blocks without a facing property are left alone.
     */
    public void faceRearTowardCable(final BlockPos relative) {
        final BlockState state = getBlockState(relative);
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return;
        }
        for (final Direction d : Direction.Plane.HORIZONTAL) {
            if (getBlockState(relative.relative(d)).getBlock() instanceof DataCableBlock) {
                setBlock(relative, state.setValue(HorizontalDirectionalBlock.FACING, d.getOpposite()));
                return;
            }
        }
    }

    /**
     * Gives a Crafting Computer a disk, installs the desktop OS {@code os} on it and installs {@code programs}
     * (by id, e.g. {@code jsc:crafting_manager}). Do this before powering the computer so it boots into the OS.
     */
    public static void installDesktop(final CraftingComputerBlockEntity cc, final ResourceLocation os,
                                      final ResourceLocation... programs) {
        cc.getHardware().setStackInSlot(CraftingComputerBlockEntity.DISK_SLOTS_START,
                new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)));
        if (!cc.installOs(os)) {
            throw new IllegalStateException("could not install " + os + " on the Crafting Computer");
        }
        for (final ResourceLocation program : programs) {
            cc.console().install(program.toString());
        }
    }

    /** Places a Monitor facing {@code facing}; it links itself to an adjacent computer within a few ticks. */
    public void placeMonitor(final BlockPos relative, final Direction facing) {
        BlockState state = ComputingModule.MONITOR.get().defaultBlockState();
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            state = state.setValue(HorizontalDirectionalBlock.FACING, facing);
        }
        setBlock(relative, state);
    }

    // Composite scenarios

    /**
     * The assembled crafting network, with handles on the parts assertions need: a booted Mainframe, a rack
     * holding one server (the network's storage) and a running Crafting Computer.
     */
    public record CraftingNetwork(MainframeBlockEntity mainframe, ServerRackBlockEntity rack,
                                  CraftingComputerBlockEntity cc) {

        /** Puts {@code count} of {@code item} straight into the server's store. */
        public void seed(final Item item, final int count) {
            rack.getServerStorage(0).insert(item, count);
        }

        public NetworkStorage storage(final ServerLevel level) {
            return NetworkStorage.of(level, mainframe.networkUuid());
        }
    }

    /**
     * Builds the standard crafting network along the x axis at y=2: Mainframe (1,2,2) — HBW cable (2,2,2)
     * with the rack beside it at (2,2,1) — Personal Router (3,2,2) — Ethernet cable (4,2,2) — Crafting
     * Computer (5,2,2), rear toward the cable.
     */
    public CraftingNetwork buildCraftingNetwork() {
        final MainframeBlockEntity mainframe = placeRunningMainframe(new BlockPos(1, 2, 2));
        setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        final ServerRackBlockEntity rack = placeSeededRack(new BlockPos(2, 2, 1));
        setBlock(new BlockPos(3, 2, 2), ComputingModule.PERSONAL_ROUTER.get());
        setBlock(new BlockPos(4, 2, 2), ComputingModule.ETHERNET_CABLE.get());
        final CraftingComputerBlockEntity cc = placeRunningCraftingComputer(new BlockPos(5, 2, 2));
        return new CraftingNetwork(mainframe, rack, cc);
    }
}
