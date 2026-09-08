/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program.cli;

/**
 * A command that hands the terminal to an editor instead of printing to it.
 *
 * <p>Everything a machine has to decide is decided here, where the machine is: whether the program is
 * installed at all, and what the verb is called. What is left for the screen is the drawing, which is
 * the half a server cannot do.
 *
 * <p>One class serves every such editor, because they differ only in their name and in which program
 * has to be installed for them to exist. An addon's editor is another one of these.
 */
public final class TtyEditorCommand implements ICliCommand, CliShell.IHandOver {

    private final String name;
    private final String summary;
    private final String programId;

    /**
     * @param name      the verb a player types
     * @param summary   what {@code help} says about it
     * @param programId the program that has to be installed for the verb to exist
     */
    public TtyEditorCommand(final String name, final String summary, final String programId) {
        this.name = name;
        this.summary = summary;
        this.programId = programId;
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public String summary() {
        return this.summary;
    }

    @Override
    public String usage() {
        return "<file>";
    }

    /** Only where it was installed: a machine that never got the editor does not offer the verb. */
    @Override
    public boolean available(final ICliComputer computer) {
        for (final ICliComputer.ProgramInfo program : computer.programs()) {
            if (program.id().equals(this.programId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Prints only when it cannot hand over.
     *
     * <p>A file that is not there yet is opened empty, the way a real editor does: writing a program
     * starts by opening the name it will be saved under. So there is nothing to check and nothing to
     * say, and the shell carries the hand-over out on its own.
     */
    @Override
    public void run(final CliContext ctx) {
        if (!ctx.hasArgs()) {
            ctx.out().error("usage: " + this.name + " <file>");
        }
    }
}
