/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.gui.layout;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.core.gui.layout.GuiLayout;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * An aggressive, cross-cutting audit of every screen layout in the mod. Per-screen tests check one screen;
 * this one sweeps them all and is hard to slip past: a layout that overlaps, spills out of its frame, hides
 * an invisible (zero-sized) solid, or — for a fixed-size screen — outgrows the on-screen budget fails here.
 * It also reflects over the layout package and fails if a new layout factory is added without an audit case,
 * so coverage can never silently regress (the way the NMS out-of-frame bug slipped through).
 */
class LayoutAuditTest {

    /** A fixed-size GUI must fit a usable screen: width up to ~360px, height up to ~256px (the project's
     *  honest ceiling — beyond it the game's auto GUI scale starts clipping the panel). */
    private static final int SCREEN_W_BUDGET = 360;
    private static final int SCREEN_H_BUDGET = 256;

    /** Layout classes that produce a {@link GuiLayout} and are exercised by {@link #cases()}. */
    private static final Set<String> COVERED = Set.of(
            "BusLayout", "ClusterManagementComputerLayout", "CraftingComputerLayout", "NmsLayout",
            "ComputerTerminalLayout", "ServerRouterLayout", "NetworkInteractorLayout",
            "CraftingSwitchLayout", "PatternEncoderLayout", "ServerRackLayout", "FilesLayout", "ThisPcLayout",
            "PatternStudioLayout");

    private record AuditCase(String label, GuiLayout layout, boolean fixedSize) {
    }

    /** Every layout, at the sizes/states worth auditing (worst cases for the parametrized ones). */
    private static List<AuditCase> cases() {
        final List<AuditCase> c = new ArrayList<>();
        c.add(new AuditCase("BusLayout", BusLayout.layout(), true));
        c.add(new AuditCase("CraftingSwitchLayout", CraftingSwitchLayout.layout(), true));
        c.add(new AuditCase("PatternEncoderLayout", PatternEncoderLayout.layout(), true));
        c.add(new AuditCase("ClusterManagementComputerLayout", ClusterManagementComputerLayout.layout(), true));
        c.add(new AuditCase("CraftingComputerLayout", CraftingComputerLayout.layout(), true));
        c.add(new AuditCase("NmsLayout", NmsLayout.layout(), true));
        c.add(new AuditCase("ServerRackLayout", ServerRackLayout.layout(), true));
        c.add(new AuditCase("ComputerTerminalLayout(mainframe)", ComputerTerminalLayout.layout(true), true));
        c.add(new AuditCase("ComputerTerminalLayout(pc)", ComputerTerminalLayout.layout(false), true));
        // The explorer and This PC are resizable desktop windows: audit the smallest, the default and a
        // maximised size, plus the row and grid geometry that depend on the width alone.
        for (final int[] size : new int[][]{
                {dev.jsc.jscomputronics.module.computing.gui.layout.FilesLayout.MIN_W,
                        dev.jsc.jscomputronics.module.computing.gui.layout.FilesLayout.MIN_H},
                {dev.jsc.jscomputronics.module.computing.gui.layout.FilesLayout.DEFAULT_W,
                        dev.jsc.jscomputronics.module.computing.gui.layout.FilesLayout.DEFAULT_H},
                {420, 300}}) {
            c.add(new AuditCase("FilesLayout(" + size[0] + "x" + size[1] + ")",
                    dev.jsc.jscomputronics.module.computing.gui.layout.FilesLayout.layout(size[0], size[1]), false));
            c.add(new AuditCase("FilesLayout.columns(" + size[0] + ")",
                    dev.jsc.jscomputronics.module.computing.gui.layout.FilesLayout.columns(size[0]), false));
        }
        for (final int[] size : new int[][]{
                {dev.jsc.jscomputronics.module.computing.gui.layout.ThisPcLayout.MIN_W,
                        dev.jsc.jscomputronics.module.computing.gui.layout.ThisPcLayout.MIN_H},
                {dev.jsc.jscomputronics.module.computing.gui.layout.ThisPcLayout.DEFAULT_W,
                        dev.jsc.jscomputronics.module.computing.gui.layout.ThisPcLayout.DEFAULT_H},
                {420, 300}}) {
            c.add(new AuditCase("ThisPcLayout(" + size[0] + "x" + size[1] + ")",
                    dev.jsc.jscomputronics.module.computing.gui.layout.ThisPcLayout.layout(size[0], size[1]), false));
            for (int buttons = 0; buttons <= 3; buttons++) {
                c.add(new AuditCase("ThisPcLayout.driveRow(" + size[0] + "," + buttons + ")",
                        dev.jsc.jscomputronics.module.computing.gui.layout.ThisPcLayout.driveRow(size[0], buttons), false));
            }
            c.add(new AuditCase("ThisPcLayout.programGrid(" + size[0] + ")",
                    dev.jsc.jscomputronics.module.computing.gui.layout.ThisPcLayout.programGrid(size[0], 7), false));
        }
        // The Server Router tiles one section per output face; audit every count up to the maximum.
        for (int s = 0; s <= ServerRouterLayout.MAX_SECTIONS; s++) {
            c.add(new AuditCase("ServerRouterLayout(" + s + ")", ServerRouterLayout.layout(s), true));
        }
        // The Network Interactor is a resizable desktop window — audit a spread of content sizes. Not
        // screen-budgeted: the window itself is clamped to the screen elsewhere; here we only require that
        // whatever size it resolves to is internally clean.
        final int minW = NetworkInteractorLayout.minContentWidth();
        c.add(new AuditCase("NetworkInteractorLayout(default)", NetworkInteractorLayout.toGuiLayout(330, 226), false));
        c.add(new AuditCase("NetworkInteractorLayout(min)", NetworkInteractorLayout.toGuiLayout(minW, 150), false));
        c.add(new AuditCase("NetworkInteractorLayout(wide)", NetworkInteractorLayout.toGuiLayout(520, 360), false));
        // The Pattern Studio is a resizable desktop window too: the content a standard monitor's window gives
        // it (194), a maximized one (210) and one too short for the inventory band, which then folds away.
        for (final int h : new int[]{194, 210, dev.jsc.jscomputronics.module.computing.gui.layout.PatternStudioLayout
                .minContentHeight() - 1}) {
            c.add(new AuditCase("PatternStudioLayout(" + h + ")",
                    dev.jsc.jscomputronics.module.computing.gui.layout.PatternStudioLayout.layout(322, h), false));
        }
        return c;
    }

    @Test
    void noLayoutHasOverlappingSolids() {
        for (final AuditCase c : cases()) {
            assertTrue(c.layout().overlaps().isEmpty(),
                    c.label() + " has overlapping solid elements: " + c.layout().overlaps());
        }
    }

    @Test
    void noLayoutSpillsOutOfItsFrame() {
        for (final AuditCase c : cases()) {
            assertTrue(c.layout().outOfBounds().isEmpty(),
                    c.label() + " has elements drawn out of frame: " + c.layout().outOfBounds());
        }
    }

    @Test
    void noLayoutHasInvisibleSolids() {
        for (final AuditCase c : cases()) {
            assertTrue(c.layout().zeroSizedSolids().isEmpty(),
                    c.label() + " has zero-sized (invisible) solid elements: " + c.layout().zeroSizedSolids());
        }
    }

    @Test
    void fixedSizeLayoutsFitTheScreenBudget() {
        for (final AuditCase c : cases()) {
            if (!c.fixedSize()) {
                continue;
            }
            assertTrue(c.layout().width() <= SCREEN_W_BUDGET,
                    c.label() + " width " + c.layout().width() + " exceeds the " + SCREEN_W_BUDGET + "px budget");
            assertTrue(c.layout().height() <= SCREEN_H_BUDGET,
                    c.label() + " height " + c.layout().height() + " exceeds the " + SCREEN_H_BUDGET + "px budget");
        }
    }

    @Test
    void everyLayoutFactoryHasAnAuditCase() throws Exception {
        // Reflection guard: any class in the layout package that produces a GuiLayout must be covered by
        // cases() above. A new screen layout added without an audit case fails here — coverage can't regress.
        final List<Class<?>> classes = layoutClasses();
        assertFalse(classes.isEmpty(), "no *Layout classes found on the classpath — the scan path is wrong");
        for (final Class<?> cls : classes) {
            final boolean producesLayout = Arrays.stream(cls.getDeclaredMethods())
                    .anyMatch(m -> Modifier.isStatic(m.getModifiers()) && Modifier.isPublic(m.getModifiers())
                            && m.getReturnType() == GuiLayout.class);
            if (producesLayout) {
                assertTrue(COVERED.contains(cls.getSimpleName()),
                        cls.getSimpleName() + " produces a GuiLayout but has no audit case in LayoutAuditTest"
                                + " — add it to cases() and COVERED");
            }
        }
    }

    /** Loads every {@code *Layout} class in the computing layout package from the compiled-classes directory.
     *  Anchored on a known layout class's code source (the {@code build/classes/java/main} root under Gradle),
     *  which is more robust than a context-classloader resource lookup. */
    private static List<Class<?>> layoutClasses() throws Exception {
        final String pkg = "dev.jsc.jscomputronics.module.computing.gui.layout";
        final URL loc = BusLayout.class.getProtectionDomain().getCodeSource().getLocation();
        final File classesRoot = new File(loc.toURI());
        final File dir = new File(classesRoot, pkg.replace('.', '/'));
        final List<Class<?>> out = new ArrayList<>();
        final File[] files = dir.listFiles((d, name) -> name.endsWith("Layout.class") && !name.contains("$"));
        if (files == null) {
            return out;
        }
        for (final File f : files) {
            final String simple = f.getName().substring(0, f.getName().length() - ".class".length());
            out.add(Class.forName(pkg + "." + simple));
        }
        return out;
    }
}
