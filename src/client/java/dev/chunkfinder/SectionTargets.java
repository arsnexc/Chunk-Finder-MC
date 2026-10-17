package dev.chunkfinder;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.function.Predicate;

/** Palette absence is a safe negative; maybeHas=true is never treated as proof of a match. */
final class SectionTargets {
    private SectionTargets() { }
    static boolean excludes(ClientLevel level,int chunkX,int chunkZ,int y,Predicate<BlockState> targets) {
        if ((y & 15) != 0 || y < level.getMinY() || (long)y + 16 > ScanHeight.maxYExclusive(level)) return false;
        var chunk=level.getChunkSource().getChunk(chunkX,chunkZ,ChunkStatus.FULL,false);
        if(chunk==null)return false;
        var section=chunk.getSection(level.getSectionIndex(y));
        return section.hasOnlyAir() || !section.maybeHas(targets);
    }
}
