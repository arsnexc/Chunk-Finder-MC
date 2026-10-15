package dev.chunkfinder.ui;

import java.util.HashMap;
import java.util.Map;

/** Non-layout animation only: hitboxes never chase moving rows. Injectable time for deterministic tests. */
public final class UiMotion {
    private record Transition(double from, double to, long since) { }
    private final Map<String, Transition> transitions = new HashMap<>();
    private static final long DURATION = 140_000_000L;
    public double value(String key, boolean on, long now, boolean reduced) {
        double target = on ? 1 : 0;
        Transition old = transitions.get(key);
        if (old == null || reduced) { transitions.put(key, new Transition(target, target, now)); return target; }
        if (old.to != target) { old = new Transition(at(old, now), target, now); transitions.put(key, old); }
        return at(old, now);
    }
    private static double at(Transition t, long now) {
        double progress = Math.max(0, Math.min(1, (double) (now - t.since) / DURATION));
        double eased = 1 - Math.pow(1 - progress, 3);
        return t.from + (t.to - t.from) * eased;
    }
    public static int mix(int from, int to, double amount) {
        double t = Math.max(0, Math.min(1, amount)); int result = 0;
        for (int shift : new int[]{0, 8, 16, 24}) {
            int a = from >>> shift & 255, b = to >>> shift & 255;
            result |= (int) Math.round(a + (b - a) * t) << shift;
        }
        return result;
    }
    public static double fittedScale(int percent, int width, int height) {
        return Math.max(0.5, Math.min(percent / 100.0, Math.min(width / 256.0, height / 144.0)));
    }
}
