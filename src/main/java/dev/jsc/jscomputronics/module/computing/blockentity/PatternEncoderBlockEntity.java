/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.blockentity;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern;
import dev.jsc.jscomputronics.module.computing.crafting.MultiStagePattern;
import dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern;
import dev.jsc.jscomputronics.module.computing.os.FilesystemKind;
import dev.jsc.jscomputronics.module.computing.os.fs.CraftFile;
import dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem;
import dev.jsc.jscomputronics.module.computing.os.fs.FileType;
import dev.jsc.jscomputronics.module.computing.os.fs.FilesystemContents;
import dev.jsc.jscomputronics.module.computing.os.media.FormattedMediaItem;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Pattern Encoder: a workstation that authors crafting recipes onto pattern media. It hosts three authoring
 * modes that all write the same {@link FileType#CRAFT} file format the Crafting Manager loads into a machine ROM:
 * a bench recipe laid out on a ghost 3x3 grid, a machine PROCESSING recipe (inputs into a named machine TYPE,
 * yielding declared outputs with per-output chances and a timeout), and a MULTI-STAGE pipeline assembled from
 * ordered bench/processing stages. The processing input/output grids and the stage list are ghost data only — the
 * player's items are never consumed; a click records a copy.
 */
public class PatternEncoderBlockEntity extends BlockEntity {

    /** Number of cells in each of the processing input and output ghost grids (3 columns, scrollable). */
    public static final int PROC_GRID = 27;

    private final ItemStackHandler ghostGrid = new ItemStackHandler(CraftingPattern.GRID_SIZE) {
        @Override
        protected void onContentsChanged(final int slot) {
            setChanged();
            refreshPreview();
        }
    };

    private final ItemStackHandler media = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(final int slot, final ItemStack stack) {
            return stack.getItem() instanceof FormattedMediaItem item && item.writable();
        }

        @Override
        public int getSlotLimit(final int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(final int slot) {
            setChanged();
        }
    };

    // PROCESSING authoring state. Inputs/outputs are ghost stacks whose count is the per-run amount; each output
    // carries a chance (100 = guaranteed). The machine TYPE is a block registry-id string (or a Crafting Switch
    // face name) the engine matches against a declared machine. All of this is synced to the client by the update
    // tag so the screen can read it back without a dedicated payload.
    private final ItemStackHandler procInputs = new ItemStackHandler(PROC_GRID) {
        @Override
        protected void onContentsChanged(final int slot) {
            setChanged();
        }
    };

    private final ItemStackHandler procOutputs = new ItemStackHandler(PROC_GRID) {
        @Override
        protected void onContentsChanged(final int slot) {
            setChanged();
        }
    };

    private final int[] outputChances = newFullChances();
    private String machineType = "";
    private int procTimeout = ProcessingPattern.DEFAULT_TIMEOUT_TICKS;

    // MULTI-STAGE authoring state: an ordered list of stages assembled from the bench grid or the processing tab.
    private final List<MultiStagePattern.Stage> stages = new ArrayList<>();

    private ItemStack preview = ItemStack.EMPTY;

    public PatternEncoderBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.PATTERN_ENCODER_BE.get(), pos, state);
    }

    private static int[] newFullChances() {
        final int[] c = new int[PROC_GRID];
        for (int i = 0; i < PROC_GRID; i++) {
            c[i] = ProcessingPattern.FULL_CHANCE;
        }
        return c;
    }

    public ItemStackHandler ghostGrid() {
        return ghostGrid;
    }

    public ItemStackHandler media() {
        return media;
    }

    public ItemStackHandler procInputs() {
        return procInputs;
    }

    public ItemStackHandler procOutputs() {
        return procOutputs;
    }

    public ItemStack preview() {
        return preview;
    }

    public void setGhost(final int cell, final ItemStack stack) {
        if (cell < 0 || cell >= CraftingPattern.GRID_SIZE) {
            return;
        }
        ghostGrid.setStackInSlot(cell, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    }

    public void refreshPreview() {
        final Level level = getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        final List<ItemStack> cells = gridCells();
        boolean empty = true;
        for (final ItemStack cell : cells) {
            if (!cell.isEmpty()) {
                empty = false;
                break;
            }
        }
        if (empty) {
            preview = ItemStack.EMPTY;
            return;
        }
        final CraftingInput input = CraftingInput.of(3, 3, cells);
        preview = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(holder -> holder.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    public boolean canWrite() {
        if (preview.isEmpty()) {
            return false;
        }
        return mediaWritable();
    }

    public boolean writePattern() {
        refreshPreview();
        if (!canWrite()) {
            return false;
        }
        final Level level = getLevel();
        if (level == null || level.isClientSide()) {
            return false;
        }
        final Optional<String> serialized = CraftFile.serialize(
                new CraftingPattern(gridCells(), preview.copy()), level.registryAccess());
        if (serialized.isEmpty()) {
            return false;
        }
        return writeToMedia(craftFileName(itemBaseName(preview)), serialized.get());
    }

    // --- PROCESSING authoring ---

    public void setProcInput(final int cell, final ItemStack carried) {
        if (cell < 0 || cell >= PROC_GRID) {
            return;
        }
        // The ghost keeps the carried count as the per-run amount; the player's items are never consumed.
        procInputs.setStackInSlot(cell, carried.isEmpty() ? ItemStack.EMPTY : carried.copy());
        sync();
    }

    public void setProcOutput(final int cell, final ItemStack carried) {
        if (cell < 0 || cell >= PROC_GRID) {
            return;
        }
        procOutputs.setStackInSlot(cell, carried.isEmpty() ? ItemStack.EMPTY : carried.copy());
        sync();
    }

    /**
     * Replaces the whole processing draft with the given recipe — used by the JEI "+" transfer, which hands us a
     * recipe's inputs and outputs at once. All output chances reset to guaranteed; the machine choice is kept
     * (the player pairs the recipe with its machine explicitly).
     */
    public void applyProcessingRecipe(final java.util.List<ItemStack> inputs,
                                      final java.util.List<ItemStack> outputs) {
        for (int i = 0; i < PROC_GRID; i++) {
            final ItemStack in = i < inputs.size() ? inputs.get(i) : ItemStack.EMPTY;
            final ItemStack out = i < outputs.size() ? outputs.get(i) : ItemStack.EMPTY;
            procInputs.setStackInSlot(i, in.isEmpty() ? ItemStack.EMPTY : in.copy());
            procOutputs.setStackInSlot(i, out.isEmpty() ? ItemStack.EMPTY : out.copy());
            outputChances[i] = ProcessingPattern.FULL_CHANCE;
        }
        sync();
    }

    public int outputChance(final int cell) {
        return cell >= 0 && cell < PROC_GRID ? outputChances[cell] : ProcessingPattern.FULL_CHANCE;
    }

    public void setOutputChance(final int cell, final int percent) {
        if (cell < 0 || cell >= PROC_GRID) {
            return;
        }
        outputChances[cell] = Math.max(1, Math.min(ProcessingPattern.FULL_CHANCE, percent));
        sync();
    }

    public String machineType() {
        return machineType;
    }

    public void setMachineType(final String type) {
        machineType = type == null ? "" : type;
        sync();
    }

    public int procTimeout() {
        return procTimeout;
    }

    public void setProcTimeout(final int ticks) {
        procTimeout = Math.max(1, ticks);
        sync();
    }

    public void clearProcessing() {
        for (int i = 0; i < PROC_GRID; i++) {
            procInputs.setStackInSlot(i, ItemStack.EMPTY);
            procOutputs.setStackInSlot(i, ItemStack.EMPTY);
            outputChances[i] = ProcessingPattern.FULL_CHANCE;
        }
        sync();
    }

    /** Builds the processing pattern currently authored (may be incomplete; validate with {@link #canWriteProcessing}). */
    public ProcessingPattern buildProcessingPattern() {
        final List<ProcessingPattern.ProcessingInput> ins = new ArrayList<>();
        final List<ProcessingPattern.ProcessingOutput> outs = new ArrayList<>();
        for (int i = 0; i < PROC_GRID; i++) {
            final ItemStack in = procInputs.getStackInSlot(i);
            if (!in.isEmpty()) {
                ins.add(new ProcessingPattern.ProcessingInput(StorageKey.of(in), in.getCount()));
            }
            final ItemStack out = procOutputs.getStackInSlot(i);
            if (!out.isEmpty()) {
                outs.add(new ProcessingPattern.ProcessingOutput(
                        StorageKey.of(out), out.getCount(), outputChances[i]));
            }
        }
        return new ProcessingPattern(ins, outs, machineType, procTimeout);
    }

    public boolean canWriteProcessing() {
        if (!mediaWritable() || machineType.isBlank()) {
            return false;
        }
        final ProcessingPattern pattern = buildProcessingPattern();
        return !pattern.inputs().isEmpty() && !pattern.outputs().isEmpty();
    }

    public boolean writeProcessingPattern() {
        final Level level = getLevel();
        if (level == null || level.isClientSide() || !canWriteProcessing()) {
            return false;
        }
        final ProcessingPattern pattern = buildProcessingPattern();
        final Optional<String> serialized = CraftFile.serializeProcessing(pattern, level.registryAccess());
        if (serialized.isEmpty()) {
            return false;
        }
        final ProcessingPattern.ProcessingOutput primary = pattern.primaryOutput();
        final String base = primary != null && !primary.key().isFluid()
                ? itemBaseName(primary.key().stack(1))
                : machineBaseName(machineType);
        return writeToMedia(craftFileName(base), serialized.get());
    }

    // --- MULTI-STAGE authoring ---

    public List<MultiStagePattern.Stage> stages() {
        return List.copyOf(stages);
    }

    public boolean addBenchStage() {
        refreshPreview();
        if (preview.isEmpty()) {
            return false;
        }
        stages.add(MultiStagePattern.Stage.bench(new CraftingPattern(gridCells(), preview.copy())));
        // Clear the recipe grid so the NEXT stage is built fresh, not the same one re-added every click.
        for (int i = 0; i < CraftingPattern.GRID_SIZE; i++) {
            ghostGrid.setStackInSlot(i, ItemStack.EMPTY);
        }
        preview = ItemStack.EMPTY;
        sync();
        return true;
    }

    public boolean addProcessingStage() {
        if (machineType.isBlank()) {
            return false;
        }
        final ProcessingPattern pattern = buildProcessingPattern();
        if (pattern.inputs().isEmpty() || pattern.outputs().isEmpty()) {
            return false;
        }
        stages.add(MultiStagePattern.Stage.proc(pattern));
        clearProcessing(); // clear inputs/outputs so the NEXT stage is built fresh (also syncs)
        return true;
    }

    /**
     * Appends a stage by loading a {@code .craft} file from the inserted medium and routing on its kind: a bench
     * pattern becomes a bench stage, a machine pattern a processing stage. A multi-stage file is rejected (a
     * multi-stage pattern cannot be nested inside another). This is how the multi-stage picker adds stages.
     */
    public boolean addStageFromMedia(final String fileName) {
        final Level level = getLevel();
        if (level == null || fileName == null || fileName.isBlank()) {
            return false;
        }
        final Optional<String> content = DiskFilesystem.read(media.getStackInSlot(0), fileName);
        if (content.isEmpty()) {
            return false;
        }
        final var registries = level.registryAccess();
        final String type = CraftFile.typeOf(content.get());
        final MultiStagePattern.Stage stage;
        if ("proc".equals(type)) {
            final Optional<ProcessingPattern> p = CraftFile.parseProcessing(content.get(), registries);
            if (p.isEmpty()) {
                return false;
            }
            stage = MultiStagePattern.Stage.proc(p.get());
        } else if ("multi".equals(type)) {
            return false; // a multi-stage pattern can't be a stage of another multi-stage
        } else {
            final Optional<CraftingPattern> p = CraftFile.parse(content.get(), registries);
            if (p.isEmpty()) {
                return false;
            }
            stage = MultiStagePattern.Stage.bench(p.get());
        }
        stages.add(stage);
        sync();
        return true;
    }

    public void removeStage(final int index) {
        if (index >= 0 && index < stages.size()) {
            stages.remove(index);
            sync();
        }
    }

    public void clearStages() {
        if (!stages.isEmpty()) {
            stages.clear();
            sync();
        }
    }

    public boolean canWriteMultiStage() {
        return mediaWritable() && !stages.isEmpty();
    }

    public boolean writeMultiStagePattern() {
        final Level level = getLevel();
        if (level == null || level.isClientSide() || !canWriteMultiStage()) {
            return false;
        }
        final MultiStagePattern pattern = new MultiStagePattern(stages);
        final Optional<String> serialized = CraftFile.serializeMultiStage(pattern, level.registryAccess());
        if (serialized.isEmpty()) {
            return false;
        }
        return writeToMedia(craftFileName("multistage"), serialized.get());
    }

    // --- shared write/media helpers ---

    private boolean mediaWritable() {
        final ItemStack stack = media.getStackInSlot(0);
        return !stack.isEmpty()
                && stack.getItem() instanceof FormattedMediaItem item
                && item.writable();
    }

    private boolean writeToMedia(final String fileName, final String content) {
        final ItemStack mediaStack = media.getStackInSlot(0);
        if (!(mediaStack.getItem() instanceof FormattedMediaItem)) {
            return false;
        }
        // Never overwrite an existing craft silently: the filesystem replaces same-path files, and a second
        // pattern with the same result name (or another multi-stage) would erase the first. Suffix instead.
        String unique = fileName;
        int n = 2;
        while (DiskFilesystem.exists(mediaStack, unique + ".craft")
                && !DiskFilesystem.read(mediaStack, unique + ".craft").map(content::equals).orElse(false)) {
            unique = fileName + "_" + n++;
        }
        final long freeWeight = mediaFreeWeight(mediaStack);
        final DiskFilesystem.WriteResult result = DiskFilesystem.write(
                mediaStack, unique + ".craft", FileType.CRAFT, content,
                freeWeight, FilesystemKind.HIERARCHICAL);
        if (result == DiskFilesystem.WriteResult.OK) {
            media.setStackInSlot(0, mediaStack);
            setChanged();
            return true;
        }
        return false;
    }

    public boolean eraseMedia() {
        // Physical media is ejected by hand; no erase-in-place for removable media.
        return false;
    }

    public void dropContents(final Level level, final BlockPos pos) {
        final ItemStack disc = media.getStackInSlot(0);
        if (!disc.isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(
                    level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, disc);
            media.setStackInSlot(0, ItemStack.EMPTY);
        }
    }

    private static String itemBaseName(final ItemStack resultItem) {
        if (resultItem == null || resultItem.isEmpty()) {
            return "pattern";
        }
        final ResourceLocation key = BuiltInRegistries.ITEM.getKey(resultItem.getItem());
        return key == null ? "pattern" : key.getPath();
    }

    private static String machineBaseName(final String machine) {
        final int colon = machine.indexOf(':');
        final String path = colon >= 0 ? machine.substring(colon + 1) : machine;
        return path.isBlank() ? "processing" : path;
    }

    private static String craftFileName(final String base) {
        final StringBuilder sb = new StringBuilder();
        for (final char c : base.toCharArray()) {
            sb.append(Character.isLetterOrDigit(c) || c == '_' ? c : '_');
            if (sb.length() >= 32) {
                break;
            }
        }
        return sb.isEmpty() ? "pattern" : sb.toString();
    }

    private static long mediaFreeWeight(final ItemStack mediaStack) {
        if (!(mediaStack.getItem() instanceof FormattedMediaItem fmt)) {
            return 0L;
        }
        final long capWeight = (long) fmt.format().capacityItems() * StorageKey.MB_EQ_PER_ITEM;
        final FilesystemContents fs = mediaStack.getOrDefault(
                ComputingModule.FILESYSTEM.get(), FilesystemContents.EMPTY);
        return Math.max(0L, capWeight - fs.usedWeight());
    }

    private List<ItemStack> gridCells() {
        final List<ItemStack> cells = new ArrayList<>(CraftingPattern.GRID_SIZE);
        for (int i = 0; i < CraftingPattern.GRID_SIZE; i++) {
            cells.add(ghostGrid.getStackInSlot(i));
        }
        return cells;
    }

    /** Marks the block entity dirty and pushes a fresh update tag so the open screen sees the new authoring state. */
    private void sync() {
        setChanged();
        final Level level = getLevel();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("GhostGrid")) {
            ghostGrid.deserializeNBT(registries, tag.getCompound("GhostGrid"));
        }
        if (tag.contains("Media")) {
            media.deserializeNBT(registries, tag.getCompound("Media"));
        }
        if (tag.contains("ProcInputs")) {
            procInputs.deserializeNBT(registries, tag.getCompound("ProcInputs"));
        }
        if (tag.contains("ProcOutputs")) {
            procOutputs.deserializeNBT(registries, tag.getCompound("ProcOutputs"));
        }
        final int[] chances = tag.getIntArray("OutputChances");
        for (int i = 0; i < PROC_GRID; i++) {
            outputChances[i] = i < chances.length && chances[i] >= 1 && chances[i] <= ProcessingPattern.FULL_CHANCE
                    ? chances[i] : ProcessingPattern.FULL_CHANCE;
        }
        machineType = tag.getString("MachineType");
        procTimeout = tag.contains("ProcTimeout")
                ? Math.max(1, tag.getInt("ProcTimeout")) : ProcessingPattern.DEFAULT_TIMEOUT_TICKS;
        stages.clear();
        if (tag.contains("Stages")) {
            final RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
            MultiStagePattern.CODEC.parse(ops, tag.get("Stages")).result()
                    .ifPresent(pattern -> stages.addAll(pattern.stages()));
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("GhostGrid", ghostGrid.serializeNBT(registries));
        tag.put("Media", media.serializeNBT(registries));
        tag.put("ProcInputs", procInputs.serializeNBT(registries));
        tag.put("ProcOutputs", procOutputs.serializeNBT(registries));
        tag.putIntArray("OutputChances", outputChances.clone());
        tag.putString("MachineType", machineType);
        tag.putInt("ProcTimeout", procTimeout);
        final RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        MultiStagePattern.CODEC.encodeStart(ops, new MultiStagePattern(stages)).result()
                .ifPresent(stagesTag -> tag.put("Stages", stagesTag));
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
