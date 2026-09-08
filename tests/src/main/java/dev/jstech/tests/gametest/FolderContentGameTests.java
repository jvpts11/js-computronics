/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Tech Series.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.operation.payload.FolderContentPayload;
import dev.jstech.computronics.operation.payload.RequestFolderContentPayload;
import dev.jstech.tests.JsTests;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A whole folder of programs going over the wire, at the size it is allowed to be.
 *
 * <p>A cap on a payload string does not cut what is too long: encoding throws and the player is
 * disconnected, far from whatever grew. So the reply an editor asks for is encoded here at its limits,
 * a full folder of the longest files it may carry, which is the shape that would break it.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class FolderContentGameTests {

    private FolderContentGameTests() {
    }

    private static final String ARENA = "empty";

    private static RegistryFriendlyByteBuf buffer(final GameTestHelper helper) {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
    }

    /** A folder as full as one is allowed to be, of files as long as one is allowed to be. */
    @GameTest(template = ARENA)
    public static void folderContent_encodesAFullFolderOfTheLongestFiles(final GameTestHelper helper) {
        final String longest = "x".repeat(FolderContentPayload.MAX_TEXT);
        final List<FolderContentPayload.WireFile> files = new ArrayList<>();
        for (int i = 0; i < FolderContentPayload.MAX_FILES; i++) {
            files.add(new FolderContentPayload.WireFile("progs/program" + i + ".can", longest));
        }
        final FolderContentPayload payload = new FolderContentPayload("progs", files);
        final RegistryFriendlyByteBuf buf = buffer(helper);
        try {
            FolderContentPayload.STREAM_CODEC.encode(buf, payload);
        } catch (final RuntimeException e) {
            helper.fail("a full folder of the longest files does not encode: " + e.getMessage());
            return;
        }
        final FolderContentPayload back = FolderContentPayload.STREAM_CODEC.decode(buf);
        helper.assertTrue(back.files().size() == FolderContentPayload.MAX_FILES,
                "every file survives the round trip");
        helper.assertTrue(back.files().getFirst().text().length() == FolderContentPayload.MAX_TEXT,
                "a file comes back whole rather than cut");
        helper.assertTrue(back.dir().equals("progs"), "the folder it is about survives");
        helper.succeed();
    }

    /** The longest path an editor could ask about, which is what the request carries. */
    @GameTest(template = ARENA)
    public static void requestFolderContent_encodesALongPath(final GameTestHelper helper) {
        final RequestFolderContentPayload payload =
                new RequestFolderContentPayload(BlockPos.ZERO, "progs/" + "deep/".repeat(30), ".can");
        final RegistryFriendlyByteBuf buf = buffer(helper);
        try {
            RequestFolderContentPayload.STREAM_CODEC.encode(buf, payload);
        } catch (final RuntimeException e) {
            helper.fail("a deep folder path does not encode: " + e.getMessage());
            return;
        }
        helper.assertTrue(RequestFolderContentPayload.STREAM_CODEC.decode(buf).equals(payload),
                "the request survives the round trip");
        helper.succeed();
    }

    /** An empty folder is an answer too, and has to travel like one. */
    @GameTest(template = ARENA)
    public static void folderContent_encodesAFolderWithNothingInIt(final GameTestHelper helper) {
        final RegistryFriendlyByteBuf buf = buffer(helper);
        FolderContentPayload.STREAM_CODEC.encode(buf, new FolderContentPayload("progs", List.of()));
        helper.assertTrue(FolderContentPayload.STREAM_CODEC.decode(buf).files().isEmpty(),
                "an empty folder comes back empty");
        helper.succeed();
    }
}
