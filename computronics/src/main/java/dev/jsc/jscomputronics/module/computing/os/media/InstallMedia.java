/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os.media;

import dev.jstech.core.tier.HardwareEra;
import dev.jsc.jscomputronics.module.computing.os.ProgramKind;

/**
 * Which physical medium a piece of software ships on. One axis, the generation the software belongs
 * to: Vintage on a floppy, Legacy on a CD, and the Standard era split the way that decade did it, a
 * bootable flash drive for the system and the daemons that run it, a DVD for the applications a player
 * opens. Size never decides: a small server daemon is not a floppy program because it is small.
 *
 * <p>Pure, so the rule is unit-tested and the creative tab, the tooltip and the installer projection
 * all read the same answer.
 */
public final class InstallMedia {

    private InstallMedia() {
    }

    /** The medium an operating system of {@code era} ships on. */
    public static MediaFormat forSystem(final HardwareEra era) {
        return switch (era) {
            case VINTAGE -> MediaFormat.FLOPPY;
            case LEGACY -> MediaFormat.CD;
            default -> MediaFormat.USB;
        };
    }

    /** The medium a program written in {@code era} ships on; a headless service goes on the stick. */
    public static MediaFormat forProgram(final HardwareEra era, final ProgramKind kind) {
        return switch (era) {
            case VINTAGE -> MediaFormat.FLOPPY;
            case LEGACY -> MediaFormat.CD;
            default -> kind == ProgramKind.SERVICE ? MediaFormat.USB : MediaFormat.DVD;
        };
    }

    /** The drive a medium of {@code format} is read in, for the words on a tooltip. */
    public static String readerName(final MediaFormat format) {
        return switch (format) {
            case FLOPPY -> "Floppy Drive";
            case CD -> "CD Drive";
            case DVD -> "DVD Drive";
            case USB -> "Dock Station";
        };
    }
}
