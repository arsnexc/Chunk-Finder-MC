package dev.chunkfinder.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Budgeted client-loaded scan state, independent of Minecraft so lifecycle/budget bugs are testable. */
public final class LoadedBlockScanner {
    public interface World {
        int minY();
        int maxY(); // exclusive
        boolean loaded(int chunkX, int chunkZ);
        boolean matches(int x, int y, int z);
        /** Maximum/actual state reads in matches(), including optional neighbor checks. */
        default int maxMatchReads() { return 1; }
        default int lastMatchReads() { return 1; }
        default boolean emptySection(int chunkX, int chunkZ, int y) { return false; }
    }
    public record Point(int x, int y, int z) {
        public double distanceSquared(double px, double py, double pz) {
            double dx = x + 0.5 - px, dy = y + 0.5 - py, dz = z + 0.5 - pz;
            return dx * dx + dy * dy + dz * dz;
        }
    }
    private record Chunk(int x, int z) { }
    private record Result(List<Point> points, long scannedAt) { }
    private static final int DISCOVERY_TICKS = 40;
    private static final int REFRESH_TICKS = 200;
    public static final int MATCHES_PER_CHUNK = 128;
    private final int budget;
    private final Map<Chunk, Result> results = new HashMap<>();
    private final ArrayDeque<Chunk> queue = new ArrayDeque<>();
    private final ArrayDeque<Chunk> validation = new ArrayDeque<>();
    private final Set<Chunk> validationQueued = new HashSet<>();
    private Object worldIdentity;
    private Scan active;
    private long tick;
    private long nextDiscovery;
    private int centerX = Integer.MIN_VALUE, centerZ = Integer.MIN_VALUE, radius = -1;
    private int loaded;
    private List<Point> snapshot = List.of();
    private boolean snapshotDirty;

    public LoadedBlockScanner(int budget) {
        if (budget < 1) throw new IllegalArgumentException("Scan budget must be positive");
        this.budget = budget;
    }

    public void clear() {
        results.clear(); queue.clear(); validation.clear(); validationQueued.clear();
        active = null; worldIdentity = null; nextDiscovery = 0; loaded = 0;
        centerX = centerZ = Integer.MIN_VALUE; radius = -1;
        snapshot = List.of();
        snapshotDirty = false;
    }

    public void tick(Object identity, World world, int playerX, int playerY, int playerZ, int requestedRadius) {
        if (identity == null) { clear(); return; }
        if (identity != worldIdentity) { clear(); worldIdentity = identity; }
        tick++;
        int cx = playerX >> 4, cz = playerZ >> 4, r = Math.max(0, Math.min(12, requestedRadius));
        if (world.maxY() <= world.minY()) { clear(); return; }
        if (cx != centerX || cz != centerZ || r != radius || tick >= nextDiscovery)
            discover(world, cx, cz, r);
        int maxReads = world.maxMatchReads();
        if (maxReads < 1 || maxReads > budget) throw new IllegalArgumentException("Invalid match read budget");
        validateOneChunk(world);
        int remaining = budget;
        while (remaining > 0) {
            if (active == null) {
                Chunk next = queue.pollFirst();
                if (next == null) break;
                if (!world.loaded(next.x, next.z) || !inRadius(next, cx, cz, r)) { remove(next); continue; }
                active = new Scan(next, world.minY(), world.maxY(), playerX, playerY, playerZ);
            }
            Scan scan = active;
            // A chunk can unload between slices. Never continue reading or publish a partial stale scan.
            if (!world.loaded(scan.chunk.x, scan.chunk.z) || !inRadius(scan.chunk, cx, cz, r)) {
                remove(scan.chunk); active = null; continue;
            }
            // Packet-driven chunk changes are applied on the client thread between ticks.
            // Check availability once for this slice, not once for every block in it.
            while (remaining > 0 && scan.cursor < scan.total) {
                int y = scan.minY + scan.cursor / 256;
                if (scan.cursor % 256 == 0 && (y & 15) == 0 && y + 16 <= scan.maxY
                        && world.emptySection(scan.chunk.x, scan.chunk.z, y)) {
                    scan.cursor += 4096;
                    remaining--; // Empty-section skips are bounded too.
                } else {
                    // Reserve the worst case, then charge actual work. Non-target
                    // blocks still cost one read even when exposure filtering is on.
                    if (remaining < maxReads) { remaining = 0; break; }
                    int local = scan.cursor % 256;
                    int x = scan.chunk.x * 16 + local / 16, z = scan.chunk.z * 16 + local % 16;
                    if (world.matches(x, y, z)) {
                        double distance = square(x + 0.5 - scan.originX) + square(y + 0.5 - scan.originY)
                                + square(z + 0.5 - scan.originZ);
                        if (scan.found.accepts(distance)) scan.found.offer(new Point(x, y, z), distance);
                    }
                    int reads = world.lastMatchReads();
                    if (reads < 0 || reads > maxReads) throw new IllegalStateException("Match exceeded declared read budget");
                    scan.cursor++; remaining -= Math.max(1, reads);
                }
            }
            if (scan.cursor >= scan.total) {
                results.put(scan.chunk, new Result(scan.found.values(), tick));
                if (validationQueued.add(scan.chunk)) validation.addLast(scan.chunk);
                active = null;
                snapshotDirty = true;
            }
        }
        publish();
        // Do not advance nextDiscovery here: doing so every idle tick starves chunk discovery forever.
    }

    private void discover(World world, int cx, int cz, int r) {
        boolean removed = results.keySet().removeIf(c -> !inRadius(c, cx, cz, r) || !world.loaded(c.x, c.z));
        validation.removeIf(c -> !results.containsKey(c));
        validationQueued.retainAll(results.keySet());
        if (removed) snapshotDirty = true;
        if (active != null && (!inRadius(active.chunk, cx, cz, r) || !world.loaded(active.chunk.x, active.chunk.z))) active = null;
        List<Chunk> candidates = new ArrayList<>();
        loaded = 0;
        for (int x = cx - r; x <= cx + r; x++) for (int z = cz - r; z <= cz + r; z++) {
            if (!world.loaded(x, z)) continue;
            loaded++;
            Chunk chunk = new Chunk(x, z);
            if (active != null && active.chunk.equals(chunk)) continue;
            Result cached = results.get(chunk);
            if (cached == null || tick - cached.scannedAt >= REFRESH_TICKS) candidates.add(chunk);
        }
        // Unseen chunks first. Then oldest cached chunks, so background refresh cannot starve distant ones.
        candidates.sort(Comparator.<Chunk>comparingLong(c -> results.containsKey(c) ? results.get(c).scannedAt : Long.MIN_VALUE)
                .thenComparingDouble(c -> square(c.x - cx) + square(c.z - cz)));
        queue.clear(); queue.addAll(candidates);
        centerX = cx; centerZ = cz; radius = r;
        nextDiscovery = tick + DISCOVERY_TICKS;
    }

    private void validateOneChunk(World world) {
        Chunk chunk = validation.pollFirst();
        if (chunk == null) return;
        validationQueued.remove(chunk);
        Result result = results.get(chunk);
        if (result == null) return;
        if (!world.loaded(chunk.x, chunk.z)) { remove(chunk); return; }
        List<Point> live = result.points.stream().filter(p -> world.matches(p.x, p.y, p.z)).toList();
        if (live.size() != result.points.size()) {
            results.put(chunk, new Result(live, result.scannedAt));
            snapshotDirty = true;
        }
        validation.addLast(chunk); validationQueued.add(chunk);
    }

    private void remove(Chunk chunk) { if (results.remove(chunk) != null) snapshotDirty = true; }
    private void publish() {
        if (!snapshotDirty) return;
        // Many short/empty chunks can finish inside one budget. Flatten their cache
        // only once at the end of the scan tick, not after each completion.
        snapshot = results.values().stream().flatMap(r -> r.points.stream()).toList();
        snapshotDirty = false;
    }
    private static boolean inRadius(Chunk c, int x, int z, int r) {
        return ChunkRange.contains(c.x, c.z, x, z, r);
    }
    private static double square(double n) { return n * n; }
    public List<Point> points() { return snapshot; }
    public List<Point> nearest(double x, double y, double z, int limit) {
        return Nearest.select(snapshot, limit, p -> p.distanceSquared(x, y, z));
    }
    /**
     * Revalidate at most {@code limit} candidates, once per module tick, not per frame.
     * Evict invalid candidates so they cannot monopolize the next tick's render slots.
     * Do not refill by probing arbitrarily many blocks in this pass: the read cap is strict.
     */
    public List<Point> nearestMatching(World world, double x, double y, double z, int limit) {
        List<Point> live = new ArrayList<>();
        Map<Chunk, Set<Point>> invalid = new HashMap<>();
        Set<Chunk> unloaded = new HashSet<>();
        for (Point point : nearest(x, y, z, limit)) {
            Chunk chunk = new Chunk(point.x >> 4, point.z >> 4);
            if (!world.loaded(chunk.x, chunk.z)) unloaded.add(chunk);
            else if (world.matches(point.x, point.y, point.z)) live.add(point);
            else invalid.computeIfAbsent(chunk, ignored -> new HashSet<>()).add(point);
        }
        boolean changed = results.keySet().removeAll(unloaded);
        if (!unloaded.isEmpty()) {
            queue.removeIf(unloaded::contains);
            validation.removeIf(unloaded::contains);
            validationQueued.removeAll(unloaded);
            if (active != null && unloaded.contains(active.chunk)) active = null;
        }
        for (Map.Entry<Chunk, Set<Point>> entry : invalid.entrySet()) {
            Result cached = results.get(entry.getKey());
            if (cached == null) continue;
            List<Point> kept = cached.points.stream().filter(p -> !entry.getValue().contains(p)).toList();
            if (kept.size() == cached.points.size()) continue;
            // Preserve refresh age and the empty cached chunk: mining is not a request
            // for an immediate full scan, nor a reason to postpone scheduled refresh.
            results.put(entry.getKey(), new Result(kept, cached.scannedAt));
            changed = true;
        }
        if (changed) { snapshotDirty = true; publish(); }
        return List.copyOf(live);
    }
    public int maxBlockReadsPerTick() { return budget + MATCHES_PER_CHUNK; }
    public int maxBlockReadsPerTick(World world) { return budget + MATCHES_PER_CHUNK * world.maxMatchReads(); }
    public int loadedCount() { return loaded; }
    public int scannedCount() { return results.size(); }
    public int pendingCount() { return queue.size() + (active == null ? 0 : 1); }
    public boolean belongsTo(Object identity) { return identity != null && identity == worldIdentity; }

    private static final class Scan {
        final Chunk chunk;
        final int minY, maxY, total;
        final double originX, originY, originZ;
        final Nearest<Point> found = new Nearest<>(MATCHES_PER_CHUNK);
        int cursor;
        Scan(Chunk chunk, int minY, int maxY, int x, int y, int z) {
            this.chunk = chunk; this.minY = minY; this.maxY = maxY;
            total = 256 * (maxY - minY);
            originX = x + 0.5; originY = y + 0.5; originZ = z + 0.5;
        }
    }
}
