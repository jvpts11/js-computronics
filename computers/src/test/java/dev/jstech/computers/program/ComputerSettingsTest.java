/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.program;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComputerSettingsTest {

    private ComputerSettings settings;

    @BeforeEach
    void setUp() {
        settings = new ComputerSettings();
    }

    @Test
    void setGuiScale_holdsBetweenHalfAndWhole() {
        settings.setGuiScale(9);
        assertEquals(50, settings.guiScale(), "a percentage below half is held at half");
        settings.setGuiScale(120);
        assertEquals(100, settings.guiScale(), "nothing bigger than the designed size");
        settings.setGuiScale(75);
        assertEquals(75, settings.guiScale());
        settings.setGuiScale(0);
        assertEquals(0, settings.guiScale(), "zero stands for the default");
    }

    @Test
    void setBrightness_clampsBelowZeroToZero() {
        settings.setBrightness(-40);
        assertEquals(0, settings.brightness());
    }

    @Test
    void applySetting_clock12hSetsTrue() {
        assertTrue(settings.applySetting("clock", "12h"));
        assertTrue(settings.clock12h());
    }

    @Test
    void applySetting_clock24hSetsFalse() {
        settings.setClock12h(true);
        assertTrue(settings.applySetting("clock", "24h"));
        assertFalse(settings.clock12h());
    }

    @Test
    void applySetting_clockInvalidValueIsRejected() {
        assertFalse(settings.applySetting("clock", "noon"));
    }

    @Test
    void applySetting_themeSystemClearsToDefault() {
        settings.setThemePreset("ocean");
        assertTrue(settings.applySetting("theme", "system"));
        assertEquals("", settings.themePreset());
    }

    @Test
    void applySetting_accentParsesSixHexDigits() {
        assertTrue(settings.applySetting("accent", "3A6AE0"));
        assertEquals(0xFF3A6AE0, settings.accent());
    }

    @Test
    void applySetting_accentAcceptsHashPrefix() {
        assertTrue(settings.applySetting("accent", "#12A26F"));
        assertEquals(0xFF12A26F, settings.accent());
    }

    @Test
    void applySetting_accentRejectsBadHex() {
        assertFalse(settings.applySetting("accent", "ZZZ"));
    }

    @Test
    void applySetting_autoopenOffTurnsItOff() {
        assertTrue(settings.applySetting("autoopen", "off"));
        assertFalse(settings.removableAutoOpen());
    }

    @Test
    void applySetting_savedriveUppercasesTheLetter() {
        assertTrue(settings.applySetting("savedrive", "d"));
        assertEquals('D', settings.defaultSaveDrive());
    }

    @Test
    void applySetting_guiscaleClampsThroughApply() {
        assertTrue(settings.applySetting("guiscale", "10"));
        assertEquals(50, settings.guiScale());
    }

    @Test
    void applySetting_defaultAppPrefixStoresPerExtension() {
        assertTrue(settings.applySetting("defaultapp:txt", "jsc:editor"));
        assertEquals("jsc:editor", settings.defaultApp("TXT"));
    }

    @Test
    void applySetting_unknownKeyReturnsFalse() {
        assertFalse(settings.applySetting("frobnicate", "1"));
    }

    @Test
    void setDefaultApp_blankValueRemovesTheEntry() {
        settings.setDefaultApp("txt", "jsc:editor");
        settings.setDefaultApp("txt", "");
        assertEquals("", settings.defaultApp("txt"));
    }

    @Test
    void taskbarCentered_defaultsToTrue() {
        assertTrue(settings.taskbarCentered());
    }

    @Test
    void applySetting_taskbarLeftClearsCentered() {
        assertTrue(settings.applySetting("taskbar", "left"));
        assertFalse(settings.taskbarCentered());
    }

    @Test
    void applySetting_taskbarCenterSetsCentered() {
        settings.setTaskbarCentered(false);
        assertTrue(settings.applySetting("taskbar", "center"));
        assertTrue(settings.taskbarCentered());
    }

    @Test
    void applySetting_taskbarInvalidValueIsRejected() {
        assertFalse(settings.applySetting("taskbar", "diagonal"));
    }

    @Test
    void darkMode_defaultsToFalse() {
        assertFalse(settings.darkMode());
    }

    @Test
    void applySetting_darkmodeOnEnablesIt() {
        assertTrue(settings.applySetting("darkmode", "on"));
        assertTrue(settings.darkMode());
    }

    @Test
    void applySetting_darkmodeOffDisablesIt() {
        settings.setDarkMode(true);
        assertTrue(settings.applySetting("darkmode", "off"));
        assertFalse(settings.darkMode());
    }

    @Test
    void applySetting_darkmodeInvalidValueIsRejected() {
        assertFalse(settings.applySetting("darkmode", "sepia"));
    }

    @Test
    void pinned_startsWithTheFileExplorer() {
        assertEquals(java.util.List.of("files"), settings.pinned());
        assertTrue(settings.isPinned("jsc:files"), "a namespaced id names the same program");
    }

    @Test
    void pin_appendsOnceAndKeepsTheOrder() {
        assertTrue(settings.pin("editor"));
        assertFalse(settings.pin("Editor"), "pinning again changes nothing");
        assertTrue(settings.pin("jsc:command_prompt"));
        assertEquals(java.util.List.of("files", "editor", "command_prompt"), settings.pinned());
    }

    @Test
    void unpin_takesTheProgramOff() {
        assertTrue(settings.unpin("files"));
        assertFalse(settings.unpin("files"), "already gone");
        assertTrue(settings.pinned().isEmpty());
    }

    @Test
    void pin_stopsAtTheCap() {
        for (int i = 0; i < ComputerSettings.MAX_PINNED + 3; i++) {
            settings.pin("program" + i);
        }
        assertEquals(ComputerSettings.MAX_PINNED, settings.pinned().size());
    }

    @Test
    void setPinned_replacesTheListDroppingBlanksAndRepeats() {
        settings.setPinned(java.util.List.of("editor", "", "editor", "files"));
        assertEquals(java.util.List.of("editor", "files"), settings.pinned());
        settings.setPinned(java.util.List.of());
        assertTrue(settings.pinned().isEmpty(), "an empty list is a choice, not the default");
    }

    @Test
    void applySetting_pinAndUnpinRouteToTheList() {
        assertTrue(settings.applySetting("pin", "editor"));
        assertTrue(settings.applySetting("pin", "editor"), "asking for what is already so is fine");
        assertTrue(settings.isPinned("editor"));
        assertTrue(settings.applySetting("unpin", "editor"));
        assertFalse(settings.isPinned("editor"));
        assertFalse(settings.applySetting("pin", "  "), "nothing to pin");
    }

    @Test
    void summaryLines_reportEveryOwnedSetting() {
        settings.setClock12h(true);
        settings.setBrightness(80);
        settings.setTaskbarCentered(false);
        final String joined = String.join("\n", settings.summaryLines());
        assertTrue(joined.contains("clock"));
        assertTrue(joined.contains("12h"));
        assertTrue(joined.contains("brightness"));
        assertTrue(joined.contains("80"));
        assertTrue(joined.contains("accent"));
        assertTrue(joined.contains("taskbar"));
        assertTrue(joined.contains("left"));
    }
}
