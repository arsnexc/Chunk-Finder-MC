package dev.chunkfinder.core;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;

/** Immutable module/target colors and a transactional edit draft. Alpha stays opaque. */
public final class EspPalette {
    public record Snapshot(Integer base, Map<String, Integer> targets) {
        public Snapshot {
            if (base != null) base |= 0xFF000000;
            TreeMap<String, Integer> clean = new TreeMap<>();
            if (targets != null) targets.forEach((id, color) -> {
                String key = TargetIds.normalize(id);
                if (key != null && color != null) clean.put(key, color | 0xFF000000);
            });
            targets = Collections.unmodifiableMap(clean);
        }
        public int color(String id, int fallback) {
            Integer target = targets.get(id == null ? "" : id);
            return target != null ? target : base != null ? base : fallback;
        }
    }
    public static final class Draft {
        private final Snapshot original;
        private Integer base;
        private final Map<String, Integer> targets;
        public Draft(Snapshot original) { this.original = original; base = original.base(); targets = new TreeMap<>(original.targets()); }
        public Snapshot snapshot() { return new Snapshot(base, targets); }
        public boolean changed() { return !original.equals(snapshot()); }
        public int color(String id, int fallback) { Integer target = targets.get(id == null ? "" : id); return target != null ? target : base != null ? base : fallback; }
        public boolean custom(String id) { return id.isEmpty() ? base != null : targets.containsKey(id); }
        public void set(String id, int color) {
            if (id.isEmpty()) base = color | 0xFF000000;
            else { String key = TargetIds.normalize(id); if (key != null) targets.put(key, color | 0xFF000000); }
        }
        public void reset(String id) { if (id.isEmpty()) base = null; else targets.remove(id); }
        public void resetAll() { base = null; targets.clear(); }
        public void replace(Snapshot value){base=value.base();targets.clear();targets.putAll(value.targets());}
    }
    private EspPalette() { }
    public static OptionalInt parse(String text) {
        if (text == null) return OptionalInt.empty();
        String value = text.trim(); if (value.startsWith("#")) value = value.substring(1);
        if (!value.matches("[0-9a-fA-F]{6}")) return OptionalInt.empty();
        return OptionalInt.of(0xFF000000 | Integer.parseInt(value, 16));
    }
    public static String hex(int color) { return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF); }
}
