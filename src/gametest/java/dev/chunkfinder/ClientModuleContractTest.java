package dev.chunkfinder;

import dev.chunkfinder.core.LoadedBlockScanner;
import dev.chunkfinder.core.LoadedBlockScanner.Point;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.level.LevelHeightAccessor;

import java.util.Set;

/** Checks actual Minecraft height and trajectory contracts with synthetic block access, without opening a world/server. */
public final class ClientModuleContractTest implements FabricClientGameTest {
    private int assertions;

    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            // minY, height, expected EXCLUSIVE maxY. Include negative and single-layer ranges.
            for (int[] bounds : new int[][]{{-64, 384, 320}, {0, 256, 256}, {-48, 16, -32}, {17, 1, 18}}) {
                LevelHeightAccessor height = LevelHeightAccessor.create(bounds[0], bounds[1]);
                check(height.getMaxY() == bounds[2] - 1, "Minecraft getMaxY is inclusive");
                check(ScanHeight.maxYExclusive(height) == bounds[2], "block ESP adapter includes the top build layer");
                check(ScanHeight.blocksPerChunk(height) == 256 * bounds[1], "Chunk Finder counts every build layer");

                Point bottom = new Point(0, bounds[0], 0), top = new Point(15, bounds[2] - 1, 15);
                Set<Point> targets = Set.of(bottom, top);
                int[] reads = {0};
                LoadedBlockScanner.World world = new LoadedBlockScanner.World() {
                    @Override public int minY() { return height.getMinY(); }
                    @Override public int maxY() { return ScanHeight.maxYExclusive(height); }
                    @Override public boolean loaded(int x, int z) { return x == 0 && z == 0; }
                    @Override public boolean matches(int x, int y, int z) {
                        if (!loaded(x >> 4, z >> 4) || !height.isInsideBuildHeight(y))
                            throw new AssertionError("Scanner read outside native height/loaded-chunk bounds");
                        reads[0]++;
                        return targets.contains(new Point(x, y, z));
                    }
                };
                int budget = 4096;
                LoadedBlockScanner scanner = new LoadedBlockScanner(budget);
                Object dimension = new Object();
                int ticks = (256 * bounds[1] + budget - 1) / budget;
                for (int tick = 0; tick < ticks; tick++) {
                    int before = reads[0];
                    scanner.tick(dimension, world, 8, bounds[0], 8, 0);
                    if (reads[0] - before > budget) throw new AssertionError("Native-height scan exceeded its slice budget");
                }
                check(Set.copyOf(scanner.points()).equals(targets), "bottom and top layer targets survive the real height adapter");
                check(reads[0] == 256 * bounds[1], "one full chunk scan visits each native build layer exactly once");
            }

            LevelHeightAccessor height = LevelHeightAccessor.create(-64, 384);
            int[] skippedSections = {0};
            LoadedBlockScanner.World air = new LoadedBlockScanner.World() {
                @Override public int minY() { return height.getMinY(); }
                @Override public int maxY() { return ScanHeight.maxYExclusive(height); }
                @Override public boolean loaded(int x, int z) { return x == 0 && z == 0; }
                @Override public boolean matches(int x, int y, int z) {
                    throw new AssertionError("Full-height empty chunk should not require individual block reads");
                }
                @Override public boolean emptySection(int x, int z, int y) {
                    if (!loaded(x, z) || !height.isInsideBuildHeight(y) || !height.isInsideBuildHeight(y + 15))
                        throw new AssertionError("Empty-section skip is outside the native build height");
                    skippedSections[0]++;
                    return true;
                }
            };
            LoadedBlockScanner scanner = new LoadedBlockScanner(1);
            Object dimension = new Object();
            for (int section = 0; section < 24; section++) scanner.tick(dimension, air, 0, 0, 0, 0);
            check(skippedSections[0] == 24, "every full-height section, including the top one, can use the empty skip");
            check(scanner.scannedCount() == 1 && scanner.points().isEmpty(), "empty chunk completes in 24 bounded section operations");
            assertions += ClientTrajectoryContractChecks.run();
        });
        System.out.println("Minecraft module contract tests passed (" + assertions + " assertions).");
        System.out.println("Actual Minecraft height/traversal/collision APIs with synthetic block access; no world/server or gameplay test.");
    }

    private void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
