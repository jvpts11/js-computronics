/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.energy.internal;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Internal utility: splits a total quantity proportionally among recipients, respecting each recipient's max demand and path capacity.
 */
public final class ProportionalSplitter {

    private ProportionalSplitter() {}

    public static Map<Long, Long> split(
            final long available,
            final Map<Long, Long> demand,
            final Map<Long, Long> cap) {

        if (available <= 0 || demand.isEmpty()) {
            return Map.of();
        }

        // Effective ceiling per recipient = min(demand, cap).
        // LinkedHashMap preserves insertion order for deterministic tiebreaks.
        final Map<Long, Long> effectiveCap = new LinkedHashMap<>();
        long totalEffectiveDemand = 0L;
        for (final Map.Entry<Long, Long> e : demand.entrySet()) {
            final long d = Math.max(0L, e.getValue());
            final long c = Math.max(0L, cap.getOrDefault(e.getKey(), Long.MAX_VALUE));
            final long eff = Math.min(d, c);
            if (eff > 0) {
                effectiveCap.put(e.getKey(), eff);
                totalEffectiveDemand = saturatingAdd(totalEffectiveDemand, eff);
            }
        }

        if (totalEffectiveDemand == 0L) {
            return Map.of();
        }

        // Easy case: enough energy for everyone → each gets their effective ceiling.
        if (available >= totalEffectiveDemand) {
            return Map.copyOf(effectiveCap);
        }

        // Shortage case: largest-remainder method.
        final Map<Long, Long> result = new LinkedHashMap<>();
        final Map<Long, Long> remainderNum = new LinkedHashMap<>();
        long allocated = 0L;
        final BigInteger avail = BigInteger.valueOf(available);
        final BigInteger total = BigInteger.valueOf(totalEffectiveDemand);
        for (final Map.Entry<Long, Long> e : effectiveCap.entrySet()) {
            // base = floor(available * effDemand / totalEffectiveDemand). The product can exceed a long
            // for late-tier supplies, so compute it in 128-bit precision; both the quotient (at most
            // `available`) and the remainder (below the total) fit back into a long without loss.
            final BigInteger product = avail.multiply(BigInteger.valueOf(e.getValue()));
            final BigInteger[] divRem = product.divideAndRemainder(total);
            final long base = divRem[0].longValueExact();
            result.put(e.getKey(), base);
            allocated += base;
            remainderNum.put(e.getKey(), divRem[1].longValueExact());
        }

        // Distribute leftover (available - allocated) by largest remainder.
        long leftover = available - allocated;
        if (leftover > 0) {
            final var sortedKeys = remainderNum.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .map(Map.Entry::getKey)
                    .toList();
            for (final Long k : sortedKeys) {
                if (leftover <= 0) {
                    break;
                }
                final long current = result.get(k);
                final long capacity = effectiveCap.get(k);
                if (current < capacity) {
                    result.put(k, current + 1L);
                    leftover--;
                }
            }
        }

        // Filter zeros to keep the map compact.
        final Map<Long, Long> filtered = new LinkedHashMap<>();
        for (final Map.Entry<Long, Long> e : result.entrySet()) {
            if (e.getValue() > 0L) {
                filtered.put(e.getKey(), e.getValue());
            }
        }
        return Map.copyOf(filtered);
    }

    private static long saturatingAdd(final long a, final long b) {
        final long r = a + b;
        if (((a ^ r) & (b ^ r)) < 0) {
            return Long.MAX_VALUE;
        }
        return r;
    }
}
