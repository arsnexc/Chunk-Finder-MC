package dev.chunkfinder;

import dev.chunkfinder.core.TrajectoryPredictor;
import dev.chunkfinder.core.TrajectoryPredictor.Point;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Real Minecraft traversal/shapes with synthetic data; no ClientLevel, world or server is created. */
final class ClientTrajectoryContractChecks {
    private static int assertions;
    private record Chunk(int x, int z) { }

    static int run() {
        assertions = 0;
        Point from = new Point(0.5, 64.5, 0.5), to = new Point(32.5, 64.5, 0.5);
        Fixture gap = new Fixture(false, new Chunk(0, 0), new Chunk(2, 0));
        check(!TrajectoryQueries.segmentLoaded(from, to, gap::loaded), "native preflight must reject an unloaded intermediate chunk even when both endpoints are loaded");
        check(gap.blockReads == 0 && gap.fluidReads == 0, "preflight uses only availability metadata");
        Fixture negative = new Fixture(false, new Chunk(-3, 0), new Chunk(-1, 0));
        check(!TrajectoryQueries.segmentLoaded(new Point(-32.5, 64.5, 0.5), new Point(-0.5, 64.5, 0.5), negative::loaded), "negative coordinates cannot skip the intermediate chunk");
        Fixture corner = new Fixture(false, new Chunk(0, 0), new Chunk(1, 1));
        check(!TrajectoryQueries.segmentLoaded(new Point(15.5, 64.5, 15.5), new Point(16.5, 64.5, 16.5), corner::loaded), "diagonal corner cells traversed by Minecraft must also be loaded");

        Fixture padded = new Fixture(false, new Chunk(1, 0));
        check(!TrajectoryQueries.segmentLoaded(new Point(16, 64.5, 0.5), new Point(17, 64.5, 0.5), padded::loaded), "Minecraft's start epsilon can visit the preceding chunk");
        check(!TrajectoryQueries.segmentLoaded(new Point(17, 64.5, 0.5), new Point(16, 64.5, 0.5), padded::loaded), "Minecraft's end epsilon is checked when travelling backwards");
        Fixture complete = new Fixture(true);
        check(TrajectoryQueries.segmentLoaded(from, to, complete::loaded), "fully loaded long segment is accepted");
        check(new HashSet<>(complete.checked).equals(Set.of(new Chunk(0, 0), new Chunk(1, 0), new Chunk(2, 0))), "native traversal visits every crossed chunk");
        check(complete.checked.size() == 3, "availability is checked once per crossed chunk, not per voxel");
        Fixture vertical = new Fixture(true);
        check(TrajectoryQueries.segmentLoaded(new Point(2.5, -10.5, 2.5), new Point(2.5, 21.5, 2.5), vertical::loaded), "vertical rays across negative Y remain valid");
        check(vertical.checked.size() == 1, "vertical traversal needs one chunk-availability lookup");

        for (Point bad : List.of(new Point(33, 64.5, 0.5), new Point(Double.NaN, 0, 0), new Point(Double.POSITIVE_INFINITY, 0, 0), from)) {
            Fixture untouched = new Fixture(true);
            check(!TrajectoryQueries.segmentLoaded(from, bad, untouched::loaded), "unsafe or zero-length native traversal is rejected");
            check(untouched.checked.isEmpty(), "unsafe coordinates never reach native voxel traversal");
        }

        Fixture wall = new Fixture(true);
        wall.solid.add(new BlockPos(4, 64, 0));
        List<Point> collided = TrajectoryPredictor.predict(from, new Point(8, 0, 0), 0, 72,
                TrajectoryQueries.view(wall, wall::loaded, CollisionContext.empty()));
        check(collided.size() == 2, "actual vanilla collision shape terminates the path");
        check(collided.getLast().distanceSquared(new Point(4, 64.5, 0.5)) < 1.0e-12, "native clip endpoint is the stone face, not the proposed end");
        check(wall.blockReads > 0 && wall.fluidReads > 0, "collision test really exercised native block/fluid access");

        Fixture missing = new Fixture(false, new Chunk(0, 0), new Chunk(2, 0));
        List<Point> blocked = TrajectoryPredictor.predict(from, new Point(32, 0, 0), 0, 72,
                TrajectoryQueries.view(missing, missing::loaded, CollisionContext.empty()));
        check(blocked.isEmpty(), "native predictor does not draw across an unloaded middle chunk");
        check(missing.blockReads == 0 && missing.fluidReads == 0, "failed native preflight prevents all collision-data reads");

        Fixture air = new Fixture(true);
        List<Point> bounded = TrajectoryPredictor.predict(from, new Point(32, 0, 0), 0, Integer.MAX_VALUE,
                TrajectoryQueries.view(air, air::loaded, CollisionContext.empty()));
        check(bounded.size() > 1 && bounded.size() <= 73, "native air clipping still obeys the step cap");
        check(Math.abs(bounded.getLast().x() - from.x() - 256) < 1.0e-8, "native long-path endpoint obeys the total length budget");
        check(air.blockReads < 400 && air.fluidReads < 400, "an extreme axis-aligned request cannot become an unbounded native raycast");
        return assertions;
    }

    private static final class Fixture implements BlockGetter {
        final boolean allLoaded;
        final Set<Chunk> chunks;
        final List<Chunk> checked = new ArrayList<>();
        final Set<BlockPos> solid = new HashSet<>();
        int blockReads, fluidReads;
        Fixture(boolean allLoaded, Chunk... chunks) {
            this.allLoaded = allLoaded; this.chunks = Set.of(chunks);
        }
        private Chunk chunk(BlockPos pos) { return new Chunk(pos.getX() >> 4, pos.getZ() >> 4); }
        boolean loaded(BlockPos pos) { checked.add(chunk(pos)); return allLoaded || chunks.contains(chunk(pos)); }
        private void requireLoaded(BlockPos pos) {
            if (!allLoaded && !chunks.contains(chunk(pos))) throw new AssertionError("Native collision read an unavailable chunk: " + pos);
        }
        @Override public int getMinY() { return -64; }
        @Override public int getHeight() { return 384; }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { requireLoaded(pos); return null; }
        @Override public BlockState getBlockState(BlockPos pos) {
            requireLoaded(pos); blockReads++;
            return (solid.contains(pos) ? Blocks.STONE : Blocks.AIR).defaultBlockState();
        }
        @Override public FluidState getFluidState(BlockPos pos) {
            requireLoaded(pos); fluidReads++; return Fluids.EMPTY.defaultFluidState();
        }
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
