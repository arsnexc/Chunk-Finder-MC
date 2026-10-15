package dev.chunkfinder;

import dev.chunkfinder.settings.BaseFinderConfig;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric-path adapter for the existing chunkfinder.json settings file. */
final class ChunkFinderSettings {
    private final BaseFinderConfig config = new BaseFinderConfig(FabricLoader.getInstance().getConfigDir().resolve("chunkfinder.json"));
    static ChunkFinderSettings load() { return new ChunkFinderSettings(); }
    boolean enabled() { return config.enabled(); }
    void setEnabled(boolean value) { config.enabled(value); }
    int sensitivity() { return config.sensitivity(); }
    void setSensitivity(int value) { config.sensitivity(value); }
    int simulationDistance() { return config.radius(); }
    void setSimulationDistance(int value) { config.radius(value); }
    boolean strict() { return config.strict(); }
    void strict(boolean value) { config.strict(value); }
    boolean geodes() { return config.geodes(); }
    void geodes(boolean value) { config.geodes(value); }
}
