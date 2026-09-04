/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.clienttest;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.block.part.InputBusPart;
import dev.jsc.jscomputronics.module.computing.block.part.ReceivingBusPart;
import dev.jsc.jscomputronics.module.computing.blockentity.CraftingComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.DataCableBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.PatternEncoderBlockEntity;
import dev.jsc.jscomputronics.module.computing.client.PatternEncoderScreen;
import dev.jsc.jscomputronics.module.computing.client.os.CraftingManagerApp;
import dev.jsc.jscomputronics.module.computing.client.os.DesktopScreen;
import dev.jsc.jscomputronics.module.computing.client.os.DesktopWindow;
import dev.jsc.jscomputronics.module.computing.client.os.NetworkInteractorApp;
import dev.jsc.jscomputronics.module.computing.crafting.NetworkRecipe;
import dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern;
import dev.jsc.jscomputronics.module.computing.menu.PatternEncoderMenu;
import dev.jsc.jscomputronics.module.computing.operation.NetworkStorage;
import dev.jsc.jscomputronics.module.computing.os.FilesystemKind;
import dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem;
import dev.jsc.jscomputronics.module.computing.os.fs.FileType;
import dev.jsc.jscomputronics.module.computing.os.media.MediaReaderBlockEntity;
import dev.jsc.jscomputronics.module.computing.program.Programs;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import dev.jsc.jscomputronics.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Client tests for the machine-autocrafting chain as the player experiences it: the Pattern Encoder
 * screen, the media, the Crafting Manager, the request and the machine driven through the switch.
 */
public final class CraftingChainClientTests {

    private CraftingChainClientTests() {
    }

    private static final int SETTLE = 4;
    private static final int SCREEN_WAIT = 40;

    // Pattern Encoder tab bar (mirrors the screen's hit areas: y 19..30; C 8..58, P 60..118, M 120..192).
    private static final int TAB_ROW_Y = 24;
    private static final int TAB_CRAFTING_X = 33;
    private static final int TAB_PROCESSING_X = 89;
    private static final int TAB_MULTI_X = 156;

    private static final BlockPos ENCODER = new BlockPos(1, 2, 4);
    private static final BlockPos PLAYER_AT_ENCODER = new BlockPos(1, 2, 6);

    @ClientTest
    public static void patternEncoder_opensAndTabsRespondToClicks(final ClientTestContext ctx) {
        ctx.thenBuild(0, world -> {
                    world.buildCraftingNetwork();
                    world.setBlock(ENCODER, ComputingModule.PATTERN_ENCODER.get());
                })
                .thenTeleport(SETTLE, PLAYER_AT_ENCODER, Direction.NORTH)
                .thenRightClick(SETTLE, ENCODER)
                .thenAwaitScreen(PatternEncoderScreen.class, SCREEN_WAIT)
                .thenScreenshot(2, "crafting-tab")
                .thenAssert(0, () -> ctx.screen(PatternEncoderScreen.class).activeTab() == PatternEncoderMenu.TAB_CRAFTING,
                        "the encoder opens on the CRAFTING tab")
                .then(0, () -> ctx.clickGui(TAB_PROCESSING_X, TAB_ROW_Y))
                .thenAssert(1, () -> ctx.screen(PatternEncoderScreen.class).activeTab() == PatternEncoderMenu.TAB_PROCESSING,
                        "clicking the PROCESSING tab switches to it")
                .thenScreenshot(2, "processing-tab")
                .then(0, () -> ctx.clickGui(TAB_MULTI_X, TAB_ROW_Y))
                .thenAssert(1, () -> ctx.screen(PatternEncoderScreen.class).activeTab() == PatternEncoderMenu.TAB_MULTI,
                        "clicking the MULTI-STAGE tab switches to it")
                .thenScreenshot(2, "multi-tab")
                .then(0, () -> ctx.clickGui(TAB_CRAFTING_X, TAB_ROW_Y))
                .thenAssert(1, () -> ctx.screen(PatternEncoderScreen.class).activeTab() == PatternEncoderMenu.TAB_CRAFTING,
                        "clicking the CRAFTING tab switches back")
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT);
    }

    // Pattern Encoder slots and controls, as clicked by the player (window-relative centres).
    private static final int MEDIA_SLOT_X = 16;
    private static final int MEDIA_SLOT_Y = 116;
    private static final int INPUT_CELL_X = 16;   // first cell of the PROCESSING inputs grid
    private static final int OUTPUT_CELL_X = 146; // first cell of the PROCESSING outputs grid
    private static final int PROC_CELL_Y = 52;
    private static final int MACHINE_BTN_X = 101;
    private static final int MACHINE_BTN_Y = 50;
    private static final int WRITE_X = 144;
    private static final int WRITE_Y = 116;
    private static final int HOTBAR_Y = 204;

    private static int hotbarX(final int slot) {
        return 16 + slot * 18;
    }

    /**
     * Authors a furnace processing pattern the way the player does: floppy from the hotbar into the media
     * slot, raw iron and an ingot from the hotbar into the ghost grids, the machine from the picker via its
     * search box, then WRITE — and checks the .craft really landed on the floppy.
     */
    @ClientTest
    public static void patternEncoder_writesProcessingPatternThroughTheGui(final ClientTestContext ctx) {
        ctx.thenBuild(0, world -> {
                    world.buildCraftingNetwork();
                    world.setBlock(ENCODER, ComputingModule.PATTERN_ENCODER.get());
                })
                .thenServer(0, level -> {
                    ctx.give(0, new ItemStack(ComputingModule.FLOPPY_DISK.get()));
                    ctx.give(1, new ItemStack(Items.RAW_IRON, 8));
                    ctx.give(2, new ItemStack(Items.IRON_INGOT));
                })
                .thenTeleport(SETTLE, PLAYER_AT_ENCODER, Direction.NORTH)
                .thenRightClick(SETTLE, ENCODER)
                .thenAwaitScreen(PatternEncoderScreen.class, SCREEN_WAIT)
                // Floppy: pick it up from the hotbar, drop it into the media slot.
                .then(2, () -> ctx.clickGui(hotbarX(0), HOTBAR_Y))
                .then(2, () -> ctx.clickGui(MEDIA_SLOT_X, MEDIA_SLOT_Y))
                .thenServer(SETTLE, level -> ctx.assertTrue(
                        encoder(ctx, level).media().getStackInSlot(0).is(ComputingModule.FLOPPY_DISK.get()),
                        "the floppy must sit in the encoder's media slot"))
                // Inputs / outputs on the PROCESSING tab, placed from the carried stack and put back.
                .then(0, () -> ctx.clickGui(TAB_PROCESSING_X, TAB_ROW_Y))
                .then(2, () -> ctx.clickGui(hotbarX(1), HOTBAR_Y))
                .then(2, () -> ctx.clickGui(INPUT_CELL_X, PROC_CELL_Y))
                .then(2, () -> ctx.clickGui(hotbarX(1), HOTBAR_Y))
                .then(2, () -> ctx.clickGui(hotbarX(2), HOTBAR_Y))
                .then(2, () -> ctx.clickGui(OUTPUT_CELL_X, PROC_CELL_Y))
                .then(2, () -> ctx.clickGui(hotbarX(2), HOTBAR_Y))
                .thenServer(SETTLE, level -> {
                    final var be = encoder(ctx, level);
                    ctx.assertTrue(be.procInput(0) != null && be.procInput(0).key().equals(StorageKey.of(Items.RAW_IRON)),
                            "input cell 0 must hold the raw iron placed from the cursor");
                    ctx.assertTrue(be.procOutput(0) != null && be.procOutput(0).key().equals(StorageKey.of(Items.IRON_INGOT)),
                            "output cell 0 must hold the ingot placed from the cursor");
                })
                .thenScreenshot(2, "grids-filled")
                // Machine: open the picker, search, pick the vanilla furnace.
                .then(0, () -> ctx.clickGui(MACHINE_BTN_X, MACHINE_BTN_Y))
                .thenAssert(1, () -> ctx.screen(PatternEncoderScreen.class).isMachinePickerOpen(),
                        "the machine button opens the picker")
                .then(0, () -> ctx.type("furnace"))
                .thenScreenshot(2, "machine-picker")
                .then(0, () -> {
                    final PatternEncoderScreen screen = ctx.screen(PatternEncoderScreen.class);
                    final int row = screen.machinePickerRows().indexOf("minecraft:furnace");
                    ctx.assertTrue(row >= 0, "searching 'furnace' must list minecraft:furnace; rows="
                            + screen.machinePickerRows());
                    ctx.clickGui(screen.machinePickerRowX(), screen.machinePickerRowY(row - screen.machinePickerScroll()));
                })
                .thenAssert(1, () -> !ctx.screen(PatternEncoderScreen.class).isMachinePickerOpen(),
                        "picking a machine closes the picker")
                .thenServer(SETTLE, level -> ctx.assertEquals("minecraft:furnace",
                        encoder(ctx, level).machineType(), "the encoder must store the picked machine"))
                // Write, then prove the .craft is on the floppy.
                .thenScreenshot(2, "ready-to-write")
                .then(0, () -> ctx.clickGui(WRITE_X, WRITE_Y))
                .thenServer(SETTLE + 2, level -> {
                    final ItemStack media = encoder(ctx, level).media().getStackInSlot(0);
                    boolean craft = false;
                    for (final DiskFilesystem.FileEntry e : DiskFilesystem.list(media, "", FilesystemKind.HIERARCHICAL)) {
                        craft |= e.type() == FileType.CRAFT;
                    }
                    ctx.assertTrue(craft, "WRITE PROCESSING must put a .craft file on the floppy");
                })
                .thenScreenshot(2, "written")
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT);
    }

    private static final BlockPos GAS_TANK = new BlockPos(3, 2, 6);
    private static final ResourceLocation OXYGEN = ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen");

    /**
     * Authors a chemical processing pattern the way the player does: a tank item that holds oxygen names the
     * gas in an input cell (a bucket's worth), the amount popup brings it to the 200 mB one purification burns,
     * an estimated cell wears its mark, and WRITE puts the recipe on the floppy with the confirmed amount.
     */
    @ClientTest
    public static void patternEncoder_writesChemicalPatternThroughTheGui(final ClientTestContext ctx) {
        final ItemStack[] gasItem = {ItemStack.EMPTY};
        ctx.thenBuild(0, world -> {
                    world.buildCraftingNetwork();
                    world.setBlock(ENCODER, ComputingModule.PATTERN_ENCODER.get());
                    world.placeFromItem(GAS_TANK, net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                            ResourceLocation.fromNamespaceAndPath("mekanism", "basic_chemical_tank")));
                })
                .thenServer(SETTLE, level -> {
                    // A tank with a little oxygen, picked up: its item carries the gas and names it for the cell.
                    final var port = dev.jsc.jscomputronics.module.computing.storage.ChemicalBridges
                            .portFor(level, ctx.abs(GAS_TANK), Direction.UP);
                    ctx.assertTrue(port.isPresent() && port.get().fill(OXYGEN, 100, false) == 100, "the tank takes oxygen");
                    level.destroyBlock(ctx.abs(GAS_TANK), true);
                })
                .thenServer(2, level -> {
                    for (final net.minecraft.world.entity.item.ItemEntity drop : level.getEntitiesOfClass(
                            net.minecraft.world.entity.item.ItemEntity.class,
                            net.minecraft.world.phys.AABB.encapsulatingFullBlocks(ctx.abs(GAS_TANK.offset(-1, -1, -1)), ctx.abs(GAS_TANK.offset(1, 1, 1))))) {
                        if (dev.jsc.jscomputronics.module.computing.storage.ChemicalBridges.chemicalOf(drop.getItem()).isPresent()) {
                            gasItem[0] = drop.getItem().copy();
                            drop.discard();
                        }
                    }
                    ctx.assertTrue(!gasItem[0].isEmpty(), "picking the tank up must give an item that carries the oxygen");
                    ctx.give(0, new ItemStack(ComputingModule.FLOPPY_DISK.get()));
                    ctx.give(1, gasItem[0]);
                    ctx.give(2, new ItemStack(Items.RAW_IRON));
                })
                .thenTeleport(SETTLE, PLAYER_AT_ENCODER, Direction.NORTH)
                .thenRightClick(SETTLE, ENCODER)
                .thenAwaitScreen(PatternEncoderScreen.class, SCREEN_WAIT)
                .then(2, () -> ctx.clickGui(hotbarX(0), HOTBAR_Y))
                .then(2, () -> ctx.clickGui(MEDIA_SLOT_X, MEDIA_SLOT_Y))
                .then(0, () -> ctx.clickGui(TAB_PROCESSING_X, TAB_ROW_Y))
                // The gas item into the first input cell, then back to the hotbar; the raw iron into the second.
                .then(2, () -> ctx.clickGui(hotbarX(1), HOTBAR_Y))
                .then(2, () -> ctx.clickGui(INPUT_CELL_X, PROC_CELL_Y))
                .then(2, () -> ctx.clickGui(hotbarX(1), HOTBAR_Y))
                .then(2, () -> ctx.clickGui(hotbarX(2), HOTBAR_Y))
                .then(2, () -> ctx.clickGui(INPUT_CELL_X + 18, PROC_CELL_Y))
                .then(2, () -> ctx.clickGui(hotbarX(2), HOTBAR_Y))
                .thenServer(SETTLE, level -> {
                    final var cell = encoder(ctx, level).procInput(0);
                    ctx.assertTrue(cell != null && cell.key().isChemical() && OXYGEN.equals(cell.key().chemicalId())
                                    && cell.amount() == 1000, "the gas item must name oxygen with a bucket's worth; got " + cell);
                    // An estimated cell beside it, the way a recipe transfer leaves one, for the eye.
                    encoder(ctx, level).setProcCell(false, 2, new dev.jsc.jscomputronics.module.computing.blockentity
                            .PatternEncoderBlockEntity.DataCell(StorageKey.chemical(OXYGEN), 200, true));
                })
                .thenScreenshot(2, "chemical-cells")
                // Click the gas cell with an empty hand: the amount popup; -100 eight times brings 1 000 to 200.
                .then(2, () -> ctx.clickGui(INPUT_CELL_X, PROC_CELL_Y))
                .thenAssert(1, () -> ctx.screen(PatternEncoderScreen.class).isAmountPopupOpen(), "a gas cell opens the amount popup")
                .thenScreenshot(2, "amount-popup")
                .then(1, () -> {
                    for (int i = 0; i < 8; i++) {
                        ctx.clickGui(PatternEncoderScreen.amountStepperX(0), PatternEncoderScreen.amountStepperY());
                    }
                })
                .thenServer(SETTLE, level -> ctx.assertTrue(encoder(ctx, level).procInput(0) != null
                        && encoder(ctx, level).procInput(0).amount() == 200, "eight -100 steps must leave 200 mB; got "
                        + encoder(ctx, level).procInput(0)))
                .then(0, () -> ctx.clickGui(PatternEncoderScreen.amountDoneX(), PatternEncoderScreen.amountDoneY()))
                .thenAssert(1, () -> !ctx.screen(PatternEncoderScreen.class).isAmountPopupOpen(), "DONE closes the popup")
                // Output and machine as before, then WRITE.
                .then(2, () -> ctx.clickGui(hotbarX(2), HOTBAR_Y))
                .then(2, () -> ctx.clickGui(OUTPUT_CELL_X, PROC_CELL_Y))
                .then(2, () -> ctx.clickGui(hotbarX(2), HOTBAR_Y))
                .then(0, () -> ctx.clickGui(MACHINE_BTN_X, MACHINE_BTN_Y))
                .then(0, () -> ctx.type("purification"))
                .then(1, () -> {
                    final PatternEncoderScreen screen = ctx.screen(PatternEncoderScreen.class);
                    final int row = screen.machinePickerRows().indexOf("mekanism:purification_chamber");
                    ctx.assertTrue(row >= 0, "searching 'purification' must list the chamber; rows=" + screen.machinePickerRows());
                    ctx.clickGui(screen.machinePickerRowX(), screen.machinePickerRowY(row - screen.machinePickerScroll()));
                })
                .thenServer(SETTLE, level -> {
                    final var built = encoder(ctx, level).buildProcessingPattern();
                    ctx.assertTrue(built.inputs().size() == 3 && built.inputs().get(0).key().isChemical()
                                    && built.inputs().get(0).amount() == 200 && !built.inputs().get(0).estimated(),
                            "the confirmed oxygen input must be 200 mB and no estimate; got " + built.inputs());
                    ctx.assertTrue(built.inputs().get(2).estimated(), "the untouched cell stays an estimate");
                })
                .then(0, () -> ctx.clickGui(WRITE_X, WRITE_Y))
                .thenServer(SETTLE + 2, level -> {
                    final ItemStack media = encoder(ctx, level).media().getStackInSlot(0);
                    boolean craft = false;
                    for (final DiskFilesystem.FileEntry e : DiskFilesystem.list(media, "", FilesystemKind.HIERARCHICAL)) {
                        craft |= e.type() == FileType.CRAFT;
                    }
                    ctx.assertTrue(craft, "WRITE PROCESSING must put the chemical .craft on the floppy");
                })
                .thenScreenshot(2, "chemical-written")
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT);
    }

    private static final BlockPos CRAFTING_COMPUTER = new BlockPos(5, 2, 2);
    private static final BlockPos DRIVE = new BlockPos(5, 2, 3);
    private static final BlockPos MONITOR = new BlockPos(6, 2, 2);
    private static final BlockPos PLAYER_AT_DRIVE = new BlockPos(5, 2, 5);
    private static final BlockPos PLAYER_AT_MONITOR = new BlockPos(8, 2, 2);
    private static final ResourceLocation FRAMES_95 =
            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "frames_95");
    private static final String CRAFTING_MANAGER_LAUNCHER = "Crafting Mgr";

    /**
     * Carries a floppy holding a .craft to the drive beside the Crafting Computer (right-click with it in
     * hand), opens the desktop on the linked monitor, launches the Crafting Manager from the Start menu,
     * selects the file and loads it — and checks the recipe lands in the Recipe ROM.
     */
    @ClientTest(timeoutTicks = 1800)
    public static void craftingManager_loadsACraftFromTheFloppyDrive(final ClientTestContext ctx) {
        ctx.thenBuild(0, world -> {
                    final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
                    // The computer hosts a monitor (needs a GPU) and boots Frames 95 with the Crafting Manager.
                    net.cc().getHardware().setStackInSlot(CraftingComputerBlockEntity.PCIE_SLOTS_START + 1,
                            new ItemStack(ComputingModule.GPU_HD_7970.get()));
                    TestWorldBuilder.installDesktop(net.cc(), FRAMES_95, Programs.CRAFTING_MANAGER);
                    net.cc().togglePower();
                    net.cc().togglePower();
                    world.setBlock(DRIVE, ComputingModule.FLOPPY_DRIVE.get());
                    world.placeMonitor(MONITOR, Direction.EAST);
                    // A floppy already carrying a bench .craft (authored server-side: the encoder GUI has its own test).
                    world.setBlock(ENCODER, ComputingModule.PATTERN_ENCODER.get());
                    final PatternEncoderBlockEntity encoder = world.blockEntity(ENCODER, PatternEncoderBlockEntity.class);
                    encoder.media().setStackInSlot(0, new ItemStack(ComputingModule.FLOPPY_DISK.get()));
                    encoder.setGhost(0, new ItemStack(Items.OAK_LOG));
                    if (!encoder.writePattern()) {
                        throw new IllegalStateException("could not author the .craft for the test");
                    }
                    final ItemStack floppy = encoder.media().getStackInSlot(0);
                    encoder.media().setStackInSlot(0, ItemStack.EMPTY);
                    ctx.give(0, floppy);
                })
                // Insert the floppy: hold it and right-click the drive.
                .thenTeleport(SETTLE, PLAYER_AT_DRIVE, Direction.NORTH)
                .then(SETTLE, () -> {
                    ctx.selectHotbar(0);
                    ctx.rightClick(DRIVE);
                })
                .thenServer(SETTLE, level -> {
                    final MediaReaderBlockEntity drive = drive(ctx, level);
                    ctx.assertTrue(drive.mediaSlot().getStackInSlot(0).is(ComputingModule.FLOPPY_DISK.get()),
                            "right-clicking the drive with the floppy must insert it");
                    ctx.assertEquals(ctx.abs(CRAFTING_COMPUTER), drive.ownerPos(),
                            "the drive must be linked to the adjacent Crafting Computer");
                })
                // Open the desktop on the monitor and launch the Crafting Manager from Start.
                .thenTeleport(0, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, SCREEN_WAIT)
                .thenWaitUntil(() -> ctx.screen(DesktopScreen.class).launcherLabels().contains(CRAFTING_MANAGER_LAUNCHER),
                        SCREEN_WAIT, "the Crafting Manager to be listed as installed")
                .thenScreenshot(2, "desktop")
                .then(0, () -> {
                    final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
                    ctx.click(desktop.startButtonX(), desktop.startButtonY());
                })
                .thenAssert(1, () -> ctx.screen(DesktopScreen.class).isStartOpen(), "the Start button opens the menu")
                .thenScreenshot(2, "start-menu")
                .then(0, () -> {
                    final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
                    final int item = desktop.launcherLabels().indexOf(CRAFTING_MANAGER_LAUNCHER);
                    ctx.click(desktop.startMenuItemX(), desktop.startMenuItemY(item));
                })
                .thenWaitUntil(() -> craftingManager(ctx) != null && craftingManager(ctx).isLoaded(),
                        SCREEN_WAIT, "the Crafting Manager window with its state")
                .thenScreenshot(2, "crafting-manager")
                // Select the file on the medium and load it.
                .then(0, () -> {
                    final CraftingManagerApp app = craftingManager(ctx);
                    ctx.assertTrue(app.hasCard(), "the Crafting Card must be detected");
                    ctx.assertTrue(!app.mediaFiles().isEmpty(), "the floppy's .craft must be listed under the media");
                    ctx.clickDesktop(app.mediaRowCenter(0));
                })
                .then(2, () -> ctx.clickDesktop(craftingManager(ctx).actionButtonCenter(0)))
                .thenWaitUntil(() -> !craftingManager(ctx).romNames().isEmpty(), SCREEN_WAIT,
                        "the loaded recipe to appear in the ROM list")
                .thenScreenshot(2, "loaded")
                .thenServer(0, level -> {
                    if (!(level.getBlockEntity(ctx.abs(CRAFTING_COMPUTER)) instanceof CraftingComputerBlockEntity cc)) {
                        throw new ClientTestFailure("no Crafting Computer at " + ctx.abs(CRAFTING_COMPUTER));
                    }
                    ctx.assertTrue(!cc.romPatterns().isEmpty(), "Load must put the pattern into the Recipe ROM");
                })
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT);
    }

    private static final BlockPos MAINFRAME = new BlockPos(1, 2, 2);
    private static final BlockPos CRAFTING_CABLE = new BlockPos(5, 2, 3);
    private static final BlockPos SWITCH = new BlockPos(5, 2, 4);
    private static final BlockPos FURNACE = new BlockPos(5, 2, 5);
    private static final BlockPos CABLE_ABOVE_FURNACE = new BlockPos(5, 3, 5);
    private static final BlockPos CABLE_BELOW_FURNACE = new BlockPos(5, 1, 5);
    private static final String NETWORK_LAUNCHER = "Network";
    private static final int CRAFT_WAIT = 200;

    /**
     * The player asks the Network Interactor for iron ingots that only a furnace recipe can make: the request
     * must reach the processing engine, which feeds the furnace through the Crafting Input Bus above it and
     * collects through the Crafting Receiving Bus below it, and the ingots must land in network storage.
     */
    @ClientTest(timeoutTicks = 2400)
    public static void networkInteractor_requestDrivesTheFurnaceThroughTheSwitchAndBuses(final ClientTestContext ctx) {
        ctx.thenBuild(0, world -> {
                    final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
                    net.cc().getHardware().setStackInSlot(CraftingComputerBlockEntity.PCIE_SLOTS_START + 1,
                            new ItemStack(ComputingModule.GPU_HD_7970.get()));
                    TestWorldBuilder.installDesktop(net.cc(), FRAMES_95, Programs.CRAFTING_MANAGER);
                    net.cc().togglePower();
                    net.cc().togglePower();
                    world.placeMonitor(MONITOR, Direction.EAST);
                    // The machine: a vanilla furnace (sided: in through the top, out through the bottom) on a
                    // crafting cable run behind the switch, with the two crafting buses aimed at it.
                    world.setBlock(CRAFTING_CABLE, ComputingModule.CRAFTING_CABLE.get());
                    world.setBlock(SWITCH, ComputingModule.CRAFTING_SWITCH.get());
                    world.setBlock(FURNACE, Blocks.FURNACE);
                    world.setBlock(CABLE_ABOVE_FURNACE, ComputingModule.CRAFTING_CABLE.get());
                    world.setBlock(CABLE_BELOW_FURNACE, ComputingModule.CRAFTING_CABLE.get());
                    net.seed(Items.RAW_IRON, 32);
                    // Finished ingots already in the furnace's output slot: collecting them proves the receiving
                    // path without waiting out real smelting (the furnace has no fuel here).
                    if (world.getBlockEntity(FURNACE) instanceof FurnaceBlockEntity furnace) {
                        furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 8));
                    }
                })
                .thenServer(SETTLE + 2, level -> {
                    final TestWorldBuilder world = TestWorldBuilder.at(level, ctx.origin());
                    if (world.getBlockEntity(CABLE_ABOVE_FURNACE) instanceof DataCableBlockEntity c) {
                        c.addPart(Direction.DOWN, new InputBusPart());
                    }
                    if (world.getBlockEntity(CABLE_BELOW_FURNACE) instanceof DataCableBlockEntity c) {
                        c.addPart(Direction.UP, new ReceivingBusPart());
                    }
                    // The furnace recipe sits in the Recipe ROM (loading it through the GUI has its own test).
                    final ProcessingPattern pattern = new ProcessingPattern(
                            List.of(new ProcessingPattern.ProcessingInput(StorageKey.of(Items.RAW_IRON), 1L)),
                            List.of(new ProcessingPattern.ProcessingOutput(StorageKey.of(Items.IRON_INGOT), 1L, 100)),
                            "minecraft:furnace", 200);
                    ctx.assertTrue(world.blockEntity(CRAFTING_COMPUTER, CraftingComputerBlockEntity.class)
                            .loadMachineRecipe(NetworkRecipe.ofProcessing(pattern)), "the furnace recipe loads into the ROM");
                })
                .thenServer(SETTLE + 2, level -> {
                    final TestWorldBuilder world = TestWorldBuilder.at(level, ctx.origin());
                    final MainframeBlockEntity mainframe = world.blockEntity(MAINFRAME, MainframeBlockEntity.class);
                    ctx.assertTrue(!mainframe.networkMachineRecipes().isEmpty(),
                            "the Mainframe must see the Crafting Computer's machine recipe");
                    final var sw = world.blockEntity(SWITCH,
                            dev.jsc.jscomputronics.module.computing.blockentity.CraftingSwitchBlockEntity.class);
                    ctx.assertTrue(sw.declaredMachines().stream().anyMatch(m -> m.machineType().equals("minecraft:furnace")),
                            "the switch must declare the adjacent furnace; declared=" + sw.declaredMachines());
                })
                // Open the desktop, launch the Network Interactor, go to its Crafting tab.
                .thenTeleport(SETTLE, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, SCREEN_WAIT)
                .then(2, () -> {
                    final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
                    ctx.click(desktop.startButtonX(), desktop.startButtonY());
                })
                .then(1, () -> {
                    final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
                    final int item = desktop.launcherLabels().indexOf(NETWORK_LAUNCHER);
                    ctx.assertTrue(item >= 0, "the Start menu must list " + NETWORK_LAUNCHER + "; got " + desktop.launcherLabels());
                    ctx.click(desktop.startMenuItemX(), desktop.startMenuItemY(item));
                })
                .thenWaitUntil(() -> networkInteractor(ctx) != null, SCREEN_WAIT, "the Network Interactor window")
                .then(2, () -> ctx.clickDesktop(networkInteractorPoint(ctx, networkInteractor(ctx).craftingTabCenter())))
                .thenWaitUntil(() -> networkInteractor(ctx).craftableNames().stream().anyMatch(n -> n.contains("Iron Ingot")),
                        SCREEN_WAIT, "the furnace recipe's ingot in the Crafting tab")
                .thenScreenshot(2, "crafting-tab")
                // Request 16 ingots through the popup.
                .then(0, () -> {
                    final NetworkInteractorApp app = networkInteractor(ctx);
                    int index = -1;
                    final List<String> names = app.craftableNames();
                    for (int i = 0; i < names.size(); i++) {
                        if (names.get(i).contains("Iron Ingot")) {
                            index = i;
                        }
                    }
                    ctx.clickDesktop(networkInteractorPoint(ctx, app.craftableCellCenter(index)));
                })
                .thenAssert(1, () -> networkInteractor(ctx).isCraftPopupOpen(), "clicking a craftable opens the request popup")
                .then(1, () -> {
                    final NetworkInteractorApp app = networkInteractor(ctx);
                    // +1 four times, then +64 would overshoot; the popup steps are -64, -1, +1, +64.
                    for (int i = 0; i < 15; i++) {
                        ctx.clickDesktop(networkInteractorPoint(ctx, app.craftPopupStepCenter(2)));
                    }
                })
                .thenAssert(1, () -> networkInteractor(ctx).craftQuantity() == 16, "the quantity steppers must reach 16")
                .thenScreenshot(2, "request-popup")
                .then(0, () -> ctx.clickDesktop(networkInteractorPoint(ctx, networkInteractor(ctx).craftPopupSubmitCenter())))
                .thenAssert(1, () -> !networkInteractor(ctx).isCraftPopupOpen(), "submitting closes the popup")
                // The engine must feed the furnace and collect the ingots into storage.
                .thenServer(SETTLE, level -> {
                    final MainframeBlockEntity mainframe = TestWorldBuilder.at(level, ctx.origin())
                            .blockEntity(MAINFRAME, MainframeBlockEntity.class);
                    ctx.assertTrue(!mainframe.activeOperationRecords().isEmpty() || !mainframe.recentOperations().isEmpty(),
                            "the request must create an operation on the Mainframe");
                })
                .thenWaitUntilServer(level -> level.getBlockEntity(ctx.abs(FURNACE)) instanceof FurnaceBlockEntity furnace
                                && furnace.getItem(0).is(Items.RAW_IRON),
                        CRAFT_WAIT, "the Input Bus to feed raw iron into the furnace", level -> {
                            final TestWorldBuilder world = TestWorldBuilder.at(level, ctx.origin());
                            final MainframeBlockEntity mainframe = world.blockEntity(MAINFRAME, MainframeBlockEntity.class);
                            final var sw = world.blockEntity(SWITCH,
                                    dev.jsc.jscomputronics.module.computing.blockentity.CraftingSwitchBlockEntity.class);
                            return "active=" + mainframe.activeOperationRecords() + " recent=" + mainframe.recentOperations()
                                    + " declared=" + sw.declaredMachines();
                        })
                .thenServer(SETTLE, level -> {
                    final TestWorldBuilder world = TestWorldBuilder.at(level, ctx.origin());
                    final MainframeBlockEntity mainframe = world.blockEntity(MAINFRAME, MainframeBlockEntity.class);
                    final long ingots = NetworkStorage.of(level, mainframe.networkUuid()).count(StorageKey.of(Items.IRON_INGOT));
                    ctx.assertTrue(ingots >= 8, "the Receiving Bus must collect the ingots into storage; got " + ingots);
                })
                .thenScreenshot(2, "after-request")
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT);
    }

    private static final String TERMINAL_LAUNCHER = "Terminal";

    /**
     * The player crafts through the Command Prompt: they open the Terminal, type an IQL {@code operation craft}
     * for a multi-stage-only recipe, and run it. The recursive craft planner never unwraps a multi-stage recipe,
     * so before the CLI and IQL shared the terminal's craft entry point this request could not run at all; now it
     * drives the furnace through the switch and buses exactly like the graphical terminal, and the ingots land in
     * network storage — proving the CLI/IQL is a true alternative interface, not a lesser one.
     */
    @ClientTest(timeoutTicks = 2400)
    public static void commandPrompt_iqlCraftRunsAMultiStageRecipe(final ClientTestContext ctx) {
        ctx.thenBuild(0, world -> {
                    final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
                    net.cc().getHardware().setStackInSlot(CraftingComputerBlockEntity.PCIE_SLOTS_START + 1,
                            new ItemStack(ComputingModule.GPU_HD_7970.get()));
                    TestWorldBuilder.installDesktop(net.cc(), FRAMES_95, Programs.COMMAND_PROMPT);
                    net.cc().togglePower();
                    net.cc().togglePower();
                    world.placeMonitor(MONITOR, Direction.EAST);
                    world.setBlock(CRAFTING_CABLE, ComputingModule.CRAFTING_CABLE.get());
                    world.setBlock(SWITCH, ComputingModule.CRAFTING_SWITCH.get());
                    world.setBlock(FURNACE, Blocks.FURNACE);
                    world.setBlock(CABLE_ABOVE_FURNACE, ComputingModule.CRAFTING_CABLE.get());
                    world.setBlock(CABLE_BELOW_FURNACE, ComputingModule.CRAFTING_CABLE.get());
                    net.seed(Items.RAW_IRON, 32);
                    // Finished ingots already in the furnace output: collecting them proves the receiving path
                    // without waiting out real smelting (the furnace has no fuel here).
                    if (world.getBlockEntity(FURNACE) instanceof FurnaceBlockEntity furnace) {
                        furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 8));
                    }
                })
                .thenServer(SETTLE + 2, level -> {
                    final TestWorldBuilder world = TestWorldBuilder.at(level, ctx.origin());
                    if (world.getBlockEntity(CABLE_ABOVE_FURNACE) instanceof DataCableBlockEntity c) {
                        c.addPart(Direction.DOWN, new InputBusPart());
                    }
                    if (world.getBlockEntity(CABLE_BELOW_FURNACE) instanceof DataCableBlockEntity c) {
                        c.addPart(Direction.UP, new ReceivingBusPart());
                    }
                    // A MULTI-STAGE recipe for iron ingots whose single stage is the furnace smelt.
                    final ProcessingPattern proc = new ProcessingPattern(
                            List.of(new ProcessingPattern.ProcessingInput(StorageKey.of(Items.RAW_IRON), 1L)),
                            List.of(new ProcessingPattern.ProcessingOutput(StorageKey.of(Items.IRON_INGOT), 1L, 100)),
                            "minecraft:furnace", 200);
                    final var multi = new dev.jsc.jscomputronics.module.computing.crafting.MultiStagePattern(
                            List.of(dev.jsc.jscomputronics.module.computing.crafting.MultiStagePattern.Stage.proc(proc)));
                    ctx.assertTrue(world.blockEntity(CRAFTING_COMPUTER, CraftingComputerBlockEntity.class)
                            .loadMachineRecipe(NetworkRecipe.ofMultiStage(multi)),
                            "the multi-stage iron recipe loads into the ROM");
                    // The recursive planner alone is blind to a multi-stage-only recipe, so the CLI/IQL depends on
                    // the shared entry point to run it at all.
                    final MainframeBlockEntity mainframe = world.blockEntity(MAINFRAME, MainframeBlockEntity.class);
                    ctx.assertTrue(mainframe.submitNetworkCraft(StorageKey.of(Items.IRON_INGOT), 1, true, "check") == null,
                            "the recursive planner must not see the multi-stage-only recipe");
                })
                // Open the desktop and launch the Terminal (the Command Prompt) from Start.
                .thenTeleport(SETTLE, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, SCREEN_WAIT)
                .thenWaitUntil(() -> ctx.screen(DesktopScreen.class).launcherLabels().contains(TERMINAL_LAUNCHER),
                        SCREEN_WAIT, "the Terminal to be listed in Start")
                .then(0, () -> {
                    final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
                    ctx.click(desktop.startButtonX(), desktop.startButtonY());
                })
                .thenAssert(1, () -> ctx.screen(DesktopScreen.class).isStartOpen(), "the Start button opens the menu")
                .then(0, () -> {
                    final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
                    ctx.click(desktop.startMenuItemX(),
                            desktop.startMenuItemY(desktop.launcherLabels().indexOf(TERMINAL_LAUNCHER)));
                })
                .thenWaitUntil(() -> ctx.screen(DesktopScreen.class).windowFor(TERMINAL_LAUNCHER) != null,
                        SCREEN_WAIT, "the Terminal window to open")
                .thenScreenshot(2, "terminal")
                // Type the IQL craft and run it with Enter.
                .then(0, () -> ctx.type("operation craft 8 iron_ingot"))
                .thenScreenshot(1, "iql-typed")
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ENTER))
                .thenScreenshot(2, "iql-run")
                .thenServer(SETTLE, level -> {
                    final MainframeBlockEntity mainframe = TestWorldBuilder.at(level, ctx.origin())
                            .blockEntity(MAINFRAME, MainframeBlockEntity.class);
                    ctx.assertTrue(!mainframe.activeOperationRecords().isEmpty() || !mainframe.recentOperations().isEmpty(),
                            "the IQL craft must create an operation on the Mainframe");
                })
                .thenWaitUntilServer(level -> {
                            final TestWorldBuilder world = TestWorldBuilder.at(level, ctx.origin());
                            final MainframeBlockEntity mainframe = world.blockEntity(MAINFRAME, MainframeBlockEntity.class);
                            return NetworkStorage.of(level, mainframe.networkUuid())
                                    .count(StorageKey.of(Items.IRON_INGOT)) >= 8;
                        }, CRAFT_WAIT, "the ingots to reach storage after the IQL craft",
                        level -> {
                            final TestWorldBuilder world = TestWorldBuilder.at(level, ctx.origin());
                            final MainframeBlockEntity mainframe = world.blockEntity(MAINFRAME, MainframeBlockEntity.class);
                            return "iron=" + NetworkStorage.of(level, mainframe.networkUuid())
                                    .count(StorageKey.of(Items.IRON_INGOT))
                                    + " active=" + mainframe.activeOperationRecords()
                                    + " recent=" + mainframe.recentOperations();
                        })
                .thenScreenshot(2, "iql-crafted")
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT);
    }

    /**
     * A recipe loaded into the Recipe ROM must still be there after the world is saved, left and reopened —
     * seen from the Crafting Manager, the way the player would check.
     */
    @ClientTest(timeoutTicks = 3600)
    public static void craftingManager_recipeRomSurvivesSaveAndReload(final ClientTestContext ctx) {
        ctx.thenBuild(0, world -> {
                    final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
                    net.cc().getHardware().setStackInSlot(CraftingComputerBlockEntity.PCIE_SLOTS_START + 1,
                            new ItemStack(ComputingModule.GPU_HD_7970.get()));
                    TestWorldBuilder.installDesktop(net.cc(), FRAMES_95, Programs.CRAFTING_MANAGER);
                    net.cc().togglePower();
                    net.cc().togglePower();
                    world.placeMonitor(MONITOR, Direction.EAST);
                    final ProcessingPattern pattern = new ProcessingPattern(
                            List.of(new ProcessingPattern.ProcessingInput(StorageKey.of(Items.RAW_IRON), 1L)),
                            List.of(new ProcessingPattern.ProcessingOutput(StorageKey.of(Items.IRON_INGOT), 1L, 100)),
                            "minecraft:furnace", 200);
                    ctx.assertTrue(net.cc().loadMachineRecipe(NetworkRecipe.ofProcessing(pattern)),
                            "the furnace recipe loads into the ROM");
                })
                .thenSaveAndReload(SETTLE)
                .thenTeleport(SETTLE, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, SCREEN_WAIT)
                .thenWaitUntil(() -> ctx.screen(DesktopScreen.class).launcherLabels().contains(CRAFTING_MANAGER_LAUNCHER),
                        SCREEN_WAIT, "the Crafting Manager to be listed after the reload")
                .then(0, () -> {
                    final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
                    ctx.click(desktop.startButtonX(), desktop.startButtonY());
                })
                .then(1, () -> {
                    final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
                    ctx.click(desktop.startMenuItemX(), desktop.startMenuItemY(
                            desktop.launcherLabels().indexOf(CRAFTING_MANAGER_LAUNCHER)));
                })
                .thenWaitUntil(() -> craftingManager(ctx) != null && craftingManager(ctx).isLoaded(),
                        SCREEN_WAIT, "the Crafting Manager window with its state")
                .thenScreenshot(2, "after-reload")
                .thenAssert(0, () -> craftingManager(ctx).romNames().stream().anyMatch(n -> n.contains("[machine]")),
                        "the machine recipe must still be in the ROM after the reload")
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT);
    }

    private static final BlockPos PLAYER_AT_SWITCH = new BlockPos(7, 2, 4);

    /**
     * The Crafting Switch GUI must tell the player what the switch sees: LINKED to the computer through its
     * cable face, and the adjacent furnace listed on the face it touches — the state the server surveys and
     * syncs, not something the client could guess.
     */
    @ClientTest
    public static void craftingSwitch_showsTheComputerLinkAndTheMachineOnItsFace(final ClientTestContext ctx) {
        ctx.thenBuild(0, world -> {
                    world.buildCraftingNetwork();
                    world.setBlock(CRAFTING_CABLE, ComputingModule.CRAFTING_CABLE.get());
                    world.setBlock(SWITCH, ComputingModule.CRAFTING_SWITCH.get());
                    world.setBlock(FURNACE, Blocks.FURNACE);
                })
                .thenTeleport(SETTLE + 2, PLAYER_AT_SWITCH, Direction.WEST)
                .thenRightClick(SETTLE, SWITCH)
                .thenAwaitScreen(dev.jsc.jscomputronics.module.computing.client.CraftingSwitchScreen.class, SCREEN_WAIT)
                .thenWaitUntil(() -> ctx.screen(dev.jsc.jscomputronics.module.computing.client.CraftingSwitchScreen.class)
                        .isLinkedShown(), SCREEN_WAIT, "the LINKED pill (survey synced to the client)")
                .thenScreenshot(2, "linked")
                .then(0, () -> {
                    final var screen = ctx.screen(dev.jsc.jscomputronics.module.computing.client.CraftingSwitchScreen.class);
                    final String north = screen.faceRowText(Direction.NORTH.get3DDataValue());
                    final String south = screen.faceRowText(Direction.SOUTH.get3DDataValue());
                    ctx.assertTrue(north.contains("computer"), "the cable face must read as the computer link; got '" + north + "'");
                    ctx.assertTrue(south.toLowerCase(java.util.Locale.ROOT).contains("machine")
                            || south.contains("Furnace"), "the furnace must be listed on the SOUTH face; got '" + south + "'");
                    ctx.clickGui(dev.jsc.jscomputronics.module.computing.client.CraftingSwitchScreen.faceRowX(),
                            dev.jsc.jscomputronics.module.computing.client.CraftingSwitchScreen.faceRowY(
                                    Direction.SOUTH.get3DDataValue()));
                })
                .thenAssert(1, () -> ctx.screen(dev.jsc.jscomputronics.module.computing.client.CraftingSwitchScreen.class)
                        .selectedFace() == Direction.SOUTH.get3DDataValue(), "clicking the SOUTH row selects it")
                .thenScreenshot(2, "south-selected")
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT);
    }

    private static NetworkInteractorApp networkInteractor(final ClientTestContext ctx) {
        final DesktopWindow window = ctx.screen(DesktopScreen.class).windowFor(NETWORK_LAUNCHER);
        return window != null && window.app() instanceof NetworkInteractorApp app ? app : null;
    }

    /** Converts a Network Interactor content-local point into desktop coordinates. */
    private static int[] networkInteractorPoint(final ClientTestContext ctx, final int[] local) {
        final DesktopWindow window = ctx.screen(DesktopScreen.class).windowFor(NETWORK_LAUNCHER);
        if (window == null) {
            throw new ClientTestFailure("the Network Interactor window is gone");
        }
        return new int[]{window.x() + 4 + local[0], window.y() + 18 + local[1]};
    }

    private static CraftingManagerApp craftingManager(final ClientTestContext ctx) {
        final DesktopWindow window = ctx.screen(DesktopScreen.class).windowFor(CRAFTING_MANAGER_LAUNCHER);
        return window != null && window.app() instanceof CraftingManagerApp app ? app : null;
    }

    private static MediaReaderBlockEntity drive(final ClientTestContext ctx, final ServerLevel level) {
        if (level.getBlockEntity(ctx.abs(DRIVE)) instanceof MediaReaderBlockEntity be) {
            return be;
        }
        throw new ClientTestFailure("no floppy drive at " + ctx.abs(DRIVE));
    }

    private static PatternEncoderBlockEntity encoder(final ClientTestContext ctx, final ServerLevel level) {
        if (level.getBlockEntity(ctx.abs(ENCODER)) instanceof PatternEncoderBlockEntity be) {
            return be;
        }
        throw new ClientTestFailure("no Pattern Encoder at " + ctx.abs(ENCODER));
    }
}
