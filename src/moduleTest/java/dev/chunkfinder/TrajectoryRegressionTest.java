package dev.chunkfinder;

import dev.chunkfinder.core.TrajectoryPredictor;
import dev.chunkfinder.core.TrajectoryPredictor.Point;

import java.util.List;

/** Exercises production trajectory math/budgets with synthetic loaded/collision access. */
final class TrajectoryRegressionTest {
    private static int assertions;
    private static final Point START = new Point(1, 10, 1);

    static int run() {
        assertions = 0;
        baseline(); limits(); invalidInputs(); collisions();
        return assertions;
    }

    private static void baseline() {
        World world = new World();
        List<Point> path = TrajectoryPredictor.predict(START, new Point(2, 0, 0), 0.1, 2, world);
        check(path.size() == 3, "two prediction steps produce two segments");
        point(START, path.getFirst(), "path starts at the current entity position");
        point(new Point(3, 10, 1), path.get(1), "first step keeps existing velocity order");
        point(new Point(4.98, 9.9, 1), path.getLast(), "existing 0.99 drag/gravity approximation retained");
        check(world.preflights == 2 && world.clips == 2, "one preflight and collision query per step");
        try { path.add(START); throw new AssertionError("mutable prediction"); }
        catch (UnsupportedOperationException expected) { assertions++; }

        World missing = new World(); missing.allow = 0;
        check(TrajectoryPredictor.predict(START, new Point(1, 0, 0), 0, 10, missing).isEmpty(), "unavailable first segment has no visible path");
        check(missing.preflights == 1 && missing.clips == 0, "failed loaded-data preflight never reaches collision access");
        World edge = new World(); edge.allow = 2;
        check(TrajectoryPredictor.predict(START, new Point(1, 0, 0), 0, 10, edge).size() == 3, "only the known prefix survives at an unloaded boundary");
        check(edge.preflights == 3 && edge.clips == 2, "prediction stops on the first unavailable segment");
    }

    private static void limits() {
        World world = new World();
        List<Point> path = TrajectoryPredictor.predict(START, new Point(1, 0, 0), 0, 200, world);
        check(world.clips <= 72 && path.size() <= 73, "requested step counts cannot exceed the hard 72-ray limit");
        world = new World();
        path = TrajectoryPredictor.predict(START, new Point(1, 0, 0), 0, Integer.MAX_VALUE, world);
        check(world.clips == 72 && path.size() == 73, "extreme step requests remain bounded");
        for (int steps : new int[]{0, -1, Integer.MIN_VALUE}) {
            World empty = new World();
            check(TrajectoryPredictor.predict(START, new Point(1, 0, 0), 0, steps, empty).isEmpty(), "nonpositive steps produce no path");
            check(empty.preflights == 0, "nonpositive steps never touch world access");
        }

        for (Point velocity : List.of(new Point(32.001, 0, 0), new Point(1.0e300, 0, 0))) {
            World fast = new World();
            check(TrajectoryPredictor.predict(START, velocity, 0, 72, fast).isEmpty(), "oversized single-step rays are rejected, not clamped into invented motion");
            check(fast.preflights == 0 && fast.clips == 0, "oversized rays cannot reach native block traversal");
        }
        for (Point velocity : List.of(new Point(32, 0, 0), new Point(-32, 0, 0), new Point(12, 12, 12))) {
            World bounded = new World();
            List<Point> points = TrajectoryPredictor.predict(START, velocity, 0, 72, bounded);
            double length = 0;
            for (int i = 1; i < points.size(); i++) {
                double segment = Math.sqrt(points.get(i - 1).distanceSquared(points.get(i)));
                check(segment > 0 && segment <= 32 + 1.0e-9, "each cached segment is nonzero and at most 32 blocks");
                length += segment;
            }
            check(Math.abs(length - 256) < 1.0e-8, "long previews end at the 256-block total-length budget");
            check(bounded.clips <= 72 && bounded.preflights == bounded.clips, "distance clipping retains the ray-count cap");
        }
        check(TrajectoryPredictor.canTrace(START, new Point(33, 10, 1)), "exact segment limit is inclusive");
        check(!TrajectoryPredictor.canTrace(START, new Point(33.001, 10, 1)), "native preflight also rejects excessive distance");
        check(!TrajectoryPredictor.canTrace(START, START), "zero-length rays need no native traversal");
    }

    private static void invalidInputs() {
        Point[] positions = {new Point(Double.NaN, 0, 0), new Point(Double.POSITIVE_INFINITY, 0, 0), new Point(31_000_000, 0, 0), null};
        for (Point position : positions) {
            World world = new World();
            check(TrajectoryPredictor.predict(position, new Point(1, 0, 0), 0, 72, world).isEmpty(), "invalid start rejected");
            check(world.preflights == 0 && world.clips == 0, "invalid start cannot query the world");
        }
        for (Point velocity : new Point[]{new Point(Double.NaN, 0, 0), new Point(0, Double.NEGATIVE_INFINITY, 0), null}) {
            World world = new World();
            check(TrajectoryPredictor.predict(START, velocity, 0, 72, world).isEmpty(), "nonfinite/null velocity rejected");
            check(world.preflights == 0, "invalid velocity cannot query the world");
        }
        for (double gravity : new double[]{Double.NaN, Double.POSITIVE_INFINITY}) {
            World world = new World();
            check(TrajectoryPredictor.predict(START, new Point(1, 0, 0), gravity, 72, world).isEmpty(), "nonfinite gravity rejected");
            check(world.preflights == 0, "invalid gravity cannot query the world");
        }
        World stationary = new World();
        check(TrajectoryPredictor.predict(START, new Point(0, 0, 0), 0, 72, stationary).isEmpty(), "stationary/no-gravity path is invisible");
        check(stationary.preflights == 0, "stationary paths do not emit repeated zero-length rays");
        World apex = new World();
        List<Point> falling = TrajectoryPredictor.predict(START, new Point(0, 0, 0), 0.1, 2, apex);
        check(falling.size() == 2 && apex.clips == 1, "a stationary future step may still accelerate under gravity");
        point(new Point(1, 9.9, 1), falling.getLast(), "apex continues into a falling segment without a duplicate point");
        World border = new World();
        check(TrajectoryPredictor.predict(new Point(29_999_999, 0, 0), new Point(2, 0, 0), 0, 1, border).isEmpty(), "world-coordinate overflow boundary is rejected");
        check(border.preflights == 0, "out-of-domain endpoints cannot reach integer block-coordinate conversion");
    }

    private static void collisions() {
        World wall = new World(); wall.hit = new Point(2, 10, 1);
        List<Point> hit = TrajectoryPredictor.predict(START, new Point(2, 0, 0), 0, 72, wall);
        check(hit.size() == 2 && wall.clips == 1, "first collision terminates prediction");
        point(wall.hit, hit.getLast(), "collision endpoint is retained");
        for (Point invalid : List.of(new Point(Double.NaN, 0, 0), new Point(100, 10, 1), new Point(2, 12, 1), START)) {
            World malformed = new World(); malformed.hit = invalid;
            check(TrajectoryPredictor.predict(START, new Point(2, 0, 0), 0, 72, malformed).isEmpty(), "invalid/off-ray/start-point hits cannot create phantom or unbounded lines");
            check(malformed.clips == 1, "malformed/zero-length collision stops rather than retrying");
        }
    }

    private static final class World implements TrajectoryPredictor.World {
        int preflights, clips, allow = Integer.MAX_VALUE;
        Point hit;
        @Override public boolean loaded(Point from, Point to) { return ++preflights <= allow; }
        @Override public Point clip(Point from, Point to) { clips++; return hit; }
    }
    private static void point(Point expected, Point actual, String message) {
        check(expected.distanceSquared(actual) < 1.0e-16, message + ": expected " + expected + ", got " + actual);
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
