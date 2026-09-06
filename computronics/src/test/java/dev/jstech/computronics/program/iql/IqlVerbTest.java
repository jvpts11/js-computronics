/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program.iql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class IqlVerbTest {

    @Test
    void values_coverTheThirteenGrammarVerbs() {
        assertEquals(13, IqlVerb.values().length);
    }

    @Test
    void fromKeyword_resolvesCaseInsensitively() {
        assertEquals(Optional.of(IqlVerb.SELECT), IqlVerb.fromKeyword("select"));
        assertEquals(Optional.of(IqlVerb.MOVE), IqlVerb.fromKeyword("MOVE"));
        assertEquals(Optional.of(IqlVerb.DELETE), IqlVerb.fromKeyword("Delete"));
    }

    @Test
    void fromKeyword_treatsShowAsAliasOfQuery() {
        assertEquals(Optional.of(IqlVerb.QUERY), IqlVerb.fromKeyword("show"));
        assertEquals(Optional.of(IqlVerb.QUERY), IqlVerb.fromKeyword("SHOW"));
    }

    @Test
    void fromKeyword_emptyForUnknownBlankOrNull() {
        assertTrue(IqlVerb.fromKeyword("frobnicate").isEmpty());
        assertTrue(IqlVerb.fromKeyword("").isEmpty());
        assertTrue(IqlVerb.fromKeyword(null).isEmpty());
    }
}
