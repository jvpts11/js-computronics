/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.network;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisjointSetUnionTest {

    private DisjointSetUnion dsu;

    @BeforeEach
    void setUp() {
        dsu = new DisjointSetUnion();
    }

    @Test
    void newDsu_isEmpty() {
        assertEquals(0, dsu.size());
        assertEquals(0, dsu.componentCount());
    }

    @Test
    void constructor_rejectsNonPositiveInitialCapacity() {
        assertThrows(IllegalArgumentException.class,
                () -> new DisjointSetUnion(0));
        assertThrows(IllegalArgumentException.class,
                () -> new DisjointSetUnion(-1));
    }

    @Test
    void makeSet_returnsDenseIncreasingIds() {
        assertEquals(0, dsu.makeSet());
        assertEquals(1, dsu.makeSet());
        assertEquals(2, dsu.makeSet());
        assertEquals(3, dsu.makeSet());
    }

    @Test
    void makeSet_incrementsSizeAndComponentCount() {
        for (int i = 1; i <= 10; i++) {
            dsu.makeSet();
            assertEquals(i, dsu.size());
            assertEquals(i, dsu.componentCount());
        }
    }

    @Test
    void makeSet_growsBackingCapacity() {
        // Default initial capacity is 16 — adding 100 should trigger resizes.
        for (int i = 0; i < 100; i++) {
            dsu.makeSet();
        }
        assertEquals(100, dsu.size());
        assertEquals(100, dsu.componentCount());
        // All elements still findable.
        for (int i = 0; i < 100; i++) {
            assertEquals(i, dsu.find(i));
        }
    }

    @Test
    void find_onSingletons_returnsSelf() {
        var a = dsu.makeSet();
        var b = dsu.makeSet();
        assertEquals(a, dsu.find(a));
        assertEquals(b, dsu.find(b));
    }

    @Test
    void find_rejectsInvalidId() {
        dsu.makeSet();
        assertThrows(IndexOutOfBoundsException.class, () -> dsu.find(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> dsu.find(1));
        assertThrows(IndexOutOfBoundsException.class, () -> dsu.find(100));
    }

    @Test
    void union_distinctSets_returnsTrueAndMerges() {
        var a = dsu.makeSet();
        var b = dsu.makeSet();
        assertTrue(dsu.union(a, b));
        assertTrue(dsu.connected(a, b));
        assertEquals(1, dsu.componentCount());
    }

    @Test
    void union_alreadyMerged_returnsFalseAndIsNoOp() {
        var a = dsu.makeSet();
        var b = dsu.makeSet();
        dsu.union(a, b);
        // Second union on same elements: no-op.
        assertFalse(dsu.union(a, b));
        assertEquals(1, dsu.componentCount());
    }

    @Test
    void union_acrossThreeElements_mergesAll() {
        var a = dsu.makeSet();
        var b = dsu.makeSet();
        var c = dsu.makeSet();
        dsu.union(a, b);
        dsu.union(b, c);
        assertTrue(dsu.connected(a, c));
        assertEquals(1, dsu.componentCount());
    }

    @Test
    void union_componentCountDecreasesByOne() {
        for (int i = 0; i < 5; i++) {
            dsu.makeSet();
        }
        assertEquals(5, dsu.componentCount());
        dsu.union(0, 1); assertEquals(4, dsu.componentCount());
        dsu.union(2, 3); assertEquals(3, dsu.componentCount());
        dsu.union(0, 2); assertEquals(2, dsu.componentCount()); // merges {0,1} with {2,3}
        dsu.union(0, 4); assertEquals(1, dsu.componentCount()); // merges {0,1,2,3} with {4}
    }

    @Test
    void connected_onSelf_isAlwaysTrue() {
        var a = dsu.makeSet();
        assertTrue(dsu.connected(a, a));
    }

    @Test
    void connected_distinctUnmergedElements_isFalse() {
        var a = dsu.makeSet();
        var b = dsu.makeSet();
        assertFalse(dsu.connected(a, b));
    }

    @Test
    void connected_isTransitive() {
        // a-b-c all merged through chain. a and c never directly unioned.
        var a = dsu.makeSet();
        var b = dsu.makeSet();
        var c = dsu.makeSet();
        dsu.union(a, b);
        dsu.union(b, c);
        assertTrue(dsu.connected(a, c));
    }

    @Test
    void find_isStableAcrossRepeatedCalls() {
        // After path compression, repeated finds must return the same root.
        var ids = new int[100];
        for (int i = 0; i < 100; i++) {
            ids[i] = dsu.makeSet();
        }
        // Build a chain: 0-1-2-3-...-99
        for (int i = 0; i < 99; i++) {
            dsu.union(ids[i], ids[i + 1]);
        }
        int root = dsu.find(ids[0]);
        // Every other element must report the same root.
        for (int i = 1; i < 100; i++) {
            assertEquals(root, dsu.find(ids[i]));
        }
    }

    @Test
    void find_pathCompression_doesNotCorruptOnDeepChain() {
        // Deeply chained unions used to be the worst case for naive
        var ids = new int[1000];
        for (int i = 0; i < 1000; i++) {
            ids[i] = dsu.makeSet();
        }
        for (int i = 0; i < 999; i++) {
            dsu.union(ids[i], ids[i + 1]);
        }
        // Sanity: all in one component.
        assertEquals(1, dsu.componentCount());
        // Random queries all consistent.
        int root = dsu.find(ids[500]);
        assertEquals(root, dsu.find(ids[0]));
        assertEquals(root, dsu.find(ids[999]));
        assertEquals(root, dsu.find(ids[123]));
    }

    @Test
    void scenario_twoSeparateNetworksThenMerge() {
        // Simulates: place 5 cables forming network A, place 5 cables
        // forming network B, then a cable connects them — they merge.
        int[] netA = new int[5];
        int[] netB = new int[5];
        for (int i = 0; i < 5; i++) netA[i] = dsu.makeSet();
        for (int i = 0; i < 5; i++) netB[i] = dsu.makeSet();
        // Build internal connections of A.
        for (int i = 0; i < 4; i++) dsu.union(netA[i], netA[i + 1]);
        // Build internal connections of B.
        for (int i = 0; i < 4; i++) dsu.union(netB[i], netB[i + 1]);
        // Two distinct networks.
        assertEquals(2, dsu.componentCount());
        assertFalse(dsu.connected(netA[0], netB[0]));
        // Bridge cable connecting them.
        dsu.union(netA[2], netB[3]);
        assertEquals(1, dsu.componentCount());
        assertTrue(dsu.connected(netA[0], netB[4]));
    }
}
