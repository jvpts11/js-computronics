/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon.ast;

/**
 * Anything in the tree that can be pointed at in the source.
 *
 * <p>Every node keeps the position it started at, one-based, so a message from any later stage of
 * the compiler can name a line and a column without the tree having to be walked backwards.
 */
public interface INode {

    /** The one-based line this node starts on. */
    int line();

    /** The one-based column this node starts at. */
    int column();
}
