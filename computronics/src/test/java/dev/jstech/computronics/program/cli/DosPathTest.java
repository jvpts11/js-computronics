/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.computronics.program.cli.DosPath.Location;
import java.util.List;
import org.junit.jupiter.api.Test;

class DosPathTest {

    private static Location cRoot() {
        return Location.root('C');
    }

    private static Location loc(final char drive, final String... segments) {
        return new Location(drive, List.of(segments));
    }

    @Test
    void resolve_relativeName_appendsToCurrent() {
        final Location result = DosPath.resolve(loc('C', "SYSTEM"), "CRAFTS");
        assertEquals("C:\\SYSTEM\\CRAFTS", result.dosPath());
        assertEquals("SYSTEM/CRAFTS", result.storagePath());
    }

    @Test
    void resolve_dotDot_popsOneSegment() {
        final Location result = DosPath.resolve(loc('C', "SYSTEM", "CRAFTS"), "..");
        assertEquals("C:\\SYSTEM", result.dosPath());
    }

    @Test
    void resolve_dotDotAtRoot_clampsToRoot() {
        final Location result = DosPath.resolve(cRoot(), "..");
        assertTrue(result.isRoot());
        assertEquals("C:\\", result.dosPath());
    }

    @Test
    void resolve_leadingBackslash_isAbsoluteFromDriveRoot() {
        final Location result = DosPath.resolve(loc('C', "SYSTEM", "CRAFTS"), "\\DATA");
        assertEquals("C:\\DATA", result.dosPath());
    }

    @Test
    void resolve_driveQualifier_switchesDriveToRoot() {
        final Location result = DosPath.resolve(loc('C', "SYSTEM"), "A:");
        assertEquals('A', result.drive());
        assertTrue(result.isRoot());
        assertEquals("A:\\", result.dosPath());
    }

    @Test
    void resolve_driveQualifierWithPath_isAbsoluteOnThatDrive() {
        final Location result = DosPath.resolve(loc('C', "SYSTEM"), "D:\\BACKUP\\OLD");
        assertEquals('D', result.drive());
        assertEquals("D:\\BACKUP\\OLD", result.dosPath());
        assertEquals("BACKUP/OLD", result.storagePath());
    }

    @Test
    void resolve_forwardSlashesAndDots_normalize() {
        final Location result = DosPath.resolve(cRoot(), "SYSTEM/./CRAFTS/../DATA");
        assertEquals("C:\\SYSTEM\\DATA", result.dosPath());
    }

    @Test
    void resolve_blankInput_returnsCurrentUnchanged() {
        final Location current = loc('C', "SYSTEM");
        assertEquals(current.dosPath(), DosPath.resolve(current, "  ").dosPath());
    }

    @Test
    void resolve_lowerCaseDrive_isUpperCased() {
        assertEquals('C', DosPath.resolve(loc('c', "x"), "y").drive());
    }

    @Test
    void location_rootStoragePath_isEmpty() {
        assertEquals("", cRoot().storagePath());
    }

    @Test
    void location_parentAndChild_areInverse() {
        final Location dir = loc('C', "SYSTEM", "CRAFTS");
        assertEquals(dir.dosPath(), dir.parent().child("CRAFTS").dosPath());
    }

    @Test
    void location_nameAtRoot_isEmpty() {
        assertEquals("", cRoot().name());
        assertEquals("CRAFTS", loc('C', "SYSTEM", "CRAFTS").name());
    }
}
