/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.network;

import java.util.Arrays;

/**
 * Disjoint Set Union (Union-Find) data structure with path compression and union by rank.
 */
public final class DisjointSetUnion {

    private int[] parent;

    private int[] rank;

    private int size;

    private int componentCount;

    public DisjointSetUnion(int initialCapacity) {
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException(
                    "initialCapacity must be > 0; got " + initialCapacity);
        }
        this.parent = new int[initialCapacity];
        this.rank = new int[initialCapacity];
        this.size = 0;
        this.componentCount = 0;
    }

    public DisjointSetUnion() {
        this(16);
    }

    public int makeSet() {
        if (size == parent.length) {
            // Grow by 2x, the same strategy as ArrayList, amortized O(1).
            int newCapacity = parent.length * 2;
            parent = Arrays.copyOf(parent, newCapacity);
            rank = Arrays.copyOf(rank, newCapacity);
        }
        int id = size;
        parent[id] = id;     // self-parent = root
        rank[id] = 0;
        size++;
        componentCount++;
        return id;
    }

    public int find(int x) {
        validate(x);
        // Pass 1: walk up to the root.
        int root = x;
        while (parent[root] != root) {
            root = parent[root];
        }
        // Pass 2: walk again, rewiring every node to point directly at root.
        int curr = x;
        while (parent[curr] != root) {
            int next = parent[curr];
            parent[curr] = root;
            curr = next;
        }
        return root;
    }

    public boolean union(int a, int b) {
        int rootA = find(a);
        int rootB = find(b);
        if (rootA == rootB) {
            return false; // Already in the same set.
        }
        // Attach the shallower tree under the deeper one.
        if (rank[rootA] < rank[rootB]) {
            parent[rootA] = rootB;
        } else if (rank[rootA] > rank[rootB]) {
            parent[rootB] = rootA;
        } else {
            // Equal ranks: arbitrary choice, but increment the survivor's rank.
            parent[rootB] = rootA;
            rank[rootA]++;
        }
        componentCount--;
        return true;
    }

    public boolean connected(int a, int b) {
        return find(a) == find(b);
    }

    public int size() {
        return size;
    }

    public int componentCount() {
        return componentCount;
    }

    public void clear() {
        size = 0;
        componentCount = 0;
    }

    private void validate(int x) {
        if (x < 0 || x >= size) {
            throw new IndexOutOfBoundsException(
                    "Element id " + x + " out of range [0, " + size + ")");
        }
    }
}
