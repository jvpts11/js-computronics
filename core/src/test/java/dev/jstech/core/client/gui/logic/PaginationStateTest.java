/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.client.gui.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginationStateTest {

    @Test
    void of_startsAtPageZero() {
        assertEquals(0, PaginationState.of(100, 10).currentPage());
    }

    @Test
    void totalPages_exactDivision() {
        assertEquals(10, PaginationState.of(100, 10).totalPages());
    }

    @Test
    void totalPages_withRemainder() {
        assertEquals(11, PaginationState.of(105, 10).totalPages());
    }

    @Test
    void totalPages_minimumOneWhenEmpty() {
        assertEquals(1, PaginationState.of(0, 10).totalPages());
    }

    @Test
    void nextPage_advances() {
        assertEquals(1, PaginationState.of(100, 10).nextPage().currentPage());
    }

    @Test
    void nextPage_clampsAtLast() {
        PaginationState last = PaginationState.of(100, 10).toPage(9).nextPage();
        assertEquals(9, last.currentPage());
    }

    @Test
    void prevPage_clampsAtZero() {
        assertEquals(0, PaginationState.of(100, 10).prevPage().currentPage());
    }

    @Test
    void toPage_clampsHigh() {
        assertEquals(9, PaginationState.of(100, 10).toPage(999).currentPage());
    }

    @Test
    void toPage_clampsLow() {
        assertEquals(0, PaginationState.of(100, 10).toPage(-5).currentPage());
    }

    @Test
    void hasNext_trueOnFirst() {
        assertTrue(PaginationState.of(100, 10).hasNext());
    }

    @Test
    void hasNext_falseOnLast() {
        assertFalse(PaginationState.of(100, 10).toPage(9).hasNext());
    }

    @Test
    void hasPrev_falseOnFirst() {
        assertFalse(PaginationState.of(100, 10).hasPrev());
    }

    @Test
    void hasPrev_trueAfterAdvance() {
        assertTrue(PaginationState.of(100, 10).nextPage().hasPrev());
    }

    @Test
    void firstItemIndex_perPage() {
        assertEquals(0, PaginationState.of(100, 10).firstItemIndex());
        assertEquals(20, PaginationState.of(100, 10).toPage(2).firstItemIndex());
    }

    @Test
    void lastItemIndexExclusive_fullPage() {
        assertEquals(10, PaginationState.of(100, 10).lastItemIndexExclusive());
    }

    @Test
    void itemsOnCurrentPage_lastPagePartial() {
        // 105 items, 10/page, last page (index 10) holds 5 items.
        PaginationState last = PaginationState.of(105, 10).toPage(10);
        assertEquals(100, last.firstItemIndex());
        assertEquals(105, last.lastItemIndexExclusive());
        assertEquals(5, last.itemsOnCurrentPage());
    }

    @Test
    void displayPageNumber_isOneIndexed() {
        assertEquals(1, PaginationState.of(100, 10).displayPageNumber());
        assertEquals(3, PaginationState.of(100, 10).toPage(2).displayPageNumber());
    }

    @Test
    void constructor_negativeTotalItems_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new PaginationState(-1, 10, 0));
    }

    @Test
    void constructor_zeroItemsPerPage_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new PaginationState(100, 0, 0));
    }

    @Test
    void constructor_clampsPageIntoRange() {
        assertEquals(9, new PaginationState(100, 10, 999).currentPage());
        assertEquals(0, new PaginationState(100, 10, -5).currentPage());
    }

    @Test
    void withTotalItems_keepsPerPage_reclampsPage() {
        PaginationState p = PaginationState.of(100, 10).toPage(9); // last page
        PaginationState shrunk = p.withTotalItems(20); // now only 2 pages
        assertEquals(10, shrunk.itemsPerPage());
        assertEquals(1, shrunk.currentPage()); // clamped to last valid page
    }

    @Test
    void withTotalItems_growing_keepsCurrentPage() {
        PaginationState p = PaginationState.of(20, 10).toPage(1);
        PaginationState grown = p.withTotalItems(100);
        assertEquals(1, grown.currentPage());
        assertEquals(10, grown.totalPages());
    }
}