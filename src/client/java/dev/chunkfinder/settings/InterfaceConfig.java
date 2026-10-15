package dev.chunkfinder.settings;

import com.google.gson.JsonArray;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/** Persistent presentation/accessibility preferences; never changes module enable states. */
public final class InterfaceConfig {
    private final JsonConfig config;
    private boolean hud, backgrounds, details, reducedMotion, contrast, hints;
    private int lines, hudScale, menuScale;
    private Set<String> minimized;
    public InterfaceConfig(Path path) {
        config = new JsonConfig(path);
        hud = config.bool("hud", true); backgrounds = config.bool("hudBackgrounds", false);
        details = config.bool("hudDetails", false); reducedMotion = config.bool("reducedMotion", false);
        contrast = config.bool("highContrast", false); hints = config.bool("tooltips", true);
        lines = config.integer("hudLines", 6, 2, 10); hudScale = config.integer("hudScale", 90, 60, 140);
        menuScale = config.integer("menuScale", 100, 75, 150);
        TreeSet<String> ids = new TreeSet<>();
        var value = config.element("minimizedPanels");
        if (value.isJsonArray()) for (var entry : value.getAsJsonArray())
            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString() && valid(entry.getAsString())) ids.add(entry.getAsString());
        minimized = Set.copyOf(ids);
    }
    private static boolean valid(String id) { return id != null && id.matches("[a-z0-9_-]{1,48}"); }
    public boolean hud() { return hud; }
    public boolean backgrounds() { return backgrounds; }
    public boolean details() { return details; }
    public boolean reducedMotion() { return reducedMotion; }
    public boolean contrast() { return contrast; }
    public boolean hints() { return hints; }
    public int lines() { return lines; }
    public int hudScale() { return hudScale; }
    public int menuScale() { return menuScale; }
    public Set<String> minimized() { return minimized; }
    public void hud(boolean v) { if (v != hud) { hud = v; save(); } }
    public void backgrounds(boolean v) { if (v != backgrounds) { backgrounds = v; save(); } }
    public void details(boolean v) { if (v != details) { details = v; save(); } }
    public void reducedMotion(boolean v) { if (v != reducedMotion) { reducedMotion = v; save(); } }
    public void contrast(boolean v) { if (v != contrast) { contrast = v; save(); } }
    public void hints(boolean v) { if (v != hints) { hints = v; save(); } }
    public void lines(int v) { int n = Math.max(2, Math.min(10, v)); if (lines != n) { lines = n; save(); } }
    public void hudScale(int v) { int n = Math.max(60, Math.min(140, v)); if (hudScale != n) { hudScale = n; save(); } }
    public void menuScale(int v) { int n = Math.max(75, Math.min(150, v)); if (menuScale != n) { menuScale = n; save(); } }
    public void minimized(Collection<String> ids) {
        TreeSet<String> clean = new TreeSet<>(); for (String id : ids) if (valid(id)) clean.add(id);
        if (!minimized.equals(clean)) { minimized = Set.copyOf(clean); save(); }
    }
    private void save() {
        config.put("hud", hud); config.put("hudBackgrounds", backgrounds); config.put("hudDetails", details);
        config.put("reducedMotion", reducedMotion); config.put("highContrast", contrast); config.put("tooltips", hints);
        config.put("hudLines", lines); config.put("hudScale", hudScale); config.put("menuScale", menuScale);
        JsonArray panels = new JsonArray(); new TreeSet<>(minimized).forEach(panels::add);
        config.putElement("minimizedPanels", panels); config.save();
    }
}
