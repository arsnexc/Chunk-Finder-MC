package dev.chunkfinder.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.chunkfinder.core.EspPalette;
import java.nio.file.Path;
import java.util.TreeMap;

/** Dedicated per-module color file: cannot overwrite a module's separately-owned scalar config. */
public final class PaletteConfig {
    private final JsonConfig config;
    private final int fallback;
    private EspPalette.Snapshot palette;
    public PaletteConfig(Path path, int fallback) {
        this.fallback = fallback;
        config = new JsonConfig(path);
        Integer base = read(config.element("base"));
        TreeMap<String, Integer> targets = new TreeMap<>();
        JsonElement stored = config.element("targets");
        if (stored.isJsonObject()) stored.getAsJsonObject().entrySet().forEach(e -> {
            Integer color = read(e.getValue()); if (color != null) targets.put(e.getKey(), color);
        });
        palette = new EspPalette.Snapshot(base, targets);
    }
    private static Integer read(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return null;
        var color = EspPalette.parse(value.getAsString()); return color.isPresent() ? color.getAsInt() : null;
    }
    public int fallback() { return fallback; }
    public int color(String id) { return palette.color(id, fallback); }
    public int color(String id, int automatic) { return palette.color(id, automatic); }
    public EspPalette.Snapshot snapshot() { return palette; }
    public void apply(EspPalette.Snapshot value) {
        if (palette.equals(value)) return;
        palette = value;
        if (value.base() == null) config.remove("base");
        else config.putElement("base", new com.google.gson.JsonPrimitive(EspPalette.hex(value.base())));
        JsonObject colors = new JsonObject();
        value.targets().forEach((id, color) -> colors.addProperty(id, EspPalette.hex(color)));
        config.putElement("targets", colors); config.save();
    }
}
