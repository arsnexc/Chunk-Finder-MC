package dev.chunkfinder;

import com.google.gson.JsonParser;
import dev.chunkfinder.settings.BlockEspConfig;
import dev.chunkfinder.settings.OverlayConfig;
import dev.chunkfinder.settings.FreecamConfig;
import dev.chunkfinder.core.ChunkRange;
import dev.chunkfinder.core.FreecamMovement;
import dev.chunkfinder.core.FreecamMovement.Vector;
import dev.chunkfinder.core.LoadedBlockScanner;
import dev.chunkfinder.core.LoadedBlockScanner.Point;
import dev.chunkfinder.core.Nearest;
import dev.chunkfinder.core.TargetIds;
import dev.chunkfinder.core.TargetSelection;
import dev.chunkfinder.core.TargetSelection.Entry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Runs production config, selection and scan logic without creating a Minecraft client/world. */
public final class ModuleRegressionTest {
    private static int assertions;
    private static final String DIAMOND = "minecraft:diamond_ore", GOLD = "minecraft:gold_ore";

    public static void main(String[] args) throws Exception {
        ids(); selection(); nearest(); configuration(); scanner(); freecamMovement(); visibleCandidates(); chunkRange(); snapshotPublication();
        assertions += TrajectoryRegressionTest.run();
        assertions += FilterSettingsRegressionTest.run();
        assertions += PresentationRegressionTest.run();
        System.out.println("Module regression tests passed (" + assertions + " assertions).");
        System.out.println("Uses fake loaded-world access; not an in-game rendering or anti-cheat test.");
    }

    private static void ids() {
        check(TargetIds.copy(List.of("diamond_ore", " minecraft:diamond_ore ", "MINECRAFT:GOLD_ORE")).equals(Set.of(DIAMOND, GOLD)), "canonical deduplicated IDs");
        check(TargetIds.copy(List.of("bad id", "!", "a:b:c", "")).isEmpty(), "invalid IDs do not become default blocks");
        check(TargetIds.copy(Set.of("missing_mod:storage/crate")).contains("missing_mod:storage/crate"), "unknown valid IDs retained");
        check(TargetIds.copy(null).isEmpty(), "null input is empty, not match-all");
        try { TargetIds.copy(Set.of(DIAMOND)).add(GOLD); throw new AssertionError("mutable target set"); }
        catch (UnsupportedOperationException expected) { assertions++; }
    }

    private static void selection() {
        List<Entry> choices = new ArrayList<>();
        for (int i = 0; i < 150; i++) choices.add(new Entry("test:ore_" + i, "Ore " + i));
        choices.add(new Entry("minecraft:oak_log", "Oak Log"));
        AtomicReference<Set<String>> live = new AtomicReference<>(Set.of("minecraft:oak_log"));
        TargetSelection draft = new TargetSelection(choices, live.get());
        draft.query("ore"); draft.selectFiltered();
        eq(151, draft.count(), "bulk selects all filtered pages and preserves hidden targets");
        eq(1, live.get().size(), "draft does not modify live settings");
        draft.clearFiltered();
        check(draft.snapshot().equals(Set.of("minecraft:oak_log")), "clear results preserves hidden selection");
        draft.toggle("test:ore_2"); draft.toggle("test:ore_3");
        draft.apply(live::set);
        eq(3, live.get().size(), "multiple target apply");
        draft.toggle("test:ore_4"); draft.apply(live::set);
        eq(3, live.get().size(), "finished transaction cannot change or apply again");
        TargetSelection canceled = new TargetSelection(choices, live.get());
        canceled.clearAll(); canceled.cancel(); canceled.apply(live::set);
        eq(3, live.get().size(), "Cancel/escape leaves live targets unchanged");
        TargetSelection empty = new TargetSelection(choices, live.get());
        empty.clearAll(); empty.apply(live::set);
        check(live.get().isEmpty(), "empty Apply deliberately matches nothing");
        AtomicInteger writes = new AtomicInteger();
        TargetSelection same = new TargetSelection(choices, Set.of());
        same.apply(ids -> writes.incrementAndGet());
        eq(0, writes.get(), "unchanged Apply does not save or reset scanners");

        TargetSelection filtered = new TargetSelection(choices, Set.of("test:ore_1", "test:ore_10", "minecraft:oak_log"));
        filtered.selectedOnly(true); filtered.query("ore");
        eq(2, filtered.filtered().size(), "selected-only and search combine");
        filtered.toggle("test:ore_1");
        eq(1, filtered.filtered().size(), "selected-only updates after a row is deselected");
        filtered.clearFiltered();
        eq(0, filtered.filtered().size(), "clearing selected results uses a stable snapshot");
        check(filtered.snapshot().equals(Set.of("minecraft:oak_log")), "unshown selections survive clear");
        filtered.selectedOnly(false); filtered.query("ORE 149");
        eq(1, filtered.filtered().size(), "multi-word case-insensitive matching");
        check(filtered.filtered().getFirst().id().equals("test:ore_149"), "match by name and registry ID");
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TargetSelection locale = new TargetSelection(List.of(new Entry("minecraft:iron_ore", "IRON ORE")), Set.of());
            locale.query("IRON"); eq(1, locale.filtered().size(), "search independent of system locale");
        } finally { Locale.setDefault(before); }

        TargetSelection missing = new TargetSelection(choices, Set.of("missing_mod:chest"));
        eq(1, missing.unavailableCount(), "missing-mod target is visible as unavailable");
        missing.query("missing_mod"); missing.clearFiltered(); missing.selectFiltered();
        eq(0, missing.count(), "bulk select does not resurrect unavailable entries");
        missing.query("ore_2"); missing.toggle("not_in_catalog:fake");
        eq(0, missing.count(), "arbitrary IDs cannot be introduced by row input");
    }

    private static void nearest() {
        check(Nearest.select(List.of(50, 8, 2, 40, 3), 3, n -> n).equals(List.of(2, 3, 8)), "nearest-N selection independent of registry order");
        check(Nearest.select(List.of("first", "second", "third"), 2, n -> 1).equals(List.of("first", "second")), "stable distance ties");
        Nearest<Integer> bounded = new Nearest<>(24);
        for (int i = 99; i >= 0; i--) bounded.offer(i, i);
        eq(24, bounded.values().size(), "trajectory cap is exactly 24, not 25");
        eq(0, bounded.values().getFirst(), "nearest trajectory first");
        eq(23, bounded.values().getLast(), "farther trajectories excluded");
        bounded.offer(-1, Double.NaN); bounded.offer(-2, Double.POSITIVE_INFINITY);
        eq(24, bounded.values().size(), "non-finite distance ignored");
    }

    private static void freecamMovement() {
        double[][] directions = {
                {0, 0, 1, -1, 0},       // South: screen-right is West (-X).
                {90, -1, 0, 0, -1},     // West: screen-right is North (-Z).
                {180, 0, -1, 1, 0},     // North: screen-right is East (+X).
                {-90, 1, 0, 0, 1},      // East: screen-right is South (+Z).
        };
        for (double[] direction : directions) {
            Vector look = new Vector(direction[1], 0, direction[2]);
            motion(direction[3] * 0.5, 0, direction[4] * 0.5,
                    FreecamMovement.offset(direction[0], look, 0, 1, 0, 0.5), "D/right at yaw " + direction[0]);
            motion(-direction[3] * 0.5, 0, -direction[4] * 0.5,
                    FreecamMovement.offset(direction[0], look, 0, -1, 0, 0.5), "A/left at yaw " + direction[0]);
            motion(look.x() * 0.5, 0, look.z() * 0.5,
                    FreecamMovement.offset(direction[0], look, 1, 0, 0, 0.5), "forward follows native look");
            motion(-look.x() * 0.5, 0, -look.z() * 0.5,
                    FreecamMovement.offset(direction[0], look, -1, 0, 0, 0.5), "backward opposes native look");
            motion(0, 0.5, 0, FreecamMovement.offset(direction[0], look, 0, 0, 1, 0.5), "Space is world-up");
            motion(0, -0.5, 0, FreecamMovement.offset(direction[0], look, 0, 0, -1, 0.5), "Shift is world-down");
            motion(0, 0, 0, FreecamMovement.offset(direction[0], look, 0, 0, 0, 0.5), "opposed/released keys cancel");
        }
        Vector south = new Vector(0, 0, 1);
        double diagonal = 0.5 / Math.sqrt(2);
        motion(-diagonal, 0, diagonal, FreecamMovement.offset(0, south, 1, 1, 0, 0.5), "diagonals keep configured speed");
        motion(-diagonal * 2, 0, diagonal * 2, FreecamMovement.offset(0, south, 1, 1, 0, 1), "sprint doubles displacement, not diagonal boost");
        motion(0, -0.3, 0.4, FreecamMovement.offset(0, new Vector(0, -0.6, 0.8), 1, 0, 0, 0.5), "forward retains native pitch");
        motion(-0.5, 0, 0, FreecamMovement.offset(0, new Vector(0, -1, 0), 0, 1, 0, 0.5), "strafe remains horizontal at vertical pitch");
        motion(0, 0, 0, FreecamMovement.offset(0, new Vector(0, -1, 1.0e-8), 1, 0, 1, 0.5), "near-canceling vertical inputs cannot normalize into full-speed drift");
        motion(0, 0, 0, FreecamMovement.offset(Double.NaN, south, 0, 1, 0, 0.5), "invalid camera rotation is safe");
        motion(0, 0, 0, FreecamMovement.offset(0, south, 1, 0, 0, Double.POSITIVE_INFINITY), "invalid distance is safe");
        motion(0, 0, 0, FreecamMovement.offset(0, south, 1, 0, 0, -1), "negative distance cannot invert controls");
    }

    private static void motion(double x, double y, double z, Vector actual, String message) {
        check(Double.isFinite(actual.x()) && Double.isFinite(actual.y()) && Double.isFinite(actual.z())
                && Math.abs(x - actual.x()) < 1.0e-9 && Math.abs(y - actual.y()) < 1.0e-9 && Math.abs(z - actual.z()) < 1.0e-9,
                message + ": expected " + new Vector(x, y, z) + ", got " + actual);
    }

    private static void configuration() throws Exception {
        Path parent = Path.of("build/module-regression"); Files.createDirectories(parent);
        Path directory = Files.createTempDirectory(parent, "settings-");
        try {
            Path file = directory.resolve("item_esp.json");
            Files.writeString(file, "{\"enabled\":true,\"traces\":false,\"blockHighlight\":false,\"searchRadius\":9,\"blockId\":\"minecraft:emerald_ore\",\"otherSetting\":17}");
            BlockEspConfig legacy = new BlockEspConfig(file, Set.of(DIAMOND));
            check(legacy.targets().equals(Set.of("minecraft:emerald_ore")), "legacy single block migrates without selecting diamond");
            check(legacy.enabled() && !legacy.traces() && !legacy.outlines(), "existing scalar options preserved");
            eq(9, legacy.radius(), "existing radius preserved");
            legacy.targets(Set.of(DIAMOND, GOLD, "missing_mod:ore"));
            var saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            check(saved.has("blockIds") && !saved.has("blockId"), "save writes new array and removes legacy key");
            eq(17, saved.get("otherSetting").getAsInt(), "unrelated settings preserved");
            BlockEspConfig reloaded = new BlockEspConfig(file, Set.of(DIAMOND));
            check(reloaded.targets().equals(Set.of(DIAMOND, GOLD, "missing_mod:ore")), "multi-target persistence roundtrip including unknown IDs");
            reloaded.targets(Set.of());
            check(new BlockEspConfig(file, Set.of(DIAMOND)).targets().isEmpty(), "saved empty array survives restart");
            Files.writeString(file, "{\"blockIds\":[],\"blockId\":\"minecraft:diamond_ore\"}");
            check(new BlockEspConfig(file, Set.of(GOLD)).targets().isEmpty(), "explicit empty takes precedence over legacy field");
            for (String malformed : List.of("null", "{}", "\"minecraft:stone\"", "42")) {
                Files.writeString(file, "{\"blockIds\":" + malformed + "}");
                check(new BlockEspConfig(file, Set.of(DIAMOND)).targets().isEmpty(), "malformed explicit selection fails closed: " + malformed);
            }
            Files.writeString(file, "{\"blockIds\":[\"gold_ore\",\"minecraft:gold_ore\",42,null,{},\"bad id\"]}");
            check(new BlockEspConfig(file, Set.of(DIAMOND)).targets().equals(Set.of(GOLD)), "mixed arrays accept only valid string IDs");
            Files.writeString(file, "{\"searchRadius\":999,\"traces\":\"false\"}");
            BlockEspConfig bounded = new BlockEspConfig(file, Set.of(DIAMOND));
            eq(12, bounded.radius(), "loaded radius clamped"); check(bounded.traces(), "malformed boolean uses default");
            bounded.radius(-100); eq(2, bounded.radius(), "edited radius clamped");
            check(new BlockEspConfig(directory.resolve("storage_esp.json"), Set.of("minecraft:chest", "minecraft:barrel"))
                    .targets().size() == 2, "storage defaults preserved when no list exists");
            check(new BlockEspConfig(directory.resolve("spawner_esp.json"), Set.of("minecraft:spawner", "minecraft:trial_spawner"))
                    .targets().size() == 2, "both spawners enabled in default target list");

            Path entities = directory.resolve("entities.json");
            Files.writeString(entities, "{\"enabled\":true,\"option\":false,\"range\":96}");
            OverlayConfig entity = new OverlayConfig(entities, 48, 16, 128, "entityIds", Set.of("minecraft:zombie", "minecraft:player"));
            check(entity.enabled() && !entity.option(), "old include-players option retained");
            eq(96, entity.range(), "old entity range retained");
            eq(2, entity.targets().size(), "legacy entity config receives default targets");
            entity.setTargets(Set.of("minecraft:zombie", "minecraft:skeleton"));
            check(new OverlayConfig(entities, 48, 16, 128, "entityIds", Set.of()).targets().equals(entity.targets()), "entity target roundtrip");
            entity.setTargets(Set.of());
            check(new OverlayConfig(entities, 48, 16, 128, "entityIds", Set.of("minecraft:player")).targets().isEmpty(), "empty entity list never means all");
            Path items = directory.resolve("items.json");
            OverlayConfig dropped = new OverlayConfig(items, 48, 16, 128, "itemIds", Set.of("minecraft:diamond"));
            dropped.setTargets(Set.of("minecraft:diamond", "minecraft:netherite_ingot"));
            check(JsonParser.parseString(Files.readString(items)).getAsJsonObject().has("itemIds"), "dropped items persist item IDs, not entity types");
            dropped.setTargets(Set.of());
            check(new OverlayConfig(items, 48, 16, 128, "itemIds", Set.of("minecraft:diamond")).targets().isEmpty(), "empty item list survives restart");
            Path freecam = directory.resolve("freecam.json");
            FreecamConfig scalar = new FreecamConfig(freecam); scalar.speed(100);
            eq(10, scalar.speed(), "freecam scalar bounds retained");
            var scalarJson = JsonParser.parseString(Files.readString(freecam)).getAsJsonObject();
            check(!scalarJson.has("entityIds") && !scalarJson.has("itemIds"), "non-ESP overlays do not gain target lists");
            try (var stream = Files.list(directory)) { check(stream.noneMatch(p -> p.toString().endsWith(".tmp")), "atomic save leaves no temporary files"); }
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void scanner() {
        Object dimension = new Object();
        FakeWorld world = new FakeWorld(2);
        world.load(0, 0); world.load(1, 0);
        Point diamond = new Point(1, 0, 2), gold = new Point(18, 1, 3), excluded = new Point(4, 0, 4);
        world.blocks.put(diamond, DIAMOND); world.blocks.put(gold, GOLD); world.blocks.put(excluded, "minecraft:emerald_ore");
        LoadedBlockScanner scan = new LoadedBlockScanner(64);
        run(scan, dimension, world, 24, 0, 1, 0, 1);
        check(new HashSet<>(scan.points()).equals(Set.of(diamond, gold)), "block scan unions multiple targets only");
        eq(2, scan.scannedCount(), "both loaded chunks scanned");
        eq(2, scan.loadedCount(), "unloaded chunks never counted as loaded");
        run(scan, dimension, world, 10, 0, 1, 0, 1);
        world.load(-1, 0); Point discovered = new Point(-4, 1, 0); world.blocks.put(discovered, GOLD);
        run(scan, dimension, world, 60, 0, 1, 0, 1);
        check(scan.points().contains(discovered), "stationary idle scanner discovers a newly loaded chunk");
        Point placed = new Point(7, 1, 7); world.blocks.put(placed, DIAMOND);
        run(scan, dimension, world, 260, 0, 1, 0, 1);
        check(scan.points().contains(placed), "budgeted refresh finds blocks placed inside an already-scanned chunk");
        world.blocks.remove(placed);
        run(scan, dimension, world, 5, 0, 1, 0, 1);
        check(!scan.points().contains(placed), "mined blocks pruned");
        scan.clear();
        check(scan.points().isEmpty() && scan.pendingCount() == 0 && scan.scannedCount() == 0, "target change/reset clears snapshots, cache and work");
        world.targets = Set.of(GOLD);
        run(scan, dimension, world, 24, 0, 1, 0, 1);
        check(!scan.points().contains(diamond) && scan.points().contains(gold), "old target results do not return after changing selection");
        world.targets = Set.of(); scan.clear(); run(scan, dimension, world, 24, 0, 1, 0, 1);
        check(scan.points().isEmpty(), "empty selection matches nothing");
        world.targets = Set.of(DIAMOND, GOLD); scan.clear();
        run(scan, dimension, world, 24, 0, 1, 0, 1);
        scan.tick(new Object(), world, 0, 1, 0, 1);
        check(scan.points().isEmpty(), "dimension switch immediately clears old results before new slices finish");
        run(scan, dimension, world, 24, 0, 1, 0, 1);
        scan.tick(dimension, world, 2000, 1, 2000, 1);
        check(scan.points().isEmpty() && scan.loadedCount() == 0, "moving outside radius drops old chunks");

        FakeWorld unloading = new FakeWorld(16); unloading.load(0, 0); unloading.blocks.put(new Point(0, 0, 0), DIAMOND);
        LoadedBlockScanner partial = new LoadedBlockScanner(16);
        partial.tick(dimension, unloading, 0, 1, 0, 0);
        check(partial.pendingCount() > 0, "partial scan retained between slices");
        partial.clear(); unloading.targets = Set.of(GOLD);
        run(partial, dimension, unloading, 300, 0, 1, 0, 0);
        check(partial.points().isEmpty(), "target change cancels partial matches");
        partial.clear(); unloading.targets = Set.of(DIAMOND);
        partial.tick(dimension, unloading, 0, 1, 0, 0); unloading.loaded.clear();
        run(partial, dimension, unloading, 3, 0, 1, 0, 0);
        check(partial.pendingCount() == 0 && partial.scannedCount() == 0, "mid-scan unload discards partial work");

        FakeWorld dense = new FakeWorld(16); dense.load(0, 0); dense.allMatch = true;
        LoadedBlockScanner capped = new LoadedBlockScanner(256);
        run(capped, dimension, dense, 16, 8, 15, 8, 0);
        eq(128, capped.points().size(), "retained per-chunk match cap");
        check(capped.points().contains(new Point(8, 15, 8)), "cap retains nearby blocks, not first blocks at world bottom");
        List<Point> brute = new ArrayList<>();
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) brute.add(new Point(x, y, z));
        brute.sort(Comparator.comparingDouble(p -> p.distanceSquared(8.5, 15.5, 8.5)));
        double worst = brute.get(127).distanceSquared(8.5, 15.5, 8.5);
        for (Point p : capped.points()) check(p.distanceSquared(8.5, 15.5, 8.5) <= worst, "nearest per-chunk cap agrees with brute force");
        eq(48, capped.nearest(8.5, 15.5, 8.5, 48).size(), "render candidate cap");

        FakeWorld air = new FakeWorld(64); air.load(0, 0); air.skipAir = true;
        LoadedBlockScanner skip = new LoadedBlockScanner(1);
        run(skip, dimension, air, 4, 0, 1, 0, 0);
        eq(1, skip.scannedCount(), "empty sections skip with bounded operations");
        eq(0, air.reads, "empty sections do not perform individual block reads");
        FakeWorld noHeight = new FakeWorld(0); noHeight.load(0, 0);
        skip.tick(dimension, noHeight, 0, 0, 0, 0);
        eq(0, noHeight.reads, "zero-height world is safe");
        check(skip.points().isEmpty(), "zero-height world clears old results");
    }

    private static void snapshotPublication() {
        Object dimension = new Object();
        FakeWorld world = new FakeWorld(1); world.load(0, 0); world.load(1, 0);
        Point first = new Point(0, 0, 0), second = new Point(16, 0, 0);
        world.blocks.put(first, DIAMOND); world.blocks.put(second, GOLD);
        LoadedBlockScanner scan = new LoadedBlockScanner(1024);
        List<Point> before = scan.points();
        AtomicInteger earlyPublications = new AtomicInteger();
        world.onRead = () -> { if (scan.points() != before) earlyPublications.incrementAndGet(); };
        run(scan, dimension, world, 1, 0, 0, 0, 1);
        eq(0, earlyPublications.get(), "scan completions are batched instead of copying/publishing the entire cache mid-tick");
        check(new HashSet<>(scan.points()).equals(Set.of(first, second)), "all completed chunks are visible together at the end of the tick");
        check(before.isEmpty(), "old scan snapshots remain immutable");
        world.onRead = null;
        List<Point> published = scan.points();
        run(scan, dimension, world, 1, 0, 0, 0, 1);
        check(scan.points() == published, "unchanged ticks do not copy the result cache");
        scan.clear();
        check(scan.points().isEmpty(), "explicit reset still clears the published snapshot immediately");
    }

    private static void chunkRange() {
        check(ChunkRange.contains(-6, -6, 0, 0, 6), "square radius includes its negative corner");
        check(ChunkRange.contains(6, 6, 0, 0, 6), "square radius includes its positive corner");
        check(!ChunkRange.contains(-6, 0, 1, 0, 6), "one-chunk move invalidates old edge work before a larger queue rebuild");
        check(!ChunkRange.contains(0, -6, 0, 1, 6), "z-axis move invalidates old edge work");
        check(ChunkRange.contains(7, 6, 1, 0, 6), "moved positive edge remains in range");
        check(ChunkRange.contains(-1, -1, -1, -1, 0), "zero radius includes exactly its own chunk");
        check(!ChunkRange.contains(0, -1, -1, -1, 0), "zero radius excludes neighbors across zero");
        check(!ChunkRange.contains(0, 0, 0, 0, -1), "invalid negative radius contains nothing");
        check(!ChunkRange.contains(Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 0, 12), "x coordinate subtraction cannot overflow into radius");
        check(!ChunkRange.contains(0, Integer.MAX_VALUE, 0, Integer.MIN_VALUE, 12), "z coordinate subtraction cannot overflow into radius");

        Object dimension = new Object();
        FakeWorld world = new FakeWorld(16); world.load(-6, 0);
        Point edge = new Point(-96, 0, 0); world.blocks.put(edge, DIAMOND);
        LoadedBlockScanner scan = new LoadedBlockScanner(16);
        run(scan, dimension, world, 1, 0, 0, 0, 6);
        eq(1, scan.pendingCount(), "edge chunk has unfinished work");
        int reads = world.reads;
        run(scan, dimension, world, 1, 16, 0, 0, 6);
        eq(0, world.reads - reads, "out-of-radius partial work performs no further block reads");
        check(scan.points().isEmpty() && scan.pendingCount() == 0, "partial matches cannot reappear outside a moved radius");
    }

    private static void visibleCandidates() {
        Object dimension = new Object();
        FakeWorld world = new FakeWorld(1);
        world.load(0, 0); world.load(1, 0);
        Point minedA = new Point(0, 0, 0), minedB = new Point(1, 0, 0);
        Point liveA = new Point(16, 0, 0), liveB = new Point(17, 0, 0);
        for (Point point : List.of(minedA, minedB, liveA, liveB)) world.blocks.put(point, DIAMOND);
        LoadedBlockScanner scan = new LoadedBlockScanner(64);
        run(scan, dimension, world, 8, 0, 0, 0, 1);
        eq(4, scan.points().size(), "visible-candidate fixture scanned both chunks");
        List<Point> before = scan.points();
        check(scan.nearestMatching(world, 0.5, 0.5, 0.5, 2).equals(List.of(minedA, minedB)), "native adapter candidate ordering");
        check(scan.points() == before, "all-live validation does not republish an identical snapshot");
        world.blocks.remove(minedA); world.blocks.remove(minedB);
        int reads = world.reads;
        check(scan.nearestMatching(world, 0.5, 0.5, 0.5, 2).isEmpty(), "mined candidates are never returned for rendering");
        eq(2, world.reads - reads, "candidate validation cannot exceed its read budget");
        check(!scan.points().contains(minedA) && !scan.points().contains(minedB), "mined nearest candidates must leave the cache, not occupy every render slot again");
        check(before.contains(minedA) && before.contains(minedB), "pruning does not mutate previously published snapshots");
        check(scan.nearestMatching(world, 0.5, 0.5, 0.5, 2).equals(List.of(liveA, liveB)), "the next bounded validation pass reaches surviving targets");
        eq(2, scan.scannedCount(), "mining does not force immediate full-chunk rescans");

        world.loaded.remove("1,0");
        reads = world.reads;
        check(scan.nearestMatching(world, 0.5, 0.5, 0.5, 1).isEmpty(), "unloaded candidate is not rendered");
        eq(0, world.reads - reads, "candidate validation never reads an unloaded block");
        check(scan.points().isEmpty(), "an unloaded chunk loses all cached candidates, not just the nearest one");
        eq(1, scan.scannedCount(), "unloaded chunk is removed from result cache");
        world.load(1, 0); world.blocks.remove(liveA); world.blocks.remove(liveB);
        Point reloaded = new Point(18, 0, 0); world.blocks.put(reloaded, GOLD);
        run(scan, dimension, world, 50, 0, 0, 0, 1);
        check(scan.points().equals(List.of(reloaded)), "reloaded chunk is discovered again without resurrecting stale candidates");

        FakeWorld dense = new FakeWorld(1); dense.load(0, 0); dense.allMatch = true;
        LoadedBlockScanner capped = new LoadedBlockScanner(256);
        run(capped, dimension, dense, 1, 8, 0, 8, 0);
        eq(128, capped.points().size(), "dense candidate fixture reaches the per-chunk cap");
        dense.allMatch = false;
        for (int remaining : new int[]{80, 32, 0}) {
            reads = dense.reads;
            check(capped.nearestMatching(dense, 8.5, 0.5, 8.5, 48).isEmpty(), "mined dense candidates are filtered");
            check(dense.reads - reads <= 48, "pruning cannot turn a render-cap query into an unbounded scan");
            eq(remaining, capped.points().size(), "each bounded pass makes progress through invalid candidates");
        }
    }

    private static void run(LoadedBlockScanner scan, Object dimension, FakeWorld world, int ticks, int x, int y, int z, int radius) {
        for (int i = 0; i < ticks; i++) {
            int reads = world.reads;
            scan.tick(dimension, world, x, y, z, radius);
            check(world.reads - reads <= scan.maxBlockReadsPerTick(), "per-tick block reads remain bounded");
        }
    }
    private static final class FakeWorld implements LoadedBlockScanner.World {
        final int height;
        final Set<String> loaded = new HashSet<>();
        final Map<Point, String> blocks = new HashMap<>();
        Set<String> targets = Set.of(DIAMOND, GOLD);
        boolean allMatch, skipAir;
        Runnable onRead;
        int reads;
        FakeWorld(int height) { this.height = height; }
        void load(int x, int z) { loaded.add(x + "," + z); }
        @Override public int minY() { return 0; }
        @Override public int maxY() { return height; }
        @Override public boolean loaded(int x, int z) { return loaded.contains(x + "," + z); }
        @Override public boolean matches(int x, int y, int z) {
            if (!loaded(x >> 4, z >> 4)) throw new AssertionError("Queried an unloaded chunk");
            if (y < minY() || y >= maxY()) throw new AssertionError("Queried outside world height");
            reads++;
            if (onRead != null) onRead.run();
            String id = blocks.get(new Point(x, y, z));
            return allMatch || id != null && targets.contains(id);
        }
        @Override public boolean emptySection(int x, int z, int y) { return skipAir; }
    }
    private static void eq(int expected, int actual, String message) { check(expected == actual, message + ": expected " + expected + ", got " + actual); }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
