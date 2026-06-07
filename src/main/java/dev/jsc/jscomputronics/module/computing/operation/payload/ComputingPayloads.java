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
import dev.jsc.jscomputronics.module.computing.blockentity.PersonalComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.menu.ComputerTerminalMenu;
import dev.jsc.jscomputronics.module.computing.menu.MainframeMenu;
import dev.jsc.jscomputronics.module.computing.menu.PersonalComputerMenu;
import dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

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
        registrar.playToServer(NetworkSelectPayload.TYPE, NetworkSelectPayload.STREAM_CODEC,
                ComputingPayloads::handleSelect);
        registrar.playToServer(NetworkInsertPayload.TYPE, NetworkInsertPayload.STREAM_CODEC,
                ComputingPayloads::handleInsert);
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
    }

    private static void handleSelect(final NetworkSelectPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (openPc(context, payload.pcPos()) instanceof PersonalComputerBlockEntity pc) {
                dispatchSelect((ServerPlayer) context.player(), pc, payload.item(), payload.quantity());
            }
        });
    }

    private static void handleInsert(final NetworkInsertPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (openPc(context, payload.pcPos()) instanceof PersonalComputerBlockEntity pc
                    && context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof PersonalComputerMenu menu) {
                final ItemStack carried = menu.getCarried();
                if (carried.isEmpty()) {
                    return;
                }
                final ItemStack payloadStack = payload.all() ? carried.copy() : carried.copyWithCount(1);
                // Take the items off the cursor now; the Operation returns any overflow.
                carried.shrink(payloadStack.getCount());
                menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                menu.broadcastChanges();
                if (!dispatchInsert(player, pc, payloadStack)) {
                    player.getInventory().placeItemBackInInventory(payloadStack);
                }
            }
        });
    }

    private static PersonalComputerBlockEntity openPc(final IPayloadContext context, final BlockPos pos) {
        if (context.player() instanceof ServerPlayer player
                && player.containerMenu instanceof PersonalComputerMenu menu
                && menu.pcPos().equals(pos)
                && player.level().getBlockEntity(pos) instanceof PersonalComputerBlockEntity pc) {
            return pc;
        }
        return null;
    }

    // Network-operation dispatch — the ONLY way storage is touched. Every PC

    public static boolean dispatchSelect(final ServerPlayer player, final PersonalComputerBlockEntity pc,
                                         final net.minecraft.world.item.Item item, final long quantity) {
        return dispatch(player, pc, (level, net, mf) -> mf.submitOperation(
                new dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperationTask(
                        level, net, item, quantity, player),
                dev.jsc.jscomputronics.common.operation.OperationPriority.MEDIUM));
    }

    public static boolean dispatchInsert(final ServerPlayer player, final PersonalComputerBlockEntity pc,
                                         final ItemStack payload) {
        return dispatch(player, pc, (level, net, mf) -> mf.submitOperation(
                new dev.jsc.jscomputronics.module.computing.operation.NetworkInsertOperationTask(
                        level, net, payload, player),
                dev.jsc.jscomputronics.common.operation.OperationPriority.MEDIUM));
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
            if (host != null && host.networkUuid() != null
                    && context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level) {
                final NetworkUuid net = host.networkUuid();
                final MainframeBlockEntity mainframe = resolveMainframe(level, net);
                if (mainframe != null) {
                    final Set<NodeUuid> sources =
                            payload.serverKeys().isEmpty() ? null : toNodes(payload.serverKeys());
                    mainframe.submitOperation(
                            new dev.jsc.jscomputronics.module.computing.operation.NetworkSelectToStorageOperationTask(
                                    level, net, payload.item(), payload.quantity(), payload.hostPos(), player, sources),
                            dev.jsc.jscomputronics.common.operation.OperationPriority.MEDIUM);
                }
            }
        });
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
            final boolean dispatched = mainframe.submitOperation(
                    new dev.jsc.jscomputronics.module.computing.operation.NetworkInsertFromTerminalOperationTask(
                            level, net, inFlight, player),
                    dev.jsc.jscomputronics.common.operation.OperationPriority.MEDIUM);
            if (!dispatched) {
                // Error path (no live dispatcher): hand the items straight to the inventory.
                player.getInventory().placeItemBackInInventory(inFlight);
            }
        });
    }

    private static void handleRequestBreakdown(final RequestServerBreakdownPayload payload,
                                               final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host != null && host.networkUuid() != null
                    && context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level) {
                PacketDistributor.sendToPlayer(player, collectBreakdown(level, host.networkUuid(), payload.item()));
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
                                                           final Item item) {
        final Map<NodeUuid, Long> perServer =
                dev.jsc.jscomputronics.module.computing.operation.NetworkStorage.of(level, net).breakdown(item);
        final List<ServerBreakdownPayload.ServerHolding> rows = new ArrayList<>();
        for (final Map.Entry<NodeUuid, Long> e : perServer.entrySet()) {
            if (rows.size() >= ServerBreakdownPayload.MAX) {
                break;
            }
            final String key = e.getKey().asString();
            rows.add(new ServerBreakdownPayload.ServerHolding(key, "SRV-" + shortId(key), e.getValue()));
        }
        return new ServerBreakdownPayload(rows);
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
        if (player == null) {
            return;
        }
        final Map<Item, Long> totals = network == null
                ? Map.of()
                : dev.jsc.jscomputronics.module.computing.operation.NetworkStorage.of(level, network).query();
        final List<NetworkItemEntry> entries = new ArrayList<>(Math.min(totals.size(),
                NetworkSnapshotPayload.MAX_ENTRIES));
        // Bounded by the wire cap so encoding never overflows the StreamCodec.
        totals.entrySet().stream().limit(NetworkSnapshotPayload.MAX_ENTRIES)
                .forEach(e -> entries.add(new NetworkItemEntry(new ItemStack(e.getKey()), e.getValue())));
        PacketDistributor.sendToPlayer(player, new NetworkSnapshotPayload(entries));
    }
}
