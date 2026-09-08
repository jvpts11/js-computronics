/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os.media;

import dev.jstech.computers.ComputingModule;
import dev.jstech.computers.PeripheralLinks;
import dev.jstech.computers.storage.ServerStorageContents;
import dev.jstech.core.peripheral.PeripheralCableType;
import dev.jstech.core.peripheral.IPeripheralEndpoint;
import dev.jstech.core.peripheral.PeripheralLinkValidator;
import dev.jstech.core.peripheral.IPeripheralOwner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * BlockEntity for the {@link MediaReaderBlock}. Holds exactly one {@link MediaItem} in an internal
 * slot; exposes the loaded payload via {@link #insertedPayload()}, the media kind via
 * {@link #insertedKind()}, and the data contents via {@link #insertedData()}.
 *
 * <p>Persistence: the slot is saved/loaded via the standard NeoForge {@link ItemStackHandler}
 * serialization, using {@link #saveAdditional} / {@link #loadAdditional} with a
 * {@link HolderLookup.Provider} (the 1.21.1 form). Sync to the client is done via
 * {@link #getUpdateTag} and {@link ClientboundBlockEntityDataPacket#create(BlockEntity)}.
 *
 * <p>Participates in the {@link PeripheralCableType#COMPUTING} peripheral system as an endpoint.
 * Each server tick the reader runs a BFS via {@link PeripheralLinks#discoverOwner} to auto-link
 * to the nearest computer, mirroring the monitor pattern. The linked owner position is stored in
 * NBT and restored on world reload.
 */
public class MediaReaderBlockEntity extends BlockEntity implements IPeripheralEndpoint {

    private static final String NBT_SLOT = "MediaSlot";
    private static final String NBT_LINKED_OWNER = "LinkedOwner";

    @Nullable
    private Long linkedOwner;

    private final ItemStackHandler slot = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(final int slotIndex, final ItemStack stack) {
            return acceptsMedia(stack);
        }

        @Override
        public int getSlotLimit(final int slotIndex) {
            return 1;
        }

        @Override
        protected void onContentsChanged(final int slotIndex) {
            setChanged();
            syncToClients();
        }
    };

    public MediaReaderBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.MEDIA_READER_BE.get(), pos, state);
    }

    /** The drive type of this reader's block, deciding which media formats its slot accepts. */
    public MediaDriveType driveType() {
        return getBlockState().getBlock() instanceof MediaReaderBlock drive
                ? drive.driveType() : MediaDriveType.FLOPPY_DRIVE;
    }

    /**
     * Returns whether this reader accepts the given stack. A {@link FormattedMediaItem} is accepted
     * only when this drive can read its {@link MediaFormat}; generic media is accepted by any drive.
     */
    public boolean acceptsMedia(final ItemStack stack) {
        if (stack.getItem() instanceof FormattedMediaItem media) {
            return driveType().accepts(media.format());
        }
        return stack.getItem() instanceof MediaItem;
    }

    /**
     * Returns the {@link MediaFormat} of the inserted medium, or {@code null} when the slot is empty
     * or the medium carries no fixed format.
     */
    @Nullable
    public MediaFormat insertedFormat() {
        return slot.getStackInSlot(0).getItem() instanceof FormattedMediaItem media
                ? media.format() : null;
    }

    // ─── IPeripheralEndpoint ──────────────────────────────────────────────────

    @Override
    public PeripheralCableType cableType() {
        return PeripheralCableType.COMPUTING;
    }

    @Override
    public Optional<Long> linkedOwner() {
        return Optional.ofNullable(linkedOwner);
    }

    @Override
    public void onOwnerLinked(final long ownerPos) {
        linkedOwner = ownerPos;
        setChanged();
    }

    @Override
    public void onOwnerUnlinked() {
        linkedOwner = null;
        setChanged();
    }

    /**
     * Returns the linked computer's position, or {@code null} when not yet linked.
     */
    @Nullable
    public BlockPos ownerPos() {
        return linkedOwner == null ? null : BlockPos.of(linkedOwner);
    }

    public static void serverTick(final Level level, final BlockPos pos,
                                  final BlockState state, final MediaReaderBlockEntity be) {
        if (level instanceof ServerLevel serverLevel) {
            be.tick(serverLevel);
        }
    }

    private void tick(final ServerLevel level) {
        final long self = worldPosition.asLong();
        final PeripheralLinkValidator validator = PeripheralLinks.validator(level);
        if (linkedOwner == null) {
            // Find a reachable computer and link to it (the validator notifies both sides).
            PeripheralLinks.discoverOwner(level, self)
                    .ifPresent(ownerPos -> validator.tryEstablishLink(ownerPos, self));
        } else {
            // Drop the link if the computer is gone or the cable path is broken.
            final boolean ownerPresent =
                    level.getBlockEntity(BlockPos.of(linkedOwner)) instanceof IPeripheralOwner;
            if (!ownerPresent
                    || !validator.isLinkStillValid(linkedOwner, self, PeripheralCableType.COMPUTING)) {
                unlink(level);
            }
        }
    }

    /**
     * Breaks the peripheral link from this reader's side, notifying the owner so it frees the slot.
     * Safe to call when no link exists (no-op).
     */
    public void unlink(final ServerLevel level) {
        if (linkedOwner != null
                && level.getBlockEntity(BlockPos.of(linkedOwner)) instanceof IPeripheralOwner owner) {
            owner.onEndpointUnlinked(worldPosition.asLong());
        }
        onOwnerUnlinked();
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Returns the OS/program id of the inserted media, or {@code null} when the slot is empty
     * or the media carries no payload component.
     */
    @Nullable
    public ResourceLocation insertedPayload() {
        final ItemStack stack = slot.getStackInSlot(0);
        if (stack.isEmpty()) {
            return null;
        }
        return MediaItem.payload(stack);
    }

    /**
     * Returns the {@link MediaKind} of the inserted medium, or {@code null} when the slot is empty.
     */
    @Nullable
    public MediaKind insertedKind() {
        final ItemStack stack = slot.getStackInSlot(0);
        if (stack.isEmpty()) {
            return null;
        }
        return MediaItem.kind(stack);
    }

    /**
     * Returns the storage snapshot of the inserted DATA medium. Returns
     * {@link ServerStorageContents#EMPTY} when the slot is empty or the medium is not a DATA kind.
     *
     * <p>// TODO(os): DATA transfer, move network storage to/from a DATA medium via a timed
     * // Operation, bounded by capacity(stack).
     */
    public ServerStorageContents insertedData() {
        final ItemStack stack = slot.getStackInSlot(0);
        if (stack.isEmpty()) {
            return ServerStorageContents.EMPTY;
        }
        return MediaItem.data(stack);
    }

    /**
     * Attempts to insert the given media stack into the slot.
     *
     * @param stack the item to insert (must be a {@link MediaItem})
     * @return the remainder after insertion (empty if fully inserted, unchanged if slot occupied)
     */
    public ItemStack insertMedia(final ItemStack stack) {
        return slot.insertItem(0, stack, false);
    }

    /**
     * Removes and returns whatever media is currently in the slot. Returns
     * {@link ItemStack#EMPTY} when the slot is already empty.
     */
    public ItemStack ejectMedia() {
        final ItemStack held = slot.getStackInSlot(0);
        if (held.isEmpty()) {
            return ItemStack.EMPTY;
        }
        slot.setStackInSlot(0, ItemStack.EMPTY);
        return held;
    }

    /**
     * Drops the slot contents into the world at this block's position. Called on block removal.
     */
    public void dropContents(final Level level, final BlockPos pos) {
        final ItemStack held = slot.getStackInSlot(0);
        if (!held.isEmpty()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), held);
            slot.setStackInSlot(0, ItemStack.EMPTY);
        }
    }

    /** Direct access to the slot handler, e.g. for capability registration. */
    public ItemStackHandler mediaSlot() {
        return slot;
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(NBT_SLOT, slot.serializeNBT(registries));
        if (linkedOwner != null) {
            tag.putLong(NBT_LINKED_OWNER, linkedOwner);
        }
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(NBT_SLOT)) {
            slot.deserializeNBT(registries, tag.getCompound(NBT_SLOT));
        }
        linkedOwner = tag.contains(NBT_LINKED_OWNER) ? tag.getLong(NBT_LINKED_OWNER) : null;
    }

    // ─── Client sync (1.21.1 forms) ──────────────────────────────────────────

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        tag.put(NBT_SLOT, slot.serializeNBT(registries));
        if (linkedOwner != null) {
            tag.putLong(NBT_LINKED_OWNER, linkedOwner);
        }
        return tag;
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void syncToClients() {
        if (level != null && !level.isClientSide()) {
            /*
             * Read the state from the world, not the cached one: while the drive is being broken the world
             * already holds its replacement, and writing the drive's state back (the drop empties the slot)
             * would make the chunk abort the removal, leaving the drive standing with its disc on the floor.
             */
            final BlockState state = level.getBlockState(worldPosition);
            if (!(state.getBlock() instanceof MediaReaderBlock)) {
                return;
            }
            final boolean loaded = !slot.getStackInSlot(0).isEmpty();
            if (state.hasProperty(MediaReaderBlock.LOADED) && state.getValue(MediaReaderBlock.LOADED) != loaded) {
                // Flip the LOADED blockstate so the model shows the lit "_active" face.
                level.setBlock(worldPosition, state.setValue(MediaReaderBlock.LOADED, loaded), Block.UPDATE_CLIENTS);
            } else {
                level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
            }
        }
    }
}
