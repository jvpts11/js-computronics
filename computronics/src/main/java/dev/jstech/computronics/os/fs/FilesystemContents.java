/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os.fs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.jstech.core.tier.HardwareEra;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The filesystem contents of a disk volume: an immutable, path-keyed map of {@link StoredFile}
 * entries plus a set of explicit, empty-capable directory paths.
 *
 * <p>Files are stored by their full path; directories are normally implicit in those paths, but a
 * hierarchical volume also keeps a separate set of directory paths so an <em>empty</em> folder
 * (one with no files yet) still persists. Directories cost no disk weight; only files consume
 * space. Flat volumes (MC-DOS) never populate the directory set, so they behave exactly as before.
 *
 * <p>Persistence and network sync mirror the {@code ServerStorageContents} pattern: a private
 * {@code Line} record serialises each file, and the directory set serialises as a plain string
 * list under an optional field so older disks (without the field) still load.
 */
public record FilesystemContents(Map<String, StoredFile> files, Set<String> directories) {

    /** An empty filesystem (no files and no directories). */
    public static final FilesystemContents EMPTY = new FilesystemContents(Map.of(), Set.of());

    /** Compact constructor: defensive copy and unmodifiable wrapping of both collections. */
    public FilesystemContents {
        files = Collections.unmodifiableMap(new LinkedHashMap<>(files));
        directories = Collections.unmodifiableSet(new LinkedHashSet<>(directories));
    }

    /**
     * Convenience constructor for a volume with files but no explicit empty directories.
     * Equivalent to {@code new FilesystemContents(files, Set.of())}.
     *
     * @param files the path-keyed file map
     */
    public FilesystemContents(final Map<String, StoredFile> files) {
        this(files, Set.of());
    }

    // Codec / StreamCodec (mirrors ServerStorageContents)

    /**
     * One persisted line: path + extension string + content.
     * The extension is stored as a string so unknown extensions survive round-trips.
     */
    private record Line(String path, String ext, String content, long mod) {
        static final Codec<Line> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                Codec.STRING.fieldOf("path").forGetter(Line::path),
                Codec.STRING.fieldOf("ext").forGetter(Line::ext),
                Codec.STRING.fieldOf("content").forGetter(Line::content),
                Codec.LONG.optionalFieldOf("mod", 0L).forGetter(Line::mod)
        ).apply(builder, Line::new));

        static final StreamCodec<RegistryFriendlyByteBuf, Line> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Line::path,
                ByteBufCodecs.STRING_UTF8, Line::ext,
                ByteBufCodecs.STRING_UTF8, Line::content,
                ByteBufCodecs.VAR_LONG, Line::mod,
                Line::new);
    }

    private static FilesystemContents fromParts(final List<Line> lines, final List<String> dirs) {
        final Map<String, StoredFile> map = new LinkedHashMap<>();
        for (final Line line : lines) {
            // Resolve FileType from the stored extension; fall back to TXT for unknown types.
            final FileType type = FileType.fromExtension(line.ext()).orElse(FileType.TXT);
            map.put(line.path(), new StoredFile(line.path(), type, line.content(), line.mod()));
        }
        return new FilesystemContents(map, new LinkedHashSet<>(dirs));
    }

    private static List<Line> toLines(final FilesystemContents contents) {
        final List<Line> lines = new ArrayList<>(contents.files.size());
        contents.files.forEach((path, file) ->
                lines.add(new Line(path, file.type().extension(), file.content(), file.modified())));
        return lines;
    }

    private static List<String> toDirs(final FilesystemContents contents) {
        return new ArrayList<>(contents.directories);
    }

    /** Persistent codec (NBT / JSON). The {@code dirs} field is optional for back-compatibility. */
    public static final Codec<FilesystemContents> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            Line.CODEC.listOf().optionalFieldOf("files", List.of()).forGetter(FilesystemContents::toLines),
            Codec.STRING.listOf().optionalFieldOf("dirs", List.of()).forGetter(FilesystemContents::toDirs)
    ).apply(builder, FilesystemContents::fromParts));

    /** Network stream codec (packet sync). */
    public static final StreamCodec<RegistryFriendlyByteBuf, FilesystemContents> STREAM_CODEC =
            StreamCodec.composite(
                    Line.STREAM_CODEC.apply(ByteBufCodecs.list(16384)), FilesystemContents::toLines,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(4096)), FilesystemContents::toDirs,
                    FilesystemContents::fromParts);

    // Space accounting

    /**
     * Returns the total disk weight consumed by all stored files, in mB-equivalents on a disk of
     * {@code era}. Directories are free, so they do not contribute.
     */
    public long usedWeight(final HardwareEra era) {
        long sum = 0L;
        for (final StoredFile file : files.values()) {
            sum += file.weight(era);
        }
        return sum;
    }

    // Immutable mutations

    /**
     * Returns a new {@link FilesystemContents} with {@code file} added (or replacing any
     * existing entry at the same path). The directory set is preserved.
     *
     * @param file the file to add or replace
     * @return a new instance containing the updated entry
     */
    public FilesystemContents with(final StoredFile file) {
        final Map<String, StoredFile> copy = new LinkedHashMap<>(files);
        copy.put(file.path(), file);
        return new FilesystemContents(copy, directories);
    }

    /**
     * Returns a new {@link FilesystemContents} with the entry at {@code path} removed.
     * If no such entry exists the returned instance is equal to {@code this}.
     *
     * @param path the path of the file to remove
     * @return a new instance without that entry
     */
    public FilesystemContents without(final String path) {
        if (!files.containsKey(path)) {
            return this;
        }
        final Map<String, StoredFile> copy = new LinkedHashMap<>(files);
        copy.remove(path);
        return new FilesystemContents(copy, directories);
    }

    /**
     * Returns a new {@link FilesystemContents} with the directory {@code path} present. If it is
     * already present the returned instance is equal to {@code this}.
     *
     * @param path the directory path to add
     * @return a new instance that includes the directory
     */
    public FilesystemContents withDir(final String path) {
        if (directories.contains(path)) {
            return this;
        }
        final Set<String> copy = new LinkedHashSet<>(directories);
        copy.add(path);
        return new FilesystemContents(files, copy);
    }

    /**
     * Returns a new {@link FilesystemContents} with the directory {@code path} removed from the
     * explicit directory set. Files are untouched (callers that delete a folder remove its files
     * separately). If the directory is absent the returned instance is equal to {@code this}.
     *
     * @param path the directory path to remove
     * @return a new instance without that directory entry
     */
    public FilesystemContents withoutDir(final String path) {
        if (!directories.contains(path)) {
            return this;
        }
        final Set<String> copy = new LinkedHashSet<>(directories);
        copy.remove(path);
        return new FilesystemContents(files, copy);
    }

    /**
     * Returns {@code true} if {@code path} is present in the explicit directory set.
     *
     * @param path the directory path to test
     * @return true if the directory exists explicitly
     */
    public boolean hasDir(final String path) {
        return directories.contains(path);
    }
}
