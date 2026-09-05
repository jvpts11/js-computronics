/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation.payload;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.computing.blockentity.CraftingComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.CraftingSwitchBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.PatternEncoderBlockEntity;
import dev.jsc.jscomputronics.module.computing.crafting.AnyTagResolver;
import dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern;
import dev.jsc.jscomputronics.module.computing.crafting.MachineCategory;
import dev.jsc.jscomputronics.module.computing.crafting.MultiStagePattern;
import dev.jsc.jscomputronics.module.computing.crafting.NetworkRecipe;
import dev.jsc.jscomputronics.module.computing.crafting.PatternWorkbench;
import dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern;
import dev.jsc.jscomputronics.module.computing.crafting.RecipeBook;
import dev.jsc.jscomputronics.module.computing.crafting.RecipeMachines;
import dev.jsc.jscomputronics.module.computing.os.FilesystemKind;
import dev.jsc.jscomputronics.module.computing.os.OsHost;
import dev.jsc.jscomputronics.module.computing.os.VolumeLabel;
import dev.jsc.jscomputronics.module.computing.os.fs.CraftFile;
import dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem;
import dev.jsc.jscomputronics.module.computing.os.fs.FileType;
import dev.jsc.jscomputronics.module.computing.os.media.FormattedMediaItem;
import dev.jsc.jscomputronics.module.computing.os.media.MediaReaderBlockEntity;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The Pattern Studio's server side: answers a window's request with the workbench state, applies its edits,
 * serves the Recipe Book, and carries a finished draft to where it goes: the linked encoder, the system disk's
 * crafts folder, or this Crafting Computer's Recipe ROM. Every handler resolves the host through the monitor
 * the player is at, so a far-away computer cannot be edited by sending its position.
 */
@EventBusSubscriber(modid = JsComputronics.MODID)
public final class PatternStudioPayloads {

    /** The system disk drive key in the Studio's file list. */
    public static final String DISK_KEY = "disk";
    private static final String CRAFTS_DIR = "crafts";

    private PatternStudioPayloads() {
    }

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(RequestPatternStudioPayload.TYPE, RequestPatternStudioPayload.STREAM_CODEC,
                PatternStudioPayloads::handleRequest);
        registrar.playToServer(PatternStudioEditPayload.TYPE, PatternStudioEditPayload.STREAM_CODEC,
                PatternStudioPayloads::handleEdit);
        registrar.playToClient(PatternStudioStatePayload.TYPE, PatternStudioStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        dev.jsc.jscomputronics.module.computing.client.os.PatternStudioApp.accept(payload)));
    }

    // ---- resolution ----

    /** The host at {@code hostPos} as an OS host with a workbench, if the player is at one of its monitors. */
    @Nullable
    public static OsHost studioHost(final ServerPlayer player, final ServerLevel level, final BlockPos hostPos,
                                    final BlockPos monitorPos) {
        final var terminal = ComputingPayloads.niHost(player, level, hostPos, monitorPos);
        return terminal instanceof OsHost host && host.studio() != null ? host : null;
    }

    private static void handleRequest(final RequestPatternStudioPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            final OsHost host = studioHost(player, level, payload.host(), payload.monitorPos());
            if (host == null) {
                return;
            }
            host.studio().refreshPreview(level);
            PacketDistributor.sendToPlayer(player, buildState(level, host, "", -1));
        });
    }

    // ---- edits ----

    private static void handleEdit(final PatternStudioEditPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            final OsHost host = studioHost(player, level, payload.host(), payload.monitorPos());
            if (host == null) {
                return;
            }
            final PatternWorkbench studio = host.studio();
            String status = "";
            int tab = -1;
            // A ghost cell takes what the player carries when the click came with nothing named: a recipe
            // viewer's drop names the item itself, a click on a cell names the cursor.
            final ItemStack carried = player.containerMenu.getCarried();
            final ItemStack item = !payload.item().isEmpty() ? payload.item() : carried;
            switch (payload.action()) {
                case PatternStudioEditPayload.BENCH_SET_CELL -> {
                    studio.setGhost(payload.index(), item);
                    studio.forget(PatternWorkbench.Kind.BENCH);
                }
                case PatternStudioEditPayload.BENCH_CLEAR_CELL -> studio.setGhost(payload.index(), ItemStack.EMPTY);
                case PatternStudioEditPayload.BENCH_SET_TAG -> studio.setAnyTag(payload.index(), payload.text());
                case PatternStudioEditPayload.BENCH_SET_NAME -> studio.setBenchName(payload.text(), payload.text2());
                case PatternStudioEditPayload.BENCH_CLEAR -> studio.clearBench();
                case PatternStudioEditPayload.PROC_SET_INPUT -> {
                    studio.setProcCell(false, payload.index(), PatternWorkbench.DataCell.fromStack(item));
                    studio.forget(PatternWorkbench.Kind.MACHINE);
                }
                case PatternStudioEditPayload.PROC_SET_OUTPUT -> {
                    studio.setProcCell(true, payload.index(), PatternWorkbench.DataCell.fromStack(item));
                    studio.forget(PatternWorkbench.Kind.MACHINE);
                }
                case PatternStudioEditPayload.PROC_CLEAR_INPUT -> studio.setProcCell(false, payload.index(), null);
                case PatternStudioEditPayload.PROC_CLEAR_OUTPUT -> studio.setProcCell(true, payload.index(), null);
                case PatternStudioEditPayload.PROC_SET_INPUT_AMOUNT ->
                        studio.setProcAmount(false, payload.index(), payload.value());
                case PatternStudioEditPayload.PROC_SET_OUTPUT_AMOUNT ->
                        studio.setProcAmount(true, payload.index(), payload.value());
                case PatternStudioEditPayload.PROC_SET_CHANCE -> studio.setOutputChance(payload.index(), (int) payload.value());
                case PatternStudioEditPayload.PROC_SET_MACHINE -> studio.setMachineType(payload.text());
                case PatternStudioEditPayload.PROC_SET_TIMEOUT -> studio.setProcTimeout((int) payload.value());
                case PatternStudioEditPayload.PROC_SET_NAME -> studio.setProcName(payload.text(), payload.text2());
                case PatternStudioEditPayload.PROC_CLEAR -> studio.clearMachine();
                case PatternStudioEditPayload.PIPE_ADD_BENCH -> {
                    studio.refreshPreview(level);
                    status = studio.addBenchStage() ? "Bench stage added" : "The bench draft is not a recipe";
                    tab = 2;
                }
                case PatternStudioEditPayload.PIPE_ADD_PROC -> {
                    status = studio.addProcessingStage() ? "Machine stage added"
                            : "The machine draft needs a machine, an input and an output";
                    tab = 2;
                }
                case PatternStudioEditPayload.PIPE_ADD_FILE -> {
                    status = addStageFromFile(level, host, payload.text(), payload.text2());
                    tab = 2;
                }
                case PatternStudioEditPayload.PIPE_REMOVE -> studio.removeStage(payload.index());
                case PatternStudioEditPayload.PIPE_SET_NAME -> studio.setPipelineName(payload.text(), payload.text2());
                case PatternStudioEditPayload.PIPE_CLEAR -> studio.clearPipeline();
                case PatternStudioEditPayload.OPEN_FILE -> {
                    final int[] opened = {-1};
                    status = openFile(level, host, payload.text(), payload.text2(), opened);
                    tab = opened[0];
                }
                case PatternStudioEditPayload.SAVE_TO_DISK -> status = saveToDisk(level, host, kindOf(payload.index()));
                case PatternStudioEditPayload.LOAD_INTO_ROM -> status = loadIntoRom(level, host, kindOf(payload.index()));
                case PatternStudioEditPayload.BURN -> status = burn(level, host, kindOf(payload.index()));
                case PatternStudioEditPayload.ENCODER_CANCEL -> {
                    final PatternEncoderBlockEntity enc = encoderOf(level, host);
                    if (enc != null) {
                        enc.cancelAll();
                        status = "Encoder queue cleared";
                    }
                }
                case PatternStudioEditPayload.ENCODER_EJECT -> {
                    final PatternEncoderBlockEntity enc = encoderOf(level, host);
                    if (enc == null) {
                        status = "No encoder linked";
                    } else if (enc.locked()) {
                        status = "The encoder is writing";
                    } else {
                        final ItemStack out = enc.ejectMedia();
                        if (!out.isEmpty() && !player.addItem(out)) {
                            player.drop(out, false);
                        }
                        status = out.isEmpty() ? "The bay is empty" : "Ejected";
                    }
                }
                default -> {
                    return;
                }
            }
            studio.refreshPreview(level);
            host.setChanged();
            PacketDistributor.sendToPlayer(player, buildState(level, host, status, tab));
        });
    }

    private static PatternWorkbench.Kind kindOf(final int index) {
        final PatternWorkbench.Kind[] kinds = PatternWorkbench.Kind.values();
        return index >= 0 && index < kinds.length ? kinds[index] : PatternWorkbench.Kind.BENCH;
    }

    /** Fills the bench from a recipe viewer's transfer: the nine cells, tags cleared. */
    public static void applyBenchGrid(final OsHost host, final ServerLevel level, final List<ItemStack> grid,
                                      final String recipeId) {
        final PatternWorkbench studio = host.studio();
        if (studio == null) {
            return;
        }
        // The viewer sends the cells it showed; the recipe itself says which of them accept a tag.
        List<String> tags = null;
        final ResourceLocation id = recipeId == null || recipeId.isEmpty() ? null : ResourceLocation.tryParse(recipeId);
        if (id != null) {
            final var holder = level.getRecipeManager().byKey(id);
            if (holder.isPresent() && holder.get().value() instanceof net.minecraft.world.item.crafting.CraftingRecipe recipe) {
                tags = RecipeBook.benchTags(recipe);
            }
        }
        studio.setGrid(grid, tags);
        studio.forget(PatternWorkbench.Kind.BENCH);
        studio.refreshPreview(level);
        host.setChanged();
    }

    /**
     * Fills the machine draft from a recipe viewer's transfer, paired with the machine the data maps the
     * recipe type to (and the network declares), when there is one.
     */
    public static void applyProcessingCells(final OsHost host, final ServerLevel level,
                                            final List<PatternWorkbench.DataCell> inputs,
                                            final List<PatternWorkbench.DataCell> outputs, final String recipeType) {
        final PatternWorkbench studio = host.studio();
        if (studio == null) {
            return;
        }
        final String machine = defaultMachine(level, host, recipeType);
        studio.applyProcessingCells(inputs, outputs, machine.isEmpty() ? null : machine);
        studio.forget(PatternWorkbench.Kind.MACHINE);
        host.setChanged();
    }

    // ---- files ----

    /** The medium behind a drive key: a linked reader's disc, or the system disk. Empty when there is none. */
    private static ItemStack volumeFor(final ServerLevel level, final OsHost host, final String key) {
        if (DISK_KEY.equals(key)) {
            return host.systemDisk();
        }
        return key.startsWith("media:") ? ComputingPayloads.mediaStackFor(level, host, key) : ItemStack.EMPTY;
    }

    /** The path a craft file name has on {@code key}: under the crafts folder on a hierarchical system disk. */
    private static String pathOn(final OsHost host, final String key, final String fileName) {
        if (DISK_KEY.equals(key) && ComputingPayloads.filesystemKindOf(host) == FilesystemKind.HIERARCHICAL) {
            return CRAFTS_DIR + "/" + fileName;
        }
        return fileName;
    }

    private static Optional<String> readCraft(final ServerLevel level, final OsHost host, final String key,
                                              final String fileName) {
        final ItemStack volume = volumeFor(level, host, key);
        if (volume.isEmpty() || fileName.isBlank() || fileName.contains("..")) {
            return Optional.empty();
        }
        return DiskFilesystem.read(volume, pathOn(host, key, fileName));
    }

    private static String openFile(final ServerLevel level, final OsHost host, final String key,
                                   final String fileName, final int[] tab) {
        final Optional<String> content = readCraft(level, host, key, fileName);
        if (content.isEmpty()) {
            return "Cannot read " + fileName;
        }
        final PatternWorkbench studio = host.studio();
        final var registries = level.registryAccess();
        switch (CraftFile.typeOf(content.get())) {
            case "proc" -> {
                final Optional<ProcessingPattern> p = CraftFile.parseProcessing(content.get(), registries);
                if (p.isEmpty()) {
                    return "Not a machine recipe: " + fileName;
                }
                studio.loadMachine(p.get(), key, fileName);
                tab[0] = 1;
            }
            case "multi" -> {
                final Optional<MultiStagePattern> p = CraftFile.parseMultiStage(content.get(), registries);
                if (p.isEmpty()) {
                    return "Not a pipeline: " + fileName;
                }
                studio.loadPipeline(p.get(), key, fileName);
                tab[0] = 2;
            }
            default -> {
                final Optional<CraftingPattern> p = CraftFile.parse(content.get(), registries);
                if (p.isEmpty()) {
                    return "Not a recipe file: " + fileName;
                }
                studio.loadBench(p.get(), key, fileName);
                tab[0] = 0;
            }
        }
        return "Opened " + fileName;
    }

    private static String addStageFromFile(final ServerLevel level, final OsHost host, final String key,
                                           final String fileName) {
        final Optional<String> content = readCraft(level, host, key, fileName);
        if (content.isEmpty()) {
            return "Cannot read " + fileName;
        }
        final var registries = level.registryAccess();
        final PatternWorkbench studio = host.studio();
        return switch (CraftFile.typeOf(content.get())) {
            case "proc" -> CraftFile.parseProcessing(content.get(), registries)
                    .map(p -> studio.addStage(MultiStagePattern.Stage.proc(p)) ? "Stage added: " + p.displayName()
                            : "Could not add " + fileName)
                    .orElse("Not a machine recipe: " + fileName);
            case "multi" -> "A pipeline cannot hold another pipeline";
            default -> CraftFile.parse(content.get(), registries)
                    .map(p -> studio.addStage(MultiStagePattern.Stage.bench(p)) ? "Stage added: " + p.displayName()
                            : "Could not add " + fileName)
                    .orElse("Not a recipe file: " + fileName);
        };
    }

    /** The file base name a draft is written under: its name, sanitized, else its result's registry path. */
    private static String fileBaseFor(final PatternWorkbench studio, final PatternWorkbench.Kind kind) {
        final String name = studio.draftName(kind);
        if (!name.isBlank()) {
            return ComputingPayloads.sanitizeFileBase(name);
        }
        return switch (kind) {
            case BENCH -> "pattern";
            case MACHINE -> "machine";
            case PIPELINE -> "pipeline";
        };
    }

    private static String saveToDisk(final ServerLevel level, final OsHost host, final PatternWorkbench.Kind kind) {
        final PatternWorkbench studio = host.studio();
        if (kind == PatternWorkbench.Kind.BENCH) {
            studio.refreshPreview(level);
        }
        final Optional<String> content = studio.serialize(kind, level.registryAccess());
        if (content.isEmpty()) {
            return "The draft is not complete";
        }
        final ItemStack disk = host.systemDisk();
        final FilesystemKind fs = ComputingPayloads.filesystemKindOf(host);
        if (disk.isEmpty() || fs == FilesystemKind.NONE) {
            return "No system disk to save to";
        }
        final String base;
        if (fs == FilesystemKind.HIERARCHICAL) {
            DiskFilesystem.mkdir(disk, CRAFTS_DIR, fs);
            base = CRAFTS_DIR + "/" + fileBaseFor(studio, kind);
        } else {
            base = fileBaseFor(studio, kind);
        }
        final String path = DiskFilesystem.uniquePath(disk, base, ".craft", content.get());
        final DiskFilesystem.WriteResult result = DiskFilesystem.write(disk, path, FileType.CRAFT, content.get(),
                host.systemDiskFreeWeight(), fs, level.getGameTime());
        if (result != DiskFilesystem.WriteResult.OK) {
            return "Save failed: " + result.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }
        host.setChanged();
        final String fileName = path.substring(path.lastIndexOf('/') + 1);
        studio.remember(kind, DISK_KEY, fileName);
        return "Saved " + fileName;
    }

    private static String loadIntoRom(final ServerLevel level, final OsHost host, final PatternWorkbench.Kind kind) {
        if (!(host instanceof CraftingComputerBlockEntity cc)) {
            return "Only a Crafting Computer holds a Recipe ROM";
        }
        if (cc.craftingCardFactor() <= 0.0) {
            return "A Crafting Card is required";
        }
        final PatternWorkbench studio = host.studio();
        final boolean loaded;
        final String name;
        switch (kind) {
            case BENCH -> {
                studio.refreshPreview(level);
                final CraftingPattern p = studio.benchPattern();
                if (p == null) {
                    return "The bench draft is not a recipe";
                }
                loaded = cc.loadPattern(p);
                name = p.displayName();
            }
            case MACHINE -> {
                if (!studio.machineComplete()) {
                    return "The machine draft needs a machine, an input and an output";
                }
                final ProcessingPattern p = studio.processingPattern();
                loaded = cc.loadMachineRecipe(NetworkRecipe.ofProcessing(p));
                name = p.displayName();
            }
            default -> {
                if (!studio.pipelineComplete()) {
                    return "The pipeline has no stages";
                }
                final MultiStagePattern p = studio.multiStagePattern();
                loaded = cc.loadMachineRecipe(NetworkRecipe.ofMultiStage(p));
                name = p.displayName();
            }
        }
        if (!loaded) {
            return "Not loaded: already in the ROM, or the ROM is full";
        }
        ComputingPayloads.reconcileCraftsFolder(cc, level);
        return "Loaded into the ROM: " + name;
    }

    private static String burn(final ServerLevel level, final OsHost host, final PatternWorkbench.Kind kind) {
        final PatternEncoderBlockEntity encoder = encoderOf(level, host);
        if (encoder == null) {
            return "No Pattern Encoder is linked to this computer";
        }
        final PatternWorkbench studio = host.studio();
        if (kind == PatternWorkbench.Kind.BENCH) {
            studio.refreshPreview(level);
        }
        final Optional<String> content = studio.serialize(kind, level.registryAccess());
        if (content.isEmpty()) {
            return "The draft is not complete";
        }
        if (!encoder.hasMedia()) {
            return "The encoder's bay is empty";
        }
        final String base = fileBaseFor(studio, kind);
        if (!encoder.queueBurn(base, content.get())) {
            return "The encoder's queue is full";
        }
        return "Sent to the encoder: " + base + ".craft";
    }

    /**
     * The machine a transferred recipe of type {@code typeId} is paired with: the first mapped machine the
     * network actually declares, else the first mapped machine, else the recipe type's generic category; empty
     * when the type is unknown, leaving the choice to the picker.
     */
    static String defaultMachine(final ServerLevel level, final OsHost host, final String typeId) {
        if (typeId == null || typeId.isEmpty()) {
            return "";
        }
        final List<String> mapped = RecipeMachines.machinesFor(typeId);
        final Map<String, String> declared = declaredMachines(level, host);
        for (final String m : mapped) {
            if (declared.containsKey(m)) {
                return m;
            }
        }
        if (!mapped.isEmpty()) {
            return mapped.get(0);
        }
        return MachineCategory.genericIdOf(typeId);
    }

    // ---- state ----

    @Nullable
    static PatternEncoderBlockEntity encoderOf(final ServerLevel level, final OsHost host) {
        for (final long endpoint : host.linkedEndpoints()) {
            if (level.getBlockEntity(BlockPos.of(endpoint)) instanceof PatternEncoderBlockEntity encoder) {
                return encoder;
            }
        }
        return null;
    }

    /** The machine types the network's switches declare, keyed by type with a readable label. */
    private static Map<String, String> declaredMachines(final ServerLevel level, final OsHost host) {
        final Map<String, String> out = new LinkedHashMap<>();
        final List<CraftingComputerBlockEntity> computers = new ArrayList<>();
        if (host instanceof CraftingComputerBlockEntity cc) {
            computers.add(cc);
        }
        final MainframeBlockEntity mf = host.networkUuid() == null ? null
                : ComputingPayloads.resolveMainframe(level, host.networkUuid());
        if (mf != null) {
            for (final BlockPos pos : mf.craftingComputerPositions()) {
                final BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof CraftingComputerBlockEntity cc && !computers.contains(cc)) {
                    computers.add(cc);
                }
            }
        }
        for (final CraftingComputerBlockEntity cc : computers) {
            for (final CraftingSwitchBlockEntity.DeclaredMachine dm : cc.availableMachines()) {
                final String type = dm.machineType();
                if (type == null || type.isBlank() || out.containsKey(type)) {
                    continue;
                }
                out.put(type, !dm.name().isBlank() ? dm.name() : type);
            }
        }
        return out;
    }

    public static PatternStudioStatePayload buildState(final ServerLevel level, final OsHost host,
                                                       final String status, final int tabHint) {
        final PatternWorkbench studio = host.studio();
        final MainframeBlockEntity mf = host.networkUuid() == null ? null
                : ComputingPayloads.resolveMainframe(level, host.networkUuid());
        final Map<StorageKey, Long> stock = mf == null ? Map.of() : mf.networkIndex().snapshot();

        // Bench: each cell with what an "any" cell would use right now, and how much of it the network holds.
        final List<PatternStudioStatePayload.BenchCell> bench = new ArrayList<>();
        final List<ItemStack> grid = studio.grid();
        final List<String> tags = studio.anyTags();
        for (int i = 0; i < CraftingPattern.GRID_SIZE; i++) {
            final ItemStack cell = grid.get(i);
            final String tag = tags.get(i);
            ItemStack resolved = ItemStack.EMPTY;
            long count = 0L;
            if (!cell.isEmpty()) {
                if (!tag.isEmpty()) {
                    final ItemStack pick = AnyTagResolver.mostStocked(tag, stock);
                    resolved = pick == null || pick.isEmpty() ? ItemStack.EMPTY : pick.copyWithCount(1);
                }
                final ItemStack counted = resolved.isEmpty() ? cell : resolved;
                count = stock.getOrDefault(StorageKey.of(counted), 0L);
            }
            bench.add(new PatternStudioStatePayload.BenchCell(cell, wire(tag, PatternStudioStatePayload.MAX_TAG),
                    resolved, count));
        }

        final List<PatternStudioStatePayload.ProcCell> inputs = new ArrayList<>();
        final List<PatternStudioStatePayload.ProcCell> outputs = new ArrayList<>();
        for (int i = 0; i < PatternWorkbench.PROC_GRID; i++) {
            final PatternWorkbench.DataCell in = studio.procInput(i);
            if (in != null) {
                inputs.add(new PatternStudioStatePayload.ProcCell(i, in, ProcessingPattern.FULL_CHANCE,
                        stock.getOrDefault(in.key(), 0L)));
            }
            final PatternWorkbench.DataCell out = studio.procOutput(i);
            if (out != null) {
                outputs.add(new PatternStudioStatePayload.ProcCell(i, out, studio.outputChance(i),
                        stock.getOrDefault(out.key(), 0L)));
            }
        }

        final List<PatternStudioStatePayload.Stage> stages = new ArrayList<>();
        for (final MultiStagePattern.Stage stage : studio.stages()) {
            if (stages.size() >= PatternStudioStatePayload.MAX_STAGES) {
                break;
            }
            if (stage.bench().isPresent()) {
                final CraftingPattern p = stage.bench().get();
                stages.add(new PatternStudioStatePayload.Stage(wire(p.displayName(), PatternStudioStatePayload.MAX_LABEL),
                        true, p.result()));
            } else if (stage.proc().isPresent()) {
                final ProcessingPattern p = stage.proc().get();
                final ProcessingPattern.ProcessingOutput primary = p.primaryOutput();
                stages.add(new PatternStudioStatePayload.Stage(wire(p.displayName(), PatternStudioStatePayload.MAX_LABEL),
                        false, primary != null && primary.key().isItem() ? primary.key().stack(1) : ItemStack.EMPTY));
            }
        }

        // Drives: every linked reader holding a medium, then the system disk's crafts folder.
        final List<PatternStudioStatePayload.Drive> drives = new ArrayList<>();
        for (final long endpoint : host.linkedEndpoints()) {
            if (drives.size() >= PatternStudioStatePayload.MAX_DRIVES - 1) {
                break;
            }
            if (level.getBlockEntity(BlockPos.of(endpoint)) instanceof MediaReaderBlockEntity reader) {
                final ItemStack m = reader.mediaSlot().getStackInSlot(0);
                if (m.isEmpty() || !(m.getItem() instanceof FormattedMediaItem fmt)) {
                    continue;
                }
                // "CD Drive: Blank CD-RW": the reader, then the medium's own label or name.
                final String drive = level.getBlockState(BlockPos.of(endpoint)).getBlock().getName().getString();
                drives.add(new PatternStudioStatePayload.Drive("media:" + endpoint,
                        wire(drive + ": " + VolumeLabel.of(m, m.getHoverName().getString()),
                                PatternStudioStatePayload.MAX_LABEL),
                        fmt.writable(), ComputingPayloads.craftFileListFromMedia(m)));
            }
        }
        final ItemStack disk = host.systemDisk();
        final FilesystemKind fs = ComputingPayloads.filesystemKindOf(host);
        if (!disk.isEmpty() && fs != FilesystemKind.NONE) {
            final List<String> files = new ArrayList<>();
            final String dir = fs == FilesystemKind.HIERARCHICAL ? CRAFTS_DIR : "";
            for (final DiskFilesystem.FileEntry e : DiskFilesystem.list(disk, dir, fs)) {
                final String name = e.path().substring(e.path().lastIndexOf('/') + 1);
                if (e.type() == FileType.CRAFT && files.size() < PatternStudioStatePayload.MAX_FILES
                        && name.length() <= PatternStudioStatePayload.MAX_NAME) {
                    files.add(name);
                }
            }
            drives.add(new PatternStudioStatePayload.Drive(DISK_KEY,
                    wire(VolumeLabel.of(disk, "System disk") + " (" + CRAFTS_DIR + ")", PatternStudioStatePayload.MAX_LABEL),
                    true, files));
        }

        final PatternEncoderBlockEntity enc = encoderOf(level, host);
        final PatternStudioStatePayload.Encoder encoder = enc == null ? PatternStudioStatePayload.Encoder.none()
                : new PatternStudioStatePayload.Encoder(true,
                        wire(enc.era().name().charAt(0) + enc.era().name().substring(1).toLowerCase(Locale.ROOT),
                                PatternStudioStatePayload.MAX_LABEL),
                        enc.hasMedia() ? wire(VolumeLabel.of(enc.mediaStack(), enc.mediaStack().getHoverName().getString()),
                                PatternStudioStatePayload.MAX_LABEL) : "",
                        wire(enc.statusLine(), PatternStudioStatePayload.MAX_STATUS),
                        enc.progressPercent(), enc.queued(), enc.busy(),
                        enc.phase() == PatternEncoderBlockEntity.Phase.ERROR);

        final List<PatternStudioStatePayload.Machine> machines = new ArrayList<>();
        for (final Map.Entry<String, String> e : declaredMachines(level, host).entrySet()) {
            if (machines.size() >= PatternStudioStatePayload.MAX_MACHINES) {
                break;
            }
            machines.add(new PatternStudioStatePayload.Machine(wire(e.getKey(), PatternStudioStatePayload.MAX_KEY),
                    wire(e.getValue(), PatternStudioStatePayload.MAX_LABEL)));
        }

        boolean romBench = false;
        boolean romProc = false;
        boolean romPipe = false;
        boolean hasCard = false;
        final boolean craftingComputer = host instanceof CraftingComputerBlockEntity;
        if (host instanceof CraftingComputerBlockEntity cc) {
            hasCard = cc.craftingCardFactor() > 0.0;
            final CraftingPattern benchPattern = studio.benchPattern();
            romBench = benchPattern != null && cc.romContains(benchPattern);
            if (studio.machineComplete()) {
                final NetworkRecipe r = NetworkRecipe.ofProcessing(studio.processingPattern());
                for (final NetworkRecipe existing : cc.machineRecipes()) {
                    if (existing.sameRecipe(r)) {
                        romProc = true;
                        break;
                    }
                }
            }
            if (studio.pipelineComplete()) {
                final NetworkRecipe r = NetworkRecipe.ofMultiStage(studio.multiStagePattern());
                for (final NetworkRecipe existing : cc.machineRecipes()) {
                    if (existing.sameRecipe(r)) {
                        romPipe = true;
                        break;
                    }
                }
            }
        }

        return new PatternStudioStatePayload(bench, studio.preview(),
                wire(studio.benchName(), PatternStudioStatePayload.MAX_NAME),
                wire(studio.benchNote(), PatternStudioStatePayload.MAX_NOTE),
                wire(studio.openedFile(PatternWorkbench.Kind.BENCH), PatternStudioStatePayload.MAX_NAME),
                inputs, outputs, wire(studio.machineType(), PatternStudioStatePayload.MAX_KEY), studio.procTimeout(),
                wire(studio.procName(), PatternStudioStatePayload.MAX_NAME),
                wire(studio.procNote(), PatternStudioStatePayload.MAX_NOTE),
                wire(studio.openedFile(PatternWorkbench.Kind.MACHINE), PatternStudioStatePayload.MAX_NAME),
                stages, wire(studio.pipelineName(), PatternStudioStatePayload.MAX_NAME),
                wire(studio.pipelineNote(), PatternStudioStatePayload.MAX_NOTE),
                wire(studio.openedFile(PatternWorkbench.Kind.PIPELINE), PatternStudioStatePayload.MAX_NAME),
                drives, encoder, machines, craftingComputer, hasCard, romBench, romProc, romPipe,
                wire(status, PatternStudioStatePayload.MAX_STATUS), tabHint);
    }

    private static String wire(final String s, final int max) {
        final String v = s == null ? "" : s;
        return v.length() <= max ? v : v.substring(0, max);
    }
}
