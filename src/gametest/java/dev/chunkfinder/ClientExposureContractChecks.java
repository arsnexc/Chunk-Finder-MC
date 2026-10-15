package dev.chunkfinder;

import dev.chunkfinder.core.LoadedBlockScanner;
import dev.chunkfinder.core.LoadedBlockScanner.Point;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** A synthetic chunk filled with server-like debris replacements, using native block states. */
final class ClientExposureContractChecks {
    private static int assertions;
    static int run() {
        assertions = 0;
        Fixture world = new Fixture();
        BlockTargetProbe filtered = new BlockTargetProbe(world, world::loaded, Set.of(Blocks.ANCIENT_DEBRIS), true);
        BlockTargetProbe raw = new BlockTargetProbe(world, world::loaded, Set.of(Blocks.ANCIENT_DEBRIS), false);
        check(!filtered.matches(8, 8, 8) && filtered.lastMatchReads() == 7, "buried debris replacements are suppressed with at most seven reads");
        check(raw.matches(8, 8, 8) && raw.lastMatchReads() == 1, "explicit raw mode remains available");
        world.states.put(new BlockPos(9, 8, 8), Blocks.AIR.defaultBlockState());
        check(filtered.matches(8, 8, 8) && filtered.lastMatchReads() == 2, "a known air face qualifies with short-circuiting");
        world.states.clear(); world.states.put(new BlockPos(8, 8, 7), Blocks.LAVA.defaultBlockState());
        check(filtered.matches(8, 8, 8) && filtered.lastMatchReads() == 7, "a known fluid face can expose a candidate");
        world.states.clear();
        check(!filtered.matches(15, 8, 8) && filtered.lastMatchReads() == 6, "an unloaded adjacent chunk is not air and is not read");
        check(!filtered.matches(8, 15, 8) && !filtered.matches(8, 0, 8), "build-height edges do not fabricate exposed faces");
        int before = world.reads;
        check(!filtered.matches(16, 8, 8) && world.reads == before, "unloaded target itself is rejected without data reads");
        world.states.put(new BlockPos(8, 8, 8), Blocks.NETHERRACK.defaultBlockState());
        check(!filtered.matches(8, 8, 8) && filtered.lastMatchReads() == 1, "ordinary blocks skip all exposure reads");
        world.states.clear();

        Object identity = new Object(); LoadedBlockScanner scanner = new LoadedBlockScanner(64);
        finish(scanner, identity, filtered, world);
        check(scanner.points().isEmpty(), "a whole solid chunk of fake debris yields no exposed markers");
        world.states.put(new BlockPos(8, 8, 8), Blocks.AIR.defaultBlockState()); scanner.clear();
        finish(scanner, identity, filtered, world);
        Set<Point> expected = Set.of(new Point(7,8,8), new Point(9,8,8), new Point(8,7,8), new Point(8,9,8), new Point(8,8,7), new Point(8,8,9));
        check(Set.copyOf(scanner.points()).equals(expected), "only the six air-adjacent client matches qualify, not every replacement");
        // Still not server verification: if fake debris is sent even beside air,
        // it is indistinguishable from real exposed debris and remains unverified.
        world.states.clear(); before = world.reads;
        check(scanner.nearestMatching(filtered, 8, 8, 8, 48).isEmpty() && scanner.points().isEmpty(), "closing exposure invalidates already-cached candidates");
        check(world.reads - before <= 48 * 7, "visible revalidation keeps its explicit neighbor-read bound");
        AncientDebrisFinderModule module = new AncientDebrisFinderModule();
        check(module.exposedOnly(), "the real debris module defaults to exposed-only filtering");
        module.setExposedOnly(false);
        check(!new AncientDebrisFinderModule().exposedOnly(), "actual debris adapter retains explicit raw mode on reload");
        module.setExposedOnly(true);
        return assertions;
    }
    private static void finish(LoadedBlockScanner scanner, Object identity, BlockTargetProbe probe, Fixture world) {
        for (int tick = 0; tick < 600 && scanner.scannedCount() == 0; tick++) {
            int before = world.reads;
            scanner.tick(identity, probe, 8, 8, 8, 0);
            // No result has been published yet, so this slice has no cached validation.
            if (world.reads - before > 64) throw new AssertionError("Exposure exceeded its accounted per-tick block-read budget");
        }
        check(scanner.scannedCount() == 1, "filtered scan completes instead of starving on expensive predicates");
    }
    private static final class Fixture implements BlockGetter {
        final Map<BlockPos, BlockState> states = new HashMap<>(); int reads;
        boolean loaded(BlockPos pos) { return (pos.getX() >> 4) == 0 && (pos.getZ() >> 4) == 0; }
        @Override public int getMinY() { return 0; }
        @Override public int getHeight() { return 16; }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public BlockState getBlockState(BlockPos pos) {
            if (!loaded(pos) || !isInsideBuildHeight(pos.getY())) throw new AssertionError("Unknown/out-of-height block was read: " + pos);
            reads++; return states.getOrDefault(pos, Blocks.ANCIENT_DEBRIS.defaultBlockState());
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
