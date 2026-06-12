/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation.payload;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.common.format.Unit;
import dev.jsc.jscomputronics.common.format.UnitFormatter;
import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.network.ServerNode;
import dev.jsc.jscomputronics.common.network.SubframeNode;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.PatternReaderBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.PersonalComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRouterBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.DatacenterStationBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import dev.jsc.jscomputronics.module.computing.datacenter.LoadBalancer;
import dev.jsc.jscomputronics.module.computing.storage.ServerStore;
import dev.jsc.jscomputronics.module.computing.menu.ComputerTerminalMenu;
import dev.jsc.jscomputronics.module.computing.menu.MainframeMenu;
import dev.jsc.jscomputronics.module.computing.menu.PatternReaderMenu;
import dev.jsc.jscomputronics.module.computing.menu.PersonalComputerMenu;
import dev.jsc.jscomputronics.module.computing.menu.ServerRouterMenu;
import dev.jsc.jscomputronics.module.computing.menu.DatacenterStationMenu;
import net.minecraft.network.chat.Component;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registers and handles the network storage payloads that let a Personal Computer's Network tab drive the storage operations: the client asks to SELECT, the server runs it through the network and replies with a fresh snapshot of what the network holds.
 */
@EventBusSubscriber(modid = JsComputronics.MODID)
public final class ComputingPayloads {

    private ComputingPayloads() {
    }

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(NetworkSnapshotPayload.TYPE, NetworkSnapshotPayload.STREAM_CODEC,
                ComputingPayloads::handleSnapshot);
        registrar.playToServer(RequestNetworkNodesPayload.TYPE, RequestNetworkNodesPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestNodes);
        registrar.playToClient(NetworkNodesPayload.TYPE, NetworkNodesPayload.STREAM_CODEC,
                ComputingPayloads::handleNodes);
        registrar.playToServer(TerminalSelectPayload.TYPE, TerminalSelectPayload.STREAM_CODEC,
                ComputingPayloads::handleTerminalSelect);
        registrar.playToServer(TerminalInsertPayload.TYPE, TerminalInsertPayload.STREAM_CODEC,
                ComputingPayloads::handleTerminalInsert);
        registrar.playToServer(RequestServerBreakdownPayload.TYPE, RequestServerBreakdownPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestBreakdown);
        registrar.playToClient(ServerBreakdownPayload.TYPE, ServerBreakdownPayload.STREAM_CODEC,
                ComputingPayloads::handleServerBreakdown);
        registrar.playToClient(OperationsLogPayload.TYPE, OperationsLogPayload.STREAM_CODEC,
                ComputingPayloads::handleOpsLog);
        registrar.playToClient(LocalStorageSnapshotPayload.TYPE, LocalStorageSnapshotPayload.STREAM_CODEC,
                ComputingPayloads::handleLocalSnapshot);
        registrar.playToServer(TerminalLocalWithdrawPayload.TYPE, TerminalLocalWithdrawPayload.STREAM_CODEC,
                ComputingPayloads::handleLocalWithdraw);
        registrar.playToServer(TerminalLocalDepositPayload.TYPE, TerminalLocalDepositPayload.STREAM_CODEC,
                ComputingPayloads::handleLocalDeposit);
        registrar.playToClient(ActiveOperationsPayload.TYPE, ActiveOperationsPayload.STREAM_CODEC,
                ComputingPayloads::handleActiveOps);
        registrar.playToClient(NetworkServersPayload.TYPE, NetworkServersPayload.STREAM_CODEC,
                ComputingPayloads::handleNetworkServers);
        registrar.playToServer(RenameServerPayload.TYPE, RenameServerPayload.STREAM_CODEC,
                ComputingPayloads::handleRenameServer);
        registrar.playToServer(TerminalLocalUploadPayload.TYPE, TerminalLocalUploadPayload.STREAM_CODEC,
                ComputingPayloads::handleLocalUpload);
        registrar.playToServer(RenamePcPayload.TYPE, RenamePcPayload.STREAM_CODEC,
                ComputingPayloads::handleRenamePc);
        registrar.playToServer(RenameServerRouterPayload.TYPE, RenameServerRouterPayload.STREAM_CODEC,
                ComputingPayloads::handleRenameServerRouter);
        registrar.playToClient(DatacenterSnapshotPayload.TYPE, DatacenterSnapshotPayload.STREAM_CODEC,
                ComputingPayloads::handleDatacenterSnapshot);
        registrar.playToServer(DatacenterStationActionPayload.TYPE, DatacenterStationActionPayload.STREAM_CODEC,
                ComputingPayloads::handleDatacenterAction);
        registrar.playToServer(DatacenterSelectPayload.TYPE, DatacenterSelectPayload.STREAM_CODEC,
                ComputingPayloads::handleDatacenterSelect);
        registrar.playToServer(TerminalMaintenancePayload.TYPE, TerminalMaintenancePayload.STREAM_CODEC,
                ComputingPayloads::handleTerminalMaintenance);
        registrar.playToServer(TerminalDropPayload.TYPE, TerminalDropPayload.STREAM_CODEC,
                ComputingPayloads::handleTerminalDrop);
        registrar.playToClient(CraftCatalogPayload.TYPE, CraftCatalogPayload.STREAM_CODEC,
                ComputingPayloads::handleCraftCatalog);
        registrar.playToServer(CraftPlanRequestPayload.TYPE, CraftPlanRequestPayload.STREAM_CODEC,
                ComputingPayloads::handleCraftPlanRequest);
        registrar.playToClient(CraftPlanPayload.TYPE, CraftPlanPayload.STREAM_CODEC,
                ComputingPayloads::handleCraftPlan);
        registrar.playToServer(CraftSubmitPayload.TYPE, CraftSubmitPayload.STREAM_CODEC,
                ComputingPayloads::handleCraftSubmit);
        registrar.playToServer(RequestRomSnapshotPayload.TYPE, RequestRomSnapshotPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestRomSnapshot);
        registrar.playToClient(RomSnapshotPayload.TYPE, RomSnapshotPayload.STREAM_CODEC,
                ComputingPayloads::handleRomSnapshot);
        registrar.playToServer(RunCommandPayload.TYPE, RunCommandPayload.STREAM_CODEC,
                ComputingPayloads::handleRunCommand);
        registrar.playToClient(CommandOutputPayload.TYPE, CommandOutputPayload.STREAM_CODEC,
                ComputingPayloads::handleCommandOutput);
        registrar.playToServer(RequestConsoleInitPayload.TYPE, RequestConsoleInitPayload.STREAM_CODEC,
                ComputingPayloads::handleRequestConsoleInit);
        registrar.playToClient(ConsoleInitPayload.TYPE, ConsoleInitPayload.STREAM_CODEC,
                ComputingPayloads::handleConsoleInit);
        registrar.playToServer(OpenProgramPayload.TYPE, OpenProgramPayload.STREAM_CODEC,
                ComputingPayloads::handleOpenProgram);
    }

    // Command Prompt — a typed line runs through the shell against the open host and the styled
    // output is streamed back. The CLI is an alternative interface over the same network operations.

    private static final int CLI_WIDTH = 50;

    private static void handleRunCommand(final RunCommandPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof dev.jsc.jscomputronics.module.computing.menu.CommandPromptMenu menu)
                    || !menu.hostPos().equals(payload.hostPos())
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.hostPos())
                            instanceof dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost host)) {
                return;
            }
            final var computer = new dev.jsc.jscomputronics.module.computing.program.ServerCliComputer(host, level);
            final var shell = dev.jsc.jscomputronics.module.computing.program.cli.CliCommands.newShell(CLI_WIDTH);
            final var response = shell.run(payload.line(), computer);
            final List<CommandOutputPayload.WireLine> wire = new ArrayList<>(response.lines().size());
            for (final var cliLine : response.lines()) {
                wire.add(new CommandOutputPayload.WireLine(cliLine.text(), cliLine.style().ordinal()));
            }
            PacketDistributor.sendToPlayer(player, new CommandOutputPayload(response.clearScreen(), wire));
            // Persist the typed line on the computer so the history survives closing the prompt or Monitor.
            if (host.console() != null && !payload.line().isBlank()) {
                host.console().pushHistory(payload.line().trim());
                ((net.minecraft.world.level.block.entity.BlockEntity) host).setChanged();
            }
        });
    }

    private static void handleRequestConsoleInit(final RequestConsoleInitPayload payload,
                                                 final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof dev.jsc.jscomputronics.module.computing.menu.CommandPromptMenu menu
                    && menu.hostPos().equals(payload.hostPos())
                    && player.level().getBlockEntity(payload.hostPos())
                            instanceof dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost host) {
                sendConsoleInit(player, host);
            }
        });
    }

    private static void sendConsoleInit(final ServerPlayer player,
            final dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost host) {
        final var console = host.console();
        final List<String> history = console == null ? List.of() : console.history();
        final List<ConsoleInitPayload.WireCommand> commands = new ArrayList<>();
        for (final var command : dev.jsc.jscomputronics.module.computing.program.cli.CliCommands.all()) {
            if (commands.size() >= ConsoleInitPayload.MAX_COMMANDS) {
                break;
            }
            commands.add(new ConsoleInitPayload.WireCommand(command.name(), command.usage()));
        }
        PacketDistributor.sendToPlayer(player, new ConsoleInitPayload(List.copyOf(history), commands));
    }

    private static void handleConsoleInit(final ConsoleInitPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jsc.jscomputronics.module.computing.client.CommandPromptScreen.acceptInit(payload));
    }

    private static void handleOpenProgram(final OpenProgramPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof ComputerTerminalMenu terminal)
                    || !terminal.hostPos().equals(payload.hostPos())
                    || !(player.level().getBlockEntity(payload.hostPos())
                            instanceof dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost)) {
                return;
            }
            // Only the Command Prompt is launchable this way for now; future programs slot in here.
            final String id = payload.programId();
            if (id.equals(dev.jsc.jscomputronics.module.computing.program.Programs.COMMAND_PROMPT.toString())
                    || id.equals("command_prompt")) {
                final net.minecraft.network.chat.Component title =
                        player.level().getBlockState(payload.hostPos()).getBlock().getName();
                player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (windowId, inv, p) -> new dev.jsc.jscomputronics.module.computing.menu.CommandPromptMenu(
                                windowId, inv, payload.monitorPos(), payload.hostPos()), title),
                        buf -> {
                            buf.writeBlockPos(payload.monitorPos());
                            buf.writeBlockPos(payload.hostPos());
                        });
            }
        });
    }

    private static void handleCommandOutput(final CommandOutputPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jsc.jscomputronics.module.computing.client.CommandPromptScreen.accept(payload));
    }

    // Recipe ROM tab on the Pattern Reader — the client lists the adjacent Crafting Computer's ROM
    // and exports patterns to a rewritable disc.

    private static void handleRequestRomSnapshot(final RequestRomSnapshotPayload payload,
                                                 final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof PatternReaderMenu menu
                    && menu.readerPos().equals(payload.readerPos())
                    && player.level().getBlockEntity(payload.readerPos())
                            instanceof PatternReaderBlockEntity reader) {
                sendRomSnapshot(player, reader);
            }
        });
    }

    private static void handleRomSnapshot(final RomSnapshotPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof PatternReaderMenu menu) {
                menu.setRomPatterns(payload.patterns());
            }
        });
    }

    public static void sendRomSnapshot(final ServerPlayer player, final PatternReaderBlockEntity reader) {
        if (player == null || player.isRemoved()) {
            return;
        }
        final List<dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern> rom = reader.romPatterns();
        final var bounded = rom.size() > RomSnapshotPayload.MAX ? rom.subList(0, RomSnapshotPayload.MAX) : rom;
        PacketDistributor.sendToPlayer(player, new RomSnapshotPayload(List.copyOf(bounded)));
    }

    private static void handleCraftCatalog(final CraftCatalogPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ComputerTerminalMenu menu) {
                menu.setCraftCatalog(payload.entries());
            }
        });
    }

    private static void handleCraftPlan(final CraftPlanPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ComputerTerminalMenu menu) {
                menu.setCraftPlan(payload);
            }
        });
    }

    public static void dispatchCraftCatalog(final ServerPlayer player, final NetworkUuid net,
                                            final ServerLevel level) {
        final MainframeBlockEntity mainframe = resolveMainframe(level, net);
        if (mainframe == null) {
            PacketDistributor.sendToPlayer(player, new CraftCatalogPayload(java.util.List.of()));
            return;
        }
        final var patterns = mainframe.networkPatterns();
        final var stock = dev.jsc.jscomputronics.module.computing.operation.NetworkStorage
                .of(level, net).query();
        final java.util.Map<StorageKey, CraftCatalogPayload.Entry> entries = new java.util.LinkedHashMap<>();
        for (final var pattern : patterns) {
            final StorageKey key = StorageKey.of(pattern.result());
            if (entries.containsKey(key)) {
                continue;
            }
            final byte dot;
            if (dev.jsc.jscomputronics.module.computing.crafting.CraftPlanner
                    .plan(key, 1, patterns, stock).feasible()) {
                dot = CraftCatalogPayload.DOT_GREEN;
            } else {
                boolean any = false;
                for (final StorageKey ingredient : pattern.ingredientTotals().keySet()) {
                    if (stock.getOrDefault(ingredient, 0L) > 0L) {
                        any = true;
                        break;
                    }
                }
                dot = any ? CraftCatalogPayload.DOT_AMBER : CraftCatalogPayload.DOT_RED;
            }
            entries.put(key, new CraftCatalogPayload.Entry(pattern.result().copy(), dot));
            if (entries.size() >= CraftCatalogPayload.MAX_ENTRIES) {
                break;
            }
        }
        PacketDistributor.sendToPlayer(player,
                new CraftCatalogPayload(java.util.List.copyOf(entries.values())));
    }

    private static void handleCraftPlanRequest(final CraftPlanRequestPayload payload,
                                               final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host == null || host.networkUuid() == null
                    || !(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || payload.quantity() <= 0L) {
                return;
            }
            final MainframeBlockEntity mainframe = resolveMainframe(level, host.networkUuid());
            if (mainframe == null) {
                return;
            }
            final var patterns = mainframe.networkPatterns();
            final var stock = dev.jsc.jscomputronics.module.computing.operation.NetworkStorage
                    .of(level, host.networkUuid()).query();
            final StorageKey key = StorageKey.of(payload.result());
            final var plan = dev.jsc.jscomputronics.module.computing.crafting.CraftPlanner
                    .plan(key, payload.quantity(), patterns, stock);

            // Raw-ingredient rows: total needed (consumed + still missing) vs what the network has.
            final java.util.Map<StorageKey, Long> need = new java.util.LinkedHashMap<>(plan.rawConsumption());
            plan.missing().forEach((k, v) -> need.merge(k, v, Long::sum));
            final java.util.List<CraftPlanPayload.Row> rows = new java.util.ArrayList<>();
            for (final var entry : need.entrySet()) {
                if (rows.size() >= CraftPlanPayload.MAX_ROWS) {
                    break;
                }
                final ItemStack icon = entry.getKey().stack(1);
                if (!icon.isEmpty()) {
                    rows.add(new CraftPlanPayload.Row(icon, entry.getValue(),
                            Math.min(stock.getOrDefault(entry.getKey(), 0L), entry.getValue())));
                }
            }
            final boolean feasible = plan.feasible();
            final long maxFeasible = feasible ? payload.quantity()
                    : dev.jsc.jscomputronics.module.computing.crafting.CraftPlanner
                            .maxFeasible(key, payload.quantity(), patterns, stock);
            PacketDistributor.sendToPlayer(player, new CraftPlanPayload(
                    payload.result(), payload.quantity(), java.util.List.copyOf(rows),
                    feasible, maxFeasible, estimateTicks(level, mainframe, plan)));
        });
    }

    private static int estimateTicks(final ServerLevel level, final MainframeBlockEntity mainframe,
                                     final dev.jsc.jscomputronics.module.computing.crafting.CraftPlanner.Plan plan) {
        long units = 0;
        for (final var step : plan.steps()) {
            units += step.runs() * Math.max(1, step.pattern().filledCells());
        }
        long rate = 0;
        for (final net.minecraft.core.BlockPos pos : mainframe.craftingComputerPositions()) {
            if (level.getBlockEntity(pos)
                    instanceof dev.jsc.jscomputronics.module.computing.blockentity.CraftingComputerBlockEntity cc
                    && cc.canCraft()) {
                rate = Math.max(rate, cc.craftingThroughput());
            }
        }
        if (rate <= 0) {
            return 0;
        }
        return (int) Math.max(1, (units + rate - 1) / rate);
    }

    private static void handleCraftSubmit(final CraftSubmitPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host == null || host.networkUuid() == null
                    || !(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || payload.quantity() <= 0L) {
                return;
            }
            final NetworkUuid net = host.networkUuid();
            final MainframeBlockEntity mainframe = resolveMainframe(level, net);
            if (mainframe == null) {
                return;
            }
            final var operation = mainframe.submitNetworkCraft(
                    StorageKey.of(payload.result()), payload.quantity(), payload.partial(), "terminal");
            if (operation != null) {
                operation.onSettle(() -> {
                    dispatchTerminalOpsLog(player, net, level);
                    dispatchActiveOperations(player, net, level);
                    dispatchCraftCatalog(player, net, level);
                });
            }
            dispatchActiveOperations(player, net, level);
            dispatchCraftCatalog(player, net, level);
        });
    }

    private static void handleRenameServerRouter(final RenameServerRouterPayload payload,
                                                 final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof ServerRouterMenu menu
                    && menu.routerPos().equals(payload.routerPos())
                    && player.level().getBlockEntity(payload.routerPos())
                            instanceof ServerRouterBlockEntity router) {
                router.setCustomName(payload.name());
            }
        });
    }

    private static void handleDatacenterSnapshot(final DatacenterSnapshotPayload payload,
                                                 final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof DatacenterStationMenu menu) {
                menu.setSnapshot(payload);
            }
        });
    }

    private static void handleDatacenterAction(final DatacenterStationActionPayload payload,
                                               final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof DatacenterStationMenu menu)
                    || !menu.stationPos().equals(payload.stationPos())
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.stationPos()) instanceof DatacenterStationBlockEntity station)) {
                return;
            }
            switch (payload.action()) {
                case DatacenterStationActionPayload.ACTION_NEXT_SECTION -> station.bindNext();
                case DatacenterStationActionPayload.ACTION_CYCLE_BALANCE -> station.cycleLoadBalanceMode();
                case DatacenterStationActionPayload.ACTION_INSERT_CURSOR -> depositCursor(menu, station, level, false);
                case DatacenterStationActionPayload.ACTION_INSERT_CURSOR_ONE -> depositCursor(menu, station, level, true);
                default -> {
                    // ACTION_REFRESH: just re-send the snapshot below.
                }
            }
            sendDatacenterSnapshot(player, level, station);
        });
    }

    private static void depositCursor(final DatacenterStationMenu menu, final DatacenterStationBlockEntity station,
                                      final ServerLevel level, final boolean single) {
        final ItemStack cursor = menu.getCarried();
        if (cursor.isEmpty()) {
            return;
        }
        final List<NodeUuid> servers = station.sectionServers();
        final List<ServerStore> stores = sectionStores(level, servers);
        if (stores.isEmpty()) {
            return;
        }
        final StorageKey key = StorageKey.of(cursor);
        final long want = single ? 1L : cursor.getCount();
        final long stored = LoadBalancer.insert(stores, key, want, station.loadBalanceMode());
        if (stored > 0L) {
            cursor.shrink((int) stored);
            menu.setCarried(cursor.isEmpty() ? ItemStack.EMPTY : cursor);
            // The cursor changed outside a normal slot click; without a broadcast the client keeps
            // showing the old stack (a ghost cursor).
            menu.broadcastChanges();
        }
    }

    private static List<ServerStore> sectionStores(final ServerLevel level, final List<NodeUuid> servers) {
        final NetworkSystem system = NetworkSystem.get(level);
        final List<ServerStore> stores = new ArrayList<>();
        for (final NodeUuid node : servers) {
            system.locationOf(node).ifPresent(loc -> {
                if (level.getBlockEntity(BlockPos.of(loc.rackPos())) instanceof ServerRackBlockEntity rack) {
                    stores.add(rack.getServerStorage(loc.slot()));
                }
            });
        }
        return stores;
    }

    public static void sendDatacenterSnapshot(final ServerPlayer player, final ServerLevel level,
                                              final DatacenterStationBlockEntity station) {
        if (player == null || player.isRemoved()) {
            return;
        }
        final List<NodeUuid> servers = station.sectionServers();
        final List<ServerStore> stores = sectionStores(level, servers);

        final Map<StorageKey, Long> totals = dev.jsc.jscomputronics.module.computing.operation.NetworkStorage
                .ofServers(level, servers).query();
        final List<NetworkItemEntry> items = new ArrayList<>(Math.min(totals.size(), DatacenterSnapshotPayload.MAX_ITEMS));
        totals.entrySet().stream().limit(DatacenterSnapshotPayload.MAX_ITEMS)
                .forEach(e -> items.add(new NetworkItemEntry(e.getKey(), e.getValue())));

        // The "giant computer" view: the section's Servers summed into one machine — storage,
        // orchestration capacity (CPU) and RAM buffer — plus a per-Server line with its real name.
        long used = 0L;
        long total = 0L;
        long cpu = 0L;
        long ram = 0L;
        final NetworkSystem sys = NetworkSystem.get(level);
        final List<DatacenterSnapshotPayload.ServerLine> lines = new ArrayList<>();
        for (int i = 0; i < servers.size() && i < DatacenterSnapshotPayload.MAX_SERVERS; i++) {
            final NodeUuid node = servers.get(i);
            final ServerStore store = i < stores.size() ? stores.get(i) : null;
            final long u = store == null ? 0L : store.usedWeight();
            final long t = store == null ? 0L : store.capacityWeight();
            used += u;
            total += t;
            String name = "srv-" + node.asString().substring(0, 4);
            final var loc = sys.locationOf(node);
            if (loc.isPresent()
                    && level.getBlockEntity(BlockPos.of(loc.get().rackPos())) instanceof ServerRackBlockEntity rack) {
                final ItemStack stack = rack.getServers().getStackInSlot(loc.get().slot());
                final var build = dev.jsc.jscomputronics.module.computing.item.ServerItem.build(stack);
                if (build != null) {
                    cpu += build.totalCapacity();
                    ram += build.ramBuffer();
                }
                final String custom = dev.jsc.jscomputronics.module.computing.item.ServerItem.customName(stack);
                if (!custom.isEmpty()) {
                    name = custom;
                }
            }
            lines.add(new DatacenterSnapshotPayload.ServerLine(name, u, t));
        }

        // Operations in flight on the network's Mainframe (the orchestrator the section runs under).
        int activeOps = 0;
        final BlockPos mfPosForOps = station.mainframePos();
        if (mfPosForOps != null && level.getBlockEntity(mfPosForOps) instanceof MainframeBlockEntity mf) {
            activeOps = mf.activeOperationRecords().size();
        }

        // MOVE destinations: the network's computers with local storage (PCs + the Mainframe).
        final List<DatacenterSnapshotPayload.DestEntry> dests = new ArrayList<>();
        final NetworkUuid net = station.network();
        if (net != null) {
            final NetworkSystem system = NetworkSystem.get(level);
            for (final var pc : system.personalComputersOf(net)) {
                if (level.getBlockEntity(BlockPos.of(pc.pos())) instanceof PersonalComputerBlockEntity pcBe
                        && pcBe.localStorageCapacity() > 0L) {
                    final String name = pcBe.customName().isEmpty()
                            ? "PC-" + pc.nodeUuid().asString().substring(0, 4) : pcBe.customName();
                    dests.add(new DatacenterSnapshotPayload.DestEntry(pc.pos(), name));
                }
            }
            system.mainframePositionOf(net).ifPresent(mfPos -> {
                if (level.getBlockEntity(BlockPos.of(mfPos))
                        instanceof dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost host
                        && host.localStorageCapacity() > 0L) {
                    dests.add(new DatacenterSnapshotPayload.DestEntry(mfPos, "Mainframe"));
                }
            });
        }

        PacketDistributor.sendToPlayer(player, new DatacenterSnapshotPayload(
                station.sectionLabel(), servers.size(), used, total,
                station.loadBalanceMode().ordinal(), station.availableSections().size(),
                cpu, ram, activeOps, items, lines, dests));
    }

    private static void handleDatacenterSelect(final DatacenterSelectPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.quantity() <= 0L) {
                return; // never dispatch a zero/negative pull (a broken or hostile client)
            }
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof DatacenterStationMenu menu)
                    || !menu.stationPos().equals(payload.stationPos())
                    || !(player.level() instanceof ServerLevel level)
                    || !(level.getBlockEntity(payload.stationPos()) instanceof DatacenterStationBlockEntity station)) {
                return;
            }
            final BlockPos mainframePos = station.mainframePos();
            if (mainframePos == null
                    || !(level.getBlockEntity(mainframePos) instanceof MainframeBlockEntity mainframe)
                    || !(level.getBlockEntity(BlockPos.of(payload.destPos()))
                            instanceof dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost dest)) {
                return;
            }
            // The destination must live on the STATION'S network — the client only ever picks from the
            // snapshot's list, so any other position is a spoofed packet reaching into a foreign network.
            final NetworkUuid stationNet = station.network();
            if (stationNet == null || !stationNet.equals(dest.networkUuid())) {
                return;
            }
            final java.util.Set<NodeUuid> sources = new java.util.HashSet<>(station.sectionServers());
            if (sources.isEmpty()) {
                return;
            }
            final var op = mainframe.submitNetworkMove(payload.key(), payload.quantity(),
                    dest.localStorage(), "datacenter", sources);
            if (op != null) {
                op.onSettle(() -> sendDatacenterSnapshot(player, level, station));
            }
            sendDatacenterSnapshot(player, level, station);
        });
    }

    private static void handleRenamePc(final RenamePcPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && hasOpenAssemblyFor(player, payload.pcPos())
                    && player.level().getBlockEntity(payload.pcPos())
                            instanceof dev.jsc.jscomputronics.module.computing.blockentity.AbstractComputerBlockEntity computer) {
                computer.setCustomName(payload.name());
            }
        });
    }

    private static boolean hasOpenAssemblyFor(final ServerPlayer player, final net.minecraft.core.BlockPos pos) {
        if (player.containerMenu instanceof PersonalComputerMenu menu) {
            return menu.pcPos().equals(pos);
        }
        if (player.containerMenu instanceof dev.jsc.jscomputronics.module.computing.menu.CraftingComputerMenu menu) {
            return menu.computerPos().equals(pos);
        }
        return false;
    }

    private static void handleLocalUpload(final TerminalLocalUploadPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host == null || host.networkUuid() == null
                    || !(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || payload.quantity() <= 0L) {
                return;
            }
            final NetworkUuid net = host.networkUuid();
            final MainframeBlockEntity mainframe = resolveMainframe(level, net);
            if (mainframe == null) {
                return;
            }
            final StorageKey key = payload.key();
            // Take the items out of local storage and carry them in the Operation; whatever the network
            // cannot hold is returned to local storage when it settles, so nothing is ever lost.
            final long taken = host.localStore().extract(key,
                    Math.min(payload.quantity(), host.localStore().count(key)));
            if (taken <= 0L) {
                return;
            }
            final var op = mainframe.submitNetworkInsert(key, taken, "local");
            if (op == null) {
                host.localStore().insert(key, taken); // no live dispatcher: put it straight back
                return;
            }
            op.onSettle(() -> {
                final long leftover = op.leftover();
                if (leftover > 0L) {
                    host.localStore().insert(key, leftover);
                }
                dispatchLocalSnapshot(player, host);
                sendSnapshot(player, level, net);
            });
        });
    }

    private static void handleRenameServer(final RenameServerPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu
                    instanceof dev.jsc.jscomputronics.module.computing.menu.ServerAssemblyMenu menu) {
                menu.setServerName(payload.name());
            }
        });
    }

    // Network-operation dispatch — the ONLY way storage is touched. Every request

    private static void returnToPlayer(final ServerPlayer player, final ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (player.isRemoved()) {
            net.minecraft.world.Containers.dropItemStack(player.level(),
                    player.getX(), player.getY(), player.getZ(), stack);
        } else {
            player.getInventory().placeItemBackInInventory(stack);
        }
    }

    public static void dispatchQuery(final ServerPlayer player, final PersonalComputerBlockEntity pc) {
        dispatch(player, pc, (level, net, mf) -> mf.submitOperation(
                new dev.jsc.jscomputronics.module.computing.operation.NetworkQueryOperationTask(level, net, player),
                dev.jsc.jscomputronics.common.operation.OperationPriority.MEDIUM));
    }

    @FunctionalInterface
    private interface OperationSubmit {
        boolean submit(ServerLevel level, NetworkUuid net, MainframeBlockEntity mainframe);
    }

    private static boolean dispatch(final ServerPlayer player, final PersonalComputerBlockEntity pc,
                                    final OperationSubmit submit) {
        final NetworkUuid net = pc.networkUuid();
        if (net == null || !(pc.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        final MainframeBlockEntity mainframe = resolveMainframe(level, net);
        return mainframe != null && submit.submit(level, net, mainframe);
    }

    public static boolean networkHasActiveOps(final ServerLevel level, final NetworkUuid network) {
        final MainframeBlockEntity mainframe = resolveMainframe(level, network);
        return mainframe != null && mainframe.hasActiveOperations();
    }

    private static void handleTerminalMaintenance(final TerminalMaintenancePayload payload,
                                                  final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host == null || !host.isMainframeHost() || host.networkUuid() == null
                    || !(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            final NetworkUuid net = host.networkUuid();
            final MainframeBlockEntity mainframe = resolveMainframe(level, net);
            if (mainframe == null) {
                return;
            }
            final dev.jsc.jscomputronics.module.computing.operation.NetworkIndex index = mainframe.networkIndex();
            byte opType;
            long count;
            ItemStack icon;
            String message;
            switch (payload.action()) {
                case TerminalMaintenancePayload.ACTION_ANALYZE -> {
                    index.analyzeIncremental(level, net);
                    opType = OperationRecord.TYPE_ANALYZE;
                    count = index.catalogSize();
                    icon = labelledIcon(Items.SPYGLASS, "index");
                    message = "ANALYZE complete - " + count + " types reconciled";
                }
                case TerminalMaintenancePayload.ACTION_REINDEX -> {
                    index.rebuild(level, net);
                    opType = OperationRecord.TYPE_REINDEX;
                    count = index.catalogSize();
                    icon = labelledIcon(Items.COMPASS, "index");
                    message = "REINDEX complete - catalog rebuilt from disks";
                }
                case TerminalMaintenancePayload.ACTION_VACUUM -> {
                    final int freed = index.vacuum(level, net);
                    opType = OperationRecord.TYPE_VACUUM;
                    count = freed;
                    icon = labelledIcon(Items.HOPPER, "ghost rows");
                    message = "VACUUM freed " + freed + (freed == 1 ? " ghost entry" : " ghost entries");
                }
                default -> {
                    return;
                }
            }
            // Index maintenance is instantaneous; log it COMPLETED so the Operations tab records that it ran.
            mainframe.recordOperation(opType, icon, count, count,
                    OperationRecord.STATUS_COMPLETED, java.util.List.of());
            player.displayClientMessage(Component.literal(message), true);
            dispatchTerminalQuery(player, net, level); // the catalog may have changed — refresh the grid
        });
    }

    private static void handleTerminalDrop(final TerminalDropPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host == null || !host.isMainframeHost() || host.networkUuid() == null
                    || !(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            final NetworkUuid net = host.networkUuid();
            final MainframeBlockEntity mainframe = resolveMainframe(level, net);
            if (mainframe == null) {
                return;
            }
            final dev.jsc.jscomputronics.module.computing.operation.NetworkIndex index = mainframe.networkIndex();
            long destroyed = 0L;
            String label;
            StorageKey recordKey;
            switch (payload.scope()) {
                case TerminalDropPayload.SCOPE_NETWORK -> {
                    destroyed = index.dropAll(level, net);
                    label = "the network";
                    recordKey = StorageKey.of(labelledIcon(Items.TNT, "network"));
                }
                case TerminalDropPayload.SCOPE_SERVER -> {
                    if (payload.serverKey().isEmpty()) {
                        return;
                    }
                    final NodeUuid node;
                    try {
                        node = NodeUuid.fromString(payload.serverKey());
                    } catch (final IllegalArgumentException malformed) {
                        return;
                    }
                    destroyed = index.dropServer(level, node);
                    label = "a server";
                    recordKey = StorageKey.of(labelledIcon(Items.TNT, serverLabel(level, node)));
                }
                case TerminalDropPayload.SCOPE_TYPES -> {
                    for (final StorageKey key : payload.types()) {
                        destroyed += index.dropType(level, net, key, null);
                    }
                    final int n = payload.types().size();
                    label = n + (n == 1 ? " type" : " types");
                    // A single-type DROP shows that data's real icon; many types collapse to a tagged marker.
                    recordKey = n == 1 ? payload.types().get(0) : StorageKey.of(labelledIcon(Items.TNT, n + " types"));
                }
                default -> {
                    return;
                }
            }
            mainframe.recordOperation(new OperationRecord(OperationRecord.TYPE_DROP, recordKey,
                    destroyed, destroyed, OperationRecord.STATUS_COMPLETED, java.util.List.of()));
            player.displayClientMessage(Component.literal(
                    "DROP destroyed " + destroyed + " from " + label), true);
            dispatchTerminalQuery(player, net, level);
        });
    }

    private static ItemStack labelledIcon(final net.minecraft.world.item.Item item, final String label) {
        final ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(label));
        return stack;
    }

    private static MainframeBlockEntity resolveMainframe(final ServerLevel level, final NetworkUuid network) {
        final java.util.Optional<Long> pos = NetworkSystem.get(level).mainframePositionOf(network);
        if (pos.isEmpty()) {
            return null;
        }
        return level.getBlockEntity(net.minecraft.core.BlockPos.of(pos.get())) instanceof MainframeBlockEntity mf
                ? mf : null;
    }

    private static void handleSnapshot(final NetworkSnapshotPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu
                    instanceof dev.jsc.jscomputronics.module.computing.menu.ComputerTerminalMenu terminal) {
                terminal.setNetworkItems(payload.items());
            }
        });
    }

    public static void dispatchTerminalQuery(final ServerPlayer player, final NetworkUuid net,
                                             final ServerLevel level) {
        final MainframeBlockEntity mainframe = resolveMainframe(level, net);
        if (mainframe != null) {
            mainframe.submitOperation(
                    new dev.jsc.jscomputronics.module.computing.operation.NetworkQueryOperationTask(level, net, player),
                    dev.jsc.jscomputronics.common.operation.OperationPriority.MEDIUM);
        }
    }

    private static void handleTerminalSelect(final TerminalSelectPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host == null || host.networkUuid() == null
                    || !(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            final NetworkUuid net = host.networkUuid();
            final MainframeBlockEntity mainframe = resolveMainframe(level, net);
            if (mainframe == null) {
                return;
            }
            // Resolve where the pulled items land: the computer's own local storage (simple/auto) or
            final Dest dest = resolveDest(host, level, net, payload.destKind(), payload.destServer());
            if (dest == null) {
                return;
            }
            Set<NodeUuid> sources = payload.serverKeys().isEmpty() ? null : toNodes(payload.serverKeys());
            // A MOVE must never pull from its own destination Server: extracting and re-inserting into
            // the same store would churn items in place. Drop the target from the sources.
            if (dest.move() && dest.target() != null) {
                sources = sourcesWithout(level, net, sources, dest.target());
                if (sources.isEmpty()) {
                    return; // the only chosen source was the destination — nothing to move
                }
            }
            final StorageKey key = payload.key();
            final var op = dest.move()
                    ? mainframe.submitNetworkMove(key, payload.quantity(), dest.handler(), dest.label(), sources)
                    : mainframe.submitNetworkSelect(key, payload.quantity(), dest.handler(), dest.label(), sources);
            if (op != null) {
                op.onSettle(() -> sendSnapshot(player, level, net));
            }
        });
    }

    /**
     * A resolved SELECT destination: where the pulled items land, the provenance label, whether it is a MOVE (into another Server), and that target Server's node (so it can be excluded as a source).
     */
    private record Dest(dev.jsc.jscomputronics.module.computing.storage.DataSink handler, String label,
                        boolean move, @Nullable NodeUuid target) {
    }

    @Nullable
    private static Dest resolveDest(final ComputerTerminalHost host, final ServerLevel level,
                                    final NetworkUuid net, final int kind, final String serverKey) {
        return kind == TerminalSelectPayload.DEST_SERVER
                ? resolveComputerDest(level, net, serverKey)
                : resolveTerminalDest(host);
    }

    @Nullable
    private static Dest resolveComputerDest(final ServerLevel level, final NetworkUuid net, final String key) {
        final NodeUuid target;
        try {
            target = NodeUuid.fromString(key);
        } catch (final IllegalArgumentException malformed) {
            return null;
        }
        final MainframeBlockEntity mf = resolveMainframe(level, net);
        if (mf != null && mf.nodeUuid() != null && mf.nodeUuid().equals(target)) {
            return new Dest(new dev.jsc.jscomputronics.module.computing.storage.LocalStoreSink(mf.localStore()),
                    "Mainframe", false, null);
        }
        // A Personal Computer on the network: a SELECT into its own local storage (leaves the network).
        for (final NetworkSystem.PersonalComputerNode pc : NetworkSystem.get(level).personalComputersOf(net)) {
            if (pc.nodeUuid().equals(target)
                    && level.getBlockEntity(BlockPos.of(pc.pos())) instanceof PersonalComputerBlockEntity pcBe) {
                return new Dest(new dev.jsc.jscomputronics.module.computing.storage.LocalStoreSink(pcBe.localStore()),
                        pcLabel(pcBe, target), false, null);
            }
        }
        return resolveServerDest(level, net, key);
    }

    @Nullable
    private static Dest resolveTerminalDest(final ComputerTerminalHost host) {
        return host.usableStorageSlots() > 0 ? new Dest(host.localStorage(), "storage", false, null) : null;
    }

    @Nullable
    private static Dest resolveServerDest(final ServerLevel level, final NetworkUuid net, final String serverKey) {
        final NodeUuid target;
        try {
            target = NodeUuid.fromString(serverKey);
        } catch (final IllegalArgumentException malformed) {
            return null;
        }
        final NetworkSystem system = NetworkSystem.get(level);
        boolean onNetwork = false;
        for (final ServerNode server : system.serversOf(net)) {
            if (server.nodeUuid().equals(target)) {
                onNetwork = true;
                break;
            }
        }
        if (!onNetwork) {
            return null;
        }
        return system.locationOf(target)
                .map(loc -> level.getBlockEntity(BlockPos.of(loc.rackPos()))
                        instanceof dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity rack
                        ? new Dest(new dev.jsc.jscomputronics.module.computing.storage.ServerStoreSink(
                                rack.getServerStorage(loc.slot())),
                                serverLabel(level, target), true, target)
                        : null)
                .orElse(null);
    }

    private static Set<NodeUuid> sourcesWithout(final ServerLevel level, final NetworkUuid net,
                                                @Nullable final Set<NodeUuid> sources, final NodeUuid target) {
        final Set<NodeUuid> result;
        if (sources != null) {
            result = new HashSet<>(sources);
        } else {
            result = new HashSet<>();
            for (final ServerNode server : NetworkSystem.get(level).serversOf(net)) {
                result.add(server.nodeUuid());
            }
        }
        result.remove(target);
        return result;
    }

    private static void handleTerminalInsert(final TerminalInsertPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host == null || host.networkUuid() == null
                    || !(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(player.containerMenu instanceof ComputerTerminalMenu menu)) {
                return;
            }
            final NetworkUuid net = host.networkUuid();
            final MainframeBlockEntity mainframe = resolveMainframe(level, net);
            if (mainframe == null) {
                return;
            }
            final int idx = payload.slotIndex();
            final boolean fromCursor = idx == TerminalInsertPayload.CURSOR || idx == TerminalInsertPayload.CURSOR_ONE;
            // A slot source must be a player-inventory slot, never a storage slot.
            if (!fromCursor && (idx < menu.storageSlotCount() || idx >= menu.slots.size())) {
                return;
            }
            final net.minecraft.world.inventory.Slot slot = fromCursor ? null : menu.getSlot(idx);
            final ItemStack source = fromCursor ? menu.getCarried() : slot.getItem();
            if (source.isEmpty()) {
                return;
            }
            // A fluid container (a filled bucket, etc.) deposits its FLUID into the network and leaves
            // the emptied container — fluid is data too. One container per click.
            if (depositFluidContainer(mainframe, menu, slot, fromCursor, source, player, level, net)) {
                return;
            }
            // Take the items off the source now ("in flight"); the Operation returns overflow.
            final ItemStack inFlight;
            if (idx == TerminalInsertPayload.CURSOR_ONE) {
                inFlight = source.copyWithCount(1);
                source.shrink(1);
                menu.setCarried(source.isEmpty() ? ItemStack.EMPTY : source);
            } else if (fromCursor) {
                inFlight = source.copy();
                menu.setCarried(ItemStack.EMPTY);
            } else {
                inFlight = source.copy();
                slot.set(ItemStack.EMPTY);
            }
            menu.broadcastChanges();
            // Push the held items into the network over ticks; return whatever does not fit (with its
            // original components) to the player when the Operation settles.
            final var op = mainframe.submitNetworkInsert(StorageKey.of(inFlight), inFlight.getCount(), "terminal");
            if (op == null) {
                // Error path (no live dispatcher): hand the items straight back.
                player.getInventory().placeItemBackInInventory(inFlight);
                return;
            }
            op.onSettle(() -> {
                final long leftover = op.leftover();
                if (leftover > 0L) {
                    returnToPlayer(player, inFlight.copyWithCount((int) leftover));
                }
                sendSnapshot(player, level, net);
            });
        });
    }

    private static boolean depositFluidContainer(final MainframeBlockEntity mainframe,
            final ComputerTerminalMenu menu, final net.minecraft.world.inventory.Slot slot,
            final boolean fromCursor, final ItemStack source, final ServerPlayer player,
            final ServerLevel level, final NetworkUuid network) {
        final var handler = FluidUtil.getFluidHandler(source.copyWithCount(1));
        if (handler.isEmpty()) {
            return false;
        }
        final FluidStack drained = handler.get().drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) {
            return false; // an empty container is not a fluid to deposit — try the item path
        }
        // A fluid container is atomic: only deposit when the network has room for the whole amount, so
        long freeWeight = 0L;
        for (final var room : mainframe.networkIndex().freeSpace(level, network)) {
            freeWeight += room.quantity();
            if (freeWeight >= drained.getAmount()) {
                break;
            }
        }
        if (freeWeight < drained.getAmount()) {
            return true;
        }
        final var op = mainframe.submitNetworkInsert(StorageKey.of(drained), drained.getAmount(), "terminal");
        if (op == null) {
            return true; // no dispatcher: do nothing, keep the full container in hand
        }
        // Remove one container from the source and hand back the emptied one.
        source.shrink(1);
        if (fromCursor) {
            menu.setCarried(source.isEmpty() ? ItemStack.EMPTY : source);
        } else {
            slot.set(source.isEmpty() ? ItemStack.EMPTY : source);
        }
        menu.broadcastChanges();
        returnToPlayer(player, handler.get().getContainer());
        op.onSettle(() -> {
            final long leftover = op.leftover();
            if (leftover > 0L) {
                // The network could not hold it all; hand back a filled container of the remainder.
                final ItemStack refilled = FluidUtil.getFilledBucket(
                        drained.copyWithAmount((int) Math.min(leftover, Integer.MAX_VALUE)));
                if (!refilled.isEmpty()) {
                    returnToPlayer(player, refilled);
                }
            }
            sendSnapshot(player, level, network);
        });
        return true;
    }

    private static void handleRequestBreakdown(final RequestServerBreakdownPayload payload,
                                               final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host != null && host.networkUuid() != null
                    && context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level) {
                PacketDistributor.sendToPlayer(player, collectBreakdown(level, host.networkUuid(), payload.key()));
                // The advanced-mode destination picker needs every computer that can hold items (the
                // Mainframe's local storage and every Server), not just those holding the clicked item.
                PacketDistributor.sendToPlayer(player, collectComputers(level, host.networkUuid()));
            }
        });
    }

    private static void handleServerBreakdown(final ServerBreakdownPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ComputerTerminalMenu menu) {
                menu.setServerBreakdown(payload.servers());
            }
        });
    }

    private static void handleNetworkServers(final NetworkServersPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ComputerTerminalMenu menu) {
                menu.setNetworkServers(payload.servers());
            }
        });
    }

    private static NetworkServersPayload collectComputers(final ServerLevel level, final NetworkUuid net) {
        final NetworkSystem system = NetworkSystem.get(level);
        final List<NetworkServersPayload.ServerEntry> rows = new ArrayList<>();
        final MainframeBlockEntity mf = resolveMainframe(level, net);
        if (mf != null && mf.nodeUuid() != null && mf.localStorageCapacity() > 0L) {
            rows.add(new NetworkServersPayload.ServerEntry(
                    mf.nodeUuid().asString(), "Mainframe", mf.localStore().free()));
        }
        for (final ServerNode server : system.serversOf(net)) {
            if (rows.size() >= NetworkServersPayload.MAX) {
                break;
            }
            final NodeUuid node = server.nodeUuid();
            final long free = system.locationOf(node)
                    .map(loc -> level.getBlockEntity(BlockPos.of(loc.rackPos()))
                            instanceof dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity rack
                            ? rack.getServerStorage(loc.slot()).free() : 0L)
                    .orElse(0L);
            rows.add(new NetworkServersPayload.ServerEntry(node.asString(), serverLabel(level, node), free));
        }
        for (final NetworkSystem.PersonalComputerNode pc : system.personalComputersOf(net)) {
            if (rows.size() >= NetworkServersPayload.MAX) {
                break;
            }
            if (level.getBlockEntity(BlockPos.of(pc.pos()))
                    instanceof PersonalComputerBlockEntity pcBe && pcBe.localStorageCapacity() > 0L) {
                rows.add(new NetworkServersPayload.ServerEntry(
                        pc.nodeUuid().asString(), pcLabel(pcBe, pc.nodeUuid()), pcBe.localStore().free()));
            }
        }
        return new NetworkServersPayload(rows);
    }

    private static NetworkServersPayload collectServers(final ServerLevel level, final NetworkUuid net) {
        final NetworkSystem system = NetworkSystem.get(level);
        final List<NetworkServersPayload.ServerEntry> rows = new ArrayList<>();
        for (final ServerNode server : system.serversOf(net)) {
            if (rows.size() >= NetworkServersPayload.MAX) {
                break;
            }
            final NodeUuid node = server.nodeUuid();
            final long free = system.locationOf(node)
                    .map(loc -> level.getBlockEntity(BlockPos.of(loc.rackPos()))
                            instanceof dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity rack
                            ? rack.getServerStorage(loc.slot()).free() : 0L)
                    .orElse(0L);
            rows.add(new NetworkServersPayload.ServerEntry(node.asString(), serverLabel(level, node), free));
        }
        return new NetworkServersPayload(rows);
    }

    public static void dispatchNetworkServers(final ServerPlayer player, final NetworkUuid net,
                                              final ServerLevel level) {
        PacketDistributor.sendToPlayer(player, collectServers(level, net));
    }

    private static String pcLabel(final PersonalComputerBlockEntity pc, final NodeUuid node) {
        return pc.customName().isEmpty() ? "PC-" + shortId(node.asString()) : pc.customName();
    }

    public static void dispatchTerminalOpsLog(final ServerPlayer player, final NetworkUuid net,
                                              final ServerLevel level) {
        final MainframeBlockEntity mainframe = resolveMainframe(level, net);
        PacketDistributor.sendToPlayer(player, new OperationsLogPayload(
                mainframe != null ? mainframe.recentOperations() : List.of()));
    }

    private static void handleOpsLog(final OperationsLogPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ComputerTerminalMenu menu) {
                menu.setOperationsLog(payload.operations());
            }
        });
    }

    public static void dispatchActiveOperations(final ServerPlayer player, final NetworkUuid net,
                                                final ServerLevel level) {
        final MainframeBlockEntity mainframe = resolveMainframe(level, net);
        PacketDistributor.sendToPlayer(player, new ActiveOperationsPayload(
                mainframe != null ? mainframe.activeOperationRecords() : List.of()));
    }

    private static void handleActiveOps(final ActiveOperationsPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ComputerTerminalMenu menu) {
                menu.setActiveOps(payload.operations());
            }
        });
    }

    private static ComputerTerminalHost openTerminal(final IPayloadContext context,
                                                     final BlockPos monitorPos, final BlockPos hostPos) {
        if (context.player() instanceof ServerPlayer player
                && player.containerMenu instanceof ComputerTerminalMenu menu
                && menu.monitorPos().equals(monitorPos)
                && menu.hostPos().equals(hostPos)
                && player.level().getBlockEntity(hostPos) instanceof ComputerTerminalHost host) {
            return host;
        }
        return null;
    }

    private static ServerBreakdownPayload collectBreakdown(final ServerLevel level, final NetworkUuid net,
                                                           final StorageKey key) {
        final Map<NodeUuid, Long> perServer = dev.jsc.jscomputronics.module.computing.operation.NetworkStorage
                .of(level, net).breakdown(key);
        final List<ServerBreakdownPayload.ServerHolding> rows = new ArrayList<>();
        for (final Map.Entry<NodeUuid, Long> e : perServer.entrySet()) {
            if (rows.size() >= ServerBreakdownPayload.MAX) {
                break;
            }
            rows.add(new ServerBreakdownPayload.ServerHolding(
                    e.getKey().asString(), serverLabel(level, e.getKey()), e.getValue()));
        }
        return new ServerBreakdownPayload(rows);
    }

    public static String serverLabel(final ServerLevel level, final NodeUuid node) {
        final String fallback = "SRV-" + shortId(node.asString());
        return dev.jsc.jscomputronics.common.network.NetworkSystem.get(level).locationOf(node)
                .map(loc -> level.getBlockEntity(net.minecraft.core.BlockPos.of(loc.rackPos()))
                        instanceof dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity rack
                        ? rack.getServers().getStackInSlot(loc.slot()) : ItemStack.EMPTY)
                .map(dev.jsc.jscomputronics.module.computing.item.ServerItem::customName)
                .filter(name -> !name.isEmpty())
                .orElse(fallback);
    }

    private static Set<NodeUuid> toNodes(final List<String> keys) {
        final Set<NodeUuid> nodes = new HashSet<>();
        for (final String key : keys) {
            try {
                nodes.add(NodeUuid.fromString(key));
            } catch (final IllegalArgumentException ignored) {
                // skip a malformed key rather than fail the whole request
            }
        }
        return nodes;
    }

    private static void handleRequestNodes(final RequestNetworkNodesPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof MainframeMenu menu
                    && menu.blockPos().equals(payload.mainframePos())
                    && player.level() instanceof ServerLevel level
                    && level.getBlockEntity(payload.mainframePos()) instanceof MainframeBlockEntity mf) {
                PacketDistributor.sendToPlayer(player, collectNodes(level, mf));
            }
        });
    }

    private static void handleNodes(final NetworkNodesPayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                dev.jsc.jscomputronics.module.computing.client.NetworkOverviewScreen.open(payload));
    }

    private static NetworkNodesPayload collectNodes(final ServerLevel level, final MainframeBlockEntity mf) {
        final UnitFormatter fmt = UnitFormatter.forCurrentLocale();
        final List<NetworkNodeInfo> nodes = new ArrayList<>();
        final NetworkUuid net = mf.networkUuid();

        nodes.add(new NetworkNodeInfo(NetworkNodeInfo.KIND_MAINFRAME,
                shortId(mf.nodeUuid().asString()),
                fmt.compact(mf.capacity(), Unit.IT_PER_TICK),
                net != null));

        if (net != null) {
            final NetworkSystem system = NetworkSystem.get(level);
            for (final ServerNode server : system.serversOf(net)) {
                if (nodes.size() >= NetworkNodesPayload.MAX_NODES) {
                    break;
                }
                nodes.add(new NetworkNodeInfo(NetworkNodeInfo.KIND_SERVER,
                        shortId(server.nodeUuid().asString()),
                        fmt.compact(server.storageMB(), Unit.MB), true));
            }
            for (final SubframeNode subframe : system.subframesOf(net)) {
                if (nodes.size() >= NetworkNodesPayload.MAX_NODES) {
                    break;
                }
                nodes.add(new NetworkNodeInfo(NetworkNodeInfo.KIND_SUBFRAME,
                        shortId(subframe.nodeUuid().asString()),
                        fmt.compact(subframe.contributedCapacity(), Unit.IT_PER_TICK), true));
            }
            for (final NetworkSystem.PersonalComputerNode pc : system.personalComputersOf(net)) {
                if (nodes.size() >= NetworkNodesPayload.MAX_NODES) {
                    break;
                }
                nodes.add(new NetworkNodeInfo(NetworkNodeInfo.KIND_PC,
                        shortId(pc.nodeUuid().asString()),
                        fmt.compact(pc.capacity(), Unit.IT_PER_TICK), true));
            }
        }
        return new NetworkNodesPayload(net != null ? shortId(net.asString()) : "", nodes);
    }

    private static String shortId(final String uuid) {
        return uuid.length() >= 6 ? uuid.substring(0, 6) : uuid;
    }

    public static void sendSnapshot(final ServerPlayer player, final ServerLevel level, final NetworkUuid network) {
        if (player == null || player.isRemoved()) {
            return; // no one to send to (e.g. the requester logged out before the Operation settled)
        }
        final Map<StorageKey, Long> totals = network == null
                ? Map.of()
                : dev.jsc.jscomputronics.module.computing.operation.NetworkStorage.of(level, network).query();
        final List<NetworkItemEntry> entries = new ArrayList<>(Math.min(totals.size(),
                NetworkSnapshotPayload.MAX_ENTRIES));
        // Bounded by the wire cap so encoding never overflows the StreamCodec. The entry carries the
        // full stack (components and all), so the terminal shows the enchanted item, not a bare one.
        totals.entrySet().stream().limit(NetworkSnapshotPayload.MAX_ENTRIES)
                .forEach(e -> entries.add(new NetworkItemEntry(e.getKey(), e.getValue())));
        PacketDistributor.sendToPlayer(player, new NetworkSnapshotPayload(entries));
    }

    // Local storage (the Storage tab) — disk-backed, component-preserving quantity view.

    private static void handleLocalSnapshot(final LocalStorageSnapshotPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ComputerTerminalMenu menu) {
                menu.setLocalItems(payload.items());
            }
        });
    }

    public static void dispatchLocalSnapshot(final ServerPlayer player, final ComputerTerminalHost host) {
        final Map<StorageKey, Long> view = host.localStore().view();
        final List<NetworkItemEntry> entries = new ArrayList<>(
                Math.min(view.size(), LocalStorageSnapshotPayload.MAX_ENTRIES));
        view.entrySet().stream().limit(LocalStorageSnapshotPayload.MAX_ENTRIES)
                .forEach(e -> entries.add(new NetworkItemEntry(e.getKey(), e.getValue())));
        PacketDistributor.sendToPlayer(player, new LocalStorageSnapshotPayload(entries));
    }

    private static void handleLocalWithdraw(final TerminalLocalWithdrawPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host == null || !(context.player() instanceof ServerPlayer player)
                    || payload.quantity() <= 0L) {
                return;
            }
            final StorageKey key = payload.key();
            if (key.isFluid()) {
                return; // a fluid cannot be held in the inventory — withdraw it via an Export Bus
            }
            final int maxStack = Math.max(1, key.stack(1).getMaxStackSize());
            // Take only as much as the player's inventory can actually hold, so a "withdraw all" on a
            // huge stack never extracts more than fits — items must never be destroyed by overflow.
            final long want = Math.min(payload.quantity(), host.localStore().count(key));
            final long toWithdraw = Math.min(want, inventoryRoomFor(player, key, maxStack));
            if (toWithdraw <= 0L) {
                return;
            }
            long remaining = host.localStore().extract(key, toWithdraw);
            while (remaining > 0L) {
                final int batch = (int) Math.min(remaining, maxStack);
                final ItemStack out = key.stack(batch);
                player.getInventory().add(out); // mutates out to whatever did not fit
                final int placed = batch - out.getCount();
                remaining -= placed;
                if (placed <= 0) {
                    break; // inventory unexpectedly full — return the remainder below
                }
            }
            if (remaining > 0L) {
                host.localStore().insert(key, remaining); // belt-and-braces: never lose the remainder
            }
            dispatchLocalSnapshot(player, host);
        });
    }

    private static long inventoryRoomFor(final ServerPlayer player, final StorageKey key, final int maxStack) {
        final ItemStack probe = key.stack(1);
        final net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        long room = 0L;
        for (int i = 0; i < inv.items.size(); i++) {
            final ItemStack slot = inv.items.get(i);
            if (slot.isEmpty()) {
                room += maxStack;
            } else if (ItemStack.isSameItemSameComponents(slot, probe)) {
                room += Math.max(0, maxStack - slot.getCount());
            }
        }
        return room;
    }

    private static void handleLocalDeposit(final TerminalLocalDepositPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host == null || !(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof ComputerTerminalMenu menu)) {
                return;
            }
            final int idx = payload.slotIndex();
            final boolean fromCursor = idx == TerminalLocalDepositPayload.CURSOR
                    || idx == TerminalLocalDepositPayload.CURSOR_ONE;
            if (!fromCursor && (idx < menu.storageSlotCount() || idx >= menu.slots.size())) {
                return; // a slot source must be a player-inventory menu slot
            }
            final net.minecraft.world.inventory.Slot slot = fromCursor ? null : menu.getSlot(idx);
            final ItemStack source = fromCursor ? menu.getCarried() : slot.getItem();
            if (source.isEmpty()) {
                return;
            }
            final int amount = idx == TerminalLocalDepositPayload.CURSOR_ONE ? 1 : source.getCount();
            final long stored = host.localStore().insert(StorageKey.of(source), amount);
            if (stored <= 0L) {
                return;
            }
            source.shrink((int) stored);
            if (fromCursor) {
                menu.setCarried(source.isEmpty() ? ItemStack.EMPTY : source);
            } else {
                slot.set(source.isEmpty() ? ItemStack.EMPTY : source);
            }
            menu.broadcastChanges();
            dispatchLocalSnapshot(player, host);
        });
    }
}
