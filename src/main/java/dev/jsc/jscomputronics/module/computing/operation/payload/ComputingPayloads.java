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
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
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
    }

    private static void handleRenamePc(final RenamePcPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof PersonalComputerMenu menu
                    && menu.pcPos().equals(payload.pcPos())
                    && player.level().getBlockEntity(payload.pcPos()) instanceof PersonalComputerBlockEntity pc) {
                pc.setCustomName(payload.name());
            }
        });
    }

    private static void handleLocalUpload(final TerminalLocalUploadPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host == null || host.networkUuid() == null
                    || !(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || payload.stack().isEmpty() || payload.quantity() <= 0L) {
                return;
            }
            final NetworkUuid net = host.networkUuid();
            final MainframeBlockEntity mainframe = resolveMainframe(level, net);
            if (mainframe == null) {
                return;
            }
            final StorageKey key = StorageKey.of(payload.stack());
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
        return dispatch(player, pc, (level, net, mf) -> {
            final var op = mf.submitNetworkSelect(item, quantity,
                    new net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper(player.getInventory()),
                    "inventory");
            if (op == null) {
                return false;
            }
            // Stop the pull if the player logs out mid-stream: their inventory is saved and orphaned
            // on disconnect, so writing into it afterwards would destroy the items.
            op.abortWhen(player::isRemoved).onSettle(() -> sendSnapshot(player, level, net));
            return true;
        });
    }

    public static boolean dispatchInsert(final ServerPlayer player, final PersonalComputerBlockEntity pc,
                                         final ItemStack payload) {
        return dispatch(player, pc, (level, net, mf) -> {
            final var op = mf.submitNetworkInsert(StorageKey.of(payload), payload.getCount(), "inventory");
            if (op == null) {
                return false;
            }
            op.onSettle(() -> {
                final long leftover = op.leftover();
                if (leftover > 0L) {
                    returnToPlayer(player, payload.copyWithCount((int) leftover));
                }
                sendSnapshot(player, level, net);
            });
            return true;
        });
    }

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
            if (mainframe == null || payload.stack().isEmpty()) {
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
            final StorageKey key = StorageKey.of(payload.stack());
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
    private record Dest(net.neoforged.neoforge.items.IItemHandler handler, String label,
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

    private static void handleRequestBreakdown(final RequestServerBreakdownPayload payload,
                                               final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host != null && host.networkUuid() != null
                    && context.player() instanceof ServerPlayer player
                    && player.level() instanceof ServerLevel level) {
                PacketDistributor.sendToPlayer(player, collectBreakdown(level, host.networkUuid(), payload.stack()));
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
                                                           final ItemStack stack) {
        final Map<NodeUuid, Long> perServer = dev.jsc.jscomputronics.module.computing.operation.NetworkStorage
                .of(level, net).breakdown(StorageKey.of(stack));
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
                .forEach(e -> entries.add(new NetworkItemEntry(e.getKey().stack(1), e.getValue())));
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
                .forEach(e -> entries.add(new NetworkItemEntry(e.getKey().stack(1), e.getValue())));
        PacketDistributor.sendToPlayer(player, new LocalStorageSnapshotPayload(entries));
    }

    private static void handleLocalWithdraw(final TerminalLocalWithdrawPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final ComputerTerminalHost host = openTerminal(context, payload.monitorPos(), payload.hostPos());
            if (host == null || !(context.player() instanceof ServerPlayer player)
                    || payload.stack().isEmpty() || payload.quantity() <= 0L) {
                return;
            }
            final StorageKey key = StorageKey.of(payload.stack());
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
