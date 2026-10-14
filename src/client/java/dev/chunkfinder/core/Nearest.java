package dev.chunkfinder.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.ToDoubleFunction;

/** Bounded nearest-N selection; avoids sorting/copying every retained match each frame. */
public final class Nearest<T> {
    private record Candidate<T>(T value, double distance, long order) { }
    private final int limit;
    private final PriorityQueue<Candidate<T>> queue;
    private long sequence;

    public Nearest(int limit) {
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        this.limit = limit;
        queue = new PriorityQueue<>(Comparator.<Candidate<T>>comparingDouble(Candidate::distance)
                .thenComparingLong(Candidate::order).reversed());
    }
    public boolean accepts(double distance) {
        return Double.isFinite(distance) && (queue.size() < limit || distance < queue.peek().distance());
    }
    public void offer(T value, double distance) {
        if (!accepts(distance)) return;
        Candidate<T> candidate = new Candidate<>(value, distance, sequence++);
        if (queue.size() < limit) queue.add(candidate);
        else if (distance < queue.peek().distance()) { queue.poll(); queue.add(candidate); }
    }
    public List<T> values() {
        List<Candidate<T>> sorted = new ArrayList<>(queue);
        sorted.sort(Comparator.<Candidate<T>>comparingDouble(Candidate::distance).thenComparingLong(Candidate::order));
        return sorted.stream().map(Candidate::value).toList();
    }
    public static <T> List<T> select(Iterable<T> values, int limit, ToDoubleFunction<T> distance) {
        Nearest<T> nearest = new Nearest<>(limit);
        for (T value : values) nearest.offer(value, distance.applyAsDouble(value));
        return nearest.values();
    }
}
