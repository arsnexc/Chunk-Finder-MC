package dev.chunkfinder;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** ClientLevel.hasChunk/hasChunkAt always return true in 1.21.11. Never use them as residency checks. */
final class LoadedChunks {
    private LoadedChunks() { }
    static boolean at(ClientLevel level, int chunkX, int chunkZ) {
        return level != null && level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null;
    }
    static boolean at(ClientLevel level, BlockPos pos) { return at(level, pos.getX() >> 4, pos.getZ() >> 4); }
}
