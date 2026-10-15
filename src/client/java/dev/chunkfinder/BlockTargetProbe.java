package dev.chunkfinder;

import dev.chunkfinder.core.ExposedFaces;
import dev.chunkfinder.core.LoadedBlockScanner;
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
    private final Set<Block> targets;
    private final boolean exposedOnly;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private int reads;
    private final SharedBlockReads shared;

    BlockTargetProbe(BlockGetter blocks, Predicate<BlockPos> available, Set<Block> targets, boolean exposedOnly) {
        this(blocks, available, targets, exposedOnly, null);
    }
    BlockTargetProbe(BlockGetter blocks, Predicate<BlockPos> available, Set<Block> targets, boolean exposedOnly, SharedBlockReads shared) {
        this.blocks = blocks; this.available = available; this.targets = targets; this.exposedOnly = exposedOnly; this.shared = shared;
    }
    @Override public int minY() { return blocks.getMinY(); }
    @Override public int maxY() { return ScanHeight.maxYExclusive(blocks); }
    @Override public boolean loaded(int x, int z) { return available.test(cursor.set(x * 16, minY(), z * 16)); }
    @Override public boolean matches(int x, int y, int z) {
        reads = 0;
        if (y < minY() || y >= maxY() || !loaded(x >> 4, z >> 4)) return false;
        if (!targets.contains(read(x, y, z).getBlock())) return false;
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
