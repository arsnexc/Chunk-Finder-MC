package dev.chunkfinder.settings;

import dev.chunkfinder.core.TargetIds;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

/** The existing block-ESP settings schema plus explicit multi-target IDs. */
public final class BlockEspConfig {
    private final JsonConfig config;
    private boolean enabled, traces, outlines, exposedOnly;
    private int radius;
    private Set<String> targets;

    public BlockEspConfig(Path file, Collection<String> defaults) { this(file, defaults, false); }
    public BlockEspConfig(Path file, Collection<String> defaults, boolean defaultExposedOnly) {
        config = new JsonConfig(file);
        enabled = config.bool("enabled", false);
        traces = config.bool("traces", true);
        outlines = config.bool("blockHighlight", true);
        exposedOnly = config.bool("exposedOnly", defaultExposedOnly);
        radius = config.integer("searchRadius", 6, 2, 12);
        targets = config.targets("blockIds", "blockId", defaults);
    }
    public boolean enabled() { return enabled; }
    public boolean traces() { return traces; }
    public boolean outlines() { return outlines; }
    public boolean exposedOnly() { return exposedOnly; }
    public void exposedOnly(boolean value) { if (exposedOnly != value) { exposedOnly = value; save(); } }
    public int radius() { return radius; }
    public Set<String> targets() { return targets; }
    public void enabled(boolean value) { if (enabled != value) { enabled = value; save(); } }
    public void traces(boolean value) { if (traces != value) { traces = value; save(); } }
    public void outlines(boolean value) { if (outlines != value) { outlines = value; save(); } }
    public void radius(int value) {
        int next = Math.max(2, Math.min(12, value));
        if (radius != next) { radius = next; save(); }
    }
    public boolean targets(Collection<String> value) {
        Set<String> next = TargetIds.copy(value);
        if (targets.equals(next)) return false;
        targets = next; save(); return true;
    }
    private void save() {
        config.put("enabled", enabled); config.put("traces", traces); config.put("blockHighlight", outlines);
        config.put("exposedOnly", exposedOnly);
        config.put("searchRadius", radius); config.putTargets("blockIds", targets);
        config.remove("blockId");
        config.save();
    }
}
