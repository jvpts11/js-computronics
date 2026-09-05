/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.clienttest;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.blockentity.CraftingComputerBlockEntity;
import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.client.CommandPromptScreen;
import dev.jstech.computronics.client.os.DesktopScreen;
import dev.jstech.computronics.client.os.DesktopWindow;
import dev.jstech.computronics.client.os.NetworkInteractorApp;
import dev.jstech.computronics.crafting.CraftingPattern;
import dev.jstech.computronics.crafting.NetworkRecipe;
import dev.jstech.computronics.crafting.ProcessingPattern;
import dev.jstech.computronics.operation.NetworkStorage;
import dev.jstech.computronics.program.Programs;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.tests.gametest.MekanismRig;
import dev.jstech.tests.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The Mekanism build as the player drives it: with the alloy machine patterns and the frame recipe in the Recipe
 * ROM, one request — from the Network Interactor's Crafting tab on the Frames desktop, or typed at the MC-DOS
 * Command Prompt — must plan the whole tree, run the infuser three times over through its buses and finish on
 * the bench with Fusion Reactor Frames.
 */
public final class MekanismClientTests {

    private MekanismClientTests() {
    }

    private static final int SETTLE = 4;
    private static final int SCREEN_WAIT = 40;
    /** Long enough for a cold start's POST to play out on the monitor before the desktop shows. */
    private static final int BOOT_WAIT = 400;
    private static final BlockPos MAINFRAME = new BlockPos(1, 2, 2);
    private static final BlockPos CRAFTING_COMPUTER = new BlockPos(5, 2, 2);
    private static final BlockPos MONITOR = new BlockPos(6, 2, 2);
    private static final BlockPos PLAYER_AT_MONITOR = new BlockPos(8, 2, 2);
    private static final String NETWORK_LAUNCHER = "Network";
    /** The Crafting Manager needs Frames XP or newer. */
    private static final ResourceLocation FRAMES_XP = ResourceLocation.fromNamespaceAndPath("jsc", "frames_xp");
    private static final ResourceLocation MC_DOS = ResourceLocation.fromNamespaceAndPath("jsc", "mc_dos");
    private static final ResourceLocation INFUSER = MekanismRig.mek("metallurgic_infuser");
    private static final ResourceLocation ALLOY_INFUSED = MekanismRig.mek("alloy_infused");
    private static final ResourceLocation ALLOY_REINFORCED = MekanismRig.mek("alloy_reinforced");
    private static final ResourceLocation ALLOY_ATOMIC = MekanismRig.mek("alloy_atomic");
    private static final ResourceLocation DUST_DIAMOND = MekanismRig.mek("dust_diamond");
    private static final ResourceLocation DUST_REFINED_OBSIDIAN = MekanismRig.mek("dust_refined_obsidian");
    private static final ResourceLocation PELLET_POLONIUM = MekanismRig.mek("pellet_polonium");
    private static final ResourceLocation STEEL_CASING = MekanismRig.mek("steel_casing");
    private static final ResourceLocation FRAME = MekanismRig.generators("fusion_reactor_frame");
    private static final String FRAME_NAME = "Fusion Reactor Frame";

    private static ProcessingPattern infuse(final StorageKey in, final StorageKey extra, final long extraCount,
                                            final StorageKey out) {
        return new ProcessingPattern(
                List.of(new ProcessingPattern.ProcessingInput(in, 1), new ProcessingPattern.ProcessingInput(extra, extraCount)),
                List.of(new ProcessingPattern.ProcessingOutput(out, 1, 100)),
                INFUSER.toString(), 400);
    }

    private static CraftingPattern framePattern() {
        final List<ItemStack> grid = new ArrayList<>(CraftingPattern.GRID_SIZE);
        for (int i = 0; i < CraftingPattern.GRID_SIZE; i++) {
            grid.add(ItemStack.EMPTY);
        }
        for (final int corner : new int[]{0, 2, 6, 8}) {
            grid.set(corner, new ItemStack(MekanismRig.item(ALLOY_ATOMIC)));
        }
        for (final int edge : new int[]{1, 3, 5, 7}) {
            grid.set(edge, new ItemStack(MekanismRig.item(PELLET_POLONIUM)));
        }
        grid.set(4, new ItemStack(MekanismRig.item(STEEL_CASING)));
        return new CraftingPattern(grid, new ItemStack(MekanismRig.item(FRAME), 4));
    }

    @ClientTest(timeoutTicks = 6000)
    public static void networkInteractor_requestPlansTheAlloyChainThroughTheInfuser(final ClientTestContext ctx) {
        ctx.thenBuild(0, world -> {
                    final TestWorldBuilder.CraftingNetwork net = MekanismRig.place(world, INFUSER);
                    net.cc().getHardware().setStackInSlot(CraftingComputerBlockEntity.PCIE_SLOTS_START + 1,
                            new ItemStack(ComputingModule.GPU_HD_7970.get()));
                    TestWorldBuilder.installDesktop(net.cc(), FRAMES_XP, Programs.CRAFTING_MANAGER);
                    net.cc().togglePower();
                    net.cc().togglePower();
                    world.placeMonitor(MONITOR, Direction.EAST);
                    // Raw stock sized for exactly one bench run of frames, straight into the server's store
                    // (the network itself only forms over the next ticks).
                    net.seed(Items.COPPER_INGOT, 4);
                    net.seed(Items.REDSTONE, 4);
                    net.seed(MekanismRig.item(DUST_DIAMOND), 8);
                    net.seed(MekanismRig.item(DUST_REFINED_OBSIDIAN), 16);
                    net.seed(MekanismRig.item(PELLET_POLONIUM), 4);
                    net.seed(MekanismRig.item(STEEL_CASING), 1);
                })
                .thenServer(SETTLE + 2, level -> {
                    final TestWorldBuilder world = TestWorldBuilder.at(level, ctx.origin());
                    MekanismRig.mountBuses(world);
                    MekanismRig.mountBottomInputBus(world);
                    // Flat patterns in the Recipe ROM: one per recipe, nothing chained by hand.
                    final CraftingComputerBlockEntity cc = world.blockEntity(CRAFTING_COMPUTER, CraftingComputerBlockEntity.class);
                    ctx.assertTrue(cc.loadPattern(framePattern()), "the frame pattern loads into the ROM");
                    ctx.assertTrue(cc.loadMachineRecipe(NetworkRecipe.ofProcessing(infuse(
                            StorageKey.of(Items.COPPER_INGOT), StorageKey.of(Items.REDSTONE), 1, MekanismRig.itemKey(ALLOY_INFUSED)))),
                            "the infused alloy pattern loads into the ROM");
                    ctx.assertTrue(cc.loadMachineRecipe(NetworkRecipe.ofProcessing(infuse(
                            MekanismRig.itemKey(ALLOY_INFUSED), MekanismRig.itemKey(DUST_DIAMOND), 2, MekanismRig.itemKey(ALLOY_REINFORCED)))),
                            "the reinforced alloy pattern loads into the ROM");
                    ctx.assertTrue(cc.loadMachineRecipe(NetworkRecipe.ofProcessing(infuse(
                            MekanismRig.itemKey(ALLOY_REINFORCED), MekanismRig.itemKey(DUST_REFINED_OBSIDIAN), 4, MekanismRig.itemKey(ALLOY_ATOMIC)))),
                            "the atomic alloy pattern loads into the ROM");
                })
                .thenServer(SETTLE + 2, level -> {
                    final TestWorldBuilder world = TestWorldBuilder.at(level, ctx.origin());
                    ctx.assertTrue(MekanismRig.discovered(world, INFUSER), "the switch must declare the infuser through its buses");
                    ctx.assertTrue(world.blockEntity(MAINFRAME, MainframeBlockEntity.class).networkProcessingPatterns().size() == 3,
                            "the Mainframe must see the three machine patterns");
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
                .thenWaitUntil(() -> networkInteractor(ctx).craftableNames().contains(FRAME_NAME), SCREEN_WAIT,
                        "the frame recipe in the Crafting tab")
                .thenScreenshot(2, "mekanism-crafting-tab")
                // Request four frames through the popup: it must plan through the machine patterns.
                .then(0, () -> {
                    final NetworkInteractorApp app = networkInteractor(ctx);
                    ctx.clickDesktop(networkInteractorPoint(ctx, app.craftableCellCenter(app.craftableNames().indexOf(FRAME_NAME))));
                })
                .thenAssert(1, () -> networkInteractor(ctx).isCraftPopupOpen(), "clicking the frame opens the request popup")
                .then(1, () -> {
                    final NetworkInteractorApp app = networkInteractor(ctx);
                    for (int i = 0; i < 3; i++) {
                        ctx.clickDesktop(networkInteractorPoint(ctx, app.craftPopupStepCenter(2)));
                    }
                })
                .thenAssert(1, () -> networkInteractor(ctx).craftQuantity() == 4, "the quantity steppers must reach 4")
                .thenScreenshot(2, "mekanism-request-popup")
                .then(0, () -> ctx.clickDesktop(networkInteractorPoint(ctx, networkInteractor(ctx).craftPopupSubmitCenter())))
                .thenAssert(1, () -> !networkInteractor(ctx).isCraftPopupOpen(), "submitting closes the popup")
                .thenServer(SETTLE, level -> {
                    final MainframeBlockEntity mainframe = mainframe(ctx, level);
                    ctx.assertTrue(!mainframe.activeOperationRecords().isEmpty(),
                            "the request must create a craft on the Mainframe; recent=" + mainframe.recentOperations());
                })
                .thenScreenshot(2, "mekanism-craft-running")
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT)
                // The infuser is powered every tick while the craft runs (a generator's job in a real base).
                .thenWaitUntilServer(level -> {
                            MekanismRig.power(level, ctx.abs(MekanismRig.MACHINE));
                            return stored(ctx, level, MekanismRig.itemKey(FRAME)) >= 4;
                        }, 5000, "four frames from the alloy chain to reach network storage",
                        level -> "frames=" + stored(ctx, level, MekanismRig.itemKey(FRAME))
                                + " active=" + mainframe(ctx, level).activeOperationRecords()
                                + " recent=" + mainframe(ctx, level).recentOperations())
                .thenServer(SETTLE, level -> {
                    for (final StorageKey raw : new StorageKey[]{StorageKey.of(Items.COPPER_INGOT), StorageKey.of(Items.REDSTONE),
                            MekanismRig.itemKey(DUST_DIAMOND), MekanismRig.itemKey(DUST_REFINED_OBSIDIAN),
                            MekanismRig.itemKey(PELLET_POLONIUM), MekanismRig.itemKey(STEEL_CASING)}) {
                        ctx.assertTrue(stored(ctx, level, raw) == 0, raw + " must be fully consumed; left " + stored(ctx, level, raw));
                    }
                })
                // Back on the desktop: the Network Interactor is still open on the network grid with the frames.
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .thenWaitUntil(() -> networkInteractor(ctx) != null, SCREEN_WAIT, "the Network Interactor window again")
                .thenScreenshot(2, "mekanism-frames-in-storage")
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT);
    }

    @ClientTest(timeoutTicks = 6000)
    public static void commandPrompt_craftRequestPlansTheAlloyChainThroughTheInfuser(final ClientTestContext ctx) {
        // The terminal-only route: MC-DOS on the Crafting Computer, the Command Prompt on its monitor, and
        // "operation craft" typed by the player must reach the same planner and drive the same machine steps.
        ctx.thenBuild(0, world -> {
                    final TestWorldBuilder.CraftingNetwork net = MekanismRig.place(world, INFUSER);
                    TestWorldBuilder.installDesktop(net.cc(), MC_DOS);
                    net.cc().togglePower();
                    net.cc().togglePower();
                    world.placeMonitor(MONITOR, Direction.EAST);
                    net.seed(Items.COPPER_INGOT, 4);
                    net.seed(Items.REDSTONE, 4);
                    net.seed(MekanismRig.item(DUST_DIAMOND), 8);
                    net.seed(MekanismRig.item(DUST_REFINED_OBSIDIAN), 16);
                    net.seed(MekanismRig.item(PELLET_POLONIUM), 4);
                    net.seed(MekanismRig.item(STEEL_CASING), 1);
                })
                .thenServer(SETTLE + 2, level -> {
                    final TestWorldBuilder world = TestWorldBuilder.at(level, ctx.origin());
                    MekanismRig.mountBuses(world);
                    MekanismRig.mountBottomInputBus(world);
                    final CraftingComputerBlockEntity cc = world.blockEntity(CRAFTING_COMPUTER, CraftingComputerBlockEntity.class);
                    ctx.assertTrue(cc.loadPattern(framePattern()), "the frame pattern loads into the ROM");
                    ctx.assertTrue(cc.loadMachineRecipe(NetworkRecipe.ofProcessing(infuse(
                            StorageKey.of(Items.COPPER_INGOT), StorageKey.of(Items.REDSTONE), 1, MekanismRig.itemKey(ALLOY_INFUSED)))), "infused loads");
                    ctx.assertTrue(cc.loadMachineRecipe(NetworkRecipe.ofProcessing(infuse(
                            MekanismRig.itemKey(ALLOY_INFUSED), MekanismRig.itemKey(DUST_DIAMOND), 2, MekanismRig.itemKey(ALLOY_REINFORCED)))), "reinforced loads");
                    ctx.assertTrue(cc.loadMachineRecipe(NetworkRecipe.ofProcessing(infuse(
                            MekanismRig.itemKey(ALLOY_REINFORCED), MekanismRig.itemKey(DUST_REFINED_OBSIDIAN), 4, MekanismRig.itemKey(ALLOY_ATOMIC)))), "atomic loads");
                })
                .thenTeleport(SETTLE + 2, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(CommandPromptScreen.class, BOOT_WAIT)
                .thenScreenshot(2, "mc-dos-prompt")
                .then(2, () -> ctx.type("operation craft 4 " + FRAME))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ENTER))
                .thenWaitUntil(() -> {
                    final CommandPromptScreen<?> screen = ctx.screen(CommandPromptScreen.class);
                    return screen.scrollbackText().stream().anyMatch(l -> l.contains("CRAFT queued"));
                }, SCREEN_WAIT, "the prompt to confirm the queued craft")
                .thenScreenshot(2, "mc-dos-craft-queued")
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT)
                .thenWaitUntilServer(level -> {
                            MekanismRig.power(level, ctx.abs(MekanismRig.MACHINE));
                            return stored(ctx, level, MekanismRig.itemKey(FRAME)) >= 4;
                        }, 5000, "four frames from the alloy chain to reach network storage",
                        level -> "frames=" + stored(ctx, level, MekanismRig.itemKey(FRAME))
                                + " active=" + mainframe(ctx, level).activeOperationRecords()
                                + " recent=" + mainframe(ctx, level).recentOperations())
                // Back at the prompt, the operation log names the finished craft.
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(CommandPromptScreen.class, BOOT_WAIT)
                .then(2, () -> ctx.type("operation query operations"))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ENTER))
                .thenScreenshot(4, "mc-dos-operations")
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT);
    }

    private static long stored(final ClientTestContext ctx, final ServerLevel level, final StorageKey key) {
        return NetworkStorage.of(level, mainframe(ctx, level).networkUuid()).count(key);
    }

    private static MainframeBlockEntity mainframe(final ClientTestContext ctx, final ServerLevel level) {
        return TestWorldBuilder.at(level, ctx.origin()).blockEntity(MAINFRAME, MainframeBlockEntity.class);
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
}
