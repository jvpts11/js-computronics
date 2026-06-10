/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.blockentity;

import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.network.ServerRouterElement;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.block.DataCableBlock;
import dev.jsc.jscomputronics.module.computing.datacenter.DatacenterSection;
import dev.jsc.jscomputronics.module.computing.datacenter.LoadBalanceMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * BlockEntity backing the Datacenter Station — a kiosk that aggregates ONE datacenter section (one output face of one Server Router) as a single unit.
 */
public class DatacenterStationBlockEntity extends BlockEntity {

    @Nullable
    private BlockPos boundRouterPos;
    @Nullable
    private Direction boundFace;

    public DatacenterStationBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.DATACENTER_STATION_BE.get(), pos, state);
    }

    @Nullable
    public NetworkUuid network() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        final NetworkSystem system = NetworkSystem.get(serverLevel);
        for (final Direction direction : Direction.values()) {
            final BlockPos neighbor = worldPosition.relative(direction);
            if (serverLevel.getBlockState(neighbor).getBlock() instanceof DataCableBlock) {
                final var net = system.connectivity().networkOf(neighbor.asLong());
                if (net.isPresent()) {
                    return net.get();
                }
            }
        }
        return null;
    }

    @Nullable
    public BlockPos mainframePos() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        final NetworkUuid net = network();
        if (net == null) {
            return null;
        }
        return NetworkSystem.get(serverLevel).mainframePositionOf(net).map(BlockPos::of).orElse(null);
    }

    @Nullable
    private ServerRouterBlockEntity boundRouter() {
        if (boundRouterPos == null || level == null) {
            return null;
        }
        return level.getBlockEntity(boundRouterPos) instanceof ServerRouterBlockEntity router ? router : null;
    }

    @Nullable
    public DatacenterSection section() {
        final ServerRouterBlockEntity router = boundRouter();
        if (router == null || boundFace == null) {
            return null;
        }
        for (final DatacenterSection section : router.sections()) {
            if (section.face() == boundFace) {
                return section;
            }
        }
        return null;
    }

    public List<NodeUuid> sectionServers() {
        final DatacenterSection section = section();
        if (section == null) {
            return List.of();
        }
        return dev.jsc.jscomputronics.module.computing.datacenter.LoadBalancer.order(
                section.servers(), loadBalanceMode(), level instanceof ServerLevel sl ? sl : null, network());
    }

    public LoadBalanceMode loadBalanceMode() {
        final ServerRouterBlockEntity router = boundRouter();
        return router != null && boundFace != null
                ? router.loadBalanceMode(boundFace)
                : LoadBalanceMode.ROUND_ROBIN;
    }

    public void cycleLoadBalanceMode() {
        final ServerRouterBlockEntity router = boundRouter();
        if (router != null && boundFace != null) {
            router.cycleLoadBalanceMode(boundFace);
        }
    }

    /**
     * A selectable datacenter section on the network: a router position, an output face, and a label.
     */
    public record SectionRef(BlockPos routerPos, Direction face, String label) {
    }

    public List<SectionRef> availableSections() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return List.of();
        }
        final NetworkUuid net = network();
        if (net == null) {
            return List.of();
        }
        final NetworkSystem system = NetworkSystem.get(serverLevel);
        final List<SectionRef> refs = new ArrayList<>();
        for (final ServerRouterElement router : system.routersOf(net)) {
            if (serverLevel.getBlockEntity(BlockPos.of(router.pos())) instanceof ServerRouterBlockEntity routerBe) {
                final String routerName = routerBe.customName().isEmpty() ? "Router" : routerBe.customName();
                for (final DatacenterSection section : routerBe.sections()) {
                    refs.add(new SectionRef(BlockPos.of(router.pos()), section.face(),
                            routerName + " · " + section.face().getName().toUpperCase(Locale.ROOT)));
                }
            }
        }
        return refs;
    }

    public void bindSection(@Nullable final BlockPos routerPos, @Nullable final Direction face) {
        this.boundRouterPos = routerPos == null ? null : routerPos.immutable();
        this.boundFace = face;
        setChanged();
    }

    @Nullable
    private Long adjacentCable() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        final NetworkSystem system = NetworkSystem.get(serverLevel);
        for (final Direction direction : Direction.values()) {
            final BlockPos neighbor = worldPosition.relative(direction);
            if (serverLevel.getBlockState(neighbor).getBlock() instanceof DataCableBlock
                    && system.connectivity().contains(neighbor.asLong())) {
                return neighbor.asLong();
            }
        }
        return null;
    }

    public void ensureBound() {
        // One forced topology refresh per GUI opening, so binding (and the first view) sees the
        // racks/Servers registered this tick instead of a section cache up to a second old.
        if (level instanceof ServerLevel serverLevel) {
            final NetworkUuid net = network();
            if (net != null) {
                for (final ServerRouterElement router : NetworkSystem.get(serverLevel).routersOf(net)) {
                    if (serverLevel.getBlockEntity(BlockPos.of(router.pos()))
                            instanceof ServerRouterBlockEntity routerBe) {
                        routerBe.recomputeNow();
                    }
                }
            }
        }
        if (section() != null) {
            return;
        }
        final List<SectionRef> available = availableSections();
        if (available.isEmpty()) {
            return;
        }
        SectionRef chosen = available.get(0);
        final Long myCable = adjacentCable();
        if (myCable != null && level instanceof ServerLevel serverLevel) {
            final var index = NetworkSystem.get(serverLevel).connectivity();
            for (final SectionRef ref : available) {
                final long branchStart = ref.routerPos().relative(ref.face()).asLong();
                if (index.reachableFrom(branchStart, java.util.Set.of(ref.routerPos().asLong()))
                        .contains(myCable)) {
                    chosen = ref;
                    break;
                }
            }
        }
        bindSection(chosen.routerPos(), chosen.face());
    }

    public void bindNext() {
        final List<SectionRef> available = availableSections();
        if (available.isEmpty()) {
            return;
        }
        int current = -1;
        for (int i = 0; i < available.size(); i++) {
            if (available.get(i).routerPos().equals(boundRouterPos) && available.get(i).face() == boundFace) {
                current = i;
                break;
            }
        }
        final SectionRef next = available.get((current + 1) % available.size());
        bindSection(next.routerPos(), next.face());
    }

    public String sectionLabel() {
        if (boundFace == null) {
            return "No section";
        }
        final ServerRouterBlockEntity router = boundRouter();
        final String routerName = router == null || router.customName().isEmpty() ? "Router" : router.customName();
        return routerName + " · " + boundFace.getName().toUpperCase(Locale.ROOT);
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (boundRouterPos != null && boundFace != null) {
            tag.putLong("Router", boundRouterPos.asLong());
            tag.putByte("Face", (byte) boundFace.get3DDataValue());
        }
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Router") && tag.contains("Face")) {
            boundRouterPos = BlockPos.of(tag.getLong("Router"));
            boundFace = Direction.from3DDataValue(tag.getByte("Face") & 0xFF);
        } else {
            boundRouterPos = null;
            boundFace = null;
        }
    }
}
