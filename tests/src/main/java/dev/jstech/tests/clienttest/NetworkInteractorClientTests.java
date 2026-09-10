/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Tech Series.
 */
package dev.jstech.tests.clienttest;

import dev.jstech.computers.ComputingModule;
import dev.jstech.computers.JsComputers;
import dev.jstech.computers.blockentity.CraftingComputerBlockEntity;
import dev.jstech.computers.blockentity.MainframeBlockEntity;
import dev.jstech.computers.client.os.DesktopScreen;
import dev.jstech.computers.client.os.DesktopWindow;
import dev.jstech.computers.client.os.NetworkInteractorApp;
import dev.jstech.computers.crafting.CraftingPattern;
import dev.jstech.computers.crafting.MultiStagePattern;
import dev.jstech.computers.crafting.NetworkRecipe;
import dev.jstech.computers.crafting.ProcessingPattern;
import dev.jstech.computers.program.Programs;
import dev.jstech.computers.storage.StorageKey;
import dev.jstech.tests.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Client tests for the Network Interactor's improvements as the player meets them: the mod and category
 * filters and the remembered view, the keyboard on the grid with the favourites it stars, the grips that
 * reshape the window, the details panel saying what makes an item, and the craft popup offering the recipes
 * that make an item side by side and saying what differs.
 */
public final class NetworkInteractorClientTests {

    private NetworkInteractorClientTests() {
    }

    private static final int SETTLE = 4;
    private static final int SCREEN_WAIT = 40;
    /** Long enough for a cold start's POST to play out on the monitor before the desktop shows. */
    private static final int BOOT_WAIT = 400;

    private static final BlockPos MAINFRAME = new BlockPos(1, 2, 2);
    private static final BlockPos CRAFTING_COMPUTER = new BlockPos(5, 2, 2);
    private static final BlockPos MONITOR = new BlockPos(6, 2, 2);
    private static final BlockPos PLAYER_AT_MONITOR = new BlockPos(8, 2, 2);
    private static final ResourceLocation FRAMES_XP =
            ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, "frames_xp");
    private static final String NETWORK_LAUNCHER = "Network";
    private static final String IRON_ID = "item|minecraft:iron_ingot";

    /**
     * The grid narrows by the mod that made the item and by its category, on top of the search; the
     * horizontal grip folds the inventory's top rows away; and all of it comes back with the window after
     * the screen was closed and the monitor opened again.
     */
    @ClientTest(timeoutTicks = 2400)
    public static void interactor_filtersByModAndCategoryAndRemembersTheView(final ClientTestContext ctx) {
        ctx.thenBuild(0, world -> {
                    final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
                    TestWorldBuilder.installDesktop(net.cc(), FRAMES_XP, Programs.CRAFTING_MANAGER);
                    net.cc().togglePower();
                    net.cc().togglePower();
                    world.placeMonitor(MONITOR, Direction.EAST);
                    net.seed(Items.RAW_IRON, 32);
                    net.seed(Items.IRON_INGOT, 8);
                    net.seed(Items.OAK_LOG, 16);
                    net.seed(ComputingModule.ETHERNET_CABLE.get().asItem(), 4);
                })
                .thenTeleport(SETTLE, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .then(2, () -> launch(ctx))
                .thenWaitUntil(() -> interactor(ctx) != null, SCREEN_WAIT, "the Network Interactor window")
                .then(2, () -> ctx.clickDesktop(point(ctx, interactor(ctx).networkTabCenter())))
                .thenWaitUntil(() -> interactor(ctx).listedNames().contains("Iron Ingot"), SCREEN_WAIT,
                        "the network grid to list the seeded ingots")
                .then(1, () -> ctx.assertTrue(interactor(ctx).listedNames().size() == 4,
                        "four kinds seeded; got " + interactor(ctx).listedNames()))
                // The mod filter: only J's Computers' cable is not Minecraft's.
                .then(1, () -> ctx.clickDesktop(point(ctx, interactor(ctx).modFilterCenter())))
                .then(1, () -> ctx.assertTrue(interactor(ctx).filterMenuOpen()
                                && interactor(ctx).filterMenuLabels().contains("Jsc")
                                && interactor(ctx).filterMenuLabels().contains("Minecraft"),
                        "the mod drop-down lists the mods in view; got " + interactor(ctx).filterMenuLabels()))
                .then(1, () -> ctx.clickDesktop(point(ctx, interactor(ctx).filterMenuPoint("Jsc"))))
                .then(1, () -> ctx.assertTrue(interactor(ctx).modFilter().equals("jsc")
                                && interactor(ctx).listedNames().size() == 1
                                && interactor(ctx).listedNames().get(0).contains("Cable"),
                        "the mod filter must leave the cable alone; got " + interactor(ctx).listedNames()))
                .then(1, () -> ctx.clickDesktop(point(ctx, interactor(ctx).modFilterCenter())))
                .then(1, () -> ctx.clickDesktop(point(ctx, interactor(ctx).filterMenuPoint("Any mod"))))
                .then(1, () -> ctx.assertTrue(interactor(ctx).modFilter().isEmpty() && interactor(ctx).listedNames().size() == 4,
                        "clearing the mod filter brings everything back; got " + interactor(ctx).listedNames()))
                // The category filter: the ingot is the one ingot; raw iron is a raw material, the log a block.
                .then(1, () -> ctx.clickDesktop(point(ctx, interactor(ctx).categoryFilterCenter())))
                .then(1, () -> ctx.assertTrue(interactor(ctx).filterMenuLabels().contains("Ingots")
                                && interactor(ctx).filterMenuLabels().contains("Raw materials"),
                        "the category drop-down lists the categories in view; got " + interactor(ctx).filterMenuLabels()))
                .then(1, () -> ctx.clickDesktop(point(ctx, interactor(ctx).filterMenuPoint("Ingots"))))
                .then(1, () -> ctx.assertTrue(interactor(ctx).listedNames().equals(List.of("Iron Ingot")),
                        "the category filter must leave the ingot alone; got " + interactor(ctx).listedNames()))
                // The search stacks on the filters.
                .then(1, () -> ctx.clickDesktop(point(ctx, interactor(ctx).searchFieldCenter())))
                .then(1, () -> ctx.type("iron"))
                .then(1, () -> ctx.assertTrue(interactor(ctx).searchText().equals("iron")
                                && interactor(ctx).listedNames().equals(List.of("Iron Ingot")),
                        "the search and the category filter stack; got " + interactor(ctx).listedNames()))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .then(1, () -> ctx.clickDesktop(point(ctx, interactor(ctx).categoryFilterCenter())))
                .then(1, () -> ctx.clickDesktop(point(ctx, interactor(ctx).filterMenuPoint("Any category"))))
                .then(1, () -> ctx.assertTrue(interactor(ctx).listedNames().equals(List.of("Iron Ingot", "Raw Iron")),
                        "the search alone lists both irons; got " + interactor(ctx).listedNames()))
                // The horizontal grip folds the inventory down to two rows and the grid grows.
                .then(1, () -> {
                    final NetworkInteractorApp app = interactor(ctx);
                    final int[] grip = app.horizontalGripCenter();
                    ctx.dragDesktop(point(ctx, grip), point(ctx, new int[] {grip[0], grip[1] + 2 * 18}));
                })
                .then(2, () -> ctx.assertTrue(interactor(ctx).inventoryRowsShown() == 2,
                        "dragging the grip two rows down folds two rows away; got " + interactor(ctx).inventoryRowsShown()))
                // A wide window has room for more grid columns: the vertical grip gives them.
                .then(1, () -> ctx.screen(DesktopScreen.class).windowFor(NETWORK_LAUNCHER).toggleMaximize())
                .then(2, () -> {
                    final NetworkInteractorApp app = interactor(ctx);
                    final int[] grip = app.verticalGripCenter();
                    ctx.dragDesktop(point(ctx, grip), point(ctx, new int[] {grip[0] + 2 * 18, grip[1]}));
                })
                .then(2, () -> ctx.assertTrue(interactor(ctx).gridColumns() == 11,
                        "dragging the grip two cells right gives the grid two columns; got " + interactor(ctx).gridColumns()))
                .thenScreenshot(2, "filters-and-grips")
                // Close the screen and open the monitor again: the view comes back as it was.
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                // Escape drops a selection first, so a second one may be needed to leave the screen.
                .then(2, () -> {
                    if (ctx.mc().screen != null) {
                        ctx.key(GLFW.GLFW_KEY_ESCAPE);
                    }
                })
                .thenAwaitNoScreen(SCREEN_WAIT)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .thenWaitUntil(() -> interactor(ctx) != null, SCREEN_WAIT, "the Network Interactor window to come back")
                .then(2, () -> ctx.assertTrue(interactor(ctx).searchText().equals("iron")
                                && interactor(ctx).inventoryRowsShown() == 2
                                && interactor(ctx).gridColumns() == 11
                                && interactor(ctx).activeTab() == 2,
                        "the search, the grips and the tab must come back; search=" + interactor(ctx).searchText()
                                + " rows=" + interactor(ctx).inventoryRowsShown() + " cols=" + interactor(ctx).gridColumns()
                                + " tab=" + interactor(ctx).activeTab()))
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                // Escape drops a selection first, so a second one may be needed to leave the screen.
                .then(2, () -> {
                    if (ctx.mc().screen != null) {
                        ctx.key(GLFW.GLFW_KEY_ESCAPE);
                    }
                })
                .thenAwaitNoScreen(SCREEN_WAIT);
    }

    /**
     * The keyboard walks the grid: the arrows move the dotted cell, F stars the item (the machine keeps the
     * star, and the Favourites tab shows it), Enter opens the request dialog, and the slash puts the keyboard
     * in the search. The details panel follows the keyboard's cell and says what makes the item.
     */
    @ClientTest(timeoutTicks = 2400)
    public static void interactor_keyboardWalksTheGridAndStarsFavourites(final ClientTestContext ctx) {
        ctx.thenBuild(0, world -> {
                    final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
                    TestWorldBuilder.installDesktop(net.cc(), FRAMES_XP, Programs.CRAFTING_MANAGER);
                    net.cc().togglePower();
                    net.cc().togglePower();
                    world.placeMonitor(MONITOR, Direction.EAST);
                    net.seed(Items.COAL, 12);
                    net.seed(Items.IRON_INGOT, 8);
                    net.seed(Items.RAW_IRON, 32);
                })
                .thenServer(SETTLE + 2, level -> {
                    final TestWorldBuilder world = TestWorldBuilder.at(level, ctx.origin());
                    final CraftingComputerBlockEntity cc = world.blockEntity(CRAFTING_COMPUTER, CraftingComputerBlockEntity.class);
                    ctx.assertTrue(cc.loadMachineRecipe(NetworkRecipe.ofProcessing(
                            smelt(Items.RAW_IRON, Items.IRON_INGOT, "minecraft:furnace", 200).withName("Blast", ""))),
                            "the furnace recipe loads into the ROM");
                    ctx.assertTrue(cc.loadPattern(new CraftingPattern(grid(Items.IRON_INGOT), new ItemStack(Items.IRON_NUGGET, 9))),
                            "the nugget pattern loads into the ROM");
                })
                .thenTeleport(SETTLE, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .then(2, () -> launch(ctx))
                .thenWaitUntil(() -> interactor(ctx) != null, SCREEN_WAIT, "the Network Interactor window")
                .then(2, () -> ctx.clickDesktop(point(ctx, interactor(ctx).networkTabCenter())))
                // The search is remembered from the last window on this client: empty it the way a player would.
                .then(1, () -> ctx.clickDesktop(point(ctx, interactor(ctx).searchFieldCenter())))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_CONTROL))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_BACKSPACE))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenWaitUntil(() -> interactor(ctx).listedNames().equals(List.of("Coal", "Iron Ingot", "Raw Iron")), SCREEN_WAIT,
                        "the network grid to list the three kinds by name",
                        () -> "search=" + interactor(ctx).searchText() + " listed=" + interactor(ctx).listedNames())
                .then(1, () -> ctx.assertTrue(!interactor(ctx).keyboardHint().isEmpty(), "the hint line says what the keys do"))
                // Right, Right: the keyboard lands on the coal, then the ingot.
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_RIGHT))
                .then(1, () -> ctx.assertTrue(interactor(ctx).keyCell() == 0,
                        "the first arrow puts the keyboard on the first cell; got " + interactor(ctx).keyCell()))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_RIGHT))
                .then(1, () -> ctx.assertTrue(interactor(ctx).keyCell() == 1 && interactor(ctx).detailsName().equals("Iron Ingot"),
                        "the keyboard's cell is the ingot and the details follow it; cell=" + interactor(ctx).keyCell()
                                + " details=" + interactor(ctx).detailsName()))
                .thenWaitUntil(() -> !interactor(ctx).detailsMadeBy().isEmpty(), SCREEN_WAIT,
                        "the details panel to learn what makes the ingot")
                .then(1, () -> ctx.assertTrue(interactor(ctx).detailsMadeBy().get(0).equals("Blast · processing · Furnace")
                                && interactor(ctx).detailsUsedIn().contains("Iron Nugget"),
                        "made by the furnace recipe, used in the nugget pattern; madeBy=" + interactor(ctx).detailsMadeBy()
                                + " usedIn=" + interactor(ctx).detailsUsedIn()))
                // F stars it: at once in the window, and on the machine.
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_F))
                .then(1, () -> ctx.assertTrue(interactor(ctx).favouriteIds().contains(IRON_ID),
                        "F stars the keyboard's item; got " + interactor(ctx).favouriteIds()))
                .thenWaitUntilServer(level -> TestWorldBuilder.at(level, ctx.origin())
                                .blockEntity(CRAFTING_COMPUTER, CraftingComputerBlockEntity.class)
                                .console().settings().isFavourite(IRON_ID),
                        SCREEN_WAIT, "the machine to keep the star",
                        level -> "favourites=" + TestWorldBuilder.at(level, ctx.origin())
                                .blockEntity(CRAFTING_COMPUTER, CraftingComputerBlockEntity.class).console().settings().favourites())
                .thenScreenshot(2, "starred")
                // The Favourites tab lists the starred ingot alone; Enter there opens its request dialog.
                .then(1, () -> ctx.clickDesktop(point(ctx, interactor(ctx).favouritesTabCenter())))
                .then(2, () -> ctx.assertTrue(interactor(ctx).activeTab() == 5
                                && interactor(ctx).listedNames().equals(List.of("Iron Ingot")),
                        "the Favourites tab lists the star; tab=" + interactor(ctx).activeTab() + " got " + interactor(ctx).listedNames()))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ENTER))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ENTER))
                .then(1, () -> ctx.assertTrue(interactor(ctx).isRequestPopupOpen(), "Enter on the starred item opens the request dialog"))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .then(1, () -> ctx.assertTrue(!interactor(ctx).isRequestPopupOpen(), "Escape closes the dialog"))
                // The star button in the details takes the star off again.
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_RIGHT))
                .then(1, () -> ctx.clickDesktop(point(ctx, interactor(ctx).detailsStarCenter())))
                .then(1, () -> ctx.assertTrue(!interactor(ctx).favouriteIds().contains(IRON_ID),
                        "the star button unstars the item; got " + interactor(ctx).favouriteIds()))
                // The slash puts the keyboard in the search; Escape puts it back on the grid.
                .then(1, () -> ctx.clickDesktop(point(ctx, interactor(ctx).networkTabCenter())))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_SLASH))
                .then(1, () -> ctx.type("coal"))
                .then(1, () -> ctx.assertTrue(interactor(ctx).searchText().equals("coal")
                                && interactor(ctx).listedNames().equals(List.of("Coal")),
                        "typing after the slash searches; got " + interactor(ctx).searchText() + " " + interactor(ctx).listedNames()))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_RIGHT))
                .then(1, () -> ctx.assertTrue(interactor(ctx).keyCell() == 0 && interactor(ctx).searchText().equals("coal"),
                        "Escape leaves the search as typed and the arrows drive the grid again; cell=" + interactor(ctx).keyCell()
                                + " search=" + interactor(ctx).searchText()))
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                // Escape drops a selection first, so a second one may be needed to leave the screen.
                .then(2, () -> {
                    if (ctx.mc().screen != null) {
                        ctx.key(GLFW.GLFW_KEY_ESCAPE);
                    }
                })
                .thenAwaitNoScreen(SCREEN_WAIT);
    }

    /**
     * A click selects an item and a double click opens it; a drag across the grid, Shift with the arrows and
     * Control+A select several, and Enter asks for them all in one dialog that sends one request per item. On
     * the Operations tab the vertical grip drags instead of picking a row.
     */
    @ClientTest(timeoutTicks = 2400)
    public static void interactor_selectsSeveralItemsAndRequestsThemTogether(final ClientTestContext ctx) {
        final int[] columnsBefore = new int[1];
        ctx.thenBuild(0, world -> {
                    final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
                    TestWorldBuilder.installDesktop(net.cc(), FRAMES_XP, Programs.CRAFTING_MANAGER);
                    net.cc().togglePower();
                    net.cc().togglePower();
                    world.placeMonitor(MONITOR, Direction.EAST);
                    net.seed(Items.COAL, 12);
                    net.seed(Items.IRON_INGOT, 8);
                    net.seed(Items.RAW_IRON, 32);
                })
                .thenTeleport(SETTLE, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .then(2, () -> launch(ctx))
                .thenWaitUntil(() -> interactor(ctx) != null, SCREEN_WAIT, "the Network Interactor window")
                .then(2, () -> ctx.clickDesktop(point(ctx, interactor(ctx).networkTabCenter())));
        clearSearch(ctx)
                .thenWaitUntil(() -> interactor(ctx).listedNames().equals(List.of("Coal", "Iron Ingot", "Raw Iron")), SCREEN_WAIT,
                        "the network grid to list the three kinds by name",
                        () -> "search=" + interactor(ctx).searchText() + " listed=" + interactor(ctx).listedNames())
                // One click selects; nothing opens.
                .then(1, () -> ctx.clickDesktop(point(ctx, interactor(ctx).gridCellCenter(0))))
                .then(2, () -> ctx.assertTrue(interactor(ctx).selectedNames().equals(List.of("Coal"))
                                && !interactor(ctx).isRequestPopupOpen() && interactor(ctx).keyCell() == 0,
                        "a click selects the coal and opens nothing; selected=" + interactor(ctx).selectedNames()
                                + " popup=" + interactor(ctx).isRequestPopupOpen()))
                // A drag across the three cells takes them all (well past the double-click time of the click before).
                .then(8, () -> ctx.dragDesktop(point(ctx, interactor(ctx).gridCellCenter(0)), point(ctx, interactor(ctx).gridCellCenter(2))))
                .then(1, () -> ctx.assertTrue(interactor(ctx).selectedNames().equals(List.of("Coal", "Iron Ingot", "Raw Iron")),
                        "the rubber band selects every cell it crosses; got " + interactor(ctx).selectedNames()))
                // Shift with an arrow reshapes the selection from where it started; Control+A takes everything.
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_LEFT, GLFW.GLFW_MOD_SHIFT))
                .then(1, () -> ctx.assertTrue(interactor(ctx).selectedNames().equals(List.of("Coal", "Iron Ingot")),
                        "Shift+Left keeps the first two; got " + interactor(ctx).selectedNames()))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_CONTROL))
                .then(1, () -> ctx.assertTrue(interactor(ctx).selectedNames().size() == 3,
                        "Control+A selects all three; got " + interactor(ctx).selectedNames()))
                // A click on the grid's empty part drops the selection (the sixth cell holds nothing).
                .then(1, () -> ctx.clickDesktop(point(ctx, interactor(ctx).gridCellCenter(5))))
                .then(1, () -> ctx.assertTrue(interactor(ctx).selectedNames().isEmpty(),
                        "a click on empty grid drops the selection; got " + interactor(ctx).selectedNames()))
                // A double click opens the item's request dialog.
                .then(1, () -> {
                    ctx.clickDesktop(point(ctx, interactor(ctx).gridCellCenter(0)));
                    ctx.clickDesktop(point(ctx, interactor(ctx).gridCellCenter(0)));
                })
                .then(1, () -> ctx.assertTrue(interactor(ctx).isRequestPopupOpen()
                                && interactor(ctx).requestPopupTitle().equals("Coal"),
                        "a double click opens the coal's request dialog; title=" + interactor(ctx).requestPopupTitle()))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                // Two selected and Enter: one dialog for both, one request each.
                .then(1, () -> ctx.clickDesktop(point(ctx, interactor(ctx).gridCellCenter(1))))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_MOD_SHIFT))
                .then(1, () -> ctx.assertTrue(interactor(ctx).selectedNames().equals(List.of("Iron Ingot", "Raw Iron")),
                        "Shift+Right extends from the ingot to the raw iron; got " + interactor(ctx).selectedNames()))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ENTER))
                .then(1, () -> ctx.assertTrue(interactor(ctx).isRequestPopupOpen()
                                && interactor(ctx).requestPopupTitle().equals("Request 2 items"),
                        "Enter opens one dialog for the selection; title=" + interactor(ctx).requestPopupTitle()))
                .thenScreenshot(2, "request-two")
                .then(1, () -> ctx.clickDesktop(point(ctx, interactor(ctx).requestPopupSubmitCenter())))
                .then(1, () -> ctx.assertTrue(!interactor(ctx).isRequestPopupOpen(), "Request closes the dialog"))
                .thenWaitUntilServer(level -> {
                            final var local = TestWorldBuilder.at(level, ctx.origin())
                                    .blockEntity(CRAFTING_COMPUTER, CraftingComputerBlockEntity.class).localStore().view();
                            return local.getOrDefault(StorageKey.of(Items.IRON_INGOT), 0L) > 0
                                    && local.getOrDefault(StorageKey.of(Items.RAW_IRON), 0L) > 0
                                    && local.getOrDefault(StorageKey.of(Items.COAL), 0L) == 0;
                        }, SCREEN_WAIT, "both items to reach this computer's local storage",
                        level -> "local=" + TestWorldBuilder.at(level, ctx.origin())
                                .blockEntity(CRAFTING_COMPUTER, CraftingComputerBlockEntity.class).localStore().view())
                // The Operations tab: the vertical grip drags; it never picks the row under it.
                .then(1, () -> ctx.screen(DesktopScreen.class).windowFor(NETWORK_LAUNCHER).toggleMaximize())
                .then(2, () -> ctx.clickDesktop(point(ctx, interactor(ctx).operationsTabCenter())))
                .then(2, () -> {
                    final NetworkInteractorApp app = interactor(ctx);
                    // The grip's place is remembered from the last window on this client, so count from where it is.
                    columnsBefore[0] = app.gridColumns();
                    final int[] grip = app.verticalGripCenter();
                    ctx.dragDesktop(point(ctx, grip), point(ctx, new int[] {grip[0] + 2 * 18, grip[1]}));
                })
                .then(2, () -> ctx.assertTrue(interactor(ctx).selectedOperation() == -1
                                && interactor(ctx).gridColumns() == columnsBefore[0] + 2,
                        "the grip drags on the Operations tab and picks no row; row=" + interactor(ctx).selectedOperation()
                                + " cols=" + interactor(ctx).gridColumns() + " before=" + columnsBefore[0]))
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                // Escape drops a selection first, so a second one may be needed to leave the screen.
                .then(2, () -> {
                    if (ctx.mc().screen != null) {
                        ctx.key(GLFW.GLFW_KEY_ESCAPE);
                    }
                })
                .thenAwaitNoScreen(SCREEN_WAIT);
    }

    /**
     * An ingot the network makes two ways: the craft popup lists both recipes as cards, plans with the one
     * picked, says what the other would do differently, reads the short rows in red with what covers them, and
     * the machine remembers the pick for the next time.
     */
    @ClientTest(timeoutTicks = 2400)
    public static void craftPopup_offersBothRecipesAndPlansWithThePicked(final ClientTestContext ctx) {
        ctx.thenBuild(0, world -> {
                    final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
                    TestWorldBuilder.installDesktop(net.cc(), FRAMES_XP, Programs.CRAFTING_MANAGER);
                    net.cc().togglePower();
                    net.cc().togglePower();
                    world.placeMonitor(MONITOR, Direction.EAST);
                    net.seed(Items.RAW_IRON, 32);
                })
                .thenServer(SETTLE + 2, level -> {
                    final TestWorldBuilder world = TestWorldBuilder.at(level, ctx.origin());
                    final CraftingComputerBlockEntity cc = world.blockEntity(CRAFTING_COMPUTER, CraftingComputerBlockEntity.class);
                    ctx.assertTrue(cc.loadMachineRecipe(NetworkRecipe.ofProcessing(
                            smelt(Items.RAW_IRON, Items.IRON_INGOT, "minecraft:furnace", 200).withName("Blast", ""))),
                            "the furnace recipe loads into the ROM");
                    // The other way needs coal too, and the network has none.
                    final ProcessingPattern coalSmelt = new ProcessingPattern(
                            List.of(new ProcessingPattern.ProcessingInput(StorageKey.of(Items.RAW_IRON), 1L),
                                    new ProcessingPattern.ProcessingInput(StorageKey.of(Items.COAL), 1L)),
                            List.of(new ProcessingPattern.ProcessingOutput(StorageKey.of(Items.IRON_INGOT), 1L, 100)),
                            "minecraft:blast_furnace", 100);
                    ctx.assertTrue(cc.loadMachineRecipe(NetworkRecipe.ofMultiStage(
                            new MultiStagePattern(List.of(MultiStagePattern.Stage.proc(coalSmelt))).withName("Iron line", ""))),
                            "the pipeline loads into the ROM");
                })
                .thenServer(SETTLE, level -> ctx.assertTrue(TestWorldBuilder.at(level, ctx.origin())
                                .blockEntity(MAINFRAME, MainframeBlockEntity.class).recipesFor(StorageKey.of(Items.IRON_INGOT)).size() == 2,
                        "the Mainframe lists both recipes for the ingot"))
                .thenTeleport(SETTLE, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .then(2, () -> launch(ctx))
                .thenWaitUntil(() -> interactor(ctx) != null, SCREEN_WAIT, "the Network Interactor window")
                .then(2, () -> ctx.clickDesktop(point(ctx, interactor(ctx).craftingTabCenter())))
                .thenWaitUntil(() -> interactor(ctx).craftableNames().contains("Blast"), SCREEN_WAIT,
                        "the Crafting tab to list the ingot by its first recipe's name")
                .then(1, () -> openCraft(ctx, "Blast"))
                .thenWaitUntil(() -> interactor(ctx).craftPopupOptionLabels().size() == 2, SCREEN_WAIT,
                        "the plan to come back with both recipes")
                .then(1, () -> ctx.assertTrue(interactor(ctx).craftPopupOptionLabels().equals(List.of("Blast", "Iron line"))
                                && interactor(ctx).craftPopupChosen() == 0
                                && interactor(ctx).craftPopupFeasible()
                                && interactor(ctx).craftPopupPlanRows().equals(List.of("Raw Iron 1/1")),
                        "the first recipe is planned first; options=" + interactor(ctx).craftPopupOptionLabels()
                                + " chosen=" + interactor(ctx).craftPopupChosen() + " rows=" + interactor(ctx).craftPopupPlanRows()))
                .thenScreenshot(2, "two-recipes")
                // Picking the other card plans with it: the coal it needs is short, and nothing makes it.
                .then(1, () -> ctx.clickDesktop(point(ctx, interactor(ctx).craftPopupCardCenter(1))))
                .thenWaitUntil(() -> interactor(ctx).craftPopupChosen() == 1 && !interactor(ctx).craftPopupFeasible(),
                        SCREEN_WAIT, "the plan to be redone with the pipeline")
                .then(1, () -> ctx.assertTrue(interactor(ctx).craftPopupPlanLabel().endsWith("with Iron line")
                                && interactor(ctx).craftPopupPlanRows().equals(List.of("Raw Iron 1/1", "Coal 0/1!"))
                                && interactor(ctx).craftPopupCoverLines().equals(List.of("Missing 1 Coal · nothing on the network makes it")),
                        "the pipeline's plan reads the coal in red; label=" + interactor(ctx).craftPopupPlanLabel()
                                + " rows=" + interactor(ctx).craftPopupPlanRows() + " cover=" + interactor(ctx).craftPopupCoverLines()))
                .then(1, () -> ctx.assertTrue(interactor(ctx).craftPopupDifferences().equals(
                                "Iron line against Blast: also takes Coal · 5 s faster for 1 · 1 Coal short, and nothing on the network makes it"),
                        "the strip says what differs; got " + interactor(ctx).craftPopupDifferences()))
                .thenScreenshot(2, "pipeline-picked")
                .thenWaitUntilServer(level -> TestWorldBuilder.at(level, ctx.origin())
                                .blockEntity(CRAFTING_COMPUTER, CraftingComputerBlockEntity.class)
                                .console().settings().recipeChoice(IRON_ID) == 1,
                        SCREEN_WAIT, "the machine to remember the pick",
                        level -> "choice=" + TestWorldBuilder.at(level, ctx.origin())
                                .blockEntity(CRAFTING_COMPUTER, CraftingComputerBlockEntity.class).console().settings().recipeChoice(IRON_ID))
                // Closed and opened again, the popup comes back on the recipe picked.
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .then(1, () -> ctx.assertTrue(!interactor(ctx).isCraftPopupOpen(), "Escape closes the craft popup"))
                .then(1, () -> openCraft(ctx, "Blast"))
                .thenWaitUntil(() -> interactor(ctx).craftPopupOptionLabels().size() == 2, SCREEN_WAIT, "the plan to come back")
                .then(1, () -> ctx.assertTrue(interactor(ctx).craftPopupChosen() == 1,
                        "the popup opens on the recipe picked last; got " + interactor(ctx).craftPopupChosen()))
                // Left walks back to the furnace recipe, and the craft submits it.
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_LEFT))
                .thenWaitUntil(() -> interactor(ctx).craftPopupChosen() == 0 && interactor(ctx).craftPopupFeasible(),
                        SCREEN_WAIT, "Left to pick the furnace recipe again")
                .then(1, () -> ctx.clickDesktop(point(ctx, interactor(ctx).craftPopupSubmitCenter())))
                .then(1, () -> ctx.assertTrue(!interactor(ctx).isCraftPopupOpen(), "submitting closes the popup"))
                .thenWaitUntilServer(level -> !TestWorldBuilder.at(level, ctx.origin())
                                .blockEntity(MAINFRAME, MainframeBlockEntity.class).activeOperationRecords().isEmpty()
                                || !TestWorldBuilder.at(level, ctx.origin())
                                .blockEntity(MAINFRAME, MainframeBlockEntity.class).recentOperations().isEmpty(),
                        SCREEN_WAIT, "the request to create an operation on the Mainframe",
                        level -> "active=" + TestWorldBuilder.at(level, ctx.origin())
                                .blockEntity(MAINFRAME, MainframeBlockEntity.class).activeOperationRecords())
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                // Escape drops a selection first, so a second one may be needed to leave the screen.
                .then(2, () -> {
                    if (ctx.mc().screen != null) {
                        ctx.key(GLFW.GLFW_KEY_ESCAPE);
                    }
                })
                .thenAwaitNoScreen(SCREEN_WAIT);
    }

    // helpers

    private static void launch(final ClientTestContext ctx) {
        final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
        ctx.click(desktop.startButtonX(), desktop.startButtonY());
        final int item = desktop.launcherLabels().indexOf(NETWORK_LAUNCHER);
        ctx.assertTrue(item >= 0, "the Start menu must list " + NETWORK_LAUNCHER + "; got " + desktop.launcherLabels());
        ctx.click(desktop.startMenuItemX(item), desktop.startMenuItemY(item));
    }

    private static void openCraft(final ClientTestContext ctx, final String title) {
        final NetworkInteractorApp app = interactor(ctx);
        final int index = app.craftableNames().indexOf(title);
        ctx.assertTrue(index >= 0, "the Crafting tab must list " + title + "; got " + app.craftableNames());
        // A click selects the craftable; a second one right after opens it.
        ctx.clickDesktop(point(ctx, app.craftableCellCenter(index)));
        ctx.clickDesktop(point(ctx, app.craftableCellCenter(index)));
    }

    /** Empties the remembered search the way a player would, so the grid lists everything. */
    private static ClientTestContext clearSearch(final ClientTestContext ctx) {
        return ctx.then(1, () -> ctx.clickDesktop(point(ctx, interactor(ctx).searchFieldCenter())))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_CONTROL))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_BACKSPACE))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE));
    }

    private static NetworkInteractorApp interactor(final ClientTestContext ctx) {
        if (!(ctx.mc().screen instanceof DesktopScreen desktop)) {
            return null;
        }
        final DesktopWindow window = desktop.windowFor(NETWORK_LAUNCHER);
        return window != null && window.app() instanceof NetworkInteractorApp app ? app : null;
    }

    /** Converts a Network Interactor content-local point into desktop coordinates. */
    private static int[] point(final ClientTestContext ctx, final int[] local) {
        final DesktopWindow window = ctx.screen(DesktopScreen.class).windowFor(NETWORK_LAUNCHER);
        if (window == null) {
            throw new ClientTestFailure("the " + NETWORK_LAUNCHER + " window is gone");
        }
        if (local == null) {
            throw new ClientTestFailure("no such control on the " + NETWORK_LAUNCHER + " window");
        }
        return new int[] {window.x() + 4 + local[0], window.y() + 18 + local[1]};
    }

    private static ProcessingPattern smelt(final Item in, final Item out, final String machineType, final int ticks) {
        return new ProcessingPattern(
                List.of(new ProcessingPattern.ProcessingInput(StorageKey.of(in), 1L)),
                List.of(new ProcessingPattern.ProcessingOutput(StorageKey.of(out), 1L, 100)),
                machineType, ticks);
    }

    private static List<ItemStack> grid(final Item first) {
        final List<ItemStack> grid = new ArrayList<>();
        grid.add(new ItemStack(first));
        for (int i = 1; i < 9; i++) {
            grid.add(ItemStack.EMPTY);
        }
        return grid;
    }
}
