/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os.media;

import java.util.EnumSet;
import java.util.Set;

/**
 * The kinds of media-reader peripheral block. A drive has no tier of its own; its capability is
 * defined by which {@link MediaFormat media formats} it can read. Each drive is a distinct block
 * (its own texture) attached to a computer via the COMPUTING peripheral cable.
 *
 * <p>Pure enum (no Minecraft imports) so the compatibility rules are unit-testable.
 */
public enum MediaDriveType {

    /** Reads 3.5" floppy disks. The earliest drive. */
    FLOPPY_DRIVE(EnumSet.of(MediaFormat.FLOPPY)),

    /** Reads CDs (CD-ROM and CD-RW). */
    CD_DRIVE(EnumSet.of(MediaFormat.CD)),

    /** Reads DVDs and, for backward compatibility, CDs. */
    DVD_DRIVE(EnumSet.of(MediaFormat.DVD, MediaFormat.CD)),

    /** Reads USB flash drives; also used for disk diagnostics and recovery. */
    DOCK_STATION(EnumSet.of(MediaFormat.USB));

    private final Set<MediaFormat> accepted;

    MediaDriveType(final Set<MediaFormat> accepted) {
        this.accepted = accepted;
    }

    /** Returns whether this drive can read media of the given format. */
    public boolean accepts(final MediaFormat format) {
        return accepted.contains(format);
    }

    /** Returns an unmodifiable view of the formats this drive accepts. */
    public Set<MediaFormat> acceptedFormats() {
        return EnumSet.copyOf(accepted);
    }
}
