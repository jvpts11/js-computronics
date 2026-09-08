/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.computronics.operation.payload.DiskFilesPayload;
import dev.jstech.computronics.operation.payload.FolderContentPayload;

/**
 * Where a file the server sent back belongs, when more than one window could have asked for it.
 *
 * <p>The Editor and the file explorer each kept the one instance of themselves and took whatever
 * arrived, which worked while they were the only two. A code editor asks for the same things, so an
 * answer meant for it would have landed in the Editor's buffer and quietly replaced what the player had
 * open there.
 *
 * <p>An editor says here what it is waiting for, by name, before it asks. An answer is handed over only
 * when it is the answer to that question; anything else falls through to the explorer and the Editor
 * exactly as it did before. Waiting on a name rather than on the next reply matters because the two ask
 * for different things at the same time: a listing the explorer wanted must never satisfy an editor
 * that asked about another folder, or the editor takes an answer that was not its and the explorer is
 * left with none.
 */
public final class CodeFileReplies {

    /** A window that asked the server for something on the disk. */
    public interface IReader {

        /** The file it asked to open. */
        default void onContent(String path, String content, boolean exists) {
        }

        /** The folder it asked to list. */
        default void onListing(DiskFilesPayload listing) {
        }

        /** The whole folder it asked to read. */
        default void onFolder(FolderContentPayload folder) {
        }

        /** What came of the save it asked for. */
        default void onSaved(boolean ok, String message) {
        }
    }

    /** Somebody waiting for one particular answer. */
    private record Waiting(IReader reader, String about) {
    }

    private static Waiting content;
    private static Waiting listing;
    private static Waiting folder;
    private static IReader saved;

    private CodeFileReplies() {
    }

    /** Says a window is about to ask for the content of {@code path}. */
    public static void expectContent(final IReader reader, final String path) {
        content = new Waiting(reader, path);
    }

    /** Says a window is about to ask for the listing of {@code dir}. */
    public static void expectListing(final IReader reader, final String dir) {
        listing = new Waiting(reader, dir);
    }

    /** Says a window is about to ask for everything in {@code dir}. */
    public static void expectFolder(final IReader reader, final String dir) {
        folder = new Waiting(reader, dir);
    }

    /**
     * Says a window is about to ask for a file to be saved.
     *
     * <p>The reply says only whether it worked, so there is nothing to match it against; a window that
     * asked most recently is the one that gets told, which is the whole of what a save reply is for.
     */
    public static void expectSaved(final IReader reader) {
        saved = reader;
    }

    /** Stops a window that is closing from being handed anything it asked for. */
    public static void forget(final IReader reader) {
        if (content != null && content.reader() == reader) {
            content = null;
        }
        if (listing != null && listing.reader() == reader) {
            listing = null;
        }
        if (folder != null && folder.reader() == reader) {
            folder = null;
        }
        if (saved == reader) {
            saved = null;
        }
    }

    /** Delivers a file's content, and says whether it was the one somebody was waiting for. */
    public static boolean content(final String path, final String text, final boolean exists) {
        if (content == null || !content.about().equals(path)) {
            return false;
        }
        final IReader reader = content.reader();
        content = null;
        reader.onContent(path, text, exists);
        return true;
    }

    /** Delivers a folder listing, and says whether it was the one somebody was waiting for. */
    public static boolean listing(final DiskFilesPayload payload) {
        if (listing == null || !listing.about().equals(payload.dir())) {
            return false;
        }
        final IReader reader = listing.reader();
        listing = null;
        reader.onListing(payload);
        return true;
    }

    /** Delivers a whole folder, and says whether it was the one somebody was waiting for. */
    public static boolean folder(final FolderContentPayload payload) {
        if (folder == null || !folder.about().equals(payload.dir())) {
            return false;
        }
        final IReader reader = folder.reader();
        folder = null;
        reader.onFolder(payload);
        return true;
    }

    /** Delivers the result of a save, and says whether anyone was waiting for it. */
    public static boolean saved(final boolean ok, final String message) {
        final IReader reader = saved;
        saved = null;
        if (reader == null) {
            return false;
        }
        reader.onSaved(ok, message);
        return true;
    }
}
