package dev.chunkfinder.core;

/** Nine metadata checks at most; never asks for blocks or requests chunk loading. */
public final class TerrainBoundary {
    @FunctionalInterface public interface Loaded { boolean at(int chunkX, int chunkZ); }
    public enum Status { AVAILABLE, EDGE, UNAVAILABLE }
    private TerrainBoundary() { }
    public static Status check(int blockX, int blockZ, Loaded loaded) {
        int x = blockX >> 4, z = blockZ >> 4;
        if (!loaded.at(x, z)) return Status.UNAVAILABLE;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if ((dx != 0 || dz != 0) && !loaded.at(x + dx, z + dz)) return Status.EDGE;
        return Status.AVAILABLE;
    }
}
