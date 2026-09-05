/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.blockentity;

import dev.jstech.core.peripheral.PeripheralCableType;
import dev.jstech.core.peripheral.PeripheralEndpoint;
import dev.jstech.core.peripheral.PeripheralLinkValidator;
import dev.jstech.core.peripheral.PeripheralOwner;
import dev.jstech.core.tier.HardwareEra;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.PeripheralLinks;
import dev.jsc.jscomputronics.module.computing.block.PatternEncoderBlock;
import dev.jsc.jscomputronics.module.computing.os.FilesystemKind;
import dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem;
import dev.jsc.jscomputronics.module.computing.os.fs.FileType;
import dev.jsc.jscomputronics.module.computing.os.fs.FsPaths;
import dev.jsc.jscomputronics.module.computing.os.media.FormattedMediaItem;
import dev.jsc.jscomputronics.module.computing.os.media.MediaFormat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * The Pattern Encoder: the burner that puts recipe files onto removable media. It is a peripheral of a
 * computer, linked over the peripheral cable like a drive, and it authors nothing itself; the computer's
 * Pattern Studio does the authoring and hands finished files over one at a time. Each file is a job: the
 * head seeks, the bytes go down at the medium's rate, the medium is read back and compared, and the bay
 * stays locked until the job is over. Jobs queue up, so a session's worth of recipes can be sent at once.
 *
 * <p>The encoder comes in three eras, and each writes the media of its day: a Vintage encoder writes floppy
 * disks, a Legacy one writes CDs, a Standard one writes DVDs, CDs and USB sticks (and no floppies).
 */
public class PatternEncoderBlockEntity extends BlockEntity implements PeripheralEndpoint,
        software.bernie.geckolib.animatable.GeoBlockEntity {

    /** The most jobs waiting behind the one being written. */
    public static final int QUEUE_MAX = 8;

    // The body's only motion: the disc spins and the activity lamp pulses while the head is down. No part
    // ever moves out of the block; everything else the body shows is bone visibility set by the renderer.
    private static final software.bernie.geckolib.animation.RawAnimation WRITE =
            software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.pattern_encoder.write");

    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache geckoCache =
            software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(
            final software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new software.bernie.geckolib.animation.AnimationController<>(this, "work", 0,
                state -> busy() ? state.setAndContinue(WRITE) : software.bernie.geckolib.animation.PlayState.STOP));
    }

    @Override
    public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
        return geckoCache;
    }
    /** How long a finished or failed job stays on the display before the next one starts. */
    public static final int HOLD_TICKS = 30;

    /** Where a job is. */
    public enum Phase {
        IDLE, SEEK, WRITE, VERIFY, DONE, ERROR
    }

    /** One file waiting to be burned: its base name (no extension) and its content. */
    public record BurnRequest(String fileName, String content) {
    }

    private static final String NBT_LINKED_OWNER = "LinkedOwner";

    private final ItemStackHandler media = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(final int slot, final ItemStack stack) {
            return acceptsMedia(stack);
        }

        @Override
        public int getSlotLimit(final int slot) {
            return 1;
        }

        @Override
        public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
            // The bay is locked while the head is on the medium: pulling the disc mid-write is how a
            // real burner ruins one, so the encoder simply refuses.
            return locked() ? ItemStack.EMPTY : super.extractItem(slot, amount, simulate);
        }

        @Override
        protected void onContentsChanged(final int slot) {
            setChanged();
            sync();
        }
    };

    private final Deque<BurnRequest> queue = new ArrayDeque<>();
    private Phase phase = Phase.IDLE;
    private int phaseTicks;
    private int phaseTotal;
    private int writeTicks;
    private String currentFile = "";
    private String message = "";
    private int completed;

    @Nullable
    private Long linkedOwner;

    public PatternEncoderBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.PATTERN_ENCODER_BE.get(), pos, state);
    }

    // ---- era and media ----

    /** The era of this encoder's chassis, which decides the media it writes. */
    public HardwareEra era() {
        return getBlockState().getBlock() instanceof PatternEncoderBlock block ? block.era() : HardwareEra.STANDARD;
    }

    /** Whether an encoder of {@code era} writes media of {@code format}. */
    public static boolean eraAccepts(final HardwareEra era, final MediaFormat format) {
        return switch (era) {
            case VINTAGE -> format == MediaFormat.FLOPPY;
            case LEGACY -> format == MediaFormat.CD;
            default -> format == MediaFormat.DVD || format == MediaFormat.CD || format == MediaFormat.USB;
        };
    }

    /**
     * Bytes the head lays down per tick on {@code format}. The fixed part of a burn is the drive, not the
     * file: a floppy seeks before it writes and a CD spins up, which is why an old drive feels slow even
     * for a tiny file. A USB stick has no head to move and takes the file at once.
     */
    public static int bytesPerTick(final MediaFormat format) {
        return switch (format) {
            case FLOPPY -> 250;
            case CD -> 1024;
            case DVD -> 4096;
            case USB -> Integer.MAX_VALUE;
        };
    }

    /** Ticks {@code format} spends finding its place before the first byte: a seek, a spin-up, a handshake. */
    public static int seekTicks(final MediaFormat format) {
        return switch (format) {
            case FLOPPY -> 20;
            case CD -> 30;
            case DVD -> 15;
            case USB -> 4;
        };
    }

    /** Ticks the read-back after the last byte takes on {@code format}. */
    public static int verifyTicks(final MediaFormat format) {
        return switch (format) {
            case FLOPPY, CD -> 10;
            case DVD -> 5;
            case USB -> 2;
        };
    }

    /** The whole burn of {@code bytes} on {@code format}: seek, write and verify. */
    public static int burnTicks(final MediaFormat format, final int bytes) {
        return seekTicks(format) + writeTicks(format, bytes) + verifyTicks(format);
    }

    private static int writeTicks(final MediaFormat format, final int bytes) {
        return Math.max(1, (int) Math.ceil(bytes / (double) bytesPerTick(format)));
    }

    /** Whether the bay takes {@code stack}: writable media of a format this era's encoder writes. */
    public boolean acceptsMedia(final ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof FormattedMediaItem item
                && item.writable()
                && eraAccepts(era(), item.format());
    }

    public ItemStackHandler media() {
        return media;
    }

    public ItemStack mediaStack() {
        return media.getStackInSlot(0);
    }

    public boolean hasMedia() {
        return !mediaStack().isEmpty();
    }

    /** Puts {@code stack} in the bay if it is empty and the medium is accepted; returns what was not taken. */
    public ItemStack insertMedia(final ItemStack stack) {
        return media.insertItem(0, stack, false);
    }

    /** Takes the medium out, unless a job holds it; empty when nothing came out. */
    public ItemStack ejectMedia() {
        return media.extractItem(0, 1, false);
    }

    // ---- jobs ----

    /**
     * Queues a file for burning. Refused when the queue is full or the name is not one the filesystem can
     * hold; the medium is checked when the job starts, so a job can be queued before the disc goes in.
     */
    public boolean queueBurn(final String fileName, final String content) {
        if (queue.size() >= QUEUE_MAX || fileName == null || fileName.isBlank() || content == null
                || fileName.length() + ".craft".length() > FsPaths.MAX_NAME_LENGTH) {
            return false;
        }
        queue.addLast(new BurnRequest(fileName, content));
        setChanged();
        sync();
        return true;
    }

    public int queued() {
        return queue.size();
    }

    public Phase phase() {
        return phase;
    }

    /** Whether the head is on the medium (seeking, writing or verifying). */
    public boolean busy() {
        return phase == Phase.SEEK || phase == Phase.WRITE || phase == Phase.VERIFY;
    }

    /** Whether the bay refuses to give the medium up. */
    public boolean locked() {
        return busy();
    }

    /** The file being burned (or just burned), without its extension. */
    public String currentFile() {
        return currentFile;
    }

    /** Files burned since the block was placed. */
    public int completed() {
        return completed;
    }

    /** The format in the bay, or null with the bay empty. */
    @Nullable
    private MediaFormat bayFormat() {
        return mediaStack().getItem() instanceof FormattedMediaItem item ? item.format() : null;
    }

    /** The format of the medium in the bay, or null with the bay empty; what the body draws. */
    @Nullable
    public MediaFormat mediaFormat() {
        return bayFormat();
    }

    /** Progress of the current job across seek, write and verify, 0..100. */
    public int progressPercent() {
        if (!busy() && phase != Phase.DONE) {
            return 0;
        }
        if (phase == Phase.DONE) {
            return 100;
        }
        final MediaFormat format = bayFormat();
        final int seek = format == null ? 0 : seekTicks(format);
        final int verify = format == null ? 0 : verifyTicks(format);
        final int total = seek + writeTicks + verify;
        final int elapsed = switch (phase) {
            case SEEK -> phaseTicks;
            case WRITE -> seek + phaseTicks;
            case VERIFY -> seek + writeTicks + phaseTicks;
            default -> 0;
        };
        return total <= 0 ? 0 : Math.max(0, Math.min(100, elapsed * 100 / total));
    }

    /** One line for a display: what the encoder is doing, or why it stopped. */
    public String statusLine() {
        return switch (phase) {
            case IDLE -> hasMedia() ? (queue.isEmpty() ? "Ready" : "Starting...") : "Insert media";
            case SEEK -> "Seeking";
            case WRITE -> "Writing " + currentFile + ".craft";
            case VERIFY -> "Verifying " + currentFile + ".craft";
            case DONE -> "Done: " + currentFile + ".craft";
            case ERROR -> message.isEmpty() ? "Error" : message;
        };
    }

    /** The reason the last job failed, or {@code ""}. */
    public String lastError() {
        return phase == Phase.ERROR ? message : "";
    }

    /** Drops every waiting job and stops the current one; nothing half-written is left on the medium. */
    public void cancelAll() {
        queue.clear();
        if (busy()) {
            enter(Phase.ERROR, HOLD_TICKS);
            message = "Cancelled";
        }
        setChanged();
        sync();
    }

    // ---- ticking ----

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final PatternEncoderBlockEntity be) {
        if (level instanceof ServerLevel serverLevel) {
            be.tick(serverLevel);
        }
    }

    private void tick(final ServerLevel level) {
        tickLink(level);
        tickJob(level);
    }

    private void tickLink(final ServerLevel level) {
        final long self = worldPosition.asLong();
        final PeripheralLinkValidator validator = PeripheralLinks.validator(level);
        if (linkedOwner == null) {
            PeripheralLinks.discoverOwner(level, self)
                    .ifPresent(ownerPos -> validator.tryEstablishLink(ownerPos, self));
        } else {
            final boolean ownerPresent = level.getBlockEntity(BlockPos.of(linkedOwner)) instanceof PeripheralOwner;
            if (!ownerPresent || !validator.isLinkStillValid(linkedOwner, self, PeripheralCableType.COMPUTING)) {
                unlink(level);
            }
        }
    }

    private void tickJob(final ServerLevel level) {
        switch (phase) {
            case IDLE -> {
                if (queue.isEmpty()) {
                    return;
                }
                if (!hasMedia()) {
                    // The job waits for a disc rather than failing: the player queued it on purpose and the
                    // display says what is missing.
                    return;
                }
                final BurnRequest next = queue.peekFirst();
                currentFile = next.fileName();
                final MediaFormat format = ((FormattedMediaItem) mediaStack().getItem()).format();
                writeTicks = writeTicks(format, next.content().getBytes(StandardCharsets.UTF_8).length);
                enter(Phase.SEEK, seekTicks(format));
                sync();
            }
            case SEEK -> {
                if (++phaseTicks >= phaseTotal) {
                    enter(Phase.WRITE, writeTicks);
                }
            }
            case WRITE -> {
                if (++phaseTicks >= phaseTotal) {
                    final BurnRequest job = queue.pollFirst();
                    if (job == null) {
                        enter(Phase.IDLE, 0);
                        return;
                    }
                    final String written = write(level, job);
                    if (written == null) {
                        enter(Phase.ERROR, HOLD_TICKS);
                        message = "Write failed: " + (hasMedia() ? "medium full" : "no medium");
                    } else {
                        currentFile = written;
                        final MediaFormat format = bayFormat();
                        enter(Phase.VERIFY, format == null ? 1 : verifyTicks(format));
                    }
                    sync();
                }
            }
            case VERIFY -> {
                if (++phaseTicks >= phaseTotal) {
                    final String path = currentFile + ".craft";
                    final Optional<String> back = hasMedia() ? DiskFilesystem.read(mediaStack(), path) : Optional.empty();
                    if (back.isPresent()) {
                        completed++;
                        enter(Phase.DONE, HOLD_TICKS);
                    } else {
                        enter(Phase.ERROR, HOLD_TICKS);
                        message = "Verify failed: " + path;
                    }
                    setChanged();
                    sync();
                }
            }
            case DONE, ERROR -> {
                if (++phaseTicks >= phaseTotal) {
                    enter(Phase.IDLE, 0);
                    sync();
                }
            }
        }
    }

    private void enter(final Phase next, final int total) {
        phase = next;
        phaseTicks = 0;
        phaseTotal = total;
        if (next != Phase.ERROR) {
            message = "";
        }
        setChanged();
    }

    /**
     * Puts the job's file on the medium under a free name and returns the base name it got, or null when the
     * medium refused it. A second file with the same name never overwrites the first; it gets a suffix.
     */
    @Nullable
    private String write(final ServerLevel level, final BurnRequest job) {
        final ItemStack mediaStack = mediaStack();
        if (!(mediaStack.getItem() instanceof FormattedMediaItem)) {
            return null;
        }
        final String path = DiskFilesystem.uniquePath(mediaStack, job.fileName(), ".craft", job.content());
        final long freeWeight = mediaFreeWeight(mediaStack);
        final DiskFilesystem.WriteResult result = DiskFilesystem.write(
                mediaStack, path, FileType.CRAFT, job.content(), freeWeight, FilesystemKind.HIERARCHICAL,
                level.getGameTime());
        if (result != DiskFilesystem.WriteResult.OK) {
            return null;
        }
        media.setStackInSlot(0, mediaStack);
        return path.endsWith(".craft") ? path.substring(0, path.length() - ".craft".length()) : path;
    }

    /** What the medium has left for files, in the filesystem's weight units. */
    private static long mediaFreeWeight(final ItemStack mediaStack) {
        if (!(mediaStack.getItem() instanceof FormattedMediaItem item)) {
            return 0L;
        }
        final long capacity = (long) item.format().capacityItems()
                * dev.jsc.jscomputronics.module.computing.storage.StorageKey.MB_EQ_PER_ITEM;
        final long used = DiskFilesystem.filesWeight(mediaStack);
        return Math.max(0L, capacity - used);
    }

    // ---- PeripheralEndpoint ----

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
        sync();
    }

    @Override
    public void onOwnerUnlinked() {
        linkedOwner = null;
        setChanged();
        sync();
    }

    /** The linked computer's position, or null while unlinked. */
    @Nullable
    public BlockPos ownerPos() {
        return linkedOwner == null ? null : BlockPos.of(linkedOwner);
    }

    /** Breaks the link from this side, freeing the computer's endpoint slot. Safe with no link. */
    public void unlink(final ServerLevel level) {
        if (linkedOwner != null && level.getBlockEntity(BlockPos.of(linkedOwner)) instanceof PeripheralOwner owner) {
            owner.onEndpointUnlinked(worldPosition.asLong());
        }
        onOwnerUnlinked();
    }

    public void dropContents(final Level level, final BlockPos pos) {
        final ItemStack disc = media.getStackInSlot(0);
        if (!disc.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, disc);
            media.setStackInSlot(0, ItemStack.EMPTY);
        }
    }

    // ---- persistence and sync ----

    private void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Media")) {
            media.deserializeNBT(registries, tag.getCompound("Media"));
        }
        queue.clear();
        final ListTag jobs = tag.getList("Queue", Tag.TAG_COMPOUND);
        for (int i = 0; i < jobs.size() && queue.size() < QUEUE_MAX; i++) {
            final CompoundTag job = jobs.getCompound(i);
            queue.addLast(new BurnRequest(job.getString("Name"), job.getString("Content")));
        }
        phase = phaseOf(tag.getString("Phase"));
        phaseTicks = tag.getInt("PhaseTicks");
        phaseTotal = tag.getInt("PhaseTotal");
        writeTicks = tag.getInt("WriteTicks");
        currentFile = tag.getString("CurrentFile");
        message = tag.getString("Message");
        completed = tag.getInt("Completed");
        linkedOwner = tag.contains(NBT_LINKED_OWNER) ? tag.getLong(NBT_LINKED_OWNER) : null;
    }

    private static Phase phaseOf(final String name) {
        for (final Phase p : Phase.values()) {
            if (p.name().equals(name)) {
                return p;
            }
        }
        return Phase.IDLE;
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Media", media.serializeNBT(registries));
        final ListTag jobs = new ListTag();
        for (final BurnRequest job : queue) {
            final CompoundTag t = new CompoundTag();
            t.putString("Name", job.fileName());
            t.putString("Content", job.content());
            jobs.add(t);
        }
        tag.put("Queue", jobs);
        tag.putString("Phase", phase.name());
        tag.putInt("PhaseTicks", phaseTicks);
        tag.putInt("PhaseTotal", phaseTotal);
        tag.putInt("WriteTicks", writeTicks);
        tag.putString("CurrentFile", currentFile);
        tag.putString("Message", message);
        tag.putInt("Completed", completed);
        if (linkedOwner != null) {
            tag.putLong(NBT_LINKED_OWNER, linkedOwner);
        }
    }

    /** The waiting jobs' file names, for a display. */
    public List<String> queuedNames() {
        final List<String> names = new ArrayList<>();
        for (final BurnRequest job : queue) {
            names.add(job.fileName());
        }
        return names;
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        // The client draws the bay, the display and the LEDs; the queued contents themselves stay on the server.
        tag.put("Media", media.serializeNBT(registries));
        tag.putString("Phase", phase.name());
        tag.putInt("PhaseTicks", phaseTicks);
        tag.putInt("PhaseTotal", phaseTotal);
        tag.putInt("WriteTicks", writeTicks);
        tag.putString("CurrentFile", currentFile);
        tag.putString("Message", message);
        tag.putInt("Completed", completed);
        tag.putInt("QueueSize", queue.size());
        if (linkedOwner != null) {
            tag.putLong(NBT_LINKED_OWNER, linkedOwner);
        }
        return tag;
    }

    /** Queue length as last synced to a client (the jobs themselves are not sent). */
    private int syncedQueue;

    @Override
    public void handleUpdateTag(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        syncedQueue = tag.getInt("QueueSize");
    }

    /** The queue length a client display shows: the synced count off the server, the real one on it. */
    public int displayQueued() {
        return level != null && level.isClientSide() ? syncedQueue : queue.size();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
