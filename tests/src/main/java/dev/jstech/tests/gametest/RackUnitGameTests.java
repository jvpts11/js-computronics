/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.gametest;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.common.hardware.DiskSize;
import dev.jsc.jscomputronics.common.hardware.StorageTier;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import dev.jsc.jscomputronics.module.computing.item.DiskItem;
import dev.jsc.jscomputronics.module.computing.item.ServerItem;
import dev.jsc.jscomputronics.module.computing.rack.RackLayout;
import dev.jsc.jscomputronics.module.computing.rack.RaidMode;
import dev.jsc.jscomputronics.module.computing.storage.DriveVolumes;
import dev.jsc.jscomputronics.module.computing.storage.ServerStore;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import dev.jstech.tests.JsTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The rack-unit model of the Server Rack: front-panel hotswap slots belong to the rack, a mounted
 * chassis claims the drive and gadget slots of the rows it occupies, and server storage is the
 * union of the claimed bay drives — so drives (and their data) stay in the rack when the server
 * itself is pulled.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class RackUnitGameTests {

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;

    private static ServerRackBlockEntity placeRack(final GameTestHelper helper, final BlockPos pos) {
        helper.setBlock(pos, ComputingModule.SERVER_RACK.get());
        if (!(helper.getBlockEntity(pos) instanceof ServerRackBlockEntity rack)) {
            throw new IllegalStateException("no server rack at " + pos);
        }
        return rack;
    }

    private static ItemStack nvmeDrive() {
        return new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1));
    }

    /** A hit on the given face of {@code pos}, {@code up} blocks above the block's floor, at its centre. */
    private static BlockHitResult hitOn(final GameTestHelper helper, final BlockPos pos, final Direction face,
                                        final double up) {
        final BlockPos abs = helper.absolutePos(pos);
        final double x = abs.getX() + 0.5 + face.getStepX() * 0.5;
        final double z = abs.getZ() + 0.5 + face.getStepZ() * 0.5;
        return new BlockHitResult(new Vec3(x, abs.getY() + up, z), face, abs, false);
    }

    private static int mountedServers(final ServerRackBlockEntity rack) {
        int count = 0;
        for (int row = 0; row < ServerRackBlockEntity.CAPACITY_U; row++) {
            if (rack.getServers().getStackInSlot(row).getItem() instanceof ServerItem) {
                count++;
            }
        }
        return count;
    }

    @GameTest(template = ARENA)
    public static void mountFromHand_seatsTheUnitInTheRowUnderTheCrosshair(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final ServerRackBlockEntity rack = placeRack(helper, pos);   // faces NORTH: the front is its north face
        final Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    // Half a block up the controller's front is texel 16: the second rack unit from the
                    // bottom, which is row 6 counted from the top.
                    player.setItemInHand(InteractionHand.MAIN_HAND, ComputingModule.defaultServer());
                    helper.useBlock(pos, player, hitOn(helper, pos, Direction.NORTH, 0.5));
                    helper.assertTrue(rack.getServers().getStackInSlot(6).getItem() instanceof ServerItem,
                            "the server seats in the row under the crosshair");
                    helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(),
                            "the hand is empty: the server went in");
                    // The same row again is refused; nothing sneaks into another row.
                    player.setItemInHand(InteractionHand.MAIN_HAND, ComputingModule.defaultServer());
                    helper.useBlock(pos, player, hitOn(helper, pos, Direction.NORTH, 0.5));
                    helper.assertTrue(mountedServers(rack) == 1 && !player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(),
                            "a taken row refuses the unit instead of seating it elsewhere");
                    // The bottom row (texel 8) cannot take a 2U chassis: refused, not moved up.
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ComputingModule.STORAGE_SERVER.get()));
                    helper.useBlock(pos, player, hitOn(helper, pos, Direction.NORTH, 0.25));
                    helper.assertTrue(mountedServers(rack) == 1 && rack.getServers().getStackInSlot(7).isEmpty(),
                            "a 2U chassis aimed at the bottom row is refused");
                    // Aimed at a side, the unit takes the first row that fits, top down.
                    helper.useBlock(pos, player, hitOn(helper, pos, Direction.WEST, 0.5));
                    helper.assertTrue(rack.getServers().getStackInSlot(0).getItem() instanceof ServerItem
                                    && player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(),
                            "from the side the 2U chassis takes the top row");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void frontSlots_roleGatesDriveInsertion(final GameTestHelper helper) {
        final ServerRackBlockEntity rack = placeRack(helper, new BlockPos(2, 2, 2));
        rack.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    // The 1U server chassis cables 3 drive slots and 1 gadget slot in its row.
                    helper.assertTrue(rack.getFrontSlots().insertItem(0, nvmeDrive(), false).isEmpty(),
                            "a drive slot of the occupied row must accept a drive");
                    helper.assertTrue(!rack.getFrontSlots().insertItem(3, nvmeDrive(), false).isEmpty(),
                            "the gadget slot must reject a drive");
                    helper.assertTrue(!rack.getFrontSlots().insertItem(4, nvmeDrive(), false).isEmpty(),
                            "a slot beyond the chassis budgets must reject a drive");
                    helper.assertTrue(!rack.getFrontSlots()
                                    .insertItem(RackLayout.SLOTS_PER_U, nvmeDrive(), false).isEmpty(),
                            "a row with no mounted unit must reject a drive");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void pullingServer_leavesDrivesAndDataInBay(final GameTestHelper helper) {
        final ServerRackBlockEntity rack = placeRack(helper, new BlockPos(2, 2, 2));
        final ItemStack server = ComputingModule.defaultServer();
        rack.getServers().setStackInSlot(0, server);
        helper.assertTrue(rack.insertDrive(0, nvmeDrive()), "the bay must take the first drive");
        helper.assertTrue(rack.insertDrive(0, nvmeDrive()), "the bay must take the second drive");
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerStore store = rack.getServerStorage(0);
                    final long stored = store.insert(StorageKey.of(Items.COBBLESTONE), 100L);
                    helper.assertTrue(stored == 100L, "the bay drives must hold 100 items; got " + stored);

                    // Pull the server: the drives and their data stay with the rack.
                    rack.getServers().setStackInSlot(0, ItemStack.EMPTY);
                    helper.assertTrue(rack.getFrontSlots().getStackInSlot(0).getItem() instanceof DiskItem,
                            "the drives must stay in the front slots when the server is pulled");
                    helper.assertTrue(rack.getServerStorage(0).count(Items.COBBLESTONE) == 0L,
                            "an empty rack unit claims no drives, so it reads no storage");

                    // Mount a chassis over the same rows again: it inherits the drives and the data.
                    rack.getServers().setStackInSlot(0, ComputingModule.defaultServer());
                    final long recovered = rack.getServerStorage(0).count(Items.COBBLESTONE);
                    helper.assertTrue(recovered == 100L,
                            "the next chassis must inherit the bay data; got " + recovered);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void tallChassis_fitsAndBlocksCoveredRows(final GameTestHelper helper) {
        final ServerRackBlockEntity rack = placeRack(helper, new BlockPos(2, 2, 2));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ItemStack storage = new ItemStack(ComputingModule.STORAGE_SERVER.get());
                    helper.assertTrue(rack.getServers().insertItem(0, storage, false).isEmpty(),
                            "a 2U chassis must mount at the top of an empty rack");
                    helper.assertTrue(!rack.getServers()
                                    .insertItem(1, new ItemStack(ComputingModule.SERVER.get()), false).isEmpty(),
                            "the row covered by the 2U chassis must reject another server");
                    helper.assertTrue(rack.getServers()
                                    .insertItem(2, new ItemStack(ComputingModule.SERVER.get()), false).isEmpty(),
                            "the first free row after the 2U chassis must accept a 1U server");
                    helper.assertTrue(!rack.getServers()
                                    .insertItem(7, new ItemStack(ComputingModule.COMPUTE_SERVER.get()), false).isEmpty(),
                            "a 2U chassis must not hang past the bottom of the rack");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void storageChassis_claimsEightDrivesAcrossTwoRows(final GameTestHelper helper) {
        final ServerRackBlockEntity rack = placeRack(helper, new BlockPos(2, 2, 2));
        rack.getServers().setStackInSlot(0, new ItemStack(ComputingModule.STORAGE_SERVER.get()));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    // 8 drive slots row-major: all 5 of the first row, then the first 3 of the second.
                    for (int slot = 0; slot < 8; slot++) {
                        helper.assertTrue(rack.getFrontSlots().insertItem(slot, nvmeDrive(), false).isEmpty(),
                                "drive slot " + slot + " of the storage chassis must accept a drive");
                    }
                    helper.assertTrue(!rack.getFrontSlots().insertItem(8, nvmeDrive(), false).isEmpty(),
                            "slot 8 is a gadget bay and must reject a drive");
                    helper.assertTrue(!rack.getFrontSlots()
                                    .insertItem(RackLayout.SLOTS_PER_U * 2, nvmeDrive(), false).isEmpty(),
                            "the row below the chassis must reject a drive");
                    helper.assertTrue(rack.getServerStorage(0).capacity() > 0,
                            "the unit must read the drives of both of its rows");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void computeChassis_wiresOneDriveOnly(final GameTestHelper helper) {
        final ServerRackBlockEntity rack = placeRack(helper, new BlockPos(2, 2, 2));
        rack.getServers().setStackInSlot(0, new ItemStack(ComputingModule.COMPUTE_SERVER.get()));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(rack.getFrontSlots().insertItem(0, nvmeDrive(), false).isEmpty(),
                            "the compute chassis cables exactly one drive bay");
                    helper.assertTrue(!rack.getFrontSlots().insertItem(1, nvmeDrive(), false).isEmpty(),
                            "slot 1 is the compute chassis' gadget bay and must reject a drive");
                    helper.assertTrue(!rack.getFrontSlots().insertItem(2, nvmeDrive(), false).isEmpty(),
                            "slots beyond the compute chassis budgets must be blocked");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void rackHost_bootsFirmwareThenInstalledOs(final GameTestHelper helper) {
        final ServerRackBlockEntity rack = placeRack(helper, new BlockPos(2, 2, 2));
        rack.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        rack.insertDrive(0, nvmeDrive());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(rack.isRunning(), "a complete powered build makes the host run");
                    helper.assertTrue(rack.needsPost(), "a freshly mounted server runs POST first");
                    helper.assertTrue(dev.jsc.jscomputronics.module.computing.os.boot.BootController
                                    .targetForComputer(rack)
                                    == dev.jsc.jscomputronics.module.computing.os.boot.BootController.BootTarget.FIRMWARE,
                            "with no system on the bay drive the host boots to the firmware");
                    final net.minecraft.resources.ResourceLocation mcNet =
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                    JsComputronics.MODID, "mc_net");
                    helper.assertTrue(rack.installOs(mcNet), "the OS must install onto the bay drive");
                    helper.assertTrue(rack.hasOs(), "the host sees the system on its bay drive");
                    helper.assertTrue(mcNet.equals(rack.installedOsId()),
                            "the installed OS id reads back from the bay drive");
                    helper.assertTrue(rack.getFrontSlots().getStackInSlot(0)
                                    .get(ComputingModule.SYSTEM_OS.get()) != null,
                            "the SYSTEM_OS component lives on the drive itself");
                    helper.assertTrue(dev.jsc.jscomputronics.module.computing.os.boot.BootController
                                    .targetForComputer(rack)
                                    != dev.jsc.jscomputronics.module.computing.os.boot.BootController.BootTarget.FIRMWARE,
                            "with a system installed the host boots past the firmware");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void rackHost_sessionDiesWhenOsDrivePulled(final GameTestHelper helper) {
        final ServerRackBlockEntity rack = placeRack(helper, new BlockPos(2, 2, 2));
        rack.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        rack.insertDrive(0, nvmeDrive());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    rack.installOs(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            JsComputronics.MODID, "mc_net"));
                    helper.assertTrue(rack.validateOsSession(), "an installed system validates the session");
                    // Hotswap the OS drive out: the system travels with it and the session dies.
                    final ItemStack pulled = rack.getFrontSlots().extractItem(0, 1, false);
                    helper.assertTrue(pulled.get(ComputingModule.SYSTEM_OS.get()) != null,
                            "the pulled drive carries the installed system with it");
                    helper.assertTrue(!rack.validateOsSession(),
                            "pulling the OS drive kills the machine's session");
                    helper.assertTrue(!rack.hasOs(), "no drive, no system");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void rackHost_consoleTravelsWithTheServer(final GameTestHelper helper) {
        final ServerRackBlockEntity rackA = placeRack(helper, new BlockPos(1, 2, 1));
        final ServerRackBlockEntity rackB = placeRack(helper, new BlockPos(4, 2, 4));
        rackA.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    rackA.console().pushHistory("uname -a");
                    // Pulling the server flushes the live console onto the item.
                    final ItemStack moved = rackA.getServers().extractItem(0, 1, false);
                    helper.assertTrue(moved.get(ComputingModule.SERVER_CONSOLE.get()) != null,
                            "the extracted server carries its console state");
                    rackB.getServers().setStackInSlot(0, moved);
                    helper.assertTrue(rackB.console().history().contains("uname -a"),
                            "the console history follows the server into the next rack");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void bayPower_switchDropsTheNodeFromTheNetwork(final GameTestHelper helper) {
        final dev.jstech.tests.testkit.TestWorldBuilder world =
                dev.jstech.tests.testkit.TestWorldBuilder.forGameTest(helper);
        final var mainframe = world.placeRunningMainframe(new BlockPos(1, 2, 2));
        helper.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        final ServerRackBlockEntity rack = world.placeSeededRack(new BlockPos(3, 2, 2),
                net.minecraft.core.Direction.EAST); // cables attach through the rear (west here)
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var net = mainframe.networkUuid();
                    helper.assertTrue(net != null, "the mainframe owns a network");
                    helper.assertTrue(dev.jstech.core.network.NetworkSystem
                                    .get(helper.getLevel()).serversOf(net).size() == 1,
                            "the powered bay registers its server on the network");
                    rack.toggleBayPower(0);
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var net = mainframe.networkUuid();
                    helper.assertTrue(dev.jstech.core.network.NetworkSystem
                                    .get(helper.getLevel()).serversOf(net).isEmpty(),
                            "switching the bay off drops the node from the network");
                    helper.assertTrue(!rack.isRunning(), "the delegating host is off with the bay");
                    // Leave a window on the machine's desktop: the power cycle must not carry it over.
                    rack.setOpenWindows(java.util.List.of(
                            new dev.jsc.jscomputronics.module.computing.os.OpenWindow(
                                    "Files", 40, 30, 200, 140, false, false)));
                    rack.toggleBayPower(0);
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var net = mainframe.networkUuid();
                    helper.assertTrue(dev.jstech.core.network.NetworkSystem
                                    .get(helper.getLevel()).serversOf(net).size() == 1,
                            "switching the bay back on re-registers the node");
                    helper.assertTrue(rack.needsPost(), "a power-cycled machine POSTs again");
                    helper.assertTrue(rack.openWindows().isEmpty(),
                            "a power cycle closes every window the machine had open");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void rackWithTwoComputers_hostGoesDark(final GameTestHelper helper) {
        final ServerRackBlockEntity rack = placeRack(helper, new BlockPos(2, 2, 2));
        rack.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        rack.getServers().setStackInSlot(2, ComputingModule.defaultServer());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    // Two computers need a KVM switch for monitor access; the direct host is dark.
                    helper.assertTrue(rack.soleComputerSlot() == -1,
                            "two mounted computers mean no single addressable machine");
                    helper.assertTrue(!rack.isRunning(), "the delegating host reports no machine");
                    helper.assertTrue(rack.console() == null, "no single console to talk to");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void kvmSwitch_letsTheMonitorAddressEachMachine(final GameTestHelper helper) {
        final ServerRackBlockEntity rack = placeRack(helper, new BlockPos(2, 2, 2));
        rack.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        rack.getServers().setStackInSlot(2, ComputingModule.defaultServer());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(!rack.hasKvmSwitch(), "no switch is mounted yet");
                    helper.assertTrue(rack.soleComputerSlot() == -1, "so the monitor cannot pick one");

                    // Mounting the switch is what turns the bays into addressable channels.
                    helper.assertTrue(rack.getServers()
                                    .insertItem(1, new ItemStack(ComputingModule.KVM_SWITCH.get()), false).isEmpty(),
                            "the 1U switch mounts in the free row between the machines");
                    helper.assertTrue(rack.hasKvmSwitch(), "the rack now has a switch");
                    helper.assertTrue(rack.computerSlots().size() == 2,
                            "the switch is not a computer, so it adds no channel");
                    helper.assertTrue(rack.soleComputerSlot() == 0,
                            "the first machine answers by default; got " + rack.soleComputerSlot());

                    rack.setActiveChannel(2);
                    helper.assertTrue(rack.soleComputerSlot() == 2,
                            "switching the channel moves the monitor to the other machine");
                    helper.assertTrue(rack.isRunning(), "the selected machine answers as the host");

                    rack.setActiveChannel(5);
                    helper.assertTrue(rack.soleComputerSlot() == 2,
                            "an empty row is not a channel, so the selection stands");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void rackUnits_spendTheSameRackUnitBudget(final GameTestHelper helper) {
        final ServerRackBlockEntity rack = placeRack(helper, new BlockPos(2, 2, 2));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(rack.getServers()
                                    .insertItem(0, new ItemStack(ComputingModule.STORAGE_SERVER.get()), false).isEmpty(),
                            "the 2U storage chassis mounts at the top");
                    helper.assertTrue(!rack.getServers()
                                    .insertItem(1, new ItemStack(ComputingModule.RACK_UPS.get()), false).isEmpty(),
                            "a rack unit cannot take a row the chassis above already covers");
                    helper.assertTrue(rack.getServers()
                                    .insertItem(2, new ItemStack(ComputingModule.RACK_UPS.get()), false).isEmpty(),
                            "the free row below takes the UPS");
                    helper.assertTrue(rack.getServers()
                                    .insertItem(3, new ItemStack(ComputingModule.COOLING_UNIT.get()), false).isEmpty(),
                            "cooling takes the next row");
                    // Rack equipment cables no front slots: its rows stay blocked.
                    helper.assertTrue(rack.roleOfFrontSlot(2 * RackLayout.SLOTS_PER_U)
                                    == RackLayout.SlotRole.BLOCKED_BUDGET,
                            "a UPS wires no hotswap slots, and the slot says so");
                    helper.assertTrue(rack.computerSlots().size() == 1,
                            "rack units are equipment, never computers");
                })
                .thenSucceed();
    }

    /** A storage chassis with {@code drives} NVMe members and a RAID Controller in its gadget bay. */
    private static ServerRackBlockEntity storageArray(final GameTestHelper helper, final BlockPos pos,
                                                      final int drives) {
        final ServerRackBlockEntity rack = placeRack(helper, pos);
        rack.getServers().setStackInSlot(0, new ItemStack(ComputingModule.STORAGE_SERVER.get()));
        for (int i = 0; i < drives; i++) {
            rack.insertDrive(0, nvmeDrive());
        }
        // The storage chassis cables 8 drive slots then 2 gadget slots: index 8 is the first gadget.
        rack.getFrontSlots().setStackInSlot(8, new ItemStack(ComputingModule.RAID_CONTROLLER.get()));
        return rack;
    }

    @GameTest(template = ARENA)
    public static void raid_modeNeedsEnoughDrivesAndSetsTheLogicalVolume(final GameTestHelper helper) {
        final ServerRackBlockEntity rack = storageArray(helper, new BlockPos(2, 2, 2), 3);
        final long perDrive = ((DiskItem) nvmeDrive().getItem()).spec().capacityItems();
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(rack.raidControllerSlot(0) == 8,
                            "the controller sits in the chassis' first gadget bay");
                    helper.assertTrue(rack.getServerStorage(0).capacity() == perDrive * 3,
                            "without a mode the drives stay independent volumes");

                    helper.assertTrue(rack.setRaidMode(0, RaidMode.RAID5), "3 drives form a RAID 5");
                    helper.assertTrue(rack.getServerStorage(0).capacity() == perDrive * 2,
                            "RAID 5 costs one drive: got " + rack.getServerStorage(0).capacity());

                    helper.assertTrue(rack.setRaidMode(0, RaidMode.RAID1), "3 drives form a mirror");
                    helper.assertTrue(rack.getServerStorage(0).capacity() == perDrive,
                            "a mirror presents a single drive's capacity");

                    helper.assertTrue(rack.setRaidMode(0, RaidMode.RAID0), "3 drives form a stripe");
                    helper.assertTrue(rack.getServerStorage(0).capacity() == perDrive * 3,
                            "a stripe presents the full sum");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void raid_mirrorSurvivesAPulledMemberWithoutDuplicatingItems(final GameTestHelper helper) {
        final ServerRackBlockEntity rack = storageArray(helper, new BlockPos(2, 2, 2), 3);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(rack.setRaidMode(0, RaidMode.RAID1), "the mirror forms");
                    final long stored = rack.getServerStorage(0).insert(StorageKey.of(Items.COBBLESTONE), 900L);
                    helper.assertTrue(stored == 900L, "the mirror must accept 900 items; got " + stored);

                    // Pull a member: the volume survives and the drive comes out blank, so nothing
                    // is duplicated into the player's hands.
                    final ItemStack pulled = rack.getFrontSlots().extractItem(0, 1, false);
                    helper.assertTrue(DriveVolumes.contents(pulled).items().isEmpty(),
                            "a pulled array member must come out blank - never a second copy");
                    helper.assertTrue(rack.getServerStorage(0).count(Items.COBBLESTONE) == 900L,
                            "the degraded mirror still serves every item; got "
                                    + rack.getServerStorage(0).count(Items.COBBLESTONE));
                    helper.assertTrue(rack.raidDegraded(0), "the array reads as degraded");
                    helper.assertTrue(!rack.raidFailed(0), "a mirror down one member has not failed");
                    helper.assertTrue(rack.getServerStorage(0).capacity()
                                    == ((DiskItem) nvmeDrive().getItem()).spec().capacityItems(),
                            "a degraded array keeps the volume size it promised");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void raid_stripeLosesEverythingWhenAMemberIsPulled(final GameTestHelper helper) {
        final ServerRackBlockEntity rack = storageArray(helper, new BlockPos(2, 2, 2), 3);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(rack.setRaidMode(0, RaidMode.RAID0), "the stripe forms");
                    rack.getServerStorage(0).insert(StorageKey.of(Items.COBBLESTONE), 900L);

                    final ItemStack pulled = rack.getFrontSlots().extractItem(0, 1, false);
                    helper.assertTrue(DriveVolumes.contents(pulled).items().isEmpty(),
                            "the pulled member carries nothing out of a dead stripe");
                    helper.assertTrue(rack.getServerStorage(0).count(Items.COBBLESTONE) == 0L,
                            "a broken stripe loses the whole array - that is the honest risk");
                    helper.assertTrue(rack.raidFailed(0), "the stripe reads as failed");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void raid_replacementDriveTriggersAMultiTickRebuild(final GameTestHelper helper) {
        final ServerRackBlockEntity rack = storageArray(helper, new BlockPos(2, 2, 2), 3);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(rack.setRaidMode(0, RaidMode.RAID5), "the parity array forms");
                    rack.getServerStorage(0).insert(StorageKey.of(Items.COBBLESTONE), 500L);
                    rack.getFrontSlots().extractItem(0, 1, false);
                    helper.assertTrue(rack.raidDegraded(0), "the array is degraded after the pull");
                    helper.assertTrue(!rack.raidRebuilding(0), "nothing rebuilds without a replacement");
                    // Slot the replacement drive back in: the rebuild job starts.
                    rack.getFrontSlots().setStackInSlot(0, nvmeDrive());
                    helper.assertTrue(rack.raidRebuilding(0), "a replacement member starts the rebuild");
                    helper.assertTrue(rack.raidRebuildPermille(0) < 1000, "the rebuild starts unfinished");
                })
                .thenExecuteAfter(60, () -> {
                    helper.assertTrue(rack.raidRebuildPermille(0) > 0 || !rack.raidRebuilding(0),
                            "the rebuild advances tick by tick");
                })
                .thenExecuteAfter(120, () -> {
                    helper.assertTrue(!rack.raidRebuilding(0), "the rebuild finishes on its own");
                    helper.assertTrue(!rack.raidDegraded(0), "the rebuilt array is whole again");
                    helper.assertTrue(rack.getServerStorage(0).count(Items.COBBLESTONE) == 500L,
                            "the volume kept its contents through the rebuild");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void hotPull_flagsTheIndexAndAReindexClearsIt(final GameTestHelper helper) {
        final dev.jstech.tests.testkit.TestWorldBuilder world =
                dev.jstech.tests.testkit.TestWorldBuilder.forGameTest(helper);
        final var mainframe = world.placeRunningMainframe(new BlockPos(1, 2, 2));
        helper.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        final ServerRackBlockEntity rack = world.placeSeededRack(new BlockPos(3, 2, 2),
                net.minecraft.core.Direction.EAST);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    rack.getServerStorage(0).insert(StorageKey.of(Items.COBBLESTONE), 64L);
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var index = mainframe.networkIndex();
                    helper.assertTrue(index.health().isClean(), "a settled index starts clean");
                    // Pulling a drive out from under a running machine leaves rows unconfirmed.
                    rack.getFrontSlots().extractItem(0, 1, false);
                    helper.assertTrue(index.health().state()
                                    == dev.jsc.jscomputronics.common.operation.index.IndexHealth.State.STALE,
                            "a hot pull marks the index stale");
                    helper.assertTrue(index.health().recommendedAction().equals("REINDEX"),
                            "the strip asks for a reindex");
                    helper.assertTrue(!index.health().affectedTypes().isEmpty(),
                            "the strip names the affected item types");
                    index.rebuild(helper.getLevel(), mainframe.networkUuid());
                    helper.assertTrue(index.health().isClean(), "a reindex settles the doubt");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void ssh_opensARemoteShellOnARackServer(final GameTestHelper helper) {
        final dev.jstech.tests.testkit.TestWorldBuilder world =
                dev.jstech.tests.testkit.TestWorldBuilder.forGameTest(helper);
        final var mainframe = world.placeRunningMainframe(new BlockPos(1, 2, 2));
        helper.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        final ServerRackBlockEntity rack = world.placeSeededRack(new BlockPos(3, 2, 2),
                net.minecraft.core.Direction.EAST);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    // Name the server so it has a host name worth typing.
                    dev.jsc.jscomputronics.module.computing.item.ServerItem.setCustomName(
                            rack.getServers().getStackInSlot(0), "vault");
                    rack.console().setComputerName("vault");
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var cli = new dev.jsc.jscomputronics.module.computing.program.ServerCliComputer(
                            mainframe, helper.getLevel());
                    helper.assertTrue(cli.reachableHosts().stream()
                                    .anyMatch(h -> h.hostname().equals("vault")),
                            "the rack server shows up as a reachable host");

                    helper.assertTrue(cli.sshConnect("vault").ok(), "ssh connects to the server");
                    helper.assertTrue(mainframe.console().sshTarget() != null,
                            "the session records the machine it is connected to");

                    helper.assertTrue(cli.sshConnect("nowhere").message().contains("host not found"),
                            "an unknown host is reported, not silently ignored");

                    helper.assertTrue(cli.sshDisconnect().ok(), "exit closes the session");
                    helper.assertTrue(mainframe.console().sshTarget() == null,
                            "the session is gone after exit");
                    helper.assertTrue(!cli.sshDisconnect().ok(),
                            "exit with no session says so instead of pretending");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void pckmgr_installsAndTracksThePackageVersion(final GameTestHelper helper) {
        final dev.jstech.tests.testkit.TestWorldBuilder world =
                dev.jstech.tests.testkit.TestWorldBuilder.forGameTest(helper);
        final var mainframe = world.placeRunningMainframe(new BlockPos(1, 2, 2));
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var cli = new dev.jsc.jscomputronics.module.computing.program.ServerCliComputer(
                            mainframe, helper.getLevel());
                    final var console = mainframe.console();
                    final String version = dev.jsc.jscomputronics.module.computing.program
                            .ServerCliComputer.modVersion();

                    // Nothing to reconcile on a machine with no packages.
                    console.install("jsc:test_package");
                    helper.assertTrue(console.installedVersion("jsc:test_package").isEmpty(),
                            "a package installed outside the manager carries no version yet");
                    helper.assertTrue(console.outdatedPackages(version).contains("jsc:test_package"),
                            "an unversioned package reads as outdated");

                    console.setInstalledVersion("jsc:test_package", version);
                    helper.assertTrue(console.outdatedPackages(version).isEmpty(),
                            "a package at the current build is up to date");
                    helper.assertTrue(!version.isEmpty(), "the current build has a version string");
                })
                .thenSucceed();
    }

    /**
     * The era of the machine on the active channel, asked of a cabinet in every state a player can put
     * it in. A server's hardware is a data component, so a machine that was never assembled carries a
     * container with no slots at all — reading the board out of it must answer "no era", not throw.
     */
    @GameTest(template = ARENA)
    public static void displayEra_followsTheActiveChannelAndSurvivesBareHardware(final GameTestHelper helper) {
        final ServerRackBlockEntity rack = placeRack(helper, new BlockPos(2, 2, 2));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(rack.installedEra() == null,
                            "an empty cabinet reports no era");

                    // A server straight from the creative tab: no hardware component was ever written.
                    rack.getServers().setStackInSlot(0, new ItemStack(ComputingModule.SERVER.get()));
                    helper.assertTrue(rack.installedEra() == null,
                            "an unassembled machine reports no era instead of throwing");

                    rack.getServers().setStackInSlot(0, ComputingModule.defaultServer());
                    helper.assertTrue(rack.installedEra() != null,
                            "an assembled machine reports the era of its board");

                    // Two machines and no KVM: the monitor cannot say which one it means, so there is
                    // no channel to read an era from.
                    rack.getServers().setStackInSlot(1, ComputingModule.defaultServer());
                    helper.assertTrue(rack.installedEra() == null,
                            "two machines without a KVM switch address none");

                    rack.getServers().setStackInSlot(2, new ItemStack(ComputingModule.KVM_SWITCH.get()));
                    rack.setActiveChannel(1);
                    helper.assertTrue(rack.installedEra() != null,
                            "with a KVM switch the active channel answers");

                    // The era has to reach the client: screens that dress by era render there.
                    final var tag = rack.getUpdateTag(helper.getLevel().registryAccess());
                    helper.assertTrue(tag.getInt("DisplayEra") >= 0,
                            "the update tag carries the active channel's era to the client");
                })
                .thenSucceed();
    }

    /**
     * The server assembly's expansion slot seats the crafting co-processor only in a Supercomputer
     * Node, and seats a GPU in any server. This goes through the same handler the assembly GUI uses,
     * so it fails the way the player would see it: a slot that will not take the part.
     */
    @GameTest(template = ARENA)
    public static void serverAssembly_seatsThePhiOnlyInANode(final GameTestHelper helper) {
        final net.minecraft.world.entity.player.Player player =
                helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        final net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
        final int slot = dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler.GPU_START;
        final ItemStack phi = new ItemStack(ComputingModule.PHI_5100.get());
        final ItemStack gpu = new ItemStack(ComputingModule.GPU_HD_7970.get());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    player.setItemInHand(hand, new ItemStack(ComputingModule.SUPERCOMPUTER_NODE.get()));
                    final var node = new dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler(player, hand);
                    helper.assertTrue(node.insertItem(slot, phi.copy(), false).isEmpty(),
                            "a node's expansion slot takes the co-processor");
                    // The second slot is for the GPU a machine needs to be sat at — not for a second
                    // co-processor, which the cluster would never count.
                    helper.assertTrue(!node.insertItem(slot + 1, phi.copy(), true).isEmpty(),
                            "a node seats exactly one co-processor");
                    helper.assertTrue(node.insertItem(slot + 1, gpu.copy(), true).isEmpty(),
                            "the node's second expansion slot takes a GPU");

                    player.setItemInHand(hand, new ItemStack(ComputingModule.SERVER.get()));
                    final var server = new dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler(player, hand);
                    helper.assertTrue(!server.insertItem(slot, phi.copy(), true).isEmpty(),
                            "a plain server refuses the co-processor");
                    helper.assertTrue(server.insertItem(slot, gpu.copy(), true).isEmpty(),
                            "a plain server still takes a GPU");
                })
                .thenSucceed();
    }

    /** A server carrying accelerators: the hot machine a dense rack is actually built out of. */
    private static ItemStack hotServer() {
        final ItemStack server = ComputingModule.defaultServer();
        final net.minecraft.core.NonNullList<ItemStack> hw = net.minecraft.core.NonNullList.withSize(
                dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler.SLOTS, ItemStack.EMPTY);
        final var existing = dev.jsc.jscomputronics.module.computing.item.ServerItem.hardware(server);
        for (int i = 0; i < hw.size() && i < existing.getSlots(); i++) {
            hw.set(i, existing.getStackInSlot(i).copy());
        }
        // One accelerator: enough to make the machine hot, still inside the 650W supply.
        hw.set(dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler.GPU_START,
                new ItemStack(ComputingModule.GPU_HD_7970.get()));
        server.set(ComputingModule.SERVER_HARDWARE.get(),
                net.minecraft.world.item.component.ItemContainerContents.fromItems(hw));
        return server;
    }

    @GameTest(template = ARENA)
    public static void coolingUnit_buysBackTheThrottleOfADenseRack(final GameTestHelper helper) {
        final ServerRackBlockEntity rack = placeRack(helper, new BlockPos(2, 2, 2));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(rack.thermalThrottlePercent() == 100,
                            "an empty cabinet runs free");
                    // Fill the cabinet with accelerator-laden machines: this is the dense rack the
                    // thermal budget exists for (plain servers stay comfortably inside it).
                    for (int row = 0; row < 4; row++) {
                        rack.getServers().setStackInSlot(row, hotServer());
                    }
                    helper.assertTrue(rack.thermalLoadWatts() > 0, "mounted machines make heat");
                    helper.assertTrue(rack.thermalThrottled(),
                            "a dense cabinet with no cooling throttles; load " + rack.thermalLoadWatts()
                                    + "W over budget " + rack.thermalBudgetWatts() + "W");
                    final int throttled = rack.thermalThrottlePercent();

                    // A Cooling Unit costs a rack unit and buys the headroom back.
                    rack.getServers().setStackInSlot(5, new ItemStack(ComputingModule.COOLING_UNIT.get()));
                    helper.assertTrue(rack.thermalThrottlePercent() > throttled,
                            "cooling raises the budget and eases the throttle");
                    helper.assertTrue(!rack.thermalThrottled(),
                            "one Cooling Unit clears this cabinet; now "
                                    + rack.thermalLoadWatts() + "W of " + rack.thermalBudgetWatts() + "W");

                    // Switching a bay off takes its heat with it.
                    rack.getServers().setStackInSlot(5, ItemStack.EMPTY);
                    final int hot = rack.thermalLoadWatts();
                    rack.toggleBayPower(0);
                    helper.assertTrue(rack.thermalLoadWatts() < hot,
                            "a powered-off machine stops heating the cabinet");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void serverServices_giveTheMachineItsRole(final GameTestHelper helper) {
        final dev.jstech.tests.testkit.TestWorldBuilder world =
                dev.jstech.tests.testkit.TestWorldBuilder.forGameTest(helper);
        final var mainframe = world.placeRunningMainframe(new BlockPos(1, 2, 2));
        helper.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        final ServerRackBlockEntity rack = world.placeSeededRack(new BlockPos(3, 2, 2),
                net.minecraft.core.Direction.EAST);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertTrue(!rack.hasService(0, "load_balancer"),
                            "a fresh server runs no services");

                    // Load Balancer: each write goes to the emptiest drive, so the bay evens out
                    // instead of filling drive after drive. The first write lands anywhere; what the
                    // service changes is where the SECOND one goes.
                    final long perDrive = ((DiskItem) nvmeDrive().getItem()).spec().capacityItems();
                    rack.getServerStorage(0).insert(StorageKey.of(Items.COBBLESTONE), perDrive / 4);
                    rack.consoleOf(0).install("jsc:load_balancer");
                    rack.getServerStorage(0).insert(StorageKey.of(Items.DIRT), perDrive / 4);
                    long fullest = 0L;
                    long emptiest = Long.MAX_VALUE;
                    for (final ItemStack drive : rack.claimedDriveStacks(0)) {
                        final long used = DriveVolumes.usedWeight(drive);
                        fullest = Math.max(fullest, used);
                        emptiest = Math.min(emptiest, used);
                    }
                    helper.assertTrue(emptiest > 0L,
                            "the balanced write went to the drive the first one skipped");
                    helper.assertTrue(fullest == emptiest,
                            "two equal writes leave the bay even; " + fullest + " vs " + emptiest);

                    // Integrity Monitor: the machine re-reads its own bay, so a hot pull leaves the
                    // index clean instead of stale.
                    rack.consoleOf(0).install("jsc:integrity_monitor");
                    mainframe.networkIndex().health().onFullRebuild();
                    rack.getFrontSlots().extractItem(0, 1, false);
                    helper.assertTrue(mainframe.networkIndex().health().isClean(),
                            "a monitored bay does not leave the index in doubt");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void rackType_takesOnlyItsOwnChassis(final GameTestHelper helper) {
        final ServerRackBlockEntity rack = placeRack(helper, new BlockPos(2, 2, 2));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(rack.rackType()
                                    == dev.jsc.jscomputronics.module.computing.rack.RackChassis.RackType.SERVER,
                            "the Server Rack is the general cabinet");
                    // The three server chassis belong to this cabinet; the supercomputer node does not.
                    for (final var chassis : dev.jsc.jscomputronics.module.computing.rack.RackChassis.values()) {
                        final boolean server = chassis
                                != dev.jsc.jscomputronics.module.computing.rack.RackChassis.SUPERCOMPUTER_NODE;
                        helper.assertTrue((chassis.rackType()
                                        == dev.jsc.jscomputronics.module.computing.rack.RackChassis.RackType.SERVER)
                                        == server,
                                chassis + (server ? " belongs in the Server Rack" : " belongs in the Supercomputer Rack"));
                    }
                    helper.assertTrue(!rack.acceptsChassis(ComputingModule.defaultSupercomputerNode()),
                            "a node is refused by the general cabinet");
                    helper.assertTrue(rack.acceptsChassis(ComputingModule.defaultServer()),
                            "a server chassis fits its own cabinet");
                    // Rack equipment fits every cabinet, whatever its type.
                    helper.assertTrue(rack.acceptsChassis(new ItemStack(ComputingModule.KVM_SWITCH.get())),
                            "rack equipment is not chassis-typed");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void bayGadgets_onlyFitGadgetSlots(final GameTestHelper helper) {
        final ServerRackBlockEntity rack = placeRack(helper, new BlockPos(2, 2, 2));
        rack.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ItemStack controller = new ItemStack(ComputingModule.RAID_CONTROLLER.get());
                    helper.assertTrue(!rack.getFrontSlots().insertItem(0, controller, false).isEmpty(),
                            "a drive bay must reject a gadget");
                    helper.assertTrue(rack.getFrontSlots().insertItem(3, controller, false).isEmpty(),
                            "the server chassis' gadget bay takes the controller");
                    helper.assertTrue(!rack.getFrontSlots()
                                    .insertItem(4, new ItemStack(ComputingModule.CACHE_CARD.get()), false).isEmpty(),
                            "a blocked slot takes nothing");
                    helper.assertTrue(rack.raidModeOf(0) == RaidMode.NONE,
                            "a fresh controller leaves the drives independent");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void bayStorage_capacityComesFromClaimedDrives(final GameTestHelper helper) {
        final ServerRackBlockEntity rack = placeRack(helper, new BlockPos(2, 2, 2));
        rack.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        final ItemStack drive = nvmeDrive();
        final long perDrive = ((DiskItem) drive.getItem()).spec().capacityItems();
        rack.insertDrive(0, drive.copy());
        rack.insertDrive(0, drive.copy());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerStore store = rack.getServerStorage(0);
                    helper.assertTrue(store.capacity() == perDrive * 2,
                            "capacity must be the sum of the claimed drives; got " + store.capacity());
                    helper.assertTrue(rack.bayStorageMb(0)
                                    == ((DiskItem) drive.getItem()).spec().capacityMb() * 2,
                            "the registered node capacity must come from the bay drives");
                    // A drive parked in an unclaimed row counts for nothing.
                    rack.getFrontSlots().setStackInSlot(RackLayout.SLOTS_PER_U * 2, nvmeDrive());
                    helper.assertTrue(rack.getServerStorage(0).capacity() == perDrive * 2,
                            "a drive outside the unit's claimed rows must not add capacity");
                })
                .thenSucceed();
    }
}
