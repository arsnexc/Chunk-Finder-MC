package dev.chunkfinder.settings;

import java.nio.file.Path;

/** Same Freecam file/range key; the old generic option field is deliberately not reused. */
public final class FreecamConfig {
    private final JsonConfig config;
    private int speed;
    private boolean swapStrafe, fullTerrain, horizontal, cutaway;
    private int acceleration, deceleration;

    public FreecamConfig(Path file) {
        config = new JsonConfig(file);
        speed = config.integer("range", 5, 1, 10);
        swapStrafe = config.bool("swapStrafe", false);
        fullTerrain = config.bool("fullTerrain", true);
        cutaway = config.bool("cutaway", true);
        horizontal = config.bool("horizontalFlight", false);
        acceleration = config.integer("accelerationTicks", 0, 0, 30);
        deceleration = config.integer("decelerationTicks", 0, 0, 30);
    }
    public boolean horizontal() { return horizontal; }
    public void horizontal(boolean v) { if (horizontal != v) { horizontal = v; save(); } }
    public int acceleration() { return acceleration; }
    public int deceleration() { return deceleration; }
    public void acceleration(int v) { int n = Math.max(0, Math.min(30, v)); if (n != acceleration) { acceleration = n; save(); } }
    public void deceleration(int v) { int n = Math.max(0, Math.min(30, v)); if (n != deceleration) { deceleration = n; save(); } }
    public int speed() { return speed; }
    public boolean cutaway(){return cutaway;}
    public void cutaway(boolean value){if(cutaway!=value){cutaway=value;save();}}
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
        config.put("range", speed); config.put("swapStrafe", swapStrafe); config.put("fullTerrain", fullTerrain); config.put("cutaway", cutaway);
        config.put("horizontalFlight", horizontal); config.put("accelerationTicks", acceleration); config.put("decelerationTicks", deceleration); config.save();
    }
}
