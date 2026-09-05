/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.JsComputronics;
import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.hardware.DiskSize;
import dev.jstech.computronics.hardware.StorageTier;
import dev.jstech.computronics.operation.payload.ComputingPayloads;
import dev.jstech.computronics.os.OsRegistry;
import dev.jstech.computronics.os.boot.BootController;
import dev.jstech.computronics.os.media.MediaItem;
import dev.jstech.computronics.os.media.MediaKind;
import dev.jstech.computronics.os.media.MediaReaderBlockEntity;
import dev.jstech.core.tier.HardwareEra;
import dev.jstech.tests.JsTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * In-world integration tests for the OS installation layer on computer block entities.
 * Tests verify that an installed OS is persisted through NBT save/load cycles and that it
 * consumes the declared disk footprint from the computer's usable storage.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class OsGameTests {

    private OsGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;

    private static final ResourceLocation SO_REDE =
            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "mc_net");

    private static final ResourceLocation MC_DOS =
            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "mc_dos");

    @GameTest(template = ARENA)
    public static void os_installPersistsAndConsumesDisk(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeRunningMainframeWithDisk(helper, pos);

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    // Precondition: no OS yet.
                    helper.assertFalse(mainframe.hasOs(),
                            "Mainframe must start with no OS installed");

                    // Capture the free storage before installation.
                    final long capacityBefore = mainframe.storageItems();
                    helper.assertTrue(capacityBefore > 0,
                            "Mainframe must report positive storage capacity before OS install");

                    // Install the Network OS.
                    final boolean installed = mainframe.installOs(SO_REDE);
                    helper.assertTrue(installed, "installOs(mc_net) must return true");
                    helper.assertTrue(mainframe.hasOs(), "hasOs() must be true after installation");
                    helper.assertTrue(SO_REDE.equals(mainframe.installedOsId()),
                            "installedOsId() must equal jsc:mc_net");

                    // Storage capacity must drop by exactly the OS footprint.
                    final long footprint = mainframe.reservedByOs();
                    final long capacityAfter = mainframe.storageItems();
                    helper.assertTrue(capacityAfter == capacityBefore - footprint,
                            "storageItems() must drop by the OS footprint (" + footprint
                                    + " items); before=" + capacityBefore + " after=" + capacityAfter);

                    // NBT round-trip: serialise and reload into a fresh BE.
                    final var registries = helper.getLevel().registryAccess();
                    final net.minecraft.nbt.CompoundTag saved =
                            mainframe.saveWithFullMetadata(registries);

                    final MainframeBlockEntity reloaded =
                            new MainframeBlockEntity(helper.absolutePos(pos), mainframe.getBlockState());
                    reloaded.loadWithComponents(saved, registries);

                    helper.assertTrue(reloaded.hasOs(),
                            "hasOs() must be true after NBT round-trip");
                    helper.assertTrue(SO_REDE.equals(reloaded.installedOsId()),
                            "installedOsId() must still be jsc:mc_net after reload; got "
                                    + reloaded.installedOsId());
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void os_installFromLinkedReader(final GameTestHelper helper) {
        final BlockPos mainframePos = new BlockPos(2, 2, 2);
        final BlockPos readerPos = mainframePos.east(); // placed adjacent — no cable needed for adjacency

        // Place a Mainframe with a GPU so it has peripheral ports, then add a Media Reader next to it.
        final MainframeBlockEntity mainframe = placeRunningMainframeWithDisk(helper, mainframePos);
        // Install a GPU so the mainframe has peripheral ports (peripheralPorts from the motherboard).
        mainframe.getInventory().setStackInSlot(MainframeBlockEntity.GPU_SLOTS_START,
                new ItemStack(ComputingModule.GPU_HD_7970.get()));
        helper.setBlock(readerPos, ComputingModule.CD_DRIVE.get());

        if (!(helper.getBlockEntity(readerPos) instanceof MediaReaderBlockEntity reader)) {
            helper.fail("no MediaReaderBlockEntity at " + readerPos);
            return;
        }

        // Stamp a media stack with OS_INSTALL kind and the mc_dos payload.
        final ItemStack media = new ItemStack(ComputingModule.CD_ROM.get());
        MediaItem.setKind(media, MediaKind.OS_INSTALL);
        MediaItem.setPayload(media, MC_DOS);
        reader.mediaSlot().setStackInSlot(0, media);

        helper.startSequence()
                // Wait for the reader's ticker to auto-discover the Mainframe and establish the peripheral link.
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertTrue(reader.ownerPos() != null
                                    && reader.ownerPos().equals(helper.absolutePos(mainframePos)),
                            "media reader must auto-link to the adjacent Mainframe over the peripheral system; "
                                    + "got ownerPos=" + reader.ownerPos());
                    helper.assertTrue(mainframe.linkedEndpoints()
                                    .contains(helper.absolutePos(readerPos).asLong()),
                            "Mainframe must list the reader as a linked endpoint");
                })
                .thenExecute(() -> {
                    // Precondition: mc_dos is registered, no OS on the Mainframe yet.
                    helper.assertTrue(OsRegistry.getOs(MC_DOS) != null,
                            "jsc:mc_dos must be registered before the install test can run");
                    helper.assertFalse(mainframe.hasOs(),
                            "Mainframe must start with no OS");

                    // Run the server-side install logic directly (no client packet needed in a GameTest).
                    final ServerLevel level = helper.getLevel();
                    ComputingPayloads.installOsFromLinkedReader(level, helper.absolutePos(mainframePos));

                    // The OS must now be installed.
                    helper.assertTrue(mainframe.hasOs(),
                            "Mainframe must have an OS after install from linked reader");
                    helper.assertTrue(MC_DOS.equals(mainframe.installedOsId()),
                            "installedOsId() must be jsc:mc_dos; got " + mainframe.installedOsId());

                    // Boot target must resolve to TERMINAL_ONLY (MC-DOS is a CLI-only OS).
                    final BootController.BootTarget target =
                            BootController.targetForComputer(helper.getBlockEntity(mainframePos));
                    helper.assertTrue(target == BootController.BootTarget.TERMINAL_ONLY,
                            "boot target must be TERMINAL_ONLY for mc_dos; got " + target);

                    // Era gating: a LEGACY-minimum OS must NOT install on a Vintage-era hardware.
                    helper.assertTrue(
                            !dev.jstech.computronics.os.OsGating.canInstall(
                                    HardwareEra.LEGACY, HardwareEra.VINTAGE),
                            "canInstall(LEGACY, VINTAGE) must be false (era gate rejects newer OS on older hardware)");
                })
                .thenSucceed();
    }

    /**
     * A Mainframe with no OS must not orchestrate: its tick skips the dispatcher, the Operation
     * index, and the IQL job agent. The dispatcher is never created and submissions return zero.
     * Once an OS is installed the next tick creates the dispatcher, enabling submissions and processing.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void os_mainframeWithoutOsDoesNotOrchestrate(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeRunningMainframeWithDisk(helper, pos);

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertFalse(mainframe.hasOs(),
                            "Mainframe must start with no OS");

                    // Without an OS the tick gate never calls runDispatch(), so the dispatcher is never
                    // created. submitSelfTest guards on dispatch == null and returns 0 (no tasks queued).
                    final int submitted = mainframe.submitSelfTest(3, 1);
                    helper.assertTrue(submitted == 0,
                            "submitSelfTest must return 0 while the Mainframe has no OS; got " + submitted);

                    // The completed count must remain at zero — no dispatch, no progress.
                    helper.assertTrue(mainframe.completedOps() == 0,
                            "completedOps must be 0 before any OS; got " + mainframe.completedOps());
                })
                // Wait beyond the time trivial tasks would complete if the dispatcher were ticking.
                .thenExecuteAfter(20, () -> {
                    helper.assertTrue(mainframe.completedOps() == 0,
                            "no Operations must complete while the Mainframe has no OS; completedOps="
                                    + mainframe.completedOps());

                    // Install the OS — the next tick will create the dispatcher.
                    final boolean installed = mainframe.installOs(SO_REDE);
                    helper.assertTrue(installed, "installOs must succeed after the OS gate is cleared");
                })
                // After the OS is installed, the next tick creates the dispatcher; submit tasks and wait.
                .thenExecuteAfter(2, () -> {
                    final int afterInstall = mainframe.submitSelfTest(3, 1);
                    helper.assertTrue(afterInstall == 3,
                            "submitSelfTest must queue tasks once the OS is installed; got " + afterInstall);
                })
                .thenExecuteAfter(10, () -> helper.assertTrue(
                        mainframe.completedOps() >= 3,
                        "Operations must complete after the OS is installed; completedOps="
                                + mainframe.completedOps()))
                .thenSucceed();
    }

    /**
     * Category-B appliances (the Server Router, the cluster switches) are firmware-only
     * infrastructure; they extend plain {@link net.minecraft.world.level.block.entity.BlockEntity},
     * not AbstractComputerBlockEntity, so they have no OS slot and never route through hasOs() or
     * BootController. The Cluster Management Computer is the counter-example: it manages the racks
     * but is a full computer, with a system of its own. This test places both and checks the
     * classification at runtime.
     */
    @GameTest(template = ARENA)
    public static void os_categoryBApplianceNeedsNoOs(final GameTestHelper helper) {
        final BlockPos mainframePos = new BlockPos(1, 2, 2);
        final BlockPos cablePos = new BlockPos(2, 2, 2);
        final BlockPos routerPos = new BlockPos(3, 2, 2);
        final BlockPos managerPos = new BlockPos(5, 2, 2);

        // Place a running Mainframe WITH an OS (the standard test setup).
        final MainframeBlockEntity mainframe =
                NetworkGameTests.placeRunningMainframe(helper, mainframePos);
        helper.setBlock(cablePos, ComputingModule.HBW_CABLE.get());

        // Place a Server Router (Category B — no OS concept).
        helper.setBlock(routerPos, ComputingModule.SERVER_ROUTER.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.EAST));

        // Place a Cluster Management Computer (Category A — a computer that runs a system).
        helper.setBlock(managerPos, ComputingModule.CLUSTER_MANAGEMENT_COMPUTER.get());

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var routerBe = helper.getBlockEntity(routerPos);
                    helper.assertTrue(routerBe instanceof dev.jstech.computronics.blockentity
                                    .ServerRouterBlockEntity,
                            "the Server Router must create a ServerRouterBlockEntity");
                    helper.assertFalse(
                            routerBe instanceof dev.jstech.computronics.blockentity
                                    .AbstractComputerBlockEntity,
                            "the Server Router must NOT extend AbstractComputerBlockEntity");
                    final var managerBe = helper.getBlockEntity(managerPos);
                    helper.assertTrue(
                            managerBe instanceof dev.jstech.computronics.blockentity
                                    .AbstractComputerBlockEntity,
                            "the Cluster Management Computer is a computer: it extends AbstractComputerBlockEntity");

                    // The Mainframe still owns its network — the appliance does not interfere.
                    helper.assertTrue(mainframe.networkUuid() != null,
                            "Mainframe must own a network regardless of appliance presence");
                })
                .thenSucceed();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Places a running Mainframe with a valid hardware build AND one HDD so that storage capacity
     * is non-zero, giving the OS footprint subtraction something to act on.
     */
    private static MainframeBlockEntity placeRunningMainframeWithDisk(
            final GameTestHelper helper, final BlockPos relative) {
        helper.setBlock(relative, ComputingModule.MAINFRAME.get());
        if (!(helper.getBlockEntity(relative) instanceof MainframeBlockEntity be)) {
            throw new IllegalStateException("no mainframe at " + relative);
        }
        final ItemStackHandler inv = be.getInventory();
        inv.setStackInSlot(MainframeBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(ComputingModule.MOTHERBOARD_MTX_P.get()));
        inv.setStackInSlot(MainframeBlockEntity.CPU_SLOTS_START,
                new ItemStack(ComputingModule.CPU_SERVO_2620.get()));
        inv.setStackInSlot(MainframeBlockEntity.RAM_SLOTS_START,
                new ItemStack(ComputingModule.RAM_DDR3_8192.get()));
        inv.setStackInSlot(MainframeBlockEntity.PSU_SLOT,
                new ItemStack(ComputingModule.PSU_650G.get()));
        // Install one HDD so total storage is positive (2 000 item-slots = 500 GB).
        inv.setStackInSlot(MainframeBlockEntity.DISK_SLOTS_START,
                new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)));
        be.togglePower();
        return be;
    }
}
