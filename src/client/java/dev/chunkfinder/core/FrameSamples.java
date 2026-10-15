package dev.chunkfinder.core;

import java.util.Arrays;

/** Bounded measured samples, not estimated FPS. Units are nanoseconds. */
public final class FrameSamples {
    public record Summary(int samples, double meanMs, double medianMs, double p95Ms, double maxMs) { }
    private final long[] samples;
    private int cursor, count;
    public FrameSamples(int capacity) { if (capacity < 1) throw new IllegalArgumentException("capacity"); samples = new long[capacity]; }
    public void add(long nanos) {
        if (nanos <= 0) return;
        samples[cursor] = nanos; cursor = (cursor + 1) % samples.length; count = Math.min(samples.length, count + 1);
    }
    public void clear() { cursor = count = 0; }
    public Summary summary() {
        if (count == 0) return new Summary(0, 0, 0, 0, 0);
        long[] sorted = Arrays.copyOf(samples, count); Arrays.sort(sorted);
        double sum = 0; for (long value : sorted) sum += value;
        return new Summary(count, sum / count / 1e6, sorted[(count - 1) / 2] / 1e6,
                sorted[Math.max(0, (int) Math.ceil(count * .95) - 1)] / 1e6, sorted[count - 1] / 1e6);
    }
}
