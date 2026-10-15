package dev.chunkfinder.core;

import java.util.ArrayList;
import java.util.List;

/** Approximate point trajectories. Never mutates an entity or asks a world to load data. */
public final class TrajectoryPredictor {
    public static final int MAX_STEPS = 72;
    public static final double MAX_SEGMENT_LENGTH = 32;
    public static final double MAX_PATH_LENGTH = 256;
    private static final double MAX_COORDINATE = 30_000_000;
    private static final double MIN_LENGTH_SQUARED = 1.0e-12;

    public record Point(double x, double y, double z) {
        public Point add(Point other) { return new Point(x + other.x, y + other.y, z + other.z); }
        public Point scale(double value) { return new Point(x * value, y * value, z * value); }
        public double distanceSquared(Point other) {
            double dx = x - other.x, dy = y - other.y, dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }
    public interface World {
        /** Metadata-only preflight covering the entire segment, not just its endpoint. */
        boolean loaded(Point from, Point to);
        /** First block collision, or null for a miss. Called only after a successful preflight. */
        Point clip(Point from, Point to);
    }

    private TrajectoryPredictor() { }

    /** Shared safety check for the predictor and native traversal adapter. */
    public static boolean canTrace(Point from, Point to) {
        if (!validPosition(from) || !validPosition(to)) return false;
        double distance = from.distanceSquared(to);
        return distance > MIN_LENGTH_SQUARED && distance <= MAX_SEGMENT_LENGTH * MAX_SEGMENT_LENGTH;
    }

    public static List<Point> predict(Point position, Point velocity, double gravity, int requestedSteps, World world) {
        if (!validPosition(position) || !finite(velocity) || !Double.isFinite(gravity)) return List.of();
        int steps = Math.max(0, Math.min(MAX_STEPS, requestedSteps));
        List<Point> points = new ArrayList<>(steps + 1);
        double travelled = 0;
        for (int step = 0; step < steps; step++) {
            double lengthSquared = velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z;
            if (!Double.isFinite(lengthSquared) || lengthSquared > MAX_SEGMENT_LENGTH * MAX_SEGMENT_LENGTH) break;
            if (lengthSquared <= MIN_LENGTH_SQUARED) {
                if (gravity == 0) break;
                // A future apex can have zero movement but gain vertical velocity next tick.
                velocity = velocity.scale(0.99).add(new Point(0, -gravity, 0));
                continue;
            }
            double length = Math.sqrt(lengthSquared), remaining = MAX_PATH_LENGTH - travelled;
            if (remaining <= 1.0e-6) break;
            boolean last = length >= remaining;
            Point next = position.add(last ? velocity.scale(remaining / length) : velocity);
            if (!canTrace(position, next) || !world.loaded(position, next)) break;
            Point hit = world.clip(position, next);
            Point endpoint = hit == null ? next : projectHit(hit, position, next);
            if (endpoint == null || position.distanceSquared(endpoint) <= MIN_LENGTH_SQUARED) break;
            if (points.isEmpty()) points.add(position);
            points.add(endpoint);
            if (hit != null || last) break;
            travelled += length;
            position = next;
            velocity = velocity.scale(0.99).add(new Point(0, -gravity, 0));
        }
        return points.size() < 2 ? List.of() : List.copyOf(points);
    }

    /** Keep even a malformed collision callback from introducing off-ray/unbounded vertices. */
    private static Point projectHit(Point hit, Point from, Point to) {
        if (!validPosition(hit)) return null;
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double t = ((hit.x - from.x) * dx + (hit.y - from.y) * dy + (hit.z - from.z) * dz)
                / (dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(t) || t < -1.0e-7 || t > 1 + 1.0e-7) return null;
        t = Math.max(0, Math.min(1, t));
        Point projected = new Point(from.x + dx * t, from.y + dy * t, from.z + dz * t);
        return projected.distanceSquared(hit) <= 1.0e-8 ? projected : null;
    }

    private static boolean finite(Point point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
    private static boolean validPosition(Point point) {
        return finite(point) && Math.abs(point.x) <= MAX_COORDINATE
                && Math.abs(point.y) <= MAX_COORDINATE && Math.abs(point.z) <= MAX_COORDINATE;
    }
}
