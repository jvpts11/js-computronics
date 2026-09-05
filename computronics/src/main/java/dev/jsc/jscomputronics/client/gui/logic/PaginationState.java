/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.client.gui.logic;

/**
 * Immutable pagination state for page-based views (e.g. a DataTable).
 */
public record PaginationState(int totalItems, int itemsPerPage, int currentPage) {

    public PaginationState {
        if (totalItems < 0) {
            throw new IllegalArgumentException(
                    "totalItems must be >= 0; got " + totalItems);
        }
        if (itemsPerPage < 1) {
            throw new IllegalArgumentException(
                    "itemsPerPage must be >= 1; got " + itemsPerPage);
        }
        final int pages = Math.max(1,
                (int) Math.ceil((double) totalItems / itemsPerPage));
        if (currentPage < 0) {
            currentPage = 0;
        } else if (currentPage >= pages) {
            currentPage = pages - 1;
        }
    }

    public static PaginationState of(final int totalItems, final int itemsPerPage) {
        return new PaginationState(totalItems, itemsPerPage, 0);
    }

    public int totalPages() {
        return Math.max(1, (int) Math.ceil((double) totalItems / itemsPerPage));
    }

    public PaginationState nextPage() {
        return new PaginationState(totalItems, itemsPerPage, currentPage + 1);
    }

    public PaginationState prevPage() {
        return new PaginationState(totalItems, itemsPerPage, currentPage - 1);
    }

    public PaginationState toPage(final int page) {
        return new PaginationState(totalItems, itemsPerPage, page);
    }

    public PaginationState withTotalItems(final int newTotal) {
        return new PaginationState(newTotal, itemsPerPage, currentPage);
    }

    public boolean hasNext() {
        return currentPage < totalPages() - 1;
    }

    public boolean hasPrev() {
        return currentPage > 0;
    }

    public int firstItemIndex() {
        return currentPage * itemsPerPage;
    }

    public int lastItemIndexExclusive() {
        return Math.min(firstItemIndex() + itemsPerPage, totalItems);
    }

    public int itemsOnCurrentPage() {
        return lastItemIndexExclusive() - firstItemIndex();
    }

    public int displayPageNumber() {
        return currentPage + 1;
    }
}