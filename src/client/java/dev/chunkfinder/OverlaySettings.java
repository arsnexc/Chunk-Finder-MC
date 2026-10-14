package dev.chunkfinder;

import dev.chunkfinder.settings.OverlayConfig;
import net.fabricmc.loader.api.FabricLoader;
import java.util.Collection;
import java.util.Set;

/** Fabric config-path adapter; JSON migration and persistence are independently tested. */
final class OverlaySettings {
    private final OverlayConfig config;
    OverlaySettings(String fileName, int defaultRange, int min, int max) {
        this(fileName, defaultRange, min, max, null, Set.of());
    }
    OverlaySettings(String fileName, int defaultRange, int min, int max, String targetKey, Collection<String> defaults) {
        config = new OverlayConfig(FabricLoader.getInstance().getConfigDir().resolve(fileName), defaultRange, min, max, targetKey, defaults);
    }
    boolean enabled() { return config.enabled(); }
    boolean option() { return config.option(); }
    int range() { return config.range(); }
    Set<String> targets() { return config.targets(); }
    void setEnabled(boolean value) { config.setEnabled(value); }
    void setOption(boolean value) { config.setOption(value); }
    void setRange(int value) { config.setRange(value); }
    void setTargets(Collection<String> value) { config.setTargets(value); }
}
