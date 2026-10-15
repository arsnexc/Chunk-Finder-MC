package dev.chunkfinder;

import net.minecraft.world.level.LevelHeightAccessor;

/** Minecraft's getMaxY() is inclusive; scanner cursors use an exclusive upper bound. */
final class ScanHeight {
    private ScanHeight() { }

    static int maxYExclusive(LevelHeightAccessor level) {
        return level.getMinY() + level.getHeight();
    }

    static int blocksPerChunk(LevelHeightAccessor level) {
        return Math.multiplyExact(16 * 16, Math.max(0, level.getHeight()));
    }
}
