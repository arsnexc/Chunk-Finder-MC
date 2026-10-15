package dev.chunkfinder;

import dev.chunkfinder.core.TrajectoryPredictor;
import dev.chunkfinder.core.TrajectoryPredictor.Point;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.function.Predicate;

/** Native block-shape adapter; availability checks never read block/fluid states. */
final class TrajectoryQueries {
    private TrajectoryQueries() { }

    static TrajectoryPredictor.World view(BlockGetter blocks, Predicate<BlockPos> loaded, CollisionContext collision) {
        return new TrajectoryPredictor.World() {
            @Override public boolean loaded(Point from, Point to) { return segmentLoaded(from, to, loaded); }
            @Override public Point clip(Point from, Point to) {
                var hit = blocks.clip(new ClipContext(vector(from), vector(to),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, collision));
                return hit.getType() == HitResult.Type.BLOCK ? point(hit.getLocation()) : null;
            }
        };
    }

    static boolean segmentLoaded(Point from, Point to, Predicate<BlockPos> loaded) {
        if (!TrajectoryPredictor.canTrace(from, to)) return false;
        // Use the very same voxel traversal as clip(), including its endpoint epsilon
        // and diagonal tie-breaking. Endpoint-only checks miss intervening chunks.
        return BlockGetter.traverseBlocks(vector(from), vector(to), new LoadedChunks(loaded),
                (chunks, pos) -> chunks.visit(pos), chunks -> Boolean.TRUE);
    }

    private static final class LoadedChunks {
        private final Predicate<BlockPos> loaded;
        private boolean checked;
        private int x, z;
        LoadedChunks(Predicate<BlockPos> loaded) { this.loaded = loaded; }
        Boolean visit(BlockPos pos) {
            int nextX = pos.getX() >> 4, nextZ = pos.getZ() >> 4;
            if (checked && x == nextX && z == nextZ) return null;
            x = nextX; z = nextZ; checked = true;
            // Availability is stable during a client-thread prediction. Reuse a
            // chunk's result across its voxels, particularly long vertical rays.
            return loaded.test(pos) ? null : Boolean.FALSE;
        }
    }

    static Point point(Vec3 vector) { return new Point(vector.x, vector.y, vector.z); }
    static Vec3 vector(Point point) { return new Vec3(point.x(), point.y(), point.z()); }
}
