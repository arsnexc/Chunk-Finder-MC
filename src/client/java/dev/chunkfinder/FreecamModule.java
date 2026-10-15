package dev.chunkfinder;

import dev.chunkfinder.core.FreecamMovement;
import dev.chunkfinder.core.FlightSmoothing;
import dev.chunkfinder.core.TerrainBoundary;
import dev.chunkfinder.settings.FreecamConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Input;

/** Detached camera. Mouse look is routed here by MouseLookMixin instead of turning the player. */
public final class FreecamModule extends Module {
    private static FreecamModule active;
    private final FreecamConfig settings = new FreecamConfig(FabricLoader.getInstance().getConfigDir().resolve("chunkfinder-freecam.json"));
    private final FreecamControls controls = new FreecamControls();
    private final FreecamTerrainVisibility terrain = new FreecamTerrainVisibility();
    private Entity camera;
    private LocalPlayer owner;
    private Entity previousCamera;
    private Input captured;
    private final FlightSmoothing smoothing = new FlightSmoothing();
    private TerrainBoundary.Status boundary = TerrainBoundary.Status.AVAILABLE;

    public FreecamModule() { super("Freecam", ModuleCategory.VISUALS, false); }

    @Override public void setEnabled(boolean enabled) {
        if (enabled == isEnabled()) return;
        if (enabled) toggle(Minecraft.getInstance());
        else stop(Minecraft.getInstance());
    }

    public String hudDetails() {
        return camera == null ? "Player-loaded chunks only" : camera.getBlockX() + ", " + camera.getBlockY() + ", " + camera.getBlockZ() + " · loaded data only";
    }
    public boolean fullTerrain() { return settings.fullTerrain(); }
    public void setFullTerrain(boolean value) {
        settings.fullTerrain(value);
        Minecraft client = Minecraft.getInstance();
        if (isEnabled() && valid(client) && value) terrain.begin(client); else terrain.end(client);
    }
    public TerrainBoundary.Status boundary() { return boundary; }
    public boolean horizontalFlight() { return settings.horizontal(); }
    public void setHorizontalFlight(boolean v) { settings.horizontal(v); smoothing.reset(); }
    public int acceleration() { return settings.acceleration(); }
    public int deceleration() { return settings.deceleration(); }
    public void setAcceleration(int v) { settings.acceleration(v); smoothing.reset(); }
    public void setDeceleration(int v) { settings.deceleration(v); smoothing.reset(); }
    public void nextSpeedPreset() { setSpeed(speed() < 5 ? 5 : speed() < 10 ? 10 : 2); }
    public int speed() { return settings.speed(); }
    public void setSpeed(int value) { settings.speed(value); }
    public boolean swapStrafe() { return settings.swapStrafe(); }
    public void setSwapStrafe(boolean value) { settings.swapStrafe(value); }

    public boolean toggle(Minecraft client) {
        if (isEnabled()) {
            stop(client);
            return true;
        }
        if (!FeatureAccess.allowed(client) || client.player.isRemoved() || !client.player.isAlive()) return false;
        Entity detached = EntityType.MARKER.create(client.level, EntitySpawnReason.LOAD);
        if (detached == null) return false;
        smoothing.reset();
        owner = client.player;
        previousCamera = client.getCameraEntity();
        camera = detached;
        camera.setPos(client.player.getX(), client.player.getEyeY(), client.player.getZ());
        camera.setYRot(client.player.getYRot());
        camera.setXRot(client.player.getXRot());
        camera.setOldPosAndRot();
        client.setCameraEntity(camera);
        controls.beginView(client.options);
        if (fullTerrain()) terrain.begin(client);
        active = this;
        super.setEnabled(true);
        return true;
    }

    /** Return true if this mouse movement was consumed by the detached camera. */
    public static boolean turnCamera(LocalPlayer player, double yaw, double pitch) {
        FreecamModule mod = active;
        Minecraft client = Minecraft.getInstance();
        if (mod == null || !mod.isEnabled() || mod.camera == null || player == null
                || player != client.player || player != mod.owner || client.getCameraEntity() != mod.camera
                || mod.camera.level() != player.level()) return false;
        mod.camera.turn(yaw, pitch);
        return true;
    }

    public void beginTick(Minecraft client) {
        if (!isEnabled()) return;
        if (!valid(client)) { stop(client); return; }
        // F5 front-view otherwise mirrors the rendered basis relative to the marker.
        controls.enforceView(client.options);
        terrain.enforce(client);
        // Mask the actual remappable bindings while the player ticks, then restore them.
        captured = controls.captureAndMask(client.options);
    }

    public void tick(Minecraft client) {
        if (!isEnabled()) return;
        if (!valid(client)) { stop(client); return; }
        // Vanilla handles F5 during its tick; normalize again before movement/rendering.
        controls.enforceView(client.options);
        terrain.enforce(client);
        camera.setOldPosAndRot();
        if (captured != null && client.screen == null) moveCamera(); else smoothing.reset();
        boundary = TerrainBoundary.check(camera.getBlockX(), camera.getBlockZ(),
                (x, z) -> LoadedChunks.at(client.level, x, z));
        restoreInputs(client);
    }

    private boolean valid(Minecraft client) {
        return FeatureAccess.allowed(client) && client.player == owner && client.player.isAlive()
                && !client.player.isRemoved() && camera != null
                && !camera.isRemoved() && camera.level() == client.level
                && client.getCameraEntity() == camera;
    }

    private void moveCamera() {
        FreecamMovement.Vector movement = smoothing.step(controls.movement(camera.getYRot(), camera.getXRot(), captured, speed(), swapStrafe(), horizontalFlight()), acceleration(), deceleration());
        if (movement.x() != 0 || movement.y() != 0 || movement.z() != 0)
            camera.setPos(camera.getX() + movement.x(), camera.getY() + movement.y(), camera.getZ() + movement.z());
    }

    private void restoreInputs(Minecraft client) {
        if (captured == null) return;
        if (client != null) controls.restore(client.options, captured);
        captured = null;
    }

    private void stop(Minecraft client) {
        smoothing.reset(); boundary = TerrainBoundary.Status.AVAILABLE;
        restoreInputs(client);
        terrain.end(client);
        // Restore our perspective only when we still own the camera, or vanilla
        // has returned it to the player (e.g. respawn). Do not override another mod.
        boolean ownView = client != null && (client.getCameraEntity() == camera || client.getCameraEntity() == client.player);
        controls.endView(client == null ? null : client.options, ownView);
        if (client != null && client.getCameraEntity() == camera) {
            Entity restore = previousCamera;
            if (client.level == null || restore == null || restore.isRemoved()
                    || restore.level() != client.level) restore = client.player;
            if (restore != null) client.setCameraEntity(restore);
        }
        if (camera != null && !camera.isRemoved()) camera.setRemoved(Entity.RemovalReason.DISCARDED);
        if (active == this) active = null;
        camera = null;
        owner = null;
        previousCamera = null;
        super.setEnabled(false);
    }

}
