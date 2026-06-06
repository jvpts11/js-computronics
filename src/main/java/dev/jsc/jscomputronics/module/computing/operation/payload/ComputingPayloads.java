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
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.PersonalComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.menu.MainframeMenu;
import dev.jsc.jscomputronics.module.computing.menu.PersonalComputerMenu;
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
import java.util.List;
import java.util.Map;

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
            if (context.player().containerMenu instanceof PersonalComputerMenu menu) {
                menu.setNetworkItems(payload.items());
            }
        });
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
