package dev.chunkfinder.core;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Shared client-tick cache with independent memory and physical-read ceilings. */
public final class TickReadCache<K,V> {
    public record Stats(int cached, int reads, int hits, long totalReads, long totalHits, long invalidations) { }
    private final int maxEntries, maxReads;
    private final Map<K,V> values = new HashMap<>();
    private Object owner;
    private long tick = Long.MIN_VALUE, totalReads, totalHits, invalidations;
    private int reads, hits;
    public TickReadCache(int maxEntries, int maxReads) {
        if (maxEntries<1 || maxReads<1) throw new IllegalArgumentException("Cache limits must be positive");
        this.maxEntries=maxEntries;this.maxReads=maxReads;
    }
    public void begin(Object owner, long tick) {
        if (owner!=this.owner || tick!=this.tick) { values.clear();reads=hits=0;this.owner=owner;this.tick=tick; }
    }
    public boolean canRead(int worstCase) { return owner!=null && worstCase>=0 && worstCase<=maxReads-reads; }
    public V get(K key, Supplier<V> read) {
        if (owner==null) throw new IllegalStateException("No active world");
        V value=values.get(key);
        if (value!=null) { hits++;totalHits++;return value; }
        if (!canRead(1)) throw new IllegalStateException("Physical read budget exhausted");
        value=java.util.Objects.requireNonNull(read.get(),"Read returned null");reads++;totalReads++;
        if (values.size()<maxEntries) values.put(key,value);
        return value;
    }
    public void invalidate(K key) { if(values.remove(key)!=null) invalidations++; }
    /** Invalidating cached values does not reset the current tick's read allowance. */
    public void invalidateAll() { if(!values.isEmpty())invalidations++;values.clear(); }
    public Stats stats(){return new Stats(values.size(),reads,hits,totalReads,totalHits,invalidations);}
}
