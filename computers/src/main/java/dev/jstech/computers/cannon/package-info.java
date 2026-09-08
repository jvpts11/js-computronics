/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
/**
 * Cannon, the language a player writes programs in, and the compiler that reads it.
 *
 * <p>A program is written in a {@code .can} file on a computer's own disk and compiled to the
 * readable assembly the runtime executes. This package holds the front of that compiler: the
 * characters, the tokens, the tree and the messages. It is deliberately free of Minecraft, because
 * reading a program is text work and belongs nowhere near a tick.
 */
package dev.jstech.computers.cannon;
