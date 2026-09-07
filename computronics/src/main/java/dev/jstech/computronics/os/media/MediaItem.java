/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os.media;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.storage.ServerStorageContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A physical medium (floppy, CD, data cartridge, etc.) capable of carrying one of three content
 * kinds defined by {@link MediaKind}:
 *
 * <ul>
 *   <li>{@link MediaKind#OS_INSTALL}, boots an OS installer; the target OS is identified by the
 *       {@link ComputingModule#MEDIA_PAYLOAD} component (a {@link ResourceLocation}).</li>
 *   <li>{@link MediaKind#PROGRAM_INSTALL}, installs an add-on program; same payload component.</li>
 *   <li>{@link MediaKind#DATA}, carries a portable item/fluid snapshot stored in the
 *       {@link ComputingModule#MEDIA_DATA} component.</li>
 * </ul>
 *
 * All state is stored in data components so it survives serialization and network sync without
 * touching raw NBT.
 *
 * <p>The registry id intentionally stays {@code os_install_media} to avoid asset churn; the class
 * rename is an internal refactor only.
 */
public class MediaItem extends Item {

    /** Default capacity in item-equivalents for a DATA medium when no component is set. */
    public static final int DEFAULT_CAPACITY = 64;

    public MediaItem(final Properties properties) {
        super(properties);
    }

    // ─── Kind ────────────────────────────────────────────────────────────────

    /**
     * Returns the {@link MediaKind} stored on this stack. Falls back to {@link MediaKind#OS_INSTALL}
     * when the component is absent so that legacy / blank media continues to behave as installer
     * media.
     */
    public static MediaKind kind(final ItemStack stack) {
        final MediaKind stored = stack.get(ComputingModule.MEDIA_KIND.get());
        return stored != null ? stored : MediaKind.OS_INSTALL;
    }

    /** Stamps the given kind onto a media stack in-place. */
    public static void setKind(final ItemStack stack, final MediaKind kind) {
        stack.set(ComputingModule.MEDIA_KIND.get(), kind);
    }

    // ─── Installer payload (OS_INSTALL / PROGRAM_INSTALL) ────────────────────

    /**
     * Returns the OS/program id encoded in this media stack, or {@code null} when the component is
     * absent (blank medium, or a DATA medium that carries no installer).
     */
    @Nullable
    public static ResourceLocation payload(final ItemStack stack) {
        return stack.get(ComputingModule.MEDIA_PAYLOAD.get());
    }

    /**
     * Stamps the given OS/program id onto a media stack in-place.
     *
     * @param stack   the media stack to stamp
     * @param payload the OS or program id to encode
     */
    public static void setPayload(final ItemStack stack, final ResourceLocation payload) {
        stack.set(ComputingModule.MEDIA_PAYLOAD.get(), payload);
    }

    // ─── Data contents (DATA) ────────────────────────────────────────────────

    /**
     * Returns the storage snapshot carried on a DATA medium, or {@link ServerStorageContents#EMPTY}
     * when the component is absent.
     */
    public static ServerStorageContents data(final ItemStack stack) {
        final ServerStorageContents stored = stack.get(ComputingModule.MEDIA_DATA.get());
        return stored != null ? stored : ServerStorageContents.EMPTY;
    }

    /**
     * Writes a storage snapshot onto a media stack in-place.
     *
     * @param stack    the media stack to stamp
     * @param contents the item/fluid snapshot to encode
     */
    public static void setData(final ItemStack stack, final ServerStorageContents contents) {
        stack.set(ComputingModule.MEDIA_DATA.get(), contents);
    }

    // ─── Capacity ────────────────────────────────────────────────────────────

    /**
     * Returns the capacity (in item-equivalents) for a DATA medium. Falls back to
     * {@link #DEFAULT_CAPACITY} when the component is absent.
     */
    public static int capacity(final ItemStack stack) {
        final Integer stored = stack.get(ComputingModule.MEDIA_CAPACITY.get());
        return stored != null ? stored : DEFAULT_CAPACITY;
    }

    /**
     * Sets the capacity (in item-equivalents) on a media stack in-place.
     *
     * @param stack    the media stack to stamp
     * @param capacity the maximum number of item-equivalent units this medium can store
     */
    public static void setCapacity(final ItemStack stack, final int capacity) {
        stack.set(ComputingModule.MEDIA_CAPACITY.get(), capacity);
    }
}
