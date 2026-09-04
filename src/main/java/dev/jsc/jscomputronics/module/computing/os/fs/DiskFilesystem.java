/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os.fs;

import dev.jsc.jscomputronics.common.tier.HardwareEra;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.item.DiskItem;
import dev.jsc.jscomputronics.module.computing.os.FilesystemKind;
import dev.jsc.jscomputronics.module.computing.os.media.FormattedMediaItem;
import dev.jsc.jscomputronics.module.computing.storage.DriveVolumes;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Server-side filesystem API that operates on the {@code FILESYSTEM} data component carried by a
 * {@code DiskItem} {@link ItemStack}.
 *
 * <p>All operations are pure: they read from and write to the component on the stack in-place; the
 * caller is responsible for propagating the mutated stack back to the inventory if needed.
 *
 * <p>The {@link #list} method includes both real stored files and the read-only
 * {@link StorageProjection} that mirrors the disk's item/fluid storage as {@code .dat} entries.
 * The {@link #read} and {@link #delete} methods reject {@code .dat} entries — items leave only via
 * the Network Interactor.
 */
public final class DiskFilesystem {

    /**
     * The hardware era a volume was made for, which decides what its files' bytes weigh: a drive's own era,
     * a medium's format era, and the standard era for anything else.
     */
    public static HardwareEra eraOf(final ItemStack volume) {
        if (volume.getItem() instanceof DiskItem disk) {
            return disk.spec().era();
        }
        if (volume.getItem() instanceof FormattedMediaItem medium) {
            return medium.format().era();
        }
        return HardwareEra.STANDARD;
    }

    /** The weight of every file on {@code volume}, in mB-equivalents at the volume's own era. */
    public static long filesWeight(final ItemStack volume) {
        return volume.getOrDefault(ComputingModule.FILESYSTEM.get(), FilesystemContents.EMPTY)
                .usedWeight(eraOf(volume));
    }

    private DiskFilesystem() {
    }

    // -------------------------------------------------------------------------
    // FileEntry
    // -------------------------------------------------------------------------

    /**
     * A single entry returned by {@link #list}: the file path, type, disk-space weight in
     * mB-equivalents, and a flag indicating whether the entry is read-only (e.g. a {@code .dat}
     * projection).
     *
     * @param path     the full path within the volume
     * @param type     the file type
     * @param weight   space consumed on disk in mB-equivalents
     * @param readOnly true if the entry cannot be written or deleted via this API
     * @param modified the world game time (total ticks) the file was last written; {@code 0} means unknown
     */
    public record FileEntry(String path, FileType type, long weight, boolean readOnly, long modified) {

        /** A file entry with an unknown modification time (0), e.g. a virtual {@code .dat} projection. */
        public FileEntry(final String path, final FileType type, final long weight, final boolean readOnly) {
            this(path, type, weight, readOnly, 0L);
        }
    }

    // -------------------------------------------------------------------------
    // WriteResult
    // -------------------------------------------------------------------------

    /**
     * The outcome of a {@link #write} call.
     */
    public enum WriteResult {
        /** The file was written (created or overwritten) successfully. */
        OK,
        /** The path is syntactically invalid for the given {@link FilesystemKind}. */
        INVALID_PATH,
        /** The file's size would exceed the available free-weight budget. */
        DISK_FULL,
        /**
         * The requested {@link FileType} is a virtual projection ({@link FileType#virtualProjection()}
         * returns true) and cannot be written as a real file.
         */
        READ_ONLY
    }

    // -------------------------------------------------------------------------
    // list
    // -------------------------------------------------------------------------

    /**
     * Lists the files visible in the given directory of a disk.
     *
     * <p>The result combines:
     * <ol>
     *   <li>Real files from the {@code FILESYSTEM} component, filtered to {@code dir}:
     *       <ul>
     *         <li>In {@link FilesystemKind#FLAT} mode, {@code dir} is ignored and all files are
     *             listed at the root.</li>
     *         <li>In {@link FilesystemKind#HIERARCHICAL} mode, only files whose parent directory
     *             equals {@code dir} are included.</li>
     *       </ul>
     *   </li>
     *   <li>Read-only {@code .dat} projection of the disk's storage volume (its stored items):
     *       <ul>
     *         <li>In {@link FilesystemKind#FLAT} mode, {@code .dat} entries are placed at the root.</li>
     *         <li>In {@link FilesystemKind#HIERARCHICAL} mode, {@code .dat} entries are placed
     *             under a {@code Storage/} directory; they appear only when {@code dir} equals
     *             {@code "Storage"}.</li>
     *       </ul>
     *   </li>
     * </ol>
     *
     * <p>Returns an empty list if the kind is {@link FilesystemKind#NONE}.
     *
     * @param disk  the disk {@link ItemStack} to read from
     * @param dir   the directory to list; ignored for FLAT; use {@code ""} for the root in HIERARCHICAL
     * @param kind  the filesystem model in use
     * @return an unmodifiable list of visible entries
     */
    public static List<FileEntry> list(final ItemStack disk, final String dir, final FilesystemKind kind) {
        if (kind == FilesystemKind.NONE) {
            return List.of();
        }

        final FilesystemContents fs = disk.getOrDefault(
                ComputingModule.FILESYSTEM.get(), FilesystemContents.EMPTY);
        final HardwareEra era = eraOf(disk);

        final List<FileEntry> result = new ArrayList<>();

        if (kind == FilesystemKind.FLAT) {
            // All real files live at the root; dir is ignored.
            for (final StoredFile file : fs.files().values()) {
                result.add(new FileEntry(file.path(), file.type(), file.weight(era), false, file.modified()));
            }
            // .dat projection also at root for FLAT.
            result.addAll(StorageProjection.project(DriveVolumes.contents(disk)));
        } else {
            // HIERARCHICAL: include real files whose parent dir matches.
            for (final StoredFile file : fs.files().values()) {
                if (dir.equals(FsPaths.parentDir(file.path()))) {
                    result.add(new FileEntry(file.path(), file.type(), file.weight(era), false, file.modified()));
                }
            }
            // .dat projection lives under "Storage/"; include only when dir == "Storage".
            if ("Storage".equals(dir)) {
                result.addAll(StorageProjection.project(DriveVolumes.contents(disk)));
            }
        }

        return List.copyOf(result);
    }

    // -------------------------------------------------------------------------
    // read
    // -------------------------------------------------------------------------

    /**
     * Reads the content of a real user file at {@code path} on the disk.
     *
     * <p>Returns {@link Optional#empty()} if:
     * <ul>
     *   <li>the path does not exist in the {@code FILESYSTEM} component, or</li>
     *   <li>the file's type is a virtual projection (i.e. a {@code .dat} entry — items only
     *       leave storage via the Network Interactor).</li>
     * </ul>
     *
     * @param disk the disk {@link ItemStack} to read from
     * @param path the full path of the file
     * @return the file's content, or empty if not readable
     */
    public static Optional<String> read(final ItemStack disk, final String path) {
        final FilesystemContents fs = disk.getOrDefault(
                ComputingModule.FILESYSTEM.get(), FilesystemContents.EMPTY);
        final StoredFile file = fs.files().get(path);
        if (file == null || file.type().virtualProjection()) {
            return Optional.empty();
        }
        return Optional.of(file.content());
    }

    // -------------------------------------------------------------------------
    // write
    // -------------------------------------------------------------------------

    /**
     * Creates or overwrites a file at {@code path} on the disk.
     *
     * <p>Checks are applied in this order:
     * <ol>
     *   <li>If {@code path} is not valid for {@code kind} ({@link FsPaths#isValidPath}),
     *       returns {@link WriteResult#INVALID_PATH} without mutation.</li>
     *   <li>If {@code type} is a virtual projection ({@link FileType#virtualProjection()}),
     *       returns {@link WriteResult#READ_ONLY} without mutation.</li>
     *   <li>If the file's byte cost ({@link FsPaths#sizeMbEq} of the UTF-8-encoded content)
     *       exceeds {@code freeWeight}, returns {@link WriteResult#DISK_FULL} without
     *       mutation.</li>
     *   <li>Otherwise, updates the {@code FILESYSTEM} component on {@code disk} and returns
     *       {@link WriteResult#OK}.</li>
     * </ol>
     *
     * @param disk       the disk {@link ItemStack} to write to (mutated on success)
     * @param path       the full file path
     * @param type       the file type
     * @param content    the file content (UTF-8 text)
     * @param freeWeight available space in mB-equivalents for this write
     * @param kind       the filesystem model in use
     * @return the result of the write attempt
     */
    public static WriteResult write(final ItemStack disk, final String path, final FileType type,
                                    final String content, final long freeWeight,
                                    final FilesystemKind kind) {
        return write(disk, path, type, content, freeWeight, kind, 0L);
    }

    /**
     * As {@link #write(ItemStack, String, FileType, String, long, FilesystemKind)}, but stamps the file's
     * modification time with {@code now} (the world game time in ticks). The caller supplies the time so this
     * class stays free of Minecraft's world; pass {@code 0} for an unknown time.
     */
    public static WriteResult write(final ItemStack disk, final String path, final FileType type,
                                    final String content, final long freeWeight,
                                    final FilesystemKind kind, final long now) {
        if (!FsPaths.isValidPath(path, kind)) {
            return WriteResult.INVALID_PATH;
        }
        if (type.virtualProjection() || installerLocked(disk)) {
            return WriteResult.READ_ONLY;
        }
        final int byteCount = content.getBytes(StandardCharsets.UTF_8).length;
        final long cost = FsPaths.sizeMbEq(byteCount, eraOf(disk));
        if (cost > freeWeight) {
            return WriteResult.DISK_FULL;
        }
        final FilesystemContents current = disk.getOrDefault(
                ComputingModule.FILESYSTEM.get(), FilesystemContents.EMPTY);
        final FilesystemContents updated = current.with(new StoredFile(path, type, content, now));
        disk.set(ComputingModule.FILESYSTEM.get(), updated);
        return WriteResult.OK;
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    /**
     * Deletes a real file at {@code path} from the disk.
     *
     * <p>Returns {@code false} — without mutation — if the path does not exist as a real stored
     * file or if the path corresponds to a {@code .dat} virtual projection (items cannot be removed
     * this way; use the Network Interactor instead).
     *
     * @param disk the disk {@link ItemStack} to modify
     * @param path the full path of the file to delete
     * @return true if the file was found and removed; false otherwise
     */
    /**
     * Whether {@code volume} is an install medium: a stamp on blank media whose whole listing is a
     * projection. Nothing is ever written to, removed from or moved on one, so a setup disc can neither
     * be damaged nor turned into a place to hide files.
     */
    static boolean installerLocked(final ItemStack volume) {
        if (!(volume.getItem() instanceof dev.jsc.jscomputronics.module.computing.os.media.MediaItem)) {
            return false;
        }
        final dev.jsc.jscomputronics.module.computing.os.media.MediaKind kind =
                dev.jsc.jscomputronics.module.computing.os.media.MediaItem.kind(volume);
        return kind != dev.jsc.jscomputronics.module.computing.os.media.MediaKind.DATA
                && dev.jsc.jscomputronics.module.computing.os.media.MediaItem.payload(volume) != null;
    }

    public static boolean delete(final ItemStack disk, final String path) {
        if (installerLocked(disk)) {
            return false;
        }
        final FilesystemContents fs = disk.getOrDefault(
                ComputingModule.FILESYSTEM.get(), FilesystemContents.EMPTY);
        final StoredFile file = fs.files().get(path);
        // Guard: never delete a .dat entry (virtual projection) or a missing file.
        if (file == null || file.type().virtualProjection()) {
            return false;
        }
        disk.set(ComputingModule.FILESYSTEM.get(), fs.without(path));
        return true;
    }

    // -------------------------------------------------------------------------
    // exists
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if a real stored file exists at {@code path} on the disk.
     *
     * <p>Virtual {@code .dat} entries are not tracked in the {@code FILESYSTEM} component and
     * therefore always return {@code false} here; use {@link #list} to enumerate them.
     *
     * @param disk the disk {@link ItemStack} to query
     * @param path the full path to check
     * @return true if the path maps to a stored file
     */
    public static boolean exists(final ItemStack disk, final String path) {
        final FilesystemContents fs = disk.getOrDefault(
                ComputingModule.FILESYSTEM.get(), FilesystemContents.EMPTY);
        return fs.files().containsKey(path);
    }

    // -------------------------------------------------------------------------
    // mkdir
    // -------------------------------------------------------------------------

    /**
     * Creates an empty directory at {@code path}. Only the {@link FilesystemKind#HIERARCHICAL}
     * model supports real folders; flat and absent filesystems return {@code false}.
     *
     * <p>Returns {@code false} — without mutation — when the path is invalid for {@code kind}, or
     * when it already exists as a stored file or directory. Directories cost no disk weight, so no
     * free-space check is needed.
     *
     * @param disk the disk {@link ItemStack} (mutated on success)
     * @param path the directory path to create
     * @param kind the filesystem model in use
     * @return true if the directory was created
     */
    public static boolean mkdir(final ItemStack disk, final String path, final FilesystemKind kind) {
        if (kind != FilesystemKind.HIERARCHICAL || !FsPaths.isValidPath(path, kind) || installerLocked(disk)) {
            return false;
        }
        final FilesystemContents fs = disk.getOrDefault(
                ComputingModule.FILESYSTEM.get(), FilesystemContents.EMPTY);
        if (fs.files().containsKey(path) || fs.hasDir(path)) {
            return false;
        }
        disk.set(ComputingModule.FILESYSTEM.get(), fs.withDir(path));
        return true;
    }

    // -------------------------------------------------------------------------
    // listDirs
    // -------------------------------------------------------------------------

    /**
     * Lists the immediate subdirectories of {@code dir} on a hierarchical disk.
     *
     * <p>The result is the union of explicit empty directories whose parent is {@code dir} and
     * implicit directories inferred from files nested deeper than {@code dir}. Returns an empty
     * list for {@link FilesystemKind#FLAT} and {@link FilesystemKind#NONE} (no folders).
     *
     * @param disk the disk {@link ItemStack} to read from
     * @param dir  the directory whose children to list ({@code ""} for the root)
     * @param kind the filesystem model in use
     * @return an unmodifiable list of immediate subdirectory paths
     */
    public static List<String> listDirs(final ItemStack disk, final String dir, final FilesystemKind kind) {
        if (kind != FilesystemKind.HIERARCHICAL) {
            return List.of();
        }
        final FilesystemContents fs = disk.getOrDefault(
                ComputingModule.FILESYSTEM.get(), FilesystemContents.EMPTY);
        final Set<String> dirs = new LinkedHashSet<>();
        // Explicit directories whose immediate parent is `dir`.
        for (final String d : fs.directories()) {
            if (dir.equals(FsPaths.parentDir(d))) {
                dirs.add(d);
            }
        }
        // Implicit directories inferred from files nested deeper than `dir`.
        final String prefix = dir.isEmpty() ? "" : dir + "/";
        for (final String filePath : fs.files().keySet()) {
            if (!filePath.startsWith(prefix)) {
                continue;
            }
            final String remainder = filePath.substring(prefix.length());
            final int slash = remainder.indexOf('/');
            if (slash > 0) {
                dirs.add(prefix + remainder.substring(0, slash));
            }
        }
        // Virtual "Storage" directory: the .dat projection of the storage volume lives under "Storage/"
        // (see list()), but those entries come from the volume, not the FILESYSTEM component, so
        // nothing else implies a "Storage" parent here. Surface it at the root when the disk holds
        // stored items, so the Files app drive tree can reach the projected .dat files.
        if (dir.isEmpty() && !DriveVolumes.peek(disk).isEmpty()) {
            dirs.add("Storage");
        }
        return List.copyOf(dirs);
    }

    // -------------------------------------------------------------------------
    // rmdir
    // -------------------------------------------------------------------------

    /**
     * Removes the directory at {@code path} and everything nested under it (subdirectories and
     * files) from a hierarchical disk.
     *
     * <p>Virtual {@code .dat} entries are never stored in the {@code FILESYSTEM} component, so they
     * are unaffected. Returns {@code false} when the kind is not hierarchical or nothing matched.
     *
     * @param disk the disk {@link ItemStack} to modify
     * @param path the directory path to remove recursively
     * @param kind the filesystem model in use
     * @return true if at least one directory or file was removed
     */
    public static boolean rmdir(final ItemStack disk, final String path, final FilesystemKind kind) {
        if (kind != FilesystemKind.HIERARCHICAL) {
            return false;
        }
        final FilesystemContents fs = disk.getOrDefault(
                ComputingModule.FILESYSTEM.get(), FilesystemContents.EMPTY);
        FilesystemContents updated = fs;
        boolean changed = false;
        if (updated.hasDir(path)) {
            updated = updated.withoutDir(path);
            changed = true;
        }
        for (final String d : new ArrayList<>(updated.directories())) {
            if (FsPaths.isUnder(path, d)) {
                updated = updated.withoutDir(d);
                changed = true;
            }
        }
        for (final String f : new ArrayList<>(updated.files().keySet())) {
            if (FsPaths.isUnder(path, f)) {
                updated = updated.without(f);
                changed = true;
            }
        }
        if (changed) {
            disk.set(ComputingModule.FILESYSTEM.get(), updated);
        }
        return changed;
    }

    // -------------------------------------------------------------------------
    // move
    // -------------------------------------------------------------------------

    /**
     * Moves a file or directory {@code src} into directory {@code destDir} on a hierarchical disk,
     * keeping its name. A directory move re-keys every nested file and subdirectory.
     *
     * @param disk    the disk {@link ItemStack} to modify
     * @param src     the file or directory path to move
     * @param destDir the destination directory ({@code ""} for the root)
     * @param kind    the filesystem model in use
     * @return true if the move was performed
     */
    public static boolean move(final ItemStack disk, final String src, final String destDir,
                               final FilesystemKind kind) {
        return !installerLocked(disk) && relocate(disk, src, FsPaths.join(destDir, FsPaths.fileName(src)), kind);
    }

    /**
     * Renames a file or directory {@code src} to {@code dest} on a hierarchical disk. This is a
     * relocation to a new full path, so it also moves the entry when {@code dest} has a different
     * parent. A directory rename re-keys every nested file and subdirectory.
     *
     * @param disk the disk {@link ItemStack} to modify
     * @param src  the current file or directory path
     * @param dest the new full path
     * @param kind the filesystem model in use
     * @return true if the rename was performed
     */
    public static boolean rename(final ItemStack disk, final String src, final String dest,
                                 final FilesystemKind kind) {
        return !installerLocked(disk) && relocate(disk, src, dest, kind);
    }

    /**
     * Re-keys a file or directory from {@code src} to the full path {@code dest}.
     *
     * <p>Returns {@code false} — without mutation — when the kind is not hierarchical, the
     * destination is the source, the destination path is invalid, the source does not exist, the
     * source is a {@code .dat} projection, the destination already holds a file, or the move would
     * place a directory inside its own subtree.
     */
    private static boolean relocate(final ItemStack disk, final String src, final String dest,
                                    final FilesystemKind kind) {
        if (kind != FilesystemKind.HIERARCHICAL) {
            return false;
        }
        final FilesystemContents fs = disk.getOrDefault(
                ComputingModule.FILESYSTEM.get(), FilesystemContents.EMPTY);
        if (dest.equals(src) || !FsPaths.isValidPath(dest, kind)) {
            return false;
        }

        // File: re-key the single stored file.
        final StoredFile file = fs.files().get(src);
        if (file != null) {
            if (file.type().virtualProjection() || fs.files().containsKey(dest)) {
                return false;
            }
            disk.set(ComputingModule.FILESYSTEM.get(),
                    fs.without(src).with(new StoredFile(dest, file.type(), file.content(), file.modified())));
            return true;
        }

        // Directory: src must be an explicit or implicit directory.
        final boolean isDir = fs.hasDir(src)
                || fs.files().keySet().stream().anyMatch(p -> FsPaths.isUnder(src, p));
        if (!isDir) {
            return false;
        }
        // Reject relocating a directory into itself or its own subtree.
        if (src.equals(dest) || FsPaths.isUnder(src, dest)) {
            return false;
        }
        // Reject relocating onto an already-occupied destination: re-keying the files into an existing
        // directory (or over an existing file) would silently overwrite colliding entries and destroy
        // data. Mirrors the collision guard on the file-rename branch above.
        if (fs.hasDir(dest) || fs.files().containsKey(dest)
                || fs.files().keySet().stream().anyMatch(p -> FsPaths.isUnder(dest, p))) {
            return false;
        }
        FilesystemContents updated = fs.withDir(dest);
        for (final String d : new ArrayList<>(fs.directories())) {
            if (d.equals(src) || FsPaths.isUnder(src, d)) {
                updated = updated.withoutDir(d).withDir(dest + d.substring(src.length()));
            }
        }
        for (final String f : new ArrayList<>(fs.files().keySet())) {
            if (FsPaths.isUnder(src, f)) {
                final StoredFile sf = fs.files().get(f);
                updated = updated.without(f)
                        .with(new StoredFile(dest + f.substring(src.length()), sf.type(), sf.content(), sf.modified()));
            }
        }
        disk.set(ComputingModule.FILESYSTEM.get(), updated);
        return true;
    }

    /**
     * Copies a file or a whole directory subtree from {@code src} to {@code dest}, leaving the source in place.
     * {@code freeWeight} is the disk's remaining space; the caller computes it (this class stays disk-spec-free).
     * Returns {@code false} without mutation when the kind is not hierarchical, the destination path is invalid,
     * the source is missing or a read-only projection, the destination is already occupied, the copy would place
     * a directory inside itself, or the copied weight would exceed {@code freeWeight}.
     */
    public static boolean copy(final ItemStack disk, final String src, final String dest,
                               final long freeWeight, final FilesystemKind kind) {
        if (kind != FilesystemKind.HIERARCHICAL || dest.equals(src) || !FsPaths.isValidPath(dest, kind)) {
            return false;
        }
        final FilesystemContents fs = disk.getOrDefault(
                ComputingModule.FILESYSTEM.get(), FilesystemContents.EMPTY);

        // File: duplicate the single stored file at the new path.
        final StoredFile file = fs.files().get(src);
        if (file != null) {
            if (file.type().virtualProjection() || fs.files().containsKey(dest)) {
                return false;
            }
            if (file.weight(eraOf(disk)) > freeWeight) {
                return false;
            }
            disk.set(ComputingModule.FILESYSTEM.get(),
                    fs.with(new StoredFile(dest, file.type(), file.content(), file.modified())));
            return true;
        }

        // Directory: duplicate the whole subtree under a new root.
        final boolean isDir = fs.hasDir(src)
                || fs.files().keySet().stream().anyMatch(p -> FsPaths.isUnder(src, p));
        if (!isDir || FsPaths.isUnder(src, dest)) {
            return false;
        }
        if (fs.hasDir(dest) || fs.files().containsKey(dest)
                || fs.files().keySet().stream().anyMatch(p -> FsPaths.isUnder(dest, p))) {
            return false;
        }
        long added = 0;
        final HardwareEra era = eraOf(disk);
        for (final String f : fs.files().keySet()) {
            if (FsPaths.isUnder(src, f)) {
                added += fs.files().get(f).weight(era);
            }
        }
        if (added > freeWeight) {
            return false;
        }
        FilesystemContents updated = fs.withDir(dest);
        for (final String d : new ArrayList<>(fs.directories())) {
            if (d.equals(src) || FsPaths.isUnder(src, d)) {
                updated = updated.withDir(dest + d.substring(src.length()));
            }
        }
        for (final String f : new ArrayList<>(fs.files().keySet())) {
            if (FsPaths.isUnder(src, f)) {
                final StoredFile sf = fs.files().get(f);
                updated = updated.with(new StoredFile(dest + f.substring(src.length()), sf.type(), sf.content(), sf.modified()));
            }
        }
        disk.set(ComputingModule.FILESYSTEM.get(), updated);
        return true;
    }
}
