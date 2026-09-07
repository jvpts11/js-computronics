/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.computronics.operation.payload.DiskFilesPayload;

/**
 * Where a file the server sent back belongs, when more than one window could have asked for it.
 *
 * <p>The Editor and the file explorer each kept the one instance of themselves and took whatever
 * arrived, which worked while they were the only two. A code editor asks for the same three things,
 * so an answer meant for it would have landed in the Editor's buffer and quietly replaced whatever
 * the player had open there.
 *
 * <p>An editor says here that it is waiting before it asks. The answer goes to whoever is waiting and
 * the slot is emptied, so the next answer falls through to the explorer and the Editor exactly as it
 * did before. Two windows asking for the same thing at the same moment is possible and the later one
 * wins; the cost is a list one round trip out of date, never the wrong file in the wrong window.
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

        /** What came of the save it asked for. */
        default void onSaved(boolean ok, String message) {
        }
    }

    private static IReader waitingContent;
    private static IReader waitingListing;
    private static IReader waitingSaved;

    private CodeFileReplies() {
    }

    /** Says a window is about to ask for a file's content. */
    public static void expectContent(final IReader reader) {
        waitingContent = reader;
    }

    /** Says a window is about to ask for a folder's listing. */
    public static void expectListing(final IReader reader) {
        waitingListing = reader;
    }

    /** Says a window is about to ask for a file to be saved. */
    public static void expectSaved(final IReader reader) {
        waitingSaved = reader;
    }

    /** Stops a window that is closing from being handed anything it asked for. */
    public static void forget(final IReader reader) {
        if (waitingContent == reader) {
            waitingContent = null;
        }
        if (waitingListing == reader) {
            waitingListing = null;
        }
        if (waitingSaved == reader) {
            waitingSaved = null;
        }
    }

    /** Delivers a file's content, and says whether anyone was waiting for it. */
    public static boolean content(final String path, final String text, final boolean exists) {
        final IReader reader = waitingContent;
        waitingContent = null;
        if (reader == null) {
            return false;
        }
        reader.onContent(path, text, exists);
        return true;
    }

    /** Delivers a folder listing, and says whether anyone was waiting for it. */
    public static boolean listing(final DiskFilesPayload payload) {
        final IReader reader = waitingListing;
        waitingListing = null;
        if (reader == null) {
            return false;
        }
        reader.onListing(payload);
        return true;
    }

    /** Delivers the result of a save, and says whether anyone was waiting for it. */
    public static boolean saved(final boolean ok, final String message) {
        final IReader reader = waitingSaved;
        waitingSaved = null;
        if (reader == null) {
            return false;
        }
        reader.onSaved(ok, message);
        return true;
    }
}
