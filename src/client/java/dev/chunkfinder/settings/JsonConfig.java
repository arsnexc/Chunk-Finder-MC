package dev.chunkfinder.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.chunkfinder.core.TargetIds;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

/** Shared config IO: retain unrelated keys and replace files atomically where supported. */
public final class JsonConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final System.Logger LOGGER = System.getLogger("chunkfinder");
    private final Path file;
    private final JsonObject json;

    public JsonConfig(Path file) {
        this.file = file;
        JsonObject read = new JsonObject();
        if (Files.exists(file)) try {
            read = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException | RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not read " + file.getFileName() + "; using defaults", exception);
        }
        json = read;
    }

    public boolean bool(String key, boolean fallback) {
        JsonElement value = json.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : fallback;
    }
    public int integer(String key, int fallback, int min, int max) {
        try {
            JsonElement value = json.get(key);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return fallback;
            return Math.max(min, Math.min(max, value.getAsInt()));
        } catch (RuntimeException ignored) { return fallback; }
    }

    public Set<String> targets(String key, String legacyKey, Collection<String> defaults) {
        if (json.has(key)) {
            JsonElement value = json.get(key);
            ArrayList<String> ids = new ArrayList<>();
            if (value.isJsonArray()) for (JsonElement entry : value.getAsJsonArray()) {
                if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) ids.add(entry.getAsString());
            }
            // Present-but-empty/null/malformed is never treated as select-all or a default block.
            return TargetIds.copy(ids);
        }
        if (legacyKey != null && json.has(legacyKey)) {
            JsonElement value = json.get(legacyKey);
            return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                    ? TargetIds.copy(Set.of(value.getAsString())) : Set.of();
        }
        return TargetIds.copy(defaults);
    }
    public void put(String key, boolean value) { json.addProperty(key, value); }
    public void put(String key, int value) { json.addProperty(key, value); }
    public void putTargets(String key, Collection<String> value) { json.add(key, GSON.toJsonTree(TargetIds.copy(value))); }
    public void remove(String key) { json.remove(key); }

    public void save() {
        Path temporary = null;
        try {
            Path parent = file.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
            Files.writeString(temporary, GSON.toJson(json));
            try {
                Files.move(temporary, file.toAbsolutePath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not save " + file.getFileName(), exception);
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }
}
