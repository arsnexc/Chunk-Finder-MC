package dev.chunkfinder.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Conservative spatial evidence, not a claim that a server block was placed by a player. */
public final class BaseClues {
    public enum Family { STORAGE, WORK, LIGHT, TRANSPORT, SPECIAL }
    public record Cluster(double x, double y, double z, int score, int blocks, int families,
                          int minX, int minY, int minZ, int maxX, int maxY, int maxZ) { }
    private record Cell(int x, int y, int z) { }
    private final Map<Cell, Bucket> windows = new HashMap<>();

    public void observe(int x, int y, int z, Family family, int weight, boolean strong) {
        if (family == null || weight <= 0) return;
        // Overlapping 16-cube windows at stride 8 avoid splitting a small setup
        // at a cell boundary, without merging unrelated structures across a chunk's height.
        for (int dx = -1; dx <= 0; dx++) for (int dy = -1; dy <= 0; dy++) for (int dz = -1; dz <= 0; dz++)
            windows.computeIfAbsent(new Cell((x >> 3) + dx, (y >> 3) + dy, (z >> 3) + dz), ignored -> new Bucket())
                    .add(x, y, z, family, weight, strong);
    }
    public List<Cluster> results(int sensitivity, boolean strict) {
        int threshold = Math.max(8, 30 - Math.max(1, Math.min(10, sensitivity)) * 2);
        List<Cluster> candidates = new ArrayList<>();
        for (Bucket b : windows.values()) {
            if (b.score < threshold || b.count < 3 || Integer.bitCount(b.families) < 2) continue;
            if (strict && b.strong == 0) continue;
            candidates.add(b.snapshot());
        }
        candidates.sort(Comparator.comparingInt(Cluster::score).reversed()
                .thenComparingInt(Cluster::minX).thenComparingInt(Cluster::minY).thenComparingInt(Cluster::minZ));
        List<Cluster> result = new ArrayList<>();
        for (Cluster c : candidates) {
            boolean overlaps = result.stream().anyMatch(old -> c.minX <= old.maxX && c.maxX >= old.minX
                    && c.minY <= old.maxY && c.maxY >= old.minY && c.minZ <= old.maxZ && c.maxZ >= old.minZ);
            if (!overlaps) result.add(c);
            if (result.size() == 4) break;
        }
        return List.copyOf(result);
    }
    private static final class Bucket {
        int score, count, families, strong;
        long x, y, z;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        void add(int px, int py, int pz, Family family, int weight, boolean important) {
            score += weight; count++; families |= 1 << family.ordinal(); if (important) strong++;
            x += px; y += py; z += pz;
            minX = Math.min(minX, px); minY = Math.min(minY, py); minZ = Math.min(minZ, pz);
            maxX = Math.max(maxX, px); maxY = Math.max(maxY, py); maxZ = Math.max(maxZ, pz);
        }
        Cluster snapshot() {
            return new Cluster((double) x / count + .5, (double) y / count + .5, (double) z / count + .5,
                    score, count, Integer.bitCount(families), minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}
