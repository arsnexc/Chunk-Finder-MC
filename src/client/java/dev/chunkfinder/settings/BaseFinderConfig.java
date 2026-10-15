package dev.chunkfinder.settings;

import java.nio.file.Path;

/** Preserves chunkfinder.json and legacy scalars; geodes remain an explicit optional feature. */
public final class BaseFinderConfig {
    private final JsonConfig config;
    private boolean enabled, strict, geodes;
    private int sensitivity, radius;
    public BaseFinderConfig(Path path) {
        config = new JsonConfig(path);
        enabled = config.bool("enabled", false);
        strict = config.bool("strictBaseClues", true);
        geodes = config.bool("showGeodes", false);
        sensitivity = config.integer("sensitivity", 5, 1, 10);
        radius = config.integer("simulationDistance", 6, 2, 12);
    }
    public boolean enabled() { return enabled; }
    public boolean strict() { return strict; }
    public boolean geodes() { return geodes; }
    public int sensitivity() { return sensitivity; }
    public int radius() { return radius; }
    public void enabled(boolean v) { if (enabled != v) { enabled = v; save(); } }
    public void strict(boolean v) { if (strict != v) { strict = v; save(); } }
    public void geodes(boolean v) { if (geodes != v) { geodes = v; save(); } }
    public void sensitivity(int v) { int n = Math.max(1, Math.min(10, v)); if (sensitivity != n) { sensitivity = n; save(); } }
    public void radius(int v) { int n = Math.max(2, Math.min(12, v)); if (radius != n) { radius = n; save(); } }
    private void save() {
        config.put("enabled", enabled); config.put("sensitivity", sensitivity); config.put("simulationDistance", radius);
        config.put("strictBaseClues", strict); config.put("showGeodes", geodes); config.save();
    }
}
