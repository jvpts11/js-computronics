/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.jstech.computers.ComputingModule;
import dev.jstech.computers.block.part.AbstractBusPart;
import dev.jstech.computers.block.part.ICablePart;
import dev.jstech.computers.blockentity.DataCableBlockEntity;
import dev.jstech.core.network.DataTier;
import dev.jstech.core.network.NetworkSystem;
import dev.jstech.core.util.BlockEntityTickers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * A data network cable block.
 */
public class DataCableBlock extends PipeBlock implements EntityBlock {

    public static final MapCodec<DataCableBlock> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.STRING.fieldOf("tier").forGetter(block -> block.tier.name()),
                    propertiesCodec()
            ).apply(instance, (tierName, props) -> new DataCableBlock(props, DataTier.valueOf(tierName))));

    private static final Map<Direction, AABB> PART_BOXES = buildPartBoxes();
    private static final Map<Direction, VoxelShape> PART_SHAPES = buildPartShapes();

    private final DataTier tier;

    public DataCableBlock(final Properties properties, final DataTier tier) {
        super(0.1875F, properties);
        this.tier = tier;
        BlockState defaultState = stateDefinition.any();
        for (final BooleanProperty property : PROPERTY_BY_DIRECTION.values()) {
            defaultState = defaultState.setValue(property, false);
        }
        registerDefaultState(defaultState);
    }

    public DataTier tier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends PipeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (final Direction direction : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction),
                    connectsTo(context.getLevel(), context.getClickedPos(), direction));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(final BlockState state, final Direction direction,
                                     final BlockState neighborState, final LevelAccessor level,
                                     final BlockPos pos, final BlockPos neighborPos) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(direction),
                connectsTo(level, pos, direction));
    }

    private boolean connectsTo(final LevelAccessor level, final BlockPos pos, final Direction direction) {
        final BlockState neighborState = level.getBlockState(pos.relative(direction));
        final var neighbor = neighborState.getBlock();
        if (neighbor instanceof DataCableBlock other) {
            return other.tier == this.tier;
        }
        /*
         * The cable only shows a connection where the device actually accepts a cable on that face
         * (a computer accepts one on its rear only), so the rendered nub never lies about connectivity.
         */
        return neighbor instanceof dev.jstech.core.network.IDataNetworkConnectable device
                && device.acceptedCableTiers().contains(this.tier)
                && device.connectsOnFace(neighborState, direction.getOpposite(), this.tier);
    }

    // Shape: the cable pipe plus a box for each mounted part

    @Override
    protected VoxelShape getShape(final BlockState state, final BlockGetter level,
                                  final BlockPos pos, final CollisionContext context) {
        VoxelShape shape = super.getShape(state, level, pos, context);
        if (level.getBlockEntity(pos) instanceof DataCableBlockEntity cable) {
            for (final Direction direction : Direction.values()) {
                if (cable.hasPart(direction)) {
                    shape = Shapes.or(shape, PART_SHAPES.get(direction));
                }
            }
        }
        return shape;
    }

    // Interaction: configure / pick up a mounted part

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DataCableBlockEntity cable)) {
            return InteractionResult.PASS;
        }
        final Direction face = partFaceAt(cable, pos, hit);
        if (face == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        /*
         * A plain click opens the part's configuration menu (import / export buses); picking a part off
         * the cable is done with a left-click, handled separately so it never breaks the cable. Each bus
         * builds its own menu, and the open packet carries the bus name so the field shows it client-side.
         */
        final ICablePart part = cable.getPart(face);
        if (part instanceof AbstractBusPart bus && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                            (id, inv, p) -> bus.createMenu(id, inv, cable, face),
                            state.getBlock().getName()),
                    buf -> {
                        buf.writeBlockPos(pos);
                        buf.writeByte(face.get3DDataValue());
                        buf.writeUtf(bus.name());
                    });
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemStack getCloneItemStack(final BlockState state, final HitResult target, final LevelReader level,
                                       final BlockPos pos, final Player player) {
        if (level.getBlockEntity(pos) instanceof DataCableBlockEntity cable && cable.hasAnyPart()) {
            /*
             * Pick by the player's look ray (same as the highlight) rather than the merged-shape
             * hit point, which can land on the cable bar in front of the part.
             */
            final Vec3 start = player.getEyePosition();
            final Vec3 end = start.add(player.getViewVector(1.0F).scale(player.blockInteractionRange() + 1.0));
            final Direction face = aimedPart(level, pos, start, end);
            final ICablePart part = face == null ? null : cable.getPart(face);
            if (part != null) {
                return part.partItem();
            }
        }
        return super.getCloneItemStack(state, target, level, pos, player);
    }

    @Nullable
    private static Direction partFaceAt(final DataCableBlockEntity cable, final BlockPos pos,
                                        final BlockHitResult hit) {
        return partFaceAt(cable, pos, hit.getLocation());
    }

    @Nullable
    private static Direction partFaceAt(final DataCableBlockEntity cable, final BlockPos pos, final Vec3 location) {
        final Vec3 local = location.subtract(Vec3.atLowerCornerOf(pos));
        for (final Direction direction : Direction.values()) {
            if (cable.hasPart(direction) && PART_BOXES.get(direction).inflate(0.02).contains(local)) {
                return direction;
            }
        }
        return null;
    }

    @Nullable
    public static Direction aimedPart(final BlockGetter level, final BlockPos pos,
                                      final Vec3 start, final Vec3 end) {
        if (!(level.getBlockEntity(pos) instanceof DataCableBlockEntity cable) || !cable.hasAnyPart()) {
            return null;
        }
        final BlockHitResult cableHit = cableShape(level.getBlockState(pos)).clip(start, end, pos);
        final double cableDist = cableHit == null ? Double.MAX_VALUE : start.distanceToSqr(cableHit.getLocation());
        Direction best = null;
        double bestDist = Double.MAX_VALUE;
        for (final Direction direction : Direction.values()) {
            if (!cable.hasPart(direction)) {
                continue;
            }
            final BlockHitResult hit = PART_SHAPES.get(direction).clip(start, end, pos);
            if (hit != null) {
                final double dist = start.distanceToSqr(hit.getLocation());
                if (dist < bestDist) {
                    bestDist = dist;
                    best = direction;
                }
            }
        }
        return best != null && bestDist <= cableDist + 1.0e-4 ? best : null;
    }

    @Nullable
    public static VoxelShape aimedOutlineShape(final BlockGetter level, final BlockPos pos,
                                               final Vec3 start, final Vec3 end) {
        if (!(level.getBlockEntity(pos) instanceof DataCableBlockEntity cable) || !cable.hasAnyPart()) {
            return null;
        }
        final Direction part = aimedPart(level, pos, start, end);
        return part != null ? PART_SHAPES.get(part) : cableShape(level.getBlockState(pos));
    }

    private static VoxelShape cableShape(final BlockState state) {
        final double lo = 0.3125;
        final double hi = 0.6875;
        VoxelShape shape = Shapes.box(lo, lo, lo, hi, hi, hi);
        if (state.getValue(NORTH)) {
            shape = Shapes.or(shape, Shapes.box(lo, lo, 0.0, hi, hi, lo));
        }
        if (state.getValue(SOUTH)) {
            shape = Shapes.or(shape, Shapes.box(lo, lo, hi, hi, hi, 1.0));
        }
        if (state.getValue(WEST)) {
            shape = Shapes.or(shape, Shapes.box(0.0, lo, lo, lo, hi, hi));
        }
        if (state.getValue(EAST)) {
            shape = Shapes.or(shape, Shapes.box(hi, lo, lo, 1.0, hi, hi));
        }
        if (state.getValue(DOWN)) {
            shape = Shapes.or(shape, Shapes.box(lo, 0.0, lo, hi, lo, hi));
        }
        if (state.getValue(UP)) {
            shape = Shapes.or(shape, Shapes.box(lo, hi, lo, hi, 1.0, hi));
        }
        return shape;
    }

    // Removal: keep part items and buffered items from being lost

    @Override
    public BlockState playerWillDestroy(final Level level, final BlockPos pos, final BlockState state,
                                        final Player player) {
        if (level instanceof ServerLevel serverLevel && !player.getAbilities().instabuild
                && level.getBlockEntity(pos) instanceof DataCableBlockEntity cable) {
            cable.dropAllParts(serverLevel);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            if (level.getBlockEntity(pos) instanceof DataCableBlockEntity cable) {
                // Never void real items the parts are buffering, on any removal path.
                cable.dropAllBuffers(serverLevel);
            }
            /*
             * A cable removed before its lazy onLoad registered it (placed and broken the same tick) has
             * nothing in the index; calling onCableRemoved would throw "Position not registered".
             */
            final var connectivity = NetworkSystem.get(serverLevel).connectivity();
            if (connectivity.contains(pos.asLong())) {
                connectivity.onCableRemoved(pos.asLong());
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // BlockEntity wiring

    @Override
    @Nullable
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new DataCableBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return BlockEntityTickers.create(type, ComputingModule.DATA_CABLE_BE.get(), DataCableBlockEntity::serverTick);
    }


    private static Map<Direction, AABB> buildPartBoxes() {
        final Map<Direction, AABB> boxes = new EnumMap<>(Direction.class);
        /*
         * Bounds the funnel model (widest at the mouth, 5px deep, sitting outside the cable core)
         * so the highlight and hit-test hug the part, not the cable.
         */
        final double a = 2.0 / 16.0;
        final double b = 14.0 / 16.0;
        final double d = 5.0 / 16.0;
        boxes.put(Direction.DOWN, new AABB(a, 0.0, a, b, d, b));
        boxes.put(Direction.UP, new AABB(a, 1.0 - d, a, b, 1.0, b));
        boxes.put(Direction.NORTH, new AABB(a, a, 0.0, b, b, d));
        boxes.put(Direction.SOUTH, new AABB(a, a, 1.0 - d, b, b, 1.0));
        boxes.put(Direction.WEST, new AABB(0.0, a, a, d, b, b));
        boxes.put(Direction.EAST, new AABB(1.0 - d, a, a, 1.0, b, b));
        return boxes;
    }

    private static Map<Direction, VoxelShape> buildPartShapes() {
        final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (final Map.Entry<Direction, AABB> entry : buildPartBoxes().entrySet()) {
            final AABB box = entry.getValue();
            shapes.put(entry.getKey(), Shapes.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
        }
        return shapes;
    }
}
