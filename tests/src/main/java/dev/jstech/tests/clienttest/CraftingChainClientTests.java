/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.clienttest;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.JsComputronics;
import dev.jstech.computronics.block.part.InputBusPart;
import dev.jstech.computronics.block.part.ReceivingBusPart;
import dev.jstech.computronics.blockentity.CraftingComputerBlockEntity;
import dev.jstech.computronics.blockentity.DataCableBlockEntity;
import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.blockentity.PatternEncoderBlockEntity;
import dev.jstech.computronics.client.os.CraftingManagerApp;
import dev.jstech.computronics.client.os.DesktopScreen;
import dev.jstech.computronics.client.os.DesktopWindow;
import dev.jstech.computronics.client.os.NetworkInteractorApp;
import dev.jstech.computronics.client.os.PatternStudioApp;
import dev.jstech.computronics.crafting.NetworkRecipe;
import dev.jstech.computronics.crafting.PatternWorkbench;
import dev.jstech.computronics.crafting.ProcessingPattern;
import dev.jstech.computronics.integration.jei.payload.SetProcessingPatternPayload;
import dev.jstech.computronics.operation.NetworkStorage;
import dev.jstech.computronics.os.media.MediaReaderBlockEntity;
import dev.jstech.computronics.program.Programs;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.tests.testkit.CraftFiles;
import dev.jstech.tests.testkit.TestWorldBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Client tests for the machine-autocrafting chain as the player experiences it: the Pattern Studio authoring
 * recipes on a computer and sending them to its encoder, the Crafting Manager loading a disc's craft into the
 * Recipe ROM, the Network Interactor and the Command Prompt requesting crafts that drive a furnace through the
 * switch and buses, and the Crafting Switch GUI reporting what it sees.
 */
public final class CraftingChainClientTests {

    private CraftingChainClientTests() {
    }

    private static final int SETTLE = 4;
    private static final int SCREEN_WAIT = 40;
    /** Long enough for a cold start's POST to play out on the monitor before the desktop shows. */
    private static final int BOOT_WAIT = 400;

    private static final BlockPos CRAFTING_COMPUTER = new BlockPos(5, 2, 2);
    private static final BlockPos DRIVE = new BlockPos(5, 2, 3);
    private static final BlockPos ENCODER = new BlockPos(5, 2, 1);
    private static final BlockPos MONITOR = new BlockPos(6, 2, 2);
    private static final BlockPos PLAYER_AT_DRIVE = new BlockPos(5, 2, 5);
    private static final BlockPos PLAYER_AT_MONITOR = new BlockPos(8, 2, 2);
    private static final ResourceLocation FRAMES_95 =
            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "frames_95");
    /** The Studio and the Crafting Manager need Frames XP or newer, so their Crafting Computers run XP. */
    private static final ResourceLocation FRAMES_XP =
            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "frames_xp");
    /** The Frames desktops name a program by its own display name. */
    private static final String CRAFTING_MANAGER_LAUNCHER = "Crafting Manager";
    private static final String STUDIO_LAUNCHER = "Pattern Studio";

    /**
     * Authors a furnace recipe the way the player does now: on the Pattern Studio, with the raw iron smelt
     * transferred from the recipe viewer (the same payload its transfer button sends, paired with the furnace
     * the data maps it to), with the timeout typed in, then Burn sends it to the encoder beside the computer,
     * which puts the .craft on the disc in its bay.
     */
    @ClientTest(timeoutTicks = 2400)
    public static void patternStudio_burnsATransferredMachineRecipeAtTheEncoder(final ClientTestContext ctx) {
        ctx.thenBuild(0, world -> {
                    final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
                    net.cc().getHardware().setStackInSlot(CraftingComputerBlockEntity.PCIE_SLOTS_START + 1,
                            new ItemStack(ComputingModule.GPU_HD_7970.get()));
                    TestWorldBuilder.installDesktop(net.cc(), FRAMES_XP, Programs.PATTERN_STUDIO);
                    net.cc().togglePower();
                    net.cc().togglePower();
                    world.placeMonitor(MONITOR, Direction.EAST);
                    world.setBlock(ENCODER, ComputingModule.PATTERN_ENCODER.get());
                    world.blockEntity(ENCODER, PatternEncoderBlockEntity.class).media()
                            .setStackInSlot(0, new ItemStack(ComputingModule.DVD_RW.get()));
                })
                .thenWaitUntilServer(level -> encoder(ctx, level).ownerPos() != null, SCREEN_WAIT,
                        "the encoder to link to the adjacent Crafting Computer", level -> "owner=" + encoder(ctx, level).ownerPos())
                .thenTeleport(SETTLE, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                // A cold start runs the firmware's POST on the monitor first; the desktop follows it.
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .thenWaitUntil(() -> ctx.screen(DesktopScreen.class).launcherLabels().contains(STUDIO_LAUNCHER),
                        SCREEN_WAIT, "the Pattern Studio to be listed as installed")
                .then(0, () -> launch(ctx, STUDIO_LAUNCHER))
                .thenWaitUntil(() -> studio(ctx) != null && studio(ctx).isLoaded(), SCREEN_WAIT, "the Studio window with its state")
                .thenAssert(0, () -> studio(ctx).state().encoder().linked(), "the Studio must see the linked encoder")
                // The player's inventory must be there without maximizing: the window the desktop opens fits it.
                .thenAssert(2, () -> studio(ctx).bandShown(), "the Studio's inventory band must show in the default window")
                .thenScreenshot(80, "studio")
                .then(0, () -> JsComputronics.LOGGER.info("[JSC-CT] viewer on the desktop: {}", viewerReport()))
                /*
                 * The viewer's transfer button: with its recipe screen over the desktop, the transfer must still
                 * find the Studio in front (the desktop is the viewer's parent screen there, not the current one).
                 */
                .then(0, () -> ctx.assertTrue(viewerShowsRecipesFor(new ItemStack(Items.IRON_INGOT)),
                        "the viewer must open its recipe screen for the iron ingot"))
                .thenWaitUntil(CraftingChainClientTests::viewerScreenOpen, SCREEN_WAIT, "the viewer's recipe screen")
                .thenScreenshot(2, "viewer-recipes")
                .thenAssert(0, CraftingChainClientTests::viewerTransferAllowed,
                        "the transfer must reach the Studio while the viewer's recipe screen covers the desktop")
                .then(0, () -> {
                    if (viewerScreenOpen()) {
                        ctx.key(GLFW.GLFW_KEY_ESCAPE); // back to the desktop under the viewer
                    }
                })
                .thenAwaitScreen(DesktopScreen.class, SCREEN_WAIT)
                // The raw iron smelt, transferred from the viewer: the payload its transfer button sends.
                .then(0, () -> transferSmelt(ctx))
                .thenWaitUntil(() -> studio(ctx).activeTab() == PatternStudioApp.TAB_MACHINE
                                && "minecraft:furnace".equals(studio(ctx).state().machineType()),
                        SCREEN_WAIT, "the smelt to land in the machine draft paired with the furnace")
                .thenServer(0, level -> {
                    final var studio = cc(ctx, level).studio();
                    ctx.assertTrue(studio.procInput(0) != null && studio.procInput(0).key().equals(StorageKey.of(Items.RAW_IRON)),
                            "the workbench holds raw iron as the input");
                    ctx.assertTrue(studio.procOutput(0) != null && studio.procOutput(0).key().equals(StorageKey.of(Items.IRON_INGOT)),
                            "the workbench holds the ingot as the output");
                })
                // Timeout typed in.
                .then(0, () -> ctx.clickDesktop(studioPoint(ctx, studio(ctx).timeoutFieldCenter())))
                .then(1, () -> {
                    for (int i = 0; i < 6; i++) {
                        ctx.key(GLFW.GLFW_KEY_BACKSPACE);
                    }
                    ctx.type("600");
                    ctx.key(GLFW.GLFW_KEY_ENTER);
                })
                .thenWaitUntilServer(level -> cc(ctx, level).studio().procTimeout() == 600, SCREEN_WAIT,
                        "the typed timeout to reach the workbench", level -> "timeout=" + cc(ctx, level).studio().procTimeout())
                .thenScreenshot(2, "machine-draft")
                // Burn, and prove the .craft lands on the disc in the encoder.
                .then(0, () -> ctx.clickDesktop(studioPoint(ctx, studio(ctx).barButtonCenter(0))))
                .thenWaitUntilServer(level -> encoder(ctx, level).completed() == 1, 200,
                        "the encoder to burn the file", level -> "encoder=" + encoder(ctx, level).statusLine())
                .thenServer(0, level -> ctx.assertTrue(CraftFiles.count(encoder(ctx, level).mediaStack()) == 1,
                        "Burn must put one .craft on the disc"))
                .thenScreenshot(2, "burned")
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT);
    }

    /**
     * Carries a floppy holding a .craft to the drive beside the Crafting Computer (right-click with it in
     * hand), opens the desktop on the linked monitor, launches the Crafting Manager from the Start menu,
     * selects the file and loads it, and checks the recipe lands in the Recipe ROM.
     */
    @ClientTest(timeoutTicks = 1800)
    public static void craftingManager_loadsACraftFromTheFloppyDrive(final ClientTestContext ctx) {
        ctx.thenBuild(0, world -> {
                    final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
                    // The computer hosts a monitor (needs a GPU) and boots Frames XP with the Crafting Manager.
                    net.cc().getHardware().setStackInSlot(CraftingComputerBlockEntity.PCIE_SLOTS_START + 1,
                            new ItemStack(ComputingModule.GPU_HD_7970.get()));
                    TestWorldBuilder.installDesktop(net.cc(), FRAMES_XP, Programs.CRAFTING_MANAGER);
                    net.cc().togglePower();
                    net.cc().togglePower();
                    world.setBlock(DRIVE, ComputingModule.FLOPPY_DRIVE.get());
                    world.placeMonitor(MONITOR, Direction.EAST);
                    // A floppy already carrying a bench .craft, as an encoder leaves it (the Studio has its own test).
                    final ItemStack floppy = new ItemStack(ComputingModule.FLOPPY_DISK.get());
                    CraftFiles.writeBench(floppy, CraftFiles.oakPlanks(), world.level().registryAccess());
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
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
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
                    ctx.click(desktop.startMenuItemX(item), desktop.startMenuItemY(item));
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

    /**
     * Opens the Crafting Manager while the drive is still empty, leaves the monitor, puts the floppy in and
     * comes back: the restored window must list the disc's .craft on its own. A window's program instance
     * outlives the screen it was opened on, so a state asked for only when the program started stayed blank
     * for good, and a disc inserted afterwards was never seen.
     */
    @ClientTest(timeoutTicks = 1800)
    public static void craftingManager_seesADiscInsertedAfterItsWindowOpened(final ClientTestContext ctx) {
        ctx.thenBuild(0, world -> {
                    final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
                    net.cc().getHardware().setStackInSlot(CraftingComputerBlockEntity.PCIE_SLOTS_START + 1,
                            new ItemStack(ComputingModule.GPU_HD_7970.get()));
                    TestWorldBuilder.installDesktop(net.cc(), FRAMES_XP, Programs.CRAFTING_MANAGER);
                    net.cc().togglePower();
                    net.cc().togglePower();
                    world.setBlock(DRIVE, ComputingModule.FLOPPY_DRIVE.get());
                    world.placeMonitor(MONITOR, Direction.EAST);
                    final ItemStack floppy = new ItemStack(ComputingModule.FLOPPY_DISK.get());
                    CraftFiles.writeBench(floppy, CraftFiles.oakPlanks(), world.level().registryAccess());
                    ctx.give(0, floppy);
                })
                // The manager first, with nothing in the drive.
                .thenTeleport(SETTLE, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .thenWaitUntil(() -> ctx.screen(DesktopScreen.class).launcherLabels().contains(CRAFTING_MANAGER_LAUNCHER),
                        SCREEN_WAIT, "the Crafting Manager to be listed as installed")
                .then(0, () -> {
                    final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
                    ctx.click(desktop.startButtonX(), desktop.startButtonY());
                })
                .then(1, () -> {
                    final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
                    final int item = desktop.launcherLabels().indexOf(CRAFTING_MANAGER_LAUNCHER);
                    ctx.click(desktop.startMenuItemX(item), desktop.startMenuItemY(item));
                })
                .thenWaitUntil(() -> craftingManager(ctx) != null && craftingManager(ctx).isLoaded(),
                        SCREEN_WAIT, "the Crafting Manager window with its state")
                .thenAssert(0, () -> craftingManager(ctx).mediaFiles().isEmpty(),
                        "nothing is listed while the drive is empty")
                // Leave the monitor, put the floppy in the drive, come back.
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT)
                .thenTeleport(SETTLE, PLAYER_AT_DRIVE, Direction.NORTH)
                .then(SETTLE, () -> {
                    ctx.selectHotbar(0);
                    ctx.rightClick(DRIVE);
                })
                .thenServer(SETTLE, level -> ctx.assertTrue(
                        drive(ctx, level).mediaSlot().getStackInSlot(0).is(ComputingModule.FLOPPY_DISK.get()),
                        "right-clicking the drive with the floppy must insert it"))
                .thenTeleport(0, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .thenWaitUntil(() -> craftingManager(ctx) != null, SCREEN_WAIT,
                        "the Crafting Manager window to come back with the desktop")
                .thenWaitUntil(() -> !craftingManager(ctx).mediaFiles().isEmpty(), SCREEN_WAIT,
                        "the restored window to list the floppy's .craft on its own")
                .thenScreenshot(2, "crafting-manager-refreshed")
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
                    TestWorldBuilder.installDesktop(net.cc(), FRAMES_XP, Programs.CRAFTING_MANAGER);
                    net.cc().togglePower();
                    net.cc().togglePower();
                    world.placeMonitor(MONITOR, Direction.EAST);
                    /*
                     * The machine: a vanilla furnace (sided: in through the top, out through the bottom) on a
                     * crafting cable run behind the switch, with the two crafting buses aimed at it.
                     */
                    world.setBlock(CRAFTING_CABLE, ComputingModule.CRAFTING_CABLE.get());
                    world.setBlock(SWITCH, ComputingModule.CRAFTING_SWITCH.get());
                    world.setBlock(FURNACE, Blocks.FURNACE);
                    world.setBlock(CABLE_ABOVE_FURNACE, ComputingModule.CRAFTING_CABLE.get());
                    world.setBlock(CABLE_BELOW_FURNACE, ComputingModule.CRAFTING_CABLE.get());
                    net.seed(Items.RAW_IRON, 32);
                    /*
                     * Finished ingots already in the furnace's output slot: collecting them proves the receiving
                     * path without waiting out real smelting (the furnace has no fuel here).
                     */
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
                            dev.jstech.computronics.blockentity.CraftingSwitchBlockEntity.class);
                    ctx.assertTrue(sw.declaredMachines().stream().anyMatch(m -> m.machineType().equals("minecraft:furnace")),
                            "the switch must declare the adjacent furnace; declared=" + sw.declaredMachines());
                })
                // Open the desktop, launch the Network Interactor, go to its Crafting tab.
                .thenTeleport(SETTLE, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .then(2, () -> {
                    final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
                    ctx.click(desktop.startButtonX(), desktop.startButtonY());
                })
                .then(1, () -> {
                    final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
                    final int item = desktop.launcherLabels().indexOf(NETWORK_LAUNCHER);
                    ctx.assertTrue(item >= 0, "the Start menu must list " + NETWORK_LAUNCHER + "; got " + desktop.launcherLabels());
                    ctx.click(desktop.startMenuItemX(item), desktop.startMenuItemY(item));
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
                                    dev.jstech.computronics.blockentity.CraftingSwitchBlockEntity.class);
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

    private static final String PROMPT_LAUNCHER = "Command Prompt";

    /**
     * The player crafts through the Command Prompt: they open it from Start, type an IQL {@code operation craft}
     * for a multi-stage-only recipe, and run it. The recursive craft planner never unwraps a multi-stage recipe,
     * so before the CLI and IQL shared the terminal's craft entry point this request could not run at all; now it
     * drives the furnace through the switch and buses exactly like the graphical terminal, and the ingots land in
     * network storage, proving the CLI/IQL is a true alternative interface, not a lesser one.
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
                    /*
                     * Finished ingots already in the furnace output: collecting them proves the receiving path
                     * without waiting out real smelting (the furnace has no fuel here).
                     */
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
                    final var multi = new dev.jstech.computronics.crafting.MultiStagePattern(
                            List.of(dev.jstech.computronics.crafting.MultiStagePattern.Stage.proc(proc)));
                    ctx.assertTrue(world.blockEntity(CRAFTING_COMPUTER, CraftingComputerBlockEntity.class)
                            .loadMachineRecipe(NetworkRecipe.ofMultiStage(multi)),
                            "the multi-stage iron recipe loads into the ROM");
                    /*
                     * The recursive planner alone is blind to a multi-stage-only recipe, so the CLI/IQL depends on
                     * the shared entry point to run it at all.
                     */
                    final MainframeBlockEntity mainframe = world.blockEntity(MAINFRAME, MainframeBlockEntity.class);
                    ctx.assertTrue(mainframe.submitNetworkCraft(StorageKey.of(Items.IRON_INGOT), 1, true, "check") == null,
                            "the recursive planner must not see the multi-stage-only recipe");
                })
                // Open the desktop and launch the Command Prompt from Start.
                .thenTeleport(SETTLE, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .thenWaitUntil(() -> ctx.screen(DesktopScreen.class).launcherLabels().contains(PROMPT_LAUNCHER),
                        SCREEN_WAIT, "the Command Prompt to be listed in Start")
                .then(0, () -> {
                    final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
                    ctx.click(desktop.startButtonX(), desktop.startButtonY());
                })
                .thenAssert(1, () -> ctx.screen(DesktopScreen.class).isStartOpen(), "the Start button opens the menu")
                .then(0, () -> {
                    final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
                    final int item = desktop.launcherLabels().indexOf(PROMPT_LAUNCHER);
                    ctx.click(desktop.startMenuItemX(item), desktop.startMenuItemY(item));
                })
                .thenWaitUntil(() -> ctx.screen(DesktopScreen.class).windowFor(PROMPT_LAUNCHER) != null,
                        SCREEN_WAIT, "the Command Prompt window to open")
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
     * A recipe loaded into the Recipe ROM must still be there after the world is saved, left and reopened,
     * seen from the Crafting Manager, the way the player would check.
     */
    @ClientTest(timeoutTicks = 3600)
    public static void craftingManager_recipeRomSurvivesSaveAndReload(final ClientTestContext ctx) {
        ctx.thenBuild(0, world -> {
                    final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
                    net.cc().getHardware().setStackInSlot(CraftingComputerBlockEntity.PCIE_SLOTS_START + 1,
                            new ItemStack(ComputingModule.GPU_HD_7970.get()));
                    TestWorldBuilder.installDesktop(net.cc(), FRAMES_XP, Programs.CRAFTING_MANAGER);
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
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .thenWaitUntil(() -> ctx.screen(DesktopScreen.class).launcherLabels().contains(CRAFTING_MANAGER_LAUNCHER),
                        SCREEN_WAIT, "the Crafting Manager to be listed after the reload")
                .then(0, () -> {
                    final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
                    ctx.click(desktop.startButtonX(), desktop.startButtonY());
                })
                .then(1, () -> {
                    final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
                    final int item = desktop.launcherLabels().indexOf(CRAFTING_MANAGER_LAUNCHER);
                    ctx.click(desktop.startMenuItemX(item), desktop.startMenuItemY(item));
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
     * cable face, and the adjacent furnace listed on the face it touches, the state the server surveys and
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
                .thenAwaitScreen(dev.jstech.computronics.client.CraftingSwitchScreen.class, SCREEN_WAIT)
                .thenWaitUntil(() -> ctx.screen(dev.jstech.computronics.client.CraftingSwitchScreen.class)
                        .isLinkedShown(), SCREEN_WAIT, "the LINKED pill (survey synced to the client)")
                .thenScreenshot(2, "linked")
                .then(0, () -> {
                    final var screen = ctx.screen(dev.jstech.computronics.client.CraftingSwitchScreen.class);
                    final String north = screen.faceRowText(Direction.NORTH.get3DDataValue());
                    final String south = screen.faceRowText(Direction.SOUTH.get3DDataValue());
                    ctx.assertTrue(north.contains("computer"), "the cable face must read as the computer link; got '" + north + "'");
                    ctx.assertTrue(south.toLowerCase(java.util.Locale.ROOT).contains("machine")
                            || south.contains("Furnace"), "the furnace must be listed on the SOUTH face; got '" + south + "'");
                    ctx.clickGui(dev.jstech.computronics.client.CraftingSwitchScreen.faceRowX(),
                            dev.jstech.computronics.client.CraftingSwitchScreen.faceRowY(
                                    Direction.SOUTH.get3DDataValue()));
                })
                .thenAssert(1, () -> ctx.screen(dev.jstech.computronics.client.CraftingSwitchScreen.class)
                        .selectedFace() == Direction.SOUTH.get3DDataValue(), "clicking the SOUTH row selects it")
                .thenScreenshot(2, "south-selected")
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT);
    }

    // helpers

    private static void launch(final ClientTestContext ctx, final String label) {
        final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
        ctx.click(desktop.startButtonX(), desktop.startButtonY());
        final int item = desktop.launcherLabels().indexOf(label);
        ctx.assertTrue(item >= 0, "the Start menu must list " + label + "; got " + desktop.launcherLabels());
        ctx.click(desktop.startMenuItemX(item), desktop.startMenuItemY(item));
    }

    private static NetworkInteractorApp networkInteractor(final ClientTestContext ctx) {
        final DesktopWindow window = ctx.screen(DesktopScreen.class).windowFor(NETWORK_LAUNCHER);
        return window != null && window.app() instanceof NetworkInteractorApp app ? app : null;
    }

    /** Converts a Network Interactor content-local point into desktop coordinates. */
    private static int[] networkInteractorPoint(final ClientTestContext ctx, final int[] local) {
        return windowPoint(ctx, NETWORK_LAUNCHER, local);
    }

    private static PatternStudioApp studio(final ClientTestContext ctx) {
        if (!(ctx.mc().screen instanceof DesktopScreen desktop)) {
            return null;
        }
        final DesktopWindow window = desktop.windowFor(STUDIO_LAUNCHER);
        return window != null && window.app() instanceof PatternStudioApp app ? app : null;
    }

    private static int[] studioPoint(final ClientTestContext ctx, final int[] local) {
        return windowPoint(ctx, STUDIO_LAUNCHER, local);
    }

    /** Converts a window's content-local point into desktop coordinates. */
    private static int[] windowPoint(final ClientTestContext ctx, final String label, final int[] local) {
        final DesktopWindow window = ctx.screen(DesktopScreen.class).windowFor(label);
        if (window == null) {
            throw new ClientTestFailure("the " + label + " window is gone");
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

    /** What the recipe viewer reports about the current screen, or a note when it is not installed. */
    private static String viewerReport() {
        if (!ModList.get().isLoaded("jei")) {
            return "viewer not installed";
        }
        return dev.jstech.computronics.integration.jei.JscJeiPlugin.describeOverlay(Minecraft.getInstance().screen);
    }

    // The viewer steps pass trivially without the viewer installed, so the rest of the test still runs.

    private static boolean viewerShowsRecipesFor(final ItemStack result) {
        return !ModList.get().isLoaded("jei")
                || dev.jstech.computronics.integration.jei.JscJeiPlugin.showRecipesFor(result);
    }

    private static boolean viewerScreenOpen() {
        return ModList.get().isLoaded("jei")
                && dev.jstech.computronics.integration.jei.JscJeiPlugin.viewerScreenOpen();
    }

    private static boolean viewerTransferAllowed() {
        return !ModList.get().isLoaded("jei")
                || dev.jstech.computronics.integration.jei.JscJeiPlugin.studioTransferAllowed();
    }

    /** Sends the raw iron smelt to the Studio's machine draft: the payload the viewer's transfer button sends. */
    private static void transferSmelt(final ClientTestContext ctx) {
        final PatternStudioApp app = studio(ctx);
        PacketDistributor.sendToServer(new SetProcessingPatternPayload(app.host(), app.monitorPos(),
                List.of(PatternWorkbench.DataCell.fromStack(new ItemStack(Items.RAW_IRON))),
                List.of(PatternWorkbench.DataCell.fromStack(new ItemStack(Items.IRON_INGOT))),
                "minecraft:smelting"));
    }

    private static CraftingComputerBlockEntity cc(final ClientTestContext ctx, final ServerLevel level) {
        if (level.getBlockEntity(ctx.abs(CRAFTING_COMPUTER)) instanceof CraftingComputerBlockEntity be) {
            return be;
        }
        throw new ClientTestFailure("no Crafting Computer at " + ctx.abs(CRAFTING_COMPUTER));
    }

    private static PatternEncoderBlockEntity encoder(final ClientTestContext ctx, final ServerLevel level) {
        if (level.getBlockEntity(ctx.abs(ENCODER)) instanceof PatternEncoderBlockEntity be) {
            return be;
        }
        throw new ClientTestFailure("no Pattern Encoder at " + ctx.abs(ENCODER));
    }
}
