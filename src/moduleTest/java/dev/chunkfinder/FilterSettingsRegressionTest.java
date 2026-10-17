package dev.chunkfinder;

import dev.chunkfinder.core.ExposedFaces;
import dev.chunkfinder.core.LoadedBlockScanner;
import dev.chunkfinder.settings.BlockEspConfig;
import dev.chunkfinder.settings.FreecamConfig;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

final class FilterSettingsRegressionTest {
    private static int assertions;
    static int run() throws Exception {
        assertions = 0; exposure(); budgets(); settings(); return assertions;
    }
    private static void exposure() {
        class World implements ExposedFaces.World {
            final Set<String> air = new HashSet<>(); int reads;
            @Override public int minY() { return -16; }
            @Override public int maxY() { return 16; }
            @Override public boolean loaded(int x, int z) { return x == 0 && z == 0; }
            @Override public boolean open(int x, int y, int z) {
                if (!loaded(x >> 4, z >> 4) || y < minY() || y >= maxY()) throw new AssertionError("Unknown neighbor read");
                reads++; return air.contains(x + "," + y + "," + z);
            }
        }
        World world = new World();
        check(!ExposedFaces.test(world, 8, 0, 8), "fully buried targets are suppressed");
        check(world.reads == 6, "buried exposure has a fixed six-neighbor bound");
        world.air.add("9,0,8"); world.reads = 0;
        check(ExposedFaces.test(world, 8, 0, 8) && world.reads == 1, "first open face short-circuits");
        world.air.clear(); world.air.add("16,0,8"); world.reads = 0;
        check(!ExposedFaces.test(world, 15, 0, 8), "unloaded neighbor is not evidence of exposure");
        check(world.reads == 5, "unloaded face is not read");
        world.air.clear(); world.air.add("8,16,8"); world.reads = 0;
        check(!ExposedFaces.test(world, 8, 15, 8) && world.reads == 5, "world ceiling is unknown, not an air face");
        world.air.clear(); world.air.add("8,-17,8"); world.reads = 0;
        check(!ExposedFaces.test(world, 8, -16, 8) && world.reads == 5, "negative world floor cannot be queried");
    }
    private static void budgets() {
        class CostWorld implements LoadedBlockScanner.World {
            int reads, last;
            @Override public int minY() { return 0; }
            @Override public int maxY() { return 1; }
            @Override public boolean loaded(int x, int z) { return x == 0 && z == 0; }
            @Override public int maxMatchReads() { return 7; }
            @Override public int lastMatchReads() { return last; }
            @Override public boolean matches(int x, int y, int z) {
                last = (x + z) % 3 == 0 ? 7 : 1; reads += last;
                return x == 8 && z == 8;
            }
        }
        CostWorld world = new CostWorld(); LoadedBlockScanner scan = new LoadedBlockScanner(16);
        Object identity = new Object();
        for (int tick = 0; tick < 150; tick++) {
            int before = world.reads;
            scan.tick(identity, world, 8, 0, 8, 0);
            check(world.reads - before <= scan.maxBlockReadsPerTick(world), "weighted matching respects scan plus cached-validation read bounds");
            // Until its first publication there cannot be any cache validation reads.
            if (scan.scannedCount() == 0) check(world.reads - before <= 16, "neighbor reads are charged to the scan slice");
        }
        check(scan.scannedCount() == 1 && scan.points().size() == 1, "weighted scan makes progress and preserves results");
        check(scan.maxBlockReadsPerTick(world) == 16 + 128 * 7, "reported validation bound includes neighbor probes");
        try {
            new LoadedBlockScanner(1).tick(identity, world, 0, 0, 0, 0);
            throw new AssertionError("impossible match budget was silently starved");
        } catch (IllegalArgumentException expected) { assertions++; }
    }
    private static void settings() throws Exception {
        Path parent = Path.of("build/module-regression"); Files.createDirectories(parent);
        Path directory = Files.createTempDirectory(parent, "filter-settings-");
        try {
            Path freecam = directory.resolve("chunkfinder-freecam.json");
            Files.writeString(freecam, "{\"range\":8,\"option\":true,\"unrelated\":12}");
            FreecamConfig config = new FreecamConfig(freecam);
            check(config.speed() == 8 && !config.swapStrafe(), "legacy generic option must not silently reverse A/D");
            check(config.fullTerrain(), "full loaded-terrain visibility defaults on");
            check(config.cutaway(), "inside-block view defaults on without rewriting an old config");
            config.fullTerrain(false);
            config.swapStrafe(true);
            FreecamConfig saved = new FreecamConfig(freecam);
            check(!saved.fullTerrain() && saved.cutaway(), "terrain visibility preference survives reload and does not disable inside-block view");
            saved.cutaway(false); check(!new FreecamConfig(freecam).cutaway(), "inside-block view preference persists"); saved.cutaway(true);
            check(saved.swapStrafe() && saved.speed() == 8, "strafe preference persists independently of speed");
            check(JsonParser.parseString(Files.readString(freecam)).getAsJsonObject().get("unrelated").getAsInt() == 12, "freecam retains unrelated keys");
            saved.speed(99); check(saved.speed() == 10, "freecam speed bounds retained");
            saved.speed(-1); check(saved.speed() == 1, "freecam minimum speed retained");

            Path debris = directory.resolve("ancient_debris_finder.json");
            Files.writeString(debris, "{\"enabled\":true,\"searchRadius\":9,\"traces\":false,\"other\":17}");
            BlockEspConfig filtered = new BlockEspConfig(debris, Set.of("minecraft:ancient_debris"), true);
            check(filtered.exposedOnly(), "old debris config gets the conservative exposed-only default");
            check(filtered.enabled() && filtered.radius() == 9 && !filtered.traces(), "existing debris scalar settings preserved");
            filtered.exposedOnly(false);
            BlockEspConfig raw = new BlockEspConfig(debris, Set.of("minecraft:ancient_debris"), true);
            check(!raw.exposedOnly(), "explicit raw mode survives reload instead of being overwritten by the default");
            check(raw.targets().equals(Set.of("minecraft:ancient_debris")), "filter setting does not replace selected IDs");
            check(JsonParser.parseString(Files.readString(debris)).getAsJsonObject().get("other").getAsInt() == 17, "debris save retains unrelated keys");
            check(!new BlockEspConfig(directory.resolve("blocks.json"), Set.of("minecraft:stone")).exposedOnly(), "other ESPs retain their old all-targets default");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
