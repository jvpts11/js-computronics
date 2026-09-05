/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.operation.exec;

import java.util.List;

/**
 * A read-only view over the timed transfers of one parent Operation — its SubOperations, one per source, running in parallel.
 */
public final class OperationProgress {

    private final List<TransferState> transfers;

    public OperationProgress(final List<TransferState> transfers) {
        this.transfers = List.copyOf(transfers);
    }

    public boolean isComplete() {
        for (final TransferState transfer : transfers) {
            if (!transfer.isComplete()) {
                return false;
            }
        }
        return true;
    }

    public long movedTotal() {
        long sum = 0L;
        for (final TransferState transfer : transfers) {
            sum += transfer.moved();
        }
        return sum;
    }

    public long total() {
        long sum = 0L;
        for (final TransferState transfer : transfers) {
            sum += transfer.total();
        }
        return sum;
    }

    public int percent() {
        final long total = total();
        return total <= 0L ? 100 : (int) (movedTotal() * 100L / total);
    }

    public int sourceCount() {
        return transfers.size();
    }
}
