package dev.chunkfinder;

import dev.chunkfinder.core.TickReadCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Shared only by this mod's block ESP modules; never loads chunks or caches across ticks. */
public final class SharedBlockReads {
    public static final int MAX_ENTRIES=32768, MAX_READS=40960;
    private final TickReadCache<Long,BlockState> cache=new TickReadCache<>(MAX_ENTRIES,MAX_READS);
    private ClientLevel level;
    private long tick;
    public void begin(ClientLevel level){this.level=level;cache.begin(level,++tick);}
    public boolean canRead(int amount){return cache.canRead(amount);}
    public BlockState read(BlockPos pos){
        if(level==null || !level.hasChunkAt(pos))throw new IllegalStateException("Attempted an unloaded shared block read");
        long key=pos.asLong();
        return cache.get(key,()->level.getBlockState(pos));
    }
    public void changed(BlockPos pos){cache.invalidate(pos.asLong());}
    public void invalidateAll(){cache.invalidateAll();}
    public TickReadCache.Stats stats(){return cache.stats();}
}
