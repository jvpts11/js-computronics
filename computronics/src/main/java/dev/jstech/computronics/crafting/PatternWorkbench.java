/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.jstech.computronics.os.fs.CraftFile;
import dev.jstech.computronics.storage.ChemicalBridges;
import dev.jstech.computronics.storage.StorageKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * A computer's recipe workbench: the three drafts the Pattern Studio edits, kept on the machine so they
 * survive the window, the monitor and the session. A bench draft (a ghost 3x3 grid with a live result,
 * per-cell "any" tags, a name and a note), a machine draft (ghost inputs and outputs fed to a machine type,
 * with chances and a timeout) and a pipeline draft (ordered stages built from the other two or opened from
 * files). Everything here is ghost data; no item is ever consumed.
 *
 * <p>The workbench also remembers which file a draft was opened from, so a burn can put it back in place.
 */
public final class PatternWorkbench {

    /** Cells in each of the machine draft's input and output grids. */
    public static final int PROC_GRID = 27;

    /**
     * One machine-draft cell: a kind of data (item, fluid or chemical), the amount per run and whether that
     * amount is an estimate a recipe transfer worked out rather than one the author confirmed.
     */
    public record DataCell(StorageKey key, long amount, boolean estimated) {

        /** What a fluid or chemical cell gets when it is placed from an item that carries it: one bucket. */
        public static final long CONTINUOUS_DEFAULT_AMOUNT = StorageKey.MB_EQ_PER_ITEM;

        public static final Codec<DataCell> CODEC = RecordCodecBuilder.create(i -> i.group(
                StorageKey.CODEC.fieldOf("key").forGetter(DataCell::key),
                Codec.LONG.fieldOf("amount").forGetter(DataCell::amount),
                Codec.BOOL.optionalFieldOf("estimated", false).forGetter(DataCell::estimated)
        ).apply(i, DataCell::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, DataCell> STREAM_CODEC = StreamCodec.composite(
                StorageKey.STREAM_CODEC, DataCell::key,
                ByteBufCodecs.VAR_LONG, DataCell::amount,
                ByteBufCodecs.BOOL, DataCell::estimated,
                DataCell::new);

        public boolean isItem() {
            return key.isItem();
        }

        /** The ghost stack an item cell shows, with the amount as its count; empty for fluids and chemicals. */
        public ItemStack stack() {
            return key.isItem() ? key.stack((int) Math.min(amount, Integer.MAX_VALUE)) : ItemStack.EMPTY;
        }

        /**
         * The cell a carried stack places: a fluid container names its fluid and a chemical-carrying item its
         * chemical (a bucket's worth each), any other stack is an item cell with the stack's count as the amount.
         */
        @Nullable
        public static DataCell fromStack(final ItemStack carried) {
            if (carried.isEmpty()) {
                return null;
            }
            final Optional<StorageKey> fluid = FluidUtil.getFluidContained(carried)
                    .filter(f -> !f.isEmpty()).map(StorageKey::of);
            if (fluid.isPresent()) {
                return new DataCell(fluid.get(), CONTINUOUS_DEFAULT_AMOUNT, false);
            }
            final Optional<StorageKey> chemical = ChemicalBridges.chemicalOf(carried).map(StorageKey::chemical);
            if (chemical.isPresent()) {
                return new DataCell(chemical.get(), CONTINUOUS_DEFAULT_AMOUNT, false);
            }
            return new DataCell(StorageKey.of(carried), carried.getCount(), false);
        }
    }

    /** Which editor a draft belongs to; also the kind of file it opens or writes. */
    public enum Kind {
        BENCH, MACHINE, PIPELINE
    }

    // bench draft
    private final List<ItemStack> grid = new ArrayList<>(CraftingPattern.GRID_SIZE);
    private final List<String> anyTags = new ArrayList<>(CraftingPattern.GRID_SIZE);
    private String benchName = "";
    private String benchNote = "";
    private ItemStack preview = ItemStack.EMPTY;

    // machine draft
    private final DataCell[] procInputs = new DataCell[PROC_GRID];
    private final DataCell[] procOutputs = new DataCell[PROC_GRID];
    private final int[] outputChances = new int[PROC_GRID];
    private String machineType = "";
    private int procTimeout = ProcessingPattern.DEFAULT_TIMEOUT_TICKS;
    private String procName = "";
    private String procNote = "";

    // pipeline draft
    private final List<MultiStagePattern.Stage> stages = new ArrayList<>();
    private String pipelineName = "";
    private String pipelineNote = "";

    // provenance: the file the current draft of each kind was opened from, so a burn writes it back
    private final String[] openedFile = {"", "", ""};
    private final String[] openedSource = {"", "", ""};

    public PatternWorkbench() {
        for (int i = 0; i < CraftingPattern.GRID_SIZE; i++) {
            grid.add(ItemStack.EMPTY);
            anyTags.add("");
        }
        Arrays.fill(outputChances, ProcessingPattern.FULL_CHANCE);
    }

    //  Bench draft

    public List<ItemStack> grid() {
        return List.copyOf(grid);
    }

    public List<String> anyTags() {
        return List.copyOf(anyTags);
    }

    public ItemStack preview() {
        return preview;
    }

    public String benchName() {
        return benchName;
    }

    public String benchNote() {
        return benchNote;
    }

    /** Places a ghost copy of {@code stack} in cell {@code cell} (an empty stack clears it and its tag). */
    public void setGhost(final int cell, final ItemStack stack) {
        if (cell < 0 || cell >= CraftingPattern.GRID_SIZE) {
            return;
        }
        grid.set(cell, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        if (stack.isEmpty()) {
            anyTags.set(cell, "");
        }
    }

    /** Replaces the whole grid (a recipe transfer); tags are cleared, {@code tags} may set them per cell. */
    public void setGrid(final List<ItemStack> cells, @Nullable final List<String> tags) {
        for (int i = 0; i < CraftingPattern.GRID_SIZE; i++) {
            final ItemStack s = i < cells.size() ? cells.get(i) : ItemStack.EMPTY;
            grid.set(i, s.isEmpty() ? ItemStack.EMPTY : s.copyWithCount(1));
            final String tag = tags != null && i < tags.size() && tags.get(i) != null ? tags.get(i) : "";
            anyTags.set(i, s.isEmpty() ? "" : tag);
        }
    }

    /** Makes cell {@code cell} accept any item of {@code tagId} ({@code ""} makes it exact again). */
    public void setAnyTag(final int cell, final String tagId) {
        if (cell < 0 || cell >= CraftingPattern.GRID_SIZE || grid.get(cell).isEmpty()) {
            return;
        }
        anyTags.set(cell, tagId == null ? "" : tagId.trim());
    }

    public void setBenchName(final String name, final String note) {
        benchName = clamp(name, CraftingPattern.MAX_NAME);
        benchNote = clamp(note, CraftingPattern.MAX_NOTE);
    }

    public void clearBench() {
        for (int i = 0; i < CraftingPattern.GRID_SIZE; i++) {
            grid.set(i, ItemStack.EMPTY);
            anyTags.set(i, "");
        }
        benchName = "";
        benchNote = "";
        preview = ItemStack.EMPTY;
        forget(Kind.BENCH);
    }

    /** Recomputes the bench result from the vanilla recipe book; empty when the grid matches no recipe. */
    public void refreshPreview(@Nullable final Level level) {
        if (level == null || level.isClientSide()) {
            return;
        }
        boolean empty = true;
        for (final ItemStack cell : grid) {
            if (!cell.isEmpty()) {
                empty = false;
                break;
            }
        }
        if (empty) {
            preview = ItemStack.EMPTY;
            return;
        }
        final CraftingInput input = CraftingInput.of(3, 3, grid);
        preview = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(holder -> holder.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    /** Whether the bench draft is a recipe the game knows. */
    public boolean benchComplete() {
        return !preview.isEmpty();
    }

    /** The bench draft as a pattern, or null when it is not a recipe. */
    @Nullable
    public CraftingPattern benchPattern() {
        return preview.isEmpty() ? null
                : new CraftingPattern(grid, preview.copy(), anyTags, benchName, benchNote);
    }

    /** Fills the bench editor from {@code pattern}, remembering where it came from. */
    public void loadBench(final CraftingPattern pattern, final String source, final String file) {
        setGrid(pattern.grid(), pattern.anyTags());
        benchName = pattern.name();
        benchNote = pattern.note();
        preview = pattern.result().copy();
        remember(Kind.BENCH, source, file);
    }

    //  Machine draft

    @Nullable
    public DataCell procInput(final int cell) {
        return cell >= 0 && cell < PROC_GRID ? procInputs[cell] : null;
    }

    @Nullable
    public DataCell procOutput(final int cell) {
        return cell >= 0 && cell < PROC_GRID ? procOutputs[cell] : null;
    }

    public int outputChance(final int cell) {
        return cell >= 0 && cell < PROC_GRID ? outputChances[cell] : ProcessingPattern.FULL_CHANCE;
    }

    public String machineType() {
        return machineType;
    }

    public int procTimeout() {
        return procTimeout;
    }

    public String procName() {
        return procName;
    }

    public String procNote() {
        return procNote;
    }

    /** Sets or clears ({@code null}) a machine-draft cell. */
    public void setProcCell(final boolean output, final int cell, @Nullable final DataCell value) {
        if (cell < 0 || cell >= PROC_GRID) {
            return;
        }
        (output ? procOutputs : procInputs)[cell] = value;
        if (output && value == null) {
            outputChances[cell] = ProcessingPattern.FULL_CHANCE;
        }
    }

    /** Sets a cell's amount per run; a confirmed amount is no longer an estimate. Clears the cell at 0. */
    public void setProcAmount(final boolean output, final int cell, final long amount) {
        final DataCell current = output ? procOutput(cell) : procInput(cell);
        if (current == null) {
            return;
        }
        setProcCell(output, cell, amount <= 0 ? null : new DataCell(current.key(), amount, false));
    }

    public void setOutputChance(final int cell, final int percent) {
        if (cell >= 0 && cell < PROC_GRID) {
            outputChances[cell] = Math.max(1, Math.min(ProcessingPattern.FULL_CHANCE, percent));
        }
    }

    public void setMachineType(final String type) {
        machineType = type == null ? "" : type.trim();
    }

    public void setProcTimeout(final int ticks) {
        procTimeout = Math.max(1, ticks);
    }

    public void setProcName(final String name, final String note) {
        procName = clamp(name, CraftingPattern.MAX_NAME);
        procNote = clamp(note, CraftingPattern.MAX_NOTE);
    }

    /**
     * Replaces the whole machine draft with the given cells (a recipe transfer). Chances reset to guaranteed;
     * the machine choice is kept unless {@code machine} names one.
     */
    public void applyProcessingCells(final List<DataCell> inputs, final List<DataCell> outputs,
                                     @Nullable final String machine) {
        for (int i = 0; i < PROC_GRID; i++) {
            procInputs[i] = i < inputs.size() ? inputs.get(i) : null;
            procOutputs[i] = i < outputs.size() ? outputs.get(i) : null;
            outputChances[i] = ProcessingPattern.FULL_CHANCE;
        }
        if (machine != null && !machine.isBlank()) {
            machineType = machine.trim();
        }
    }

    /** {@link #applyProcessingCells} for plain item stacks (each stack's count is its amount). */
    public void applyProcessingRecipe(final List<ItemStack> inputs, final List<ItemStack> outputs,
                                      @Nullable final String machine) {
        final List<DataCell> ins = new ArrayList<>();
        for (final ItemStack in : inputs) {
            final DataCell cell = DataCell.fromStack(in);
            if (cell != null) {
                ins.add(cell);
            }
        }
        final List<DataCell> outs = new ArrayList<>();
        for (final ItemStack out : outputs) {
            final DataCell cell = DataCell.fromStack(out);
            if (cell != null) {
                outs.add(cell);
            }
        }
        applyProcessingCells(ins, outs, machine);
    }

    public void clearMachine() {
        Arrays.fill(procInputs, null);
        Arrays.fill(procOutputs, null);
        Arrays.fill(outputChances, ProcessingPattern.FULL_CHANCE);
        machineType = "";
        procTimeout = ProcessingPattern.DEFAULT_TIMEOUT_TICKS;
        procName = "";
        procNote = "";
        forget(Kind.MACHINE);
    }

    /** The machine draft as a pattern (possibly incomplete; see {@link #machineComplete()}). */
    public ProcessingPattern processingPattern() {
        final List<ProcessingPattern.ProcessingInput> ins = new ArrayList<>();
        final List<ProcessingPattern.ProcessingOutput> outs = new ArrayList<>();
        for (int i = 0; i < PROC_GRID; i++) {
            final DataCell in = procInputs[i];
            if (in != null) {
                ins.add(new ProcessingPattern.ProcessingInput(in.key(), in.amount(), in.estimated()));
            }
            final DataCell out = procOutputs[i];
            if (out != null) {
                outs.add(new ProcessingPattern.ProcessingOutput(out.key(), out.amount(), outputChances[i]));
            }
        }
        return new ProcessingPattern(ins, outs, machineType, procTimeout, procName, procNote);
    }

    /** Whether the machine draft names a machine and has at least one input and one output. */
    public boolean machineComplete() {
        final ProcessingPattern p = processingPattern();
        return !machineType.isBlank() && !p.inputs().isEmpty() && !p.outputs().isEmpty();
    }

    /** Fills the machine editor from {@code pattern}, remembering where it came from. */
    public void loadMachine(final ProcessingPattern pattern, final String source, final String file) {
        Arrays.fill(procInputs, null);
        Arrays.fill(procOutputs, null);
        Arrays.fill(outputChances, ProcessingPattern.FULL_CHANCE);
        int i = 0;
        for (final ProcessingPattern.ProcessingInput in : pattern.inputs()) {
            if (i < PROC_GRID) {
                procInputs[i++] = new DataCell(in.key(), in.amount(), in.estimated());
            }
        }
        i = 0;
        for (final ProcessingPattern.ProcessingOutput out : pattern.outputs()) {
            if (i < PROC_GRID) {
                procOutputs[i] = new DataCell(out.key(), out.amount(), false);
                outputChances[i++] = out.chancePercent();
            }
        }
        machineType = pattern.machineType();
        procTimeout = pattern.timeoutTicks();
        procName = pattern.name();
        procNote = pattern.note();
        remember(Kind.MACHINE, source, file);
    }

    //  Pipeline draft

    public List<MultiStagePattern.Stage> stages() {
        return List.copyOf(stages);
    }

    public String pipelineName() {
        return pipelineName;
    }

    public String pipelineNote() {
        return pipelineNote;
    }

    public void setPipelineName(final String name, final String note) {
        pipelineName = clamp(name, CraftingPattern.MAX_NAME);
        pipelineNote = clamp(note, CraftingPattern.MAX_NOTE);
    }

    /** Appends the bench draft as a stage and clears the bench so the next stage starts fresh. */
    public boolean addBenchStage() {
        final CraftingPattern pattern = benchPattern();
        if (pattern == null) {
            return false;
        }
        stages.add(MultiStagePattern.Stage.bench(pattern));
        for (int i = 0; i < CraftingPattern.GRID_SIZE; i++) {
            grid.set(i, ItemStack.EMPTY);
            anyTags.set(i, "");
        }
        preview = ItemStack.EMPTY;
        return true;
    }

    /** Appends the machine draft as a stage and clears its cells so the next stage starts fresh. */
    public boolean addProcessingStage() {
        if (!machineComplete()) {
            return false;
        }
        stages.add(MultiStagePattern.Stage.proc(processingPattern()));
        Arrays.fill(procInputs, null);
        Arrays.fill(procOutputs, null);
        Arrays.fill(outputChances, ProcessingPattern.FULL_CHANCE);
        return true;
    }

    /** Appends a stage read from a file: a bench or machine pattern; a pipeline cannot nest another. */
    public boolean addStage(final MultiStagePattern.Stage stage) {
        if (stage == null || (stage.bench().isEmpty() && stage.proc().isEmpty())) {
            return false;
        }
        stages.add(stage);
        return true;
    }

    public void removeStage(final int index) {
        if (index >= 0 && index < stages.size()) {
            stages.remove(index);
        }
    }

    public void clearPipeline() {
        stages.clear();
        pipelineName = "";
        pipelineNote = "";
        forget(Kind.PIPELINE);
    }

    public boolean pipelineComplete() {
        return !stages.isEmpty();
    }

    public MultiStagePattern multiStagePattern() {
        return new MultiStagePattern(stages, pipelineName, pipelineNote);
    }

    /** Fills the pipeline editor from {@code pattern}, remembering where it came from. */
    public void loadPipeline(final MultiStagePattern pattern, final String source, final String file) {
        stages.clear();
        stages.addAll(pattern.stages());
        pipelineName = pattern.name();
        pipelineNote = pattern.note();
        remember(Kind.PIPELINE, source, file);
    }

    //  Provenance and files

    /** The file the {@code kind} draft was opened from ({@code ""} when it was started fresh). */
    public String openedFile(final Kind kind) {
        return openedFile[kind.ordinal()];
    }

    /** Where that file lives: a drive key such as {@code media:<pos>}, {@code disk} for the system disk, or {@code ""}. */
    public String openedSource(final Kind kind) {
        return openedSource[kind.ordinal()];
    }

    public void remember(final Kind kind, final String source, final String file) {
        openedSource[kind.ordinal()] = source == null ? "" : source;
        openedFile[kind.ordinal()] = file == null ? "" : file;
    }

    public void forget(final Kind kind) {
        remember(kind, "", "");
    }

    /** The name the {@code kind} draft goes by: its author's name, else its result's or a plain default. */
    public String draftName(final Kind kind) {
        return switch (kind) {
            case BENCH -> benchName.isEmpty()
                    ? (preview.isEmpty() ? "" : preview.getHoverName().getString()) : benchName;
            case MACHINE -> procName.isEmpty()
                    ? (machineComplete() ? processingPattern().displayName() : "") : procName;
            case PIPELINE -> pipelineName.isEmpty()
                    ? (stages.isEmpty() ? "" : multiStagePattern().displayName()) : pipelineName;
        };
    }

    /**
     * The file content the {@code kind} draft burns, or empty when the draft is not complete.
     */
    public Optional<String> serialize(final Kind kind, final HolderLookup.Provider registries) {
        return switch (kind) {
            case BENCH -> {
                final CraftingPattern p = benchPattern();
                yield p == null ? Optional.empty() : CraftFile.serialize(p, registries);
            }
            case MACHINE -> machineComplete()
                    ? CraftFile.serializeProcessing(processingPattern(), registries) : Optional.empty();
            case PIPELINE -> pipelineComplete()
                    ? CraftFile.serializeMultiStage(multiStagePattern(), registries) : Optional.empty();
        };
    }

    //  Persistence

    public void save(final CompoundTag tag, final HolderLookup.Provider registries) {
        final RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        final ListTag cells = new ListTag();
        for (final ItemStack stack : grid) {
            cells.add(stack.isEmpty() ? new CompoundTag() : stack.save(registries, new CompoundTag()));
        }
        tag.put("Grid", cells);
        final ListTag tags = new ListTag();
        for (final String t : anyTags) {
            tags.add(StringTag.valueOf(t));
        }
        tag.put("AnyTags", tags);
        tag.putString("BenchName", benchName);
        tag.putString("BenchNote", benchNote);
        if (!preview.isEmpty()) {
            tag.put("Preview", preview.save(registries, new CompoundTag()));
        }
        tag.put("ProcInputs", saveCells(procInputs, ops));
        tag.put("ProcOutputs", saveCells(procOutputs, ops));
        tag.putIntArray("OutputChances", outputChances.clone());
        tag.putString("MachineType", machineType);
        tag.putInt("ProcTimeout", procTimeout);
        tag.putString("ProcName", procName);
        tag.putString("ProcNote", procNote);
        MultiStagePattern.CODEC.encodeStart(ops, multiStagePattern()).result()
                .ifPresent(stagesTag -> tag.put("Stages", stagesTag));
        for (final Kind kind : Kind.values()) {
            tag.putString("Opened" + kind.name(), openedFile[kind.ordinal()]);
            tag.putString("Source" + kind.name(), openedSource[kind.ordinal()]);
        }
    }

    public void load(final CompoundTag tag, final HolderLookup.Provider registries) {
        final RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        final ListTag cells = tag.getList("Grid", Tag.TAG_COMPOUND);
        final ListTag tags = tag.getList("AnyTags", Tag.TAG_STRING);
        for (int i = 0; i < CraftingPattern.GRID_SIZE; i++) {
            grid.set(i, i < cells.size() && !cells.getCompound(i).isEmpty()
                    ? ItemStack.parseOptional(registries, cells.getCompound(i)) : ItemStack.EMPTY);
            anyTags.set(i, i < tags.size() ? tags.getString(i) : "");
        }
        benchName = tag.getString("BenchName");
        benchNote = tag.getString("BenchNote");
        preview = tag.contains("Preview") ? ItemStack.parseOptional(registries, tag.getCompound("Preview")) : ItemStack.EMPTY;
        loadCells(tag, "ProcInputs", procInputs, ops);
        loadCells(tag, "ProcOutputs", procOutputs, ops);
        final int[] chances = tag.getIntArray("OutputChances");
        for (int i = 0; i < PROC_GRID; i++) {
            outputChances[i] = i < chances.length && chances[i] >= 1 && chances[i] <= ProcessingPattern.FULL_CHANCE
                    ? chances[i] : ProcessingPattern.FULL_CHANCE;
        }
        machineType = tag.getString("MachineType");
        procTimeout = tag.contains("ProcTimeout") ? Math.max(1, tag.getInt("ProcTimeout"))
                : ProcessingPattern.DEFAULT_TIMEOUT_TICKS;
        procName = tag.getString("ProcName");
        procNote = tag.getString("ProcNote");
        stages.clear();
        pipelineName = "";
        pipelineNote = "";
        if (tag.contains("Stages")) {
            MultiStagePattern.CODEC.parse(ops, tag.get("Stages")).result().ifPresent(pattern -> {
                stages.addAll(pattern.stages());
                pipelineName = pattern.name();
                pipelineNote = pattern.note();
            });
        }
        for (final Kind kind : Kind.values()) {
            openedFile[kind.ordinal()] = tag.getString("Opened" + kind.name());
            openedSource[kind.ordinal()] = tag.getString("Source" + kind.name());
        }
    }

    private static ListTag saveCells(final DataCell[] cells, final RegistryOps<Tag> ops) {
        final ListTag list = new ListTag();
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] == null) {
                continue;
            }
            final int index = i;
            DataCell.CODEC.encodeStart(ops, cells[i]).result().ifPresent(cellTag -> {
                final CompoundTag row = new CompoundTag();
                row.put("Cell", cellTag);
                row.putInt("Index", index);
                list.add(row);
            });
        }
        return list;
    }

    private static void loadCells(final CompoundTag tag, final String name, final DataCell[] cells,
                                  final RegistryOps<Tag> ops) {
        Arrays.fill(cells, null);
        if (!(tag.get(name) instanceof ListTag list)) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag row = list.getCompound(i);
            final int index = row.getInt("Index");
            if (index >= 0 && index < cells.length && row.contains("Cell")) {
                cells[index] = DataCell.CODEC.parse(ops, row.get("Cell")).result().orElse(null);
            }
        }
    }

    private static String clamp(final String s, final int max) {
        final String value = s == null ? "" : s.trim();
        return value.length() <= max ? value : value.substring(0, max);
    }
}
