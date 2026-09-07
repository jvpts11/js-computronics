/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.block;

import com.mojang.serialization.MapCodec;
import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.blockentity.MainframePartBlockEntity;
import dev.jstech.computronics.menu.MainframeMenu;
import dev.jstech.core.network.IDataNetworkConnectable;
import dev.jstech.core.network.DataTier;
import dev.jstech.core.peripheral.PeripheralCableType;
import dev.jstech.core.peripheral.IPeripheralConnectable;
import dev.jstech.core.tier.HardwareEra;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A structural part of the Mainframe multiblock — one of the 11 non-controller blocks.
 */
public class MainframePartBlock extends HorizontalDirectionalBlock
        implements EntityBlock, IDataNetworkConnectable, IPeripheralConnectable {

    public static final MapCodec<MainframePartBlock> CODEC = simpleCodec(MainframePartBlock::new);

    @Override
    public PeripheralCableType peripheralType() {
        // The whole Mainframe footprint is a COMPUTING peripheral owner, so a
        // Peripheral Cable may attach to any part's face, not just the controller.
        return PeripheralCableType.COMPUTING;
    }

    public static final BooleanProperty CORE = BooleanProperty.create("core");

    // The part inherits its controller's hardware era so the whole footprint wears one skin. The value
    // is the era's level() ordinal (0=Vintage, 1=Legacy, 2=Standard) — only the eras that actually have
    // a Mainframe controller. Storing the ordinal keeps the Minecraft-aware property type out of the
    // pure HardwareEra enum; consumers map it back with HardwareEra.fromLevel(int).
    public static final IntegerProperty ERA = IntegerProperty.create(
            "era", HardwareEra.VINTAGE.level(), HardwareEra.STANDARD.level());

    public MainframePartBlock(final Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CORE, false)
                .setValue(ERA, HardwareEra.STANDARD.level()));
    }

    @Override
    protected MapCodec<MainframePartBlock> codec() {
        return CODEC;
    }

    @Override
    public java.util.Set<DataTier> acceptedCableTiers() {
        // The whole Mainframe footprint takes HBW — a cable may attach to any face.
        return java.util.Set.of(DataTier.T2_HBW);
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CORE, ERA);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof MainframePartBlockEntity part
                && part.controllerPos() != null
                && level.getBlockEntity(part.controllerPos()) instanceof MainframeBlockEntity controller) {
            final BlockPos controllerPos = part.controllerPos();
            // Sneaking anywhere on the cabinet takes its service panel off, the same as on the
            // controller: a player has no way to tell which of the twelve blocks they are looking at.
            if (player.isShiftKeyDown()) {
                controller.toggleServicePanel();
                return InteractionResult.sidedSuccess(false);
            }
            serverPlayer.openMenu(
                    new SimpleMenuProvider(
                            (id, inventory, p) -> new MainframeMenu(id, inventory, controller),
                            level.getBlockState(controllerPos).getBlock().getName()),
                    buf -> buf.writeBlockPos(controllerPos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public BlockState playerWillDestroy(final Level level, final BlockPos pos, final BlockState state,
                                        final Player player) {
        // Drops happen here (not in dissolve) so creative mode never spills items.
        if (level instanceof ServerLevel serverLevel && !player.getAbilities().instabuild
                && level.getBlockEntity(pos) instanceof MainframePartBlockEntity part
                && part.controllerPos() != null
                && level.getBlockState(part.controllerPos()).getBlock()
                        instanceof dev.jstech.core.multiblock.AbstractMultiblockControllerBlock controller) {
            controller.dropContentsExternally(serverLevel, part.controllerPos());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * What the middle mouse button picks off a structural block: the Mainframe itself, in its own era.
     * The whole footprint is drawn as one cabinet, so a player aiming anywhere at it expects to pick the
     * machine; a part has no item of its own and picking one used to hand back nothing at all.
     */
    @Override
    public net.minecraft.world.item.ItemStack getCloneItemStack(final BlockState state,
                                                                final net.minecraft.world.phys.HitResult target,
                                                                final net.minecraft.world.level.LevelReader level,
                                                                final BlockPos pos, final Player player) {
        if (level.getBlockEntity(pos) instanceof MainframePartBlockEntity part && part.controllerPos() != null) {
            final BlockState controller = level.getBlockState(part.controllerPos());
            if (controller.getBlock() instanceof MainframeBlock) {
                return new net.minecraft.world.item.ItemStack(controller.getBlock());
            }
        }
        return super.getCloneItemStack(state, target, level, pos, player);
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof MainframePartBlockEntity part
                && part.controllerPos() != null
                && level.getBlockState(part.controllerPos()).getBlock()
                        instanceof dev.jstech.core.multiblock.AbstractMultiblockControllerBlock controller) {
            controller.dissolve(serverLevel, part.controllerPos(), state.getValue(FACING));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new MainframePartBlockEntity(pos, state);
    }
}
