/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Tech Series.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computers.operation.payload.CommandOutputPayload;
import dev.jstech.computers.operation.payload.DesktopShellOutputPayload;
import dev.jstech.computers.program.cli.CliCommands;
import dev.jstech.computers.program.cli.ICliCommand;
import dev.jstech.computers.os.ShellFamily;
import dev.jstech.tests.JsTests;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Handing a terminal to an editor, over the wire and through the shell.
 *
 * <p>The machine decides whether an editor opens, so the two replies that carry that decision are
 * encoded here at the longest they may be: a cap on a payload string does not cut what is too long, it
 * throws as the packet is sent and takes the player's connection with it.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class TtyEditorGameTests {

    private TtyEditorGameTests() {
    }

    private static final String ARENA = "empty";

    /** The longest a file's path may be on the wire, which is what an editor is opened on. */
    private static final String LONG_PATH = "progs/" + "deep/".repeat(29) + "a.can";

    private static RegistryFriendlyByteBuf buffer(final GameTestHelper helper) {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
    }

    /** The desktop terminal's reply, carrying a hand-over on the longest path it could name. */
    @GameTest(template = ARENA)
    public static void desktopShellOutput_carriesAHandOverOverTheWire(final GameTestHelper helper) {
        final DesktopShellOutputPayload payload = new DesktopShellOutputPayload(
                false, false, "C:\\progs>", List.of(), "vim", LONG_PATH);
        final RegistryFriendlyByteBuf buf = buffer(helper);
        try {
            DesktopShellOutputPayload.STREAM_CODEC.encode(buf, payload);
        } catch (final RuntimeException e) {
            helper.fail("a hand-over does not encode: " + e.getMessage());
            return;
        }
        final DesktopShellOutputPayload back = DesktopShellOutputPayload.STREAM_CODEC.decode(buf);
        helper.assertTrue(back.handsOver(), "the reply still says it hands the terminal over");
        helper.assertTrue(back.editor().equals("vim"), "the editor named survives");
        helper.assertTrue(back.editorPath().equals(LONG_PATH), "the file it opens on survives whole");
        helper.succeed();
    }

    /** The same for the terminal of a machine with no desktop at all, which is where it matters most. */
    @GameTest(template = ARENA)
    public static void commandOutput_carriesAHandOverOverTheWire(final GameTestHelper helper) {
        final CommandOutputPayload payload =
                new CommandOutputPayload(false, "C:\\progs>", List.of(), "vim", LONG_PATH);
        final RegistryFriendlyByteBuf buf = buffer(helper);
        try {
            CommandOutputPayload.STREAM_CODEC.encode(buf, payload);
        } catch (final RuntimeException e) {
            helper.fail("a hand-over does not encode: " + e.getMessage());
            return;
        }
        final CommandOutputPayload back = CommandOutputPayload.STREAM_CODEC.decode(buf);
        helper.assertTrue(back.handsOver(), "the reply still says it hands the terminal over");
        helper.assertTrue(back.editorPath().equals(LONG_PATH), "the file it opens on survives whole");
        helper.succeed();
    }

    /** A reply that only printed says so, which is what nearly every command does. */
    @GameTest(template = ARENA)
    public static void aPlainReply_handsNothingOver(final GameTestHelper helper) {
        helper.assertTrue(!new DesktopShellOutputPayload(false, false, "", List.of()).handsOver(),
                "a plain desktop reply hands nothing over");
        helper.assertTrue(!new CommandOutputPayload(false, "", List.of()).handsOver(),
                "a plain prompt reply hands nothing over");
        helper.succeed();
    }

    /**
     * The editors are verbs of the shell, so a machine that has them lists them in help.
     *
     * <p>They are also gated on being installed, which is why the plain shell offers them and a
     * computer without them does not; that half is the machine's own answer at run time.
     */
    @GameTest(template = ARENA)
    public static void theEditors_areCommandsOfEveryShellFamily(final GameTestHelper helper) {
        for (final ShellFamily family : ShellFamily.values()) {
            for (final String verb : List.of("vim", "emacs")) {
                boolean found = false;
                for (final ICliCommand command : CliCommands.commandsFor(family)) {
                    if (command.name().equals(verb)) {
                        found = true;
                        helper.assertTrue(command.usage().contains("<file>"),
                                verb + " should say it takes a file");
                    }
                }
                helper.assertTrue(found, family + " should know " + verb);
            }
        }
        helper.succeed();
    }
}
