package dev.chunkfinder.settings;

import java.nio.file.Path;

/** Same Freecam file/range key; the old generic option field is deliberately not reused. */
public final class FreecamConfig {
    private final JsonConfig config;
    private int speed;
    private boolean swapStrafe, fullTerrain;

    public FreecamConfig(Path file) {
        config = new JsonConfig(file);
        speed = config.integer("range", 5, 1, 10);
        swapStrafe = config.bool("swapStrafe", false);
        fullTerrain = config.bool("fullTerrain", true);
    }
    public int speed() { return speed; }
    public boolean fullTerrain() { return fullTerrain; }
    public void fullTerrain(boolean value) { if (fullTerrain != value) { fullTerrain = value; save(); } }
    public boolean swapStrafe() { return swapStrafe; }
    public void speed(int value) {
        int next = Math.max(1, Math.min(10, value));
        if (speed != next) { speed = next; save(); }
    }
    public void swapStrafe(boolean value) {
        if (swapStrafe != value) { swapStrafe = value; save(); }
    }
    private void save() {
        config.put("range", speed); config.put("swapStrafe", swapStrafe); config.put("fullTerrain", fullTerrain); config.save();
    }
}
