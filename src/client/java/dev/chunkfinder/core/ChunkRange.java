package dev.chunkfinder.core;

/** Shared inclusive square chunk radius, safe for negative/extreme coordinates. */
public final class ChunkRange {
    private ChunkRange() { }

    public static boolean contains(int chunkX, int chunkZ, int centerX, int centerZ, int radius) {
        return radius >= 0 && Math.abs((long) chunkX - centerX) <= radius
                && Math.abs((long) chunkZ - centerZ) <= radius;
    }
}
