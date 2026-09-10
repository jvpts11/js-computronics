/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CompletionContextTest {

    /** The caret is written as a pipe, so a case reads as what the player is looking at. */
    private static CompletionContext.Where at(final String lineWithCaret) {
        final int caret = lineWithCaret.indexOf('|');
        return CompletionContext.at(lineWithCaret.replace("|", ""), caret);
    }

    @Test
    void at_readsTheReceiverAndTheEmptyPrefixJustAfterADot() {
        final CompletionContext.Where where = at("        Network.|");
        assertEquals("Network", where.receiver());
        assertEquals("", where.prefix());
        assertTrue(where.intoMember());
    }

    @Test
    void at_readsHowMuchOfTheMemberHasBeenTyped() {
        final CompletionContext.Where where = at("        Network.Watch|");
        assertEquals("Network", where.receiver());
        assertEquals("Watch", where.prefix());
    }

    @Test
    void at_pointsAtTheColumnTheTypedNameStartsOn() {
        final CompletionContext.Where where = at("Network.Wat|");
        assertEquals(9, where.from());
        assertEquals("Wat", where.prefix());
    }

    @Test
    void at_readsABareNameAsAskingForATypeRatherThanAMember() {
        final CompletionContext.Where where = at("        Netw|");
        assertEquals("", where.receiver());
        assertEquals("Netw", where.prefix());
        assertFalse(where.intoMember());
    }

    @Test
    void at_offersNothingOnAnEmptyLine() {
        assertNull(at("|"));
        assertNull(at("        |"));
    }

    @Test
    void at_offersNothingInTheMiddleOfAWord() {
        assertNull(at("Netw|ork.Watch"));
        assertNull(at("Network.Wa|tch"));
    }

    @Test
    void at_offersNothingAfterADotWithNothingBeforeIt() {
        assertNull(at(".|"));
        assertNull(at("        .|"));
    }

    @Test
    void at_readsThroughAnUnderscoreAndADigitInAName() {
        final CompletionContext.Where where = at("my_thing2.Va|");
        assertEquals("my_thing2", where.receiver());
        assertEquals("Va", where.prefix());
    }

    @Test
    void at_readsTheWholeChainBeforeTheDot() {
        final CompletionContext.Where where = at("Network.Current.Qu|");
        assertEquals("Network.Current", where.receiver());
        assertEquals(java.util.List.of("Network", "Current"), where.chain());
        assertEquals("Qu", where.prefix());
    }

    @Test
    void at_stopsAChainAtACall() {
        // What a call gives back is not read through; the list stays closed rather than guessing.
        assertNull(at("Network.Find(name).|"));
        assertNull(at("        items[0].|"));
    }

    @Test
    void chain_isEmptyForANameOnItsOwn() {
        final CompletionContext.Where where = at("        Net|");
        assertFalse(where.intoMember());
        assertTrue(where.chain().isEmpty());
    }

    @Test
    void at_isNotConfusedByWhatComesAfterTheCaret() {
        final CompletionContext.Where where = CompletionContext.at("Network. + rest", 8);
        assertEquals("Network", where.receiver());
        assertEquals("", where.prefix());
    }

    @Test
    void at_refusesACaretOutsideTheLine() {
        assertNull(CompletionContext.at("abc", -1));
        assertNull(CompletionContext.at("abc", 4));
        assertNull(CompletionContext.at(null, 0));
    }

    @Test
    void at_readsAReceiverThatFollowsAnOpeningBracket() {
        final CompletionContext.Where where = at("        Console.PrintLine(Network.|");
        assertEquals("Network", where.receiver());
        assertEquals("", where.prefix());
    }
}
