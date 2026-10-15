package dev.chunkfinder;

import dev.chunkfinder.core.ExposedFaces;
import dev.chunkfinder.core.LoadedBlockScanner;
import dev.chunkfinder.core.TargetPolicies;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.Map;
import java.util.HashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.function.Predicate;

/** Loaded-only registry match with optional exposure filtering and accounted block-state reads. */
class BlockTargetProbe implements LoadedBlockScanner.World, ExposedFaces.World {
    private final BlockGetter blocks;
    private final Predicate<BlockPos> available;
    private final Map<Block,String> targets;
    private final TargetPolicies.Snapshot policies;
    private String lastTarget="";
    private final boolean exposedOnly;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private int reads;
    private final SharedBlockReads shared;

    BlockTargetProbe(BlockGetter blocks, Predicate<BlockPos> available, Set<Block> targets, boolean exposedOnly) {
        this(blocks, available, targets, exposedOnly, null);
    }
    BlockTargetProbe(BlockGetter blocks, Predicate<BlockPos> available, Set<Block> targets, boolean exposedOnly, SharedBlockReads shared) {
        this(blocks,available,ids(targets),exposedOnly,shared,TargetPolicies.DEFAULT);
    }
    BlockTargetProbe(BlockGetter blocks, Predicate<BlockPos> available, Map<Block,String> targets, boolean exposedOnly, SharedBlockReads shared, TargetPolicies.Snapshot policies) {
        this.blocks=blocks;this.available=available;this.targets=targets;this.exposedOnly=exposedOnly;this.shared=shared;this.policies=policies;
    }
    private static Map<Block,String> ids(Set<Block> blocks) { Map<Block,String> result=new HashMap<>();for(Block block:blocks)result.put(block,BuiltInRegistries.BLOCK.getKey(block).toString());return Map.copyOf(result); }
    @Override public String lastMatchTarget(){return lastTarget;}
    @Override public TargetPolicies.Snapshot policies(){return policies;}
    @Override public int minY() { return blocks.getMinY(); }
    @Override public int maxY() { return ScanHeight.maxYExclusive(blocks); }
    @Override public boolean loaded(int x, int z) { return available.test(cursor.set(x * 16, minY(), z * 16)); }
    @Override public boolean matches(int x, int y, int z) {
        reads = 0;lastTarget="";
        if (y < minY() || y >= maxY() || !loaded(x >> 4, z >> 4)) return false;
        String id=targets.get(read(x,y,z).getBlock());if(id==null)return false;lastTarget=id;
        return !exposedOnly || ExposedFaces.test(this, x, y, z);
    }
    @Override public boolean open(int x, int y, int z) {
        BlockState state = read(x, y, z);
        return state.isAir() || !state.getFluidState().isEmpty();
    }
    private BlockState read(int x, int y, int z) { reads++; return shared == null ? blocks.getBlockState(cursor.set(x, y, z)) : shared.read(cursor.set(x, y, z)); }
    @Override public boolean canRead(int amount) { return shared == null || shared.canRead(amount); }
    @Override public int maxMatchReads() { return exposedOnly ? 7 : 1; }
    @Override public int lastMatchReads() { return reads; }
}
