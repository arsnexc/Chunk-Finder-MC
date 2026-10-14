package dev.chunkfinder.settings;

import dev.chunkfinder.core.TargetIds;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

/** Independent scalar overlay settings, optionally with entity-type or item-ID targets. */
public final class OverlayConfig {
    private final JsonConfig config;
    private final int min, max;
    private final String targetKey;
    private boolean enabled, option;
    private int range;
    private Set<String> targets;

    public OverlayConfig(Path file, int defaultRange, int min, int max) {
        this(file, defaultRange, min, max, null, Set.of());
    }
    public OverlayConfig(Path file, int defaultRange, int min, int max, String targetKey, Collection<String> defaults) {
        config = new JsonConfig(file);
        this.min = min; this.max = max; this.targetKey = targetKey;
        enabled = config.bool("enabled", false);
        option = config.bool("option", true);
        range = config.integer("range", defaultRange, min, max);
        targets = targetKey == null ? Set.of() : config.targets(targetKey, null, defaults);
    }
    public boolean enabled() { return enabled; }
    public boolean option() { return option; }
    public int range() { return range; }
    public Set<String> targets() { return targets; }
    public void setEnabled(boolean value) { if (enabled != value) { enabled = value; save(); } }
    public void setOption(boolean value) { if (option != value) { option = value; save(); } }
    public void setRange(int value) {
        int next = Math.max(min, Math.min(max, value));
        if (range != next) { range = next; save(); }
    }
    public void setTargets(Collection<String> values) {
        if (targetKey == null) throw new IllegalStateException("This overlay has no target list");
        Set<String> next = TargetIds.copy(values);
        if (!targets.equals(next)) { targets = next; save(); }
    }
    private void save() {
        config.put("enabled", enabled); config.put("option", option); config.put("range", range);
        if (targetKey != null) config.putTargets(targetKey, targets);
        config.save();
    }
}
