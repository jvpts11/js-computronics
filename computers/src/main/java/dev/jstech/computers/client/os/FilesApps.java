/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.os;

import dev.jstech.computers.operation.payload.DiskFilesPayload;
import java.util.ArrayList;
import java.util.List;

/**
 * Every file explorer that is open, and what the disk tells them.
 *
 * <p>A machine has one disk and may have several windows looking at it. The explorer used to keep one
 * instance of itself as the one that gets listings, so with two windows on the same folder a file
 * deleted in one stayed in the other, and a listing asked for by one could land in the other and
 * change the folder it was showing. A listing now goes to every explorer on that folder, and a change
 * to the disk, from wherever it was made, has every explorer ask again.
 */
public final class FilesApps {

    private static final List<FilesApp> OPEN = new ArrayList<>();

    private FilesApps() {
    }

    /** Says an explorer is open and wants what the disk says. */
    static void register(final FilesApp app) {
        if (!OPEN.contains(app)) {
            OPEN.add(app);
        }
    }

    /** Says an explorer is gone. */
    static void forget(final FilesApp app) {
        OPEN.remove(app);
    }

    /** Says every explorer is gone, which is what a desktop closing means for the ones it held. */
    static void forgetAll() {
        OPEN.clear();
    }

    /**
     * Hands a listing to every explorer showing that folder.
     *
     * <p>One on another folder is left alone: it asked about something else and will get its own
     * answer, and taking this one would have moved it somewhere it did not go.
     */
    public static void accept(final DiskFilesPayload payload) {
        for (final FilesApp app : List.copyOf(OPEN)) {
            if (app.currentDir().equals(payload.dir())) {
                app.accept(payload);
            }
        }
    }

    /** Has every open explorer ask the disk again, whatever folder each is on. */
    public static void refreshAll() {
        for (final FilesApp app : List.copyOf(OPEN)) {
            app.refresh();
        }
    }

    /**
     * Says the disk changed, so everything showing it asks again.
     *
     * <p>Called right after the change is sent, not when it is confirmed: the machine handles what a
     * player sends in order, so a listing asked for after a delete is a listing without the file. Every
     * explorer and the desktop ask, whichever window made the change, which is what keeps a file made in
     * an editor showing up in an explorer beside it.
     */
    public static void diskChanged() {
        refreshAll();
        DesktopScreen.refreshActive();
    }
}
