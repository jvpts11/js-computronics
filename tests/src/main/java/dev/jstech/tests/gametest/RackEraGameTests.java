/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.HardwareItems;
import dev.jstech.computronics.blockentity.ServerRackBlockEntity;
import dev.jstech.computronics.item.ServerHardwareHandler;
import dev.jstech.tests.JsTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Servers and cabinets have eras. A case takes only boards of its own era; a cabinet seats servers of
 * its own era or earlier. The cabinet is drawn as one model from what each row holds, so the block
 * entity must report every row, the bay power and the service panel the way the renderer reads them.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class RackEraGameTests {

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;

    private RackEraGameTests() {
    }

    private static ServerRackBlockEntity placeRack(final GameTestHelper helper, final BlockPos pos, final Block block) {
        helper.setBlock(pos, block);
        if (!(helper.getBlockEntity(pos) instanceof ServerRackBlockEntity rack)) {
            throw new IllegalStateException("no rack at " + pos);
        }
        return rack;
    }

    private static ItemStack stack(final net.minecraft.world.item.Item item) {
        return new ItemStack(item);
    }

    @GameTest(template = ARENA)
    public static void rackEra_vintageRackSeatsOnlyVintageServers(final GameTestHelper helper) {
        final ServerRackBlockEntity rack = placeRack(helper, new BlockPos(2, 2, 2), ComputingModule.VINTAGE_SERVER_RACK.get());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ItemStackHandler rows = rack.getServers();
                    helper.assertTrue(rows.isItemValid(0, stack(ComputingModule.VINTAGE_SERVER.get())),
                            "a Vintage rack seats a Vintage server");
                    helper.assertFalse(rows.isItemValid(1, stack(ComputingModule.LEGACY_SERVER.get())),
                            "a Vintage rack refuses a Legacy server");
                    helper.assertFalse(rows.isItemValid(2, stack(ComputingModule.SERVER.get())),
                            "a Vintage rack refuses a Standard server");
                    helper.assertTrue(rows.isItemValid(3, stack(ComputingModule.KVM_SWITCH.get())),
                            "rack equipment has no era and still fits");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void rackEra_legacyRackSeatsItsEraAndEarlier(final GameTestHelper helper) {
        final ServerRackBlockEntity rack = placeRack(helper, new BlockPos(2, 2, 2), ComputingModule.LEGACY_SERVER_RACK.get());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ItemStackHandler rows = rack.getServers();
                    helper.assertTrue(rows.isItemValid(0, stack(ComputingModule.VINTAGE_SERVER.get())),
                            "a Legacy rack seats a Vintage server");
                    helper.assertTrue(rows.isItemValid(1, stack(ComputingModule.LEGACY_SERVER.get())),
                            "a Legacy rack seats a Legacy server");
                    helper.assertFalse(rows.isItemValid(2, stack(ComputingModule.SERVER.get())),
                            "a Legacy rack refuses a Standard server");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void rackEra_standardRackSeatsEveryEra(final GameTestHelper helper) {
        final ServerRackBlockEntity rack = placeRack(helper, new BlockPos(2, 2, 2), ComputingModule.SERVER_RACK.get());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ItemStackHandler rows = rack.getServers();
                    helper.assertTrue(rows.isItemValid(0, stack(ComputingModule.VINTAGE_SERVER.get()))
                                    && rows.isItemValid(1, stack(ComputingModule.LEGACY_SERVER.get()))
                                    && rows.isItemValid(2, stack(ComputingModule.SERVER.get())),
                            "a Standard rack seats every era of server");
                    helper.assertFalse(rows.isItemValid(3, stack(ComputingModule.SUPERCOMPUTER_NODE.get())),
                            "the era rule never overrides the cabinet type: a node still needs its own rack");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void serverCase_takesOnlyBoardsOfItsEra(final GameTestHelper helper) {
        final Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        final InteractionHand hand = InteractionHand.MAIN_HAND;
        final ItemStack vintageBoard = stack(HardwareItems.MOTHERBOARD_EEB_VINTAGE.get());
        final ItemStack legacyBoard = stack(HardwareItems.MOTHERBOARD_EATX_LEGACY_S940.get());
        final ItemStack standardBoard = stack(ComputingModule.MOTHERBOARD_EEB_P.get());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    player.setItemInHand(hand, stack(ComputingModule.VINTAGE_SERVER.get()));
                    final ServerHardwareHandler vintage = new ServerHardwareHandler(player, hand);
                    helper.assertTrue(vintage.insertItem(ServerHardwareHandler.MOBO, vintageBoard.copy(), true).isEmpty(),
                            "a Vintage case takes a Vintage server board");
                    helper.assertFalse(vintage.insertItem(ServerHardwareHandler.MOBO, standardBoard.copy(), true).isEmpty(),
                            "a Vintage case refuses a Standard board");

                    player.setItemInHand(hand, stack(ComputingModule.LEGACY_SERVER.get()));
                    final ServerHardwareHandler legacy = new ServerHardwareHandler(player, hand);
                    helper.assertTrue(legacy.insertItem(ServerHardwareHandler.MOBO, legacyBoard.copy(), true).isEmpty(),
                            "a Legacy case takes a Legacy server board");
                    helper.assertFalse(legacy.insertItem(ServerHardwareHandler.MOBO, vintageBoard.copy(), true).isEmpty(),
                            "a Legacy case refuses a Vintage board");

                    player.setItemInHand(hand, stack(ComputingModule.SERVER.get()));
                    final ServerHardwareHandler standard = new ServerHardwareHandler(player, hand);
                    helper.assertTrue(standard.insertItem(ServerHardwareHandler.MOBO, standardBoard.copy(), true).isEmpty(),
                            "a Standard case takes a Standard server board");
                    helper.assertFalse(standard.insertItem(ServerHardwareHandler.MOBO, vintageBoard.copy(), true).isEmpty(),
                            "a Standard case refuses a Vintage board");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void rackModel_reportsWhatEachRowSeats(final GameTestHelper helper) {
        final ServerRackBlockEntity rack = placeRack(helper, new BlockPos(2, 2, 2), ComputingModule.SERVER_RACK.get());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ItemStackHandler rows = rack.getServers();
                    rows.setStackInSlot(0, ComputingModule.defaultServer().copy());   // a Standard 1U in row 0
                    rows.setStackInSlot(1, stack(ComputingModule.STORAGE_SERVER.get()));   // 2U: rows 1 and 2
                    rows.setStackInSlot(3, stack(ComputingModule.LEGACY_SERVER.get()));
                    rows.setStackInSlot(5, stack(ComputingModule.VINTAGE_SERVER.get()));
                    rows.setStackInSlot(6, stack(ComputingModule.KVM_SWITCH.get()));
                    rows.setStackInSlot(7, stack(ComputingModule.RACK_UPS.get()));
                    helper.assertTrue(rack.unitCodeAt(0) == ServerRackBlockEntity.UNIT_SERVER_STANDARD
                                    && rack.unitCodeAt(1) == ServerRackBlockEntity.UNIT_STORAGE_2U
                                    && rack.unitCodeAt(2) == ServerRackBlockEntity.UNIT_NONE
                                    && rack.unitCodeAt(3) == ServerRackBlockEntity.UNIT_SERVER_LEGACY
                                    && rack.unitCodeAt(4) == ServerRackBlockEntity.UNIT_NONE
                                    && rack.unitCodeAt(5) == ServerRackBlockEntity.UNIT_SERVER_VINTAGE
                                    && rack.unitCodeAt(6) == ServerRackBlockEntity.UNIT_KVM_SWITCH
                                    && rack.unitCodeAt(7) == ServerRackBlockEntity.UNIT_RACK_UPS,
                            "every row reports the unit it starts, and the second row of a 2U reports nothing");
                    helper.assertTrue(rack.anyComputerSeated() && rack.anyBayOn(),
                            "seated computers with their bays on spin the fans and light the bar");
                    final CompoundTag tag = rack.getUpdateTag(helper.getLevel().registryAccess());
                    final byte[] units = tag.getByteArray("Units");
                    helper.assertTrue(units.length == ServerRackBlockEntity.CAPACITY_U
                                    && units[1] == ServerRackBlockEntity.UNIT_STORAGE_2U
                                    && units[6] == ServerRackBlockEntity.UNIT_KVM_SWITCH,
                            "the block update carries one unit code per row for the client's model");
                    for (final int computer : rack.computerSlots()) {
                        rack.toggleBayPower(computer);
                    }
                    helper.assertTrue(!rack.anyBayOn() && rack.anyComputerSeated(),
                            "with every bay off the fans stop and the bar shows seated-but-dark");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void supercomputerRack_servicePanelComesOffOnlyThere(final GameTestHelper helper) {
        final ServerRackBlockEntity cabinet = placeRack(helper, new BlockPos(1, 2, 2), ComputingModule.SUPERCOMPUTER_RACK.get());
        final ServerRackBlockEntity rack = placeRack(helper, new BlockPos(5, 2, 2), ComputingModule.SERVER_RACK.get());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertFalse(cabinet.servicePanelOff(), "the livery panel starts on");
                    cabinet.toggleServicePanel();
                    helper.assertTrue(cabinet.servicePanelOff(), "sneak-use takes the panel off");
                    helper.assertTrue(cabinet.getUpdateTag(helper.getLevel().registryAccess()).getBoolean("ServicePanelOff"),
                            "the panel state travels to the client with the block update");
                    cabinet.toggleServicePanel();
                    helper.assertFalse(cabinet.servicePanelOff(), "and puts it back");
                    rack.toggleServicePanel();
                    helper.assertFalse(rack.servicePanelOff(), "a server rack has no panel to take off");
                    cabinet.getServers().setStackInSlot(2, ComputingModule.defaultSupercomputerNode());
                    helper.assertTrue(cabinet.unitCodeAt(2) == ServerRackBlockEntity.UNIT_NODE_2U
                                    && cabinet.unitCodeAt(3) == ServerRackBlockEntity.UNIT_NONE,
                            "a seated node reports its start row for the chassis bands");
                })
                .thenSucceed();
    }
}
